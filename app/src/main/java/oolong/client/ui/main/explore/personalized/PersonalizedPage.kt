package oolong.client.ui.main.explore.personalized

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.model.DislikeOption
import oolong.client.model.ThreadSummary
import oolong.client.ui.components.BlockableContent
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.components.FeedCard
import oolong.client.ui.components.LoadMoreEffect
import oolong.client.ui.components.loadMoreFooter
import oolong.client.ui.designsystem.Container
import oolong.client.ui.designsystem.EndPull
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.VerticalDivider
import oolong.client.ui.designsystem.states.StateScreen
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.main.OnTabReselected
import oolong.client.ui.main.explore.FeedThreadEvents
import oolong.client.ui.main.explore.ThreadPeeker
import oolong.client.ui.main.explore.rememberFeedThreadEvents
import oolong.client.ui.main.explore.rememberThreadPeeker
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.TextStyles

@Composable
fun PersonalizedPage(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: PersonalizedViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.open() }
    OnTabReselected("personalized") { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    PersonalizedScreen(
        state = state,
        events = rememberFeedThreadEvents(onAgree = viewModel::agree),
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onRefreshShown = viewModel::refreshShown,
        onDislike = viewModel::dislike,
        onUndoDislike = viewModel::undoDislike,
        contentPadding = contentPadding,
    )
}

/**
 * Recommended threads, which pull down to refresh and load more at their end. A refresh shows what
 * it brought where its loader was, and a row marks where the threads already seen begin.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PersonalizedScreen(
    state: PersonalizedState,
    events: FeedThreadEvents,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDislike: (ThreadSummary, reasons: List<DislikeOption>, clickTime: Long) -> Unit,
    onUndoDislike: (threadId: Long) -> Unit = {},
    onRefreshShown: (id: Int) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val list = state.list
    val refreshing = list.refresh == LoadStatus.Loading
    val error = (list.refresh as? LoadStatus.Error)?.error
    val lazyListState = rememberLazyListState()

    val lastRefresh = state.lastRefresh
    // The new threads arrive in front of the one in view, which the list would otherwise keep in
    // place; they come into view in the frame they arrive instead
    var scrolledFor by remember { mutableIntStateOf(0) }
    SideEffect {
        if (lastRefresh != null && !lastRefresh.failed && lastRefresh.id != scrolledFor) {
            scrolledFor = lastRefresh.id
            lazyListState.requestScrollToItem(0)
        }
    }
    val result = lastRefresh?.let {
        when {
            it.failed -> stringResource(R.string.toast_feed_refresh_failed)
            it.count == 0 -> stringResource(R.string.toast_feed_no_new)
            else -> pluralStringResource(R.plurals.toast_feed_refresh, it.count, it.count)
        }
    }
    StateScreen(
        modifier = Modifier.fillMaxSize(),
        isEmpty = list.items.isEmpty(),
        isError = error != null,
        isLoading = refreshing,
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error) },
    ) {
        LoadMoreEffect(lazyListState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = onLoadMore)
        PullRefresh(
            refreshing = refreshing,
            revealUnpulled = true,
            onRefresh = onRefresh,
            result = result,
            onResultShown = { lastRefresh?.let { onRefreshShown(it.id) } },
            indicatorTop = contentPadding.calculateTopPadding(),
            endPull = if (list.hasMore) EndPull.None else EndPull.Tease,
        ) {
            FeedList(
                state = lazyListState,
                contentPadding = contentPadding,
                items = list.items,
                seenFrom = state.seenFrom,
                disliked = state.disliked,
                undoable = state.undoable,
                events = events,
                onDislike = onDislike,
                onUndoDislike = onUndoDislike,
                onRefresh = onRefresh,
                footer = { loadMoreFooter(list.append, list.hasMore, onLoadMore) },
            )
        }
    }
}

@Composable
private fun FeedList(
    state: LazyListState,
    contentPadding: PaddingValues,
    items: ImmutableList<RecommendedItem>,
    seenFrom: Long?,
    disliked: ImmutableSet<Long>,
    undoable: ImmutableMap<Long, String?>,
    events: FeedThreadEvents,
    onDislike: (ThreadSummary, reasons: List<DislikeOption>, clickTime: Long) -> Unit,
    onUndoDislike: (threadId: Long) -> Unit,
    onRefresh: () -> Unit,
    footer: LazyListScope.() -> Unit,
) {
    val peeker = rememberThreadPeeker()
    // Where the threads already seen begin; nothing above the first means nothing new
    val seenIndex = remember(items, seenFrom) {
        if (seenFrom == null) -1 else items.indexOfFirst { it.feed.thread.id == seenFrom }
    }
    MyLazyColumn(
        state = state,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (seenIndex > 0) {
            feedItems(items.subList(0, seenIndex), disliked, undoable, events, peeker, onDislike, onUndoDislike)
            item(key = "seen", contentType = "seen") { SeenMark(onRefresh) }
            feedItems(items.subList(seenIndex, items.size), disliked, undoable, events, peeker, onDislike, onUndoDislike)
        } else {
            feedItems(items, disliked, undoable, events, peeker, onDislike, onUndoDislike)
        }
        footer()
    }
}

/** A run of threads, divided from each other but not from what follows the last. */
private fun LazyListScope.feedItems(
    items: List<RecommendedItem>,
    disliked: ImmutableSet<Long>,
    undoable: ImmutableMap<Long, String?>,
    events: FeedThreadEvents,
    peeker: ThreadPeeker?,
    onDislike: (ThreadSummary, reasons: List<DislikeOption>, clickTime: Long) -> Unit,
    onUndoDislike: (threadId: Long) -> Unit,
) {
    itemsIndexed(
        items = items,
        key = { _, item -> "${item.feed.thread.id}" },
        contentType = { _, item ->
            val thread = item.feed.thread
            when {
                thread.video != null -> "Video"
                thread.images.size == 1 -> "SingleMedia"
                thread.images.size > 1 -> "MultiMedia"
                else -> "PlainText"
            }
        }
    ) { index, (feed, dislikeOptions) ->
        val (item, blocked) = feed
        val threadId = item.id
        val isHidden = threadId in disliked
        val showDivider = !isHidden && index < items.size - 1
        // The tip's words, kept for it to fold away with once it can no longer be taken back
        val tip = remember { arrayOfNulls<String>(1) }
        if (threadId in undoable) tip[0] = undoable[threadId]
        Container {
            AnimatedVisibility(
                visible = !isHidden,
                enter = EnterTransition.None,
                // The tip fades, then its gap closes from below, the threads after it moving up
                exit = fadeOut(Motion.fadeOut()) + shrinkVertically(Motion.dismiss(), shrinkTowards = Alignment.Top),
            ) {
                Column {
                    AnimatedContent(
                        // Once hidden it stays the tip, not the thread, while it folds away
                        targetState = threadId in undoable || isHidden,
                        transitionSpec = { fadeIn(Motion.fadeIn()) togetherWith fadeOut(Motion.fadeOut()) using SizeTransform(clip = true) { _, _ -> Motion.settle(IntSize.VisibilityThreshold) } },
                        label = "dislike",
                    ) { undo ->
                        if (undo) {
                            UndoDislikeRow(text = tip[0], onUndo = { onUndoDislike(threadId) })
                        } else {
                            BlockableContent(
                                block = blocked,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 16.dp)
                            ) {
                                Column {
                                    FeedCard(
                                        thread = item,
                                        showReplyTime = true,
                                        onClick = events.onOpen,
                                        onClickReply = events.onOpenReplies,
                                        onAgree = events.onAgree,
                                        onClickForum = events.onOpenForum,
                                        onClickUser = events.onOpenUser,
                                        onClickShared = events.onOpenOrigin,
                                        onClickFeedReply = events.onOpenFeedReply,
                                        onLongPress = peeker?.let { peeker ->
                                            { thread, from, at, origin ->
                                                peeker.peek(thread, from, at, origin, dislikeOptions) { clickTime, reasons ->
                                                    onDislike(thread, reasons, clickTime)
                                                }
                                            }
                                        },
                                    ) {
                                        FeedMenu(
                                            thread = item,
                                            options = dislikeOptions,
                                            onDislike = { clickTime, reasons -> onDislike(item, reasons, clickTime) },
                                        )
                                    }
                                    if (showDivider) {
                                        VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Where the threads already seen begin, with a way to refresh again. */
@Composable
private fun SeenMark(onRefresh: () -> Unit) {
    val colors = ExtendedTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(colors.divider))
        Text(
            text = stringResource(id = R.string.tip_seen_here),
            style = TextStyles.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 12.dp),
        )
        Text(
            text = " · ",
            style = TextStyles.caption,
            color = colors.textSecondary,
        )
        Text(
            text = stringResource(id = R.string.btn_refresh),
            style = TextStyles.caption,
            color = colors.accent,
            modifier = Modifier
                .clickable(interactionSource = null, indication = PressFeedback.overlay(Corners.sm), onClick = onRefresh)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Box(modifier = Modifier.padding(start = 8.dp).weight(1f).height(1.dp).background(colors.divider))
    }
}

/** In place of a thread just disliked: what happens next, and a way to take it back. */
@Composable
private fun UndoDislikeRow(text: String?, onUndo: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(ExtendedTheme.colors.chip, Corners.md)
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Text(
            text = text ?: stringResource(id = R.string.tip_disliked),
            color = ExtendedTheme.colors.textSecondary,
            style = MaterialTheme.typography.body2,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 14.dp),
        )
        Text(
            text = stringResource(id = R.string.button_undo),
            color = ExtendedTheme.colors.emphasis,
            style = MaterialTheme.typography.subtitle2,
            modifier = Modifier
                .clickable(interactionSource = null, indication = PressFeedback.overlay(Corners.sm), onClick = onUndo)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
