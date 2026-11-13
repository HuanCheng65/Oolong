package oolong.client.ui.page.history.list

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import oolong.client.R
import oolong.client.model.HistoryEntry
import oolong.client.ui.common.relativeTime
import oolong.client.ui.common.theme.compose.Corners
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.Symbols
import oolong.client.ui.common.theme.compose.TextStyles
import oolong.client.ui.page.history.HistoryDay
import oolong.client.ui.page.history.HistoryList
import oolong.client.ui.widgets.compose.Avatar
import oolong.client.ui.widgets.compose.ContextMenuItem
import oolong.client.ui.widgets.compose.LoadMoreEffect
import oolong.client.ui.widgets.compose.MyLazyColumn
import oolong.client.ui.widgets.compose.NetworkImage
import oolong.client.ui.widgets.compose.SearchBox
import oolong.client.ui.widgets.compose.VerticalDivider
import oolong.client.ui.widgets.compose.longPressMenu

/**
 * One tab of the history, under the days its visits were on, with a field above that narrows it;
 * long pressing a visit offers to delete it or start choosing. While choosing ([selected] isn't
 * null), tapping a visit chooses it. With [historyOff], a row under the field says no new visits
 * are kept, with a way to keep them again.
 */
@Composable
fun <T : HistoryEntry> HistoryListContent(
    list: HistoryList<T>,
    query: String,
    @StringRes searchHint: Int,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (T) -> Unit,
    onDelete: (T) -> Unit,
    selected: Set<Long>? = null,
    onSelect: (T) -> Unit = {},
    onStartSelecting: (T) -> Unit = {},
    historyOff: Boolean = false,
    onTurnOnHistory: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val lazyListState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LoadMoreEffect(lazyListState, enabled = list.hasMore, onLoadMore = onLoadMore)
        MyLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            contentPadding = contentPadding,
        ) {
            if (selected == null) {
                item(key = "search", contentType = "search") { HistorySearchField(query, searchHint, onQueryChange) }
                if (historyOff) item(key = "off", contentType = "off") { HistoryOffRow(onTurnOn = onTurnOnHistory) }
            }
            if (list.isEmpty && query.isNotBlank()) {
                item(key = "nothing", contentType = "nothing") {
                    Text(
                        text = stringResource(id = R.string.history_search_nothing),
                        style = TextStyles.caption,
                        color = ExtendedTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                    )
                }
            }
            list.sections.forEach { section ->
                item(key = section.day, contentType = "day") { DayHeader(section.day) }
                entries(section.entries, selected, onOpen, onDelete, onSelect, onStartSelecting)
            }
        }
    }
}

private fun <T : HistoryEntry> LazyListScope.entries(
    entries: List<T>,
    selected: Set<Long>?,
    onOpen: (T) -> Unit,
    onDelete: (T) -> Unit,
    onSelect: (T) -> Unit,
    onStartSelecting: (T) -> Unit,
) {
    items(items = entries, key = { it.id }, contentType = { it::class }) { entry ->
        val delete = stringResource(id = R.string.title_delete)
        val select = stringResource(id = R.string.history_select)
        val chosen = selected != null && entry.id in selected
        Column(
            modifier = Modifier
                .animateItem()
                .then(
                    if (selected != null) {
                        Modifier.toggleable(value = chosen, role = Role.Checkbox, onValueChange = { onSelect(entry) })
                    } else {
                        Modifier.longPressMenu(
                            items = {
                                listOf(
                                    ContextMenuItem(Symbols.Checklist, select, onClick = { onStartSelecting(entry) }),
                                    ContextMenuItem(Symbols.Delete, delete, onClick = { onDelete(entry) }),
                                )
                            },
                            onClick = { onOpen(entry) },
                        )
                    }
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected != null) {
                    Icon(
                        imageVector = if (chosen) Symbols.CheckCircleFill else Symbols.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (chosen) ExtendedTheme.colors.text else ExtendedTheme.colors.textDisabled,
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(24.dp),
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (entry) {
                        is HistoryEntry.Thread -> ThreadRow(entry)
                        is HistoryEntry.Forum -> ForumRow(entry)
                    }
                }
            }
            // Between the visits of a day, not after its last
            if (entry !== entries.last()) VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun HistorySearchField(query: String, @StringRes hint: Int, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    SearchBox(
        keyword = query,
        onKeywordChange = onQueryChange,
        onKeywordSubmit = { focusManager.clearFocus() },
        placeholder = {
            Text(text = stringResource(id = hint), color = ExtendedTheme.colors.onSearchField.copy(alpha = ContentAlpha.medium))
        },
        prependIcon = { Icon(imageVector = Symbols.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
        shape = Corners.full,
        submitButton = false,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .height(40.dp),
    )
}

/** Says that history is turned off in the settings, with a button that turns it on. */
@Composable
private fun HistoryOffRow(onTurnOn: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_me_incognito_on),
            contentDescription = null,
            tint = ExtendedTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(id = R.string.history_off),
            style = TextStyles.caption,
            color = ExtendedTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onTurnOn) {
            Text(text = stringResource(id = R.string.button_turn_on), color = ExtendedTheme.colors.text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DayHeader(day: HistoryDay) {
    Text(
        text = stringResource(
            id = when (day) {
                HistoryDay.Today -> R.string.title_history_today
                HistoryDay.Yesterday -> R.string.title_history_yesterday
                HistoryDay.Earlier -> R.string.title_history_before
            }
        ),
        style = TextStyles.caption,
        fontWeight = FontWeight.Bold,
        color = ExtendedTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/**
 * A visited thread: its title, then where it is, who started it, how far it was read and when,
 * with its picture beside them when it has one.
 */
@Composable
private fun ThreadRow(thread: HistoryEntry.Thread) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = thread.title,
                style = TextStyles.name,
                fontWeight = FontWeight.Bold,
                color = ExtendedTheme.colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val forum = if (thread.forumName.isNotEmpty()) stringResource(id = R.string.title_forum, thread.forumName) else null
            val details = listOfNotNull(
                thread.authorName.ifEmpty { null },
                if (thread.floor > 1) stringResource(id = R.string.history_read_up_to_floor, thread.floor) else null,
                relativeTime(thread.time),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Read out as the words, not the dots between them
                modifier = Modifier.clearAndSetSemantics { contentDescription = listOfNotNull(forum).plus(details).joinToString(SpokenSeparator) },
            ) {
                if (forum != null) {
                    if (thread.forumAvatar.isNotEmpty()) {
                        Avatar(data = thread.forumAvatar, size = ForumIconSize, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = forum,
                        style = TextStyles.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = ExtendedTheme.colors.text.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                    Dot()
                }
                Text(
                    text = details.joinToString(MetaSeparator),
                    style = TextStyles.caption,
                    color = ExtendedTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (thread.thumbnail.isNotEmpty()) {
            NetworkImage(
                imageUri = thread.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(ThumbnailSize)
                    .clip(Corners.sm),
            )
        }
    }
}

@Composable
private fun Dot() {
    Text(text = MetaSeparator, style = TextStyles.caption, color = ExtendedTheme.colors.textSecondary)
}

/** A visited forum: its picture and name, when it was last visited and how many times. */
@Composable
private fun ForumRow(forum: HistoryEntry.Forum) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Avatar(data = forum.avatar, size = 40.dp, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = forum.title, style = TextStyles.name, fontWeight = FontWeight.Bold, color = ExtendedTheme.colors.text, maxLines = 1)
            val details = listOfNotNull(
                relativeTime(forum.time),
                if (forum.visits > 0) pluralStringResource(id = R.plurals.history_visits, forum.visits, forum.visits) else null,
            )
            Text(
                text = details.joinToString(MetaSeparator),
                modifier = Modifier.clearAndSetSemantics { contentDescription = details.joinToString(SpokenSeparator) },
                style = TextStyles.caption,
                color = ExtendedTheme.colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

private const val MetaSeparator = " · "
private const val SpokenSeparator = ", "
private val ForumIconSize = 16.dp
private val ThumbnailSize = 64.dp
