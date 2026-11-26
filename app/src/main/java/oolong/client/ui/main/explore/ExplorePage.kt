package oolong.client.ui.main.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.navigation.LocalNavigator
import oolong.client.ui.components.LocalAccount
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTab
import oolong.client.ui.designsystem.GlassTabRow
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.LazyLoadHorizontalPager
import oolong.client.ui.designsystem.TabText
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.main.LocalMainTabs
import oolong.client.ui.main.MainTabs
import oolong.client.ui.main.OnTabReselected
import oolong.client.ui.main.explore.concern.ConcernPage
import oolong.client.ui.main.explore.hot.HotPage
import oolong.client.ui.main.explore.personalized.PersonalizedPage

@Immutable
data class ExplorePageItem(
    val id: String,
    val name: @Composable (selected: Boolean) -> Unit,
    val content: @Composable (contentPadding: PaddingValues) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorePageTab(
    pagerState: PagerState,
    pages: ImmutableList<ExplorePageItem>
) {
    val coroutineScope = rememberCoroutineScope()
    val tabs = LocalMainTabs.current

    GlassTabRow(
        selectedTabIndex = pagerState.currentPage,
        pagerState = pagerState,
        onSelect = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
    ) {
        pages.fastForEachIndexed { index, item ->
            GlassTab(
                text = { item.name(pagerState.currentPage == index) },
                selected = pagerState.currentPage == index,
                selectedContentColor = ExtendedTheme.colors.text,
                unselectedContentColor = ExtendedTheme.colors.textSecondary,
                onClick = {
                    if (pagerState.currentPage == index) {
                        tabs.reselect(item.id)
                    } else {
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorePage() {
    val account = LocalAccount.current
    val navigator = LocalNavigator.current

    val loggedIn = remember(account) { account != null }

    val pages = remember {
        listOfNotNull(
            if (loggedIn) ExplorePageItem(
                "concern",
                { TabText(text = stringResource(id = R.string.title_concern), selected = it) },
                { ConcernPage(contentPadding = it) }
            ) else null,
            ExplorePageItem(
                "personalized",
                { TabText(text = stringResource(id = R.string.title_personalized), selected = it) },
                { PersonalizedPage(contentPadding = it) }
            ),
            ExplorePageItem(
                "hot",
                { TabText(text = stringResource(id = R.string.title_hot), selected = it) },
                { HotPage(contentPadding = it) }
            ),
        ).toImmutableList()
    }
    val pagerState = rememberPagerState(initialPage = if (account != null) 1 else 0) { pages.size }
    val tabs = LocalMainTabs.current
    OnTabReselected(MainTabs.EXPLORE) { tabs.reselect(pages[pagerState.currentPage].id) }

    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            // The tabs are the page's title; the bottom bar already names the page
            GlassTopBar(title = { ExplorePageTab(pagerState = pagerState, pages = pages) }, centerTitle = true)
        },
        modifier = Modifier.fillMaxSize(),
    ) { paddingValues ->
        LazyLoadHorizontalPager(
            state = pagerState,
            key = { pages[it].id },
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
            userScrollEnabled = true,
        ) {
            pages[it].content(paddingValues)
        }
    }
}
