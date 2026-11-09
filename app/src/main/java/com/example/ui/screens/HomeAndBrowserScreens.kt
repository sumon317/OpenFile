package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.archive.ArchiveCreationFormat
import com.example.core.archive.ArchiveEngine
import com.example.core.archive.ArchiveEntryItem
import com.example.core.database.BookmarkEntity
import com.example.core.database.NetworkProfileEntity
import com.example.core.database.RecentFileEntity
import com.example.core.datastore.AppSettingsState
import com.example.core.filesystem.ClipboardMode
import com.example.core.filesystem.ClipboardState
import com.example.core.filesystem.FileSortAndFilterEngine
import com.example.core.model.FileCategory
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.example.core.model.SortField
import com.example.core.model.SortOrder
import com.example.core.model.StorageVolumeInfo
import com.example.core.model.ViewMode
import com.example.core.model.formatByteSize
import com.example.core.model.isPathInsideHiddenDirectory
import com.example.core.network.FtpServerRuntimeState
import com.example.core.update.AppUpdateUiState
import com.example.ui.BrowserUiState
import com.example.ui.OpenFileDestination
import com.example.ui.components.BreadcrumbBar
import com.example.ui.components.FileItemGridCard
import com.example.ui.components.FileItemRow
import com.example.ui.theme.ApkGreen
import com.example.ui.theme.FolderAmber
import com.example.ui.theme.StorageDangerCrimson
import com.example.ui.theme.StorageWarningOrange

import androidx.compose.runtime.Immutable

private val VolumeCardShape = RoundedCornerShape(18.dp)
private val VolumeIconShape = RoundedCornerShape(14.dp)
private val VolumeBadgeShape = RoundedCornerShape(8.dp)
private val VolumeBarShape = RoundedCornerShape(4.dp)
private val CategoryCardShape = RoundedCornerShape(16.dp)
private val CategoryIconBadgeShape = RoundedCornerShape(14.dp)
private val RecentFileCardShape = RoundedCornerShape(10.dp)

@Immutable
private data class HomeTileSpec(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val badgeColor: Color,
    val testTag: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    volumes: List<StorageVolumeInfo>,
    bookmarks: List<BookmarkEntity>,
    recentFiles: List<RecentFileEntity>,
    ftpState: FtpServerRuntimeState,
    onVolumeClick: (StorageVolumeInfo) -> Unit,
    onRequestSafAccess: () -> Unit,
    onNavigateDestination: (OpenFileDestination) -> Unit,
    onBookmarkClick: (BookmarkEntity) -> Unit,
    onRecentFileClick: (RecentFileEntity) -> Unit,
    categoryCounts: Map<FileCategory, Int> = emptyMap(),
    remoteProfilesCount: Int = 0,
    remoteProfiles: List<NetworkProfileEntity> = emptyList(),
    activeRemoteProfileId: Long? = null,
    onRemoteProfileClick: (NetworkProfileEntity) -> Unit = {},
    hasAllFilesAccess: Boolean = true,
    onRequestAllFilesAccess: () -> Unit = {},
    onOpenAccessFromPc: () -> Unit = { onNavigateDestination(OpenFileDestination.NETWORK) },
    onOpenRemoteConnections: () -> Unit = { onNavigateDestination(OpenFileDestination.NETWORK) },
    appUpdateState: AppUpdateUiState? = null,
    onOpenUpdateDialog: () -> Unit = {},
    showHiddenFiles: Boolean = false
) {
    val displayRecentFiles = remember(recentFiles, showHiddenFiles) {
        if (showHiddenFiles) recentFiles else recentFiles.filter { !it.displayName.startsWith(".") && !isPathInsideHiddenDirectory(it.uriOrPath) }
    }
    val gridTiles = remember(categoryCounts, remoteProfilesCount, ftpState.isRunning) {
        fun countLabel(cat: FileCategory, fallback: String): String {
            val c = categoryCounts[cat]
            return if (c != null) "$c items" else fallback
        }
        listOf(
            HomeTileSpec(
                label = "New Files",
                subtitle = countLabel(FileCategory.RECENT_FILES, "Recent"),
                icon = Icons.Filled.History,
                badgeColor = Color(0xFF29B6F6),
                testTag = "shortcut_${OpenFileDestination.RECENT_FILES.route}",
                onClick = { onNavigateDestination(OpenFileDestination.RECENT_FILES) }
            ),
            HomeTileSpec(
                label = "Downloads",
                subtitle = countLabel(FileCategory.DOWNLOADS, "Folder"),
                icon = Icons.Filled.Download,
                badgeColor = Color(0xFF26A69A),
                testTag = "shortcut_${OpenFileDestination.DOWNLOADS.route}",
                onClick = { onNavigateDestination(OpenFileDestination.DOWNLOADS) }
            ),
            HomeTileSpec(
                label = "Documents",
                subtitle = countLabel(FileCategory.DOCUMENTS, "PDF & Office"),
                icon = Icons.Filled.Description,
                badgeColor = Color(0xFF42A5F5),
                testTag = "shortcut_${OpenFileDestination.DOCUMENTS.route}",
                onClick = { onNavigateDestination(OpenFileDestination.DOCUMENTS) }
            ),
            HomeTileSpec(
                label = "Images",
                subtitle = countLabel(FileCategory.IMAGES, "Photos"),
                icon = Icons.Filled.Image,
                badgeColor = Color(0xFFFF7043),
                testTag = "shortcut_${OpenFileDestination.IMAGES.route}",
                onClick = { onNavigateDestination(OpenFileDestination.IMAGES) }
            ),
            HomeTileSpec(
                label = "Audio",
                subtitle = countLabel(FileCategory.AUDIO, "Music"),
                icon = Icons.Filled.AudioFile,
                badgeColor = Color(0xFFAB47BC),
                testTag = "shortcut_${OpenFileDestination.AUDIO.route}",
                onClick = { onNavigateDestination(OpenFileDestination.AUDIO) }
            ),
            HomeTileSpec(
                label = "Videos",
                subtitle = countLabel(FileCategory.VIDEOS, "Movies"),
                icon = Icons.Filled.VideoFile,
                badgeColor = Color(0xFFEF5350),
                testTag = "shortcut_${OpenFileDestination.VIDEOS.route}",
                onClick = { onNavigateDestination(OpenFileDestination.VIDEOS) }
            ),
            HomeTileSpec(
                label = "Apps & APKs",
                subtitle = categoryCounts[FileCategory.APPLICATIONS]?.let { "$it apps" } ?: "Installed",
                icon = Icons.Filled.Android,
                badgeColor = ApkGreen,
                testTag = "shortcut_${OpenFileDestination.APPLICATIONS.route}",
                onClick = { onNavigateDestination(OpenFileDestination.APPLICATIONS) }
            ),
            HomeTileSpec(
                label = "Archives",
                subtitle = countLabel(FileCategory.ARCHIVES, "ZIP / 7Z"),
                icon = Icons.Filled.FolderZip,
                badgeColor = FolderAmber,
                testTag = "shortcut_${OpenFileDestination.ARCHIVES.route}",
                onClick = { onNavigateDestination(OpenFileDestination.ARCHIVES) }
            ),
            HomeTileSpec(
                label = "Large Files",
                subtitle = countLabel(FileCategory.LARGE_FILES, "> 25 MB"),
                icon = Icons.Filled.Storage,
                badgeColor = Color(0xFFEC407A),
                testTag = "shortcut_${OpenFileDestination.LARGE_FILES.route}",
                onClick = { onNavigateDestination(OpenFileDestination.LARGE_FILES) }
            ),
            HomeTileSpec(
                label = "Remote",
                subtitle = if (remoteProfilesCount > 0) "$remoteProfilesCount server(s)" else "SMB / SFTP / FTP",
                icon = Icons.Filled.Lan,
                badgeColor = Color(0xFFFFA726),
                testTag = "home_network_button",
                onClick = onOpenRemoteConnections
            ),
            HomeTileSpec(
                label = "Access from PC",
                subtitle = if (ftpState.isRunning) "Server Active" else "Wi-Fi FTP",
                icon = Icons.Filled.Computer,
                badgeColor = Color(0xFF00ACC1),
                testTag = "home_access_pc_button",
                onClick = onOpenAccessFromPc
            ),
            HomeTileSpec(
                label = "Storage Analysis",
                subtitle = "Space & Dups",
                icon = Icons.Filled.Analytics,
                badgeColor = Color(0xFF7E57C2),
                testTag = "shortcut_${OpenFileDestination.STORAGE_ANALYSIS.route}",
                onClick = { onNavigateDestination(OpenFileDestination.STORAGE_ANALYSIS) }
            )
        ).chunked(3)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Full Storage Access (MANAGE_EXTERNAL_STORAGE) Banner when not yet granted on Android 11+
        if (!hasAllFilesAccess) {
            item(key = "all_files_access_banner", contentType = "permission_banner") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = VolumeCardShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("all_files_access_banner")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Full File System Access Required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = "Android hides non-media files (PDFs, ZIPs, APKs, Documents, and folders) unless 'All files access' is enabled. Grant access to see your complete file system.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = onRequestAllFilesAccess,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_all_files_access_button")
                        ) {
                            Text("Grant All Files Access")
                        }
                    }
                }
            }
        }

        // In-App GitHub Release Update Available Banner
        if (appUpdateState != null && appUpdateState.updateAvailable && appUpdateState.latestRelease != null) {
            val rel = appUpdateState.latestRelease
            item(key = "github_update_banner", contentType = "update_banner") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = VolumeCardShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenUpdateDialog() }
                        .testTag("home_update_available_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Update Available: ${rel.tagName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Tap to download & install directly from GitHub Releases (${formatByteSize(rel.apkSizeBytes)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onOpenUpdateDialog,
                            modifier = Modifier.testTag("home_update_now_button")
                        ) {
                            Text("Update")
                        }
                    }
                }
            }
        }

        // Active Wi-Fi FTP Server Status Banner if running
        if (ftpState.isRunning) {
            item(key = "ftp_active_banner", contentType = "ftp_banner") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAccessFromPc() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Computer, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Access from PC Active",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = ftpState.connectionUrl,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        Text("Manage", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Section 1: Storage Volumes (File Manager + Top Storage Cards)
        item(key = "storage_volumes_header", contentType = "section_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Storage Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                TextButton(
                    onClick = { onNavigateDestination(OpenFileDestination.STORAGE_ANALYSIS) },
                    modifier = Modifier.testTag("home_analyzer_button")
                ) {
                    Icon(Icons.Filled.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Analyze")
                }
            }
        }

        items(
            items = volumes,
            key = { it.id },
            contentType = { "volume_card" }
        ) { vol ->
            val barColor = when {
                vol.usageFraction > 0.88f -> StorageDangerCrimson
                vol.usageFraction > 0.75f -> StorageWarningOrange
                else -> MaterialTheme.colorScheme.primary
            }
            Card(
                onClick = {
                    if (vol.isSafGrantRevoked) onRequestSafAccess() else onVolumeClick(vol)
                },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                shape = VolumeCardShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("volume_card_${vol.id}")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val configuration = LocalConfiguration.current
                    val screenWidthDp = configuration.screenWidthDp
                    val deviceTitle = remember(vol.title, screenWidthDp) {
                        adaptiveStorageDeviceName(vol, screenWidthDp)
                    }
                    val titleStyle = when {
                        screenWidthDp < 360 -> MaterialTheme.typography.titleSmall
                        screenWidthDp >= 600 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (vol.isSafGrantRevoked) MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                                        else barColor.copy(alpha = 0.16f),
                                        VolumeIconShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (vol.isSafGrantRevoked) Icons.Filled.Warning else Icons.Filled.Storage,
                                    contentDescription = null,
                                    tint = if (vol.isSafGrantRevoked) MaterialTheme.colorScheme.error else barColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = deviceTitle,
                                    style = titleStyle,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = vol.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (vol.isSafGrantRevoked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = barColor.copy(alpha = 0.14f),
                            shape = VolumeBadgeShape
                        ) {
                            Text(
                                text = "${(vol.usageFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = barColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { vol.usageFraction },
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(VolumeBarShape)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatByteSize(vol.usedBytes)} / ${formatByteSize(vol.totalBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${formatByteSize(vol.availableBytes)} free",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Saved network devices (FTP / SFTP / SMB) are listed with the storage devices for one-tap access.
        items(
            items = remoteProfiles,
            key = { "remote_profile_${it.id}" },
            contentType = { "remote_device_card" }
        ) { profile ->
            val isConnected = profile.id == activeRemoteProfileId
            val accent = if (isConnected) ApkGreen else MaterialTheme.colorScheme.primary
            Card(
                onClick = { onRemoteProfileClick(profile) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                shape = VolumeCardShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("remote_device_card_${profile.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(accent.copy(alpha = 0.16f), VolumeIconShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lan,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${profile.protocol} • ${profile.host}:${profile.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = accent.copy(alpha = 0.14f),
                        shape = VolumeBadgeShape
                    ) {
                        Text(
                            text = if (isConnected) "Connected" else "Tap to open",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Section 2: File Manager + Symmetrical 3-Column Circular-Badge Grid
        item(key = "home_categories_grid", contentType = "categories_grid") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Categories & Quick Access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                gridTiles.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { tile ->
                            CategoryShortcutCard(
                                label = tile.label,
                                subtitle = tile.subtitle,
                                icon = tile.icon,
                                badgeColor = tile.badgeColor,
                                onClick = tile.onClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(tile.testTag)
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Bookmarks
        if (bookmarks.isNotEmpty()) {
            item(key = "pinned_bookmarks_section", contentType = "bookmarks_section") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pinned Bookmarks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { onNavigateDestination(OpenFileDestination.BOOKMARKS) }) {
                        Text("View All")
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    bookmarks.take(8).forEach { bm ->
                        AssistChip(
                            onClick = { onBookmarkClick(bm) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Bookmark,
                                    contentDescription = null,
                                    tint = FolderAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(bm.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        }

        // Section 5: Recently Accessed Files
        item(key = "recent_files_header", contentType = "section_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recently Opened Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (displayRecentFiles.isNotEmpty()) {
                    TextButton(onClick = { onNavigateDestination(OpenFileDestination.RECENT_FILES) }) {
                        Text("More")
                    }
                }
            }
        }

        if (displayRecentFiles.isEmpty()) {
            item(key = "recent_files_empty", contentType = "empty_hint") {
                Text(
                    text = "Files you open or inspect will appear here for one-tap access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(
                items = displayRecentFiles.take(8),
                key = { it.uriOrPath },
                contentType = { "recent_file" }
            ) { recent ->
                Surface(
                    onClick = { onRecentFileClick(recent) },
                    shape = RecentFileCardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recent.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatByteSize(recent.sizeBytes)} • ${recent.providerType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryShortcutCard(
    label: String,
    subtitle: String,
    icon: ImageVector,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CategoryCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(badgeColor.copy(alpha = 0.18f), CategoryIconBadgeShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = badgeColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Resolves an adaptive display name for a storage device based on the available screen width.
 * - Compact screens (<360dp, small phones / split-screen): "Main Storage", "SD Card", "USB Drive"
 * - Medium screens (360dp - 599dp, standard phones): "Internal Storage", "SD Card (ID)", "USB Drive"
 * - Expanded screens (>=600dp, tablets / foldables unfolded / desktop): Full descriptive names ("Internal Shared Storage", etc.)
 */
fun adaptiveStorageDeviceName(volume: StorageVolumeInfo, screenWidthDp: Int): String {
    val raw = volume.title
    return when {
        screenWidthDp < 360 -> {
            when {
                raw.contains("Internal", ignoreCase = true) -> "Main Storage"
                raw.contains("SD Card", ignoreCase = true) || raw.contains("MicroSD", ignoreCase = true) -> "SD Card"
                raw.contains("USB", ignoreCase = true) || raw.contains("OTG", ignoreCase = true) -> "USB Drive"
                else -> raw
            }
        }
        screenWidthDp < 600 -> {
            when {
                raw.equals("Internal Shared Storage", ignoreCase = true) -> "Internal Storage"
                raw.startsWith("Physical SD Card", ignoreCase = true) -> raw.replace("Physical ", "")
                raw.contains("USB Storage", ignoreCase = true) -> "USB Drive"
                else -> raw
            }
        }
        else -> {
            raw
        }
    }
}

/**
 * Full-featured File Browser screen with Breadcrumbs, Search & Filter chips, List/Grid toggle,
 * Multi-selection bar, Archive virtual entry inspector, and New Folder/File/Archive dialogs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileBrowserScreen(
    browserState: BrowserUiState,
    settings: AppSettingsState,
    bookmarks: List<BookmarkEntity>,
    clipboard: ClipboardState,
    onCrumbClick: (FileReference) -> Unit,
    onFileClick: (FileReference) -> Unit,
    onFileLongClick: (FileReference) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleViewMode: (ViewMode) -> Unit,
    onChangeSort: (SortField, SortOrder) -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateTextFile: (String, String) -> Unit,
    onRenameItem: (FileReference, String) -> Unit,
    onCopyItems: (List<FileReference>, ClipboardMode) -> Unit,
    onPasteHere: () -> Unit,
    onClearClipboard: () -> Unit,
    onDeleteItems: (List<FileReference>, Boolean) -> Unit,
    onToggleBookmark: (FileReference) -> Unit,
    onShareItem: (FileReference) -> Unit,
    onOpenProperties: (FileReference) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onCompressSelected: (String, ArchiveCreationFormat) -> Unit,
    onExtractArchive: (FileReference, Set<String>?) -> Unit,
    onCloseArchiveInspector: () -> Unit,
    onDownloadRemoteItem: (FileReference) -> Unit,
    onOpenWithExternal: ((FileReference) -> Unit)? = null,
    onQuickPreview: ((FileReference) -> Unit)? = null,
    onOpenParentFolder: ((FileReference) -> Unit)? = null,
    onOpenLocalRoot: (() -> Unit)? = null,
    onPreviewArchiveEntry: ((FileReference, ArchiveEntryItem) -> Unit)? = null,
    onOpenArchiveSubFolder: ((String) -> Unit)? = null,
    onViewArchive: ((FileReference) -> Unit)? = null,
    hasAllFilesAccess: Boolean = true,
    onRequestAllFilesAccess: (() -> Unit)? = null,
    onToggleShowHidden: (() -> Unit)? = null,
    onRequestSafForPath: ((String?) -> Unit)? = null
) {
    if (browserState.inspectArchiveFile != null) {
        NativeZipViewer(
            archiveRef = browserState.inspectArchiveFile,
            entries = browserState.archiveEntries,
            currentSubPath = browserState.archiveSubPath,
            onNavigateUp = onCloseArchiveInspector,
            onOpenSubFolder = { onOpenArchiveSubFolder?.invoke(it) },
            onPreviewEntry = { entry -> onPreviewArchiveEntry?.invoke(browserState.inspectArchiveFile, entry) },
            onExtractArchive = onExtractArchive,
            onClose = onCloseArchiveInspector
        )
        return
    }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileReference?>(null) }
    var deleteConfirmTargets by remember { mutableStateOf<List<FileReference>?>(null) }
    var extensionFilter by remember { mutableStateOf<String?>(null) }

    val bookmarkedPaths = remember(bookmarks) { bookmarks.map { it.uriOrPath }.toHashSet() }

    // Fast path: skip filter engine entirely when there's no active query or extension filter
    val filteredItems = remember(
        browserState.items,
        browserState.searchQuery,
        extensionFilter,
        settings.showHiddenFiles
    ) {
        val hasQuery = browserState.searchQuery.isNotBlank()
        val hasExtFilter = extensionFilter != null
        val showHidden = settings.showHiddenFiles
        when {
            !hasQuery && !hasExtFilter && showHidden -> browserState.items
            !hasQuery && !hasExtFilter -> browserState.items.filter { !it.isHidden }
            else -> FileSortAndFilterEngine.filterFiles(
                files = browserState.items,
                query = browserState.searchQuery,
                extensionFilter = extensionFilter,
                showHidden = showHidden
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Action & Search Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            OutlinedTextField(
                value = browserState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (browserState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("browser_search_input")
            )

            // View Mode Toggle
            IconButton(
                onClick = {
                    onToggleViewMode(if (settings.defaultViewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                },
                modifier = Modifier.size(38.dp).testTag("browser_toggle_view_mode")
            ) {
                Icon(
                    imageVector = if (settings.defaultViewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = "Toggle List or Grid view",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Sort Menu
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.size(38.dp).testTag("browser_sort_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort files", modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    val activeSortField = browserState.currentSortField
                    val activeSortOrder = browserState.currentSortOrder
                    SortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = {
                                val active = activeSortField == field
                                Text(
                                    text = if (active) "✓ Sort by ${field.label}" else "Sort by ${field.label}",
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                showSortMenu = false
                                onChangeSort(field, activeSortOrder)
                            }
                        )
                    }
                    HorizontalDivider()
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = {
                                val active = activeSortOrder == order
                                Text(
                                    text = if (active) "✓ ${order.label}" else order.label,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                showSortMenu = false
                                onChangeSort(activeSortField, order)
                            }
                        )
                    }
                    if (onToggleShowHidden != null) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (settings.showHiddenFiles) "✓ Show Hidden Files (.dot)" else "Show Hidden Files (.dot)",
                                    fontWeight = if (settings.showHiddenFiles) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                showSortMenu = false
                                onToggleShowHidden()
                            }
                        )
                    }
                }
            }

            if (browserState.currentDirectory != null) {
                IconButton(
                    onClick = { showNewFolderDialog = true },
                    modifier = Modifier.size(38.dp).testTag("browser_new_folder_button")
                ) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder", modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = { showNewFileDialog = true },
                    modifier = Modifier.size(38.dp).testTag("browser_new_file_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "New Text File", modifier = Modifier.size(20.dp))
                }
            }
        }

        // Breadcrumb Navigation Bar or Virtual Category Context Bar
        if (browserState.breadcrumbs.isNotEmpty()) {
            BreadcrumbBar(
                breadcrumbs = browserState.breadcrumbs,
                providerType = browserState.currentDirectory?.providerType,
                onCrumbClick = onCrumbClick
            )
        } else if (browserState.activeCategoryFilter != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("category_context_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Category: ${browserState.activeCategoryFilter.title}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val count = filteredItems.size
                    Text(
                        text = "$count ${if (count == 1) "item" else "items"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Quick extension filter chips when searching
        if (browserState.searchQuery.isNotEmpty() || extensionFilter != null) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("pdf", "zip", "jpg", "mp4", "mp3", "txt", "json", "apk").forEach { ext ->
                    FilterChip(
                        selected = extensionFilter == ext,
                        onClick = { extensionFilter = if (extensionFilter == ext) null else ext },
                        label = { Text(".$ext") }
                    )
                }
            }
        }

        // Multi-Selection Top Header Strip
        if (browserState.isMultiSelectActive) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                        val selectedCount = browserState.selectedUris.size
                        Text(
                            text = "$selectedCount ${if (selectedCount == 1) "item" else "items"} selected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onSelectAll,
                            modifier = Modifier.testTag("multiselect_select_all")
                        ) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select All")
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        if (!hasAllFilesAccess &&
            browserState.activeRemoteProfile == null &&
            onRequestAllFilesAccess != null
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("browser_all_files_access_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Grant 'All files access' to view all non-media files & folders",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestAllFilesAccess,
                        modifier = Modifier.testTag("browser_grant_all_files_button")
                    ) {
                        Text("Allow", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Restricted /Android/obb and /Android/data helper banner with 1-tap SAF mount
        val currentLocalPath = browserState.currentDirectory?.uriOrPath.orEmpty().replace('\\', '/')
        val isInsideRestrictedAndroidDir = browserState.currentDirectory?.providerType == ProviderType.LOCAL &&
            (currentLocalPath.endsWith("/Android/obb", ignoreCase = true) ||
                currentLocalPath.contains("/Android/obb/", ignoreCase = true) ||
                currentLocalPath.endsWith("/Android/data", ignoreCase = true) ||
                currentLocalPath.contains("/Android/data/", ignoreCase = true))

        if (isInsideRestrictedAndroidDir && onRequestSafForPath != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("browser_restricted_saf_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Android Scoped Storage: ${browserState.currentDirectory?.displayName}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Select an app folder below or tap 'Mount SAF' to grant direct folder access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { onRequestSafForPath(browserState.currentDirectory?.uriOrPath) },
                        modifier = Modifier.testTag("browser_mount_saf_button")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mount SAF", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }



        // Main File List or Grid + Optional Two-Pane Tablet Inspector
        val isWideDualPane = LocalConfiguration.current.screenWidthDp >= 700
        val context = androidx.compose.ui.platform.LocalContext.current
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val singleSelectedItem = remember(isWideDualPane, browserState.selectedUris, filteredItems) {
                if (isWideDualPane && browserState.selectedUris.size == 1) {
                    val targetUri = browserState.selectedUris.first()
                    filteredItems.firstOrNull { it.uriOrPath == targetUri }
                } else null
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (browserState.isLoading && filteredItems.isNotEmpty()) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.TopCenter),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    when {
                        browserState.isLoading && filteredItems.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        browserState.errorMessage != null -> {
                            val errMsg = browserState.errorMessage
                            val isSafRequired = errMsg.startsWith("SAF_REQUIRED:")
                            val targetRestrictedPath = if (isSafRequired) {
                                errMsg.removePrefix("SAF_REQUIRED:")
                            } else {
                                browserState.currentDirectory?.uriOrPath
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSafRequired) Icons.Filled.FolderOpen else Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = if (isSafRequired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (isSafRequired) {
                                        "Unlock '${browserState.currentDirectory?.displayName ?: "App Folder"}'"
                                    } else {
                                        errMsg
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSafRequired) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                )
                                if (isSafRequired) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Android 11+ requires a one-time Storage Access Framework (SAF) confirmation for this app's OBB/Data folder. Tap below and select 'Use this folder'.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (onRequestSafForPath != null) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Button(
                                            onClick = { onRequestSafForPath(targetRestrictedPath) },
                                            modifier = Modifier.testTag("grant_targeted_saf_button")
                                        ) {
                                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Grant Folder Access (SAF)")
                                        }
                                    }
                                }
                            }
                        }
                        filteredItems.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (browserState.searchQuery.isNotEmpty()) {
                                        "No files match '${browserState.searchQuery}'"
                                    } else {
                                        "This folder is empty"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (browserState.currentDirectory != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(onClick = { showNewFolderDialog = true }) {
                                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create First Folder")
                                    }
                                }
                            }
                        }
                        settings.defaultViewMode == ViewMode.GRID -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 140.dp),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize().testTag("browser_grid_view")
                            ) {
                                items(
                                    items = filteredItems,
                                    key = { it.uriOrPath },
                                    contentType = { if (it.isDirectory) "dir" else "file" }
                                ) { item ->
                                    FileItemGridCard(
                                        file = item,
                                        isSelected = item.uriOrPath in browserState.selectedUris,
                                        showThumbnails = settings.showThumbnails,
                                        onClick = { onFileClick(item) },
                                        onLongClick = { onFileLongClick(item) },
                                        onProperties = { onOpenProperties(item) },
                                        context = context
                                    )
                                }
                            }
                        }
                        else -> {
                            val isRemote = browserState.activeRemoteProfile != null
                            LazyColumn(modifier = Modifier.fillMaxSize().testTag("browser_list_view")) {
                                items(
                                    items = filteredItems,
                                    key = { it.uriOrPath },
                                    contentType = { if (it.isDirectory) "dir" else "file" }
                                ) { item ->
                                    FileItemRow(
                                        file = item,
                                        isSelected = item.uriOrPath in browserState.selectedUris,
                                        isBookmarked = item.uriOrPath in bookmarkedPaths,
                                        showThumbnails = settings.showThumbnails,
                                        showExtensions = settings.showFileExtensions,
                                        onClick = { onFileClick(item) },
                                        onLongClick = { onFileLongClick(item) },
                                        onRename = { renameTarget = item },
                                        onCopy = { onCopyItems(listOf(item), ClipboardMode.COPY) },
                                        onCut = { onCopyItems(listOf(item), ClipboardMode.CUT) },
                                        onDelete = {
                                            if (settings.confirmBeforeDelete) {
                                                deleteConfirmTargets = listOf(item)
                                            } else {
                                                val isSecondary = item.uriOrPath.startsWith("/storage/") && !item.uriOrPath.startsWith("/storage/emulated")
                                                onDeleteItems(listOf(item), settings.moveToTrashByDefault && !isSecondary)
                                            }
                                        },
                                        onToggleBookmark = { onToggleBookmark(item) },
                                        onShare = { onShareItem(item) },
                                        onProperties = { onOpenProperties(item) },
                                        onDownloadRemote = if (isRemote) ({ onDownloadRemoteItem(item) }) else null,
                                        onOpenWith = if (onOpenWithExternal != null && (browserState.activeRemoteProfile == null)) ({ onOpenWithExternal(item) }) else null,
                                        onQuickPreview = if (onQuickPreview != null) ({ onQuickPreview(item) }) else null,
                                        onOpenParentFolder = if (browserState.currentDirectory == null && onOpenParentFolder != null) ({ onOpenParentFolder(item) }) else null,
                                        onViewArchive = if (onViewArchive != null && (item.extension.lowercase() in setOf("zip", "tar", "gz", "xz", "7z", "jar", "apk", "tgz", "txz") || item.displayName.endsWith(".zip", ignoreCase = true) || item.displayName.endsWith(".tar", ignoreCase = true) || item.displayName.endsWith(".7z", ignoreCase = true))) ({ onViewArchive(item) }) else null,
                                        showParentPath = browserState.currentDirectory == null,
                                        context = context
                                    )
                                }
                            }
                        }
                    }
                }

                // Canonical Tablet Two-Pane Inspector Side Panel when wide and an item is selected
                if (isWideDualPane && singleSelectedItem != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .testTag("tablet_inspector_pane")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Inspector",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = singleSelectedItem.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = singleSelectedItem.mimeType,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider()
                            Text(
                                text = "Size: ${singleSelectedItem.formattedSize}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Permissions: ${singleSelectedItem.permissionsString}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Provider: ${singleSelectedItem.providerType.label}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            OutlinedButton(
                                onClick = { onOpenProperties(singleSelectedItem) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Full Hashes & Info")
                            }
                        }
                    }
                }
            }
        }

        // File Manager + Contextual Bottom Selection Toolbar (Copy, Move, Rename, Delete, Share, Compress)
        if (browserState.isMultiSelectActive) {
            // Use the already-computed filteredItemsByUri map for O(1) lookup instead of O(n) filter
            val selectedItemsList = remember(browserState.items, browserState.selectedUris) {
                browserState.items.filter { it.uriOrPath in browserState.selectedUris }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bottom_selection_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomActionBarItem(
                        icon = Icons.Filled.ContentCopy,
                        label = "Copy",
                        testTag = "multiselect_copy",
                        onClick = { onCopyItems(selectedItemsList, ClipboardMode.COPY) }
                    )
                    BottomActionBarItem(
                        icon = Icons.Filled.ContentCut,
                        label = "Move",
                        testTag = "multiselect_cut",
                        onClick = { onCopyItems(selectedItemsList, ClipboardMode.CUT) }
                    )
                    if (selectedItemsList.size == 1) {
                        BottomActionBarItem(
                            icon = Icons.Filled.Edit,
                            label = "Rename",
                            testTag = "multiselect_rename",
                            onClick = { renameTarget = selectedItemsList.first() }
                        )
                    }
                    BottomActionBarItem(
                        icon = Icons.Filled.Delete,
                        label = "Delete",
                        testTag = "multiselect_delete",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            if (settings.confirmBeforeDelete) {
                                deleteConfirmTargets = selectedItemsList
                            } else {
                                val hasSecondary = selectedItemsList.any { it.uriOrPath.startsWith("/storage/") && !it.uriOrPath.startsWith("/storage/emulated") }
                                onDeleteItems(selectedItemsList, settings.moveToTrashByDefault && !hasSecondary)
                            }
                        }
                    )
                    if (selectedItemsList.size == 1) {
                        BottomActionBarItem(
                            icon = Icons.Filled.Share,
                            label = "Share",
                            testTag = "multiselect_share",
                            onClick = { onShareItem(selectedItemsList.first()) }
                        )
                    }
                    BottomActionBarItem(
                        icon = Icons.Filled.FolderZip,
                        label = "Compress",
                        testTag = "multiselect_compress",
                        onClick = { showCompressDialog = true }
                    )
                }
            }
        }

        // File Manager + Persistent Bottom Clipboard Paste Bar
        if (clipboard.isNotEmpty && browserState.currentDirectory != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bottom_clipboard_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val clipCount = clipboard.items.size
                    Text(
                        text = "$clipCount ${if (clipCount == 1) "item" else "items"} ready to ${if (clipboard.mode == ClipboardMode.CUT) "move" else "copy"}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onClearClipboard) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = onPasteHere,
                            modifier = Modifier.testTag("browser_paste_button")
                        ) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste Here")
                        }
                    }
                }
            }
        }

        // KDE Dolphin-Style Directory Summary Footer Bar
        val dirSummary = remember(filteredItems) {
            var folders = 0
            var files = 0
            var totalBytes = 0L
            for (item in filteredItems) {
                if (item.isDirectory) folders++ else {
                    files++
                    totalBytes += item.sizeBytes
                }
            }
            Triple(folders, files, totalBytes)
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("status_footer_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val folderSummary = when (dirSummary.first) {
                    0 -> null
                    1 -> "1 folder"
                    else -> "${dirSummary.first} folders"
                }
                val fileSummary = when (dirSummary.second) {
                    1 -> "1 file"
                    else -> "${dirSummary.second} files"
                }
                val totalSummary = listOfNotNull(folderSummary, fileSummary).joinToString(", ") + " • ${formatByteSize(dirSummary.third)}"
                Text(
                    text = totalSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${browserState.currentSortField.label} (${if (browserState.currentSortOrder == SortOrder.ASCENDING) "↑" else "↓"})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // Create Folder Dialog
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.testTag("dialog_folder_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            onCreateFolder(folderName)
                            showNewFolderDialog = false
                        }
                    },
                    modifier = Modifier.testTag("dialog_confirm_create_folder")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Create Text File Dialog
    if (showNewFileDialog) {
        var fileName by remember { mutableStateOf("note.txt") }
        var fileContent by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Create New Text File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("File name (e.g. notes.md)") },
                        singleLine = true,
                        modifier = Modifier.testTag("dialog_file_name_input")
                    )
                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        label = { Text("Initial text content") },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fileName.isNotBlank()) {
                            onCreateTextFile(fileName, fileContent)
                            showNewFileDialog = false
                        }
                    },
                    modifier = Modifier.testTag("dialog_confirm_create_file")
                ) {
                    Text("Save File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Rename Dialog
    if (renameTarget != null) {
        val target = renameTarget!!
        var updatedName by remember(target) { mutableStateOf(target.displayName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Item") },
            text = {
                OutlinedTextField(
                    value = updatedName,
                    onValueChange = { updatedName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.testTag("dialog_rename_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (updatedName.isNotBlank()) {
                            onRenameItem(target, updatedName)
                            renameTarget = null
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Compress Archive Dialog
    if (showCompressDialog) {
        var archiveBaseName by remember { mutableStateOf("Archive_${System.currentTimeMillis() / 1000}") }
        var selectedFormat by remember { mutableStateOf(ArchiveCreationFormat.ZIP) }
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("Create Archive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = archiveBaseName,
                        onValueChange = { archiveBaseName = it },
                        label = { Text("Archive name") },
                        singleLine = true
                    )
                    Text("Format (7Z is extract-only on mobile):", style = MaterialTheme.typography.labelMedium)
                    ArchiveCreationFormat.entries.forEach { fmt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFormat = fmt }
                                .padding(vertical = 4.dp)
                        ) {
                            FilterChip(
                                selected = selectedFormat == fmt,
                                onClick = { selectedFormat = fmt },
                                label = { Text(fmt.label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCompressSelected(archiveBaseName, selectedFormat)
                        showCompressDialog = false
                    }
                ) {
                    Text("Compress")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompressDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete Confirmation Dialog
    if (deleteConfirmTargets != null) {
        val targets = deleteConfirmTargets!!
        val isAllLocalOrSaf = targets.all { it.providerType == ProviderType.LOCAL || it.providerType == ProviderType.SAF }
        val hasSecondaryOrRemote = targets.any {
            it.providerType != ProviderType.LOCAL && it.providerType != ProviderType.SAF ||
                (it.providerType == ProviderType.LOCAL && it.uriOrPath.startsWith("/storage/") && !it.uriOrPath.startsWith("/storage/emulated"))
        }
        var useTrash by remember(deleteConfirmTargets, settings.moveToTrashByDefault) {
            mutableStateOf(settings.moveToTrashByDefault && isAllLocalOrSaf && !hasSecondaryOrRemote)
        }
        val count = targets.size
        val totalSize = formatByteSize(targets.sumOf { it.sizeBytes })

        AlertDialog(
            onDismissRequest = { deleteConfirmTargets = null },
            icon = {
                Icon(
                    imageVector = if (useTrash) Icons.Filled.Delete else Icons.Filled.DeleteForever,
                    contentDescription = null,
                    tint = if (useTrash) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = when {
                        useTrash && count == 1 -> "Move to Recycle Bin?"
                        useTrash -> "Move $count items to Recycle Bin?"
                        count == 1 -> "Delete \"${targets.first().displayName}\"?"
                        else -> "Delete $count items directly?"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = when {
                            useTrash && count == 1 -> "\"${targets.first().displayName}\" ($totalSize) will be moved to the Recycle Bin. You can restore it anytime."
                            useTrash -> "$count items ($totalSize) will be moved to the Recycle Bin. You can restore them anytime."
                            count == 1 -> "\"${targets.first().displayName}\" ($totalSize) will be deleted directly and permanently. This action cannot be undone."
                            else -> "$count items ($totalSize) will be deleted directly and permanently. This action cannot be undone."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasSecondaryOrRemote) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Items on SD Card / USB storage skip Recycle Bin and are deleted directly.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    } else if (isAllLocalOrSaf) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (useTrash) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (useTrash) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { useTrash = !useTrash }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = useTrash,
                                    onCheckedChange = { useTrash = it }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Move to Recycle Bin",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (useTrash) "Files can be recovered from Trash later" else "Skip Recycle Bin and delete directly (instant)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (useTrash) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteItems(targets, useTrash)
                        deleteConfirmTargets = null
                    },
                    colors = if (useTrash) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    },
                    modifier = Modifier.testTag("dialog_confirm_delete")
                ) {
                    Text(if (useTrash) "Move to Trash" else "Delete Directly")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTargets = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BottomActionBarItem(
    icon: ImageVector,
    label: String,
    testTag: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

/**
 * Model representing an entry (direct file, explicit directory, or inferred folder)
 * inside a zip/archive for hierarchical or flat browsing.
 */
data class ArchiveVirtualItem(
    val entryPath: String,
    val displayName: String,
    val isDirectory: Boolean,
    val uncompressedSize: Long,
    val compressedSize: Long,
    val lastModifiedEpochMs: Long,
    val isSuspiciousPath: Boolean,
    val childCount: Int = 0,
    val originalEntry: ArchiveEntryItem? = null
)

fun resolveArchiveVirtualItems(
    entries: List<ArchiveEntryItem>,
    subPath: String,
    searchQuery: String,
    isFlatView: Boolean
): List<ArchiveVirtualItem> {
    if (searchQuery.isNotBlank()) {
        val q = searchQuery.trim().lowercase()
        return entries
            .filter { it.displayName.lowercase().contains(q) || it.entryPath.lowercase().contains(q) }
            .map { entry ->
                ArchiveVirtualItem(
                    entryPath = entry.entryPath,
                    displayName = entry.displayName.ifEmpty { entry.entryPath },
                    isDirectory = entry.isDirectory,
                    uncompressedSize = entry.uncompressedSize,
                    compressedSize = entry.compressedSize,
                    lastModifiedEpochMs = entry.lastModifiedEpochMs,
                    isSuspiciousPath = entry.isSuspiciousPath,
                    originalEntry = entry
                )
            }
    }

    if (isFlatView) {
        return entries.map { entry ->
            ArchiveVirtualItem(
                entryPath = entry.entryPath,
                displayName = entry.displayName.ifEmpty { entry.entryPath },
                isDirectory = entry.isDirectory,
                uncompressedSize = entry.uncompressedSize,
                compressedSize = entry.compressedSize,
                lastModifiedEpochMs = entry.lastModifiedEpochMs,
                isSuspiciousPath = entry.isSuspiciousPath,
                originalEntry = entry
            )
        }
    }

    // Hierarchical Folder Navigation
    val cleanSub = subPath.trim().trim('/')
    val prefix = if (cleanSub.isEmpty()) "" else "$cleanSub/"

    val folderChildrenCount = mutableMapOf<String, Int>()
    val folderMaxModified = mutableMapOf<String, Long>()
    val directFiles = mutableListOf<ArchiveVirtualItem>()
    val explicitFolders = mutableMapOf<String, ArchiveEntryItem>()

    for (entry in entries) {
        val cleanEntry = entry.entryPath.replace('\\', '/').trimStart('/')
        if (prefix.isNotEmpty() && !cleanEntry.startsWith(prefix)) {
            continue
        }
        val rel = if (prefix.isEmpty()) cleanEntry else cleanEntry.removePrefix(prefix)
        val trimmedRel = rel.trim('/')
        if (trimmedRel.isEmpty()) continue

        val slashIdx = trimmedRel.indexOf('/')
        if (slashIdx != -1) {
            val folderName = trimmedRel.substring(0, slashIdx)
            val fullFolderPath = if (prefix.isEmpty()) folderName else "$prefix$folderName"
            folderChildrenCount[fullFolderPath] = (folderChildrenCount[fullFolderPath] ?: 0) + 1
            if (entry.lastModifiedEpochMs > (folderMaxModified[fullFolderPath] ?: 0L)) {
                folderMaxModified[fullFolderPath] = entry.lastModifiedEpochMs
            }
        } else {
            if (entry.isDirectory) {
                val fullFolderPath = if (prefix.isEmpty()) trimmedRel else "$prefix$trimmedRel"
                explicitFolders[fullFolderPath] = entry
            } else {
                directFiles.add(
                    ArchiveVirtualItem(
                        entryPath = entry.entryPath,
                        displayName = entry.displayName.ifEmpty { trimmedRel },
                        isDirectory = false,
                        uncompressedSize = entry.uncompressedSize,
                        compressedSize = entry.compressedSize,
                        lastModifiedEpochMs = entry.lastModifiedEpochMs,
                        isSuspiciousPath = entry.isSuspiciousPath,
                        originalEntry = entry
                    )
                )
            }
        }
    }

    val allFolderPaths = (folderChildrenCount.keys + explicitFolders.keys).distinct()
    val folderItems = allFolderPaths.map { folderPath ->
        val folderName = folderPath.substringAfterLast('/')
        val explicit = explicitFolders[folderPath]
        ArchiveVirtualItem(
            entryPath = "$folderPath/",
            displayName = folderName,
            isDirectory = true,
            uncompressedSize = 0L,
            compressedSize = 0L,
            lastModifiedEpochMs = explicit?.lastModifiedEpochMs ?: (folderMaxModified[folderPath] ?: 0L),
            isSuspiciousPath = explicit?.isSuspiciousPath ?: false,
            childCount = folderChildrenCount[folderPath] ?: 0,
            originalEntry = explicit
        )
    }.sortedBy { it.displayName.lowercase() }

    val sortedFiles = directFiles.sortedBy { it.displayName.lowercase() }

    return folderItems + sortedFiles
}

fun getArchiveFileIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        in FileCategory.IMAGES.extensions -> Icons.Filled.Image
        in FileCategory.VIDEOS.extensions -> Icons.Filled.VideoFile
        in FileCategory.AUDIO.extensions -> Icons.Filled.AudioFile
        in FileCategory.DOCUMENTS.extensions -> {
            if (ext == "pdf") Icons.Filled.PictureAsPdf else Icons.Filled.Description
        }
        in setOf("kt", "java", "xml", "json", "py", "js", "ts", "html", "css", "c", "cpp", "h", "rs", "go", "sh", "yml", "yaml", "gradle") -> Icons.Filled.Code
        in FileCategory.ARCHIVES.extensions, "zip", "tar", "gz", "xz", "7z", "jar", "apk" -> Icons.Filled.FolderZip
        else -> Icons.Filled.Description
    }
}

@Composable
fun ArchiveEntryThumbnailBadge(
    archiveRef: FileReference,
    entryPath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbFile by produceState<java.io.File?>(initialValue = null, key1 = archiveRef.uriOrPath, key2 = entryPath) {
        value = withContext(Dispatchers.IO) {
            val file = java.io.File(archiveRef.uriOrPath)
            if (file.exists() && file.canRead()) {
                ArchiveEngine.getOrExtractEntryThumbnail(
                    archiveFile = file,
                    entryPath = entryPath,
                    cacheDir = context.cacheDir
                )
            } else null
        }
    }

    val thumb = thumbFile
    if (thumb != null && thumb.exists()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(thumb)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(6.dp))
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier
        )
    }
}

/**
 * Native Zip & Archive Viewer.
 * Allows instant inspection, breadcrumb navigation, search, and on-the-fly previewing
 * of individual files within any archive without decompressing the entire archive to storage.
 */
@Composable
fun NativeZipViewer(
    archiveRef: FileReference,
    entries: List<ArchiveEntryItem>,
    currentSubPath: String,
    onNavigateUp: () -> Unit,
    onOpenSubFolder: (String) -> Unit,
    onPreviewEntry: (ArchiveEntryItem) -> Unit,
    onExtractArchive: (FileReference, Set<String>?) -> Unit,
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isFlatView by remember { mutableStateOf(false) }

    val totalUncompressed = remember(entries) { entries.sumOf { it.uncompressedSize } }
    val archiveSize = archiveRef.sizeBytes
    val savingsPercent = remember(totalUncompressed, archiveSize) {
        if (totalUncompressed > archiveSize && totalUncompressed > 0L) {
            ((1.0 - archiveSize.toDouble() / totalUncompressed.toDouble()) * 100.0).toInt().coerceIn(0, 99)
        } else 0
    }

    val virtualItems = remember(entries, currentSubPath, searchQuery, isFlatView) {
        resolveArchiveVirtualItems(entries, currentSubPath, searchQuery, isFlatView)
    }

    val hasSuspiciousEntries = remember(entries) { entries.any { it.isSuspiciousPath } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("native_zip_viewer")
    ) {
        // 1. Archive Header & Summary Banner
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("zip_viewer_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to files",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.FolderZip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = archiveRef.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append("${entries.size} items • ${formatByteSize(archiveSize)}")
                                    if (savingsPercent > 0) {
                                        append(" • $savingsPercent% compressed")
                                    }
                                    if (totalUncompressed > 0) {
                                        append(" (raw: ${formatByteSize(totalUncompressed)})")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onExtractArchive(archiveRef, null) },
                        modifier = Modifier.testTag("extract_all_archive_button")
                    ) {
                        Icon(Icons.Filled.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Extract All")
                    }
                }

                // Security & Protection Badge
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (hasSuspiciousEntries) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = if (hasSuspiciousEntries) Icons.Filled.Warning else Icons.Filled.Security,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (hasSuspiciousEntries) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (hasSuspiciousEntries) "Zip-Slip Traversal Blocked" else "Zip-Slip Guard Active • Safe Inspection",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (hasSuspiciousEntries) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        // 2. Breadcrumbs Bar (when in folder hierarchy view)
        if (!isFlatView && searchQuery.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Archive Root crumb
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onOpenSubFolder("") }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderZip,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (currentSubPath.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Root",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (currentSubPath.isEmpty()) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentSubPath.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val segments = currentSubPath.trim('/').split('/').filter { it.isNotEmpty() }
                    var accumulated = ""
                    segments.forEachIndexed { index, seg ->
                        accumulated = if (accumulated.isEmpty()) seg else "$accumulated/$seg"
                        val isLast = index == segments.size - 1
                        val pathForSeg = accumulated
                        Text(
                            text = "/",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        Text(
                            text = seg,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                            color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenSubFolder(pathForSeg) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 3. Search and Flat/Folder View Mode Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search inside archive...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("zip_search_input")
            )
            IconButton(
                onClick = { isFlatView = !isFlatView },
                modifier = Modifier
                    .size(44.dp)
                    .testTag("zip_toggle_flat_view")
            ) {
                Icon(
                    imageVector = if (isFlatView) Icons.Filled.Folder else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = if (isFlatView) "Switch to Folder View" else "Switch to Flat View",
                    tint = if (isFlatView) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        // 4. Archive Entry List
        val showParentRow = !isFlatView && currentSubPath.isNotEmpty() && searchQuery.isEmpty()

        if (virtualItems.isEmpty() && !showParentRow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No entries match '$searchQuery'" else "This folder is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("zip_entry_list")
            ) {
                // Parent ".." row when nested
                if (showParentRow) {
                    item(key = "archive_parent_dir") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val current = currentSubPath.trim('/')
                                    val lastSlash = current.lastIndexOf('/')
                                    val parent = if (lastSlash != -1) current.substring(0, lastSlash) else ""
                                    onOpenSubFolder(parent)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go up",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = ".. (Parent directory)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider()
                    }
                }

                items(
                    items = virtualItems,
                    key = { it.entryPath },
                    contentType = { if (it.isDirectory) "dir" else "file" }
                ) { item ->
                    val isSuspicious = item.isSuspiciousPath
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSuspicious) {
                                if (item.isDirectory) {
                                    onOpenSubFolder(item.entryPath.trimEnd('/'))
                                } else {
                                    val entryToPreview = item.originalEntry ?: ArchiveEntryItem(
                                        entryPath = item.entryPath,
                                        displayName = item.displayName,
                                        isDirectory = false,
                                        uncompressedSize = item.uncompressedSize,
                                        compressedSize = item.compressedSize,
                                        lastModifiedEpochMs = item.lastModifiedEpochMs,
                                        isSuspiciousPath = false
                                    )
                                    onPreviewEntry(entryToPreview)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val ext = item.displayName.substringAfterLast('.', "").lowercase()
                            val isImageEntry = !item.isDirectory && !isSuspicious && ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
                            if (isImageEntry) {
                                ArchiveEntryThumbnailBadge(
                                    archiveRef = archiveRef,
                                    entryPath = item.entryPath,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                val icon = when {
                                    isSuspicious -> Icons.Filled.Warning
                                    item.isDirectory -> Icons.Filled.Folder
                                    else -> getArchiveFileIcon(item.displayName)
                                }
                                val iconTint = when {
                                    isSuspicious -> MaterialTheme.colorScheme.error
                                    item.isDirectory -> FolderAmber
                                    else -> MaterialTheme.colorScheme.primary
                                }

                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isFlatView || searchQuery.isNotEmpty()) item.entryPath else item.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSuspicious) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when {
                                        item.isDirectory -> "${item.childCount} item(s)"
                                        item.compressedSize > 0 -> "${formatByteSize(item.uncompressedSize)} • compressed: ${formatByteSize(item.compressedSize)}"
                                        else -> formatByteSize(item.uncompressedSize)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Action Buttons on Right
                        if (isSuspicious) {
                            Text(
                                text = "BLOCKED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!item.isDirectory) {
                                    // Direct Preview Button
                                    IconButton(
                                        onClick = {
                                            val entryToPreview = item.originalEntry ?: ArchiveEntryItem(
                                                entryPath = item.entryPath,
                                                displayName = item.displayName,
                                                isDirectory = false,
                                                uncompressedSize = item.uncompressedSize,
                                                compressedSize = item.compressedSize,
                                                lastModifiedEpochMs = item.lastModifiedEpochMs,
                                                isSuspiciousPath = false
                                            )
                                            onPreviewEntry(entryToPreview)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Visibility,
                                            contentDescription = "Preview file",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                // Single-file / Single-folder extract button
                                IconButton(
                                    onClick = { onExtractArchive(archiveRef, setOf(item.entryPath)) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Unarchive,
                                        contentDescription = "Extract entry",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}


