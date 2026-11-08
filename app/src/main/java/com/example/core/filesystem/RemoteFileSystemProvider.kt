package com.example.core.filesystem

import android.content.Context
import com.example.core.database.NetworkProfileEntity
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.example.core.network.RemoteNetworkClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * [VirtualFileSystemProvider] backed by a saved FTP / FTPS / SFTP / SMB profile so the Transfer Queue
 * can copy, move and delete between remote servers and local storage.
 *
 * Streams are bridged through a private cache directory: reads download to a temp file first, writes
 * are uploaded to the server when the returned [OutputStream] is closed.
 */
class RemoteFileSystemProvider(
    private val context: Context,
    private val profile: NetworkProfileEntity,
    private val secret: String
) : VirtualFileSystemProvider {

    override val providerType: ProviderType = when (profile.protocol.uppercase()) {
        "FTPS" -> ProviderType.FTPS
        "SFTP" -> ProviderType.SFTP
        "SMB" -> ProviderType.SMB
        else -> ProviderType.FTP
    }

    private val profileMetadata = mapOf(RemoteNetworkClients.REMOTE_PROFILE_ID_KEY to profile.id.toString())

    init {
        // Remove temp transfer files orphaned by a crash or process kill (older than 24h).
        runCatching {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            File(context.cacheDir, "remote_xfer").listFiles()
                ?.filter { it.lastModified() < cutoff }
                ?.forEach { it.deleteRecursively() }
        }
    }

    private fun join(parent: String, name: String): String =
        if (parent.endsWith("/")) "$parent$name" else "$parent/$name"

    private fun newTempDir(): File =
        File(context.cacheDir, "remote_xfer/${UUID.randomUUID()}").apply { mkdirs() }

    private fun childRef(parent: FileReference, name: String, isDirectory: Boolean, size: Long = 0L) = FileReference(
        providerType = providerType,
        uriOrPath = join(parent.uriOrPath, name),
        displayName = name,
        parentUriOrPath = parent.uriOrPath,
        isDirectory = isDirectory,
        sizeBytes = size,
        lastModifiedEpochMs = System.currentTimeMillis(),
        metadata = profileMetadata
    )

    override suspend fun listChildren(directory: FileReference, showHidden: Boolean): Result<List<FileReference>> =
        RemoteNetworkClients.listRemoteDirectory(profile, secret, directory.uriOrPath).map { items ->
            items
                .map { it.copy(metadata = it.metadata + profileMetadata) }
                .filter { showHidden || !it.isHidden }
        }

    override suspend fun createDirectory(parent: FileReference, name: String): Result<FileReference> {
        // Merge into an existing folder instead of failing when it is already there.
        val existing = listChildren(parent, showHidden = true).getOrNull()
            ?.firstOrNull { it.displayName == name && it.isDirectory }
        if (existing != null) return Result.success(existing)
        return RemoteNetworkClients.createRemoteDirectory(profile, secret, join(parent.uriOrPath, name))
            .map { childRef(parent, name, isDirectory = true) }
    }

    override suspend fun createTextFile(parent: FileReference, name: String, content: String): Result<FileReference> =
        withContext(Dispatchers.IO) {
            val tempDir = newTempDir()
            try {
                val temp = File(tempDir, name).apply { writeText(content) }
                RemoteNetworkClients.uploadLocalFile(profile, secret, temp, parent.uriOrPath)
                    .map { childRef(parent, name, isDirectory = false, size = temp.length()) }
            } finally {
                tempDir.deleteRecursively()
            }
        }

    override suspend fun rename(target: FileReference, newName: String): Result<FileReference> =
        RemoteNetworkClients.renameRemoteItem(profile, secret, target, newName).map {
            val parent = target.parentUriOrPath ?: "/"
            target.copy(
                uriOrPath = join(parent, newName),
                displayName = newName,
                extension = newName.substringAfterLast('.', "").lowercase()
            )
        }

    override suspend fun delete(target: FileReference): Result<Boolean> = runCatching {
        deleteRecursive(target)
        true
    }

    private suspend fun deleteRecursive(target: FileReference) {
        if (target.isDirectory) {
            // FTP/SFTP refuse to remove non-empty folders, so empty them first.
            listChildren(target, showHidden = true).getOrDefault(emptyList()).forEach { deleteRecursive(it) }
        }
        RemoteNetworkClients.deleteRemoteItem(profile, secret, target).getOrThrow()
    }

    override suspend fun openInputStream(target: FileReference): Result<InputStream> = withContext(Dispatchers.IO) {
        runCatching {
            RemoteNetworkClients.openRemoteInputStream(profile, secret, target.uriOrPath)
        }.recoverCatching {
            // Graceful fallback to temp-file download if streaming is unsupported by remote host
            val tempDir = newTempDir()
            val temp = File(tempDir, target.displayName)
            RemoteNetworkClients.downloadRemoteFile(profile, secret, target, temp).getOrThrow()
            object : FileInputStream(temp) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        tempDir.deleteRecursively()
                    }
                }
            }
        }
    }

    override suspend fun openOutputStream(
        parent: FileReference,
        fileName: String,
        append: Boolean
    ): Result<Pair<FileReference, OutputStream>> = withContext(Dispatchers.IO) {
        val targetPath = join(parent.uriOrPath, fileName)
        val ref = childRef(parent, fileName, isDirectory = false)
        runCatching {
            val stream = RemoteNetworkClients.openRemoteOutputStream(profile, secret, targetPath)
            ref to stream
        }.recoverCatching {
            // Graceful fallback to temp-file upload if streaming is unsupported
            val tempDir = newTempDir()
            val temp = File(tempDir, fileName)
            val stream = object : FileOutputStream(temp) {
                private var closed = false
                override fun close() {
                    if (closed) return
                    closed = true
                    try {
                        super.close()
                        runBlocking {
                            RemoteNetworkClients.uploadLocalFile(profile, secret, temp, parent.uriOrPath).getOrThrow()
                        }
                    } finally {
                        tempDir.deleteRecursively()
                    }
                }
            }
            ref to stream
        }
    }

    override suspend fun exists(parent: FileReference, childName: String): Boolean =
        listChildren(parent, showHidden = true).getOrNull()?.any { it.displayName == childName } == true
}
