package oolong.client.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.model.SearchHistoryItem
import oolong.client.model.SearchOrder
import oolong.client.model.SearchedPost
import oolong.client.navigation.ForumKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.navigation.ThreadKey
import oolong.client.navigation.UserProfileKey
import oolong.client.ui.components.SearchBox
import oolong.client.ui.designsystem.Container
import oolong.client.ui.designsystem.ContentSwap
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTab
import oolong.client.ui.designsystem.GlassTabRow
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.LazyLoadHorizontalPager
import oolong.client.ui.designsystem.MyBackHandler
import oolong.client.ui.designsystem.Reveal
import oolong.client.ui.designsystem.TabClickMenu
import oolong.client.ui.designsystem.TabText
import oolong.client.ui.designsystem.choiceMenuItems
import oolong.client.ui.designsystem.motionItem
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.designsystem.theme.TiebaLiteTheme
import oolong.client.ui.search.forum.SearchForumList
import oolong.client.ui.search.thread.SearchThreadResults
import oolong.client.ui.search.user.SearchUserList
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape

@Immutable
data class SearchPageItem(
    val id: String,
    @StringRes val title: Int,
    val content: @Composable (contentPadding: PaddingValues) -> Unit,
    val supportSort: Boolean = false,
    val sortTypes: ImmutableMap<String, Int> = persistentMapOf(),
    val selectedSortType: () -> Int = { -1 },
    val onSelectedSortTypeChange: (Int) -> Unit = {},
)

@Composable
fun SearchPage(
    keyword: String = "",
    navigator: Navigator = LocalNavigator.current,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }

    // Opened for a keyword, it searches for it once, as if typed
    var searched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!searched && keyword.isNotBlank()) viewModel.search(keyword)
        searched = true
    }

    LaunchedEffect(state.keyword) {
        if (state.keyword.isNotEmpty()) input = state.keyword
    }
    // Back from what it was opened for goes back to where that came from, not to the history
    val backToHistory = state.keyword.isNotEmpty() && state.keyword != keyword.trim()
    MyBackHandler(
        enabled = backToHistory,
    ) {
        viewModel.search("")
    }

    ProvideNavigator(navigator = navigator) {
        SearchScreen(
            state = state,
            input = input,
            onInputChange = {
                input = it
                viewModel.onInputChange(it)
            },
            onSearch = {
                input = it
                viewModel.search(it)
            },
            onBack = {
                if (backToHistory) viewModel.search("") else navigator.navigateUp()
            },
            onOpenTab = viewModel::open,
            onRefreshTab = viewModel::refresh,
            onLoadMoreThreads = viewModel::loadMoreThreads,
            onSetOrder = viewModel::setOrder,
            onForget = viewModel::forget,
            onClearHistory = viewModel::clearHistory,
            onOpenForum = { navigator.navigate(ForumKey(it)) },
            onOpenUser = { navigator.navigate(UserProfileKey(it)) },
            onOpenThread = { navigator.navigate(ThreadKey(threadId = it.threadId)) },
        )
    }
}

/** Where the search field is, against the search page's top bar. */
enum class SearchFieldPlace {
    /** The page's own, in its top bar. */
    Own,

    /** Someone else's, laid over the top bar's row, which is kept clear for it. */
    TopBar,

    /** Someone else's, away from the top bar, such as above the keyboard; the bar carries only the tabs. */
    Elsewhere,
}

/**
 * The search page: the history and suggestions while the user types a keyword, then the forums,
 * threads and users found, in tabs. [fieldPlace] says where the field is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    state: SearchState,
    input: String,
    onInputChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onOpenTab: (SearchTab) -> Unit,
    onRefreshTab: (SearchTab) -> Unit,
    onLoadMoreThreads: () -> Unit,
    onSetOrder: (SearchOrder) -> Unit,
    onForget: (SearchHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
    onOpenForum: (forumName: String) -> Unit,
    onOpenUser: (uid: Long) -> Unit,
    onOpenThread: (SearchedPost) -> Unit,
    fieldPlace: SearchFieldPlace = SearchFieldPlace.Own,
) {
    val searching = state.keyword.isNotEmpty()

    val orderNames = persistentMapOf(
        stringResource(R.string.title_search_order_new) to SearchOrder.Newest,
        stringResource(R.string.title_search_order_old) to SearchOrder.Oldest,
        stringResource(R.string.title_search_order_relevant) to SearchOrder.Relevant,
    )
    val pages = persistentListOf(
        SearchPageItem(
            id = "forum",
            title = R.string.title_search_forum,
            content = { padding ->
                LaunchedEffect(state.keyword) { onOpenTab(SearchTab.Forums) }
                SearchForumList(
                    state = state.forums,
                    onRefresh = { onRefreshTab(SearchTab.Forums) },
                    onOpen = { onOpenForum(it.name) },
                    contentPadding = padding,
                )
            }
        ),
        SearchPageItem(
            id = "thread",
            title = R.string.title_search_thread,
            content = { padding ->
                LaunchedEffect(state.keyword) { onOpenTab(SearchTab.Threads) }
                SearchThreadResults(
                    list = state.threads,
                    keyword = state.keyword,
                    onRefresh = { onRefreshTab(SearchTab.Threads) },
                    onLoadMore = onLoadMoreThreads,
                    onOpen = onOpenThread,
                    onOpenUser = { author -> author.id.takeIf { it != 0L }?.let(onOpenUser) },
                    onOpenForum = onOpenForum,
                    contentPadding = padding,
                    blocks = state.threadBlocks,
                )
            },
            supportSort = true,
            sortTypes = orderNames.mapValues { it.value.ordinal }.toImmutableMap(),
            selectedSortType = { state.order.ordinal },
            onSelectedSortTypeChange = { onSetOrder(SearchOrder.entries[it]) }
        ),
        SearchPageItem(
            id = "user",
            title = R.string.title_search_user,
            content = { padding ->
                LaunchedEffect(state.keyword) { onOpenTab(SearchTab.Users) }
                SearchUserList(
                    state = state.users,
                    onRefresh = { onRefreshTab(SearchTab.Users) },
                    onOpen = { onOpenUser(it.id) },
                    contentPadding = padding,
                )
            }
        ),
    )
    val pagerState = rememberPagerState { pages.size }
    GlassScaffold(
        topBar = {
            when {
                fieldPlace == SearchFieldPlace.Own -> GlassTopBar(
                    title = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(end = 12.dp)
                        ) {
                            // The bar styles its title slot as a title; the field keeps body text.
                            ProvideTextStyle(MaterialTheme.typography.body1) {
                                SearchTopBar(
                                    keyword = input,
                                    onKeywordChange = onInputChange,
                                    onKeywordSubmit = onSearch,
                                    onBack = onBack,
                                )
                            }
                        }
                    },
                ) {
                    Reveal(Unit.takeIf { searching }) { SearchTabRow(pagerState = pagerState, pages = pages) }
                }

                fieldPlace == SearchFieldPlace.TopBar -> GlassTopBar(title = {}) {
                    Reveal(Unit.takeIf { searching }) { SearchTabRow(pagerState = pagerState, pages = pages) }
                }

                // The field is elsewhere: only the tabs, which open under the status bar once there are any
                else -> GlassTopBar(title = null) {
                    Reveal(Unit.takeIf { searching }) { SearchTabRow(pagerState = pagerState, pages = pages) }
                }
            }
        }
    ) { paddingValues ->
        val body = when {
            searching -> SearchBody.Results
            state.suggestions.isNotEmpty() && input.isNotBlank() -> SearchBody.Suggestions
            else -> SearchBody.History
        }
        ContentSwap(targetState = body, modifier = Modifier.fillMaxSize()) { shown ->
            when (shown) {
                SearchBody.Results -> LazyLoadHorizontalPager(
                    state = pagerState,
                    key = { pages[it].id },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    pages[it].content(paddingValues)
                }

                SearchBody.Suggestions -> SearchSuggestionList(
                    suggestions = state.suggestions,
                    contentPadding = paddingValues,
                    onItemClick = onSearch,
                )

                SearchBody.History -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(paddingValues)
                ) {
                    Container {
                        SearchHistoryList(
                            searchHistories = state.history,
                            onSearchHistoryClick = { onSearch(it.keyword) },
                            onDelete = onForget,
                            onClear = onClearHistory,
                        )
                    }
                }
            }
        }
    }
}

/** What the search page's body shows. */
private enum class SearchBody { History, Suggestions, Results }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchSuggestionList(
    suggestions: ImmutableList<String>,
    onItemClick: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
    ) {
        items(
            items = suggestions,
            key = { it }
        ) {
            Container(
                modifier = Modifier.motionItem(this)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .clickable {
                            onItemClick(it)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Symbols.Search,
                        contentDescription = stringResource(id = R.string.desc_search_sug, it),
                        tint = ExtendedTheme.colors.text
                    )

                    Text(
                        text = it,
                        style = MaterialTheme.typography.subtitle2,
                        modifier = Modifier.weight(1f),
                        color = ExtendedTheme.colors.text
                    )
                }
            }
        }
    }
}

@Preview("SearchSuggestionList", backgroundColor = 0xFFFFFFFF)
@Composable
private fun SearchSuggestionListPreview() {
    TiebaLiteTheme {
        Box(
            modifier = Modifier.background(ExtendedTheme.colors.background)
        ) {
            SearchSuggestionList(
                suggestions = persistentListOf("1", "2", "3"),
                onItemClick = {}
            )
        }
    }
}

/**
 * The result pages' tabs on a glass capsule, as every page's tabs are, under the field. Tapping a
 * tab that sorts again while it is chosen opens its orders.
 */
@Composable
private fun ColumnScope.SearchTabRow(
    pagerState: PagerState,
    pages: ImmutableList<SearchPageItem>,
) {
    val coroutineScope = rememberCoroutineScope()
    val select: (Int) -> Unit = { coroutineScope.launch { pagerState.animateScrollToPage(it) } }
    GlassTabRow(
        selectedTabIndex = pagerState.currentPage,
        pagerState = pagerState,
        onSelect = select,
        // Across the page, as wide as the row of the field above it, the tabs sharing it
        fill = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        pages.fastForEachIndexed { index, item ->
            val selected = pagerState.currentPage == index
            if (item.supportSort) {
                TabClickMenu(
                    selected = selected,
                    onClick = { select(index) },
                    text = { TabText(stringResource(id = item.title), selected) },
                    items = {
                        choiceMenuItems(item.sortTypes.map { (label, value) -> value to label }, item.selectedSortType()) {
                            item.onSelectedSortTypeChange(it)
                        }
                    },
                    selectedContentColor = ExtendedTheme.colors.text,
                    unselectedContentColor = ExtendedTheme.colors.textSecondary,
                )
            } else {
                GlassTab(
                    text = { TabText(stringResource(id = item.title), selected) },
                    selected = selected,
                    selectedContentColor = ExtendedTheme.colors.text,
                    unselectedContentColor = ExtendedTheme.colors.textSecondary,
                    onClick = { select(index) },
                )
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onKeywordSubmit: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    SearchBox(
        keyword = keyword,
        onKeywordChange = onKeywordChange,
        modifier = Modifier.fillMaxSize(),
        onKeywordSubmit = onKeywordSubmit,
        placeholder = {
            Text(
                text = stringResource(id = R.string.hint_search),
                color = ExtendedTheme.colors.onSearchField.copy(alpha = ContentAlpha.medium)
            )
        },
        prependIcon = {
            Box(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = PressFeedback.overlay(Corners.full),
                        role = Role.Button,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Symbols.ArrowBack,
                    contentDescription = stringResource(id = R.string.button_back)
                )
            }
        },
        shape = SmoothCornerShape(6.dp)
    )
}

@Preview("SearchBox")
@Composable
fun PreviewSearchBox() {
    var keyword by remember { mutableStateOf("") }
    TiebaLiteTheme {
        Box(
            modifier = Modifier
                .height(64.dp)
                .background(ExtendedTheme.colors.background)
                .padding(vertical = 8.dp, horizontal = 16.dp)
        ) {
            SearchTopBar(
                keyword = keyword,
                onKeywordChange = { keyword = it }
            )
        }
    }
}
