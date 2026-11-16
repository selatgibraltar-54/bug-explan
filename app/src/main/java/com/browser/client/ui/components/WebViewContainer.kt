package com.browser.client.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.browser.client.data.preferences.BrowserSettings
import com.browser.client.engine.WebTabCallbacks
import com.browser.client.engine.WebViewPool

@Composable
fun WebViewContainer(
    tabId: String,
    isIncognito: Boolean,
    isDesktopMode: Boolean,
    pendingUrlToLoad: String?,
    webViewPool: WebViewPool,
    settingsProvider: () -> BrowserSettings,
    callbacks: WebTabCallbacks,
    onConsumePendingLoad: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { container ->
            val webView = webViewPool.getOrCreateWebView(
                tabId = tabId,
                isIncognito = isIncognito,
                uiContext = container.context,
                settingsProvider = settingsProvider,
                isDesktopProvider = { isDesktopMode },
                callbacks = callbacks
            )
            if (webView.parent !== container) {
                (webView.parent as? ViewGroup)?.removeView(webView)
                container.removeAllViews()
                container.addView(
                    webView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
            if (!pendingUrlToLoad.isNullOrBlank()) {
                onConsumePendingLoad(tabId)
            }
        }
    )
}
