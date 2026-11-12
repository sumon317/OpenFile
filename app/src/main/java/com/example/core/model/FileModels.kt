package com.example.core.model

import androidx.compose.runtime.Immutable
import java.text.DecimalFormat

/**
 * Identifies the underlying storage or protocol provider for a [FileReference].
 */
enum class ProviderType(val label: String, val isRemote: Boolean) {
    LOCAL("Local Storage", false),
    SAF("SAF Document Tree", false),
    MEDIA_STORE("MediaStore Index", false),
    ARCHIVE_VIRTUAL("Archive Container", false),
    FTP("FTP Server", true),
    FTPS("FTPS (TLS)", true),
    SFTP("SFTP (SSH)", true),
    SMB("SMB2/3 Share", true)
}

/**
 * Capability flags advertised by a [ProviderType] and individual [FileReference] items.
 * Prevents the UI and transfer engine from assuming every backend behaves like POSIX local storage.
 */
enum class ProviderCapability {
    LIST_CHILDREN,
    RANDOM_ACCESS_READ,
    STREAM_READ,
    STREAM_WRITE,
    CREATE_DIR,
    CREATE_FILE,
    ATOMIC_RENAME,
    INTRA_PROVIDER_MOVE,
    DELETE,
    TRASH_IN_PLACE,
    FAST_DIRECTORY_SIZE,
    RESUME_TRANSFER,
    CHECKSUM_CALCULATION,
    POSIX_METADATA,
    ARCHIVE_EXTRACT,
    REMOTE_TRASH
}

/**
 * Structured, capability-aware reference to any file or directory across Local, SAF, MediaStore,
 * Archive, or Network (FTP/FTPS/SFTP/SMB) providers.
 */
data class FileReference(
    val providerType: ProviderType,
    val uriOrPath: String,
    val displayName: String,
    val parentUriOrPath: String?,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val lastModifiedEpochMs: Long = 0L,
    val mimeType: String = if (isDirectory) "inode/directory" else "application/octet-stream",
    val extension: String = displayName.substringAfterLast('.', "").lowercase(),
    val isHidden: Boolean = displayName.startsWith(".") || isPathInsideHiddenDirectory(uriOrPath) || isPathInsideHiddenDirectory(parentUriOrPath),
    val capabilities: Set<ProviderCapability> = defaultCapabilitiesFor(providerType, isDirectory),
    val metadata: Map<String, String> = emptyMap()
) {
    val formattedSize: String by lazy(LazyThreadSafetyMode.NONE) {
        if (isDirectory) {
            val childCount = metadata["childCount"]
            if (childCount != null) "$childCount items" else "Folder"
        } else {
            formatByteSize(sizeBytes)
        }
    }

    val permissionsString: String by lazy(LazyThreadSafetyMode.NONE) {
        metadata["permissions"] ?: if (isDirectory) "drwxr-xr-x" else "-rw-r--r--"
    }

    fun matchesMedia(other: FileReference?): Boolean {
        if (other == null) return false
        if (this.uriOrPath == other.uriOrPath) return true
        val thisLocal = this.metadata["localBackingPath"] ?: if (this.providerType == ProviderType.LOCAL) this.uriOrPath else null
        val otherLocal = other.metadata["localBackingPath"] ?: if (other.providerType == ProviderType.LOCAL) other.uriOrPath else null
        if (thisLocal != null && otherLocal != null && thisLocal == otherLocal) return true
        return this.displayName.equals(other.displayName, ignoreCase = true) && this.sizeBytes == other.sizeBytes
    }

    companion object {
        private val LOCAL_FILE_CAPS: Set<ProviderCapability> = setOf(
            ProviderCapability.STREAM_READ,
            ProviderCapability.STREAM_WRITE,
            ProviderCapability.RANDOM_ACCESS_READ,
            ProviderCapability.ATOMIC_RENAME,
            ProviderCapability.INTRA_PROVIDER_MOVE,
            ProviderCapability.DELETE,
            ProviderCapability.TRASH_IN_PLACE,
            ProviderCapability.RESUME_TRANSFER,
            ProviderCapability.CHECKSUM_CALCULATION,
            ProviderCapability.POSIX_METADATA
        )
        private val LOCAL_DIR_CAPS: Set<ProviderCapability> = LOCAL_FILE_CAPS + setOf(
            ProviderCapability.LIST_CHILDREN,
            ProviderCapability.CREATE_DIR,
            ProviderCapability.CREATE_FILE,
            ProviderCapability.FAST_DIRECTORY_SIZE
        )
        private val LOCAL_READONLY_FILE_CAPS: Set<ProviderCapability> = setOf(
            ProviderCapability.STREAM_READ,
            ProviderCapability.RANDOM_ACCESS_READ,
            ProviderCapability.INTRA_PROVIDER_MOVE,
            ProviderCapability.TRASH_IN_PLACE,
            ProviderCapability.RESUME_TRANSFER,
            ProviderCapability.CHECKSUM_CALCULATION,
            ProviderCapability.POSIX_METADATA
        )
        private val LOCAL_READONLY_DIR_CAPS: Set<ProviderCapability> = LOCAL_READONLY_FILE_CAPS + setOf(
            ProviderCapability.LIST_CHILDREN,
            ProviderCapability.FAST_DIRECTORY_SIZE
        )

        private val SAF_FILE_CAPS: Set<ProviderCapability> = setOf(
            ProviderCapability.STREAM_READ,
            ProviderCapability.STREAM_WRITE,
            ProviderCapability.ATOMIC_RENAME,
            ProviderCapability.DELETE,
            ProviderCapability.CHECKSUM_CALCULATION
        )
        private val SAF_DIR_CAPS: Set<ProviderCapability> = SAF_FILE_CAPS + setOf(
            ProviderCapability.LIST_CHILDREN,
            ProviderCapability.CREATE_DIR,
            ProviderCapability.CREATE_FILE
        )
        private val SAF_READONLY_FILE_CAPS: Set<ProviderCapability> = setOf(
            ProviderCapability.STREAM_READ,
            ProviderCapability.CHECKSUM_CALCULATION
        )
        private val SAF_READONLY_DIR_CAPS: Set<ProviderCapability> = SAF_READONLY_FILE_CAPS + setOf(
            ProviderCapability.LIST_CHILDREN
        )

        private val MEDIA_STORE_CAPS: Set<ProviderCapability> = setOf(
            ProviderCapability.STREAM_READ,
            ProviderCapability.DELETE,
            ProviderCapability.CHECKSUM_CALCULATION
        )

        private val ARCHIVE_FILE_CAPS: Set<ProviderCapability> = setOf(
            ProviderCapability.STREAM_READ,
            ProviderCapability.ARCHIVE_EXTRACT
        )
        private val ARCHIVE_DIR_CAPS: Set<ProviderCapability> = ARCHIVE_FILE_CAPS + setOf(
            ProviderCapability.LIST_CHILDREN
        )

        private val NETWORK_FILE_CAPS: Set<ProviderCapability> = setOf(
            ProviderCapability.STREAM_READ,
            ProviderCapability.STREAM_WRITE,
            ProviderCapability.ATOMIC_RENAME,
            ProviderCapability.DELETE,
            ProviderCapability.RESUME_TRANSFER
        )
        private val NETWORK_DIR_CAPS: Set<ProviderCapability> = NETWORK_FILE_CAPS + setOf(
            ProviderCapability.LIST_CHILDREN,
            ProviderCapability.CREATE_DIR
        )

        fun defaultCapabilitiesFor(
            provider: ProviderType,
            isDirectory: Boolean
        ): Set<ProviderCapability> {
            return when (provider) {
                ProviderType.LOCAL -> if (isDirectory) LOCAL_DIR_CAPS else LOCAL_FILE_CAPS
                ProviderType.SAF -> if (isDirectory) SAF_DIR_CAPS else SAF_FILE_CAPS
                ProviderType.MEDIA_STORE -> MEDIA_STORE_CAPS
                ProviderType.ARCHIVE_VIRTUAL -> if (isDirectory) ARCHIVE_DIR_CAPS else ARCHIVE_FILE_CAPS
                ProviderType.FTP, ProviderType.FTPS, ProviderType.SFTP, ProviderType.SMB ->
                    if (isDirectory) NETWORK_DIR_CAPS else NETWORK_FILE_CAPS
            }
        }

        fun readOnlyCapabilitiesFor(
            provider: ProviderType,
            isDirectory: Boolean
        ): Set<ProviderCapability> {
            return when (provider) {
                ProviderType.LOCAL -> if (isDirectory) LOCAL_READONLY_DIR_CAPS else LOCAL_READONLY_FILE_CAPS
                ProviderType.SAF -> if (isDirectory) SAF_READONLY_DIR_CAPS else SAF_READONLY_FILE_CAPS
                else -> defaultCapabilitiesFor(provider, isDirectory)
            }
        }
    }
}

enum class SortField(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    DATE("Modified Date"),
    TYPE("Extension")
}

enum class SortOrder(val label: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

enum class ViewMode(val label: String) {
    LIST("List"),
    GRID("Grid")
}

enum class ConflictResolution(val label: String) {
    OVERWRITE("Overwrite existing"),
    KEEP_BOTH("Keep both (Auto-rename)"),
    SKIP("Skip file")
}

enum class FileCategory(val title: String, val extensions: Set<String>) {
    DOWNLOADS("Downloads", emptySet()),
    IMAGES(
        "Images",
        setOf(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic", "heif",
            "avif", "ico", "tif", "tiff", "raw", "dng", "cr2", "nef", "arw",
            "rw2", "orf", "pef", "raf", "srw", "psd", "hdr"
        )
    ),
    VIDEOS(
        "Videos",
        setOf(
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "ts", "m2ts",
            "mts", "m4v", "mpg", "mpeg", "wmv", "vob", "ogv"
        )
    ),
    AUDIO(
        "Audio",
        setOf(
            "mp3", "flac", "wav", "ogg", "oga", "m4a", "m4b", "aac", "opus",
            "wma", "amr", "mid", "midi", "aiff", "aif", "ape", "alac",
            "mka", "ra", "ram", "wv", "dsf", "dff", "ac3", "eac3", "dts",
            "mp2", "3ga", "caf"
        )
    ),
    DOCUMENTS(
        "Documents",
        setOf(
            "pdf", "txt", "md", "markdown", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "odt", "ods", "odp", "odf", "cbz", "cbr", "rtf", "epub", "mobi", "csv", "tsv", "json", "xml",
            "html", "htm", "css", "js", "ts", "kt", "kts", "java", "py", "sh",
            "c", "cpp", "h", "hpp", "rs", "go", "rb", "php", "sql", "log",
            "ini", "conf", "cfg", "properties", "yaml", "yml", "toml", "gradle"
        )
    ),
    APPLICATIONS("Applications", setOf("apk", "apks", "xapk", "aab")),
    ARCHIVES(
        "Archives",
        setOf(
            "zip", "rar", "tar", "gz", "tgz", "xz", "txz", "7z", "bz2",
            "tbz2", "zst", "lz4", "iso", "cab", "cbz", "cbr"
        )
    ),
    LARGE_FILES("Large Files", emptySet()),
    RECENT_FILES("Recent Files", emptySet());

    companion object {
        fun categorize(file: FileReference): FileCategory? {
            if (file.isDirectory) return null
            val ext = file.extension.lowercase()
            return entries.firstOrNull { ext in it.extensions }
        }
    }
}

@Immutable
data class StorageVolumeInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val rootReference: FileReference,
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
    val isRemovable: Boolean = false,
    val isSafGrantRevoked: Boolean = false
) {
    val usageFraction: Float
        get() = if (totalBytes > 0L) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

@Immutable
data class CategoryUsageBucket(
    val categoryName: String,
    val bytesUsed: Long,
    val fileCount: Int,
    val fractionOfTotal: Float,
    val colorHex: Long
)

data class StorageAnalysisReport(
    val rootPath: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val scannedFilesCount: Int,
    val scannedDirectoriesCount: Int,
    val categoryBuckets: List<CategoryUsageBucket>,
    val largestFiles: List<FileReference>,
    val largestDirectories: List<Pair<FileReference, Long>>,
    val extensionDistribution: List<Triple<String, Int, Long>>,
    val duplicateGroups: List<DuplicateFileGroup>
)

data class DuplicateFileGroup(
    val sha256Hash: String,
    val fileSizeBytes: Long,
    val files: List<FileReference>
) {
    val reclaimableBytes: Long
        get() = if (files.size > 1) fileSizeBytes * (files.size - 1) else 0L
}

@Immutable
data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val apkSizeBytes: Long,
    val firstInstallTimeMs: Long,
    val lastUpdateTimeMs: Long,
    val isSystemApp: Boolean,
    val targetSdk: Int,
    val minSdk: Int,
    val canShareApk: Boolean = apkPath.isNotEmpty()
)

data class ApkArchiveInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val requestedPermissions: List<String>,
    val fileReference: FileReference
)

private val BYTE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
    val unitIndex = z.coerceIn(0, BYTE_UNITS.lastIndex)
    val divisor = 1L shl (unitIndex * 10)
    val scaled10 = (bytes * 10L) / divisor
    val whole = scaled10 / 10L
    val frac = scaled10 % 10L
    return if (frac == 0L || whole >= 100L) {
        "$whole ${BYTE_UNITS[unitIndex]}"
    } else {
        "$whole.$frac ${BYTE_UNITS[unitIndex]}"
    }
}

/**
 * Determines whether any directory segment in the given path or URI starts with a '.' (hidden folder),
 * such as `.openfile_trash`, `.thumbnails`, or `.hidden_folder`.
 */
fun isPathInsideHiddenDirectory(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    val clean = if (path.startsWith("file://")) {
        path.substring(7)
    } else {
        runCatching { java.net.URLDecoder.decode(path, "UTF-8") }.getOrDefault(path)
    }
    val parent = if (clean.contains('/')) clean.substringBeforeLast('/') else return false
    return parent.split('/', ':').any { segment ->
        segment.startsWith('.') && segment != "." && segment != ".."
    }
}
