package com.example.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import com.example.ui.theme.ApkGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-performance LRU cache and decoder for installed Android application icons
 * and uninstalled APK package icons.
 */
object AppIconCache {
    private val memoryCache = object : LruCache<String, ImageBitmap>(400) {}

    fun get(key: String): ImageBitmap? = memoryCache.get(key)

    fun put(key: String, bitmap: ImageBitmap) {
        memoryCache.put(key, bitmap)
    }

    fun clear() {
        memoryCache.evictAll()
    }

    /**
     * Loads the icon for an installed app by package name.
     */
    fun loadInstalledAppIcon(context: Context, packageName: String, targetPx: Int = 128): ImageBitmap? {
        val cached = get(packageName)
        if (cached != null) return cached

        return runCatching {
            val pm = context.packageManager
            val drawable: Drawable = try {
                if (packageName == context.packageName) {
                    context.applicationInfo.loadIcon(pm) ?: pm.defaultActivityIcon
                } else {
                    pm.getApplicationIcon(packageName)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                if (packageName == context.packageName) {
                    context.applicationInfo.loadIcon(pm) ?: pm.defaultActivityIcon
                } else {
                    return@runCatching null
                }
            } catch (e: Exception) {
                if (packageName == context.packageName) {
                    context.applicationInfo.loadIcon(pm) ?: pm.defaultActivityIcon
                } else {
                    return@runCatching null
                }
            }
            val bitmap = drawableToBitmap(drawable, targetPx)
            val imageBitmap = bitmap.asImageBitmap()
            put(packageName, imageBitmap)
            imageBitmap
        }.getOrNull()
    }

    /**
     * Loads the icon from an uninstalled `.apk` file archive on disk.
     */
    fun loadApkIcon(context: Context, apkPath: String, targetPx: Int = 128): ImageBitmap? {
        val file = File(apkPath)
        if (!file.exists() || !file.canRead()) return null
        val cacheKey = "apk:${file.absolutePath}:${file.lastModified()}"
        val cached = get(cacheKey) ?: get(apkPath)
        if (cached != null) return cached

        return runCatching {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkPath, 0)
            } ?: return null

            val appInfo = pkgInfo.applicationInfo ?: return null
            appInfo.sourceDir = apkPath
            appInfo.publicSourceDir = apkPath

            val drawable = appInfo.loadIcon(pm) ?: return null
            val bitmap = drawableToBitmap(drawable, targetPx)
            val imageBitmap = bitmap.asImageBitmap()
            put(cacheKey, imageBitmap)
            put(apkPath, imageBitmap)
            imageBitmap
        }.getOrNull()
    }

    /**
     * Safely converts any Android [Drawable] (AdaptiveIconDrawable, VectorDrawable, BitmapDrawable, etc.)
     * to a hardware-renderable [Bitmap].
     */
    fun drawableToBitmap(drawable: Drawable, targetPx: Int = 128): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            val bmp = drawable.bitmap
            if (bmp.width == targetPx && bmp.height == targetPx) return bmp
            if (bmp.width > 0 && bmp.height > 0) {
                return Bitmap.createScaledBitmap(bmp, targetPx, targetPx, true)
            }
        }

        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else targetPx
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else targetPx
        val bitmap = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return if (w != targetPx || h != targetPx) {
            Bitmap.createScaledBitmap(bitmap, targetPx, targetPx, true)
        } else {
            bitmap
        }
    }
}

/**
 * Composable that displays an Android Application icon.
 * Supports both installed packages ([packageName]) and uninstalled `.apk` files ([apkPath]).
 * Features instantaneous LRU memory cache hits, background coroutine decoding,
 * and elegant placeholder fallbacks.
 */
@Composable
fun AppIcon(
    modifier: Modifier = Modifier,
    packageName: String? = null,
    apkPath: String? = null,
    size: Dp = 40.dp,
    fallbackTint: Color = ApkGreen,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val key = packageName ?: apkPath ?: ""

    var iconBitmap by remember(key) {
        mutableStateOf(if (key.isNotEmpty()) AppIconCache.get(key) else null)
    }

    LaunchedEffect(key) {
        if (iconBitmap == null && key.isNotEmpty()) {
            val loaded = withContext(Dispatchers.IO) {
                if (!packageName.isNullOrBlank()) {
                    AppIconCache.loadInstalledAppIcon(context, packageName)
                        ?: (if (!apkPath.isNullOrBlank()) AppIconCache.loadApkIcon(context, apkPath) else null)
                } else if (!apkPath.isNullOrBlank()) {
                    AppIconCache.loadApkIcon(context, apkPath)
                } else {
                    null
                }
            }
            if (loaded != null) {
                iconBitmap = loaded
            }
        }
    }

    val shape = remember { RoundedCornerShape(8.dp) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        val bmp = iconBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Surface(
                shape = shape,
                color = fallbackTint.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Android,
                        contentDescription = contentDescription,
                        tint = fallbackTint,
                        modifier = Modifier.size((size.value * 0.7f).dp)
                    )
                }
            }
        }
    }
}
