package com.nofence.browser.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks")
    suspend fun clearAll()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 500")
    fun observeRecent(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC LIMIT 100")
    fun search(query: String): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryEntity): Long

    @Delete
    suspend fun delete(item: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Delete
    suspend fun delete(download: DownloadEntity)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}

@Dao
interface ProxyProfileDao {
    @Query("SELECT * FROM proxy_profiles ORDER BY isSelected DESC, name ASC")
    fun observeAll(): Flow<List<ProxyProfileEntity>>

    @Query("SELECT * FROM proxy_profiles ORDER BY isSelected DESC, name ASC")
    suspend fun getAll(): List<ProxyProfileEntity>

    @Query("SELECT * FROM proxy_profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedProfile(): ProxyProfileEntity?

    @Query("SELECT * FROM proxy_profiles WHERE isSelected = 1 LIMIT 1")
    fun observeSelectedProfile(): Flow<ProxyProfileEntity?>

    @Query("SELECT * FROM proxy_profiles WHERE isFallback = 1 AND isSelected = 0 ORDER BY lastLatencyMs ASC")
    suspend fun getFallbackProfiles(): List<ProxyProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProxyProfileEntity): Long

    @Update
    suspend fun update(profile: ProxyProfileEntity)

    @Delete
    suspend fun delete(profile: ProxyProfileEntity)

    @Query("UPDATE proxy_profiles SET isSelected = CASE WHEN id = :profileId THEN 1 ELSE 0 END")
    suspend fun selectExclusive(profileId: Long)
}

@Dao
interface TabStateDao {
    @Query("SELECT * FROM saved_tabs ORDER BY positionIndex ASC")
    suspend fun getAllSavedTabs(): List<TabStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tabs: List<TabStateEntity>)

    @Query("DELETE FROM saved_tabs WHERE tabId = :tabId")
    suspend fun deleteById(tabId: String)

    @Query("DELETE FROM saved_tabs")
    suspend fun clearAll()
}
