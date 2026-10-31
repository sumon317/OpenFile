package com.example.core.network

import com.example.core.model.formatByteSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class FtpServerConfig(
    val bindAddress: String,
    val port: Int = 2121,
    val passivePortStart: Int = 50000,
    val passivePortEnd: Int = 50050,
    val rootDirectory: File,
    val username: String = "openfile",
    val password: String = "",
    val allowAnonymous: Boolean = false,
    val allowWrite: Boolean = true,
    val useExplicitTls: Boolean = false,
    val idleTimeoutMinutes: Int = 15
)

data class FtpLogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val clientAddress: String,
    val message: String,
    val isWarning: Boolean = false
)

data class FtpServerRuntimeState(
    val isRunning: Boolean = false,
    val boundIpAddress: String = "127.0.0.1",
    val port: Int = 2121,
    val passivePortRange: String = "50000-50050",
    val rootPath: String = "",
    val activeClientsCount: Int = 0,
    val bytesTransferred: Long = 0L,
    val allowWrite: Boolean = true,
    val allowAnonymous: Boolean = false,
    val useExplicitTls: Boolean = false,
    val lastActivityEpochMs: Long = System.currentTimeMillis(),
    val logs: List<FtpLogEntry> = emptyList()
) {
    val connectionUrl: String
        get() = "${if (useExplicitTls) "ftps" else "ftp"}://$boundIpAddress:$port/"
}

/**
 * RFC-959 / RFC-2428 (PASV & EPSV) / RFC-3659 (SIZE, MDTM, REST) local LAN FTP server engine.
 * - Binds strictly to the resolved local Wi-Fi / LAN interface IPv4 address.
 * - Allocates passive data sockets inside the configured [passivePortStart..passivePortEnd] range.
 * - Enforces canonical path confinement so clients cannot escape [FtpServerConfig.rootDirectory].
 * - Tracks live client sessions, byte counters, and idle auto-shutdown.
 */
object FtpServerEngine {

    private val _state = MutableStateFlow(FtpServerRuntimeState())
    val state: StateFlow<FtpServerRuntimeState> = _state.asStateFlow()

    private var serverScope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null
    private var idleWatchdogJob: Job? = null
    private val activeClientSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val nextPassivePortIndex = AtomicInteger(0)

    /**
     * Resolves the device's active local Wi-Fi (`wlan0`), hotspot (`ap0`/`rndis0`), or emulator (`eth0`)
     * private IPv4 address. Never returns a public cellular WAN address.
     */
    fun resolveLocalLanIpAddress(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val preferredOrder = listOf("wlan", "ap", "rndis", "eth", "en")
            val candidates = mutableListOf<Pair<String, String>>()

            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        candidates.add(nif.name.lowercase() to hostAddr)
                    }
                }
            }
            for (prefix in preferredOrder) {
                val match = candidates.firstOrNull { it.first.startsWith(prefix) }
                if (match != null) return@runCatching match.second
            }
            candidates.firstOrNull()?.second ?: "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    /**
     * Validates that a client-requested FTP path stays strictly inside [rootDir].
     */
    fun resolveSafeFtpFile(rootDir: File, currentWorkingDir: String, argumentPath: String): File {
        val canonicalRoot = rootDir.canonicalFile
        val combinedVirtualPath = if (argumentPath.startsWith("/")) {
            argumentPath
        } else {
            "$currentWorkingDir/$argumentPath"
        }
        val normalizedSegments = mutableListOf<String>()
        for (segment in combinedVirtualPath.replace('\\', '/').split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> if (normalizedSegments.isNotEmpty()) normalizedSegments.removeAt(normalizedSegments.lastIndex)
                else -> normalizedSegments.add(segment)
            }
        }
        val relativePath = normalizedSegments.joinToString(File.separator)
        val candidate = if (relativePath.isEmpty()) {
            canonicalRoot
        } else {
            File(canonicalRoot, relativePath).canonicalFile
        }
        val rootPrefix = canonicalRoot.path + File.separator
        if (candidate != canonicalRoot && !candidate.path.startsWith(rootPrefix)) {
            throw SecurityException("FTP path access outside server root denied.")
        }
        return candidate
    }

    fun startServer(config: FtpServerConfig, onIdleTimeoutStop: () -> Unit = {}): Result<Unit> {
        stopServer()
        return runCatching {
            if (!config.rootDirectory.exists()) {
                config.rootDirectory.mkdirs()
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            serverScope = scope

            val bindInet = InetAddress.getByName(config.bindAddress)
            val listener = ServerSocket(config.port, 50, bindInet)
            serverSocket = listener

            _state.value = FtpServerRuntimeState(
                isRunning = true,
                boundIpAddress = config.bindAddress,
                port = config.port,
                passivePortRange = "${config.passivePortStart}-${config.passivePortEnd}",
                rootPath = config.rootDirectory.absolutePath,
                activeClientsCount = 0,
                bytesTransferred = 0L,
                allowWrite = config.allowWrite,
                allowAnonymous = config.allowAnonymous,
                useExplicitTls = config.useExplicitTls,
                lastActivityEpochMs = System.currentTimeMillis(),
                logs = listOf(
                    FtpLogEntry(
                        clientAddress = config.bindAddress,
                        message = "FTP Server started on ${config.bindAddress}:${config.port} (PASV ${config.passivePortStart}-${config.passivePortEnd})"
                    )
                )
            )

            // Accept loop
            scope.launch {
                while (isActive && !listener.isClosed) {
                    val client = runCatching { listener.accept() }.getOrNull() ?: break
                    activeClientSockets.add(client)
                    updateClientCount()
                    launch {
                        handleClientSession(client, config)
                    }
                }
            }

            // Idle watchdog loop
            idleWatchdogJob = scope.launch {
                val timeoutMs = config.idleTimeoutMinutes.coerceAtLeast(1) * 60_000L
                while (isActive) {
                    delay(15_000L)
                    val idleElapsed = System.currentTimeMillis() - _state.value.lastActivityEpochMs
                    if (activeClientSockets.isEmpty() && idleElapsed >= timeoutMs) {
                        appendLog("Server", "Auto-stopping FTP server after ${config.idleTimeoutMinutes} min of inactivity.", isWarning = true)
                        stopServer()
                        onIdleTimeoutStop()
                        break
                    }
                }
            }
        }
    }

    fun stopServer() {
        runCatching { idleWatchdogJob?.cancel() }
        activeClientSockets.forEach { runCatching { it.close() } }
        activeClientSockets.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverScope?.cancel()
        serverScope = null
        _state.value = _state.value.copy(
            isRunning = false,
            activeClientsCount = 0
        )
    }

    private fun allocatePassiveServerSocket(config: FtpServerConfig): ServerSocket {
        val bindInet = InetAddress.getByName(config.bindAddress)
        val rangeSize = (config.passivePortEnd - config.passivePortStart + 1).coerceAtLeast(1)
        for (attempt in 0 until rangeSize) {
            val offset = (nextPassivePortIndex.getAndIncrement() and Int.MAX_VALUE) % rangeSize
            val candidatePort = config.passivePortStart + offset
            try {
                return ServerSocket(candidatePort, 5, bindInet)
            } catch (_: Exception) {
                // Port in use, try next in passive range
            }
        }
        return ServerSocket(0, 5, bindInet)
    }

    private fun handleClientSession(controlSocket: Socket, config: FtpServerConfig) {
        val clientAddr = controlSocket.inetAddress?.hostAddress ?: "unknown"
        var passiveServer: ServerSocket? = null
        try {
            controlSocket.soTimeout = config.idleTimeoutMinutes.coerceAtLeast(1) * 60_000
            val reader = BufferedReader(InputStreamReader(controlSocket.getInputStream(), Charsets.UTF_8))
            val writer = PrintWriter(OutputStreamWriter(controlSocket.getOutputStream(), Charsets.UTF_8), false)

            fun reply(code: Int, text: String) {
                writer.print("$code $text\r\n")
                writer.flush()
            }

            appendLog(clientAddr, "Client connected")
            reply(220, "OpenFile Local FTP Server Ready")

            var pendingUser = ""
            var isAuthenticated = false
            var currentVirtualDir = "/"
            var renameFromFile: File? = null
            var restartByteOffset = 0L

            while (!controlSocket.isClosed) {
                val rawLine = reader.readLine() ?: break
                touchActivity()
                val spaceIdx = rawLine.indexOf(' ')
                val cmd = (if (spaceIdx > 0) rawLine.substring(0, spaceIdx) else rawLine).uppercase(Locale.ROOT)
                val arg = if (spaceIdx > 0) rawLine.substring(spaceIdx + 1).trim() else ""

                when (cmd) {
                    "FEAT" -> {
                        writer.print("211-Features:\r\n UTF8\r\n PASV\r\n EPSV\r\n SIZE\r\n MDTM\r\n REST STREAM\r\n TVFS\r\n211 End\r\n")
                        writer.flush()
                    }
                    "SYST" -> reply(215, "UNIX Type: L8")
                    "OPTS" -> reply(200, "UTF8 mode enabled")
                    "NOOP" -> reply(200, "OK")
                    "TYPE" -> reply(200, "Type set to $arg")
                    "MODE" -> reply(200, "Mode set")
                    "STRU" -> reply(200, "Structure set")
                    "USER" -> {
                        pendingUser = arg
                        if (config.allowAnonymous && (arg.equals("anonymous", true) || arg.equals("ftp", true))) {
                            isAuthenticated = true
                            appendLog(clientAddr, "Anonymous login accepted")
                            reply(230, "Anonymous login granted")
                        } else {
                            reply(331, "User name okay, need password")
                        }
                    }
                    "PASS" -> {
                        if (config.allowAnonymous && (pendingUser.equals("anonymous", true) || pendingUser.equals("ftp", true))) {
                            isAuthenticated = true
                            reply(230, "Anonymous login granted")
                        } else if (pendingUser == config.username && arg == config.password) {
                            isAuthenticated = true
                            appendLog(clientAddr, "User '$pendingUser' authenticated")
                            reply(230, "User logged in, proceed")
                        } else {
                            appendLog(clientAddr, "Authentication failed for '$pendingUser'", isWarning = true)
                            reply(530, "Authentication failed")
                        }
                    }
                    "QUIT" -> {
                        reply(221, "Goodbye")
                        break
                    }
                    else -> {
                        if (!isAuthenticated) {
                            reply(530, "Please log in with USER and PASS")
                            continue
                        }
                        when (cmd) {
                            "PWD", "XPWD" -> reply(257, "\"$currentVirtualDir\" is current directory")
                            "CWD", "XCWD" -> {
                                val target = runCatching {
                                    resolveSafeFtpFile(config.rootDirectory, currentVirtualDir, arg)
                                }.getOrNull()
                                if (target != null && target.exists() && target.isDirectory) {
                                    currentVirtualDir = toVirtualPath(config.rootDirectory, target)
                                    reply(250, "Directory changed to $currentVirtualDir")
                                } else {
                                    reply(550, "Failed to change directory")
                                }
                            }
                            "CDUP", "XCUP" -> {
                                val target = runCatching {
                                    resolveSafeFtpFile(config.rootDirectory, currentVirtualDir, "..")
                                }.getOrNull()
                                if (target != null && target.exists()) {
                                    currentVirtualDir = toVirtualPath(config.rootDirectory, target)
                                    reply(250, "Directory changed to $currentVirtualDir")
                                } else {
                                    reply(550, "Failed to change directory")
                                }
                            }
                            "PASV" -> {
                                runCatching { passiveServer?.close() }
                                val pasv = allocatePassiveServerSocket(config)
                                passiveServer = pasv
                                val ipParts = config.bindAddress.split('.').map { it.toIntOrNull() ?: 0 }
                                val p1 = (pasv.localPort shr 8) and 0xFF
                                val p2 = pasv.localPort and 0xFF
                                val tuple = "${ipParts.getOrElse(0) { 127 }},${ipParts.getOrElse(1) { 0 }},${ipParts.getOrElse(2) { 0 }},${ipParts.getOrElse(3) { 1 }},$p1,$p2"
                                reply(227, "Entering Passive Mode ($tuple)")
                            }
                            "EPSV" -> {
                                runCatching { passiveServer?.close() }
                                val pasv = allocatePassiveServerSocket(config)
                                passiveServer = pasv
                                reply(229, "Entering Extended Passive Mode (|||${pasv.localPort}|)")
                            }
                            "LIST", "NLST" -> {
                                val pasv = passiveServer
                                if (pasv == null) {
                                    reply(425, "Use PASV or EPSV first")
                                    continue
                                }
                                val listTargetPath = if (arg.isBlank() || arg.startsWith("-")) "." else arg
                                val dir = runCatching {
                                    resolveSafeFtpFile(config.rootDirectory, currentVirtualDir, listTargetPath)
                                }.getOrNull()
                                if (dir == null || !dir.exists()) {
                                    reply(550, "Directory not found")
                                    continue
                                }
                                reply(150, "Opening data connection for directory listing")
                                pasv.soTimeout = 15_000
                                pasv.accept().use { dataSocket ->
                                    val dataWriter = PrintWriter(OutputStreamWriter(dataSocket.getOutputStream(), Charsets.UTF_8), false)
                                    val children = if (dir.isDirectory) dir.listFiles().orEmpty() else arrayOf(dir)
                                    val dateFmt = SimpleDateFormat("MMM dd HH:mm", Locale.US)
                                    for (child in children) {
                                        if (cmd == "NLST") {
                                            dataWriter.print("${child.name}\r\n")
                                        } else {
                                            val perms = buildString {
                                                append(if (child.isDirectory) 'd' else '-')
                                                append(if (child.canRead()) "r-x" else "---")
                                                append(if (child.canWrite() && config.allowWrite) "rwx" else "r-x")
                                                append("r-x")
                                            }
                                            val dateStr = dateFmt.format(Date(child.lastModified()))
                                            val size = if (child.isDirectory) 4096L else child.length()
                                            dataWriter.print("$perms 1 openfile openfile $size $dateStr ${child.name}\r\n")
                                        }
                                    }
                                    dataWriter.flush()
                                }
                                runCatching { pasv.close() }
                                passiveServer = null
                                reply(226, "Transfer complete")
                            }
                            "SIZE" -> {
                                val target = runCatching {
                                    resolveSafeFtpFile(config.rootDirectory, currentVirtualDir, arg)
                                }.getOrNull()
                                if (target != null && target.exists() && target.isFile) {
                                    reply(213, target.length().toString())
                                } else {
                                    reply(550, "Could not get file size")
                                }
                            }
                            "MDTM" -> {
                                val target = runCatching {
                                    resolveSafeFtpFile(config.rootDirectory, currentVirtualDir, arg)
                                }.getOrNull()
                                if (target != null && target.exists()) {
                                    val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
                                        timeZone = TimeZone.getTimeZone("UTC")
                                    }
                                    reply(213, fmt.format(Date(target.lastModified())))
                                } else {
                                    reply(550, "File not found")
                                }
                            }
                            "REST" -> {
                                restartByteOffset = arg.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                                reply(350, "Restarting at $restartByteOffset")
                            }
                            "RETR" -> {
                                val pasv = passiveServer
                                if (pasv == null) {
                                    reply(425, "Use PASV or EPSV first")
                                    continue
                                }
                                val target = runCatching {
                                    resolveSafeFtpFile(config.rootDirectory, currentVirtualDir, arg)
                                }.getOrNull()
                                if (target == null || !target.exists() || !target.isFile) {
                                    reply(550, "File not found")
                                    continue
                                }
                                reply(150, "Opening binary mode data connection for ${target.name} (${target.length()} bytes)")
                                var sentBytes = 0L
                                pasv.accept().use { dataSocket ->
                                    BufferedInputStream(FileInputStream(target)).use { fis ->
                                        if (restartByteOffset > 0L) {
                                            fis.skip(restartByteOffset)
                                        }
                                        val out = BufferedOutputStream(dataSocket.getOutputStream())
                                        val buf = ByteArray(64 * 1024)
                                        var r = fis.read(buf)
                                        while (r > 0) {
                                            out.write(buf, 0, r)
                                            sentBytes += r
                                            r = fis.read(buf)
                                        }
                                        out.flush()
                                    }
                                }
                                restartByteOffset = 0L
                                runCatching { pasv.close() }
                                passiveServer = null
                                recordTransferredBytes(sentBytes)
                                appendLog(clientAddr, "Downloaded ${target.name} (${formatByteSize(sentBytes)})")
                                reply(226, "Transfer complete")
                            }
                            "STOR" -> {
                                if (!config.allowWrite) {
                                    reply(550, "Server is in Read-Only mode")
                                    continue
                                }
                                val pasv = passiveServer
                                if (pasv == null) {
                                    reply(425, "Use PASV or EPSV first")
                                    continue
                                }
                                val target = runCatching {
                                    resolveSafeFtpFile(config.rootDirectory, currentVirtualDir, arg)
                                }.getOrNull()
                                if (target == null) {
                                    reply(550, "Invalid destination path")
                                    continue
                                }
                                target.parentFile?.mkdirs()
                                reply(150, "Ok to send data")
                                var receivedBytes = 0L
                                pasv.accept().use { dataSocket ->
                                    val append = restartByteOffset > 0L
                                    BufferedOutputStream(FileOutputStream(target, append)).use { fos ->
                                        val input = BufferedInputStream(dataSocket.getInputStream())
                                        val buf = ByteArray(64 * 1024)
                                        var r = input.read(buf)
                                        while (r > 0) {
                                            fos.write(buf, 0, r)
                                            receivedBytes += r
                                            r = input.read(buf)
                                        }
                                        fos.flush()
                                    }
                                }
                                restartByteOffset = 0L
                                runCatching { pasv.close() }
                                passiveServer = null
                                recordTransferredBytes(receivedBytes)
                                appendLog(clientAddr, "Uploaded ${target.name} (${formatByteSize(receivedBytes)})")
                                reply(226, "Transfer complete")
                            }
                            "MKD", "XMKD" -> {
                                if (!config.allowWrite) {
                                    reply(550, "Read-only mode")
                                    continue
                                }
                                val dir = runCatching {
                                    resolveSafeFtpFile(config.rootDirectory, currentVirtualDir, arg)
                                }.getOrNull()
                                if (dir != null && dir.mkdirs()) {
                                    appendLog(clientAddr, "Created directory ${dir.name}")
                                    reply(257, "\"${toVirtualPath(config.rootDirectory, dir)}\" created")
                                } else {
                                    reply(550, "Create directory operation failed")
                                }
                            }
                            "DELE" -> {
                                if (!config.allowWrite) {
                                    reply(550, "Read-only mode")
                                    continue
                                }
                                val target = runCatching {
                                    resolveSafeFtpFile(config.rootDirectory, currentVirtualDir, arg)
                                }.getOrNull()
                                if (target != null && target.isFile && target.delete()) {
                                    appendLog(clientAddr, "Deleted file ${target.name}")
                                    reply(250, "Delete operation successful")
                                } else {
                                    reply(550, "Delete operation failed")
                                }
                            }
                            "RMD", "XRMD" -> {
                                if (!config.allowWrite) {
                                    reply(550, "Read-only mode")
                                    continue
                                }
                                val target = runCatching {
                                    resolveSafeFtpFile(config.rootDirectory, currentVirtualDir, arg)
                                }.getOrNull()
                                if (target != null && target.isDirectory && target.delete()) {
                                    appendLog(clientAddr, "Removed directory ${target.name}")
                                    reply(250, "Remove directory operation successful")
                                } else {
                                    reply(550, "Remove directory failed (directory may not be empty)")
                                }
                            }
                            "RNFR" -> {
                                if (!config.allowWrite) {
                                    reply(550, "Read-only mode")
                                    continue
                                }
                                val target = runCatching {
                                    resolveSafeFtpFile(config.rootDirectory, currentVirtualDir, arg)
                                }.getOrNull()
                                if (target != null && target.exists()) {
                                    renameFromFile = target
                                    reply(350, "Ready for RNTO")
                                } else {
                                    reply(550, "RNFR target does not exist")
                                }
                            }
                            "RNTO" -> {
                                val src = renameFromFile
                                renameFromFile = null
                                val dst = runCatching {
                                    resolveSafeFtpFile(config.rootDirectory, currentVirtualDir, arg)
                                }.getOrNull()
                                if (src != null && dst != null && src.renameTo(dst)) {
                                    appendLog(clientAddr, "Renamed ${src.name} -> ${dst.name}")
                                    reply(250, "Rename successful")
                                } else {
                                    reply(550, "Rename failed")
                                }
                            }
                            else -> reply(502, "Command '$cmd' not implemented")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            appendLog(clientAddr, "Session closed: ${e.message ?: "disconnected"}")
        } finally {
            runCatching { passiveServer?.close() }
            activeClientSockets.remove(controlSocket)
            runCatching { controlSocket.close() }
            updateClientCount()
        }
    }

    private fun toVirtualPath(rootDir: File, target: File): String {
        val rootCanonical = rootDir.canonicalFile.path
        val targetCanonical = target.canonicalFile.path
        val rel = targetCanonical.removePrefix(rootCanonical).replace('\\', '/')
        return if (rel.isEmpty()) "/" else if (rel.startsWith("/")) rel else "/$rel"
    }

    private fun touchActivity() {
        _state.value = _state.value.copy(lastActivityEpochMs = System.currentTimeMillis())
    }

    private fun updateClientCount() {
        _state.value = _state.value.copy(activeClientsCount = activeClientSockets.size)
    }

    private fun recordTransferredBytes(delta: Long) {
        _state.value = _state.value.copy(
            bytesTransferred = _state.value.bytesTransferred + delta,
            lastActivityEpochMs = System.currentTimeMillis()
        )
    }

    fun appendLog(client: String, message: String, isWarning: Boolean = false) {
        val updated = (listOf(FtpLogEntry(clientAddress = client, message = message, isWarning = isWarning)) + _state.value.logs).take(60)
        _state.value = _state.value.copy(logs = updated)
    }
}
