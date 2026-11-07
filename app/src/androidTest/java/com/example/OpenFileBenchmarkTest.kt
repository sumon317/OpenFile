package com.example

import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.core.filesystem.FileSortAndFilterEngine
import com.example.core.filesystem.LocalFileSystemProvider
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.example.core.model.SortField
import com.example.core.model.SortOrder
import com.example.ui.OpenFileDestination
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

private const val TAG = "OpenFileBenchmark"

data class FrameMetricRecord(
    val totalMs: Double,
    val layoutMeasureMs: Double,
    val drawMs: Double,
    val animMs: Double
)

data class MacrobenchReport(
    val testName: String,
    val totalFrames: Int,
    val refreshRateHz: Float,
    val targetDeadlineMs: Double,
    val meanFrameMs: Double,
    val p50Ms: Double,
    val p90Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val jankFramesCount: Int,
    val jankPercentage: Double,
    val averageFps: Double
) {
    fun printReport() {
        val banner = "=".repeat(72)
        val sb = StringBuilder()
        sb.appendLine("\n$banner")
        sb.appendLine("  MACROBENCHMARK REPORT: $testName")
        sb.appendLine("  Hardware Display:     ${"%.1f".format(refreshRateHz)} Hz (Deadline: ${"%.2f".format(targetDeadlineMs)} ms)")
        sb.appendLine("  Total Frames Sampled: $totalFrames")
        sb.appendLine("  Mean Frame Time:      ${"%.2f".format(meanFrameMs)} ms")
        sb.appendLine("  P50 (Median Frame):   ${"%.2f".format(p50Ms)} ms")
        sb.appendLine("  P90 Frame Time:       ${"%.2f".format(p90Ms)} ms")
        sb.appendLine("  P95 Frame Time:       ${"%.2f".format(p95Ms)} ms")
        sb.appendLine("  P99 Frame Time:       ${"%.2f".format(p99Ms)} ms")
        sb.appendLine("  Jank Frames (> ${"%.1f".format(targetDeadlineMs)}ms): $jankFramesCount (${"%.2f".format(jankPercentage)}%)")
        sb.appendLine("  Effective Frame Rate: ${"%.1f".format(averageFps)} FPS")
        sb.appendLine(banner)

        val reportStr = sb.toString()
        println(reportStr)
        Log.i(TAG, reportStr)
        val bundle = Bundle().apply {
            putString(Instrumentation.REPORT_KEY_STREAMRESULT, reportStr)
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
    }
}

data class MicrobenchReport(
    val benchmarkName: String,
    val iterations: Int,
    val minMs: Double,
    val maxMs: Double,
    val avgMs: Double,
    val p95Ms: Double,
    val throughputOpsPerSec: Double
) {
    fun printReport() {
        val banner = "-".repeat(72)
        val sb = StringBuilder()
        sb.appendLine("\n$banner")
        sb.appendLine("  MICROBENCHMARK: $benchmarkName ($iterations runs)")
        sb.appendLine("  Min:        ${"%.3f".format(minMs)} ms")
        sb.appendLine("  Max:        ${"%.3f".format(maxMs)} ms")
        sb.appendLine("  Average:    ${"%.3f".format(avgMs)} ms")
        sb.appendLine("  P95:        ${"%.3f".format(p95Ms)} ms")
        sb.appendLine("  Throughput: ${"%.1f".format(throughputOpsPerSec)} ops/sec")
        sb.appendLine(banner)

        val reportStr = sb.toString()
        println(reportStr)
        Log.i(TAG, reportStr)
        val bundle = Bundle().apply {
            putString(Instrumentation.REPORT_KEY_STREAMRESULT, reportStr)
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
    }
}

@RunWith(AndroidJUnit4::class)
class OpenFileBenchmarkTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    // =============================================================================================
    // 1. MICROBENCHMARKS: CPU, Algorithm & State Performance Tests
    // =============================================================================================

    @Test
    fun microbench01_sortEngine_1000Files() {
        val items = (1..1000).map { i ->
            FileReference(
                providerType = ProviderType.LOCAL,
                uriOrPath = "/storage/emulated/0/Folder/item_$i.${if (i % 3 == 0) "jpg" else if (i % 3 == 1) "mp4" else "pdf"}",
                displayName = "Item_${(1000 - i).toString().padStart(4, '0')}.ext",
                parentUriOrPath = "/storage/emulated/0/Folder",
                isDirectory = (i % 8 == 0),
                sizeBytes = (i * 1024L * 1024L) % (500L * 1024L * 1024L),
                lastModifiedEpochMs = System.currentTimeMillis() - (i * 3600_000L)
            )
        }

        // Warm up JIT
        repeat(15) {
            FileSortAndFilterEngine.sortFiles(items, SortField.NAME, SortOrder.ASCENDING)
            FileSortAndFilterEngine.sortFiles(items, SortField.SIZE, SortOrder.DESCENDING)
        }

        val iterations = 50
        val durations = mutableListOf<Double>()

        for (it in 0 until iterations) {
            val field = when (it % 4) {
                0 -> SortField.NAME
                1 -> SortField.SIZE
                2 -> SortField.DATE
                else -> SortField.TYPE
            }
            val elapsedMs = measureNanoTime {
                FileSortAndFilterEngine.sortFiles(items, field, SortOrder.ASCENDING)
            } / 1_000_000.0
            durations.add(elapsedMs)
        }

        durations.sort()
        val report = MicrobenchReport(
            benchmarkName = "FileSortAndFilterEngine.sortFiles (1,000 files across Name/Size/Date/Type)",
            iterations = iterations,
            minMs = durations.first(),
            maxMs = durations.last(),
            avgMs = durations.average(),
            p95Ms = durations[(durations.size * 0.95).toInt()],
            throughputOpsPerSec = 1000.0 / durations.average()
        )
        report.printReport()
        assertTrue("Average sort time for 1,000 items must be under 15ms", report.avgMs < 15.0)
    }

    @Test
    fun microbench02_filterEngine_searchQuery() {
        val items = (1..1000).map { i ->
            FileReference(
                providerType = ProviderType.LOCAL,
                uriOrPath = "/storage/emulated/0/Test/file_$i.txt",
                displayName = if (i % 5 == 0) "invoice_report_$i.pdf" else "photo_vacation_$i.jpg",
                parentUriOrPath = "/storage/emulated/0/Test",
                isDirectory = false,
                sizeBytes = 1024L * i
            )
        }

        // Warm up
        repeat(15) {
            FileSortAndFilterEngine.filterFiles(files = items, query = "invoice", showHidden = true)
            FileSortAndFilterEngine.filterFiles(files = items, query = "vacation", extensionFilter = "jpg", showHidden = false)
        }

        val iterations = 60
        val durations = mutableListOf<Double>()

        for (it in 0 until iterations) {
            val query = if (it % 2 == 0) "invoice" else "photo"
            val ext = if (it % 3 == 0) "pdf" else null
            val elapsedMs = measureNanoTime {
                FileSortAndFilterEngine.filterFiles(files = items, query = query, extensionFilter = ext, showHidden = false)
            } / 1_000_000.0
            durations.add(elapsedMs)
        }

        durations.sort()
        val report = MicrobenchReport(
            benchmarkName = "FileSortAndFilterEngine.filterFiles (1,000 files query substring search)",
            iterations = iterations,
            minMs = durations.first(),
            maxMs = durations.last(),
            avgMs = durations.average(),
            p95Ms = durations[(durations.size * 0.95).toInt()],
            throughputOpsPerSec = 1000.0 / durations.average()
        )
        report.printReport()
        assertTrue("Average filter time for 1,000 items must be under 10ms", report.avgMs < 10.0)
    }

    @Test
    fun microbench03_fileReferenceMetadataAccess() {
        val ref = FileReference(
            providerType = ProviderType.LOCAL,
            uriOrPath = "/storage/emulated/0/Documents/report_final_2026.pdf",
            displayName = "report_final_2026.pdf",
            parentUriOrPath = "/storage/emulated/0/Documents",
            isDirectory = false,
            sizeBytes = 15_420_810L,
            lastModifiedEpochMs = 1730000000000L,
            metadata = mapOf("childCount" to "12", "permissions" to "-rwxr-xr-x")
        )

        // Warm up
        repeat(1000) {
            ref.formattedSize
            ref.permissionsString
            ref.extension
        }

        val iterations = 10_000
        var sink = 0
        val elapsedTotalMs = measureTimeMillis {
            for (i in 0 until iterations) {
                sink += ref.formattedSize.length
                sink += ref.permissionsString.length
                sink += ref.extension.length
            }
        }.toDouble()

        val avgMs = elapsedTotalMs / iterations
        val report = MicrobenchReport(
            benchmarkName = "FileReference metadata formatting & property evaluation",
            iterations = iterations,
            minMs = avgMs,
            maxMs = avgMs,
            avgMs = avgMs,
            p95Ms = avgMs,
            throughputOpsPerSec = if (elapsedTotalMs > 0) (iterations.toDouble() / elapsedTotalMs) * 1000.0 else 1_000_000.0
        )
        report.printReport()
        assertTrue(sink > 0)
    }

    // =============================================================================================
    // 2. MACROBENCHMARKS: Real-Device Frame Metrics, Smoothness & Scrolling Benchmarks
    // =============================================================================================

    @Test
    fun macrobench01_fileBrowserScrollingFrameMetrics() {
        val testDir = File(targetContext.cacheDir, "benchmark_scroll_dir_${System.currentTimeMillis()}").apply { mkdirs() }
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            // Create 100 sample files and folders with realistic sizes and extensions
            for (i in 1..100) {
                if (i % 6 == 0) {
                    File(testDir, "SubFolder_$i").mkdirs()
                } else {
                    val ext = when (i % 5) {
                        0 -> "jpg"
                        1 -> "mp4"
                        2 -> "pdf"
                        3 -> "zip"
                        else -> "txt"
                    }
                    File(testDir, "Document_Sample_${i.toString().padStart(3, '0')}.$ext").writeText("Sample content $i")
                }
            }

            val localProvider = LocalFileSystemProvider(targetContext)
            val testDirRef = localProvider.toFileReference(testDir)

            // Open test folder in FileBrowserScreen
            scenario.onActivity { activity ->
                activity.viewModel.navigateToDestination(OpenFileDestination.INTERNAL_STORAGE)
                activity.viewModel.openDirectory(testDirRef)
            }
            composeTestRule.waitForIdle()

            // Wait for asynchronous directory listing to complete and populate the LazyColumn
            composeTestRule.waitUntil(timeoutMillis = 10_000) {
                composeTestRule.onAllNodesWithTag("browser_list_view").fetchSemanticsNodes().isNotEmpty()
            }

            // Attach FrameMetricsAvailableListener to the real hardware Window
            val frameRecords = CopyOnWriteArrayList<FrameMetricRecord>()
            val handler = Handler(Looper.getMainLooper())
            val frameListener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
                val totalNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
                val layoutNs = frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
                val drawNs = frameMetrics.getMetric(FrameMetrics.DRAW_DURATION)
                val animNs = frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION)
                frameRecords.add(
                    FrameMetricRecord(
                        totalMs = totalNs / 1_000_000.0,
                        layoutMeasureMs = layoutNs / 1_000_000.0,
                        drawMs = drawNs / 1_000_000.0,
                        animMs = animNs / 1_000_000.0
                    )
                )
            }

            scenario.onActivity { activity ->
                activity.window.addOnFrameMetricsAvailableListener(frameListener, handler)
            }

            // Perform repeated smooth and rapid scrolls up and down through the list
            val listView = composeTestRule.onNodeWithTag("browser_list_view")
            repeat(6) {
                listView.performTouchInput {
                    swipeUp(durationMillis = 350)
                }
                composeTestRule.waitForIdle()
                listView.performTouchInput {
                    swipeDown(durationMillis = 350)
                }
                composeTestRule.waitForIdle()
            }

            var refreshRate = 60f
            scenario.onActivity { activity ->
                activity.window.removeOnFrameMetricsAvailableListener(frameListener)
                refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    activity.display?.refreshRate ?: 60f
                } else {
                    @Suppress("DEPRECATION")
                    activity.windowManager.defaultDisplay.refreshRate
                }
            }

            val targetDeadlineMs = 1000.0 / refreshRate
            val totalDurations = frameRecords.map { it.totalMs }.sorted()
            val jankCount = totalDurations.count { it > targetDeadlineMs }
            val jankPct = if (totalDurations.isNotEmpty()) (jankCount.toDouble() / totalDurations.size) * 100.0 else 0.0
            val meanFrame = if (totalDurations.isNotEmpty()) totalDurations.average() else 0.0
            val p50 = if (totalDurations.isNotEmpty()) totalDurations[(totalDurations.size * 0.50).toInt()] else 0.0
            val p90 = if (totalDurations.isNotEmpty()) totalDurations[(totalDurations.size * 0.90).toInt()] else 0.0
            val p95 = if (totalDurations.isNotEmpty()) totalDurations[(totalDurations.size * 0.95).toInt()] else 0.0
            val p99 = if (totalDurations.isNotEmpty()) totalDurations[(totalDurations.size * 0.99).toInt()] else 0.0
            val avgFps = if (meanFrame > 0) (1000.0 / meanFrame).coerceAtMost(refreshRate.toDouble()) else refreshRate.toDouble()

            val report = MacrobenchReport(
                testName = "FileBrowserScreen LazyColumn Scrolling (100 items on physical device)",
                totalFrames = totalDurations.size,
                refreshRateHz = refreshRate,
                targetDeadlineMs = targetDeadlineMs,
                meanFrameMs = meanFrame,
                p50Ms = p50,
                p90Ms = p90,
                p95Ms = p95,
                p99Ms = p99,
                jankFramesCount = jankCount,
                jankPercentage = jankPct,
                averageFps = avgFps
            )
            report.printReport()

            assertTrue("At least 30 frames must be recorded during scroll", totalDurations.size >= 30)
            assertTrue("P50 frame time must be under deadline (${targetDeadlineMs}ms), was: $p50 ms", p50 <= targetDeadlineMs)
        } finally {
            scenario.close()
            testDir.deleteRecursively()
        }
    }

    @Test
    fun macrobench02_homeScreenScrollSmoothness() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            scenario.onActivity { activity ->
                activity.viewModel.navigateToDestination(OpenFileDestination.HOME)
            }
            composeTestRule.waitForIdle()

            val frameRecords = CopyOnWriteArrayList<FrameMetricRecord>()
            val handler = Handler(Looper.getMainLooper())
            val frameListener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
                val totalNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
                val layoutNs = frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
                val drawNs = frameMetrics.getMetric(FrameMetrics.DRAW_DURATION)
                val animNs = frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION)
                frameRecords.add(
                    FrameMetricRecord(
                        totalMs = totalNs / 1_000_000.0,
                        layoutMeasureMs = layoutNs / 1_000_000.0,
                        drawMs = drawNs / 1_000_000.0,
                        animMs = animNs / 1_000_000.0
                    )
                )
            }

            scenario.onActivity { activity ->
                activity.window.addOnFrameMetricsAvailableListener(frameListener, handler)
            }

            val homeList = composeTestRule.onNodeWithTag("home_screen_list")
            repeat(5) {
                homeList.performTouchInput {
                    swipeUp(durationMillis = 350)
                }
                composeTestRule.waitForIdle()
                homeList.performTouchInput {
                    swipeDown(durationMillis = 350)
                }
                composeTestRule.waitForIdle()
            }

            var refreshRate = 60f
            scenario.onActivity { activity ->
                activity.window.removeOnFrameMetricsAvailableListener(frameListener)
                refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    activity.display?.refreshRate ?: 60f
                } else {
                    @Suppress("DEPRECATION")
                    activity.windowManager.defaultDisplay.refreshRate
                }
            }

            val targetDeadlineMs = 1000.0 / refreshRate
            val totalDurations = frameRecords.map { it.totalMs }.sorted()
            val jankCount = totalDurations.count { it > targetDeadlineMs }
            val jankPct = if (totalDurations.isNotEmpty()) (jankCount.toDouble() / totalDurations.size) * 100.0 else 0.0
            val meanFrame = if (totalDurations.isNotEmpty()) totalDurations.average() else 0.0
            val p50 = if (totalDurations.isNotEmpty()) totalDurations[(totalDurations.size * 0.50).toInt()] else 0.0
            val p90 = if (totalDurations.isNotEmpty()) totalDurations[(totalDurations.size * 0.90).toInt()] else 0.0
            val p95 = if (totalDurations.isNotEmpty()) totalDurations[(totalDurations.size * 0.95).toInt()] else 0.0
            val p99 = if (totalDurations.isNotEmpty()) totalDurations[(totalDurations.size * 0.99).toInt()] else 0.0
            val avgFps = if (meanFrame > 0) (1000.0 / meanFrame).coerceAtMost(refreshRate.toDouble()) else refreshRate.toDouble()

            val report = MacrobenchReport(
                testName = "HomeScreen Scrolling (Storage Cards & Badges Grid on device)",
                totalFrames = totalDurations.size,
                refreshRateHz = refreshRate,
                targetDeadlineMs = targetDeadlineMs,
                meanFrameMs = meanFrame,
                p50Ms = p50,
                p90Ms = p90,
                p95Ms = p95,
                p99Ms = p99,
                jankFramesCount = jankCount,
                jankPercentage = jankPct,
                averageFps = avgFps
            )
            report.printReport()

            assertTrue("At least 30 frames must be recorded", totalDurations.size >= 30)
            assertTrue("P50 frame time must be under deadline (${targetDeadlineMs}ms), was: $p50 ms", p50 <= targetDeadlineMs)
        } finally {
            scenario.close()
        }
    }
}
