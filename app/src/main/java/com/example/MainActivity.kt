package com.example

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
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
import com.example.ui.OpenFileAppShell
import com.example.ui.OpenFileViewModel
import com.example.ui.theme.OpenFileTheme

class MainActivity : ComponentActivity() {

    internal val viewModel: OpenFileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
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
        if (savedInstanceState == null) handleOAuthIntent(intent)
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && data.scheme == "com.openfile.manager") {
            viewModel.handleOAuthRedirect(data)
        }
    }

    companion object {
        @Volatile
        private var imageLoaderConfigured = false
    }
}
