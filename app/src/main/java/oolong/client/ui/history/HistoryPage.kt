package oolong.client.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.data.history.Incognito
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.rememberSettings
import oolong.client.model.HistoryEntry
import oolong.client.navigation.ForumKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ThreadKey
import oolong.client.ui.designsystem.ActionItem
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.ConfirmDialog
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTab
import oolong.client.ui.designsystem.GlassTabRow
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.MenuIconButton
import oolong.client.ui.designsystem.MyBackHandler
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.TabText
import oolong.client.ui.designsystem.TextButton
import oolong.client.ui.designsystem.floatingGlass
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.history.list.HistoryListContent
import oolong.client.ui.thread.ThreadPageFrom
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.TextStyles

@Composable
fun HistoryPage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keepsHistory by Incognito.keepsHistory.collectAsState()
    val settings = rememberSettings()
    HistoryScreen(
        state = state,
        historyOff = !keepsHistory,
        onTurnOnHistory = { settings[SettingKeys.KeepHistory] = true },
        onBack = { navigator.navigateUp() },
        onClear = viewModel::clear,
        onLoadMore = viewModel::loadMore,
        onDelete = viewModel::delete,
        onSearch = viewModel::search,
        onStartSelecting = viewModel::startSelecting,
        onStopSelecting = viewModel::stopSelecting,
        onSelect = viewModel::toggle,
        onSelectAll = viewModel::toggleAll,
        onDeleteSelected = viewModel::deleteSelected,
        onOpen = { entry ->
            when (entry) {
                is HistoryEntry.Forum -> navigator.navigate(ForumKey(entry.name))
                is HistoryEntry.Thread -> navigator.navigate(
                    ThreadKey(
                        entry.threadId,
                        postId = entry.postId,
                        seeLz = entry.seeLz,
                        from = ThreadPageFrom.FROM_HISTORY
                    )
                )
            }
        },
    )
}

/**
 * The visited threads and forums, in two tabs that are the bar's title. While visits are being
 * chosen the bar says how many are, and a button at the bottom deletes them. [historyOff], each
 * tab says so above its visits.
 */
@Composable
fun HistoryScreen(
    state: HistoryState,
    onBack: () -> Unit,
    onClear: (HistoryRange) -> Unit,
    onLoadMore: (HistoryTab) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onOpen: (HistoryEntry) -> Unit,
    onSearch: (String) -> Unit = {},
    onStartSelecting: (HistoryEntry?) -> Unit = {},
    onStopSelecting: () -> Unit = {},
    onSelect: (HistoryEntry) -> Unit = {},
    onSelectAll: (HistoryTab) -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    historyOff: Boolean = false,
    onTurnOnHistory: () -> Unit = {},
) {
    val pagerState = rememberPagerState { HistoryTab.entries.size }
    val coroutineScope = rememberCoroutineScope()
    val selected = state.selected
    val tab = HistoryTab.entries[pagerState.currentPage]
    MyBackHandler(enabled = selected != null, onBack = onStopSelecting)

    val clearDialog = rememberDialogState()
    var clearing by rememberSaveable { mutableStateOf(HistoryRange.All) }
    ConfirmDialog(
        dialogState = clearDialog,
        onConfirm = { onClear(clearing) },
        confirmText = stringResource(id = R.string.history_clear),
        title = {
            Text(
                text = stringResource(
                    id = when (clearing) {
                        HistoryRange.LastHour -> R.string.history_clear_last_hour_title
                        HistoryRange.Today -> R.string.history_clear_today_title
                        HistoryRange.All -> R.string.history_clear_all_title
                    }
                )
            )
        },
    ) {
        Text(text = stringResource(id = R.string.history_clear_message))
    }
    val clearItems = HistoryRange.entries.map { range ->
        ContextMenuItem(
            icon = if (range == HistoryRange.All) Symbols.Delete else Symbols.Schedule,
            label = stringResource(
                id = when (range) {
                    HistoryRange.LastHour -> R.string.history_clear_last_hour
                    HistoryRange.Today -> R.string.history_clear_today
                    HistoryRange.All -> R.string.history_clear_all
                }
            ),
            onClick = {
                clearing = range
                clearDialog.show()
            },
        )
    }

    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            // One bar for browsing and choosing: its title, button and actions refocus in place
            val choosing = selected != null
            GlassTopBar(
                title = {
                    Refocus(targetState = choosing) { now ->
                        if (now) {
                            val count = selected?.size ?: 0
                            Refocus(targetState = count) {
                                Text(text = pluralStringResource(id = R.plurals.history_selected, it, it))
                            }
                        } else {
                            GlassTabRow(
                                selectedTabIndex = pagerState.currentPage,
                                pagerState = pagerState,
                                onSelect = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                            ) {
                                HistoryTab.entries.forEachIndexed { index, tab ->
                                    GlassTab(
                                        text = {
                                            TabText(
                                                text = stringResource(
                                                    id = when (tab) {
                                                        HistoryTab.Threads -> R.string.title_history_tab_threads
                                                        HistoryTab.Forums -> R.string.title_history_tab_forums
                                                    }
                                                ),
                                                selected = pagerState.currentPage == index,
                                            )
                                        },
                                        selected = pagerState.currentPage == index,
                                        selectedContentColor = ExtendedTheme.colors.text,
                                        unselectedContentColor = ExtendedTheme.colors.textSecondary,
                                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                                    )
                                }
                            }
                        }
                    }
                },
                centerTitle = true,
                navigationIcon = {
                    Refocus(targetState = choosing) { now ->
                        if (now) {
                            ActionItem(icon = Symbols.Close, contentDescription = stringResource(id = R.string.button_cancel), onClick = onStopSelecting)
                        } else {
                            BackNavigationIcon(onBackPressed = onBack)
                        }
                    }
                },
                actions = {
                    Refocus(targetState = choosing) { now ->
                        if (now) {
                            val chosen = selected.orEmpty()
                            val shown = state.list(tab).sections.sumOf { it.entries.size }
                            val allChosen = shown > 0 && state.list(tab).sections.all { section -> section.entries.all { it.id in chosen } }
                            TextButton(onClick = { onSelectAll(tab) }) {
                                Refocus(targetState = allChosen) {
                                    Text(
                                        text = stringResource(id = if (it) R.string.history_select_none else R.string.history_select_all),
                                        color = ExtendedTheme.colors.topBarContent,
                                    )
                                }
                            }
                        } else {
                            Row {
                                ActionItem(icon = Symbols.Checklist, contentDescription = stringResource(id = R.string.history_select), onClick = { onStartSelecting(null) })
                                MenuIconButton(icon = Symbols.MoreVert, contentDescription = stringResource(id = R.string.btn_more), items = { clearItems })
                            }
                        }
                    }
                },
            )
        },
        overlay = {
            var lastCount by remember { mutableIntStateOf(0) }
            if (selected != null) lastCount = selected.size
            DeleteSelectedButton(visible = selected != null, count = selected?.size ?: lastCount, onClick = onDeleteSelected)
        },
    ) { contentPaddings ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { it },
            verticalAlignment = Alignment.Top,
            // Choosing is in one tab at a time
            userScrollEnabled = selected == null,
        ) { page ->
            when (HistoryTab.entries[page]) {
                HistoryTab.Threads -> HistoryListContent(
                    list = state.threads,
                    query = state.query,
                    searchHint = R.string.history_search_threads,
                    onQueryChange = onSearch,
                    onLoadMore = { onLoadMore(HistoryTab.Threads) },
                    onOpen = onOpen,
                    onDelete = onDelete,
                    selected = selected,
                    onSelect = onSelect,
                    onStartSelecting = onStartSelecting,
                    historyOff = historyOff,
                    onTurnOnHistory = onTurnOnHistory,
                    contentPadding = contentPaddings,
                )

                HistoryTab.Forums -> HistoryListContent(
                    list = state.forums,
                    query = state.query,
                    searchHint = R.string.history_search_forums,
                    onQueryChange = onSearch,
                    onLoadMore = { onLoadMore(HistoryTab.Forums) },
                    onOpen = onOpen,
                    onDelete = onDelete,
                    selected = selected,
                    onSelect = onSelect,
                    onStartSelecting = onStartSelecting,
                    historyOff = historyOff,
                    onTurnOnHistory = onTurnOnHistory,
                    contentPadding = contentPaddings,
                )
            }
        }
    }
}

/**
 * A glass capsule floating at the bottom that deletes the [count] visits chosen. It rises from
 * below the screen while [visible] and goes back down after.
 */
@Composable
private fun DeleteSelectedButton(visible: Boolean, count: Int, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Far enough down to clear the navigation bar and the margin under it
        // With motion reduced it only fades in where it stands
        val reduced = LocalReduceMotion.current
        AnimatedVisibility(
            visible = visible,
            enter = if (reduced) fadeIn(Motion.fadeIn()) else slideInVertically(Motion.present()) { it * 3 },
            exit = if (reduced) fadeOut(Motion.fadeOut()) else slideOutVertically(Motion.dismiss()) { it * 3 },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    // Always answering presses, so its glass keeps the same modifiers while the count
                    // comes and goes; with nothing chosen the click is off and no press comes
                    .floatingGlass(Corners.full, pressable = true)
                    .clickable(
                        interactionSource = interactionSource,
                        // The glass answers the press itself
                        indication = null,
                        enabled = count > 0,
                        role = Role.Button,
                        onClick = onClick,
                    ),
            ) {
                val color by animateColorAsState(if (count > 0) MaterialTheme.colors.error else ExtendedTheme.colors.textDisabled, Motion.settle(), label = "deleteColor")
                Icon(imageVector = Symbols.Delete, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pluralStringResource(id = R.plurals.history_delete_selected, count, count),
                    color = color,
                    style = TextStyles.label,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
