package oolong.client.ui.forum.searchpost

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.settingAsState
import oolong.client.model.ForumSearchOrder
import oolong.client.model.QuotedPost
import oolong.client.model.SearchAuthor
import oolong.client.model.SearchHistoryItem
import oolong.client.model.SearchedPost
import oolong.client.navigation.ForumSearchPostKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.navigation.SubPostsKey
import oolong.client.navigation.ThreadKey
import oolong.client.navigation.UserProfileKey
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.components.LoadMoreEffect
import oolong.client.ui.components.SearchThreadList
import oolong.client.ui.components.loadMoreFooter
import oolong.client.ui.designsystem.Container
import oolong.client.ui.designsystem.ContentSwap
import oolong.client.ui.designsystem.EndPull
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTabRow
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.LocalChromePadding
import oolong.client.ui.designsystem.MyBackHandler
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.Reveal
import oolong.client.ui.designsystem.TabClickMenu
import oolong.client.ui.designsystem.TabText
import oolong.client.ui.designsystem.choiceMenuItems
import oolong.client.ui.designsystem.states.StateScreen
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.main.MainSearchField
import oolong.client.ui.main.mainSearchPadding
import oolong.client.ui.main.rememberOpenSearchState
import oolong.client.ui.search.SearchHistoryList

@Composable
fun ForumSearchPostPage(
    key: ForumSearchPostKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: ForumSearchPostViewModel = hiltViewModel<ForumSearchPostViewModel, ForumSearchPostViewModel.Factory> { it.create(key) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProvideNavigator(navigator = navigator) {
        ForumSearchPostScreen(
            state = state,
            onBack = { navigator.navigateUp() },
            onSearch = viewModel::search,
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onSetOrder = viewModel::setOrder,
            onSetOnlyThreads = viewModel::setOnlyThreads,
            onForget = viewModel::forget,
            onClearHistory = viewModel::clearHistory,
            onOpen = { post ->
                when {
                    post.quoted != null -> navigator.navigate(
                        SubPostsKey(threadId = post.threadId, subPostId = post.subPostId, loadFromSubPost = true)
                    )

                    post.thread != null -> navigator.navigate(
                        ThreadKey(threadId = post.threadId, postId = post.postId, scrollToReply = true)
                    )

                    else -> navigator.navigate(ThreadKey(threadId = post.threadId))
                }
            },
            onOpenUser = { author -> author.id.takeIf { it != 0L }?.let { navigator.navigate(UserProfileKey(it)) } },
            onOpenQuoted = { navigator.navigate(ThreadKey(threadId = it.threadId, postId = it.postId, scrollToReply = true)) },
            onOpenThread = { navigator.navigate(ThreadKey(threadId = it.threadId, scrollToReply = true)) },
        )
    }
}

/**
 * Searching one forum: its history until the user searches, then the threads and replies found,
 * all or threads only, in tabs. The field is where the user keeps search on the main page: above
 * the keyboard beside a button that closes the page, or in the top bar after a back button.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ForumSearchPostScreen(
    state: ForumSearchPostState,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSetOrder: (ForumSearchOrder) -> Unit,
    onSetOnlyThreads: (Boolean) -> Unit,
    onForget: (SearchHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
    onOpen: (SearchedPost) -> Unit,
    onOpenUser: (SearchAuthor) -> Unit,
    onOpenQuoted: (QuotedPost) -> Unit,
    onOpenThread: (QuotedPost) -> Unit,
) {
    val list = state.list
    val searching = state.keyword.isNotEmpty()
    val isRefreshing = list.refresh == LoadStatus.Loading
    val error = (list.refresh as? LoadStatus.Error)?.error
    val focusManager = LocalFocusManager.current
    val atTop by settingAsState(SettingKeys.SearchAtTop)
    var input by rememberSaveable { mutableStateOf(state.keyword) }
    LaunchedEffect(state.keyword) {
        if (state.keyword.isNotEmpty()) input = state.keyword
    }
    val search: (String) -> Unit = {
        input = it
        focusManager.clearFocus()
        onSearch(it)
    }
    // Back from what was found goes back to the history, as on the main page's search
    val backToHistory = { search("") }
    MyBackHandler(enabled = searching, onBack = backToHistory)
    // Opened afresh, the user is here to type; coming back to what was found, to read it
    val fieldState = rememberOpenSearchState(focus = !searching)

    CompositionLocalProvider(LocalChromePadding provides mainSearchPadding(atTop)) {
        GlassScaffold(
            topBar = {
                when {
                    // The bar's row is kept clear for the field, laid over it
                    atTop -> GlassTopBar(title = {}) {
                        Reveal(Unit.takeIf { searching }) { Column { ForumSearchTabRow(state, onSetOrder, onSetOnlyThreads) } }
                    }

                    // Only the tabs, which open under the status bar once there are any
                    else -> GlassTopBar(title = null) {
                        Reveal(Unit.takeIf { searching }) { Column { ForumSearchTabRow(state, onSetOrder, onSetOnlyThreads) } }
                    }
                }
            },
            overlay = {
                MainSearchField(
                    state = fieldState,
                    atTop = atTop,
                    input = input,
                    onInputChange = { input = it },
                    onSearch = search,
                    onOpen = {},
                    // At the top it is a back button, which goes back as the system's does
                    onClose = if (atTop && searching) backToHistory else onBack,
                    placeholder = stringResource(id = R.string.hint_search_in_ba, state.forumName),
                )
            },
        ) { paddingValues ->
            ContentSwap(targetState = searching, modifier = Modifier.fillMaxSize()) { results ->
                if (results) {
                    StateScreen(
                        modifier = Modifier.fillMaxSize(),
                        isEmpty = list.items.isEmpty(),
                        isError = error != null,
                        isLoading = isRefreshing,
                        onReload = onRefresh,
                        errorScreen = { ErrorScreen(error = error) }
                    ) {
                        // Each search and each tab starts from the top
                        val lazyListState = key(state.keyword, state.onlyThreads, state.order) { rememberLazyListState() }
                        LoadMoreEffect(lazyListState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = onLoadMore)
                        PullRefresh(
                            refreshing = isRefreshing,
                            onRefresh = onRefresh,
                            modifier = Modifier.fillMaxSize(),
                            indicatorTop = paddingValues.calculateTopPadding(),
                            endPull = if (list.hasMore) EndPull.None else EndPull.Tease,
                        ) {
                            SearchThreadList(
                                data = list.items,
                                lazyListState = lazyListState,
                                onItemClick = onOpen,
                                onItemUserClick = onOpenUser,
                                onItemForumClick = {},
                                onQuotePostClick = onOpenQuoted,
                                onMainPostClick = onOpenThread,
                                hideForum = true,
                                searchKeyword = state.keyword,
                                contentPadding = paddingValues,
                                blockOf = { state.blocks[it.key] },
                                footer = { loadMoreFooter(list.append, list.hasMore, onLoadMore) },
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(paddingValues)
                    ) {
                        Container {
                            SearchHistoryList(
                                searchHistories = state.history,
                                onSearchHistoryClick = { search(it.keyword) },
                                onDelete = onForget,
                                onClear = onClearHistory,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * All that was found or threads only, as tabs; tapped again, the chosen one offers the orders, as
 * the main page's search does for threads.
 */
@Composable
private fun ColumnScope.ForumSearchTabRow(
    state: ForumSearchPostState,
    onSetOrder: (ForumSearchOrder) -> Unit,
    onSetOnlyThreads: (Boolean) -> Unit,
) {
    val orders = listOf(
        ForumSearchOrder.Newest to stringResource(R.string.title_search_order_new),
        ForumSearchOrder.Relevant to stringResource(R.string.title_search_order_relevant),
    )
    val tabs = listOf(
        false to R.string.title_search_filter_all,
        true to R.string.title_search_tab_threads,
    )
    val selected = tabs.indexOfFirst { it.first == state.onlyThreads }
    // On a glass capsule, as every page's tabs are; tapping the chosen tab again opens the orders
    GlassTabRow(
        selectedTabIndex = selected,
        onSelect = { onSetOnlyThreads(tabs[it].first) },
        // Across the page, as wide as the row of the field above it, the tabs sharing it
        fill = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        tabs.forEach { (onlyThreads, text) ->
            TabClickMenu(
                selected = onlyThreads == state.onlyThreads,
                onClick = { onSetOnlyThreads(onlyThreads) },
                text = { TabText(stringResource(id = text), onlyThreads == state.onlyThreads) },
                items = { choiceMenuItems(orders, state.order, onSetOrder) },
                selectedContentColor = ExtendedTheme.colors.text,
                unselectedContentColor = ExtendedTheme.colors.textSecondary,
            )
        }
    }
}
