package com.browser.client

import android.app.Application
import android.webkit.CookieManager
import com.browser.client.data.local.ClearPassDatabase
import com.browser.client.data.preferences.BrowserPreferencesRepository
import com.browser.client.data.repository.BrowserDataRepository
import com.browser.client.data.repository.ClearanceRepository
import com.browser.client.engine.BrowserDownloadEngine
import com.browser.client.engine.CloudflareClearanceEngine
import com.browser.client.engine.NetworkMonitor
import com.browser.client.engine.SessionFingerprintManager
import com.browser.client.engine.WebViewPool

class AppContainer(application: Application) {
    private val database = ClearPassDatabase.getInstance(application)

    val clearanceRepository = ClearanceRepository(database.clearanceDao())
    val browserDataRepository = BrowserDataRepository(
        bookmarkDao = database.bookmarkDao(),
        historyDao = database.historyDao(),
        downloadDao = database.downloadDao(),
        searchHistoryDao = database.searchHistoryDao()
    )
    val preferencesRepository = BrowserPreferencesRepository(application)
    val fingerprintManager = SessionFingerprintManager(application)
    val networkMonitor = NetworkMonitor(application)
    val clearanceEngine = CloudflareClearanceEngine(
        repository = clearanceRepository,
        fingerprintManager = fingerprintManager
    )
    val downloadEngine = BrowserDownloadEngine(
        context = application,
        repository = browserDataRepository
    )
    val webViewPool = WebViewPool(
        context = application,
        clearanceEngine = clearanceEngine,
        fingerprintManager = fingerprintManager,
        networkMonitor = networkMonitor
    )
}

class ClearPassApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CookieManager.getInstance().setAcceptCookie(true)
        container = AppContainer(this)
    }
}
