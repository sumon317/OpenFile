package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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

@Composable
fun FileIconBadge(
    file: FileReference,
    showThumbnails: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    if (isSelected) {
        Box(
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary),
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

    val ext = file.extension.lowercase()
    if (showThumbnails && !file.isDirectory && file.providerType == ProviderType.LOCAL && ext in FileCategory.IMAGES.extensions) {
        val context = LocalContext.current
        val imageRequest = remember(file.uriOrPath) {
            ImageRequest.Builder(context)
                .data(File(file.uriOrPath))
                .size(128)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = file.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        return
    }

    val (icon, tintColor) = when {
        file.isDirectory -> Icons.Filled.Folder to FolderAmber
        ext in FileCategory.ARCHIVES.extensions -> Icons.Filled.FolderZip to ArchiveBrown
        ext in FileCategory.IMAGES.extensions -> Icons.Filled.Image to MediaTeal
        ext in FileCategory.VIDEOS.extensions -> Icons.Filled.VideoFile to VideoCoral
        ext in FileCategory.AUDIO.extensions -> Icons.Filled.AudioFile to AudioPurple
        ext == "pdf" -> Icons.Filled.PictureAsPdf to VideoCoral
        ext == "apk" -> Icons.Filled.Android to ApkGreen
        ext in setOf("kt", "java", "py", "js", "ts", "json", "xml", "html", "sh", "yaml", "yml") ->
            Icons.Filled.Code to DocumentIndigo
        ext in FileCategory.DOCUMENTS.extensions -> Icons.Filled.Description to DocumentIndigo
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.secondary
    }

    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tintColor.copy(alpha = 0.14f)),
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
    onQuickPreview: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateStr = remember(file.lastModifiedEpochMs) {
        if (file.lastModifiedEpochMs <= 0L) ""
        else (rowDateFormat.get() ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()))
            .format(Date(file.lastModifiedEpochMs))
    }
    val displayTitle = if (!showExtensions && !file.isDirectory && file.displayName.contains('.')) {
        file.displayName.substringBeforeLast('.')
    } else {
        file.displayName
    }

    Surface(
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            Color.Transparent
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("file_row_${file.displayName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileIconBadge(
                file = file,
                showThumbnails = showThumbnails,
                isSelected = isSelected
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
                    if (isBookmarked) {
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
                    text = listOfNotNull(
                        file.formattedSize,
                        dateStr.takeIf { it.isNotEmpty() },
                        file.permissionsString
                    ).joinToString("  •  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
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
                    DropdownMenuItem(
                        text = { Text(if (isBookmarked) "Remove Bookmark" else "Add Bookmark") },
                        leadingIcon = {
                            Icon(
                                if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
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
    onProperties: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        shape = RoundedCornerShape(12.dp),
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
                isSelected = isSelected
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
                    text = file.formattedSize,
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
            Text(
                text = file.displayName,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = file.uriOrPath,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()

            PropertyKeyValueRow("Provider", file.providerType.label)
            PropertyKeyValueRow("Type / MIME", file.mimeType)
            PropertyKeyValueRow("Size", "${formatByteSize(file.sizeBytes)} (${file.sizeBytes} bytes)")
            PropertyKeyValueRow("Last Modified", modifiedFormatted)
            PropertyKeyValueRow("Permissions", file.permissionsString)

            if (apkInfo != null) {
                HorizontalDivider()
                Text(
                    text = "Android Package (APK) Metadata",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                PropertyKeyValueRow("App Label", apkInfo.appName)
                PropertyKeyValueRow("Package Name", apkInfo.packageName)
                PropertyKeyValueRow("Version", "${apkInfo.versionName} (${apkInfo.versionCode})")
                PropertyKeyValueRow("SDK Target", "minSdk ${apkInfo.minSdk} • targetSdk ${apkInfo.targetSdk}")
                Button(
                    onClick = { onInstallApk(File(file.uriOrPath)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Android, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Install APK via System Package Installer")
                }
            }

            if (!file.isDirectory && file.providerType == ProviderType.LOCAL) {
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
 * Built-in File Preview System supporting Text/Code/JSON/Markdown, Pinch-to-Zoom Images,
 * PDF Page Rendering (`PdfRenderer`), and inline Audio playback (`MediaPlayer`).
 */
@Composable
fun FilePreviewDialog(
    file: FileReference,
    onDismiss: () -> Unit,
    onOpenExternal: (FileReference) -> Unit
) {
    val ext = file.extension.lowercase()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${file.mimeType} • ${file.formattedSize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onOpenExternal(file) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open With")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close preview")
                    }
                }
                HorizontalDivider()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    when {
                        ext in FileCategory.IMAGES.extensions -> {
                            val imgModel = if (file.providerType == ProviderType.LOCAL) File(file.uriOrPath) else file.uriOrPath
                            ZoomableImagePreview(imgModel, file.displayName)
                        }
                        ext == "pdf" -> {
                            PdfDocumentPreview(file)
                        }
                        ext in FileCategory.AUDIO.extensions -> {
                            AudioPlayerPreview(file, file.displayName)
                        }
                        ext in FileCategory.VIDEOS.extensions || file.mimeType.startsWith("video/") -> {
                            VideoPreviewPlaceholder(file = file, onOpenExternal = { onOpenExternal(file) })
                        }
                        else -> {
                            TextAndCodePreview(file)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewPlaceholder(
    file: FileReference,
    onOpenExternal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play Video",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = file.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${file.mimeType} • ${file.formattedSize}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onOpenExternal
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open in Video Player")
        }
    }
}

@Composable
private fun ZoomableImagePreview(imageModel: Any, title: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = title,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}

@Composable
private fun PdfDocumentPreview(file: FileReference) {
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file, currentPageIndex) {
        withContext(Dispatchers.IO) {
            runCatching {
                val pfd = if (file.providerType == ProviderType.LOCAL) {
                    ParcelFileDescriptor.open(File(file.uriOrPath), ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    context.contentResolver.openFileDescriptor(Uri.parse(file.uriOrPath), "r")
                } ?: throw IllegalStateException("Cannot open file descriptor for PDF")
                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        pageCount = renderer.pageCount
                        if (renderer.pageCount > 0) {
                            val idx = currentPageIndex.coerceIn(0, renderer.pageCount - 1)
                            renderer.openPage(idx).use { page ->
                                val bmp = Bitmap.createBitmap(
                                    (page.width * 2).coerceAtMost(2048),
                                    (page.height * 2).coerceAtMost(2048),
                                    Bitmap.Config.ARGB_8888
                                )
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pageBitmap = bmp
                            }
                        }
                    }
                }
            }.onFailure {
                errorText = it.message ?: "Unable to render PDF"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (errorText != null) {
            Text(text = errorText!!, color = MaterialTheme.colorScheme.error)
        } else if (pageBitmap != null) {
            Image(
                bitmap = pageBitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${currentPageIndex + 1}",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                    enabled = currentPageIndex > 0
                ) {
                    Text("Prev Page")
                }
                Text("Page ${currentPageIndex + 1} / $pageCount")
                OutlinedButton(
                    onClick = { if (currentPageIndex < pageCount - 1) currentPageIndex++ },
                    enabled = currentPageIndex < pageCount - 1
                ) {
                    Text("Next Page")
                }
            }
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun AudioPlayerPreview(file: FileReference, title: String) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(file) {
        isPrepared = false
        isPlaying = false
        playerError = null
        val prepared = withContext(Dispatchers.IO) {
            runCatching {
                MediaPlayer().apply {
                    if (file.providerType == ProviderType.LOCAL) {
                        setDataSource(File(file.uriOrPath).absolutePath)
                    } else {
                        setDataSource(context, Uri.parse(file.uriOrPath))
                    }
                    prepare()
                    setOnCompletionListener { isPlaying = false }
                }
            }
        }
        prepared.onSuccess {
            mediaPlayer = it
            isPrepared = true
        }.onFailure {
            playerError = it.message ?: "Unable to prepare audio"
        }
    }

    DisposableEffect(file) {
        onDispose {
            val mp = mediaPlayer
            mediaPlayer = null
            runCatching {
                mp?.stop()
                mp?.release()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(AudioPurple.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AudioFile,
                contentDescription = null,
                tint = AudioPurple,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        if (playerError != null) {
            Text("Audio preview unavailable: $playerError", style = MaterialTheme.typography.bodySmall)
        } else {
            Button(
                onClick = {
                    val mp = mediaPlayer ?: return@Button
                    if (mp.isPlaying) {
                        mp.pause()
                        isPlaying = false
                    } else {
                        mp.start()
                        isPlaying = true
                    }
                }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isPlaying) "Pause Audio" else "Play Audio")
            }
        }
    }
}

@Composable
private fun TextAndCodePreview(file: FileReference) {
    var contentLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (file.providerType == ProviderType.LOCAL) {
                    val targetFile = File(file.uriOrPath)
                    val sampleBuffer = ByteArray(4096)
                    val bytesRead = targetFile.inputStream().use { it.read(sampleBuffer) }
                    val isBinary = bytesRead > 0 && sampleBuffer.take(bytesRead).any { it == 0.toByte() }
                    if (isBinary) {
                        contentLines = listOf("Binary file: Cannot preview as text. Use 'Open With' above to open with an external application.")
                    } else {
                        val raw = targetFile.inputStream().bufferedReader().use { reader ->
                            val lines = mutableListOf<String>()
                            var count = 0
                            var line = reader.readLine()
                            while (line != null && count < 400) {
                                lines.add(line)
                                count++
                                line = reader.readLine()
                            }
                            lines
                        }
                        contentLines = raw.ifEmpty { listOf("(Empty file)") }
                    }
                } else {
                    contentLines = listOf("Remote file preview: Download to local storage to inspect full contents.")
                }
            }.onFailure {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    RoundedCornerShape(10.dp)
                )
                .padding(10.dp)
        ) {
            items(contentLines.size) { index ->
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
                    items(jobs, key = { it.jobId }) { job ->
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
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${formatByteSize(job.transferredBytes)} / ${formatByteSize(job.totalBytes)} (${job.processedFiles}/${job.totalFiles} files)",
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
