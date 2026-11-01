package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.NetworkProfileEntity
import com.example.core.database.OpenFileDatabase
import com.example.core.filesystem.ClipboardMode
import com.example.core.filesystem.LocalFileSystemProvider
import com.example.core.filesystem.SafDocumentProvider
import com.example.core.filesystem.TransferQueueEngine
import com.example.core.network.FtpServerConfig
import com.example.core.network.FtpServerEngine
import com.example.core.network.RemoteNetworkClients
import com.example.core.security.KeystoreCredentialVault
import com.example.core.storage.StorageAnalyzerEngine
import com.example.ui.OpenFileDestination
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

        // 9. CLOUD_STORAGE
        composeTestRule.runOnUiThread {
            viewModel.navigateToDestination(OpenFileDestination.CLOUD_STORAGE)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cloud_storage_screen").assertIsDisplayed()

        // 10. SETTINGS
        composeTestRule.runOnUiThread {
            viewModel.navigateToDestination(OpenFileDestination.SETTINGS)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // 11. Return HOME via Drawer
        composeTestRule.onNodeWithTag("top_bar_drawer_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_home").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Storage Devices").assertIsDisplayed()
    }

    @Test
    fun `UI CUJ tests theme cycling view mode toggle and directory status footer`() {
        composeTestRule.waitForIdle()

        // Cycle theme modes via TopAppBar button (SYSTEM -> LIGHT -> DARK -> AMOLED -> SYSTEM)
        composeTestRule.onNodeWithTag("top_bar_theme_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("top_bar_theme_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("top_bar_theme_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Internal Storage Browser
        composeTestRule.onNodeWithTag("top_bar_drawer_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_internal_storage").performScrollTo().performClick()
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
}
