package com.browser.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.browser.client.R
import com.browser.client.data.local.BookmarkEntity
import com.browser.client.data.local.HistoryEntity
import com.browser.client.data.local.SearchHistoryEntity
import com.browser.client.engine.DomainUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksHistoryScreen(
    bookmarks: List<BookmarkEntity>,
    history: List<HistoryEntity>,
    searchHistory: List<SearchHistoryEntity> = emptyList(),
    initialTab: Int = 0,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onAddBookmark: (title: String, url: String, isPinned: Boolean) -> Unit,
    onUpdateBookmark: (id: Long, title: String, url: String, isPinned: Boolean) -> Unit,
    onToggleBookmarkPin: (Long) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteHistoryItem: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onRunSearchQuery: (SearchHistoryEntity) -> Unit = { onOpenUrl(it.searchUrl) },
    onDeleteSearchHistoryItem: (Long) -> Unit = {},
    onClearSearchHistory: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    LaunchedEffect(initialTab) {
        selectedTab = initialTab.coerceIn(0, 2)
    }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedTab == 2) "Search History"
                        else stringResource(R.string.bookmarks_title)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("bookmarks_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    if (selectedTab == 1 && history.isNotEmpty()) {
                        TextButton(
                            onClick = onClearHistory,
                            modifier = Modifier.testTag("history_clear_all_button")
                        ) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    } else if (selectedTab == 2 && searchHistory.isNotEmpty()) {
                        TextButton(
                            onClick = onClearSearchHistory,
                            modifier = Modifier.testTag("search_history_clear_all_button")
                        ) {
                            Text("Clear All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Add Bookmark") },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("bookmarks_add_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = { Icon(Icons.Rounded.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("bookmarks_tab_button")
                ) {
                    Text(
                        text = "Bookmarks (${bookmarks.size})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("history_tab_button")
                ) {
                    Text(
                        text = "History (${history.size})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SegmentedButton(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = { Icon(Icons.Rounded.ManageSearch, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("search_history_tab_button")
                ) {
                    Text(
                        text = "Searches (${searchHistory.size})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("bookmarks_search_input")
            )

            if (selectedTab == 0) {
                val filteredBookmarks = remember(bookmarks, searchQuery) {
                    if (searchQuery.isBlank()) bookmarks else bookmarks.filter {
                        it.title.contains(searchQuery.trim(), ignoreCase = true) ||
                            it.url.contains(searchQuery.trim(), ignoreCase = true) ||
                            it.domain.contains(searchQuery.trim(), ignoreCase = true)
                    }
                }
                if (filteredBookmarks.isEmpty()) {
                    EmptyLibraryState(
                        icon = Icons.Rounded.Bookmark,
                        title = "No Bookmarks Found",
                        subtitle = "Tap Add Bookmark below or Bookmark Page in the menu to save websites locally."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 88.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredBookmarks, key = { it.id }) { item ->
                            BookmarkItemCard(
                                item = item,
                                onClick = { onOpenUrl(item.url) },
                                onTogglePin = { onToggleBookmarkPin(item.id) },
                                onEdit = { editingBookmark = item },
                                onDelete = { onDeleteBookmark(item.id) }
                            )
                        }
                    }
                }
            } else if (selectedTab == 1) {
                val filteredHistory = remember(history, searchQuery) {
                    if (searchQuery.isBlank()) history else history.filter {
                        it.title.contains(searchQuery.trim(), ignoreCase = true) ||
                            it.url.contains(searchQuery.trim(), ignoreCase = true)
                    }
                }
                if (filteredHistory.isEmpty()) {
                    EmptyLibraryState(
                        icon = Icons.Rounded.History,
                        title = "No History Yet",
                        subtitle = "Pages you visit appear here with Cloudflare clearance indicators."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredHistory, key = { it.id }) { entry ->
                            HistoryItemRow(
                                title = entry.title,
                                subtitle = "${DomainUtils.formatTimestamp(entry.visitedAt)} • ${entry.domain}",
                                isCfProtected = entry.wasCfProtected,
                                onClick = { onOpenUrl(entry.url) },
                                onDelete = { onDeleteHistoryItem(entry.id) }
                            )
                        }
                    }
                }
            } else {
                val filteredSearches = remember(searchHistory, searchQuery) {
                    val trimmed = searchQuery.trim()
                    if (trimmed.isBlank()) {
                        searchHistory
                    } else {
                        searchHistory.filter {
                            it.query.contains(trimmed, ignoreCase = true) ||
                                it.searchEngine.contains(trimmed, ignoreCase = true)
                        }
                    }
                }
                if (filteredSearches.isEmpty()) {
                    EmptyLibraryState(
                        icon = Icons.Rounded.ManageSearch,
                        title = "No Search History Yet",
                        subtitle = "Search queries entered in the address bar appear here chronologically. Incognito searches are never saved."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("search_history_list")
                    ) {
                        item(key = "search_history_header") {
                            Text(
                                text = "CHRONOLOGICAL SEARCHES (${filteredSearches.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        items(filteredSearches, key = { it.id }) { entry ->
                            SearchHistoryItemRow(
                                entry = entry,
                                onClick = { onRunSearchQuery(entry) },
                                onDelete = { onDeleteSearchHistoryItem(entry.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        BookmarkEditorDialog(
            dialogTitle = "Add Bookmark",
            confirmLabel = "Save",
            initialTitle = "",
            initialUrl = "",
            initialPinned = false,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, url, isPinned ->
                onAddBookmark(title, url, isPinned)
                showAddDialog = false
            }
        )
    }

    editingBookmark?.let { target ->
        BookmarkEditorDialog(
            dialogTitle = "Edit Bookmark",
            confirmLabel = "Update",
            initialTitle = target.title,
            initialUrl = target.url,
            initialPinned = target.isPinned,
            onDismiss = { editingBookmark = null },
            onConfirm = { title, url, isPinned ->
                onUpdateBookmark(target.id, title, url, isPinned)
                editingBookmark = null
            }
        )
    }
}

@Composable
private fun BookmarkItemCard(
    item: BookmarkEntity,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val initial = item.title.firstOrNull()?.uppercaseChar()?.toString()
        ?: item.domain.firstOrNull()?.uppercaseChar()?.toString()
        ?: "B"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("bookmark_item_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isPinned) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Pinned",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = item.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Saved ${DomainUtils.formatTimestamp(item.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .testTag("bookmark_pin_${item.id}")
                ) {
                    Icon(
                        imageVector = if (item.isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (item.isPinned) "Unpin from Start Page" else "Pin to Start Page",
                        tint = if (item.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .testTag("bookmark_edit_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit bookmark",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .testTag("bookmark_delete_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete bookmark",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkEditorDialog(
    dialogTitle: String,
    confirmLabel: String,
    initialTitle: String,
    initialUrl: String,
    initialPinned: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String, isPinned: Boolean) -> Unit
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var isPinned by remember(initialPinned) { mutableStateOf(initialPinned) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("bookmark_editor_dialog")
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Wikipedia") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bookmark_title_input")
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Website URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bookmark_url_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pin to Start Page shortcuts",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it },
                        modifier = Modifier.testTag("bookmark_pin_switch")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, url, isPinned) },
                enabled = url.isNotBlank(),
                modifier = Modifier.testTag("bookmark_save_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun HistoryItemRow(
    title: String,
    subtitle: String,
    isCfProtected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isCfProtected) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = "1x Verified",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchHistoryItemRow(
    entry: SearchHistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("search_history_item_${entry.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.query,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${DomainUtils.formatTimestamp(entry.searchedAt)} • ${entry.searchEngine}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
                    .testTag("search_history_delete_${entry.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete search entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp)
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
