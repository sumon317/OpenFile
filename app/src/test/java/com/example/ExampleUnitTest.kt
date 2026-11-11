package com.example

import com.example.core.archive.ArchiveCreationFormat
import com.example.core.archive.ArchiveEngine
import com.example.core.archive.ArchiveEntryItem
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
        assertTrue(runCatching { LocalFileSystemProvider.sanitizeFileName("../hack") }.isFailure)
        assertTrue(runCatching { LocalFileSystemProvider.sanitizeFileName("bad\u0000name.txt") }.isFailure)
        assertTrue(runCatching { LocalFileSystemProvider.sanitizeFileName("line\nbreak.txt") }.isFailure)
        assertTrue(runCatching { LocalFileSystemProvider.sanitizeFileName("line\rbreak.txt") }.isFailure)
        assertTrue(runCatching { LocalFileSystemProvider.sanitizeFileName("   ") }.isFailure)
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

    @Test
    fun `NaturalOrderComparator sorts numeric filenames in natural human order 1 2 3 instead of 1 10 11`() {
        val files = listOf(
            "10.jpg",
            "1.jpg",
            "20.jpg",
            "2.jpg",
            "11.jpg",
            "3.jpg",
            "12.jpg",
            "photo_9.png",
            "photo_10.png",
            "photo_1.png"
        )
        val sorted = files.sortedWith(com.example.core.filesystem.NaturalOrderComparator)
        assertEquals(
            listOf(
                "1.jpg",
                "2.jpg",
                "3.jpg",
                "10.jpg",
                "11.jpg",
                "12.jpg",
                "20.jpg",
                "photo_1.png",
                "photo_9.png",
                "photo_10.png"
            ),
            sorted
        )

        // Verifies that numeric equality does not pre-empt differing suffix characters
        val suffixFiles = listOf("file1_b.txt", "file01_a.txt", "file1.txt", "file01.txt")
        val sortedSuffix = suffixFiles.sortedWith(com.example.core.filesystem.NaturalOrderComparator)
        assertEquals(
            listOf("file1.txt", "file01.txt", "file01_a.txt", "file1_b.txt"),
            sortedSuffix
        )
    }

    @Test
    fun `resolveArchiveVirtualItems displays archive entries in natural alphanumeric order`() {
        val entries = listOf(
            ArchiveEntryItem("pics/10.jpg", "10.jpg", false, 100L, 80L, 1000L, false),
            ArchiveEntryItem("pics/1.jpg", "1.jpg", false, 100L, 80L, 1000L, false),
            ArchiveEntryItem("pics/2.jpg", "2.jpg", false, 100L, 80L, 1000L, false),
            ArchiveEntryItem("pics/3.jpg", "3.jpg", false, 100L, 80L, 1000L, false),
            ArchiveEntryItem("pics/11.jpg", "11.jpg", false, 100L, 80L, 1000L, false)
        )
        val items = com.example.ui.screens.resolveArchiveVirtualItems(entries, subPath = "pics", searchQuery = "", isFlatView = false)
        assertEquals(
            listOf("1.jpg", "2.jpg", "3.jpg", "10.jpg", "11.jpg"),
            items.map { it.displayName }
        )
    }

    @Test
    fun `DocumentReaderEngine parses csv and tsv spreadsheets accurately`() {
        val tempDir = Files.createTempDirectory("openfile_csv_test").toFile()
        try {
            val csvFile = File(tempDir, "data.csv").apply {
                writeText("ID,Product,Price,Notes\n1,\"Mechanical Keyboard, RGB\",89.99,\"Top seller, prime\"\n2,Mouse,29.50,Wireless\n")
            }
            val sheetDoc = com.example.core.document.DocumentReaderEngine.readSpreadsheet(csvFile)
            assertEquals("data.csv", sheetDoc.title)
            assertEquals(1, sheetDoc.sheets.size)
            val sheet = sheetDoc.sheets[0]
            assertEquals(3, sheet.rows.size)
            assertEquals(listOf("ID", "Product", "Price", "Notes"), sheet.rows[0])
            assertEquals(listOf("1", "Mechanical Keyboard, RGB", "89.99", "Top seller, prime"), sheet.rows[1])
            assertEquals(listOf("2", "Mouse", "29.50", "Wireless"), sheet.rows[2])
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `DocumentReaderEngine reads cbz comic book archive and extracts pages`() {
        val tempDir = Files.createTempDirectory("openfile_cbz_test").toFile()
        try {
            val cbzFile = File(tempDir, "manga.cbz")
            java.util.zip.ZipOutputStream(java.io.FileOutputStream(cbzFile)).use { zos ->
                for (page in listOf("page_10.jpg", "page_1.jpg", "page_2.jpg")) {
                    zos.putNextEntry(java.util.zip.ZipEntry(page))
                    zos.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
                    zos.closeEntry()
                }
                zos.putNextEntry(java.util.zip.ZipEntry("metadata.xml"))
                zos.write("<xml/>".toByteArray())
                zos.closeEntry()
            }

            val cbz = com.example.core.document.DocumentReaderEngine.readCbz(cbzFile)
            assertEquals(3, cbz.pages.size)
            // Verify natural order: page_1, page_2, page_10
            assertEquals(listOf("page_1.jpg", "page_2.jpg", "page_10.jpg"), cbz.pages.map { it.entryName })

            val cacheDir = File(tempDir, "cache").apply { mkdirs() }
            val extracted = com.example.core.document.DocumentReaderEngine.extractCbzPage(cbzFile, cbz.pages[0], cacheDir)
            org.junit.Assert.assertNotNull(extracted)
            assertTrue(extracted!!.exists())
            assertTrue(extracted.length() > 0L)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `DocumentReaderEngine parses rich markdown text with headers code blocks lists checklists and tables`() {
        val mdText = """
            # Project Overview
            
            This is a **bold statement** with *italics* and `inline code`.
            
            > Essential architecture note
            
            - [x] Implement DOCX reader
            - [ ] Implement PPTX reader
            
            - Feature A
            - Feature B
            
            1. Step One
            2. Step Two
            
            ```kotlin
            fun main() {
                println("Hello OpenFile")
            }
            ```
            
            | Feature | Status |
            |---|---|
            | PDF | Supported |
            | CBZ | Supported |
            
            ---
        """.trimIndent()

        val mdDoc = com.example.core.document.DocumentReaderEngine.parseMarkdown(mdText, "test.md")
        assertEquals("test.md", mdDoc.title)

        val header = mdDoc.nodes.first { it is com.example.core.document.MarkdownNode.Header } as com.example.core.document.MarkdownNode.Header
        assertEquals(1, header.level)
        assertEquals("Project Overview", header.text)

        val codeBlock = mdDoc.nodes.first { it is com.example.core.document.MarkdownNode.CodeBlock } as com.example.core.document.MarkdownNode.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertTrue(codeBlock.code.contains("Hello OpenFile"))

        val checklist = mdDoc.nodes.filterIsInstance<com.example.core.document.MarkdownNode.ChecklistItem>()
        assertEquals(2, checklist.size)
        assertTrue(checklist[0].isChecked)
        assertFalse(checklist[1].isChecked)

        val table = mdDoc.nodes.first { it is com.example.core.document.MarkdownNode.Table } as com.example.core.document.MarkdownNode.Table
        assertEquals(listOf("Feature", "Status"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("PDF", "Supported"), table.rows[0])

        val p = mdDoc.nodes.first { it is com.example.core.document.MarkdownNode.Paragraph } as com.example.core.document.MarkdownNode.Paragraph
        assertTrue(p.inlines.any { it.isBold && it.text.contains("bold statement") })
        assertTrue(p.inlines.any { it.isItalic && it.text.contains("italics") })
        assertTrue(p.inlines.any { it.isCode && it.text.contains("inline code") })
    }

    @Test
    fun `DocumentReaderEngine correctly parses markdown conventions including bold italics nested inlines lists and tables`() {
        val sample = """
            # Header with **bold** and *italic*
            
            This has **bold text**, *italic text*, ***bold italic***, and __another bold__ with _another italic_.
            Here is **bold with *nested italic* inside** and `val x = 42`.
            Also supports ~~strikethrough~~, ==highlighted text==, and <b>HTML bold</b> with <i>HTML italic</i>.
            Visit [OpenFile GitHub](https://github.com/sumon317/OpenFile) or https://android.com.
            
            - **First item** with *emphasis*
            - Second item with `code`
            
            1. **Step 1:** Download
            2. *Step 2:* Install
            
            > **Important Note:** This is a *crucial* architecture tip.
            
            | Option | **Status** | *Speed* |
            |---|---|---|
            | `ExoPlayer` | **Active** | *1.5x* |
            
            ---
        """.trimIndent()

        val doc = com.example.core.document.DocumentReaderEngine.parseMarkdown(sample, "markdown_conventions.md")

        // 1. Header has parsed inlines
        val header = doc.nodes.filterIsInstance<com.example.core.document.MarkdownNode.Header>().first()
        assertEquals(1, header.level)
        assertTrue(header.inlines.any { it.isBold && it.text == "bold" })
        assertTrue(header.inlines.any { it.isItalic && it.text == "italic" })

        // 2. Paragraphs parse all inline conventions
        val p = doc.nodes.filterIsInstance<com.example.core.document.MarkdownNode.Paragraph>().first()
        assertTrue(p.inlines.any { it.isBold && it.text == "bold text" })
        assertTrue(p.inlines.any { it.isItalic && it.text == "italic text" })
        assertTrue(p.inlines.any { it.isBold && it.isItalic && it.text == "bold italic" })
        assertTrue(p.inlines.any { it.isBold && it.text == "another bold" })
        assertTrue(p.inlines.any { it.isItalic && it.text == "another italic" })
        // Nested bold + italic
        assertTrue(p.inlines.any { it.isBold && it.isItalic && it.text == "nested italic" })
        assertTrue(p.inlines.any { it.isCode && it.text == "val x = 42" })
        assertTrue(p.inlines.any { it.isStrikethrough && it.text == "strikethrough" })
        assertTrue(p.inlines.any { it.isHighlight && it.text == "highlighted text" })
        assertTrue(p.inlines.any { it.isBold && it.text == "HTML bold" })
        assertTrue(p.inlines.any { it.isItalic && it.text == "HTML italic" })
        // Links
        assertTrue(p.inlines.any { it.linkUrl == "https://github.com/sumon317/OpenFile" && it.text == "OpenFile GitHub" })
        assertTrue(p.inlines.any { it.linkUrl == "https://android.com" })

        // 3. List items have inlines
        val listItems = doc.nodes.filterIsInstance<com.example.core.document.MarkdownNode.ListItem>()
        val bullet1 = listItems[0]
        assertTrue(bullet1.inlines.any { it.isBold && it.text == "First item" })
        assertTrue(bullet1.inlines.any { it.isItalic && it.text == "emphasis" })

        val ordered1 = listItems.first { it.isOrdered }
        assertTrue(ordered1.inlines.any { it.isBold && it.text == "Step 1:" })

        // 4. Blockquote has inlines
        val blockquote = doc.nodes.filterIsInstance<com.example.core.document.MarkdownNode.Blockquote>().first()
        assertTrue(blockquote.inlines.any { it.isBold && it.text == "Important Note:" })
        assertTrue(blockquote.inlines.any { it.isItalic && it.text == "crucial" })

        // 5. Table has cell inlines
        val table = doc.nodes.filterIsInstance<com.example.core.document.MarkdownNode.Table>().first()
        assertTrue(table.headerInlines[1].any { it.isBold && it.text == "Status" })
        assertTrue(table.headerInlines[2].any { it.isItalic && it.text == "Speed" })
        assertTrue(table.rowInlines[0][0].any { it.isCode && it.text == "ExoPlayer" })
        assertTrue(table.rowInlines[0][1].any { it.isBold && it.text == "Active" })
        assertTrue(table.rowInlines[0][2].any { it.isItalic && it.text == "1.5x" })
    }

    @Test
    fun `FilePreviewState supports playlist sibling media navigation and index tracking`() {
        val song1 = com.example.core.model.FileReference(
            providerType = com.example.core.model.ProviderType.LOCAL,
            uriOrPath = "/storage/emulated/0/Music/song1.mp3",
            displayName = "song1.mp3",
            parentUriOrPath = "/storage/emulated/0/Music",
            isDirectory = false,
            sizeBytes = 6_400_000L,
            mimeType = "audio/mpeg"
        )
        val song2 = com.example.core.model.FileReference(
            providerType = com.example.core.model.ProviderType.LOCAL,
            uriOrPath = "/storage/emulated/0/Music/song2.mp3",
            displayName = "song2.mp3",
            parentUriOrPath = "/storage/emulated/0/Music",
            isDirectory = false,
            sizeBytes = 7_200_000L,
            mimeType = "audio/mpeg"
        )
        val song3 = com.example.core.model.FileReference(
            providerType = com.example.core.model.ProviderType.LOCAL,
            uriOrPath = "/storage/emulated/0/Music/song3.mp3",
            displayName = "song3.mp3",
            parentUriOrPath = "/storage/emulated/0/Music",
            isDirectory = false,
            sizeBytes = 5_100_000L,
            mimeType = "audio/mpeg"
        )

        val mediaList = listOf(song1, song2, song3)
        val state = com.example.ui.FilePreviewState(
            currentFile = song2,
            siblingMedia = mediaList,
            initialMediaIndex = 1
        )

        val currentIdx = state.siblingMedia.indexOfFirst { it.uriOrPath == state.currentFile.uriOrPath }
        assertEquals(1, currentIdx)
        assertTrue(currentIdx > 0) // hasPrevious
        assertTrue(currentIdx < state.siblingMedia.size - 1) // hasNext
        assertEquals("song1.mp3", state.siblingMedia[currentIdx - 1].displayName)
        assertEquals("song3.mp3", state.siblingMedia[currentIdx + 1].displayName)
        assertEquals("6.1 MB", song1.formattedSize)
    }
}


