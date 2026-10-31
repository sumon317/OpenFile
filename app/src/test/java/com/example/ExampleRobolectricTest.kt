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
import com.example.core.filesystem.LocalFileSystemProvider
import com.example.core.network.FtpServerConfig
import com.example.core.network.FtpServerEngine
import com.example.core.network.RemoteNetworkClients
import com.example.core.security.KeystoreCredentialVault
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

        val renamedRef = provider.rename(noteRef, "renamed_hello.txt").getOrThrow()
        assertEquals("renamed_hello.txt", renamedRef.displayName)

        val deleted = provider.delete(folderRef).getOrThrow()
        assertTrue(deleted)
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
    fun `UI CUJ navigates Home Internal Storage Network and Settings`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Storage Devices").assertIsDisplayed()

        // Open Navigation Drawer and navigate to Network & FTP Server screen
        composeTestRule.onNodeWithTag("top_bar_drawer_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_network").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("network_screen").assertIsDisplayed()

        // Open Navigation Drawer and navigate to Internal Storage Browser
        composeTestRule.onNodeWithTag("top_bar_drawer_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_internal_storage").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("browser_search_input").assertIsDisplayed()

        // Open Navigation Drawer and navigate to Settings
        composeTestRule.onNodeWithTag("top_bar_drawer_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_settings").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }
}
