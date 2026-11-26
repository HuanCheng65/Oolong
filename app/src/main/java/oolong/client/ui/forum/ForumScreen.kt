package oolong.client.ui.forum

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.ScaffoldState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.model.ForumDetail
import oolong.client.model.ForumTab
import oolong.client.ui.components.FeedCardPlaceholder
import oolong.client.ui.designsystem.ContentSwap
import oolong.client.ui.designsystem.EndPull
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBarRowHeight
import oolong.client.ui.designsystem.LocalChromePadding
import oolong.client.ui.designsystem.LocalPageAccent
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.SharedHeaderPager
import oolong.client.ui.designsystem.onPageAccent
import oolong.client.ui.designsystem.pageAccentOrPrimary
import oolong.client.ui.designsystem.pageWash
import oolong.client.ui.designsystem.rememberAvatarAccent
import oolong.client.ui.designsystem.rememberPageScrollState
import oolong.client.ui.designsystem.rememberScrollProgress
import oolong.client.ui.designsystem.rememberSharedHeaderState
import oolong.client.ui.designsystem.sharedHeaderPinned
import oolong.client.ui.designsystem.states.DefaultErrorScreen
import oolong.client.ui.designsystem.states.StateScreen
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.designsystem.toListStart
import oolong.client.ui.designsystem.underSharedHeader
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.pressScale

/** What the floating button does, as set in the settings. */
enum class ForumFab { Refresh, BackToTop, Hidden }

/**
 * The forum page: the header over the tabs' lists, going up under the bar as they scroll, and one
 * list of threads per tab.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ForumScreen(
    state: ForumState,
    onEvent: (ForumEvent) -> Unit,
    modifier: Modifier = Modifier,
    fab: ForumFab = ForumFab.Refresh,
    scaffoldState: ScaffoldState = rememberScaffoldState(),
) {
    // Placeholders give way to the forum by refocusing, laid out as it is
    ContentSwap(targetState = state.forum == null, modifier = modifier) { loading ->
        val forum = state.forum
        if (loading || forum == null) {
            ForumLoading(state, onEvent, Modifier)
        } else {
            ForumContent(state, forum, onEvent, fab, scaffoldState)
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ForumContent(
    state: ForumState,
    forum: ForumDetail,
    onEvent: (ForumEvent) -> Unit,
    fab: ForumFab,
    scaffoldState: ScaffoldState,
    modifier: Modifier = Modifier,
) {
    val tabs = state.tabs
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(state.selectedTab).coerceAtLeast(0)) { tabs.size }
    val currentTabs by rememberUpdatedState(tabs)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            currentTabs.getOrNull(page)?.let { onEvent(ForumEvent.SelectTab(it)) }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    // One choice for every tab and every forum
    var topsFolded by remember { mutableStateOf(Settings.of(context)[SettingKeys.ForumTopsFolded]) }
    // Saved, so each tab keeps its place while another page is open on top
    val listStates = tabs.associateWith { tab -> key(tab) { rememberLazyListState() } }

    // The header is shared by the tabs' lists, and the tabs stay under the bar once it has gone
    val forumScroll = rememberPageScrollState()
    val header = rememberSharedHeaderState(forumScroll)
    // Once the header has gone, the bar's row follows the scroll away and back; the tabs stay
    val away = rememberScrollProgress(away = GlassTopBarRowHeight, enabled = { header.gone }, page = forumScroll)
    val currentList = state.list(state.selectedTab)
    // Another order loading shows by the list dimming, not by the pull's spinner
    val refreshing = currentList.refresh == LoadStatus.Loading && currentList.items.isNotEmpty() && state.switching != state.selectedTab

    val accent = rememberAvatarAccent(forum.avatar)
    CompositionLocalProvider(LocalPageAccent provides accent) {
        GlassScaffold(
            scaffoldState = scaffoldState,
            backgroundColor = Color.Transparent,
            pageScroll = forumScroll,
            modifier = modifier.fillMaxSize(),
            topBar = {
                ForumTopBar(
                    onEvent = onEvent,
                    menu = forumMenu(onEvent, liked = forum.liked),
                    title = { ForumBarTitle(forum, onEvent) },
                    hiddenFraction = { away.value },
                    pinnedOffset = { header.pinnedOffset },
                    pinnedContent = {
                        ForumTabRow(
                            tabs = tabs,
                            pagerState = pagerState,
                            sortType = state.sortType,
                            goodClasses = forum.goodClasses,
                            goodClassId = state.goodClassId,
                            onSelect = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                            onEvent = { event ->
                                // Another class or order is another list, which starts at its top
                                val changed = when (event) {
                                    is ForumEvent.SetGoodClass -> ForumTab.Good.takeIf { event.classId != state.goodClassId }
                                    is ForumEvent.SetSort -> ForumTab.Latest.takeIf { event.sortType != state.sortType }
                                    else -> null
                                }
                                changed?.let { listStates[it] }?.let(header::toListStart)
                                onEvent(event)
                            },
                            modifier = Modifier
                                .align(Alignment.Start)
                                .sharedHeaderPinned(header)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    },
                )
            },
            floatingActionButton = {
                if (fab != ForumFab.Hidden) {
                    val press = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .pressScale(press)
                            .size(56.dp)
                            .shadow(elevation = 8.dp, shape = CircleShape)
                            .background(pageAccentOrPrimary(), CircleShape)
                            .clickable(interactionSource = press, indication = PressFeedback.overlay(CircleShape), role = Role.Button) {
                                coroutineScope.launch {
                                    listStates[state.selectedTab]?.animateScrollToItem(0)
                                    if (fab == ForumFab.Refresh) onEvent(ForumEvent.Refresh)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            tint = onPageAccent(),
                            imageVector = if (fab == ForumFab.Refresh) Symbols.Refresh else Symbols.VerticalAlignTop,
                            contentDescription = stringResource(if (fab == ForumFab.Refresh) R.string.btn_refresh else R.string.btn_back_to_top),
                        )
                    }
                }
            },
        ) { contentPadding ->
            SharedHeaderPager(
                state = header,
                pagerState = pagerState,
                contentPadding = contentPadding,
                key = { tabs[it].toString() },
                wash = pageWash(),
                // The header and the lists follow a pull; the colour behind them stays
                frame = { content ->
                    PullRefresh(
                        refreshing = refreshing,
                        onRefresh = { onEvent(ForumEvent.Refresh) },
                        indicatorTop = contentPadding.calculateTopPadding(),
                        endPull = if (currentList.hasMore) EndPull.None else EndPull.Tease,
                    ) { content() }
                },
                header = { ForumHeader(forum = forum, loggedIn = state.loggedIn, joining = state.joining, onEvent = onEvent, modifier = Modifier.fillMaxWidth()) },
            ) { page ->
                val tab = tabs[page]
                val list = state.list(tab)
                val listPadding = LocalChromePadding.current
                StateScreen(
                    isEmpty = list.items.isEmpty(),
                    isError = list.refresh is LoadStatus.Error,
                    isLoading = list.waiting,
                    onReload = { onEvent(ForumEvent.Refresh) },
                    modifier = Modifier.fillMaxSize(),
                    loadingScreen = {
                        Column(modifier = Modifier
                            .fillMaxSize()
                            .underSharedHeader()
                            .padding(listPadding)) {
                            repeat(4) { FeedCardPlaceholder() }
                        }
                    },
                    errorScreen = {
                        Box(modifier = Modifier.padding(listPadding)) { DefaultErrorScreen(this@StateScreen) }
                    },
                ) {
                    // Another order or class loading dims the list it will replace
                    val dim by animateFloatAsState(if (state.switching == tab) SwitchingAlpha else 1f, Motion.settle(), label = "forumSwitching")
                    // The dimmed list keeps the times of the order it came in until the other one replaces it
                    val listedSort = remember { intArrayOf(state.sortType) }
                    if (state.switching != tab) listedSort[0] = state.sortType
                    ForumThreadList(
                        modifier = Modifier.graphicsLayer { alpha = dim },
                        tab = tab,
                        list = list,
                        sortType = listedSort[0],
                        onEvent = onEvent,
                        listState = listStates.getValue(tab),
                        contentPadding = listPadding,
                        topsFolded = topsFolded,
                        onFoldTops = {
                            topsFolded = it
                            Settings.of(context)[SettingKeys.ForumTopsFolded] = it
                        },
                    )
                }
            }
        }
    }
}

/** The page before the forum is known: placeholders while it loads, or the error it failed with. */
@Composable
private fun ForumLoading(state: ForumState, onEvent: (ForumEvent) -> Unit, modifier: Modifier) {
    GlassScaffold(
        backgroundColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
        topBar = {
            ForumTopBar(
                onEvent = onEvent,
                canSearch = false,
                menu = forumMenu(onEvent, liked = false),
            )
        },
    ) { contentPadding ->
        val error = state.firstLoad as? LoadStatus.Error
        if (error != null) {
            StateScreen(
                isEmpty = true,
                isError = true,
                isLoading = false,
                onReload = { onEvent(ForumEvent.Refresh) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {}
        } else {
            Column(modifier = Modifier.padding(contentPadding)) {
                ForumHeaderPlaceholder(
                    forumName = state.forumName,
                    loggedIn = state.loggedIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
                ForumTabRowPlaceholder()
                repeat(4) { FeedCardPlaceholder() }
            }
        }
    }
}

/** How far a list dims while another order or class of it loads. */
private const val SwitchingAlpha = 0.4f
