package oolong.client.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.settingAsState
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.navigation.UpdateKey
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.LazyLoadHorizontalPager
import oolong.client.ui.designsystem.LocalChromePadding
import oolong.client.ui.designsystem.LocalNavigationRailShown
import oolong.client.ui.designsystem.LocalTopBarEnd
import oolong.client.ui.designsystem.LocalTopBarEndRoom
import oolong.client.ui.designsystem.MyBackHandler
import oolong.client.ui.designsystem.TopBarEndRoom
import oolong.client.ui.designsystem.rememberPageScrollState
import oolong.client.ui.designsystem.rememberScrollProgress
import oolong.client.ui.designsystem.theme.CapTextScale
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.main.explore.ExplorePage
import oolong.client.ui.main.home.HomePage
import oolong.client.ui.main.notifications.NotificationsTab
import oolong.client.ui.main.user.UserPage
import oolong.client.ui.search.SearchViewModel

/**
 * The four tabs. On a phone their navigation is the floating bar at the bottom; on wider windows
 * it is the rail beside the panes, which lives outside this page.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPage(
    navigator: Navigator = LocalNavigator.current,
) {
    val tabs = LocalMainTabs.current
    val navigationItems = rememberMainTabItems(tabs)
    val selectedIndex = navigationItems.indexOfFirst { it.id == tabs.selected }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { navigationItems.size }
    LaunchedEffect(selectedIndex, navigationItems.size) {
        if (pagerState.currentPage != selectedIndex) pagerState.scrollToPage(selectedIndex)
    }
    // From a drag of the pages until they settle; a tap on a tab jumps without one
    var swiping by remember { mutableStateOf(false) }
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) swiping = true }
    val currentItems by rememberUpdatedState(navigationItems)
    LaunchedEffect(pagerState) {
        // A swipe picks the tab it settles on. The page in view, not settledPage, which may not
        // have caught up yet: picked wrong, the tab left could not be picked again from the bar
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                // Whether or not the drag was seen in time: a quick one can stop before it is
                swiping = false
                currentItems.getOrNull(pagerState.currentPage)?.let { tabs.selected = it.id }
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val themeColors = ExtendedTheme.colors
    val showBottomNavigation = !LocalNavigationRailShown.current
    val onReselected: (Int) -> Unit = { tabs.reselect(navigationItems[it].id) }
    val search = rememberMainSearchState()
    val searchViewModel: SearchViewModel = hiltViewModel()
    val searchUiState by searchViewModel.state.collectAsStateWithLifecycle()
    var searchInput by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(searchUiState.keyword) {
        if (searchUiState.keyword.isNotEmpty()) searchInput = searchUiState.keyword
    }
    val focusManager = LocalFocusManager.current
    val openSearch: () -> Unit = {
        coroutineScope.launch { search.open() }
    }
    val closeSearch: () -> Unit = {
        focusManager.clearFocus()
        coroutineScope.launch {
            search.close()
            // Opened again, search starts from the history
            searchInput = ""
            searchViewModel.search("")
        }
    }
    // Another tab picked, from the rail too, leaves search for that tab. Only a change: the page
    // composed again on coming back from what search opened finds search still open, and keeps it
    val currentCloseSearch by rememberUpdatedState(closeSearch)
    LaunchedEffect(Unit) {
        snapshotFlow { tabs.selected }.drop(1).collect { if (search.open) currentCloseSearch() }
    }
    val searchShown by remember { derivedStateOf { search.progress.value > 0f } }
    val searchKeptAtTop by settingAsState(SettingKeys.SearchAtTop)
    // Beside the rail, whose tabs run down the side, the pages don't swipe
    val swipeTabs by settingAsState(SettingKeys.MainTabSwipe)
    // Beside the rail there is no bar to put it by: it stands at the top of the list
    val searchAtTop = searchKeptAtTop || !showBottomNavigation

    ProvideNavigator(navigator = navigator) {
        // The bar folds to its icons as a tab's content goes on and unfolds as it comes back; it
        // stays as it is when another tab opens or the user comes back from another page
        val page = rememberPageScrollState()
        val navBarCompact = rememberScrollProgress(away = 24.dp, toggle = true, page = page)
        GlassScaffold(
            backgroundColor = Color.Transparent,
            pageScroll = page,
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                // Its labels keep their size, as the bar's height does not grow
                if (showBottomNavigation) CapTextScale(max = 1f) {
                    FloatingNavBar(
                        currentPosition = pagerState.currentPage,
                        onChangePosition = { index ->
                            tabs.selected = navigationItems[index].id
                            // Also when the tab was already the picked one but the pages show another
                            if (pagerState.currentPage != index) coroutineScope.launch { pagerState.scrollToPage(index) }
                        },
                        onReselected = onReselected,
                        navigationItems = navigationItems,
                        collapsedFraction = { navBarCompact.value },
                        themeColors = themeColors,
                        modifier = Modifier
                            .graphicsLayer { alpha = 1f - search.progress.value }
                            .then(if (search.open) Modifier.clearAndSetSemantics {} else Modifier),
                        onSearchButtonPlaced = if (searchAtTop) null else { bounds -> search.buttonBounds = bounds },
                        searching = searchShown,
                        onSearch = openSearch,
                        swipePosition = {
                            if (swiping) pagerState.currentPage + pagerState.currentPageOffsetFraction else null
                        },
                    )
                }
            },
            overlay = {
                MainSearchField(
                    state = search,
                    atTop = searchAtTop,
                    input = searchInput,
                    onInputChange = {
                        searchInput = it
                        searchViewModel.onInputChange(it)
                    },
                    onSearch = {
                        searchInput = it
                        focusManager.clearFocus()
                        searchViewModel.search(it)
                    },
                    onOpen = openSearch,
                    onClose = closeSearch,
                )
            },
            snackbarHost = { host ->
                Box(contentAlignment = Alignment.BottomCenter) {
                    UpdateHint(hidden = searchShown, onOpen = { navigator.navigate(UpdateKey) })
                }
            },
        ) { paddingValues ->
            val chromePadding = if (showBottomNavigation) {
                PaddingValues(bottom = paddingValues.calculateBottomPadding())
            } else {
                PaddingValues(0.dp)
            }
            CompositionLocalProvider(LocalChromePadding provides chromePadding) {
                // Beside the rail, the field stands at the end of the top bar of the tab in view
                // One size on every tab, however much room each tab's title leaves it
                val barSearchRoom = remember { TopBarEndRoom() }
                val barSearch: (@Composable (Dp) -> Unit)? = when {
                    !showBottomNavigation -> { room -> TopBarSearchField(state = search, room = room, onOpen = openSearch) }
                    // Kept at the top on a phone, it is a round button at the end of the feeds' and
                    // the messages' bars; the home page has its field under its title
                    searchAtTop -> { _ -> TopSearchField(state = search, onOpen = openSearch, compact = true) }
                    else -> null
                }
                LazyLoadHorizontalPager(
                    state = pagerState,
                    key = { navigationItems[it].id },
                    // Gives way to search, which on the translucent theme has no ground of its own
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - search.progress.value },
                    verticalAlignment = Alignment.Top,
                    userScrollEnabled = swipeTabs && showBottomNavigation && !search.open,
                ) { page ->
                    // Only the tab in view, so the field search opens from is the one that shows
                    CompositionLocalProvider(
                        LocalTopBarEnd provides barSearch.takeIf {
                            page == pagerState.currentPage &&
                                (!showBottomNavigation || navigationItems[page].id in TopSearchButtonTabs)
                        },
                        LocalTopBarEndRoom provides barSearchRoom,
                    ) {
                        when (navigationItems[page].id) {
                            MainTabs.HOME -> HomePage(
                                canOpenExplore = navigationItems.any { item -> item.id == MainTabs.EXPLORE },
                                showSearchBox = false,
                                searchField = if (showBottomNavigation && searchAtTop) {
                                    { TopSearchField(state = search, onOpen = openSearch) }
                                } else {
                                    null
                                },
                            ) {
                                tabs.selected = MainTabs.EXPLORE
                            }

                            MainTabs.EXPLORE -> ExplorePage()
                            MainTabs.NOTIFICATIONS -> NotificationsTab()
                            MainTabs.USER -> UserPage()
                        }
                    }
                }
                // From the moment it opens, before it shows, see [MainSearchState.open]
                if (search.open || searchShown) {
                    CompositionLocalProvider(LocalChromePadding provides mainSearchPadding(searchAtTop)) {
                        Box(modifier = Modifier.graphicsLayer { alpha = search.progress.value }) {
                            MainSearchContent(
                                state = searchUiState,
                                atTop = searchAtTop,
                                input = searchInput,
                                viewModel = searchViewModel,
                                navigator = navigator,
                                onSearch = {
                                    searchInput = it
                                    focusManager.clearFocus()
                                    searchViewModel.search(it)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    MyBackHandler(enabled = tabs.selected != MainTabs.HOME) {
        tabs.selected = MainTabs.HOME
    }
    // After the tabs' handler, so it is asked first: back from results goes to the history
    MyBackHandler(enabled = search.open) {
        if (searchUiState.keyword.isNotEmpty()) searchViewModel.search("") else closeSearch()
    }
}

/** The tabs that, on a phone with search kept at the top, have a search button in their bar. */
private val TopSearchButtonTabs = setOf(MainTabs.EXPLORE, MainTabs.NOTIFICATIONS)
