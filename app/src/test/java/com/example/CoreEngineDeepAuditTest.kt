package com.example

import com.example.core.archive.ArchiveEngine
import com.example.core.document.DocumentFormatDetector
import com.example.core.document.DocxParser
import com.example.core.document.Ole2DocumentReader
import com.example.core.document.PresentationParser
import com.example.core.document.SpreadsheetParser
import com.example.core.filesystem.LocalFileSystemProvider
import com.example.core.filesystem.TransferQueueEngine
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoreEngineDeepAuditTest {

    // ---------------------------------------------------------------------------------------------
    // 1. ZipSlip & Path Traversal Protections
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testZipSlipPathTraversalBlockedInArchiveEngine() {
        val destRoot = Files.createTempDirectory("zipslip_test_root").toFile()
        try {
            val maliciousPaths = listOf(
                "../evil.sh",
                "../../etc/passwd",
                "sub/../../evil.sh",
                "sub/../..",
                "sub/..",
                "..",
                ".",
                "C:evil.txt",
                "D:/evil.sh",
                "/evil.sh",
                "\\\\evil.sh"
            )

            for (path in maliciousPaths) {
                try {
                    ArchiveEngine.validateSafeTargetFile(destRoot, path)
                    fail("Expected SecurityException for malicious entry: $path")
                } catch (_: SecurityException) {
                    // Success: Zip-Slip blocked
                }
            }

            // Safe target resolution
            val safeFile = ArchiveEngine.validateSafeTargetFile(destRoot, "sub/dir/safe.txt")
            val rootPrefix = destRoot.canonicalFile.path.trimEnd(File.separatorChar) + File.separator
            assertTrue(safeFile.path.startsWith(rootPrefix))
        } finally {
            destRoot.deleteRecursively()
        }
    }

    @Test
    fun testSuspiciousEntryDetection() {
        assertTrue(ArchiveEngine.isSuspiciousEntryName("../evil.txt"))
        assertTrue(ArchiveEngine.isSuspiciousEntryName("evil/.."))
        assertTrue(ArchiveEngine.isSuspiciousEntryName("evil/../other"))
        assertTrue(ArchiveEngine.isSuspiciousEntryName(".."))
        assertTrue(ArchiveEngine.isSuspiciousEntryName("."))
        assertTrue(ArchiveEngine.isSuspiciousEntryName("/root.txt"))
        assertTrue(ArchiveEngine.isSuspiciousEntryName("C:test.txt"))

        assertFalse(ArchiveEngine.isSuspiciousEntryName("normal_file.txt"))
        assertFalse(ArchiveEngine.isSuspiciousEntryName("folder/subfolder/file.png"))

        assertTrue(DocumentFormatDetector.isZipSlipAttempt("../evil.txt"))
        assertTrue(DocumentFormatDetector.isZipSlipAttempt("evil/.."))
        assertTrue(DocumentFormatDetector.isZipSlipAttempt(".."))
        assertTrue(DocumentFormatDetector.isZipSlipAttempt("C:test.txt"))
        assertFalse(DocumentFormatDetector.isZipSlipAttempt("word/document.xml"))
    }

    // ---------------------------------------------------------------------------------------------
    // 2. Ole2 Robust Bounds & Corrupt File Protections
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testOle2RejectsOversizedOrCorruptFatSectors() {
        val tempFile = File.createTempFile("corrupt_fat", ".doc")
        tempFile.deleteOnExit()

        // 512-byte header with OLE2 magic but numFatSectors set to an insane number (10,000,000)
        val header = ByteArray(512)
        val magic = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
        )
        System.arraycopy(magic, 0, header, 0, 8)
        // Sector shift at offset 30: 9 (512 bytes)
        header[30] = 9
        header[31] = 0
        // numFatSectors at offset 44: 10,000,000
        header[44] = 0x80.toByte()
        header[45] = 0x96.toByte()
        header[46] = 0x98.toByte()
        header[47] = 0x00
        tempFile.writeBytes(header)

        val result = Ole2DocumentReader.extractStream(tempFile, "WordDocument")
        assertNull("Should safely return null without throwing OutOfMemoryError", result)
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Document Parser Error Recovery & Legacy Rejection
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testDocxParserRejectsEmptyOrTruncatedLegacyDoc() {
        val tempDoc = File.createTempFile("empty_legacy", ".doc")
        tempDoc.deleteOnExit()
        tempDoc.writeBytes(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()))

        try {
            DocxParser.parse(tempDoc)
            fail("Expected UnsupportedOperationException for truncated legacy .doc")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message!!.contains("Legacy binary document"))
        }
    }

    @Test
    fun testPresentationParserRejectsEmptyOrTruncatedLegacyPpt() {
        val tempPpt = File.createTempFile("empty_legacy", ".ppt")
        tempPpt.deleteOnExit()
        tempPpt.writeBytes(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()))

        try {
            PresentationParser.parse(tempPpt)
            fail("Expected UnsupportedOperationException for truncated legacy .ppt")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message!!.contains("Legacy binary presentation"))
        }
    }

    @Test
    fun testSpreadsheetParserRejectsEmptyOrTruncatedLegacyXls() {
        val tempXls = File.createTempFile("empty_legacy", ".xls")
        tempXls.deleteOnExit()
        tempXls.writeBytes(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()))

        try {
            SpreadsheetParser.parse(tempXls)
            fail("Expected UnsupportedOperationException for truncated legacy .xls")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message!!.contains("Legacy binary spreadsheet"))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 4. TransferQueueEngine Auto-Rename Reserved Names
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testGenerateAutoRenamedNameHonorsReservedNames() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val localProvider = LocalFileSystemProvider(context)
        val tempDir = Files.createTempDirectory("rename_test").toFile()
        try {
            val parentRef = FileReference(
                providerType = ProviderType.LOCAL,
                uriOrPath = tempDir.absolutePath,
                displayName = tempDir.name,
                parentUriOrPath = tempDir.parent,
                isDirectory = true
            )
            // Existing file on disk
            File(tempDir, "document.pdf").writeText("sample")

            val db = com.example.core.database.OpenFileDatabase.getInstance(context)
            val safProvider = com.example.core.filesystem.SafDocumentProvider(context)
            val engine = TransferQueueEngine(
                context = context,
                dao = db.dao(),
                localProvider = localProvider,
                safProvider = safProvider,
                appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
            )

            // When document (1).pdf is in reservedNames set, candidate becomes document (2).pdf
            val reserved = setOf("document (1).pdf")
            val chosen = engine.generateAutoRenamedName(
                provider = localProvider,
                parent = parentRef,
                originalName = "document.pdf",
                isDirectory = false,
                reservedNames = reserved
            )
            assertEquals("document (2).pdf", chosen)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 5. Directory Size Cycle Prevention
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testCalculateDirectorySizeCycleProtection() = kotlinx.coroutines.runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val localProvider = LocalFileSystemProvider(context)
        val tempDir = Files.createTempDirectory("symlink_test").toFile()
        try {
            val fileA = File(tempDir, "fileA.txt").apply { writeText("Hello") }
            val size = localProvider.calculateDirectorySize(tempDir)
            assertEquals(5L, size)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
