package com.browser.client.engine

import android.webkit.CookieManager
import android.webkit.WebView
import com.browser.client.data.local.ClearanceEntity
import com.browser.client.data.preferences.BrowserSettings
import com.browser.client.data.repository.ClearanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class DomainClearanceStatus {
    NONE,
    VERIFYING_1X,
    VERIFIED_1X,
    RESTORED_FROM_VAULT
}

data class ClearanceCaptureResult(
    val status: DomainClearanceStatus,
    val domain: String,
    val newlyCaptured: Boolean,
    val entity: ClearanceEntity? = null
)

class CloudflareClearanceEngine(
    private val repository: ClearanceRepository,
    private val fingerprintManager: SessionFingerprintManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vaultCache = ConcurrentHashMap<String, ClearanceEntity>()
    private val cookieManager: CookieManager by lazy { CookieManager.getInstance() }

    private val _lastCaptureEvent = MutableStateFlow<String?>(null)
    val lastCaptureEvent: StateFlow<String?> = _lastCaptureEvent.asStateFlow()

    init {
        cookieManager.setAcceptCookie(true)
        scope.launch {
            repository.allClearances.collect { list ->
                vaultCache.clear()
                list.forEach { entity ->
                    vaultCache[entity.domain] = entity
                }
            }
        }
    }

    fun getCachedClearance(urlOrHost: String?): ClearanceEntity? {
        val host = DomainUtils.extractHost(urlOrHost)
        val root = DomainUtils.extractRootDomain(host)
        if (host.isBlank() && root.isBlank()) return null
        return vaultCache[host] ?: vaultCache[root]
    }

    fun getProtectedVaultDomains(): Set<String> = vaultCache.keys.toSet()

    /**
     * Prepares WebView before loading a URL:
     * 1. Locks the exact User-Agent if the domain already has a verified clearance.
     * 2. Re-injects saved `cf_clearance` / `__cf_bm` cookies into CookieManager if absent
     *    so the website never re-plays Cloudflare verification.
     * In Incognito mode, skips all Vault lookups, writes, and third-party cookies.
     */
    fun prepareForUrl(
        webView: WebView,
        url: String,
        settings: BrowserSettings,
        tabDesktopMode: Boolean,
        isIncognito: Boolean = false
    ): DomainClearanceStatus {
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, !isIncognito)

        val host = DomainUtils.extractHost(url)
        val rootDomain = DomainUtils.extractRootDomain(host)
        if (host.isBlank()) return DomainClearanceStatus.NONE

        if (isIncognito) {
            val ua = fingerprintManager.resolveUserAgent(settings.stripWebViewToken, tabDesktopMode)
            if (webView.settings.userAgentString != ua) {
                webView.settings.userAgentString = ua
            }
            return DomainClearanceStatus.NONE
        }

        val saved = vaultCache[host] ?: vaultCache[rootDomain]
        if (saved != null) {
            val targetUa = if (settings.perDomainUaLock && saved.isDesktopMode == tabDesktopMode) {
                saved.lockedUserAgent
            } else if (settings.perDomainUaLock && !tabDesktopMode) {
                saved.lockedUserAgent
            } else {
                fingerprintManager.resolveUserAgent(settings.stripWebViewToken, tabDesktopMode)
            }
            if (webView.settings.userAgentString != targetUa) {
                webView.settings.userAgentString = targetUa
            }

            if (settings.autoRehydrateBeforeLoad) {
                val existingCookies = parseCookies(cookieManager.getCookie(url))
                val currentClearance = existingCookies["cf_clearance"]
                if (currentClearance != saved.cfClearanceToken) {
                    injectCookiesForDomain(url, saved)
                    scope.launch { repository.markReused(saved.domain) }
                    return DomainClearanceStatus.RESTORED_FROM_VAULT
                }
            }
            scope.launch { repository.markReused(saved.domain) }
            return DomainClearanceStatus.VERIFIED_1X
        } else {
            val ua = fingerprintManager.resolveUserAgent(settings.stripWebViewToken, tabDesktopMode)
            if (webView.settings.userAgentString != ua) {
                webView.settings.userAgentString = ua
            }
            return DomainClearanceStatus.NONE
        }
    }

    /**
     * Inspects cookies and DOM status after a page finishes or transitions.
     * Captures `cf_clearance` as soon as the 1-time verification completes and
     * flushes CookieManager immediately to disk.
     */
    fun inspectAndCaptureClearance(
        webView: WebView,
        url: String,
        isChallengeDomDetected: Boolean,
        settings: BrowserSettings,
        isDesktopMode: Boolean,
        isIncognito: Boolean = false,
        hasCloudflareNetworkSignal: Boolean = false
    ): ClearanceCaptureResult {
        val host = DomainUtils.extractHost(url)
        val rootDomain = DomainUtils.extractRootDomain(host)
        if (host.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return ClearanceCaptureResult(DomainClearanceStatus.NONE, "", false)
        }
        if (isIncognito) {
            return ClearanceCaptureResult(
                status = if (isChallengeDomDetected) DomainClearanceStatus.VERIFYING_1X else DomainClearanceStatus.NONE,
                domain = rootDomain.ifBlank { host },
                newlyCaptured = false
            )
        }

        val urlCookies = parseCookies(cookieManager.getCookie(url))
        val rootCookies = parseCookies(cookieManager.getCookie("https://$rootDomain"))
        val mergedCookies = rootCookies + urlCookies

        val cfClearance = mergedCookies["cf_clearance"]
        val cfBm = mergedCookies["__cf_bm"]
        val extraCf = mergedCookies
            .filterKeys { key ->
                (key.startsWith("cf_") || key.startsWith("__cf")) &&
                    key != "cf_clearance" && key != "__cf_bm"
            }
            .entries
            .joinToString("; ") { "${it.key}=${it.value}" }

        if (!cfClearance.isNullOrBlank() && !isChallengeDomDetected) {
            if (settings.autoFlushOnChallengePass) {
                cookieManager.flush()
            }
            val existing = vaultCache[rootDomain] ?: vaultCache[host]
            val ua = webView.settings.userAgentString
                ?: fingerprintManager.resolveUserAgent(settings.stripWebViewToken, isDesktopMode)
            val now = System.currentTimeMillis()

            val isNewOrUpdated = existing == null || existing.cfClearanceToken != cfClearance
            val entity = ClearanceEntity(
                domain = rootDomain.ifBlank { host },
                originUrl = DomainUtils.extractOrigin(url).ifBlank { "https://$host" },
                cfClearanceToken = cfClearance,
                cfBmToken = cfBm ?: existing?.cfBmToken,
                extraCfCookies = extraCf.ifBlank { existing?.extraCfCookies.orEmpty() },
                lockedUserAgent = ua,
                isDesktopMode = isDesktopMode,
                verifiedAt = if (isNewOrUpdated) now else existing.verifiedAt,
                lastUsedAt = now,
                reuseCount = existing?.reuseCount ?: 0,
                challengeCount = existing?.challengeCount ?: 1
            )

            vaultCache[entity.domain] = entity
            scope.launch {
                repository.upsert(entity)
            }

            if (isNewOrUpdated) {
                _lastCaptureEvent.value = entity.domain
            }

            return ClearanceCaptureResult(
                status = DomainClearanceStatus.VERIFIED_1X,
                domain = entity.domain,
                newlyCaptured = isNewOrUpdated,
                entity = entity
            )
        }

        if (isChallengeDomDetected) {
            return ClearanceCaptureResult(
                status = DomainClearanceStatus.VERIFYING_1X,
                domain = rootDomain.ifBlank { host },
                newlyCaptured = false
            )
        }

        val hasAnyCloudflarePresence = hasCloudflareNetworkSignal ||
            isChallengeDomDetected ||
            !cfClearance.isNullOrBlank() ||
            !cfBm.isNullOrBlank()

        if (!hasAnyCloudflarePresence) {
            return ClearanceCaptureResult(
                status = DomainClearanceStatus.NONE,
                domain = rootDomain.ifBlank { host },
                newlyCaptured = false
            )
        }

        val existing = vaultCache[host] ?: vaultCache[rootDomain]
        return if (existing != null) {
            ClearanceCaptureResult(
                status = DomainClearanceStatus.VERIFIED_1X,
                domain = existing.domain,
                newlyCaptured = false,
                entity = existing
            )
        } else {
            ClearanceCaptureResult(
                status = DomainClearanceStatus.NONE,
                domain = rootDomain.ifBlank { host },
                newlyCaptured = false
            )
        }
    }

    fun consumeCaptureEvent() {
        _lastCaptureEvent.value = null
    }

    fun flushCookies() {
        runCatching { cookieManager.flush() }
    }

    suspend fun revokeDomainClearance(domain: String) {
        vaultCache.remove(domain)
        repository.deleteByDomain(domain)
        val targets = listOf("https://$domain", "https://www.$domain")
        targets.forEach { target ->
            cookieManager.setCookie(target, "cf_clearance=; Max-Age=0; Path=/")
            cookieManager.setCookie(target, "__cf_bm=; Max-Age=0; Path=/")
        }
        cookieManager.flush()
    }

    suspend fun clearAllClearances() {
        val domains = vaultCache.keys.toList()
        vaultCache.clear()
        repository.clearAll()
        domains.forEach { domain ->
            cookieManager.setCookie("https://$domain", "cf_clearance=; Max-Age=0; Path=/")
            cookieManager.setCookie("https://$domain", "__cf_bm=; Max-Age=0; Path=/")
        }
        cookieManager.flush()
    }

    private fun injectCookiesForDomain(url: String, entity: ClearanceEntity) {
        val origin = DomainUtils.extractOrigin(url).ifBlank { entity.originUrl }
        val domainTargets = listOf(origin, "https://${entity.domain}")
        domainTargets.distinct().forEach { target ->
            cookieManager.setCookie(
                target,
                "cf_clearance=${entity.cfClearanceToken}; Domain=.${entity.domain}; Path=/; Secure; HttpOnly; SameSite=None"
            )
            cookieManager.setCookie(
                target,
                "cf_clearance=${entity.cfClearanceToken}; Path=/; Secure"
            )
            if (!entity.cfBmToken.isNullOrBlank()) {
                cookieManager.setCookie(
                    target,
                    "__cf_bm=${entity.cfBmToken}; Domain=.${entity.domain}; Path=/; Secure; HttpOnly; SameSite=None"
                )
            }
            if (entity.extraCfCookies.isNotBlank()) {
                entity.extraCfCookies.split(";").map { it.trim() }.filter { it.contains("=") }.forEach { pair ->
                    cookieManager.setCookie(target, "$pair; Path=/; Secure")
                }
            }
        }
        cookieManager.flush()
    }

    private fun parseCookies(rawCookieHeader: String?): Map<String, String> {
        if (rawCookieHeader.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        rawCookieHeader.split(";").forEach { segment ->
            val idx = segment.indexOf('=')
            if (idx > 0) {
                val name = segment.substring(0, idx).trim()
                val value = segment.substring(idx + 1).trim()
                if (name.isNotBlank() && value.isNotBlank()) {
                    result[name] = value
                }
            }
        }
        return result
    }

    companion object {
        fun isCloudflareChallengeRequest(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val lower = url.lowercase()
            val host = DomainUtils.extractHost(lower)
            if (host == "challenges.cloudflare.com" || host.endsWith(".challenges.cloudflare.com")) {
                return true
            }
            return lower.contains("/cdn-cgi/challenge-platform") ||
                lower.contains("/cdn-cgi/bm/cv") ||
                lower.contains("/cdn-cgi/l/chk_captcha")
        }

        const val CF_DETECTION_JS = """
            (function() {
                try {
                    var title = (document.title || '').toLowerCase();
                    var href = (window.location && window.location.href) ? window.location.href.toLowerCase() : '';
                    var hasCfDom = !!(document.querySelector(
                        'input[name="cf-turnstile-response"], .cf-turnstile, #challenge-form, #cf-challenge-running, #challenge-stage, #cf-wrapper, .cf-browser-verification, iframe[src*="challenges.cloudflare.com"], script[src*="challenges.cloudflare.com"], script[src*="/cdn-cgi/challenge-platform"]'
                    ));
                    var hasCfObj = typeof window._cf_chl_opt !== 'undefined' || typeof window.turnstile !== 'undefined';
                    var isCfPath = href.indexOf('/cdn-cgi/challenge-platform') !== -1;
                    var bodyText = (document.body && document.body.innerText) ? document.body.innerText.toLowerCase() : '';
                    var hasCfBranding = bodyText.indexOf('cloudflare') !== -1 || bodyText.indexOf('ray id:') !== -1;
                    var hasCfTitle = hasCfBranding && (
                        title.indexOf('just a moment') !== -1 ||
                        title.indexOf('attention required') !== -1 ||
                        title.indexOf('one more step') !== -1
                    );
                    return (hasCfDom || hasCfTitle || hasCfObj || isCfPath) ? 'CHALLENGE' : 'CLEAR';
                } catch (e) {
                    return 'CLEAR';
                }
            })();
        """
    }
}
