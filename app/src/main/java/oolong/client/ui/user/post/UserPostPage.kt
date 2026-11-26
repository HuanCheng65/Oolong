package oolong.client.ui.user.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.collections.immutable.ImmutableList
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.model.ThreadSummary
import oolong.client.model.UserPost
import oolong.client.model.UserReplies
import oolong.client.model.UserThread
import oolong.client.navigation.ForumKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.SubPostsKey
import oolong.client.navigation.ThreadKey
import oolong.client.navigation.UserProfileKey
import oolong.client.ui.components.Card
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.components.FeedCard
import oolong.client.ui.components.FeedCardPlaceholder
import oolong.client.ui.components.LoadMoreEffect
import oolong.client.ui.components.TipScreen
import oolong.client.ui.components.UserHeader
import oolong.client.ui.components.loadMoreFooter
import oolong.client.ui.components.relativeTime
import oolong.client.ui.designsystem.Button
import oolong.client.ui.designsystem.Container
import oolong.client.ui.designsystem.DecorativeLottie
import oolong.client.ui.designsystem.EndPull
import oolong.client.ui.designsystem.LocalChromePadding
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.UserHeader
import oolong.client.ui.designsystem.chromePadding
import oolong.client.ui.designsystem.states.StateScreen
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun UserPostPage(
    list: PagerState<UserPost>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onAgree: (ThreadSummary) -> Unit,
    hidden: Boolean = false,
    fluid: Boolean = false,
    enablePullRefresh: Boolean = false,
) {
    val navigator = LocalNavigator.current
    val posts = list.items
    val isRefreshing = list.waiting
    val error = (list.refresh as? LoadStatus.Error)?.error
    val hidePost = hidden

    StateScreen(
        modifier = Modifier.fillMaxSize(),
        isEmpty = posts.isEmpty(),
        isError = error != null,
        isLoading = isRefreshing,
        onReload = onRefresh,
        loadingScreen = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .chromePadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column {
                    repeat(4) {
                        FeedCardPlaceholder()
                    }
                }
            }
        },
        errorScreen = { ErrorScreen(error = error, modifier = Modifier.chromePadding()) },
        emptyScreen = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .chromePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (hidePost) {
                    TipScreen(
                        title = { Text(text = stringResource(id = R.string.title_user_hide_post)) },
                        image = {
                            val composition by rememberLottieComposition(
                                LottieCompositionSpec.RawRes(
                                    R.raw.lottie_hide
                                )
                            )
                            DecorativeLottie(
                                composition = composition,
                                loops = true,
                                modifier = Modifier
                                    .padding(vertical = 16.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(2.5f)
                            )
                        },
                        scrollable = false,
                    )
                } else {
                    TipScreen(
                        title = { Text(text = stringResource(id = R.string.title_empty)) },
                        image = {
                            val composition by rememberLottieComposition(
                                LottieCompositionSpec.RawRes(
                                    R.raw.lottie_empty_box
                                )
                            )
                            DecorativeLottie(
                                composition = composition,
                                loops = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f)
                            )
                        },
                        actions = {
                            if (canReload) {
                                Button(onClick = { reload() }) {
                                    Text(text = stringResource(id = R.string.btn_refresh))
                                }
                            }
                        },
                        scrollable = false,
                    )
                }
            }
        },
    ) {
        val lazyListState = rememberLazyListState()

        LoadMoreEffect(lazyListState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = onLoadMore)
        PullRefresh(
            refreshing = list.refresh == LoadStatus.Loading,
            onRefresh = onRefresh,
            enabled = enablePullRefresh,
            indicatorTop = LocalChromePadding.current.calculateTopPadding(),
            endPull = if (list.hasMore) EndPull.None else EndPull.Tease,
        ) {
            UserPostList(
                data = posts,
                fluid = fluid,
                lazyListState = lazyListState,
                onClickItem = { threadId, postId, isSubPost ->
                    if (postId == null) {
                        navigator.navigate(ThreadKey(threadId))
                    } else {
                        if (isSubPost) {
                            navigator.navigate(
                                SubPostsKey(
                                    threadId = threadId,
                                    subPostId = postId,
                                    loadFromSubPost = true
                                )
                            )
                        } else {
                            navigator.navigate(
                                ThreadKey(
                                    threadId,
                                    postId = postId,
                                    scrollToReply = true
                                )
                            )
                        }
                    }
                },
                onAgreeItem = onAgree,
                onClickReply = {
                    navigator.navigate(
                        ThreadKey(
                            it.id,
                            it.forum.id,
                            scrollToReply = true
                        )
                    )
                },
                onClickUser = {
                    navigator.navigate(UserProfileKey(it))
                },
                onClickForum = {
                    navigator.navigate(ForumKey(it))
                },
                onClickOriginThread = {
                    navigator.navigate(ThreadKey(it))
                },
                footer = { loadMoreFooter(list.append, list.hasMore, onLoadMore) },
            )
        }
    }
}

@Composable
private fun UserPostList(
    data: ImmutableList<UserPost>,
    fluid: Boolean = false,
    lazyListState: LazyListState = rememberLazyListState(),
    onClickItem: (threadId: Long, postId: Long?, isSubPost: Boolean) -> Unit = { _, _, _ -> },
    onAgreeItem: (ThreadSummary) -> Unit = {},
    onClickReply: (ThreadSummary) -> Unit = {},
    onClickUser: (id: Long) -> Unit = {},
    onClickForum: (name: String) -> Unit = {},
    onClickOriginThread: (threadId: Long) -> Unit = {},
    footer: LazyListScope.() -> Unit = {},
) {
    MyLazyColumn(state = lazyListState, contentPadding = LocalChromePadding.current) {
        items(
            items = data,
            key = { it.key }
        ) { itemData ->
            Container(fluid = fluid) {
                UserPostItem(
                    post = itemData,
                    onClick = onClickItem,
                    onAgree = onAgreeItem,
                    onClickReply = onClickReply,
                    onClickUser = onClickUser,
                    onClickForum = onClickForum,
                    onClickOriginThread = onClickOriginThread,
                )
            }
        }
        footer()
    }
}

@Composable
fun UserPostItem(
    post: UserPost,
    onAgree: (ThreadSummary) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (threadId: Long, postId: Long?, isSubPost: Boolean) -> Unit = { _, _, _ -> },
    onClickReply: (ThreadSummary) -> Unit = {},
    onClickUser: (id: Long) -> Unit = {},
    onClickForum: (name: String) -> Unit = {},
    onClickOriginThread: (threadId: Long) -> Unit = {},
) {
    when (post) {
        is UserThread -> FeedCard(
            thread = post.thread,
            onClick = { onClick(it.id, null, false) },
            onAgree = onAgree,
            modifier = modifier,
            onClickReply = onClickReply,
            onClickUser = { onClickUser(it.id) },
            onClickForum = { onClickForum(it.name) },
            onClickShared = { onClickOriginThread(it.id) },
        )

        is UserReplies -> UserRepliesItem(post, onClick, onClickUser, onClickOriginThread, modifier)
    }
}

/** The user's replies in a thread, each opening where it is, over the thread's title. */
@Composable
private fun UserRepliesItem(
    post: UserReplies,
    onClick: (threadId: Long, postId: Long?, isSubPost: Boolean) -> Unit,
    onClickUser: (id: Long) -> Unit,
    onClickOriginThread: (threadId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    run {
        Card(
            header = {
                UserHeader(
                    nameProvider = { post.author.name },
                    nameShowProvider = { post.author.nameShow },
                    portraitProvider = { post.author.portrait },
                    onClick = {
                        onClickUser(post.author.id)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            },
            content = {
                Column {
                    post.replies.fastForEach {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onClick(
                                        post.threadId,
                                        it.postId,
                                        it.isSubPost
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = it.text,
                                style = MaterialTheme.typography.body1,
                                color = ExtendedTheme.colors.text,
                            )

                            Text(
                                text = relativeTime(it.time),
                                style = MaterialTheme.typography.caption,
                                color = ExtendedTheme.colors.textSecondary,
                            )
                        }
                    }
                }

                Text(
                    text = post.threadTitle,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .background(ExtendedTheme.colors.inset, SmoothCornerShape(6.dp))
                        .clickable(interactionSource = null, indication = PressFeedback.overlay(SmoothCornerShape(6.dp))) {
                            onClickOriginThread(post.threadId)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.body2,
                )
            },
            modifier = modifier,
            contentPadding = PaddingValues(0.dp),
        )
    }
}
