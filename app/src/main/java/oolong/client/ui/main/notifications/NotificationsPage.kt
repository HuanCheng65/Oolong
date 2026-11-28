package oolong.client.ui.main.notifications

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.model.NotificationType
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTab
import oolong.client.ui.designsystem.GlassTabRow
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.LazyLoadHorizontalPager
import oolong.client.ui.designsystem.TabText
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.main.MainTabs
import oolong.client.ui.main.OnTabReselected
import oolong.client.ui.main.notifications.likes.ReceivedLikesListPage
import oolong.client.ui.main.notifications.likes.ReceivedLikesViewModel
import oolong.client.ui.main.notifications.list.NotificationsListPage
import oolong.client.ui.main.notifications.list.NotificationsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationsPage(
    navigator: Navigator = LocalNavigator.current,
    initialTab: Int = 0,
) {
    val pages = notificationPages()
    val pagerState = rememberPagerState(
        initialPage = initialTab,
    ) { pages.size }
    val coroutineScope = rememberCoroutineScope()
    ProvideNavigator(navigator = navigator) {
        GlassScaffold(
            backgroundColor = Color.Transparent,
            topBar = {
                GlassTopBar(
                    title = { NotificationTabs(pagerState, pages.map { it.first }) },
                    centerTitle = true,
                    navigationIcon = {
                        BackNavigationIcon {
                            navigator.navigateUp()
                        }
                    },
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { paddingValues ->
            LazyLoadHorizontalPager(
                state = pagerState,
                key = { pages[it].first },
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
                userScrollEnabled = true,
            ) {
                pages[it].second(paddingValues)
            }
        }
    }
}

/** The notifications tab of the main page; [NotificationsPage] is the page links open. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationsTab(
    initialTab: Int = 0,
    viewModel: NotificationsViewModel = hiltViewModel(),
    likesViewModel: ReceivedLikesViewModel = hiltViewModel(),
) {
    val pages = notificationPages()
    val pagerState = rememberPagerState(
        initialPage = initialTab,
    ) { pages.size }
    // Tapping the tab again refreshes the list in view
    OnTabReselected(MainTabs.NOTIFICATIONS) {
        when (val type = NotificationTypes.getOrNull(pagerState.currentPage)) {
            null -> likesViewModel.refresh()
            else -> viewModel.refresh(type)
        }
    }
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { NotificationTabs(pagerState, pages.map { it.first }) },
                centerTitle = true,
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { paddingValues ->
        LazyLoadHorizontalPager(
            state = pagerState,
            key = { pages[it].first },
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            userScrollEnabled = true,
        ) {
            pages[it].second(paddingValues)
        }
    }
}

/** The notification lists, in the order of the tabs; the likes come after them. */
private val NotificationTypes = listOf(NotificationType.ReplyMe, NotificationType.AtMe)

/** The tabs' names and pages: replies, mentions, likes. */
@Composable
private fun notificationPages() = listOf<Pair<String, (@Composable (PaddingValues) -> Unit)>>(
    stringResource(id = R.string.messages_tab_replies) to @Composable {
        NotificationsListPage(type = NotificationType.ReplyMe, contentPadding = it)
    },
    stringResource(id = R.string.messages_tab_mentions) to @Composable {
        NotificationsListPage(type = NotificationType.AtMe, contentPadding = it)
    },
    stringResource(id = R.string.messages_tab_likes) to @Composable {
        ReceivedLikesListPage(contentPadding = it)
    },
)

/** The pages' names on a tab capsule in the bar's title row: on these pages the tabs are the title. */
@Composable
private fun NotificationTabs(pagerState: PagerState, names: List<String>) {
    val coroutineScope = rememberCoroutineScope()
    GlassTabRow(
        selectedTabIndex = pagerState.currentPage,
        pagerState = pagerState,
        onSelect = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
    ) {
        names.forEachIndexed { index, name ->
            GlassTab(
                text = { TabText(name, pagerState.currentPage == index) },
                selected = pagerState.currentPage == index,
                selectedContentColor = ExtendedTheme.colors.text,
                unselectedContentColor = ExtendedTheme.colors.textSecondary,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
            )
        }
    }
}
