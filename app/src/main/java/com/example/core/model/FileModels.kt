package com.example.core.model

import androidx.compose.runtime.Immutable
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

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
    SMB("SMB2/3 Share", true),
    GOOGLE_DRIVE("Google Drive", true),
    ONEDRIVE("Microsoft OneDrive", true),
    DROPBOX("Dropbox", true)
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
 * Archive, Network (FTP/FTPS/SFTP/SMB), or Cloud (Drive/OneDrive/Dropbox) providers.
 */
@Immutable
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
    val isHidden: Boolean = displayName.startsWith("."),
    val capabilities: Set<ProviderCapability> = defaultCapabilitiesFor(providerType, isDirectory),
    val metadata: Map<String, String> = emptyMap()
) {
    val formattedSize: String
        get() = if (isDirectory) {
            val childCount = metadata["childCount"]
            if (childCount != null) "$childCount items" else "Folder"
        } else {
            formatByteSize(sizeBytes)
        }

    val permissionsString: String
        get() = metadata["permissions"] ?: if (isDirectory) "drwxr-xr-x" else "-rw-r--r--"

    companion object {
        fun defaultCapabilitiesFor(
            provider: ProviderType,
            isDirectory: Boolean
        ): Set<ProviderCapability> {
            return when (provider) {
                ProviderType.LOCAL -> buildSet {
                    add(ProviderCapability.STREAM_READ)
                    add(ProviderCapability.STREAM_WRITE)
                    add(ProviderCapability.RANDOM_ACCESS_READ)
                    add(ProviderCapability.ATOMIC_RENAME)
                    add(ProviderCapability.INTRA_PROVIDER_MOVE)
                    add(ProviderCapability.DELETE)
                    add(ProviderCapability.TRASH_IN_PLACE)
                    add(ProviderCapability.RESUME_TRANSFER)
                    add(ProviderCapability.CHECKSUM_CALCULATION)
                    add(ProviderCapability.POSIX_METADATA)
                    if (isDirectory) {
                        add(ProviderCapability.LIST_CHILDREN)
                        add(ProviderCapability.CREATE_DIR)
                        add(ProviderCapability.CREATE_FILE)
                        add(ProviderCapability.FAST_DIRECTORY_SIZE)
                    }
                }
                ProviderType.SAF -> buildSet {
                    add(ProviderCapability.STREAM_READ)
                    add(ProviderCapability.STREAM_WRITE)
                    add(ProviderCapability.ATOMIC_RENAME)
                    add(ProviderCapability.DELETE)
                    add(ProviderCapability.CHECKSUM_CALCULATION)
                    if (isDirectory) {
                        add(ProviderCapability.LIST_CHILDREN)
                        add(ProviderCapability.CREATE_DIR)
                        add(ProviderCapability.CREATE_FILE)
                    }
                }
                ProviderType.MEDIA_STORE -> setOf(
                    ProviderCapability.STREAM_READ,
                    ProviderCapability.DELETE,
                    ProviderCapability.CHECKSUM_CALCULATION
                )
                ProviderType.ARCHIVE_VIRTUAL -> buildSet {
                    add(ProviderCapability.STREAM_READ)
                    add(ProviderCapability.ARCHIVE_EXTRACT)
                    if (isDirectory) add(ProviderCapability.LIST_CHILDREN)
                }
                ProviderType.FTP, ProviderType.FTPS, ProviderType.SFTP, ProviderType.SMB -> buildSet {
                    add(ProviderCapability.STREAM_READ)
                    add(ProviderCapability.STREAM_WRITE)
                    add(ProviderCapability.ATOMIC_RENAME)
                    add(ProviderCapability.DELETE)
                    add(ProviderCapability.RESUME_TRANSFER)
                    if (isDirectory) {
                        add(ProviderCapability.LIST_CHILDREN)
                        add(ProviderCapability.CREATE_DIR)
                    }
                }
                ProviderType.GOOGLE_DRIVE, ProviderType.ONEDRIVE, ProviderType.DROPBOX -> buildSet {
                    add(ProviderCapability.STREAM_READ)
                    add(ProviderCapability.STREAM_WRITE)
                    add(ProviderCapability.ATOMIC_RENAME)
                    add(ProviderCapability.INTRA_PROVIDER_MOVE)
                    add(ProviderCapability.DELETE)
                    add(ProviderCapability.REMOTE_TRASH)
                    add(ProviderCapability.CHECKSUM_CALCULATION)
                    if (isDirectory) {
                        add(ProviderCapability.LIST_CHILDREN)
                        add(ProviderCapability.CREATE_DIR)
                        add(ProviderCapability.CREATE_FILE)
                    }
                }
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
    IMAGES("Images", setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic", "raw")),
    VIDEOS("Videos", setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "ts")),
    AUDIO("Audio", setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus")),
    DOCUMENTS("Documents", setOf("pdf", "txt", "md", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "json", "xml", "html", "kt", "java", "py", "sh", "log", "ini", "yaml", "yml")),
    APPLICATIONS("Applications", setOf("apk", "apks", "xapk", "aab")),
    ARCHIVES("Archives", setOf("zip", "tar", "gz", "tgz", "xz", "txz", "7z", "bz2")),
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

@Immutable
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

@Immutable
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

@Immutable
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

private val byteDecimalFormat = object : ThreadLocal<DecimalFormat>() {
    override fun initialValue(): DecimalFormat = DecimalFormat("#,##0.#")
}

fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    val formatter = byteDecimalFormat.get() ?: DecimalFormat("#,##0.#")
    return formatter.format(value) + " " + units[digitGroups]
}
