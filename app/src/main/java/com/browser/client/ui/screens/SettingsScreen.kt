package com.browser.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.browser.client.R
import com.browser.client.data.preferences.BrowserSettings
import com.browser.client.data.preferences.JsSecurityMode
import com.browser.client.data.preferences.SearchEngine
import com.browser.client.data.preferences.ThemeMode
import com.browser.client.engine.AdAndDataGuard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: BrowserSettings,
    isDefaultBrowser: Boolean,
    onBack: () -> Unit,
    onRequestDefaultBrowser: () -> Unit,
    onSetSearchEngine: (SearchEngine) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAdBlocker: (Boolean) -> Unit,
    onSetDataSaver: (Boolean) -> Unit,
    onSetDefaultDesktopMode: (Boolean) -> Unit,
    onSetJsSecurityMode: (JsSecurityMode) -> Unit,
    onSetAutoFlush: (Boolean) -> Unit,
    onSetAutoRehydrate: (Boolean) -> Unit,
    onSetStripWebViewToken: (Boolean) -> Unit,
    onSetPerDomainUaLock: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onClearSearchHistory: () -> Unit = {},
    onClearAllClearances: () -> Unit
) {
    BackHandler(onBack = onBack)

    val blockedAds = AdAndDataGuard.blockedAdsCount.get()
    val savedData = AdAndDataGuard.savedDataRequestsCount.get()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Default Browser Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    1.dp,
                    if (isDefaultBrowser) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isDefaultBrowser) Icons.Rounded.CheckCircle else Icons.Rounded.OpenInBrowser,
                            contentDescription = null,
                            tint = if (isDefaultBrowser) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isDefaultBrowser) {
                                    stringResource(R.string.default_browser_active)
                                } else {
                                    stringResource(R.string.set_default_browser)
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (isDefaultBrowser) {
                                    "All HTTP/HTTPS links from other apps open in Vortex with your saved 1x clearances."
                                } else {
                                    "Make Vortex (com.browser.client) your Android default browser so external links never re-verify."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onRequestDefaultBrowser,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_default_browser_button")
                    ) {
                        Text(if (isDefaultBrowser) "Manage Default Browser Role" else "Set as Default Browser")
                    }
                }
            }

            // Search Engine (Google / Bing / Brave)
            SettingsSection(title = "SEARCH ENGINE") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    SearchEngine.entries.forEach { engine ->
                        FilterChip(
                            selected = settings.searchEngine == engine,
                            onClick = { onSetSearchEngine(engine) },
                            label = { Text(engine.label) },
                            modifier = Modifier.testTag("search_engine_${engine.name.lowercase()}")
                        )
                    }
                }
            }

            // AdBlocker & Data Saver
            SettingsSection(title = "ADBLOCKER & DATA SAVER") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Adblocker", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Blocks ad networks & trackers while allowing Cloudflare Turnstile ($blockedAds blocked)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.adBlockerEnabled,
                        onCheckedChange = onSetAdBlocker,
                        modifier = Modifier.testTag("adblocker_switch")
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DataSaverOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Data Saver", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Sends Save-Data header & skips heavy third-party media ($savedData saved)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.dataSaverEnabled,
                        onCheckedChange = onSetDataSaver,
                        modifier = Modifier.testTag("data_saver_switch")
                    )
                }
            }

            // Desktop Mode by Default
            SettingsSection(title = "DESKTOP MODE") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DesktopWindows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Always Request Desktop Site", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Open new tabs with Desktop Chrome User-Agent & 1280px desktop viewport.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.defaultDesktopMode,
                        onCheckedChange = onSetDefaultDesktopMode,
                        modifier = Modifier.testTag("default_desktop_mode_switch")
                    )
                }
            }

            // JS Management (Fast/Balanced vs Slower/Enhanced Security)
            SettingsSection(title = "JS MANAGEMENT") {
                JsSecurityOptionRow(
                    title = "Fast Browser • Balanced Security",
                    subtitle = "Make browser fast with a little bit of security (full JS speed, popups & standard mixed content).",
                    icon = Icons.Rounded.Speed,
                    selected = settings.jsSecurityMode == JsSecurityMode.FAST_BALANCED,
                    onClick = { onSetJsSecurityMode(JsSecurityMode.FAST_BALANCED) },
                    modifier = Modifier.testTag("js_mode_fast")
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                JsSecurityOptionRow(
                    title = "Slower Browser • Security Enhanced",
                    subtitle = "Not as fast, but enhanced security (blocks third-party JS trackers, popups & mixed content while keeping 1x Cloudflare active).",
                    icon = Icons.Rounded.Security,
                    selected = settings.jsSecurityMode == JsSecurityMode.STRICT_SECURITY,
                    onClick = { onSetJsSecurityMode(JsSecurityMode.STRICT_SECURITY) },
                    modifier = Modifier.testTag("js_mode_strict")
                )
            }

            // One-Time Cloudflare Engine
            SettingsSection(title = "ONE-TIME CLOUDFLARE ENGINE") {
                SettingToggleRow(
                    title = "Auto-Flush Cookies on Pass",
                    subtitle = "Immediately write cf_clearance to SQLite disk after 1st challenge.",
                    checked = settings.autoFlushOnChallengePass,
                    onCheckedChange = onSetAutoFlush
                )
                SettingToggleRow(
                    title = "Pre-Load Clearance Rehydration",
                    subtitle = "Re-inject saved cf_clearance before navigation if cleared by WebView.",
                    checked = settings.autoRehydrateBeforeLoad,
                    onCheckedChange = onSetAutoRehydrate
                )
                SettingToggleRow(
                    title = "Clean Chrome User-Agent",
                    subtitle = "Strip '; wv' WebView token so Cloudflare issues durable clearances.",
                    checked = settings.stripWebViewToken,
                    onCheckedChange = onSetStripWebViewToken
                )
                SettingToggleRow(
                    title = "Per-Domain User-Agent Lock",
                    subtitle = "Lock the exact User-Agent used during verification for each domain.",
                    checked = settings.perDomainUaLock,
                    onCheckedChange = onSetPerDomainUaLock
                )
            }

            // Theme
            SettingsSection(title = "APPEARANCE") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }

            // Data Management
            SettingsSection(title = "DATA & PRIVACY") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClearHistory,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear History")
                        }
                        OutlinedButton(
                            onClick = onClearSearchHistory,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_clear_search_history_button")
                        ) {
                            Text("Clear Searches")
                        }
                    }
                    OutlinedButton(
                        onClick = onClearAllClearances,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Clearance Vault", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun JsSecurityOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
