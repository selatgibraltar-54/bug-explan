package com.browser.client.engine

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.browser.client.data.preferences.BrowserSettings
import com.browser.client.data.preferences.JsSecurityMode
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

object AdAndDataGuard {

    val blockedAdsCount = AtomicInteger(0)
    val savedDataRequestsCount = AtomicInteger(0)

    private val adHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagmanager.com",
        "google-analytics.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "adnxs.com",
        "adsrvr.org",
        "amazon-adsystem.com",
        "criteo.com",
        "criteo.net",
        "outbrain.com",
        "taboola.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "moatads.com",
        "scorecardresearch.com",
        "quantserve.com",
        "ads.yahoo.com",
        "advertising.com",
        "admob.com",
        "applovin.com",
        "unityads.unity3d.com",
        "facebook.net",
        "connect.facebook.net",
        "analytics.tiktok.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "amplitude.com",
        "branch.io",
        "appsflyer.com",
        "sentry.io",
        "bugsnag.com",
        "newrelic.com",
        "fullstory.com",
        "mouseflow.com",
        "clarity.ms",
        "mc.yandex.ru",
        "pixel.facebook.com",
        "bat.bing.com",
        "ads-twitter.com",
        "analytics.twitter.com",
        "snap.licdn.com",
        "doubleverify.com",
        "demdex.net",
        "omtrdc.net"
    )

    private val incognitoTelemetryPaths = listOf(
        "/collect", "/beacon", "/pixel", "/telemetry", "/analytics", "/metrics", "/track"
    )

    private val heavyMediaExtensions = listOf(".mp4", ".webm", ".m4v", ".mov", ".avi", ".flv")

    fun shouldAllowCloudflareCritical(host: String): Boolean {
        return host == "challenges.cloudflare.com" ||
            host.endsWith(".challenges.cloudflare.com") ||
            host == "static.cloudflareinsights.com"
    }

    fun inspectRequest(
        request: WebResourceRequest,
        pageRootDomain: String,
        settings: BrowserSettings,
        isIncognito: Boolean = false
    ): WebResourceResponse? {
        val uri = request.url ?: return null
        val host = uri.host?.lowercase().orEmpty().removePrefix("www.")
        if (host.isBlank()) return null

        // Never block main frame navigation or Cloudflare challenge infrastructure
        if (request.isForMainFrame || shouldAllowCloudflareCritical(host)) {
            return null
        }

        val reqRoot = DomainUtils.extractRootDomain(host)
        val path = uri.path?.lowercase().orEmpty()

        // 1. AdBlocker (always enforced in Incognito)
        if ((settings.adBlockerEnabled || isIncognito) && isAdHost(host, reqRoot)) {
            blockedAdsCount.incrementAndGet()
            return emptyResponse()
        }

        // 2. Hardened Incognito telemetry & third-party beacon blocking
        if (isIncognito && reqRoot.isNotBlank() && reqRoot != pageRootDomain) {
            if (incognitoTelemetryPaths.any { path.contains(it) }) {
                blockedAdsCount.incrementAndGet()
                return emptyResponse()
            }
        }

        // 3. Data Saver: block heavy background video preloads & third-party heavy assets
        if (settings.dataSaverEnabled) {
            val isHeavyMedia = heavyMediaExtensions.any { path.endsWith(it) }
            val isThirdPartyImage = reqRoot != pageRootDomain &&
                (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
                    path.endsWith(".webp") || path.endsWith(".gif"))
            if (isHeavyMedia || isThirdPartyImage) {
                savedDataRequestsCount.incrementAndGet()
                return emptyResponse()
            }
        }

        // 4. Strict JS Security Mode: block third-party external JS scripts (except Cloudflare Turnstile)
        if (settings.jsSecurityMode == JsSecurityMode.STRICT_SECURITY) {
            val isScript = path.endsWith(".js") ||
                request.requestHeaders["Accept"]?.contains("javascript", ignoreCase = true) == true
            if (isScript && reqRoot.isNotBlank() && reqRoot != pageRootDomain) {
                return emptyResponse("application/javascript")
            }
        }

        return null
    }

    private fun isAdHost(host: String, rootDomain: String): Boolean {
        if (rootDomain in adHosts || host in adHosts) return true
        return adHosts.any { host.endsWith(".$it") }
    }

    private fun emptyResponse(mimeType: String = "text/plain"): WebResourceResponse {
        return WebResourceResponse(
            mimeType,
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    const val INCOGNITO_HARDENING_JS = """
        (function() {
            try {
                if (window.__clearpassIncognitoHardened) return;
                window.__clearpassIncognitoHardened = true;
                try { window.name = ''; } catch (e) {}
                if (navigator) {
                    try { navigator.sendBeacon = function() { return true; }; } catch (e) {}
                    try { Object.defineProperty(navigator, 'doNotTrack', { get: function() { return '1'; }, configurable: true }); } catch (e) {}
                    try { Object.defineProperty(navigator, 'globalPrivacyControl', { get: function() { return true; }, configurable: true }); } catch (e) {}
                    try { navigator.getBattery = function() { return Promise.reject(new Error('Blocked in Private Mode')); }; } catch (e) {}
                    try { navigator.vibrate = function() { return false; }; } catch (e) {}
                    if (navigator.clipboard && navigator.clipboard.readText) {
                        try { navigator.clipboard.readText = function() { return Promise.reject(new Error('Clipboard read blocked')); }; } catch (e) {}
                    }
                }
            } catch (e) {}
        })();
    """
}
