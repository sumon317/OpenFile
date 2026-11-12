package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import com.example.core.model.FileCategory
import com.example.core.model.ProviderType
import com.example.ui.components.ConflictResolutionDialog
import com.example.ui.components.FilePreviewDialog
import com.example.ui.components.FilePreviewScreen
import com.example.ui.components.FilePropertiesModalSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.components.OpenWithBottomSheet
import com.example.ui.components.TransferQueueBottomSheet
import com.example.ui.screens.ApplicationsScreen
import com.example.ui.screens.BookmarksScreen
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
    val transferJobs by viewModel.transferJobs.collectAsStateWithLifecycle()
    val conflictPrompt by viewModel.conflictPrompt.collectAsStateWithLifecycle()
    val undoAction by viewModel.latestUndoAction.collectAsStateWithLifecycle()
    val browserState by viewModel.browserState.collectAsStateWithLifecycle()
    val previewState by viewModel.previewState.collectAsStateWithLifecycle()
    val propertiesFile by viewModel.propertiesFile.collectAsStateWithLifecycle()
    val showAppUpdateDialog by viewModel.showAppUpdateDialog.collectAsStateWithLifecycle()
    val appUpdateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

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
        viewModel.refreshStorageVolumes()
        viewModel.refreshCategoryCountsAndPrewarm(force = true)
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
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(msg.second)
        viewModel.clearStatusMessage()
    }

    // Media Playback State - use ViewModel's exposed state flows (must be before BackHandler)
    val mediaIsPlaying by viewModel.mediaIsPlaying.collectAsStateWithLifecycle()
    val mediaCurrentFile by viewModel.mediaCurrentFile.collectAsStateWithLifecycle()
    
    // Connect the media playback controller to start observing service state
    LaunchedEffect(viewModel.mediaPlaybackController) {
        viewModel.mediaPlaybackController.connect()
    }

    // Hardware Back navigation for full-window file preview (highest priority)
    BackHandler(enabled = previewState != null) {
        viewModel.closePreviewDialog()
    }

    // Sync preview dialog if background service changes track while preview is open
    LaunchedEffect(mediaCurrentFile) {
        val currentMedia = mediaCurrentFile
        val preview = previewState
        if (currentMedia != null && preview != null && preview.siblingMedia.isNotEmpty()) {
            val isAudio = preview.currentFile.extension.lowercase() in com.example.core.model.FileCategory.AUDIO.extensions ||
                preview.currentFile.mimeType.startsWith("audio/")
            if (isAudio && preview.currentFile.uriOrPath != currentMedia.uriOrPath) {
                viewModel.previewMediaAt(currentMedia)
            }
        }
    }

    // Hardware Back navigation handling for browser stack & sub-destinations (only when no preview is open)
    BackHandler(
        enabled = previewState == null && (
            currentDest != OpenFileDestination.HOME ||
            browserState.isMultiSelectActive ||
            browserState.inspectArchiveFile != null
        )
    ) {
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
        var count = 0
        for (job in transferJobs) {
            if (job.status == "RUNNING" || job.status == "PAUSED" || job.status == "INTERRUPTED") count++
        }
        count
    }

    val activeRunningJob = remember(transferJobs) {
        transferJobs.firstOrNull { it.status == "RUNNING" }
    }

    val onCycleThemeMode: () -> Unit = {
        val currentMode = viewModel.settings.value.themeMode
        val nextMode = when (currentMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.AMOLED
            ThemeMode.AMOLED -> ThemeMode.SYSTEM
        }
        viewModel.setThemeMode(nextMode)
        val modeLabel = when (nextMode) {
            ThemeMode.SYSTEM -> "System Default"
            ThemeMode.LIGHT -> "Light Theme"
            ThemeMode.DARK -> "Dark Theme"
            ThemeMode.AMOLED -> "Pure Black (AMOLED)"
        }
        viewModel.showStatus("Theme: $modeLabel")
    }

    val isExpandedTablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    Box(modifier = Modifier.fillMaxSize()) {

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
                        customTitleOverride = when {
                            browserState.inspectArchiveFile != null ->
                                "Archive: ${browserState.inspectArchiveFile!!.displayName}"
                            currentDest == OpenFileDestination.INTERNAL_STORAGE && browserState.activeRemoteProfile != null ->
                                browserState.activeRemoteProfile!!.displayName
                            else -> null
                        },
                        currentThemeMode = settings.themeMode,
                        activeTransferCount = activeTransferCount,
                        activeRunningJob = activeRunningJob,
                        undoActionDescription = undoAction?.description,
                        snackbarHostState = snackbarHostState,
                        onOpenDrawer = {},
                        onNavigateUp = {
                            if (!viewModel.navigateUpInBrowser()) {
                                viewModel.navigateToDestination(OpenFileDestination.HOME)
                            }
                        },
                        onCycleThemeMode = onCycleThemeMode,
                        onOpenTransferQueue = { showTransferQueueSheet = true },
                        onRefresh = { viewModel.refreshCurrentDirectory() },
                        onTriggerUndo = { undoAction?.let { viewModel.performUndo(it) } },
                        onDismissUndo = { viewModel.transferEngine.dismissUndoBanner() },
                        mediaPlaybackController = viewModel.mediaPlaybackController,
                        onExpandMedia = {
                            val cur = viewModel.mediaCurrentFile.value
                            if (cur != null) {
                                viewModel.openMediaPreview(cur)
                            }
                        }
                    ) {
                        DestinationRouter(
                            currentDest = currentDest,
                            viewModel = viewModel,
                            browserState = browserState,
                            settings = settings,
                            onRequestSafAccess = { safTreeLauncher.launch(null) },
                            onRequestSafForPath = { path ->
                                safTreeLauncher.launch(viewModel.buildSafInitialUriForLocalPath(path))
                            }
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
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .testTag("navigation_drawer_sheet")
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.FolderOpen,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "OpenFile",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Capability-Aware File Manager",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

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
                    customTitleOverride = when {
                        browserState.inspectArchiveFile != null ->
                            "Archive: ${browserState.inspectArchiveFile!!.displayName}"
                        currentDest == OpenFileDestination.INTERNAL_STORAGE && browserState.activeRemoteProfile != null ->
                            browserState.activeRemoteProfile!!.displayName
                        else -> null
                    },
                    currentThemeMode = settings.themeMode,
                    activeTransferCount = activeTransferCount,
                    activeRunningJob = activeRunningJob,
                    undoActionDescription = undoAction?.description,
                    snackbarHostState = snackbarHostState,
                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                    onNavigateUp = {
                        if (!viewModel.navigateUpInBrowser()) {
                            viewModel.navigateToDestination(OpenFileDestination.HOME)
                        }
                    },
                    onCycleThemeMode = onCycleThemeMode,
                    onOpenTransferQueue = { showTransferQueueSheet = true },
                    onRefresh = { viewModel.refreshCurrentDirectory() },
                    onTriggerUndo = { undoAction?.let { viewModel.performUndo(it) } },
                    onDismissUndo = { viewModel.transferEngine.dismissUndoBanner() },
                    mediaPlaybackController = viewModel.mediaPlaybackController,
                    onExpandMedia = {
                        val cur = viewModel.mediaCurrentFile.value
                        if (cur != null) {
                            viewModel.openMediaPreview(cur)
                        }
                    }
                ) {
                    DestinationRouter(
                        currentDest = currentDest,
                        viewModel = viewModel,
                        browserState = browserState,
                        settings = settings,
                        onRequestSafAccess = { safTreeLauncher.launch(null) },
                        onRequestSafForPath = { path ->
                            safTreeLauncher.launch(viewModel.buildSafInitialUriForLocalPath(path))
                        }
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

    // Full-Window File Preview (File Manager + style)
    if (previewState != null) {
        FilePreviewScreen(
            state = previewState!!,
            onDismiss = { viewModel.closePreviewDialog() },
            onOpenExternal = { viewModel.openWithExternalApp(it, forceChooser = true) },
            onSaveTextContent = { item, newText -> viewModel.saveEditedTextFile(item, newText) },
            onLoadArchiveImage = { entry ->
                previewState?.archiveContext?.let { ctx ->
                    viewModel.getOrExtractArchiveEntry(ctx.archiveRef, entry)
                }
            },
            onPreviousMedia = { viewModel.previewPreviousMedia() },
            onNextMedia = { viewModel.previewNextMedia() },
            onSelectMedia = { viewModel.previewMediaAt(it) }
        )
    }

    // File Properties & Checksums Sheet
    if (propertiesFile != null) {
        val propertiesChecksums by viewModel.propertiesChecksums.collectAsStateWithLifecycle()
        val propertiesApkInfo by viewModel.propertiesApkInfo.collectAsStateWithLifecycle()
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

    // Modern Material 3 "Open With..." Bottom Sheet
    val openWithFile by viewModel.openWithFile.collectAsStateWithLifecycle()
    if (openWithFile != null) {
        OpenWithBottomSheet(
            file = openWithFile!!,
            currentAction = settings.fileOpenAction,
            onOpenBuiltIn = { item ->
                val archiveExts = FileCategory.ARCHIVES.extensions + setOf("zip", "jar", "7z", "tar", "gz", "xz", "tgz", "txz")
                if (item.extension.lowercase() in archiveExts) {
                    viewModel.inspectArchive(item)
                } else if (item.extension.lowercase() == "apk" && item.providerType == ProviderType.LOCAL) {
                    viewModel.appManager.installApkWithSystemInstaller(File(item.uriOrPath))
                        .onFailure { viewModel.openPropertiesSheet(item) }
                } else {
                    viewModel.openBuiltInPreview(item)
                }
            },
            onOpenExternal = { item ->
                viewModel.openWithExternalApp(item, forceChooser = true)
            },
            onSetDefaultAction = { action ->
                viewModel.setFileOpenAction(action)
            },
            onDismiss = { viewModel.dismissOpenWith() }
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

    // Native In-App GitHub Releases Auto-Update Modal Dialog
    if (showAppUpdateDialog) {
        val rel = appUpdateState.latestRelease
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                if (!appUpdateState.isDownloading) {
                    viewModel.dismissAppUpdateDialog()
                }
            },
            title = {
                Text(
                    text = if (rel != null) "Update Available: ${rel.tagName}" else "App Updates",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (rel != null) {
                        Text(
                            text = "${rel.releaseTitle} • Installed: v${appUpdateState.currentVersionName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Asset: ${rel.apkAssetName} (${com.example.core.model.formatByteSize(rel.apkSizeBytes)})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()
                        Text(
                            text = rel.changelog,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = appUpdateState.statusMessage ?: "You are running OpenFile v${appUpdateState.currentVersionName}.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (appUpdateState.isDownloading) {
                        Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { appUpdateState.downloadProgressFraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Downloading: ${(appUpdateState.downloadProgressFraction * 100).toInt()}% (${com.example.core.model.formatByteSize(appUpdateState.downloadedBytes)} / ${com.example.core.model.formatByteSize(appUpdateState.totalBytes)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (appUpdateState.errorMessage != null) {
                        Text(
                            text = appUpdateState.errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                if (rel != null && appUpdateState.updateAvailable) {
                    androidx.compose.material3.Button(
                        onClick = { viewModel.downloadAndInstallAppUpdate() },
                        enabled = !appUpdateState.isDownloading,
                        modifier = Modifier.testTag("update_dialog_install_button")
                    ) {
                        Text(
                            when {
                                appUpdateState.isDownloading -> "Downloading..."
                                appUpdateState.downloadedApkFile != null -> "Install Now"
                                else -> "Download & Install"
                            }
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissAppUpdateDialog() },
                    enabled = !appUpdateState.isDownloading
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffoldContent(
    isExpandedTablet: Boolean,
    currentDest: OpenFileDestination,
    customTitleOverride: String? = null,
    currentThemeMode: ThemeMode,
    activeTransferCount: Int,
    activeRunningJob: com.example.core.database.TransferJobEntity? = null,
    undoActionDescription: String?,
    snackbarHostState: SnackbarHostState,
    onOpenDrawer: () -> Unit,
    onNavigateUp: () -> Unit,
    onCycleThemeMode: () -> Unit,
    onOpenTransferQueue: () -> Unit,
    onRefresh: () -> Unit,
    onTriggerUndo: () -> Unit,
    onDismissUndo: () -> Unit,
    mediaPlaybackController: com.example.core.media.MediaPlaybackController,
    onExpandMedia: () -> Unit,
    content: @Composable () -> Unit
) {
    // Media Playback State - collect inside MainScaffoldContent for bottomBar access
    val mediaIsPlaying by mediaPlaybackController.isPlayingState.collectAsStateWithLifecycle()
    val mediaCurrentFile by mediaPlaybackController.currentFileState.collectAsStateWithLifecycle()
    
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                            text = customTitleOverride ?: if (currentDest == OpenFileDestination.HOME) "OpenFile" else currentDest.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        // Quick Theme Mode Cycle Button (SYSTEM -> LIGHT -> DARK -> AMOLED) with Material 3 Tonal Container
                        if (currentDest == OpenFileDestination.HOME) {
                            IconButton(
                                onClick = onCycleThemeMode,
                                modifier = Modifier.testTag("top_bar_theme_button")
                            ) {
                                val themeIcon = when (currentThemeMode) {
                                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                                    ThemeMode.DARK -> Icons.Filled.DarkMode
                                    ThemeMode.AMOLED -> Icons.Filled.Contrast
                                    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = themeIcon,
                                            contentDescription = "Theme: ${currentThemeMode.name}",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
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
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (activeRunningJob != null) {
                    val frac = if (activeRunningJob.totalBytes > 0L) {
                        (activeRunningJob.transferredBytes.toFloat() / activeRunningJob.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val percentText = if (activeRunningJob.totalBytes > 0L) " (${(frac * 100).toInt()}%)" else ""
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenTransferQueue)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.SwapVert,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = activeRunningJob.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${com.example.core.model.formatByteSize(activeRunningJob.transferredBytes)} / ${com.example.core.model.formatByteSize(activeRunningJob.totalBytes)}$percentText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            if (activeRunningJob.totalBytes <= 0L) {
                                androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { frac },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
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
                // Mini Player for media playback
                if (mediaIsPlaying || mediaCurrentFile != null) {
                    MiniPlayer(
                        controller = mediaPlaybackController,
                        onExpand = onExpandMedia,
                        onDismiss = {
                            mediaPlaybackController.stop()
                        }
                    )
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
    browserState: BrowserUiState,
    settings: com.example.core.datastore.AppSettingsState,
    onRequestSafAccess: () -> Unit,
    onRequestSafForPath: (String?) -> Unit
) {
    val hasAllFilesAccess by viewModel.hasAllFilesAccess.collectAsStateWithLifecycle()
    when (currentDest) {
        OpenFileDestination.HOME -> {
            val volumes by viewModel.storageVolumes.collectAsStateWithLifecycle()
            val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
            val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
            val networkProfiles by viewModel.networkProfiles.collectAsStateWithLifecycle()
            val ftpState by viewModel.ftpServerState.collectAsStateWithLifecycle()
            val categoryCounts by viewModel.categoryCounts.collectAsStateWithLifecycle()
            val appUpdateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
            val homeBrowserState by viewModel.browserState.collectAsStateWithLifecycle()
            HomeScreen(
                volumes = volumes,
                bookmarks = bookmarks,
                recentFiles = recentFiles,
                ftpState = ftpState,
                categoryCounts = categoryCounts,
                remoteProfilesCount = networkProfiles.size,
                remoteProfiles = networkProfiles,
                activeRemoteProfileId = homeBrowserState.activeRemoteProfile?.id,
                onRemoteProfileClick = {
                    viewModel.showStatus("Connecting to ${it.displayName}...")
                    viewModel.openRemoteNetworkDirectory(it)
                },
                hasAllFilesAccess = hasAllFilesAccess,
                onRequestAllFilesAccess = { viewModel.requestAllFilesAccessInSettings() },
                onVolumeClick = { vol ->
                    viewModel.openVolumeInBrowser(vol.rootReference)
                },
                onRequestSafAccess = onRequestSafAccess,
                onNavigateDestination = { viewModel.navigateToDestination(it) },
                onBookmarkClick = { bm -> viewModel.openBookmark(bm) },
                onRecentFileClick = { recent -> viewModel.openRecentFile(recent) },
                onOpenAccessFromPc = { viewModel.openNetworkTab(0) },
                onOpenRemoteConnections = { viewModel.openNetworkTab(1) },
                appUpdateState = appUpdateState,
                onOpenUpdateDialog = { viewModel.openAppUpdateDialog() },
                showHiddenFiles = settings.showHiddenFiles,
                onClearRecentFiles = { viewModel.clearRecentFiles() },
                onSaveScrollPosition = { key, idx, offset -> viewModel.saveScrollPosition(key, idx, offset) },
                onGetScrollPosition = { key -> viewModel.getScrollPosition(key) }
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
            val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
            val clipboard by viewModel.clipboard.collectAsStateWithLifecycle()
            FileBrowserScreen(
                browserState = browserState,
                settings = settings,
                bookmarks = bookmarks,
                clipboard = clipboard,
                hasAllFilesAccess = hasAllFilesAccess,
                onRequestAllFilesAccess = { viewModel.requestAllFilesAccessInSettings() },
                onToggleShowHidden = { viewModel.setShowHiddenFiles(!settings.showHiddenFiles) },
                onRequestSafForPath = onRequestSafForPath,
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
                onOpenWithExternal = { viewModel.requestOpenWith(it) },
                onQuickPreview = { viewModel.openBuiltInPreview(it) },
                onOpenParentFolder = { viewModel.openContainingFolder(it) },
                onOpenLocalRoot = { viewModel.navigateToDestination(OpenFileDestination.INTERNAL_STORAGE) },
                onPreviewArchiveEntry = { ref, entry -> viewModel.previewArchiveEntry(ref, entry) },
                onOpenArchiveSubFolder = { viewModel.openArchiveSubFolder(it) },
                onViewArchive = { viewModel.inspectArchive(it) },
                onClearRecentFiles = { viewModel.clearRecentFiles() },
                onSaveScrollPosition = { key, idx, offset -> viewModel.saveScrollPosition(key, idx, offset) },
                onGetScrollPosition = { key -> viewModel.getScrollPosition(key) }
            )
        }

        OpenFileDestination.STORAGE_ANALYSIS -> {
            val analysisReport by viewModel.analysisReport.collectAsStateWithLifecycle()
            val isAnalyzing by viewModel.isAnalyzingStorage.collectAsStateWithLifecycle()
            val analysisProgress by viewModel.analysisProgressText.collectAsStateWithLifecycle()
            val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
            val clipboard by viewModel.clipboard.collectAsStateWithLifecycle()
            val analysisVolumes by viewModel.storageVolumes.collectAsStateWithLifecycle()
            val analysisTarget by viewModel.analysisTargetRoot.collectAsStateWithLifecycle()
            StorageAnalysisScreen(
                report = analysisReport,
                volumes = analysisVolumes,
                activeRootPath = analysisTarget?.absolutePath,
                isScanning = isAnalyzing,
                progressText = analysisProgress,
                settings = settings,
                bookmarks = bookmarks,
                clipboard = clipboard,
                onRefreshScan = { customDir ->
                    // null = rescan whichever storage is currently selected
                    if (customDir != null) viewModel.runStorageAnalysis(customDir) else viewModel.runStorageAnalysis()
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
                    val isSecondary = dupRef.uriOrPath.startsWith("/storage/") && !dupRef.uriOrPath.startsWith("/storage/emulated")
                    viewModel.deleteItems(listOf(dupRef), moveToTrash = settings.moveToTrashByDefault && !isSecondary)
                },
                onRenameItem = { item, newName -> viewModel.renameItem(item, newName) },
                onCopyItems = { items, mode ->
                    viewModel.transferEngine.copyToClipboard(items, mode)
                    viewModel.showStatus("${items.size} item(s) added to clipboard — choose destination folder to paste")
                },
                onClearClipboard = { viewModel.transferEngine.clearClipboard() },
                onNavigateToStorageForPaste = {
                    viewModel.navigateToDestination(OpenFileDestination.INTERNAL_STORAGE)
                },
                onDeleteItems = { items, useTrash -> viewModel.deleteItems(items, useTrash) },
                onToggleBookmark = { viewModel.toggleBookmark(it) },
                onShareItem = { viewModel.shareFileItem(it) },
                onOpenProperties = { viewModel.openPropertiesSheet(it) },
                onOpenParentFolder = { viewModel.openContainingFolder(it) },
                onOpenWithExternal = { viewModel.requestOpenWith(it) },
                onQuickPreview = { viewModel.openBuiltInPreview(it) },
                onCompressItems = { items, name, fmt ->
                    viewModel.createArchiveFromItems(items, name, fmt)
                }
            )
        }

        OpenFileDestination.APPLICATIONS -> {
            val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
            val includeSystemApps by viewModel.includeSystemApps.collectAsStateWithLifecycle()
            val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
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
            val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
            BookmarksScreen(
                bookmarks = bookmarks,
                onOpenBookmark = { bm -> viewModel.openBookmark(bm) },
                onRenameBookmark = { bm, title -> viewModel.renameBookmark(bm, title) },
                onDeleteBookmark = { bm -> viewModel.deleteBookmark(bm) }
            )
        }

        OpenFileDestination.TRASH -> {
            val trashItems by viewModel.trashItems.collectAsStateWithLifecycle()
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
            val ftpState by viewModel.ftpServerState.collectAsStateWithLifecycle()
            val networkProfiles by viewModel.networkProfiles.collectAsStateWithLifecycle()
            val discoveredHosts by viewModel.discoveredLanHosts.collectAsStateWithLifecycle()
            val isScanningLan by viewModel.isScanningLan.collectAsStateWithLifecycle()
            val sshHostKeyPrompt by viewModel.sshHostKeyPrompt.collectAsStateWithLifecycle()
            val selectedNetworkTab by viewModel.selectedNetworkTab.collectAsStateWithLifecycle()
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
                    viewModel.saveFtpServerConfig(
                        port = port,
                        passiveStart = pStart,
                        passiveEnd = pEnd,
                        username = user,
                        anonymousAllowed = anon,
                        allowWrite = write,
                        useTls = tls,
                        idleTimeoutMinutes = timeout,
                        rootPath = root,
                        showConfirmation = false
                    )
                },
                onSaveFtpConfig = { port, pStart, pEnd, user, pass, anon, write, tls, timeout, root ->
                    viewModel.saveFtpServerConfig(
                        port = port,
                        passiveStart = pStart,
                        passiveEnd = pEnd,
                        username = user,
                        passwordPlain = pass,
                        anonymousAllowed = anon,
                        allowWrite = write,
                        useTls = tls,
                        idleTimeoutMinutes = timeout,
                        rootPath = root,
                        showConfirmation = true
                    )
                    if (ftpState.isRunning) {
                        viewModel.startLocalFtpServer(pass)
                    }
                },
                onSaveProfile = { name, proto, host, port, user, secret, path, share, anon, pasv ->
                    viewModel.saveNetworkProfile(name, proto, host, port, user, secret, path, share, anon, pasv)
                },
                onConnectDiscoveredHost = { name, proto, host, port, user, secret, path, share, anon, pasv ->
                    viewModel.saveNetworkProfile(
                        displayName = name,
                        protocol = proto,
                        host = host,
                        port = port,
                        username = user,
                        passwordOrPrivateKey = secret,
                        remoteInitialPath = path,
                        shareName = share,
                        isAnonymous = anon,
                        usePassiveMode = pasv,
                        connectImmediately = true
                    )
                },
                onDeleteProfile = { viewModel.deleteNetworkProfile(it) },
                onConnectProfile = { viewModel.openRemoteNetworkDirectory(it) },
                onScanLan = { viewModel.scanLanForServers() },
                onTrustSshHostKey = { viewModel.trustAndPinSshHostKey(it) },
                onDismissSshPrompt = { viewModel.dismissSshHostKeyPrompt() },
                initialTab = selectedNetworkTab,
                savedFtpPassword = produceState(initialValue = "openfile123", key1 = settings.ftpSecretAlias) {
                    value = withContext(Dispatchers.IO) { viewModel.getSavedFtpPassword() }
                }.value
            )
        }

        OpenFileDestination.SETTINGS -> {
            val networkProfiles by viewModel.networkProfiles.collectAsStateWithLifecycle()
            val appUpdateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
            val vaultCount by produceState(
                initialValue = 0,
                networkProfiles,
                settings
            ) {
                value = withContext(Dispatchers.IO) {
                    viewModel.credentialVault.countStoredSecrets()
                }
            }
            SettingsScreen(
                settings = settings,
                storedVaultSecretsCount = vaultCount,
                onThemeModeChange = { mode ->
                    viewModel.setThemeMode(mode)
                    val modeLabel = when (mode) {
                        ThemeMode.SYSTEM -> "System Default"
                        ThemeMode.LIGHT -> "Light Theme"
                        ThemeMode.DARK -> "Dark Theme"
                        ThemeMode.AMOLED -> "Pure Black (AMOLED)"
                    }
                    viewModel.showStatus("Theme: $modeLabel")
                },
                onDynamicColorChange = { viewModel.setDynamicColor(it) },
                onViewModeChange = { viewModel.setViewMode(it) },
                onShowThumbnailsChange = { viewModel.setShowThumbnails(it) },
                onShowHiddenChange = { viewModel.setShowHiddenFiles(it) },
                onConfirmDeleteChange = { viewModel.setConfirmBeforeDelete(it) },
                onMoveToTrashChange = { viewModel.setMoveToTrashByDefault(it) },
                onVerifyChecksumChange = { viewModel.setVerifyChecksumAfterTransfer(it) },
                onClearPrivacyData = { purgeVault -> viewModel.clearAllPrivacyHistoryAndCredentials(purgeVault) },
                onMountSafFolder = onRequestSafAccess,
                appUpdateState = appUpdateState,
                onAutoCheckUpdatesChange = { viewModel.setAutoCheckUpdates(it) },
                onCheckForUpdatesNow = { viewModel.checkForAppUpdates(manualUserTrigger = true) },
                onDownloadAndInstallUpdate = { viewModel.downloadAndInstallAppUpdate() },
                onOpenReleasesInBrowser = { viewModel.appUpdateEngine.openReleasesPageInBrowser() },
                onFileOpenActionChange = { viewModel.setFileOpenAction(it) }
            )
        }
    }
}
