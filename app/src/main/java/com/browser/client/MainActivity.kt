package com.browser.client

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browser.client.engine.DefaultBrowserHelper
import com.browser.client.engine.DomainUtils
import com.browser.client.ui.BrowserViewModel
import com.browser.client.ui.screens.BrowserScreen
import com.browser.client.ui.theme.ClearPassTheme

class MainActivity : ComponentActivity() {

    private val app: ClearPassApplication
        get() = application as ClearPassApplication

    private val viewModel: BrowserViewModel by viewModels {
        BrowserViewModel.Factory(application, app.container)
    }

    private val defaultBrowserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshDefaultBrowserStatus()
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val callback = viewModel.pendingFileChooserCallback
        viewModel.pendingFileChooserCallback = null
        if (callback != null) {
            if (uris.isEmpty()) {
                callback.onReceiveValue(null)
            } else {
                callback.onReceiveValue(uris.toTypedArray())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.onRequestFileChooser = { params ->
            val mimeTypes = params?.acceptTypes
                ?.filter { !it.isNullOrBlank() }
                ?.toTypedArray()
                ?.takeIf { it.isNotEmpty() }
                ?: arrayOf("*/*")
            fileChooserLauncher.launch(mimeTypes)
        }

        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isIncognitoActive = uiState.activeTab.isIncognito

            LaunchedEffect(isIncognitoActive) {
                if (isIncognitoActive) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            ClearPassTheme(
                themeMode = settings.themeMode,
                isIncognito = isIncognitoActive
            ) {
                BrowserScreen(
                    viewModel = viewModel,
                    webViewPool = app.container.webViewPool,
                    onRequestDefaultBrowser = {
                        val roleIntent = DefaultBrowserHelper.createDefaultBrowserIntent(this)
                        defaultBrowserLauncher.launch(roleIntent)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDefaultBrowserStatus()
    }

    override fun onPause() {
        app.container.clearanceEngine.flushCookies()
        super.onPause()
    }

    override fun onStop() {
        app.container.clearanceEngine.flushCookies()
        super.onStop()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val url = intent.dataString
                if (!url.isNullOrBlank()) {
                    viewModel.openExternalUrl(url)
                }
            }
            Intent.ACTION_WEB_SEARCH -> {
                val query = intent.getStringExtra(SearchManager.QUERY)
                if (!query.isNullOrBlank()) {
                    val url = DomainUtils.resolveInputToUrl(
                        query,
                        viewModel.settings.value.searchEngine
                    )
                    viewModel.openExternalUrl(url)
                }
            }
        }
    }
}
