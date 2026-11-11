package com.example.core.document

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Result of document format detection based on file signatures (magic bytes) and structure.
 */
sealed interface DetectedDocumentType {
    object Xlsx : DetectedDocumentType
    object Pptx : DetectedDocumentType
    object Docx : DetectedDocumentType
    object Ods : DetectedDocumentType
    object Odp : DetectedDocumentType
    object Odt : DetectedDocumentType
    object Cbz : DetectedDocumentType
    object Pdf : DetectedDocumentType
    object Csv : DetectedDocumentType
    object Tsv : DetectedDocumentType
    object Markdown : DetectedDocumentType
    object PlainText : DetectedDocumentType

    /** Legacy binary OLE2 Compound File formats (.xls, .ppt, .doc). Unsupported by this pipeline. */
    data class LegacyOle2(val apparentExtension: String) : DetectedDocumentType

    /** Encrypted or password-protected OOXML/Office files. */
    object EncryptedPasswordProtected : DetectedDocumentType

    /** Unknown or unsupported binary/text format. */
    data class Unknown(val reason: String) : DetectedDocumentType
}

/**
 * Validates zip entries against zip-slip and zip-bomb attacks, and determines document types
 * from header magic bytes rather than file extensions.
 */
object DocumentFormatDetector {

    // Magic bytes signatures
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val OLE2_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
    )
    private val PDF_MAGIC = byteArrayOf(0x25, 0x50, 0x44, 0x46) // %PDF

    // Zip safety limits
    const val MAX_ZIP_ENTRIES = 10_000
    const val MAX_XML_ENTRY_BYTES = 100L * 1024L * 1024L // 100 MB
    const val MAX_MEDIA_ENTRY_BYTES = 50L * 1024L * 1024L // 50 MB
    const val MAX_TOTAL_DECOMPRESSED_BYTES = 250L * 1024L * 1024L // 250 MB

    /**
     * Inspects the file by magic bytes first, falling back to extension only if ambiguous.
     */
    fun detect(file: File): DetectedDocumentType {
        if (!file.exists() || file.length() == 0L) {
            return DetectedDocumentType.Unknown("File is empty or does not exist")
        }

        val header = ByteArray(8)
        var bytesRead = 0
        try {
            FileInputStream(file).use { fis ->
                bytesRead = fis.read(header)
            }
        } catch (e: Exception) {
            return DetectedDocumentType.Unknown("Cannot read file header: ${e.message}")
        }

        // 1. Check Legacy OLE2 (Compound File Binary Format: .xls, .ppt, .doc)
        if (bytesRead >= 8 && header.sliceArray(0 until 8).contentEquals(OLE2_MAGIC)) {
            // Check if this OLE2 container is actually an EncryptedPackage wrapping an OOXML document
            if (isEncryptedOoxmlPackage(file)) {
                return DetectedDocumentType.EncryptedPasswordProtected
            }
            return DetectedDocumentType.LegacyOle2(file.extension.lowercase())
        }

        // 2. Check PDF (%PDF)
        if (bytesRead >= 4 && header.sliceArray(0 until 4).contentEquals(PDF_MAGIC)) {
            return DetectedDocumentType.Pdf
        }

        // 3. Check ZIP-based packages (OOXML, ODF, CBZ)
        if (bytesRead >= 4 && header.sliceArray(0 until 4).contentEquals(ZIP_MAGIC)) {
            return inspectZipPackage(file)
        }

        // 4. Non-ZIP fallback: Delimited or plain text
        val ext = file.extension.lowercase()
        return when (ext) {
            "csv" -> DetectedDocumentType.Csv
            "tsv" -> DetectedDocumentType.Tsv
            "md", "markdown" -> DetectedDocumentType.Markdown
            "txt", "log", "ini", "conf", "json", "xml", "yaml", "yml" -> DetectedDocumentType.PlainText
            else -> DetectedDocumentType.Unknown("Unrecognized file format")
        }
    }

    /**
     * Inspects internal zip entry structure to differentiate XLSX, PPTX, DOCX, ODS, ODP, ODT, CBZ.
     */
    private fun inspectZipPackage(file: File): DetectedDocumentType {
        var hasContentTypes = false
        var hasWorkbook = false
        var hasPresentation = false
        var hasWordDocument = false
        var odfMimeType: String? = null
        var isComic = false
        var isEncrypted = false

        try {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                var count = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    count++
                    if (count > MAX_ZIP_ENTRIES) {
                        return DetectedDocumentType.Unknown("File exceeds maximum allowed zip entries ($MAX_ZIP_ENTRIES)")
                    }

                    // Zip-slip security check
                    if (isZipSlipAttempt(entry.name)) {
                        return DetectedDocumentType.Unknown("Insecure zip entry path detected: ${entry.name}")
                    }

                    val name = entry.name
                    when {
                        name == "[Content_Types].xml" -> hasContentTypes = true
                        name == "xl/workbook.xml" -> hasWorkbook = true
                        name == "ppt/presentation.xml" -> hasPresentation = true
                        name == "word/document.xml" -> hasWordDocument = true
                        name == "EncryptedPackage" -> isEncrypted = true
                        name.equals("mimetype", ignoreCase = true) -> {
                            odfMimeType = try {
                                zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readLine()?.trim() }
                            } catch (_: Exception) {
                                null
                            }
                        }
                        name.endsWith(".jpg", ignoreCase = true) ||
                            name.endsWith(".jpeg", ignoreCase = true) ||
                            name.endsWith(".png", ignoreCase = true) ||
                            name.endsWith(".webp", ignoreCase = true) -> {
                            isComic = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("password", ignoreCase = true) || msg.contains("encrypted", ignoreCase = true)) {
                return DetectedDocumentType.EncryptedPasswordProtected
            }
            return DetectedDocumentType.Unknown("Failed to inspect zip archive: ${e.message}")
        }

        if (isEncrypted) {
            return DetectedDocumentType.EncryptedPasswordProtected
        }

        // OOXML detection
        if (hasWorkbook) return DetectedDocumentType.Xlsx
        if (hasPresentation) return DetectedDocumentType.Pptx
        if (hasWordDocument) return DetectedDocumentType.Docx

        // ODF detection
        if (odfMimeType != null) {
            return when (odfMimeType) {
                "application/vnd.oasis.opendocument.spreadsheet" -> DetectedDocumentType.Ods
                "application/vnd.oasis.opendocument.presentation" -> DetectedDocumentType.Odp
                "application/vnd.oasis.opendocument.text" -> DetectedDocumentType.Odt
                else -> DetectedDocumentType.Unknown("Unsupported ODF MIME type: $odfMimeType")
            }
        }

        // Extension hint if standard zip structure without recognizable OOXML/ODF marker
        val ext = file.extension.lowercase()
        return when {
            ext in setOf("cbz", "cbr") || isComic -> DetectedDocumentType.Cbz
            ext == "xlsx" -> DetectedDocumentType.Xlsx
            ext == "pptx" -> DetectedDocumentType.Pptx
            ext == "docx" -> DetectedDocumentType.Docx
            ext == "ods" -> DetectedDocumentType.Ods
            ext == "odp" -> DetectedDocumentType.Odp
            ext == "odt" -> DetectedDocumentType.Odt
            else -> DetectedDocumentType.Unknown("Generic ZIP archive without recognized document structure")
        }
    }

    /**
     * Checks if the OLE2 file is an EncryptedPackage (standard Office password protection wrapper).
     */
    private fun isEncryptedOoxmlPackage(file: File): Boolean {
        return try {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(4096)
                val read = fis.read(buffer)
                if (read > 0) {
                    val content = String(buffer, 0, read, Charsets.ISO_8859_1)
                    content.contains("EncryptedPackage") || content.contains("EncryptionInfo")
                } else false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Prevents Zip Slip directory traversal attacks.
     */
    fun isZipSlipAttempt(entryName: String): Boolean {
        val normalized = entryName.replace('\\', '/')
        return normalized.startsWith("/") || normalized.contains("../") || normalized.contains("/..")
    }

    /**
     * Wraps an InputStream with decompressed-byte tracking to defend against zip bombs.
     */
    fun createSafeBoundedStream(
        rawStream: InputStream,
        maxBytes: Long = MAX_XML_ENTRY_BYTES
    ): InputStream {
        return object : BufferedInputStream(rawStream) {
            private var totalRead = 0L

            override fun read(): Int {
                val b = super.read()
                if (b != -1) {
                    totalRead++
                    checkLimit()
                }
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val count = super.read(b, off, len)
                if (count > 0) {
                    totalRead += count
                    checkLimit()
                }
                return count
            }

            private fun checkLimit() {
                if (totalRead > maxBytes) {
                    throw IllegalStateException("Decompressed entry exceeded safe threshold of ${maxBytes / (1024 * 1024)} MB")
                }
            }
        }
    }
}
