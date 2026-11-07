package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.NetworkProfileEntity
import com.example.core.database.OpenFileDatabase
import com.example.core.filesystem.ClipboardMode
import com.example.core.filesystem.LocalFileSystemProvider
import com.example.core.filesystem.SafDocumentProvider
import com.example.core.filesystem.TransferQueueEngine
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.example.core.model.StorageVolumeInfo
import com.example.core.network.FtpServerConfig
import com.example.ui.screens.adaptiveStorageDeviceName
import com.example.core.network.FtpServerEngine
import com.example.core.network.RemoteNetworkClients
import com.example.core.security.KeystoreCredentialVault
import com.example.core.storage.StorageAnalyzerEngine
import com.example.ui.OpenFileDestination
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name resource is OpenFile`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("OpenFile", context.getString(R.string.app_name))
    }

    @Test
    fun `keystore credential vault encrypts decrypts and deletes secrets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = KeystoreCredentialVault(context)
        vault.clearAllSecrets()

        val alias = vault.storeSecret(null, "super_secret_sftp_passphrase_2026")
        assertTrue(alias.startsWith("sec_"))

        val decrypted = vault.retrieveSecret(alias)
        assertEquals("super_secret_sftp_passphrase_2026", decrypted)

        vault.deleteSecret(alias)
        assertNull(vault.retrieveSecret(alias))
    }

    @Test
    fun `local file system provider creates folder text file renames and deletes`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = LocalFileSystemProvider(context)
        val workspace = provider.getAppWorkspaceDirectory()
        val rootRef = provider.toFileReference(workspace)

        val folderRef = provider.createDirectory(rootRef, "RobolectricTestDir").getOrThrow()
        assertTrue(folderRef.isDirectory)

        val noteRef = provider.createTextFile(folderRef, "hello.txt", "OpenFile content").getOrThrow()
        assertEquals("hello.txt", noteRef.displayName)

        // Detailed reference includes child count and canonical path in metadata
        val detailedFolderRef = provider.toFileReference(
            File(folderRef.uriOrPath),
            includeChildCount = true,
            includeCanonicalPath = true
        )
        assertEquals("1", detailedFolderRef.metadata["childCount"])
        assertNotNull(detailedFolderRef.metadata["canonicalPath"])

        val renamedRef = provider.rename(noteRef, "renamed_hello.txt").getOrThrow()
        assertEquals("renamed_hello.txt", renamedRef.displayName)

        val deleted = provider.delete(folderRef).getOrThrow()
        assertTrue(deleted)
    }

    @Test
    fun `same-directory copy creates auto-renamed duplicate without deleting original and blocks subfolder self-copy`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = OpenFileDatabase.getInstance(context)
        val localProvider = LocalFileSystemProvider(context)
        val safProvider = SafDocumentProvider(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val engine = TransferQueueEngine(context, db.dao(), localProvider, safProvider, scope)

        val tempDir = Files.createTempDirectory("openfile_transfer_test").toFile()
        try {
            val originalFile = File(tempDir, "document.txt").apply {
                writeText("Critical data that must never be lost on same-folder copy")
            }
            val dirRef = localProvider.toFileReference(tempDir)
            val fileRef = localProvider.toFileReference(originalFile)

            // 1. Copy and paste into the exact same directory
            engine.copyToClipboard(listOf(fileRef), ClipboardMode.COPY)
            var completed = false
            engine.enqueuePasteFromClipboard(
                destinationDir = dirRef,
                verifyChecksum = true,
                onComplete = { completed = true }
            )

            // Wait for async job completion
            var attempts = 0
            while (!completed && attempts < 50) {
                delay(50)
                attempts++
            }

            val duplicatedFile = File(tempDir, "document (1).txt")
            assertTrue("Original file must still exist", originalFile.exists())
            assertTrue("Duplicate file 'document (1).txt' must be created", duplicatedFile.exists())
            assertEquals(originalFile.readText(), duplicatedFile.readText())

            // 2. Verify subfolder self-recursion guard prevents copying parent into its own child
            val childFolder = File(tempDir, "child_sub").apply { mkdirs() }
            val childRef = localProvider.toFileReference(childFolder)
            var subDone = false
            var subFailed = false
            engine.copyToClipboard(listOf(dirRef), ClipboardMode.COPY)
            engine.enqueuePasteFromClipboard(
                destinationDir = childRef,
                verifyChecksum = false,
                onComplete = { res ->
                    subFailed = res.isFailure
                    subDone = true
                }
            )
            var subAttempts = 0
            while (!subDone && subAttempts < 50) {
                delay(50)
                subAttempts++
            }
            assertTrue("Self-subfolder copy should fail safely with an error", subFailed)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `trash soft-delete and restore round-trip preserves file content`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = OpenFileDatabase.getInstance(context)
        val localProvider = LocalFileSystemProvider(context)
        val safProvider = SafDocumentProvider(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val engine = TransferQueueEngine(context, db.dao(), localProvider, safProvider, scope)

        val tempDir = Files.createTempDirectory("openfile_trash_test").toFile()
        try {
            val sampleFile = File(tempDir, "important.txt").apply {
                writeText("Restorable content 123")
            }
            val fileRef = localProvider.toFileReference(sampleFile)

            // Soft-delete to trash
            val deleteRes = engine.deleteItems(listOf(fileRef), moveToTrash = true)
            assertTrue(deleteRes.isSuccess)
            assertFalse("File should be moved out of original folder", sampleFile.exists())

            val undo = engine.latestUndoAction.value
            assertNotNull("Undo action should be registered for trash move", undo)

            // Restore from trash via Undo callback
            val restoreRes = undo!!.performUndo()
            assertTrue(restoreRes.isSuccess)
            assertTrue("File should be restored to original path", sampleFile.exists())
            assertEquals("Restorable content 123", sampleFile.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `storage analyzer computes breakdown and finds exact SHA-256 duplicates`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val localProvider = LocalFileSystemProvider(context)
        val analyzer = StorageAnalyzerEngine(localProvider)
        val tempRoot = Files.createTempDirectory("openfile_analyzer_test").toFile()
        try {
            val dupContent = "Identical duplicate payload across two files for SHA-256 verification"
            File(tempRoot, "dup_one.txt").writeText(dupContent)
            File(tempRoot, "dup_two.txt").writeText(dupContent)
            File(tempRoot, "unique.log").writeText("Unique log content")

            val report = analyzer.analyzeDirectory(
                rootDir = tempRoot,
                includeDuplicateScan = true
            ).getOrThrow()

            assertEquals(3, report.scannedFilesCount)
            assertEquals(1, report.duplicateGroups.size)
            assertEquals(2, report.duplicateGroups.first().files.size)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `embedded FTP server and FTP client perform real loopback directory listing and file transfer`() = runBlocking {
        val tempServerRoot = Files.createTempDirectory("ftp_server_loopback").toFile()
        val freePort = ServerSocket(0).use { it.localPort }
        try {
            File(tempServerRoot, "server_readme.txt").writeText("Transferred over real local FTP passive socket!")

            val startRes = FtpServerEngine.startServer(
                FtpServerConfig(
                    bindAddress = "127.0.0.1",
                    port = freePort,
                    passivePortStart = 51000,
                    passivePortEnd = 51020,
                    rootDirectory = tempServerRoot,
                    username = "admin",
                    password = "secret99",
                    allowAnonymous = false,
                    allowWrite = true
                )
            )
            assertTrue(startRes.isSuccess)

            val profile = NetworkProfileEntity(
                displayName = "Loopback FTP",
                protocol = "FTP",
                host = "127.0.0.1",
                port = freePort,
                username = "admin",
                secretAlias = null,
                remoteInitialPath = "/",
                usePassiveMode = true
            )

            val remoteListing = RemoteNetworkClients.listRemoteDirectory(profile, "secret99", "/").getOrThrow()
            assertTrue(remoteListing.any { it.displayName == "server_readme.txt" })

            val downloadedFile = File(tempServerRoot, "downloaded_copy.txt")
            val remoteItem = remoteListing.first { it.displayName == "server_readme.txt" }
            RemoteNetworkClients.downloadRemoteFile(profile, "secret99", remoteItem, downloadedFile).getOrThrow()
            assertEquals("Transferred over real local FTP passive socket!", downloadedFile.readText())
        } finally {
            FtpServerEngine.stopServer()
            tempServerRoot.deleteRecursively()
        }
    }

    @Test
    fun `embedded FTP server exposes device and sdcard virtual mounts like File Manager Plus`() = runBlocking {
        val tempDeviceRoot = Files.createTempDirectory("ftp_device_root").toFile()
        val tempSdCardRoot = Files.createTempDirectory("ftp_sdcard_root").toFile()
        val freePort = ServerSocket(0).use { it.localPort }
        try {
            File(tempDeviceRoot, "internal_note.txt").writeText("From Internal Storage")
            File(tempSdCardRoot, "sdcard_photo.jpg").writeText("From Removable SD Card")

            val startRes = FtpServerEngine.startServer(
                FtpServerConfig(
                    bindAddress = "127.0.0.1",
                    port = freePort,
                    passivePortStart = 51025,
                    passivePortEnd = 51045,
                    rootDirectory = tempDeviceRoot,
                    virtualMounts = linkedMapOf(
                        "device" to tempDeviceRoot,
                        "sdcard" to tempSdCardRoot
                    ),
                    username = "openfile",
                    password = "secret99",
                    allowAnonymous = false,
                    allowWrite = true
                )
            )
            assertTrue(startRes.isSuccess)

            val profile = NetworkProfileEntity(
                displayName = "Multi-Storage FTP",
                protocol = "FTP",
                host = "127.0.0.1",
                port = freePort,
                username = "openfile",
                secretAlias = null,
                remoteInitialPath = "/",
                usePassiveMode = true
            )

            // 1. Listing "/" reveals both "device" and "sdcard" virtual folders
            val rootListing = RemoteNetworkClients.listRemoteDirectory(profile, "secret99", "/").getOrThrow()
            val rootNames = rootListing.map { it.displayName }.toSet()
            assertTrue("Expected 'device' and 'sdcard' at FTP root /, got $rootNames", "device" in rootNames && "sdcard" in rootNames)

            // 2. Listing "/sdcard" reveals files on the SD card
            val sdCardListing = RemoteNetworkClients.listRemoteDirectory(profile, "secret99", "/sdcard").getOrThrow()
            assertTrue(sdCardListing.any { it.displayName == "sdcard_photo.jpg" })

            // 3. Downloading from "/sdcard/sdcard_photo.jpg" retrieves the SD card file
            val sdItem = sdCardListing.first { it.displayName == "sdcard_photo.jpg" }
            val downloadedSdFile = File(tempDeviceRoot, "fetched_from_sd.jpg")
            RemoteNetworkClients.downloadRemoteFile(profile, "secret99", sdItem, downloadedSdFile).getOrThrow()
            assertEquals("From Removable SD Card", downloadedSdFile.readText())
        } finally {
            FtpServerEngine.stopServer()
            tempDeviceRoot.deleteRecursively()
            tempSdCardRoot.deleteRecursively()
        }
    }

    @Test
    fun `UI CUJ navigates through all 17 destinations and verifies screens and options`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Storage Devices").assertIsDisplayed()

        val viewModel = composeTestRule.activity.viewModel

        // 1. INTERNAL_STORAGE + New Folder & New File dialog triggers
        composeTestRule.runOnUiThread {
            viewModel.navigateToDestination(OpenFileDestination.INTERNAL_STORAGE)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("browser_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("status_footer_bar").assertIsDisplayed()

        // Open and dismiss New Folder dialog
        composeTestRule.onNodeWithTag("browser_new_folder_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Create New Folder").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        // Open and dismiss New Text File dialog
        composeTestRule.onNodeWithTag("browser_new_file_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Create New Text File").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        // 2. DOWNLOADS
        composeTestRule.runOnUiThread {
            viewModel.navigateToDestination(OpenFileDestination.DOWNLOADS)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("browser_search_input").assertIsDisplayed()

        // 3. Virtual Categories (IMAGES, VIDEOS, AUDIO, DOCUMENTS, ARCHIVES, LARGE_FILES, RECENT_FILES)
        val virtualCategories = listOf(
            OpenFileDestination.IMAGES,
            OpenFileDestination.VIDEOS,
            OpenFileDestination.AUDIO,
            OpenFileDestination.DOCUMENTS,
            OpenFileDestination.ARCHIVES,
            OpenFileDestination.LARGE_FILES,
            OpenFileDestination.RECENT_FILES
        )
        for (dest in virtualCategories) {
            composeTestRule.runOnUiThread {
                viewModel.navigateToDestination(dest)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("category_context_banner").assertIsDisplayed()
        }

        // 4. APPLICATIONS
        composeTestRule.runOnUiThread {
            viewModel.navigateToDestination(OpenFileDestination.APPLICATIONS)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("applications_screen").assertIsDisplayed()

        // 5. STORAGE_ANALYSIS
        composeTestRule.runOnUiThread {
            viewModel.navigateToDestination(OpenFileDestination.STORAGE_ANALYSIS)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("storage_analysis_screen").assertIsDisplayed()

        // 6. BOOKMARKS (also test adding & displaying a bookmark)
        composeTestRule.runOnUiThread {
            val wsRef = viewModel.localProvider.toFileReference(viewModel.localProvider.getAppWorkspaceDirectory())
            viewModel.toggleBookmark(wsRef)
            viewModel.navigateToDestination(OpenFileDestination.BOOKMARKS)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bookmarks_screen").assertIsDisplayed()

        // 7. TRASH
        composeTestRule.runOnUiThread {
            viewModel.navigateToDestination(OpenFileDestination.TRASH)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("trash_screen").assertIsDisplayed()

        // 8. NETWORK (Wi-Fi FTP Server tab + Remote Connections tab)
        composeTestRule.runOnUiThread {
            viewModel.navigateToDestination(OpenFileDestination.NETWORK)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("network_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("network_tab_remote_clients").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("network_add_profile_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("network_tab_ftp_server").performClick()
        composeTestRule.waitForIdle()

        // 9. SETTINGS
        composeTestRule.runOnUiThread {
            viewModel.navigateToDestination(OpenFileDestination.SETTINGS)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // 10. Return HOME via Drawer
        composeTestRule.onNodeWithTag("top_bar_drawer_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_home").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Storage Devices").assertIsDisplayed()
    }

    @Test
    fun `UI CUJ tests theme cycling view mode toggle and directory status footer`() {
        composeTestRule.waitForIdle()

        val vm = composeTestRule.activity.viewModel
        assertEquals(ThemeMode.SYSTEM, vm.settings.value.themeMode)

        // Cycle theme modes via TopAppBar button (SYSTEM -> LIGHT -> DARK -> AMOLED -> SYSTEM)
        composeTestRule.onNodeWithTag("top_bar_theme_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(ThemeMode.LIGHT, vm.settings.value.themeMode)

        composeTestRule.onNodeWithTag("top_bar_theme_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ThemeMode.DARK, vm.settings.value.themeMode)

        composeTestRule.onNodeWithTag("top_bar_theme_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ThemeMode.AMOLED, vm.settings.value.themeMode)

        composeTestRule.onNodeWithTag("top_bar_theme_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(ThemeMode.SYSTEM, vm.settings.value.themeMode)

        // Navigate to Internal Storage Browser
        composeTestRule.onNodeWithTag("top_bar_drawer_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_internal_storage").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            !composeTestRule.activity.viewModel.browserState.value.isLoading &&
                composeTestRule.activity.viewModel.browserState.value.items.isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Verify KDE Dolphin-style directory status footer bar is displayed
        composeTestRule.onNodeWithTag("status_footer_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("browser_list_view").assertIsDisplayed()

        // Toggle from List view to Grid view and back
        composeTestRule.onNodeWithTag("browser_toggle_view_mode").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("browser_grid_view").assertIsDisplayed()

        composeTestRule.onNodeWithTag("browser_toggle_view_mode").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("browser_list_view").assertIsDisplayed()
    }

    @Test
    fun `local directory listing of 300 files and subfolders completes rapidly without nested IO bottleneck`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = LocalFileSystemProvider(context)
        val benchRoot = Files.createTempDirectory("openfile_perf_bench").toFile()
        try {
            for (i in 1..250) {
                File(benchRoot, "file_$i.txt").writeText("payload_$i")
            }
            for (d in 1..50) {
                val sub = File(benchRoot, "folder_$d").apply { mkdirs() }
                File(sub, "nested.txt").writeText("nested")
            }
            val rootRef = provider.toFileReference(benchRoot)
            val startMs = System.currentTimeMillis()
            val children = provider.listChildren(rootRef, showHidden = true).getOrThrow()
            val elapsedMs = System.currentTimeMillis() - startMs

            assertEquals(300, children.size)
            assertTrue("Expected fast listChildren (< 1500ms), took ${elapsedMs}ms", elapsedMs < 1500)
        } finally {
            benchRoot.deleteRecursively()
        }
    }

    @Test
    fun `UI responsiveness benchmark verifies non-blocking main-thread latency and rapid search debouncing`() = runBlocking {
        composeTestRule.waitForIdle()
        val viewModel = composeTestRule.activity.viewModel

        // 1. Measure main-thread return latency of openDirectory and openCategoryOrSpecialFolder (must be < 50ms)
        val workspaceRef = viewModel.localProvider.toFileReference(viewModel.localProvider.getAppWorkspaceDirectory())
        var openDirMainThreadMs = 0L
        var openCategoryMainThreadMs = 0L

        composeTestRule.runOnUiThread {
            val t0 = System.nanoTime()
            viewModel.openDirectory(workspaceRef)
            openDirMainThreadMs = (System.nanoTime() - t0) / 1_000_000

            val t1 = System.nanoTime()
            viewModel.openCategoryOrSpecialFolder(com.example.core.model.FileCategory.IMAGES)
            openCategoryMainThreadMs = (System.nanoTime() - t1) / 1_000_000
        }

        assertTrue("openDirectory must not block main thread (< 50ms), took ${openDirMainThreadMs}ms", openDirMainThreadMs < 50)
        assertTrue("openCategoryOrSpecialFolder must not block main thread (< 50ms), took ${openCategoryMainThreadMs}ms", openCategoryMainThreadMs < 50)

        // 2. Measure optimistic 0ms settings toggle latency on main thread
        var themeToggleMainThreadMs = 0L
        composeTestRule.runOnUiThread {
            val t0 = System.nanoTime()
            viewModel.setShowHiddenFiles(true)
            viewModel.setShowThumbnails(true)
            themeToggleMainThreadMs = (System.nanoTime() - t0) / 1_000_000
        }
        assertTrue("Optimistic settings toggle must complete in < 20ms, took ${themeToggleMainThreadMs}ms", themeToggleMainThreadMs < 20)

        // 3. Verify rapid typing in search box is debounced and does not flood Room DB
        viewModel.dao.clearSearchHistory()
        val rapidKeystrokes = listOf("re", "rep", "repo", "repor", "report", "report_2026")
        composeTestRule.runOnUiThread {
            for (partial in rapidKeystrokes) {
                viewModel.updateSearchQuery(partial)
            }
        }
        assertEquals("report_2026", viewModel.browserState.value.searchQuery)
        // Wait for 350ms debounce window to elapse
        delay(500L)
        composeTestRule.waitForIdle()
        // Only the final debounced query should have been persisted
        val savedQueries = viewModel.searchHistory.value
        assertTrue("Debounced search should not write every intermediate keystroke", savedQueries.size <= 1)

        // 4. Verify in-memory KeystoreCredentialVault cache serves repeated countStoredSecrets in < 50ms
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = KeystoreCredentialVault(context)
        vault.storeSecret(null, "cached_perf_token")
        val cacheStartNs = System.nanoTime()
        repeat(100) {
            vault.countStoredSecrets()
        }
        val cacheElapsedMs = (System.nanoTime() - cacheStartNs) / 1_000_000
        assertTrue("100 cached vault count calls should take < 50ms, took ${cacheElapsedMs}ms", cacheElapsedMs < 50)

        // 5. Verify parallel two-stage duplicate detection across 80 same-size 64 KB files
        val tempDir = Files.createTempDirectory("openfile_parallel_dup_bench").toFile()
        try {
            val candidates = mutableListOf<com.example.core.model.FileReference>()
            val baseSize = 64 * 1024
            for (group in 1..20) {
                val payload = ByteArray(baseSize) { idx -> ((idx + group * 13) and 0xFF).toByte() }
                for (copy in 1..2) {
                    val f = File(tempDir, "dup_${group}_$copy.bin").apply { writeBytes(payload) }
                    candidates.add(viewModel.localProvider.toFileReference(f))
                }
            }
            for (u in 1..40) {
                val uniquePayload = ByteArray(baseSize) { idx -> ((idx * 7 + u * 31) and 0xFF).toByte() }
                val f = File(tempDir, "unique_$u.bin").apply { writeBytes(uniquePayload) }
                candidates.add(viewModel.localProvider.toFileReference(f))
            }
            val dupStartMs = System.currentTimeMillis()
            val groups = com.example.core.filesystem.ChecksumAndDuplicateEngine.findDuplicates(candidates, viewModel.localProvider)
            val dupElapsedMs = System.currentTimeMillis() - dupStartMs
            assertEquals(20, groups.size)
            assertTrue("Expected parallel duplicate scan of 80 files < 1500ms, took ${dupElapsedMs}ms", dupElapsedMs < 1500)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `File Manager Plus home dashboard 12-tile grid and bottom selection and clipboard bars work smoothly`() {
        composeTestRule.waitForIdle()
        val viewModel = composeTestRule.activity.viewModel

        // 1. Verify File Manager + 12-tile grid tags on Home screen
        val expectedTiles = listOf(
            "shortcut_downloads",
            "shortcut_images",
            "shortcut_audio",
            "shortcut_videos",
            "shortcut_documents",
            "shortcut_applications",
            "shortcut_recent_files",
            "shortcut_archives",
            "shortcut_large_files",
            "home_network_button",
            "home_access_pc_button",
            "shortcut_storage_analysis"
        )
        for (tag in expectedTiles) {
            composeTestRule.onNodeWithTag(tag).assertExists()
        }
        composeTestRule.onNodeWithTag("home_analyzer_button").assertExists()

        // 2. Verify 'Remote' tile opens NetworkScreen on tab 1 (Remote Connections)
        composeTestRule.runOnUiThread {
            viewModel.openNetworkTab(1)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("network_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("network_add_profile_button").assertIsDisplayed()

        // 3. Verify 'Access from PC' opens NetworkScreen on tab 0 (Wi-Fi FTP Server) with collapsed security notice and eye password toggle
        composeTestRule.runOnUiThread {
            viewModel.openNetworkTab(0)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("network_tab_ftp_server").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ftp_security_notice_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ftp_security_notice_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ftp_security_notice_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ftp_password_toggle").assertExists()

        // 4. Navigate to Internal Storage workspace, select 1 item, and verify Bottom Selection Bar (Copy, Move, Rename, Delete, Share, Compress)
        val workspaceRef = viewModel.localProvider.toFileReference(viewModel.localProvider.getAppWorkspaceDirectory())
        composeTestRule.runOnUiThread {
            viewModel.openVolumeInBrowser(workspaceRef)
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !viewModel.browserState.value.isLoading && viewModel.browserState.value.items.isNotEmpty()
        }
        composeTestRule.waitForIdle()

        val firstItem = viewModel.browserState.value.items.first()
        composeTestRule.runOnUiThread {
            viewModel.toggleSelection(firstItem)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("bottom_selection_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("multiselect_copy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("multiselect_cut").assertIsDisplayed()
        composeTestRule.onNodeWithTag("multiselect_rename").assertIsDisplayed()
        composeTestRule.onNodeWithTag("multiselect_delete").assertIsDisplayed()
        composeTestRule.onNodeWithTag("multiselect_share").assertIsDisplayed()
        composeTestRule.onNodeWithTag("multiselect_compress").assertIsDisplayed()

        // 5. Tap Copy on the bottom selection bar -> verify Bottom Clipboard Bar appears with Paste Here
        composeTestRule.onNodeWithTag("multiselect_copy").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_clipboard_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("browser_paste_button").assertIsDisplayed()
    }

    @Test
    fun `storage analyzer result screen supports sorting searching renaming copying deleting and opening containing folder`() = runBlocking {
        composeTestRule.waitForIdle()
        val viewModel = composeTestRule.activity.viewModel
        val tempRoot = Files.createTempDirectory("openfile_analyzer_actions_test").toFile()
        try {
            val subFolder = File(tempRoot, "MediaHeavyFolder").apply { mkdirs() }
            val bigFile = File(subFolder, "heavy_video.mp4").apply {
                writeBytes(ByteArray(32 * 1024) { (it and 0xFF).toByte() })
            }
            val dupPayload = "Duplicate content inside storage analyzer for interactive action test"
            val dupA = File(subFolder, "dup_alpha.txt").apply { writeText(dupPayload) }
            val dupB = File(subFolder, "dup_beta.txt").apply { writeText(dupPayload) }

            // 1. Navigate to Storage Analysis and scan tempRoot
            composeTestRule.runOnUiThread {
                viewModel.navigateToDestination(OpenFileDestination.STORAGE_ANALYSIS)
                viewModel.runStorageAnalysis(tempRoot)
            }
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                !viewModel.isAnalyzingStorage.value && viewModel.analysisReport.value != null
            }
            composeTestRule.waitForIdle()

            // Switch to Largest Files tab and verify search & sort controls exist
            composeTestRule.onNodeWithTag("analyzer_tab_largest_files").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("analyzer_search_input").assertIsDisplayed()
            composeTestRule.onNodeWithTag("analyzer_sort_button").assertIsDisplayed()

            val initialReport = viewModel.analysisReport.value!!
            assertTrue(initialReport.largestFiles.any { it.displayName == "heavy_video.mp4" })

            // 2. Rename 'heavy_video.mp4' -> 'renamed_video.mp4' and verify in-place update in Storage Analysis report
            val heavyRef = initialReport.largestFiles.first { it.displayName == "heavy_video.mp4" }
            composeTestRule.runOnUiThread {
                viewModel.renameItem(heavyRef, "renamed_video.mp4")
            }
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.analysisReport.value?.largestFiles?.any { it.displayName == "renamed_video.mp4" } == true
            }
            val renamedRef = viewModel.analysisReport.value!!.largestFiles.first { it.displayName == "renamed_video.mp4" }

            // 3. Copy the renamed file from Storage Analysis -> verify clipboard bar appears with 'Select Destination Folder'
            composeTestRule.runOnUiThread {
                viewModel.transferEngine.copyToClipboard(listOf(renamedRef), ClipboardMode.COPY)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("analyzer_bottom_clipboard_bar").assertIsDisplayed()
            composeTestRule.onNodeWithTag("analyzer_choose_paste_folder_button").assertIsDisplayed()
            composeTestRule.runOnUiThread {
                viewModel.transferEngine.clearClipboard()
            }
            composeTestRule.waitForIdle()

            // 4. Delete one duplicate file ('dup_alpha.txt') and verify in-place removal from analysisReport without rescan
            val dupARef = viewModel.analysisReport.value!!.largestFiles.first { it.displayName == "dup_alpha.txt" }
            composeTestRule.runOnUiThread {
                viewModel.deleteItems(listOf(dupARef), moveToTrash = false)
            }
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.analysisReport.value?.largestFiles?.none { it.displayName == "dup_alpha.txt" } == true
            }
            assertFalse("Deleted duplicate file should be removed from disk", dupA.exists())
            assertTrue("Remaining file dup_beta.txt should still exist", dupB.exists())
            assertTrue("Duplicate group should be cleared once only 1 copy remains", viewModel.analysisReport.value!!.duplicateGroups.isEmpty())

            // 5. Open Containing Folder for 'renamed_video.mp4' -> verify navigation to parent folder in FileBrowserScreen
            composeTestRule.runOnUiThread {
                viewModel.openContainingFolder(renamedRef)
            }
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                viewModel.currentDestination.value == OpenFileDestination.INTERNAL_STORAGE &&
                    viewModel.browserState.value.currentDirectory?.uriOrPath == subFolder.absolutePath &&
                    !viewModel.browserState.value.isLoading
            }
            assertTrue(
                "Containing folder should list renamed_video.mp4",
                viewModel.browserState.value.items.any { it.displayName == "renamed_video.mp4" }
            )
            assertFalse(bigFile.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `adaptiveStorageDeviceName adapts storage devices names dynamically across compact medium and expanded screens`() {
        val dummyRef = FileReference(
            providerType = ProviderType.LOCAL,
            uriOrPath = "/dummy",
            displayName = "dummy",
            parentUriOrPath = "/",
            isDirectory = true,
            sizeBytes = 0L,
            lastModifiedEpochMs = 0L
        )

        val internalVolume = StorageVolumeInfo(
            id = "internal_primary",
            title = "Internal Shared Storage",
            subtitle = "/storage/emulated/0",
            rootReference = dummyRef,
            totalBytes = 64_000_000_000L,
            usedBytes = 20_000_000_000L,
            availableBytes = 44_000_000_000L
        )

        val sdCardVolume = StorageVolumeInfo(
            id = "sdcard_vol",
            title = "Physical SD Card (1234-5678)",
            subtitle = "/storage/1234-5678",
            rootReference = dummyRef,
            totalBytes = 32_000_000_000L,
            usedBytes = 10_000_000_000L,
            availableBytes = 22_000_000_000L,
            isRemovable = true
        )

        val usbVolume = StorageVolumeInfo(
            id = "usb_vol",
            title = "USB Storage Device (OTG)",
            subtitle = "/storage/ABCD-EF01",
            rootReference = dummyRef,
            totalBytes = 16_000_000_000L,
            usedBytes = 5_000_000_000L,
            availableBytes = 11_000_000_000L,
            isRemovable = true
        )

        // 1. Compact screen (< 360dp, e.g. 320dp small phone / split-screen)
        assertEquals("Main Storage", adaptiveStorageDeviceName(internalVolume, screenWidthDp = 320))
        assertEquals("SD Card", adaptiveStorageDeviceName(sdCardVolume, screenWidthDp = 320))
        assertEquals("USB Drive", adaptiveStorageDeviceName(usbVolume, screenWidthDp = 320))

        // 2. Medium screen (360dp - 599dp, e.g. 411dp standard phone)
        assertEquals("Internal Storage", adaptiveStorageDeviceName(internalVolume, screenWidthDp = 411))
        assertEquals("SD Card (1234-5678)", adaptiveStorageDeviceName(sdCardVolume, screenWidthDp = 411))
        assertEquals("USB Drive", adaptiveStorageDeviceName(usbVolume, screenWidthDp = 411))

        // 3. Expanded screen (>= 600dp, e.g. 800dp tablet / foldable / desktop)
        assertEquals("Internal Shared Storage", adaptiveStorageDeviceName(internalVolume, screenWidthDp = 800))
        assertEquals("Physical SD Card (1234-5678)", adaptiveStorageDeviceName(sdCardVolume, screenWidthDp = 800))
        assertEquals("USB Storage Device (OTG)", adaptiveStorageDeviceName(usbVolume, screenWidthDp = 800))
    }
}
