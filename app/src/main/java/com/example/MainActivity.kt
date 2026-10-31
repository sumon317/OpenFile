package com.example

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

    private val openFileViewModel: OpenFileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by openFileViewModel.settings.collectAsStateWithLifecycle()
            OpenFileTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.useDynamicColor
            ) {
                OpenFileAppShell(viewModel = openFileViewModel)
            }
        }
    }
}
