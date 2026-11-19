package com.example.core.media

import com.example.core.model.FileReference
import java.io.File
import java.util.regex.Pattern

data class LrcLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {
    // Matches [01:23.45] or [01:23:45] or [01:23]
    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{2,3}))?\\]")

    fun parse(content: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") || trimmed.startsWith("[offset:")
            ) {
                return@forEach
            }
            val matcher = TIME_TAG_PATTERN.matcher(trimmed)
            val timeTags = mutableListOf<Long>()
            var lastMatchEnd = 0
            while (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msPart = matcher.group(3)
                val ms = when {
                    msPart == null -> 0L
                    msPart.length == 2 -> (msPart.toLongOrNull() ?: 0L) * 10L
                    else -> msPart.toLongOrNull() ?: 0L
                }
                val totalMs = min * 60_000L + sec * 1_000L + ms
                timeTags.add(totalMs)
                lastMatchEnd = matcher.end()
            }
            val lyricText = trimmed.substring(lastMatchEnd).trim()
            if (lyricText.isNotEmpty() || timeTags.isNotEmpty()) {
                for (t in timeTags) {
                    lines.add(LrcLine(t, lyricText))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    fun findCurrentLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
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
        val path = file.metadata["localBackingPath"] ?: file.uriOrPath
        val audioFile = File(path)
        if (!audioFile.exists()) return null

        val parentDir = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension

        // 1. Check exact base name match: song.lrc
        val exactMatch = File(parentDir, "$baseName.lrc")
        if (exactMatch.exists() && exactMatch.isFile) return exactMatch

        // 2. Case-insensitive match in same directory
        val candidates = parentDir.listFiles { f ->
            f.isFile && f.extension.equals("lrc", ignoreCase = true)
        } ?: return null

        return candidates.firstOrNull { it.nameWithoutExtension.equals(baseName, ignoreCase = true) }
    }
}
