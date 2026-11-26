package oolong.client.ui.likes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.model.ForumSummary
import oolong.client.model.LikedReply
import oolong.client.model.PostContent
import oolong.client.model.ThreadSummary
import oolong.client.model.User
import oolong.client.navigation.ForumKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.SubPostsKey
import oolong.client.navigation.ThreadKey
import oolong.client.navigation.UserProfileKey
import oolong.client.ui.components.AuthorName
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.components.FeedCard
import oolong.client.ui.components.KeepTopThroughRefresh
import oolong.client.ui.components.LoadMoreEffect
import oolong.client.ui.components.MetaText
import oolong.client.ui.components.PbContentText
import oolong.client.ui.components.ThreadAgreeBtn
import oolong.client.ui.components.UserHeader
import oolong.client.ui.components.loadMoreFooter
import oolong.client.ui.components.relativeTime
import oolong.client.ui.components.rememberTextLinkActions
import oolong.client.ui.components.toAnnotatedString
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.Container
import oolong.client.ui.designsystem.EndPull
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTab
import oolong.client.ui.designsystem.GlassTabRow
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.Sizes
import oolong.client.ui.designsystem.TabText
import oolong.client.ui.designsystem.UserHeader
import oolong.client.ui.designsystem.VerticalDivider
import oolong.client.ui.designsystem.pagerPageFocus
import oolong.client.ui.designsystem.states.DefaultEmptyScreen
import oolong.client.ui.designsystem.states.DefaultLoadingScreen
import oolong.client.ui.designsystem.states.StateScreen
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.util.StringUtil
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.PressFeedback

@Composable
fun LikesPage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: LikesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LikesScreen(
        state = state,
        onBack = { navigator.navigateUp() },
        onShow = viewModel::open,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onAgreeThread = { viewModel.agree(it) },
        onAgreeReply = { viewModel.agree(it) },
        onOpenThread = { navigator.navigate(ThreadKey(it.id, it.forum.id)) },
        onOpenThreadReplies = { navigator.navigate(ThreadKey(it.id, it.forum.id, scrollToReply = true)) },
        onOpenReply = { reply ->
            val floorId = reply.floorId
            if (floorId != null) {
                navigator.navigate(
                    SubPostsKey(
                        threadId = reply.threadId,
                        forumId = reply.forum.id,
                        postId = floorId,
                        subPostId = reply.postId,
                        loadFromSubPost = true,
                    )
                )
            } else {
                navigator.navigate(ThreadKey(reply.threadId, reply.forum.id, postId = reply.postId, scrollToReply = true))
            }
        },
        onOpenThreadOf = { navigator.navigate(ThreadKey(it.threadId, it.forum.id)) },
        onOpenUser = { navigator.navigate(UserProfileKey(it.id)) },
        onOpenForum = { navigator.navigate(ForumKey(it.name)) },
    )
}

/**
 * What the user liked: threads and replies, in two tabs that are the bar's title, the latest liked
 * first. Each keeps its like button, which takes the like back.
 */
@Composable
fun LikesScreen(
    state: LikesState,
    onBack: () -> Unit,
    /** [LikesTab] came into view. */
    onShow: (LikesTab) -> Unit,
    onRefresh: (LikesTab) -> Unit,
    onLoadMore: (LikesTab) -> Unit,
    onAgreeThread: (ThreadSummary) -> Unit,
    onAgreeReply: (LikedReply) -> Unit,
    onOpenThread: (ThreadSummary) -> Unit,
    onOpenThreadReplies: (ThreadSummary) -> Unit,
    onOpenReply: (LikedReply) -> Unit,
    /** The thread a liked reply was made in. */
    onOpenThreadOf: (LikedReply) -> Unit,
    onOpenUser: (User) -> Unit,
    onOpenForum: (ForumSummary) -> Unit,
    initialTab: LikesTab = LikesTab.Threads,
) {
    val pagerState = rememberPagerState(initialPage = initialTab.ordinal) { LikesTab.entries.size }
    val coroutineScope = rememberCoroutineScope()
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = {
                    GlassTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        pagerState = pagerState,
                        onSelect = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                    ) {
                        LikesTab.entries.forEachIndexed { index, tab ->
                            GlassTab(
                                text = {
                                    TabText(
                                        text = stringResource(
                                            id = when (tab) {
                                                LikesTab.Threads -> R.string.likes_tab_threads
                                                LikesTab.Replies -> R.string.likes_tab_replies
                                            }
                                        ),
                                        selected = pagerState.currentPage == index,
                                    )
                                },
                                selected = pagerState.currentPage == index,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            )
                        }
                    }
                },
                centerTitle = true,
                navigationIcon = { BackNavigationIcon(onBackPressed = onBack) },
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { contentPaddings ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { it },
            verticalAlignment = Alignment.Top,
        ) { page ->
            // Pages off screen keep a keyboard's focus out, or Tab would turn the pager to them
            Box(modifier = Modifier.pagerPageFocus(showing = page == pagerState.currentPage), propagateMinConstraints = true) {
                val tab = LikesTab.entries[page]
                LaunchedEffect(tab) { onShow(tab) }
                when (tab) {
                    LikesTab.Threads -> LikesList(
                        list = state.threads,
                        contentPadding = contentPaddings,
                        onRefresh = { onRefresh(tab) },
                        onLoadMore = { onLoadMore(tab) },
                    ) { threads ->
                        itemsIndexed(items = threads, key = { _, thread -> thread.id }) { index, thread ->
                            Container {
                                Column {
                                    FeedCard(
                                        thread = thread,
                                        onClick = onOpenThread,
                                        onAgree = onAgreeThread,
                                        onClickReply = onOpenThreadReplies,
                                        onClickUser = onOpenUser,
                                        onClickForum = onOpenForum,
                                    )
                                    if (index < threads.lastIndex) VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }

                    LikesTab.Replies -> LikesList(
                        list = state.replies,
                        contentPadding = contentPaddings,
                        onRefresh = { onRefresh(tab) },
                        onLoadMore = { onLoadMore(tab) },
                    ) { replies ->
                        itemsIndexed(items = replies, key = { _, reply -> reply.postId }) { index, reply ->
                            Container {
                                Column {
                                    LikedReplyItem(
                                        reply = reply,
                                        onOpen = { onOpenReply(reply) },
                                        onOpenThread = { onOpenThreadOf(reply) },
                                        onOpenUser = { onOpenUser(reply.author) },
                                        onAgree = { onAgreeReply(reply) },
                                    )
                                    // Divided like feed cards
                                    if (index < replies.lastIndex) VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One of the lists, which pulls down to refresh and loads more at its end. */
@Composable
private fun <T> LikesList(
    list: PagerState<T>,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    items: LazyListScope.(ImmutableList<T>) -> Unit,
) {
    val refreshing = list.waiting
    val error = (list.refresh as? LoadStatus.Error)?.error
    val lazyListState = rememberLazyListState()
    LoadMoreEffect(lazyListState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = onLoadMore)
    KeepTopThroughRefresh(lazyListState, refreshing)
    StateScreen(
        isEmpty = list.items.isEmpty(),
        isError = error != null,
        isLoading = refreshing,
        modifier = Modifier.fillMaxSize(),
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error, modifier = Modifier.padding(contentPadding)) },
        loadingScreen = { Box(modifier = Modifier.padding(contentPadding)) { DefaultLoadingScreen() } },
        emptyScreen = { Box(modifier = Modifier.padding(contentPadding)) { DefaultEmptyScreen() } },
    ) {
        PullRefresh(
            refreshing = list.refresh == LoadStatus.Loading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorTop = contentPadding.calculateTopPadding(),
            endPull = if (list.hasMore) EndPull.None else EndPull.Tease,
        ) {
            MyLazyColumn(state = lazyListState, contentPadding = contentPadding) {
                items(list.items)
                loadMoreFooter(list.append, list.hasMore, onLoadMore)
            }
        }
    }
}

/**
 * A liked reply: who made it, when and in which forum, with its like button; what it says, cut
 * after a few lines; then the title of its thread, in the grey block replies quote in. The reply
 * opens where it is in the thread, the title the thread from its start.
 */
@Composable
private fun LikedReplyItem(
    reply: LikedReply,
    onOpen: () -> Unit,
    onOpenThread: () -> Unit,
    onOpenUser: () -> Unit,
    onAgree: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val author = reply.author
        val time = relativeTime(reply.time)
        UserHeader(
            avatar = { Avatar(data = StringUtil.getAvatarUrl(author.portrait), size = Sizes.Small, contentDescription = null) },
            name = { AuthorName(text = AnnotatedString(author.displayName)) },
            onClick = onOpenUser,
            desc = {
                MetaText(
                    text = if (reply.forum.name.isEmpty()) {
                        time
                    } else {
                        stringResource(id = R.string.likes_reply_meta, time, stringResource(id = R.string.title_forum, reply.forum.name))
                    }
                )
            },
        ) {
            // Its count ends at the item's edge, as a card's does
            Box(modifier = Modifier.offset(x = AgreeEdgeOffset)) {
                ThreadAgreeBtn(hasAgree = reply.agree.agreed, agreeNum = reply.agree.count.toInt(), onClick = onAgree)
            }
        }
        PbContentText(
            text = rememberReplyText(reply.content),
            fontSize = 15.sp,
            color = ExtendedTheme.colors.text,
            maxLines = ReplyMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = reply.threadTitle,
            modifier = Modifier
                .fillMaxWidth()
                .background(ExtendedTheme.colors.chip, Corners.sm)
                .clickable(interactionSource = null, indication = PressFeedback.overlay(Corners.sm), onClick = onOpenThread)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            color = ExtendedTheme.colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = ThreadTitleMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The text of a reply as one run, its emoticons inline and its mentions and links as links. */
@Composable
private fun rememberReplyText(content: ImmutableList<PostContent>): AnnotatedString {
    val accent = ExtendedTheme.colors.accent
    val videoTag = stringResource(id = R.string.tag_video)
    val warning = MaterialTheme.colors.error
    val actualHost = stringResource(id = R.string.link_actual_host)
    val links = rememberTextLinkActions()
    return remember(content, accent, videoTag, warning, actualHost, links) {
        buildAnnotatedString {
            content.forEach { block ->
                if (block is PostContent.Text) append(block.spans.toAnnotatedString(accent, videoTag, warning, actualHost, links))
            }
        }
    }
}

/** The padding inside the like button, which its count is moved out by. */
private val AgreeEdgeOffset = 8.dp
private const val ReplyMaxLines = 6
private const val ThreadTitleMaxLines = 2
