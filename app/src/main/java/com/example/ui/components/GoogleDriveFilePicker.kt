package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.cloud.CloudProviderKind
import com.example.core.cloud.CloudStorageEngine
import com.example.core.database.BookmarkEntity
import com.example.core.database.CloudAccountEntity
import com.example.core.datastore.AppSettingsState
import com.example.core.filesystem.ClipboardMode
import com.example.core.filesystem.ClipboardState
import com.example.core.filesystem.FileSortAndFilterEngine
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.example.core.model.SortField
import com.example.core.model.SortOrder
import com.example.core.model.ViewMode
import com.example.ui.theme.ApkGreen
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.launch

/**
 * Top-level Google Drive sections supported by the native FilePicker.
 */
enum class GoogleDriveSection(
    val sectionId: String,
    val title: String,
    val icon: ImageVector
) {
    MY_DRIVE("root", "My Drive", Icons.Filled.Cloud),
    SHARED_WITH_ME("sharedWithMe", "Shared with me", Icons.Filled.FolderShared),
    STARRED("starred", "Starred", Icons.Filled.Star),
    RECENT("recent", "Recent", Icons.Filled.History)
}

/**
 * Native Google Drive FilePicker interface that mimics the local file browser, allowing users to
 * browse, select, and interact with Google Drive files directly within OpenFile using the OAuth2
 * token retrieved from the existing authentication flow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoogleDriveFilePicker(
    account: CloudAccountEntity,
    oauthAccessToken: String,
    settings: AppSettingsState,
    bookmarks: List<BookmarkEntity>,
    clipboard: ClipboardState,
    onPreviewOrEditFile: (FileReference) -> Unit,
    onCopyItems: (List<FileReference>, ClipboardMode) -> Unit,
    onPasteToDriveFolder: (CloudAccountEntity, String) -> Unit,
    onClearClipboard: () -> Unit,
    onDownloadToLocal: (FileReference) -> Unit,
    onShareItem: (FileReference) -> Unit,
    onOpenProperties: (FileReference) -> Unit,
    onToggleBookmark: (FileReference) -> Unit,
    onOpenFullBrowserAtFolder: ((CloudAccountEntity, String) -> Unit)? = null,
    onReauthenticateOAuth: (() -> Unit)? = null,
    onFilesPicked: ((List<FileReference>) -> Unit)? = null,
    onClosePicker: (() -> Unit)? = null,
    onStatusMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeSection by remember(account.id) { mutableStateOf(GoogleDriveSection.MY_DRIVE) }
    var currentFolderIdOrPath by remember(account.id) { mutableStateOf("root") }
    var breadcrumbs by remember(account.id) {
        mutableStateOf(
            listOf(
                FileReference(
                    providerType = ProviderType.GOOGLE_DRIVE,
                    uriOrPath = "root",
                    displayName = "${account.displayName} (${account.accountEmail})",
                    parentUriOrPath = null,
                    isDirectory = true,
                    capabilities = FileReference.defaultCapabilitiesFor(ProviderType.GOOGLE_DRIVE, true),
                    metadata = mapOf("accountId" to account.id.toString())
                )
            )
        )
    }
    var rawItems by remember { mutableStateOf<List<FileReference>>(emptyList()) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var viewMode by remember(settings.defaultViewMode) { mutableStateOf(settings.defaultViewMode) }
    var sortField by remember(settings.defaultSortField) { mutableStateOf(settings.defaultSortField) }
    var sortOrder by remember(settings.defaultSortOrder) { mutableStateOf(settings.defaultSortOrder) }
    var showSortMenu by remember { mutableStateOf(false) }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileReference?>(null) }
    var deleteConfirmTargets by remember { mutableStateOf<List<FileReference>?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    val bookmarkedPaths = remember(bookmarks) { bookmarks.map { it.uriOrPath }.toSet() }
    val providerKind = remember(account.provider) {
        runCatching { CloudProviderKind.valueOf(account.provider) }.getOrDefault(CloudProviderKind.GOOGLE_DRIVE)
    }

    val isLiveOAuth = remember(account, oauthAccessToken) {
        CloudStorageEngine.isLiveOAuthSession(account, oauthAccessToken)
    }
    val maskedTokenPreview = remember(oauthAccessToken) {
        val clean = oauthAccessToken.trim()
        when {
            clean.isEmpty() -> "OAuth2 Session Ready"
            clean.length <= 12 -> "Bearer ${clean.take(4)}…"
            else -> "Bearer ${clean.take(8)}…${clean.takeLast(4)}"
        }
    }

    // Load directory items using the OAuth2 token from the existing authentication flow
    LaunchedEffect(account.id, oauthAccessToken, currentFolderIdOrPath, settings.showHiddenFiles, reloadKey) {
        isLoading = true
        errorMessage = null
        CloudStorageEngine.listCloudDirectory(
            account = account,
            accessToken = oauthAccessToken,
            folderIdOrPath = currentFolderIdOrPath,
            context = context,
            showHidden = settings.showHiddenFiles
        ).fold(
            onSuccess = { loaded ->
                rawItems = loaded
                isLoading = false
            },
            onFailure = { err ->
                errorMessage = err.message ?: "Unable to list Google Drive files"
                isLoading = false
            }
        )
    }

    val sortedAndFilteredItems = remember(rawItems, searchQuery, sortField, sortOrder, settings.showHiddenFiles) {
        val sorted = FileSortAndFilterEngine.sortFiles(rawItems, sortField, sortOrder)
        if (searchQuery.isBlank()) {
            if (settings.showHiddenFiles) sorted else sorted.filter { !it.isHidden }
        } else {
            FileSortAndFilterEngine.filterFiles(
                files = sorted,
                query = searchQuery,
                extensionFilter = null,
                showHidden = settings.showHiddenFiles
            )
        }
    }

    fun navigateToDriveFolder(targetRef: FileReference) {
        selectedUris = emptySet()
        val targetPath = targetRef.uriOrPath.ifBlank { "root" }
        currentFolderIdOrPath = targetPath
        val rootRef = FileReference(
            providerType = providerKind.providerType,
            uriOrPath = activeSection.sectionId,
            displayName = "${account.displayName} • ${activeSection.title}",
            parentUriOrPath = null,
            isDirectory = true,
            capabilities = FileReference.defaultCapabilitiesFor(providerKind.providerType, true),
            metadata = mapOf("accountId" to account.id.toString())
        )
        breadcrumbs = when {
            targetPath == activeSection.sectionId || targetPath.equals("root", ignoreCase = true) -> listOf(rootRef)
            targetPath.contains("/") || !isLiveOAuth -> {
                val segments = targetPath.split('/').filter { it.isNotBlank() }
                val crumbs = mutableListOf(rootRef)
                var running = ""
                var parent = "root"
                for (seg in segments) {
                    running = if (running.isEmpty()) seg else "$running/$seg"
                    crumbs += FileReference(
                        providerType = providerKind.providerType,
                        uriOrPath = running,
                        displayName = seg,
                        parentUriOrPath = parent,
                        isDirectory = true,
                        capabilities = FileReference.defaultCapabilitiesFor(providerKind.providerType, true),
                        metadata = mapOf("accountId" to account.id.toString())
                    )
                    parent = running
                }
                crumbs
            }
            else -> {
                val existingIndex = breadcrumbs.indexOfFirst { it.uriOrPath == targetPath }
                if (existingIndex >= 0) {
                    breadcrumbs.subList(0, existingIndex + 1)
                } else {
                    breadcrumbs + targetRef
                }
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("google_drive_file_picker")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. OAuth2 Token & Account Header Bar
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${account.displayName} FilePicker",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    color = ApkGreen.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.testTag("drive_picker_oauth_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VerifiedUser,
                                            contentDescription = null,
                                            tint = ApkGreen,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = maskedTokenPreview,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = ApkGreen
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${account.accountEmail} • Native OAuth2 Drive Browser & FilePicker",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (onReauthenticateOAuth != null) {
                            TextButton(
                                onClick = onReauthenticateOAuth,
                                modifier = Modifier.testTag("drive_picker_reauth_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("OAuth", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (onOpenFullBrowserAtFolder != null) {
                            FilledTonalButton(
                                onClick = { onOpenFullBrowserAtFolder(account, currentFolderIdOrPath) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .testTag("drive_picker_expand_browser_button")
                            ) {
                                Text("Full Browser", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (onClosePicker != null) {
                            IconButton(
                                onClick = onClosePicker,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("drive_picker_close_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close Google Drive FilePicker")
                            }
                        }
                    }
                }
            }

            // 2. Google Drive Section Tabs (My Drive, Shared with me, Starred, Recent)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoogleDriveSection.entries.forEach { section ->
                    val isSelected = activeSection == section
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            activeSection = section
                            selectedUris = emptySet()
                            currentFolderIdOrPath = section.sectionId
                            breadcrumbs = listOf(
                                FileReference(
                                    providerType = providerKind.providerType,
                                    uriOrPath = section.sectionId,
                                    displayName = "${account.displayName} • ${section.title}",
                                    parentUriOrPath = null,
                                    isDirectory = true,
                                    capabilities = FileReference.defaultCapabilitiesFor(providerKind.providerType, true),
                                    metadata = mapOf("accountId" to account.id.toString())
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(section.title) },
                        modifier = Modifier.testTag("drive_picker_section_${section.sectionId}")
                    )
                }
            }

            // 3. Local-Browser-Identical Search, View Mode, Sort, and Create Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Drive files...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
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
                        .testTag("drive_picker_search_input")
                )

                // List / Grid View Toggle
                IconButton(
                    onClick = {
                        viewMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("drive_picker_view_toggle")
                ) {
                    Icon(
                        imageVector = if (viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = "Toggle Drive view mode",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Sort Menu
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("drive_picker_sort_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort Drive files",
                            modifier = Modifier.size(20.dp)
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

                // New Drive Folder
                IconButton(
                    onClick = { showNewFolderDialog = true },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("drive_picker_new_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreateNewFolder,
                        contentDescription = "New Drive Folder",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // New Drive Text File
                IconButton(
                    onClick = { showNewFileDialog = true },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("drive_picker_new_file_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                        contentDescription = "New Drive Text File",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Refresh Drive Directory
                IconButton(
                    onClick = { reloadKey++ },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("drive_picker_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh Drive folder",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 4. Interactive Breadcrumb Bar (mimics local FileBrowserScreen)
            if (breadcrumbs.isNotEmpty()) {
                BreadcrumbBar(
                    breadcrumbs = breadcrumbs,
                    providerType = providerKind.providerType,
                    onCrumbClick = { crumb -> navigateToDriveFolder(crumb) }
                )
            }

            // 5. Multi-Selection Top Header Strip
            if (selectedUris.isNotEmpty()) {
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
                            IconButton(
                                onClick = { selectedUris = emptySet() },
                                modifier = Modifier.testTag("drive_picker_clear_selection")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                            }
                            Text(
                                text = "${selectedUris.size} selected in Google Drive",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = {
                                selectedUris = sortedAndFilteredItems.map { it.uriOrPath }.toSet()
                            },
                            modifier = Modifier.testTag("drive_picker_select_all")
                        ) {
                            Icon(Icons.Filled.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select All")
                        }
                    }
                }
            }

            HorizontalDivider()

            // 6. Drive File List / Grid Body
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = errorMessage ?: "Error loading Google Drive",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            OutlinedButton(onClick = { reloadKey++ }) {
                                Text("Retry")
                            }
                        }
                    }
                    sortedAndFilteredItems.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) {
                                    "No Google Drive files match '$searchQuery'"
                                } else {
                                    "This Google Drive folder is empty"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                            OutlinedButton(onClick = { showNewFolderDialog = true }) {
                                Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Drive Folder")
                            }
                        }
                    }
                    viewMode == ViewMode.LIST -> {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("drive_picker_list")
                        ) {
                            items(
                                items = sortedAndFilteredItems,
                                key = { it.uriOrPath },
                                contentType = { if (it.isDirectory) "folder" else "file" }
                            ) { item ->
                                val isSelected = selectedUris.contains(item.uriOrPath)
                                val isBookmarked = bookmarkedPaths.contains(item.uriOrPath)
                                FileItemRow(
                                    file = item,
                                    isSelected = isSelected,
                                    isBookmarked = isBookmarked,
                                    showThumbnails = settings.showThumbnails,
                                    showExtensions = true,
                                    onClick = {
                                        if (selectedUris.isNotEmpty()) {
                                            selectedUris = if (isSelected) selectedUris - item.uriOrPath else selectedUris + item.uriOrPath
                                        } else if (item.isDirectory) {
                                            navigateToDriveFolder(item)
                                        } else {
                                            onPreviewOrEditFile(item)
                                        }
                                    },
                                    onLongClick = {
                                        selectedUris = if (isSelected) selectedUris - item.uriOrPath else selectedUris + item.uriOrPath
                                    },
                                    onQuickPreview = { onPreviewOrEditFile(item) },
                                    onCopy = { onCopyItems(listOf(item), ClipboardMode.COPY) },
                                    onCut = { onCopyItems(listOf(item), ClipboardMode.CUT) },
                                    onRename = { renameTarget = item },
                                    onDelete = { deleteConfirmTargets = listOf(item) },
                                    onShare = { onShareItem(item) },
                                    onProperties = { onOpenProperties(item) },
                                    onToggleBookmark = { onToggleBookmark(item) },
                                    onDownloadRemote = { onDownloadToLocal(item) }
                                )
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 132.dp),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("drive_picker_grid")
                        ) {
                            items(
                                items = sortedAndFilteredItems,
                                key = { it.uriOrPath },
                                contentType = { if (it.isDirectory) "folder" else "file" }
                            ) { item ->
                                val isSelected = selectedUris.contains(item.uriOrPath)
                                FileItemGridCard(
                                    file = item,
                                    isSelected = selectedUris.contains(item.uriOrPath),
                                    showThumbnails = settings.showThumbnails,
                                    onClick = {
                                        if (selectedUris.isNotEmpty()) {
                                            selectedUris = if (isSelected) selectedUris - item.uriOrPath else selectedUris + item.uriOrPath
                                        } else if (item.isDirectory) {
                                            navigateToDriveFolder(item)
                                        } else {
                                            onPreviewOrEditFile(item)
                                        }
                                    },
                                    onLongClick = {
                                        selectedUris = if (isSelected) selectedUris - item.uriOrPath else selectedUris + item.uriOrPath
                                    },
                                    onProperties = { onOpenProperties(item) }
                                )
                            }
                        }
                    }
                }
            }

            // 7. Bottom Contextual Action Bar (Multi-select actions + FilePicker Confirm)
            if (selectedUris.isNotEmpty()) {
                val selectedItems = sortedAndFilteredItems.filter { selectedUris.contains(it.uriOrPath) }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    onCopyItems(selectedItems, ClipboardMode.COPY)
                                    selectedUris = emptySet()
                                },
                                modifier = Modifier.testTag("drive_picker_action_copy")
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy")
                            }
                            TextButton(
                                onClick = {
                                    onCopyItems(selectedItems, ClipboardMode.CUT)
                                    selectedUris = emptySet()
                                },
                                modifier = Modifier.testTag("drive_picker_action_cut")
                            ) {
                                Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cut / Move")
                            }
                            if (selectedItems.size == 1) {
                                TextButton(
                                    onClick = { renameTarget = selectedItems.first() },
                                    modifier = Modifier.testTag("drive_picker_action_rename")
                                ) {
                                    Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rename")
                                }
                            }
                            TextButton(
                                onClick = {
                                    selectedItems.forEach { onDownloadToLocal(it) }
                                    selectedUris = emptySet()
                                },
                                modifier = Modifier.testTag("drive_picker_action_download")
                            ) {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Download")
                            }
                            if (selectedItems.size == 1 && !selectedItems.first().isDirectory) {
                                TextButton(
                                    onClick = { onShareItem(selectedItems.first()) }
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share")
                                }
                            }
                            TextButton(
                                onClick = { deleteConfirmTargets = selectedItems },
                                modifier = Modifier.testTag("drive_picker_action_delete")
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }

                        if (onFilesPicked != null && selectedItems.isNotEmpty()) {
                            Button(
                                onClick = {
                                    onFilesPicked(selectedItems)
                                    selectedUris = emptySet()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("drive_picker_confirm_button")
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pick / Import Selected (${selectedItems.size})")
                            }
                        }
                    }
                }
            }

            // 8. Clipboard Paste Bar (allows pasting Local or Cloud files directly into the current Google Drive folder)
            if (clipboard.isNotEmpty) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Clipboard: ${clipboard.items.size} item(s) (${if (clipboard.mode == ClipboardMode.CUT) "Cut / Move" else "Copy"})",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Ready to paste into Google Drive (${currentFolderIdOrPath})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onClearClipboard) {
                                Text("Clear")
                            }
                            Button(
                                onClick = {
                                    onPasteToDriveFolder(account, currentFolderIdOrPath)
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(350)
                                        reloadKey++
                                    }
                                },
                                modifier = Modifier.testTag("drive_picker_paste_button")
                            ) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste to Drive")
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Folder Dialog inside Google Drive FilePicker
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Google Drive Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drive_picker_new_folder_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = folderName.trim()
                        if (trimmed.isNotEmpty()) {
                            showNewFolderDialog = false
                            coroutineScope.launch {
                                CloudStorageEngine.createCloudFolder(
                                    account = account,
                                    accessToken = oauthAccessToken,
                                    parentIdOrPath = currentFolderIdOrPath,
                                    folderName = trimmed,
                                    context = context
                                ).fold(
                                    onSuccess = {
                                        onStatusMessage("Created '$trimmed' in ${account.displayName}")
                                        reloadKey++
                                    },
                                    onFailure = {
                                        onStatusMessage("Create folder failed: ${it.message}")
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.testTag("drive_picker_create_folder_confirm")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Create Text File Dialog inside Google Drive FilePicker
    if (showNewFileDialog) {
        var fileName by remember { mutableStateOf("drive_notes.txt") }
        var fileContent by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New Google Drive Text File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("File name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drive_picker_new_file_name_input")
                    )
                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        label = { Text("Initial content") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drive_picker_new_file_content_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = fileName.trim()
                        if (trimmed.isNotEmpty()) {
                            showNewFileDialog = false
                            coroutineScope.launch {
                                CloudStorageEngine.createCloudTextFile(
                                    account = account,
                                    accessToken = oauthAccessToken,
                                    parentIdOrPath = currentFolderIdOrPath,
                                    fileName = trimmed,
                                    content = fileContent,
                                    context = context
                                ).fold(
                                    onSuccess = {
                                        onStatusMessage("Saved '$trimmed' to ${account.displayName}")
                                        reloadKey++
                                    },
                                    onFailure = {
                                        onStatusMessage("Create file failed: ${it.message}")
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.testTag("drive_picker_create_file_confirm")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Rename Dialog inside Google Drive FilePicker
    if (renameTarget != null) {
        val target = renameTarget!!
        var newName by remember(target) { mutableStateOf(target.displayName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename in Google Drive") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drive_picker_rename_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotEmpty()) {
                            renameTarget = null
                            coroutineScope.launch {
                                CloudStorageEngine.renameCloudItem(
                                    account = account,
                                    accessToken = oauthAccessToken,
                                    target = target,
                                    newName = trimmed,
                                    context = context
                                ).fold(
                                    onSuccess = {
                                        selectedUris = emptySet()
                                        onStatusMessage("Renamed to '$trimmed'")
                                        reloadKey++
                                    },
                                    onFailure = {
                                        onStatusMessage("Rename failed: ${it.message}")
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.testTag("drive_picker_rename_confirm")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Confirmation Dialog inside Google Drive FilePicker
    if (deleteConfirmTargets != null) {
        val targets = deleteConfirmTargets!!
        AlertDialog(
            onDismissRequest = { deleteConfirmTargets = null },
            title = { Text("Delete ${targets.size} Drive Item(s)?") },
            text = {
                Text("Remove the selected item(s) from ${account.displayName} (${account.accountEmail})?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteConfirmTargets = null
                        coroutineScope.launch {
                            var deleted = 0
                            for (item in targets) {
                                if (CloudStorageEngine.deleteCloudItem(account, oauthAccessToken, item, context).isSuccess) {
                                    deleted++
                                }
                            }
                            selectedUris = emptySet()
                            onStatusMessage("Deleted $deleted item(s) from ${account.displayName}")
                            reloadKey++
                        }
                    },
                    modifier = Modifier.testTag("drive_picker_delete_confirm")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTargets = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Full-screen Modal Dialog hosting the native GoogleDriveFilePicker so users can pick/import
 * or manage Google Drive files from any screen in OpenFile.
 */
@Composable
fun GoogleDriveFilePickerDialog(
    account: CloudAccountEntity,
    oauthAccessToken: String,
    settings: AppSettingsState,
    bookmarks: List<BookmarkEntity>,
    clipboard: ClipboardState,
    onPreviewOrEditFile: (FileReference) -> Unit,
    onCopyItems: (List<FileReference>, ClipboardMode) -> Unit,
    onPasteToDriveFolder: (CloudAccountEntity, String) -> Unit,
    onClearClipboard: () -> Unit,
    onDownloadToLocal: (FileReference) -> Unit,
    onShareItem: (FileReference) -> Unit,
    onOpenProperties: (FileReference) -> Unit,
    onToggleBookmark: (FileReference) -> Unit,
    onOpenFullBrowserAtFolder: (CloudAccountEntity, String) -> Unit,
    onReauthenticateOAuth: () -> Unit,
    onFilesPicked: (List<FileReference>) -> Unit,
    onDismiss: () -> Unit,
    onStatusMessage: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .testTag("google_drive_file_picker_dialog")
        ) {
            GoogleDriveFilePicker(
                account = account,
                oauthAccessToken = oauthAccessToken,
                settings = settings,
                bookmarks = bookmarks,
                clipboard = clipboard,
                onPreviewOrEditFile = onPreviewOrEditFile,
                onCopyItems = onCopyItems,
                onPasteToDriveFolder = onPasteToDriveFolder,
                onClearClipboard = onClearClipboard,
                onDownloadToLocal = onDownloadToLocal,
                onShareItem = onShareItem,
                onOpenProperties = onOpenProperties,
                onToggleBookmark = onToggleBookmark,
                onOpenFullBrowserAtFolder = { acc, folder ->
                    onDismiss()
                    onOpenFullBrowserAtFolder(acc, folder)
                },
                onReauthenticateOAuth = onReauthenticateOAuth,
                onFilesPicked = { picked ->
                    onFilesPicked(picked)
                    onDismiss()
                },
                onClosePicker = onDismiss,
                onStatusMessage = onStatusMessage,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
