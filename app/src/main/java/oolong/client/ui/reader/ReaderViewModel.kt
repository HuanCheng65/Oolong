package oolong.client.ui.reader

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import oolong.client.arch.LoadStatus
import oolong.client.arch.query.QueryClient
import oolong.client.data.thread.ThreadCursor
import oolong.client.data.thread.ThreadQuery
import oolong.client.data.thread.ThreadRepository
import oolong.client.data.thread.Threads
import oolong.client.model.ThreadDetail
import oolong.client.model.ThreadSummary
import oolong.client.navigation.ReaderKey

@Immutable
data class ReaderState(
    /** Shown until the thread loads. */
    val preview: ThreadSummary? = null,
    val thread: ThreadDetail? = null,
    val document: ReaderDocument = ReaderDocument.Empty,
    val loading: LoadStatus = LoadStatus.Idle,
    val hasMore: Boolean = false,
    /** The thread's last floor, which the reading position is told against. */
    val lastFloor: Int = 0,
    /** Loading on to the floor the reader opens at, before showing the text. */
    val seeking: Boolean = false,
)

sealed interface ReaderEffect {
    /** Scroll to block [index] of the document. */
    data class ScrollToBlock(val index: Int) : ReaderEffect
}

/** Loads the thread author's floors page by page and reads them as one text. */
@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted private val args: ReaderKey,
    repository: ThreadRepository,
    client: QueryClient,
    private val positions: ReaderPositions,
) : ViewModel() {
    private val query = ThreadQuery(threadId = args.threadId, seeLz = true, forumId = args.forumId, offline = args.offline)

    // The thread page with only the author's posts keeps the same list in the app's cache
    private val pager = repository.posts.pager(viewModelScope, query, ThreadCursor.first())

    private val page = pager.loads.mapNotNull { it.latest?.response?.page }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val preview = client.get(Threads, args.threadId)

    /** Heading shapes found so far, which stay found as more floors load. */
    private var kinds = emptySet<HeadingKind>()

    // Only the first page brings the first floor
    private val firstPost = pager.loads.map { it.refreshed?.response?.page?.firstPost ?: it.latest?.response?.page?.firstPost }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val document = combine(firstPost, pager.state.map { it.items }.distinctUntilChanged()) { first, posts ->
        val all = if (first != null && posts.none { it.id == first.id }) listOf(first) + posts else posts
        buildReaderDocument(all, kinds).also { kinds = it.kinds }
    }.flowOn(Dispatchers.Default)

    private val readUpTo = MutableStateFlow<Long?>(null)

    /** The floor still to load on to and scroll to, or null once there. */
    private val seekFloor = MutableStateFlow(args.floor.takeIf { it > 1 })

    private val effects = Channel<ReaderEffect>(Channel.BUFFERED)
    val effect: Flow<ReaderEffect> = effects.receiveAsFlow()

    val state: StateFlow<ReaderState> = combine(page, pager.state, document, seekFloor) { page, pager, document, seekFloor ->
        val thread = page?.thread
        ReaderState(
            preview = preview.takeIf { thread == null },
            thread = thread,
            document = document,
            loading = if (pager.refresh != LoadStatus.Idle) pager.refresh else pager.append,
            hasMore = pager.hasMore,
            lastFloor = maxOf(thread?.replyCount?.plus(1) ?: 0, pager.items.lastOrNull()?.floor ?: 0),
            seeking = seekFloor != null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderState(preview = preview))

    init {
        state.onEach(::seek).launchIn(viewModelScope)
    }

    /** Loads on until the floor to open at is in, then scrolls to the author's floor at or before it. */
    private fun seek(state: ReaderState) {
        val floor = seekFloor.value ?: return
        val blocks = state.document.blocks
        when {
            state.loading is LoadStatus.Error -> seekFloor.value = null
            blocks.any { it.floor > floor } || (!state.hasMore && state.loading == LoadStatus.Idle && blocks.isNotEmpty()) -> {
                var index = blocks.indexOfLast { it.floor <= floor }
                while (index > 0 && blocks[index - 1].floor == blocks[index].floor) index--
                if (index > 0) effects.trySend(ReaderEffect.ScrollToBlock(index))
                seekFloor.value = null
            }
            else -> loadMore()
        }
    }

    // The thread page under the reader follows to where it stopped
    override fun onCleared() {
        readUpTo.value?.takeIf { it != openedAt }?.let { positions.leave(args.threadId, it) }
    }

    fun loadMore() {
        val state = pager.state.value
        if (state.hasMore && state.append == LoadStatus.Idle && state.refresh == LoadStatus.Idle) pager.loadMore()
    }

    fun retry() {
        if (pager.state.value.items.isEmpty()) pager.refresh() else pager.loadMore()
    }

    /** Where the reader opened, once it got there; leaving from here leaves the thread page as it was. */
    private var openedAt: Long? = null

    /** The floor on screen, which the thread page follows to when the reader closes. */
    fun onReadUpTo(postId: Long) {
        if (seekFloor.value != null) return
        if (openedAt == null) openedAt = postId
        readUpTo.value = postId
    }

    @AssistedFactory
    interface Factory {
        fun create(args: ReaderKey): ReaderViewModel
    }
}
