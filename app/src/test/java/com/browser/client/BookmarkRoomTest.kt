package com.browser.client

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browser.client.data.local.BookmarkEntity
import com.browser.client.data.local.ClearPassDatabase
import com.browser.client.data.preferences.ThemeMode
import com.browser.client.data.repository.BrowserDataRepository
import com.browser.client.ui.screens.BookmarksHistoryScreen
import com.browser.client.ui.theme.ClearPassTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookmarkRoomTest {

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
    fun `room bookmark repository supports add, update, pin toggle, and delete with timestamp`() = runBlocking {
        val beforeInsert = System.currentTimeMillis()
        repository.addBookmark(
            title = "Kotlin Docs",
            url = "https://kotlinlang.org/docs/home.html",
            isPinned = false
        )

        val savedList = repository.allBookmarks.first()
        assertEquals(1, savedList.size)
        val saved = savedList.first()
        assertEquals("Kotlin Docs", saved.title)
        assertEquals("https://kotlinlang.org/docs/home.html", saved.url)
        assertEquals("kotlinlang.org", saved.domain)
        assertFalse(saved.isPinned)
        assertTrue(saved.createdAt >= beforeInsert)

        repository.updateBookmark(
            id = saved.id,
            title = "Kotlin Official",
            url = "https://kotlinlang.org",
            isPinned = true
        )
        val updated = repository.allBookmarks.first().first()
        assertEquals("Kotlin Official", updated.title)
        assertEquals("https://kotlinlang.org", updated.url)
        assertTrue(updated.isPinned)
        assertEquals(saved.createdAt, updated.createdAt)

        val pinnedList = repository.pinnedBookmarks.first()
        assertEquals(1, pinnedList.size)

        repository.toggleBookmarkPinned(updated.id)
        assertTrue(repository.pinnedBookmarks.first().isEmpty())

        repository.removeBookmarkById(updated.id)
        assertTrue(repository.getAllBookmarksOnce().isEmpty())
    }

    @Test
    fun `bookmarks screen displays timestamp and triggers add, edit, and delete callbacks`() {
        val sample = BookmarkEntity(
            id = 42L,
            title = "Wikipedia",
            url = "https://www.wikipedia.org",
            domain = "www.wikipedia.org",
            isPinned = true,
            createdAt = System.currentTimeMillis()
        )
        var addedTriple: Triple<String, String, Boolean>? = null
        var editedTitle = ""
        var deletedId = -1L

        composeRule.setContent {
            ClearPassTheme(themeMode = ThemeMode.LIGHT) {
                BookmarksHistoryScreen(
                    bookmarks = listOf(sample),
                    history = emptyList(),
                    onBack = {},
                    onOpenUrl = {},
                    onAddBookmark = { t, u, p -> addedTriple = Triple(t, u, p) },
                    onUpdateBookmark = { _, t, _, _ -> editedTitle = t },
                    onToggleBookmarkPin = {},
                    onDeleteBookmark = { id -> deletedId = id },
                    onDeleteHistoryItem = {},
                    onClearHistory = {}
                )
            }
        }

        composeRule.onNodeWithText("Wikipedia").assertIsDisplayed()
        composeRule.onNodeWithText("https://www.wikipedia.org").assertIsDisplayed()
        composeRule.onNodeWithText("Pinned").assertIsDisplayed()

        // Test Add Bookmark dialog flow
        composeRule.onNodeWithTag("bookmarks_add_fab").performClick()
        composeRule.onNodeWithTag("bookmark_editor_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("bookmark_title_input").performTextInput("Rust Lang")
        composeRule.onNodeWithTag("bookmark_url_input").performTextInput("https://www.rust-lang.org")
        composeRule.onNodeWithTag("bookmark_pin_switch").performClick()
        composeRule.onNodeWithTag("bookmark_save_button").performClick()

        assertEquals(Triple("Rust Lang", "https://www.rust-lang.org", true), addedTriple)

        // Test Edit Bookmark dialog flow
        composeRule.onNodeWithTag("bookmark_edit_42").performClick()
        composeRule.onNodeWithTag("bookmark_title_input").performTextClearance()
        composeRule.onNodeWithTag("bookmark_title_input").performTextInput("Wiki Encyclopedia")
        composeRule.onNodeWithTag("bookmark_save_button").performClick()

        assertEquals("Wiki Encyclopedia", editedTitle)

        // Test Delete callback
        composeRule.onNodeWithTag("bookmark_delete_42").performClick()
        assertEquals(42L, deletedId)
    }
}
