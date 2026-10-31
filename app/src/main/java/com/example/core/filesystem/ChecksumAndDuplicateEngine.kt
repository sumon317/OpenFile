package com.example.core.filesystem

import com.example.core.model.DuplicateFileGroup
import com.example.core.model.FileReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class FileChecksums(
    val md5Hex: String,
    val sha256Hex: String
)

/**
 * Streaming checksum verifier (MD5 & SHA-256) and two-stage duplicate file detector.
 */
object ChecksumAndDuplicateEngine {

    private const val STREAM_BUFFER_SIZE = 64 * 1024
    private const val PARTIAL_HASH_BYTES = 16 * 1024L

    /**
     * Computes both MD5 and SHA-256 in a single streaming pass over [inputStream].
     */
    suspend fun computeChecksums(inputStream: InputStream): FileChecksums = withContext(Dispatchers.IO) {
        val md5 = MessageDigest.getInstance("MD5")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        inputStream.use { stream ->
            var read = stream.read(buffer)
            while (read > 0) {
                coroutineContext.ensureActive()
                md5.update(buffer, 0, read)
                sha256.update(buffer, 0, read)
                read = stream.read(buffer)
            }
        }
        FileChecksums(
            md5Hex = md5.digest().toHex(),
            sha256Hex = sha256.digest().toHex()
        )
    }

    suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        file.inputStream().use { stream ->
            var read = stream.read(buffer)
            while (read > 0) {
                coroutineContext.ensureActive()
                sha256.update(buffer, 0, read)
                read = stream.read(buffer)
            }
        }
        sha256.digest().toHex()
    }

    /**
     * Detects duplicate files inside [candidateFiles] without hashing unique-sized files:
     * 1. Group by exact byte size (> 0 bytes).
     * 2. For size-collision groups, compute a fast 16 KB head + 16 KB tail fingerprint.
     * 3. For head/tail matches, compute full streaming SHA-256 to guarantee zero false positives.
     */
    suspend fun findDuplicates(
        candidateFiles: List<FileReference>,
        localProvider: LocalFileSystemProvider
    ): List<DuplicateFileGroup> = withContext(Dispatchers.IO) {
        val sizeGroups = candidateFiles
            .filter { !it.isDirectory && it.sizeBytes > 0L }
            .groupBy { it.sizeBytes }
            .filterValues { it.size > 1 }

        val duplicates = mutableListOf<DuplicateFileGroup>()

        for ((sizeBytes, group) in sizeGroups) {
            coroutineContext.ensureActive()
            // Stage 1: Fast partial fingerprint for local files
            val partialGroups = group.groupBy { ref ->
                val file = File(ref.uriOrPath)
                if (file.exists() && file.canRead()) {
                    computeHeadTailFingerprint(file)
                } else {
                    "full_required"
                }
            }.filterValues { it.size > 1 }

            // Stage 2: Full SHA-256 verification
            for ((_, subGroup) in partialGroups) {
                val fullHashGroups = mutableMapOf<String, MutableList<FileReference>>()
                for (ref in subGroup) {
                    coroutineContext.ensureActive()
                    val sha = runCatching {
                        localProvider.openInputStream(ref).getOrThrow().use { stream ->
                            computeChecksums(stream).sha256Hex
                        }
                    }.getOrNull() ?: continue
                    fullHashGroups.getOrPut(sha) { mutableListOf() }.add(ref)
                }
                for ((sha, matchedRefs) in fullHashGroups) {
                    if (matchedRefs.size > 1) {
                        duplicates.add(
                            DuplicateFileGroup(
                                sha256Hash = sha,
                                fileSizeBytes = sizeBytes,
                                files = matchedRefs
                            )
                        )
                    }
                }
            }
        }

        duplicates.sortedByDescending { it.reclaimableBytes }
    }

    private fun computeHeadTailFingerprint(file: File): String {
        val length = file.length()
        if (length <= PARTIAL_HASH_BYTES * 2) {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(file.readBytes())
            return md.digest().toHex()
        }
        val md = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(file, "r").use { raf ->
            val buf = ByteArray(PARTIAL_HASH_BYTES.toInt())
            val headRead = raf.read(buf)
            if (headRead > 0) md.update(buf, 0, headRead)
            raf.seek(length - PARTIAL_HASH_BYTES)
            val tailRead = raf.read(buf)
            if (tailRead > 0) md.update(buf, 0, tailRead)
        }
        return md.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
