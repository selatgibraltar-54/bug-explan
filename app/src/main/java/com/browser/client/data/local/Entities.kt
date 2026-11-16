package com.browser.client.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "verified_domains")
data class ClearanceEntity(
    @PrimaryKey val domain: String,
    val originUrl: String,
    val cfClearanceToken: String,
    val cfBmToken: String? = null,
    val extraCfCookies: String = "",
    val lockedUserAgent: String,
    val isDesktopMode: Boolean = false,
    val verifiedAt: Long,
    val lastUsedAt: Long,
    val reuseCount: Int = 0,
    val challengeCount: Int = 1
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val domain: String,
    val isPinned: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val domain: String,
    val visitedAt: Long = System.currentTimeMillis(),
    val wasCfProtected: Boolean = false
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val url: String,
    val domain: String,
    val mimeType: String,
    val contentLength: Long = -1L,
    val downloadManagerId: Long = -1L,
    val downloadedAt: Long = System.currentTimeMillis(),
    val localFilePath: String = "",
    val bytesDownloaded: Long = 0L,
    val status: String = "COMPLETED",
    val errorMessage: String = ""
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val searchEngine: String,
    val searchUrl: String,
    val searchedAt: Long = System.currentTimeMillis()
)
