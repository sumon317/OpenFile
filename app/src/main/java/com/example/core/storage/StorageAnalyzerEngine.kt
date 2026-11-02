package com.example.core.storage

import android.os.StatFs
import com.example.core.filesystem.ChecksumAndDuplicateEngine
import com.example.core.filesystem.LocalFileSystemProvider
import com.example.core.model.CategoryUsageBucket
import com.example.core.model.FileCategory
import com.example.core.model.FileReference
import com.example.core.model.StorageAnalysisReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Asynchronous, cancellable storage analyzer engine.
 * Calculates category breakdowns, largest files, largest directories, extension statistics,
 * and duplicate file groups without blocking the UI thread.
 */
class StorageAnalyzerEngine(
    private val localProvider: LocalFileSystemProvider
) {

    suspend fun analyzeDirectory(
        rootDir: File,
        includeDuplicateScan: Boolean = true,
        onProgress: (scannedFiles: Int, scannedBytes: Long, currentPath: String) -> Unit = { _, _, _ -> }
    ): Result<StorageAnalysisReport> = withContext(Dispatchers.IO) {
        runCatching {
            val stat = runCatching { StatFs(rootDir.absolutePath) }.getOrNull()
            val totalCapacity = stat?.totalBytes ?: (64L * 1024 * 1024 * 1024)
            val freeCapacity = stat?.availableBytes ?: (32L * 1024 * 1024 * 1024)
            val usedCapacity = (totalCapacity - freeCapacity).coerceAtLeast(0L)

            val allFiles = mutableListOf<FileReference>()
            val directorySizes = mutableMapOf<File, Long>()
            val categoryTotals = mutableMapOf<String, Pair<Long, Int>>()
            val extensionStats = mutableMapOf<String, Pair<Int, Long>>()

            var scannedFiles = 0
            var scannedDirs = 0
            var scannedBytes = 0L
            var lastProgressEmitMs = 0L

            val stack = ArrayDeque<File>()
            stack.add(rootDir)

            while (stack.isNotEmpty()) {
                coroutineContext.ensureActive()
                val dir = stack.removeLast()
                scannedDirs++
                val children = dir.listFiles() ?: continue
                var directBytes = 0L

                for (child in children) {
                    coroutineContext.ensureActive()
                    if (child.isDirectory) {
                        if (!localProvider.isRestrictedAndroidDirectory(child)) {
                            stack.add(child)
                        }
                    } else {
                        val ref = localProvider.toFileReference(child)
                        val size = ref.sizeBytes
                        scannedFiles++
                        scannedBytes += size
                        directBytes += size
                        allFiles.add(ref)

                        // Category bucket
                        val catName = FileCategory.categorize(ref)?.title ?: "Other"
                        val prevCat = categoryTotals[catName] ?: (0L to 0)
                        categoryTotals[catName] = (prevCat.first + size) to (prevCat.second + 1)

                        // Extension bucket
                        val extKey = if (ref.extension.isBlank()) "(no ext)" else ".${ref.extension}"
                        val prevExt = extensionStats[extKey] ?: (0 to 0L)
                        extensionStats[extKey] = (prevExt.first + 1) to (prevExt.second + size)

                        if (scannedFiles % 25 == 0) {
                            val now = System.currentTimeMillis()
                            if (now - lastProgressEmitMs >= 100L) {
                                lastProgressEmitMs = now
                                onProgress(scannedFiles, scannedBytes, child.name)
                            }
                        }
                    }
                }

                // Accumulate size up to rootDir
                var ancestor: File? = dir
                while (ancestor != null && ancestor.absolutePath.startsWith(rootDir.absolutePath)) {
                    directorySizes[ancestor] = (directorySizes[ancestor] ?: 0L) + directBytes
                    if (ancestor == rootDir) break
                    ancestor = ancestor.parentFile
                }
            }

            val totalScanned = scannedBytes.coerceAtLeast(1L)
            val colorMap = mapOf(
                "Images" to 0xFF00897B,
                "Videos" to 0xFFE53935,
                "Audio" to 0xFF8E24AA,
                "Documents" to 0xFF3949AB,
                "Archives" to 0xFFE37400,
                "Applications" to 0xFF2E7D32,
                "Other" to 0xFF546E7A
            )

            val buckets = categoryTotals.entries
                .sortedByDescending { it.value.first }
                .map { (name, pair) ->
                    CategoryUsageBucket(
                        categoryName = name,
                        bytesUsed = pair.first,
                        fileCount = pair.second,
                        fractionOfTotal = (pair.first.toFloat() / totalScanned.toFloat()).coerceIn(0f, 1f),
                        colorHex = colorMap[name] ?: 0xFF0B57D0
                    )
                }

            val largestFiles = allFiles.sortedByDescending { it.sizeBytes }.take(50)
            val largestDirs = directorySizes.entries
                .filter { it.key != rootDir }
                .sortedByDescending { it.value }
                .take(30)
                .map { localProvider.toFileReference(it.key) to it.value }

            val topExtensions = extensionStats.entries
                .sortedByDescending { it.value.second }
                .take(15)
                .map { Triple(it.key, it.value.first, it.value.second) }

            val duplicateGroups = if (includeDuplicateScan) {
                ChecksumAndDuplicateEngine.findDuplicates(allFiles, localProvider)
            } else {
                emptyList()
            }

            StorageAnalysisReport(
                rootPath = rootDir.absolutePath,
                totalBytes = totalCapacity,
                usedBytes = usedCapacity.coerceAtLeast(scannedBytes),
                freeBytes = freeCapacity,
                scannedFilesCount = scannedFiles,
                scannedDirectoriesCount = scannedDirs,
                categoryBuckets = buckets,
                largestFiles = largestFiles,
                largestDirectories = largestDirs,
                extensionDistribution = topExtensions,
                duplicateGroups = duplicateGroups
            )
        }
    }
}
