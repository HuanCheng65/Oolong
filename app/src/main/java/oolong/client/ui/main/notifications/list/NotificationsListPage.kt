package oolong.client.ui.main.notifications.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import oolong.client.model.Notification
import oolong.client.model.NotificationType
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.SubPostsKey
import oolong.client.navigation.ThreadKey
import oolong.client.navigation.UserProfileKey
import oolong.client.ui.components.AuthorName
import oolong.client.ui.components.BlockableContent
import oolong.client.ui.components.EmoticonText
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.components.KeepTopThroughRefresh
import oolong.client.ui.components.LoadMoreEffect
import oolong.client.ui.components.MetaText
import oolong.client.ui.components.UserHeader
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
import oolong.core.designsystem.theme.PressFeedback

@Composable
fun NotificationsListPage(
    type: NotificationType,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val listType = type
    LaunchedEffect(listType) { viewModel.open(listType) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    NotificationList(
        type = listType,
        list = state.list(listType),
        contentPadding = contentPadding,
        onRefresh = { viewModel.refresh(listType) },
        onLoadMore = { viewModel.loadMore(listType) },
        onOpen = { notification ->
            if (notification.inPost) {
                navigator.navigate(
                    SubPostsKey(threadId = notification.threadId, subPostId = notification.postId, loadFromSubPost = true)
                )
            } else {
                navigator.navigate(ThreadKey(threadId = notification.threadId, postId = notification.postId))
            }
        },
        onOpenQuote = { notification ->
            val quotePostId = notification.quotePostId
            if (notification.inPost && quotePostId != null) {
                // The quoted id may be a post or a reply inside one, so let pb/floor resolve it as a reply
                navigator.navigate(
                    SubPostsKey(threadId = notification.threadId, subPostId = quotePostId, loadFromSubPost = true)
                )
            } else {
                navigator.navigate(ThreadKey(threadId = notification.threadId))
            }
        },
        onOpenUser = { navigator.navigate(UserProfileKey(it)) },
    )
}

/** One list of notifications, which pulls down to refresh and loads more at its end. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun NotificationList(
    type: NotificationType,
    list: PagerState<NotificationItem>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (Notification) -> Unit,
    onOpenQuote: (Notification) -> Unit,
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
                itemsIndexed(
                    items = list.items,
                    key = { _, (it) -> "${it.postId}_${it.author?.id}_${it.time}" },
                ) { index, (notification, blocked) ->
                    Container {
                        Column {
                            BlockableContent(
                                block = blocked,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                NotificationItemContent(type, notification, onOpen, onOpenQuote, onOpenUser)
                            }
                            // Divided like feed cards
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
private fun NotificationItemContent(
    type: NotificationType,
    notification: Notification,
    onOpen: (Notification) -> Unit,
    onOpenQuote: (Notification) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable { onOpen(notification) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val author = notification.author
        if (author != null) {
            UserHeader(
                avatar = {
                    Avatar(data = StringUtil.getAvatarUrl(author.portrait), size = Sizes.Small, contentDescription = null)
                },
                name = { AuthorName(text = AnnotatedString(author.displayName)) },
                onClick = { onOpenUser(author.id) },
                desc = { MetaText(text = relativeTime(notification.time)) },
            ) {}
        }
        EmoticonText(
            text = notification.content,
            fontSize = 15.sp,
            color = ExtendedTheme.colors.text,
        )
        val quoteText = when {
            type == NotificationType.AtMe -> notification.threadTitle
            notification.inPost -> notification.quoteContent
            else -> stringResource(id = R.string.text_message_list_item_reply_my_thread, notification.threadTitle)
        }
        if (quoteText != null) {
            // What it answers, in the grey block replies quote in
            EmoticonText(
                text = quoteText,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ExtendedTheme.colors.chip, Corners.sm)
                    .clickable(interactionSource = null, indication = PressFeedback.overlay(Corners.sm)) { onOpenQuote(notification) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = ExtendedTheme.colors.textSecondary,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
