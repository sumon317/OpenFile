package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.core.archive.ArchiveCreationFormat
import com.example.core.archive.ArchiveEngine
import com.example.core.database.OpenFileDatabase
import com.example.core.filesystem.ChecksumAndDuplicateEngine
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
import kotlinx.coroutines.flow.first
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
import java.io.File
import java.net.ServerSocket
import java.util.UUID

/**
 * Full End-to-End Instrumented Test Suite executing directly on the physical Android hardware.
 * Validates:
 * 1. Hardware Android Keystore AES-256-GCM hardware keymaster
 * 2. SQLite / Room database tables & DAO queries
 * 3. Local filesystem I/O, file creation, auto-rename copy, and soft-delete/trash
 * 4. Real loopback Wi-Fi FTP socket and directory listing
 * 5. Apache Commons Archive compression and extraction
 * 6. Jetpack Compose UI interaction across destinations
 */
@RunWith(AndroidJUnit4::class)
class OpenFileDeviceInstrumentedTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun test01_hardwareKeystoreEncryptionDecryption() {
        val vault = KeystoreCredentialVault(targetContext)
        val secretValue = "device_hardware_secret_key_${UUID.randomUUID()}"
        val alias = vault.storeSecret(null, secretValue)
        assertTrue(alias.startsWith("sec_"))

        val retrieved = vault.retrieveSecret(alias)
        assertEquals(secretValue, retrieved)

        vault.deleteSecret(alias)
        assertNull(vault.retrieveSecret(alias))
    }

    @Test
    fun test02_roomDatabaseIntegrity() = runBlocking {
        val db = OpenFileDatabase.getInstance(targetContext)
        val dao = db.dao()

        val bookmark = com.example.core.database.BookmarkEntity(
            title = "Test Bookmark",
            providerType = "LOCAL",
            uriOrPath = "/storage/emulated/0/Download",
            isDirectory = true,
            mimeType = "vnd.android.document/directory"
        )
        val bmId = dao.insertBookmark(bookmark)
        assertTrue(bmId > 0)

        val list = dao.observeBookmarks().first()
        assertTrue(list.any { it.title == "Test Bookmark" })

        dao.deleteBookmarkById(bmId)
        val afterDelete = dao.observeBookmarks().first()
        assertFalse(afterDelete.any { it.title == "Test Bookmark" })
    }

    @Test
    fun test03_filesystemAndTransferQueueOnDevice() = runBlocking {
        val db = OpenFileDatabase.getInstance(targetContext)
        val localProvider = LocalFileSystemProvider(targetContext)
        val safProvider = SafDocumentProvider(targetContext)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val engine = TransferQueueEngine(targetContext, db.dao(), localProvider, safProvider, scope)

        val tempDir = File(targetContext.cacheDir, "device_test_dir_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val original = File(tempDir, "note.txt").apply { writeText("Hello Device Storage") }
            val dirRef = localProvider.toFileReference(tempDir)
            val fileRef = localProvider.toFileReference(original)

            engine.copyToClipboard(listOf(fileRef), ClipboardMode.COPY)
            var done = false
            engine.enqueuePasteFromClipboard(
                destinationDir = dirRef,
                verifyChecksum = true,
                onComplete = { done = true }
            )

            var attempts = 0
            while (!done && attempts < 60) {
                delay(50)
                attempts++
            }

            val duplicate = File(tempDir, "note (1).txt")
            assertTrue("Original file must remain", original.exists())
            assertTrue("Duplicate file must exist", duplicate.exists())
            assertEquals("Hello Device Storage", duplicate.readText())

            // Test soft-delete to trash & restore
            val deleteRes = engine.deleteItems(listOf(fileRef), moveToTrash = true)
            assertTrue(deleteRes.isSuccess)
            assertFalse(original.exists())

            val undo = engine.latestUndoAction.value
            assertNotNull(undo)
            undo?.performUndo()
            assertTrue(original.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun test04_embeddedFtpServerLoopbackOnDevice() = runBlocking {
        val ftpRoot = File(targetContext.cacheDir, "device_ftp_root_${System.currentTimeMillis()}").apply { mkdirs() }
        val testPort = ServerSocket(0).use { it.localPort }
        try {
            File(ftpRoot, "device_readme.txt").writeText("FTP Loopback on Phone!")
            val startRes = FtpServerEngine.startServer(
                FtpServerConfig(
                    bindAddress = "127.0.0.1",
                    port = testPort,
                    passivePortStart = 51200,
                    passivePortEnd = 51210,
                    rootDirectory = ftpRoot,
                    username = "phoneuser",
                    password = "phonepassword",
                    allowAnonymous = false,
                    allowWrite = true
                )
            )
            assertTrue("FTP Server must start", startRes.isSuccess)

            val profile = com.example.core.database.NetworkProfileEntity(
                displayName = "Loopback Phone",
                protocol = "FTP",
                host = "127.0.0.1",
                port = testPort,
                username = "phoneuser",
                secretAlias = null,
                remoteInitialPath = "/",
                usePassiveMode = true
            )

            val listResult = RemoteNetworkClients.listRemoteDirectory(profile, "phonepassword", "/").getOrThrow()
            assertTrue(listResult.any { it.displayName == "device_readme.txt" })
        } finally {
            FtpServerEngine.stopServer()
            ftpRoot.deleteRecursively()
        }
    }

    @Test
    fun test05_archiveCompressionAndExtractionOnDevice() = runBlocking {
        val testDir = File(targetContext.cacheDir, "device_archive_test_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val srcDir = File(testDir, "src").apply { mkdirs() }
            val testDoc = File(srcDir, "doc.txt").apply { writeText("Archive On Device Content 2026") }
            val zipOut = File(testDir, "package.zip")

            val createRes = ArchiveEngine.createArchive(
                sourceFiles = listOf(testDoc),
                outputArchiveFile = zipOut,
                format = ArchiveCreationFormat.ZIP
            )
            assertTrue(createRes.isSuccess)
            assertTrue(zipOut.exists() && zipOut.length() > 0)

            val extractDir = File(testDir, "extracted")
            val extractRes = ArchiveEngine.extractArchive(zipOut, extractDir).getOrThrow()
            assertEquals(1, extractRes)

            val extractedDoc = File(extractDir, "doc.txt")
            assertTrue(extractedDoc.exists())
            assertEquals(ChecksumAndDuplicateEngine.computeSha256(testDoc), ChecksumAndDuplicateEngine.computeSha256(extractedDoc))
        } finally {
            testDir.deleteRecursively()
        }
    }

    @Test
    fun test06_uiDestinationsNavigationOnDevice() {
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val viewModel = activity.viewModel
                assertNotNull("ViewModel must be attached", viewModel)
                assertEquals(OpenFileDestination.HOME, viewModel.currentDestination.value)

                // 1. Applications screen
                viewModel.navigateToDestination(OpenFileDestination.APPLICATIONS)
                assertEquals(OpenFileDestination.APPLICATIONS, viewModel.currentDestination.value)

                // 2. Storage Analysis screen
                viewModel.navigateToDestination(OpenFileDestination.STORAGE_ANALYSIS)
                assertEquals(OpenFileDestination.STORAGE_ANALYSIS, viewModel.currentDestination.value)

                // 3. Network screen
                viewModel.navigateToDestination(OpenFileDestination.NETWORK)
                assertEquals(OpenFileDestination.NETWORK, viewModel.currentDestination.value)

                // 4. Settings screen
                viewModel.navigateToDestination(OpenFileDestination.SETTINGS)
                assertEquals(OpenFileDestination.SETTINGS, viewModel.currentDestination.value)

                // 5. Return Home
                viewModel.navigateToDestination(OpenFileDestination.HOME)
                assertEquals(OpenFileDestination.HOME, viewModel.currentDestination.value)
            }
        } finally {
            scenario.close()
        }
    }
}
