package com.example.core.filesystem

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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

    /**
     * Returns the primary accessible internal root directory.
     * If shared external storage is readable/writable, returns `/storage/emulated/0`;
     * otherwise provides a seeded accessible workspace inside externalFilesDir/filesDir so
     * local file management always works out of the box even before runtime permissions.
     */
    fun getPrimaryRootDirectory(): File {
        val externalRoot = Environment.getExternalStorageDirectory()
        if (externalRoot != null && externalRoot.exists() && externalRoot.canRead() && !externalRoot.list().isNullOrEmpty()) {
            return externalRoot
        }
        val workspaceRoot = File(context.getExternalFilesDir(null) ?: context.filesDir, "InternalStorage")
        ensureInitialWorkspaceStructure(workspaceRoot)
        return workspaceRoot
    }

    fun getAppWorkspaceDirectory(): File {
        val workspaceRoot = File(context.getExternalFilesDir(null) ?: context.filesDir, "OpenFileWorkspace")
        ensureInitialWorkspaceStructure(workspaceRoot)
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
        val name = file.name.ifEmpty { file.absolutePath }
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
                put("canonicalPath", runCatching { file.canonicalPath }.getOrDefault(file.absolutePath))
            }
            if (childCount != null) put("childCount", childCount)
            if (isRestrictedAndroidDirectory(file)) {
                put("scopedRestriction", "Protected by Android 11+ Scoped Storage. Use SAF if supported.")
            }
        }

        val defaultCaps = FileReference.defaultCapabilitiesFor(ProviderType.LOCAL, isDir)
        val capabilities = if (canWrite) {
            defaultCaps
        } else {
            defaultCaps.toMutableSet().apply {
                remove(ProviderCapability.STREAM_WRITE)
                remove(ProviderCapability.CREATE_DIR)
                remove(ProviderCapability.CREATE_FILE)
                remove(ProviderCapability.DELETE)
                remove(ProviderCapability.ATOMIC_RENAME)
            }
        }

        return FileReference(
            providerType = ProviderType.LOCAL,
            uriOrPath = file.absolutePath,
            displayName = name,
            parentUriOrPath = file.parentFile?.absolutePath,
            isDirectory = isDir,
            sizeBytes = if (isDir) 0L else file.length(),
            lastModifiedEpochMs = file.lastModified(),
            mimeType = mime,
            extension = ext,
            isHidden = name.startsWith("."),
            capabilities = capabilities,
            metadata = metadata
        )
    }

    fun isRestrictedAndroidDirectory(file: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val normalized = file.absolutePath.replace('\\', '/')
        return normalized.endsWith("/Android/data") ||
            normalized.contains("/Android/data/") ||
            normalized.endsWith("/Android/obb") ||
            normalized.contains("/Android/obb/")
    }

    override suspend fun listChildren(
        directory: FileReference,
        showHidden: Boolean
    ): Result<List<FileReference>> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(directory.uriOrPath)
            if (!dir.exists()) throw IllegalArgumentException("Directory does not exist: ${directory.displayName}")
            if (isRestrictedAndroidDirectory(dir) && !dir.canRead()) {
                throw SecurityException("Android 11+ restricts direct access to ${dir.name}. Use Storage Access Framework (SAF) where supported.")
            }
            val rawChildren = dir.listFiles()
                ?: if (!dir.canRead()) {
                    throw SecurityException("Permission denied reading ${directory.displayName}")
                } else {
                    emptyArray()
                }
            rawChildren.asSequence()
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

    override suspend fun delete(target: FileReference): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(target.uriOrPath)
            if (!file.exists()) return@runCatching true
            file.deleteRecursively()
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

    fun getStorageVolumes(safProvider: SafDocumentProvider, persistedGrantUris: List<Pair<String, String>>): List<StorageVolumeInfo> {
        val volumes = mutableListOf<StorageVolumeInfo>()
        val primaryRoot = getPrimaryRootDirectory()
        val statFs = runCatching { StatFs(primaryRoot.absolutePath) }.getOrNull()
        val total = statFs?.totalBytes ?: (64L * 1024 * 1024 * 1024)
        val avail = statFs?.availableBytes ?: (32L * 1024 * 1024 * 1024)
        val used = (total - avail).coerceAtLeast(0L)

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

        val appWorkspace = getAppWorkspaceDirectory()
        if (appWorkspace.absolutePath != primaryRoot.absolutePath) {
            volumes.add(
                StorageVolumeInfo(
                    id = "app_workspace",
                    title = "OpenFile Sandbox Workspace",
                    subtitle = "Always-accessible local storage",
                    rootReference = toFileReference(appWorkspace),
                    totalBytes = total,
                    usedBytes = used,
                    availableBytes = avail,
                    isRemovable = false
                )
            )
        }

        // Check removable SD / USB volumes via getExternalFilesDirs
        val extDirs = context.getExternalFilesDirs(null)
        extDirs.drop(1).filterNotNull().forEachIndexed { index, extDir ->
            val stat = runCatching { StatFs(extDir.absolutePath) }.getOrNull()
            val extTotal = stat?.totalBytes ?: 0L
            val extAvail = stat?.availableBytes ?: 0L
            volumes.add(
                StorageVolumeInfo(
                    id = "external_vol_$index",
                    title = "External Media / SD Card #${index + 1}",
                    subtitle = extDir.absolutePath,
                    rootReference = toFileReference(extDir),
                    totalBytes = extTotal,
                    usedBytes = (extTotal - extAvail).coerceAtLeast(0L),
                    availableBytes = extAvail,
                    isRemovable = true
                )
            )
        }

        // Include SAF persisted DocumentTree grants (including revoked detection)
        for ((uriStr, label) in persistedGrantUris) {
            val uri = Uri.parse(uriStr)
            val valid = safProvider.isUriPermissionValid(uri)
            val ref = safProvider.fromTreeUri(uri, label)
            volumes.add(
                StorageVolumeInfo(
                    id = "saf_$uriStr",
                    title = label,
                    subtitle = if (valid) "SAF Document Tree" else "SAF Permission Revoked — Tap to Re-grant",
                    rootReference = ref,
                    totalBytes = total,
                    usedBytes = used,
                    availableBytes = avail,
                    isRemovable = true,
                    isSafGrantRevoked = !valid
                )
            )
        }
        return volumes
    }

    companion object {
        fun sanitizeFileName(raw: String): String {
            val trimmed = raw.trim()
            require(trimmed.isNotEmpty()) { "File name cannot be empty." }
            require(!trimmed.contains('/') && !trimmed.contains('\\') && trimmed != ".." && trimmed != ".") {
                "File name contains invalid path characters."
            }
            return trimmed
        }

        fun guessMimeType(ext: String): String = when (ext.lowercase()) {
            "txt", "md", "log", "ini", "conf", "kt", "java", "py", "js", "ts", "sh", "yaml", "yml" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "csv" -> "text/csv"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz", "tgz" -> "application/gzip"
            "xz", "txz" -> "application/x-xz"
            "7z" -> "application/x-7z-compressed"
            "apk" -> "application/vnd.android.package-archive"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
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

    private fun toFileReference(doc: DocumentFile, parentUri: String?): FileReference {
        val name = doc.name ?: doc.uri.lastPathSegment ?: "Document"
        val isDir = doc.isDirectory
        val ext = if (isDir) "" else name.substringAfterLast('.', "").lowercase()
        val caps = FileReference.defaultCapabilitiesFor(ProviderType.SAF, isDir).toMutableSet()
        if (!doc.canWrite()) {
            caps.remove(ProviderCapability.STREAM_WRITE)
            caps.remove(ProviderCapability.CREATE_DIR)
            caps.remove(ProviderCapability.CREATE_FILE)
            caps.remove(ProviderCapability.DELETE)
            caps.remove(ProviderCapability.ATOMIC_RENAME)
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
            isHidden = name.startsWith("."),
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
 * (Images, Videos, Audio) and blends results with accessible local directories.
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

        // 1. Query MediaStore for Images, Videos, Audio when permissions allow
        runCatching {
            when (category) {
                FileCategory.IMAGES -> queryMediaCollection(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    results
                )
                FileCategory.VIDEOS -> queryMediaCollection(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    results
                )
                FileCategory.AUDIO -> queryMediaCollection(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    results
                )
                else -> {}
            }
        }

        // 2. Also scan accessible local roots so files inside the local workspace/storage are always included
        val roots = listOfNotNull(
            localProvider.getPrimaryRootDirectory(),
            localProvider.getAppWorkspaceDirectory()
        ).distinctBy { it.absolutePath }

        for (root in roots) {
            if (category == FileCategory.DOWNLOADS) {
                val dl = File(root, "Download")
                if (dl.exists()) {
                    dl.listFiles()?.forEach { f ->
                        val ref = localProvider.toFileReference(f)
                        results[ref.uriOrPath] = ref
                    }
                }
                continue
            }
            val stack = ArrayDeque<File>()
            stack.add(root)
            var visited = 0
            while (stack.isNotEmpty() && visited < 5000) {
                val curr = stack.removeLast()
                val children = curr.listFiles() ?: continue
                for (child in children) {
                    visited++
                    if (child.isDirectory) {
                        if (!child.name.startsWith(".")) stack.add(child)
                    } else {
                        val ref = localProvider.toFileReference(child)
                        val matches = when (category) {
                            FileCategory.LARGE_FILES -> ref.sizeBytes >= largeFileThresholdBytes
                            FileCategory.RECENT_FILES -> (System.currentTimeMillis() - ref.lastModifiedEpochMs) <= 14L * 24 * 3600 * 1000
                            else -> ref.extension in category.extensions
                        }
                        if (matches) {
                            results[ref.uriOrPath] = ref
                        }
                    }
                }
            }
        }

        when (category) {
            FileCategory.LARGE_FILES -> results.values.sortedByDescending { it.sizeBytes }
            FileCategory.RECENT_FILES -> results.values.sortedByDescending { it.lastModifiedEpochMs }
            else -> results.values.sortedByDescending { it.lastModifiedEpochMs }
        }
    }

    private fun queryMediaCollection(collectionUri: Uri, sink: MutableMap<String, FileReference>) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATA
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        context.contentResolver.query(collectionUri, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

            var count = 0
            while (cursor.moveToNext() && count < 500) {
                count++
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Media_$id"
                val size = cursor.getLong(sizeCol)
                val dateSec = cursor.getLong(dateCol)
                val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                val rawPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                val rawFile = rawPath?.let { File(it) }

                if (rawFile != null && rawFile.exists() && rawFile.canRead()) {
                    val ref = localProvider.toFileReference(rawFile)
                    sink[ref.uriOrPath] = ref
                } else {
                    val contentUri = ContentUris.withAppendedId(collectionUri, id)
                    val ext = name.substringAfterLast('.', "").lowercase()
                    sink[contentUri.toString()] = FileReference(
                        providerType = ProviderType.MEDIA_STORE,
                        uriOrPath = contentUri.toString(),
                        displayName = name,
                        parentUriOrPath = null,
                        isDirectory = false,
                        sizeBytes = size,
                        lastModifiedEpochMs = dateSec * 1000L,
                        mimeType = mime,
                        extension = ext
                    )
                }
            }
        }
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
        val comparator = Comparator<FileReference> { a, b ->
            if (a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }
            val rawCompare = when (field) {
                SortField.NAME -> a.displayName.lowercase().compareTo(b.displayName.lowercase())
                SortField.SIZE -> a.sizeBytes.compareTo(b.sizeBytes).let {
                    if (it == 0) a.displayName.lowercase().compareTo(b.displayName.lowercase()) else it
                }
                SortField.DATE -> a.lastModifiedEpochMs.compareTo(b.lastModifiedEpochMs).let {
                    if (it == 0) a.displayName.lowercase().compareTo(b.displayName.lowercase()) else it
                }
                SortField.TYPE -> a.extension.compareTo(b.extension).let {
                    if (it == 0) a.displayName.lowercase().compareTo(b.displayName.lowercase()) else it
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
        val trimmedQuery = query.trim().lowercase()
        val trimmedExt = extensionFilter?.trim()?.removePrefix(".")?.lowercase()
        return files.filter { file ->
            if (!showHidden && file.isHidden) return@filter false
            if (trimmedQuery.isNotEmpty() && !file.displayName.lowercase().contains(trimmedQuery)) {
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
