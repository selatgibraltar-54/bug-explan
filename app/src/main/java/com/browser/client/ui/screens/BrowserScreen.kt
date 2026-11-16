package com.browser.client.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.browser.client.R
import com.browser.client.engine.WebViewPool
import com.browser.client.ui.BrowserOverlay
import com.browser.client.ui.BrowserViewModel
import com.browser.client.ui.PasskeyPromptState
import com.browser.client.ui.components.OfflineErrorView
import com.browser.client.ui.components.OmniboxBar
import com.browser.client.ui.components.ReaderModeView
import com.browser.client.ui.components.StartPage
import com.browser.client.ui.components.WebViewContainer

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    webViewPool: WebViewPool,
    onRequestDefaultBrowser: () -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(context) {
        viewModel.attachActivityContext(context)
        onDispose { }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val networkStatus by viewModel.networkStatus.collectAsStateWithLifecycle()
    val clearances by viewModel.clearances.collectAsStateWithLifecycle()
    val pinnedBookmarks by viewModel.pinnedBookmarks.collectAsStateWithLifecycle()
    val allBookmarks by viewModel.allBookmarks.collectAsStateWithLifecycle()
    val recentHistory by viewModel.recentHistory.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val downloadItems by viewModel.downloadItems.collectAsStateWithLifecycle()
    val activeDownloadCount by viewModel.activeDownloadCount.collectAsStateWithLifecycle()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importStarterWallpaper(uri)
        }
    }

    val activeTab = uiState.activeTab
    val isBookmarked = remember(allBookmarks, activeTab.url) {
        allBookmarks.any { it.url == activeTab.url }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    BackHandler(enabled = !activeTab.isHome && uiState.activeOverlay == BrowserOverlay.NONE) {
        viewModel.goBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                OmniboxBar(
                    tab = activeTab,
                    networkStatus = networkStatus,
                    addressInput = uiState.addressInput,
                    isEditingAddress = uiState.isEditingAddress,
                    tabCount = uiState.tabs.size,
                    vaultCount = clearances.size,
                    activeDownloadCount = activeDownloadCount,
                    isBookmarked = isBookmarked,
                    findInPageVisible = uiState.findInPageVisible,
                    findInPageQuery = uiState.findInPageQuery,
                    onAddressInputChange = viewModel::onAddressInputChanged,
                    onAddressFocusChange = viewModel::onAddressFocusChanged,
                    onSubmitUrl = viewModel::navigateFromOmnibox,
                    onReloadOrStop = viewModel::reloadOrStop,
                    onShieldClick = { viewModel.openOverlay(BrowserOverlay.SITE_INSPECTOR) },
                    onTabsClick = { viewModel.openOverlay(BrowserOverlay.TAB_SWITCHER) },
                    onNewTab = { viewModel.openNewTab() },
                    onNewIncognitoTab = { viewModel.openIncognitoTab() },
                    onShredIncognito = viewModel::closeAllIncognitoTabs,
                    onSearchImageNewTab = viewModel::openImageSearchInNewTab,
                    onToggleBookmark = viewModel::toggleCurrentBookmark,
                    onToggleDesktopMode = viewModel::toggleDesktopMode,
                    onToggleReaderMode = viewModel::toggleReaderMode,
                    onToggleFindInPage = viewModel::toggleFindInPage,
                    onFindQueryChange = viewModel::updateFindQuery,
                    onFindNext = viewModel::findNext,
                    onShareUrl = {
                        if (activeTab.url.isNotBlank()) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, activeTab.title)
                                putExtra(Intent.EXTRA_TEXT, activeTab.url)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share link"))
                        }
                    },
                    onOpenVault = { viewModel.openOverlay(BrowserOverlay.CLEARANCE_VAULT) },
                    onOpenBookmarksHistory = { viewModel.openBookmarksHistory(initialTab = 0) },
                    onOpenSearchHistory = { viewModel.openBookmarksHistory(initialTab = 2) },
                    onOpenDownloads = { viewModel.openOverlay(BrowserOverlay.DOWNLOADS) },
                    onOpenPasswordManager = { viewModel.openGooglePasswordManager() },
                    onOpenCustomizeStarter = { viewModel.openOverlay(BrowserOverlay.CUSTOMIZE_STARTER) },
                    onOpenSettings = { viewModel.openOverlay(BrowserOverlay.SETTINGS) },
                    searchHistory = searchHistory,
                    onRunSearchQuery = viewModel::runSearchFromHistory,
                    onDeleteSearchHistoryItem = viewModel::deleteSearchHistoryItem
                )
            },
            bottomBar = {
                BrowserBottomToolbar(
                    canGoBack = !activeTab.isHome,
                    canGoForward = !activeTab.isHome && activeTab.canGoForward,
                    isHome = activeTab.isHome,
                    vaultCount = clearances.size,
                    onBack = { viewModel.goBack() },
                    onForward = { viewModel.goForward() },
                    onHome = { viewModel.goHome() },
                    onVault = { viewModel.openOverlay(BrowserOverlay.CLEARANCE_VAULT) },
                    onBookmarks = { viewModel.openOverlay(BrowserOverlay.BOOKMARKS_HISTORY) },
                    onSettings = { viewModel.openOverlay(BrowserOverlay.SETTINGS) }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    activeTab.isHome -> {
                        StartPage(
                            bookmarks = pinnedBookmarks,
                            clearances = clearances,
                            settings = settings,
                            networkStatus = networkStatus,
                            isDefaultBrowser = uiState.isDefaultBrowser,
                            isIncognito = activeTab.isIncognito,
                            onOpenUrl = viewModel::loadUrlInActiveTab,
                            onAddBookmark = viewModel::addCustomBookmark,
                            onOpenVault = { viewModel.openOverlay(BrowserOverlay.CLEARANCE_VAULT) },
                            onOpenCustomizeStarter = { viewModel.openOverlay(BrowserOverlay.CUSTOMIZE_STARTER) },
                            onSearchImageNewTab = viewModel::openImageSearchInNewTab,
                            onOpenIncognitoTab = { viewModel.openIncognitoTab() },
                            onExitIncognito = viewModel::closeAllIncognitoTabs,
                            onRequestDefaultBrowser = onRequestDefaultBrowser
                        )
                    }
                    activeTab.offlineErrorUrl != null -> {
                        OfflineErrorView(
                            failedUrl = activeTab.offlineErrorUrl!!,
                            isOnlineNow = networkStatus.isConnected,
                            onRetry = viewModel::retryOfflineTab,
                            onGoHome = viewModel::goHome
                        )
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            WebViewContainer(
                                tabId = activeTab.id,
                                isIncognito = activeTab.isIncognito,
                                isDesktopMode = activeTab.isDesktopMode,
                                pendingUrlToLoad = activeTab.pendingUrlToLoad,
                                webViewPool = webViewPool,
                                settingsProvider = { settings },
                                callbacks = viewModel,
                                onConsumePendingLoad = viewModel::consumePendingLoad
                            )
                            AnimatedVisibility(
                                visible = activeTab.isReaderMode && activeTab.readerArticle != null,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                activeTab.readerArticle?.let { article ->
                                    ReaderModeView(
                                        article = article,
                                        url = activeTab.url,
                                        fontScale = uiState.readerFontScale,
                                        isSepiaPaper = uiState.readerSepiaPaper && !activeTab.isIncognito,
                                        isIncognito = activeTab.isIncognito,
                                        onDecreaseFont = viewModel::decreaseReaderFont,
                                        onIncreaseFont = viewModel::increaseReaderFont,
                                        onToggleSepia = viewModel::toggleReaderSepia,
                                        onExitReaderMode = viewModel::toggleReaderMode
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full-Screen Overlays
        AnimatedVisibility(
            visible = uiState.activeOverlay == BrowserOverlay.TAB_SWITCHER,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TabSwitcherSheet(
                tabs = uiState.tabs,
                activeTabId = uiState.activeTabId,
                maxTabs = BrowserViewModel.MAX_TABS,
                onSelectTab = viewModel::selectTab,
                onCloseTab = viewModel::closeTab,
                onNewTab = { viewModel.openNewTab() },
                onNewIncognitoTab = { viewModel.openIncognitoTab() },
                onCloseAllIncognito = viewModel::closeAllIncognitoTabs,
                onDismiss = viewModel::closeOverlay
            )
        }

        AnimatedVisibility(
            visible = uiState.activeOverlay == BrowserOverlay.CUSTOMIZE_STARTER,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CustomizeStarterPageScreen(
                settings = settings,
                onBack = viewModel::closeOverlay,
                onImportImage = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemoveImage = viewModel::removeStarterWallpaper,
                onSearchImageNewTab = viewModel::openImageSearchInNewTab,
                onToggleShowImageSearchOnNewTab = viewModel::setShowImageSearchOnNewTab,
                onSetThemeMode = viewModel::setThemeMode
            )
        }

        AnimatedVisibility(
            visible = uiState.activeOverlay == BrowserOverlay.CLEARANCE_VAULT,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ClearanceVaultScreen(
                clearances = clearances,
                onBack = viewModel::closeOverlay,
                onOpenSite = { url -> viewModel.loadUrlInActiveTab(url) },
                onRevokeDomain = viewModel::revokeClearance,
                onClearAll = viewModel::clearAllClearances,
                onFlushCookies = viewModel::flushCookiesNow
            )
        }

        AnimatedVisibility(
            visible = uiState.activeOverlay == BrowserOverlay.BOOKMARKS_HISTORY,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BookmarksHistoryScreen(
                bookmarks = allBookmarks,
                history = recentHistory,
                searchHistory = searchHistory,
                initialTab = uiState.bookmarksHistoryInitialTab,
                onBack = viewModel::closeOverlay,
                onOpenUrl = { url -> viewModel.loadUrlInActiveTab(url) },
                onAddBookmark = viewModel::addCustomBookmark,
                onUpdateBookmark = viewModel::updateBookmark,
                onToggleBookmarkPin = viewModel::toggleBookmarkPinned,
                onDeleteBookmark = viewModel::removeBookmark,
                onDeleteHistoryItem = viewModel::deleteHistoryItem,
                onClearHistory = viewModel::clearHistory,
                onRunSearchQuery = viewModel::runSearchFromHistory,
                onDeleteSearchHistoryItem = viewModel::deleteSearchHistoryItem,
                onClearSearchHistory = viewModel::clearSearchHistory
            )
        }

        AnimatedVisibility(
            visible = uiState.activeOverlay == BrowserOverlay.DOWNLOADS,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            DownloadsScreen(
                downloads = downloadItems,
                onBack = viewModel::closeOverlay,
                onPauseDownload = viewModel::pauseDownload,
                onResumeDownload = viewModel::resumeDownload,
                onCancelDownload = viewModel::cancelDownload,
                onRetryDownload = viewModel::retryDownload,
                onDeleteDownload = viewModel::deleteDownloadItem,
                onClearAll = viewModel::clearDownloadsHistory,
                onShowMessage = viewModel::showMessage
            )
        }

        AnimatedVisibility(
            visible = uiState.activeOverlay == BrowserOverlay.SETTINGS,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SettingsScreen(
                settings = settings,
                isDefaultBrowser = uiState.isDefaultBrowser,
                onBack = viewModel::closeOverlay,
                onRequestDefaultBrowser = onRequestDefaultBrowser,
                onSetSearchEngine = viewModel::setSearchEngine,
                onSetThemeMode = viewModel::setThemeMode,
                onSetAdBlocker = viewModel::setAdBlockerEnabled,
                onSetDataSaver = viewModel::setDataSaverEnabled,
                onSetDefaultDesktopMode = viewModel::setDefaultDesktopMode,
                onSetJsSecurityMode = viewModel::setJsSecurityMode,
                onSetAutoFlush = viewModel::setAutoFlush,
                onSetAutoRehydrate = viewModel::setAutoRehydrate,
                onSetStripWebViewToken = viewModel::setStripWebViewToken,
                onSetPerDomainUaLock = viewModel::setPerDomainUaLock,
                onClearHistory = viewModel::clearHistory,
                onClearSearchHistory = viewModel::clearSearchHistory,
                onClearAllClearances = viewModel::clearAllClearances
            )
        }

        if (uiState.activeOverlay == BrowserOverlay.SITE_INSPECTOR && !activeTab.isHome) {
            SiteInspectorSheet(
                tab = activeTab,
                clearanceEntity = if (activeTab.isIncognito) null else viewModel.getClearanceEntityForActiveTab(),
                onDismiss = viewModel::closeOverlay,
                onToggleDesktopMode = viewModel::toggleDesktopMode,
                onOpenPasswordManager = { viewModel.openGooglePasswordManager() },
                onRevokeAndReload = { domain ->
                    viewModel.revokeClearance(domain)
                    viewModel.reloadOrStop()
                },
                onShredIncognito = viewModel::closeAllIncognitoTabs,
                onOpenVault = { viewModel.openOverlay(BrowserOverlay.CLEARANCE_VAULT) }
            )
        }

        uiState.passkeyPrompt?.let { prompt ->
            PasskeyPromptDialog(
                prompt = prompt,
                onOpenGooglePasswordManager = {
                    viewModel.confirmOpenGooglePasswordManagerFromPrompt(useWebPortal = false)
                },
                onOpenWebPortal = {
                    viewModel.confirmOpenGooglePasswordManagerFromPrompt(useWebPortal = true)
                },
                onDismiss = viewModel::dismissPasskeyPrompt
            )
        }
    }
}

@Composable
private fun PasskeyPromptDialog(
    prompt: PasskeyPromptState,
    onOpenGooglePasswordManager: () -> Unit,
    onOpenWebPortal: () -> Unit,
    onDismiss: () -> Unit
) {
    val actionTitle = when {
        prompt.isIncognito -> "Passkey Requested in Incognito"
        prompt.mode == "create" -> "Create Passkey"
        else -> "Verify with Passkey"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(actionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (prompt.openedPasswordManagerAutomatically) {
                        "${prompt.originHost} asked to verify with a passkey. Google Password Manager was opened for this request."
                    } else {
                        "${prompt.originHost} requested passkey verification."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (prompt.reason.isNotBlank()) {
                    Text(
                        text = prompt.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onOpenGooglePasswordManager,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("passkey_open_gpm_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Google Password Manager")
                }
                OutlinedButton(
                    onClick = onOpenWebPortal,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("passkey_open_web_button")
                ) {
                    Text("Open passwords.google.com")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("passkey_dismiss_button")
            ) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
private fun BrowserBottomToolbar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isHome: Boolean,
    vaultCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onVault: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = canGoBack,
                    modifier = Modifier.testTag("nav_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
                IconButton(
                    onClick = onForward,
                    enabled = canGoForward,
                    modifier = Modifier.testTag("nav_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = stringResource(R.string.cd_forward)
                    )
                }
                IconButton(
                    onClick = onHome,
                    modifier = Modifier.testTag("nav_home_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = stringResource(R.string.cd_home),
                        tint = if (isHome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onVault,
                    modifier = Modifier.testTag("nav_vault_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (vaultCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ) {
                                    Text(vaultCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = stringResource(R.string.cd_vault),
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onBookmarks,
                    modifier = Modifier.testTag("nav_bookmarks_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bookmarks,
                        contentDescription = "Bookmarks and history"
                    )
                }
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.testTag("nav_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Browser settings"
                    )
                }
            }
        }
    }
}
