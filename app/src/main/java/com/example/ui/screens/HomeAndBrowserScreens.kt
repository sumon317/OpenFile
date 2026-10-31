package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ViewList
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onRecentFileClick: (RecentFileEntity) -> Unit
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
                        .clickable { onNavigateDestination(OpenFileDestination.NETWORK) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lan, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Local Wi-Fi FTP Server Active",
                                    style = MaterialTheme.typography.titleSmall
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

        // Section 1: Storage Volumes & SAF Grants
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Storage Devices", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onRequestSafAccess,
                        modifier = Modifier.testTag("home_add_saf_button")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mount SAF Folder")
                    }
                    TextButton(
                        onClick = { onNavigateDestination(OpenFileDestination.STORAGE_ANALYSIS) },
                        modifier = Modifier.testTag("home_analyzer_button")
                    ) {
                        Icon(Icons.Filled.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Analyze")
                    }
                }
            }
        }

        items(volumes, key = { it.id }) { vol ->
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("volume_card_${vol.id}")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (vol.isSafGrantRevoked) Icons.Filled.Warning else Icons.Filled.Storage,
                                contentDescription = null,
                                tint = if (vol.isSafGrantRevoked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(vol.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = vol.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (vol.isSafGrantRevoked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = "${(vol.usageFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = barColor
                        )
                    }
                    LinearProgressIndicator(
                        progress = { vol.usageFraction },
                        color = barColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatByteSize(vol.usedBytes)} used",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${formatByteSize(vol.availableBytes)} free of ${formatByteSize(vol.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section 2: Category Grid (9 shortcuts)
        item {
            Text("Categories & Quick Access", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            val shortcuts = listOf(
                Triple("Downloads", Icons.Filled.Download, OpenFileDestination.DOWNLOADS),
                Triple("Images", Icons.Filled.Image, OpenFileDestination.IMAGES),
                Triple("Videos", Icons.Filled.VideoFile, OpenFileDestination.VIDEOS),
                Triple("Audio", Icons.Filled.AudioFile, OpenFileDestination.AUDIO),
                Triple("Documents", Icons.Filled.Description, OpenFileDestination.DOCUMENTS),
                Triple("Apps & APKs", Icons.Filled.Android, OpenFileDestination.APPLICATIONS),
                Triple("Archives", Icons.Filled.FolderZip, OpenFileDestination.ARCHIVES),
                Triple("Large Files", Icons.Filled.Storage, OpenFileDestination.LARGE_FILES),
                Triple("Recent", Icons.Filled.History, OpenFileDestination.RECENT_FILES)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shortcuts.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (label, icon, dest) ->
                            CategoryShortcutCard(
                                label = label,
                                icon = icon,
                                onClick = { onNavigateDestination(dest) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("shortcut_${dest.route}")
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Network & Cloud Connections
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { onNavigateDestination(OpenFileDestination.NETWORK) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_network_button")
                ) {
                    Icon(Icons.Filled.Lan, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Network & FTP")
                }
                FilledTonalButton(
                    onClick = { onNavigateDestination(OpenFileDestination.CLOUD_STORAGE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_cloud_button")
                ) {
                    Icon(Icons.Filled.Cloud, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cloud Drives")
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
                    Text("Pinned Bookmarks", style = MaterialTheme.typography.titleMedium)
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
                Text("Recently Opened Files", style = MaterialTheme.typography.titleMedium)
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
            items(recentFiles.take(8), key = { it.uriOrPath }) { recent ->
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

@Composable
private fun CategoryShortcutCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
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
    onDownloadRemoteItem: (FileReference) -> Unit
) {
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileReference?>(null) }
    var deleteConfirmTargets by remember { mutableStateOf<List<FileReference>?>(null) }
    var extensionFilter by remember { mutableStateOf<String?>(null) }

    val bookmarkedPaths = remember(bookmarks) { bookmarks.map { it.uriOrPath }.toSet() }

    val filteredItems = remember(
        browserState.items,
        browserState.searchQuery,
        extensionFilter,
        settings.showHiddenFiles
    ) {
        FileSortAndFilterEngine.filterFiles(
            files = browserState.items,
            query = browserState.searchQuery,
            extensionFilter = extensionFilter,
            showHidden = settings.showHiddenFiles
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Action & Search Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = browserState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search name or extension...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (browserState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
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
                modifier = Modifier.testTag("browser_toggle_view_mode")
            ) {
                Icon(
                    imageVector = if (settings.defaultViewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                    contentDescription = "Toggle List or Grid view"
                )
            }

            // Sort Menu
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.testTag("browser_sort_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort files")
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
                    modifier = Modifier.testTag("browser_new_folder_button")
                ) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder")
                }
                IconButton(
                    onClick = { showNewFileDialog = true },
                    modifier = Modifier.testTag("browser_new_file_button")
                ) {
                    Icon(Icons.Filled.NoteAdd, contentDescription = "New Text File")
                }
            }
        }

        // Breadcrumb Navigation Bar
        if (browserState.breadcrumbs.isNotEmpty()) {
            BreadcrumbBar(
                breadcrumbs = browserState.breadcrumbs,
                providerType = browserState.currentDirectory?.providerType,
                onCrumbClick = onCrumbClick
            )
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

        // Clipboard Paste Banner if items are waiting in clipboard
        if (clipboard.isNotEmpty && browserState.currentDirectory != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
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
                        style = MaterialTheme.typography.labelLarge
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

        // Multi-Selection Action Bar
        if (browserState.isMultiSelectActive) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                        Text(
                            text = "${browserState.selectedUris.size} selected",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Row {
                        IconButton(onClick = onSelectAll, modifier = Modifier.testTag("multiselect_select_all")) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(
                            onClick = {
                                val sel = browserState.items.filter { it.uriOrPath in browserState.selectedUris }
                                onCopyItems(sel, ClipboardMode.COPY)
                            },
                            modifier = Modifier.testTag("multiselect_copy")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy selected")
                        }
                        IconButton(
                            onClick = {
                                val sel = browserState.items.filter { it.uriOrPath in browserState.selectedUris }
                                onCopyItems(sel, ClipboardMode.CUT)
                            },
                            modifier = Modifier.testTag("multiselect_cut")
                        ) {
                            Icon(Icons.Filled.ContentCut, contentDescription = "Cut selected")
                        }
                        IconButton(
                            onClick = { showCompressDialog = true },
                            modifier = Modifier.testTag("multiselect_compress")
                        ) {
                            Icon(Icons.Filled.FolderZip, contentDescription = "Compress to Archive")
                        }
                        IconButton(
                            onClick = {
                                val sel = browserState.items.filter { it.uriOrPath in browserState.selectedUris }
                                if (settings.confirmBeforeDelete) {
                                    deleteConfirmTargets = sel
                                } else {
                                    onDeleteItems(sel, settings.moveToTrashByDefault)
                                }
                            },
                            modifier = Modifier.testTag("multiselect_delete")
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error
                            )
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
                    items(browserState.archiveEntries, key = { it.entryPath }) { entry ->
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

        // Main File List or Grid
        Box(modifier = Modifier.fillMaxSize()) {
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
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it.uriOrPath }) { item ->
                            FileItemGridCard(
                                file = item,
                                isSelected = item.uriOrPath in browserState.selectedUris,
                                showThumbnails = settings.showThumbnails,
                                onClick = { onFileClick(item) },
                                onLongClick = { onFileLongClick(item) },
                                onProperties = { onOpenProperties(item) }
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredItems, key = { it.uriOrPath }) { item ->
                            val isRemote = browserState.activeRemoteProfile != null || browserState.activeCloudAccount != null
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
                                        onDeleteItems(listOf(item), settings.moveToTrashByDefault)
                                    }
                                },
                                onToggleBookmark = { onToggleBookmark(item) },
                                onShare = { onShareItem(item) },
                                onProperties = { onOpenProperties(item) },
                                onDownloadRemote = if (isRemote) ({ onDownloadRemoteItem(item) }) else null
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
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
