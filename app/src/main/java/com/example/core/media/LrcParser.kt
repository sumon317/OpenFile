package com.example.core.media

import com.example.core.model.FileReference
import java.io.File
import java.util.regex.Pattern

data class LrcLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {
    // Matches [mm:ss.xx], [hh:mm:ss.xx], [mm:ss], [hh:mm:ss:xx], or [mm:ss:xx]
    // Group 1: optional hours (\d{1,2})
    // Group 2: minutes (\d{1,3})
    // Group 3: seconds (\d{2})
    // Group 4: optional fraction/ms (\d{1,3})
    private val TIME_TAG_PATTERN = Pattern.compile(
        "\\[(?:(\\d{1,2}):)?(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]"
    )
    private val OFFSET_PATTERN = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE)
    private val META_TAG_PATTERN = Pattern.compile("^\\[[a-zA-Z]{1,10}:.*\\]$")

    fun parse(content: String): List<LrcLine> {
        val cleanContent = content.removePrefix("\uFEFF")
        val lines = mutableListOf<LrcLine>()
        var globalOffsetMs = 0L

        // First pass: extract global offset tag [offset:+/-ms] if present
        cleanContent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val offsetMatcher = OFFSET_PATTERN.matcher(trimmed)
            if (offsetMatcher.find()) {
                globalOffsetMs = offsetMatcher.group(1)?.toLongOrNull() ?: 0L
            }
        }

        cleanContent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            // Skip metadata header tags e.g. [ti: ...], [ar: ...], [al: ...], [offset: ...]
            if (META_TAG_PATTERN.matcher(trimmed).matches() || OFFSET_PATTERN.matcher(trimmed).find()) {
                return@forEach
            }

            val matcher = TIME_TAG_PATTERN.matcher(trimmed)
            val timeTags = mutableListOf<Long>()
            var lastMatchEnd = 0
            while (matcher.find()) {
                val hourGroup = matcher.group(1)
                val minGroup = matcher.group(2)
                val secGroup = matcher.group(3)
                val msPart = matcher.group(4)

                val hours = hourGroup?.toLongOrNull() ?: 0L
                val min = minGroup?.toLongOrNull() ?: 0L
                val sec = secGroup?.toLongOrNull() ?: 0L
                val ms = when {
                    msPart == null -> 0L
                    msPart.length == 1 -> (msPart.toLongOrNull() ?: 0L) * 100L
                    msPart.length == 2 -> (msPart.toLongOrNull() ?: 0L) * 10L
                    else -> msPart.take(3).toLongOrNull() ?: 0L
                }

                val totalMs = (hours * 3_600_000L + min * 60_000L + sec * 1_000L + ms + globalOffsetMs).coerceAtLeast(0L)
                timeTags.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            if (timeTags.isNotEmpty()) {
                val lyricText = trimmed.substring(lastMatchEnd).trim()
                for (t in timeTags) {
                    lines.add(LrcLine(t, lyricText))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    fun findCurrentLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < 0) return if (lines.isNotEmpty() && positionMs >= 0) 0 else -1
        var low = 0
        var high = lines.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun findMatchingLrcFile(file: FileReference): File? {
        val rawPath = file.metadata["localBackingPath"] ?: file.uriOrPath
        val path = rawPath.removePrefix("file://")
        val audioFile = File(path)
        if (!audioFile.exists()) return null

        val parentDir = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension

        // 1. Check exact base name match: song.lrc
        val exactMatch = File(parentDir, "$baseName.lrc")
        if (exactMatch.exists() && exactMatch.isFile) return exactMatch

        // 2. Case-insensitive match in same directory
        val candidates = runCatching {
            parentDir.listFiles { f ->
                f.isFile && f.extension.equals("lrc", ignoreCase = true)
            }
        }.getOrNull() ?: return null

        return candidates.firstOrNull { it.nameWithoutExtension.equals(baseName, ignoreCase = true) }
    }
}
