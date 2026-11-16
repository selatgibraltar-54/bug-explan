package com.browser.client.engine

import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.max

enum class ReaderBlockType {
    HEADING,
    PARAGRAPH,
    QUOTE,
    LIST_ITEM,
    CODE
}

data class ReaderBlock(
    val type: ReaderBlockType,
    val text: String,
    val level: Int = 2
)

data class ReaderArticle(
    val title: String,
    val byline: String,
    val siteName: String,
    val readingTimeMinutes: Int,
    val blocks: List<ReaderBlock>
)

object ReaderModeExtractor {

    fun parseExtractedJson(rawEvalResult: String?, fallbackUrl: String): ReaderArticle {
        val fallbackHost = DomainUtils.extractHost(fallbackUrl).ifBlank { "Web Page" }
        if (rawEvalResult.isNullOrBlank() || rawEvalResult == "null") {
            return ReaderArticle(
                title = fallbackHost,
                byline = "",
                siteName = fallbackHost,
                readingTimeMinutes = 1,
                blocks = emptyList()
            )
        }

        return runCatching {
            val unwrapped = when (val token = JSONTokener(rawEvalResult).nextValue()) {
                is String -> JSONObject(token)
                is JSONObject -> token
                else -> JSONObject(rawEvalResult)
            }
            val title = unwrapped.optString("title").trim().ifBlank { fallbackHost }
            val byline = unwrapped.optString("byline").trim()
            val siteName = unwrapped.optString("siteName").trim().ifBlank { fallbackHost }
            val jsonBlocks = unwrapped.optJSONArray("blocks")

            val blocks = buildList {
                if (jsonBlocks != null) {
                    for (i in 0 until jsonBlocks.length()) {
                        val obj = jsonBlocks.optJSONObject(i) ?: continue
                        val text = obj.optString("text").trim()
                        if (text.isBlank()) continue
                        val type = when (obj.optString("type").uppercase()) {
                            "HEADING" -> ReaderBlockType.HEADING
                            "QUOTE" -> ReaderBlockType.QUOTE
                            "LIST_ITEM" -> ReaderBlockType.LIST_ITEM
                            "CODE" -> ReaderBlockType.CODE
                            else -> ReaderBlockType.PARAGRAPH
                        }
                        val level = obj.optInt("level", 2).coerceIn(1, 4)
                        add(ReaderBlock(type = type, text = text, level = level))
                    }
                }
            }

            val wordCount = blocks.sumOf { block ->
                block.text.split(Regex("\\s+")).count { it.isNotBlank() }
            }
            val minutes = max(1, (wordCount + 180) / 200)

            ReaderArticle(
                title = title,
                byline = byline,
                siteName = siteName,
                readingTimeMinutes = minutes,
                blocks = blocks
            )
        }.getOrElse {
            ReaderArticle(
                title = fallbackHost,
                byline = "",
                siteName = fallbackHost,
                readingTimeMinutes = 1,
                blocks = emptyList()
            )
        }
    }

    /**
     * Non-destructive Readability-inspired DOM extractor.
     * Clones candidate content nodes, strips ads, navigation menus, sidebars, headers,
     * footers, cookie modals, and styling, returning structured core blocks as JSON.
     */
    const val EXTRACT_ARTICLE_JS = """
        (function() {
            try {
                var cleanText = function(s) {
                    return (s || '').replace(/\s+/g, ' ').trim();
                };

                var titleEl = document.querySelector('article h1, main h1, h1');
                var ogTitle = document.querySelector('meta[property="og:title"]');
                var title = cleanText(titleEl ? titleEl.innerText : '') ||
                    cleanText(ogTitle ? ogTitle.getAttribute('content') : '') ||
                    cleanText(document.title || '');

                var ogSite = document.querySelector('meta[property="og:site_name"]');
                var siteName = cleanText(ogSite ? ogSite.getAttribute('content') : '') ||
                    (window.location ? window.location.hostname.replace(/^www\./, '') : '');

                var authorMeta = document.querySelector('meta[name="author"], meta[property="article:author"]');
                var authorEl = document.querySelector('[rel="author"], .byline, .author, [class*="byline"], [class*="author-name"]');
                var byline = cleanText(authorMeta ? authorMeta.getAttribute('content') : '') ||
                    cleanText(authorEl ? authorEl.innerText : '');
                if (byline.length > 90) byline = byline.slice(0, 90);

                var junkSelector = [
                    'script', 'style', 'noscript', 'template', 'iframe', 'svg', 'canvas',
                    'nav', 'header', 'footer', 'aside', 'form', 'button',
                    '[role="navigation"]', '[role="banner"]', '[role="contentinfo"]',
                    '[role="complementary"]', '[role="search"]', '[aria-hidden="true"]',
                    '.nav', '.navbar', '.menu', '.sidebar', '.side-bar', '.toc',
                    '.ad', '.ads', '.advert', '.advertisement', '.sponsored', '.promo',
                    '.banner', '.cookie', '.cookies', '.consent', '.popup', '.modal',
                    '.newsletter', '.subscribe', '.social', '.share', '.comments',
                    '.comment-section', '.related', '.recommended', '[class*="ad-"]',
                    '[id*="ad-"]', '[class*="sidebar"]', '[class*="cookie"]',
                    '[class*="newsletter"]', '[class*="social"]', '[class*="menu"]'
                ].join(',');

                var isLikelyJunk = function(el) {
                    if (!el || !el.tagName) return true;
                    var tag = el.tagName.toLowerCase();
                    if (tag === 'nav' || tag === 'header' || tag === 'footer' || tag === 'aside' || tag === 'form') return true;
                    var cls = ((el.className || '') + ' ' + (el.id || '')).toLowerCase();
                    if (/(^|[\s_-])(nav|navbar|menu|sidebar|footer|header|ad|ads|advert|promo|cookie|popup|modal|newsletter|share|social|comment|related|widget|breadcrumb)([\s_-]|$)/.test(cls)) {
                        return true;
                    }
                    return false;
                };

                var scoreNode = function(node) {
                    if (!node || isLikelyJunk(node)) return -1;
                    var clone = node.cloneNode(true);
                    clone.querySelectorAll(junkSelector).forEach(function(n) { n.remove(); });
                    var paragraphs = clone.querySelectorAll('p');
                    var textLen = cleanText(clone.innerText || clone.textContent || '').length;
                    if (textLen < 120) return -1;
                    var linkLen = 0;
                    clone.querySelectorAll('a').forEach(function(a) {
                        linkLen += cleanText(a.innerText || '').length;
                    });
                    var linkDensity = textLen > 0 ? (linkLen / textLen) : 1;
                    if (linkDensity > 0.55) return -1;
                    return (textLen * (1 - linkDensity)) + (paragraphs.length * 60);
                };

                var bestNode = null;
                var bestScore = -1;
                var candidates = document.querySelectorAll('article, main, [role="main"], [itemprop="articleBody"], section, div');
                for (var i = 0; i < candidates.length && i < 250; i++) {
                    var cand = candidates[i];
                    var s = scoreNode(cand);
                    var tag = cand.tagName.toLowerCase();
                    if (tag === 'article' || tag === 'main' || cand.getAttribute('role') === 'main') {
                        s *= 1.35;
                    }
                    if (s > bestScore) {
                        bestScore = s;
                        bestNode = cand;
                    }
                }

                if (!bestNode) {
                    bestNode = document.body;
                }

                var work = bestNode.cloneNode(true);
                work.querySelectorAll(junkSelector).forEach(function(n) { n.remove(); });

                var blocks = [];
                var seen = {};
                var pushBlock = function(type, text, level) {
                    var t = cleanText(text);
                    if (!t || t.length < 4) return;
                    if (t === title && blocks.length === 0) return;
                    var key = type + ':' + t;
                    if (seen[key]) return;
                    seen[key] = true;
                    blocks.push({ type: type, text: t, level: level || 2 });
                };

                var elements = work.querySelectorAll('h1, h2, h3, h4, p, blockquote, li, pre');
                for (var j = 0; j < elements.length; j++) {
                    var el = elements[j];
                    if (el.closest('blockquote') && el.tagName.toLowerCase() !== 'blockquote') continue;
                    if (el.closest('pre') && el.tagName.toLowerCase() !== 'pre') continue;
                    var tName = el.tagName.toLowerCase();
                    var content = el.innerText || el.textContent || '';
                    if (tName === 'h1' || tName === 'h2') {
                        pushBlock('HEADING', content, 2);
                    } else if (tName === 'h3' || tName === 'h4') {
                        pushBlock('HEADING', content, 3);
                    } else if (tName === 'blockquote') {
                        pushBlock('QUOTE', content, 2);
                    } else if (tName === 'li') {
                        if (cleanText(content).length >= 15) {
                            pushBlock('LIST_ITEM', content, 2);
                        }
                    } else if (tName === 'pre') {
                        pushBlock('CODE', (el.textContent || '').trim(), 2);
                    } else if (tName === 'p') {
                        if (cleanText(content).length >= 20) {
                            pushBlock('PARAGRAPH', content, 2);
                        }
                    }
                }

                if (blocks.length === 0) {
                    var rawFallback = cleanText(work.innerText || work.textContent || '');
                    if (rawFallback.length > 40) {
                        var parts = rawFallback.split(/(?<=[.!?])\s+/);
                        var buf = '';
                        for (var k = 0; k < parts.length; k++) {
                            buf += (buf ? ' ' : '') + parts[k];
                            if (buf.length >= 220) {
                                pushBlock('PARAGRAPH', buf, 2);
                                buf = '';
                            }
                        }
                        if (buf) pushBlock('PARAGRAPH', buf, 2);
                    }
                }

                return JSON.stringify({
                    title: title,
                    byline: byline,
                    siteName: siteName,
                    blocks: blocks.slice(0, 400)
                });
            } catch (err) {
                return JSON.stringify({
                    title: document.title || '',
                    byline: '',
                    siteName: '',
                    blocks: []
                });
            }
        })();
    """
}
