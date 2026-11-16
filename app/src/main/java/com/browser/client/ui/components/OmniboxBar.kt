package com.browser.client.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.browser.client.R
import com.browser.client.data.local.SearchHistoryEntity
import com.browser.client.engine.DomainClearanceStatus
import com.browser.client.engine.DomainUtils
import com.browser.client.engine.NetworkStatus
import com.browser.client.ui.BrowserTabState

@Composable
fun OmniboxBar(
    tab: BrowserTabState,
    networkStatus: NetworkStatus,
    addressInput: String,
    isEditingAddress: Boolean,
    tabCount: Int,
    vaultCount: Int,
    activeDownloadCount: Int = 0,
    isBookmarked: Boolean,
    findInPageVisible: Boolean,
    findInPageQuery: String,
    onAddressInputChange: (String) -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    onSubmitUrl: (String) -> Unit,
    onReloadOrStop: () -> Unit,
    onShieldClick: () -> Unit,
    onTabsClick: () -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onShredIncognito: () -> Unit,
    onSearchImageNewTab: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onToggleReaderMode: () -> Unit,
    onToggleFindInPage: () -> Unit,
    onFindQueryChange: (String) -> Unit,
    onFindNext: (Boolean) -> Unit,
    onShareUrl: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenBookmarksHistory: () -> Unit,
    onOpenSearchHistory: () -> Unit = {},
    onOpenDownloads: () -> Unit,
    onOpenPasswordManager: () -> Unit,
    onOpenCustomizeStarter: () -> Unit,
    onOpenSettings: () -> Unit,
    searchHistory: List<SearchHistoryEntity> = emptyList(),
    onRunSearchQuery: (SearchHistoryEntity) -> Unit = {},
    onDeleteSearchHistoryItem: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var menuExpanded by remember { mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "cf_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cf_pulse_alpha"
    )

    val borderColor = when {
        !networkStatus.isConnected -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        tab.isIncognito -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        tab.clearanceStatus == DomainClearanceStatus.VERIFYING_1X ->
            MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
        tab.clearanceStatus == DomainClearanceStatus.VERIFIED_1X ||
            tab.clearanceStatus == DomainClearanceStatus.RESTORED_FROM_VAULT ->
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Omnibox Pill
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.2.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shield / Security / Offline Button
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !tab.isHome, onClick = onShieldClick)
                                .testTag("shield_status_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                !networkStatus.isConnected -> Icon(
                                    imageVector = Icons.Rounded.WifiOff,
                                    contentDescription = "Offline",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                tab.isIncognito && tab.isHome -> Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = "Hardened Incognito",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                                tab.isIncognito -> Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = stringResource(R.string.cd_shield_status),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                                tab.isHome -> Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                tab.clearanceStatus == DomainClearanceStatus.VERIFYING_1X -> Icon(
                                    imageVector = Icons.Rounded.HourglassTop,
                                    contentDescription = stringResource(R.string.cd_shield_status),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .alpha(pulseAlpha)
                                )
                                tab.clearanceStatus == DomainClearanceStatus.VERIFIED_1X ||
                                    tab.clearanceStatus == DomainClearanceStatus.RESTORED_FROM_VAULT -> Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = stringResource(R.string.cd_shield_status),
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                tab.url.startsWith("https://") -> Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = stringResource(R.string.cd_shield_status),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                else -> Icon(
                                    imageVector = Icons.Rounded.Public,
                                    contentDescription = stringResource(R.string.cd_shield_status),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Editable URL / Display Host
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val displayText = if (isEditingAddress) {
                                addressInput
                            } else if (tab.isHome) {
                                ""
                            } else {
                                DomainUtils.extractHost(tab.url).ifBlank { tab.url }
                            }

                            val submitCurrentInput = {
                                val textToSubmit = addressInput
                                onSubmitUrl(textToSubmit)
                                focusManager.clearFocus()
                            }

                            BasicTextField(
                                value = if (isEditingAddress) addressInput else displayText,
                                onValueChange = onAddressInputChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = if (!tab.isHome && !isEditingAddress) {
                                        MaterialTheme.typography.labelMedium.fontFamily
                                    } else {
                                        MaterialTheme.typography.bodyLarge.fontFamily
                                    }
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go,
                                    autoCorrectEnabled = !tab.isIncognito
                                ),
                                keyboardActions = KeyboardActions(
                                    onGo = { submitCurrentInput() },
                                    onDone = { submitCurrentInput() },
                                    onSearch = { submitCurrentInput() },
                                    onSend = { submitCurrentInput() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyUp &&
                                            (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                                        ) {
                                            submitCurrentInput()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    .onFocusChanged { focusState ->
                                        onAddressFocusChange(focusState.isFocused)
                                    }
                                    .testTag("address_bar_input"),
                                decorationBox = { innerTextField ->
                                    if ((isEditingAddress && addressInput.isEmpty()) ||
                                        (!isEditingAddress && tab.isHome)
                                    ) {
                                        Text(
                                            text = stringResource(
                                                if (tab.isIncognito) R.string.incognito_placeholder
                                                else R.string.search_or_url_placeholder
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        if (isEditingAddress && addressInput.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    val textToSubmit = addressInput
                                    onSubmitUrl(textToSubmit)
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("go_address_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Go",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onAddressInputChange("") },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("clear_address_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_clear_input),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else if (!tab.isHome) {
                            if (tab.clearanceStatus == DomainClearanceStatus.VERIFIED_1X ||
                                tab.clearanceStatus == DomainClearanceStatus.RESTORED_FROM_VAULT
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .padding(end = 2.dp)
                                        .clickable(onClick = onShieldClick)
                                ) {
                                    Text(
                                        text = "1x",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (tab.offlineErrorUrl == null) {
                                IconButton(
                                    onClick = onToggleReaderMode,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("omnibox_reader_mode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoStories,
                                        contentDescription = if (tab.isReaderMode) "Exit Reader Mode" else "Reader Mode",
                                        tint = if (tab.isReaderMode) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = onReloadOrStop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("reload_stop_button")
                            ) {
                                Icon(
                                    imageVector = if (tab.isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh,
                                    contentDescription = stringResource(
                                        if (tab.isLoading) R.string.cd_stop else R.string.cd_reload
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Tab count pill button
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onTabsClick)
                        .testTag("tab_count_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(
                                width = 1.8.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(7.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (tabCount > 99) "99+" else tabCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 3-Dot Overflow Menu Button
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("browser_menu_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (activeDownloadCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ) {
                                        Text(activeDownloadCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.cd_menu),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("New Tab") },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onNewTab()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New Incognito Tab (Hardened)") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.VisibilityOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onNewIncognitoTab()
                            }
                        )
                        if (tab.isIncognito) {
                            DropdownMenuItem(
                                text = { Text("Shred & Exit Incognito") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onShredIncognito()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Search Image on New Tab") },
                            leadingIcon = { Icon(Icons.Rounded.ImageSearch, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onSearchImageNewTab()
                            }
                        )
                        if (!tab.isHome) {
                            DropdownMenuItem(
                                text = { Text(if (isBookmarked) "Remove Bookmark" else "Bookmark Page") },
                                leadingIcon = {
                                    Icon(
                                        if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                        contentDescription = null,
                                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleBookmark()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (tab.isReaderMode) "Reader Mode (On)" else "Reader Mode")
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.AutoStories,
                                        contentDescription = null,
                                        tint = if (tab.isReaderMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleReaderMode()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Find in Page") },
                                leadingIcon = { Icon(Icons.Rounded.FindInPage, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onToggleFindInPage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Link") },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onShareUrl()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(if (tab.isDesktopMode) "Desktop Mode (On)" else "Desktop Mode")
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.DesktopWindows,
                                    contentDescription = null,
                                    tint = if (tab.isDesktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleDesktopMode()
                            },
                            modifier = Modifier.testTag("menu_desktop_mode_item")
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Customize Starter Page") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onOpenCustomizeStarter()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clearance Vault ($vaultCount)") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onOpenVault()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Bookmarks & History") },
                            leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenBookmarksHistory()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (searchHistory.isNotEmpty()) "Search History (${searchHistory.size})"
                                    else "Search History"
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.ManageSearch, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSearchHistory()
                            },
                            modifier = Modifier.testTag("menu_search_history_item")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (activeDownloadCount > 0) "Downloads ($activeDownloadCount active)"
                                    else "Downloads"
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenDownloads()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Google Password Manager") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onOpenPasswordManager()
                            },
                            modifier = Modifier.testTag("menu_password_manager_item")
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            }
                        )
                    }
                }
            }

            // Recent Searches Suggestions (while typing in non-incognito omnibox)
            val matchingSearches = remember(searchHistory, addressInput, isEditingAddress, tab.isIncognito) {
                if (!isEditingAddress || tab.isIncognito) {
                    emptyList()
                } else {
                    val trimmed = addressInput.trim()
                    val filtered = if (trimmed.isEmpty()) {
                        searchHistory
                    } else {
                        searchHistory.filter { it.query.contains(trimmed, ignoreCase = true) }
                    }
                    filtered.take(5)
                }
            }
            AnimatedVisibility(
                visible = matchingSearches.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("omnibox_recent_searches_panel"),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT SEARCHES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "View all",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    focusManager.clearFocus()
                                    onOpenSearchHistory()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    matchingSearches.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    focusManager.clearFocus()
                                    onRunSearchQuery(entry)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = entry.query,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = DomainUtils.formatTimestamp(entry.searchedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = { onDeleteSearchHistoryItem(entry.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove recent search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Live Offline Connection Banner
            AnimatedVisibility(
                visible = !networkStatus.isConnected,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "No internet • Wi-Fi & Mobile Data disconnected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Hardened Incognito Active Banner
            AnimatedVisibility(
                visible = networkStatus.isConnected && tab.isIncognito && !tab.isHome && tab.offlineErrorUrl == null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f))
                        .clickable(onClick = onShieldClick)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PrivacyTip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Hardened Incognito • Screen shield, 3P cookie & tracker block ON",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onShredIncognito)
                            .testTag("omnibox_shred_incognito")
                    ) {
                        Text(
                            text = "Shred",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Live Cloudflare Challenge / Restored Banner
            AnimatedVisibility(
                visible = networkStatus.isConnected && !tab.isIncognito && !tab.isHome && tab.offlineErrorUrl == null && (
                    tab.clearanceStatus == DomainClearanceStatus.VERIFYING_1X ||
                        tab.clearanceStatus == DomainClearanceStatus.RESTORED_FROM_VAULT
                    ),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val isVerifying = tab.clearanceStatus == DomainClearanceStatus.VERIFYING_1X
                val bannerBg = if (isVerifying) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
                val bannerFg = if (isVerifying) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bannerBg)
                        .clickable(onClick = onShieldClick)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isVerifying) Icons.Rounded.HourglassTop else Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = bannerFg,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(
                            if (isVerifying) R.string.status_verifying else R.string.status_restored
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = bannerFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Desktop Mode Active Strip
            AnimatedVisibility(
                visible = networkStatus.isConnected && tab.isDesktopMode && !tab.isHome && tab.offlineErrorUrl == null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DesktopWindows,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Desktop Mode • 1280px viewport & Desktop Chrome UA",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onToggleDesktopMode)
                            .testTag("omnibox_desktop_mode_exit")
                    ) {
                        Text(
                            text = "Mobile",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Find-in-page strip
            AnimatedVisibility(
                visible = findInPageVisible && !tab.isHome,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FindInPage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = findInPageQuery,
                        onValueChange = onFindQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (findInPageQuery.isEmpty()) {
                                Text(
                                    text = "Find in page...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    )
                    IconButton(onClick = { onFindNext(false) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
                    }
                    IconButton(onClick = { onFindNext(true) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
                    }
                    IconButton(onClick = onToggleFindInPage, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close find bar")
                    }
                }
            }

            // Windows XP style segmented loading progress bar
            if (tab.isLoading) {
                val xpProgress = if (tab.progress in 1..99) tab.progress / 100f else null
                WinXpProgressBar(
                    progress = xpProgress,
                    height = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("omnibox_winxp_progress")
                )
            }
        }
    }
}
