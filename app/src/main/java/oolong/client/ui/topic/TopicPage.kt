package oolong.client.ui.topic

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import oolong.client.R
import oolong.client.arch.Actions
import oolong.client.arch.LoadStatus
import oolong.client.arch.MessageController
import oolong.client.arch.Page
import oolong.client.arch.PagerState
import oolong.client.arch.query.QueryClient
import oolong.client.arch.query.pager
import oolong.client.data.block.BlockRuleSource
import oolong.client.data.explore.ExploreRepository
import oolong.client.data.thread.ThreadActions
import oolong.client.data.thread.Threads
import oolong.client.model.ForumSummary
import oolong.client.model.ThreadSummary
import oolong.client.model.Topic
import oolong.client.model.TopicForum
import oolong.client.navigation.ForumKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.TopicKey
import oolong.client.ui.components.BlockableContent
import oolong.client.ui.components.ContentImage
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.components.FeedCard
import oolong.client.ui.components.LoadMoreEffect
import oolong.client.ui.components.loadMoreFooter
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.EndPull
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.VerticalDivider
import oolong.client.ui.designsystem.states.StateScreen
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.main.explore.FeedThread
import oolong.client.ui.main.explore.FeedThreadEvents
import oolong.client.ui.main.explore.feedThread
import oolong.client.ui.main.explore.mapItems
import oolong.client.ui.main.explore.rememberFeedThreadEvents
import oolong.client.util.StringUtil.getShortNumString
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.Spacing
import oolong.core.designsystem.theme.TextStyles

/** Where a topic's next page starts: its number, and the last thread of the one before. */
private data class TopicCursor(val page: Int, val lastThreadId: Long)

/** What the first page of a topic tells of it besides its threads. */
private data class TopicHead(val topic: Topic?, val forums: List<TopicForum>, val leadThreadId: Long?)

@Immutable
data class TopicState(
    val topic: Topic? = null,
    val forums: ImmutableList<TopicForum> = persistentListOf(),
    /** The thread picked to lead the topic, which shows as pinned. */
    val leadThreadId: Long? = null,
    val threads: PagerState<FeedThread> = PagerState(refresh = LoadStatus.Loading),
)

/** A hot topic and the threads talking about it, a page at a time. */
@HiltViewModel(assistedFactory = TopicViewModel.Factory::class)
class TopicViewModel @AssistedInject constructor(
    @Assisted private val key: TopicKey,
    private val repository: ExploreRepository,
    client: QueryClient,
    private val threadActions: ThreadActions,
    private val messages: MessageController,
    blockRuleSource: BlockRuleSource,
) : ViewModel() {
    private val actions = Actions(viewModelScope)
    private val head = MutableStateFlow(TopicHead(null, emptyList(), null))

    private val pager = client.pager(
        scope = viewModelScope,
        initialKey = TopicCursor(1, 0),
        itemKey = { thread: ThreadSummary -> thread.id },
        normalize = { Threads(it) },
    ) { cursor ->
        val page = repository.topic(key.id, key.name, cursor.page, cursor.lastThreadId)
        if (cursor.page == 1) head.value = TopicHead(page.topic, page.forums, page.threads.firstOrNull { it.second }?.first?.id)
        val threads = page.threads.map { it.first }
        Page(threads, next = threads.lastOrNull()?.takeIf { page.hasMore }?.let { TopicCursor(cursor.page + 1, it.id) })
    }

    val state: StateFlow<TopicState> = combine(head, pager.state, blockRuleSource.rules) { head, threads, rules ->
        TopicState(
            topic = head.topic,
            forums = head.forums.toImmutableList(),
            leadThreadId = head.leadThreadId,
            threads = threads.mapItems(rules::feedThread),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TopicState())

    init {
        pager.refresh()
    }

    fun refresh() = pager.refresh()

    fun loadMore() = pager.loadMore()

    fun agree(thread: ThreadSummary) = actions.launch("agree-${thread.id}") {
        runCatching { threadActions.agree(thread, !thread.agree.agreed) }
            .onFailure { messages.showError(R.string.toast_agree_failed, it) }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: TopicKey): TopicViewModel
    }
}

@Composable
fun TopicPage(
    key: TopicKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: TopicViewModel = hiltViewModel<TopicViewModel, TopicViewModel.Factory> { it.create(key) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    // The header names the topic; the bar does once it has scrolled away
    val headerGone by remember { derivedStateOf { state.topic == null || lazyListState.firstVisibleItemIndex > 0 } }
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = {
                    // Fades in as the header scrolls away and out as it comes back
                    AnimatedVisibility(visible = headerGone, enter = fadeIn(Motion.fadeIn()), exit = fadeOut(Motion.fadeOut())) {
                        GlassTopBarTitle(text = state.topic?.name ?: key.name.ifEmpty { stringResource(R.string.title_topic) })
                    }
                },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { padding ->
        TopicScreen(
            state = state,
            events = rememberFeedThreadEvents(onAgree = viewModel::agree),
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onOpenForum = { navigator.navigate(ForumKey(it.name)) },
            contentPadding = padding,
            lazyListState = lazyListState,
        )
    }
}

/**
 * A topic: its picture, name, place and heat, what it is about, the forums it is talked about in,
 * then its threads, the one picked to lead first and marked pinned.
 */
@Composable
fun TopicScreen(
    state: TopicState,
    events: FeedThreadEvents,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenForum: (ForumSummary) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val list = state.threads
    val error = (list.refresh as? LoadStatus.Error)?.error
    StateScreen(
        modifier = Modifier.fillMaxSize(),
        isEmpty = list.items.isEmpty() && state.topic == null,
        isError = error != null,
        // Pulled to refresh, the page stays and the pull shows it; only the first load replaces it
        isLoading = list.waiting && state.topic == null,
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error, modifier = Modifier.padding(contentPadding)) },
    ) {
        LoadMoreEffect(lazyListState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = onLoadMore)
        PullRefresh(
            refreshing = list.refresh == LoadStatus.Loading && (list.items.isNotEmpty() || state.topic != null),
            onRefresh = onRefresh,
            indicatorTop = contentPadding.calculateTopPadding(),
            endPull = if (list.hasMore) EndPull.None else EndPull.Tease,
        ) {
            val topPadding = contentPadding.calculateTopPadding()
            MyLazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item(key = "Head") {
                    val topic = state.topic
                    if (topic != null) TopicHeader(topic, barHeight = topPadding) else Box(modifier = Modifier.padding(top = topPadding))
                }
                if (state.forums.isNotEmpty()) {
                    item(key = "ForumsTitle") {
                        Text(
                            text = stringResource(R.string.topic_forums),
                            style = TextStyles.label,
                            color = ExtendedTheme.colors.text,
                            modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.sm, bottom = Spacing.xxs),
                        )
                    }
                    items(state.forums, key = { "Forum_${it.forum.id}" }) { TopicForumRow(it, onOpenForum) }
                    item(key = "ForumsEnd") { VerticalDivider(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) }
                }
                itemsIndexed(list.items, key = { _, item -> item.thread.id }) { index, item ->
                    BlockableContent(block = item.block, modifier = Modifier.padding(vertical = Spacing.xs, horizontal = Spacing.md)) {
                        Column {
                            FeedCard(
                                thread = item.thread,
                                showReplyTime = true,
                                onClick = events.onOpen,
                                onClickReply = events.onOpenReplies,
                                onAgree = events.onAgree,
                                onClickForum = events.onOpenForum,
                                onClickUser = events.onOpenUser,
                                onClickShared = events.onOpenOrigin,
                                onLongPress = events.onPeek,
                                dislikeAction = { if (item.thread.id == state.leadThreadId) PinnedLabel() },
                            )
                            if (index < list.items.size - 1) VerticalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
                        }
                    }
                }
                loadMoreFooter(list.append, list.hasMore, onLoadMore)
            }
        }
    }
}

/**
 * The topic's picture, small and square, beside its name, place and heat, over the same picture
 * blown up and blurred behind the bar, fading into the page; then what it is about. The pictures
 * come small, so only the blurred one fills the width.
 */
@Composable
private fun TopicHeader(topic: Topic, barHeight: Dp) {
    val page = ExtendedTheme.colors.background.takeIf { it.alpha == 1f } ?: ExtendedTheme.colors.card
    Column {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (topic.picture.isNotEmpty()) {
                TopicBackdrop(topic.picture, modifier = Modifier.matchParentSize())
            } else {
                topic.color?.let { Box(modifier = Modifier.matchParentSize().background(Color(it).copy(alpha = 0.3f))) }
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.16f), 0.55f to page.copy(alpha = 0.15f), 1f to page)),
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier
                    .padding(top = barHeight + Spacing.xs)
                    .padding(horizontal = Spacing.md)
                    .padding(bottom = Spacing.lg),
            ) {
                if (topic.picture.isNotEmpty()) {
                    ContentImage(
                        imageUri = topic.picture,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(CoverSize),
                        shape = Corners.lg,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(text = topic.name, style = TextStyles.title, color = ExtendedTheme.colors.text)
                    val stats = listOfNotNull(
                        topic.rank.takeIf { it > 0 }?.let { stringResource(R.string.topic_rank, it) },
                        stringResource(R.string.hot_num, topic.discussCount.getShortNumString()),
                    ).joinToString(" · ")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        TopicTag(topic.tag)
                        Text(text = stats, style = TextStyles.caption, color = ExtendedTheme.colors.textSecondary)
                    }
                }
            }
        }
        if (topic.description.isNotEmpty()) {
            Text(
                text = topic.description,
                style = TextStyles.reply,
                color = ExtendedTheme.colors.text,
                modifier = Modifier.padding(horizontal = Spacing.md).padding(bottom = Spacing.xs),
            )
        }
    }
}

/**
 * [picture] as a soft field of its colours: decoded tiny and drawn large, which blurs it on every
 * version, and blurred further where the system can.
 */
@Composable
private fun TopicBackdrop(picture: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(picture) { ImageRequest.Builder(context).data(picture).size(BackdropDecodeSize).build() }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.High,
        modifier = modifier
            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(24.dp, BlurredEdgeTreatment.Rectangle) else Modifier),
    )
}

/** A forum the topic is talked about in: its avatar, name and how many follow it. */
@Composable
private fun TopicForumRow(item: TopicForum, onOpen: (ForumSummary) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(item.forum) }
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Avatar(data = item.forum.avatar, size = 44.dp, contentDescription = null, shape = Corners.md)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.title_forum, item.forum.name),
                style = TextStyles.name,
                color = ExtendedTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.forum_rank_members, item.members.getShortNumString()).replace(Regex("([万亿]) "), "$1"),
                style = TextStyles.footnote,
                color = ExtendedTheme.colors.textSecondary,
            )
        }
        Icon(imageVector = Symbols.ChevronRight, contentDescription = null, tint = ExtendedTheme.colors.textDisabled, modifier = Modifier.size(20.dp))
    }
}

private val CoverSize = 96.dp
private const val BackdropDecodeSize = 48

@Composable
private fun PinnedLabel() {
    Text(
        text = stringResource(R.string.content_top),
        style = TextStyles.footnote,
        color = ExtendedTheme.colors.textSecondary,
        modifier = Modifier
            .background(ExtendedTheme.colors.chip, Corners.xs)
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
    )
}
