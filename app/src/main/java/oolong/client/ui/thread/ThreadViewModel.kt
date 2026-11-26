package oolong.client.ui.thread

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.ApplicationScope
import oolong.client.arch.MessageController
import oolong.client.arch.PagerState
import oolong.client.arch.query.Loads
import oolong.client.arch.query.QueryClient
import oolong.client.arch.query.QueryPager
import oolong.client.arch.query.withLatestEntities
import oolong.client.data.block.BlockMemo
import oolong.client.data.block.BlockPlace
import oolong.client.data.block.BlockRuleSource
import oolong.client.data.block.BlockRules
import oolong.client.data.block.BlockVerdict
import oolong.client.data.block.Blockable
import oolong.client.data.block.check
import oolong.client.data.history.ThreadHistory
import oolong.client.data.post.SentReplies
import oolong.client.data.thread.FloorCursor
import oolong.client.data.thread.Posts
import oolong.client.data.thread.SubPostSortStore
import oolong.client.data.thread.ThreadActions
import oolong.client.data.thread.ThreadCursor
import oolong.client.data.thread.ThreadLoad
import oolong.client.data.thread.ThreadPage
import oolong.client.data.thread.ThreadQuery
import oolong.client.data.thread.ThreadRepository
import oolong.client.data.thread.ThreadSort
import oolong.client.data.thread.Threads
import oolong.client.model.Post
import oolong.client.model.PostContent
import oolong.client.model.SubPost
import oolong.client.model.ThreadDetail
import oolong.client.model.ThreadSummary
import oolong.client.model.User
import oolong.client.model.Viewer
import oolong.client.navigation.ThreadKey
import oolong.client.ui.reader.ReaderPositions

/** A reply and how the block rules block it, or some of the replies shown inside it, by their ids. */
@Immutable
data class PostItem(
    val post: Post,
    val block: BlockVerdict? = null,
    val subPostBlocks: ImmutableMap<Long, BlockVerdict> = persistentMapOf(),
) {
    val blocked: Boolean get() = block != null
}

/**
 * The replies a floor shows inside it once opened past the few its page brings: [subPosts] in
 * order, the first of them those, with how the block rules block them by id. [complete] once the
 * server has no more.
 */
@Immutable
data class FloorReplies(
    val subPosts: ImmutableList<SubPost> = persistentListOf(),
    val blocks: ImmutableMap<Long, BlockVerdict> = persistentMapOf(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val complete: Boolean = false,
)

@Immutable
data class ThreadState(
    val query: ThreadQuery,
    /** Shown until the thread loads. */
    val preview: ThreadSummary? = null,
    val thread: ThreadDetail? = null,
    val firstPost: Post? = null,
    val posts: PagerState<PostItem> = PagerState(),
    val viewer: Viewer = Viewer(null),
    /** The furthest page loaded, and the number of pages, for jumping to a page. */
    val currentPage: Int = 0,
    val totalPage: Int = 0,
    /** The floors opened to more of their replies, by post id. */
    val floorReplies: ImmutableMap<Long, FloorReplies> = persistentMapOf(),
)

sealed interface ThreadEffect {
    data object NavigateUp : ThreadEffect

    /** Scroll to the first reply, after jumping somewhere in the thread. */
    data object ScrollToFirstReply : ThreadEffect

    /** Scroll to a post that has just been loaded, such as the user's own reply. */
    data class ScrollToPost(val postId: Long) : ThreadEffect
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ThreadViewModel.Factory::class)
class ThreadViewModel @AssistedInject constructor(
    @Assisted private val args: ThreadKey,
    private val repository: ThreadRepository,
    private val client: QueryClient,
    private val threads: ThreadActions,
    blockRuleSource: BlockRuleSource,
    private val history: ThreadHistory,
    private val messages: MessageController,
    @ApplicationScope private val applicationScope: CoroutineScope,
    readerPositions: ReaderPositions,
    sentReplies: SentReplies,
    subPostSort: SubPostSortStore? = null,
) : ViewModel() {
    private val query = MutableStateFlow(args.query())

    /** What each loaded page says about the thread, as opposed to its posts. */
    private val meta = MutableStateFlow(Meta())

    private data class Meta(
        val page: ThreadPage? = null,
        val maxPage: Int = 0,
    )

    /** The thread as the list it was opened from showed it, if it was. */
    private val preview = client.get(Threads, args.threadId)

    private val effects = Channel<ThreadEffect>(Channel.BUFFERED)
    val effect: Flow<ThreadEffect> = effects.receiveAsFlow()

    private val actions = Actions(viewModelScope)
    private val readUpTo = MutableStateFlow<Long?>(null)

    /** Where the list was last loaded from as a whole, so that retrying loads the same place. */
    private var refreshKey: ThreadCursor = ThreadCursor.first(args.postId)
    private var scrollAfterRefresh = args.scrollToReply && args.postId == 0L

    /** A post to scroll to as soon as a page containing it loads. */
    private var scrollToPost: Long? = args.postId.takeIf { it != 0L }

    /** Holds the list shown; the one shown before is let go when another replaces it. */
    private var listJob: Job? = null

    /**
     * The posts of the query shown, kept by the app: a thread opened again, or just peeked at,
     * shows what was loaded of it, and turning a filter back off shows the list from before.
     */
    private val lists = MutableStateFlow(open(query.value, refreshKey))

    private val posts: QueryPager<ThreadCursor, Post, ThreadLoad> get() = lists.value

    private fun open(query: ThreadQuery, from: ThreadCursor): QueryPager<ThreadCursor, Post, ThreadLoad> {
        listJob?.cancel()
        val job = Job(viewModelScope.coroutineContext.job).also { listJob = it }
        return repository.posts.pager(viewModelScope + job, query, from)
    }

    /** Takes what the list loaded besides its posts, and scrolls to a post once a page brings it. */
    private suspend fun onLoads(loads: Loads<ThreadCursor, ThreadLoad>) {
        val latest = loads.latest ?: return
        // Asking for newer posts and finding none brings nothing to take
        val page = latest.response.page ?: return
        val refreshed = loads.refreshed?.response?.page
        meta.update { meta ->
            meta.copy(
                page = page.copy(firstPost = page.firstPost ?: refreshed?.firstPost ?: meta.page?.firstPost),
                maxPage = if (latest.refreshed) page.currentPage else maxOf(meta.maxPage, page.currentPage, refreshed?.currentPage ?: 0),
            )
        }
        if (latest.refreshed && latest.cursor == refreshKey && scrollAfterRefresh) {
            scrollAfterRefresh = false
            effects.send(ThreadEffect.ScrollToFirstReply)
        }
        scrollToPost?.let { postId ->
            if (page.posts.items.any { it.id == postId } || page.firstPost?.id == postId) {
                scrollToPost = null
                effects.send(ThreadEffect.ScrollToPost(postId))
            }
        }
    }

    /** Block flags for each post, which most state changes leave as they are. */
    private val blocked = BlockMemo<Post, PostItem>()

    // Rules for one forum, or about the thread author, need to know the thread's
    private val origin = meta.map { meta -> meta.page?.thread?.let { ThreadOrigin(it.forum.name, it.author.id) } }.distinctUntilChanged()

    private val items: Flow<PagerState<PostItem>> = combine(
        lists.flatMapLatest { it.state },
        blockRuleSource.rules,
        origin,
    ) { state, rules, origin ->
        val items = blocked.map(rules to origin, state.items) { it.withBlocked(rules, origin) }.toImmutableList()
        PagerState(
            items = items,
            refresh = state.refresh,
            append = state.append,
            prepend = state.prepend,
            hasMore = state.hasMore,
            hasPrevious = state.hasPrevious,
        )
    }

    private val latestMeta = meta.withLatestEntities(client) { meta ->
        meta.copy(page = meta.page?.let { it.copy(thread = it.thread.copy(summary = Threads(it.thread.summary))) })
    }

    /** Floors opened to more of their replies, as far as they go, by post id. */
    private val opened = MutableStateFlow<PersistentMap<Long, OpenedFloor>>(persistentMapOf())
    private val openingJobs = mutableMapOf<Long, Job>()

    private val floorReplies: Flow<ImmutableMap<Long, FloorReplies>> = combine(opened, blockRuleSource.rules, origin) { opened, rules, origin ->
        opened.mapValues { (_, floor) ->
            val subPosts = floor.subPosts().take(floor.shown)
            FloorReplies(
                subPosts = subPosts.toImmutableList(),
                blocks = subPosts
                    .mapNotNull { subPost -> rules.checkIn(origin, subPost.author, subPost.content)?.let { subPost.id to it } }
                    .toMap()
                    .toImmutableMap(),
                loading = floor.loading,
                failed = floor.failed,
                complete = floor.next == null && floor.shown >= floor.subPosts().size,
            )
        }.toImmutableMap()
    }

    val state: StateFlow<ThreadState> = combine(query, latestMeta, items, floorReplies) { query, meta, items, floorReplies ->
        val page = meta.page
        ThreadState(
            query = query,
            preview = preview.takeIf { page == null },
            thread = page?.thread,
            firstPost = page?.firstPost,
            posts = items,
            viewer = page?.viewer ?: Viewer(null),
            currentPage = meta.maxPage,
            totalPage = page?.totalPage ?: 0,
            floorReplies = floorReplies,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThreadState(query.value))

    init {
        lists.flatMapLatest { it.loads }.onEach(::onLoads).launchIn(viewModelScope)
        // Back from the reader, the page follows to where the reading stopped
        readerPositions.left(args.threadId).onEach(::showPost).launchIn(viewModelScope)
        sentReplies.to(args.threadId).onEach { onReplySent(it.postId, inPost = it.inPost) }.launchIn(viewModelScope)
        combine(meta.map { it.page?.thread }.filterNotNull(), readUpTo) { thread, postId -> thread to postId }
            .debounce(HISTORY_DELAY)
            .onEach { (thread, postId) -> history.save(thread, query.value.seeLz, findPost(postId), meta.value.page?.firstPost) }
            .launchIn(viewModelScope)
        // The replies under each post come in the new order, and those opened go back to them
        subPostSort?.changes?.drop(1)?.onEach {
            openingJobs.values.forEach { it.cancel() }
            openingJobs.clear()
            opened.value = persistentMapOf()
            repository.changed(args.threadId)
        }?.launchIn(viewModelScope)
    }

    /** Loads the thread again from its start, or from its newest post when newest first. */
    fun refresh() = refreshFrom(ThreadCursor.first())

    /** Loads the thread again from where it was last loaded, after an error. */
    fun retry() = posts.refresh(refreshKey)

    fun loadMore() = posts.loadMore()

    /** Pulling up past the end: looks for replies newer than the last one loaded. */
    fun loadLatest() {
        val last = posts.state.value.items.lastOrNull() ?: return
        if (query.value.sort == ThreadSort.NewestFirst) return
        posts.loadMore(from = ThreadCursor.After(last.id))
    }

    /** The page before the first one loaded, or the newer posts when newest first. */
    fun loadPrevious() {
        if (query.value.sort == ThreadSort.NewestFirst) {
            val newest = posts.state.value.items.firstOrNull() ?: return refresh()
            val meta = meta.value
            posts.loadPrevious(
                from = ThreadCursor.newerThan(newest.id, meta.maxPage, meta.page?.lastPage ?: 0)
            )
        } else {
            posts.loadPrevious()
        }
    }

    /** Only oldest first is paged by number; newest first has no page numbers to jump to. */
    fun jumpToPage(page: Int) {
        if (query.value.sort == ThreadSort.NewestFirst) return
        refreshFrom(ThreadCursor.Number(page), scrollToFirstReply = true)
    }

    /** The floor on screen, where the reader opens. */
    fun readingFloor(): Int = findPost(readUpTo.value)?.floor ?: 0

    /** Scrolls to post [postId], loading the thread from it unless it is loaded. */
    private fun showPost(postId: Long) {
        if (postId == readUpTo.value) return
        if (findPost(postId) != null) {
            effects.trySend(ThreadEffect.ScrollToPost(postId))
        } else {
            jumpToPost(postId)
        }
    }

    /** The floor of post [postId], or null while it isn't loaded. */
    fun floorOf(postId: Long): Int? = findPost(postId)?.floor

    /**
     * Goes to post [postId], as the picture viewer asks to show where a picture is: scrolled to
     * when loaded, else the thread loads from it. Even the post being read is scrolled to, which
     * may be only partly on screen.
     */
    fun goToPost(postId: Long) {
        if (findPost(postId) != null) effects.trySend(ThreadEffect.ScrollToPost(postId)) else jumpToPost(postId)
    }

    fun jumpToPost(postId: Long) {
        scrollToPost = postId
        refreshFrom(ThreadCursor.Anchor(postId))
    }

    fun setSeeLz(seeLz: Boolean) {
        if (query.value.seeLz == seeLz) return
        query.update { it.copy(seeLz = seeLz) }
        show(ThreadCursor.first())
    }

    fun setSort(sort: ThreadSort) {
        if (query.value.sort == sort) return
        query.update { it.copy(sort = sort) }
        show(ThreadCursor.first())
    }

    fun agreeThread() = actions.launch("agree-thread") {
        val thread = state.value.thread ?: return@launch
        runCatching { threads.agree(thread.summary, !thread.agree.agreed) }
            .onFailure { messages.showError(R.string.toast_agree_failed, it) }
    }

    /** Votes for [optionIds] in the thread's poll, shown at once and rolled back if it fails. */
    fun vote(optionIds: Set<Int>) = actions.launch("vote") {
        val thread = meta.value.page?.thread ?: return@launch
        val poll = thread.poll?.takeIf { !it.hasVoted && !it.closed && optionIds.isNotEmpty() } ?: return@launch
        updateThread {
            it.copy(
                poll = poll.copy(
                    options = poll.options.map { option -> if (option.id in optionIds) option.copy(votes = option.votes + 1) else option }
                        .toImmutableList(),
                    voters = poll.voters + 1,
                    hasVoted = true,
                    chosen = optionIds.toImmutableSet(),
                ),
            )
        }
        runCatching { repository.vote(thread, optionIds) }.onFailure { e ->
            updateThread { it.copy(poll = poll) }
            messages.showError(R.string.toast_vote_failed, e)
        }
    }

    fun agreePost(postId: Long) = actions.launch("agree-$postId") {
        val thread = meta.value.page?.thread ?: return@launch
        val post = posts.state.value.items.firstOrNull { it.id == postId } ?: return@launch
        runCatching { threads.agreePost(thread.id, thread.forum.id, postId, !post.agree.agreed) }
            .onFailure { messages.showError(R.string.toast_agree_failed, it) }
    }

    /** Stores the thread marked as read up to [post], by default the last one the user saw. */
    fun addStore(post: Post? = findPost(readUpTo.value)) = actions.launch("store") {
        val thread = meta.value.page?.thread ?: return@launch
        val mark = post ?: meta.value.page?.firstPost ?: return@launch
        runCatching { repository.addStore(thread.id, mark.id) }
            .onSuccess {
                updateThread { it.copy(storeMarkPostId = mark.id) }
                messages.show(R.string.message_add_favorite_success, mark.floor)
            }
            .onFailure { messages.showError(R.string.message_add_favorite_failure, it) }
    }

    fun removeStore() = actions.launch("store") {
        val page = meta.value.page ?: return@launch
        runCatching { repository.removeStore(page.thread.id, page.thread.forum.id, page.tbs) }
            .onSuccess {
                updateThread { it.copy(storeMarkPostId = null) }
                messages.show(R.string.message_remove_favorite_success)
            }
            .onFailure { messages.showError(R.string.delete_store_failure, it) }
    }

    /** The post the store mark would move to on leaving, or null when there is nothing to update. */
    fun storeMarkUpdate(): Post? {
        val thread = meta.value.page?.thread ?: return null
        val post = findPost(readUpTo.value) ?: return null
        return post.takeIf { thread.isStored && thread.storeMarkPostId != post.id && post.floor > 0 }
    }

    /** Leaves the thread, moving the store mark to [post] on the way out. */
    fun leaveUpdatingStoreMark(post: Post) {
        val threadId = meta.value.page?.thread?.id ?: return
        // The page closes right away, so the request must not be tied to it
        applicationScope.launch {
            runCatching { repository.addStore(threadId, post.id) }
                .onSuccess { messages.show(R.string.message_update_collect_mark_success) }
                .onFailure { messages.showError(R.string.message_update_collect_mark_failed, it) }
        }
        effects.trySend(ThreadEffect.NavigateUp)
    }

    fun deletePost(post: Post) = actions.launch("delete-${post.id}") {
        val page = meta.value.page ?: return@launch
        val own = post.author.id == page.viewer.user?.id
        runCatching { repository.deletePost(page.thread, post.id, ownPost = own, tbs = page.tbs) }
            .onSuccess {
                posts.update { items -> items.filterNot { it.id == post.id } }
                messages.show(R.string.toast_delete_success)
            }
            .onFailure { messages.showError(R.string.toast_delete_failure, it) }
    }

    fun deleteThread() = actions.launch("delete-thread") {
        val page = meta.value.page ?: return@launch
        val own = page.thread.author.id == page.viewer.user?.id
        runCatching { repository.deleteThread(page.thread, ownThread = own, tbs = page.tbs) }
            .onSuccess { effects.send(ThreadEffect.NavigateUp) }
            .onFailure { messages.showError(R.string.toast_delete_failure, it) }
    }

    /**
     * Shows [REPLIES_PER_OPENING] more of the replies inside [post], loading pages of them as needed,
     * after the few the thread's page brings.
     */
    fun openSubPosts(post: Post) {
        val start = opened.value[post.id] ?: OpenedFloor(preview = post.subPosts, shown = post.subPosts.size, next = FloorCursor(1))
        if (start.loading) return
        val forumId = meta.value.page?.thread?.forum?.id ?: return
        val target = start.shown + REPLIES_PER_OPENING
        openingJobs[post.id] = viewModelScope.launch {
            var floor = start.copy(loading = true, failed = false)
            opened.update { it.put(post.id, floor) }
            floor = try {
                while (floor.subPosts().size < target) {
                    val next = floor.next ?: break
                    val page = repository.floor(args.threadId, post.id, forumId, next, floor.preview.map { it.id })
                    floor = floor.copy(fetched = floor.fetched + page.subPosts.items, next = page.subPosts.next)
                }
                floor.copy(shown = minOf(target, floor.subPosts().size), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                floor.copy(loading = false, failed = true)
            }
            opened.update { it.put(post.id, floor) }
        }
    }

    /** Back to the few replies inside post [postId] that the thread's page brings. */
    fun closeSubPosts(postId: Long) {
        openingJobs.remove(postId)?.cancel()
        opened.update { it.remove(postId) }
    }

    /**
     * The user's reply was sent: load it and show it. A reply inside post [inPost] updates that
     * post where it is.
     */
    private fun onReplySent(newPostId: Long, inPost: Long? = null) {
        repository.changed(args.threadId)
        if (inPost != null) return reloadPost(inPost)
        scrollToPost = newPostId
        when {
            query.value.sort == ThreadSort.NewestFirst -> loadPrevious()
            posts.state.value.hasMore -> refreshFrom(ThreadCursor.Anchor(newPostId))
            else -> posts.loadMore(from = ThreadCursor.Anchor(newPostId))
        }
    }

    private fun reloadPost(postId: Long) = actions.launch("reload-$postId") {
        runCatching { repository.page(query.value, ThreadCursor.Anchor(postId)) }
            .onSuccess { page ->
                val reloaded = page.posts.items.firstOrNull { it.id == postId } ?: return@onSuccess
                client.update(Posts, postId) { reloaded }
            }
    }

    /** The last post on screen, which history and the store mark remember. */
    fun onReadUpTo(postId: Long) {
        readUpTo.value = postId
    }

    /** Shows the list of the query now asked for, as kept if it is, from [from]. */
    private fun show(from: ThreadCursor) {
        refreshKey = from
        scrollAfterRefresh = false
        lists.value = open(query.value, from)
    }

    private fun refreshFrom(key: ThreadCursor, scrollToFirstReply: Boolean = false) {
        refreshKey = key
        scrollAfterRefresh = scrollToFirstReply
        posts.refresh(key)
    }

    private fun findPost(postId: Long?): Post? {
        postId ?: return null
        return posts.state.value.items.firstOrNull { it.id == postId }
            ?: meta.value.page?.firstPost?.takeIf { it.id == postId }
    }

    private fun updateThread(transform: (ThreadDetail) -> ThreadDetail) {
        meta.update { meta ->
            meta.copy(page = meta.page?.let { it.copy(thread = transform(it.thread)) })
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(args: ThreadKey): ThreadViewModel
    }

    private companion object {
        const val HISTORY_DELAY = 1_000L

        /** How many more replies inside a floor each opening shows. */
        const val REPLIES_PER_OPENING = 10
    }
}

/**
 * A floor opened to more of its replies: the [preview] its page brought, those [fetched] from the
 * pages of the replies, the [shown] first of both, and the page to load next, null past the last.
 */
private data class OpenedFloor(
    val preview: List<SubPost>,
    val shown: Int,
    val next: FloorCursor?,
    val fetched: List<SubPost> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
) {
    /** The pages of the replies start with those the preview has. */
    fun subPosts(): List<SubPost> = (preview + fetched).distinctBy { it.id }
}

/** Where a thread's posts were written: its forum, by name, and who started it. */
private data class ThreadOrigin(val forum: String, val authorId: Long)

private fun BlockRules.checkIn(origin: ThreadOrigin?, author: User, content: List<PostContent>) = check(
    author, content, BlockPlace.Thread, forum = origin?.forum,
    byThreadAuthor = origin != null && author.id == origin.authorId,
)

private fun Post.withBlocked(rules: BlockRules, origin: ThreadOrigin?): PostItem {
    val post = this
    fun check(author: User, content: List<PostContent>) = rules.checkIn(origin, author, content)
    return PostItem(
        post = post,
        block = check(post.author, post.content),
        subPostBlocks = post.subPosts
            .mapNotNull { subPost -> check(subPost.author, subPost.content)?.let { subPost.id to it } }
            .toMap()
            .toImmutableMap(),
    )
}
