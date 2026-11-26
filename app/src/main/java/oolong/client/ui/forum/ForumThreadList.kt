package oolong.client.ui.forum

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.model.ForumSort
import oolong.client.model.ForumTab
import oolong.client.model.ThreadSummary
import oolong.client.ui.components.BlockableContent
import oolong.client.ui.components.FeedCard
import oolong.client.ui.components.LoadMoreEffect
import oolong.client.ui.components.loadMoreFooter
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.Reveal
import oolong.client.ui.designsystem.VerticalDivider
import oolong.client.ui.designsystem.adaptive.LocalWindowSizeClass
import oolong.client.ui.designsystem.adaptive.WindowWidthSizeClass
import oolong.client.ui.designsystem.motionItem
import oolong.client.ui.designsystem.pageAccentOrPrimary
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.main.explore.rememberThreadPeeker
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import oolong.core.designsystem.theme.TextStyles

private enum class ItemType { Top, PlainText, SingleMedia, MultiMedia, Video }

/**
 * One tab's threads: those pinned to the top as single lines, then the rest. With [topsFolded],
 * only the first pinned thread shows, with how many there are; a button at its end folds and
 * unfolds them. The good threads tab starts with the classes to filter them by.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun ForumThreadList(
    tab: ForumTab,
    list: PagerState<ForumThreadItem>,
    /** The order of the latest threads, which their times follow. */
    sortType: Int,
    onEvent: (ForumEvent) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    topsFolded: Boolean = false,
    onFoldTops: (Boolean) -> Unit = {},
) {
    val itemFraction = when (LocalWindowSizeClass.current.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> 0.5f
        else -> 1f
    }
    val items = list.items
    val tops = remember(items) { items.filter { it.thread.isTop == true } }
    val threads = remember(items) { items.filter { it.thread.isTop != true } }
    // One line alone is not worth folding
    val foldable = tops.size >= 2
    val folded = foldable && topsFolded
    val peeker = rememberThreadPeeker()
    // Threads ordered by their last reply tell when that was; any other list, when each was posted
    val showReplyTime = when (tab) {
        ForumTab.Latest -> sortType == ForumSort.REPLY_TIME
        ForumTab.Good -> true
        else -> false
    }

    @Composable
    fun Item(item: ForumThreadItem, content: @Composable (ThreadSummary) -> Unit) {
        BlockableContent(
            block = item.block,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth(itemFraction)) { content(item.thread) }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LoadMoreEffect(listState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = { onEvent(ForumEvent.LoadMore) })
        MyLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
        ) {
            // One item, so folding opens and closes the rows in place and what follows moves
            // along with them rather than jumping
            if (tops.isNotEmpty()) {
                item(key = "Tops", contentType = ItemType.Top) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Item(tops.first()) { thread ->
                            TopThreadItem(
                                title = thread.topTitle,
                                onClick = { onEvent(ForumEvent.OpenThread(thread)) },
                                modifier = Modifier.fillMaxWidth(),
                                toggle = if (foldable) {
                                    { FoldTopsButton(count = tops.size, folded = folded, onClick = { onFoldTops(!folded) }) }
                                } else null,
                            )
                        }
                        Reveal(tops.drop(1).takeIf { foldable && !folded }) { rest ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                rest.forEach { item ->
                                    key(item.thread.id) {
                                        Item(item) { thread ->
                                            TopThreadItem(
                                                title = thread.topTitle,
                                                onClick = { onEvent(ForumEvent.OpenThread(thread)) },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            itemsIndexed(
                items = threads,
                key = { _, item -> item.thread.id },
                contentType = { _, item ->
                    val thread = item.thread
                    when {
                        thread.images.size == 1 -> ItemType.SingleMedia
                        thread.images.isNotEmpty() -> ItemType.MultiMedia
                        thread.video != null -> ItemType.Video
                        else -> ItemType.PlainText
                    }
                }
            ) { index, item ->
                // Threads come and go by fading; they don't glide, as the shared header changing
                // height moves the whole list at once
                Box(modifier = Modifier.motionItem(this, placement = false)) {
                    Item(item) { thread ->
                        if (index > 0 || tops.isNotEmpty()) {
                            if (index == 0) Spacer(modifier = Modifier.height(8.dp))
                            VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        FeedCard(
                            thread = thread,
                            onClick = { onEvent(ForumEvent.OpenThread(it)) },
                            onClickReply = { onEvent(ForumEvent.OpenReplies(it)) },
                            onAgree = { onEvent(ForumEvent.Agree(tab, it.id)) },
                            onClickShared = { onEvent(ForumEvent.OpenOriginThread(it)) },
                            onClickUser = { onEvent(ForumEvent.OpenUser(it.id)) },
                            showForum = false,
                            showReplyTime = showReplyTime,
                            onLongPress = peeker?.let { { thread, from, at, origin -> it.peek(thread, from, at, origin) } },
                        )
                    }
                }
            }
            // Not under a list emptied for another order, as it fades away
            if (items.isNotEmpty()) loadMoreFooter(list.append, list.hasMore, onRetry = { onEvent(ForumEvent.LoadMore) })
        }
    }
}

/**
 * Folds and unfolds the pinned threads, staying at the end of the first one: folded, with how many
 * there are; unfolded, the arrow alone, turned up.
 */
@Composable
private fun FoldTopsButton(count: Int, folded: Boolean, onClick: () -> Unit) {
    val description = if (folded) {
        pluralStringResource(id = R.plurals.button_unfold_forum_tops, count = count, count)
    } else {
        stringResource(id = R.string.button_fold_forum_tops)
    }
    val turn by animateFloatAsState(if (folded) 0f else 180f, Motion.settle(), label = "foldTops")
    Row(
        modifier = Modifier
            .clickable(interactionSource = null, indication = PressFeedback.overlay(SmoothCornerShape.Capsule), onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = folded,
            enter = fadeIn(Motion.fadeIn()) + expandHorizontally(Motion.settle(), expandFrom = Alignment.Start),
            exit = fadeOut(Motion.fadeOut()) + shrinkHorizontally(Motion.dismiss(), shrinkTowards = Alignment.Start),
        ) {
            Text(
                text = "$count",
                style = TextStyles.caption,
                color = ExtendedTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Icon(
            imageVector = Symbols.ExpandMore,
            contentDescription = null,
            tint = ExtendedTheme.colors.textSecondary,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = turn },
        )
    }
}

/** What kind a top row is, as a small tinted capsule. */
@Composable
private fun TypeChip(text: String, accent: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = accent,
        modifier = Modifier
            .background(accent.copy(alpha = 0.14f), SmoothCornerShape.Capsule)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private val ThreadSummary.topTitle: String get() = title.takeUnless { it.isBlank() } ?: abstract

@Composable
private fun TopThreadItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    toggle: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TypeChip(stringResource(id = R.string.content_top), pageAccentOrPrimary())
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp
        )
        toggle?.invoke()
    }
}
