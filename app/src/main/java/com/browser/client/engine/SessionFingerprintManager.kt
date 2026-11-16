package com.browser.client.engine

import android.content.Context
import android.webkit.WebSettings

class SessionFingerprintManager(context: Context) {

    private val rawDefaultUa: String by lazy {
        val detected = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
        if (detected.isNullOrBlank() || !detected.contains("Chrome/")) {
            FALLBACK_MOBILE_UA
        } else {
            detected
        }
    }

    private val chromeVersion: String by lazy {
        val match = Regex("""Chrome/(\d+\.\d+\.\d+\.\d+)""").find(rawDefaultUa)
        match?.groupValues?.getOrNull(1) ?: "134.0.0.0"
    }

    /**
     * Produces a clean Android Chrome User-Agent without the `; wv` WebView marker
     * or `Version/X.X` token, ensuring Cloudflare binds cf_clearance cleanly and
     * consistently across sessions.
     */
    fun getMobileUserAgent(stripWebViewToken: Boolean): String {
        if (!stripWebViewToken) return rawDefaultUa
        return rawDefaultUa
            .replace("; wv)", ")")
            .replace("; wv;", ";")
            .replace(Regex("""Version/\d+\.\d+\s*"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }

    fun getDesktopUserAgent(): String {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36"
    }

    fun resolveUserAgent(stripWebViewToken: Boolean, isDesktopMode: Boolean): String {
        return if (isDesktopMode) {
            getDesktopUserAgent()
        } else {
            getMobileUserAgent(stripWebViewToken)
        }
    }

    companion object {
        private const val FALLBACK_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"
    }
}
