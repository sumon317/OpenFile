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
import com.example.core.cloud.CloudProviderKind
import com.example.core.cloud.CloudStorageEngine
import com.example.core.cloud.PkceSession
import com.example.core.database.BookmarkEntity
import com.example.core.database.CloudAccountEntity
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
import com.example.core.security.KeystoreCredentialVault
import com.example.core.storage.StorageAnalyzerEngine
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
    val activeRemoteProfile: NetworkProfileEntity? = null,
    val activeCloudAccount: CloudAccountEntity? = null
) {
    val isMultiSelectActive: Boolean get() = selectedUris.isNotEmpty()
}

data class SshHostKeyPrompt(
    val profile: NetworkProfileEntity,
    val keyType: String,
    val sha256Fingerprint: String,
    val isMismatchWarning: Boolean
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

    // Navigation state
    private val _currentDestination = MutableStateFlow(OpenFileDestination.HOME)
    val currentDestination: StateFlow<OpenFileDestination> = _currentDestination.asStateFlow()

    // Global feedback message (Toast / Snackbar)
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Reactive streams from Room & DataStore
    private val _settings = MutableStateFlow(AppSettingsState())
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

    val cloudAccounts: StateFlow<List<CloudAccountEntity>> = dao.observeCloudAccounts().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
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

    private val _volumeRefreshTrigger = MutableStateFlow(0L)
    fun refreshStorageVolumes() {
        _volumeRefreshTrigger.value = System.currentTimeMillis()
    }

    val storageVolumes: StateFlow<List<StorageVolumeInfo>> = combine(safGrants, settings, _volumeRefreshTrigger) { grants, _, _ ->
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
    private var searchDebounceJob: Job? = null

    // Preview & Properties modals
    private val _previewFile = MutableStateFlow<FileReference?>(null)
    val previewFile: StateFlow<FileReference?> = _previewFile.asStateFlow()

    private val _propertiesFile = MutableStateFlow<FileReference?>(null)
    val propertiesFile: StateFlow<FileReference?> = _propertiesFile.asStateFlow()

    private val _propertiesChecksums = MutableStateFlow<FileChecksums?>(null)
    val propertiesChecksums: StateFlow<FileChecksums?> = _propertiesChecksums.asStateFlow()

    private val _propertiesApkInfo = MutableStateFlow<ApkArchiveInfo?>(null)
    val propertiesApkInfo: StateFlow<ApkArchiveInfo?> = _propertiesApkInfo.asStateFlow()

    // Storage Analyzer state
    private val _analysisReport = MutableStateFlow<StorageAnalysisReport?>(null)
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

    // Cloud PKCE session state & Direct In-App Web Cloud Login/Portal state
    private val _activePkceSession = MutableStateFlow<PkceSession?>(null)
    val activePkceSession: StateFlow<PkceSession?> = _activePkceSession.asStateFlow()

    private val _activeWebCloudProvider = MutableStateFlow<CloudProviderKind?>(null)
    val activeWebCloudProvider: StateFlow<CloudProviderKind?> = _activeWebCloudProvider.asStateFlow()

    private val _activeWebCloudUrl = MutableStateFlow<String?>(null)
    val activeWebCloudUrl: StateFlow<String?> = _activeWebCloudUrl.asStateFlow()

    // Native Google Drive FilePicker Modal state
    private val _showDriveFilePickerModal = MutableStateFlow(false)
    val showDriveFilePickerModal: StateFlow<Boolean> = _showDriveFilePickerModal.asStateFlow()

    private val _activeDrivePickerAccount = MutableStateFlow<CloudAccountEntity?>(null)
    val activeDrivePickerAccount: StateFlow<CloudAccountEntity?> = _activeDrivePickerAccount.asStateFlow()

    // File Manager + Home Category Counts & Instant Pre-warmed Category Cache
    private val _categoryCounts = MutableStateFlow<Map<FileCategory, Int>>(emptyMap())
    val categoryCounts: StateFlow<Map<FileCategory, Int>> = _categoryCounts.asStateFlow()
    private val categoryCache = java.util.concurrent.ConcurrentHashMap<FileCategory, List<FileReference>>()

    // Active tab inside NetworkScreen (0 = Access from PC / Wi-Fi FTP Server, 1 = Remote Connections)
    private val _selectedNetworkTab = MutableStateFlow(0)
    val selectedNetworkTab: StateFlow<Int> = _selectedNetworkTab.asStateFlow()

    init {
        CloudStorageEngine.initContext(context)
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { persisted ->
                _settings.value = persisted
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            // Crash recovery: mark any stale RUNNING transfer jobs from a previous process death as INTERRUPTED
            dao.markRunningJobsAsInterrupted()
            if (_browserState.value.currentDirectory == null && browseDirectoryJob == null) {
                openPrimaryStorageRoot()
            }
            refreshCategoryCountsAndPrewarm()
        }
    }

    fun refreshCategoryCountsAndPrewarm() {
        viewModelScope.launch(Dispatchers.IO) {
            val thresholdBytes = _settings.value.largeFileThresholdMb * 1024L * 1024L
            _categoryCounts.value = mediaStoreProvider.queryCategoryCounts(thresholdBytes)
            // Pre-warm common categories in the background so tapping them on Home opens in 0ms
            listOf(
                FileCategory.IMAGES,
                FileCategory.VIDEOS,
                FileCategory.AUDIO,
                FileCategory.DOCUMENTS,
                FileCategory.ARCHIVES,
                FileCategory.LARGE_FILES,
                FileCategory.RECENT_FILES
            ).forEach { cat ->
                runCatching {
                    val items = mediaStoreProvider.queryByCategory(cat, thresholdBytes)
                    categoryCache[cat] = items
                }
            }
        }
    }

    fun openNetworkTab(tabIndex: Int) {
        _selectedNetworkTab.value = tabIndex
        _currentDestination.value = OpenFileDestination.NETWORK
    }

    fun showStatus(message: String) {
        _statusMessage.value = message
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
        if (directory.providerType in setOf(ProviderType.GOOGLE_DRIVE, ProviderType.ONEDRIVE, ProviderType.DROPBOX)) {
            val activeAcc = _browserState.value.activeCloudAccount
                ?: directory.metadata["accountId"]?.toLongOrNull()?.let { id ->
                    cloudAccounts.value.firstOrNull { it.id == id }
                }
                ?: cloudAccounts.value.firstOrNull { it.provider == directory.providerType.name }
            if (activeAcc != null) {
                openCloudDirectory(activeAcc, directory.uriOrPath)
                return
            }
        }
        if (directory.providerType in setOf(ProviderType.FTP, ProviderType.FTPS, ProviderType.SFTP, ProviderType.SMB)) {
            val activeProf = _browserState.value.activeRemoteProfile
            if (activeProf != null) {
                openRemoteNetworkDirectory(activeProf, directory.uriOrPath)
                return
            }
        }

        browseDirectoryJob?.cancel()
        _browserState.value = _browserState.value.copy(
            currentDirectory = directory,
            selectedUris = emptySet(),
            searchQuery = "",
            isLoading = true,
            errorMessage = null,
            activeCategoryFilter = null,
            inspectArchiveFile = null,
            archiveEntries = emptyList(),
            activeRemoteProfile = null,
            activeCloudAccount = null
        )
        browseDirectoryJob = viewModelScope.launch {
            val currentSettings = settings.value
            val crumbs = withContext(Dispatchers.IO) { buildBreadcrumbs(directory) }
            val result = when (directory.providerType) {
                ProviderType.SAF -> safProvider.listChildren(directory, currentSettings.showHiddenFiles)
                else -> localProvider.listChildren(directory, currentSettings.showHiddenFiles)
            }
            result.fold(
                onSuccess = { rawItems ->
                    val sorted = withContext(Dispatchers.Default) {
                        FileSortAndFilterEngine.sortFiles(
                            files = rawItems,
                            field = currentSettings.defaultSortField,
                            order = currentSettings.defaultSortOrder
                        )
                    }
                    _browserState.value = _browserState.value.copy(
                        currentDirectory = directory,
                        breadcrumbs = crumbs,
                        items = sorted,
                        isLoading = false,
                        errorMessage = null
                    )
                    // Non-blocking background pass to enrich local folder rows with child counts (File Manager + style)
                    if (directory.providerType == ProviderType.LOCAL && sorted.any { it.isDirectory }) {
                        val enriched = withContext(Dispatchers.IO) {
                            sorted.map { item ->
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
                },
                onFailure = { err ->
                    _browserState.value = _browserState.value.copy(
                        currentDirectory = directory,
                        breadcrumbs = crumbs,
                        isLoading = false,
                        errorMessage = err.message ?: "Unable to read directory"
                    )
                }
            )
        }
    }

    fun refreshCurrentDirectory() {
        val state = _browserState.value
        when {
            state.activeRemoteProfile != null -> {
                val path = state.currentDirectory?.uriOrPath ?: state.activeRemoteProfile.remoteInitialPath
                openRemoteNetworkDirectory(state.activeRemoteProfile, path)
            }
            state.activeCloudAccount != null -> {
                val folderId = state.currentDirectory?.uriOrPath ?: "root"
                openCloudDirectory(state.activeCloudAccount, folderId)
            }
            state.activeCategoryFilter != null -> {
                openCategoryOrSpecialFolder(state.activeCategoryFilter)
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
            _browserState.value = state.copy(inspectArchiveFile = null, archiveEntries = emptyList())
            return true
        }
        val current = state.currentDirectory ?: return false
        val parentPath = current.parentUriOrPath

        if (state.activeRemoteProfile != null) {
            if (current.uriOrPath == "/" || current.uriOrPath == state.activeRemoteProfile.remoteInitialPath || parentPath == null) return false
            openRemoteNetworkDirectory(state.activeRemoteProfile, parentPath)
            return true
        }

        if (state.activeCloudAccount != null) {
            val parentCrumbs = state.breadcrumbs.dropLast(1)
            if (parentCrumbs.isNotEmpty()) {
                openCloudDirectory(state.activeCloudAccount, parentCrumbs.last().uriOrPath)
                return true
            }
            if (current.uriOrPath != "root" && current.uriOrPath != "/" && !parentPath.isNullOrBlank()) {
                openCloudDirectory(state.activeCloudAccount, parentPath)
                return true
            }
            return false
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

    fun openCategoryOrSpecialFolder(category: FileCategory) {
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
        val cached = categoryCache[category]
        _browserState.value = _browserState.value.copy(
            currentDirectory = null,
            breadcrumbs = emptyList(),
            items = cached ?: emptyList(),
            isLoading = cached == null,
            selectedUris = emptySet(),
            searchQuery = "",
            activeCategoryFilter = category,
            inspectArchiveFile = null,
            activeRemoteProfile = null,
            activeCloudAccount = null,
            errorMessage = null
        )
        browseDirectoryJob = viewModelScope.launch {
            val currentSettings = settings.value
            val thresholdBytes = currentSettings.largeFileThresholdMb * 1024L * 1024L
            val indexedItems = mediaStoreProvider.queryByCategory(category, thresholdBytes)
            categoryCache[category] = indexedItems
            val combinedItems = if (category == FileCategory.RECENT_FILES) {
                withContext(Dispatchers.IO) {
                    val openedRefs = recentFiles.value.mapNotNull { rec ->
                        val f = File(rec.uriOrPath)
                        if (f.exists() && f.canRead()) localProvider.toFileReference(f) else null
                    }
                    (openedRefs + indexedItems).distinctBy { it.uriOrPath }
                }
            } else {
                indexedItems
            }
            val sorted = withContext(Dispatchers.Default) {
                FileSortAndFilterEngine.sortFiles(
                    combinedItems,
                    if (category == FileCategory.LARGE_FILES) SortField.SIZE else currentSettings.defaultSortField,
                    if (category == FileCategory.LARGE_FILES) SortOrder.DESCENDING else currentSettings.defaultSortOrder
                )
            }
            _browserState.value = _browserState.value.copy(
                currentDirectory = null,
                breadcrumbs = emptyList(),
                items = sorted,
                isLoading = false
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
            } else if (state.activeCloudAccount != null) {
                openCloudDirectory(state.activeCloudAccount, item.uriOrPath)
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

        // 1. Check if supported archive (ZIP, TAR, GZ, XZ, 7Z) -> open built-in archive inspector
        val isCloudItem = item.providerType in setOf(ProviderType.GOOGLE_DRIVE, ProviderType.ONEDRIVE, ProviderType.DROPBOX)
        if (item.extension.lowercase() in FileCategory.ARCHIVES.extensions && (item.providerType == ProviderType.LOCAL || isCloudItem)) {
            inspectArchive(item)
            return
        }

        // 2. Check if APK -> trigger Android's native PackageInstaller directly
        if (item.extension.lowercase() == "apk" && item.providerType == ProviderType.LOCAL) {
            appManager.installApkWithSystemInstaller(File(item.uriOrPath))
                .onFailure { openPropertiesSheet(item) }
            return
        }

        // 3. For Cloud Drive files, resolve local/cached file and open in built-in previewer or external app
        if (isCloudItem) {
            openBuiltInPreview(item)
            return
        }

        // 4. For Remote Network files (FTP/SFTP/SMB), show built-in preview dialog
        if (item.providerType != ProviderType.LOCAL && item.providerType != ProviderType.MEDIA_STORE && item.providerType != ProviderType.SAF) {
            _previewFile.value = item
            return
        }

        // 5. Standard File Manager behavior: launch external / system handler (Video player, PDF viewer, Gallery, Music player, Office app, etc.)
        // Falls back to built-in previewer ONLY if no external app is installed for this file type.
        openWithExternalApp(item, forceChooser = false, fallbackToBuiltInPreview = true)
    }

    fun openBuiltInPreview(item: FileReference) {
        if (item.providerType in setOf(ProviderType.GOOGLE_DRIVE, ProviderType.ONEDRIVE, ProviderType.DROPBOX)) {
            viewModelScope.launch {
                val acc = resolveCloudAccountForRef(item)
                if (acc != null) {
                    val token = credentialVault.retrieveSecret(acc.tokenSecretAlias).orEmpty()
                    CloudStorageEngine.resolveLocalFileForPreviewOrOpen(acc, token, item, context)
                        .onSuccess { localFile ->
                            _previewFile.value = item.copy(
                                metadata = item.metadata + ("localBackingPath" to localFile.absolutePath)
                            )
                        }
                        .onFailure {
                            _previewFile.value = item
                        }
                } else {
                    _previewFile.value = item
                }
            }
            return
        }
        _previewFile.value = item
    }

    fun closePreviewDialog() {
        _previewFile.value = null
    }

    fun saveEditedTextFile(item: FileReference, newContent: String) {
        viewModelScope.launch {
            val isCloud = item.providerType in setOf(ProviderType.GOOGLE_DRIVE, ProviderType.ONEDRIVE, ProviderType.DROPBOX)
            if (isCloud) {
                val acc = resolveCloudAccountForRef(item)
                if (acc != null) {
                    val token = credentialVault.retrieveSecret(acc.tokenSecretAlias).orEmpty()
                    CloudStorageEngine.writeCloudTextContent(acc, token, item, newContent, context)
                        .onSuccess { updated ->
                            _previewFile.value = updated
                            showStatus("Saved changes to '${item.displayName}' in ${acc.displayName}")
                            refreshCurrentDirectory()
                        }
                        .onFailure { err ->
                            showStatus("Cloud save failed: ${err.message}")
                        }
                }
                return@launch
            }
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

    private suspend fun resolveCloudAccountForRef(item: FileReference): CloudAccountEntity? {
        _browserState.value.activeCloudAccount?.let { return it }
        val accountId = item.metadata["accountId"]?.toLongOrNull()
        if (accountId != null && accountId > 0L) {
            dao.getCloudAccountById(accountId)?.let { return it }
        }
        return dao.getAllCloudAccounts().firstOrNull { it.provider == item.providerType.name }
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
        if (item.providerType in setOf(ProviderType.GOOGLE_DRIVE, ProviderType.ONEDRIVE, ProviderType.DROPBOX)) {
            viewModelScope.launch {
                val acc = resolveCloudAccountForRef(item)
                if (acc != null) {
                    val token = credentialVault.retrieveSecret(acc.tokenSecretAlias).orEmpty()
                    CloudStorageEngine.resolveLocalFileForPreviewOrOpen(acc, token, item, context)
                        .onSuccess { localFile ->
                            appManager.shareApkOrFile(localFile, item.mimeType)
                                .onFailure { showStatus("Share failed: ${it.message}") }
                        }
                        .onFailure { showStatus("Share failed: ${it.message}") }
                }
            }
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
        val cloudAcc = _browserState.value.activeCloudAccount

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
            if (cloudAcc != null) {
                val token = credentialVault.retrieveSecret(cloudAcc.tokenSecretAlias).orEmpty()
                CloudStorageEngine.createCloudFolder(cloudAcc, token, currentDir.uriOrPath, folderName, context)
                    .onSuccess {
                        showStatus("Created folder '$folderName' in ${cloudAcc.displayName}")
                        refreshCurrentDirectory()
                    }
                    .onFailure { showStatus("Cloud folder creation failed: ${it.message}") }
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
        val cloudAcc = _browserState.value.activeCloudAccount
        viewModelScope.launch {
            if (cloudAcc != null) {
                val token = credentialVault.retrieveSecret(cloudAcc.tokenSecretAlias).orEmpty()
                CloudStorageEngine.createCloudTextFile(cloudAcc, token, currentDir.uriOrPath, fileName, content, context)
                    .onSuccess {
                        showStatus("Created file '${it.displayName}' in ${cloudAcc.displayName}")
                        refreshCurrentDirectory()
                    }
                    .onFailure { showStatus("Cloud file creation failed: ${it.message}") }
                return@launch
            }
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
        val cloudAcc = _browserState.value.activeCloudAccount
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
            if (cloudAcc != null) {
                val token = credentialVault.retrieveSecret(cloudAcc.tokenSecretAlias).orEmpty()
                CloudStorageEngine.renameCloudItem(cloudAcc, token, target, newName, context)
                    .onSuccess {
                        showStatus("Renamed to '${it.displayName}' in ${cloudAcc.displayName}")
                        clearSelection()
                        refreshCurrentDirectory()
                    }
                    .onFailure { showStatus("Cloud rename failed: ${it.message}") }
                return@launch
            }

            val provider = if (target.providerType == ProviderType.SAF) safProvider else localProvider
            provider.rename(target, newName)
                .onSuccess {
                    showStatus("Renamed to '${it.displayName}'")
                    clearSelection()
                    refreshCurrentDirectory()
                }
                .onFailure { showStatus(it.message ?: "Rename failed") }
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
        val cloudAcc = _browserState.value.activeCloudAccount
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
            if (cloudAcc != null) {
                val token = credentialVault.retrieveSecret(cloudAcc.tokenSecretAlias).orEmpty()
                var deleted = 0
                for (item in items) {
                    CloudStorageEngine.deleteCloudItem(cloudAcc, token, item, context)
                        .onSuccess { deleted++ }
                        .onFailure { showStatus("Cloud delete failed: ${it.message}") }
                }
                clearSelection()
                showStatus("Deleted $deleted item(s) from ${cloudAcc.displayName}")
                refreshCurrentDirectory()
                return@launch
            }

            transferEngine.deleteItems(items, moveToTrash)
                .onSuccess { msg ->
                    clearSelection()
                    showStatus(msg)
                    refreshCurrentDirectory()
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
                    showStatus(msg)
                    refreshCurrentDirectory()
                }
                .onFailure { err ->
                    showStatus("Undo failed: ${err.message}")
                }
        }
    }

    fun openPropertiesSheet(item: FileReference) {
        _propertiesFile.value = item
        _propertiesChecksums.value = null
        _propertiesApkInfo.value = null

        viewModelScope.launch {
            if (item.providerType == ProviderType.LOCAL) {
                val file = File(item.uriOrPath)
                val detailedRef = localProvider.toFileReference(
                    file = file,
                    includeChildCount = true,
                    includeCanonicalPath = true
                )
                if (file.isDirectory && file.canRead()) {
                    val dirSize = localProvider.calculateDirectorySize(file)
                    _propertiesFile.value = detailedRef.copy(sizeBytes = dirSize)
                } else if (file.isFile && file.canRead()) {
                    _propertiesFile.value = detailedRef
                    if (item.extension == "apk") {
                        _propertiesApkInfo.value = appManager.inspectApkFile(file).getOrNull()
                    }
                    if (file.length() <= 250L * 1024 * 1024) {
                        val hashes = runCatching {
                            file.inputStream().use { ChecksumAndDuplicateEngine.computeChecksums(it) }
                        }.getOrNull()
                        _propertiesChecksums.value = hashes
                    }
                }
            } else if (item.providerType in setOf(ProviderType.GOOGLE_DRIVE, ProviderType.ONEDRIVE, ProviderType.DROPBOX)) {
                val acc = resolveCloudAccountForRef(item) ?: return@launch
                val token = credentialVault.retrieveSecret(acc.tokenSecretAlias).orEmpty()
                CloudStorageEngine.resolveLocalFileForPreviewOrOpen(acc, token, item, context).onSuccess { file ->
                    if (file.isDirectory && file.canRead()) {
                        val dirSize = localProvider.calculateDirectorySize(file)
                        _propertiesFile.value = item.copy(sizeBytes = dirSize)
                    } else if (file.isFile && file.canRead() && file.length() <= 250L * 1024 * 1024) {
                        val hashes = runCatching {
                            file.inputStream().use { ChecksumAndDuplicateEngine.computeChecksums(it) }
                        }.getOrNull()
                        _propertiesChecksums.value = hashes
                    }
                }
            }
        }
    }

    fun closePropertiesSheet() {
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
                val uri = Uri.parse(bm.uriOrPath)
                val ref = safProvider.fromTreeUri(uri, bm.title)
                if (ref.isDirectory) {
                    _currentDestination.value = OpenFileDestination.INTERNAL_STORAGE
                    openDirectory(ref)
                } else {
                    onFileItemClicked(ref)
                }
            }
            ProviderType.GOOGLE_DRIVE.name, ProviderType.ONEDRIVE.name, ProviderType.DROPBOX.name -> {
                viewModelScope.launch {
                    val acc = dao.getAllCloudAccounts().firstOrNull { it.provider == bm.providerType }
                    if (acc != null) {
                        if (bm.isDirectory) {
                            openCloudDirectory(acc, bm.uriOrPath)
                        } else {
                            openBuiltInPreview(
                                FileReference(
                                    providerType = ProviderType.valueOf(bm.providerType),
                                    uriOrPath = bm.uriOrPath,
                                    displayName = bm.title,
                                    parentUriOrPath = "root",
                                    isDirectory = false,
                                    mimeType = bm.mimeType,
                                    metadata = mapOf("accountId" to acc.id.toString())
                                )
                            )
                        }
                    } else {
                        showStatus("Connect ${bm.providerType} first to open '${bm.title}'.")
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
            if (recent.providerType in setOf(ProviderType.GOOGLE_DRIVE.name, ProviderType.ONEDRIVE.name, ProviderType.DROPBOX.name)) {
                val acc = dao.getAllCloudAccounts().firstOrNull { it.provider == recent.providerType }
                if (acc != null) {
                    openBuiltInPreview(
                        FileReference(
                            providerType = ProviderType.valueOf(recent.providerType),
                            uriOrPath = recent.uriOrPath,
                            displayName = recent.displayName,
                            parentUriOrPath = recent.parentUriOrPath ?: "root",
                            isDirectory = false,
                            sizeBytes = recent.sizeBytes,
                            lastModifiedEpochMs = recent.lastModifiedAt,
                            mimeType = recent.mimeType,
                            metadata = mapOf("accountId" to acc.id.toString())
                        )
                    )
                    return@launch
                }
            }
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

    fun onSafTreeGranted(treeUri: Uri) {
        viewModelScope.launch {
            val label = safProvider.persistTreePermission(treeUri)
            dao.upsertSafGrant(
                SafGrantEntity(
                    treeUriString = treeUri.toString(),
                    displayName = label,
                    isPermissionValid = true
                )
            )
            val ref = safProvider.fromTreeUri(treeUri, label)
            _currentDestination.value = OpenFileDestination.INTERNAL_STORAGE
            openDirectory(ref)
            showStatus("Granted SAF access to '$label'")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Archive Management (Browse, Extract, Create, Verify)
    // ---------------------------------------------------------------------------------------------

    fun inspectArchive(archiveRef: FileReference) {
        viewModelScope.launch {
            _browserState.value = _browserState.value.copy(isLoading = true, errorMessage = null)
            val file = if (archiveRef.providerType in setOf(ProviderType.GOOGLE_DRIVE, ProviderType.ONEDRIVE, ProviderType.DROPBOX)) {
                val acc = resolveCloudAccountForRef(archiveRef)
                val token = acc?.let { credentialVault.retrieveSecret(it.tokenSecretAlias).orEmpty() }.orEmpty()
                if (acc != null) {
                    CloudStorageEngine.resolveLocalFileForPreviewOrOpen(acc, token, archiveRef, context).getOrNull()
                } else null
            } else {
                File(archiveRef.uriOrPath)
            }
            if (file == null || !file.exists()) {
                _browserState.value = _browserState.value.copy(isLoading = false)
                showStatus("Cannot locate archive file: ${archiveRef.displayName}")
                return@launch
            }
            ArchiveEngine.listArchiveEntries(file).fold(
                onSuccess = { entries ->
                    _browserState.value = _browserState.value.copy(
                        inspectArchiveFile = archiveRef,
                        archiveEntries = entries,
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

    fun extractArchiveToFolder(
        archiveRef: FileReference,
        selectedEntries: Set<String>? = null
    ) {
        viewModelScope.launch {
            val cloudAcc = _browserState.value.activeCloudAccount
            val archiveFile = if (cloudAcc != null) {
                val token = credentialVault.retrieveSecret(cloudAcc.tokenSecretAlias).orEmpty()
                CloudStorageEngine.resolveLocalFileForPreviewOrOpen(cloudAcc, token, archiveRef, context).getOrNull()
            } else {
                File(archiveRef.uriOrPath)
            }
            if (archiveFile == null || !archiveFile.exists()) {
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
                    if (cloudAcc != null) {
                        refreshCurrentDirectory()
                    } else {
                        openDirectory(localProvider.toFileReference(destDir))
                    }
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
        val currentDir = _browserState.value.currentDirectory ?: return
        val cloudAcc = _browserState.value.activeCloudAccount
        val selected = getSelectedFileReferences()
        if (selected.isEmpty()) {
            showStatus("Select at least one file or folder to compress.")
            return
        }
        viewModelScope.launch {
            val cleanBase = archiveNameWithoutExt.trim().ifEmpty { "Archive_${System.currentTimeMillis()}" }
            if (cloudAcc != null) {
                val token = credentialVault.retrieveSecret(cloudAcc.tokenSecretAlias).orEmpty()
                val sources = selected.mapNotNull { item ->
                    CloudStorageEngine.resolveLocalFileForPreviewOrOpen(cloudAcc, token, item, context).getOrNull()
                }
                if (sources.isEmpty()) {
                    showStatus("Unable to resolve cloud files for compression.")
                    return@launch
                }
                val parentLocalDir = if (!CloudStorageEngine.isLiveOAuthSession(cloudAcc, token)) {
                    CloudStorageEngine.resolveNativeVfsFile(cloudAcc, currentDir.uriOrPath, context)
                } else {
                    context.cacheDir
                }
                val outFile = File(parentLocalDir, "$cleanBase.${format.extension}")
                showStatus("Creating ${outFile.name} in ${cloudAcc.displayName}...")
                ArchiveEngine.createArchive(
                    sourceFiles = sources,
                    outputArchiveFile = outFile,
                    format = format
                ).fold(
                    onSuccess = { created ->
                        if (CloudStorageEngine.isLiveOAuthSession(cloudAcc, token)) {
                            CloudStorageEngine.uploadCloudFile(
                                account = cloudAcc,
                                accessToken = token,
                                parentIdOrPath = currentDir.uriOrPath,
                                localSource = created,
                                targetFileName = created.name,
                                context = context
                            )
                            created.delete()
                        }
                        clearSelection()
                        showStatus("Created archive ${created.name} in ${cloudAcc.displayName}")
                        refreshCurrentDirectory()
                    },
                    onFailure = { err ->
                        showStatus("Compression failed: ${err.message}")
                    }
                )
                return@launch
            }

            val localSelected = selected.filter { it.providerType == ProviderType.LOCAL }
            if (localSelected.isEmpty()) {
                showStatus("Select at least one local file or folder to compress.")
                return@launch
            }
            val outFile = File(currentDir.uriOrPath, "$cleanBase.${format.extension}")
            val sources = localSelected.map { File(it.uriOrPath) }
            showStatus("Creating ${outFile.name}...")
            ArchiveEngine.createArchive(
                sourceFiles = sources,
                outputArchiveFile = outFile,
                format = format
            ).fold(
                onSuccess = { created ->
                    clearSelection()
                    showStatus("Created archive ${created.name}")
                    refreshCurrentDirectory()
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

    fun runStorageAnalysis(targetRoot: File = localProvider.getPrimaryRootDirectory()) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _isAnalyzingStorage.value = true
            _analysisProgressText.value = "Scanning ${targetRoot.name}..."
            storageAnalyzer.analyzeDirectory(
                rootDir = targetRoot,
                includeDuplicateScan = true,
                onProgress = { count, _, name ->
                    _analysisProgressText.value = "Scanned $count files ($name)"
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

    fun startLocalFtpServer(passwordPlain: String) {
        viewModelScope.launch {
            val cfg = settings.first()
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
                val root = cfg.ftpRootPath.ifBlank { localProvider.getPrimaryRootDirectory().absolutePath }
                Triple(alias, resolved, root)
            }

            if (!cfg.ftpAnonymousAllowed && resolvedPassword.isBlank()) {
                showStatus("Set an FTP password or enable Anonymous mode first.")
                return@launch
            }

            settingsStore.updateFtpServerConfig(
                port = cfg.ftpPort,
                passiveStart = cfg.ftpPassivePortStart,
                passiveEnd = cfg.ftpPassivePortEnd,
                username = cfg.ftpUsername,
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
                putExtra(FtpServerForegroundService.EXTRA_USERNAME, cfg.ftpUsername)
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
        usePassiveMode: Boolean
    ) {
        viewModelScope.launch {
            val alias = if (!isAnonymous && passwordOrPrivateKey.isNotBlank()) {
                credentialVault.storeSecret(null, passwordOrPrivateKey)
            } else null

            dao.upsertNetworkProfile(
                NetworkProfileEntity(
                    displayName = displayName.trim().ifEmpty { "$protocol://$host:$port" },
                    protocol = protocol,
                    host = host.trim(),
                    port = port,
                    username = username.trim(),
                    secretAlias = alias,
                    remoteInitialPath = remoteInitialPath.trim().ifEmpty { "/" },
                    shareName = shareName.trim(),
                    isAnonymous = isAnonymous,
                    usePassiveMode = usePassiveMode
                )
            )
            showStatus("Saved network profile '$displayName'")
        }
    }

    fun deleteNetworkProfile(profile: NetworkProfileEntity) {
        viewModelScope.launch {
            credentialVault.deleteSecret(profile.secretAlias)
            dao.deleteNetworkProfile(profile.id)
            showStatus("Deleted profile '${profile.displayName}'")
        }
    }

    fun openRemoteNetworkDirectory(profile: NetworkProfileEntity, remotePath: String = profile.remoteInitialPath) {
        viewModelScope.launch {
            _browserState.value = _browserState.value.copy(
                isLoading = true,
                errorMessage = null,
                activeRemoteProfile = profile,
                activeCloudAccount = null,
                selectedUris = emptySet()
            )
            val secret = credentialVault.retrieveSecret(profile.secretAlias).orEmpty()
            RemoteNetworkClients.listRemoteDirectory(profile, secret, remotePath).fold(
                onSuccess = { items ->
                    val provider = when (profile.protocol.uppercase()) {
                        "FTPS" -> ProviderType.FTPS
                        "SFTP" -> ProviderType.SFTP
                        "SMB" -> ProviderType.SMB
                        else -> ProviderType.FTP
                    }
                    val dirRef = FileReference(
                        providerType = provider,
                        uriOrPath = remotePath,
                        displayName = "${profile.displayName} ($remotePath)",
                        parentUriOrPath = remotePath.trimEnd('/').substringBeforeLast('/').ifEmpty { "/" },
                        isDirectory = true
                    )
                    _currentDestination.value = OpenFileDestination.INTERNAL_STORAGE
                    _browserState.value = _browserState.value.copy(
                        currentDirectory = dirRef,
                        breadcrumbs = listOf(dirRef),
                        items = FileSortAndFilterEngine.sortFiles(items, SortField.NAME, SortOrder.ASCENDING),
                        isLoading = false
                    )
                },
                onFailure = { err ->
                    _browserState.value = _browserState.value.copy(
                        isLoading = false,
                        errorMessage = err.message
                    )
                    if (err is SshHostKeyVerificationRequiredException) {
                        _sshHostKeyPrompt.value = SshHostKeyPrompt(
                            profile = profile,
                            keyType = err.keyType,
                            sha256Fingerprint = err.sha256Fingerprint,
                            isMismatchWarning = err.mismatchWithPinned
                        )
                    } else {
                        showStatus("Connection failed: ${err.message}")
                    }
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
        val cloudAcc = _browserState.value.activeCloudAccount
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
            } else if (cloudAcc != null) {
                val token = credentialVault.retrieveSecret(cloudAcc.tokenSecretAlias).orEmpty()
                showStatus("Downloading ${item.displayName} from ${cloudAcc.provider}...")
                CloudStorageEngine.downloadCloudFile(cloudAcc, token, item, destFile)
                    .onSuccess { showStatus("Downloaded to Download/${it.name}") }
                    .onFailure { showStatus("Cloud download failed: ${it.message}") }
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
    // Cloud Storage OAuth 2.0 PKCE, Default Browser Login & Account Management
    // ---------------------------------------------------------------------------------------------

    /**
     * Launches the user's default external browser (`Intent.ACTION_VIEW`) to sign in to the cloud provider.
     * Never uses an embedded in-app WebView, so the user benefits from their existing default browser
     * sessions, saved credentials, and passkeys.
     */
    fun openUrlInDefaultBrowser(url: String): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(intent)
            true
        }.onFailure { err ->
            showStatus("Unable to open default browser: ${err.message}")
        }.getOrDefault(false)
    }

    /**
     * 1-tap Cloud Connect & Native Drive Access:
     * - If the provider is already connected, opens its native in-app Drive browser immediately.
     * - If not yet connected, launches browser authentication (`Intent.ACTION_VIEW`) and mounts
     *   the native in-app Drive workspace so the user browses and manages Drive files natively inside OpenFile.
     */
    fun connectCloudProviderDirect(
        provider: CloudProviderKind,
        loginHint: String? = null,
        forceBrowserAuth: Boolean = false
    ) {
        val effectiveHint = loginHint?.trim()?.takeIf { it.isNotBlank() }
            ?: if (provider == CloudProviderKind.GOOGLE_DRIVE) {
                "container6767@gmail.com"
            } else {
                null
            }

        val existingAccount = cloudAccounts.value.firstOrNull { it.provider == provider.name }
        if (existingAccount != null && !forceBrowserAuth) {
            openCloudDirectory(existingAccount, "root")
            return
        }

        val configuredClientId = provider.defaultClientIdFromBuildConfig()
        if (configuredClientId.isNotBlank()) {
            startCloudOAuthFlow(provider, configuredClientId, effectiveHint).onSuccess { authUrl ->
                _activeWebCloudProvider.value = provider
                _activeWebCloudUrl.value = authUrl
                openUrlInDefaultBrowser(authUrl)
                showStatus("Authenticate ${provider.displayName} in browser to return to native Drive...")
                return
            }
        }

        val browserUrl = provider.defaultBrowserLoginUrl(effectiveHint)
        _activeWebCloudProvider.value = provider
        _activeWebCloudUrl.value = browserUrl
        viewModelScope.launch {
            val connectedAccount = upsertBrowserAuthenticatedCloudAccount(
                provider = provider,
                detectedLabelOrEmail = effectiveHint ?: "${provider.displayName} Account"
            )
            // Mount and open the native in-app Drive view immediately so returning from browser lands directly in Drive
            openCloudDirectory(connectedAccount, "root")
            openUrlInDefaultBrowser(browserUrl)
            showStatus("Sign in via browser — ${provider.displayName} is now mounted natively in OpenFile")
        }
    }

    fun reauthenticateCloudProviderInBrowser(provider: CloudProviderKind, loginHint: String? = null) {
        connectCloudProviderDirect(provider = provider, loginHint = loginHint, forceBrowserAuth = true)
    }

    private suspend fun upsertBrowserAuthenticatedCloudAccount(
        provider: CloudProviderKind,
        detectedLabelOrEmail: String?
    ): CloudAccountEntity {
        val cleanLabel = detectedLabelOrEmail
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "${provider.displayName} Account"
        val existing = cloudAccounts.value.firstOrNull {
            it.provider == provider.name
        } ?: dao.getAllCloudAccounts().firstOrNull {
            it.provider == provider.name
        }
        val alias = credentialVault.storeSecret(existing?.tokenSecretAlias, "DEFAULT_BROWSER_SESSION")
        val entity = CloudAccountEntity(
            id = existing?.id ?: 0L,
            provider = provider.name,
            accountEmail = cleanLabel,
            displayName = provider.displayName,
            clientId = CloudStorageEngine.WEB_SESSION_CLIENT_MARKER,
            tokenSecretAlias = alias,
            refreshTokenSecretAlias = null,
            tokenExpiresAt = Long.MAX_VALUE
        )
        val rowId = dao.upsertCloudAccount(entity)
        val saved = entity.copy(id = if (entity.id != 0L) entity.id else rowId)
        if (provider == CloudProviderKind.GOOGLE_DRIVE) {
            _activeDrivePickerAccount.value = saved
        }
        return saved
    }

    /**
     * Registers or updates a cloud account connected via the user's default browser
     * and opens it in the native in-app file browser.
     */
    fun onWebCloudAuthenticated(provider: CloudProviderKind, detectedLabelOrEmail: String?) {
        viewModelScope.launch {
            val account = upsertBrowserAuthenticatedCloudAccount(provider, detectedLabelOrEmail)
            showStatus("Connected ${provider.displayName} (${account.accountEmail})")
            openCloudDirectory(account, "root")
        }
    }

    fun closeWebCloudPortal() {
        _activeWebCloudProvider.value = null
        _activeWebCloudUrl.value = null
    }

    fun downloadFromCloudWebSession(
        url: String,
        cookies: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        viewModelScope.launch {
            val downloadsDir = File(localProvider.getPrimaryRootDirectory(), "Download")
            showStatus("Downloading cloud file to Download/...")
            CloudStorageEngine.downloadFromWebSession(
                url = url,
                cookies = cookies,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimeType,
                destDir = downloadsDir
            ).onSuccess { savedFile ->
                showStatus("Downloaded '${savedFile.name}' to Download/")
                refreshCategoryCountsAndPrewarm()
            }.onFailure { err ->
                showStatus("Cloud download failed: ${err.message}")
            }
        }
    }

    fun startCloudOAuthFlow(
        provider: CloudProviderKind,
        clientId: String,
        loginHint: String? = null
    ): Result<String> {
        return runCatching {
            val effectiveHint = loginHint ?: if (provider == CloudProviderKind.GOOGLE_DRIVE) {
                "container6767@gmail.com"
            } else {
                null
            }
            val session = CloudStorageEngine.createPkceAuthorizationSession(provider, clientId, effectiveHint)
            _activePkceSession.value = session
            session.authorizationUrl
        }
    }

    /**
     * Stores a Google Drive account authorized on-device through Google Play services
     * and immediately opens the Google Drive storage in the app.
     */
    fun onGoogleDriveAuthorized(accessToken: String, email: String?) {
        viewModelScope.launch {
            val resolvedEmail = email?.takeIf { it.isNotBlank() }
                ?: CloudStorageEngine.fetchAccountEmail(CloudProviderKind.GOOGLE_DRIVE, accessToken)
                ?: "Google Drive Account"
            val existing = cloudAccounts.value.firstOrNull {
                it.provider == CloudProviderKind.GOOGLE_DRIVE.name &&
                    it.accountEmail.equals(resolvedEmail, ignoreCase = true)
            } ?: dao.getAllCloudAccounts().firstOrNull {
                it.provider == CloudProviderKind.GOOGLE_DRIVE.name
            }
            val alias = credentialVault.storeSecret(existing?.tokenSecretAlias, accessToken)
            val entity = CloudAccountEntity(
                id = existing?.id ?: 0L,
                provider = CloudProviderKind.GOOGLE_DRIVE.name,
                accountEmail = resolvedEmail,
                displayName = CloudProviderKind.GOOGLE_DRIVE.displayName,
                clientId = com.example.core.cloud.GoogleDriveOnDeviceAuth.ON_DEVICE_CLIENT_MARKER,
                tokenSecretAlias = alias,
                refreshTokenSecretAlias = null,
                tokenExpiresAt = System.currentTimeMillis() + 55 * 60_000L
            )
            val rowId = dao.upsertCloudAccount(entity)
            val saved = entity.copy(id = if (entity.id != 0L) entity.id else rowId)
            _activeDrivePickerAccount.value = saved
            _activeWebCloudProvider.value = null
            _activeWebCloudUrl.value = null
            showStatus("Connected Google Drive ($resolvedEmail)")
            openCloudDirectory(saved)
        }
    }

    /**
     * Handles the OAuth 2.0 redirect (com.openfile.manager:/oauth2redirect) delivered by the user's default browser.
     * Validates `state`, surfaces provider errors, exchanges the code automatically, and opens the cloud storage.
     */
    fun handleOAuthRedirect(uri: Uri) {
        val error = uri.getQueryParameter("error")
        if (error != null) {
            val desc = uri.getQueryParameter("error_description") ?: error
            _activePkceSession.value = null
            _activeWebCloudProvider.value = null
            _activeWebCloudUrl.value = null
            showStatus("Cloud authorization failed: $desc")
            return
        }
        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            showStatus("Cloud authorization failed: no authorization code returned")
            return
        }
        val session = _activePkceSession.value
        if (session == null) {
            showStatus("Cloud authorization expired. Tap Connect and try again.")
            return
        }
        if (uri.getQueryParameter("state") != session.state) {
            _activePkceSession.value = null
            showStatus("Cloud authorization rejected: state mismatch")
            return
        }
        completeCloudOAuthWithCode("", code, false)
    }

    fun completeCloudOAuthWithCode(
        accountEmailOrLabel: String,
        authorizationCodeOrAccessToken: String,
        directAccessTokenMode: Boolean
    ) {
        val session = _activePkceSession.value
        if (session == null) {
            showStatus("Start the browser consent step first (button 1), then paste the code.")
            return
        }
        viewModelScope.launch {
            if (directAccessTokenMode) {
                val token = authorizationCodeOrAccessToken.trim()
                val resolvedEmail = accountEmailOrLabel.takeIf { it.isNotBlank() }
                    ?: CloudStorageEngine.fetchAccountEmail(session.provider, token)
                    ?: "${session.provider.displayName} Account"
                val tokenAlias = credentialVault.storeSecret(null, token)
                val entity = CloudAccountEntity(
                    provider = session.provider.name,
                    accountEmail = resolvedEmail,
                    displayName = session.provider.displayName,
                    clientId = session.clientId,
                    tokenSecretAlias = tokenAlias,
                    refreshTokenSecretAlias = null,
                    tokenExpiresAt = System.currentTimeMillis() + 3600_000L
                )
                val rowId = dao.upsertCloudAccount(entity)
                _activePkceSession.value = null
                _activeWebCloudProvider.value = null
                _activeWebCloudUrl.value = null
                showStatus("Connected ${session.provider.displayName} ($resolvedEmail)")
                openCloudDirectory(entity.copy(id = rowId))
                return@launch
            }

            CloudStorageEngine.exchangeAuthorizationCode(session, authorizationCodeOrAccessToken).fold(
                onSuccess = { tokenResp ->
                    val resolvedEmail = accountEmailOrLabel.takeIf { it.isNotBlank() }
                        ?: CloudStorageEngine.fetchAccountEmail(session.provider, tokenResp.accessToken)
                        ?: "${session.provider.displayName} Account"
                    val accessAlias = credentialVault.storeSecret(null, tokenResp.accessToken)
                    val refreshAlias = tokenResp.refreshToken?.let { credentialVault.storeSecret(null, it) }
                    val entity = CloudAccountEntity(
                        provider = session.provider.name,
                        accountEmail = resolvedEmail,
                        displayName = session.provider.displayName,
                        clientId = session.clientId,
                        tokenSecretAlias = accessAlias,
                        refreshTokenSecretAlias = refreshAlias,
                        tokenExpiresAt = System.currentTimeMillis() + (tokenResp.expiresInSeconds * 1000L)
                    )
                    val rowId = dao.upsertCloudAccount(entity)
                    _activePkceSession.value = null
                    _activeWebCloudProvider.value = null
                    _activeWebCloudUrl.value = null
                    showStatus("Connected ${session.provider.displayName} ($resolvedEmail)")
                    openCloudDirectory(entity.copy(id = rowId))
                },
                onFailure = { err ->
                    showStatus("OAuth token exchange failed: ${err.message}")
                }
            )
        }
    }

    fun disconnectCloudAccount(account: CloudAccountEntity) {
        viewModelScope.launch {
            credentialVault.deleteSecret(account.tokenSecretAlias)
            credentialVault.deleteSecret(account.refreshTokenSecretAlias)
            dao.deleteCloudAccount(account.id)
            if (_activeWebCloudProvider.value?.name == account.provider) {
                _activeWebCloudProvider.value = null
                _activeWebCloudUrl.value = null
            }
            showStatus("Disconnected ${account.displayName} (${account.accountEmail})")
        }
    }

    /**
     * Retrieves the active OAuth 2.0 Bearer access token stored in the encrypted Keystore vault
     * for the given CloudAccountEntity.
     */
    fun retrieveCloudAccessToken(account: CloudAccountEntity): String {
        return credentialVault.retrieveSecret(account.tokenSecretAlias).orEmpty()
    }

    fun openDriveFilePickerModal() {
        val immediateAcc = _activeDrivePickerAccount.value
            ?: _browserState.value.activeCloudAccount?.takeIf { it.provider == CloudProviderKind.GOOGLE_DRIVE.name }
            ?: cloudAccounts.value.lastOrNull { it.provider == CloudProviderKind.GOOGLE_DRIVE.name }
        if (immediateAcc != null) {
            _activeDrivePickerAccount.value = immediateAcc
            _showDriveFilePickerModal.value = true
            return
        }
        viewModelScope.launch {
            val currentAccounts = dao.getAllCloudAccounts()
            val googleAcc = currentAccounts.lastOrNull { it.provider == CloudProviderKind.GOOGLE_DRIVE.name }
            if (googleAcc != null) {
                _activeDrivePickerAccount.value = googleAcc
            } else {
                val created = upsertBrowserAuthenticatedCloudAccount(
                    provider = CloudProviderKind.GOOGLE_DRIVE,
                    detectedLabelOrEmail = "container6767@gmail.com"
                )
                _activeDrivePickerAccount.value = created
            }
            _showDriveFilePickerModal.value = true
        }
    }

    fun closeDriveFilePickerModal() {
        _showDriveFilePickerModal.value = false
    }

    /**
     * Pastes items currently on the clipboard directly into a target folder inside the specified
     * Cloud account (e.g., Google Drive folder inside GoogleDriveFilePicker).
     */
    fun pasteClipboardIntoCloudFolder(account: CloudAccountEntity, targetFolderIdOrPath: String) {
        val pk = runCatching { CloudProviderKind.valueOf(account.provider) }.getOrDefault(CloudProviderKind.GOOGLE_DRIVE)
        val normalizedFolder = targetFolderIdOrPath.trim().trim('/').ifEmpty { "root" }
        val destDir = FileReference(
            providerType = pk.providerType,
            uriOrPath = normalizedFolder,
            displayName = normalizedFolder,
            parentUriOrPath = "root",
            isDirectory = true,
            capabilities = FileReference.defaultCapabilitiesFor(pk.providerType, true),
            metadata = mapOf("accountId" to account.id.toString())
        )
        val verify = settings.value.verifyChecksumAfterTransfer
        transferEngine.enqueuePasteFromClipboard(
            destinationDir = destDir,
            verifyChecksum = verify,
            onComplete = { result ->
                result.onSuccess { msg ->
                    showStatus(msg)
                    refreshCurrentDirectory()
                }.onFailure { err ->
                    showStatus("Drive transfer error: ${err.message}")
                    refreshCurrentDirectory()
                }
            }
        )
    }

    /**
     * Imports files/folders picked inside GoogleDriveFilePicker directly into the active local
     * directory (or Downloads directory if no local folder is active).
     */
    fun importPickedDriveFilesToCurrentDirectory(
        pickedItems: List<FileReference>,
        explicitTargetDir: FileReference? = null,
        onComplete: (Result<String>) -> Unit = {}
    ) {
        if (pickedItems.isEmpty()) {
            onComplete(Result.failure(IllegalArgumentException("No Drive items selected")))
            return
        }
        val currentDir = explicitTargetDir ?: _browserState.value.currentDirectory
        val destLocalDir = if (currentDir != null && currentDir.providerType == ProviderType.LOCAL) {
            currentDir
        } else {
            val downloadsDir = File(localProvider.getPrimaryRootDirectory(), "Download").apply { mkdirs() }
            localProvider.toFileReference(downloadsDir)
        }
        transferEngine.copyToClipboard(pickedItems, ClipboardMode.COPY)
        val verify = settings.value.verifyChecksumAfterTransfer
        transferEngine.enqueuePasteFromClipboard(
            destinationDir = destLocalDir,
            verifyChecksum = verify,
            onComplete = { result ->
                result.onSuccess {
                    showStatus("Imported ${pickedItems.size} item(s) from Google Drive to ${destLocalDir.displayName}")
                    if (_browserState.value.currentDirectory?.providerType == ProviderType.LOCAL) {
                        refreshCurrentDirectory()
                    }
                }.onFailure { err ->
                    showStatus("Drive import error: ${err.message}")
                }
                onComplete(result)
            }
        )
    }

    fun openCloudDirectory(account: CloudAccountEntity, folderIdOrPath: String = "root") {
        val normalizedFolder = folderIdOrPath.trim().trim('/').ifEmpty { "root" }
        browseDirectoryJob?.cancel()
        _browserState.value = _browserState.value.copy(
            isLoading = true,
            errorMessage = null,
            activeCloudAccount = account,
            activeRemoteProfile = null,
            selectedUris = emptySet()
        )
        browseDirectoryJob = viewModelScope.launch {
            var accessToken = credentialVault.retrieveSecret(account.tokenSecretAlias).orEmpty()
            // On-device Google accounts have no refresh token: silently re-authorize via Play services.
            if (account.clientId == com.example.core.cloud.GoogleDriveOnDeviceAuth.ON_DEVICE_CLIENT_MARKER &&
                System.currentTimeMillis() > account.tokenExpiresAt - 60_000L
            ) {
                runCatching {
                    com.example.core.cloud.GoogleDriveOnDeviceAuth.authorize(getApplication(), account.accountEmail)
                }.onSuccess { result ->
                    val fresh = result.accessToken
                    if (!result.hasResolution() && !fresh.isNullOrBlank()) {
                        accessToken = fresh
                        credentialVault.storeSecret(account.tokenSecretAlias, fresh)
                        dao.upsertCloudAccount(account.copy(tokenExpiresAt = System.currentTimeMillis() + 55 * 60_000L))
                    } else {
                        showStatus("Google Drive token expired; using native Drive workspace until re-connected.")
                    }
                }.onFailure {
                    showStatus("Could not renew Google Drive token: ${it.message}")
                }
            }
            // Auto-refresh if expired and refresh token exists
            if (System.currentTimeMillis() > account.tokenExpiresAt && account.refreshTokenSecretAlias != null) {
                val refreshTok = credentialVault.retrieveSecret(account.refreshTokenSecretAlias)
                if (!refreshTok.isNullOrBlank()) {
                    val pk = CloudProviderKind.valueOf(account.provider)
                    CloudStorageEngine.refreshAccessToken(pk, account.clientId, refreshTok).onSuccess { refreshed ->
                        accessToken = refreshed.accessToken
                        credentialVault.storeSecret(account.tokenSecretAlias, refreshed.accessToken)
                        dao.upsertCloudAccount(
                            account.copy(tokenExpiresAt = System.currentTimeMillis() + refreshed.expiresInSeconds * 1000L)
                        )
                    }
                }
            }

            val currentSettings = _settings.value
            CloudStorageEngine.listCloudDirectory(
                account = account,
                accessToken = accessToken,
                folderIdOrPath = normalizedFolder,
                context = context,
                showHidden = currentSettings.showHiddenFiles
            ).fold(
                onSuccess = { items ->
                    val pk = CloudProviderKind.valueOf(account.provider)
                    val sortedItems = FileSortAndFilterEngine.sortFiles(
                        items,
                        currentSettings.defaultSortField,
                        currentSettings.defaultSortOrder
                    )
                    val rootRef = FileReference(
                        providerType = pk.providerType,
                        uriOrPath = "root",
                        displayName = "${account.displayName} (${account.accountEmail})",
                        parentUriOrPath = null,
                        isDirectory = true,
                        capabilities = FileReference.defaultCapabilitiesFor(pk.providerType, true),
                        metadata = mapOf("accountId" to account.id.toString())
                    )
                    val previousState = _browserState.value
                    val updatedBreadcrumbs: List<FileReference> = when {
                        normalizedFolder.equals("root", ignoreCase = true) -> listOf(rootRef)
                        normalizedFolder.contains("/") || !CloudStorageEngine.isLiveOAuthSession(account, accessToken) || pk != CloudProviderKind.GOOGLE_DRIVE -> {
                            // Path-based hierarchy (Native Drive VFS, OneDrive, Dropbox)
                            val segments = normalizedFolder.split('/').filter { it.isNotBlank() }
                            val crumbs = mutableListOf<FileReference>(rootRef)
                            var runningPath = ""
                            var parentPath = "root"
                            for (seg in segments) {
                                runningPath = if (runningPath.isEmpty()) seg else "$runningPath/$seg"
                                crumbs += FileReference(
                                    providerType = pk.providerType,
                                    uriOrPath = runningPath,
                                    displayName = seg,
                                    parentUriOrPath = parentPath,
                                    isDirectory = true,
                                    capabilities = FileReference.defaultCapabilitiesFor(pk.providerType, true),
                                    metadata = mapOf("accountId" to account.id.toString())
                                )
                                parentPath = runningPath
                            }
                            crumbs
                        }
                        else -> {
                            // Opaque folder ID hierarchy (Live Google Drive OAuth v3)
                            val existingIdx = previousState.breadcrumbs.indexOfFirst { it.uriOrPath == normalizedFolder }
                            if (existingIdx >= 0) {
                                previousState.breadcrumbs.subList(0, existingIdx + 1)
                            } else {
                                val matchedItem = previousState.items.firstOrNull { it.uriOrPath == normalizedFolder }
                                val parentId = previousState.currentDirectory?.uriOrPath ?: "root"
                                val newCrumb = FileReference(
                                    providerType = pk.providerType,
                                    uriOrPath = normalizedFolder,
                                    displayName = matchedItem?.displayName ?: normalizedFolder,
                                    parentUriOrPath = parentId,
                                    isDirectory = true,
                                    capabilities = FileReference.defaultCapabilitiesFor(pk.providerType, true),
                                    metadata = mapOf("accountId" to account.id.toString())
                                )
                                val baseCrumbs = previousState.breadcrumbs.ifEmpty { listOf(rootRef) }
                                baseCrumbs + newCrumb
                            }
                        }
                    }
                    val currentDirRef = updatedBreadcrumbs.lastOrNull() ?: rootRef
                    _currentDestination.value = OpenFileDestination.INTERNAL_STORAGE
                    _browserState.value = previousState.copy(
                        currentDirectory = currentDirRef,
                        breadcrumbs = updatedBreadcrumbs,
                        items = sortedItems,
                        isLoading = false,
                        errorMessage = null,
                        activeCloudAccount = account,
                        activeRemoteProfile = null
                    )
                },
                onFailure = { err ->
                    _browserState.value = _browserState.value.copy(
                        isLoading = false,
                        errorMessage = err.message
                    )
                    showStatus("Cloud directory load failed: ${err.message}")
                }
            )
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
            refreshCurrentDirectory()
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
        _settings.value = _settings.value.copy(defaultSortField = field, defaultSortOrder = order)
        viewModelScope.launch {
            settingsStore.setSorting(field, order)
            refreshCurrentDirectory()
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
}
