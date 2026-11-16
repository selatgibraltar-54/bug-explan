package com.browser.client.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.browser.client.AppContainer
import com.browser.client.data.local.BookmarkEntity
import com.browser.client.data.local.ClearanceEntity
import com.browser.client.data.local.HistoryEntity
import com.browser.client.data.local.SearchHistoryEntity
import com.browser.client.data.preferences.BrowserSettings
import com.browser.client.data.preferences.JsSecurityMode
import com.browser.client.data.preferences.SearchEngine
import com.browser.client.data.preferences.ThemeMode
import com.browser.client.engine.ClearanceCaptureResult
import com.browser.client.engine.DefaultBrowserHelper
import com.browser.client.engine.DomainClearanceStatus
import com.browser.client.engine.DomainUtils
import com.browser.client.engine.DownloadProgressSnapshot
import com.browser.client.engine.DownloadTaskStatus
import com.browser.client.engine.GooglePasswordManagerLaunchResult
import com.browser.client.engine.NetworkStatus
import com.browser.client.engine.PasskeyCreateResult
import com.browser.client.engine.PasskeyGetResult
import com.browser.client.engine.PasskeyManagerBridge
import com.browser.client.engine.ReaderArticle
import com.browser.client.engine.WebTabCallbacks
import com.browser.client.engine.toSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference
import java.util.UUID

enum class BrowserOverlay {
    NONE,
    TAB_SWITCHER,
    CLEARANCE_VAULT,
    BOOKMARKS_HISTORY,
    DOWNLOADS,
    CUSTOMIZE_STARTER,
    SETTINGS,
    SITE_INSPECTOR
}

data class BrowserTabState(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "",
    val title: String = "Start Page",
    val isHome: Boolean = true,
    val isIncognito: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isReaderMode: Boolean = false,
    val isExtractingReader: Boolean = false,
    val readerArticle: ReaderArticle? = null,
    val clearanceStatus: DomainClearanceStatus = DomainClearanceStatus.NONE,
    val activeDomain: String = "",
    val pendingUrlToLoad: String? = null,
    val offlineErrorUrl: String? = null
)

data class PasskeyPromptState(
    val tabId: String,
    val callId: String,
    val mode: String,
    val pageUrl: String,
    val originHost: String,
    val reason: String,
    val isIncognito: Boolean,
    val openedPasswordManagerAutomatically: Boolean = false
)

data class BrowserUiState(
    val tabs: List<BrowserTabState> = listOf(BrowserTabState()),
    val activeTabId: String = "",
    val addressInput: String = "",
    val isEditingAddress: Boolean = false,
    val activeOverlay: BrowserOverlay = BrowserOverlay.NONE,
    val bookmarksHistoryInitialTab: Int = 0,
    val findInPageVisible: Boolean = false,
    val findInPageQuery: String = "",
    val readerFontScale: Float = 1.0f,
    val readerSepiaPaper: Boolean = false,
    val isDefaultBrowser: Boolean = false,
    val passkeyPrompt: PasskeyPromptState? = null,
    val snackbarMessage: String? = null
) {
    val activeTab: BrowserTabState
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
}

class BrowserViewModel(
    application: Application,
    private val container: AppContainer
) : AndroidViewModel(application), WebTabCallbacks {

    private val initialTab = BrowserTabState()

    private val _uiState = MutableStateFlow(
        BrowserUiState(
            tabs = listOf(initialTab),
            activeTabId = initialTab.id,
            isDefaultBrowser = DefaultBrowserHelper.isDefaultBrowser(application)
        )
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val settings: StateFlow<BrowserSettings> = container.preferencesRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BrowserSettings())

    val networkStatus: StateFlow<NetworkStatus> = container.networkMonitor.networkStatus

    val clearances: StateFlow<List<ClearanceEntity>> = container.clearanceRepository.allClearances
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinnedBookmarks: StateFlow<List<BookmarkEntity>> = container.browserDataRepository.pinnedBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBookmarks: StateFlow<List<BookmarkEntity>> = container.browserDataRepository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentHistory: StateFlow<List<HistoryEntity>> = container.browserDataRepository.recentHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = container.browserDataRepository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadItems: StateFlow<List<DownloadProgressSnapshot>> = combine(
        container.browserDataRepository.allDownloads,
        container.downloadEngine.activeDownloads,
        container.downloadEngine.incognitoDownloads
    ) { entities, activeMap, incognitoList ->
        val normalItems = entities.map { entity ->
            activeMap[entity.id] ?: entity.toSnapshot()
        }
        incognitoList + normalItems
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloadCount: StateFlow<Int> = downloadItems
        .map { items ->
            items.count {
                it.status == DownloadTaskStatus.QUEUED || it.status == DownloadTaskStatus.DOWNLOADING
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    var pendingFileChooserCallback: ValueCallback<Array<Uri>>? = null
    var onRequestFileChooser: ((WebChromeClient.FileChooserParams?) -> Unit)? = null
    private var activityContextRef: WeakReference<Context>? = null

    init {
        viewModelScope.launch {
            container.browserDataRepository.ensureDefaultBookmarksSeeded()
        }
        viewModelScope.launch {
            container.clearanceEngine.lastCaptureEvent.collect { domain ->
                if (!domain.isNullOrBlank()) {
                    showMessage("Cloudflare verified 1x & saved for $domain")
                    container.clearanceEngine.consumeCaptureEvent()
                }
            }
        }
    }

    fun attachActivityContext(context: Context) {
        activityContextRef = WeakReference(context)
    }

    fun refreshDefaultBrowserStatus() {
        val held = DefaultBrowserHelper.isDefaultBrowser(getApplication())
        _uiState.update { it.copy(isDefaultBrowser = held) }
    }

    fun onAddressInputChanged(value: String) {
        _uiState.update { it.copy(addressInput = value) }
    }

    fun onAddressFocusChanged(focused: Boolean) {
        _uiState.update { state ->
            if (state.isEditingAddress == focused) return@update state
            val currentUrl = if (state.activeTab.isHome) "" else state.activeTab.url
            state.copy(
                isEditingAddress = focused,
                addressInput = currentUrl
            )
        }
    }

    fun navigateFromOmnibox(rawInput: String) {
        val currentEngine = settings.value.searchEngine
        val resolved = DomainUtils.resolveInputToUrl(rawInput, currentEngine)
        if (resolved.isBlank()) return
        val searchQuery = DomainUtils.extractSearchQueryIfSearch(rawInput)
        val isIncognito = _uiState.value.activeTab.isIncognito
        if (!isIncognito && !searchQuery.isNullOrBlank()) {
            viewModelScope.launch {
                container.browserDataRepository.recordSearchQuery(
                    query = searchQuery,
                    searchEngine = currentEngine.label,
                    searchUrl = resolved
                )
            }
        }
        loadUrlInActiveTab(resolved)
        _uiState.update { it.copy(isEditingAddress = false) }
    }

    fun loadUrlInActiveTab(url: String) {
        val active = _uiState.value.activeTab
        val domain = DomainUtils.extractRootDomain(url)

        // Check live Data/Wi-Fi connectivity before attempting load
        if (!container.networkMonitor.isOnlineNow()) {
            updateTab(active.id) { tab ->
                tab.copy(
                    url = url,
                    title = domain.ifBlank { url },
                    isHome = false,
                    isLoading = false,
                    progress = 0,
                    activeDomain = domain,
                    pendingUrlToLoad = null,
                    offlineErrorUrl = url
                )
            }
            _uiState.update {
                it.copy(
                    addressInput = url,
                    isEditingAddress = false,
                    activeOverlay = BrowserOverlay.NONE
                )
            }
            return
        }

        val cached = if (active.isIncognito) null else container.clearanceEngine.getCachedClearance(url)
        val initialStatus = if (cached != null) {
            DomainClearanceStatus.VERIFIED_1X
        } else {
            DomainClearanceStatus.NONE
        }

        updateTab(active.id) { tab ->
            tab.copy(
                url = url,
                isHome = false,
                isLoading = true,
                progress = 10,
                isReaderMode = false,
                isExtractingReader = false,
                readerArticle = null,
                activeDomain = domain,
                clearanceStatus = initialStatus,
                pendingUrlToLoad = url,
                offlineErrorUrl = null
            )
        }
        _uiState.update {
            it.copy(
                addressInput = url,
                isEditingAddress = false,
                activeOverlay = BrowserOverlay.NONE
            )
        }

        val existingWv = container.webViewPool.getExistingWebView(active.id)
        if (existingWv != null) {
            val prepStatus = container.webViewPool.loadUrl(
                tabId = active.id,
                url = url,
                settings = settings.value,
                isDesktopMode = active.isDesktopMode,
                isIncognito = active.isIncognito
            )
            updateTab(active.id) { tab ->
                tab.copy(
                    pendingUrlToLoad = null,
                    clearanceStatus = if (prepStatus != DomainClearanceStatus.NONE) prepStatus else tab.clearanceStatus
                )
            }
        }
    }

    fun retryOfflineTab() {
        val active = _uiState.value.activeTab
        val target = active.offlineErrorUrl ?: active.url
        if (target.isBlank()) return
        if (!container.networkMonitor.isOnlineNow()) {
            showMessage("No internet. Please enable Wi-Fi or Mobile Data.")
            return
        }
        loadUrlInActiveTab(target)
    }

    fun consumePendingLoad(tabId: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        val pending = tab.pendingUrlToLoad ?: return
        if (container.webViewPool.getExistingWebView(tabId) == null) {
            return
        }
        if (!container.networkMonitor.isOnlineNow()) {
            updateTab(tabId) {
                it.copy(pendingUrlToLoad = null, isLoading = false, offlineErrorUrl = pending)
            }
            return
        }
        updateTab(tabId) { it.copy(pendingUrlToLoad = null, offlineErrorUrl = null) }
        val prepStatus = container.webViewPool.loadUrl(
            tabId = tabId,
            url = pending,
            settings = settings.value,
            isDesktopMode = tab.isDesktopMode,
            isIncognito = tab.isIncognito
        )
        if (prepStatus != DomainClearanceStatus.NONE) {
            updateTab(tabId) { it.copy(clearanceStatus = prepStatus) }
        }
    }

    fun openExternalUrl(url: String) {
        if (url.isBlank()) return
        val current = _uiState.value.activeTab
        if (!current.isIncognito &&
            ((current.isHome && current.url.isBlank()) || _uiState.value.tabs.size >= MAX_TABS)
        ) {
            if (_uiState.value.tabs.size >= MAX_TABS) {
                showMessage("Tab limit (30) reached — opened in current tab")
            }
            loadUrlInActiveTab(url)
        } else if (_uiState.value.tabs.size >= MAX_TABS) {
            val normalTab = _uiState.value.tabs.firstOrNull { !it.isIncognito }
            if (normalTab != null) {
                selectTab(normalTab.id)
                loadUrlInActiveTab(url)
            } else {
                loadUrlInActiveTab(url)
            }
            showMessage("Tab limit (30) reached — opened in existing tab")
        } else {
            openNewTab(url, isIncognito = false)
        }
    }

    fun openImageSearchInNewTab() {
        val imageUrl = settings.value.searchEngine.imageSearchUrl
        openNewTab(imageUrl, isIncognito = _uiState.value.activeTab.isIncognito)
    }

    fun openIncognitoTab(url: String? = null) {
        openNewTab(url = url, isIncognito = true)
    }

    fun openNewTab(url: String? = null, isIncognito: Boolean = false) {
        if (_uiState.value.tabs.size >= MAX_TABS) {
            showMessage("Maximum 30 tabs reached. Close a tab first.")
            return
        }
        val isOnline = url.isNullOrBlank() || container.networkMonitor.isOnlineNow()
        val defaultTitle = if (isIncognito) "Private Tab" else "Start Page"
        val newTab = BrowserTabState(
            url = url.orEmpty(),
            title = if (url.isNullOrBlank()) defaultTitle else DomainUtils.extractHost(url),
            isHome = url.isNullOrBlank(),
            isIncognito = isIncognito,
            isDesktopMode = settings.value.defaultDesktopMode,
            activeDomain = if (url.isNullOrBlank()) "" else DomainUtils.extractRootDomain(url),
            pendingUrlToLoad = if (!url.isNullOrBlank() && isOnline) url else null,
            offlineErrorUrl = if (!url.isNullOrBlank() && !isOnline) url else null
        )
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
                addressInput = url.orEmpty(),
                isEditingAddress = false,
                activeOverlay = BrowserOverlay.NONE,
                findInPageVisible = false,
                findInPageQuery = ""
            )
        }
    }

    fun closeAllIncognitoTabs() {
        val incognitoTabs = _uiState.value.tabs.filter { it.isIncognito }
        incognitoTabs.forEach { tab ->
            container.webViewPool.removeWebView(tab.id)
        }
        container.webViewPool.shredIncognitoArtifacts()
        container.downloadEngine.shredIncognitoDownloads()
        _uiState.update { state ->
            val remainingNormal = state.tabs.filterNot { it.isIncognito }
            if (remainingNormal.isEmpty()) {
                val fresh = BrowserTabState(isDesktopMode = settings.value.defaultDesktopMode)
                state.copy(
                    tabs = listOf(fresh),
                    activeTabId = fresh.id,
                    addressInput = "",
                    isEditingAddress = false,
                    activeOverlay = BrowserOverlay.NONE,
                    findInPageVisible = false,
                    findInPageQuery = ""
                )
            } else {
                val nextActive = remainingNormal.firstOrNull { it.id == state.activeTabId }
                    ?: remainingNormal.last()
                state.copy(
                    tabs = remainingNormal,
                    activeTabId = nextActive.id,
                    addressInput = if (nextActive.isHome) "" else nextActive.url,
                    isEditingAddress = false,
                    activeOverlay = BrowserOverlay.NONE,
                    findInPageVisible = false,
                    findInPageQuery = ""
                )
            }
        }
        showMessage("Incognito session shredded & wiped")
    }

    fun selectTab(tabId: String) {
        val target = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        _uiState.update {
            it.copy(
                activeTabId = tabId,
                addressInput = if (target.isHome) "" else target.url,
                isEditingAddress = false,
                activeOverlay = BrowserOverlay.NONE,
                findInPageVisible = false
            )
        }
    }

    fun closeTab(tabId: String) {
        val closingTab = _uiState.value.tabs.firstOrNull { it.id == tabId }
        container.webViewPool.removeWebView(tabId)
        val remainingAfter = _uiState.value.tabs.filterNot { it.id == tabId }
        if (closingTab?.isIncognito == true && remainingAfter.none { it.isIncognito }) {
            container.downloadEngine.shredIncognitoDownloads()
        }
        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.id == tabId }
            if (remaining.isEmpty()) {
                val fresh = BrowserTabState(isDesktopMode = settings.value.defaultDesktopMode)
                state.copy(
                    tabs = listOf(fresh),
                    activeTabId = fresh.id,
                    addressInput = "",
                    isEditingAddress = false
                )
            } else {
                val nextActive = if (state.activeTabId == tabId) {
                    remaining.last()
                } else {
                    remaining.firstOrNull { it.id == state.activeTabId } ?: remaining.last()
                }
                state.copy(
                    tabs = remaining,
                    activeTabId = nextActive.id,
                    addressInput = if (nextActive.isHome) "" else nextActive.url
                )
            }
        }
    }

    fun goBack(): Boolean {
        val tab = _uiState.value.activeTab
        if (tab.isHome) return false
        if (tab.isReaderMode) {
            updateTab(tab.id) { it.copy(isReaderMode = false) }
            return true
        }
        if (tab.offlineErrorUrl != null) {
            goHome()
            return true
        }
        val wv = container.webViewPool.getExistingWebView(tab.id)
        return if (wv != null && wv.canGoBack()) {
            wv.goBack()
            true
        } else {
            goHome()
            true
        }
    }

    fun goForward() {
        val tab = _uiState.value.activeTab
        if (tab.isHome) return
        container.webViewPool.getExistingWebView(tab.id)?.let { wv ->
            if (wv.canGoForward()) wv.goForward()
        }
    }

    fun reloadOrStop() {
        val tab = _uiState.value.activeTab
        if (tab.isHome) return
        if (tab.offlineErrorUrl != null) {
            retryOfflineTab()
            return
        }
        if (!container.networkMonitor.isOnlineNow()) {
            updateTab(tab.id) { it.copy(isLoading = false, offlineErrorUrl = tab.url) }
            return
        }
        val wv = container.webViewPool.getExistingWebView(tab.id) ?: return
        if (tab.isLoading) {
            wv.stopLoading()
            updateTab(tab.id) { it.copy(isLoading = false, progress = 100) }
        } else {
            updateTab(tab.id) { it.copy(isReaderMode = false, readerArticle = null) }
            container.clearanceEngine.prepareForUrl(
                webView = wv,
                url = tab.url,
                settings = settings.value,
                tabDesktopMode = tab.isDesktopMode,
                isIncognito = tab.isIncognito
            )
            wv.reload()
        }
    }

    fun goHome() {
        val tab = _uiState.value.activeTab
        updateTab(tab.id) {
            it.copy(
                isHome = true,
                isLoading = false,
                progress = 0,
                isReaderMode = false,
                isExtractingReader = false,
                readerArticle = null,
                title = if (it.isIncognito) "Private Tab" else "Start Page",
                clearanceStatus = DomainClearanceStatus.NONE,
                offlineErrorUrl = null
            )
        }
        _uiState.update {
            it.copy(
                addressInput = "",
                isEditingAddress = false,
                findInPageVisible = false,
                findInPageQuery = ""
            )
        }
    }

    fun toggleReaderMode() {
        val tab = _uiState.value.activeTab
        if (tab.isHome || tab.offlineErrorUrl != null) return
        if (tab.isReaderMode) {
            updateTab(tab.id) { it.copy(isReaderMode = false, isExtractingReader = false) }
            return
        }
        updateTab(tab.id) { it.copy(isExtractingReader = true) }
        container.webViewPool.extractReadableArticle(tab.id, tab.url) { article ->
            updateTab(tab.id) {
                it.copy(
                    isReaderMode = true,
                    isExtractingReader = false,
                    readerArticle = article
                )
            }
        }
    }

    fun increaseReaderFont() {
        _uiState.update { state ->
            state.copy(readerFontScale = (state.readerFontScale + 0.12f).coerceAtMost(1.6f))
        }
    }

    fun decreaseReaderFont() {
        _uiState.update { state ->
            state.copy(readerFontScale = (state.readerFontScale - 0.12f).coerceAtLeast(0.8f))
        }
    }

    fun toggleReaderSepia() {
        _uiState.update { state -> state.copy(readerSepiaPaper = !state.readerSepiaPaper) }
    }

    fun toggleDesktopMode() {
        val tab = _uiState.value.activeTab
        val nextDesktop = !tab.isDesktopMode
        updateTab(tab.id) { it.copy(isDesktopMode = nextDesktop) }
        container.webViewPool.setDesktopMode(
            tabId = tab.id,
            enabled = nextDesktop,
            settings = settings.value
        )
        val wv = container.webViewPool.getExistingWebView(tab.id)
        if (!tab.isHome && wv != null && tab.url.isNotBlank()) {
            wv.reload()
        }
        showMessage(if (nextDesktop) "Desktop site enabled" else "Mobile site enabled")
    }

    fun toggleCurrentBookmark() {
        val tab = _uiState.value.activeTab
        if (tab.isHome || tab.url.isBlank()) return
        viewModelScope.launch {
            val exists = allBookmarks.value.any { it.url == tab.url }
            if (exists) {
                container.browserDataRepository.removeBookmarkByUrl(tab.url)
                showMessage("Removed bookmark")
            } else {
                container.browserDataRepository.addBookmark(tab.title, tab.url, isPinned = true)
                showMessage("Bookmarked ${DomainUtils.extractHost(tab.url)}")
            }
        }
    }

    fun addCustomBookmark(title: String, url: String, isPinned: Boolean = true) {
        val resolved = DomainUtils.resolveInputToUrl(url, settings.value.searchEngine)
        if (resolved.isBlank()) return
        viewModelScope.launch {
            container.browserDataRepository.addBookmark(title, resolved, isPinned = isPinned)
            showMessage(if (isPinned) "Added speed dial shortcut" else "Bookmark saved")
        }
    }

    fun updateBookmark(id: Long, title: String, url: String, isPinned: Boolean) {
        val resolved = DomainUtils.resolveInputToUrl(url, settings.value.searchEngine)
        if (resolved.isBlank()) return
        viewModelScope.launch {
            container.browserDataRepository.updateBookmark(id, title, resolved, isPinned)
            showMessage("Bookmark updated")
        }
    }

    fun toggleBookmarkPinned(id: Long) {
        viewModelScope.launch {
            container.browserDataRepository.toggleBookmarkPinned(id)
        }
    }

    fun removeBookmark(id: Long) {
        viewModelScope.launch {
            container.browserDataRepository.removeBookmarkById(id)
        }
    }

    fun revokeClearance(domain: String) {
        viewModelScope.launch {
            container.clearanceEngine.revokeDomainClearance(domain)
            val active = _uiState.value.activeTab
            if (active.activeDomain == domain) {
                updateTab(active.id) { it.copy(clearanceStatus = DomainClearanceStatus.NONE) }
            }
            showMessage("Cleared 1x verification for $domain")
        }
    }

    fun clearAllClearances() {
        viewModelScope.launch {
            container.clearanceEngine.clearAllClearances()
            _uiState.update { state ->
                state.copy(
                    tabs = state.tabs.map { it.copy(clearanceStatus = DomainClearanceStatus.NONE) }
                )
            }
            showMessage("All saved Cloudflare clearances cleared")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.browserDataRepository.clearHistory()
            showMessage("Browsing history cleared")
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            container.browserDataRepository.deleteHistoryItem(id)
        }
    }

    fun runSearchFromHistory(entry: SearchHistoryEntity) {
        val active = _uiState.value.activeTab
        val currentEngine = settings.value.searchEngine
        val targetUrl = DomainUtils.resolveInputToUrl(entry.query, currentEngine)
        if (!active.isIncognito) {
            viewModelScope.launch {
                container.browserDataRepository.recordSearchQuery(
                    query = entry.query,
                    searchEngine = currentEngine.label,
                    searchUrl = targetUrl
                )
            }
        }
        loadUrlInActiveTab(targetUrl)
    }

    fun deleteSearchHistoryItem(id: Long) {
        viewModelScope.launch {
            container.browserDataRepository.deleteSearchHistoryItem(id)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            container.browserDataRepository.clearSearchHistory()
            showMessage("Search history cleared")
        }
    }

    fun flushCookiesNow() {
        container.clearanceEngine.flushCookies()
        showMessage("Cookies synced to persistent disk storage")
    }

    fun importStarterWallpaper(uri: Uri) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val destFile = File(app.filesDir, "starter_wallpaper_${System.currentTimeMillis()}.jpg")
                    // Clean old wallpaper files
                    app.filesDir.listFiles()?.filter {
                        it.name.startsWith("starter_wallpaper_") && it.name != destFile.name
                    }?.forEach { it.delete() }

                    app.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else null
                }.getOrNull()
            }
            if (!savedPath.isNullOrBlank()) {
                container.preferencesRepository.setStarterWallpaperPath(savedPath)
                showMessage("Starter page image imported")
            } else {
                showMessage("Could not import selected image")
            }
        }
    }

    fun removeStarterWallpaper() {
        viewModelScope.launch {
            val currentPath = settings.value.starterWallpaperPath
            withContext(Dispatchers.IO) {
                if (currentPath.isNotBlank()) {
                    runCatching { File(currentPath).delete() }
                }
            }
            container.preferencesRepository.setStarterWallpaperPath("")
            showMessage("Starter page wallpaper removed")
        }
    }

    fun openOverlay(overlay: BrowserOverlay) {
        _uiState.update {
            it.copy(
                activeOverlay = overlay,
                bookmarksHistoryInitialTab = if (overlay == BrowserOverlay.BOOKMARKS_HISTORY) 0 else it.bookmarksHistoryInitialTab,
                isEditingAddress = false
            )
        }
    }

    fun openBookmarksHistory(initialTab: Int = 0) {
        _uiState.update {
            it.copy(
                activeOverlay = BrowserOverlay.BOOKMARKS_HISTORY,
                bookmarksHistoryInitialTab = initialTab.coerceIn(0, 2),
                isEditingAddress = false
            )
        }
    }

    fun closeOverlay() {
        _uiState.update { it.copy(activeOverlay = BrowserOverlay.NONE) }
    }

    fun toggleFindInPage() {
        _uiState.update { state ->
            val next = !state.findInPageVisible
            if (!next) {
                container.webViewPool.getExistingWebView(state.activeTabId)?.clearMatches()
            }
            state.copy(
                findInPageVisible = next,
                findInPageQuery = if (next) state.findInPageQuery else ""
            )
        }
    }

    fun updateFindQuery(query: String) {
        _uiState.update { it.copy(findInPageQuery = query) }
        val wv = container.webViewPool.getExistingWebView(_uiState.value.activeTabId) ?: return
        if (query.isBlank()) {
            wv.clearMatches()
        } else {
            wv.findAllAsync(query)
        }
    }

    fun findNext(forward: Boolean) {
        container.webViewPool.getExistingWebView(_uiState.value.activeTabId)?.findNext(forward)
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { container.preferencesRepository.setSearchEngine(engine) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.preferencesRepository.setThemeMode(mode) }
    }

    fun setAutoFlush(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setAutoFlush(enabled) }
    }

    fun setAutoRehydrate(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setAutoRehydrate(enabled) }
    }

    fun setStripWebViewToken(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setStripWebViewToken(enabled) }
    }

    fun setPerDomainUaLock(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setPerDomainUaLock(enabled) }
    }

    fun setAdBlockerEnabled(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setAdBlockerEnabled(enabled) }
    }

    fun setDataSaverEnabled(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setDataSaverEnabled(enabled) }
    }

    fun setJsSecurityMode(mode: JsSecurityMode) {
        viewModelScope.launch {
            container.preferencesRepository.setJsSecurityMode(mode)
            val active = _uiState.value.activeTab
            container.webViewPool.getExistingWebView(active.id)?.let { wv ->
                container.webViewPool.applyRuntimeSettings(
                    webView = wv,
                    settings = settings.value.copy(jsSecurityMode = mode),
                    isIncognito = active.isIncognito,
                    isDesktopMode = active.isDesktopMode
                )
            }
        }
    }

    fun setDefaultDesktopMode(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setDefaultDesktopMode(enabled) }
    }

    fun setShowImageSearchOnNewTab(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setShowImageSearchOnNewTab(enabled) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun getClearanceEntityForActiveTab(): ClearanceEntity? {
        val active = _uiState.value.activeTab
        return container.clearanceEngine.getCachedClearance(active.url)
    }

    // --- WebTabCallbacks ---

    override fun onPageStarted(tabId: String, url: String) {
        val domain = DomainUtils.extractRootDomain(url)
        updateTab(tabId) { tab ->
            val domainChanged = domain != tab.activeDomain
            tab.copy(
                url = url,
                isHome = false,
                isLoading = true,
                progress = 15,
                isReaderMode = if (tab.url != url) false else tab.isReaderMode,
                readerArticle = if (tab.url != url) null else tab.readerArticle,
                clearanceStatus = if (domainChanged) DomainClearanceStatus.NONE else tab.clearanceStatus,
                activeDomain = domain,
                offlineErrorUrl = null
            )
        }
        if (_uiState.value.activeTabId == tabId && !_uiState.value.isEditingAddress) {
            _uiState.update { it.copy(addressInput = url) }
        }
    }

    override fun onPageFinished(tabId: String, url: String, title: String?) {
        val cleanTitle = title?.takeIf { it.isNotBlank() } ?: DomainUtils.extractHost(url)
        updateTab(tabId) { tab ->
            tab.copy(
                url = url,
                title = cleanTitle,
                isLoading = false,
                progress = 100
            )
        }
        if (_uiState.value.activeTabId == tabId && !_uiState.value.isEditingAddress) {
            _uiState.update { it.copy(addressInput = url) }
        }
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId }
        if (tab != null && !tab.isIncognito && tab.offlineErrorUrl == null) {
            val hasCf = tab.clearanceStatus == DomainClearanceStatus.VERIFIED_1X ||
                tab.clearanceStatus == DomainClearanceStatus.RESTORED_FROM_VAULT
            val detectedSearch = DomainUtils.extractSearchQueryFromUrl(url)
            viewModelScope.launch {
                container.browserDataRepository.recordHistory(cleanTitle, url, hasCf)
                if (detectedSearch != null) {
                    container.browserDataRepository.recordSearchQuery(
                        query = detectedSearch.first,
                        searchEngine = detectedSearch.second,
                        searchUrl = url
                    )
                }
            }
        }
    }

    override fun onProgressChanged(tabId: String, progress: Int) {
        updateTab(tabId) { it.copy(progress = progress, isLoading = progress < 100) }
    }

    override fun onTitleReceived(tabId: String, title: String) {
        updateTab(tabId) { it.copy(title = title) }
    }

    override fun onNavigationStateChanged(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        updateTab(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    override fun onClearanceResult(tabId: String, result: ClearanceCaptureResult) {
        updateTab(tabId) { tab ->
            val resolvedStatus = if (tab.clearanceStatus == DomainClearanceStatus.RESTORED_FROM_VAULT &&
                result.status == DomainClearanceStatus.VERIFIED_1X
            ) {
                DomainClearanceStatus.RESTORED_FROM_VAULT
            } else {
                result.status
            }
            tab.copy(
                clearanceStatus = resolvedStatus,
                activeDomain = result.domain.ifBlank { tab.activeDomain }
            )
        }
    }

    override fun onPrepareStatus(tabId: String, status: DomainClearanceStatus) {
        updateTab(tabId) { it.copy(clearanceStatus = status) }
    }

    override fun onOfflineError(tabId: String, url: String) {
        updateTab(tabId) { tab ->
            tab.copy(
                isLoading = false,
                progress = 0,
                offlineErrorUrl = url
            )
        }
    }

    override fun onOpenNewTab(url: String, isIncognito: Boolean) {
        openNewTab(url, isIncognito = isIncognito)
    }

    override fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Boolean {
        pendingFileChooserCallback?.onReceiveValue(null)
        pendingFileChooserCallback = filePathCallback
        onRequestFileChooser?.invoke(fileChooserParams)
        return onRequestFileChooser != null
    }

    fun pauseDownload(id: Long) {
        container.downloadEngine.pauseDownload(id)
    }

    fun resumeDownload(id: Long) {
        if (!container.networkMonitor.isOnlineNow()) {
            showMessage("No internet connection to resume download")
            return
        }
        container.downloadEngine.resumeDownload(id)
    }

    fun cancelDownload(id: Long) {
        container.downloadEngine.cancelDownload(id)
    }

    fun retryDownload(id: Long) {
        if (!container.networkMonitor.isOnlineNow()) {
            showMessage("No internet connection to retry download")
            return
        }
        container.downloadEngine.retryDownload(id)
    }

    fun deleteDownloadItem(id: Long) {
        container.downloadEngine.deleteDownload(id)
    }

    fun clearDownloadsHistory() {
        container.downloadEngine.clearDownloadsHistory()
        showMessage("Browser download history cleared")
    }

    override fun onDownloadRequested(
        fileName: String,
        url: String,
        userAgent: String,
        mimeType: String,
        contentLength: Long,
        isIncognito: Boolean
    ) {
        if (!container.networkMonitor.isOnlineNow()) {
            showMessage("No internet connection for downloading $fileName")
            return
        }
        container.downloadEngine.startDownload(
            fileName = fileName,
            url = url,
            userAgent = userAgent,
            mimeType = mimeType,
            contentLength = contentLength,
            isIncognito = isIncognito
        )
        showMessage(
            if (isIncognito) "Downloading $fileName (Private session — not saved to history)"
            else "Downloading $fileName"
        )
    }

    override fun onPasskeyRequested(
        tabId: String,
        callId: String,
        mode: String,
        requestJson: String,
        pageUrl: String,
        isIncognito: Boolean
    ) {
        val originHost = DomainUtils.extractHost(pageUrl).ifBlank { "this site" }
        if (isIncognito) {
            _uiState.update {
                it.copy(
                    passkeyPrompt = PasskeyPromptState(
                        tabId = tabId,
                        callId = callId,
                        mode = mode,
                        pageUrl = pageUrl,
                        originHost = originHost,
                        reason = "Hardened Incognito paused automatic Google Password Manager launch so external apps aren't opened silently from a private tab.",
                        isIncognito = true,
                        openedPasswordManagerAutomatically = false
                    )
                )
            }
            return
        }

        if (mode == "uri" || requestJson.isBlank()) {
            val result = openGooglePasswordManager(openWebFallbackInTab = true)
            _uiState.update {
                it.copy(
                    passkeyPrompt = PasskeyPromptState(
                        tabId = tabId,
                        callId = callId,
                        mode = mode,
                        pageUrl = pageUrl,
                        originHost = originHost,
                        reason = if (result == GooglePasswordManagerLaunchResult.OPENED_NATIVE) {
                            "$originHost requested passkey verification. Google Password Manager was opened."
                        } else {
                            "$originHost requested passkey verification. Opened Google Password Manager web portal."
                        },
                        isIncognito = false,
                        openedPasswordManagerAutomatically = true
                    )
                )
            }
            return
        }

        val uiContext = activityContextRef?.get() ?: getApplication<Application>()
        viewModelScope.launch {
            if (mode == "create") {
                when (val createResult = PasskeyManagerBridge.createPasskey(uiContext, requestJson)) {
                    is PasskeyCreateResult.RegistrationJson -> {
                        container.webViewPool.resolvePasskeyJsCall(
                            tabId = tabId,
                            callId = callId,
                            responseJson = createResult.registrationResponseJson
                        )
                        showMessage("Passkey saved for $originHost")
                    }
                    PasskeyCreateResult.Canceled -> {
                        container.webViewPool.rejectPasskeyJsCall(
                            tabId = tabId,
                            callId = callId,
                            reason = "Passkey creation cancelled"
                        )
                    }
                    is PasskeyCreateResult.FallbackRequired -> {
                        container.webViewPool.rejectPasskeyJsCall(
                            tabId = tabId,
                            callId = callId,
                            reason = createResult.reason
                        )
                        launchFallbackGooglePasswordManager(
                            tabId = tabId,
                            callId = callId,
                            mode = mode,
                            pageUrl = pageUrl,
                            originHost = originHost,
                            reason = createResult.reason
                        )
                    }
                }
            } else {
                when (val getResult = PasskeyManagerBridge.getPasskeyOrPassword(uiContext, requestJson)) {
                    is PasskeyGetResult.PublicKeyJson -> {
                        container.webViewPool.resolvePasskeyJsCall(
                            tabId = tabId,
                            callId = callId,
                            responseJson = getResult.authenticationResponseJson
                        )
                        showMessage("Passkey verified for $originHost")
                    }
                    is PasskeyGetResult.Password -> {
                        container.webViewPool.rejectPasskeyJsCall(
                            tabId = tabId,
                            callId = callId,
                            reason = "Password credential selected"
                        )
                        showMessage("Retrieved credential for ${getResult.id} from Google Password Manager")
                    }
                    PasskeyGetResult.Canceled -> {
                        container.webViewPool.rejectPasskeyJsCall(
                            tabId = tabId,
                            callId = callId,
                            reason = "Passkey verification cancelled"
                        )
                    }
                    is PasskeyGetResult.FallbackRequired -> {
                        container.webViewPool.rejectPasskeyJsCall(
                            tabId = tabId,
                            callId = callId,
                            reason = getResult.reason
                        )
                        launchFallbackGooglePasswordManager(
                            tabId = tabId,
                            callId = callId,
                            mode = mode,
                            pageUrl = pageUrl,
                            originHost = originHost,
                            reason = getResult.reason
                        )
                    }
                }
            }
        }
    }

    private fun launchFallbackGooglePasswordManager(
        tabId: String,
        callId: String,
        mode: String,
        pageUrl: String,
        originHost: String,
        reason: String
    ) {
        val launchResult = openGooglePasswordManager(openWebFallbackInTab = false)
        _uiState.update {
            it.copy(
                passkeyPrompt = PasskeyPromptState(
                    tabId = tabId,
                    callId = callId,
                    mode = mode,
                    pageUrl = pageUrl,
                    originHost = originHost,
                    reason = reason,
                    isIncognito = false,
                    openedPasswordManagerAutomatically = launchResult == GooglePasswordManagerLaunchResult.OPENED_NATIVE
                )
            )
        }
    }

    fun openGooglePasswordManager(
        openWebFallbackInTab: Boolean = true
    ): GooglePasswordManagerLaunchResult {
        val uiContext = activityContextRef?.get() ?: getApplication<Application>()
        val result = PasskeyManagerBridge.openGooglePasswordManager(
            context = uiContext,
            onFallbackWebUrl = if (openWebFallbackInTab) {
                { webUrl -> openNewTab(webUrl, isIncognito = false) }
            } else {
                null
            }
        )
        when (result) {
            GooglePasswordManagerLaunchResult.OPENED_NATIVE ->
                showMessage("Opening Google Password Manager")
            GooglePasswordManagerLaunchResult.OPENED_WEB_FALLBACK ->
                showMessage("Opened passwords.google.com")
            GooglePasswordManagerLaunchResult.FAILED ->
                showMessage("Google Password Manager is unavailable on this device")
        }
        return result
    }

    fun confirmOpenGooglePasswordManagerFromPrompt(useWebPortal: Boolean = false) {
        val prompt = _uiState.value.passkeyPrompt
        if (prompt != null && prompt.callId.isNotBlank()) {
            container.webViewPool.rejectPasskeyJsCall(
                tabId = prompt.tabId,
                callId = prompt.callId,
                reason = "Continuing verification in Google Password Manager"
            )
        }
        _uiState.update { it.copy(passkeyPrompt = null) }
        if (useWebPortal) {
            openNewTab(PasskeyManagerBridge.GOOGLE_PASSWORD_MANAGER_WEB_URL, isIncognito = false)
        } else {
            openGooglePasswordManager(openWebFallbackInTab = true)
        }
    }

    fun dismissPasskeyPrompt() {
        val prompt = _uiState.value.passkeyPrompt
        if (prompt != null && prompt.callId.isNotBlank() && prompt.isIncognito) {
            container.webViewPool.rejectPasskeyJsCall(
                tabId = prompt.tabId,
                callId = prompt.callId,
                reason = "Passkey verification dismissed"
            )
        }
        _uiState.update { it.copy(passkeyPrompt = null) }
    }

    private fun updateTab(tabId: String, transform: (BrowserTabState) -> BrowserTabState) {
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { if (it.id == tabId) transform(it) else it }
            )
        }
    }

    override fun onCleared() {
        container.networkMonitor.unregister()
        container.clearanceEngine.flushCookies()
        container.webViewPool.destroyAll()
        super.onCleared()
    }

    class Factory(
        private val application: Application,
        private val container: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BrowserViewModel(application, container) as T
        }
    }

    companion object {
        const val MAX_TABS = 30
    }
}
