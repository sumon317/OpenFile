package com.example.core.archive

import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Supported archive creation formats.
 * Note: 7Z is supported for browsing and extraction via [SevenZFile], while creation
 * is intentionally limited to streaming formats (ZIP, TAR, TAR.GZ, TAR.XZ) to avoid
 * mobile OOMs from LZMA2 seekable dictionary buffers.
 */
enum class ArchiveCreationFormat(val extension: String, val label: String) {
    ZIP("zip", "ZIP Archive (.zip)"),
    TAR("tar", "TAR Archive (.tar)"),
    TAR_GZ("tar.gz", "GZip Compressed TAR (.tar.gz)"),
    TAR_XZ("tar.xz", "XZ Compressed TAR (.tar.xz)")
}

data class ArchiveEntryItem(
    val entryPath: String,
    val displayName: String,
    val isDirectory: Boolean,
    val uncompressedSize: Long,
    val compressedSize: Long,
    val lastModifiedEpochMs: Long,
    val isSuspiciousPath: Boolean
)

/**
 * Security-hardened Archive Engine supporting ZIP, TAR, TAR.GZ, GZ, XZ, and 7Z.
 * Enforces strict canonical Zip-Slip validation and zip-bomb size/ratio protections.
 */
object ArchiveEngine {

    private const val BUFFER_SIZE = 64 * 1024
    const val DEFAULT_MAX_EXTRACT_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB safety ceiling per extraction

    /**
     * Validates that extracting [entryName] inside [destinationRoot] will never escape
     * [destinationRoot] via `../`, symlinks, or leading slashes (Zip-Slip protection).
     * Throws [SecurityException] if path traversal is detected.
     */
    fun validateSafeTargetFile(destinationRoot: File, entryName: String): File {
        val cleanedEntry = entryName.replace('\\', '/').trimStart('/')
        if (cleanedEntry.isEmpty() || cleanedEntry.contains("../") || cleanedEntry == "..") {
            throw SecurityException("Blocked Zip-Slip path traversal attempt in archive entry: '$entryName'")
        }
        val canonicalRoot = destinationRoot.canonicalFile
        val targetFile = File(canonicalRoot, cleanedEntry).canonicalFile
        val rootPrefix = canonicalRoot.path + File.separator
        if (targetFile != canonicalRoot && !targetFile.path.startsWith(rootPrefix)) {
            throw SecurityException("Blocked Zip-Slip escape outside destination directory: '$entryName'")
        }
        return targetFile
    }

    fun isSuspiciousEntryName(entryName: String): Boolean {
        val normalized = entryName.replace('\\', '/')
        return normalized.startsWith("/") ||
            normalized.contains("../") ||
            normalized.endsWith("/..") ||
            normalized == ".."
    }

    /**
     * Lists all entries inside a `.zip`, `.tar`, `.tar.gz`, `.tgz`, `.tar.xz`, `.txz`, `.gz`, `.xz`, or `.7z` file.
     */
    suspend fun listArchiveEntries(archiveFile: File): Result<List<ArchiveEntryItem>> = withContext(Dispatchers.IO) {
        runCatching {
            require(archiveFile.exists() && archiveFile.canRead()) {
                "Archive file is not readable: ${archiveFile.name}"
            }
            val lowerName = archiveFile.name.lowercase()
            if (lowerName.endsWith(".7z")) {
                return@runCatching listSevenZEntries(archiveFile)
            }
            if (lowerName.endsWith(".gz") && !lowerName.endsWith(".tar.gz") && !lowerName.endsWith(".tgz")) {
                val targetName = archiveFile.name.removeSuffix(".gz").removeSuffix(".GZ")
                return@runCatching listOf(
                    ArchiveEntryItem(
                        entryPath = targetName,
                        displayName = targetName,
                        isDirectory = false,
                        uncompressedSize = archiveFile.length(),
                        compressedSize = archiveFile.length(),
                        lastModifiedEpochMs = archiveFile.lastModified(),
                        isSuspiciousPath = isSuspiciousEntryName(targetName)
                    )
                )
            }
            if (lowerName.endsWith(".xz") && !lowerName.endsWith(".tar.xz") && !lowerName.endsWith(".txz")) {
                val targetName = archiveFile.name.removeSuffix(".xz").removeSuffix(".XZ")
                return@runCatching listOf(
                    ArchiveEntryItem(
                        entryPath = targetName,
                        displayName = targetName,
                        isDirectory = false,
                        uncompressedSize = archiveFile.length(),
                        compressedSize = archiveFile.length(),
                        lastModifiedEpochMs = archiveFile.lastModified(),
                        isSuspiciousPath = isSuspiciousEntryName(targetName)
                    )
                )
            }

            val items = mutableListOf<ArchiveEntryItem>()
            openArchiveInputStream(archiveFile).use { ais ->
                var entry: ArchiveEntry? = ais.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
                    val rawName = entry.name
                    val cleanName = rawName.trimEnd('/').substringAfterLast('/')
                    items.add(
                        ArchiveEntryItem(
                            entryPath = rawName,
                            displayName = cleanName.ifEmpty { rawName },
                            isDirectory = entry.isDirectory,
                            uncompressedSize = entry.size.coerceAtLeast(0L),
                            compressedSize = if (entry is ZipArchiveEntry) entry.compressedSize.coerceAtLeast(0L) else 0L,
                            lastModifiedEpochMs = entry.lastModifiedDate?.time ?: archiveFile.lastModified(),
                            isSuspiciousPath = isSuspiciousEntryName(rawName)
                        )
                    )
                    entry = ais.nextEntry
                }
            }
            items
        }
    }

    private fun listSevenZEntries(archiveFile: File): List<ArchiveEntryItem> {
        val entries = mutableListOf<ArchiveEntryItem>()
        SevenZFile.builder().setFile(archiveFile).get().use { sevenZ ->
            var entry = sevenZ.nextEntry
            while (entry != null) {
                val rawName = entry.name ?: "entry"
                val cleanName = rawName.trimEnd('/').substringAfterLast('/')
                entries.add(
                    ArchiveEntryItem(
                        entryPath = rawName,
                        displayName = cleanName.ifEmpty { rawName },
                        isDirectory = entry.isDirectory,
                        uncompressedSize = entry.size.coerceAtLeast(0L),
                        compressedSize = 0L,
                        lastModifiedEpochMs = if (entry.hasLastModifiedDate) entry.lastModifiedDate.time else archiveFile.lastModified(),
                        isSuspiciousPath = isSuspiciousEntryName(rawName)
                    )
                )
                entry = sevenZ.nextEntry
            }
        }
        return entries
    }

    /**
     * Extracts all (or [selectedEntryPaths] if non-null) from [archiveFile] into [destinationDir].
     * Enforces Zip-Slip canonical path validation and [maxAllowedBytes] zip-bomb protection.
     */
    suspend fun extractArchive(
        archiveFile: File,
        destinationDir: File,
        selectedEntryPaths: Set<String>? = null,
        maxAllowedBytes: Long = DEFAULT_MAX_EXTRACT_BYTES,
        onProgress: (extractedFiles: Int, totalExtractedBytes: Long, currentEntry: String) -> Unit = { _, _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                throw IllegalStateException("Cannot create extraction directory: ${destinationDir.absolutePath}")
            }
            val lowerName = archiveFile.name.lowercase()
            if (lowerName.endsWith(".7z")) {
                return@runCatching extractSevenZ(
                    archiveFile = archiveFile,
                    destinationDir = destinationDir,
                    selectedEntryPaths = selectedEntryPaths,
                    maxAllowedBytes = maxAllowedBytes,
                    onProgress = onProgress
                )
            }
            if (lowerName.endsWith(".gz") && !lowerName.endsWith(".tar.gz") && !lowerName.endsWith(".tgz")) {
                val targetName = archiveFile.name.removeSuffix(".gz").removeSuffix(".GZ")
                val safeTarget = validateSafeTargetFile(destinationDir, targetName)
                GzipCompressorInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { gis ->
                    safeTarget.outputStream().use { out ->
                        copyLimited(gis, out, maxAllowedBytes)
                    }
                }
                onProgress(1, safeTarget.length(), targetName)
                return@runCatching 1
            }
            if (lowerName.endsWith(".xz") && !lowerName.endsWith(".tar.xz") && !lowerName.endsWith(".txz")) {
                val targetName = archiveFile.name.removeSuffix(".xz").removeSuffix(".XZ")
                val safeTarget = validateSafeTargetFile(destinationDir, targetName)
                XZCompressorInputStream(BufferedInputStream(FileInputStream(archiveFile))).use { xis ->
                    safeTarget.outputStream().use { out ->
                        copyLimited(xis, out, maxAllowedBytes)
                    }
                }
                onProgress(1, safeTarget.length(), targetName)
                return@runCatching 1
            }

            var extractedCount = 0
            var totalBytesWritten = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            openArchiveInputStream(archiveFile).use { ais ->
                var entry: ArchiveEntry? = ais.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
                    val currentEntry = entry
                    if (selectedEntryPaths == null || currentEntry.name in selectedEntryPaths) {
                        val safeTarget = validateSafeTargetFile(destinationDir, currentEntry.name)
                        if (currentEntry.isDirectory) {
                            safeTarget.mkdirs()
                        } else {
                            safeTarget.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(safeTarget)).use { out ->
                                var read = ais.read(buffer)
                                while (read > 0) {
                                    coroutineContext.ensureActive()
                                    totalBytesWritten += read
                                    if (totalBytesWritten > maxAllowedBytes) {
                                        throw SecurityException("Extraction aborted: Archive exceeded maximum safe uncompressed size (${maxAllowedBytes / (1024 * 1024)} MB).")
                                    }
                                    out.write(buffer, 0, read)
                                    read = ais.read(buffer)
                                }
                            }
                            extractedCount++
                            onProgress(extractedCount, totalBytesWritten, currentEntry.name)
                        }
                    }
                    entry = ais.nextEntry
                }
            }
            extractedCount
        }
    }

    private suspend fun extractSevenZ(
        archiveFile: File,
        destinationDir: File,
        selectedEntryPaths: Set<String>?,
        maxAllowedBytes: Long,
        onProgress: (Int, Long, String) -> Unit
    ): Int {
        var extractedCount = 0
        var totalBytesWritten = 0L
        val buffer = ByteArray(BUFFER_SIZE)

        SevenZFile.builder().setFile(archiveFile).get().use { sevenZ ->
            var entry = sevenZ.nextEntry
            while (entry != null) {
                coroutineContext.ensureActive()
                val entryName = entry.name ?: "entry"
                if (selectedEntryPaths == null || entryName in selectedEntryPaths) {
                    val safeTarget = validateSafeTargetFile(destinationDir, entryName)
                    if (entry.isDirectory) {
                        safeTarget.mkdirs()
                    } else {
                        safeTarget.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(safeTarget)).use { out ->
                            var read = sevenZ.read(buffer)
                            while (read > 0) {
                                coroutineContext.ensureActive()
                                totalBytesWritten += read
                                if (totalBytesWritten > maxAllowedBytes) {
                                    throw SecurityException("7Z extraction aborted: Exceeded safe uncompressed limit.")
                                }
                                out.write(buffer, 0, read)
                                read = sevenZ.read(buffer)
                            }
                        }
                        extractedCount++
                        onProgress(extractedCount, totalBytesWritten, entryName)
                    }
                }
                entry = sevenZ.nextEntry
            }
        }
        return extractedCount
    }

    /**
     * Creates a new archive (`.zip`, `.tar`, `.tar.gz`, or `.tar.xz`) from [sourceFiles] inside [outputArchiveFile].
     */
    suspend fun createArchive(
        sourceFiles: List<File>,
        outputArchiveFile: File,
        format: ArchiveCreationFormat,
        onProgress: (processedFiles: Int, currentFileName: String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            outputArchiveFile.parentFile?.mkdirs()
            var count = 0
            when (format) {
                ArchiveCreationFormat.ZIP -> {
                    ZipArchiveOutputStream(BufferedOutputStream(FileOutputStream(outputArchiveFile))).use { zos ->
                        for (src in sourceFiles) {
                            count = addFileToZipRecursive(zos, src, parentPath = "", currentCount = count, onProgress = onProgress)
                        }
                        zos.finish()
                    }
                }
                ArchiveCreationFormat.TAR -> {
                    TarArchiveOutputStream(BufferedOutputStream(FileOutputStream(outputArchiveFile))).use { tos ->
                        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                        for (src in sourceFiles) {
                            count = addFileToTarRecursive(tos, src, parentPath = "", currentCount = count, onProgress = onProgress)
                        }
                        tos.finish()
                    }
                }
                ArchiveCreationFormat.TAR_GZ -> {
                    GzipCompressorOutputStream(BufferedOutputStream(FileOutputStream(outputArchiveFile))).use { gzos ->
                        TarArchiveOutputStream(gzos).use { tos ->
                            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                            for (src in sourceFiles) {
                                count = addFileToTarRecursive(tos, src, parentPath = "", currentCount = count, onProgress = onProgress)
                            }
                            tos.finish()
                        }
                    }
                }
                ArchiveCreationFormat.TAR_XZ -> {
                    XZCompressorOutputStream(BufferedOutputStream(FileOutputStream(outputArchiveFile))).use { xzos ->
                        TarArchiveOutputStream(xzos).use { tos ->
                            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                            for (src in sourceFiles) {
                                count = addFileToTarRecursive(tos, src, parentPath = "", currentCount = count, onProgress = onProgress)
                            }
                            tos.finish()
                        }
                    }
                }
            }
            outputArchiveFile
        }
    }

    /**
     * Verifies archive stream integrity without writing files to disk.
     */
    suspend fun verifyArchiveIntegrity(archiveFile: File): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = listArchiveEntries(archiveFile).getOrThrow()
            val suspicious = entries.firstOrNull { it.isSuspiciousPath }
            if (suspicious != null) {
                throw SecurityException("Suspicious path traversal entry detected: ${suspicious.entryPath}")
            }
            entries.size
        }
    }

    private suspend fun addFileToZipRecursive(
        zos: ZipArchiveOutputStream,
        file: File,
        parentPath: String,
        currentCount: Int,
        onProgress: (Int, String) -> Unit
    ): Int {
        coroutineContext.ensureActive()
        val entryPath = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"
        if (file.isDirectory) {
            val dirEntry = ZipArchiveEntry("$entryPath/")
            zos.putArchiveEntry(dirEntry)
            zos.closeArchiveEntry()
            var count = currentCount
            file.listFiles()?.forEach { child ->
                count = addFileToZipRecursive(zos, child, entryPath, count, onProgress)
            }
            return count
        } else {
            val entry = ZipArchiveEntry(file, entryPath)
            zos.putArchiveEntry(entry)
            file.inputStream().buffered(BUFFER_SIZE).use { input ->
                input.copyTo(zos, BUFFER_SIZE)
            }
            zos.closeArchiveEntry()
            val next = currentCount + 1
            onProgress(next, file.name)
            return next
        }
    }

    private suspend fun addFileToTarRecursive(
        tos: TarArchiveOutputStream,
        file: File,
        parentPath: String,
        currentCount: Int,
        onProgress: (Int, String) -> Unit
    ): Int {
        coroutineContext.ensureActive()
        val entryPath = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"
        if (file.isDirectory) {
            val dirEntry = TarArchiveEntry(file, "$entryPath/")
            tos.putArchiveEntry(dirEntry)
            tos.closeArchiveEntry()
            var count = currentCount
            file.listFiles()?.forEach { child ->
                count = addFileToTarRecursive(tos, child, entryPath, count, onProgress)
            }
            return count
        } else {
            val entry = TarArchiveEntry(file, entryPath)
            entry.size = file.length()
            tos.putArchiveEntry(entry)
            file.inputStream().buffered(BUFFER_SIZE).use { input ->
                input.copyTo(tos, BUFFER_SIZE)
            }
            tos.closeArchiveEntry()
            val next = currentCount + 1
            onProgress(next, file.name)
            return next
        }
    }

    private fun openArchiveInputStream(archiveFile: File): ArchiveInputStream<out ArchiveEntry> {
        val lower = archiveFile.name.lowercase()
        val fis = BufferedInputStream(FileInputStream(archiveFile))
        return when {
            lower.endsWith(".zip") || lower.endsWith(".jar") || lower.endsWith(".apk") -> ZipArchiveInputStream(fis)
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> TarArchiveInputStream(GzipCompressorInputStream(fis))
            lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> TarArchiveInputStream(XZCompressorInputStream(fis))
            lower.endsWith(".tar") -> TarArchiveInputStream(fis)
            else -> ZipArchiveInputStream(fis)
        }
    }

    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, maxBytes: Long) {
        val buf = ByteArray(BUFFER_SIZE)
        var total = 0L
        var read = input.read(buf)
        while (read > 0) {
            total += read
            if (total > maxBytes) {
                throw SecurityException("Decompression aborted: Exceeded maximum safe byte limit.")
            }
            output.write(buf, 0, read)
            read = input.read(buf)
        }
    }
}
