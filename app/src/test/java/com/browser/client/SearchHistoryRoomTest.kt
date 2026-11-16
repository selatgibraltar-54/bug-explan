package com.browser.client

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browser.client.data.local.ClearPassDatabase
import com.browser.client.data.local.SearchHistoryEntity
import com.browser.client.data.preferences.ThemeMode
import com.browser.client.data.repository.BrowserDataRepository
import com.browser.client.engine.DomainUtils
import com.browser.client.ui.BrowserViewModel
import com.browser.client.ui.screens.BookmarksHistoryScreen
import com.browser.client.ui.theme.ClearPassTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchHistoryRoomTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: ClearPassDatabase
    private lateinit var repository: BrowserDataRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, ClearPassDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BrowserDataRepository(
            bookmarkDao = db.bookmarkDao(),
            historyDao = db.historyDao(),
            downloadDao = db.downloadDao(),
            searchHistoryDao = db.searchHistoryDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `search history repository stores chronological queries and clears individual or all entries`() =
        runBlocking {
            repository.recordSearchQuery(
                query = "kotlin coroutines",
                searchEngine = "Google",
                searchUrl = "https://www.google.com/search?q=kotlin+coroutines",
                searchedAt = 1000L
            )
            repository.recordSearchQuery(
                query = "jetpack compose room",
                searchEngine = "Brave",
                searchUrl = "https://search.brave.com/search?q=jetpack+compose+room",
                searchedAt = 2000L
            )
            repository.recordSearchQuery(
                query = "android passkeys",
                searchEngine = "Bing",
                searchUrl = "https://www.bing.com/search?q=android+passkeys",
                searchedAt = 3000L
            )

            val chronological = repository.searchHistory.first()
            assertEquals(
                listOf("android passkeys", "jetpack compose room", "kotlin coroutines"),
                chronological.map { it.query }
            )

            // Repeating an older query updates its timestamp and moves it to the top
            repository.recordSearchQuery(
                query = "Kotlin Coroutines",
                searchEngine = "Google",
                searchUrl = "https://www.google.com/search?q=Kotlin+Coroutines",
                searchedAt = 4000L
            )
            val reordered = repository.searchHistory.first()
            assertEquals(
                listOf("Kotlin Coroutines", "android passkeys", "jetpack compose room"),
                reordered.map { it.query }
            )

            // Individual delete
            val middleId = reordered[1].id
            repository.deleteSearchHistoryItem(middleId)
            val afterSingleDelete = repository.getAllSearchHistoryOnce()
            assertEquals(2, afterSingleDelete.size)
            assertEquals(
                listOf("Kotlin Coroutines", "jetpack compose room"),
                afterSingleDelete.map { it.query }
            )

            // Clear all
            repository.clearSearchHistory()
            assertTrue(repository.getAllSearchHistoryOnce().isEmpty())
        }

    @Test
    fun `domain utils extracts search queries cleanly without treating URLs as searches`() {
        assertEquals("android room tutorial", DomainUtils.extractSearchQueryIfSearch("android room tutorial"))
        assertEquals("weather", DomainUtils.extractSearchQueryIfSearch("  weather  "))
        assertNull(DomainUtils.extractSearchQueryIfSearch("example.com"))
        assertNull(DomainUtils.extractSearchQueryIfSearch("https://github.com"))
        assertNull(DomainUtils.extractSearchQueryIfSearch("192.168.1.1"))

        val parsed = DomainUtils.extractSearchQueryFromUrl("https://www.google.com/search?q=clearpass+browser")
        assertEquals("clearpass browser" to "Google", parsed)
        assertNull(DomainUtils.extractSearchQueryFromUrl("https://github.com/android"))
    }

    @Test
    fun `viewModel captures omnibox searches in normal mode and skips incognito searches`() =
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<Application>() as ClearPassApplication
            app.container.browserDataRepository.clearSearchHistory()
            val viewModel = BrowserViewModel(app, app.container)

            viewModel.navigateFromOmnibox("material 3 search bar")
            viewModel.navigateFromOmnibox("https://kotlinlang.org")

            // Incognito search must never persist
            viewModel.openIncognitoTab()
            viewModel.navigateFromOmnibox("secret incognito query")

            var entries = emptyList<SearchHistoryEntity>()
            repeat(40) {
                shadowOf(Looper.getMainLooper()).idle()
                entries = app.container.browserDataRepository.getAllSearchHistoryOnce()
                if (entries.isNotEmpty()) return@repeat
                kotlinx.coroutines.delay(25)
            }
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(1, entries.size)
            assertEquals("material 3 search bar", entries.first().query)
        }

    @Test
    fun `bookmarks history screen displays search history tab with individual delete and clear all`() {
        val items = listOf(
            SearchHistoryEntity(
                id = 10L,
                query = "compose lazycolumn",
                searchEngine = "Google",
                searchUrl = "https://www.google.com/search?q=compose+lazycolumn",
                searchedAt = 2_000_000L
            ),
            SearchHistoryEntity(
                id = 11L,
                query = "room dao flow",
                searchEngine = "Brave",
                searchUrl = "https://search.brave.com/search?q=room+dao+flow",
                searchedAt = 1_000_000L
            )
        )

        var selectedSearch: SearchHistoryEntity? = null
        var deletedId = -1L
        var clearedAll = false

        composeRule.setContent {
            ClearPassTheme(themeMode = ThemeMode.LIGHT) {
                BookmarksHistoryScreen(
                    bookmarks = emptyList(),
                    history = emptyList(),
                    searchHistory = items,
                    initialTab = 2,
                    onBack = {},
                    onOpenUrl = {},
                    onAddBookmark = { _, _, _ -> },
                    onUpdateBookmark = { _, _, _, _ -> },
                    onToggleBookmarkPin = {},
                    onDeleteBookmark = {},
                    onDeleteHistoryItem = {},
                    onClearHistory = {},
                    onRunSearchQuery = { selectedSearch = it },
                    onDeleteSearchHistoryItem = { deletedId = it },
                    onClearSearchHistory = { clearedAll = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("compose lazycolumn").assertIsDisplayed()
        composeRule.onNodeWithText("room dao flow").assertIsDisplayed()

        composeRule.onNodeWithTag("search_history_item_10").performClick()
        assertEquals(10L, selectedSearch?.id)

        composeRule.onNodeWithTag("search_history_delete_11").performClick()
        assertEquals(11L, deletedId)

        composeRule.onNodeWithTag("search_history_clear_all_button").performClick()
        assertTrue(clearedAll)
    }
}
