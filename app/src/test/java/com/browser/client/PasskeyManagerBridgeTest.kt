package com.browser.client

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import com.browser.client.engine.GooglePasswordManagerLaunchResult
import com.browser.client.engine.PasskeyManagerBridge
import com.browser.client.ui.BrowserViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PasskeyManagerBridgeTest {

    @Test
    fun `isPasskeyUri detects fido webauthn passkey and intent schemes`() {
        assertTrue(PasskeyManagerBridge.isPasskeyUri("fido:/1234567890"))
        assertTrue(PasskeyManagerBridge.isPasskeyUri("FIDO:/ABC"))
        assertTrue(PasskeyManagerBridge.isPasskeyUri("webauthn://login?rp=example.com"))
        assertTrue(PasskeyManagerBridge.isPasskeyUri("passkey://verify"))
        assertTrue(
            PasskeyManagerBridge.isPasskeyUri(
                "intent://auth#Intent;scheme=fido;package=com.google.android.gms;end"
            )
        )
        assertFalse(PasskeyManagerBridge.isPasskeyUri("https://github.com/login"))
        assertFalse(PasskeyManagerBridge.isPasskeyUri("https://passwords.google.com"))
        assertFalse(PasskeyManagerBridge.isPasskeyUri(""))
    }

    @Test
    fun `openGooglePasswordManager launches native GMS activity when registered`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = shadowOf(context.packageManager)

        val component = ComponentName(
            "com.google.android.gms",
            "com.google.android.gms.credential.manager.PasswordManagerActivity"
        )
        val activityInfo = ActivityInfo().apply {
            packageName = component.packageName
            name = component.className
        }
        shadowPm.addOrUpdateActivity(activityInfo)
        shadowPm.addIntentFilterForActivity(
            component,
            IntentFilter("com.google.android.gms.credential.manager.ACTION_OPEN_CREDENTIAL_MANAGER").apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            }
        )

        var fallbackCalled = false
        val result = PasskeyManagerBridge.openGooglePasswordManager(
            context = context,
            onFallbackWebUrl = { fallbackCalled = true }
        )

        assertEquals(GooglePasswordManagerLaunchResult.OPENED_NATIVE, result)
        assertFalse(fallbackCalled)

        val startedIntent = shadowOf(context).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals("com.google.android.gms", startedIntent.`package`)
    }

    @Test
    fun `openGooglePasswordManager invokes passwords google com fallback when native handler absent`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        var capturedFallbackUrl: String? = null

        val result = PasskeyManagerBridge.openGooglePasswordManager(
            context = context,
            onFallbackWebUrl = { capturedFallbackUrl = it }
        )

        assertEquals(GooglePasswordManagerLaunchResult.OPENED_WEB_FALLBACK, result)
        assertEquals(PasskeyManagerBridge.GOOGLE_PASSWORD_MANAGER_WEB_URL, capturedFallbackUrl)
    }

    @Test
    fun `viewModel opens Google Password Manager on passkey URI and clears prompt on dismiss`() {
        val app = ApplicationProvider.getApplicationContext<Application>() as ClearPassApplication
        val viewModel = BrowserViewModel(app, app.container)
        val tabId = viewModel.uiState.value.activeTab.id

        viewModel.onPasskeyRequested(
            tabId = tabId,
            callId = "",
            mode = "uri",
            requestJson = "",
            pageUrl = "https://accounts.example.com/signin",
            isIncognito = false
        )

        val prompt = viewModel.uiState.value.passkeyPrompt
        assertNotNull(prompt)
        assertEquals("accounts.example.com", prompt?.originHost)
        assertTrue(prompt?.openedPasswordManagerAutomatically == true)

        viewModel.dismissPasskeyPrompt()
        assertNull(viewModel.uiState.value.passkeyPrompt)
    }

    @Test
    fun `viewModel pauses external password manager auto-launch in Hardened Incognito until confirmed`() {
        val app = ApplicationProvider.getApplicationContext<Application>() as ClearPassApplication
        val viewModel = BrowserViewModel(app, app.container)
        viewModel.openIncognitoTab("https://github.com/login")
        val incognitoTab = viewModel.uiState.value.activeTab
        assertTrue(incognitoTab.isIncognito)

        viewModel.onPasskeyRequested(
            tabId = incognitoTab.id,
            callId = "pk_1",
            mode = "get",
            requestJson = "{\"challenge\":\"abc\"}",
            pageUrl = "https://github.com/login",
            isIncognito = true
        )

        val prompt = viewModel.uiState.value.passkeyPrompt
        assertNotNull(prompt)
        assertTrue(prompt?.isIncognito == true)
        assertFalse(prompt?.openedPasswordManagerAutomatically == true)

        viewModel.confirmOpenGooglePasswordManagerFromPrompt(useWebPortal = true)
        assertNull(viewModel.uiState.value.passkeyPrompt)
        assertEquals(
            PasskeyManagerBridge.GOOGLE_PASSWORD_MANAGER_WEB_URL,
            viewModel.uiState.value.activeTab.url
        )
        assertFalse(viewModel.uiState.value.activeTab.isIncognito)
    }
}
