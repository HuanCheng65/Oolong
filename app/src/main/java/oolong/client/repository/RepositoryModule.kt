package oolong.client.repository

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import oolong.client.arch.ApplicationScope
import oolong.client.arch.query.QueryClient
import oolong.client.components.web.WebSessions
import oolong.client.dataStore
import oolong.client.utils.EmoticonManager
import oolong.client.utils.appPreferences
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindBlockRuleSource(source: DatabaseBlockRuleSource): BlockRuleSource

    @Binds
    abstract fun bindHistoryOwner(owner: AccountHistoryOwner): HistoryOwner

    @Binds
    abstract fun bindThreadHistory(history: DatabaseThreadHistory): ThreadHistory

    @Binds
    abstract fun bindForumHistory(history: DatabaseForumHistory): ForumHistory

    @Binds
    abstract fun bindForumSortStore(store: DataStoreForumSortStore): ForumSortStore

    @Binds
    abstract fun bindOwnProfileCache(cache: AccountOwnProfileCache): OwnProfileCache

    companion object {
        @Provides
        fun provideWebSessions(): WebSessions = WebSessions.instance

        @Provides
        fun provideVideoThreadFilter(@ApplicationContext context: Context): VideoThreadFilter =
            VideoThreadFilter { context.appPreferences.blockVideo }

        @Provides
        fun provideSubPostSortStore(@ApplicationContext context: Context): SubPostSortStore = object : SubPostSortStore {
            override var sort: SubPostSort
                get() = SubPostSort.of(context.appPreferences.subPostSort)
                set(value) {
                    context.appPreferences.subPostSort = value.value
                }
            override val changes: Flow<SubPostSort> = context.dataStore.data
                .map { SubPostSort.of(it[stringPreferencesKey("subPostSort")]) }
                .distinctUntilChanged()
        }

        @Provides
        fun provideConcernFeedStore(@ApplicationContext context: Context): ConcernFeedStore = object : ConcernFeedStore {
            override var lastRequestUnix: Long
                get() = context.appPreferences.userLikeLastRequestUnix
                set(value) {
                    context.appPreferences.userLikeLastRequestUnix = value
                }
        }

        @Provides
        fun provideFeedAutoRefresh(@ApplicationContext context: Context): FeedAutoRefresh =
            FeedAutoRefresh { context.appPreferences.feedAutoRefresh }

        @Provides
        @Singleton
        fun provideFeedSnapshots(@ApplicationContext context: Context, accounts: AccountRepository): FeedSnapshots =
            FileFeedSnapshots(File(context.cacheDir, "feeds"), uid = { accounts.current.value?.uid })

        @Provides
        @Singleton
        fun provideRecentResponses(@ApplicationContext context: Context): RecentResponses =
            FileRecentResponses(RecentResponses.dir(context))

        @Provides
        fun providePosterName(accounts: AccountRepository): PosterName = PosterName { accounts.current.value?.nameShow }

        /** The app's cache, emptied when the account changes, since what it holds is as that account sees it. */
        @Provides
        @Singleton
        fun provideQueryClient(@ApplicationScope scope: CoroutineScope, accounts: AccountRepository): QueryClient =
            QueryClient(scope).also { client ->
                accounts.current.map { it?.uid }.distinctUntilChanged().drop(1)
                    .onEach { client.clear() }
                    .launchIn(scope)
            }

        @Provides
        fun provideEmoticonRegistry(): EmoticonRegistry = EmoticonRegistry(EmoticonManager::registerEmoticon)
    }
}
