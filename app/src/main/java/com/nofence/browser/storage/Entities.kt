package com.nofence.browser.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = true)]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folder: String = "General",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "history",
    indices = [Index(value = ["visitedAt"]), Index(value = ["url"])]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val sourceUrl: String,
    val mimeType: String,
    val destinationPath: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val routedVia: String,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ProxyProtocol {
    SOCKS5,
    HTTP,
    HTTPS_CONNECT
}

@Entity(tableName = "proxy_profiles")
data class ProxyProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: ProxyProtocol,
    val host: String,
    val port: Int,
    val hasAuth: Boolean = false,
    val remoteDns: Boolean = true,
    val isFallback: Boolean = false,
    val isSelected: Boolean = false,
    val lastLatencyMs: Long? = null,
    val lastVerifiedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_tabs")
data class TabStateEntity(
    @PrimaryKey val tabId: String,
    val title: String,
    val url: String,
    val isDesktopMode: Boolean = false,
    val isSelected: Boolean = false,
    val positionIndex: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
