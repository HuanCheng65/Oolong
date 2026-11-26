package oolong.client.ui.main.explore.concern

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.ui.components.BlockableContent
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.components.FeedCard
import oolong.client.ui.components.KeepTopThroughRefresh
import oolong.client.ui.components.LoadMoreEffect
import oolong.client.ui.components.loadMoreFooter
import oolong.client.ui.designsystem.Container
import oolong.client.ui.designsystem.EndPull
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.VerticalDivider
import oolong.client.ui.designsystem.states.StateScreen
import oolong.client.ui.main.OnTabReselected
import oolong.client.ui.main.explore.FeedThread
import oolong.client.ui.main.explore.FeedThreadEvents
import oolong.client.ui.main.explore.rememberFeedThreadEvents

@Composable
fun ConcernPage(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: ConcernViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.open() }
    OnTabReselected("concern") { viewModel.refresh() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConcernScreen(
        list = state,
        events = rememberFeedThreadEvents(onAgree = viewModel::agree),
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        contentPadding = contentPadding,
    )
}

/** The threads of the users the user follows, which pull down to refresh and load more at their end. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ConcernScreen(
    list: PagerState<FeedThread>,
    events: FeedThreadEvents,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val error = (list.refresh as? LoadStatus.Error)?.error
    StateScreen(
        modifier = Modifier.fillMaxSize(),
        isEmpty = list.items.isEmpty(),
        isError = error != null,
        isLoading = list.refresh == LoadStatus.Loading,
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error) },
    ) {
        val lazyListState = rememberLazyListState()
        LoadMoreEffect(lazyListState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = onLoadMore)
        KeepTopThroughRefresh(lazyListState, list.refresh == LoadStatus.Loading)
        PullRefresh(
            refreshing = list.refresh == LoadStatus.Loading,
            revealUnpulled = true,
            onRefresh = onRefresh,
            indicatorTop = contentPadding.calculateTopPadding(),
            endPull = if (list.hasMore) EndPull.None else EndPull.Tease,
        ) {
            MyLazyColumn(
                state = lazyListState,
                contentPadding = contentPadding,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(items = list.items, key = { _, item -> item.thread.id }) { index, item ->
                    Container {
                        BlockableContent(
                            block = item.block,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
                        ) {
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
                                )
                                if (index < list.items.size - 1) {
                                    VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
                loadMoreFooter(list.append, list.hasMore, onLoadMore)
            }
        }
    }
}
