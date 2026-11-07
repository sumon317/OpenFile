package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.core.archive.ArchiveCreationFormat
import com.example.core.database.BookmarkEntity
import com.example.core.database.TrashItemEntity
import com.example.core.datastore.AppSettingsState
import com.example.core.filesystem.ClipboardMode
import com.example.core.filesystem.ClipboardState
import com.example.core.filesystem.FileSortAndFilterEngine
import com.example.core.model.FileCategory
import com.example.core.model.FileReference
import com.example.core.model.InstalledAppInfo
import com.example.core.model.ProviderType
import com.example.core.model.SortField
import com.example.core.model.SortOrder
import com.example.core.model.StorageAnalysisReport
import com.example.core.model.formatByteSize
import com.example.ui.components.FileItemRow
import com.example.ui.theme.ApkGreen
import com.example.ui.theme.FolderAmber
import com.example.ui.theme.JetBrainsMonoFamily
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Interactive Visual Storage Analyzer with Canvas ring chart, category breakdown,
 * largest files, largest directories drill-down, extension distribution, SHA-256 Duplicate Finder,
 * and full file management actions (Copy, Cut/Move, Rename, Delete, Share, Bookmark, Compress,
 * Open Containing Folder, Open With, Quick Preview, Properties & Hashes, and Multi-Selection).
 */
@Composable
fun StorageAnalysisScreen(
    report: StorageAnalysisReport?,
    isScanning: Boolean,
    progressText: String,
    onRefreshScan: (File?) -> Unit,
    onCancelScan: () -> Unit,
    onOpenFileOrDirectory: (FileReference) -> Unit,
    onDeleteDuplicateFile: (FileReference) -> Unit,
    settings: AppSettingsState = AppSettingsState(),
    bookmarks: List<BookmarkEntity> = emptyList(),
    clipboard: ClipboardState = ClipboardState(),
    onRenameItem: (FileReference, String) -> Unit = { _, _ -> },
    onCopyItems: (List<FileReference>, ClipboardMode) -> Unit = { _, _ -> },
    onClearClipboard: () -> Unit = {},
    onNavigateToStorageForPaste: () -> Unit = {},
    onDeleteItems: (List<FileReference>, Boolean) -> Unit = { items, _ ->
        items.forEach(onDeleteDuplicateFile)
    },
    onToggleBookmark: (FileReference) -> Unit = {},
    onShareItem: (FileReference) -> Unit = {},
    onOpenProperties: (FileReference) -> Unit = {},
    onOpenParentFolder: (FileReference) -> Unit = {},
    onOpenWithExternal: ((FileReference) -> Unit)? = null,
    onQuickPreview: ((FileReference) -> Unit)? = null,
    onCompressItems: (List<FileReference>, String, ArchiveCreationFormat) -> Unit = { _, _, _ -> }
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Largest Files", "Largest Folders", "Duplicates")

    // Local sorting, search, and category filter state for Storage Analyzer results
    var sortField by remember { mutableStateOf(SortField.SIZE) }
    var sortOrder by remember { mutableStateOf(SortOrder.DESCENDING) }
    var searchQuery by remember { mutableStateOf("") }
    var activeCategoryNameFilter by remember { mutableStateOf<String?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Multi-selection & Action Dialog states inside Storage Analyzer
    var selectedUris by remember(report) { mutableStateOf<Set<String>>(emptySet()) }
    var renameTarget by remember { mutableStateOf<FileReference?>(null) }
    var deleteConfirmTargets by remember { mutableStateOf<List<FileReference>?>(null) }
    var compressTargets by remember { mutableStateOf<List<FileReference>?>(null) }

    val bookmarkedPaths = remember(bookmarks) { bookmarks.map { it.uriOrPath }.toHashSet() }
    val isMultiSelectActive = selectedUris.isNotEmpty()

    // Compute sorted & filtered lists for Largest Files & Largest Folders
    val sortedLargestFiles = remember(
        report?.largestFiles,
        sortField,
        sortOrder,
        searchQuery,
        activeCategoryNameFilter
    ) {
        val raw = report?.largestFiles.orEmpty()
        val filtered = raw.filter { file ->
            val matchesQuery = searchQuery.isBlank() ||
                file.displayName.contains(searchQuery.trim(), ignoreCase = true) ||
                file.uriOrPath.contains(searchQuery.trim(), ignoreCase = true)
            val matchesCategory = activeCategoryNameFilter == null ||
                (FileCategory.categorize(file)?.title ?: "Other").equals(activeCategoryNameFilter, ignoreCase = true)
            matchesQuery && matchesCategory
        }
        FileSortAndFilterEngine.sortFiles(filtered, sortField, sortOrder)
    }

    val sortedLargestDirs = remember(
        report?.largestDirectories,
        sortField,
        sortOrder,
        searchQuery
    ) {
        val rawDirs = report?.largestDirectories.orEmpty().map { (dirRef, totalSize) ->
            if (dirRef.sizeBytes == totalSize) dirRef else dirRef.copy(sizeBytes = totalSize)
        }
        val filtered = if (searchQuery.isBlank()) {
            rawDirs
        } else {
            val q = searchQuery.trim()
            rawDirs.filter {
                it.displayName.contains(q, ignoreCase = true) ||
                    it.uriOrPath.contains(q, ignoreCase = true)
            }
        }
        FileSortAndFilterEngine.sortFiles(filtered, sortField, sortOrder)
    }

    val filteredDuplicateGroups = remember(report?.duplicateGroups, searchQuery) {
        val rawGroups = report?.duplicateGroups.orEmpty()
        if (searchQuery.isBlank()) {
            rawGroups
        } else {
            val q = searchQuery.trim()
            rawGroups.filter { group ->
                group.files.any {
                    it.displayName.contains(q, ignoreCase = true) ||
                        it.uriOrPath.contains(q, ignoreCase = true)
                }
            }
        }
    }

    // Lookup map for all items in the report so multi-select actions work across tabs
    val allSelectableByUri = remember(report) {
        val map = LinkedHashMap<String, FileReference>()
        report?.largestFiles?.forEach { map[it.uriOrPath] = it }
        report?.largestDirectories?.forEach { (dir, size) ->
            map[dir.uriOrPath] = dir.copy(sizeBytes = size)
        }
        report?.duplicateGroups?.forEach { grp ->
            grp.files.forEach { map[it.uriOrPath] = it }
        }
        map
    }

    val selectedItemsList = remember(selectedUris, allSelectableByUri) {
        selectedUris.mapNotNull { allSelectableByUri[it] }
    }

    fun toggleSelect(item: FileReference) {
        val next = selectedUris.toMutableSet()
        if (!next.add(item.uriOrPath)) {
            next.remove(item.uriOrPath)
        }
        selectedUris = next
    }

    fun selectAllInCurrentTab() {
        val tabUris = when (selectedTab) {
            1 -> sortedLargestFiles.map { it.uriOrPath }.toSet()
            2 -> sortedLargestDirs.map { it.uriOrPath }.toSet()
            3 -> filteredDuplicateGroups.flatMap { grp -> grp.files.drop(1).map { it.uriOrPath } }.toSet()
            else -> emptySet()
        }
        selectedUris = selectedUris + tabUris
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("storage_analysis_screen")
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Storage Analyzer", style = MaterialTheme.typography.titleLarge)
                if (report != null) {
                    Text(
                        text = "${report.scannedFilesCount} files across ${report.scannedDirectoriesCount} folders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isScanning) {
                OutlinedButton(onClick = onCancelScan) {
                    Text("Cancel Scan")
                }
            } else {
                Button(
                    onClick = {
                        selectedUris = emptySet()
                        onRefreshScan(null)
                    },
                    modifier = Modifier.testTag("analyzer_rescan_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rescan")
                }
            }
        }

        // Sub-navigation tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { idx, label ->
                val tabTag = when (idx) {
                    0 -> "analyzer_tab_overview"
                    1 -> "analyzer_tab_largest_files"
                    2 -> "analyzer_tab_largest_folders"
                    else -> "analyzer_tab_duplicates"
                }
                FilterChip(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    label = { Text(label) },
                    modifier = Modifier.testTag(tabTag)
                )
            }
            if (activeCategoryNameFilter != null && selectedTab == 1) {
                FilterChip(
                    selected = true,
                    onClick = { activeCategoryNameFilter = null },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = "Clear category filter", modifier = Modifier.size(14.dp))
                    },
                    label = { Text("Category: $activeCategoryNameFilter") }
                )
            }
        }

        // Search & Sort Controls (shown on Largest Files, Largest Folders, and Duplicates tabs)
        if (!isScanning && report != null && selectedTab in 1..3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = when (selectedTab) {
                                1 -> "Filter largest files..."
                                2 -> "Filter largest folders..."
                                else -> "Filter duplicate files..."
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("analyzer_search_input")
                )

                if (selectedTab in 1..2) {
                    Box {
                        OutlinedButton(
                            onClick = { showSortMenu = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("analyzer_sort_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${sortField.label} ${if (sortOrder == SortOrder.ASCENDING) "↑" else "↓"}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortField.entries.forEach { field ->
                                DropdownMenuItem(
                                    text = {
                                        val active = sortField == field
                                        Text(
                                            text = if (active) "✓ Sort by ${field.label}" else "Sort by ${field.label}",
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        showSortMenu = false
                                        sortField = field
                                    }
                                )
                            }
                            HorizontalDivider()
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        val active = sortOrder == order
                                        Text(
                                            text = if (active) "✓ ${order.label}" else order.label,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        showSortMenu = false
                                        sortOrder = order
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Multi-Selection Top Header Strip
        if (isMultiSelectActive) {
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
                        IconButton(onClick = { selectedUris = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                        }
                        val count = selectedUris.size
                        Text(
                            text = "$count ${if (count == 1) "item" else "items"} selected (${formatByteSize(selectedItemsList.sumOf { it.sizeBytes })})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (selectedTab in 1..3) {
                        TextButton(
                            onClick = { selectAllInCurrentTab() },
                            modifier = Modifier.testTag("analyzer_select_all_button")
                        ) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedTab == 3) "Select Redundant" else "Select All")
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

        if (isScanning) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(progressText, style = MaterialTheme.typography.bodyMedium)
                }
            }
            return
        }

        if (report == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { onRefreshScan(null) }) {
                    Text("Start Storage Analysis")
                }
            }
            return
        }

        // Main Tab Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> StorageOverviewTab(
                    report = report,
                    onCategoryClick = { categoryName ->
                        activeCategoryNameFilter = categoryName
                        selectedTab = 1
                    }
                )
                1 -> LargestFilesTab(
                    files = sortedLargestFiles,
                    selectedUris = selectedUris,
                    bookmarkedPaths = bookmarkedPaths,
                    settings = settings,
                    onItemClick = { item ->
                        if (isMultiSelectActive) {
                            toggleSelect(item)
                        } else {
                            onOpenFileOrDirectory(item)
                        }
                    },
                    onItemLongClick = { item -> toggleSelect(item) },
                    onRename = { renameTarget = it },
                    onCopy = { item -> onCopyItems(listOf(item), ClipboardMode.COPY) },
                    onCut = { item -> onCopyItems(listOf(item), ClipboardMode.CUT) },
                    onDelete = { item ->
                        if (settings.confirmBeforeDelete) {
                            deleteConfirmTargets = listOf(item)
                        } else {
                            onDeleteItems(listOf(item), settings.moveToTrashByDefault)
                        }
                    },
                    onToggleBookmark = onToggleBookmark,
                    onShare = onShareItem,
                    onProperties = onOpenProperties,
                    onOpenParentFolder = onOpenParentFolder,
                    onOpenWith = onOpenWithExternal,
                    onQuickPreview = onQuickPreview,
                    onCompress = { item -> compressTargets = listOf(item) }
                )
                2 -> LargestDirectoriesTab(
                    directories = sortedLargestDirs,
                    selectedUris = selectedUris,
                    bookmarkedPaths = bookmarkedPaths,
                    settings = settings,
                    onItemClick = { dirRef ->
                        if (isMultiSelectActive) {
                            toggleSelect(dirRef)
                        } else {
                            onOpenFileOrDirectory(dirRef)
                        }
                    },
                    onItemLongClick = { dirRef -> toggleSelect(dirRef) },
                    onDrillDown = { dirRef ->
                        selectedUris = emptySet()
                        onRefreshScan(File(dirRef.uriOrPath))
                        selectedTab = 0
                    },
                    onRename = { renameTarget = it },
                    onCopy = { dirRef -> onCopyItems(listOf(dirRef), ClipboardMode.COPY) },
                    onCut = { dirRef -> onCopyItems(listOf(dirRef), ClipboardMode.CUT) },
                    onDelete = { dirRef ->
                        if (settings.confirmBeforeDelete) {
                            deleteConfirmTargets = listOf(dirRef)
                        } else {
                            onDeleteItems(listOf(dirRef), settings.moveToTrashByDefault)
                        }
                    },
                    onToggleBookmark = onToggleBookmark,
                    onShare = onShareItem,
                    onProperties = onOpenProperties,
                    onOpenParentFolder = onOpenParentFolder,
                    onCompress = { dirRef -> compressTargets = listOf(dirRef) }
                )
                3 -> DuplicateFilesTab(
                    groups = filteredDuplicateGroups,
                    selectedUris = selectedUris,
                    bookmarkedPaths = bookmarkedPaths,
                    settings = settings,
                    onItemClick = { fileRef ->
                        if (isMultiSelectActive) {
                            toggleSelect(fileRef)
                        } else {
                            onOpenFileOrDirectory(fileRef)
                        }
                    },
                    onItemLongClick = { fileRef -> toggleSelect(fileRef) },
                    onSelectRedundantInGroup = { groupFiles ->
                        selectedUris = selectedUris + groupFiles.drop(1).map { it.uriOrPath }
                    },
                    onRename = { renameTarget = it },
                    onCopy = { fileRef -> onCopyItems(listOf(fileRef), ClipboardMode.COPY) },
                    onCut = { fileRef -> onCopyItems(listOf(fileRef), ClipboardMode.CUT) },
                    onDelete = { fileRef ->
                        if (settings.confirmBeforeDelete) {
                            deleteConfirmTargets = listOf(fileRef)
                        } else {
                            onDeleteItems(listOf(fileRef), settings.moveToTrashByDefault)
                        }
                    },
                    onToggleBookmark = onToggleBookmark,
                    onShare = onShareItem,
                    onProperties = onOpenProperties,
                    onOpenParentFolder = onOpenParentFolder,
                    onOpenWith = onOpenWithExternal,
                    onQuickPreview = onQuickPreview,
                    onCompress = { fileRef -> compressTargets = listOf(fileRef) }
                )
            }
        }

        // Contextual Bottom Selection Toolbar (Copy, Move, Rename, Delete, Share, Compress, Info)
        if (isMultiSelectActive && selectedItemsList.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analyzer_bottom_selection_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnalyzerBottomActionItem(
                        icon = Icons.Filled.ContentCopy,
                        label = "Copy",
                        testTag = "analyzer_multiselect_copy",
                        onClick = {
                            onCopyItems(selectedItemsList, ClipboardMode.COPY)
                            selectedUris = emptySet()
                        }
                    )
                    AnalyzerBottomActionItem(
                        icon = Icons.Filled.ContentCut,
                        label = "Move",
                        testTag = "analyzer_multiselect_cut",
                        onClick = {
                            onCopyItems(selectedItemsList, ClipboardMode.CUT)
                            selectedUris = emptySet()
                        }
                    )
                    if (selectedItemsList.size == 1) {
                        AnalyzerBottomActionItem(
                            icon = Icons.Filled.Edit,
                            label = "Rename",
                            testTag = "analyzer_multiselect_rename",
                            onClick = { renameTarget = selectedItemsList.first() }
                        )
                    }
                    AnalyzerBottomActionItem(
                        icon = Icons.Filled.Delete,
                        label = "Delete",
                        testTag = "analyzer_multiselect_delete",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            if (settings.confirmBeforeDelete) {
                                deleteConfirmTargets = selectedItemsList
                            } else {
                                onDeleteItems(selectedItemsList, settings.moveToTrashByDefault)
                                selectedUris = emptySet()
                            }
                        }
                    )
                    if (selectedItemsList.size == 1 && !selectedItemsList.first().isDirectory) {
                        AnalyzerBottomActionItem(
                            icon = Icons.Filled.Share,
                            label = "Share",
                            testTag = "analyzer_multiselect_share",
                            onClick = { onShareItem(selectedItemsList.first()) }
                        )
                    }
                    AnalyzerBottomActionItem(
                        icon = Icons.Filled.FolderZip,
                        label = "Compress",
                        testTag = "analyzer_multiselect_compress",
                        onClick = { compressTargets = selectedItemsList }
                    )
                    if (selectedItemsList.size == 1) {
                        AnalyzerBottomActionItem(
                            icon = Icons.Filled.Info,
                            label = "Info",
                            testTag = "analyzer_multiselect_info",
                            onClick = { onOpenProperties(selectedItemsList.first()) }
                        )
                    }
                }
            }
        }

        // Persistent Bottom Clipboard Bar (allows jumping to a destination folder to Paste)
        if (clipboard.isNotEmpty) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analyzer_bottom_clipboard_bar")
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
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onClearClipboard) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = onNavigateToStorageForPaste,
                            modifier = Modifier.testTag("analyzer_choose_paste_folder_button")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Destination Folder")
                        }
                    }
                }
            }
        }
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
                    modifier = Modifier.testTag("analyzer_dialog_rename_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (updatedName.isNotBlank()) {
                            onRenameItem(target, updatedName)
                            selectedUris = selectedUris - target.uriOrPath
                            renameTarget = null
                        }
                    },
                    modifier = Modifier.testTag("analyzer_dialog_confirm_rename")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Confirmation Dialog
    if (deleteConfirmTargets != null) {
        val targets = deleteConfirmTargets!!
        val isLocalOrSaf = targets.all { it.providerType == ProviderType.LOCAL || it.providerType == ProviderType.SAF }
        var useTrash by remember { mutableStateOf(settings.moveToTrashByDefault && isLocalOrSaf) }
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
                        useTrash && count == 1 -> "Move to Trash?"
                        useTrash -> "Move $count items to Trash?"
                        count == 1 -> "Delete \"${targets.first().displayName}\"?"
                        else -> "Delete $count items permanently?"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = when {
                            useTrash && count == 1 -> "\"${targets.first().displayName}\" ($totalSize) will be moved to the Trash. You can restore it anytime."
                            useTrash -> "$count items ($totalSize) will be moved to the Trash. You can restore them anytime."
                            count == 1 -> "\"${targets.first().displayName}\" ($totalSize) will be permanently deleted. This action cannot be undone."
                            else -> "$count items ($totalSize) will be permanently deleted. This action cannot be undone."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isLocalOrSaf) {
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
                                        text = "Move to Trash",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (useTrash) "Files can be recovered from Trash later" else "Skip Trash and delete permanently",
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
                        selectedUris = selectedUris - targets.map { it.uriOrPath }.toSet()
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
                    modifier = Modifier.testTag("analyzer_dialog_confirm_delete")
                ) {
                    Text(if (useTrash) "Move to Trash" else "Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTargets = null }) { Text("Cancel") }
            }
        )
    }

    // Compress Archive Dialog
    if (compressTargets != null) {
        val targets = compressTargets!!
        var archiveBaseName by remember(targets) {
            val defaultName = if (targets.size == 1) {
                targets.first().displayName.substringBeforeLast('.').ifBlank { "Archive" }
            } else {
                "Archive_${System.currentTimeMillis() / 1000}"
            }
            mutableStateOf(defaultName)
        }
        var selectedFormat by remember { mutableStateOf(ArchiveCreationFormat.ZIP) }
        AlertDialog(
            onDismissRequest = { compressTargets = null },
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
                        onCompressItems(targets, archiveBaseName, selectedFormat)
                        selectedUris = emptySet()
                        compressTargets = null
                    }
                ) {
                    Text("Compress")
                }
            },
            dismissButton = {
                TextButton(onClick = { compressTargets = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AnalyzerBottomActionItem(
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

@Composable
private fun StorageOverviewTab(
    report: StorageAnalysisReport,
    onCategoryClick: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Custom Canvas Multi-Segment Ring Chart cached across recompositions
                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Spacer(
                            modifier = Modifier
                                .size(110.dp)
                                .drawWithCache {
                                    val strokeWidth = 18.dp.toPx()
                                    val baseStroke = Stroke(width = strokeWidth)
                                    val arcStroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                    onDrawBehind {
                                        drawArc(
                                            color = Color.Gray.copy(alpha = 0.2f),
                                            startAngle = 0f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            style = baseStroke
                                        )
                                        var currentStartAngle = -90f
                                        report.categoryBuckets.forEach { bucket ->
                                            val sweep = (bucket.fractionOfTotal * 360f).coerceAtLeast(2f)
                                            drawArc(
                                                color = Color(bucket.colorHex),
                                                startAngle = currentStartAngle,
                                                sweepAngle = sweep,
                                                useCenter = false,
                                                style = arcStroke
                                            )
                                            currentStartAngle += sweep
                                        }
                                    }
                                }
                        )
                        val pct = if (report.totalBytes > 0) {
                            ((report.usedBytes.toDouble() / report.totalBytes.toDouble()) * 100).toInt()
                        } else 0
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Total Capacity: ${formatByteSize(report.totalBytes)}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Used: ${formatByteSize(report.usedBytes)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Available: ${formatByteSize(report.freeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ApkGreen
                        )
                    }
                }
            }
        }

        item {
            Text("Usage by Category (Tap to filter files)", style = MaterialTheme.typography.titleMedium)
        }

        items(
            items = report.categoryBuckets,
            key = { it.categoryName },
            contentType = { "category_bucket" }
        ) { bucket ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCategoryClick(bucket.categoryName) }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(bucket.colorHex))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${bucket.categoryName} (${bucket.fileCount} files)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(formatByteSize(bucket.bytesUsed), style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator(
                    progress = { bucket.fractionOfTotal },
                    color = Color(bucket.colorHex),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (report.extensionDistribution.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Top File Extensions", style = MaterialTheme.typography.titleMedium)
            }
            items(
                items = report.extensionDistribution,
                key = { it.first },
                contentType = { "ext_dist" }
            ) { (ext, count, bytes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$ext ($count files)",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily
                    )
                    Text(
                        text = formatByteSize(bytes),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun LargestFilesTab(
    files: List<FileReference>,
    selectedUris: Set<String>,
    bookmarkedPaths: Set<String>,
    settings: AppSettingsState,
    onItemClick: (FileReference) -> Unit,
    onItemLongClick: (FileReference) -> Unit,
    onRename: (FileReference) -> Unit,
    onCopy: (FileReference) -> Unit,
    onCut: (FileReference) -> Unit,
    onDelete: (FileReference) -> Unit,
    onToggleBookmark: (FileReference) -> Unit,
    onShare: (FileReference) -> Unit,
    onProperties: (FileReference) -> Unit,
    onOpenParentFolder: (FileReference) -> Unit,
    onOpenWith: ((FileReference) -> Unit)?,
    onQuickPreview: ((FileReference) -> Unit)?,
    onCompress: (FileReference) -> Unit
) {
    val context = LocalContext.current
    if (files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No matching files found.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("analyzer_largest_files_list")
    ) {
        items(
            items = files,
            key = { it.uriOrPath },
            contentType = { "file" }
        ) { file ->
            FileItemRow(
                file = file,
                isSelected = file.uriOrPath in selectedUris,
                isBookmarked = file.uriOrPath in bookmarkedPaths,
                showThumbnails = settings.showThumbnails,
                showExtensions = settings.showFileExtensions,
                onClick = { onItemClick(file) },
                onLongClick = { onItemLongClick(file) },
                onRename = { onRename(file) },
                onCopy = { onCopy(file) },
                onCut = { onCut(file) },
                onDelete = { onDelete(file) },
                onToggleBookmark = { onToggleBookmark(file) },
                onShare = { onShare(file) },
                onProperties = { onProperties(file) },
                onOpenParentFolder = { onOpenParentFolder(file) },
                onOpenWith = if (onOpenWith != null) ({ onOpenWith(file) }) else null,
                onQuickPreview = if (onQuickPreview != null) ({ onQuickPreview(file) }) else null,
                onCompress = { onCompress(file) },
                showParentPath = true,
                context = context
            )
        }
    }
}

@Composable
private fun LargestDirectoriesTab(
    directories: List<FileReference>,
    selectedUris: Set<String>,
    bookmarkedPaths: Set<String>,
    settings: AppSettingsState,
    onItemClick: (FileReference) -> Unit,
    onItemLongClick: (FileReference) -> Unit,
    onDrillDown: (FileReference) -> Unit,
    onRename: (FileReference) -> Unit,
    onCopy: (FileReference) -> Unit,
    onCut: (FileReference) -> Unit,
    onDelete: (FileReference) -> Unit,
    onToggleBookmark: (FileReference) -> Unit,
    onShare: (FileReference) -> Unit,
    onProperties: (FileReference) -> Unit,
    onOpenParentFolder: (FileReference) -> Unit,
    onCompress: (FileReference) -> Unit
) {
    val context = LocalContext.current
    if (directories.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No matching folders found.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("analyzer_largest_folders_list")
    ) {
        items(
            items = directories,
            key = { it.uriOrPath },
            contentType = { "dir" }
        ) { dirRef ->
            FileItemRow(
                file = dirRef,
                isSelected = dirRef.uriOrPath in selectedUris,
                isBookmarked = dirRef.uriOrPath in bookmarkedPaths,
                showThumbnails = settings.showThumbnails,
                showExtensions = settings.showFileExtensions,
                onClick = { onItemClick(dirRef) },
                onLongClick = { onItemLongClick(dirRef) },
                onRename = { onRename(dirRef) },
                onCopy = { onCopy(dirRef) },
                onCut = { onCut(dirRef) },
                onDelete = { onDelete(dirRef) },
                onToggleBookmark = { onToggleBookmark(dirRef) },
                onShare = { onShare(dirRef) },
                onProperties = { onProperties(dirRef) },
                onOpenParentFolder = { onOpenParentFolder(dirRef) },
                onDrillDownAnalyze = { onDrillDown(dirRef) },
                onCompress = { onCompress(dirRef) },
                showParentPath = true,
                context = context
            )
        }
    }
}

@Composable
private fun DuplicateFilesTab(
    groups: List<com.example.core.model.DuplicateFileGroup>,
    selectedUris: Set<String>,
    bookmarkedPaths: Set<String>,
    settings: AppSettingsState,
    onItemClick: (FileReference) -> Unit,
    onItemLongClick: (FileReference) -> Unit,
    onSelectRedundantInGroup: (List<FileReference>) -> Unit,
    onRename: (FileReference) -> Unit,
    onCopy: (FileReference) -> Unit,
    onCut: (FileReference) -> Unit,
    onDelete: (FileReference) -> Unit,
    onToggleBookmark: (FileReference) -> Unit,
    onShare: (FileReference) -> Unit,
    onProperties: (FileReference) -> Unit,
    onOpenParentFolder: (FileReference) -> Unit,
    onOpenWith: ((FileReference) -> Unit)?,
    onQuickPreview: ((FileReference) -> Unit)?,
    onCompress: (FileReference) -> Unit
) {
    val context = LocalContext.current
    if (groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No duplicate files detected (verified by SHA-256).")
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("analyzer_duplicates_list")
    ) {
        items(
            items = groups,
            key = { it.sha256Hash },
            contentType = { "dup_group" }
        ) { group ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${group.files.size} Identical Copies (${formatByteSize(group.fileSizeBytes)} each)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Reclaim ${formatByteSize(group.reclaimableBytes)} • SHA-256: ${group.sha256Hash.take(16)}...",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (group.files.size > 1) {
                            TextButton(onClick = { onSelectRedundantInGroup(group.files) }) {
                                Text("Select Copies")
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    group.files.forEach { fileRef ->
                        FileItemRow(
                            file = fileRef,
                            isSelected = fileRef.uriOrPath in selectedUris,
                            isBookmarked = fileRef.uriOrPath in bookmarkedPaths,
                            showThumbnails = settings.showThumbnails,
                            showExtensions = settings.showFileExtensions,
                            onClick = { onItemClick(fileRef) },
                            onLongClick = { onItemLongClick(fileRef) },
                            onRename = { onRename(fileRef) },
                            onCopy = { onCopy(fileRef) },
                            onCut = { onCut(fileRef) },
                            onDelete = { onDelete(fileRef) },
                            onToggleBookmark = { onToggleBookmark(fileRef) },
                            onShare = { onShare(fileRef) },
                            onProperties = { onProperties(fileRef) },
                            onOpenParentFolder = { onOpenParentFolder(fileRef) },
                            onOpenWith = if (onOpenWith != null) ({ onOpenWith(fileRef) }) else null,
                            onQuickPreview = if (onQuickPreview != null) ({ onQuickPreview(fileRef) }) else null,
                            onCompress = { onCompress(fileRef) },
                            showParentPath = true,
                            context = context
                        )
                    }
                }
            }
        }
    }
}

/**
 * Installed Application & APK Manager Screen.
 */
@Composable
fun ApplicationsScreen(
    apps: List<InstalledAppInfo>,
    includeSystemApps: Boolean,
    isLoading: Boolean,
    onToggleSystemApps: (Boolean) -> Unit,
    onLaunchApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onShareApk: (File) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered by produceState(initialValue = apps, apps, searchQuery) {
        value = if (searchQuery.isBlank()) {
            apps
        } else {
            withContext(Dispatchers.Default) {
                apps.filter {
                    it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
                }
            }
        }
    }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("applications_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed packages...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = includeSystemApps,
                onClick = { onToggleSystemApps(!includeSystemApps) },
                label = { Text("System Apps") }
            )
        }
        HorizontalDivider()

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = filtered,
                    key = { it.packageName },
                    contentType = { "app_card" }
                ) { app ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Filled.Android,
                                        contentDescription = null,
                                        tint = ApkGreen,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(app.appName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = formatByteSize(app.apkSizeBytes),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Text(
                                text = "v${app.versionName} (${app.versionCode}) • SDK ${app.targetSdk} • Installed ${dateFormat.format(Date(app.firstInstallTimeMs))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onOpenAppInfo(app.packageName) }) {
                                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("App Info")
                                }
                                if (app.canShareApk) {
                                    TextButton(onClick = { onShareApk(File(app.apkPath)) }) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share APK")
                                    }
                                }
                                TextButton(onClick = { onLaunchApp(app.packageName) }) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bookmarks Manager Screen.
 */
@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkEntity>,
    onOpenBookmark: (BookmarkEntity) -> Unit,
    onRenameBookmark: (BookmarkEntity, String) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit
) {
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("bookmarks_screen")
    ) {
        Text(
            text = "Pinned Bookmarks (${bookmarks.size})",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        HorizontalDivider()
        if (bookmarks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No bookmarks yet. Use the file menu to pin frequent folders or files.")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = bookmarks,
                    key = { it.id },
                    contentType = { "bookmark" }
                ) { bm ->
                    Card(
                        onClick = { onOpenBookmark(bm) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = FolderAmber)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(bm.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${bm.providerType} • ${bm.uriOrPath}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row {
                                IconButton(onClick = { editingBookmark = bm }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Rename bookmark")
                                }
                                IconButton(onClick = { onDeleteBookmark(bm) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remove bookmark",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingBookmark != null) {
        val bm = editingBookmark!!
        var newTitle by remember(bm) { mutableStateOf(bm.title) }
        AlertDialog(
            onDismissRequest = { editingBookmark = null },
            title = { Text("Rename Bookmark") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Bookmark title") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameBookmark(bm, newTitle)
                        editingBookmark = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmark = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Scoped Trash Screen with Restore, Permanent Delete, and Empty Trash actions.
 */
@Composable
fun TrashScreen(
    trashItems: List<TrashItemEntity>,
    onRestoreItem: (TrashItemEntity) -> Unit,
    onDeletePermanently: (TrashItemEntity) -> Unit,
    onEmptyTrash: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("trash_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Trash (${trashItems.size})", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Local & SAF soft-deleted items. Remote FTP/SFTP/SMB/Cloud deletions bypass local trash.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trashItems.isNotEmpty()) {
                Button(onClick = onEmptyTrash) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Empty Trash")
                }
            }
        }
        HorizontalDivider()

        if (trashItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Trash is empty.")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = trashItems,
                    key = { it.id },
                    contentType = { "trash" }
                ) { item ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${formatByteSize(item.sizeBytes)} • From: ${item.originalParentUriOrPath}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row {
                                IconButton(onClick = { onRestoreItem(item) }) {
                                    Icon(
                                        Icons.Filled.RestoreFromTrash,
                                        contentDescription = "Restore ${item.displayName}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onDeletePermanently(item) }) {
                                    Icon(
                                        Icons.Filled.DeleteForever,
                                        contentDescription = "Delete permanently",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
