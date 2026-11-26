package oolong.client.ui.search.thread

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.data.block.BlockVerdict
import oolong.client.model.SearchAuthor
import oolong.client.model.SearchedPost
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.components.LoadMoreEffect
import oolong.client.ui.components.SearchThreadList
import oolong.client.ui.components.loadMoreFooter
import oolong.client.ui.designsystem.EndPull
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.states.StateScreen

/** Threads and replies a search found, which pull down to search again and load more at their end. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SearchThreadResults(
    list: PagerState<SearchedPost>,
    keyword: String,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (SearchedPost) -> Unit,
    onOpenUser: (SearchAuthor) -> Unit,
    onOpenForum: (forumName: String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    blocks: ImmutableMap<String, BlockVerdict> = persistentMapOf(),
) {
    val refreshing = list.refresh == LoadStatus.Loading
    val error = (list.refresh as? LoadStatus.Error)?.error
    StateScreen(
        modifier = Modifier.fillMaxSize(),
        isEmpty = list.items.isEmpty(),
        isError = error != null,
        isLoading = refreshing,
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error) },
    ) {
        val lazyListState = rememberLazyListState()
        LoadMoreEffect(lazyListState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = onLoadMore)
        PullRefresh(
            refreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorTop = contentPadding.calculateTopPadding(),
            endPull = if (list.hasMore) EndPull.None else EndPull.Tease,
        ) {
            SearchThreadList(
                data = list.items,
                lazyListState = lazyListState,
                onItemClick = onOpen,
                onItemUserClick = onOpenUser,
                onItemForumClick = onOpenForum,
                searchKeyword = keyword,
                contentPadding = contentPadding,
                blockOf = { blocks[it.key] },
                footer = { loadMoreFooter(list.append, list.hasMore, onLoadMore) },
            )
        }
    }
}
