package com.browser.client

import com.browser.client.engine.ReaderBlockType
import com.browser.client.engine.ReaderModeExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReaderModeExtractorTest {

    @Test
    fun `parses quoted JSON returned by WebView evaluateJavascript cleanly`() {
        val rawWebViewResult = """
            "{\"title\":\"Clean Reading Article\",\"byline\":\"Jane Doe\",\"siteName\":\"example.org\",\"blocks\":[{\"type\":\"HEADING\",\"text\":\"Introduction\",\"level\":2},{\"type\":\"PARAGRAPH\",\"text\":\"This is the core article paragraph stripped of ads and navigation menus.\",\"level\":2},{\"type\":\"QUOTE\",\"text\":\"Simplicity is the ultimate sophistication.\",\"level\":2},{\"type\":\"LIST_ITEM\",\"text\":\"Zero clutter or sidebars\",\"level\":2}]}"
        """.trimIndent()

        val article = ReaderModeExtractor.parseExtractedJson(rawWebViewResult, "https://example.org/post")
        assertEquals("Clean Reading Article", article.title)
        assertEquals("Jane Doe", article.byline)
        assertEquals("example.org", article.siteName)
        assertEquals(4, article.blocks.size)
        assertEquals(ReaderBlockType.HEADING, article.blocks[0].type)
        assertEquals(ReaderBlockType.PARAGRAPH, article.blocks[1].type)
        assertEquals(ReaderBlockType.QUOTE, article.blocks[2].type)
        assertEquals(ReaderBlockType.LIST_ITEM, article.blocks[3].type)
        assertTrue(article.readingTimeMinutes >= 1)
    }

    @Test
    fun `falls back gracefully when page returns null or empty payload`() {
        val article = ReaderModeExtractor.parseExtractedJson("null", "https://news.ycombinator.com/item?id=1")
        assertEquals("news.ycombinator.com", article.siteName)
        assertTrue(article.blocks.isEmpty())
    }
}
