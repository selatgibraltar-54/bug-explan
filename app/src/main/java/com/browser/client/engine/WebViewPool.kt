package com.browser.client.engine

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import com.browser.client.data.preferences.BrowserSettings
import com.browser.client.data.preferences.JsSecurityMode

interface WebTabCallbacks {
    fun onPageStarted(tabId: String, url: String)
    fun onPageFinished(tabId: String, url: String, title: String?)
    fun onProgressChanged(tabId: String, progress: Int)
    fun onTitleReceived(tabId: String, title: String)
    fun onNavigationStateChanged(tabId: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onClearanceResult(tabId: String, result: ClearanceCaptureResult)
    fun onPrepareStatus(tabId: String, status: DomainClearanceStatus)
    fun onOfflineError(tabId: String, url: String)
    fun onOpenNewTab(url: String, isIncognito: Boolean = false)
    fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Boolean
    fun onDownloadRequested(
        fileName: String,
        url: String,
        mimeType: String,
        contentLength: Long,
        downloadManagerId: Long
    )
}

class WebViewPool(
    private val context: Context,
    private val clearanceEngine: CloudflareClearanceEngine,
    private val fingerprintManager: SessionFingerprintManager,
    private val networkMonitor: NetworkMonitor
) {
    private val webViews = mutableMapOf<String, WebView>()
    private val currentPageRootDomains = mutableMapOf<String, String>()
    private val incognitoTabIds = mutableSetOf<String>()
    private val incognitoVisitedDomains = mutableSetOf<String>()

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(
        tabId: String,
        isIncognito: Boolean = false,
        settingsProvider: () -> BrowserSettings,
        isDesktopProvider: () -> Boolean,
        callbacks: WebTabCallbacks
    ): WebView {
        if (isIncognito) {
            incognitoTabIds.add(tabId)
        }
        webViews[tabId]?.let { existing ->
            applyRuntimeSettings(existing, settingsProvider(), isIncognito)
            return existing
        }

        val webView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                databaseEnabled = !isIncognito
                @Suppress("DEPRECATION")
                saveFormData = !isIncognito
                @Suppress("DEPRECATION")
                setGeolocationEnabled(!isIncognito)
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                setSupportMultipleWindows(true)
                cacheMode = if (isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                allowFileAccess = false
                mediaPlaybackRequiresUserGesture = isIncognito
                userAgentString = fingerprintManager.resolveUserAgent(
                    stripWebViewToken = settingsProvider().stripWebViewToken,
                    isDesktopMode = isDesktopProvider()
                )
            }
            applyRuntimeSettings(this, settingsProvider(), isIncognito)

            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(this, !isIncognito)

            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                handleDownload(url, userAgent, contentDisposition, mimeType, contentLength, callbacks)
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (request == null) return null
                    val rootDomain = currentPageRootDomains[tabId].orEmpty()
                    return AdAndDataGuard.inspectRequest(
                        request = request,
                        pageRootDomain = rootDomain,
                        settings = settingsProvider(),
                        isIncognito = isIncognito
                    )
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val targetUrl = request.url.toString()
                    val scheme = request.url.scheme?.lowercase().orEmpty()
                    if (scheme != "http" && scheme != "https" && scheme != "about") {
                        // Hardened Incognito blocks silent external app scheme probes/leaks
                        if (!isIncognito) {
                            try {
                                val externalIntent = Intent(Intent.ACTION_VIEW, request.url).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(externalIntent)
                            } catch (_: ActivityNotFoundException) {
                                // Ignore unsupported custom schemes safely
                            }
                        }
                        return true
                    }
                    if (request.isForMainFrame) {
                        if (!networkMonitor.isOnlineNow()) {
                            callbacks.onOfflineError(tabId, targetUrl)
                            return true
                        }
                        val host = DomainUtils.extractHost(targetUrl)
                        val rootDomain = DomainUtils.extractRootDomain(targetUrl)
                        currentPageRootDomains[tabId] = rootDomain
                        if (isIncognito) {
                            if (host.isNotBlank()) incognitoVisitedDomains.add(host)
                            if (rootDomain.isNotBlank()) incognitoVisitedDomains.add(rootDomain)
                        }
                        applyRuntimeSettings(view, settingsProvider(), isIncognito)
                        val prepStatus = clearanceEngine.prepareForUrl(
                            webView = view,
                            url = targetUrl,
                            settings = settingsProvider(),
                            tabDesktopMode = isDesktopProvider(),
                            isIncognito = isIncognito
                        )
                        callbacks.onPrepareStatus(tabId, prepStatus)
                    }
                    return false
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val host = DomainUtils.extractHost(url)
                    val rootDomain = DomainUtils.extractRootDomain(url)
                    currentPageRootDomains[tabId] = rootDomain
                    if (isIncognito) {
                        if (host.isNotBlank()) incognitoVisitedDomains.add(host)
                        if (rootDomain.isNotBlank()) incognitoVisitedDomains.add(rootDomain)
                        view.evaluateJavascript(AdAndDataGuard.INCOGNITO_HARDENING_JS, null)
                    }
                    applyRuntimeSettings(view, settingsProvider(), isIncognito)
                    callbacks.onPageStarted(tabId, url)
                    callbacks.onNavigationStateChanged(tabId, view.canGoBack(), view.canGoForward())
                    val prepStatus = clearanceEngine.prepareForUrl(
                        webView = view,
                        url = url,
                        settings = settingsProvider(),
                        tabDesktopMode = isDesktopProvider(),
                        isIncognito = isIncognito
                    )
                    callbacks.onPrepareStatus(tabId, prepStatus)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (isIncognito) {
                        view.evaluateJavascript(AdAndDataGuard.INCOGNITO_HARDENING_JS, null)
                    }
                    callbacks.onPageFinished(tabId, url, view.title)
                    callbacks.onNavigationStateChanged(tabId, view.canGoBack(), view.canGoForward())

                    view.evaluateJavascript(CloudflareClearanceEngine.CF_DETECTION_JS) { raw ->
                        val isChallenge = raw?.contains("CHALLENGE", ignoreCase = true) == true
                        val result = clearanceEngine.inspectAndCaptureClearance(
                            webView = view,
                            url = url,
                            isChallengeDomDetected = isChallenge,
                            settings = settingsProvider(),
                            isDesktopMode = isDesktopProvider(),
                            isIncognito = isIncognito
                        )
                        callbacks.onClearanceResult(tabId, result)
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame && !networkMonitor.isOnlineNow()) {
                        callbacks.onOfflineError(tabId, request.url.toString())
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    callbacks.onProgressChanged(tabId, newProgress)
                    if (newProgress >= 85) {
                        val currentUrl = view.url.orEmpty()
                        if (currentUrl.startsWith("http")) {
                            view.evaluateJavascript(CloudflareClearanceEngine.CF_DETECTION_JS) { raw ->
                                val isChallenge = raw?.contains("CHALLENGE", ignoreCase = true) == true
                                val result = clearanceEngine.inspectAndCaptureClearance(
                                    webView = view,
                                    url = currentUrl,
                                    isChallengeDomDetected = isChallenge,
                                    settings = settingsProvider(),
                                    isDesktopMode = isDesktopProvider(),
                                    isIncognito = isIncognito
                                )
                                callbacks.onClearanceResult(tabId, result)
                            }
                        }
                    }
                }

                override fun onReceivedTitle(view: WebView, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!title.isNullOrBlank()) {
                        callbacks.onTitleReceived(tabId, title)
                    }
                }

                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    val hitTest = view.hitTestResult
                    val targetUrl = hitTest.extra
                    if (!targetUrl.isNullOrBlank() &&
                        (targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))
                    ) {
                        callbacks.onOpenNewTab(targetUrl, isIncognito)
                        return true
                    }
                    val transportWebView = WebView(context)
                    transportWebView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            v: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            callbacks.onOpenNewTab(request.url.toString(), isIncognito)
                            transportWebView.destroy()
                            return true
                        }
                    }
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    if (transport != null) {
                        transport.webView = transportWebView
                        resultMsg.sendToTarget()
                        return true
                    }
                    return false
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    return callbacks.onShowFileChooser(filePathCallback, fileChooserParams)
                }
            }
        }

        webViews[tabId] = webView
        return webView
    }

    fun getExistingWebView(tabId: String): WebView? = webViews[tabId]

    fun loadUrl(
        tabId: String,
        url: String,
        settings: BrowserSettings,
        isDesktopMode: Boolean,
        isIncognito: Boolean = false
    ): DomainClearanceStatus {
        val wv = webViews[tabId] ?: return DomainClearanceStatus.NONE
        val host = DomainUtils.extractHost(url)
        val rootDomain = DomainUtils.extractRootDomain(url)
        currentPageRootDomains[tabId] = rootDomain
        if (isIncognito) {
            incognitoTabIds.add(tabId)
            if (host.isNotBlank()) incognitoVisitedDomains.add(host)
            if (rootDomain.isNotBlank()) incognitoVisitedDomains.add(rootDomain)
        }
        applyRuntimeSettings(wv, settings, isIncognito)
        val status = clearanceEngine.prepareForUrl(wv, url, settings, isDesktopMode, isIncognito)
        val extraHeaders = buildMap {
            if (settings.dataSaverEnabled) put("Save-Data", "on")
            if (isIncognito) {
                put("DNT", "1")
                put("Sec-GPC", "1")
            }
        }
        if (extraHeaders.isNotEmpty()) {
            wv.loadUrl(url, extraHeaders)
        } else {
            wv.loadUrl(url)
        }
        return status
    }

    fun applyRuntimeSettings(
        webView: WebView,
        settings: BrowserSettings,
        isIncognito: Boolean = false
    ) {
        val cm = CookieManager.getInstance()
        cm.setAcceptThirdPartyCookies(webView, !isIncognito)
        webView.settings.apply {
            if (isIncognito) {
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                allowContentAccess = false
                allowFileAccess = false
                @Suppress("DEPRECATION")
                databaseEnabled = false
                @Suppress("DEPRECATION")
                saveFormData = false
                @Suppress("DEPRECATION")
                setGeolocationEnabled(false)
                cacheMode = WebSettings.LOAD_NO_CACHE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            } else {
                when (settings.jsSecurityMode) {
                    JsSecurityMode.FAST_BALANCED -> {
                        javaScriptEnabled = true
                        javaScriptCanOpenWindowsAutomatically = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        allowContentAccess = true
                    }
                    JsSecurityMode.STRICT_SECURITY -> {
                        javaScriptEnabled = true
                        javaScriptCanOpenWindowsAutomatically = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowContentAccess = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = true
                        }
                    }
                }
                @Suppress("DEPRECATION")
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
        }
    }

    fun removeWebView(tabId: String) {
        val wasIncognito = incognitoTabIds.remove(tabId)
        currentPageRootDomains.remove(tabId)
        webViews.remove(tabId)?.apply {
            stopLoading()
            clearHistory()
            clearFormData()
            clearSslPreferences()
            clearMatches()
            if (wasIncognito) {
                clearCache(true)
            }
            removeAllViews()
            destroy()
        }
        if (wasIncognito && incognitoTabIds.isEmpty()) {
            shredIncognitoArtifacts()
        }
    }

    fun shredIncognitoArtifacts() {
        runCatching {
            WebStorage.getInstance().deleteAllData()
        }
        runCatching {
            @Suppress("DEPRECATION")
            WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
        }
        val protectedDomains = clearanceEngine.getProtectedVaultDomains()
        val cm = CookieManager.getInstance()
        val domainsToScrub = incognitoVisitedDomains.toList()
        domainsToScrub.forEach { domain ->
            val root = DomainUtils.extractRootDomain(domain)
            val isProtected = domain in protectedDomains || root in protectedDomains
            if (!isProtected && domain.isNotBlank()) {
                val raw = cm.getCookie("https://$domain").orEmpty()
                raw.split(";").forEach { segment ->
                    val name = segment.substringBefore("=").trim()
                    if (name.isNotBlank()) {
                        cm.setCookie("https://$domain", "$name=; Max-Age=0; Path=/")
                        cm.setCookie("https://$domain", "$name=; Max-Age=0; Domain=.$domain; Path=/")
                    }
                }
            }
        }
        cm.flush()
        incognitoVisitedDomains.clear()
    }

    fun destroyAll() {
        val keys = webViews.keys.toList()
        keys.forEach { removeWebView(it) }
        if (incognitoVisitedDomains.isNotEmpty()) {
            shredIncognitoArtifacts()
        }
    }

    private fun handleDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long,
        callbacks: WebTabCallbacks
    ) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                addRequestHeader("User-Agent", userAgent)
                setTitle(fileName)
                setDescription("Downloading via ClearPass Browser")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val downloadId = dm?.enqueue(request) ?: -1L
            callbacks.onDownloadRequested(
                fileName = fileName,
                url = url,
                mimeType = mimeType,
                contentLength = contentLength,
                downloadManagerId = downloadId
            )
        } catch (_: Exception) {
            // Ignore malformed download URLs
        }
    }
}
