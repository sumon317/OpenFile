package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.example.ui.theme.AudioPurple
import com.example.ui.theme.VideoCoral
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

val MediaBadgeShape = RoundedCornerShape(10.dp)

/**
 * High-performance, thread-safe LRU memory cache and decoder for video frames
 * and audio embedded album artwork.
 */
object MediaThumbnailCache {
    private val memoryCache = object : LruCache<String, ImageBitmap>(350) {}
    private val noThumbnailSet = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun get(key: String): ImageBitmap? = memoryCache.get(key)

    fun put(key: String, bitmap: ImageBitmap) {
        memoryCache.put(key, bitmap)
    }

    fun hasNoThumbnail(key: String): Boolean = noThumbnailSet.contains(key)

    fun markNoThumbnail(key: String) {
        noThumbnailSet.add(key)
    }

    fun clear() {
        memoryCache.evictAll()
        noThumbnailSet.clear()
    }

    fun getCacheKey(file: FileReference): String =
        "media_thumb:${file.uriOrPath}:${file.lastModifiedEpochMs}:${file.sizeBytes}"

    /**
     * Extracts and caches a representative frame thumbnail from a video file.
     * Uses platform ContentResolver thumbnailing on API 29+ with graceful fallback
     * to MediaMetadataRetriever and ThumbnailUtils across all Android versions.
     */
    fun loadVideoThumbnail(context: Context, file: FileReference, targetPx: Int = 128): ImageBitmap? {
        val key = getCacheKey(file)
        val cached = get(key)
        if (cached != null) return cached
        if (hasNoThumbnail(key)) return null

        val localPath = file.metadata["localBackingPath"] ?: if (file.providerType == ProviderType.LOCAL) file.uriOrPath else null
        val isContentUri = file.uriOrPath.startsWith("content://")

        val bitmap: Bitmap? = runCatching {
            var extracted: Bitmap? = null

            // 1. Android Q+ (API 29+) hardware accelerated thumbnail loader
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (isContentUri) {
                    extracted = runCatching {
                        context.contentResolver.loadThumbnail(Uri.parse(file.uriOrPath), Size(targetPx, targetPx), null)
                    }.getOrNull()
                } else if (!localPath.isNullOrBlank()) {
                    val localFile = File(localPath)
                    if (localFile.exists() && localFile.canRead()) {
                        extracted = runCatching {
                            ThumbnailUtils.createVideoThumbnail(localFile, Size(targetPx, targetPx), null)
                        }.getOrNull()
                    }
                }
            }

            // 2. High-performance MediaMetadataRetriever fallback
            if (extracted == null) {
                val retriever = MediaMetadataRetriever()
                try {
                    if (isContentUri) {
                        retriever.setDataSource(context, Uri.parse(file.uriOrPath))
                    } else if (!localPath.isNullOrBlank()) {
                        val localFile = File(localPath)
                        if (localFile.exists() && localFile.canRead()) {
                            retriever.setDataSource(localFile.absolutePath)
                        } else {
                            return@runCatching null
                        }
                    } else {
                        return@runCatching null
                    }

                    // Retrieve frame around 1 second mark or first keyframe
                    extracted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        retriever.getScaledFrameAtTime(
                            1_000_000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            targetPx,
                            targetPx
                        ) ?: retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } else {
                        retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } ?: retriever.frameAtTime
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Throwable) {}
                }
            }

            extracted?.let { centerCropToSquare(it, targetPx) }
        }.getOrNull()

        return if (bitmap != null) {
            val imageBitmap = bitmap.asImageBitmap()
            put(key, imageBitmap)
            imageBitmap
        } else {
            markNoThumbnail(key)
            null
        }
    }

    /**
     * Extracts and caches embedded album cover artwork from an audio file.
     * Checks embedded ID3 / FLAC / MP4 APIC artwork frames via MediaMetadataRetriever,
     * and queries Android MediaStore thumbnail caches on API 29+.
     */
    fun loadAudioThumbnail(context: Context, file: FileReference, targetPx: Int = 128): ImageBitmap? {
        val key = getCacheKey(file)
        val cached = get(key)
        if (cached != null) return cached
        if (hasNoThumbnail(key)) return null

        val localPath = file.metadata["localBackingPath"] ?: if (file.providerType == ProviderType.LOCAL) file.uriOrPath else null
        val isContentUri = file.uriOrPath.startsWith("content://")

        val bitmap: Bitmap? = runCatching {
            var extracted: Bitmap? = null

            // 1. Android Q+ (API 29+) MediaStore thumbnail loader for audio
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isContentUri) {
                extracted = runCatching {
                    context.contentResolver.loadThumbnail(Uri.parse(file.uriOrPath), Size(targetPx, targetPx), null)
                }.getOrNull()
            }

            // 2. Embedded ID3 / FLAC APIC picture extraction via MediaMetadataRetriever
            if (extracted == null) {
                val retriever = MediaMetadataRetriever()
                try {
                    if (isContentUri) {
                        retriever.setDataSource(context, Uri.parse(file.uriOrPath))
                    } else if (!localPath.isNullOrBlank()) {
                        val localFile = File(localPath)
                        if (localFile.exists() && localFile.canRead()) {
                            retriever.setDataSource(localFile.absolutePath)
                        } else {
                            return@runCatching null
                        }
                    } else {
                        return@runCatching null
                    }

                    val pictureBytes = retriever.embeddedPicture
                    if (pictureBytes != null && pictureBytes.isNotEmpty()) {
                        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, boundsOpts)
                        val sampleSize = calculateInSampleSize(boundsOpts, targetPx, targetPx)
                        val decodeOpts = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        extracted = BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, decodeOpts)
                    }
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Throwable) {}
                }
            }

            extracted?.let { centerCropToSquare(it, targetPx) }
        }.getOrNull()

        return if (bitmap != null) {
            val imageBitmap = bitmap.asImageBitmap()
            put(key, imageBitmap)
            imageBitmap
        } else {
            markNoThumbnail(key)
            null
        }
    }

    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun centerCropToSquare(bitmap: Bitmap, targetPx: Int): Bitmap {
        if (bitmap.width == targetPx && bitmap.height == targetPx) return bitmap
        val minDim = minOf(bitmap.width, bitmap.height)
        if (minDim <= 0) return bitmap
        val x = (bitmap.width - minDim) / 2
        val y = (bitmap.height - minDim) / 2
        val square = Bitmap.createBitmap(bitmap, x, y, minDim, minDim)
        return if (square.width != targetPx || square.height != targetPx) {
            val scaled = Bitmap.createScaledBitmap(square, targetPx, targetPx, true)
            if (scaled != square && square != bitmap) {
                square.recycle()
            }
            scaled
        } else {
            square
        }
    }
}

/**
 * Thumbnail badge for Video files.
 * Displays decoded video frame with subtle centered play overlay,
 * or falls back seamlessly to the standard video icon badge.
 */
@Composable
fun VideoThumbnailBadge(
    file: FileReference,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    fallbackTint: Color = VideoCoral
) {
    val context = LocalContext.current
    val cacheKey = remember(file.uriOrPath, file.lastModifiedEpochMs, file.sizeBytes) {
        MediaThumbnailCache.getCacheKey(file)
    }

    var thumbnailBitmap by remember(cacheKey) {
        mutableStateOf(MediaThumbnailCache.get(cacheKey))
    }

    LaunchedEffect(cacheKey) {
        if (thumbnailBitmap == null && !MediaThumbnailCache.hasNoThumbnail(cacheKey)) {
            val loaded = withContext(Dispatchers.IO) {
                MediaThumbnailCache.loadVideoThumbnail(context, file, targetPx = 128)
            }
            if (loaded != null) {
                thumbnailBitmap = loaded
            }
        }
    }

    if (thumbnailBitmap != null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(MediaBadgeShape)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = thumbnailBitmap!!,
                contentDescription = file.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Subtle semi-transparent play icon overlay
            Box(
                modifier = Modifier
                    .size((size.value * 0.45f).coerceIn(16f, 24f).dp)
                    .background(Color.Black.copy(alpha = 0.50f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size((size.value * 0.32f).coerceIn(12f, 18f).dp)
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(fallbackTint.copy(alpha = 0.14f), MediaBadgeShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.VideoFile,
                contentDescription = file.displayName,
                tint = fallbackTint,
                modifier = Modifier.size((size.value * 0.57f).dp)
            )
        }
    }
}

/**
 * Thumbnail badge for Audio files.
 * Displays embedded album cover artwork with subtle corner music indicator,
 * or falls back seamlessly to the standard audio icon badge.
 */
@Composable
fun AudioThumbnailBadge(
    file: FileReference,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    fallbackTint: Color = AudioPurple
) {
    val context = LocalContext.current
    val cacheKey = remember(file.uriOrPath, file.lastModifiedEpochMs, file.sizeBytes) {
        MediaThumbnailCache.getCacheKey(file)
    }

    var thumbnailBitmap by remember(cacheKey) {
        mutableStateOf(MediaThumbnailCache.get(cacheKey))
    }

    LaunchedEffect(cacheKey) {
        if (thumbnailBitmap == null && !MediaThumbnailCache.hasNoThumbnail(cacheKey)) {
            val loaded = withContext(Dispatchers.IO) {
                MediaThumbnailCache.loadAudioThumbnail(context, file, targetPx = 128)
            }
            if (loaded != null) {
                thumbnailBitmap = loaded
            }
        }
    }

    if (thumbnailBitmap != null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(MediaBadgeShape)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = thumbnailBitmap!!,
                contentDescription = file.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Subtle corner indicator for music tracks
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .background(AudioPurple.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .background(fallbackTint.copy(alpha = 0.14f), MediaBadgeShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AudioFile,
                contentDescription = file.displayName,
                tint = fallbackTint,
                modifier = Modifier.size((size.value * 0.57f).dp)
            )
        }
    }
}
