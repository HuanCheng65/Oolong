package oolong.client.data

import android.content.Context
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
import oolong.client.data.account.AccountOwnProfileCache
import oolong.client.data.account.AccountRepository
import oolong.client.data.account.OwnProfileCache
import oolong.client.data.block.BlockRuleSource
import oolong.client.data.block.DatabaseBlockRuleSource
import oolong.client.data.emoticon.EmoticonManager
import oolong.client.data.explore.ConcernFeedStore
import oolong.client.data.explore.FeedAutoRefresh
import oolong.client.data.explore.FeedSnapshots
import oolong.client.data.explore.FileFeedSnapshots
import oolong.client.data.forum.DataStoreForumSortStore
import oolong.client.data.forum.DatabaseForumHistory
import oolong.client.data.forum.ForumHistory
import oolong.client.data.forum.ForumSortStore
import oolong.client.data.forum.VideoThreadFilter
import oolong.client.data.history.AccountHistoryOwner
import oolong.client.data.history.DatabaseThreadHistory
import oolong.client.data.history.HistoryOwner
import oolong.client.data.history.ThreadHistory
import oolong.client.data.post.PosterName
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.data.thread.EmoticonRegistry
import oolong.client.data.thread.SubPostSort
import oolong.client.data.thread.SubPostSortStore
import oolong.client.data.web.WebSessions
import oolong.client.diagnostics.FileRecentResponses
import oolong.client.diagnostics.RecentResponses
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
        fun provideVideoThreadFilter(settings: Settings): VideoThreadFilter =
            VideoThreadFilter { settings[SettingKeys.BlockVideo] }

        @Provides
        fun provideSubPostSortStore(settings: Settings): SubPostSortStore = object : SubPostSortStore {
            override var sort: SubPostSort
                get() = SubPostSort.of(settings[SettingKeys.SubPostSort])
                set(value) {
                    settings[SettingKeys.SubPostSort] = value.value
                }
            override val changes: Flow<SubPostSort> = settings.flow(SettingKeys.SubPostSort).map(SubPostSort::of).distinctUntilChanged()
        }

        @Provides
        fun provideConcernFeedStore(settings: Settings): ConcernFeedStore = object : ConcernFeedStore {
            override var lastRequestUnix: Long
                get() = settings[SettingKeys.UserLikeLastRequestUnix]
                set(value) {
                    settings[SettingKeys.UserLikeLastRequestUnix] = value
                }
        }

        @Provides
        fun provideFeedAutoRefresh(settings: Settings): FeedAutoRefresh =
            FeedAutoRefresh { settings[SettingKeys.FeedAutoRefresh] }

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
