package oolong.client.ui.page.history

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
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import oolong.client.Incognito
import oolong.client.KeepHistoryKey
import oolong.client.R
import oolong.client.dataStore
import oolong.client.model.HistoryEntry
import oolong.client.ui.common.theme.compose.Corners
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.PressFeedback
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.ui.common.theme.compose.pressScale
import oolong.client.ui.navigation.ForumKey
import oolong.client.ui.navigation.Navigator
import oolong.client.ui.navigation.ThreadKey
import oolong.client.ui.page.LocalNavigator
import oolong.client.ui.page.history.list.HistoryListContent
import oolong.client.ui.page.thread.ThreadPageFrom
import oolong.client.ui.widgets.compose.ActionItem
import oolong.client.ui.widgets.compose.BackNavigationIcon
import oolong.client.ui.widgets.compose.ConfirmDialog
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.ui.widgets.compose.GlassScaffold
import oolong.client.ui.widgets.compose.GlassTab
import oolong.client.ui.widgets.compose.GlassTabRow
import oolong.client.ui.widgets.compose.GlassTopBar
import oolong.client.ui.widgets.compose.MenuIconButton
import oolong.client.ui.widgets.compose.MyBackHandler
import oolong.client.ui.widgets.compose.floatingGlass
import oolong.client.ui.widgets.compose.isGlassAvailable
import oolong.client.ui.widgets.compose.rememberDialogState

@Composable
fun HistoryPage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keepsHistory by Incognito.keepsHistory.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    HistoryScreen(
        state = state,
        historyOff = !keepsHistory,
        onTurnOnHistory = { scope.launch { context.dataStore.edit { it[KeepHistoryKey] = true } } },
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
            if (selected != null) {
                val shown = state.list(tab).sections.sumOf { it.entries.size }
                val allChosen = shown > 0 && state.list(tab).sections.all { section -> section.entries.all { it.id in selected } }
                GlassTopBar(
                    title = { Text(text = pluralStringResource(id = R.plurals.history_selected, selected.size, selected.size)) },
                    navigationIcon = {
                        ActionItem(icon = Symbols.Close, contentDescription = stringResource(id = R.string.button_cancel), onClick = onStopSelecting)
                    },
                    actions = {
                        TextButton(onClick = { onSelectAll(tab) }) {
                            Text(
                                text = stringResource(id = if (allChosen) R.string.history_select_none else R.string.history_select_all),
                                color = ExtendedTheme.colors.topBarContent,
                            )
                        }
                    },
                )
            } else {
                GlassTopBar(
                    title = {
                        GlassTabRow(
                            selectedTabIndex = pagerState.currentPage,
                            pagerState = pagerState,
                            onSelect = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                        ) {
                            HistoryTab.entries.forEachIndexed { index, tab ->
                                GlassTab(
                                    text = {
                                        Text(
                                            text = stringResource(
                                                id = when (tab) {
                                                    HistoryTab.Threads -> R.string.title_history_tab_threads
                                                    HistoryTab.Forums -> R.string.title_history_tab_forums
                                                }
                                            )
                                        )
                                    },
                                    selected = pagerState.currentPage == index,
                                    selectedContentColor = ExtendedTheme.colors.text,
                                    unselectedContentColor = ExtendedTheme.colors.textSecondary,
                                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                                )
                            }
                        }
                    },
                    centerTitle = true,
                    navigationIcon = { BackNavigationIcon(onBackPressed = onBack) },
                    actions = {
                        ActionItem(icon = Symbols.Checklist, contentDescription = stringResource(id = R.string.history_select), onClick = { onStartSelecting(null) })
                        MenuIconButton(icon = Symbols.MoreVert, contentDescription = stringResource(id = R.string.btn_more), items = { clearItems })
                    },
                )
            }
        },
        overlay = {
            if (selected != null) DeleteSelectedButton(count = selected.size, onClick = onDeleteSelected)
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

/** A glass capsule floating at the bottom that deletes the [count] visits chosen. */
@Composable
private fun DeleteSelectedButton(count: Int, onClick: () -> Unit) {
    val glass = isGlassAvailable()
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .then(if (glass) Modifier else Modifier.pressScale(interactionSource))
                .floatingGlass(Corners.full, interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    // Glass answers the press itself
                    indication = if (glass) null else PressFeedback.Overlay,
                    enabled = count > 0,
                    role = Role.Button,
                    onClick = onClick,
                ),
        ) {
            val color = if (count > 0) MaterialTheme.colors.error else ExtendedTheme.colors.textDisabled
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
