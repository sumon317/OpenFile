package com.example

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.example.ui.OpenFileAppShell
import com.example.ui.OpenFileViewModel
import com.example.ui.theme.OpenFileTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> {
                val singleUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
                listOfNotNull(singleUri ?: intent.data)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                } ?: emptyList()
            }
            else -> emptyList()
        }

        val targetUri = uris.firstOrNull() ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            var name = "Document"
            var size = 0L
            try {
                contentResolver.query(targetUri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }
            } catch (_: Exception) {}

            if (name == "Document" && targetUri.lastPathSegment != null) {
                name = targetUri.lastPathSegment?.substringAfterLast('/') ?: "Document"
            }

            // Cache incoming content stream to a local temp file so random-access readers (PDF, ZIP, Video, Office) work reliably
            var localBackingPath: String? = null
            if (targetUri.scheme == "file") {
                localBackingPath = targetUri.path
            } else if (targetUri.scheme == "content") {
                runCatching {
                    val safeDir = File(cacheDir, "opened_externally").apply { mkdirs() }
                    val sanitizedName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "opened_file" }
                    val tempFile = File(safeDir, sanitizedName)
                    contentResolver.openInputStream(targetUri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0L) {
                        localBackingPath = tempFile.absolutePath
                        if (size == 0L) size = tempFile.length()
                    }
                }
            }

            val resolvedPath = localBackingPath
            val meta: Map<String, String> = if (!resolvedPath.isNullOrBlank()) {
                mapOf("localBackingPath" to resolvedPath)
            } else {
                emptyMap()
            }

            val ref = FileReference(
                providerType = ProviderType.SAF,
                uriOrPath = targetUri.toString(),
                displayName = name,
                parentUriOrPath = null,
                isDirectory = false,
                sizeBytes = size,
                mimeType = intent.type ?: contentResolver.getType(targetUri) ?: "application/octet-stream",
                metadata = meta
            )

            withContext(Dispatchers.Main) {
                viewModel.onFileItemClicked(ref)
            }
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
