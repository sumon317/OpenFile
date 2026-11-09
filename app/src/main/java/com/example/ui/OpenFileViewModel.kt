package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.apps.AppManagerEngine
import com.example.core.archive.ArchiveCreationFormat
import com.example.core.archive.ArchiveEngine
import com.example.core.archive.ArchiveEntryItem
import com.example.core.database.BookmarkEntity
import com.example.core.database.NetworkProfileEntity
import com.example.core.database.OpenFileDatabase
import com.example.core.database.RecentFileEntity
import com.example.core.database.SafGrantEntity
import com.example.core.database.SearchQueryEntity
import com.example.core.database.TransferJobEntity
import com.example.core.database.TrashItemEntity
import com.example.core.datastore.AppSettingsState
import com.example.core.datastore.SettingsDataStore
import com.example.core.filesystem.ChecksumAndDuplicateEngine
import com.example.core.filesystem.ClipboardMode
import com.example.core.filesystem.ClipboardState
import com.example.core.filesystem.ConflictPromptRequest
import com.example.core.filesystem.FileChecksums
import com.example.core.filesystem.FileSortAndFilterEngine
import com.example.core.filesystem.NaturalOrderComparator
import com.example.core.filesystem.LocalFileSystemProvider
import com.example.core.filesystem.MediaStoreProvider
import com.example.core.filesystem.SafDocumentProvider
import com.example.core.filesystem.TransferQueueEngine
import com.example.core.filesystem.UndoActionRecord
import com.example.core.model.ApkArchiveInfo
import com.example.core.model.ConflictResolution
import com.example.core.model.FileCategory
import com.example.core.model.FileReference
import com.example.core.model.InstalledAppInfo
import com.example.core.model.ProviderType
import com.example.core.model.SortField
import com.example.core.model.SortOrder
import com.example.core.model.StorageAnalysisReport
import com.example.core.model.StorageVolumeInfo
import com.example.core.model.ViewMode
import com.example.core.network.DiscoveredLanHost
import com.example.core.network.FtpServerEngine
import com.example.core.network.FtpServerForegroundService
import com.example.core.network.FtpServerRuntimeState
import com.example.core.network.RemoteNetworkClients
import com.example.core.network.SshHostKeyVerificationRequiredException
import com.example.core.network.isAuthenticationFailureMessage
import com.example.core.security.KeystoreCredentialVault
import com.example.core.storage.StorageAnalyzerEngine
import com.example.core.update.AppUpdateEngine
import com.example.core.update.AppUpdateUiState
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BrowserUiState(
    val currentDirectory: FileReference? = null,
    val breadcrumbs: List<FileReference> = emptyList(),
    val items: List<FileReference> = emptyList(),
    val selectedUris: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val activeCategoryFilter: FileCategory? = null,
    val inspectArchiveFile: FileReference? = null,
    val archiveEntries: List<ArchiveEntryItem> = emptyList(),
    val archiveSubPath: String = "",
    val activeRemoteProfile: NetworkProfileEntity? = null,
    val currentSortField: SortField = SortField.NAME,
    val currentSortOrder: SortOrder = SortOrder.ASCENDING
) {
    val isMultiSelectActive: Boolean get() = selectedUris.isNotEmpty()
}

data class SshHostKeyPrompt(
    val profile: NetworkProfileEntity,
    val keyType: String,
    val sha256Fingerprint: String,
    val isMismatchWarning: Boolean
)

data class ArchiveImagePreviewContext(
    val archiveRef: FileReference,
    val imageEntries: List<ArchiveEntryItem>,
    val initialIndex: Int
)

data class FilePreviewState(
    val currentFile: FileReference,
    val siblingImages: List<FileReference> = emptyList(),
    val initialImageIndex: Int = 0,
    val archiveContext: ArchiveImagePreviewContext? = null
)

class OpenFileViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    val database = OpenFileDatabase.getInstance(context)
    val dao = database.dao()
    val settingsStore = SettingsDataStore(context)
    val credentialVault = KeystoreCredentialVault(context)

    val localProvider = LocalFileSystemProvider(context)
    val safProvider = SafDocumentProvider(context)
    val mediaStoreProvider = MediaStoreProvider(context, localProvider)
    val transferEngine = TransferQueueEngine(context, dao, localProvider, safProvider, viewModelScope, credentialVault)
    val storageAnalyzer = StorageAnalyzerEngine(localProvider)
    val appManager = AppManagerEngine(context, localProvider)
    val appUpdateEngine = AppUpdateEngine(context)

    // In-App GitHub Releases Auto-Update State
    private val _appUpdateState = MutableStateFlow(
        run {
            val (vName, vCode) = appUpdateEngine.getCurrentVersionInfo()
            AppUpdateUiState(currentVersionName = vName, currentVersionCode = vCode)
        }
    )
    val appUpdateState: StateFlow<AppUpdateUiState> = _appUpdateState.asStateFlow()

    private val _showAppUpdateDialog = MutableStateFlow(false)
    val showAppUpdateDialog: StateFlow<Boolean> = _showAppUpdateDialog.asStateFlow()
    private var updateDownloadJob: Job? = null

    // Navigation state
    private val _currentDestination = MutableStateFlow(OpenFileDestination.HOME)
    val currentDestination: StateFlow<OpenFileDestination> = _currentDestination.asStateFlow()

    // Global feedback message (Toast / Snackbar)
    private val _statusMessage = MutableStateFlow<Pair<Long, String>?>(null)
    val statusMessage: StateFlow<Pair<Long, String>?> = _statusMessage.asStateFlow()

    // Reactive streams from Room & DataStore (pre-warmed with cached theme to eliminate startup flash)
    private val _settings = MutableStateFlow(
        AppSettingsState(
            themeMode = settingsStore.getInitialThemeMode(),
            useDynamicColor = settingsStore.getInitialDynamicColor()
        )
    )
    val settings: StateFlow<AppSettingsState> = _settings.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkEntity>> = dao.observeBookmarks().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val recentFiles: StateFlow<List<RecentFileEntity>> = dao.observeRecentFiles().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val networkProfiles: StateFlow<List<NetworkProfileEntity>> = dao.observeNetworkProfiles().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val trashItems: StateFlow<List<TrashItemEntity>> = dao.observeTrashItems().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val transferJobs: StateFlow<List<TransferJobEntity>> = dao.observeTransferJobs().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val searchHistory: StateFlow<List<SearchQueryEntity>> = dao.observeSearchHistory().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val safGrants: StateFlow<List<SafGrantEntity>> = dao.observeSafGrants().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _hasAllFilesAccess = MutableStateFlow(localProvider.hasAllFilesAccess())
    val hasAllFilesAccess: StateFlow<Boolean> = _hasAllFilesAccess.asStateFlow()

    private val _volumeRefreshTrigger = MutableStateFlow(0L)
    fun refreshStorageVolumes(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val prevAccess = _hasAllFilesAccess.value
        val currentAccess = localProvider.hasAllFilesAccess()
        val accessChanged = prevAccess != currentAccess
        if (!force && !accessChanged && now - _volumeRefreshTrigger.value < 5_000L) {
            return
        }
        _hasAllFilesAccess.value = currentAccess
        if (accessChanged || force) {
            localProvider.invalidateRootCache()
        }
        _volumeRefreshTrigger.value = now

        val currentDirPath = _browserState.value.currentDirectory?.uriOrPath
        val wasInFallbackSandbox = currentDirPath != null && currentDirPath.contains("/files/InternalStorage")
        if ((!prevAccess && currentAccess) || (currentAccess && wasInFallbackSandbox)) {
            refreshCategoryCountsAndPrewarm(force = true)
            if (wasInFallbackSandbox || _currentDestination.value == OpenFileDestination.INTERNAL_STORAGE) {
                openPrimaryStorageRoot()
            } else {
                refreshCurrentDirectory()
            }
        }
    }

    fun requestAllFilesAccessInSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pkgUri = Uri.parse("package:${context.packageName}")
            val specificIntent = Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                pkgUri
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val fallbackIntent = Intent(
                android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(specificIntent)
            }.recoverCatching {
                context.startActivity(fallbackIntent)
            }.onFailure { err ->
                showStatus("Unable to open All Files Access settings: ${err.message}")
            }
        }
    }

    val storageVolumes: StateFlow<List<StorageVolumeInfo>> = combine(safGrants, _volumeRefreshTrigger) { grants, _ ->
        withContext(Dispatchers.IO) {
            localProvider.getStorageVolumes(
                safProvider = safProvider,
                persistedGrantUris = grants.map { it.treeUriString to it.displayName }
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Clipboard, Conflict Prompt, Undo Action, and FTP Server runtime state
    val clipboard: StateFlow<ClipboardState> = transferEngine.clipboard
    val conflictPrompt: StateFlow<ConflictPromptRequest?> = transferEngine.conflictPrompt
    val latestUndoAction: StateFlow<UndoActionRecord?> = transferEngine.latestUndoAction
    val ftpServerState: StateFlow<FtpServerRuntimeState> = FtpServerEngine.state

    // Browser UI state
    private val _browserState = MutableStateFlow(BrowserUiState())
    val browserState: StateFlow<BrowserUiState> = _browserState.asStateFlow()
    private var browseDirectoryJob: Job? = null
    private var propertiesJob: Job? = null

    private data class PropertyComputeResult(
        val detailedRef: FileReference,
        val dirSize: Long,
        val apk: ApkArchiveInfo?,
        val hashes: FileChecksums?
    )
    private var searchDebounceJob: Job? = null

    // Preview & Properties modals / windows
    private val _previewState = MutableStateFlow<FilePreviewState?>(null)
    val previewState: StateFlow<FilePreviewState?> = _previewState.asStateFlow()

    private val _previewFile = MutableStateFlow<FileReference?>(null)
    val previewFile: StateFlow<FileReference?> = _previewFile.asStateFlow()

    private val _extractedArchiveImageCache = java.util.concurrent.ConcurrentHashMap<String, File>()

    suspend fun getOrExtractArchiveEntry(
        archiveRef: FileReference,
        entry: ArchiveEntryItem
    ): File? = withContext(Dispatchers.IO) {
        val key = "${archiveRef.uriOrPath}::${entry.entryPath}"
        val existing = _extractedArchiveImageCache[key]
        if (existing != null && existing.exists() && existing.length() > 0L) {
            return@withContext existing
        }
        val archiveFile = File(archiveRef.uriOrPath)
        if (!archiveFile.exists()) return@withContext null

        ArchiveEngine.extractSingleEntryToCache(
            archiveFile = archiveFile,
            entryPath = entry.entryPath,
            cacheDir = context.cacheDir
        ).getOrNull()?.also {
            _extractedArchiveImageCache[key] = it
        }
    }

    private val _propertiesFile = MutableStateFlow<FileReference?>(null)
    val propertiesFile: StateFlow<FileReference?> = _propertiesFile.asStateFlow()

    private val _propertiesChecksums = MutableStateFlow<FileChecksums?>(null)
    val propertiesChecksums: StateFlow<FileChecksums?> = _propertiesChecksums.asStateFlow()

    private val _propertiesApkInfo = MutableStateFlow<ApkArchiveInfo?>(null)
    val propertiesApkInfo: StateFlow<ApkArchiveInfo?> = _propertiesApkInfo.asStateFlow()

    // Storage Analyzer state
    private val _analysisReport = MutableStateFlow<StorageAnalysisReport?>(null)
    /** The storage root (internal, SD card, USB OTG...) the analyzer is currently targeting. */
    private val _analysisTargetRoot = MutableStateFlow<File?>(null)
    val analysisTargetRoot: StateFlow<File?> = _analysisTargetRoot.asStateFlow()
    val analysisReport: StateFlow<StorageAnalysisReport?> = _analysisReport.asStateFlow()

    private val _isAnalyzingStorage = MutableStateFlow(false)
    val isAnalyzingStorage: StateFlow<Boolean> = _isAnalyzingStorage.asStateFlow()

    private val _analysisProgressText = MutableStateFlow("")
    val analysisProgressText: StateFlow<String> = _analysisProgressText.asStateFlow()
    private var analysisJob: Job? = null

    // Installed Applications state
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _includeSystemApps = MutableStateFlow(false)
    val includeSystemApps: StateFlow<Boolean> = _includeSystemApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    // Network LAN Discovery & SSH Host Key Verification prompt
    private val _discoveredLanHosts = MutableStateFlow<List<DiscoveredLanHost>>(emptyList())
    val discoveredLanHosts: StateFlow<List<DiscoveredLanHost>> = _discoveredLanHosts.asStateFlow()

    private val _isScanningLan = MutableStateFlow(false)
    val isScanningLan: StateFlow<Boolean> = _isScanningLan.asStateFlow()

    private val _sshHostKeyPrompt = MutableStateFlow<SshHostKeyPrompt?>(null)
    val sshHostKeyPrompt: StateFlow<SshHostKeyPrompt?> = _sshHostKeyPrompt.asStateFlow()

    // File Manager + Home Category Counts & Instant Pre-warmed Category Cache
    private val _categoryCounts = MutableStateFlow<Map<FileCategory, Int>>(emptyMap())
    val categoryCounts: StateFlow<Map<FileCategory, Int>> = _categoryCounts.asStateFlow()
    private val categoryCache = java.util.concurrent.ConcurrentHashMap<FileCategory, List<FileReference>>()
    private val categoryCacheAt = java.util.concurrent.ConcurrentHashMap<FileCategory, Long>()

    // Active tab inside NetworkScreen (0 = Access from PC / Wi-Fi FTP Server, 1 = Remote Connections)
    private val _selectedNetworkTab = MutableStateFlow(0)
    val selectedNetworkTab: StateFlow<Int> = _selectedNetworkTab.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { persisted ->
                _settings.value = persisted
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            // Crash recovery: mark any stale RUNNING transfer jobs from a previous process death as INTERRUPTED
            dao.markRunningJobsAsInterrupted()
        }
        // Defer MediaStore category prewarm so it does not compete with the first frame
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(1200L)
            refreshCategoryCountsAndPrewarm()
        }
        // Clean up any old downloaded update APKs from previous updates so app cache & storage stays minimal
        viewModelScope.launch(Dispatchers.IO) {
            appUpdateEngine.cleanDownloadedUpdateApks()
        }
        // Automatically check GitHub Releases for app updates in the background on real devices
        if (!Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            viewModelScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(2500L)
                if (_settings.value.autoCheckUpdates) {
                    checkForAppUpdates(manualUserTrigger = false)
                }
            }
        }
    }

    @Volatile
    private var lastCategoryPrewarmEpochMs: Long = 0L

    fun refreshCategoryCountsAndPrewarm(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCategoryPrewarmEpochMs < 45_000L && _categoryCounts.value.isNotEmpty()) {
            return
        }
        lastCategoryPrewarmEpochMs = now
        viewModelScope.launch(Dispatchers.IO) {
            val showHidden = _settings.value.showHiddenFiles
            val thresholdBytes = _settings.value.largeFileThresholdMb * 1024L * 1024L
            _categoryCounts.value = mediaStoreProvider.queryCategoryCounts(thresholdBytes)
            // Pre-warm common categories in the background with cooperative yields so UI scrolling stays silky smooth
            listOf(
                FileCategory.IMAGES,
                FileCategory.VIDEOS,
                FileCategory.AUDIO,
                FileCategory.DOCUMENTS,
                FileCategory.ARCHIVES,
                FileCategory.APPLICATIONS,
                FileCategory.LARGE_FILES,
                FileCategory.RECENT_FILES
            ).forEach { cat ->
                kotlinx.coroutines.yield()
                runCatching {
                    val items = mediaStoreProvider.queryByCategory(cat, thresholdBytes)
                    categoryCache[cat] = items
                    categoryCacheAt[cat] = System.currentTimeMillis()
                    if (cat != FileCategory.APPLICATIONS) {
                        val count = if (showHidden) items.size else items.count { !it.isHidden }
                        _categoryCounts.value = _categoryCounts.value + (cat to count)
                    }
                }
            }
        }
    }

    fun openNetworkTab(tabIndex: Int) {
        _selectedNetworkTab.value = tabIndex
        _currentDestination.value = OpenFileDestination.NETWORK
    }

    fun showStatus(message: String) {
        _statusMessage.value = System.nanoTime() to message
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // ---------------------------------------------------------------------------------------------
    // Navigation & Directory Browsing
    // ---------------------------------------------------------------------------------------------

    fun navigateToDestination(destination: OpenFileDestination) {
        _currentDestination.value = destination
        when (destination) {
            OpenFileDestination.HOME -> refreshCategoryCountsAndPrewarm()
            OpenFileDestination.INTERNAL_STORAGE -> openPrimaryStorageRoot()
            OpenFileDestination.DOWNLOADS -> openCategoryOrSpecialFolder(FileCategory.DOWNLOADS)
            OpenFileDestination.IMAGES -> openCategoryOrSpecialFolder(FileCategory.IMAGES)
            OpenFileDestination.VIDEOS -> openCategoryOrSpecialFolder(FileCategory.VIDEOS)
            OpenFileDestination.AUDIO -> openCategoryOrSpecialFolder(FileCategory.AUDIO)
            OpenFileDestination.DOCUMENTS -> openCategoryOrSpecialFolder(FileCategory.DOCUMENTS)
            OpenFileDestination.ARCHIVES -> openCategoryOrSpecialFolder(FileCategory.ARCHIVES)
            OpenFileDestination.LARGE_FILES -> openCategoryOrSpecialFolder(FileCategory.LARGE_FILES)
            OpenFileDestination.RECENT_FILES -> openCategoryOrSpecialFolder(FileCategory.RECENT_FILES)
            OpenFileDestination.APPLICATIONS -> loadApplications(_includeSystemApps.value)
            OpenFileDestination.STORAGE_ANALYSIS -> {
                refreshStorageVolumes(force = true)
                if (_analysisReport.value == null && !_isAnalyzingStorage.value) {
                    runStorageAnalysis()
                }
            }
            else -> {}
        }
    }

    fun openVolumeInBrowser(rootReference: FileReference) {
        _currentDestination.value = OpenFileDestination.INTERNAL_STORAGE
        openDirectory(rootReference)
    }

    fun openPrimaryStorageRoot() {
        viewModelScope.launch {
            val ref = withContext(Dispatchers.IO) {
                val rootDir = localProvider.getPrimaryRootDirectory()
                localProvider.toFileReference(rootDir)
            }
            openDirectory(ref)
        }
    }

    fun openDirectory(directory: FileReference) {
        if (directory.providerType in setOf(ProviderType.FTP, ProviderType.FTPS, ProviderType.SFTP, ProviderType.SMB)) {
            val activeProf = _browserState.value.activeRemoteProfile
            if (activeProf != null) {
                openRemoteNetworkDirectory(activeProf, directory.uriOrPath)
                return
            }
        }

        browseDirectoryJob?.cancel()
        val isRemoteOrEmpty = directory.providerType.isRemote || _browserState.value.items.isEmpty()
        val currentSettings = settings.value
        _browserState.value = _browserState.value.copy(
            currentDirectory = directory,
            selectedUris = emptySet(),
            searchQuery = "",
            isLoading = isRemoteOrEmpty,
            errorMessage = null,
            activeCategoryFilter = null,
            inspectArchiveFile = null,
            archiveEntries = emptyList(),
            activeRemoteProfile = null,
            currentSortField = currentSettings.defaultSortField,
            currentSortOrder = currentSettings.defaultSortOrder
        )
        browseDirectoryJob = viewModelScope.launch {
            // Run breadcrumbs + listing + sort + childCount all on IO/Default — one single pass
            val (crumbs, sortedItems) = withContext(Dispatchers.IO) {
                val crumbs = buildBreadcrumbs(directory)
                val primaryRootPath = localProvider.getPrimaryRootDirectory().absolutePath
                val isRestrictedLocal = directory.providerType == ProviderType.LOCAL &&
                    localProvider.isRestrictedAndroidDirectory(File(directory.uriOrPath))
                val grantedSafRef = if (isRestrictedLocal) {
                    safProvider.findGrantedSafReferenceForLocalPath(directory.uriOrPath, primaryRootPath)
                } else null

                val listResult = when {
                    directory.providerType == ProviderType.SAF ->
                        safProvider.listChildren(directory, currentSettings.showHiddenFiles)
                    grantedSafRef != null ->
                        safProvider.listChildren(grantedSafRef, currentSettings.showHiddenFiles)
                    else -> {
                        val rawRes = localProvider.listChildren(directory, currentSettings.showHiddenFiles)
                        if (isRestrictedLocal && localProvider.isTopRestrictedContainer(File(directory.uriOrPath))) {
                            val childSafGrants = safProvider.findChildSafGrantsForParentLocalPath(directory.uriOrPath, primaryRootPath)
                            rawRes.map { items ->
                                items.map { item ->
                                    if (item.displayName in childSafGrants || "*" in childSafGrants) {
                                        item.copy(
                                            metadata = (item.metadata - "restrictedAppFolder") + ("safMounted" to "true")
                                        )
                                    } else item
                                }
                            }
                        } else {
                            rawRes
                        }
                    }
                }
                val sorted = listResult.getOrNull()?.let { rawItems ->
                    val sortedList = FileSortAndFilterEngine.sortFiles(
                        files = rawItems,
                        field = currentSettings.defaultSortField,
                        order = currentSettings.defaultSortOrder
                    )
                    val folderCount = sortedList.count { it.isDirectory }
                    if (directory.providerType == ProviderType.LOCAL && grantedSafRef == null && folderCount in 1..40) {
                        sortedList.map { item ->
                            if (item.isDirectory && item.metadata["childCount"] == null && item.metadata["restrictedAppFolder"] == null) {
                                val count = runCatching {
                                    val list = File(item.uriOrPath).list()
                                    if (currentSettings.showHiddenFiles) list?.size else list?.count { !it.startsWith(".") }
                                }.getOrNull()
                                if (count != null) {
                                    item.copy(metadata = item.metadata + ("childCount" to count.toString()))
                                } else item
                            } else item
                        }
                    } else {
                        sortedList
                    }
                }
                Pair(crumbs, sorted to listResult.exceptionOrNull())
            }
            val (itemsForInitialRender, listError) = sortedItems

            if (listError != null || itemsForInitialRender == null) {
                _browserState.value = _browserState.value.copy(
                    currentDirectory = directory,
                    breadcrumbs = crumbs,
                    isLoading = false,
                    errorMessage = listError?.message ?: "Unable to read directory"
                )
                return@launch
            }

            _browserState.value = _browserState.value.copy(
                currentDirectory = directory,
                breadcrumbs = crumbs,
                items = itemsForInitialRender,
                isLoading = false,
                errorMessage = null
            )

            // For huge directories (>40 folders), enrich child counts in the background
            val folderCount = itemsForInitialRender.count { it.isDirectory }
            if (directory.providerType == ProviderType.LOCAL && folderCount > 40) {
                val enriched = withContext(Dispatchers.IO) {
                    itemsForInitialRender.map { item ->
                        if (item.isDirectory && item.metadata["childCount"] == null) {
                            val count = runCatching {
                                val list = File(item.uriOrPath).list()
                                if (currentSettings.showHiddenFiles) list?.size else list?.count { !it.startsWith(".") }
                            }.getOrNull()
                            if (count != null) {
                                item.copy(metadata = item.metadata + ("childCount" to count.toString()))
                            } else {
                                item
                            }
                        } else {
                            item
                        }
                    }
                }
                if (_browserState.value.currentDirectory?.uriOrPath == directory.uriOrPath) {
                    _browserState.value = _browserState.value.copy(items = enriched)
                }
            }
        }
    }

    fun refreshCurrentDirectory() {
        val state = _browserState.value
        when {
            state.activeRemoteProfile != null -> {
                val path = state.currentDirectory?.uriOrPath ?: state.activeRemoteProfile.remoteInitialPath
                openRemoteNetworkDirectory(state.activeRemoteProfile, path)
            }
            state.activeCategoryFilter != null -> {
                openCategoryOrSpecialFolder(
                    state.activeCategoryFilter,
                    state.currentSortField,
                    state.currentSortOrder
                )
            }
            state.currentDirectory != null -> {
                openDirectory(state.currentDirectory)
            }
        }
    }

    fun navigateUpInBrowser(): Boolean {
        val state = _browserState.value
        if (state.isMultiSelectActive) {
            clearSelection()
            return true
        }
        if (state.inspectArchiveFile != null) {
            if (state.archiveSubPath.isNotEmpty()) {
                val parentSub = if (state.archiveSubPath.contains('/')) {
                    state.archiveSubPath.substringBeforeLast('/')
                } else {
                    ""
                }
                _browserState.value = state.copy(archiveSubPath = parentSub)
                return true
            }
            _browserState.value = state.copy(inspectArchiveFile = null, archiveEntries = emptyList(), archiveSubPath = "")
            return true
        }
        val current = state.currentDirectory ?: return false
        val parentPath = current.parentUriOrPath

        if (state.activeRemoteProfile != null) {
            if (current.uriOrPath == "/" || current.uriOrPath == state.activeRemoteProfile.remoteInitialPath || parentPath == null) return false
            openRemoteNetworkDirectory(state.activeRemoteProfile, parentPath)
            return true
        }

        if (parentPath == null) return false

        if (current.providerType == ProviderType.LOCAL) {
            val parentCrumbs = state.breadcrumbs.dropLast(1)
            if (parentCrumbs.isNotEmpty()) {
                openDirectory(parentCrumbs.last())
                return true
            }
            val primaryRoot = localProvider.getPrimaryRootDirectory().absolutePath
            val workspaceRoot = localProvider.getAppWorkspaceDirectory().absolutePath
            if (current.uriOrPath == primaryRoot || current.uriOrPath == workspaceRoot) {
                return false
            }
            viewModelScope.launch(Dispatchers.IO) {
                val parentFile = File(parentPath)
                if (parentFile.exists() && parentFile.canRead()) {
                    openDirectory(localProvider.toFileReference(parentFile))
                }
            }
            return true
        } else if (current.providerType == ProviderType.SAF) {
            val parentCrumbs = state.breadcrumbs.dropLast(1)
            if (parentCrumbs.isNotEmpty()) {
                openDirectory(parentCrumbs.last())
                return true
            }
        }
        return false
    }

    private fun buildBreadcrumbs(current: FileReference): List<FileReference> {
        if (current.providerType != ProviderType.LOCAL) {
            val existing = _browserState.value.breadcrumbs
            val idx = existing.indexOfFirst { it.uriOrPath == current.uriOrPath }
            return if (idx >= 0) existing.take(idx + 1) else existing + current
        }
        val rootPath = localProvider.getPrimaryRootDirectory().absolutePath
        val workspacePath = localProvider.getAppWorkspaceDirectory().absolutePath
        val baseRoot = if (current.uriOrPath.startsWith(workspacePath)) workspacePath else rootPath

        val crumbs = mutableListOf<FileReference>()
        var pointer: File? = File(current.uriOrPath)
        while (pointer != null) {
            crumbs.add(0, localProvider.toFileReference(pointer))
            if (pointer.absolutePath == baseRoot || pointer.parentFile == null) break
            pointer = pointer.parentFile
        }
        return crumbs
    }

    fun defaultSortForCategory(category: FileCategory?, currentSettings: AppSettingsState): Pair<SortField, SortOrder> {
        return when (category) {
            FileCategory.IMAGES -> SortField.DATE to SortOrder.DESCENDING
            FileCategory.AUDIO -> SortField.NAME to SortOrder.ASCENDING
            FileCategory.VIDEOS -> SortField.NAME to SortOrder.ASCENDING
            FileCategory.DOCUMENTS -> SortField.NAME to SortOrder.ASCENDING
            FileCategory.RECENT_FILES -> SortField.DATE to SortOrder.DESCENDING
            FileCategory.LARGE_FILES -> SortField.SIZE to SortOrder.DESCENDING
            else -> currentSettings.defaultSortField to currentSettings.defaultSortOrder
        }
    }

    fun openCategoryOrSpecialFolder(
        category: FileCategory,
        overrideSortField: SortField? = null,
        overrideSortOrder: SortOrder? = null
    ) {
        if (category == FileCategory.DOWNLOADS) {
            _currentDestination.value = OpenFileDestination.DOWNLOADS
            viewModelScope.launch(Dispatchers.IO) {
                val root = localProvider.getPrimaryRootDirectory()
                val dl = File(root, "Download").apply { if (!exists()) mkdirs() }
                openDirectory(localProvider.toFileReference(dl))
            }
            return
        }
        browseDirectoryJob?.cancel()
        val currentSettings = settings.value
        val (sortField, sortOrder) = if (overrideSortField != null && overrideSortOrder != null) {
            overrideSortField to overrideSortOrder
        } else {
            defaultSortForCategory(category, currentSettings)
        }
        val nowMs = System.currentTimeMillis()
        val cachedAt = categoryCacheAt[category] ?: 0L
        val cached = categoryCache[category]?.takeIf { nowMs - cachedAt < 30_000L }
            ?.let { list ->
                if (currentSettings.showHiddenFiles) list else list.filter { !it.isHidden }
            }
        val initialItems = if (cached != null) {
            FileSortAndFilterEngine.sortFiles(cached, sortField, sortOrder)
        } else {
            emptyList()
        }
        _browserState.value = _browserState.value.copy(
            currentDirectory = null,
            breadcrumbs = emptyList(),
            items = initialItems,
            isLoading = cached == null,
            selectedUris = emptySet(),
            searchQuery = "",
            activeCategoryFilter = category,
            inspectArchiveFile = null,
            activeRemoteProfile = null,
            errorMessage = null,
            currentSortField = sortField,
            currentSortOrder = sortOrder
        )
        browseDirectoryJob = viewModelScope.launch {
            val thresholdBytes = currentSettings.largeFileThresholdMb * 1024L * 1024L
            val indexedItems = mediaStoreProvider.queryByCategory(category, thresholdBytes)
            categoryCache[category] = indexedItems
            categoryCacheAt[category] = System.currentTimeMillis()
            val combinedItems = if (category == FileCategory.RECENT_FILES) {
                withContext(Dispatchers.IO) {
                    val openedRefs = recentFiles.value.mapNotNull { rec ->
                        val f = File(rec.uriOrPath)
                        if (f.exists() && f.canRead() && !f.isDirectory) localProvider.toFileReference(f) else null
                    }
                    (openedRefs + indexedItems).distinctBy { it.uriOrPath }
                }
            } else {
                indexedItems
            }
            val visibleItems = if (currentSettings.showHiddenFiles) combinedItems else combinedItems.filter { !it.isHidden }
            val sorted = withContext(Dispatchers.Default) {
                FileSortAndFilterEngine.sortFiles(
                    visibleItems,
                    sortField,
                    sortOrder
                )
            }
            _browserState.value = _browserState.value.copy(
                currentDirectory = null,
                breadcrumbs = emptyList(),
                items = sorted,
                isLoading = false,
                currentSortField = sortField,
                currentSortOrder = sortOrder
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // File Click, Open, Preview, Selection & Search
    // ---------------------------------------------------------------------------------------------

    fun onFileItemClicked(item: FileReference) {
        val state = _browserState.value
        if (state.isMultiSelectActive) {
            toggleSelection(item)
            return
        }
        if (item.isDirectory) {
            if (state.activeRemoteProfile != null) {
                openRemoteNetworkDirectory(state.activeRemoteProfile, item.uriOrPath)
            } else {
                _currentDestination.value = OpenFileDestination.INTERNAL_STORAGE
                openDirectory(item)
            }
            return
        }

        // Record in Recent Files
        viewModelScope.launch {
            dao.upsertRecentFile(
                RecentFileEntity(
                    uriOrPath = item.uriOrPath,
                    providerType = item.providerType.name,
                    displayName = item.displayName,
                    parentUriOrPath = item.parentUriOrPath,
                    mimeType = item.mimeType,
                    sizeBytes = item.sizeBytes,
                    lastModifiedAt = item.lastModifiedEpochMs
                )
            )
        }

        // 1. Check if supported archive (ZIP, TAR, GZ, XZ, 7Z, JAR) -> open native zip viewer
        val archiveExts = FileCategory.ARCHIVES.extensions + setOf("zip", "jar", "7z", "tar", "gz", "xz", "tgz", "txz")
        if (item.extension.lowercase() in archiveExts) {
            inspectArchive(item)
            return
        }

        // 2. Check if APK -> trigger Android's native PackageInstaller directly
        if (item.extension.lowercase() == "apk" && item.providerType == ProviderType.LOCAL) {
            appManager.installApkWithSystemInstaller(File(item.uriOrPath))
                .onFailure { openPropertiesSheet(item) }
            return
        }

        // 3. For text files & code files, open in built-in full-window editor directly (just like File Manager +)
        val textExtensions = setOf(
            "txt", "md", "log", "ini", "conf", "kt", "java", "py", "js", "ts", "sh",
            "yaml", "yml", "json", "xml", "html", "htm", "csv", "c", "cpp", "h",
            "hpp", "rs", "go", "rb", "php", "sql", "gradle", "properties", "toml"
        )
        if (item.extension.lowercase() in textExtensions) {
            openBuiltInPreview(item)
            return
        }

        // 4. For images, open in built-in image viewer with swipe support (just like File Manager +)
        if (item.extension.lowercase() in FileCategory.IMAGES.extensions) {
            openBuiltInPreview(item)
            return
        }

        // 5. For Remote Network files (FTP/SFTP/SMB), show built-in preview
        if (item.providerType != ProviderType.LOCAL && item.providerType != ProviderType.MEDIA_STORE && item.providerType != ProviderType.SAF) {
            openBuiltInPreview(item)
            return
        }

        // 6. Standard File Manager behavior: launch external / system handler (Video player, PDF viewer, Music player, Office app, etc.)
        // Falls back to built-in previewer ONLY if no external app is installed for this file type.
        openWithExternalApp(item, forceChooser = false, fallbackToBuiltInPreview = true)
    }

    fun openBuiltInPreview(item: FileReference) {
        val ext = item.extension.lowercase()
        if (ext in FileCategory.IMAGES.extensions) {
            val currentItems = _browserState.value.items
            val images = currentItems.filter { it.extension.lowercase() in FileCategory.IMAGES.extensions }
            val idx = images.indexOfFirst { it.uriOrPath == item.uriOrPath }.coerceAtLeast(0)
            _previewState.value = FilePreviewState(
                currentFile = item,
                siblingImages = if (images.isNotEmpty()) images else listOf(item),
                initialImageIndex = idx
            )
        } else {
            _previewState.value = FilePreviewState(currentFile = item)
        }
        _previewFile.value = item
    }

    fun closePreviewDialog() {
        _previewState.value = null
        _previewFile.value = null
    }

    fun saveEditedTextFile(item: FileReference, newContent: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val backingPath = item.metadata["localBackingPath"] ?: item.uriOrPath
                    File(backingPath).writeText(newContent)
                }
            }.onSuccess {
                showStatus("Saved '${item.displayName}'")
                refreshCurrentDirectory()
            }.onFailure { err ->
                showStatus("Failed to save file: ${err.message}")
            }
        }
    }

    fun openWithExternalApp(
        item: FileReference,
        forceChooser: Boolean = false,
        fallbackToBuiltInPreview: Boolean = false
    ) {
        runCatching {
            val resolvedMime = resolveMimeType(item)
            val localBacking = item.metadata["localBackingPath"]?.let { File(it) }
                ?: if (item.providerType == ProviderType.LOCAL) File(item.uriOrPath) else null
            val uri = if (localBacking != null) {
                try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        localBacking
                    )
                } catch (_: Exception) {
                    Uri.fromFile(localBacking)
                }
            } else {
                Uri.parse(item.uriOrPath)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, resolvedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val targetIntent = if (forceChooser) {
                Intent.createChooser(intent, "Open ${item.displayName}").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                intent
            }
            try {
                context.startActivity(targetIntent)
            } catch (e: Exception) {
                // Try wildcard category MIME fallback (e.g. video/*, audio/*, image/*, text/*)
                val wildcardMime = when {
                    resolvedMime.startsWith("video/") -> "video/*"
                    resolvedMime.startsWith("audio/") -> "audio/*"
                    resolvedMime.startsWith("image/") -> "image/*"
                    resolvedMime.startsWith("text/") -> "text/*"
                    else -> null
                }
                if (wildcardMime != null && resolvedMime != wildcardMime) {
                    intent.setDataAndType(uri, wildcardMime)
                    val fallbackIntent = if (forceChooser) {
                        Intent.createChooser(intent, "Open ${item.displayName}").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } else {
                        intent
                    }
                    context.startActivity(fallbackIntent)
                } else {
                    throw e
                }
            }
        }.onFailure {
            if (fallbackToBuiltInPreview && canPreviewBuiltIn(item)) {
                _previewFile.value = item
            } else {
                showStatus("No app installed to open ${item.displayName}")
            }
        }
    }

    private fun canPreviewBuiltIn(item: FileReference): Boolean {
        val ext = item.extension.lowercase()
        val textExtensions = setOf(
            "txt", "md", "log", "ini", "conf", "kt", "java", "py", "js", "ts", "sh",
            "yaml", "yml", "json", "xml", "html", "htm", "csv", "c", "cpp", "h",
            "hpp", "rs", "go", "rb", "php", "sql", "gradle", "properties", "toml"
        )
        return ext in textExtensions ||
            ext == "pdf" ||
            ext in FileCategory.IMAGES.extensions ||
            ext in FileCategory.AUDIO.extensions
    }

    private fun resolveMimeType(item: FileReference): String {
        val ext = item.extension.lowercase()
        val guessed = LocalFileSystemProvider.guessMimeType(ext)
        if (guessed != "application/octet-stream") return guessed
        return if (item.mimeType.isNotBlank() && item.mimeType != "application/octet-stream" && item.mimeType != "*/*") {
            item.mimeType
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        }
    }

    fun shareFileItem(item: FileReference) {
        if (item.providerType == ProviderType.LOCAL) {
            appManager.shareApkOrFile(File(item.uriOrPath), item.mimeType)
                .onFailure { showStatus("Share failed: ${it.message}") }
            return
        }
        showStatus("Download remote file to local storage before sharing.")
    }

    fun toggleSelection(item: FileReference) {
        val current = _browserState.value.selectedUris.toMutableSet()
        if (item.uriOrPath in current) {
            current.remove(item.uriOrPath)
        } else {
            current.add(item.uriOrPath)
        }
        _browserState.value = _browserState.value.copy(selectedUris = current)
    }

    fun selectAllItems() {
        val all = _browserState.value.items.map { it.uriOrPath }.toSet()
        _browserState.value = _browserState.value.copy(selectedUris = all)
    }

    fun clearSelection() {
        _browserState.value = _browserState.value.copy(selectedUris = emptySet())
    }

    fun getSelectedFileReferences(): List<FileReference> {
        val selected = _browserState.value.selectedUris
        return _browserState.value.items.filter { it.uriOrPath in selected }
    }

    fun updateSearchQuery(query: String) {
        _browserState.value = _browserState.value.copy(searchQuery = query)
        searchDebounceJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            searchDebounceJob = viewModelScope.launch(Dispatchers.IO) {
                delay(350L)
                dao.insertSearchQuery(SearchQueryEntity(query = trimmed))
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // File Operations (Create Folder/File, Rename, Copy, Cut, Paste, Delete, Properties)
    // ---------------------------------------------------------------------------------------------

    fun createNewFolder(folderName: String) {
        val currentDir = _browserState.value.currentDirectory ?: return
        val remoteProfile = _browserState.value.activeRemoteProfile

        viewModelScope.launch {
            if (remoteProfile != null) {
                val secret = credentialVault.retrieveSecret(remoteProfile.secretAlias).orEmpty()
                val newPath = "${currentDir.uriOrPath.trimEnd('/')}/$folderName"
                RemoteNetworkClients.createRemoteDirectory(remoteProfile, secret, newPath)
                    .onSuccess {
                        showStatus("Created remote folder '$folderName'")
                        refreshCurrentDirectory()
                    }
                    .onFailure { showStatus("Remote mkdir failed: ${it.message}") }
                return@launch
            }

            val provider = if (currentDir.providerType == ProviderType.SAF) safProvider else localProvider
            provider.createDirectory(currentDir, folderName)
                .onSuccess {
                    showStatus("Created folder '${it.displayName}'")
                    refreshCurrentDirectory()
                }
                .onFailure { showStatus(it.message ?: "Failed to create folder") }
        }
    }

    fun createNewTextFile(fileName: String, content: String) {
        val currentDir = _browserState.value.currentDirectory ?: return
        viewModelScope.launch {
            val provider = if (currentDir.providerType == ProviderType.SAF) safProvider else localProvider
            provider.createTextFile(currentDir, fileName, content)
                .onSuccess {
                    showStatus("Created file '${it.displayName}'")
                    refreshCurrentDirectory()
                }
                .onFailure { showStatus(it.message ?: "Failed to create file") }
        }
    }

    fun renameItem(target: FileReference, newName: String) {
        val remoteProfile = _browserState.value.activeRemoteProfile
        viewModelScope.launch {
            if (remoteProfile != null) {
                val secret = credentialVault.retrieveSecret(remoteProfile.secretAlias).orEmpty()
                RemoteNetworkClients.renameRemoteItem(remoteProfile, secret, target, newName)
                    .onSuccess {
                        showStatus("Renamed to '$newName'")
                        clearSelection()
                        refreshCurrentDirectory()
                    }
                    .onFailure { showStatus("Remote rename failed: ${it.message}") }
                return@launch
            }

            val provider = if (target.providerType == ProviderType.SAF) safProvider else localProvider
            provider.rename(target, newName)
                .onSuccess { renamedRef ->
                    categoryCache.clear()
                    mediaStoreProvider.invalidateSupplementalCache()
                    _browserState.value = _browserState.value.copy(
                        items = _browserState.value.items.map { item ->
                            if (item.uriOrPath == target.uriOrPath) renamedRef else item
                        }
                    )
                    updateAnalysisReportOnRename(target, renamedRef)
                    showStatus("Renamed to '${renamedRef.displayName}'")
                    clearSelection()
                    if (_browserState.value.currentDirectory != null) {
                        refreshCurrentDirectory()
                    }
                }
                .onFailure { showStatus(it.message ?: "Rename failed") }
        }
    }

    fun openContainingFolder(item: FileReference) {
        val parentPath = item.parentUriOrPath ?: File(item.uriOrPath).parent
        if (parentPath.isNullOrBlank()) {
            showStatus("Cannot resolve containing folder for '${item.displayName}'")
            return
        }
        viewModelScope.launch {
            val parentRef = withContext(Dispatchers.IO) {
                val parentFile = File(parentPath)
                if (parentFile.exists() && parentFile.isDirectory) {
                    localProvider.toFileReference(parentFile)
                } else null
            }
            if (parentRef != null) {
                _currentDestination.value = OpenFileDestination.INTERNAL_STORAGE
                openDirectory(parentRef)
            } else {
                showStatus("Containing folder not found: $parentPath")
            }
        }
    }

    fun copySelectedToClipboard(mode: ClipboardMode) {
        val selected = getSelectedFileReferences()
        if (selected.isEmpty()) return
        transferEngine.copyToClipboard(selected, mode)
        clearSelection()
        showStatus("${if (mode == ClipboardMode.CUT) "Cut" else "Copied"} ${selected.size} item(s) to clipboard")
    }

    fun pasteClipboardHere() {
        val destDir = _browserState.value.currentDirectory ?: return
        viewModelScope.launch {
            val verify = settings.first().verifyChecksumAfterTransfer
            transferEngine.enqueuePasteFromClipboard(
                destinationDir = destDir,
                verifyChecksum = verify,
                onComplete = { result ->
                    result.onSuccess { msg ->
                        categoryCache.clear()
                        mediaStoreProvider.invalidateSupplementalCache()
                        showStatus(msg)
                        refreshCurrentDirectory()
                    }.onFailure { err ->
                        showStatus("Transfer error: ${err.message}")
                        refreshCurrentDirectory()
                    }
                }
            )
        }
    }

    fun deleteItems(items: List<FileReference>, moveToTrash: Boolean) {
        val remoteProfile = _browserState.value.activeRemoteProfile
        viewModelScope.launch {
            if (remoteProfile != null) {
                val secret = credentialVault.retrieveSecret(remoteProfile.secretAlias).orEmpty()
                var deleted = 0
                for (item in items) {
                    RemoteNetworkClients.deleteRemoteItem(remoteProfile, secret, item)
                        .onSuccess { deleted++ }
                        .onFailure { showStatus("Delete failed: ${it.message}") }
                }
                clearSelection()
                showStatus("Deleted $deleted remote item(s)")
                refreshCurrentDirectory()
                return@launch
            }

            transferEngine.deleteItems(items, moveToTrash)
                .onSuccess { msg ->
                    val deletedPaths = items.map { it.uriOrPath }.toHashSet()
                    categoryCache.clear()
                    mediaStoreProvider.invalidateSupplementalCache()
                    _browserState.value = _browserState.value.copy(
                        items = _browserState.value.items.filterNot { it.uriOrPath in deletedPaths }
                    )
                    updateAnalysisReportOnDelete(items)
                    clearSelection()
                    showStatus(msg)
                    if (_browserState.value.currentDirectory != null) {
                        refreshCurrentDirectory()
                    }
                }
                .onFailure { err ->
                    showStatus(err.message ?: "Delete failed")
                }
        }
    }

    fun performUndo(record: UndoActionRecord) {
        viewModelScope.launch {
            transferEngine.dismissUndoBanner()
            record.performUndo()
                .onSuccess { msg ->
                    categoryCache.clear()
                    mediaStoreProvider.invalidateSupplementalCache()
                    showStatus(msg)
                    refreshCurrentDirectory()
                    val currentReport = _analysisReport.value
                    if (currentReport != null && _currentDestination.value == OpenFileDestination.STORAGE_ANALYSIS) {
                        runStorageAnalysis(File(currentReport.rootPath))
                    }
                }
                .onFailure { err ->
                    showStatus("Undo failed: ${err.message}")
                }
        }
    }

    private fun updateAnalysisReportOnRename(oldRef: FileReference, newRef: FileReference) {
        val current = _analysisReport.value ?: return
        val updatedLargestFiles = current.largestFiles.map {
            if (it.uriOrPath == oldRef.uriOrPath) newRef.copy(sizeBytes = it.sizeBytes) else it
        }
        val updatedLargestDirs = current.largestDirectories.map { (dirRef, totalSize) ->
            if (dirRef.uriOrPath == oldRef.uriOrPath) {
                newRef.copy(sizeBytes = totalSize) to totalSize
            } else {
                dirRef to totalSize
            }
        }
        val updatedDuplicates = current.duplicateGroups.map { group ->
            group.copy(
                files = group.files.map { f ->
                    if (f.uriOrPath == oldRef.uriOrPath) newRef.copy(sizeBytes = f.sizeBytes) else f
                }
            )
        }
        _analysisReport.value = current.copy(
            largestFiles = updatedLargestFiles,
            largestDirectories = updatedLargestDirs,
            duplicateGroups = updatedDuplicates
        )
    }

    private fun updateAnalysisReportOnDelete(deletedItems: List<FileReference>) {
        val current = _analysisReport.value ?: return
        val deletedPaths = deletedItems.map { it.uriOrPath }.toHashSet()
        val freedBytes = deletedItems.sumOf { it.sizeBytes }.coerceAtLeast(0L)
        val updatedLargestFiles = current.largestFiles.filterNot { it.uriOrPath in deletedPaths }
        val updatedLargestDirs = current.largestDirectories.filterNot { (dirRef, _) ->
            dirRef.uriOrPath in deletedPaths
        }
        val updatedDuplicates = current.duplicateGroups.mapNotNull { group ->
            val remaining = group.files.filterNot { it.uriOrPath in deletedPaths }
            if (remaining.size >= 2) {
                group.copy(files = remaining)
            } else {
                null
            }
        }

        // Accurately update category usage buckets and recalculate fractions
        val categoryDeltas = mutableMapOf<String, Pair<Long, Int>>()
        for (item in deletedItems) {
            if (!item.isDirectory) {
                val cat = FileCategory.categorize(item)?.title ?: "Other"
                val prev = categoryDeltas[cat] ?: (0L to 0)
                categoryDeltas[cat] = (prev.first + item.sizeBytes) to (prev.second + 1)
            }
        }
        val updatedTotalBytes = (current.usedBytes - freedBytes).coerceAtLeast(0L)
        val updatedBuckets = current.categoryBuckets.map { bucket ->
            val delta = categoryDeltas[bucket.categoryName]
            if (delta != null) {
                val newBytes = (bucket.bytesUsed - delta.first).coerceAtLeast(0L)
                val newCount = (bucket.fileCount - delta.second).coerceAtLeast(0)
                val newFraction = if (updatedTotalBytes > 0L) newBytes.toFloat() / updatedTotalBytes.toFloat() else 0f
                bucket.copy(bytesUsed = newBytes, fileCount = newCount, fractionOfTotal = newFraction)
            } else {
                val newFraction = if (updatedTotalBytes > 0L) bucket.bytesUsed.toFloat() / updatedTotalBytes.toFloat() else 0f
                bucket.copy(fractionOfTotal = newFraction)
            }
        }

        _analysisReport.value = current.copy(
            usedBytes = updatedTotalBytes,
            freeBytes = current.freeBytes + freedBytes,
            scannedFilesCount = (current.scannedFilesCount - deletedItems.count { !it.isDirectory }).coerceAtLeast(0),
            scannedDirectoriesCount = (current.scannedDirectoriesCount - deletedItems.count { it.isDirectory }).coerceAtLeast(0),
            categoryBuckets = updatedBuckets,
            largestFiles = updatedLargestFiles,
            largestDirectories = updatedLargestDirs,
            duplicateGroups = updatedDuplicates
        )
    }

    fun openPropertiesSheet(item: FileReference) {
        propertiesJob?.cancel()
        _propertiesFile.value = item
        _propertiesChecksums.value = null
        _propertiesApkInfo.value = null
        val originalItem = item

        propertiesJob = viewModelScope.launch {
            if (item.providerType == ProviderType.LOCAL) {
                val (detailedRef, dirSize, apk, hashes) = withContext(Dispatchers.IO) {
                    val file = File(item.uriOrPath)
                    val dr = localProvider.toFileReference(
                        file = file,
                        includeChildCount = true,
                        includeCanonicalPath = true
                    )
                    val ds = if (file.isDirectory) runCatching { localProvider.calculateDirectorySize(file) }.getOrDefault(0L) else 0L
                    val a = if (!file.isDirectory && item.extension == "apk") appManager.inspectApkFile(file).getOrNull() else null
                    val h = if (file.isFile && file.length() <= 250L * 1024 * 1024) runCatching {
                        file.inputStream().use { ChecksumAndDuplicateEngine.computeChecksums(it) }
                    }.getOrNull() else null
                    PropertyComputeResult(dr, ds, a, h)
                }
                if (detailedRef.isDirectory) {
                    _propertiesFile.value = detailedRef.copy(sizeBytes = if (dirSize > 0) dirSize else detailedRef.sizeBytes)
                } else {
                    _propertiesFile.value = detailedRef
                }
                _propertiesApkInfo.value = apk
                _propertiesChecksums.value = hashes
            }
        }
    }

    fun closePropertiesSheet() {
        propertiesJob?.cancel()
        propertiesJob = null
        _propertiesFile.value = null
        _propertiesChecksums.value = null
        _propertiesApkInfo.value = null
    }

    // ---------------------------------------------------------------------------------------------
    // Bookmarks, Recent Files & SAF Grants
    // ---------------------------------------------------------------------------------------------

    fun openBookmark(bm: BookmarkEntity) {
        when (bm.providerType) {
            ProviderType.SAF.name -> {
                viewModelScope.launch {
                    val uri = Uri.parse(bm.uriOrPath)
                    val ref = withContext(Dispatchers.IO) { safProvider.fromTreeUri(uri, bm.title) }
                    if (ref.isDirectory) {
                        _currentDestination.value = OpenFileDestination.INTERNAL_STORAGE
                        openDirectory(ref)
                    } else {
                        onFileItemClicked(ref)
                    }
                }
            }
            else -> {
                viewModelScope.launch {
                    val ref = withContext(Dispatchers.IO) {
                        val file = File(bm.uriOrPath)
                        if (file.exists()) localProvider.toFileReference(file) else null
                    }
                    if (ref != null) {
                        if (ref.isDirectory) {
                            _currentDestination.value = OpenFileDestination.INTERNAL_STORAGE
                            openDirectory(ref)
                        } else {
                            onFileItemClicked(ref)
                        }
                    } else {
                        showStatus("Bookmarked path '${bm.title}' no longer exists.")
                    }
                }
            }
        }
    }

    fun openRecentFile(recent: RecentFileEntity) {
        viewModelScope.launch {
            val ref = withContext(Dispatchers.IO) {
                val file = File(recent.uriOrPath)
                if (file.exists()) localProvider.toFileReference(file) else null
            }
            if (ref != null) {
                onFileItemClicked(ref)
            } else {
                showStatus("Recent file '${recent.displayName}' was moved or deleted.")
            }
        }
    }

    fun toggleBookmark(item: FileReference) {
        viewModelScope.launch {
            val existing = bookmarks.value.firstOrNull { it.uriOrPath == item.uriOrPath }
            if (existing != null) {
                dao.deleteBookmarkById(existing.id)
                showStatus("Removed bookmark '${item.displayName}'")
            } else {
                dao.insertBookmark(
                    BookmarkEntity(
                        title = item.displayName,
                        providerType = item.providerType.name,
                        uriOrPath = item.uriOrPath,
                        isDirectory = item.isDirectory,
                        mimeType = item.mimeType
                    )
                )
                showStatus("Bookmarked '${item.displayName}'")
            }
        }
    }

    fun renameBookmark(bookmark: BookmarkEntity, newTitle: String) {
        viewModelScope.launch {
            dao.updateBookmark(bookmark.copy(title = newTitle.trim().ifEmpty { bookmark.title }))
            showStatus("Renamed bookmark")
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            dao.deleteBookmarkById(bookmark.id)
            showStatus("Removed bookmark '${bookmark.title}'")
        }
    }

    fun buildSafInitialUriForLocalPath(targetLocalPath: String? = null): Uri? {
        val path = targetLocalPath ?: _browserState.value.currentDirectory?.uriOrPath ?: return null
        val file = File(path)
        if (localProvider.isRestrictedAndroidDirectory(file)) {
            runCatching {
                if (!file.exists()) file.mkdirs()
            }
        }
        return safProvider.buildInitialSafUriForLocalPath(
            localPath = path,
            primaryRootPath = localProvider.getPrimaryRootDirectory().absolutePath
        )
    }

    fun onSafTreeGranted(treeUri: Uri) {
        viewModelScope.launch {
            val (label, ref) = withContext(Dispatchers.IO) {
                val l = safProvider.persistTreePermission(treeUri)
                val r = safProvider.fromTreeUri(treeUri, l)
                l to r
            }
            dao.upsertSafGrant(
                SafGrantEntity(
                    treeUriString = treeUri.toString(),
                    displayName = label,
                    isPermissionValid = true
                )
            )
            _currentDestination.value = OpenFileDestination.INTERNAL_STORAGE
            val currentDir = _browserState.value.currentDirectory
            if (currentDir != null &&
                currentDir.providerType == ProviderType.LOCAL &&
                localProvider.isRestrictedAndroidDirectory(File(currentDir.uriOrPath))
            ) {
                openDirectory(currentDir)
            } else {
                openDirectory(ref)
            }
            showStatus("Granted SAF access to '$label'")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Archive Management (Browse, Extract, Create, Verify)
    // ---------------------------------------------------------------------------------------------

    fun inspectArchive(archiveRef: FileReference) {
        viewModelScope.launch {
            _browserState.value = _browserState.value.copy(isLoading = true, errorMessage = null)
            val file = if (archiveRef.providerType == ProviderType.SAF && !File(archiveRef.uriOrPath).exists()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val uri = Uri.parse(archiveRef.uriOrPath)
                        val cacheFile = File(context.cacheDir, "saf_archive_temp_${archiveRef.displayName}")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            cacheFile.outputStream().use { out -> input.copyTo(out) }
                        }
                        cacheFile
                    }.getOrNull()
                } ?: File(archiveRef.uriOrPath)
            } else {
                File(archiveRef.uriOrPath)
            }

            if (!file.exists()) {
                _browserState.value = _browserState.value.copy(isLoading = false)
                showStatus("Cannot locate archive file: ${archiveRef.displayName}")
                return@launch
            }
            ArchiveEngine.listArchiveEntries(file).fold(
                onSuccess = { entries ->
                    _currentDestination.value = OpenFileDestination.INTERNAL_STORAGE
                    _browserState.value = _browserState.value.copy(
                        inspectArchiveFile = archiveRef.copy(uriOrPath = file.absolutePath),
                        archiveEntries = entries,
                        archiveSubPath = "",
                        isLoading = false
                    )
                },
                onFailure = { err ->
                    _browserState.value = _browserState.value.copy(isLoading = false)
                    showStatus("Cannot read archive: ${err.message}")
                }
            )
        }
    }

    fun openArchiveSubFolder(subPath: String) {
        _browserState.value = _browserState.value.copy(archiveSubPath = subPath)
    }

    fun closeArchiveInspector() {
        _browserState.value = _browserState.value.copy(
            inspectArchiveFile = null,
            archiveEntries = emptyList(),
            archiveSubPath = ""
        )
    }

    fun previewArchiveEntry(archiveRef: FileReference, entry: ArchiveEntryItem) {
        if (entry.isDirectory) {
            openArchiveSubFolder(entry.entryPath.trimEnd('/'))
            return
        }
        viewModelScope.launch {
            val archiveFile = File(archiveRef.uriOrPath)
            if (!archiveFile.exists()) {
                showStatus("Archive file not found: ${archiveRef.displayName}")
                return@launch
            }
            _browserState.value = _browserState.value.copy(isLoading = true)
            val extracted = getOrExtractArchiveEntry(archiveRef, entry)
            _browserState.value = _browserState.value.copy(isLoading = false)

            if (extracted == null) {
                showStatus("Cannot view file: ${entry.displayName}")
                return@launch
            }

            val ref = localProvider.toFileReference(extracted).copy(
                displayName = entry.displayName,
                sizeBytes = entry.uncompressedSize
            )

            val ext = entry.displayName.substringAfterLast('.', "").lowercase()
            if (ext in FileCategory.IMAGES.extensions) {
                val allArchiveImages = _browserState.value.archiveEntries.filter {
                    !it.isDirectory && it.displayName.substringAfterLast('.', "").lowercase() in FileCategory.IMAGES.extensions
                }
                val currentSub = _browserState.value.archiveSubPath.trim('/')
                val folderImages = if (currentSub.isNotEmpty()) {
                    allArchiveImages.filter {
                        val p = if (it.entryPath.contains('/')) it.entryPath.substringBeforeLast('/') else ""
                        p == currentSub
                    }
                } else {
                    val direct = allArchiveImages.filter { !it.entryPath.contains('/') }
                    if (direct.isNotEmpty()) direct else allArchiveImages
                }
                val rawTarget = if (folderImages.isNotEmpty()) folderImages else allArchiveImages
                val targetImages = rawTarget.sortedWith { a, b -> NaturalOrderComparator.compare(a.displayName, b.displayName) }
                val idx = targetImages.indexOfFirst { it.entryPath == entry.entryPath }.coerceAtLeast(0)

                _previewState.value = FilePreviewState(
                    currentFile = ref,
                    archiveContext = ArchiveImagePreviewContext(
                        archiveRef = archiveRef,
                        imageEntries = targetImages,
                        initialIndex = idx
                    )
                )
            } else {
                _previewState.value = FilePreviewState(currentFile = ref)
            }
            _previewFile.value = ref
        }
    }

    fun extractArchiveToFolder(
        archiveRef: FileReference,
        selectedEntries: Set<String>? = null
    ) {
        viewModelScope.launch {
            val archiveFile = File(archiveRef.uriOrPath)
            if (!archiveFile.exists()) {
                showStatus("Archive file not found")
                return@launch
            }
            val baseName = archiveFile.name.substringBeforeLast('.')
            val destDir = File(archiveFile.parentFile ?: localProvider.getPrimaryRootDirectory(), "${baseName}_extracted")
            showStatus("Extracting ${archiveFile.name}...")
            ArchiveEngine.extractArchive(
                archiveFile = archiveFile,
                destinationDir = destDir,
                selectedEntryPaths = selectedEntries
            ).fold(
                onSuccess = { count ->
                    showStatus("Extracted $count file(s) to ${destDir.name}")
                    _browserState.value = _browserState.value.copy(
                        inspectArchiveFile = null,
                        archiveEntries = emptyList()
                    )
                    openDirectory(localProvider.toFileReference(destDir))
                },
                onFailure = { err ->
                    showStatus("Extraction blocked or failed: ${err.message}")
                }
            )
        }
    }

    fun createArchiveFromSelection(
        archiveNameWithoutExt: String,
        format: ArchiveCreationFormat
    ) {
        createArchiveFromItems(getSelectedFileReferences(), archiveNameWithoutExt, format)
    }

    fun createArchiveFromItems(
        selected: List<FileReference>,
        archiveNameWithoutExt: String,
        format: ArchiveCreationFormat
    ) {
        val currentDir = _browserState.value.currentDirectory
        if (selected.isEmpty()) {
            showStatus("Select at least one file or folder to compress.")
            return
        }
        viewModelScope.launch {
            val cleanBase = archiveNameWithoutExt.trim().ifEmpty { "Archive_${System.currentTimeMillis()}" }
            val localSelected = selected.filter { it.providerType == ProviderType.LOCAL }
            if (localSelected.isEmpty()) {
                showStatus("Select at least one local file or folder to compress.")
                return@launch
            }
            val destinationFolder = if (currentDir != null && currentDir.providerType == ProviderType.LOCAL) {
                File(currentDir.uriOrPath)
            } else {
                File(localSelected.first().uriOrPath).parentFile ?: localProvider.getPrimaryRootDirectory()
            }
            val outFile = File(destinationFolder, "$cleanBase.${format.extension}")
            val sources = localSelected.map { File(it.uriOrPath) }
            showStatus("Creating ${outFile.name}...")
            ArchiveEngine.createArchive(
                sourceFiles = sources,
                outputArchiveFile = outFile,
                format = format
            ).fold(
                onSuccess = { created ->
                    clearSelection()
                    categoryCache.clear()
                    mediaStoreProvider.invalidateSupplementalCache()
                    showStatus("Created archive ${created.name} in ${destinationFolder.name}")
                    if (currentDir != null) {
                        refreshCurrentDirectory()
                    }
                },
                onFailure = { err ->
                    showStatus("Compression failed: ${err.message}")
                }
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Storage Analyzer & Duplicate Cleanup
    // ---------------------------------------------------------------------------------------------

    fun runStorageAnalysis(targetRoot: File = _analysisTargetRoot.value ?: localProvider.getPrimaryRootDirectory()) {
        analysisJob?.cancel()
        _analysisTargetRoot.value = targetRoot
        // Drop a stale report that belongs to a different storage so the UI never mixes volumes.
        if (_analysisReport.value?.rootPath != targetRoot.absolutePath) {
            _analysisReport.value = null
        }
        analysisJob = viewModelScope.launch {
            _isAnalyzingStorage.value = true
            _analysisProgressText.value = "Scanning ${targetRoot.absolutePath}..."
            var lastEmitTimeMs = 0L
            storageAnalyzer.analyzeDirectory(
                rootDir = targetRoot,
                includeDuplicateScan = true,
                onProgress = { count, _, name ->
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastEmitTimeMs >= 100L) {
                        lastEmitTimeMs = now
                        _analysisProgressText.value = "Scanned $count files ($name)"
                    }
                }
            ).fold(
                onSuccess = { report ->
                    _analysisReport.value = report
                    _isAnalyzingStorage.value = false
                },
                onFailure = { err ->
                    _isAnalyzingStorage.value = false
                    showStatus("Storage scan interrupted: ${err.message}")
                }
            )
        }
    }

    fun cancelStorageAnalysis() {
        analysisJob?.cancel()
        _isAnalyzingStorage.value = false
    }

    // ---------------------------------------------------------------------------------------------
    // Installed Applications & APK Manager
    // ---------------------------------------------------------------------------------------------

    fun loadApplications(includeSystem: Boolean) {
        _includeSystemApps.value = includeSystem
        viewModelScope.launch {
            _isLoadingApps.value = true
            val list = appManager.loadInstalledApps(includeSystem)
            _installedApps.value = list
            _isLoadingApps.value = false
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Network Suite: Local FTP Server & Remote FTP/FTPS/SFTP/SMB Clients
    // ---------------------------------------------------------------------------------------------

    fun getSavedFtpPassword(): String {
        val alias = settings.value.ftpSecretAlias
        return if (alias.isNotBlank()) {
            credentialVault.retrieveSecret(alias).orEmpty().ifBlank { "openfile123" }
        } else {
            "openfile123"
        }
    }

    fun saveFtpServerConfig(
        port: Int,
        passiveStart: Int,
        passiveEnd: Int,
        username: String,
        passwordPlain: String = "",
        anonymousAllowed: Boolean,
        allowWrite: Boolean,
        useTls: Boolean,
        idleTimeoutMinutes: Int,
        rootPath: String,
        showConfirmation: Boolean = false
    ) {
        val cleanUser = username.trim().ifBlank { "openfile" }
        val currentCfg = _settings.value
        // Update in-memory state immediately so starting the server never reads stale settings
        _settings.value = currentCfg.copy(
            ftpPort = port,
            ftpPassivePortStart = passiveStart,
            ftpPassivePortEnd = passiveEnd,
            ftpUsername = cleanUser,
            ftpAnonymousAllowed = anonymousAllowed,
            ftpAllowWrite = allowWrite,
            ftpUseExplicitTls = useTls,
            ftpIdleTimeoutMinutes = idleTimeoutMinutes,
            ftpRootPath = rootPath
        )
        viewModelScope.launch {
            val secretAlias = withContext(Dispatchers.IO) {
                if (passwordPlain.isNotBlank()) {
                    credentialVault.storeSecret(currentCfg.ftpSecretAlias.ifBlank { null }, passwordPlain)
                } else {
                    currentCfg.ftpSecretAlias
                }
            }
            _settings.value = _settings.value.copy(ftpSecretAlias = secretAlias)
            settingsStore.updateFtpServerConfig(
                port = port,
                passiveStart = passiveStart,
                passiveEnd = passiveEnd,
                username = cleanUser,
                secretAlias = secretAlias,
                anonymousAllowed = anonymousAllowed,
                allowWrite = allowWrite,
                useTls = useTls,
                idleTimeoutMinutes = idleTimeoutMinutes,
                rootPath = rootPath
            )
            if (showConfirmation) {
                showStatus("FTP server configuration saved")
            }
        }
    }

    fun startLocalFtpServer(passwordPlain: String) {
        viewModelScope.launch {
            val cfg = _settings.value
            val cleanUser = cfg.ftpUsername.trim().ifBlank { "openfile" }
            val (secretAlias, resolvedPassword, rootPath) = withContext(Dispatchers.IO) {
                val alias = if (passwordPlain.isNotBlank()) {
                    credentialVault.storeSecret(cfg.ftpSecretAlias.ifBlank { null }, passwordPlain)
                } else {
                    cfg.ftpSecretAlias
                }
                val resolved = if (passwordPlain.isNotBlank()) {
                    passwordPlain
                } else {
                    credentialVault.retrieveSecret(alias).orEmpty()
                }
                val primaryPath = localProvider.getPrimaryRootDirectory().absolutePath
                val root = if (cfg.ftpRootPath.isBlank() || cfg.ftpRootPath == primaryPath) {
                    "ALL"
                } else {
                    cfg.ftpRootPath
                }
                Triple(alias, resolved, root)
            }

            if (!cfg.ftpAnonymousAllowed && resolvedPassword.isBlank()) {
                showStatus("Set an FTP password or enable Anonymous mode first.")
                return@launch
            }

            _settings.value = cfg.copy(
                ftpUsername = cleanUser,
                ftpSecretAlias = secretAlias,
                ftpRootPath = rootPath
            )

            settingsStore.updateFtpServerConfig(
                port = cfg.ftpPort,
                passiveStart = cfg.ftpPassivePortStart,
                passiveEnd = cfg.ftpPassivePortEnd,
                username = cleanUser,
                secretAlias = secretAlias,
                anonymousAllowed = cfg.ftpAnonymousAllowed,
                allowWrite = cfg.ftpAllowWrite,
                useTls = cfg.ftpUseExplicitTls,
                idleTimeoutMinutes = cfg.ftpIdleTimeoutMinutes,
                rootPath = rootPath
            )

            val serviceIntent = Intent(context, FtpServerForegroundService::class.java).apply {
                action = FtpServerForegroundService.ACTION_START
                putExtra(FtpServerForegroundService.EXTRA_PORT, cfg.ftpPort)
                putExtra(FtpServerForegroundService.EXTRA_PASV_START, cfg.ftpPassivePortStart)
                putExtra(FtpServerForegroundService.EXTRA_PASV_END, cfg.ftpPassivePortEnd)
                putExtra(FtpServerForegroundService.EXTRA_ROOT_PATH, rootPath)
                putExtra(FtpServerForegroundService.EXTRA_USERNAME, cleanUser)
                putExtra(FtpServerForegroundService.EXTRA_PASSWORD, resolvedPassword)
                putExtra(FtpServerForegroundService.EXTRA_ANONYMOUS, cfg.ftpAnonymousAllowed)
                putExtra(FtpServerForegroundService.EXTRA_ALLOW_WRITE, cfg.ftpAllowWrite)
                putExtra(FtpServerForegroundService.EXTRA_USE_TLS, cfg.ftpUseExplicitTls)
                putExtra(FtpServerForegroundService.EXTRA_TIMEOUT_MIN, cfg.ftpIdleTimeoutMinutes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    fun stopLocalFtpServer() {
        val stopIntent = Intent(context, FtpServerForegroundService::class.java).apply {
            action = FtpServerForegroundService.ACTION_STOP
        }
        context.startService(stopIntent)
    }

    fun saveNetworkProfile(
        displayName: String,
        protocol: String,
        host: String,
        port: Int,
        username: String,
        passwordOrPrivateKey: String,
        remoteInitialPath: String,
        shareName: String,
        isAnonymous: Boolean,
        usePassiveMode: Boolean,
        connectImmediately: Boolean = false
    ) {
        viewModelScope.launch {
            val cleanHost = host.trim()
            val existing = networkProfiles.value.firstOrNull {
                it.host.equals(cleanHost, ignoreCase = true) &&
                    it.port == port &&
                    it.protocol.equals(protocol, ignoreCase = true)
            }
            val previousSecret = withContext(Dispatchers.IO) {
                credentialVault.retrieveSecret(existing?.secretAlias)
            }
            val alias = if (!isAnonymous && passwordOrPrivateKey.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    credentialVault.storeSecret(existing?.secretAlias, passwordOrPrivateKey)
                }
            } else if (!isAnonymous) {
                existing?.secretAlias
            } else {
                null
            }

            val resolvedName = displayName.trim().ifEmpty { "$protocol://$cleanHost:$port" }
            val entity = NetworkProfileEntity(
                id = existing?.id ?: 0L,
                displayName = resolvedName,
                protocol = protocol,
                host = cleanHost,
                port = port,
                username = username.trim(),
                secretAlias = alias,
                remoteInitialPath = remoteInitialPath.trim().ifEmpty { "/" },
                shareName = shareName.trim(),
                expectedHostKeyFingerprint = existing?.expectedHostKeyFingerprint,
                isAnonymous = isAnonymous,
                usePassiveMode = usePassiveMode
            )
            val rowId = dao.upsertNetworkProfile(entity)
            val savedProfile = entity.copy(id = if (entity.id != 0L) entity.id else rowId)
            if (connectImmediately) {
                showStatus("Connecting to $resolvedName...")
                openRemoteNetworkDirectory(savedProfile, savedProfile.remoteInitialPath) { err ->
                    // Only keep credentials that actually worked (or that await SSH host-key trust),
                    // so a typo never turns into a broken one-tap shortcut.
                    if (err !is SshHostKeyVerificationRequiredException) {
                        viewModelScope.launch {
                            if (existing == null) {
                                withContext(Dispatchers.IO) { credentialVault.deleteSecret(savedProfile.secretAlias) }
                                dao.deleteNetworkProfile(savedProfile.id)
                            } else {
                                dao.upsertNetworkProfile(existing)
                                if (previousSecret != null && existing.secretAlias != null) {
                                    withContext(Dispatchers.IO) {
                                        credentialVault.storeSecret(existing.secretAlias, previousSecret)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                showStatus("Saved network profile '$resolvedName'")
            }
        }
    }

    fun deleteNetworkProfile(profile: NetworkProfileEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                credentialVault.deleteSecret(profile.secretAlias)
            }
            dao.deleteNetworkProfile(profile.id)
            showStatus("Deleted profile '${profile.displayName}'")
        }
    }

    private fun buildRemoteBreadcrumbs(
        profile: NetworkProfileEntity,
        provider: ProviderType,
        path: String
    ): List<FileReference> {
        // Tag every crumb with its server so paste / copy / move can reach the right remote profile.
        val profileMeta = mapOf(RemoteNetworkClients.REMOTE_PROFILE_ID_KEY to profile.id.toString())
        val crumbs = mutableListOf(
            FileReference(
                providerType = provider,
                uriOrPath = "/",
                displayName = profile.displayName,
                parentUriOrPath = null,
                isDirectory = true,
                metadata = profileMeta
            )
        )
        var acc = ""
        path.split('/').filter { it.isNotBlank() }.forEach { segment ->
            acc = "$acc/$segment"
            crumbs += FileReference(
                providerType = provider,
                uriOrPath = acc,
                displayName = segment,
                parentUriOrPath = acc.substringBeforeLast('/').ifEmpty { "/" },
                isDirectory = true,
                metadata = profileMeta
            )
        }
        return crumbs
    }

    fun openRemoteNetworkDirectory(
        profile: NetworkProfileEntity,
        remotePath: String = profile.remoteInitialPath,
        onConnectFailed: (Throwable) -> Unit = {}
    ) {
        viewModelScope.launch {
            val previousRemoteProfile = _browserState.value.activeRemoteProfile
            _browserState.value = _browserState.value.copy(
                isLoading = true,
                errorMessage = null,
                selectedUris = emptySet()
            )
            val secret = withContext(Dispatchers.IO) {
                credentialVault.retrieveSecret(profile.secretAlias).orEmpty()
            }
            RemoteNetworkClients.listRemoteDirectory(profile, secret, remotePath).fold(
                onSuccess = { items ->
                    val provider = when (profile.protocol.uppercase()) {
                        "FTPS" -> ProviderType.FTPS
                        "SFTP" -> ProviderType.SFTP
                        "SMB" -> ProviderType.SMB
                        else -> ProviderType.FTP
                    }
                    val normalizedPath = remotePath.ifBlank { "/" }
                    val crumbs = buildRemoteBreadcrumbs(profile, provider, normalizedPath)
                    val dirRef = crumbs.last().let {
                        if (it.uriOrPath == "/") it.copy(displayName = "${profile.displayName} (/)") else it
                    }
                    val currentSettings = settings.value
                    _currentDestination.value = OpenFileDestination.INTERNAL_STORAGE
                    _browserState.value = _browserState.value.copy(
                        currentDirectory = dirRef,
                        breadcrumbs = crumbs,
                        items = FileSortAndFilterEngine.sortFiles(
                            items,
                            currentSettings.defaultSortField,
                            currentSettings.defaultSortOrder
                        ),
                        isLoading = false,
                        errorMessage = null,
                        activeRemoteProfile = profile,
                        activeCategoryFilter = null,
                        searchQuery = "",
                        inspectArchiveFile = null,
                        archiveEntries = emptyList(),
                        currentSortField = currentSettings.defaultSortField,
                        currentSortOrder = currentSettings.defaultSortOrder
                    )
                },
                onFailure = { err ->
                    // Never leave the browser bound to a remote profile it could not actually open.
                    _browserState.value = _browserState.value.copy(
                        isLoading = false,
                        errorMessage = err.message,
                        activeRemoteProfile = previousRemoteProfile
                    )
                    if (err is SshHostKeyVerificationRequiredException) {
                        _sshHostKeyPrompt.value = SshHostKeyPrompt(
                            profile = profile,
                            keyType = err.keyType,
                            sha256Fingerprint = err.sha256Fingerprint,
                            isMismatchWarning = err.mismatchWithPinned
                        )
                    } else {
                        val reason = err.message ?: err::class.java.simpleName
                        val hint = if (isAuthenticationFailureMessage(reason)) {
                            " — saved password may have changed. Edit the server in Remote Connections to update it."
                        } else {
                            ""
                        }
                        showStatus("Connection failed: $reason$hint")
                    }
                    onConnectFailed(err)
                }
            )
        }
    }

    fun trustAndPinSshHostKey(prompt: SshHostKeyPrompt) {
        viewModelScope.launch {
            val updated = prompt.profile.copy(expectedHostKeyFingerprint = prompt.sha256Fingerprint)
            dao.upsertNetworkProfile(updated)
            _sshHostKeyPrompt.value = null
            showStatus("Pinned SSH host key (${prompt.sha256Fingerprint.take(22)}...)")
            openRemoteNetworkDirectory(updated, updated.remoteInitialPath)
        }
    }

    fun dismissSshHostKeyPrompt() {
        _sshHostKeyPrompt.value = null
    }

    fun downloadRemoteItemToDownloads(item: FileReference) {
        val profile = _browserState.value.activeRemoteProfile
        viewModelScope.launch {
            val downloadsDir = File(localProvider.getPrimaryRootDirectory(), "Download").apply {
                if (!exists()) mkdirs()
            }
            val destFile = File(downloadsDir, item.displayName)
            if (profile != null) {
                val secret = credentialVault.retrieveSecret(profile.secretAlias).orEmpty()
                showStatus("Downloading ${item.displayName}...")
                RemoteNetworkClients.downloadRemoteFile(profile, secret, item, destFile)
                    .onSuccess { showStatus("Downloaded to Download/${it.name}") }
                    .onFailure { showStatus("Download failed: ${it.message}") }
            }
        }
    }

    fun scanLanForServers() {
        viewModelScope.launch {
            _isScanningLan.value = true
            val lanIp = withContext(Dispatchers.IO) { FtpServerEngine.resolveLocalLanIpAddress() }
            val found = RemoteNetworkClients.scanLocalNetworkForFileServers(lanIp)
            _discoveredLanHosts.value = found
            _isScanningLan.value = false
            showStatus("LAN scan finished: ${found.size} service(s) found")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Settings & Privacy Actions (Optimistic 0ms UI update + background DataStore persistence)
    // ---------------------------------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) {
        _settings.value = _settings.value.copy(themeMode = mode)
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        _settings.value = _settings.value.copy(useDynamicColor = enabled)
        viewModelScope.launch { settingsStore.setDynamicColor(enabled) }
    }

    fun setViewMode(mode: ViewMode) {
        _settings.value = _settings.value.copy(defaultViewMode = mode)
        viewModelScope.launch { settingsStore.setViewMode(mode) }
    }

    fun setShowHiddenFiles(enabled: Boolean) {
        _settings.value = _settings.value.copy(showHiddenFiles = enabled)
        viewModelScope.launch {
            settingsStore.setShowHiddenFiles(enabled)
            refreshCategoryCountsAndPrewarm(force = true)
            val activeCategory = _browserState.value.activeCategoryFilter
            if (activeCategory != null) {
                openCategoryOrSpecialFolder(activeCategory)
            } else {
                refreshCurrentDirectory()
            }
        }
    }

    fun setShowThumbnails(enabled: Boolean) {
        _settings.value = _settings.value.copy(showThumbnails = enabled)
        viewModelScope.launch { settingsStore.setShowThumbnails(enabled) }
    }

    fun setConfirmBeforeDelete(enabled: Boolean) {
        _settings.value = _settings.value.copy(confirmBeforeDelete = enabled)
        viewModelScope.launch { settingsStore.setConfirmBeforeDelete(enabled) }
    }

    fun setMoveToTrashByDefault(enabled: Boolean) {
        _settings.value = _settings.value.copy(moveToTrashByDefault = enabled)
        viewModelScope.launch { settingsStore.setMoveToTrashByDefault(enabled) }
    }

    fun setVerifyChecksumAfterTransfer(enabled: Boolean) {
        _settings.value = _settings.value.copy(verifyChecksumAfterTransfer = enabled)
        viewModelScope.launch { settingsStore.setVerifyChecksumAfterTransfer(enabled) }
    }

    fun setSorting(field: SortField, order: SortOrder) {
        val isCategory = _browserState.value.activeCategoryFilter != null
        if (!isCategory) {
            _settings.value = _settings.value.copy(defaultSortField = field, defaultSortOrder = order)
            viewModelScope.launch { settingsStore.setSorting(field, order) }
        }
        viewModelScope.launch {
            val currentItems = _browserState.value.items
            if (currentItems.isNotEmpty()) {
                val resorted = withContext(Dispatchers.Default) {
                    FileSortAndFilterEngine.sortFiles(currentItems, field, order)
                }
                _browserState.value = _browserState.value.copy(
                    items = resorted,
                    currentSortField = field,
                    currentSortOrder = order
                )
            } else {
                _browserState.value = _browserState.value.copy(
                    currentSortField = field,
                    currentSortOrder = order
                )
            }
        }
    }

    fun clearAllPrivacyHistoryAndCredentials(clearVault: Boolean) {
        viewModelScope.launch {
            dao.clearSearchHistory()
            dao.clearRecentFiles()
            dao.clearFinishedTransferJobs()
            if (clearVault) {
                credentialVault.clearAllSecrets()
            }
            showStatus(if (clearVault) "Cleared history and purged Keystore vault" else "Cleared search & recent history")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // GitHub Releases Auto-Update Actions (https://github.com/sumon317/OpenFile/releases)
    // ---------------------------------------------------------------------------------------------

    fun setAutoCheckUpdates(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoCheckUpdates = enabled)
        viewModelScope.launch { settingsStore.setAutoCheckUpdates(enabled) }
    }

    fun openAppUpdateDialog() {
        _showAppUpdateDialog.value = true
    }

    fun dismissAppUpdateDialog() {
        _showAppUpdateDialog.value = false
    }

    fun checkForAppUpdates(manualUserTrigger: Boolean = true) {
        if (_appUpdateState.value.isChecking) return
        val (currentVersion, currentCode) = appUpdateEngine.getCurrentVersionInfo()
        _appUpdateState.value = _appUpdateState.value.copy(
            currentVersionName = currentVersion,
            currentVersionCode = currentCode,
            isChecking = true,
            errorMessage = null,
            statusMessage = "Checking GitHub Releases..."
        )
        viewModelScope.launch {
            appUpdateEngine.fetchLatestRelease().fold(
                onSuccess = { release ->
                    val isNewer = AppUpdateEngine.isNewerVersion(release.tagName, currentVersion)
                    _appUpdateState.value = _appUpdateState.value.copy(
                        isChecking = false,
                        updateAvailable = isNewer,
                        latestRelease = release,
                        lastCheckedEpochMs = System.currentTimeMillis(),
                        errorMessage = null,
                        statusMessage = if (isNewer) {
                            "Update available: ${release.tagName} (${release.apkAssetName})"
                        } else {
                            "You are on the latest version (v$currentVersion)"
                        }
                    )
                    if (isNewer) {
                        if (manualUserTrigger) {
                            _showAppUpdateDialog.value = true
                        }
                        showStatus("New update available: ${release.tagName}")
                    } else if (manualUserTrigger) {
                        showStatus("OpenFile v$currentVersion is up to date!")
                    }
                },
                onFailure = { err ->
                    val msg = err.message ?: "Unable to check GitHub Releases"
                    _appUpdateState.value = _appUpdateState.value.copy(
                        isChecking = false,
                        lastCheckedEpochMs = System.currentTimeMillis(),
                        errorMessage = msg,
                        statusMessage = msg
                    )
                    if (manualUserTrigger) {
                        showStatus("Update check: $msg")
                    }
                }
            )
        }
    }

    fun downloadAndInstallAppUpdate() {
        val release = _appUpdateState.value.latestRelease ?: return
        if (_appUpdateState.value.isDownloading) return

        val existingFile = _appUpdateState.value.downloadedApkFile
        if (existingFile != null && existingFile.exists() && existingFile.length() > 0L) {
            triggerApkInstallation(existingFile)
            return
        }

        updateDownloadJob?.cancel()
        _appUpdateState.value = _appUpdateState.value.copy(
            isDownloading = true,
            downloadProgressFraction = 0f,
            downloadedBytes = 0L,
            totalBytes = release.apkSizeBytes,
            errorMessage = null,
            statusMessage = "Downloading ${release.apkAssetName}..."
        )
        updateDownloadJob = viewModelScope.launch {
            appUpdateEngine.downloadReleaseApk(
                release = release,
                onProgress = { downloaded, total ->
                    val frac = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                    _appUpdateState.value = _appUpdateState.value.copy(
                        isDownloading = true,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        downloadProgressFraction = frac
                    )
                }
            ).fold(
                onSuccess = { apkFile ->
                    _appUpdateState.value = _appUpdateState.value.copy(
                        isDownloading = false,
                        downloadProgressFraction = 1f,
                        downloadedBytes = apkFile.length(),
                        totalBytes = apkFile.length(),
                        downloadedApkFile = apkFile,
                        statusMessage = "Downloaded ${release.apkAssetName}. Launching installer..."
                    )
                    triggerApkInstallation(apkFile)
                },
                onFailure = { err ->
                    val msg = err.message ?: "Download failed"
                    _appUpdateState.value = _appUpdateState.value.copy(
                        isDownloading = false,
                        errorMessage = msg,
                        statusMessage = "Download failed: $msg"
                    )
                    showStatus("Update download failed: $msg")
                }
            )
        }
    }

    fun triggerApkInstallation(apkFile: File? = _appUpdateState.value.downloadedApkFile) {
        val target = apkFile ?: return
        if (!target.exists()) {
            showStatus("Downloaded APK file not found; please download again.")
            _appUpdateState.value = _appUpdateState.value.copy(downloadedApkFile = null)
            return
        }
        if (!appUpdateEngine.canRequestPackageInstalls()) {
            _appUpdateState.value = _appUpdateState.value.copy(
                waitingForInstallPermission = true,
                statusMessage = "Grant 'Install unknown apps' permission to complete update."
            )
            showStatus("Allow 'Install unknown apps' for OpenFile to install the update")
            appUpdateEngine.openInstallUnknownAppsSettings()
            return
        }
        _appUpdateState.value = _appUpdateState.value.copy(waitingForInstallPermission = false)
        appUpdateEngine.launchPackageInstaller(target).onFailure { err ->
            showStatus("Failed to launch installer: ${err.message}")
        }
    }

    fun onResumeCheckPendingUpdateInstall() {
        val state = _appUpdateState.value
        val apkFile = state.downloadedApkFile
        if (state.waitingForInstallPermission && apkFile != null && apkFile.exists() && appUpdateEngine.canRequestPackageInstalls()) {
            _appUpdateState.value = state.copy(
                waitingForInstallPermission = false,
                statusMessage = "Permission granted! Launching package installer..."
            )
            appUpdateEngine.launchPackageInstaller(apkFile)
            return
        }

        // If the user returned to the app and the current installed version is now >= the version in the downloaded APK,
        // the installation succeeded -> delete the cached APK to keep app storage minimal.
        if (apkFile != null) {
            val (currentVName, _) = appUpdateEngine.getCurrentVersionInfo()
            val latestRel = state.latestRelease
            if (latestRel != null && !AppUpdateEngine.isNewerVersion(latestRel.tagName, currentVName)) {
                viewModelScope.launch(Dispatchers.IO) {
                    appUpdateEngine.cleanDownloadedUpdateApks()
                }
                _appUpdateState.value = state.copy(
                    downloadedApkFile = null,
                    updateAvailable = false,
                    statusMessage = "OpenFile is up to date (v$currentVName)."
                )
            }
        }
    }
}
