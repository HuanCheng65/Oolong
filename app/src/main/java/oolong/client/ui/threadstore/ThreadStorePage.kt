package oolong.client.ui.threadstore

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.arch.PagerState
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.model.StoredThread
import oolong.client.model.User
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ThreadKey
import oolong.client.navigation.UserProfileKey
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.components.LoadMoreEffect
import oolong.client.ui.components.UserHeader
import oolong.client.ui.components.inlineContentSafe
import oolong.client.ui.components.loadMoreFooter
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.EndPull
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.Sizes
import oolong.client.ui.designsystem.UserHeader
import oolong.client.ui.designsystem.longPressMenu
import oolong.client.ui.designsystem.motionItem
import oolong.client.ui.designsystem.states.StateScreen
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.thread.ThreadPageFrom
import oolong.client.ui.thread.ThreadPageFromStoreExtra
import oolong.client.ui.thread.ThreadSortType
import oolong.client.util.StringUtil
import oolong.client.util.StringUtil.getUsernameAnnotatedString
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Motion

/** How far a stored thread dims while it is being taken out. */
private const val RemovingAlpha = 0.4f

private val UpdateTipTextStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 10.sp)

@Composable
fun ThreadStorePage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: ThreadStoreViewModel = hiltViewModel(),
) {
    val list by viewModel.state.collectAsStateWithLifecycle()
    val removing by viewModel.removing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ThreadStoreScreen(
        list = list,
        removing = removing,
        onBack = { navigator.navigateUp() },
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onRemove = viewModel::remove,
        onOpenUser = { navigator.navigate(UserProfileKey(it.id)) },
        onOpen = { thread ->
            navigator.navigate(
                ThreadKey(
                    threadId = thread.threadId,
                    postId = thread.markPostId,
                    seeLz = Settings.of(context)[SettingKeys.CollectThreadSeeLz],
                    sortType = if (Settings.of(context)[SettingKeys.CollectThreadDescSort]) ThreadSortType.SORT_TYPE_DESC else ThreadSortType.SORT_TYPE_DEFAULT,
                    from = ThreadPageFrom.FROM_STORE,
                    extra = ThreadPageFromStoreExtra(
                        maxPid = thread.maxPostId,
                        maxFloor = thread.latestFloor
                    )
                )
            )
        },
    )
}

/** The threads the user stored; pulls down to refresh, and long pressing one offers to remove it. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ThreadStoreScreen(
    list: PagerState<StoredThread>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRemove: (StoredThread) -> Unit,
    onOpenUser: (User) -> Unit,
    onOpen: (StoredThread) -> Unit,
    removing: Set<Long> = emptySet(),
) {
    val isRefreshing = list.refresh == LoadStatus.Loading
    val error = (list.refresh as? LoadStatus.Error)?.error
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_my_collect)) },
                navigationIcon = {
                    BackNavigationIcon(onBackPressed = onBack)
                }
            )
        }
    ) { contentPaddings ->
        StateScreen(
            isEmpty = list.items.isEmpty(),
            isError = error != null,
            isLoading = isRefreshing,
            modifier = Modifier.fillMaxSize(),
            onReload = onRefresh,
            // Below the bar, as the list would be
            errorScreen = { ErrorScreen(error = error, modifier = Modifier.padding(contentPaddings)) },
        ) {
            val lazyListState = rememberLazyListState()

            LoadMoreEffect(lazyListState, enabled = list.hasMore && list.append == LoadStatus.Idle, onLoadMore = onLoadMore)
            PullRefresh(
                refreshing = isRefreshing,
                onRefresh = onRefresh,
                indicatorTop = contentPaddings.calculateTopPadding(),
                endPull = if (list.hasMore) EndPull.None else EndPull.Tease,
            ) {
                MyLazyColumn(state = lazyListState, contentPadding = contentPaddings) {
                    items(
                        items = list.items,
                        key = { it.threadId }
                    ) { thread ->
                        // Taken out at once, it waits dimmed for the server, then goes, or comes back
                        val dim by animateFloatAsState(if (thread.threadId in removing) RemovingAlpha else 1f, Motion.settle(), label = "removing")
                        StoreItem(
                            info = thread,
                            onUserClick = { thread.author?.let(onOpenUser) },
                            onClick = { onOpen(thread) },
                            onDelete = { onRemove(thread) },
                            modifier = Modifier.motionItem(this).graphicsLayer { alpha = dim },
                        )
                    }
                    loadMoreFooter(list.append, list.hasMore, onLoadMore)
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun StoreItem(
    info: StoredThread,
    onUserClick: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasUpdate = info.hasUpdate
    val isDeleted = info.deleted
    val textMeasurer = rememberTextMeasurer()
    val unstore = stringResource(id = R.string.title_collect_on)
    Box(
        modifier = Modifier.longPressMenu(
            items = { listOf(ContextMenuItem(Symbols.BookmarkRemove, unstore, onClick = onDelete)) },
            onClick = onClick,
        ),
    ) {
        Column(
            modifier = modifier
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UserHeader(
                avatar = {
                    Avatar(
                        data = StringUtil.getAvatarUrl(info.author?.portrait),
                        size = Sizes.Small,
                        contentDescription = null
                    )
                },
                name = {
                    Text(
                        text = getUsernameAnnotatedString(
                            LocalContext.current,
                            info.author?.name.orEmpty(),
                            info.author?.nameShow,
                            LocalContentColor.current
                        )
                    )
                },
                onClick = onUserClick,
            )
            val title = remember(info, hasUpdate) {
                buildAnnotatedString {
                    append(info.title)
                    if (hasUpdate) {
                        append(" ")
                        appendInlineContent("Update", info.latestFloor.toString())
                    }
                }
            }
            val updateTip = stringResource(
                id = R.string.tip_thread_store_update,
                info.latestFloor.toString()
            )
            val result = remember {
                textMeasurer.measure(
                    AnnotatedString(updateTip),
                    style = UpdateTipTextStyle
                ).size
            }
            // In the density the title is drawn in, whose text scale is the app's own
            val (width, height) = with(LocalDensity.current) {
                (result.width.toDp() + 12.dp * 2 + 1.dp).toSp() to (result.height.toDp() + 4.dp * 2).toSp()
            }
            Text(
                text = title,
                modifier = Modifier.inlineContentSafe(),
                fontSize = 15.sp,
                color = if (isDeleted) ExtendedTheme.colors.textDisabled else ExtendedTheme.colors.text,
                inlineContent = mapOf(
                    "Update" to InlineTextContent(
                        placeholder = Placeholder(
                            width = width,
                            height = height,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        ),
                        children = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = ExtendedTheme.colors.chip,
                                        shape = Corners.xs
                                    )
                                    .padding(vertical = 4.dp, horizontal = 12.dp)
                            ) {
                                Text(
                                    text = updateTip,
                                    style = UpdateTipTextStyle,
                                    color = ExtendedTheme.colors.onChip,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    )
                )
            )
            if (isDeleted) {
                Text(
                    text = stringResource(id = R.string.tip_thread_store_deleted),
                    fontSize = 12.sp,
                    color = ExtendedTheme.colors.textDisabled
                )
            }
        }
    }
}
