package com.example.core.filesystem

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.core.model.FileCategory
import com.example.core.model.FileReference
import com.example.core.model.ProviderCapability
import com.example.core.model.ProviderType
import com.example.core.model.SortField
import com.example.core.model.SortOrder
import com.example.core.model.StorageVolumeInfo
import com.example.core.model.isPathInsideHiddenDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * Capability-aware interface implemented by Local, SAF, MediaStore, Archive, Network, and Cloud providers.
 */
interface VirtualFileSystemProvider {
    val providerType: ProviderType
    suspend fun listChildren(directory: FileReference, showHidden: Boolean): Result<List<FileReference>>
    suspend fun createDirectory(parent: FileReference, name: String): Result<FileReference>
    suspend fun createTextFile(parent: FileReference, name: String, content: String): Result<FileReference>
    suspend fun rename(target: FileReference, newName: String): Result<FileReference>
    suspend fun delete(target: FileReference): Result<Boolean>
    suspend fun openInputStream(target: FileReference): Result<InputStream>
    suspend fun openOutputStream(parent: FileReference, fileName: String, append: Boolean = false): Result<Pair<FileReference, OutputStream>>
    suspend fun exists(parent: FileReference, childName: String): Boolean
}

/**
 * Local POSIX/Java IO file system provider for accessible internal/external shared storage
 * and app-managed directories. Respects Android 11+ scoped-storage boundaries.
 */
class LocalFileSystemProvider(private val context: Context) : VirtualFileSystemProvider {

    override val providerType: ProviderType = ProviderType.LOCAL
    @Volatile
    private var workspaceInitialized: Boolean = false

    @Volatile
    private var cachedPrimaryRoot: File? = null

    private val appLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun hasAllFilesAccess(): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                val ext = Environment.getExternalStorageDirectory()
                ext != null && ext.exists() && ext.canRead()
            }
        }.getOrDefault(true)
    }

    fun invalidateRootCache() {
        cachedPrimaryRoot = null
    }

    /**
     * Returns the primary accessible internal root directory.
     * If shared external storage is readable and populated, returns `/storage/emulated/0`;
     * otherwise provides a seeded accessible workspace inside externalFilesDir/filesDir so
     * local file management always works out of the box even before runtime permissions.
     */
    fun getPrimaryRootDirectory(): File {
        cachedPrimaryRoot?.let { if (it.exists() && it.canRead()) return it }
        val externalRoot = Environment.getExternalStorageDirectory()
        if (externalRoot != null && externalRoot.exists()) {
            if (hasAllFilesAccess() || externalRoot.canRead()) {
                val hasStandardFolder = File(externalRoot, "Download").exists() ||
                    File(externalRoot, "Android").exists() ||
                    File(externalRoot, "DCIM").exists() ||
                    !externalRoot.list().isNullOrEmpty()
                if (hasStandardFolder) {
                    cachedPrimaryRoot = externalRoot
                    return externalRoot
                }
            }
        }
        val workspaceRoot = File(context.getExternalFilesDir(null) ?: context.filesDir, "InternalStorage")
        ensureInitialWorkspaceStructure(workspaceRoot)
        // Do NOT cache workspaceRoot in cachedPrimaryRoot so that as soon as storage permission
        // is granted, getPrimaryRootDirectory() immediately switches to real /storage/emulated/0.
        return workspaceRoot
    }

    fun getAppWorkspaceDirectory(): File {
        val workspaceRoot = File(context.getExternalFilesDir(null) ?: context.filesDir, "OpenFileWorkspace")
        if (!workspaceInitialized) {
            ensureInitialWorkspaceStructure(workspaceRoot)
            workspaceInitialized = true
        }
        return workspaceRoot
    }

    fun ensureInitialWorkspaceStructure(root: File) {
        if (!root.exists()) root.mkdirs()
        val standardFolders = listOf("Download", "Documents", "Pictures", "Music", "Movies", "Archives", "Projects")
        for (folder in standardFolders) {
            val dir = File(root, folder)
            if (!dir.exists()) dir.mkdirs()
        }
        val readme = File(File(root, "Documents"), "Welcome_to_OpenFile.md")
        if (!readme.exists()) {
            runCatching {
                readme.writeText(
                    """
                    # OpenFile File Manager
                    
                    Welcome to **OpenFile** — a privacy-first, capability-aware Android file manager.
                    
                    ## Key Capabilities
                    - **Local & SAF Storage**: Browse Internal Storage, SD cards, and USB OTG drives.
                    - **Conflict-Safe File Engine**: Copy, Cut, Paste, Rename, Trash & Restore, and SHA-256 verification.
                    - **Archive Engine**: Inspect, extract, and create ZIP, TAR, GZ, XZ, and browse 7Z archives with Zip-Slip protection.
                    - **Network & Cloud**: Built-in Foreground Wi-Fi FTP/FTPS Server, FTP/SFTP/SMB2/3 Clients, and Cloud OAuth2.
                    """.trimIndent()
                )
            }
        }
        val notesJson = File(File(root, "Projects"), "architecture_config.json")
        if (!notesJson.exists()) {
            runCatching {
                notesJson.writeText(
                    """
                    {
                      "application": "OpenFile",
                      "package": "com.openfile.manager",
                      "providers": ["LOCAL", "SAF", "MEDIA_STORE", "FTP", "SFTP", "SMB", "CLOUD"],
                      "security": {
                        "keystoreCipher": "AES/GCM/NoPadding",
                        "zipSlipGuard": true,
                        "sshHostKeyVerification": true
                      }
                    }
                    """.trimIndent()
                )
            }
        }
    }

    fun toFileReference(
        file: File,
        includeChildCount: Boolean = false,
        includeCanonicalPath: Boolean = false
    ): FileReference {
        val isDir = file.isDirectory
        val path = file.absolutePath
        val name = file.name.ifEmpty { path }
        val ext = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
        val mime = if (isDir) {
            "inode/directory"
        } else {
            val guessed = guessMimeType(ext)
            if (guessed != "application/octet-stream" || ext.isEmpty()) {
                guessed
            } else {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: guessed
            }
        }

        val isRestricted = isRestrictedAndroidDirectory(file)
        // Fast path for normal directory listings: avoid 3 extra POSIX access() syscalls per file
        if (!includeChildCount && !includeCanonicalPath && !isRestricted) {
            return FileReference(
                providerType = ProviderType.LOCAL,
                uriOrPath = path,
                displayName = name,
                parentUriOrPath = file.parent,
                isDirectory = isDir,
                sizeBytes = if (isDir) 0L else file.length(),
                lastModifiedEpochMs = file.lastModified(),
                mimeType = mime,
                extension = ext,
                isHidden = name.startsWith(".") || isPathInsideHiddenDirectory(path) || isPathInsideHiddenDirectory(file.parent),
                capabilities = FileReference.defaultCapabilitiesFor(ProviderType.LOCAL, isDir),
                metadata = if (isDir) DefaultDirMetadata else DefaultFileMetadata
            )
        }

        val canRead = file.canRead()
        val canWrite = file.canWrite()
        val perms = buildString(4) {
            append(if (isDir) 'd' else '-')
            append(if (canRead) 'r' else '-')
            append(if (canWrite) 'w' else '-')
            append(if (file.canExecute()) 'x' else '-')
        }
        val childCount = if (includeChildCount && isDir && canRead) {
            file.list()?.size?.toString()
        } else null

        val metadata = buildMap {
            put("permissions", perms)
            if (includeCanonicalPath) {
                put("canonicalPath", runCatching { file.canonicalPath }.getOrDefault(path))
            }
            if (childCount != null) put("childCount", childCount)
            if (isRestricted) {
                put("scopedRestriction", "Protected by Android 11+ Scoped Storage. Use SAF if supported.")
            }
        }

        val capabilities = if (canWrite) {
            FileReference.defaultCapabilitiesFor(ProviderType.LOCAL, isDir)
        } else {
            FileReference.readOnlyCapabilitiesFor(ProviderType.LOCAL, isDir)
        }

        return FileReference(
            providerType = ProviderType.LOCAL,
            uriOrPath = path,
            displayName = name,
            parentUriOrPath = file.parent,
            isDirectory = isDir,
            sizeBytes = if (isDir) 0L else file.length(),
            lastModifiedEpochMs = file.lastModified(),
            mimeType = mime,
            extension = ext,
            isHidden = name.startsWith(".") || isPathInsideHiddenDirectory(path) || isPathInsideHiddenDirectory(file.parent),
            capabilities = capabilities,
            metadata = metadata
        )
    }

    fun isRestrictedAndroidDirectory(file: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val normalized = file.absolutePath.replace('\\', '/').trimEnd('/')
        return normalized.endsWith("/Android/data", ignoreCase = true) ||
            normalized.contains("/Android/data/", ignoreCase = true) ||
            normalized.endsWith("/Android/obb", ignoreCase = true) ||
            normalized.contains("/Android/obb/", ignoreCase = true)
    }

    fun isTopRestrictedContainer(file: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val normalized = file.absolutePath.replace('\\', '/').trimEnd('/')
        return normalized.endsWith("/Android/data", ignoreCase = true) ||
            normalized.endsWith("/Android/obb", ignoreCase = true)
    }

    private fun tryListViaRoot(dir: File, showHidden: Boolean): List<FileReference>? {
        return runCatching {
            val suBinary = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su")
                .firstOrNull { File(it).exists() } ?: return null
            val escapedPath = dir.absolutePath.replace("'", "'\\''")
            val process = ProcessBuilder(suBinary, "-c", "ls -1Ap '$escapedPath'")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                process.destroy()
                return null
            }
            val lines = process.inputStream.bufferedReader().readLines()
            lines.mapNotNull { rawLine ->
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val isDir = trimmed.endsWith("/")
                val name = trimmed.trimEnd('/')
                if (name.isEmpty() || name == "." || name == "..") return@mapNotNull null
                if (!showHidden && name.startsWith(".")) return@mapNotNull null
                val childFile = File(dir, name)
                FileReference(
                    providerType = ProviderType.LOCAL,
                    uriOrPath = childFile.absolutePath,
                    displayName = name,
                    parentUriOrPath = dir.absolutePath,
                    isDirectory = isDir,
                    sizeBytes = if (isDir) 0L else childFile.length(),
                    lastModifiedEpochMs = childFile.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                    mimeType = if (isDir) "inode/directory" else guessMimeType(name.substringAfterLast('.', "")),
                    extension = if (isDir) "" else name.substringAfterLast('.', "").lowercase(),
                    isHidden = name.startsWith(".") || isPathInsideHiddenDirectory(childFile.absolutePath) || isPathInsideHiddenDirectory(dir.absolutePath),
                    metadata = mapOf("permissions" to if (isDir) "drwxr-xr-x" else "-rw-r--r--", "rootResolved" to "true")
                )
            }
        }.getOrNull()
    }

    private fun discoverPackageFoldersForRestrictedContainer(
        containerDir: File,
        showHidden: Boolean,
        safMountedChildNames: Set<String> = emptySet()
    ): List<FileReference> {
        runCatching {
            // Ensure our own app's external data and obb dirs are materialized on disk
            context.obbDirs
            context.getExternalFilesDirs(null)
        }
        val pm = context.packageManager
        val installedApps = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        val results = LinkedHashMap<String, FileReference>()

        for (appInfo in installedApps) {
            val pkg = appInfo.packageName ?: continue
            if (!showHidden && pkg.startsWith(".")) continue
            val isUserApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 ||
                (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val pkgDir = File(containerDir, pkg)
            val existsDirectly = runCatching { pkgDir.exists() }.getOrDefault(false)
            val isSafMounted = pkg in safMountedChildNames

            if (existsDirectly || isSafMounted || isUserApp) {
                val appLabel = appLabelCache.getOrPut(pkg) {
                    runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg)
                }
                val canListDirectly = existsDirectly && runCatching { pkgDir.list() != null }.getOrDefault(false)
                val meta = buildMap {
                    put("permissions", if (canListDirectly || isSafMounted) "drwxrwx---" else "drwx------")
                    put("appLabel", appLabel)
                    if (isSafMounted) {
                        put("safMounted", "true")
                    } else if (!canListDirectly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        put("restrictedAppFolder", "true")
                    }
                }
                results[pkg] = FileReference(
                    providerType = ProviderType.LOCAL,
                    uriOrPath = pkgDir.absolutePath,
                    displayName = pkg,
                    parentUriOrPath = containerDir.absolutePath,
                    isDirectory = true,
                    sizeBytes = 0L,
                    lastModifiedEpochMs = pkgDir.lastModified().takeIf { it > 0L } ?: 0L,
                    mimeType = "inode/directory",
                    extension = "",
                    isHidden = pkg.startsWith(".") || isPathInsideHiddenDirectory(pkgDir.absolutePath),
                    metadata = meta
                )
            }
        }
        return results.values.toList()
    }

    override suspend fun listChildren(
        directory: FileReference,
        showHidden: Boolean
    ): Result<List<FileReference>> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(directory.uriOrPath)
            val isRestricted = isRestrictedAndroidDirectory(dir)
            val isTopContainer = isTopRestrictedContainer(dir)

            if (isTopContainer && !dir.exists()) {
                runCatching { dir.mkdirs() }
            }

            if (!dir.exists() && !isRestricted) {
                throw IllegalArgumentException("Directory does not exist: ${directory.displayName}")
            }

            val rawChildren = if (dir.exists()) dir.listFiles() else null

            if (isRestricted) {
                // 1. If direct File.listFiles() returned items and it's not a top container, return them
                if (!rawChildren.isNullOrEmpty() && !isTopContainer) {
                    return@runCatching rawChildren.asSequence()
                        .filter { showHidden || !it.name.startsWith(".") }
                        .map { toFileReference(it) }
                        .toList()
                }

                // 2. Try root (su) fallback if available
                val rootItems = tryListViaRoot(dir, showHidden)
                if (!rootItems.isNullOrEmpty()) {
                    return@runCatching rootItems
                }

                // 3. If this is /Android/obb or /Android/data, merge any direct items with installed app package folders
                if (isTopContainer) {
                    val merged = LinkedHashMap<String, FileReference>()
                    rawChildren?.forEach { file ->
                        if (showHidden || !file.name.startsWith(".")) {
                            merged[file.name] = toFileReference(file)
                        }
                    }
                    val pkgFolders = discoverPackageFoldersForRestrictedContainer(dir, showHidden)
                    for (pkgRef in pkgFolders) {
                        val existing = merged[pkgRef.displayName]
                        if (existing != null) {
                            merged[pkgRef.displayName] = existing.copy(
                                metadata = existing.metadata + pkgRef.metadata
                            )
                        } else {
                            merged[pkgRef.displayName] = pkgRef
                        }
                    }
                    return@runCatching merged.values.toList()
                }

                // 4. Specific package folder inside /Android/obb/<pkg> or /Android/data/<pkg> that requires SAF grant
                if (rawChildren == null) {
                    throw SecurityException("SAF_REQUIRED:${dir.absolutePath}")
                }
            }

            val effectiveChildren = rawChildren
                ?: if (!dir.canRead()) {
                    throw SecurityException("Permission denied reading ${directory.displayName}")
                } else {
                    emptyArray()
                }
            effectiveChildren.asSequence()
                .filter { showHidden || !it.name.startsWith(".") }
                .map { toFileReference(it) }
                .toList()
        }
    }

    override suspend fun createDirectory(
        parent: FileReference,
        name: String
    ): Result<FileReference> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = sanitizeFileName(name)
            val newDir = File(parent.uriOrPath, sanitized)
            if (newDir.exists()) throw IllegalStateException("A file or folder named '$sanitized' already exists.")
            if (!newDir.mkdirs()) throw IllegalStateException("Failed to create folder '$sanitized'.")
            toFileReference(newDir)
        }
    }

    override suspend fun createTextFile(
        parent: FileReference,
        name: String,
        content: String
    ): Result<FileReference> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = sanitizeFileName(name)
            val target = File(parent.uriOrPath, sanitized)
            if (target.exists()) throw IllegalStateException("File '$sanitized' already exists.")
            target.writeText(content, Charsets.UTF_8)
            toFileReference(target)
        }
    }

    override suspend fun rename(
        target: FileReference,
        newName: String
    ): Result<FileReference> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = sanitizeFileName(newName)
            val sourceFile = File(target.uriOrPath)
            val parentFile = sourceFile.parentFile ?: throw IllegalStateException("Cannot rename root directory.")
            val destFile = File(parentFile, sanitized)
            if (destFile.exists()) throw IllegalStateException("Target '$sanitized' already exists.")
            if (!sourceFile.renameTo(destFile)) {
                throw IllegalStateException("Failed to rename '${sourceFile.name}' to '$sanitized'.")
            }
            toFileReference(destFile)
        }
    }

    /**
     * Checks if [path] resides on a secondary/removable storage volume (e.g. physical SD card or USB OTG).
     */
    fun isSecondaryStoragePath(path: String): Boolean {
        val norm = path.replace('\\', '/').trimEnd('/')
        val primary = getPrimaryRootDirectory().absolutePath.replace('\\', '/').trimEnd('/')
        if (norm.startsWith("$primary/") || norm == primary) return false
        return norm.startsWith("/storage/") || norm.startsWith("/mnt/")
    }

    override suspend fun delete(target: FileReference): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(target.uriOrPath)
            if (!file.exists()) return@runCatching true

            // 1. Direct standard file system delete (instantaneous for primary or writable secondary)
            if (file.deleteRecursively()) {
                return@runCatching true
            }

            // 2. Fallback: If on external SD card / USB OTG, attempt SAF document delete using any persisted grant
            val primaryRoot = getPrimaryRootDirectory().absolutePath
            val safRef = SafDocumentProvider(context).findGrantedSafReferenceForLocalPath(file.absolutePath, primaryRoot)
            if (safRef != null) {
                val safDeleted = runCatching {
                    val uri = Uri.parse(safRef.uriOrPath)
                    val doc = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
                    doc?.delete() ?: false
                }.getOrDefault(false)
                if (safDeleted && !file.exists()) {
                    return@runCatching true
                }
            }

            // 3. Fallback: MediaStore delete (for photos, videos, audio, downloads)
            runCatching {
                val contentUri = MediaStore.Files.getContentUri("external")
                val deletedRows = context.contentResolver.delete(
                    contentUri,
                    "${MediaStore.MediaColumns.DATA} = ?",
                    arrayOf(file.absolutePath)
                )
                if (deletedRows > 0 && !file.exists()) {
                    return@runCatching true
                }
            }

            // 4. Verify whether file was deleted
            if (file.exists()) {
                throw IOException("Unable to delete '${target.displayName}'. File is write-protected or requires SAF access on this storage.")
            }
            true
        }
    }

    override suspend fun openInputStream(target: FileReference): Result<InputStream> = withContext(Dispatchers.IO) {
        runCatching {
            FileInputStream(File(target.uriOrPath))
        }
    }

    override suspend fun openOutputStream(
        parent: FileReference,
        fileName: String,
        append: Boolean
    ): Result<Pair<FileReference, OutputStream>> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = sanitizeFileName(fileName)
            val dest = File(parent.uriOrPath, sanitized)
            dest.parentFile?.mkdirs()
            val out = FileOutputStream(dest, append)
            toFileReference(dest) to out
        }
    }

    override suspend fun exists(parent: FileReference, childName: String): Boolean = withContext(Dispatchers.IO) {
        File(parent.uriOrPath, childName).exists()
    }

    suspend fun calculateDirectorySize(dir: File): Long = withContext(Dispatchers.IO) {
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.add(child)
                } else {
                    total += child.length()
                }
            }
        }
        total
    }

    /**
     * Discovers all physical removable storage root directories (microSD cards, USB OTG drives)
     * across Android API levels and OEM ROMs using:
     * 1. [StorageManager.getStorageVolumes] (`directory`, `uuid` -> `/storage/<uuid>`, reflection `getPath`)
     * 2. [Context.getExternalFilesDirs], `obbDirs`, and `externalMediaDirs`
     * 3. Persisted SAF URI permissions (`tree/<UUID>:`) mapped to `/storage/<UUID>`
     * 4. `/proc/mounts` and `/storage` directory scan for mounted volumes (e.g. `XXXX-XXXX`)
     */
    fun discoverRemovableStorageDirectories(): List<Pair<String, File>> {
        val primaryRoot = getPrimaryRootDirectory()
        val primaryExtPath = runCatching { Environment.getExternalStorageDirectory()?.absolutePath }.getOrNull().orEmpty()
        val addedPaths = mutableSetOf(
            primaryRoot.absolutePath,
            runCatching { primaryRoot.canonicalPath }.getOrDefault(primaryRoot.absolutePath),
            primaryExtPath,
            "/storage/emulated/0",
            "/storage/emulated",
            "/storage/self"
        )
        val discovered = mutableListOf<Pair<String, File>>()

        fun tryAddVolume(label: String, candidate: File?) {
            val dir = candidate ?: return
            val absPath = dir.absolutePath.trimEnd('/')
            if (absPath.isEmpty() || absPath == "/storage" || absPath.startsWith("/storage/emulated") ||
                absPath.startsWith("/storage/self") || absPath.startsWith("/data")
            ) {
                return
            }
            val existsOrMounted = runCatching { dir.exists() || dir.isDirectory || dir.list() != null }.getOrDefault(false)
            if (!existsOrMounted) return
            val canonical = runCatching { dir.canonicalPath }.getOrDefault(absPath)
            if (addedPaths.add(absPath) && (canonical == absPath || addedPaths.add(canonical))) {
                discovered.add(label to dir)
            }
        }

        val extFilesDirs = runCatching { context.getExternalFilesDirs(null).filterNotNull() }.getOrDefault(emptyList())
        extFilesDirs.firstOrNull()?.absolutePath?.substringBefore("/Android")?.takeIf { it.isNotBlank() }?.let {
            addedPaths.add(it.trimEnd('/'))
        }
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        val smVolumes = runCatching { storageManager?.storageVolumes }.getOrNull().orEmpty()

        for (sv in smVolumes) {
            if (sv.isPrimary) continue
            val state = runCatching { sv.state }.getOrNull()
            if (state != null && state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) {
                continue
            }
            val desc = runCatching { sv.getDescription(context) }.getOrNull()?.takeIf { it.isNotBlank() } ?: "SD Card"
            val dirApi30 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { sv.directory }.getOrNull()
            } else null
            val reflectedPath = runCatching {
                sv.javaClass.getMethod("getPath").invoke(sv) as? String
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { File(it) }
            val uuidDir = sv.uuid?.takeIf { it.isNotBlank() }?.let { File("/storage/$it") }
            val fromExtFiles = extFilesDirs.drop(1).firstOrNull { f ->
                val path = f.absolutePath
                val uuid = sv.uuid
                if (uuid != null) path.contains(uuid, ignoreCase = true)
                else (!path.startsWith("/storage/emulated") && !path.startsWith("/data"))
            }?.let { File(it.absolutePath.substringBefore("/Android")) }

            val resolved = dirApi30 ?: reflectedPath ?: fromExtFiles ?: uuidDir
            tryAddVolume(desc, resolved)
        }

        // Check secondary entries (drop(1)) of getExternalFilesDirs and obbDirs for any remaining removable volumes
        val allSecondaryAppExtDirs = buildList {
            addAll(extFilesDirs.drop(1))
            addAll(runCatching { context.obbDirs.filterNotNull().drop(1) }.getOrDefault(emptyList()))
        }
        allSecondaryAppExtDirs.forEach { extDir ->
            val rootPath = extDir.absolutePath.substringBefore("/Android")
            if (rootPath.isNotBlank() && rootPath != extDir.absolutePath) {
                tryAddVolume("SD Card", File(rootPath))
            }
        }

        // Check persisted SAF URI permissions for volume UUIDs (e.g. content://com.android.externalstorage.documents/tree/1234-5678%3A)
        runCatching {
            val uuidRegex = Regex("tree/([0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}):")
            for (perm in context.contentResolver.persistedUriPermissions) {
                val decoded = Uri.decode(perm.uri.toString())
                val match = uuidRegex.find(decoded)
                if (match != null) {
                    val uuid = match.groupValues[1]
                    tryAddVolume("SD Card ($uuid)", File("/storage/$uuid"))
                }
            }
        }

        // Check /proc/mounts for mounted /storage/<volume> entries (works even when /storage itself is 0711 non-listable)
        runCatching {
            val mountsFile = File("/proc/mounts")
            if (mountsFile.canRead()) {
                mountsFile.forEachLine { line ->
                    val parts = line.split(' ')
                    if (parts.size >= 2) {
                        val mountPoint = parts[1]
                        if (mountPoint.startsWith("/storage/") && mountPoint.count { it == '/' } == 2) {
                            val volName = mountPoint.substringAfterLast('/')
                            if (volName !in IgnoredStorageDirNames) {
                                tryAddVolume("SD Card ($volName)", File(mountPoint))
                            }
                        }
                    }
                }
            }
        }

        // Direct /storage child scan if readable
        runCatching {
            File("/storage").listFiles()?.forEach { child ->
                if (child.name !in IgnoredStorageDirNames && child.isDirectory) {
                    tryAddVolume("SD Card (${child.name})", child)
                }
            }
        }

        return discovered
    }

    /**
     * Builds the File Manager + style FTP root configuration:
     * - When [configuredRootMode] is `"ALL"`, `""`, or matches legacy default primary storage when an SD card is present,
     *   exposes a virtual multi-storage root `/` with `/device` (Internal Storage) and `/sdcard` (SD Card, plus `/sdcard2` if multiple).
     * - When [configuredRootMode] is `"INTERNAL"`, exposes Internal Storage directly at `/`.
     * - When [configuredRootMode] points to a specific directory (e.g. SD card root path), exposes that directory at `/`.
     */
    fun resolveFtpServerRootAndMounts(configuredRootMode: String): Pair<File, Map<String, File>> {
        val primaryRoot = getPrimaryRootDirectory()
        val removableList = discoverRemovableStorageDirectories()
        val normalizedMode = configuredRootMode.trim()

        // Explicit single-volume Internal Storage mode
        if (normalizedMode.equals("INTERNAL", ignoreCase = true)) {
            return primaryRoot to emptyMap()
        }

        // Explicit single-volume SD Card or custom path mode (when not legacy auto-saved primaryRoot)
        if (normalizedMode.isNotBlank() &&
            !normalizedMode.equals("ALL", ignoreCase = true) &&
            normalizedMode != primaryRoot.absolutePath
        ) {
            val customDir = File(normalizedMode)
            if (customDir.exists() && customDir.isDirectory) {
                return customDir to emptyMap()
            }
        }

        // File Manager + "All Storage" Virtual Root mode (/device + /sdcard)
        val mounts = LinkedHashMap<String, File>()
        mounts["device"] = primaryRoot
        removableList.forEachIndexed { index, (_, file) ->
            val mountKey = if (index == 0) "sdcard" else "sdcard${index + 1}"
            mounts[mountKey] = file
        }
        return primaryRoot to mounts
    }

    fun getStorageVolumes(safProvider: SafDocumentProvider, persistedGrantUris: List<Pair<String, String>>): List<StorageVolumeInfo> {
        val volumes = mutableListOf<StorageVolumeInfo>()
        val primaryRoot = getPrimaryRootDirectory()
        val statFs = runCatching { StatFs(primaryRoot.absolutePath) }.getOrNull()
        val total = statFs?.totalBytes ?: (64L * 1024 * 1024 * 1024)
        val avail = statFs?.availableBytes ?: (32L * 1024 * 1024 * 1024)
        val used = (total - avail).coerceAtLeast(0L)

        // 1. Primary Internal Storage (always present)
        volumes.add(
            StorageVolumeInfo(
                id = "internal_primary",
                title = "Internal Shared Storage",
                subtitle = primaryRoot.absolutePath,
                rootReference = toFileReference(primaryRoot),
                totalBytes = total,
                usedBytes = used,
                availableBytes = avail,
                isRemovable = false
            )
        )

        // 2. Discover real physical & removable volumes (SD card, USB OTG)
        val addedPaths = mutableSetOf(primaryRoot.absolutePath)
        val removableDirs = discoverRemovableStorageDirectories()
        for ((desc, rootFile) in removableDirs) {
            if (addedPaths.add(rootFile.absolutePath)) {
                val stat = runCatching { StatFs(rootFile.absolutePath) }.getOrNull()
                val extTotal = stat?.totalBytes ?: 0L
                val extAvail = stat?.availableBytes ?: 0L
                volumes.add(
                    StorageVolumeInfo(
                        id = "storage_vol_${rootFile.name}",
                        title = desc,
                        subtitle = rootFile.absolutePath,
                        rootReference = toFileReference(rootFile),
                        totalBytes = extTotal,
                        usedBytes = (extTotal - extAvail).coerceAtLeast(0L),
                        availableBytes = extAvail,
                        isRemovable = true
                    )
                )
            }
        }

        // Only include SAF grants if they represent a separate external root drive not already covered
        for ((uriStr, label) in persistedGrantUris) {
            val decoded = Uri.decode(uriStr)
            if (decoded.contains("primary:") || decoded.contains("emulated")) continue
            val isAlreadyCovered = volumes.any { vol ->
                val segment = vol.subtitle.substringAfterLast('/').takeIf { it.isNotBlank() }
                segment != null && decoded.contains(segment)
            }
            if (isAlreadyCovered) continue
            val isDriveRoot = decoded.endsWith(":") || decoded.contains("tree/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}".toRegex())
            if (isDriveRoot && addedPaths.add(uriStr)) {
                val uri = Uri.parse(uriStr)
                val valid = safProvider.isUriPermissionValid(uri)
                val ref = safProvider.fromTreeUri(uri, label)
                volumes.add(
                    StorageVolumeInfo(
                        id = "saf_$uriStr",
                        title = label,
                        subtitle = if (valid) "SAF Removable Volume" else "SAF Permission Revoked",
                        rootReference = ref,
                        totalBytes = total,
                        usedBytes = used,
                        availableBytes = avail,
                        isRemovable = true,
                        isSafGrantRevoked = !valid
                    )
                )
            }
        }
        return volumes
    }

    companion object {
        private val DefaultDirMetadata = mapOf("permissions" to "drwxr-xr-x")
        private val DefaultFileMetadata = mapOf("permissions" to "-rw-r--r--")
        private val IgnoredStorageDirNames = setOf(
            "emulated",
            "self",
            "enc_emulated",
            "knox-emulated",
            "sdcard0",
            "container"
        )

        fun sanitizeFileName(raw: String): String {
            val trimmed = raw.trim()
            require(trimmed.isNotEmpty()) { "File name cannot be empty." }
            require(!trimmed.contains('/') && !trimmed.contains('\\') && trimmed != ".." && trimmed != ".") {
                "File name contains invalid path characters."
            }
            return trimmed
        }

        fun guessMimeType(ext: String): String = when (ext.lowercase()) {
            "txt", "md", "log", "ini", "conf", "kt", "java", "py", "js", "sh", "yaml", "yml", "c", "cpp", "h", "hpp", "rs", "go", "rb", "php", "sql", "gradle", "properties", "toml" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "csv" -> "text/csv"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "odp" -> "application/vnd.oasis.opendocument.presentation"
            "rtf" -> "application/rtf"
            "epub" -> "application/epub+zip"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "tar" -> "application/x-tar"
            "gz", "tgz" -> "application/gzip"
            "bz2", "tbz2" -> "application/x-bzip2"
            "xz", "txz" -> "application/x-xz"
            "7z" -> "application/x-7z-compressed"
            "apk", "apks", "xapk" -> "application/vnd.android.package-archive"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "heic", "heif" -> "image/heic"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "flv" -> "video/x-flv"
            "ts" -> "video/mp2t"
            "wmv" -> "video/x-ms-wmv"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "opus" -> "audio/opus"
            else -> "application/octet-stream"
        }
    }
}

/**
 * Storage Access Framework (SAF) DocumentFile provider with persisted URI permission checks
 * and revoked-grant detection.
 */
class SafDocumentProvider(private val context: Context) : VirtualFileSystemProvider {

    override val providerType: ProviderType = ProviderType.SAF

    fun isUriPermissionValid(uri: Uri): Boolean {
        val persisted = context.contentResolver.persistedUriPermissions
        val hasPersisted = persisted.any {
            it.uri == uri && it.isReadPermission
        }
        if (!hasPersisted) return false
        return runCatching {
            val doc = DocumentFile.fromTreeUri(context, uri)
            doc != null && doc.canRead()
        }.getOrDefault(false)
    }

    fun persistTreePermission(uri: Uri): String {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
        val doc = DocumentFile.fromTreeUri(context, uri)
        return doc?.name ?: uri.lastPathSegment ?: "Granted Folder"
    }

    fun fromTreeUri(uri: Uri, fallbackName: String = "SAF Folder"): FileReference {
        val doc = DocumentFile.fromTreeUri(context, uri)
        return if (doc != null) {
            toFileReference(doc, parentUri = null)
        } else {
            FileReference(
                providerType = ProviderType.SAF,
                uriOrPath = uri.toString(),
                displayName = fallbackName,
                parentUriOrPath = null,
                isDirectory = true
            )
        }
    }

    /**
     * Builds the initial Document URI for `Intent.ACTION_OPEN_DOCUMENT_TREE` (`EXTRA_INITIAL_URI`)
     * targeting a specific local directory (such as `/storage/emulated/0/Android/obb` or `/Android/obb/<pkg>`).
     */
    fun buildInitialSafUriForLocalPath(localPath: String, primaryRootPath: String): Uri? {
        return runCatching {
            val normPath = localPath.replace('\\', '/').trimEnd('/')
            val normRoot = primaryRootPath.replace('\\', '/').trimEnd('/')
            val relPath = if (normPath.startsWith("$normRoot/")) {
                normPath.removePrefix("$normRoot/")
            } else if (normPath == normRoot) {
                ""
            } else {
                return null
            }
            val docId = if (relPath.isEmpty()) "primary:" else "primary:$relPath"
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
        }.getOrNull()
    }

    /**
     * Checks if any persisted SAF tree permission covers [localPath] (either on primary storage or
     * external SD card/USB OTG) and returns a ready-to-use [FileReference] backed by [ProviderType.SAF].
     */
    fun findGrantedSafReferenceForLocalPath(localPath: String, primaryRootPath: String): FileReference? {
        return runCatching {
            val normPath = localPath.replace('\\', '/').trimEnd('/')
            val normRoot = primaryRootPath.replace('\\', '/').trimEnd('/')

            val targetDocId = when {
                normPath.startsWith("$normRoot/") -> "primary:" + normPath.removePrefix("$normRoot/")
                normPath == normRoot -> "primary:"
                normPath.startsWith("/storage/") && !normPath.startsWith("/storage/emulated") -> {
                    val sub = normPath.removePrefix("/storage/")
                    val volId = sub.substringBefore('/')
                    val rel = sub.substringAfter('/', "")
                    if (rel.isEmpty()) "$volId:" else "$volId:$rel"
                }
                normPath.startsWith("/mnt/media_rw/") -> {
                    val sub = normPath.removePrefix("/mnt/media_rw/")
                    val volId = sub.substringBefore('/')
                    val rel = sub.substringAfter('/', "")
                    if (rel.isEmpty()) "$volId:" else "$volId:$rel"
                }
                else -> null
            } ?: return null

            val persisted = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }
            for (perm in persisted) {
                val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(perm.uri) }.getOrNull() ?: continue
                val match = targetDocId.equals(treeDocId, ignoreCase = true) ||
                    targetDocId.startsWith("$treeDocId/", ignoreCase = true) ||
                    (treeDocId.endsWith(":") && targetDocId.startsWith(treeDocId, ignoreCase = true))

                if (match) {
                    val childDocUri = if (targetDocId.equals(treeDocId, ignoreCase = true)) {
                        perm.uri
                    } else {
                        DocumentsContract.buildDocumentUriUsingTree(perm.uri, targetDocId)
                    }
                    val doc = DocumentFile.fromSingleUri(context, childDocUri)
                        ?: DocumentFile.fromTreeUri(context, childDocUri)
                    if (doc != null && (doc.canRead() || doc.exists())) {
                        return toFileReference(doc, parentUri = null).copy(
                            displayName = normPath.substringAfterLast('/'),
                            metadata = mapOf("localBackingPath" to normPath, "safMounted" to "true")
                        )
                    }
                }
            }
            null
        }.getOrNull()
    }

    /**
     * Returns the set of direct child folder names under [parentLocalPath] that already have a persisted SAF tree grant.
     */
    fun findChildSafGrantsForParentLocalPath(parentLocalPath: String, primaryRootPath: String): Set<String> {
        return runCatching {
            val normPath = parentLocalPath.replace('\\', '/').trimEnd('/')
            val normRoot = primaryRootPath.replace('\\', '/').trimEnd('/')
            if (!normPath.startsWith("$normRoot/")) return emptySet()
            val relPath = normPath.removePrefix("$normRoot/")
            val parentDocIdPrefix = "primary:$relPath/"

            val result = mutableSetOf<String>()
            val persisted = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }
            for (perm in persisted) {
                val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(perm.uri) }.getOrNull() ?: continue
                if (treeDocId.equals("primary:$relPath", ignoreCase = true)) {
                    result.add("*")
                } else if (treeDocId.startsWith(parentDocIdPrefix, ignoreCase = true)) {
                    val remainder = treeDocId.substring(parentDocIdPrefix.length)
                    val childName = remainder.substringBefore('/')
                    if (childName.isNotEmpty()) result.add(childName)
                }
            }
            result
        }.getOrDefault(emptySet())
    }

    private fun toFileReference(doc: DocumentFile, parentUri: String?): FileReference {
        val name = doc.name ?: doc.uri.lastPathSegment ?: "Document"
        val isDir = doc.isDirectory
        val ext = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
        val caps = if (doc.canWrite()) {
            FileReference.defaultCapabilitiesFor(ProviderType.SAF, isDir)
        } else {
            FileReference.readOnlyCapabilitiesFor(ProviderType.SAF, isDir)
        }
        return FileReference(
            providerType = ProviderType.SAF,
            uriOrPath = doc.uri.toString(),
            displayName = name,
            parentUriOrPath = parentUri,
            isDirectory = isDir,
            sizeBytes = if (isDir) 0L else doc.length(),
            lastModifiedEpochMs = doc.lastModified(),
            mimeType = doc.type ?: LocalFileSystemProvider.guessMimeType(ext),
            extension = ext,
            isHidden = name.startsWith(".") || isPathInsideHiddenDirectory(doc.uri.toString()) || isPathInsideHiddenDirectory(parentUri),
            capabilities = caps
        )
    }

    private fun resolveDocumentFile(ref: FileReference): DocumentFile {
        val uri = Uri.parse(ref.uriOrPath)
        val treeDoc = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
        if (treeDoc != null) return treeDoc
        val singleDoc = DocumentFile.fromSingleUri(context, uri)
        return singleDoc ?: throw SecurityException("Unable to resolve SAF document: ${ref.displayName}. Permission may have been revoked.")
    }

    override suspend fun listChildren(
        directory: FileReference,
        showHidden: Boolean
    ): Result<List<FileReference>> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = resolveDocumentFile(directory)
            if (!doc.canRead()) {
                throw SecurityException("SAF permission revoked for '${directory.displayName}'. Please re-grant folder access.")
            }
            doc.listFiles()
                .asSequence()
                .filter { showHidden || !(it.name?.startsWith(".") ?: false) }
                .map { toFileReference(it, parentUri = directory.uriOrPath) }
                .toList()
        }
    }

    override suspend fun createDirectory(
        parent: FileReference,
        name: String
    ): Result<FileReference> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = LocalFileSystemProvider.sanitizeFileName(name)
            val parentDoc = resolveDocumentFile(parent)
            val created = parentDoc.createDirectory(sanitized)
                ?: throw IllegalStateException("SAF provider refused to create directory '$sanitized'.")
            toFileReference(created, parentUri = parent.uriOrPath)
        }
    }

    override suspend fun createTextFile(
        parent: FileReference,
        name: String,
        content: String
    ): Result<FileReference> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = LocalFileSystemProvider.sanitizeFileName(name)
            val parentDoc = resolveDocumentFile(parent)
            val created = parentDoc.createFile("text/plain", sanitized)
                ?: throw IllegalStateException("SAF provider refused to create file '$sanitized'.")
            context.contentResolver.openOutputStream(created.uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            toFileReference(created, parentUri = parent.uriOrPath)
        }
    }

    override suspend fun rename(
        target: FileReference,
        newName: String
    ): Result<FileReference> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = LocalFileSystemProvider.sanitizeFileName(newName)
            val targetUri = Uri.parse(target.uriOrPath)
            val renamedUri = DocumentsContract.renameDocument(context.contentResolver, targetUri, sanitized)
                ?: throw IllegalStateException("SAF provider does not support renaming '${target.displayName}'.")
            val doc = DocumentFile.fromSingleUri(context, renamedUri)
                ?: throw IllegalStateException("Failed to resolve renamed SAF document.")
            toFileReference(doc, parentUri = target.parentUriOrPath)
        }
    }

    override suspend fun delete(target: FileReference): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = resolveDocumentFile(target)
            doc.delete()
        }
    }

    override suspend fun openInputStream(target: FileReference): Result<InputStream> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(target.uriOrPath)
            context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open input stream for SAF URI: ${target.displayName}")
        }
    }

    override suspend fun openOutputStream(
        parent: FileReference,
        fileName: String,
        append: Boolean
    ): Result<Pair<FileReference, OutputStream>> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = LocalFileSystemProvider.sanitizeFileName(fileName)
            val parentDoc = resolveDocumentFile(parent)
            val existing = parentDoc.findFile(sanitized)
            val targetDoc = existing ?: parentDoc.createFile(
                LocalFileSystemProvider.guessMimeType(sanitized.substringAfterLast('.', "")),
                sanitized
            ) ?: throw IllegalStateException("Failed to create SAF document '$sanitized'.")
            val mode = if (append) "wa" else "w"
            val stream = context.contentResolver.openOutputStream(targetDoc.uri, mode)
                ?: throw IllegalStateException("Failed to open SAF output stream for '$sanitized'.")
            toFileReference(targetDoc, parentUri = parent.uriOrPath) to stream
        }
    }

    override suspend fun exists(parent: FileReference, childName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val parentDoc = resolveDocumentFile(parent)
            parentDoc.findFile(childName) != null
        }.getOrDefault(false)
    }
}

/**
 * Queries Android's MediaStore content resolver for fast category indexing
 * (Images, Videos, Audio, Documents, Archives, Large Files, Recent Files)
 * and blends results with accessible local directories.
 */
class MediaStoreProvider(
    private val context: Context,
    private val localProvider: LocalFileSystemProvider
) {

    suspend fun queryByCategory(
        category: FileCategory,
        largeFileThresholdBytes: Long = 25L * 1024 * 1024
    ): List<FileReference> = withContext(Dispatchers.IO) {
        val results = LinkedHashMap<String, FileReference>()

        // 1. Query Android MediaStore content index for all categories
        runCatching {
            when (category) {
                FileCategory.IMAGES -> {
                    queryMediaCollection(
                        collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        sink = results
                    )
                }
                FileCategory.VIDEOS -> {
                    queryMediaCollection(
                        collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        sink = results
                    )
                }
                FileCategory.AUDIO -> {
                    queryMediaCollection(
                        collectionUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        sink = results
                    )
                }
                FileCategory.DOCUMENTS -> {
                    val docExtensions = FileCategory.DOCUMENTS.extensions
                    val extClauses = docExtensions.joinToString(" OR ") { "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.$it'" }
                    val selection = "($extClauses) OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/pdf' OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE 'text/%' OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/msword' OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/vnd.%'"
                    queryMediaCollection(
                        collectionUri = MediaStore.Files.getContentUri("external"),
                        selection = selection,
                        sink = results
                    )
                }
                FileCategory.ARCHIVES -> {
                    val archiveExtensions = FileCategory.ARCHIVES.extensions + setOf("rar")
                    val extClauses = archiveExtensions.joinToString(" OR ") { "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.$it'" }
                    val selection = "($extClauses) OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/zip' OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/x-%'"
                    queryMediaCollection(
                        collectionUri = MediaStore.Files.getContentUri("external"),
                        selection = selection,
                        sink = results
                    )
                }
                FileCategory.LARGE_FILES -> {
                    val selection = "${MediaStore.MediaColumns.SIZE} >= ?"
                    val selectionArgs = arrayOf(largeFileThresholdBytes.toString())
                    queryMediaCollection(
                        collectionUri = MediaStore.Files.getContentUri("external"),
                        selection = selection,
                        selectionArgs = selectionArgs,
                        sink = results
                    )
                }
                FileCategory.RECENT_FILES -> {
                    val selection = "${MediaStore.MediaColumns.SIZE} > 0"
                    queryMediaCollection(
                        collectionUri = MediaStore.Files.getContentUri("external"),
                        selection = selection,
                        sink = results
                    )
                }
                FileCategory.APPLICATIONS -> {
                    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.apk' OR ${MediaStore.MediaColumns.MIME_TYPE} = 'application/vnd.android.package-archive'"
                    queryMediaCollection(
                        collectionUri = MediaStore.Files.getContentUri("external"),
                        selection = selection,
                        sink = results
                    )
                }
                FileCategory.DOWNLOADS -> {}
            }
        }

        // 2. Supplement with a single shared pass over accessible directories on primary storage
        // so files not yet indexed by MediaStore (APKs, Archives, Documents, Downloads, etc.)
        // are discovered without walking the filesystem 8 separate times.
        runCatching {
            val primaryRoot = localProvider.getPrimaryRootDirectory()
            val supplementalFiles = if (category == FileCategory.DOWNLOADS) {
                val dlDir = File(primaryRoot, "Download")
                dlDir.listFiles()
                    ?.asSequence()
                    ?.filter { !it.isDirectory && !it.name.startsWith(".") }
                    ?.map { localProvider.toFileReference(it) }
                    ?.toList()
                    .orEmpty()
            } else {
                getOrScanSupplementalFiles(primaryRoot)
            }

            for (ref in supplementalFiles) {
                if (results.containsKey(ref.uriOrPath)) continue
                val matches = when (category) {
                    FileCategory.LARGE_FILES -> ref.sizeBytes >= largeFileThresholdBytes
                    FileCategory.RECENT_FILES -> !ref.isDirectory
                    FileCategory.DOWNLOADS -> true
                    else -> ref.extension in category.extensions
                }
                if (matches) {
                    results[ref.uriOrPath] = ref
                }
            }
        }

        when (category) {
            FileCategory.LARGE_FILES -> results.values.sortedByDescending { it.sizeBytes }
            FileCategory.RECENT_FILES -> results.values.sortedByDescending { it.lastModifiedEpochMs }
            FileCategory.IMAGES -> results.values.sortedByDescending { it.lastModifiedEpochMs }
            FileCategory.AUDIO, FileCategory.VIDEOS, FileCategory.DOCUMENTS -> results.values.sortedBy { it.displayName.lowercase() }
            else -> results.values.sortedByDescending { it.lastModifiedEpochMs }
        }
    }

    @Volatile
    private var cachedSupplementalFiles: List<FileReference> = emptyList()

    @Volatile
    private var cachedSupplementalAtMs: Long = 0L

    @Volatile
    private var cachedSupplementalRootPath: String = ""

    @Synchronized
    fun invalidateSupplementalCache() {
        cachedSupplementalAtMs = 0L
        cachedSupplementalFiles = emptyList()
    }

    @Synchronized
    private fun getOrScanSupplementalFiles(primaryRoot: File): List<FileReference> {
        val now = System.currentTimeMillis()
        val rootPath = primaryRoot.absolutePath
        if (rootPath == cachedSupplementalRootPath &&
            now - cachedSupplementalAtMs < 30_000L &&
            cachedSupplementalFiles.isNotEmpty()
        ) {
            return cachedSupplementalFiles
        }

        val collected = ArrayList<FileReference>(256)
        val topDirs = primaryRoot.listFiles()?.filter { dir ->
            dir.isDirectory &&
                !dir.name.startsWith(".") &&
                !dir.name.equals("Android", ignoreCase = true)
        }.orEmpty()

        primaryRoot.listFiles()?.forEach { file ->
            if (!file.isDirectory && !file.name.startsWith(".")) {
                collected.add(localProvider.toFileReference(file))
            }
        }

        fun scanDirRecursive(dir: File, depth: Int) {
            if (depth > 3 || collected.size >= 1200) return
            val children = dir.listFiles() ?: return
            for (file in children) {
                if (file.name.startsWith(".")) continue
                if (file.isDirectory) {
                    if (!file.name.equals("data", ignoreCase = true) && !file.name.equals("obb", ignoreCase = true)) {
                        scanDirRecursive(file, depth + 1)
                    }
                } else {
                    collected.add(localProvider.toFileReference(file))
                    if (collected.size >= 1200) return
                }
            }
        }

        for (folder in topDirs) {
            scanDirRecursive(folder, 1)
            if (collected.size >= 1200) break
        }

        cachedSupplementalFiles = collected
        cachedSupplementalAtMs = System.currentTimeMillis()
        cachedSupplementalRootPath = rootPath
        return collected
    }

    private fun queryMediaCollection(
        collectionUri: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sink: MutableMap<String, FileReference>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        context.contentResolver.query(collectionUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

            var count = 0
            while (cursor.moveToNext() && count < 1000) {
                count++
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Media_$id"
                val size = cursor.getLong(sizeCol)
                val dateSec = cursor.getLong(dateCol)
                val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                val rawPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                val ext = name.substringAfterLast('.', "").lowercase()

                if (!rawPath.isNullOrBlank() && rawPath.startsWith("/")) {
                    val parentPath = rawPath.substringBeforeLast('/', "").ifEmpty { null }
                    sink[rawPath] = FileReference(
                        providerType = ProviderType.LOCAL,
                        uriOrPath = rawPath,
                        displayName = name,
                        parentUriOrPath = parentPath,
                        isDirectory = false,
                        sizeBytes = size,
                        lastModifiedEpochMs = dateSec * 1000L,
                        mimeType = mime,
                        extension = ext,
                        isHidden = name.startsWith(".") || isPathInsideHiddenDirectory(rawPath) || isPathInsideHiddenDirectory(parentPath)
                    )
                } else {
                    val contentUri = ContentUris.withAppendedId(collectionUri, id)
                    sink[contentUri.toString()] = FileReference(
                        providerType = ProviderType.MEDIA_STORE,
                        uriOrPath = contentUri.toString(),
                        displayName = name,
                        parentUriOrPath = null,
                        isDirectory = false,
                        sizeBytes = size,
                        lastModifiedEpochMs = dateSec * 1000L,
                        mimeType = mime,
                        extension = ext,
                        isHidden = name.startsWith(".") || isPathInsideHiddenDirectory(contentUri.toString())
                    )
                }
            }
        }
    }

    suspend fun queryCategoryCounts(
        largeFileThresholdBytes: Long = 25L * 1024 * 1024
    ): Map<FileCategory, Int> = withContext(Dispatchers.IO) {
        val counts = mutableMapOf<FileCategory, Int>()
        runCatching {
            val downloadDir = File(localProvider.getPrimaryRootDirectory(), "Download")
            counts[FileCategory.DOWNLOADS] = downloadDir.list()?.count { !it.startsWith(".") } ?: 0
        }
        runCatching {
            counts[FileCategory.IMAGES] = countCursor(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            counts[FileCategory.VIDEOS] = countCursor(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            counts[FileCategory.AUDIO] = countCursor(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)

            val docExtensions = FileCategory.DOCUMENTS.extensions
            val extClauses = docExtensions.joinToString(" OR ") { "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.$it'" }
            val docSelection = "($extClauses) OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/pdf' OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE 'text/%' OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/msword' OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/vnd.%'"
            counts[FileCategory.DOCUMENTS] = countCursor(MediaStore.Files.getContentUri("external"), docSelection)

            val archiveExtensions = FileCategory.ARCHIVES.extensions + setOf("rar")
            val archClauses = archiveExtensions.joinToString(" OR ") { "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '%.$it'" }
            val archSelection = "($archClauses) OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/zip' OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE 'application/x-%'"
            counts[FileCategory.ARCHIVES] = countCursor(MediaStore.Files.getContentUri("external"), archSelection)

            counts[FileCategory.LARGE_FILES] = countCursor(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.MediaColumns.SIZE} >= ?",
                arrayOf(largeFileThresholdBytes.toString())
            )

            val fourteenDaysAgoSec = (System.currentTimeMillis() - 14L * 24 * 3600 * 1000) / 1000L
            counts[FileCategory.RECENT_FILES] = countCursor(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.MediaColumns.DATE_MODIFIED} >= ?",
                arrayOf(fourteenDaysAgoSec.toString())
            )
        }
        runCatching {
            val userApps = context.packageManager.getInstalledApplications(0).count {
                (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (it.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }
            counts[FileCategory.APPLICATIONS] = userApps
        }
        counts
    }

    private fun countCursor(
        uri: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): Int {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                selectionArgs,
                null
            )?.use { it.count } ?: 0
        }.getOrDefault(0)
    }
}

/**
 * Pure utility for sorting and filtering [FileReference] collections.
 * Directories are always grouped before regular files, matching KDE Dolphin & File Manager + conventions.
 */
object FileSortAndFilterEngine {

    fun sortFiles(
        files: List<FileReference>,
        field: SortField,
        order: SortOrder
    ): List<FileReference> {
        if (files.size <= 1) return files
        val comparator = Comparator<FileReference> { a, b ->
            if (a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }
            val rawCompare = when (field) {
                SortField.NAME -> a.displayName.compareTo(b.displayName, ignoreCase = true)
                SortField.SIZE -> a.sizeBytes.compareTo(b.sizeBytes).let {
                    if (it == 0) a.displayName.compareTo(b.displayName, ignoreCase = true) else it
                }
                SortField.DATE -> a.lastModifiedEpochMs.compareTo(b.lastModifiedEpochMs).let {
                    if (it == 0) a.displayName.compareTo(b.displayName, ignoreCase = true) else it
                }
                SortField.TYPE -> a.extension.compareTo(b.extension, ignoreCase = true).let {
                    if (it == 0) a.displayName.compareTo(b.displayName, ignoreCase = true) else it
                }
            }
            if (order == SortOrder.ASCENDING) rawCompare else -rawCompare
        }
        return files.sortedWith(comparator)
    }

    fun filterFiles(
        files: List<FileReference>,
        query: String = "",
        extensionFilter: String? = null,
        categoryFilter: FileCategory? = null,
        minSizeBytes: Long? = null,
        maxSizeBytes: Long? = null,
        modifiedAfterEpochMs: Long? = null,
        showHidden: Boolean = true
    ): List<FileReference> {
        val trimmedQuery = query.trim()
        val trimmedExt = extensionFilter?.trim()?.removePrefix(".")?.lowercase()
        return files.filter { file ->
            if (!showHidden && file.isHidden) return@filter false
            if (trimmedQuery.isNotEmpty() && !file.displayName.contains(trimmedQuery, ignoreCase = true)) {
                return@filter false
            }
            if (!trimmedExt.isNullOrEmpty() && file.extension != trimmedExt) {
                return@filter false
            }
            if (categoryFilter != null && !file.isDirectory && file.extension !in categoryFilter.extensions) {
                return@filter false
            }
            if (minSizeBytes != null && !file.isDirectory && file.sizeBytes < minSizeBytes) {
                return@filter false
            }
            if (maxSizeBytes != null && !file.isDirectory && file.sizeBytes > maxSizeBytes) {
                return@filter false
            }
            if (modifiedAfterEpochMs != null && file.lastModifiedEpochMs < modifiedAfterEpochMs) {
                return@filter false
            }
            true
        }
    }
}
