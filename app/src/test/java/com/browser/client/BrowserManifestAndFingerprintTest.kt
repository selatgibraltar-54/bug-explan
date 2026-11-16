package com.browser.client

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.test.core.app.ApplicationProvider
import com.browser.client.data.preferences.BrowserSettings
import com.browser.client.engine.AdAndDataGuard
import com.browser.client.engine.SessionFingerprintManager
import com.browser.client.ui.BrowserViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrowserManifestAndFingerprintTest {

    @Test
    fun `app name matches Vortex and package name remains unchanged`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Vortex", context.getString(R.string.app_name))
        assertEquals("com.browser.client", context.packageName)
    }

    @Test
    fun `manifest handles https VIEW intent for default browser eligibility`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val resolved = context.packageManager.queryIntentActivities(
            viewIntent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        assertTrue(resolved.any { it.activityInfo.packageName == context.packageName })
    }

    @Test
    fun `clean mobile user agent strips wv marker`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = SessionFingerprintManager(context)
        val ua = manager.getMobileUserAgent(stripWebViewToken = true)
        assertFalse(ua.contains("; wv"))
        assertTrue(ua.contains("Chrome/"))
    }

    @Test
    fun `hardened incognito forces ad and third party telemetry blocking while preserving cloudflare challenge`() {
        val disabledAdSetting = BrowserSettings(adBlockerEnabled = false)

        val trackerRequest = fakeWebRequest("https://doubleclick.net/ad.js", isMainFrame = false)
        assertNotNull(
            AdAndDataGuard.inspectRequest(
                request = trackerRequest,
                pageRootDomain = "example.com",
                settings = disabledAdSetting,
                isIncognito = true
            )
        )

        val beaconRequest = fakeWebRequest("https://metrics.other.com/collect?id=1", isMainFrame = false)
        assertNotNull(
            AdAndDataGuard.inspectRequest(
                request = beaconRequest,
                pageRootDomain = "example.com",
                settings = disabledAdSetting,
                isIncognito = true
            )
        )

        val cfChallenge = fakeWebRequest("https://challenges.cloudflare.com/turnstile/v0/api.js", isMainFrame = false)
        assertNull(
            AdAndDataGuard.inspectRequest(
                request = cfChallenge,
                pageRootDomain = "example.com",
                settings = disabledAdSetting,
                isIncognito = true
            )
        )
    }

    @Test
    fun `viewModel opens incognito tab and shreds all incognito tabs cleanly`() {
        val app = ApplicationProvider.getApplicationContext<Application>() as ClearPassApplication
        val viewModel = BrowserViewModel(app, app.container)

        assertFalse(viewModel.uiState.value.activeTab.isIncognito)
        viewModel.openIncognitoTab()
        assertTrue(viewModel.uiState.value.activeTab.isIncognito)
        assertEquals(2, viewModel.uiState.value.tabs.size)

        viewModel.closeAllIncognitoTabs()
        assertEquals(1, viewModel.uiState.value.tabs.size)
        assertFalse(viewModel.uiState.value.activeTab.isIncognito)
    }

    @Test
    fun `desktop user agent switches to Windows Chrome and viewModel toggles active tab desktop mode`() {
        val app = ApplicationProvider.getApplicationContext<Application>() as ClearPassApplication
        val manager = SessionFingerprintManager(app)
        val desktopUa = manager.resolveUserAgent(stripWebViewToken = true, isDesktopMode = true)
        assertTrue(desktopUa.contains("Windows NT 10.0; Win64; x64"))
        assertFalse(desktopUa.contains("Mobile"))

        val viewModel = BrowserViewModel(app, app.container)
        assertFalse(viewModel.uiState.value.activeTab.isDesktopMode)
        viewModel.toggleDesktopMode()
        assertTrue(viewModel.uiState.value.activeTab.isDesktopMode)
        viewModel.toggleDesktopMode()
        assertFalse(viewModel.uiState.value.activeTab.isDesktopMode)
    }

    private fun fakeWebRequest(url: String, isMainFrame: Boolean): WebResourceRequest {
        val parsed = Uri.parse(url)
        return object : WebResourceRequest {
            override fun getUrl(): Uri = parsed
            override fun isForMainFrame(): Boolean = isMainFrame
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
        }
    }
}
