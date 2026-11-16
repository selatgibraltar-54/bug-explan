package com.browser.client.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.browserDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "clearpass_settings"
)

enum class SearchEngine(
    val label: String,
    val searchUrlTemplate: String,
    val imageSearchUrl: String
) {
    GOOGLE(
        label = "Google",
        searchUrlTemplate = "https://www.google.com/search?q=%s",
        imageSearchUrl = "https://www.google.com/imghp"
    ),
    BING(
        label = "Bing",
        searchUrlTemplate = "https://www.bing.com/search?q=%s",
        imageSearchUrl = "https://www.bing.com/images"
    ),
    BRAVE(
        label = "Brave",
        searchUrlTemplate = "https://search.brave.com/search?q=%s",
        imageSearchUrl = "https://search.brave.com/images"
    )
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light")
}

enum class JsSecurityMode(val label: String, val subtitle: String) {
    FAST_BALANCED(
        label = "Fast • Balanced Security",
        subtitle = "Make browser fast with standard security & full JS speed."
    ),
    STRICT_SECURITY(
        label = "Enhanced Security",
        subtitle = "Slower browsing with strict third-party JS & mixed-content protection."
    )
}

data class BrowserSettings(
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val autoFlushOnChallengePass: Boolean = true,
    val autoRehydrateBeforeLoad: Boolean = true,
    val stripWebViewToken: Boolean = true,
    val perDomainUaLock: Boolean = true,
    val defaultDesktopMode: Boolean = false,
    val adBlockerEnabled: Boolean = true,
    val dataSaverEnabled: Boolean = false,
    val jsSecurityMode: JsSecurityMode = JsSecurityMode.FAST_BALANCED,
    val starterWallpaperPath: String = "",
    val showImageSearchOnNewTab: Boolean = true
)

class BrowserPreferencesRepository(private val context: Context) {

    private object Keys {
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_FLUSH = booleanPreferencesKey("auto_flush_on_pass")
        val AUTO_REHYDRATE = booleanPreferencesKey("auto_rehydrate_before_load")
        val STRIP_WV = booleanPreferencesKey("strip_wv_token")
        val PER_DOMAIN_UA_LOCK = booleanPreferencesKey("per_domain_ua_lock")
        val DEFAULT_DESKTOP_MODE = booleanPreferencesKey("default_desktop_mode")
        val AD_BLOCKER = booleanPreferencesKey("ad_blocker_enabled")
        val DATA_SAVER = booleanPreferencesKey("data_saver_enabled")
        val JS_SECURITY_MODE = stringPreferencesKey("js_security_mode")
        val STARTER_WALLPAPER_PATH = stringPreferencesKey("starter_wallpaper_path")
        val SHOW_IMAGE_SEARCH_NEW_TAB = booleanPreferencesKey("show_image_search_new_tab")
    }

    val settingsFlow: Flow<BrowserSettings> = context.browserDataStore.data.map { prefs ->
        val engineName = prefs[Keys.SEARCH_ENGINE] ?: SearchEngine.GOOGLE.name
        val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        val jsModeName = prefs[Keys.JS_SECURITY_MODE] ?: JsSecurityMode.FAST_BALANCED.name
        BrowserSettings(
            searchEngine = SearchEngine.entries.firstOrNull { it.name == engineName } ?: SearchEngine.GOOGLE,
            themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.DARK,
            autoFlushOnChallengePass = prefs[Keys.AUTO_FLUSH] ?: true,
            autoRehydrateBeforeLoad = prefs[Keys.AUTO_REHYDRATE] ?: true,
            stripWebViewToken = prefs[Keys.STRIP_WV] ?: true,
            perDomainUaLock = prefs[Keys.PER_DOMAIN_UA_LOCK] ?: true,
            defaultDesktopMode = prefs[Keys.DEFAULT_DESKTOP_MODE] ?: false,
            adBlockerEnabled = prefs[Keys.AD_BLOCKER] ?: true,
            dataSaverEnabled = prefs[Keys.DATA_SAVER] ?: false,
            jsSecurityMode = JsSecurityMode.entries.firstOrNull { it.name == jsModeName }
                ?: JsSecurityMode.FAST_BALANCED,
            starterWallpaperPath = prefs[Keys.STARTER_WALLPAPER_PATH].orEmpty(),
            showImageSearchOnNewTab = prefs[Keys.SHOW_IMAGE_SEARCH_NEW_TAB] ?: true
        )
    }

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.browserDataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.browserDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAutoFlush(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.AUTO_FLUSH] = enabled }
    }

    suspend fun setAutoRehydrate(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.AUTO_REHYDRATE] = enabled }
    }

    suspend fun setStripWebViewToken(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.STRIP_WV] = enabled }
    }

    suspend fun setPerDomainUaLock(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.PER_DOMAIN_UA_LOCK] = enabled }
    }

    suspend fun setDefaultDesktopMode(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.DEFAULT_DESKTOP_MODE] = enabled }
    }

    suspend fun setAdBlockerEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.AD_BLOCKER] = enabled }
    }

    suspend fun setDataSaverEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.DATA_SAVER] = enabled }
    }

    suspend fun setJsSecurityMode(mode: JsSecurityMode) {
        context.browserDataStore.edit { it[Keys.JS_SECURITY_MODE] = mode.name }
    }

    suspend fun setStarterWallpaperPath(path: String) {
        context.browserDataStore.edit { it[Keys.STARTER_WALLPAPER_PATH] = path }
    }

    suspend fun setShowImageSearchOnNewTab(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.SHOW_IMAGE_SEARCH_NEW_TAB] = enabled }
    }
}
