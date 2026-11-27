package oolong.client.ui.main.notifications.likes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.model.Like
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.SubPostsKey
import oolong.client.navigation.ThreadKey
import oolong.client.navigation.UserProfileKey
import oolong.client.ui.components.AuthorName
import oolong.client.ui.components.EmoticonText
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.components.KeepTopThroughRefresh
import oolong.client.ui.components.LoadMoreEffect
import oolong.client.ui.components.MetaText
import oolong.client.ui.components.loadMoreFooter
import oolong.client.ui.components.relativeTime
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.Container
import oolong.client.ui.designsystem.EndPull
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.Sizes
import oolong.client.ui.designsystem.UserHeader
import oolong.client.ui.designsystem.VerticalDivider
import oolong.client.ui.designsystem.plus
import oolong.client.ui.designsystem.states.DefaultEmptyScreen
import oolong.client.ui.designsystem.states.DefaultLoadingScreen
import oolong.client.ui.designsystem.states.StateScreen
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.util.StringUtil
import oolong.core.designsystem.theme.Corners

/** The likes on the user's posts, one row per post and day. */
@Composable
fun ReceivedLikesListPage(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ReceivedLikesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    ReceivedLikesList(
        list = state,
        contentPadding = contentPadding,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onOpen = { like ->
            if (like.inPost) {
                navigator.navigate(SubPostsKey(threadId = like.threadId, subPostId = like.postId, loadFromSubPost = true))
            } else {
                navigator.navigate(ThreadKey(threadId = like.threadId, postId = like.postId))
            }
        },
        onOpenUser = { navigator.navigate(UserProfileKey(it)) },
    )
}

@Composable
fun ReceivedLikesList(
    list: PagerState<LikeGroup>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (Like) -> Unit,
    onOpenUser: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val refreshing = list.refresh == LoadStatus.Loading
    val error = (list.refresh as? LoadStatus.Error)?.error
    val lazyListState = rememberLazyListState()
    LoadMoreEffect(lazyListState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = onLoadMore)
    KeepTopThroughRefresh(lazyListState, refreshing)
    StateScreen(
        isEmpty = list.items.isEmpty(),
        isError = error != null,
        isLoading = refreshing,
        modifier = modifier.fillMaxSize(),
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error, modifier = Modifier.padding(contentPadding)) },
        loadingScreen = { Box(modifier = Modifier.padding(contentPadding)) { DefaultLoadingScreen() } },
        emptyScreen = { Box(modifier = Modifier.padding(contentPadding)) { DefaultEmptyScreen() } },
    ) {
        PullRefresh(
            refreshing = refreshing,
            revealUnpulled = true,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorTop = contentPadding.calculateTopPadding(),
            endPull = if (list.hasMore) EndPull.None else EndPull.Tease,
        ) {
            MyLazyColumn(
                contentPadding = contentPadding.plus(PaddingValues(vertical = 4.dp)),
                state = lazyListState,
            ) {
                itemsIndexed(items = list.items, key = { _, group -> group.key }) { index, group ->
                    Container {
                        Column {
                            LikeRow(group, onOpen, onOpenUser)
                            if (index < list.items.lastIndex) {
                                VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
                loadMoreFooter(list.append, list.hasMore, onLoadMore)
            }
        }
    }
}

@Composable
private fun LikeRow(
    group: LikeGroup,
    onOpen: (Like) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    val like = group.latest
    val colors = ExtendedTheme.colors
    val time = relativeTime(like.time)
    Column(
        modifier = Modifier
            .then(if (like.deleted) Modifier.alpha(0.55f) else Modifier.clickable { onOpen(like) })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UserHeader(
            avatar = { LikerAvatars(group) },
            name = { AuthorName(text = AnnotatedString(like.liker.displayName)) },
            onClick = { onOpenUser(like.liker.id) },
            desc = {
                MetaText(
                    text = if (group.likers.size > 1) {
                        pluralStringResource(id = R.plurals.text_likes_me, count = group.likers.size, group.likers.size, time)
                    } else {
                        stringResource(id = R.string.text_liked_one, time)
                    }
                )
            },
        ) {}
        // What was liked, in the grey block replies quote in
        EmoticonText(
            text = when {
                like.deleted -> stringResource(id = R.string.text_liked_deleted)
                else -> like.content.ifEmpty { like.threadTitle }
            },
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.chip, Corners.sm)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            color = colors.textSecondary,
            fontSize = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One avatar, or for several likers the newest two, one over the other's corner. */
@Composable
private fun LikerAvatars(group: LikeGroup) {
    val likers = group.likers
    if (likers.size == 1) {
        Avatar(data = StringUtil.getAvatarUrl(likers[0].portrait), size = Sizes.Small, contentDescription = null)
        return
    }
    val small = 26.dp
    Box(modifier = Modifier.size(Sizes.Small)) {
        Avatar(
            data = StringUtil.getAvatarUrl(likers[1].portrait),
            size = small,
            contentDescription = null,
            modifier = Modifier.padding(start = Sizes.Small - small, top = Sizes.Small - small),
        )
        Avatar(
            data = StringUtil.getAvatarUrl(likers[0].portrait),
            size = small,
            contentDescription = null,
            modifier = Modifier.border(2.dp, ExtendedTheme.colors.background, CircleShape),
        )
    }
}
