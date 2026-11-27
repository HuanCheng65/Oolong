package oolong.client.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val accountPrefs = context.getSharedPreferences("accountData", Context.MODE_PRIVATE)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(
                LegacyDatabaseImport(
                    legacyFile = context.getDatabasePath(LegacyDatabaseImport.LEGACY_NAME),
                    accountPrefs = accountPrefs,
                )
            )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, migration4To5(accountPrefs), migration5To6(accountPrefs))
            .build()
    }

    @Provides
    fun provideAccountDao(database: AppDatabase) = database.accountDao()

    @Provides
    fun provideDraftDao(database: AppDatabase) = database.draftDao()

    @Provides
    fun provideHistoryDao(database: AppDatabase) = database.historyDao()

    @Provides
    fun provideBlockDao(database: AppDatabase) = database.blockDao()

    @Provides
    fun provideTopForumDao(database: AppDatabase) = database.topForumDao()

    @Provides
    fun provideSearchHistoryDao(database: AppDatabase) = database.searchHistoryDao()

    @Provides
    fun provideOfflineThreadDao(database: AppDatabase) = database.offlineThreadDao()
}
