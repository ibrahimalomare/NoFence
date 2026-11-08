package com.nofence.browser.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class StorageTypeConverters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(value) }.getOrDefault(DownloadStatus.FAILED)

    @TypeConverter
    fun fromProxyProtocol(protocol: ProxyProtocol): String = protocol.name

    @TypeConverter
    fun toProxyProtocol(value: String): ProxyProtocol =
        runCatching { ProxyProtocol.valueOf(value) }.getOrDefault(ProxyProtocol.SOCKS5)
}

@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        ProxyProfileEntity::class,
        TabStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(StorageTypeConverters::class)
abstract class NoFenceDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun proxyProfileDao(): ProxyProfileDao
    abstract fun tabStateDao(): TabStateDao

    companion object {
        @Volatile
        private var INSTANCE: NoFenceDatabase? = null

        fun getInstance(context: Context): NoFenceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoFenceDatabase::class.java,
                    "nofence_browser.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
