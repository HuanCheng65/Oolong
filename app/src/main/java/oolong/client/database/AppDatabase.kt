package oolong.client.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        DraftEntity::class,
        HistoryEntity::class,
        BlockEntity::class,
        TopForumEntity::class,
        SearchHistoryEntity::class,
        ForumSearchHistoryEntity::class,
        OfflineThreadEntity::class,
    ],
    version = 7,
    autoMigrations = [AutoMigration(from = 6, to = 7)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun draftDao(): DraftDao
    abstract fun historyDao(): HistoryDao
    abstract fun blockDao(): BlockDao
    abstract fun topForumDao(): TopForumDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun offlineThreadDao(): OfflineThreadDao

    companion object {
        const val NAME = "app.db"
    }
}
