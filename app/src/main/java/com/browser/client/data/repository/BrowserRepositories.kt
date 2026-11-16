package com.browser.client.data.repository

import com.browser.client.data.local.BookmarkDao
import com.browser.client.data.local.BookmarkEntity
import com.browser.client.data.local.ClearanceDao
import com.browser.client.data.local.ClearanceEntity
import com.browser.client.data.local.DownloadDao
import com.browser.client.data.local.DownloadEntity
import com.browser.client.data.local.HistoryDao
import com.browser.client.data.local.HistoryEntity
import com.browser.client.data.local.SearchHistoryDao
import com.browser.client.data.local.SearchHistoryEntity
import com.browser.client.engine.DomainUtils
import kotlinx.coroutines.flow.Flow

class ClearanceRepository(private val clearanceDao: ClearanceDao) {
    val allClearances: Flow<List<ClearanceEntity>> = clearanceDao.observeAllClearances()

    suspend fun getAllOnce(): List<ClearanceEntity> = clearanceDao.getAllClearancesOnce()

    suspend fun getByDomain(domain: String): ClearanceEntity? = clearanceDao.getByDomain(domain)

    suspend fun upsert(entity: ClearanceEntity) = clearanceDao.upsert(entity)

    suspend fun markReused(domain: String) =
        clearanceDao.markReused(domain, System.currentTimeMillis())

    suspend fun deleteByDomain(domain: String) = clearanceDao.deleteByDomain(domain)

    suspend fun clearAll() = clearanceDao.clearAll()
}

class BrowserDataRepository(
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
    private val downloadDao: DownloadDao,
    private val searchHistoryDao: SearchHistoryDao
) {
    val allBookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.observeAllBookmarks()
    val pinnedBookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.observePinnedBookmarks()
    val recentHistory: Flow<List<HistoryEntity>> = historyDao.observeRecentHistory()
    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.observeAllDownloads()
    val searchHistory: Flow<List<SearchHistoryEntity>> = searchHistoryDao.observeSearchHistory()

    suspend fun ensureDefaultBookmarksSeeded() {
        if (bookmarkDao.countBookmarks() == 0) {
            val defaults = listOf(
                BookmarkEntity(
                    title = "YouTube",
                    url = "https://www.youtube.com",
                    domain = "youtube.com",
                    isPinned = true
                ),
                BookmarkEntity(
                    title = "Instagram",
                    url = "https://www.instagram.com",
                    domain = "instagram.com",
                    isPinned = true
                ),
                BookmarkEntity(
                    title = "Facebook",
                    url = "https://www.facebook.com",
                    domain = "facebook.com",
                    isPinned = true
                )
            )
            defaults.forEach { bookmarkDao.insert(it) }
        }
    }

    suspend fun addBookmark(title: String, url: String, isPinned: Boolean = true) {
        val cleanTitle = title.ifBlank { DomainUtils.extractHost(url).ifBlank { url } }
        val domain = DomainUtils.extractHost(url)
        val existing = bookmarkDao.getByUrl(url)
        if (existing != null) {
            bookmarkDao.update(
                existing.copy(
                    title = cleanTitle,
                    domain = domain,
                    isPinned = isPinned || existing.isPinned
                )
            )
        } else {
            bookmarkDao.insert(
                BookmarkEntity(
                    title = cleanTitle,
                    url = url,
                    domain = domain,
                    isPinned = isPinned,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun updateBookmark(id: Long, title: String, url: String, isPinned: Boolean) {
        val existing = bookmarkDao.getById(id) ?: return
        val cleanTitle = title.ifBlank { DomainUtils.extractHost(url).ifBlank { url } }
        val domain = DomainUtils.extractHost(url)
        bookmarkDao.update(
            existing.copy(
                title = cleanTitle,
                url = url,
                domain = domain,
                isPinned = isPinned
            )
        )
    }

    suspend fun toggleBookmarkPinned(id: Long) {
        val existing = bookmarkDao.getById(id) ?: return
        bookmarkDao.update(existing.copy(isPinned = !existing.isPinned))
    }

    suspend fun getAllBookmarksOnce(): List<BookmarkEntity> = bookmarkDao.getAllOnce()

    suspend fun removeBookmarkByUrl(url: String) = bookmarkDao.deleteByUrl(url)

    suspend fun removeBookmarkById(id: Long) = bookmarkDao.deleteById(id)

    suspend fun recordHistory(title: String, url: String, wasCfProtected: Boolean) {
        if (url.isBlank() || url.startsWith("about:")) return
        val domain = DomainUtils.extractHost(url)
        historyDao.insert(
            HistoryEntity(
                title = title.ifBlank { domain.ifBlank { url } },
                url = url,
                domain = domain,
                wasCfProtected = wasCfProtected
            )
        )
    }

    suspend fun deleteHistoryItem(id: Long) = historyDao.deleteById(id)

    suspend fun clearHistory() = historyDao.clearAll()

    suspend fun recordSearchQuery(
        query: String,
        searchEngine: String,
        searchUrl: String,
        searchedAt: Long = System.currentTimeMillis()
    ) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return
        val existing = searchHistoryDao.findByNormalizedQuery(cleanQuery)
        if (existing != null) {
            searchHistoryDao.update(
                existing.copy(
                    query = cleanQuery,
                    searchEngine = searchEngine,
                    searchUrl = searchUrl,
                    searchedAt = searchedAt
                )
            )
        } else {
            searchHistoryDao.insert(
                SearchHistoryEntity(
                    query = cleanQuery,
                    searchEngine = searchEngine,
                    searchUrl = searchUrl,
                    searchedAt = searchedAt
                )
            )
        }
    }

    suspend fun getAllSearchHistoryOnce(): List<SearchHistoryEntity> =
        searchHistoryDao.getAllOnce()

    suspend fun deleteSearchHistoryItem(id: Long) = searchHistoryDao.deleteById(id)

    suspend fun clearSearchHistory() = searchHistoryDao.clearAll()

    suspend fun createDownloadRecord(
        fileName: String,
        url: String,
        mimeType: String,
        contentLength: Long,
        localFilePath: String,
        status: String
    ): Long {
        return downloadDao.insert(
            DownloadEntity(
                fileName = fileName.ifBlank { "downloaded_file" },
                url = url,
                domain = DomainUtils.extractHost(url),
                mimeType = mimeType.ifBlank { "application/octet-stream" },
                contentLength = contentLength,
                downloadManagerId = -1L,
                localFilePath = localFilePath,
                bytesDownloaded = 0L,
                status = status,
                errorMessage = ""
            )
        )
    }

    suspend fun updateDownloadState(
        id: Long,
        fileName: String,
        mimeType: String,
        bytesDownloaded: Long,
        contentLength: Long,
        status: String,
        localFilePath: String,
        errorMessage: String = ""
    ) {
        downloadDao.updateState(
            id = id,
            fileName = fileName,
            mimeType = mimeType,
            bytesDownloaded = bytesDownloaded,
            contentLength = contentLength,
            status = status,
            localFilePath = localFilePath,
            errorMessage = errorMessage
        )
    }

    suspend fun getDownloadById(id: Long): DownloadEntity? = downloadDao.getById(id)

    suspend fun getAllDownloadsOnce(): List<DownloadEntity> = downloadDao.getAllOnce()

    suspend fun deleteDownloadItem(id: Long) = downloadDao.deleteById(id)

    suspend fun clearDownloadsHistory() = downloadDao.clearAll()
}
