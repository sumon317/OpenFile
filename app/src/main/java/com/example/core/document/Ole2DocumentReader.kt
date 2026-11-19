package com.example.core.document

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lightweight, zero-dependency streaming extractor for Microsoft Compound File Binary (OLE2)
 * documents (.doc, .xls, .ppt).
 *
 * Implements:
 * - OLE2 header and sector parsing (512-byte & 4096-byte sectors)
 * - DIFAT and FAT chain traversal
 * - Directory entry stream location
 * - Safe stream extraction for "WordDocument", "Workbook", "PowerPoint Document"
 * - Graceful fallback heuristics for corrupted or truncated documents
 */
object Ole2DocumentReader {

    private val OLE2_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
    )

    private const val ENDOFCHAIN = -2
    private const val FREESECT = -1

    fun isOle2File(file: File): Boolean {
        if (!file.exists() || file.length() < 512) return false
        val header = ByteArray(8)
        return try {
            file.inputStream().use { it.read(header) == 8 && header.contentEquals(OLE2_MAGIC) }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extracts a named stream (e.g., "WordDocument", "Workbook", "PowerPoint Document")
     * from an OLE2 file.
     */
    fun extractStream(file: File, targetStreamName: String): ByteArray? {
        if (!isOle2File(file)) return null

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val headerBuf = ByteArray(512)
                raf.seek(0)
                raf.readFully(headerBuf)
                val bb = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)

                val sectorShift = bb.getShort(30).toInt()
                val sectorSize = 1 shl sectorShift
                val numFatSectors = bb.getInt(44)
                val firstDirSector = bb.getInt(48)
                val numDifatSectors = bb.getInt(80)

                if (sectorSize !in 512..4096 || numFatSectors <= 0) return null

                // 1. Read DIFAT entries (first 109 entries in header)
                val fatSectors = mutableListOf<Int>()
                bb.position(76)
                val maxHeaderDifat = 109.coerceAtMost(numFatSectors)
                for (i in 0 until maxHeaderDifat) {
                    val sec = bb.getInt()
                    if (sec >= 0) fatSectors.add(sec)
                }

                // If more DIFAT sectors exist, read them
                var nextDifatSector = bb.getInt(76 + 109 * 4)
                var difatCount = 0
                while (nextDifatSector >= 0 && difatCount < numDifatSectors && fatSectors.size < numFatSectors) {
                    difatCount++
                    val difatBuf = ByteArray(sectorSize)
                    raf.seek((nextDifatSector + 1L) * sectorSize)
                    raf.readFully(difatBuf)
                    val dbb = ByteBuffer.wrap(difatBuf).order(ByteOrder.LITTLE_ENDIAN)
                    val entriesInDifat = (sectorSize / 4) - 1
                    for (i in 0 until entriesInDifat) {
                        if (fatSectors.size >= numFatSectors) break
                        val sec = dbb.getInt()
                        if (sec >= 0) fatSectors.add(sec)
                    }
                    nextDifatSector = dbb.getInt((sectorSize - 4))
                }

                // 2. Read full FAT
                val intsPerSector = sectorSize / 4
                val totalFatEntries = fatSectors.size * intsPerSector
                val fat = IntArray(totalFatEntries)
                val sectorBytes = ByteArray(sectorSize)

                for ((idx, fatSec) in fatSectors.withIndex()) {
                    if (fatSec < 0) continue
                    raf.seek((fatSec + 1L) * sectorSize)
                    raf.readFully(sectorBytes)
                    val sbb = ByteBuffer.wrap(sectorBytes).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until intsPerSector) {
                        fat[idx * intsPerSector + i] = sbb.getInt()
                    }
                }

                // 3. Read Directory stream
                val dirBytes = readChain(raf, firstDirSector, fat, sectorSize, maxBytes = 256 * 1024)
                if (dirBytes == null || dirBytes.size < 128) return null

                // 4. Find the target stream directory entry
                val numEntries = dirBytes.size / 128
                val db = ByteBuffer.wrap(dirBytes).order(ByteOrder.LITTLE_ENDIAN)

                var streamStartSector = -1
                var streamSize = 0L

                for (e in 0 until numEntries) {
                    val offset = e * 128
                    val nameLen = db.getShort(offset + 64).toInt()
                    if (nameLen in 2..64) {
                        val nameChars = CharArray((nameLen / 2) - 1)
                        for (c in nameChars.indices) {
                            nameChars[c] = db.getChar(offset + (c * 2))
                        }
                        val entryName = String(nameChars)
                        val entryType = db.get(offset + 66).toInt()

                        if (entryName.equals(targetStreamName, ignoreCase = true) ||
                            (targetStreamName.equals("Workbook", ignoreCase = true) && entryName.equals("Book", ignoreCase = true))
                        ) {
                            streamStartSector = db.getInt(offset + 116)
                            streamSize = db.getLong(offset + 120)
                            break
                        }
                    }
                }

                if (streamStartSector < 0 || streamSize <= 0) return null

                // Cap stream size to 64MB for safety
                val safeSize = streamSize.coerceAtMost(64L * 1024 * 1024).toInt()
                readChain(raf, streamStartSector, fat, sectorSize, safeSize)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readChain(
        raf: RandomAccessFile,
        startSector: Int,
        fat: IntArray,
        sectorSize: Int,
        maxBytes: Int
    ): ByteArray? {
        if (startSector < 0) return null
        val result = ByteArray(maxBytes)
        var currentSector = startSector
        var bytesRead = 0
        val tempBuf = ByteArray(sectorSize)
        val visited = HashSet<Int>()

        while (currentSector >= 0 && currentSector != ENDOFCHAIN && bytesRead < maxBytes) {
            if (!visited.add(currentSector)) break // Cycle prevention
            val fileOffset = (currentSector + 1L) * sectorSize
            if (fileOffset + sectorSize > raf.length()) break

            raf.seek(fileOffset)
            val toRead = (maxBytes - bytesRead).coerceAtMost(sectorSize)
            raf.readFully(tempBuf, 0, toRead)
            System.arraycopy(tempBuf, 0, result, bytesRead, toRead)
            bytesRead += toRead

            currentSector = if (currentSector < fat.size) fat[currentSector] else ENDOFCHAIN
        }

        return if (bytesRead > 0) result.copyOf(bytesRead) else null
    }

    /**
     * Extracts readable text strings from a raw binary byte array (supports UTF-16LE and ASCII).
     */
    fun extractReadableText(data: ByteArray): List<String> {
        val lines = mutableListOf<String>()
        val sb = StringBuilder()

        // 1. Try UTF-16LE scan
        var i = 0
        while (i < data.size - 1) {
            val b1 = data[i].toInt() and 0xFF
            val b2 = data[i + 1].toInt() and 0xFF
            val ch = (b2 shl 8) or b1
            if (ch in 32..126 || ch in 160..0xFFFD || ch == 10 || ch == 13 || ch == 9) {
                if (ch == 10 || ch == 13) {
                    val str = sb.toString().trim()
                    if (str.length >= 2) lines.add(str)
                    sb.setLength(0)
                } else {
                    sb.append(ch.toChar())
                }
            } else {
                if (sb.length >= 4) {
                    lines.add(sb.toString().trim())
                }
                sb.setLength(0)
            }
            i += 2
        }
        if (sb.length >= 4) lines.add(sb.toString().trim())

        // If UTF-16LE yielded little text, try ASCII scan
        if (lines.size < 3) {
            sb.setLength(0)
            val asciiLines = mutableListOf<String>()
            for (b in data) {
                val c = b.toInt() and 0xFF
                if (c in 32..126 || c == 9) {
                    sb.append(c.toChar())
                } else if (c == 10 || c == 13) {
                    val str = sb.toString().trim()
                    if (str.length >= 3) asciiLines.add(str)
                    sb.setLength(0)
                } else {
                    if (sb.length >= 4) asciiLines.add(sb.toString().trim())
                    sb.setLength(0)
                }
            }
            if (sb.length >= 4) asciiLines.add(sb.toString().trim())
            if (asciiLines.size > lines.size) return asciiLines
        }

        return lines
    }
}
