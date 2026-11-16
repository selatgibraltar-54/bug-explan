package com.browser.client

import com.browser.client.data.preferences.SearchEngine
import com.browser.client.engine.DomainUtils
import com.browser.client.ui.BrowserViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainUtilsTest {

    @Test
    fun `extractRootDomain normalizes subdomains and multi-part TLDs`() {
        assertEquals("example.com", DomainUtils.extractRootDomain("https://www.sub.example.com/path"))
        assertEquals("bbc.co.uk", DomainUtils.extractRootDomain("https://news.bbc.co.uk/world"))
        assertEquals("cloudflare.com", DomainUtils.extractRootDomain("dash.cloudflare.com"))
    }

    @Test
    fun `resolveInputToUrl detects domains vs search queries across Google Bing and Brave`() {
        assertEquals(
            "https://example.org",
            DomainUtils.resolveInputToUrl("example.org", SearchEngine.GOOGLE)
        )
        assertEquals(
            "https://www.google.com/search?q=kotlin+compose",
            DomainUtils.resolveInputToUrl("kotlin compose", SearchEngine.GOOGLE)
        )
        assertEquals(
            "https://www.bing.com/search?q=android",
            DomainUtils.resolveInputToUrl("android", SearchEngine.BING)
        )
        assertEquals(
            "https://search.brave.com/search?q=privacy",
            DomainUtils.resolveInputToUrl("privacy", SearchEngine.BRAVE)
        )
        assertEquals(
            "http://localhost:8080",
            DomainUtils.resolveInputToUrl("localhost:8080", SearchEngine.BRAVE)
        )
    }

    @Test
    fun `maskTokenPreview masks long tokens cleanly`() {
        val masked = DomainUtils.maskTokenPreview("abc1234567890xyz987654")
        assertEquals("abc1234567•••987654", masked)
    }

    @Test
    fun `max tabs limit is 30`() {
        assertEquals(30, BrowserViewModel.MAX_TABS)
    }
}
