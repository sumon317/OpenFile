package com.example

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.example.ui.OpenFileAppShell
import com.example.ui.OpenFileViewModel
import com.example.ui.theme.OpenFileTheme

class MainActivity : ComponentActivity() {

    internal val viewModel: OpenFileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val initialThemeMode = viewModel.settingsStore.getInitialThemeMode()
        val systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isDark = when (initialThemeMode) {
            com.example.ui.theme.ThemeMode.LIGHT -> false
            com.example.ui.theme.ThemeMode.DARK, com.example.ui.theme.ThemeMode.AMOLED -> true
            com.example.ui.theme.ThemeMode.SYSTEM -> systemDark
        }
        window.decorView.setBackgroundColor(if (isDark) 0xFF111318.toInt() else 0xFFF9F9FF.toInt())
        super.onCreate(savedInstanceState)
        configureFastImageLoader()
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            OpenFileTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.useDynamicColor
            ) {
                OpenFileAppShell(viewModel = viewModel)
            }
        }
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
            }
            else -> null
        }

        if (uri != null) {
            var name = "Document"
            var size = 0L
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }
            } catch (_: Exception) {}

            val ref = FileReference(
                providerType = ProviderType.SAF,
                uriOrPath = uri.toString(),
                displayName = name,
                parentUriOrPath = null,
                isDirectory = false,
                sizeBytes = size,
                mimeType = intent.type ?: contentResolver.getType(uri) ?: "application/octet-stream"
            )
            viewModel.openBuiltInPreview(ref)
        }
    }

    private fun configureFastImageLoader() {
        if (imageLoaderConfigured) return
        synchronized(Companion) {
            if (imageLoaderConfigured) return
            val appCtx = applicationContext
            val loader = ImageLoader.Builder(appCtx)
                .memoryCache {
                    MemoryCache.Builder(appCtx)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(appCtx.cacheDir.resolve("openfile_thumb_cache"))
                        .maxSizeBytes(64L * 1024L * 1024L)
                        .build()
                }
                .bitmapConfig(Bitmap.Config.RGB_565)
                .allowHardware(true)
                .crossfade(false)
                .build()
            Coil.setImageLoader(loader)
            imageLoaderConfigured = true
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStorageVolumes()
        viewModel.onResumeCheckPendingUpdateInstall()
    }

    companion object {
        @Volatile
        private var imageLoaderConfigured = false
    }
}
