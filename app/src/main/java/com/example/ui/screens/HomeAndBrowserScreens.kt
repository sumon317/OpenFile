package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.archive.ArchiveCreationFormat
import com.example.core.database.BookmarkEntity
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
import com.example.core.network.FtpServerRuntimeState
import com.example.ui.BrowserUiState
import com.example.ui.OpenFileDestination
import com.example.ui.components.BreadcrumbBar
import com.example.ui.components.FileItemGridCard
import com.example.ui.components.FileItemRow
import com.example.ui.theme.ApkGreen
import com.example.ui.theme.FolderAmber
import com.example.ui.theme.StorageDangerCrimson
import com.example.ui.theme.StorageWarningOrange

import com.example.core.database.CloudAccountEntity

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
    cloudAccountsCount: Int = 0,
    remoteProfilesCount: Int = 0,
    cloudAccounts: List<CloudAccountEntity> = emptyList(),
    onOpenCloudAccount: (CloudAccountEntity) -> Unit = {},
    onOpenAccessFromPc: () -> Unit = { onNavigateDestination(OpenFileDestination.NETWORK) },
    onOpenRemoteConnections: () -> Unit = { onNavigateDestination(OpenFileDestination.NETWORK) }
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Wi-Fi FTP Server Status Banner if running
        if (ftpState.isRunning) {
            item {
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
        item {
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
                shape = RoundedCornerShape(18.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (vol.isSafGrantRevoked) MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                                        else barColor.copy(alpha = 0.16f)
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
                            Column {
                                Text(
                                    text = vol.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
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
                        Surface(
                            color = barColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
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
                            .clip(RoundedCornerShape(4.dp))
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

        // Connected Cloud Drives mounted directly under Storage Devices (File Manager + style)
        items(
            items = cloudAccounts,
            key = { "home_cloud_${it.id}" },
            contentType = { "cloud_volume_card" }
        ) { account ->
            Card(
                onClick = { onOpenCloudAccount(account) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_cloud_volume_${account.provider}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background( Color(0xFF5C6BC0).copy(alpha = 0.16f) ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cloud,
                                contentDescription = null,
                                tint = Color(0xFF5C6BC0),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = account.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${account.accountEmail} • Native In-App Drive",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(
                        color = ApkGreen.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Mounted",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ApkGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Section 2: File Manager + Symmetrical 3-Column Circular-Badge Grid
        item {
            Text(
                text = "Categories & Quick Access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            fun countLabel(cat: FileCategory, fallback: String): String {
                val c = categoryCounts[cat]
                return if (c != null) "$c items" else fallback
            }

            val gridTiles = listOf(
                HomeTileSpec(
                    label = "Downloads",
                    subtitle = countLabel(FileCategory.DOWNLOADS, "Folder"),
                    icon = Icons.Filled.Download,
                    badgeColor = Color(0xFF26A69A),
                    testTag = "shortcut_${OpenFileDestination.DOWNLOADS.route}",
                    onClick = { onNavigateDestination(OpenFileDestination.DOWNLOADS) }
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
                    label = "Documents",
                    subtitle = countLabel(FileCategory.DOCUMENTS, "PDF & Office"),
                    icon = Icons.Filled.Description,
                    badgeColor = Color(0xFF42A5F5),
                    testTag = "shortcut_${OpenFileDestination.DOCUMENTS.route}",
                    onClick = { onNavigateDestination(OpenFileDestination.DOCUMENTS) }
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
                    label = "New Files",
                    subtitle = countLabel(FileCategory.RECENT_FILES, "Recent"),
                    icon = Icons.Filled.History,
                    badgeColor = Color(0xFF29B6F6),
                    testTag = "shortcut_${OpenFileDestination.RECENT_FILES.route}",
                    onClick = { onNavigateDestination(OpenFileDestination.RECENT_FILES) }
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
                    label = "Cloud Drives",
                    subtitle = if (cloudAccountsCount > 0) "$cloudAccountsCount connected" else "Drive / OneDrive",
                    icon = Icons.Filled.Cloud,
                    badgeColor = Color(0xFF5C6BC0),
                    testTag = "home_cloud_button",
                    onClick = { onNavigateDestination(OpenFileDestination.CLOUD_STORAGE) }
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
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                gridTiles.chunked(3).forEach { rowItems ->
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

        // Section 3: File Manager + Dedicated Storage Analysis Card
        item {
            Card(
                onClick = { onNavigateDestination(OpenFileDestination.STORAGE_ANALYSIS) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Analytics,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Storage Analysis",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Clean large files, duplicate files, and space hogs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { onNavigateDestination(OpenFileDestination.STORAGE_ANALYSIS) }
                    ) {
                        Text("Analyze")
                    }
                }
            }
        }

        // Section 4: Bookmarks
        if (bookmarks.isNotEmpty()) {
            item {
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
        item {
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
                if (recentFiles.isNotEmpty()) {
                    TextButton(onClick = { onNavigateDestination(OpenFileDestination.RECENT_FILES) }) {
                        Text("More")
                    }
                }
            }
        }

        if (recentFiles.isEmpty()) {
            item {
                Text(
                    text = "Files you open or inspect will appear here for one-tap access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(
                items = recentFiles.take(8),
                key = { it.uriOrPath },
                contentType = { "recent_file" }
            ) { recent ->
                Surface(
                    onClick = { onRecentFileClick(recent) },
                    shape = RoundedCornerShape(10.dp),
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

private data class HomeTileSpec(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val badgeColor: Color,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
private fun CategoryShortcutCard(
    label: String,
    subtitle: String,
    icon: ImageVector,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(badgeColor.copy(alpha = 0.18f)),
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
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    cloudAccounts: List<CloudAccountEntity> = emptyList(),
    onOpenLocalRoot: (() -> Unit)? = null,
    onOpenCloudAccount: ((CloudAccountEntity) -> Unit)? = null,
    onReauthCloudAccount: ((CloudAccountEntity) -> Unit)? = null,
    onOpenDriveFilePicker: (() -> Unit)? = null,
    onOpenCloudSection: ((CloudAccountEntity, String) -> Unit)? = null
) {
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileReference?>(null) }
    var deleteConfirmTargets by remember { mutableStateOf<List<FileReference>?>(null) }
    var extensionFilter by remember { mutableStateOf<String?>(null) }

    val bookmarkedPaths = remember(bookmarks) { bookmarks.map { it.uriOrPath }.toSet() }

    val initialUnfiltered = remember(browserState.items, settings.showHiddenFiles) {
        if (settings.showHiddenFiles) browserState.items
        else browserState.items.filter { !it.isHidden }
    }

    val filteredItems by produceState(
        initialValue = initialUnfiltered,
        browserState.items,
        browserState.searchQuery,
        extensionFilter,
        settings.showHiddenFiles
    ) {
        value = if (browserState.searchQuery.isBlank() && extensionFilter == null) {
            initialUnfiltered
        } else {
            withContext(Dispatchers.Default) {
                FileSortAndFilterEngine.filterFiles(
                    files = browserState.items,
                    query = browserState.searchQuery,
                    extensionFilter = extensionFilter,
                    showHidden = settings.showHiddenFiles
                )
            }
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
                    SortField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = {
                                val active = settings.defaultSortField == field
                                Text(
                                    text = if (active) "✓ Sort by ${field.label}" else "Sort by ${field.label}",
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                showSortMenu = false
                                onChangeSort(field, settings.defaultSortOrder)
                            }
                        )
                    }
                    HorizontalDivider()
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = {
                                val active = settings.defaultSortOrder == order
                                Text(
                                    text = if (active) "✓ ${order.label}" else order.label,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                showSortMenu = false
                                onChangeSort(settings.defaultSortField, order)
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
                        text = "Category Index: ${browserState.activeCategoryFilter.title}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${filteredItems.size} matching item(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Quick Storage Location Switcher (Internal Storage <-> Google Drive / Cloud) for 1-tap cross-storage Cut & Copy
        if ((cloudAccounts.isNotEmpty() || browserState.activeCloudAccount != null || clipboard.isNotEmpty) &&
            onOpenLocalRoot != null && onOpenCloudAccount != null
        ) {
            val isCloudActive = browserState.activeCloudAccount != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !isCloudActive && browserState.activeRemoteProfile == null,
                    onClick = onOpenLocalRoot,
                    leadingIcon = {
                        Icon(Icons.Filled.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Internal Storage") },
                    modifier = Modifier.testTag("quick_switch_internal_storage")
                )
                cloudAccounts.forEach { acc ->
                    val selected = browserState.activeCloudAccount?.id == acc.id
                    FilterChip(
                        selected = selected,
                        onClick = { onOpenCloudAccount(acc) },
                        leadingIcon = {
                            Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(
                                text = "${acc.displayName} (${acc.accountEmail})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("quick_switch_cloud_${acc.provider}")
                    )
                }
                if (onOpenDriveFilePicker != null) {
                    AssistChip(
                        onClick = onOpenDriveFilePicker,
                        leadingIcon = {
                            Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Drive FilePicker") },
                        modifier = Modifier.testTag("browser_open_drive_picker_button")
                    )
                }
                if (isCloudActive && onReauthCloudAccount != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { browserState.activeCloudAccount?.let(onReauthCloudAccount) },
                        modifier = Modifier.testTag("browser_reauth_cloud_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Browser Auth", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (isCloudActive &&
                browserState.activeCloudAccount?.provider == "GOOGLE_DRIVE" &&
                onOpenCloudSection != null
            ) {
                val activeAcc = browserState.activeCloudAccount!!
                val currentFolderId = browserState.currentDirectory?.uriOrPath ?: "root"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "root" to "My Drive",
                        "sharedWithMe" to "Shared with me",
                        "starred" to "Starred",
                        "recent" to "Recent"
                    ).forEach { (secId, secTitle) ->
                        FilterChip(
                            selected = currentFolderId == secId,
                            onClick = { onOpenCloudSection(activeAcc, secId) },
                            label = { Text(secTitle) },
                            modifier = Modifier.testTag("browser_drive_section_$secId")
                        )
                    }
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
                        Text(
                            text = "${browserState.selectedUris.size} selected",
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

        // Archive Virtual Contents Inspector (if user opened a .zip/.tar/.7z)
        if (browserState.inspectArchiveFile != null) {
            val archiveRef = browserState.inspectArchiveFile
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Archive Inspector: ${archiveRef.displayName}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${browserState.archiveEntries.size} entries • Zip-Slip Guard Active",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = onCloseArchiveInspector) {
                                Text("Close")
                            }
                            Button(
                                onClick = { onExtractArchive(archiveRef, null) },
                                modifier = Modifier.testTag("extract_all_archive_button")
                            ) {
                                Icon(Icons.Filled.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Extract All")
                            }
                        }
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = browserState.archiveEntries,
                        key = { it.entryPath },
                        contentType = { "archive_entry" }
                    ) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.entryPath,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (entry.isSuspiciousPath) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (entry.isDirectory) "Directory" else "Uncompressed: ${formatByteSize(entry.uncompressedSize)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (entry.isSuspiciousPath) {
                                Text(
                                    text = "SUSPICIOUS PATH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (!entry.isDirectory) {
                                OutlinedButton(
                                    onClick = { onExtractArchive(archiveRef, setOf(entry.entryPath)) }
                                ) {
                                    Text("Extract")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
            return@Column
        }

        // Main File List or Grid + Optional Two-Pane Tablet Inspector
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isWideDualPane = maxWidth >= 700.dp
            val singleSelectedItem = remember(browserState.selectedUris, filteredItems) {
                if (browserState.selectedUris.size == 1) {
                    val uri = browserState.selectedUris.first()
                    filteredItems.firstOrNull { it.uriOrPath == uri }
                } else null
            }

            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when {
                        browserState.isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        browserState.errorMessage != null -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = browserState.errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        filteredItems.isEmpty() -> {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = if (browserState.searchQuery.isNotEmpty()) {
                                        "No files match '${browserState.searchQuery}'"
                                    } else {
                                        "This folder is empty"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (browserState.currentDirectory != null) {
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
                                        isSelected = { item.uriOrPath in browserState.selectedUris },
                                        showThumbnails = settings.showThumbnails,
                                        onClick = { onFileClick(item) },
                                        onLongClick = { onFileLongClick(item) },
                                        onProperties = { onOpenProperties(item) }
                                    )
                                }
                            }
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize().testTag("browser_list_view")) {
                                items(
                                    items = filteredItems,
                                    key = { it.uriOrPath },
                                    contentType = { if (it.isDirectory) "dir" else "file" }
                                ) { item ->
                                    val isRemote = browserState.activeRemoteProfile != null || browserState.activeCloudAccount != null
                                    FileItemRow(
                                        file = item,
                                        isSelected = { item.uriOrPath in browserState.selectedUris },
                                        isBookmarked = { item.uriOrPath in bookmarkedPaths },
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
                                                onDeleteItems(listOf(item), settings.moveToTrashByDefault)
                                            }
                                        },
                                        onToggleBookmark = { onToggleBookmark(item) },
                                        onShare = { onShareItem(item) },
                                        onProperties = { onOpenProperties(item) },
                                        onDownloadRemote = if (isRemote) ({ onDownloadRemoteItem(item) }) else null,
                                        onOpenWith = if (onOpenWithExternal != null && (browserState.activeRemoteProfile == null)) ({ onOpenWithExternal(item) }) else null,
                                        onQuickPreview = if (onQuickPreview != null) ({ onQuickPreview(item) }) else null
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                                onDeleteItems(selectedItemsList, settings.moveToTrashByDefault)
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
                    Text(
                        text = "${clipboard.items.size} item(s) ready to ${if (clipboard.mode == ClipboardMode.CUT) "Move" else "Copy"}",
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
        val dirSummary by produceState(initialValue = Triple(0, 0, 0L), filteredItems) {
            value = withContext(Dispatchers.Default) {
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
                Text(
                    text = "${dirSummary.first} folder(s), ${dirSummary.second} file(s) • ${formatByteSize(dirSummary.third)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${settings.defaultSortField.label} (${if (settings.defaultSortOrder == SortOrder.ASCENDING) "↑" else "↓"})",
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
        val isLocalOrSaf = targets.all { it.providerType == ProviderType.LOCAL || it.providerType == ProviderType.SAF }
        var useTrash by remember { mutableStateOf(settings.moveToTrashByDefault && isLocalOrSaf) }

        AlertDialog(
            onDismissRequest = { deleteConfirmTargets = null },
            title = { Text("Delete ${targets.size} Item(s)?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (useTrash) {
                            "Items will be moved to OpenFile Trash and can be restored later."
                        } else {
                            "Items will be permanently deleted and cannot be recovered."
                        }
                    )
                    if (isLocalOrSaf) {
                        FilterChip(
                            selected = useTrash,
                            onClick = { useTrash = !useTrash },
                            label = { Text("Move to Trash instead of permanent delete") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteItems(targets, useTrash)
                        deleteConfirmTargets = null
                    },
                    modifier = Modifier.testTag("dialog_confirm_delete")
                ) {
                    Text(if (useTrash) "Move to Trash" else "Delete Permanently")
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

