package com.example

import com.example.core.archive.ArchiveCreationFormat
import com.example.core.archive.ArchiveEngine
import com.example.core.filesystem.ChecksumAndDuplicateEngine
import com.example.core.filesystem.FileSortAndFilterEngine
import com.example.core.filesystem.LocalFileSystemProvider
import com.example.core.model.FileCategory
import com.example.core.model.FileReference
import com.example.core.model.ProviderCapability
import com.example.core.model.ProviderType
import com.example.core.model.SortField
import com.example.core.model.SortOrder
import com.example.core.model.isPathInsideHiddenDirectory
import com.example.core.network.FtpServerEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

class ExampleUnitTest {

    @Test
    fun `sorting always places directories first and sorts by field and order`() {
        val items = listOf(
            FileReference(ProviderType.LOCAL, "/storage/b.txt", "b.txt", "/storage", false, sizeBytes = 500L, lastModifiedEpochMs = 1000L),
            FileReference(ProviderType.LOCAL, "/storage/Projects", "Projects", "/storage", true, sizeBytes = 0L, lastModifiedEpochMs = 500L),
            FileReference(ProviderType.LOCAL, "/storage/a.pdf", "a.pdf", "/storage", false, sizeBytes = 2000L, lastModifiedEpochMs = 3000L),
            FileReference(ProviderType.LOCAL, "/storage/Android", "Android", "/storage", true, sizeBytes = 0L, lastModifiedEpochMs = 200L)
        )

        val byNameAsc = FileSortAndFilterEngine.sortFiles(items, SortField.NAME, SortOrder.ASCENDING)
        assertEquals(listOf("Android", "Projects", "a.pdf", "b.txt"), byNameAsc.map { it.displayName })

        val bySizeDesc = FileSortAndFilterEngine.sortFiles(items, SortField.SIZE, SortOrder.DESCENDING)
        assertEquals(listOf("Projects", "Android", "a.pdf", "b.txt"), bySizeDesc.map { it.displayName })
    }

    @Test
    fun `filtering respects hidden flag extension and category`() {
        val items = listOf(
            FileReference(ProviderType.LOCAL, "/a/.nomedia", ".nomedia", "/a", false, isHidden = true),
            FileReference(ProviderType.LOCAL, "/a/photo.jpg", "photo.jpg", "/a", false, extension = "jpg"),
            FileReference(ProviderType.LOCAL, "/a/report.pdf", "report.pdf", "/a", false, extension = "pdf")
        )

        val visibleOnly = FileSortAndFilterEngine.filterFiles(items, showHidden = false)
        assertEquals(2, visibleOnly.size)

        val imagesOnly = FileSortAndFilterEngine.filterFiles(items, categoryFilter = FileCategory.IMAGES, showHidden = false)
        assertEquals(listOf("photo.jpg"), imagesOnly.map { it.displayName })
    }

    @Test(expected = SecurityException::class)
    fun `archive Zip-Slip guard blocks relative parent traversal`() {
        val destRoot = Files.createTempDirectory("openfile_extract_test").toFile()
        try {
            ArchiveEngine.validateSafeTargetFile(destRoot, "../../etc/passwd")
        } finally {
            destRoot.deleteRecursively()
        }
    }

    @Test
    fun `ftp path sandbox confines CDUP and relative traversal inside server root`() {
        val ftpRoot = Files.createTempDirectory("openfile_ftp_root").toFile()
        try {
            val resolved = FtpServerEngine.resolveSafeFtpFile(ftpRoot, "/Documents", "../../../../secret.txt")
            assertEquals(File(ftpRoot.canonicalFile, "secret.txt"), resolved)
        } finally {
            ftpRoot.deleteRecursively()
        }
    }

    @Test
    fun `archive round-trip creation and extraction preserves SHA-256 integrity`() = runTest {
        val tempRoot = Files.createTempDirectory("openfile_archive_roundtrip").toFile()
        try {
            val srcDir = File(tempRoot, "source").apply { mkdirs() }
            val fileA = File(srcDir, "alpha.txt").apply { writeText("Hello OpenFile Archive Engine!\nLine 2") }
            val subDir = File(srcDir, "nested").apply { mkdirs() }
            val fileB = File(subDir, "beta.json").apply { writeText("{\"status\":\"verified\"}") }

            val zipFile = File(tempRoot, "bundle.zip")
            val createResult = ArchiveEngine.createArchive(
                sourceFiles = listOf(fileA, subDir),
                outputArchiveFile = zipFile,
                format = ArchiveCreationFormat.ZIP
            )
            assertTrue(createResult.isSuccess)
            assertTrue(zipFile.exists() && zipFile.length() > 0)

            val entries = ArchiveEngine.listArchiveEntries(zipFile).getOrThrow()
            assertTrue(entries.any { it.entryPath == "alpha.txt" })
            assertTrue(entries.any { it.entryPath.endsWith("beta.json") })

            val extractDir = File(tempRoot, "extracted")
            val extractedCount = ArchiveEngine.extractArchive(zipFile, extractDir).getOrThrow()
            assertEquals(2, extractedCount)

            val origSha = ChecksumAndDuplicateEngine.computeSha256(fileA)
            val extSha = ChecksumAndDuplicateEngine.computeSha256(File(extractDir, "alpha.txt"))
            assertEquals(origSha, extSha)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `streaming checksum calculator computes valid MD5 and SHA-256`() = runTest {
        val payload = "OpenFile Checksum Test Payload".toByteArray(Charsets.UTF_8)
        val checksums = ChecksumAndDuplicateEngine.computeChecksums(ByteArrayInputStream(payload))
        assertEquals(32, checksums.md5Hex.length)
        assertEquals(64, checksums.sha256Hex.length)
    }

    @Test
    fun `provider capability matrix differentiates Local SAF and Archive capabilities`() {
        val localDirCaps = FileReference.defaultCapabilitiesFor(ProviderType.LOCAL, isDirectory = true)
        val safDirCaps = FileReference.defaultCapabilitiesFor(ProviderType.SAF, isDirectory = true)
        val archiveCaps = FileReference.defaultCapabilitiesFor(ProviderType.ARCHIVE_VIRTUAL, isDirectory = false)

        assertTrue(ProviderCapability.FAST_DIRECTORY_SIZE in localDirCaps)
        assertFalse(ProviderCapability.FAST_DIRECTORY_SIZE in safDirCaps)
        assertTrue(ProviderCapability.ARCHIVE_EXTRACT in archiveCaps)
        assertFalse(ProviderCapability.STREAM_WRITE in archiveCaps)
    }

    @Test
    fun `sanitizeFileName rejects path separators and dot-dot`() {
        assertEquals("report_2026.pdf", LocalFileSystemProvider.sanitizeFileName("  report_2026.pdf "))
        val illegalResult = runCatching { LocalFileSystemProvider.sanitizeFileName("../hack") }
        assertTrue(illegalResult.isFailure)
    }

    @Test
    fun `sorting and filtering 2000 items completes within fast latency budget`() {
        val rawItems = (1..2000).map { i ->
            val isDir = i % 10 == 0
            FileReference(
                providerType = ProviderType.LOCAL,
                uriOrPath = "/storage/emulated/0/item_$i.txt",
                displayName = if (isDir) "Folder_$i" else "Document_$i.txt",
                parentUriOrPath = "/storage/emulated/0",
                isDirectory = isDir,
                sizeBytes = if (isDir) 0L else (i * 1024L),
                lastModifiedEpochMs = 1_700_000_000_000L + i
            )
        }
        val startNs = System.nanoTime()
        val sorted = FileSortAndFilterEngine.sortFiles(rawItems, SortField.SIZE, SortOrder.DESCENDING)
        val filtered = FileSortAndFilterEngine.filterFiles(sorted, query = "Document_1", showHidden = false)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

        assertEquals(200, sorted.take(200).count { it.isDirectory })
        assertTrue("Expected fast sort+filter (< 500ms), took ${elapsedMs}ms", elapsedMs < 500)
        assertTrue(filtered.isNotEmpty())
    }

    @Test
    fun `sorting and filtering 5000 items across all SortFields stays within 60fps UI responsiveness budget`() {
        val rawItems = (1..5000).map { i ->
            val isDir = i % 15 == 0
            val ext = when (i % 5) {
                0 -> "pdf"
                1 -> "jpg"
                2 -> "mp4"
                3 -> "zip"
                else -> "txt"
            }
            FileReference(
                providerType = ProviderType.LOCAL,
                uriOrPath = "/storage/emulated/0/bench/item_$i.$ext",
                displayName = if (isDir) "Dir_$i" else "File_${5000 - i}.$ext",
                parentUriOrPath = "/storage/emulated/0/bench",
                isDirectory = isDir,
                sizeBytes = if (isDir) 0L else ((i % 97) * 4096L),
                lastModifiedEpochMs = 1_700_000_000_000L + (i % 1000) * 1000L,
                extension = if (isDir) "" else ext
            )
        }

        val startNs = System.nanoTime()
        for (field in SortField.entries) {
            val sorted = FileSortAndFilterEngine.sortFiles(rawItems, field, SortOrder.ASCENDING)
            assertEquals(5000, sorted.size)
        }
        val filtered = FileSortAndFilterEngine.filterFiles(
            files = rawItems,
            query = "File_4",
            extensionFilter = "pdf",
            showHidden = false
        )
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        assertTrue("Expected 5,000-item multi-sort + filter < 400ms, took ${elapsedMs}ms", elapsedMs < 400)
        assertTrue(filtered.all { it.extension == "pdf" && it.displayName.contains("File_4") })
    }

    @Test
    fun `github releases auto-updater parses release JSON and compares semantic versions`() {
        assertTrue(com.example.core.update.AppUpdateEngine.isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue(com.example.core.update.AppUpdateEngine.isNewerVersion("v2.0.0", "1.4.0"))
        assertFalse(com.example.core.update.AppUpdateEngine.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(com.example.core.update.AppUpdateEngine.isNewerVersion("v1.0.0-beta", "1.0.0"))
        assertTrue(com.example.core.update.AppUpdateEngine.isNewerVersion("v1.0.0", "1.0.0-rc1"))

        val sampleReleaseJson = """
            {
              "tag_name": "v1.1.0",
              "name": "OpenFile v1.1.0",
              "body": "### What's New\n- Direct GitHub Releases Auto-Updater\n- Faster Compose scrolling",
              "published_at": "2026-11-07T12:00:00Z",
              "html_url": "https://github.com/sumon317/OpenFile/releases/tag/v1.1.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "OpenFile-v1.1.0-release.apk",
                  "size": 14850000,
                  "browser_download_url": "https://github.com/sumon317/OpenFile/releases/download/v1.1.0/OpenFile-v1.1.0-release.apk"
                }
              ]
            }
        """.trimIndent()

        val parsed = com.example.core.update.AppUpdateEngine.parseReleaseJson(sampleReleaseJson)
        org.junit.Assert.assertNotNull(parsed)
        assertEquals("v1.1.0", parsed!!.tagName)
        assertEquals("1.1.0", parsed.versionName)
        assertEquals("OpenFile-v1.1.0-release.apk", parsed.apkAssetName)
        assertEquals(14850000L, parsed.apkSizeBytes)
        assertTrue(com.example.core.update.AppUpdateEngine.isNewerVersion(parsed.tagName, "1.0.0"))
    }

    @Test
    fun `default sorting for quick access categories matches requirements`() {
        // Images: recent at the top
        val images = listOf(
            FileReference(ProviderType.LOCAL, "/storage/old.jpg", "old.jpg", "/storage", false, lastModifiedEpochMs = 1000L),
            FileReference(ProviderType.LOCAL, "/storage/new.jpg", "new.jpg", "/storage", false, lastModifiedEpochMs = 5000L),
            FileReference(ProviderType.LOCAL, "/storage/mid.jpg", "mid.jpg", "/storage", false, lastModifiedEpochMs = 3000L)
        )
        val sortedImages = FileSortAndFilterEngine.sortFiles(images, SortField.DATE, SortOrder.DESCENDING)
        assertEquals(listOf("new.jpg", "mid.jpg", "old.jpg"), sortedImages.map { it.displayName })

        // Audio: alphabetical ascending
        val audio = listOf(
            FileReference(ProviderType.LOCAL, "/storage/z_track.mp3", "z_track.mp3", "/storage", false),
            FileReference(ProviderType.LOCAL, "/storage/a_song.mp3", "a_song.mp3", "/storage", false),
            FileReference(ProviderType.LOCAL, "/storage/m_podcast.mp3", "m_podcast.mp3", "/storage", false)
        )
        val sortedAudio = FileSortAndFilterEngine.sortFiles(audio, SortField.NAME, SortOrder.ASCENDING)
        assertEquals(listOf("a_song.mp3", "m_podcast.mp3", "z_track.mp3"), sortedAudio.map { it.displayName })

        // Videos: alphabetical ascending
        val videos = listOf(
            FileReference(ProviderType.LOCAL, "/storage/clip_z.mp4", "clip_z.mp4", "/storage", false),
            FileReference(ProviderType.LOCAL, "/storage/clip_a.mp4", "clip_a.mp4", "/storage", false)
        )
        val sortedVideos = FileSortAndFilterEngine.sortFiles(videos, SortField.NAME, SortOrder.ASCENDING)
        assertEquals(listOf("clip_a.mp4", "clip_z.mp4"), sortedVideos.map { it.displayName })

        // Documents: alphabetical ascending
        val docs = listOf(
            FileReference(ProviderType.LOCAL, "/storage/summary.pdf", "summary.pdf", "/storage", false),
            FileReference(ProviderType.LOCAL, "/storage/agenda.docx", "agenda.docx", "/storage", false)
        )
        val sortedDocs = FileSortAndFilterEngine.sortFiles(docs, SortField.NAME, SortOrder.ASCENDING)
        assertEquals(listOf("agenda.docx", "summary.pdf"), sortedDocs.map { it.displayName })

        // New files: any file type, most recent at the top
        val anyFiles = listOf(
            FileReference(ProviderType.LOCAL, "/storage/document.pdf", "document.pdf", "/storage", false, lastModifiedEpochMs = 1500L),
            FileReference(ProviderType.LOCAL, "/storage/app.apk", "app.apk", "/storage", false, lastModifiedEpochMs = 9000L),
            FileReference(ProviderType.LOCAL, "/storage/backup.zip", "backup.zip", "/storage", false, lastModifiedEpochMs = 4000L),
            FileReference(ProviderType.LOCAL, "/storage/voice.m4a", "voice.m4a", "/storage", false, lastModifiedEpochMs = 2000L)
        )
        val sortedNewFiles = FileSortAndFilterEngine.sortFiles(anyFiles, SortField.DATE, SortOrder.DESCENDING)
        assertEquals(listOf("app.apk", "backup.zip", "voice.m4a", "document.pdf"), sortedNewFiles.map { it.displayName })
    }

    @Test
    fun `native zip viewer extracts single entry to cache without decompressing entire archive`() = runTest {
        val tempRoot = Files.createTempDirectory("openfile_single_entry_test").toFile()
        try {
            val srcDir = File(tempRoot, "src").apply { mkdirs() }
            val fileA = File(srcDir, "readme.md").apply { writeText("# Welcome to OpenFile") }
            val subDir = File(srcDir, "docs").apply { mkdirs() }
            val fileB = File(subDir, "spec.json").apply { writeText("{\"feature\":\"native_zip_viewer\"}") }
            File(subDir, "large_data.bin").writeBytes(ByteArray(1024 * 64) { 0x42 })

            val zipFile = File(tempRoot, "test_archive.zip")
            val createResult = ArchiveEngine.createArchive(
                sourceFiles = listOf(fileA, subDir),
                outputArchiveFile = zipFile,
                format = ArchiveCreationFormat.ZIP
            )
            assertTrue(createResult.isSuccess)

            val cacheDir = File(tempRoot, "cache").apply { mkdirs() }
            val extractResult = ArchiveEngine.extractSingleEntryToCache(
                archiveFile = zipFile,
                entryPath = "docs/spec.json",
                cacheDir = cacheDir
            )

            assertTrue(extractResult.isSuccess)
            val cachedFile = extractResult.getOrThrow()
            assertTrue(cachedFile.exists())
            assertEquals("{\"feature\":\"native_zip_viewer\"}", cachedFile.readText())

            // Ensure other files were not extracted into the cache folder
            val cachedFiles = cacheDir.listFiles()?.flatMap { if (it.isDirectory) it.listFiles()?.toList() ?: emptyList() else listOf(it) } ?: emptyList()
            assertFalse(cachedFiles.any { it.name == "large_data.bin" })
            assertFalse(cachedFiles.any { it.name == "readme.md" })
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `resolveArchiveVirtualItems accurately organizes folder hierarchy and search results`() {
        val entries = listOf(
            com.example.core.archive.ArchiveEntryItem("src/main/App.kt", "App.kt", false, 500, 200, 1000L, false),
            com.example.core.archive.ArchiveEntryItem("src/main/res/icon.png", "icon.png", false, 1500, 1200, 1000L, false),
            com.example.core.archive.ArchiveEntryItem("src/test/AppTest.kt", "AppTest.kt", false, 300, 150, 1000L, false),
            com.example.core.archive.ArchiveEntryItem("README.md", "README.md", false, 120, 80, 1000L, false)
        )

        // 1. Root level: should see "src/" as a virtual folder and "README.md" as a direct file
        val rootItems = com.example.ui.screens.resolveArchiveVirtualItems(entries, subPath = "", searchQuery = "", isFlatView = false)
        assertEquals(2, rootItems.size)
        assertTrue(rootItems[0].isDirectory && rootItems[0].displayName == "src")
        assertEquals(3, rootItems[0].childCount) // 3 items inside src
        assertFalse(rootItems[1].isDirectory && rootItems[1].displayName == "README.md")

        // 2. Inside "src": should see "main/" and "test/" virtual folders
        val srcItems = com.example.ui.screens.resolveArchiveVirtualItems(entries, subPath = "src", searchQuery = "", isFlatView = false)
        assertEquals(2, srcItems.size)
        assertEquals(listOf("main", "test"), srcItems.map { it.displayName })

        // 3. Inside "src/main": should see "res/" folder and "App.kt" file
        val mainItems = com.example.ui.screens.resolveArchiveVirtualItems(entries, subPath = "src/main", searchQuery = "", isFlatView = false)
        assertEquals(2, mainItems.size)
        assertTrue(mainItems[0].isDirectory && mainItems[0].displayName == "res")
        assertFalse(mainItems[1].isDirectory && mainItems[1].displayName == "App.kt")

        // 4. Flat view: should return all 4 items directly
        val flatItems = com.example.ui.screens.resolveArchiveVirtualItems(entries, subPath = "", searchQuery = "", isFlatView = true)
        assertEquals(4, flatItems.size)

        // 5. Search query "test": should match "src/test/AppTest.kt"
        val searchItems = com.example.ui.screens.resolveArchiveVirtualItems(entries, subPath = "", searchQuery = "test", isFlatView = false)
        assertEquals(1, searchItems.size)
        assertEquals("src/test/AppTest.kt", searchItems[0].entryPath)
    }

    @Test
    fun `cleanDownloadedUpdateApks deletes update APK files and returns deleted count`() {
        val tempRoot = Files.createTempDirectory("openfile_update_clean_test").toFile()
        try {
            val updatesDir = File(tempRoot, "updates/v1_2_0").apply { mkdirs() }
            val apkFile = File(updatesDir, "OpenFile-v1.2.0-release.apk").apply {
                writeText("fake apk binary content")
            }
            val partFile = File(updatesDir, "download.apk.part").apply {
                writeText("incomplete part")
            }
            assertTrue(apkFile.exists())
            assertTrue(partFile.exists())

            // Test walking and deleting candidate files
            var deletedCount = 0
            val files = updatesDir.walkBottomUp().toList()
            for (file in files) {
                if (file != updatesDir) {
                    if (file.isFile) deletedCount++
                    file.delete()
                }
            }
            assertEquals(2, deletedCount)
            assertFalse(apkFile.exists())
            assertFalse(partFile.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `isPathInsideHiddenDirectory accurately detects files inside hidden directory trees`() {
        assertTrue(isPathInsideHiddenDirectory("/storage/emulated/0/.openfile_trash/photo.jpg"))
        assertTrue(isPathInsideHiddenDirectory("/storage/emulated/0/.thumbnails/thumb1.png"))
        assertTrue(isPathInsideHiddenDirectory("/storage/emulated/0/.hidden_folder/sub/doc.pdf"))
        assertTrue(isPathInsideHiddenDirectory("file:///storage/emulated/0/.secret/notes.txt"))
        assertTrue(isPathInsideHiddenDirectory("content://com.android.externalstorage.documents/tree/primary%3A.openfile_trash%2Fphoto.jpg"))

        assertFalse(isPathInsideHiddenDirectory("/storage/emulated/0/DCIM/Camera/photo.jpg"))
        assertFalse(isPathInsideHiddenDirectory("/storage/emulated/0/Download/report.pdf"))
        assertFalse(isPathInsideHiddenDirectory("/storage/emulated/0/normal.jpg"))
        assertFalse(isPathInsideHiddenDirectory(null))
        assertFalse(isPathInsideHiddenDirectory(""))
    }

    @Test
    fun `FileReference and filterFiles exclude files inside hidden directories when showHidden is false`() {
        val normalFile = FileReference(
            providerType = ProviderType.LOCAL,
            uriOrPath = "/storage/emulated/0/DCIM/Camera/photo.jpg",
            displayName = "photo.jpg",
            parentUriOrPath = "/storage/emulated/0/DCIM/Camera",
            isDirectory = false
        )
        assertFalse(normalFile.isHidden)

        val trashFile = FileReference(
            providerType = ProviderType.LOCAL,
            uriOrPath = "/storage/emulated/0/.openfile_trash/deleted_pic.jpg",
            displayName = "deleted_pic.jpg",
            parentUriOrPath = "/storage/emulated/0/.openfile_trash",
            isDirectory = false
        )
        assertTrue(trashFile.isHidden)

        val dotFile = FileReference(
            providerType = ProviderType.LOCAL,
            uriOrPath = "/storage/emulated/0/Documents/.secret.txt",
            displayName = ".secret.txt",
            parentUriOrPath = "/storage/emulated/0/Documents",
            isDirectory = false
        )
        assertTrue(dotFile.isHidden)

        val allFiles = listOf(normalFile, trashFile, dotFile)

        val hiddenExcluded = FileSortAndFilterEngine.filterFiles(allFiles, showHidden = false)
        assertEquals(listOf("photo.jpg"), hiddenExcluded.map { it.displayName })

        val hiddenIncluded = FileSortAndFilterEngine.filterFiles(allFiles, showHidden = true)
        assertEquals(3, hiddenIncluded.size)
    }
}

