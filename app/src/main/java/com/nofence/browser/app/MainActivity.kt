package com.nofence.browser.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.nofence.browser.ui.NoFenceApp
import com.nofence.browser.ui.theme.NoFenceTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: NoFenceViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(
            this,
            NoFenceViewModel.Factory(application)
        )[NoFenceViewModel::class.java]

        // Handle incoming ACTION_VIEW http/https intents
        handleIncomingIntent(intent)

        // Wire system back button to navigate back inside active GeckoSession if possible
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val tab = viewModel.activeTab.value
                    if (tab != null && tab.canGoBack) {
                        viewModel.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        )

        setContent {
            NoFenceTheme {
                NoFenceApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val dataUri = intent?.dataString ?: return
        if (dataUri.startsWith("http://", ignoreCase = true) ||
            dataUri.startsWith("https://", ignoreCase = true)
        ) {
            viewModel.navigateToUrlOrSearch(dataUri)
        }
    }
}
