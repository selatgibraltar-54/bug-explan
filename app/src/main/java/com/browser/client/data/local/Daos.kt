package com.browser.client.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClearanceDao {
    @Query("SELECT * FROM verified_domains ORDER BY lastUsedAt DESC")
    fun observeAllClearances(): Flow<List<ClearanceEntity>>

    @Query("SELECT * FROM verified_domains")
    suspend fun getAllClearancesOnce(): List<ClearanceEntity>

    @Query("SELECT * FROM verified_domains WHERE domain = :domain LIMIT 1")
    suspend fun getByDomain(domain: String): ClearanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ClearanceEntity)

    @Query("UPDATE verified_domains SET lastUsedAt = :timestamp, reuseCount = reuseCount + 1 WHERE domain = :domain")
    suspend fun markReused(domain: String, timestamp: Long)

    @Query("DELETE FROM verified_domains WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)

    @Query("DELETE FROM verified_domains")
    suspend fun clearAll()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY isPinned DESC, createdAt DESC")
    fun observeAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE isPinned = 1 ORDER BY createdAt ASC")
    fun observePinnedBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY isPinned DESC, createdAt DESC")
    suspend fun getAllOnce(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): BookmarkEntity?

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun countBookmarks(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun observeIsBookmarked(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 200")
    fun observeRecentHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    suspend fun getAllOnce(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Query(
        """
        UPDATE downloads 
        SET fileName = :fileName,
            mimeType = :mimeType,
            bytesDownloaded = :bytesDownloaded,
            contentLength = :contentLength,
            status = :status,
            localFilePath = :localFilePath,
            errorMessage = :errorMessage
        WHERE id = :id
        """
    )
    suspend fun updateState(
        id: Long,
        fileName: String,
        mimeType: String,
        bytesDownloaded: Long,
        contentLength: Long,
        status: String,
        localFilePath: String,
        errorMessage: String
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC, id DESC LIMIT 200")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC, id DESC")
    suspend fun getAllOnce(): List<SearchHistoryEntity>

    @Query("SELECT * FROM search_history WHERE LOWER(query) = LOWER(:query) LIMIT 1")
    suspend fun findByNormalizedQuery(query: String): SearchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SearchHistoryEntity): Long

    @Update
    suspend fun update(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}
