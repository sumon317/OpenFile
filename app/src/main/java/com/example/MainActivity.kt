package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.OpenFileAppShell
import com.example.ui.OpenFileViewModel
import com.example.ui.theme.OpenFileTheme

class MainActivity : ComponentActivity() {

    internal val viewModel: OpenFileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
