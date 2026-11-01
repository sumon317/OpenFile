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
}
