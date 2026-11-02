package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.ProviderType
import com.example.ui.components.ConflictResolutionDialog
import com.example.ui.components.FilePreviewDialog
import com.example.ui.components.FilePropertiesModalSheet
import com.example.ui.components.TransferQueueBottomSheet
import com.example.ui.screens.ApplicationsScreen
import com.example.ui.screens.BookmarksScreen
import com.example.ui.screens.CloudStorageScreen
import com.example.ui.screens.FileBrowserScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NetworkScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StorageAnalysisScreen
import com.example.ui.screens.TrashScreen
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenFileAppShell(viewModel: OpenFileViewModel) {
    val currentDest by viewModel.currentDestination.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val volumes by viewModel.storageVolumes.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val networkProfiles by viewModel.networkProfiles.collectAsStateWithLifecycle()
    val cloudAccounts by viewModel.cloudAccounts.collectAsStateWithLifecycle()
    val trashItems by viewModel.trashItems.collectAsStateWithLifecycle()
    val transferJobs by viewModel.transferJobs.collectAsStateWithLifecycle()
    val clipboard by viewModel.clipboard.collectAsStateWithLifecycle()
    val conflictPrompt by viewModel.conflictPrompt.collectAsStateWithLifecycle()
    val undoAction by viewModel.latestUndoAction.collectAsStateWithLifecycle()
    val ftpState by viewModel.ftpServerState.collectAsStateWithLifecycle()
    val browserState by viewModel.browserState.collectAsStateWithLifecycle()
    val previewFile by viewModel.previewFile.collectAsStateWithLifecycle()
    val propertiesFile by viewModel.propertiesFile.collectAsStateWithLifecycle()
    val propertiesChecksums by viewModel.propertiesChecksums.collectAsStateWithLifecycle()
    val propertiesApkInfo by viewModel.propertiesApkInfo.collectAsStateWithLifecycle()
    val analysisReport by viewModel.analysisReport.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzingStorage.collectAsStateWithLifecycle()
    val analysisProgress by viewModel.analysisProgressText.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val includeSystemApps by viewModel.includeSystemApps.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val discoveredHosts by viewModel.discoveredLanHosts.collectAsStateWithLifecycle()
    val isScanningLan by viewModel.isScanningLan.collectAsStateWithLifecycle()
    val sshHostKeyPrompt by viewModel.sshHostKeyPrompt.collectAsStateWithLifecycle()
    val activePkceSession by viewModel.activePkceSession.collectAsStateWithLifecycle()
    val activeWebCloudProvider by viewModel.activeWebCloudProvider.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val categoryCounts by viewModel.categoryCounts.collectAsStateWithLifecycle()
    val selectedNetworkTab by viewModel.selectedNetworkTab.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var showTransferQueueSheet by remember { mutableStateOf(false) }

    // SAF DocumentTree launcher
    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.onSafTreeGranted(treeUri)
        }
    }

    // Runtime permissions launcher (Media & Notifications)
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshCurrentDirectory()
    }

    LaunchedEffect(Unit) {
        val permsToRequest = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        runCatching { permissionsLauncher.launch(permsToRequest.toTypedArray()) }
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusMessage()
    }

    // Hardware Back navigation handling for browser stack, active cloud web portal & sub-destinations
    BackHandler(
        enabled = currentDest != OpenFileDestination.HOME ||
            browserState.isMultiSelectActive ||
            browserState.inspectArchiveFile != null ||
            activeWebCloudProvider != null
    ) {
        if (activeWebCloudProvider != null) {
            viewModel.closeWebCloudPortal()
            return@BackHandler
        }
        val handledInBrowser = if (currentDest in setOf(
                OpenFileDestination.INTERNAL_STORAGE,
                OpenFileDestination.DOWNLOADS,
                OpenFileDestination.IMAGES,
                OpenFileDestination.VIDEOS,
                OpenFileDestination.AUDIO,
                OpenFileDestination.DOCUMENTS,
                OpenFileDestination.ARCHIVES,
                OpenFileDestination.LARGE_FILES,
                OpenFileDestination.RECENT_FILES
            )
        ) {
            viewModel.navigateUpInBrowser()
        } else {
            false
        }
        if (!handledInBrowser) {
            viewModel.navigateToDestination(OpenFileDestination.HOME)
        }
    }

    val activeTransferCount = remember(transferJobs) {
        transferJobs.count { it.status == "RUNNING" || it.status == "PAUSED" || it.status == "INTERRUPTED" }
    }

    val onCycleThemeMode: () -> Unit = {
        val nextMode = when (settings.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.AMOLED
            ThemeMode.AMOLED -> ThemeMode.SYSTEM
        }
        viewModel.setThemeMode(nextMode)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedTablet = maxWidth >= 600.dp

        if (isExpandedTablet) {
            // Canonical Tablet / Foldable Layout with persistent NavigationRail
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .testTag("adaptive_navigation_rail")
                ) {
                    OpenFileDestination.entries.forEach { dest ->
                        val selected = currentDest == dest
                        NavigationRailItem(
                            selected = selected,
                            onClick = { viewModel.navigateToDestination(dest) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.title
                                )
                            },
                            label = { Text(dest.title) },
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    MainScaffoldContent(
                        isExpandedTablet = true,
                        currentDest = currentDest,
                        currentThemeMode = settings.themeMode,
                        activeTransferCount = activeTransferCount,
                        undoActionDescription = undoAction?.description,
                        snackbarHostState = snackbarHostState,
                        onOpenDrawer = {},
                        onNavigateUp = {
                            if (activeWebCloudProvider != null) {
                                viewModel.closeWebCloudPortal()
                            } else if (!viewModel.navigateUpInBrowser()) {
                                viewModel.navigateToDestination(OpenFileDestination.HOME)
                            }
                        },
                        onCycleThemeMode = onCycleThemeMode,
                        onRequestSaf = { safTreeLauncher.launch(null) },
                        onOpenTransferQueue = { showTransferQueueSheet = true },
                        onRefresh = { viewModel.refreshCurrentDirectory() },
                        onTriggerUndo = { undoAction?.let { viewModel.performUndo(it) } },
                        onDismissUndo = { viewModel.transferEngine.dismissUndoBanner() }
                    ) {
                        DestinationRouter(
                            currentDest = currentDest,
                            viewModel = viewModel,
                            volumes = volumes,
                            bookmarks = bookmarks,
                            recentFiles = recentFiles,
                            networkProfiles = networkProfiles,
                            cloudAccounts = cloudAccounts,
                            trashItems = trashItems,
                            ftpState = ftpState,
                            browserState = browserState,
                            settings = settings,
                            clipboard = clipboard,
                            analysisReport = analysisReport,
                            isAnalyzing = isAnalyzing,
                            analysisProgress = analysisProgress,
                            installedApps = installedApps,
                            includeSystemApps = includeSystemApps,
                            isLoadingApps = isLoadingApps,
                            discoveredHosts = discoveredHosts,
                            isScanningLan = isScanningLan,
                            sshHostKeyPrompt = sshHostKeyPrompt,
                            activePkceSession = activePkceSession,
                            categoryCounts = categoryCounts,
                            selectedNetworkTab = selectedNetworkTab,
                            onRequestSafAccess = { safTreeLauncher.launch(null) }
                        )
                    }
                }
            }
        } else {
            // Compact Phone Layout with ModalNavigationDrawer
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier
                            .width(300.dp)
                            .verticalScroll(rememberScrollState())
                            .testTag("navigation_drawer_sheet")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Text(
                                text = "OpenFile",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Capability-Aware File Manager",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()

                        DrawerSection.entries.forEach { section ->
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                            OpenFileDestination.entries
                                .filter { it.section == section }
                                .forEach { dest ->
                                    val selected = currentDest == dest
                                    NavigationDrawerItem(
                                        label = { Text(dest.title) },
                                        selected = selected,
                                        onClick = {
                                            coroutineScope.launch { drawerState.close() }
                                            viewModel.navigateToDestination(dest)
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.title
                                            )
                                        },
                                        modifier = Modifier
                                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                                            .testTag(dest.testTag)
                                    )
                                }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            ) {
                MainScaffoldContent(
                    isExpandedTablet = false,
                    currentDest = currentDest,
                    currentThemeMode = settings.themeMode,
                    activeTransferCount = activeTransferCount,
                    undoActionDescription = undoAction?.description,
                    snackbarHostState = snackbarHostState,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onNavigateUp = {
                        if (activeWebCloudProvider != null) {
                            viewModel.closeWebCloudPortal()
                        } else if (!viewModel.navigateUpInBrowser()) {
                            viewModel.navigateToDestination(OpenFileDestination.HOME)
                        }
                    },
                    onCycleThemeMode = onCycleThemeMode,
                    onRequestSaf = { safTreeLauncher.launch(null) },
                    onOpenTransferQueue = { showTransferQueueSheet = true },
                    onRefresh = { viewModel.refreshCurrentDirectory() },
                    onTriggerUndo = { undoAction?.let { viewModel.performUndo(it) } },
                    onDismissUndo = { viewModel.transferEngine.dismissUndoBanner() }
                ) {
                    DestinationRouter(
                        currentDest = currentDest,
                        viewModel = viewModel,
                        volumes = volumes,
                        bookmarks = bookmarks,
                        recentFiles = recentFiles,
                        networkProfiles = networkProfiles,
                        cloudAccounts = cloudAccounts,
                        trashItems = trashItems,
                        ftpState = ftpState,
                        browserState = browserState,
                        settings = settings,
                        clipboard = clipboard,
                        analysisReport = analysisReport,
                        isAnalyzing = isAnalyzing,
                        analysisProgress = analysisProgress,
                        installedApps = installedApps,
                        includeSystemApps = includeSystemApps,
                        isLoadingApps = isLoadingApps,
                        discoveredHosts = discoveredHosts,
                        isScanningLan = isScanningLan,
                        sshHostKeyPrompt = sshHostKeyPrompt,
                        activePkceSession = activePkceSession,
                        categoryCounts = categoryCounts,
                        selectedNetworkTab = selectedNetworkTab,
                        onRequestSafAccess = { safTreeLauncher.launch(null) }
                    )
                }
            }
        }
    }

    // Conflict Resolution Dialog
    if (conflictPrompt != null) {
        ConflictResolutionDialog(
            prompt = conflictPrompt!!,
            onResolve = { resolution, applyToAll ->
                conflictPrompt?.responseDeferred?.complete(resolution to applyToAll)
            }
        )
    }

    // File Preview Dialog
    if (previewFile != null) {
        FilePreviewDialog(
            file = previewFile!!,
            onDismiss = { viewModel.closePreviewDialog() },
            onOpenExternal = { viewModel.openWithExternalApp(it, forceChooser = true) }
        )
    }

    // File Properties & Checksums Sheet
    if (propertiesFile != null) {
        FilePropertiesModalSheet(
            file = propertiesFile!!,
            checksums = propertiesChecksums,
            apkInfo = propertiesApkInfo,
            onDismiss = { viewModel.closePropertiesSheet() },
            onInstallApk = { apkFile ->
                viewModel.appManager.installApkWithSystemInstaller(apkFile)
                    .onFailure { viewModel.showStatus("Install failed: ${it.message}") }
            },
            onOpenExternal = { viewModel.openWithExternalApp(it, forceChooser = true) }
        )
    }

    // Unified Transfer Queue Sheet
    if (showTransferQueueSheet) {
        TransferQueueBottomSheet(
            jobs = transferJobs,
            onPause = { viewModel.transferEngine.pauseJob(it) },
            onResume = { viewModel.transferEngine.resumeJob(it) },
            onCancel = { viewModel.transferEngine.cancelJob(it) },
            onClearFinished = {
                coroutineScope.launch { viewModel.dao.clearFinishedTransferJobs() }
            },
            onDismiss = { showTransferQueueSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffoldContent(
    isExpandedTablet: Boolean,
    currentDest: OpenFileDestination,
    currentThemeMode: ThemeMode,
    activeTransferCount: Int,
    undoActionDescription: String?,
    snackbarHostState: SnackbarHostState,
    onOpenDrawer: () -> Unit,
    onNavigateUp: () -> Unit,
    onCycleThemeMode: () -> Unit,
    onRequestSaf: () -> Unit,
    onOpenTransferQueue: () -> Unit,
    onRefresh: () -> Unit,
    onTriggerUndo: () -> Unit,
    onDismissUndo: () -> Unit,
    content: @Composable () -> Unit
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    Row {
                        if (!isExpandedTablet) {
                            IconButton(
                                onClick = onOpenDrawer,
                                modifier = Modifier.testTag("top_bar_drawer_button")
                            ) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                            }
                        }
                        if (currentDest != OpenFileDestination.HOME) {
                            IconButton(
                                onClick = onNavigateUp,
                                modifier = Modifier.testTag("top_bar_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
                            }
                        }
                    }
                },
                title = {
                    Text(
                        text = if (currentDest == OpenFileDestination.HOME) "OpenFile" else currentDest.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    // Quick Theme Mode Cycle Button (SYSTEM -> LIGHT -> DARK -> AMOLED)
                    IconButton(
                        onClick = onCycleThemeMode,
                        modifier = Modifier.testTag("top_bar_theme_button")
                    ) {
                        val themeIcon = when (currentThemeMode) {
                            ThemeMode.LIGHT -> Icons.Filled.LightMode
                            ThemeMode.DARK -> Icons.Filled.DarkMode
                            ThemeMode.AMOLED -> Icons.Filled.Contrast
                            ThemeMode.SYSTEM -> Icons.Filled.Contrast
                        }
                        Icon(
                            imageVector = themeIcon,
                            contentDescription = "Cycle Theme (${currentThemeMode.name})"
                        )
                    }
                    // Transfer Queue Button with Badge
                    IconButton(
                        onClick = onOpenTransferQueue,
                        modifier = Modifier.testTag("top_bar_transfer_queue_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (activeTransferCount > 0) {
                                    Badge { Text(activeTransferCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.SwapVert, contentDescription = "Transfer Queue")
                        }
                    }
                    // Refresh
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("top_bar_refresh_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh directory")
                    }
                    // Overflow Menu (Mount SAF Folder, etc.)
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("top_bar_overflow_button")
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Mount SAF Folder") },
                                onClick = {
                                    showOverflowMenu = false
                                    onRequestSaf()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.FolderOpen,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (undoActionDescription != null) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = undoActionDescription,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Row {
                            TextButton(onClick = onTriggerUndo) {
                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = MaterialTheme.colorScheme.inversePrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("UNDO", color = MaterialTheme.colorScheme.inversePrimary, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = onDismissUndo) {
                                Text("Dismiss", color = MaterialTheme.colorScheme.inverseOnSurface)
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            content()
        }
    }
}

@Composable
private fun DestinationRouter(
    currentDest: OpenFileDestination,
    viewModel: OpenFileViewModel,
    volumes: List<com.example.core.model.StorageVolumeInfo>,
    bookmarks: List<com.example.core.database.BookmarkEntity>,
    recentFiles: List<com.example.core.database.RecentFileEntity>,
    networkProfiles: List<com.example.core.database.NetworkProfileEntity>,
    cloudAccounts: List<com.example.core.database.CloudAccountEntity>,
    trashItems: List<com.example.core.database.TrashItemEntity>,
    ftpState: com.example.core.network.FtpServerRuntimeState,
    browserState: BrowserUiState,
    settings: com.example.core.datastore.AppSettingsState,
    clipboard: com.example.core.filesystem.ClipboardState,
    analysisReport: com.example.core.model.StorageAnalysisReport?,
    isAnalyzing: Boolean,
    analysisProgress: String,
    installedApps: List<com.example.core.model.InstalledAppInfo>,
    includeSystemApps: Boolean,
    isLoadingApps: Boolean,
    discoveredHosts: List<com.example.core.network.DiscoveredLanHost>,
    isScanningLan: Boolean,
    sshHostKeyPrompt: SshHostKeyPrompt?,
    activePkceSession: com.example.core.cloud.PkceSession?,
    categoryCounts: Map<com.example.core.model.FileCategory, Int>,
    selectedNetworkTab: Int,
    onRequestSafAccess: () -> Unit
) {
    when (currentDest) {
        OpenFileDestination.HOME -> {
            HomeScreen(
                volumes = volumes,
                bookmarks = bookmarks,
                recentFiles = recentFiles,
                ftpState = ftpState,
                categoryCounts = categoryCounts,
                cloudAccountsCount = cloudAccounts.size,
                remoteProfilesCount = networkProfiles.size,
                onVolumeClick = { vol ->
                    viewModel.openVolumeInBrowser(vol.rootReference)
                },
                onRequestSafAccess = onRequestSafAccess,
                onNavigateDestination = { viewModel.navigateToDestination(it) },
                onBookmarkClick = { bm -> viewModel.openBookmark(bm) },
                onRecentFileClick = { recent -> viewModel.openRecentFile(recent) },
                onOpenAccessFromPc = { viewModel.openNetworkTab(0) },
                onOpenRemoteConnections = { viewModel.openNetworkTab(1) }
            )
        }

        OpenFileDestination.INTERNAL_STORAGE,
        OpenFileDestination.DOWNLOADS,
        OpenFileDestination.IMAGES,
        OpenFileDestination.VIDEOS,
        OpenFileDestination.AUDIO,
        OpenFileDestination.DOCUMENTS,
        OpenFileDestination.ARCHIVES,
        OpenFileDestination.RECENT_FILES,
        OpenFileDestination.LARGE_FILES -> {
            FileBrowserScreen(
                browserState = browserState,
                settings = settings,
                bookmarks = bookmarks,
                clipboard = clipboard,
                onCrumbClick = { viewModel.openDirectory(it) },
                onFileClick = { viewModel.onFileItemClicked(it) },
                onFileLongClick = { viewModel.toggleSelection(it) },
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onToggleViewMode = { viewModel.setViewMode(it) },
                onChangeSort = { field, order -> viewModel.setSorting(field, order) },
                onCreateFolder = { viewModel.createNewFolder(it) },
                onCreateTextFile = { name, txt -> viewModel.createNewTextFile(name, txt) },
                onRenameItem = { item, newName -> viewModel.renameItem(item, newName) },
                onCopyItems = { items, mode ->
                    viewModel.transferEngine.copyToClipboard(items, mode)
                    viewModel.clearSelection()
                    viewModel.showStatus("${items.size} item(s) added to clipboard")
                },
                onPasteHere = { viewModel.pasteClipboardHere() },
                onClearClipboard = { viewModel.transferEngine.clearClipboard() },
                onDeleteItems = { items, useTrash -> viewModel.deleteItems(items, useTrash) },
                onToggleBookmark = { viewModel.toggleBookmark(it) },
                onShareItem = { viewModel.shareFileItem(it) },
                onOpenProperties = { viewModel.openPropertiesSheet(it) },
                onSelectAll = { viewModel.selectAllItems() },
                onClearSelection = { viewModel.clearSelection() },
                onCompressSelected = { name, fmt -> viewModel.createArchiveFromSelection(name, fmt) },
                onExtractArchive = { archiveRef, entries -> viewModel.extractArchiveToFolder(archiveRef, entries) },
                onCloseArchiveInspector = { viewModel.navigateUpInBrowser() },
                onDownloadRemoteItem = { viewModel.downloadRemoteItemToDownloads(it) },
                onOpenWithExternal = { viewModel.openWithExternalApp(it, forceChooser = true) },
                onQuickPreview = { viewModel.openBuiltInPreview(it) }
            )
        }

        OpenFileDestination.STORAGE_ANALYSIS -> {
            StorageAnalysisScreen(
                report = analysisReport,
                isScanning = isAnalyzing,
                progressText = analysisProgress,
                onRefreshScan = { customDir ->
                    viewModel.runStorageAnalysis(customDir ?: viewModel.localProvider.getPrimaryRootDirectory())
                },
                onCancelScan = { viewModel.cancelStorageAnalysis() },
                onOpenFileOrDirectory = { ref ->
                    if (ref.isDirectory) {
                        viewModel.navigateToDestination(OpenFileDestination.INTERNAL_STORAGE)
                        viewModel.openDirectory(ref)
                    } else {
                        viewModel.onFileItemClicked(ref)
                    }
                },
                onDeleteDuplicateFile = { dupRef ->
                    viewModel.deleteItems(listOf(dupRef), moveToTrash = true)
                    viewModel.runStorageAnalysis()
                }
            )
        }

        OpenFileDestination.APPLICATIONS -> {
            ApplicationsScreen(
                apps = installedApps,
                includeSystemApps = includeSystemApps,
                isLoading = isLoadingApps,
                onToggleSystemApps = { viewModel.loadApplications(it) },
                onLaunchApp = { pkg ->
                    viewModel.appManager.launchApp(pkg)
                        .onFailure { viewModel.showStatus(it.message ?: "Unable to launch app") }
                },
                onOpenAppInfo = { pkg ->
                    viewModel.appManager.openSystemAppInfo(pkg)
                },
                onShareApk = { apkFile ->
                    viewModel.appManager.shareApkOrFile(apkFile)
                        .onFailure { viewModel.showStatus("Cannot share APK: ${it.message}") }
                }
            )
        }

        OpenFileDestination.BOOKMARKS -> {
            BookmarksScreen(
                bookmarks = bookmarks,
                onOpenBookmark = { bm -> viewModel.openBookmark(bm) },
                onRenameBookmark = { bm, title -> viewModel.renameBookmark(bm, title) },
                onDeleteBookmark = { bm -> viewModel.deleteBookmark(bm) }
            )
        }

        OpenFileDestination.TRASH -> {
            val scope = rememberCoroutineScope()
            TrashScreen(
                trashItems = trashItems,
                onRestoreItem = { item ->
                    scope.launch {
                        viewModel.transferEngine.restoreTrashItemsById(listOf(item.id))
                            .onSuccess { viewModel.showStatus(it) }
                            .onFailure { viewModel.showStatus("Restore failed: ${it.message}") }
                    }
                },
                onDeletePermanently = { item ->
                    scope.launch {
                        viewModel.transferEngine.permanentlyDeleteTrashItem(item)
                        viewModel.showStatus("Permanently deleted '${item.displayName}'")
                    }
                },
                onEmptyTrash = {
                    scope.launch {
                        viewModel.transferEngine.emptyTrash()
                        viewModel.showStatus("Emptied Trash")
                    }
                }
            )
        }

        OpenFileDestination.NETWORK -> {
            val scope = rememberCoroutineScope()
            NetworkScreen(
                ftpState = ftpState,
                settings = settings,
                profiles = networkProfiles,
                discoveredHosts = discoveredHosts,
                isScanningLan = isScanningLan,
                sshHostKeyPrompt = sshHostKeyPrompt,
                onStartFtpServer = { pass -> viewModel.startLocalFtpServer(pass) },
                onStopFtpServer = { viewModel.stopLocalFtpServer() },
                onUpdateFtpConfig = { port, pStart, pEnd, user, anon, write, tls, timeout, root ->
                    scope.launch {
                        viewModel.settingsStore.updateFtpServerConfig(
                            port = port,
                            passiveStart = pStart,
                            passiveEnd = pEnd,
                            username = user,
                            secretAlias = settings.ftpSecretAlias,
                            anonymousAllowed = anon,
                            allowWrite = write,
                            useTls = tls,
                            idleTimeoutMinutes = timeout,
                            rootPath = root
                        )
                    }
                },
                onSaveProfile = { name, proto, host, port, user, secret, path, share, anon, pasv ->
                    viewModel.saveNetworkProfile(name, proto, host, port, user, secret, path, share, anon, pasv)
                },
                onDeleteProfile = { viewModel.deleteNetworkProfile(it) },
                onConnectProfile = { viewModel.openRemoteNetworkDirectory(it) },
                onScanLan = { viewModel.scanLanForServers() },
                onTrustSshHostKey = { viewModel.trustAndPinSshHostKey(it) },
                onDismissSshPrompt = { viewModel.dismissSshHostKeyPrompt() },
                initialTab = selectedNetworkTab
            )
        }

        OpenFileDestination.CLOUD_STORAGE -> {
            val activeWebProvider by viewModel.activeWebCloudProvider.collectAsStateWithLifecycle()
            val activeWebUrl by viewModel.activeWebCloudUrl.collectAsStateWithLifecycle()
            CloudStorageScreen(
                accounts = cloudAccounts,
                activePkceSession = activePkceSession,
                onStartOAuth = { provider, clientId -> viewModel.startCloudOAuthFlow(provider, clientId) },
                onCompleteOAuth = { email, code, direct -> viewModel.completeCloudOAuthWithCode(email, code, direct) },
                onOpenCloudAccount = { viewModel.openCloudDirectory(it) },
                onDisconnectAccount = { viewModel.disconnectCloudAccount(it) },
                onShowMessage = { viewModel.showStatus(it) },
                onGoogleDriveAuthorized = { token, email -> viewModel.onGoogleDriveAuthorized(token, email) },
                activeWebCloudProvider = activeWebProvider,
                activeWebCloudUrl = activeWebUrl,
                onConnectProviderDirect = { provider -> viewModel.connectCloudProviderDirect(provider) },
                onWebCloudAuthenticated = { provider, label -> viewModel.onWebCloudAuthenticated(provider, label) },
                onOAuthRedirectUri = { uri -> viewModel.handleOAuthRedirect(uri) },
                onDownloadCloudWebFile = { url, cookies, ua, cd, mime ->
                    viewModel.downloadFromCloudWebSession(url, cookies, ua, cd, mime)
                },
                onCloseWebCloudPortal = { viewModel.closeWebCloudPortal() }
            )
        }

        OpenFileDestination.SETTINGS -> {
            val vaultCount by produceState(
                initialValue = 0,
                networkProfiles,
                cloudAccounts,
                settings
            ) {
                value = withContext(Dispatchers.IO) {
                    viewModel.credentialVault.countStoredSecrets()
                }
            }
            SettingsScreen(
                settings = settings,
                storedVaultSecretsCount = vaultCount,
                onThemeModeChange = { viewModel.setThemeMode(it) },
                onDynamicColorChange = { viewModel.setDynamicColor(it) },
                onViewModeChange = { viewModel.setViewMode(it) },
                onShowThumbnailsChange = { viewModel.setShowThumbnails(it) },
                onShowHiddenChange = { viewModel.setShowHiddenFiles(it) },
                onConfirmDeleteChange = { viewModel.setConfirmBeforeDelete(it) },
                onMoveToTrashChange = { viewModel.setMoveToTrashByDefault(it) },
                onVerifyChecksumChange = { viewModel.setVerifyChecksumAfterTransfer(it) },
                onClearPrivacyData = { purgeVault -> viewModel.clearAllPrivacyHistoryAndCredentials(purgeVault) }
            )
        }
    }
}
