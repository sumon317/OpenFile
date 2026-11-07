package com.example.core.network

import com.example.core.database.NetworkProfileEntity
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.EnumSet
import java.util.Properties
import java.util.Vector
import java.util.concurrent.TimeUnit

/**
 * Thrown when an SFTP server presents an SSH host key whose SHA-256 fingerprint has not yet
 * been verified and pinned by the user, or when the server's fingerprint changed.
 * OpenFile never disables SSH host-key verification silently.
 */
class SshHostKeyVerificationRequiredException(
    val host: String,
    val port: Int,
    val keyType: String,
    val sha256Fingerprint: String,
    val mismatchWithPinned: Boolean
) : SecurityException(
    if (mismatchWithPinned) {
        "WARNING: Remote SSH host key for $host:$port has changed! Expected pinned fingerprint does not match $sha256Fingerprint."
    } else {
        "First connection to SFTP host $host:$port. Verify SSH host key fingerprint ($keyType): $sha256Fingerprint"
    }
)

data class DiscoveredLanHost(
    val ipAddress: String,
    val port: Int,
    val protocolGuess: String,
    val bannerOrName: String,
    /** Normalised connection protocol: FTP, FTPS, SFTP or SMB. */
    val protocol: String = "FTP"
)

/** Returns true when [message] looks like a wrong username / password failure for any protocol. */
fun isAuthenticationFailureMessage(message: String?): Boolean {
    val m = message?.lowercase() ?: return false
    return "login failed" in m || "auth fail" in m || "authentication" in m ||
        "logon_failure" in m || "access_denied" in m || "not logged in" in m
}

/**
 * Real remote network client engine for FTP, FTPS, SFTP (JSch with strict host-key verification),
 * and SMB2/3 (SMBJ), plus local subnet LAN service discovery.
 */
object RemoteNetworkClients {

    /**
     * Lists a remote directory on an FTP, FTPS, SFTP, or SMB2/3 server using [profile] and [decryptedSecret].
     */
    suspend fun listRemoteDirectory(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        remotePath: String
    ): Result<List<FileReference>> = withContext(Dispatchers.IO) {
        runCatching {
            when (profile.protocol.uppercase()) {
                "FTP", "FTPS" -> listFtpDirectory(profile, decryptedSecret, remotePath)
                "SFTP" -> listSftpDirectory(profile, decryptedSecret, remotePath)
                "SMB" -> listSmbDirectory(profile, decryptedSecret, remotePath)
                else -> throw IllegalArgumentException("Unsupported network protocol: ${profile.protocol}")
            }
        }
    }

    suspend fun downloadRemoteFile(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        remoteFile: FileReference,
        localDestinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            localDestinationFile.parentFile?.mkdirs()
            when (profile.protocol.uppercase()) {
                "FTP", "FTPS" -> downloadFtpFile(profile, decryptedSecret, remoteFile.uriOrPath, localDestinationFile)
                "SFTP" -> downloadSftpFile(profile, decryptedSecret, remoteFile.uriOrPath, localDestinationFile)
                "SMB" -> downloadSmbFile(profile, decryptedSecret, remoteFile.uriOrPath, localDestinationFile)
                else -> throw IllegalArgumentException("Unsupported protocol: ${profile.protocol}")
            }
            localDestinationFile
        }
    }

    suspend fun uploadLocalFile(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        localSourceFile: File,
        remoteParentDirPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val remoteTarget = if (remoteParentDirPath.endsWith("/")) {
                "$remoteParentDirPath${localSourceFile.name}"
            } else {
                "$remoteParentDirPath/${localSourceFile.name}"
            }
            when (profile.protocol.uppercase()) {
                "FTP", "FTPS" -> uploadFtpFile(profile, decryptedSecret, localSourceFile, remoteTarget)
                "SFTP" -> uploadSftpFile(profile, decryptedSecret, localSourceFile, remoteTarget)
                "SMB" -> uploadSmbFile(profile, decryptedSecret, localSourceFile, remoteTarget)
                else -> throw IllegalArgumentException("Unsupported protocol: ${profile.protocol}")
            }
            Unit
        }
    }

    suspend fun createRemoteDirectory(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        remoteDirPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (profile.protocol.uppercase()) {
                "FTP", "FTPS" -> withFtpClient(profile, decryptedSecret) { client ->
                    if (!client.makeDirectory(remoteDirPath)) {
                        throw IllegalStateException("FTP MKD failed: ${client.replyString}")
                    }
                }
                "SFTP" -> withSftpChannel(profile, decryptedSecret) { channel ->
                    channel.mkdir(remoteDirPath)
                }
                "SMB" -> withSmbDiskShare(profile, decryptedSecret) { share ->
                    share.mkdir(remoteDirPath.trim('/').replace('/', '\\'))
                }
                else -> throw IllegalArgumentException("Unsupported protocol: ${profile.protocol}")
            }
            Unit
        }
    }

    suspend fun deleteRemoteItem(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        remoteItem: FileReference
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (profile.protocol.uppercase()) {
                "FTP", "FTPS" -> withFtpClient(profile, decryptedSecret) { client ->
                    val ok = if (remoteItem.isDirectory) {
                        client.removeDirectory(remoteItem.uriOrPath)
                    } else {
                        client.deleteFile(remoteItem.uriOrPath)
                    }
                    if (!ok) throw IllegalStateException("FTP delete failed: ${client.replyString}")
                }
                "SFTP" -> withSftpChannel(profile, decryptedSecret) { channel ->
                    if (remoteItem.isDirectory) channel.rmdir(remoteItem.uriOrPath)
                    else channel.rm(remoteItem.uriOrPath)
                }
                "SMB" -> withSmbDiskShare(profile, decryptedSecret) { share ->
                    val smbPath = remoteItem.uriOrPath.trim('/').replace('/', '\\')
                    if (remoteItem.isDirectory) share.rmdir(smbPath, true)
                    else share.rm(smbPath)
                }
                else -> throw IllegalArgumentException("Unsupported protocol: ${profile.protocol}")
            }
            Unit
        }
    }

    suspend fun renameRemoteItem(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        remoteItem: FileReference,
        newName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val parent = remoteItem.parentUriOrPath ?: "/"
            val newPath = if (parent.endsWith("/")) "$parent$newName" else "$parent/$newName"
            when (profile.protocol.uppercase()) {
                "FTP", "FTPS" -> withFtpClient(profile, decryptedSecret) { client ->
                    if (!client.rename(remoteItem.uriOrPath, newPath)) {
                        throw IllegalStateException("FTP rename failed: ${client.replyString}")
                    }
                }
                "SFTP" -> withSftpChannel(profile, decryptedSecret) { channel ->
                    channel.rename(remoteItem.uriOrPath, newPath)
                }
                "SMB" -> withSmbDiskShare(profile, decryptedSecret) { share ->
                    val oldSmb = remoteItem.uriOrPath.trim('/').replace('/', '\\')
                    val newSmb = newPath.trim('/').replace('/', '\\')
                    share.open(
                        oldSmb,
                        EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    ).use { entry ->
                        entry.rename(newSmb, false)
                    }
                }
                else -> throw IllegalArgumentException("Unsupported protocol: ${profile.protocol}")
            }
            Unit
        }
    }

    // ---------------------------------------------------------------------------------------------
    // FTP / FTPS Implementation (Apache Commons Net)
    // ---------------------------------------------------------------------------------------------

    private fun <T> withFtpClient(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        block: (FTPClient) -> T
    ): T {
        val client: FTPClient = if (profile.protocol.equals("FTPS", ignoreCase = true)) {
            // Port 990 is the IANA implicit-FTPS port; everything else uses explicit AUTH TLS.
            FTPSClient(profile.port == 990)
        } else {
            FTPClient()
        }
        client.connectTimeout = 10_000
        client.defaultTimeout = 15_000
        client.controlEncoding = "UTF-8"
        try {
            client.connect(profile.host, profile.port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                throw IllegalStateException("FTP server refused connection: ${client.replyString}")
            }
            if (client is FTPSClient) {
                client.execPBSZ(0)
                client.execPROT("P")
            }
            val user = if (profile.isAnonymous) "anonymous" else profile.username
            val pass = if (profile.isAnonymous) "anonymous@openfile.local" else decryptedSecret
            if (!client.login(user, pass)) {
                throw SecurityException("FTP login failed for user '$user': ${client.replyString}")
            }
            if (profile.usePassiveMode) {
                client.enterLocalPassiveMode()
            } else {
                client.enterLocalActiveMode()
            }
            client.setFileType(FTP.BINARY_FILE_TYPE)
            return block(client)
        } finally {
            runCatching { if (client.isConnected) client.logout() }
            runCatching { if (client.isConnected) client.disconnect() }
        }
    }

    private fun listFtpDirectory(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        remotePath: String
    ): List<FileReference> = withFtpClient(profile, decryptedSecret) { client ->
        val normalized = remotePath.ifBlank { "/" }
        val files = client.listFiles(normalized) ?: emptyArray()
        if (files.isEmpty() && !FTPReply.isPositiveCompletion(client.replyCode)) {
            throw IllegalStateException("FTP cannot list '$normalized': ${client.replyString.trim()}")
        }
        val provider = if (profile.protocol.equals("FTPS", true)) ProviderType.FTPS else ProviderType.FTP
        files.filter { it.name != "." && it.name != ".." }.map { ftpFile ->
            val fullPath = if (normalized.endsWith("/")) "$normalized${ftpFile.name}" else "$normalized/${ftpFile.name}"
            FileReference(
                providerType = provider,
                uriOrPath = fullPath,
                displayName = ftpFile.name,
                parentUriOrPath = normalized,
                isDirectory = ftpFile.isDirectory,
                sizeBytes = if (ftpFile.isDirectory) 0L else ftpFile.size.coerceAtLeast(0L),
                lastModifiedEpochMs = ftpFile.timestamp?.timeInMillis ?: 0L
            )
        }
    }

    private fun downloadFtpFile(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        remotePath: String,
        localFile: File
    ) = withFtpClient(profile, decryptedSecret) { client ->
        localFile.outputStream().buffered().use { out ->
            if (!client.retrieveFile(remotePath, out)) {
                throw IllegalStateException("FTP download failed: ${client.replyString}")
            }
        }
    }

    private fun uploadFtpFile(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        localFile: File,
        remotePath: String
    ) = withFtpClient(profile, decryptedSecret) { client ->
        localFile.inputStream().buffered().use { input ->
            if (!client.storeFile(remotePath, input)) {
                throw IllegalStateException("FTP upload failed: ${client.replyString}")
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // SFTP Implementation (JSch with strict SHA-256 Host-Key Fingerprint Verification)
    // ---------------------------------------------------------------------------------------------

    private fun <T> withSftpChannel(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        block: (ChannelSftp) -> T
    ): T {
        val jsch = JSch()
        // If decryptedSecret looks like an OpenSSH/PEM private key, load as identity
        val isPrivateKey = decryptedSecret.contains("-----BEGIN ")
        if (isPrivateKey) {
            jsch.addIdentity(
                "openfile_key",
                decryptedSecret.toByteArray(Charsets.UTF_8),
                null,
                null
            )
        }

        val session = jsch.getSession(profile.username, profile.host, profile.port)
        if (!isPrivateKey) {
            session.setPassword(decryptedSecret)
        }

        // Strict Host-Key Verification: never set StrictHostKeyChecking=no silently
        val pinnedFingerprint = profile.expectedHostKeyFingerprint?.trim()
        session.hostKeyRepository = object : HostKeyRepository {
            override fun check(host: String, key: ByteArray): Int {
                val actualFingerprint = computeSshKeySha256Fingerprint(key)
                if (pinnedFingerprint.isNullOrBlank()) {
                    throw SshHostKeyVerificationRequiredException(
                        host = profile.host,
                        port = profile.port,
                        keyType = "SSH Host Key",
                        sha256Fingerprint = actualFingerprint,
                        mismatchWithPinned = false
                    )
                }
                return if (pinnedFingerprint.equals(actualFingerprint, ignoreCase = true)) {
                    HostKeyRepository.OK
                } else {
                    throw SshHostKeyVerificationRequiredException(
                        host = profile.host,
                        port = profile.port,
                        keyType = "SSH Host Key",
                        sha256Fingerprint = actualFingerprint,
                        mismatchWithPinned = true
                    )
                }
            }

            override fun add(hostkey: HostKey?, ui: UserInfo?) {}
            override fun remove(host: String?, type: String?) {}
            override fun remove(host: String?, type: String?, key: ByteArray?) {}
            override fun getKnownHostsRepositoryID(): String = "openfile_known_hosts"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        }

        val config = Properties().apply {
            put("StrictHostKeyChecking", "ask")
        }
        session.setConfig(config)
        session.connect(12_000)
        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect(12_000)
        try {
            return block(channel)
        } finally {
            runCatching { channel.disconnect() }
            runCatching { session.disconnect() }
        }
    }

    private fun computeSshKeySha256Fingerprint(keyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(keyBytes)
        val b64 = android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        return "SHA256:$b64"
    }

    private fun listSftpDirectory(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        remotePath: String
    ): List<FileReference> = withSftpChannel(profile, decryptedSecret) { channel ->
        val normalized = remotePath.ifBlank { runCatching { channel.home }.getOrNull() ?: "/" }
        @Suppress("UNCHECKED_CAST")
        val rawVector = channel.ls(normalized) as Vector<ChannelSftp.LsEntry>
        rawVector.filter { it.filename != "." && it.filename != ".." }.map { entry ->
            val attrs = entry.attrs
            val fullPath = if (normalized.endsWith("/")) "$normalized${entry.filename}" else "$normalized/${entry.filename}"
            // Symlinked folders report isDir=false; resolve the target so they stay browsable.
            val isDirectory = attrs.isDir ||
                (attrs.isLink && runCatching { channel.stat(fullPath).isDir }.getOrDefault(false))
            FileReference(
                providerType = ProviderType.SFTP,
                uriOrPath = fullPath,
                displayName = entry.filename,
                parentUriOrPath = normalized,
                isDirectory = isDirectory,
                sizeBytes = if (isDirectory) 0L else attrs.size,
                lastModifiedEpochMs = attrs.mTime * 1000L,
                metadata = mapOf("permissions" to attrs.permissionsString)
            )
        }
    }

    private fun downloadSftpFile(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        remotePath: String,
        localFile: File
    ) = withSftpChannel(profile, decryptedSecret) { channel ->
        localFile.outputStream().buffered().use { out ->
            channel.get(remotePath, out)
        }
    }

    private fun uploadSftpFile(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        localFile: File,
        remotePath: String
    ) = withSftpChannel(profile, decryptedSecret) { channel ->
        localFile.inputStream().buffered().use { input ->
            channel.put(input, remotePath)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // SMB2 / SMB3 Implementation (SMBJ)
    // ---------------------------------------------------------------------------------------------

    private fun <T> withSmbDiskShare(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        block: (DiskShare) -> T
    ): T {
        val shareName = profile.shareName.ifBlank {
            profile.remoteInitialPath.trim('/').substringBefore('/').ifBlank { "Public" }
        }
        val smbConfig = SmbConfig.builder()
            .withTimeout(15, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            .build()
        val client = SMBClient(smbConfig)
        try {
            val connection = client.connect(profile.host, profile.port)
            val authContext = if (profile.isAnonymous) {
                AuthenticationContext.anonymous()
            } else {
                val domain = profile.username.substringBefore('\\', "")
                val user = profile.username.substringAfter('\\')
                AuthenticationContext(user, decryptedSecret.toCharArray(), domain.ifBlank { null })
            }
            val session = connection.authenticate(authContext)
            val share = session.connectShare(shareName) as? DiskShare
                ?: throw IllegalStateException("SMB share '$shareName' is not a DiskShare.")
            try {
                return block(share)
            } finally {
                runCatching { share.close() }
                runCatching { session.close() }
                runCatching { connection.close() }
            }
        } finally {
            runCatching { client.close() }
        }
    }

    private fun listSmbDirectory(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        remotePath: String
    ): List<FileReference> = withSmbDiskShare(profile, decryptedSecret) { share ->
        val smbDir = remotePath.trim('/').replace('/', '\\')
        val entries = share.list(smbDir)
        entries.filter { it.fileName != "." && it.fileName != ".." }.map { info ->
            val isDir = (info.fileAttributes and 0x10L) != 0L // FILE_ATTRIBUTE_DIRECTORY
            val childPath = if (remotePath.isBlank() || remotePath == "/") {
                "/${info.fileName}"
            } else {
                "${remotePath.trimEnd('/')}/${info.fileName}"
            }
            FileReference(
                providerType = ProviderType.SMB,
                uriOrPath = childPath,
                displayName = info.fileName,
                parentUriOrPath = remotePath.ifBlank { "/" },
                isDirectory = isDir,
                sizeBytes = if (isDir) 0L else info.endOfFile,
                lastModifiedEpochMs = info.changeTime.toEpochMillis()
            )
        }
    }

    private fun downloadSmbFile(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        remotePath: String,
        localFile: File
    ) = withSmbDiskShare(profile, decryptedSecret) { share ->
        val smbPath = remotePath.trim('/').replace('/', '\\')
        share.openFile(
            smbPath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        ).use { remoteFile ->
            remoteFile.inputStream.use { input ->
                localFile.outputStream().buffered().use { out ->
                    input.copyTo(out)
                }
            }
        }
    }

    private fun uploadSmbFile(
        profile: NetworkProfileEntity,
        decryptedSecret: String,
        localFile: File,
        remotePath: String
    ) = withSmbDiskShare(profile, decryptedSecret) { share ->
        val smbPath = remotePath.trim('/').replace('/', '\\')
        share.openFile(
            smbPath,
            EnumSet.of(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            null
        ).use { remoteFile ->
            remoteFile.outputStream.use { out ->
                localFile.inputStream().buffered().use { input ->
                    input.copyTo(out)
                }
            }
        }
    }

    /**
     * Scans the local /24 subnet for responsive FTP (21, 2121), SFTP/SSH (22), and SMB (445) hosts.
     */
    suspend fun scanLocalNetworkForFileServers(localIp: String): List<DiscoveredLanHost> = withContext(Dispatchers.IO) {
        val prefix = localIp.substringBeforeLast('.', "")
        if (prefix.isBlank() || prefix == "127.0.0") {
            // Check localhost ports (including active OpenFile FTP Server)
            return@withContext probeHostPorts("127.0.0.1")
        }
        val results = mutableListOf<DiscoveredLanHost>()
        results.addAll(probeHostPorts(localIp))

        coroutineScope {
            val deferreds = (1..254).chunked(32).flatMap { batch ->
                batch.map { hostOctet ->
                    val candidateIp = "$prefix.$hostOctet"
                    async {
                        if (candidateIp == localIp) emptyList()
                        else probeHostPorts(candidateIp, timeoutMs = 180)
                    }
                }
            }
            deferreds.awaitAll().forEach { results.addAll(it) }
        }
        results.distinctBy { "${it.ipAddress}:${it.port}" }
    }

    private fun probeHostPorts(ip: String, timeoutMs: Int = 250): List<DiscoveredLanHost> {
        val found = mutableListOf<DiscoveredLanHost>()
        // port, protocol label, normalised protocol, friendly name
        val targetPorts = listOf(
            listOf(2121, "FTP (OpenFile / Alt)", "FTP", "OpenFile FTP"),
            listOf(21, "FTP", "FTP", "FTP Server"),
            listOf(22, "SFTP / SSH", "SFTP", "SFTP Server"),
            listOf(8022, "SFTP / SSH (Termux)", "SFTP", "SFTP Server"),
            listOf(445, "SMB2/3 Share", "SMB", "SMB Share")
        )
        for (entry in targetPorts) {
            val port = entry[0] as Int
            val label = entry[1] as String
            val protocol = entry[2] as String
            val friendly = entry[3] as String
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), timeoutMs)
                    found.add(
                        DiscoveredLanHost(
                            ipAddress = ip,
                            port = port,
                            protocolGuess = label,
                            bannerOrName = "$friendly • $ip",
                            protocol = protocol
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
        return found
    }
}
