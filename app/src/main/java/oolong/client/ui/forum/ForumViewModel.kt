package oolong.client.ui.forum

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.arch.Pager
import oolong.client.arch.PagerState
import oolong.client.arch.query.QueryClient
import oolong.client.arch.query.pager
import oolong.client.data.block.BlockMemo
import oolong.client.data.block.BlockPlace
import oolong.client.data.block.BlockRuleSource
import oolong.client.data.block.BlockRules
import oolong.client.data.block.BlockVerdict
import oolong.client.data.block.check
import oolong.client.data.forum.ForumCursor
import oolong.client.data.forum.ForumHistory
import oolong.client.data.forum.ForumRepository
import oolong.client.data.forum.ForumSortStore
import oolong.client.data.thread.ThreadActions
import oolong.client.data.thread.Threads
import oolong.client.model.ForumDetail
import oolong.client.model.ForumSort
import oolong.client.model.ForumTab
import oolong.client.model.ThreadSummary
import oolong.client.navigation.ForumKey
import oolong.core.network.identity.SessionProvider

/** A thread in a forum's list, and how the block rules block it, if they do. */
@Immutable
data class ForumThreadItem(val thread: ThreadSummary, val block: BlockVerdict? = null) {
    val blocked: Boolean get() = block != null
}

@Immutable
data class ForumState(
    val forumName: String,
    /** The forum, once the first page of a tab that lists it has loaded. */
    val forum: ForumDetail? = null,
    val selectedTab: ForumTab = ForumTab.Latest,
    /** The sort type of the latest threads. */
    val sortType: Int = ForumSort.REPLY_TIME,
    /** The class of good threads listed, 0 for all of them. */
    val goodClassId: Int = 0,
    val lists: ImmutableMap<ForumTab, PagerState<ForumThreadItem>> = persistentMapOf(),
    val loggedIn: Boolean = false,
    /** Whether following or signing in is on its way to the server. */
    val joining: Boolean = false,
    /** The tab whose list is being loaded for another order or class, shown dimmed till it comes. */
    val switching: ForumTab? = null,
) {
    val tabs: ImmutableList<ForumTab> get() = forum?.tabs ?: ForumTab.Fixed

    fun list(tab: ForumTab): PagerState<ForumThreadItem> = lists[tab] ?: PagerState()

    /** How the page's first load went, which shows instead of the page until the forum is known. */
    val firstLoad: LoadStatus get() = if (forum != null) LoadStatus.Idle else list(selectedTab).refresh
}

/**
 * A forum's page: the forum, and a list of threads for each of its tabs, each loaded the first
 * time its tab is shown.
 */
@HiltViewModel(assistedFactory = ForumViewModel.Factory::class)
class ForumViewModel @AssistedInject constructor(
    @Assisted key: ForumKey,
    private val repository: ForumRepository,
    private val client: QueryClient,
    private val threads: ThreadActions,
    blockRuleSource: BlockRuleSource,
    private val history: ForumHistory,
    private val sortStore: ForumSortStore,
    private val messages: MessageController,
    private val sessionProvider: SessionProvider,
) : ViewModel() {
    private val forumName: String = key.forumName

    private val forum = MutableStateFlow<ForumDetail?>(null)
    private var tbs: String? = null
    private val selection = MutableStateFlow(Selection())
    private val pagers = MutableStateFlow<Map<ForumTab, Pager<ForumCursor, ThreadSummary>>>(emptyMap())
    private val actions = Actions(viewModelScope)
    private val joining = MutableStateFlow(false)

    private data class Selection(
        val tab: ForumTab = ForumTab.Latest,
        val sortType: Int = ForumSort.REPLY_TIME,
        val goodClassId: Int = 0,
        /** The tab last switched to another order or class, until the page is reloaded. */
        val switched: ForumTab? = null,
    )

    /** Block flags for each tab's threads, which most state changes leave as they are. */
    private val blocked = mutableMapOf<ForumTab, BlockMemo<ThreadSummary, ForumThreadItem>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val listStates = pagers.flatMapLatest { pagers ->
        if (pagers.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(pagers.map { (tab, pager) -> pager.state.map { tab to it } }) { it.toMap() }
        }
    }

    val state: StateFlow<ForumState> =
        combine(forum, selection, listStates, blockRuleSource.rules, joining) { forum, selection, lists, rules, joining ->
            ForumState(
                forumName = forumName,
                forum = forum,
                selectedTab = selection.tab,
                sortType = selection.sortType,
                goodClassId = selection.goodClassId,
                lists = lists.mapValues { (tab, list) -> list.withItems(items(tab, list.items, rules)) }.toImmutableMap(),
                loggedIn = sessionProvider.currentSession() != null,
                joining = joining,
                switching = selection.switched?.takeIf { lists[it]?.refresh == LoadStatus.Loading },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ForumState(forumName))

    init {
        viewModelScope.launch {
            val sortType = runCatching { sortStore.sortType(forumName) }.getOrDefault(ForumSort.REPLY_TIME)
            selection.update { it.copy(sortType = sortType) }
            pagerOf(selection.value.tab)
        }
    }

    fun selectTab(tab: ForumTab) {
        selection.update { it.copy(tab = tab) }
        pagerOf(tab)
    }

    fun setSort(sortType: Int) {
        if (sortType == selection.value.sortType) return
        selection.update { it.copy(sortType = sortType, switched = ForumTab.Latest) }
        viewModelScope.launch { runCatching { sortStore.setSortType(forumName, sortType) } }
        // The list stays, dimmed, till the other order comes and takes its place
        pagers.value[ForumTab.Latest]?.refresh()
    }

    fun setGoodClass(classId: Int) {
        if (classId == selection.value.goodClassId) return
        selection.update { it.copy(goodClassId = classId, switched = ForumTab.Good) }
        pagers.value[ForumTab.Good]?.refresh()
    }

    /** Reloads the shown tab, and with it the forum when that tab lists it. */
    fun refresh() {
        selection.update { it.copy(switched = null) }
        pagerOf(selection.value.tab).refresh()
    }

    fun loadMore() = pagerOf(selection.value.tab).loadMore()

    /** Follows the forum, or when it is followed signs in to it. */
    fun followOrSignIn() {
        val forum = forum.value ?: return
        if (!forum.liked) like(forum) else if (!forum.signedIn) signIn(forum)
    }

    private fun like(forum: ForumDetail) = actions.launch("like") {
        joining.value = true
        runCatching { repository.like(forum, tbs) }
            .also { joining.value = false }
            .onSuccess {
                this@ForumViewModel.forum.value = it
                messages.show(R.string.toast_like_success, it.memberCount.toString())
            }
            .onFailure { messages.showError(R.string.toast_like_failed, it) }
    }

    private fun signIn(forum: ForumDetail) = actions.launch("sign-in") {
        joining.value = true
        runCatching { repository.signIn(forum, tbs) }
            .also { joining.value = false }
            .onSuccess {
                this@ForumViewModel.forum.value = it.forum
                messages.show(R.string.toast_sign_success, it.bonusPoints.toString(), it.rank.toString())
            }
            .onFailure { messages.showError(R.string.toast_sign_failed, it) }
    }

    fun unlike() {
        val forum = forum.value ?: return
        actions.launch("unlike") {
            runCatching { repository.unlike(forum, tbs) }
                .onSuccess {
                    this@ForumViewModel.forum.value = it
                    messages.show(R.string.toast_unlike_success)
                }
                .onFailure { messages.showError(R.string.toast_unlike_failed, it) }
        }
    }

    fun agree(tab: ForumTab, threadId: Long) {
        val pager = pagers.value[tab] ?: return
        val thread = pager.state.value.items.firstOrNull { it.id == threadId } ?: return
        actions.launch("agree-$threadId") {
            runCatching { threads.agree(thread, !thread.agree.agreed) }
                .onFailure { messages.showError(R.string.toast_agree_failed, it) }
        }
    }

    private fun pagerOf(tab: ForumTab): Pager<ForumCursor, ThreadSummary> {
        pagers.value[tab]?.let { return it }
        val pager = client.pager(
            scope = viewModelScope,
            initialKey = ForumCursor.first(tab),
            itemKey = { thread: ThreadSummary -> thread.id },
            normalize = { Threads(it) },
        ) { cursor ->
            val selection = selection.value
            val page = repository.threads(
                forumName = forumName,
                forumId = forum.value?.id ?: 0,
                tab = tab,
                cursor = cursor,
                sortType = selection.sortType,
                goodClassId = selection.goodClassId,
                // Until a page has told what the forum is, the page is still opening
                opening = forum.value == null,
            )
            page.forum?.let { loaded ->
                val current = forum.value
                if (current == null) history.save(loaded)
                // Only the latest threads count the whole forum: the good threads, of a class
                // above all, come with how many good threads there are
                forum.value = if (current == null || tab == ForumTab.Latest) {
                    loaded
                } else {
                    loaded.copy(memberCount = current.memberCount, threadCount = current.threadCount, postCount = current.postCount)
                }
                tbs = page.tbs ?: tbs
            }
            page.threads
        }
        pagers.update { it + (tab to pager) }
        return pager
    }

    private fun items(tab: ForumTab, threads: List<ThreadSummary>, rules: BlockRules): ImmutableList<ForumThreadItem> {
        val memo = blocked.getOrPut(tab) { BlockMemo() }
        return memo.map(rules, threads) { ForumThreadItem(it, rules.check(it, BlockPlace.Forum)) }.toImmutableList()
    }

    @AssistedFactory
    interface Factory {
        fun create(key: ForumKey): ForumViewModel
    }
}

private fun <T, R> PagerState<T>.withItems(items: ImmutableList<R>) = PagerState(
    items = items,
    refresh = refresh,
    append = append,
    prepend = prepend,
    hasMore = hasMore,
    hasPrevious = hasPrevious,
    loaded = loaded,
)
