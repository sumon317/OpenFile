package com.example.ui.components

import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import com.example.core.archive.ArchiveEntryItem
import com.example.ui.FilePreviewState
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.database.TransferJobEntity
import com.example.core.filesystem.ConflictPromptRequest
import com.example.core.filesystem.FileChecksums
import com.example.core.model.ApkArchiveInfo
import com.example.core.model.ConflictResolution
import com.example.core.model.FileCategory
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.example.core.model.formatByteSize
import com.example.ui.theme.ApkGreen
import com.example.ui.theme.ArchiveBrown
import com.example.ui.theme.AudioPurple
import com.example.ui.theme.DocumentIndigo
import com.example.ui.theme.FolderAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MediaTeal
import com.example.ui.theme.VideoCoral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CodeExtensions = setOf(
    "kt", "java", "py", "js", "ts", "json", "xml", "html", "sh", "yaml", "yml"
)

private fun resolveLocalBackingFile(file: FileReference): File? {
    file.metadata["localBackingPath"]?.takeIf { it.isNotBlank() }?.let { path ->
        val f = File(path)
        if (f.exists()) return f
    }
    if (file.providerType == ProviderType.LOCAL) {
        return File(file.uriOrPath)
    }
    return null
}

private fun resolveFastThumbnailPath(file: FileReference): String? {
    val localBackingPath = file.metadata["localBackingPath"]
    if (!localBackingPath.isNullOrBlank()) return localBackingPath
    if (file.providerType == ProviderType.LOCAL) return file.uriOrPath
    if (file.uriOrPath.startsWith("content://")) return file.uriOrPath
    return null
}

private val FileIconBadgeShape = RoundedCornerShape(10.dp)
private val GridCardShape = RoundedCornerShape(12.dp)

private class FastDateCache(private val capacity: Int = 128) {
    private val keys = LongArray(capacity) { -1L }
    private val values = arrayOfNulls<String>(capacity)

    fun format(epochMs: Long, formatter: SimpleDateFormat): String {
        if (epochMs <= 0L) return ""
        val idx = ((epochMs xor (epochMs ushr 16)).toInt() and (capacity - 1))
        if (keys[idx] == epochMs) {
            val cached = values[idx]
            if (cached != null) return cached
        }
        val formatted = formatter.format(Date(epochMs))
        keys[idx] = epochMs
        values[idx] = formatted
        return formatted
    }
}

private val dateCache = FastDateCache()

@Composable
fun FileIconBadge(
    file: FileReference,
    showThumbnails: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    context: android.content.Context = LocalContext.current
) {
    if (isSelected) {
        Box(
            modifier = modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.primary, FileIconBadgeShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        return
    }

    val ext = file.extension

    // 1. Image Thumbnail (Local path or Content URI)
    val thumbPath = if (showThumbnails && !file.isDirectory && ext in FileCategory.IMAGES.extensions) {
        resolveFastThumbnailPath(file)
    } else null

    if (thumbPath != null) {
        val model: Any = if (thumbPath.startsWith("content://")) Uri.parse(thumbPath) else File(thumbPath)
        val imageRequest = remember(thumbPath, context) {
            ImageRequest.Builder(context)
                .data(model)
                .size(120, 120)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .memoryCacheKey(thumbPath)
                .diskCacheKey(thumbPath)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = file.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(42.dp)
                .clip(FileIconBadgeShape)
        )
        return
    }

    // 2. Video Thumbnail (Decoded video frame with subtle play overlay)
    if (showThumbnails && !file.isDirectory && ext in FileCategory.VIDEOS.extensions) {
        VideoThumbnailBadge(
            file = file,
            size = 42.dp,
            modifier = modifier
        )
        return
    }

    // 3. Audio Thumbnail (Embedded album cover art with corner music indicator)
    if (showThumbnails && !file.isDirectory && ext in FileCategory.AUDIO.extensions) {
        AudioThumbnailBadge(
            file = file,
            size = 42.dp,
            modifier = modifier
        )
        return
    }

    // 4. Display actual application icon for APK files when thumbnails are enabled
    if (showThumbnails && !file.isDirectory && ext.equals("apk", ignoreCase = true)) {
        val apkPath = resolveFastThumbnailPath(file)
        if (apkPath != null && (apkPath.startsWith("content://") || File(apkPath).exists())) {
            AppIcon(
                apkPath = apkPath,
                size = 42.dp,
                modifier = modifier
            )
            return
        }
    }

    val defaultSecondaryColor = MaterialTheme.colorScheme.secondary
    val (icon, tintColor) = remember(file.isDirectory, ext, defaultSecondaryColor) {
        when {
            file.isDirectory -> Icons.Filled.Folder to FolderAmber
            ext in FileCategory.ARCHIVES.extensions -> Icons.Filled.FolderZip to ArchiveBrown
            ext in FileCategory.IMAGES.extensions -> Icons.Filled.Image to MediaTeal
            ext in FileCategory.VIDEOS.extensions -> Icons.Filled.VideoFile to VideoCoral
            ext in FileCategory.AUDIO.extensions -> Icons.Filled.AudioFile to AudioPurple
            ext == "pdf" -> Icons.Filled.PictureAsPdf to VideoCoral
            ext == "apk" -> Icons.Filled.Android to ApkGreen
            ext in CodeExtensions -> Icons.Filled.Code to DocumentIndigo
            ext in FileCategory.DOCUMENTS.extensions -> Icons.Filled.Description to DocumentIndigo
            else -> Icons.AutoMirrored.Filled.InsertDriveFile to defaultSecondaryColor
        }
    }

    Box(
        modifier = modifier
            .size(42.dp)
            .background(tintColor.copy(alpha = 0.14f), FileIconBadgeShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = file.displayName,
            tint = tintColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun BreadcrumbBar(
    breadcrumbs: List<FileReference>,
    providerType: ProviderType?,
    onCrumbClick: (FileReference) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (providerType != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = providerType.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        breadcrumbs.forEachIndexed { index, crumb ->
            val isLast = index == breadcrumbs.lastIndex
            Surface(
                onClick = { onCrumbClick(crumb) },
                color = if (isLast) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("breadcrumb_${crumb.displayName}")
            ) {
                Text(
                    text = crumb.displayName.ifBlank { "/" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isLast) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (!isLast) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private val rowDateFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
}

fun formatParentPathForDisplay(rawPath: String): String {
    val normalized = rawPath.replace('\\', '/').trimEnd('/')
    return when {
        normalized == "/storage/emulated/0" || normalized == "/storage/emulated/0/" -> "Internal Storage"
        normalized.startsWith("/storage/emulated/0/") -> {
            "Internal Storage › " + normalized.removePrefix("/storage/emulated/0/").replace("/", " › ")
        }
        normalized == "/sdcard" || normalized == "/sdcard/" -> "Internal Storage"
        normalized.startsWith("/sdcard/") -> {
            "Internal Storage › " + normalized.removePrefix("/sdcard/").replace("/", " › ")
        }
        else -> normalized.replace("/", " › ").trimStart(' ', '›', ' ')
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    file: FileReference,
    isSelected: Boolean,
    isBookmarked: Boolean,
    showThumbnails: Boolean,
    showExtensions: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShare: () -> Unit,
    onProperties: () -> Unit,
    onDownloadRemote: (() -> Unit)? = null,
    onOpenWith: (() -> Unit)? = null,
    onQuickPreview: (() -> Unit)? = null,
    onViewArchive: (() -> Unit)? = null,
    onOpenParentFolder: (() -> Unit)? = null,
    onCompress: (() -> Unit)? = null,
    onDrillDownAnalyze: (() -> Unit)? = null,
    showParentPath: Boolean = false,
    context: android.content.Context = LocalContext.current
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val displayTitle = remember(file.displayName, file.isDirectory, showExtensions) {
        if (!showExtensions && !file.isDirectory && file.displayName.contains('.')) {
            file.displayName.substringBeforeLast('.')
        } else {
            file.displayName
        }
    }
    val childCountMeta = file.metadata["childCount"]
    val permissionsMeta = file.metadata["permissions"]
    val appLabelMeta = file.metadata["appLabel"]
    val safMountedMeta = file.metadata["safMounted"]
    val restrictedMeta = file.metadata["restrictedAppFolder"]
    // Memoize the complete subtitle string — date formatting is relatively expensive
    val subtitleText = remember(
        file.isDirectory,
        file.sizeBytes,
        file.lastModifiedEpochMs,
        childCountMeta,
        permissionsMeta,
        appLabelMeta,
        safMountedMeta,
        restrictedMeta
    ) {
        val dateStr = if (file.lastModifiedEpochMs <= 0L) ""
        else dateCache.format(
            file.lastModifiedEpochMs,
            rowDateFormat.get() ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        )
        // formattedSize is evaluated once here
        val sizePart = file.formattedSize
        val prefix = if (!appLabelMeta.isNullOrBlank()) "$appLabelMeta  •  " else ""
        val statusSuffix = when {
            safMountedMeta == "true" -> "  •  SAF Mounted"
            restrictedMeta == "true" -> "  •  Tap to open / mount"
            else -> ""
        }
        if (dateStr.isEmpty()) {
            "$prefix$sizePart$statusSuffix"
        } else {
            "$prefix$sizePart  •  $dateStr$statusSuffix"
        }
    }
    val bookmarked = isBookmarked
    val dividerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val selectedBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (isSelected) {
                    drawRect(color = selectedBgColor)
                }
                val stroke = 1.dp.toPx()
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke
                )
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("file_row_${file.displayName}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
            FileIconBadge(
                file = file,
                showThumbnails = showThumbnails,
                isSelected = isSelected,
                context = context
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (file.isDirectory) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (bookmarked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = "Bookmarked",
                            tint = FolderAmber,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showParentPath && !file.parentUriOrPath.isNullOrBlank()) {
                    Text(
                        text = formatParentPathForDisplay(file.parentUriOrPath),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("file_menu_${file.displayName}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More actions for ${file.displayName}"
                    )
                }
                if (menuExpanded) {
                    DropdownMenu(
                        expanded = true,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (onDrillDownAnalyze != null && file.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Analyze This Folder") },
                                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDrillDownAnalyze()
                                }
                            )
                        }
                        if (onOpenParentFolder != null && !file.parentUriOrPath.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("Open Containing Folder") },
                                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenParentFolder()
                                }
                            )
                        }
                        if (onOpenWith != null && !file.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Open With...") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenWith()
                                }
                            )
                        }
                        if (onQuickPreview != null && !file.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Quick Preview") },
                                leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onQuickPreview()
                                }
                            )
                        }
                        if (onViewArchive != null) {
                            DropdownMenuItem(
                                text = { Text("View Archive") },
                                leadingIcon = { Icon(Icons.Filled.FolderZip, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onViewArchive()
                                }
                            )
                        }
                        if (onDownloadRemote != null && !file.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Download to Local") },
                                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDownloadRemote()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Cut / Move") },
                            leadingIcon = { Icon(Icons.Filled.ContentCut, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onCut()
                            }
                        )
                        if (onCompress != null) {
                            DropdownMenuItem(
                                text = { Text("Compress to Archive") },
                                leadingIcon = { Icon(Icons.Filled.FolderZip, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onCompress()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (bookmarked) "Remove Bookmark" else "Add Bookmark") },
                            leadingIcon = {
                                Icon(
                                    if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleBookmark()
                            }
                        )
                        if (!file.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onShare()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Properties & Hashes") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onProperties()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemGridCard(
    file: FileReference,
    isSelected: Boolean,
    showThumbnails: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onProperties: () -> Unit,
    context: android.content.Context = LocalContext.current
) {
    val formattedSize = remember(file.isDirectory, file.sizeBytes, file.metadata["childCount"]) {
        file.formattedSize
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        shape = GridCardShape,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("file_grid_${file.displayName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FileIconBadge(
                file = file,
                showThumbnails = showThumbnails,
                isSelected = isSelected,
                context = context
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = file.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formattedSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(
                    onClick = onProperties,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Info",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ConflictResolutionDialog(
    prompt: ConflictPromptRequest,
    onResolve: (ConflictResolution, Boolean) -> Unit
) {
    var applyToAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = FolderAmber
            )
        },
        title = { Text("File Conflict Detected") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "An item named '${prompt.conflictingName}' already exists in '${prompt.destinationDirectory.displayName}'.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Source size: ${formatByteSize(prompt.sourceFile.sizeBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Checkbox(
                        checked = applyToAll,
                        onCheckedChange = { applyToAll = it }
                    )
                    Text(
                        text = "Apply this choice to all remaining conflicts",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { onResolve(ConflictResolution.KEEP_BOTH, applyToAll) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("conflict_keep_both")
                ) {
                    Text("Keep Both (Auto-Rename)")
                }
                FilledTonalButton(
                    onClick = { onResolve(ConflictResolution.OVERWRITE, applyToAll) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("conflict_overwrite")
                ) {
                    Text("Overwrite Destination")
                }
                OutlinedButton(
                    onClick = { onResolve(ConflictResolution.SKIP, applyToAll) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("conflict_skip")
                ) {
                    Text("Skip This File")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilePropertiesModalSheet(
    file: FileReference,
    checksums: FileChecksums?,
    apkInfo: ApkArchiveInfo?,
    onDismiss: () -> Unit,
    onInstallApk: (File) -> Unit,
    onOpenExternal: (FileReference) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val modifiedFormatted = remember(file.lastModifiedEpochMs) {
        if (file.lastModifiedEpochMs <= 0L) "Unknown"
        else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModifiedEpochMs))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FileIconBadge(
                    file = file,
                    showThumbnails = true,
                    isSelected = false,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = file.uriOrPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider()

            PropertyKeyValueRow("Provider", file.providerType.label)
            PropertyKeyValueRow("Type / MIME", file.mimeType)
            PropertyKeyValueRow("Size", "${formatByteSize(file.sizeBytes)} (${file.sizeBytes} bytes)")
            PropertyKeyValueRow("Last Modified", modifiedFormatted)
            PropertyKeyValueRow("Permissions", file.permissionsString)

            if (apkInfo != null) {
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val localApk = resolveLocalBackingFile(file)?.absolutePath ?: file.uriOrPath
                    AppIcon(
                        packageName = apkInfo.packageName,
                        apkPath = localApk,
                        size = 48.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = apkInfo.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = apkInfo.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                PropertyKeyValueRow("App Label", apkInfo.appName)
                PropertyKeyValueRow("Package Name", apkInfo.packageName)
                PropertyKeyValueRow("Version", "${apkInfo.versionName} (${apkInfo.versionCode})")
                PropertyKeyValueRow("SDK Target", "minSdk ${apkInfo.minSdk} • targetSdk ${apkInfo.targetSdk}")
                Button(
                    onClick = { onInstallApk(resolveLocalBackingFile(file) ?: File(file.uriOrPath)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Android, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Install APK via System Package Installer")
                }
            }

            if (!file.isDirectory && resolveFastThumbnailPath(file) != null) {
                HorizontalDivider()
                Text(
                    text = "Integrity Checksums",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (checksums == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Computing streaming MD5 & SHA-256...", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    PropertyKeyValueRow("MD5", checksums.md5Hex, onCopy = {
                        clipboardManager.setText(AnnotatedString(checksums.md5Hex))
                    })
                    PropertyKeyValueRow("SHA-256", checksums.sha256Hex, onCopy = {
                        clipboardManager.setText(AnnotatedString(checksums.sha256Hex))
                    })
                }
            }

            HorizontalDivider()
            Text(
                text = "Advertised Provider Capabilities",
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                file.capabilities.forEach { cap ->
                    AssistChip(
                        onClick = {},
                        label = { Text(cap.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            if (!file.isDirectory) {
                OutlinedButton(
                    onClick = { onOpenExternal(file) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open With System Application")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PropertyKeyValueRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = JetBrainsMonoFamily
            )
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $label", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Full-Window File Preview System (File Manager + style):
 * Dedicated window views for Text/Code/JSON/Markdown, Pinch-to-Zoom Image Gallery with horizontal
 * swiping across siblings and archive entries, PDF Page Rendering, and inline Audio/Video players.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    state: FilePreviewState,
    onDismiss: () -> Unit,
    onOpenExternal: (FileReference) -> Unit,
    onSaveTextContent: ((FileReference, String) -> Unit)? = null,
    onLoadArchiveImage: (suspend (ArchiveEntryItem) -> File?)? = null,
    onPreviousMedia: (() -> Unit)? = null,
    onNextMedia: (() -> Unit)? = null,
    onSelectMedia: ((FileReference) -> Unit)? = null
) {
    val file = state.currentFile
    val ext = file.extension.lowercase()
    val isImage = ext in FileCategory.IMAGES.extensions || state.archiveContext != null
    val isAudio = ext in FileCategory.AUDIO.extensions || file.mimeType.startsWith("audio/")
    val isVideo = ext in FileCategory.VIDEOS.extensions || file.mimeType.startsWith("video/")
    val isMedia = isAudio || isVideo
    val isImageOrDarkMedia = isImage || isVideo || isAudio || ext in setOf("cbz", "cbr")

    val currentMediaIndex = state.siblingMedia.indexOfFirst { it.uriOrPath == file.uriOrPath }
    val hasPreviousMedia = currentMediaIndex > 0
    val hasNextMedia = currentMediaIndex >= 0 && currentMediaIndex < state.siblingMedia.size - 1

    var activeTitle by remember(file) { mutableStateOf(file.displayName) }
    var activeSubtitle by remember(file) {
        mutableStateOf(
            if (isImageOrDarkMedia) {
                file.formattedSize
            } else {
                "${file.providerType.label} • ${file.mimeType} • ${file.formattedSize}"
            }
        )
    }

    // Intercept back gesture immediately to dismiss the preview
    BackHandler {
        onDismiss()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isVideo) Modifier
                else Modifier.statusBarsPadding().navigationBarsPadding()
            ),
        color = if (isImageOrDarkMedia) Color.Black else MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Full-Window Top App Bar: only for non-media document and image types
            if (!isMedia) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = activeTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isImageOrDarkMedia) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isImageOrDarkMedia) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { onOpenExternal(file) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isImageOrDarkMedia) Color.White else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Open With",
                                color = if (isImageOrDarkMedia) Color.White else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isImageOrDarkMedia) Color.Black.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = if (isImageOrDarkMedia) Color.White else MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = if (isImageOrDarkMedia) Color.White else MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = if (isImageOrDarkMedia) Color.White else MaterialTheme.colorScheme.primary
                    )
                )

                if (!isImageOrDarkMedia) {
                    HorizontalDivider()
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(if (isImage || isVideo || isAudio || ext in setOf("cbz", "cbr", "pdf")) 0.dp else 12.dp)
            ) {
                when {
                    isImage -> {
                        ImagePagerPreview(
                            state = state,
                            onLoadArchiveImage = onLoadArchiveImage,
                            onPageTitleChanged = { title, sub ->
                                activeTitle = title
                                activeSubtitle = sub
                            }
                        )
                    }
                    ext == "pdf" -> {
                        PdfDocumentViewer(file)
                    }
                    ext in setOf("docx", "doc", "odt", "odf") -> {
                        DocxDocumentPreview(file)
                    }
                    ext in setOf("pptx", "ppt", "odp") -> {
                        PptxDocumentPreview(file)
                    }
                    ext in setOf("xlsx", "xls", "ods", "csv", "tsv") -> {
                        SpreadsheetPreview(file)
                    }
                    ext in setOf("cbz", "cbr") -> {
                        CbzComicPreview(file)
                    }
                    ext in setOf("md", "markdown") -> {
                        MarkdownDocumentPreview(file = file, onSaveText = onSaveTextContent)
                    }
                    isAudio -> {
                        AudioPlayerPreview(
                            file = file,
                            playlist = state.siblingMedia,
                            onSelectMedia = onSelectMedia,
                            onOpenExternal = { onOpenExternal(file) },
                            onPrevious = onPreviousMedia,
                            onNext = onNextMedia,
                            hasPrevious = hasPreviousMedia,
                            hasNext = hasNextMedia,
                            onDismiss = onDismiss
                        )
                    }
                    isVideo -> {
                        VideoPlayerPreview(
                            file = file,
                            playlist = state.siblingMedia,
                            onSelectMedia = onSelectMedia,
                            onOpenExternal = { onOpenExternal(file) },
                            onPrevious = onPreviousMedia,
                            onNext = onNextMedia,
                            hasPrevious = hasPreviousMedia,
                            hasNext = hasNextMedia,
                            onDismiss = onDismiss
                        )
                    }
                    else -> {
                        TextAndCodePreview(file = file, onSaveTextContent = onSaveTextContent)
                    }
                }
            }
        }
    }
}

@Composable
fun FilePreviewDialog(
    file: FileReference,
    onDismiss: () -> Unit,
    onOpenExternal: (FileReference) -> Unit,
    onSaveTextContent: ((FileReference, String) -> Unit)? = null
) {
    FilePreviewScreen(
        state = FilePreviewState(currentFile = file),
        onDismiss = onDismiss,
        onOpenExternal = onOpenExternal,
        onSaveTextContent = onSaveTextContent
    )
}

@Composable
private fun ImagePagerPreview(
    state: FilePreviewState,
    onLoadArchiveImage: (suspend (ArchiveEntryItem) -> File?)? = null,
    onPageTitleChanged: (title: String, subtitle: String) -> Unit
) {
    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    val archiveContext = state.archiveContext
    if (archiveContext != null) {
        val entries = archiveContext.imageEntries
        val initialIdx = archiveContext.initialIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        val pagerState = rememberPagerState(initialPage = initialIdx) { entries.size }

        LaunchedEffect(pagerState.currentPage) {
            isCurrentPageZoomed = false
            val cur = pagerState.currentPage
            if (cur in entries.indices) {
                val entry = entries[cur]
                onPageTitleChanged(
                    entry.displayName,
                    "${cur + 1} of ${entries.size} • ${formatByteSize(entry.uncompressedSize)}"
                )
            }
            if (cur > 0 && onLoadArchiveImage != null) {
                onLoadArchiveImage(entries[cur - 1])
            }
            if (cur < entries.size - 1 && onLoadArchiveImage != null) {
                onLoadArchiveImage(entries[cur + 1])
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                key = { entries[it].entryPath },
                userScrollEnabled = !isCurrentPageZoomed,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("archive_image_pager")
            ) { page ->
                val entry = entries[page]
                val fileState by produceState<File?>(
                    initialValue = if (page == initialIdx) File(state.currentFile.uriOrPath) else null,
                    key1 = entry.entryPath
                ) {
                    if (value == null && onLoadArchiveImage != null) {
                        value = onLoadArchiveImage(entry)
                    }
                }

                if (fileState != null) {
                    ZoomableImagePreview(
                        imageModel = fileState!!,
                        title = entry.displayName,
                        onZoomChanged = { isZoomed ->
                            if (pagerState.currentPage == page) {
                                isCurrentPageZoomed = isZoomed
                            }
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    } else {
        val images = if (state.siblingImages.isNotEmpty()) state.siblingImages else listOf(state.currentFile)
        val initialIdx = state.initialImageIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))
        val pagerState = rememberPagerState(initialPage = initialIdx) { images.size }

        LaunchedEffect(pagerState.currentPage) {
            isCurrentPageZoomed = false
            val cur = pagerState.currentPage
            if (cur in images.indices) {
                val f = images[cur]
                onPageTitleChanged(
                    f.displayName,
                    "${cur + 1} of ${images.size} • ${f.formattedSize}"
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                key = { images[it].uriOrPath },
                userScrollEnabled = !isCurrentPageZoomed,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("folder_image_pager")
            ) { page ->
                val f = images[page]
                val imgModel = resolveFastThumbnailPath(f)?.let { File(it) } ?: f.uriOrPath
                ZoomableImagePreview(
                    imageModel = imgModel,
                    title = f.displayName,
                    onZoomChanged = { isZoomed ->
                        if (pagerState.currentPage == page) {
                            isCurrentPageZoomed = isZoomed
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ZoomableImagePreview(
    imageModel: Any,
    title: String,
    onZoomChanged: ((Boolean) -> Unit)? = null
) {
    var scale by remember(imageModel) { mutableFloatStateOf(1f) }
    var offsetX by remember(imageModel) { mutableFloatStateOf(0f) }
    var offsetY by remember(imageModel) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(imageModel) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                            onZoomChanged?.invoke(false)
                        } else {
                            scale = 2.5f
                            onZoomChanged?.invoke(true)
                        }
                    }
                )
            }
            .pointerInput(imageModel) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }

                        if (scale > 1.05f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1.05f) {
                                val maxOffsetX = (size.width.toFloat() * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                val maxOffsetY = (size.height.toFloat() * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                onZoomChanged?.invoke(true)
                            } else {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                                onZoomChanged?.invoke(false)
                            }

                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        } else {
                            if (pointerCount >= 2) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                if (newScale > 1.05f) {
                                    scale = newScale
                                    val maxOffsetX = (size.width.toFloat() * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                    val maxOffsetY = (size.height.toFloat() * (newScale - 1f) / 2f).coerceAtLeast(0f)
                                    offsetX = (offsetX + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    onZoomChanged?.invoke(true)

                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) {
                                            change.consume()
                                        }
                                    }
                                }
                            }
                            // Single pointer when not zoomed does NOT consume events,
                            // allowing HorizontalPager to receive drag and swipe left/right freely!
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        )
    }
}

@Composable
private fun TextAndCodePreview(
    file: FileReference,
    onSaveTextContent: ((FileReference, String) -> Unit)? = null
) {
    var contentLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var editableText by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var canEditFile by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            runCatching {
                val targetFile = resolveLocalBackingFile(file)
                if (targetFile != null && targetFile.exists()) {
                    val sampleBuffer = ByteArray(4096)
                    val bytesRead = targetFile.inputStream().use { it.read(sampleBuffer) }
                    val isBinary = bytesRead > 0 && sampleBuffer.take(bytesRead).any { it == 0.toByte() }
                    if (isBinary) {
                        canEditFile = false
                        contentLines = listOf("Binary file: Cannot preview as text. Use 'Open With' above to open with an external application.")
                    } else {
                        val raw = targetFile.inputStream().bufferedReader().use { reader ->
                            val lines = mutableListOf<String>()
                            var count = 0
                            var line = reader.readLine()
                            while (line != null && count < 800) {
                                lines.add(line)
                                count++
                                line = reader.readLine()
                            }
                            lines
                        }
                        editableText = raw.joinToString("\n")
                        canEditFile = true
                        contentLines = raw.ifEmpty { listOf("(Empty file)") }
                    }
                } else {
                    canEditFile = false
                    contentLines = listOf("Remote file preview: Download to local storage to inspect full contents.")
                }
            }.onFailure {
                canEditFile = false
                contentLines = listOf("Unable to preview file as text: ${it.message}")
            }
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            if (canEditFile && onSaveTextContent != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "Editing ${file.displayName} (${file.providerType.label})" else "Read-only view • Tap Edit to modify",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isEditing) {
                            OutlinedButton(onClick = { isEditing = false }) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    onSaveTextContent(file, editableText)
                                    contentLines = editableText.lines().ifEmpty { listOf("(Empty file)") }
                                    isEditing = false
                                },
                                modifier = Modifier.testTag("save_text_file_btn")
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save to ${file.providerType.label}")
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { isEditing = true },
                                modifier = Modifier.testTag("edit_text_file_btn")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit File")
                            }
                        }
                    }
                }
            }

            if (isEditing) {
                OutlinedTextField(
                    value = editableText,
                    onValueChange = { editableText = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("text_editor_field")
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLowest,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp)
                ) {
                    items(
                        count = contentLines.size,
                        key = { it },
                        contentType = { "code_line" }
                    ) { index ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "%4d  ".format(index + 1),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = contentLines[index],
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFamily
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferQueueBottomSheet(
    jobs: List<TransferJobEntity>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onClearFinished: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Unified Transfer Queue", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onClearFinished) {
                    Text("Clear Finished")
                }
            }
            if (jobs.isEmpty()) {
                Text(
                    text = "No active or recent file transfers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                items = jobs,
                key = { it.jobId },
                contentType = { "job" }
            ) { job ->
                        val fraction = if (job.totalBytes > 0L) {
                            (job.transferredBytes.toFloat() / job.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = job.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = job.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (job.status) {
                                            "COMPLETED" -> ApkGreen
                                            "FAILED", "INTERRUPTED" -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                                if (job.status == "RUNNING" && job.totalBytes <= 0L) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                } else {
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val percentText = if (job.totalBytes > 0L) " (${(fraction * 100).toInt()}%)" else ""
                                    Text(
                                        text = "${formatByteSize(job.transferredBytes)} / ${formatByteSize(job.totalBytes)}$percentText • ${job.processedFiles}/${job.totalFiles} files",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (job.status == "RUNNING" || job.status == "PAUSED") {
                                        Row {
                                            TextButton(
                                                onClick = {
                                                    if (job.status == "PAUSED") onResume(job.jobId)
                                                    else onPause(job.jobId)
                                                }
                                            ) {
                                                Text(if (job.status == "PAUSED") "Resume" else "Pause")
                                            }
                                            TextButton(onClick = { onCancel(job.jobId) }) {
                                                Text("Cancel", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                                if (job.checksumResult != null) {
                                    Text(
                                        text = job.checksumResult,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ApkGreen
                                    )
                                }
                                if (job.errorMessage != null) {
                                    Text(
                                        text = job.errorMessage,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
