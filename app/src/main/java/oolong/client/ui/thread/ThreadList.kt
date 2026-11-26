package oolong.client.ui.thread

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.data.thread.ThreadSort
import oolong.client.media.ImageUtil
import oolong.client.model.LoadPicPageData
import oolong.client.model.PhotoViewData
import oolong.client.model.PicItem
import oolong.client.model.Post
import oolong.client.model.PostContent
import oolong.client.model.knownFirstFloor
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.components.LoadMoreEffect
import oolong.client.ui.components.SharedThreadCard
import oolong.client.ui.components.loadMoreFooter
import oolong.client.ui.designsystem.Container
import oolong.client.ui.designsystem.ContentSwap
import oolong.client.ui.designsystem.EndPull
import oolong.client.ui.designsystem.GlassTopBarItemHeight
import oolong.client.ui.designsystem.LocalPressedByGlass
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.SegmentedControl
import oolong.client.ui.designsystem.ToggleIcon
import oolong.client.ui.designsystem.WavyLoader
import oolong.client.ui.designsystem.floatingGlass
import oolong.client.ui.designsystem.matchedGeometryAnchor
import oolong.client.ui.designsystem.motionItem
import oolong.client.ui.designsystem.plus
import oolong.client.ui.designsystem.pressedByGlass
import oolong.client.ui.designsystem.states.StateScreenScope
import oolong.client.ui.designsystem.theme.CapTextScale
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.util.toastShort
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape

private const val FIRST_POST_KEY = "FirstPost"
private const val POST_KEY_PREFIX = "Post_"

/** The index of the first reply in the list, after the first floor and the reply count header. */
internal const val FIRST_REPLY_INDEX = 1

private const val THREAD_HEADER_KEY = "ThreadHeader"

/** The index of the reply count header in the list, which the bar's switches stand in for once it is under the bar. */
internal const val THREAD_HEADER_INDEX = FIRST_REPLY_INDEX

/**
 * Before the replies are swapped for another list of them (the author's posts, the other order):
 * read past the reply header, the list goes to the start of the replies, the header just under
 * the bar where its switches stand in for it. Otherwise the list stays where it is, kept there by
 * the floor or the header on screen.
 */
internal fun LazyListState.keepRepliesStart() {
    if (firstVisibleItemIndex > THREAD_HEADER_INDEX) requestScrollToItem(THREAD_HEADER_INDEX + 1)
}

/** The key the reply count header reports where it is by, for the bar's switches to follow. */
internal object ReplyHeader

/** The posts of the thread, or the first load or its error in their place. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun ThreadContent(
    state: ThreadState,
    preferences: ThreadPreferences,
    listState: LazyListState,
    padding: PaddingValues,
    onEvent: (ThreadEvent) -> Unit,
    chromeEnd: () -> Int = { 0 },
) {
    val posts = state.posts
    val firstPost = state.firstPost ?: state.preview?.knownFirstFloor()
    val loadingFirstTime = posts.refresh == LoadStatus.Loading && posts.items.isEmpty()
    val error = (posts.refresh as? LoadStatus.Error)?.error

    // Loading, failing and the thread replace one another as a whole
    val face = when {
        firstPost != null || posts.items.isNotEmpty() -> ThreadFace.Posts
        error != null -> ThreadFace.Error
        else -> ThreadFace.Loading
    }
    ContentSwap(targetState = face, modifier = Modifier.fillMaxSize()) { shown ->
        when (shown) {
            ThreadFace.Posts -> ThreadPosts(state, preferences, listState, padding, onEvent, chromeEnd)
            else -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (shown == ThreadFace.Error && error != null) {
                    remember { StateScreenScope { onEvent(ThreadEvent.Retry) } }
                        .ErrorScreen(error = error, modifier = Modifier.fillMaxSize())
                } else if (shown == ThreadFace.Loading) {
                    WavyLoader()
                }
            }
        }
    }
}

private enum class ThreadFace { Loading, Error, Posts }

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ThreadPosts(
    state: ThreadState,
    preferences: ThreadPreferences,
    listState: LazyListState,
    padding: PaddingValues,
    onEvent: (ThreadEvent) -> Unit,
    chromeEnd: () -> Int,
) {
    val posts = state.posts
    val firstPost = state.firstPost ?: state.preview?.knownFirstFloor()
    val loadingFirstTime = posts.refresh == LoadStatus.Loading && posts.items.isEmpty()
    val error = (posts.refresh as? LoadStatus.Error)?.error

    TrackReadPosition(listState, firstPost?.id, onEvent)

    val newestFirst = state.query.sort == ThreadSort.NewestFirst
    // The first load shows its own indicator in the list
    val pullRefreshing = !loadingFirstTime &&
        (if (newestFirst) posts.prepend == LoadStatus.Loading else posts.refresh == LoadStatus.Loading)
    val options = PostCardOptions(
        threadAuthorId = state.thread?.author?.id ?: firstPost?.author?.id ?: 0L,
        hideBlocked = preferences.hideBlocked,
        hideReply = preferences.hideReply,
    )
    val viewerId = state.viewer.user?.id
    val photoViewData = rememberPhotoViewData(state)

    // Until the first page of replies is in, the end of the list says how that load is going
    val firstLoad = posts.items.isEmpty() && (loadingFirstTime || error != null)
    LoadMoreEffect(listState, enabled = posts.hasMore && posts.append == LoadStatus.Idle, onLoadMore = { onEvent(ThreadEvent.LoadMore) })
    PullRefresh(
        refreshing = pullRefreshing,
        onRefresh = { onEvent(if (newestFirst) ThreadEvent.LoadPrevious else ThreadEvent.Refresh) },
        enabled = posts.hasPrevious || newestFirst,
        indicatorTop = padding.calculateTopPadding(),
        endPull = when {
            firstLoad || posts.hasMore -> EndPull.None
            newestFirst -> EndPull.Tease
            else -> EndPull.Action { onEvent(ThreadEvent.LoadLatest) }
        },
    ) {
        MyLazyColumn(
            state = listState,
            contentPadding = padding.plus(PaddingValues(bottom = 8.dp)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = FIRST_POST_KEY) {
                if (firstPost != null) {
                    FirstPost(state, firstPost, options, viewerId, photoViewData, onEvent)
                }
            }
            if (state.thread != null) {
                item(key = THREAD_HEADER_KEY) { ThreadHeader(state, onEvent) }
            }
            if (posts.hasPrevious && !newestFirst) {
                item(key = "LoadPrevious") { LoadPreviousButton(loading = posts.prepend == LoadStatus.Loading, onEvent = onEvent) }
            }
            itemsIndexed(
                items = posts.items,
                key = { _, it -> "$POST_KEY_PREFIX${it.post.id}" },
            ) { index, item ->
                // Floors come and go by fading, as on switching to the author's or the other order;
                // they don't glide, as their heights change all the time with pictures and replies
                Container(modifier = Modifier.motionItem(this, placement = false).landedGlow(item.post.id, LocalLandedFloor.current)) {
                    ThreadFloor(
                        item = item,
                        options = options,
                        onEvent = onEvent,
                        listState = listState,
                        key = "$POST_KEY_PREFIX${item.post.id}",
                        stickBelow = chromeEnd,
                        startsRule = index == 0,
                        isStoreMark = item.post.id == state.thread?.storeMarkPostId,
                        // The thread's author may delete anyone's reply in it
                        canDelete = viewerId != null && (item.post.author.id == viewerId || options.threadAuthorId == viewerId),
                        photoViewData = { photoViewData(item.post, it) },
                        replies = state.floorReplies[item.post.id],
                        collapsible = true,
                    )
                }
            }
            loadMoreFooter(
                append = if (firstLoad) posts.refresh else posts.append,
                hasMore = posts.hasMore,
                pullToLoadLatest = !newestFirst && !firstLoad,
                // While the replies load anew, as after switching to the author's posts or the
                // other order, the floor and the reply header can stay where they were
                fillScreen = firstLoad && state.thread != null,
                onRetry = {
                    onEvent(
                        when {
                            firstLoad -> ThreadEvent.Retry
                            posts.hasMore -> ThreadEvent.LoadMore
                            else -> ThreadEvent.LoadLatest
                        }
                    )
                },
            )
        }
    }
}

@Composable
private fun FirstPost(
    state: ThreadState,
    post: Post,
    options: PostCardOptions,
    viewerId: Long?,
    photoViewData: (Post, PostContent.Image) -> PhotoViewData?,
    onEvent: (ThreadEvent) -> Unit,
) {
    Container {
        Column {
            ThreadFirstPost(
                post = post,
                state = state,
                options = options,
                isStoreMark = post.id == state.thread?.storeMarkPostId,
                canDelete = viewerId != null && post.author.id == viewerId,
                photoViewData = { photoViewData(post, it) },
                onEvent = onEvent,
            )
            state.thread?.shared?.let { shared ->
                SharedThreadCard(
                    shared = shared,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                        .background(ExtendedTheme.colors.inset, SmoothCornerShape(6.dp))
                        .clickable(interactionSource = null, indication = PressFeedback.overlay(SmoothCornerShape(6.dp))) {
                            onEvent(ThreadEvent.OpenOriginThread(shared.id, shared.forumId))
                        }
                        .padding(16.dp)
                )
            }
        }
    }
}

/**
 * The reply count, and how the replies are shown: newest first or not, and all posts or the thread
 * author's only. It is content and scrolls away; once it is under the bar, the bar's switches
 * ([ThreadFilterSwitches]) stand in for it.
 */
@Composable
internal fun ThreadHeader(
    state: ThreadState,
    onEvent: (ThreadEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Container(modifier = modifier) {
        Row(
            modifier = Modifier
                .matchedGeometryAnchor(ReplyHeader)
                .background(ExtendedTheme.colors.background)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.title_thread_header, "${(state.thread?.replyCount ?: 1) - 1}"),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ExtendedTheme.colors.text,
            )
            Spacer(modifier = Modifier.weight(1f))
            // Controls, so their text grows only as far as the bars' does, leaving the count its room
            CapTextScale {
                SortSwitch(
                    newestFirst = state.query.sort == ThreadSort.NewestFirst,
                    onEvent = onEvent,
                    modifier = Modifier.padding(end = 8.dp),
                )
                SegmentedControl(
                    options = persistentListOf(stringResource(R.string.text_all), stringResource(R.string.title_see_lz)),
                    selected = if (state.query.seeLz) 1 else 0,
                    onSelect = { onEvent(ThreadEvent.SetSeeLz(it == 1)) },
                    // Both options as wide as the wider one
                    modifier = Modifier.width(IntrinsicSize.Max),
                )
            }
        }
    }
}

/** The header's newest-first switch: as tall as the switch beside it, its track darker while on. */
@Composable
private fun SortSwitch(newestFirst: Boolean, onEvent: (ThreadEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = ExtendedTheme.colors
    val track by animateColorAsState(if (newestFirst) colors.text.copy(alpha = 0.12f) else colors.chip, Motion.snap(), label = "sortTrack")
    FilterSwitch(
        checked = newestFirst,
        label = stringResource(R.string.title_sort),
        notice = stringResource(if (newestFirst) R.string.toast_oldest_first else R.string.toast_newest_first),
        onToggle = { onEvent(ThreadEvent.ToggleSort) },
        modifier = modifier
            .size(SortSwitchSize)
            .background(track, CircleShape),
    ) {
        SortIcon(flipped = newestFirst, tint = if (newestFirst) colors.text else colors.textSecondary)
    }
}

private val SortSwitchSize = 38.dp

@Composable
private fun LoadPreviousButton(loading: Boolean, onEvent: (ThreadEvent) -> Unit) {
    Container {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !loading) { onEvent(ThreadEvent.LoadPrevious) }
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            // While the page above loads, the button refocuses into the loader in its place
            Refocus(targetState = loading) { now ->
                if (now) {
                    WavyLoader(modifier = Modifier.size(24.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Symbols.AlignVerticalTop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(id = R.string.btn_load_previous),
                            color = ExtendedTheme.colors.text,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/** Where [postId] is in the list, or null when it isn't loaded. */
internal fun ThreadState.indexOfPost(postId: Long): Int? {
    if (postId == firstPost?.id) return 0
    val index = posts.items.indexOfFirst { it.post.id == postId }
    if (index == -1) return null
    val loadPrevious = if (posts.hasPrevious && query.sort != ThreadSort.NewestFirst) 1 else 0
    return FIRST_REPLY_INDEX + 1 + loadPrevious + index
}

/** Reports the last post on screen as the list scrolls, or the first floor when no reply is. */
@Composable
private fun TrackReadPosition(listState: LazyListState, firstPostId: Long?, onEvent: (ThreadEvent) -> Unit) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentFirstPostId by rememberUpdatedState(firstPostId)
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull { (it.key as? String)?.startsWith(POST_KEY_PREFIX) == true }
                ?.let { (it.key as String).removePrefix(POST_KEY_PREFIX).toLongOrNull() }
                ?: currentFirstPostId?.takeIf { it != 0L }
        }
            .distinctUntilChanged()
            .collect { postId -> if (postId != null) currentOnEvent(ThreadEvent.ReadUpTo(postId)) }
    }
}

/** What the image viewer needs to open an image of a post and page through the thread's images. */
@Composable
private fun rememberPhotoViewData(state: ThreadState): (Post, PostContent.Image) -> PhotoViewData? {
    val context = LocalContext.current
    val thread = state.thread
    val seeLz = state.query.seeLz
    return remember(thread?.id, thread?.forum, seeLz) {
        { post, image ->
            thread?.let {
                PhotoViewData(
                    data = LoadPicPageData(
                        forumId = it.forum.id,
                        forumName = it.forum.name,
                        threadId = it.id,
                        postId = post.id,
                        objType = "pb",
                        picId = image.id,
                        picIndex = 1,
                        seeLz = seeLz,
                        originUrl = image.originUrl,
                    ),
                    picItems = persistentListOf(
                        PicItem(
                            picId = image.id,
                            picIndex = 1,
                            url = ImageUtil.getUrl(context, true, image.originUrl, *image.urls.toTypedArray()),
                            originUrl = image.originUrl,
                            showOriginBtn = image.showOriginButton,
                            originSize = image.originSize,
                            postId = post.id,
                        )
                    ),
                )
            }
        }
    }
}

/**
 * Only the thread author's posts, and newest first, as two switches on a glass capsule in the top
 * bar: there once the reply header, which has the same, is under the bar ([shown] from 0 to 1),
 * so they can be changed anywhere in the thread. On, a switch sits on a pill in the page's colour
 * turned dark, and its icon fills or turns over.
 */
@Composable
internal fun ThreadFilterSwitches(
    seeLz: Boolean,
    newestFirst: Boolean,
    onEvent: (ThreadEvent) -> Unit,
    shown: () -> Float,
) {
    val colors = ExtendedTheme.colors
    val visible by remember { derivedStateOf { shown() > 0.05f } }
    Row(
        modifier = Modifier
            .graphicsLayer {
                val fraction = shown()
                alpha = fraction
                val scale = 0.8f + 0.2f * fraction
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(1f, 0.5f)
            }
            .height(GlassTopBarItemHeight)
            .floatingGlass(SmoothCornerShape.Capsule, pressable = true)
            .padding(SwitchInset)
            // Hidden, they take no taps, nor a screen reader's attention
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
        horizontalArrangement = Arrangement.spacedBy(SwitchGap),
    ) {
        val pill = if (colors.background.alpha < 1f) colors.sheet else colors.background
        // Their glass answers presses on them as one piece
        CompositionLocalProvider(LocalPressedByGlass provides true) {
            BarSwitch(
                checked = seeLz,
                enabled = visible,
                label = stringResource(R.string.title_see_lz),
                notice = stringResource(if (seeLz) R.string.toast_all_posts else R.string.title_see_lz),
                onToggle = { onEvent(ThreadEvent.SetSeeLz(!seeLz)) },
            ) { on ->
                // The icon turns with the round behind it, rather than flipping before it has faded in
                val boxTint by animateColorAsState(if (on) pill else colors.textSecondary, Motion.snap(), label = "lzBox")
                val lettersTint by animateColorAsState(if (on) colors.text else colors.textSecondary, Motion.snap(), label = "lzLetters")
                Box(contentAlignment = Alignment.Center) {
                    ToggleIcon(checked = on, on = LzIcon.BoxFill, off = LzIcon.Box, contentDescription = null, tint = boxTint)
                    Icon(LzIcon.Letters, contentDescription = null, tint = lettersTint)
                }
            }
            BarSwitch(
                checked = newestFirst,
                enabled = visible,
                label = stringResource(R.string.title_sort),
                notice = stringResource(if (newestFirst) R.string.toast_oldest_first else R.string.toast_newest_first),
                onToggle = { onEvent(ThreadEvent.ToggleSort) },
            ) { on ->
                val sortTint by animateColorAsState(if (on) pill else colors.textSecondary, Motion.snap(), label = "sortTint")
                SortIcon(flipped = on, tint = sortTint)
            }
        }
    }
}

/** One of the bar's switches: a 40dp circle, on a pill in the text colour while [checked]. */
@Composable
private fun BarSwitch(
    checked: Boolean,
    enabled: Boolean,
    label: String,
    notice: String,
    onToggle: () -> Unit,
    icon: @Composable (on: Boolean) -> Unit,
) {
    val colors = ExtendedTheme.colors
    val pill by animateColorAsState(if (checked) colors.text else colors.text.copy(alpha = 0f), Motion.snap(), label = "barSwitch")
    FilterSwitch(
        checked = checked,
        label = label,
        notice = notice,
        onToggle = onToggle,
        enabled = enabled,
        modifier = Modifier
            .size(GlassTopBarItemHeight - SwitchInset * 2)
            .background(pill, CircleShape),
    ) {
        icon(checked)
    }
}

/**
 * A switch for how the replies are shown, as an icon: a tap turns it over and says what the
 * replies now show in a [notice]; a long press says what it is, in [label], the way the system's
 * bar buttons do, and switches nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterSwitch(
    checked: Boolean,
    label: String,
    notice: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                interactionSource = null,
                // On the bar's glass, which answers the press
                indication = if (pressedByGlass()) null else PressFeedback.overlay(CircleShape),
                onClickLabel = label,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    context.toastShort(label)
                },
                onClick = {
                    haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    context.toastShort(notice)
                    onToggle()
                },
            )
            .semantics {
                contentDescription = label
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** The order icon, turned upside down for newest first. */
@Composable
internal fun SortIcon(flipped: Boolean, tint: Color) {
    val turn by animateFloatAsState(if (flipped) 1f else 0f, Motion.snap(), label = "sortTurn")
    Icon(
        imageVector = Symbols.Sort,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.graphicsLayer { scaleY = 1f - 2f * turn },
    )
}

private val SwitchInset = 4.dp
private val SwitchGap = 0.dp

/**
 * The floor a jump landed on, as after sending a reply or opening a notification, which lights
 * up once and fades (design-system.md §7.2) so the eye finds it on a page that changed at once.
 */
@Stable
internal class LandedFloor {
    var postId by mutableStateOf<Long?>(null)
        private set
    val glow = Animatable(0f)

    suspend fun land(postId: Long) {
        this.postId = postId
        glow.snapTo(0f)
        glow.animateTo(1f, Motion.fadeIn())
        delay(LandedHoldMillis)
        glow.animateTo(0f, Motion.settle())
        this.postId = null
    }
}

internal val LocalLandedFloor = staticCompositionLocalOf<LandedFloor?> { null }

/** Lights this floor while it is the one [landed] on. */
@Composable
internal fun Modifier.landedGlow(postId: Long, landed: LandedFloor?): Modifier {
    if (landed == null || landed.postId != postId) return this
    val color = ExtendedTheme.colors.accent.copy(alpha = LandedAlpha)
    return drawWithContent {
        drawContent()
        drawRect(color, alpha = landed.glow.value.coerceIn(0f, 1f))
    }
}

private const val LandedHoldMillis = 500L
private const val LandedAlpha = 0.12f
