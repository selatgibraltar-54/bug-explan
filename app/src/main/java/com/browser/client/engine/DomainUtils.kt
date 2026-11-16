package com.browser.client.engine

import com.browser.client.data.preferences.SearchEngine
import java.net.URI
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DomainUtils {

    private val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")

    private val multiPartSuffixes = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk",
        "com.au", "net.au", "org.au",
        "co.jp", "ne.jp", "or.jp",
        "co.id", "or.id", "ac.id", "go.id", "web.id", "my.id",
        "com.br", "com.mx", "com.sg", "com.my", "co.kr", "co.in", "com.tr"
    )

    fun extractHost(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            val normalized = if (url.contains("://")) url else "https://$url"
            URI(normalized).host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Computes a registrable root domain (eTLD+1 heuristic) so subdomains share
     * Cloudflare clearance cookies seamlessly when appropriate.
     */
    fun extractRootDomain(urlOrHost: String?): String {
        val host = if (urlOrHost.orEmpty().contains("://") || urlOrHost.orEmpty().contains("/")) {
            extractHost(urlOrHost)
        } else {
            urlOrHost.orEmpty().lowercase(Locale.ROOT).removePrefix("www.").trim()
        }
        if (host.isBlank()) return ""
        if (isIpAddress(host)) return host

        val parts = host.split(".").filter { it.isNotBlank() }
        if (parts.size <= 2) return host

        val lastTwo = "${parts[parts.size - 2]}.${parts.last()}"
        return if (lastTwo in multiPartSuffixes && parts.size >= 3) {
            "${parts[parts.size - 3]}.$lastTwo"
        } else {
            lastTwo
        }
    }

    fun extractOrigin(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: "https"
            val host = uri.host ?: return ""
            val port = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
            "$scheme://$host$port"
        } catch (_: Exception) {
            ""
        }
    }

    fun resolveInputToUrl(input: String, searchEngine: SearchEngine): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        if (looksLikeDirectUrlOrDomain(trimmed)) {
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("about:", ignoreCase = true)
            ) {
                return trimmed
            }
            val hostCandidate = trimmed.substringBefore("/").substringBefore(":")
            val scheme = if (hostCandidate.equals("localhost", ignoreCase = true) ||
                isIpAddress(hostCandidate)
            ) {
                "http://"
            } else {
                "https://"
            }
            return "$scheme$trimmed"
        }

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        return searchEngine.searchUrlTemplate.format(encoded)
    }

    fun extractSearchQueryIfSearch(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        return if (looksLikeDirectUrlOrDomain(trimmed)) null else trimmed
    }

    fun extractSearchQueryFromUrl(url: String?): Pair<String, String>? {
        if (url.isNullOrBlank()) return null
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
            val path = uri.path.orEmpty().lowercase(Locale.ROOT)
            val engineLabel = when {
                host.endsWith("google.com") && path.startsWith("/search") -> SearchEngine.GOOGLE.label
                host.endsWith("bing.com") && path.startsWith("/search") -> SearchEngine.BING.label
                host == "search.brave.com" && path.startsWith("/search") -> SearchEngine.BRAVE.label
                host.endsWith("duckduckgo.com") -> "DuckDuckGo"
                else -> return null
            }
            val rawQuery = uri.rawQuery ?: return null
            for (pair in rawQuery.split("&")) {
                val eqIdx = pair.indexOf('=')
                if (eqIdx <= 0) continue
                val key = pair.substring(0, eqIdx)
                if (key == "q" || key == "p") {
                    val decoded = java.net.URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8").trim()
                    if (decoded.isNotEmpty()) {
                        return decoded to engineLabel
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun looksLikeDirectUrlOrDomain(trimmed: String): Boolean {
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true)
        ) {
            return true
        }
        val hasSpace = trimmed.contains(" ")
        val hostCandidate = trimmed.substringBefore("/").substringBefore(":")
        return !hasSpace && (
            (trimmed.contains(".") && !trimmed.startsWith(".") && !trimmed.endsWith(".")) ||
                hostCandidate.equals("localhost", ignoreCase = true) ||
                isIpAddress(hostCandidate)
            )
    }

    fun maskTokenPreview(token: String): String {
        if (token.isBlank()) return "—"
        return if (token.length <= 16) {
            token
        } else {
            "${token.take(10)}•••${token.takeLast(6)}"
        }
    }

    fun formatTimestamp(millis: Long): String {
        if (millis <= 0L) return "Never"
        val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        return formatter.format(Date(millis))
    }

    private fun isIpAddress(host: String): Boolean {
        if (!ipv4Regex.matches(host)) return false
        return host.split(".").all { part ->
            val num = part.toIntOrNull() ?: return@all false
            num in 0..255
        }
    }
}
