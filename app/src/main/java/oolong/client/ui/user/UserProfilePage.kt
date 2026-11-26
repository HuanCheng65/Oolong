package oolong.client.ui.user

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.model.UserProfile
import oolong.client.navigation.EditProfileKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.navigation.UserProfileKey
import oolong.client.navigation.transition.openedWithHero
import oolong.client.navigation.transition.pageHeroTarget
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarRowHeight
import oolong.client.ui.designsystem.LazyLoadHorizontalPager
import oolong.client.ui.designsystem.LocalChromePadding
import oolong.client.ui.designsystem.LocalPageAccent
import oolong.client.ui.designsystem.MenuIconButton
import oolong.client.ui.designsystem.ProvideContentColor
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.SharedHeaderPager
import oolong.client.ui.designsystem.Sizes
import oolong.client.ui.designsystem.adaptive.LocalWindowSizeClass
import oolong.client.ui.designsystem.adaptive.WindowWidthSizeClass
import oolong.client.ui.designsystem.pageWash
import oolong.client.ui.designsystem.placeholder.PlaceholderHighlight
import oolong.client.ui.designsystem.placeholder.fade
import oolong.client.ui.designsystem.placeholder.placeholder
import oolong.client.ui.designsystem.rememberAvatarAccent
import oolong.client.ui.designsystem.rememberPageScrollState
import oolong.client.ui.designsystem.rememberScrollProgress
import oolong.client.ui.designsystem.rememberSharedHeaderState
import oolong.client.ui.designsystem.sharedHeaderPinned
import oolong.client.ui.designsystem.states.DefaultLoadingScreen
import oolong.client.ui.designsystem.states.StateScreen
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.user.likeforum.UserLikeForumPage
import oolong.client.ui.user.post.UserPostPage
import oolong.client.util.StringUtil
import oolong.client.util.StringUtil.getShortNumString
import oolong.client.util.TiebaUtil

@Composable
fun UserProfilePage(
    key: UserProfileKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: UserProfileViewModel = hiltViewModel<UserProfileViewModel, UserProfileViewModel.Factory> { it.create(key) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showBlack by remember { mutableStateOf(false) }
    if (showBlack) {
        state.profile?.let { user ->
            BlackDialog(
                name = user.nameShow.ifEmpty { user.name },
                settings = state.black,
                onSave = viewModel::setBlack,
                onDismiss = { showBlack = false },
            )
        }
    }
    ProvideNavigator(navigator = navigator) {
        StateScreen(
            modifier = Modifier.fillMaxSize(),
            isEmpty = state.profile == null,
            isError = state.load is LoadStatus.Error,
            isLoading = state.load == LoadStatus.Loading,
            onReload = viewModel::refreshProfile,
            // Loading and failing keep a bar with a way back, as the profile has
            errorScreen = { WithBackBar(onBack = { navigator.navigateUp() }) { ErrorScreen(error = (state.load as? LoadStatus.Error)?.error) } },
            loadingScreen = {
                val hero = openedWithHero()
                if (hero != null) {
                    UserProfileSkeleton(avatar = hero.image, onBack = { navigator.navigateUp() })
                } else {
                    WithBackBar(onBack = { navigator.navigateUp() }) { DefaultLoadingScreen() }
                }
            },
        ) {
            state.profile?.let {
                UserProfileContent(
                    user = it,
                    showActionBtn = state.loggedIn,
                    disableButton = state.following,
                    isSelf = state.isSelf,
                    openAt = key.tab,
                    onBack = { navigator.navigateUp() },
                    onFollow = viewModel::toggleFollow,
                    onUnfollow = viewModel::toggleFollow,
                    onRefresh = viewModel::refreshAll,
                    loading = state.load == LoadStatus.Loading,
                    onAddToList = viewModel::addToList,
                    onBlack = {
                        viewModel.loadBlack()
                        showBlack = true
                    }.takeIf { state.loggedIn },
                    listContent = { tab, expanded -> UserProfileList(state, tab, expanded, viewModel) },
                )
            }
        }
    }
}

/** One of the profile's lists, opened the first time it shows. */
@Composable
private fun UserProfileList(state: UserProfileState, tab: UserTab, expanded: Boolean, viewModel: UserProfileViewModel) {
    LaunchedEffect(tab) { viewModel.open(tab) }
    when (tab) {
        UserTab.Forums -> UserLikeForumPage(
            list = state.forums,
            onRefresh = { viewModel.refresh(tab) },
            onLoadMore = { viewModel.loadMore(tab) },
            fluid = expanded,
            enablePullRefresh = expanded,
        )

        else -> UserPostPage(
            list = if (tab == UserTab.Threads) state.threads else state.posts,
            hidden = state.postsHidden,
            onRefresh = { viewModel.refresh(tab) },
            onLoadMore = { viewModel.loadMore(tab) },
            onAgree = { viewModel.agree(tab, it.id, it.firstPostId) },
            fluid = expanded,
            enablePullRefresh = expanded,
        )
    }
}

@Composable
private fun UserProfileContent(
    user: UserProfile,
    showActionBtn: Boolean,
    disableButton: Boolean,
    isSelf: Boolean,
    /** The list shown first. */
    openAt: UserTab,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onRefresh: () -> Unit,
    /** Whether the profile is loading again, which a pull shows until it is done. */
    loading: Boolean,
    onAddToList: (allow: Boolean) -> Unit,
    /** Opens the blacklist settings; null when no one is logged in. */
    onBlack: (() -> Unit)?,
    listContent: @Composable (UserTab, Boolean) -> Unit,
) {
    when (LocalWindowSizeClass.current.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> {
            UserProfileContentExpanded(
                user = user,
                showActionBtn = showActionBtn,
                disableButton = disableButton,
                isSelf = isSelf,
                openAt = openAt,
                onBack = onBack,
                onFollow = onFollow,
                onUnfollow = onUnfollow,
                onAddToList = onAddToList,
                onBlack = onBlack,
                listContent = listContent,
            )
        }

        else -> {
            UserProfileContentNormal(
                user = user,
                showActionBtn = showActionBtn,
                disableButton = disableButton,
                isSelf = isSelf,
                openAt = openAt,
                onBack = onBack,
                onFollow = onFollow,
                onUnfollow = onUnfollow,
                onRefresh = onRefresh,
                loading = loading,
                onAddToList = onAddToList,
                onBlack = onBlack,
                listContent = listContent,
            )
        }
    }
}

/**
 * The bar: back, and blocking someone else; [title], and [pinnedContent] below it, [pinnedOffset]
 * further down while the header above them is still in view.
 */
@Composable
private fun UserProfileToolbar(
    isSelf: Boolean,
    onBack: () -> Unit,
    onAddToList: (allow: Boolean) -> Unit,
    /** Opens the blacklist settings; null when no one is logged in. */
    onBlack: (() -> Unit)?,
    title: @Composable () -> Unit = {},
    hiddenFraction: () -> Float = { 0f },
    pinnedOffset: () -> Int = { 0 },
    pinnedContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    GlassTopBar(
        title = title,
        navigationIcon = {
            BackNavigationIcon(onBackPressed = onBack)
        },
        actions = {
            if (!isSelf) {
                val toBlackList = stringResource(id = R.string.menu_add_user_to_black_list)
                val toWhiteList = stringResource(id = R.string.menu_add_user_to_white_list)
                val black = stringResource(id = R.string.menu_black_user)
                MenuIconButton(
                    icon = Symbols.NoAccounts,
                    contentDescription = stringResource(id = R.string.btn_block),
                    items = {
                        listOfNotNull(
                            ContextMenuItem(Symbols.VisibilityOff, toBlackList) { onAddToList(false) },
                            ContextMenuItem(Symbols.Visibility, toWhiteList) { onAddToList(true) },
                            onBlack?.let { ContextMenuItem(Symbols.Block, black, startsGroup = true, onClick = it) },
                        )
                    },
                )
            }
        },
        hiddenFraction = hiddenFraction,
        pinnedOffset = pinnedOffset,
        pinnedContent = pinnedContent,
    )
}

/** The profile's tabs: what they started, their replies to the user themselves, and the forums they follow. */
@Composable
private fun rememberProfilePages(
    isSelf: Boolean,
    listContent: @Composable (UserTab, Boolean) -> Unit,
): ImmutableList<UserProfilePageData> = remember(isSelf) {
    listOfNotNull(
        UserProfilePageData(
            id = "threads",
            tab = UserTab.Threads,
            title = { stringResource(id = R.string.title_profile_threads_tab, it.threadCount.getShortNumString()) },
            content = { tab, expanded -> listContent(tab, expanded) },
        ),
        UserProfilePageData(
            id = "posts",
            tab = UserTab.Posts,
            title = { stringResource(id = R.string.title_profile_posts_tab, it.postCount.getShortNumString()) },
            content = { tab, expanded -> listContent(tab, expanded) },
        ).takeIf { isSelf },
        UserProfilePageData(
            id = "concern_forums",
            tab = UserTab.Forums,
            title = { stringResource(id = R.string.title_profile_concern_forums_tab, it.forumCount.toString()) },
            content = { tab, expanded -> listContent(tab, expanded) },
        ),
    ).toImmutableList()
}

/** What the header's button does: edit one's own profile, or follow or unfollow someone else. */
@Composable
private fun profileButton(
    user: UserProfile,
    isSelf: Boolean,
    showActionBtn: Boolean,
    disableButton: Boolean,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
): (() -> Unit)? {
    if (!showActionBtn) return null
    val navigator = LocalNavigator.current
    return {
        when {
            disableButton -> Unit
            isSelf -> navigator.navigate(EditProfileKey)
            !user.followed -> onFollow()
            else -> onUnfollow()
        }
    }
}

/**
 * The profile on a phone: the header over the tabs' lists, going up under the bar as they scroll,
 * the avatar and name docking in the bar, and the tabs staying under it.
 */
@Composable
private fun UserProfileContentNormal(
    user: UserProfile,
    showActionBtn: Boolean,
    disableButton: Boolean,
    isSelf: Boolean,
    /** The list shown first. */
    openAt: UserTab,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onRefresh: () -> Unit,
    loading: Boolean,
    onAddToList: (allow: Boolean) -> Unit,
    /** Opens the blacklist settings; null when no one is logged in. */
    onBlack: (() -> Unit)?,
    listContent: @Composable (UserTab, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val pages = rememberProfilePages(isSelf, listContent)
    val pagerState = rememberPagerState(initialPage = pages.indexOfFirst { it.tab == openAt }.coerceAtLeast(0)) { pages.size }
    val pageScroll = rememberPageScrollState()
    val header = rememberSharedHeaderState(pageScroll)
    // Once the header has gone, the bar's row follows the scroll away and back; the tabs stay
    val away = rememberScrollProgress(away = GlassTopBarRowHeight, enabled = { header.gone }, page = pageScroll)
    val accent = rememberAvatarAccent(StringUtil.getAvatarUrl(user.portrait))
    CompositionLocalProvider(LocalPageAccent provides accent) {
        GlassScaffold(
            backgroundColor = Color.Transparent,
            pageScroll = pageScroll,
            topBar = {
                UserProfileToolbar(
                    isSelf = isSelf,
                    onBack = onBack,
                    onAddToList = onAddToList,
                    onBlack = onBlack,
                    title = { UserProfileBarTitle(user) },
                    hiddenFraction = { away.value },
                    pinnedOffset = { header.pinnedOffset },
                    pinnedContent = {
                        UserProfileTabRow(
                            user = user,
                            pages = pages,
                            pagerState = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .sharedHeaderPinned(header)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    },
                )
            },
        ) { contentPadding ->
            // A pull spins until the profile has loaded again: until loading has started and
            // ended, or at most a while if it never starts
            var pulled by remember { mutableStateOf(false) }
            val currentLoading by rememberUpdatedState(loading)
            LaunchedEffect(pulled) {
                if (!pulled) return@LaunchedEffect
                withTimeoutOrNull(PullMostMillis) {
                    snapshotFlow { currentLoading }.first { it }
                    snapshotFlow { currentLoading }.first { !it }
                }
                pulled = false
            }
            ProvideContentColor(color = ExtendedTheme.colors.text) {
                SharedHeaderPager(
                    state = header,
                    pagerState = pagerState,
                    contentPadding = contentPadding,
                    key = { pages[it].id },
                    wash = pageWash(),
                    // The header and the lists follow a pull; the colour behind them stays
                    frame = { content ->
                        PullRefresh(
                            refreshing = pulled,
                            onRefresh = {
                                onRefresh()
                                pulled = true
                            },
                            indicatorTop = contentPadding.calculateTopPadding(),
                        ) { content() }
                    },
                    header = {
                        UserProfileHeader(
                            user = user,
                            isSelf = isSelf,
                            onButton = profileButton(user, isSelf, showActionBtn, disableButton, onFollow, onUnfollow),
                            onCopyId = { TiebaUtil.copyText(context, user.tiebaUid) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                ) { page ->
                    pages[page].content(pages[page].tab, false)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserProfileContentExpanded(
    user: UserProfile,
    showActionBtn: Boolean,
    disableButton: Boolean,
    isSelf: Boolean,
    /** The list shown first. */
    openAt: UserTab,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onAddToList: (allow: Boolean) -> Unit,
    /** Opens the blacklist settings; null when no one is logged in. */
    onBlack: (() -> Unit)?,
    listContent: @Composable (UserTab, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    GlassScaffold(
        topBar = {
            UserProfileToolbar(
                isSelf = isSelf,
                onBack = onBack,
                onAddToList = onAddToList,
                onBlack = onBlack,
            )
        }
    ) { paddingValues ->
        ProvideContentColor(color = ExtendedTheme.colors.text) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .padding(top = paddingValues.calculateTopPadding())
                        .fillMaxHeight()
                        .fillMaxWidth(0.75f)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val pages = rememberProfilePages(isSelf, listContent)
                    val pagerState = rememberPagerState(initialPage = pages.indexOfFirst { it.tab == openAt }.coerceAtLeast(0)) { pages.size }

                    UserProfileHeader(
                        user = user,
                        isSelf = isSelf,
                        onButton = profileButton(user, isSelf, showActionBtn, disableButton, onFollow, onUnfollow),
                        onCopyId = { TiebaUtil.copyText(context, user.tiebaUid) },
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.Top),
                        docking = false,
                    )

                    Column(
                        modifier = Modifier.weight(2f)
                    ) {
                        UserProfileTabRow(
                            user = user,
                            pages = pages,
                            pagerState = pagerState,
                        )

                        CompositionLocalProvider(
                            LocalChromePadding provides
                                PaddingValues(bottom = paddingValues.calculateBottomPadding())
                        ) {
                            LazyLoadHorizontalPager(
                                state = pagerState,
                                key = { pages[it].id },
                                modifier = Modifier.weight(1f)
                            ) {
                                pages[it].content(pages[it].tab, true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Immutable
data class UserProfilePageData(
    val id: String,
    val tab: UserTab,
    val title: @Composable (UserProfile) -> String,
    val content: @Composable (UserTab, Boolean) -> Unit,
)

/**
 * A profile while it loads, opened from an avatar: the bar and the avatar where the header will
 * show it, so the avatar has somewhere to land; the rest stands in grey.
 */
@Composable
private fun UserProfileSkeleton(avatar: String?, onBack: () -> Unit) {
    GlassScaffold(
        topBar = {
            GlassTopBar(title = {}, navigationIcon = { BackNavigationIcon(onBackPressed = onBack) })
        },
    ) { paddingValues ->
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .padding(HeaderPadding),
        ) {
            // At the size lists show it, so it is the copy at hand, there from the start
            Avatar(
                data = avatar,
                contentDescription = null,
                modifier = Modifier
                    .pageHeroTarget()
                    .size(ProfileAvatarSize),
                decodedAt = Sizes.Small,
            )
            Box(modifier = Modifier.size(width = 160.dp, height = 24.dp).placeholder(visible = true, highlight = PlaceholderHighlight.fade()))
            Box(modifier = Modifier.size(width = 220.dp, height = 16.dp).placeholder(visible = true, highlight = PlaceholderHighlight.fade()))
        }
    }
}

/** [content] centred under a bar with only a way back, for a profile not loaded yet or failed. */
@Composable
private fun WithBackBar(onBack: () -> Unit, content: @Composable () -> Unit) {
    GlassScaffold(
        topBar = { GlassTopBar(title = {}, navigationIcon = { BackNavigationIcon(onBackPressed = onBack) }) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { content() }
    }
}

/** The longest a pull spins when loading never starts. */
private const val PullMostMillis = 10_000L
