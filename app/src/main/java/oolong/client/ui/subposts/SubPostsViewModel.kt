package oolong.client.ui.subposts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.MessageController
import oolong.client.arch.PagerState
import oolong.client.arch.query.QueryClient
import oolong.client.arch.query.pager
import oolong.client.arch.query.withLatestEntities
import oolong.client.data.block.BlockMemo
import oolong.client.data.block.BlockPlace
import oolong.client.data.block.BlockRuleSource
import oolong.client.data.block.BlockRules
import oolong.client.data.block.BlockVerdict
import oolong.client.data.block.Blockable
import oolong.client.data.block.check
import oolong.client.data.post.SentReplies
import oolong.client.data.thread.FloorCursor
import oolong.client.data.thread.FloorPage
import oolong.client.data.thread.SubPostSort
import oolong.client.data.thread.SubPostSortStore
import oolong.client.data.thread.SubPosts
import oolong.client.data.thread.ThreadActions
import oolong.client.data.thread.ThreadRepository
import oolong.client.data.thread.post
import oolong.client.model.ForumSummary
import oolong.client.model.Post
import oolong.client.model.PostContent
import oolong.client.model.SubPost
import oolong.client.model.User
import oolong.client.navigation.SubPostsKey
import oolong.core.network.TiebaServerException
import oolong.core.network.identity.SessionProvider

/** A reply inside the post and how the block rules block it, if they do. */
@Immutable
data class SubPostItem(val subPost: SubPost, val block: BlockVerdict? = null) {
    val blocked: Boolean get() = block != null
}

@Immutable
data class SubPostsState(
    val threadId: Long,
    val post: Post? = null,
    val postBlock: BlockVerdict? = null,
    val threadAuthorId: Long = 0,
    val forum: ForumSummary? = null,
    val subPosts: PagerState<SubPostItem> = PagerState(),
    /** How many replies the post has in all. */
    val totalCount: Int = 0,
    /** The logged-in user's id, or null for a guest. */
    val viewerId: Long? = null,
    val sort: SubPostSort = SubPostSort.Hot,
)

sealed interface SubPostsEffect {
    data object NavigateUp : SubPostsEffect

    /** Scroll to a reply that has just been loaded. */
    data class ScrollToSubPost(val subPostId: Long) : SubPostsEffect

    /** Scroll past the post to the replies, once they first show. */
    data object ScrollToReplies : SubPostsEffect
}

/**
 * The replies inside one post. Opened from a reply (`loadFromSubPost`), the post and forum ids
 * passed in may not be the post's; the ones the first page returns are used from then on.
 */
@HiltViewModel(assistedFactory = SubPostsViewModel.Factory::class)
class SubPostsViewModel @AssistedInject constructor(
    @Assisted key: SubPostsKey,
    private val repository: ThreadRepository,
    private val client: QueryClient,
    private val threads: ThreadActions,
    blockRuleSource: BlockRuleSource,
    private val messages: MessageController,
    private val sessionProvider: SessionProvider,
    sentReplies: SentReplies,
    private val sortStore: SubPostSortStore? = null,
) : ViewModel() {
    private val threadId: Long = key.threadId
    private val subPostId: Long = key.subPostId
    private val loadFromSubPost: Boolean = key.loadFromSubPost
    private val previewIds: List<Long> = key.previewIds

    private var postId: Long = key.postId
    private var forumId: Long = key.forumId

    private val floor = MutableStateFlow<FloorPage?>(null)
    private val sort = MutableStateFlow(sortStore?.sort ?: SubPostSort.Hot)
    private val effects = Channel<SubPostsEffect>(Channel.BUFFERED)
    val effect: Flow<SubPostsEffect> = effects.receiveAsFlow()
    private val actions = Actions(viewModelScope)

    private val firstKey = FloorCursor(page = 1, subPostId = subPostId.takeIf { loadFromSubPost } ?: 0L)

    /** A reply to scroll to as soon as a page containing it loads. */
    private var scrollToSubPost: Long? = firstKey.subPostId.takeIf { it != 0L }

    private var scrollToReplies = key.atReplies

    private val subPosts = client.pager(
        scope = viewModelScope,
        initialKey = firstKey,
        itemKey = { subPost: SubPost -> subPost.id },
        normalize = { SubPosts(it) },
    ) { key ->
        val page = try {
            repository.floor(threadId, postId, forumId, key, previewIds)
        } catch (e: TiebaServerException) {
            // A reply a feed recommends may be a floor itself, which isn't found as a reply inside one
            if (key.subPostId == 0L || e.code != NotFoundCode) throw e
            scrollToSubPost = null
            repository.floor(threadId, key.subPostId, forumId, FloorCursor(key.page), previewIds)
        }
        postId = page.post.id
        forumId = page.forum.id
        floor.value = page
        if (scrollToReplies && page.subPosts.items.isNotEmpty()) {
            scrollToReplies = false
            effects.send(SubPostsEffect.ScrollToReplies)
        }
        scrollToSubPost?.let { id ->
            if (page.subPosts.items.any { it.id == id }) {
                scrollToSubPost = null
                effects.send(SubPostsEffect.ScrollToSubPost(id))
            }
        }
        page.subPosts
    }

    /** Block flags for the last list and rules seen, which most state changes leave as they are. */
    private val blocked = BlockMemo<SubPost, SubPostItem>()

    /** The post as liked here or anywhere else it shows. */
    private val latestFloor = floor.withLatestEntities(client) { it?.copy(post = post(it.post)) }

    val state: StateFlow<SubPostsState> = combine(latestFloor, subPosts.state, blockRuleSource.rules, sort) { floor, pager, rules, sort ->
        val forumName = floor?.forum?.name
        val threadAuthorId = floor?.threadAuthorId ?: 0L
        fun check(author: User, content: List<PostContent>) = rules.check(
            author, content, BlockPlace.Thread, forum = forumName, byThreadAuthor = threadAuthorId != 0L && author.id == threadAuthorId,
        )
        val items = blocked.map(Triple(rules, forumName, threadAuthorId), pager.items) { SubPostItem(it, check(it.author, it.content)) }
            .toImmutableList()
        SubPostsState(
            threadId = threadId,
            post = floor?.post,
            postBlock = floor?.post?.let { check(it.author, it.content) },
            threadAuthorId = floor?.threadAuthorId ?: 0L,
            forum = floor?.forum,
            subPosts = PagerState(
                items = items,
                refresh = pager.refresh,
                append = pager.append,
                prepend = pager.prepend,
                hasMore = pager.hasMore,
                hasPrevious = pager.hasPrevious,
                loaded = pager.loaded,
            ),
            totalCount = floor?.totalCount ?: 0,
            viewerId = sessionProvider.currentSession()?.uid?.toLongOrNull(),
            sort = sort,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SubPostsState(threadId))

    fun retry() = subPosts.refresh(firstKey)

    fun refresh() = subPosts.refresh(FloorCursor(1))

    /**
     * Orders the replies by [sort] from now on, here and under the posts of threads, and loads
     * them again from the start.
     */
    fun setSort(sort: SubPostSort) {
        if (sort == this.sort.value) return
        sortStore?.sort = sort
        this.sort.value = sort
        subPosts.refresh(FloorCursor(1))
    }

    fun loadMore() = subPosts.loadMore()

    fun loadPrevious() = subPosts.loadPrevious()

    fun agreePost() = actions.launch("agree-post") {
        val post = state.value.post ?: return@launch
        runCatching { threads.agreePost(threadId, forumId, post.id, !post.agree.agreed) }
            .onFailure { messages.showError(R.string.toast_agree_failed, it) }
    }

    fun agreeSubPost(subPostId: Long) = actions.launch("agree-$subPostId") {
        val subPost = subPosts.state.value.items.firstOrNull { it.id == subPostId } ?: return@launch
        runCatching { threads.agreeSubPost(threadId, forumId, subPostId, !subPost.agree.agreed) }
            .onFailure { messages.showError(R.string.toast_agree_failed, it) }
    }

    /** Deletes a reply inside the post, or with null the post itself, which closes the page. */
    fun delete(subPost: SubPost?) = actions.launch("delete-${subPost?.id}") {
        val page = floor.value ?: return@launch
        val viewerId = sessionProvider.currentSession()?.uid?.toLongOrNull()
        val author = subPost?.author ?: page.post.author
        runCatching {
            repository.deletePost(
                forum = page.forum,
                threadId = threadId,
                postId = subPost?.id ?: page.post.id,
                ownPost = author.id == viewerId,
                isSubPost = subPost != null,
                tbs = page.tbs,
            )
        }
            .onSuccess {
                messages.show(R.string.toast_delete_success)
                if (subPost == null) {
                    effects.send(SubPostsEffect.NavigateUp)
                } else {
                    subPosts.update { items -> items.filterNot { it.id == subPost.id } }
                }
            }
            .onFailure { messages.showError(R.string.toast_delete_failure, it) }
    }

    init {
        sentReplies.to(threadId).filter { it.inPost == postId }.onEach { onReplySent(it.postId) }.launchIn(viewModelScope)
    }

    /** The user replied inside this post: load the page with their reply and show it. */
    private fun onReplySent(newSubPostId: Long) {
        scrollToSubPost = newSubPostId
        subPosts.refresh(FloorCursor(page = 1, subPostId = newSubPostId))
    }

    @AssistedFactory
    interface Factory {
        fun create(key: SubPostsKey): SubPostsViewModel
    }
}

/** The server's code for a post or reply it can't find. */
private const val NotFoundCode = 4
