package oolong.client.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneStrategy
import kotlinx.collections.immutable.ImmutableList
import oolong.client.LocalNotificationCountFlow
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.rememberSettings
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.transition.FramedSinglePaneSceneStrategy
import oolong.client.navigation.transition.LeadingChrome
import oolong.client.navigation.transition.LocalIsPop
import oolong.client.navigation.transition.LocalLeadingChrome
import oolong.client.navigation.transition.LocalPageOrigins
import oolong.client.navigation.transition.LocalPagePeek
import oolong.client.navigation.transition.PageBackHandler
import oolong.client.navigation.transition.PageDisplay
import oolong.client.navigation.transition.PagePeek
import oolong.client.navigation.transition.PageTransitions
import oolong.client.navigation.transition.rememberPageScenes
import oolong.client.ui.captcha.CaptchaPage
import oolong.client.ui.designsystem.LocalNavigationRailShown
import oolong.client.ui.designsystem.adaptive.LocalWindowSizeClass
import oolong.client.ui.designsystem.adaptive.WindowHeightSizeClass
import oolong.client.ui.designsystem.adaptive.WindowWidthSizeClass
import oolong.client.ui.designsystem.theme.CapTextScale
import oolong.client.ui.editor.ImageEditorHost
import oolong.client.ui.forum.ForumPage
import oolong.client.ui.forum.detail.ForumDetailPage
import oolong.client.ui.forum.rule.ForumRuleDetailPage
import oolong.client.ui.forum.searchpost.ForumSearchPostPage
import oolong.client.ui.history.HistoryPage
import oolong.client.ui.hottopic.list.HotTopicListPage
import oolong.client.ui.likes.LikesPage
import oolong.client.ui.login.LoginPage
import oolong.client.ui.login.SignedInNoticeHost
import oolong.client.ui.main.LocalMainTabs
import oolong.client.ui.main.MainPage
import oolong.client.ui.main.MainTabs
import oolong.client.ui.main.NavigationItem
import oolong.client.ui.main.NavigationRail
import oolong.client.ui.main.notifications.NotificationsPage
import oolong.client.ui.main.rememberMainTabItems
import oolong.client.ui.offline.OfflineThreadsPage
import oolong.client.ui.photoview.PhotoViewerHost
import oolong.client.ui.reader.ReaderPage
import oolong.client.ui.search.SearchPage
import oolong.client.ui.settings.SettingsPage
import oolong.client.ui.settings.about.AboutPage
import oolong.client.ui.settings.account.AccountManagePage
import oolong.client.ui.settings.block.BlockRulePage
import oolong.client.ui.settings.block.BlockSettingsPage
import oolong.client.ui.settings.block.blocklist.BlockListPage
import oolong.client.ui.settings.catalog.ControlCatalogPage
import oolong.client.ui.settings.crash.CrashLogPage
import oolong.client.ui.settings.crash.CrashLogsPage
import oolong.client.ui.settings.crash.DiagnosticsPage
import oolong.client.ui.settings.crash.ErrorRecordPage
import oolong.client.ui.settings.crash.ErrorRecordsPage
import oolong.client.ui.settings.custom.CustomSettingsPage
import oolong.client.ui.settings.custom.FontSizePage
import oolong.client.ui.settings.custom.SearchPlacementPage
import oolong.client.ui.settings.habit.HabitSettingsPage
import oolong.client.ui.settings.more.MoreSettingsPage
import oolong.client.ui.settings.oksign.OKSignSettingsPage
import oolong.client.ui.settings.post.PostSettingsPage
import oolong.client.ui.settings.privacy.BlackListPage
import oolong.client.ui.settings.privacy.DislikedForumsPage
import oolong.client.ui.settings.privacy.PrivacySettingsPage
import oolong.client.ui.settings.storage.StoragePage
import oolong.client.ui.settings.theme.AppThemePage
import oolong.client.ui.settings.theme.TranslucentThemePage
import oolong.client.ui.settings.update.UpdatePage
import oolong.client.ui.settings.widgets.WidgetsPage
import oolong.client.ui.subposts.SubPostsPage
import oolong.client.ui.subposts.SubPostsSheetPage
import oolong.client.ui.thread.ThreadPage
import oolong.client.ui.threadstore.ThreadStorePage
import oolong.client.ui.topic.TopicPage
import oolong.client.ui.user.UserProfilePage
import oolong.client.ui.user.edit.EditProfilePage
import oolong.client.ui.user.follows.UserFollowsPage
import oolong.client.ui.webview.WebViewPage

/** Whether the entry being composed is the top of the back stack, as opposed to one below a sheet or dialog. */
val LocalIsTopEntry = compositionLocalOf { true }

/** The page [key] opens. */
@Composable
private fun KeyContent(key: Key) {
    when (key) {
        MainKey -> MainPage()
        is ThreadKey -> ThreadPage(key)
        is ReaderKey -> ReaderPage(key)
        is SubPostsKey -> SubPostsPage(key)
        is SubPostsSheetKey -> SubPostsSheetPage(key)
        is ForumKey -> ForumPage(key)
        is ForumDetailKey -> ForumDetailPage(key)
        is ForumRuleDetailKey -> ForumRuleDetailPage(key)
        is ForumSearchPostKey -> ForumSearchPostPage(key)
        is UserProfileKey -> UserProfilePage(key)
        is UserFollowsKey -> UserFollowsPage(key)
        EditProfileKey -> EditProfilePage()
        is NotificationsKey -> NotificationsPage(initialTab = key.initialTab)
        is SearchKey -> SearchPage(keyword = key.keyword)
        HistoryKey -> HistoryPage()
        ThreadStoreKey -> ThreadStorePage()
        LikesKey -> LikesPage()
        OfflineThreadsKey -> OfflineThreadsPage()
        HotTopicListKey -> HotTopicListPage()
        is TopicKey -> TopicPage(key)
        is WebViewKey -> WebViewPage(key.initialUrl)
        is CaptchaKey -> CaptchaPage(key)
        LoginKey -> LoginPage()
        SettingsKey -> SettingsPage()
        AccountManageKey -> AccountManagePage()
        AppThemeKey -> AppThemePage()
        TranslucentThemeKey -> TranslucentThemePage()
        CustomSettingsKey -> CustomSettingsPage()
        FontSizeKey -> FontSizePage()
        SearchPlacementKey -> SearchPlacementPage()
        HabitSettingsKey -> HabitSettingsPage()
        PostSettingsKey -> PostSettingsPage()
        BlockSettingsKey -> BlockSettingsPage()
        BlockListKey -> BlockListPage()
        DislikedForumsKey -> DislikedForumsPage()
        PrivacySettingsKey -> PrivacySettingsPage()
        BlackListKey -> BlackListPage()
        is BlockRuleKey -> BlockRulePage(key)
        OKSignSettingsKey -> OKSignSettingsPage()
        MoreSettingsKey -> MoreSettingsPage()
        AboutKey -> AboutPage()
        UpdateKey -> UpdatePage()
        StorageKey -> StoragePage()
        CrashLogsKey -> CrashLogsPage()
        is CrashLogKey -> CrashLogPage(key)
        ErrorRecordsKey -> ErrorRecordsPage()
        WidgetsKey -> WidgetsPage()
        is ErrorRecordKey -> ErrorRecordPage(key)
        DiagnosticsKey -> DiagnosticsPage()
        ControlCatalogKey -> ControlCatalogPage()
    }
}

/** Where [this] goes when two panes fit; null for pages that show over others. */
internal val Key.paneRole: PaneRole?
    get() = when (this) {
        MainKey, is ForumKey, is SearchKey, HistoryKey, ThreadStoreKey, LikesKey, OfflineThreadsKey, HotTopicListKey, is TopicKey,
        is ForumSearchPostKey, SettingsKey, is NotificationsKey -> PaneRole.List

        // The reader takes the whole window, laying out its own contents beside the text when wide
        LoginKey, TranslucentThemeKey, is ReaderKey -> PaneRole.Full
        is SubPostsSheetKey -> null
        else -> PaneRole.Detail
    }

/**
 * Whether [this] is read rather than scanned, so it keeps to [MaxReadingWidth] on wide windows.
 * Lists lay themselves out for the width they get; web pages and logging in are sized by the web.
 */
private val Key.readsAtWidth: Boolean
    get() = paneRole == PaneRole.Detail && this !is WebViewKey && this !is CaptchaKey

/** Whether [this] is done and left, as logging in or reporting a post, shown as a sheet on wide windows. */
private val Key.isTask: Boolean
    get() = this == LoginKey || (this is WebViewKey && task) || this is CaptchaKey

private fun Key.metadata(): Map<String, Any> = when (presentation) {
    Presentation.Sheet -> BottomSheetSceneStrategy.bottomSheet()
    Presentation.Page -> paneRole(checkNotNull(paneRole)) + if (isTask) FormSheetSceneStrategy.formSheet() else emptyMap()
}

/**
 * Shows [backStack], with the navigation rail beside it on windows wider than a phone. Scenes
 * are tried in order: overlays first, then any added before the panes, two panes where they
 * fit, and the single pane, which takes whatever is left.
 */
@Composable
fun AppNavDisplay(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
    sceneStrategies: List<SceneStrategy<NavKey>> = emptyList(),
) {
    val windowSizeClass = LocalWindowSizeClass.current
    // Two panes need the width for both and the height for their bars; a phone held sideways
    // is wide enough but too short, and keeps one pane
    val twoPane = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded &&
        windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact
    val wide = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    // Beside the pages on windows wider than a phone; a page over the whole window goes over it.
    // Not in a window opened on one page, which has no home or tabs to go to
    val showRail = wide && backStack.firstOrNull()?.key == MainKey
    // Room for a phone-sized sheet with the pages showing round it
    val roomy = wide && windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact

    val tabs = rememberSaveable(saver = MainTabs.Saver) { MainTabs() }
    val notificationCountFlow = LocalNotificationCountFlow.current
    LaunchedEffect(notificationCountFlow) {
        notificationCountFlow.collect { tabs.messageCount = it }
    }

    val listDetailState = rememberListDetailState()
    val onBack: () -> Unit = remember(backStack) { { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) } }
    val strategies = remember(twoPane, roomy, sceneStrategies, listDetailState, onBack) {
        listOf(DialogSceneStrategy<NavKey>(onBack), BottomSheetSceneStrategy<NavKey>(onBack), FormSheetSceneStrategy<NavKey>(roomy, onBack)) +
            sceneStrategies +
            listOfNotNull(if (twoPane) listDetailState.strategy else null) +
            FramedSinglePaneSceneStrategy()
    }

    val navigator = remember(backStack) { BackStackNavigator(backStack) }
    SideEffect { navigator.twoPane = twoPane }
    val overlap = PageTransitions.rememberOverlap()
    val ids = backStack.map { (it as Page).id }
    val previousIds = remember { mutableStateOf(ids) }
    val railJump = remember { RailJump() }
    val change = remember(ids) {
        val previous = previousIds.value
        previousIds.value = ids
        BackStackChange(before = previous.toSet(), after = ids.toSet(), jump = railJump.pending)
            .also { railJump.pending = false }
    }
    val isPop = remember(ids) {
        change.before.size > ids.size && change.before.containsAll(ids)
    }
    // The window changing between one pane and two lays the same pages out again; that is not
    // a page opening, so it does not slide
    val previousTwoPane = remember { mutableStateOf(twoPane) }
    val relayout = remember(ids, twoPane) {
        val changed = previousTwoPane.value != twoPane
        previousTwoPane.value = twoPane
        changed
    }
    CompositionLocalProvider(
        LocalMainTabs provides tabs,
        LocalNavigationRailShown provides showRail,
        LocalIsPop provides isPop,
        LocalBackStackChange provides change,
    ) {
        ImageEditorHost {
            PhotoViewerHost {
                // Over the pages, under the viewers: a login page's card stays as it goes back
                SignedInNoticeHost {
                    // The rail lies under the pages and keeps its width whatever they are: pages leave
                    // room beside it, and one over the whole window goes over it
                    // ([BesideLeadingChrome]), so opening one lays nothing out again
                    var railWidth by remember { mutableStateOf(0.dp) }
                    val density = LocalDensity.current
                    // While such a page shows, coming or going, the pages may draw over the rail;
                    // else they end at it, so a page sliding off beside it doesn't pass over it
                    var coverShown by remember { mutableStateOf(false) }
                    // A form sheet dims the rail with the pages, and stands in the middle of the window
                    var sheetShown by remember { mutableStateOf(false) }
                    val chrome = remember(railWidth, showRail) {
                        LeadingChrome(if (showRail) railWidth else 0.dp) { it.paneRole == PaneRole.Full }
                    }
                    Box(modifier = modifier.fillMaxSize()) {
                        // Over the wallpaper nothing would hide it, so there it goes while such a page shows
                        if (showRail && (overlap.value || !coverShown)) {
                            Box(modifier = Modifier.onSizeChanged { railWidth = with(density) { it.width.toDp() } }) {
                                MainRail(backStack, tabs, railJump, showLabels = windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact)
                            }
                        }
                        // The display lays out no container of its own: sheets and dialogs are emitted beside
                        // the pages, so they need a box here to cover them
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    val left = if (coverShown || sheetShown || !showRail) 0f else railWidth.toPx()
                                    clipRect(left = left, top = 0f, right = size.width, bottom = size.height) { this@drawWithContent.drawContent() }
                                },
                        ) {
                            val pagePeek = remember { PagePeek() }
                            val pageOrigins = LocalPageOrigins.current
                            val entries = rememberDecoratedNavEntries(
                                backStack = backStack,
                                entryDecorators = listOf(
                                    rememberSaveableStateHolderNavEntryDecorator(),
                                    rememberViewModelStoreNavEntryDecorator(),
                                ),
                                entryProvider = entryProvider {
                                    entry<Page>(
                                        clazzContentKey = { it.id },
                                        metadata = { it.key.metadata() },
                                    ) { page ->
                                        val isTop = backStack.lastOrNull() == page
                                        val pageNavigator = remember(navigator, page) { navigator.forPage(page) }
                                        CompositionLocalProvider(
                                            LocalIsTopEntry provides isTop,
                                            LocalNavigator provides pageNavigator,
                                        ) {
                                            if (page.key.readsAtWidth) {
                                                ReadingWidth { KeyContent(page.key) }
                                            } else {
                                                KeyContent(page.key)
                                            }
                                        }
                                    }
                                },
                            )
                            val scenes = rememberPageScenes(entries, strategies)
                            PageBackHandler(scenes, pagePeek, onBack)
                            CompositionLocalProvider(LocalPagePeek provides pagePeek, LocalLeadingChrome provides chrome) {
                                PageDisplay(
                                    scenes = scenes,
                                    peek = pagePeek,
                                    modifier = Modifier.fillMaxSize(),
                                    onShown = { shown ->
                                        coverShown = shown.any { chrome.covers(it) }
                                        sheetShown = shown.any { isFormSheet(it) }
                                    },
                                ) { from, to, peek ->
                                    PageTransitions.between(from, to, overlap, relayout, peek, pageOrigins, chrome.takeIf { showRail })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The pane layout, its list width kept across launches once the divider has been moved. */
@Composable
private fun rememberListDetailState(): ListDetailStateHolder {
    val settings = rememberSettings()
    return remember(settings) {
        val state = ListDetailState()
        settings[SettingKeys.ListPaneWidth]?.let { state.listWidth = it.dp }
        ListDetailStateHolder(
            state = state,
            strategy = ListDetailSceneStrategy(state) { width -> settings[SettingKeys.ListPaneWidth] = width?.value },
        )
    }
}

private class ListDetailStateHolder(val state: ListDetailState, val strategy: ListDetailSceneStrategy<NavKey>)

/** Set by the rail just before it clears the stack, so the panes switch across instead of stepping back. */
private class RailJump {
    var pending = false
}

/**
 * The rail beside the panes. Choosing a tab closes every open page and shows the main page on
 * that tab; choosing the tab already shown, with nothing open, refreshes it.
 */
@Composable
private fun MainRail(backStack: NavBackStack<NavKey>, tabs: MainTabs, jump: RailJump, showLabels: Boolean) {
    val items = rememberMainTabItems(tabs)
    val selectedIndex = items.indexOfFirst { it.id == tabs.selected }.coerceAtLeast(0)
    // Chrome, its labels growing only so far with the text size, as the bars' do
    CapTextScale { MainRailContent(backStack, tabs, jump, showLabels, items, selectedIndex) }
}

@Composable
private fun MainRailContent(
    backStack: NavBackStack<NavKey>,
    tabs: MainTabs,
    jump: RailJump,
    showLabels: Boolean,
    items: ImmutableList<NavigationItem>,
    selectedIndex: Int,
) {
    NavigationRail(
        currentPosition = selectedIndex,
        onSelect = { index ->
            val item = items[index]
            if (backStack.size == 1 && index == selectedIndex) {
                tabs.reselect(item.id)
            } else {
                jump.pending = backStack.size > 1
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                // A window opened on one page has no home under it: the rail brings it
                if (backStack.firstOrNull()?.key != MainKey) {
                    backStack.clear()
                    backStack.add(Page(MainKey))
                }
                tabs.selected = item.id
            }
            item.onClick?.invoke()
        },
        navigationItems = items,
        showLabels = showLabels,
    )
}
