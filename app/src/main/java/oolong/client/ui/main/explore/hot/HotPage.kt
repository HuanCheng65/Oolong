package oolong.client.ui.main.explore.hot

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import oolong.client.R
import oolong.client.arch.LoadStatus
import oolong.client.model.ForumMeasure
import oolong.client.model.ForumSummary
import oolong.client.model.RankReason
import oolong.client.model.RankedForum
import oolong.client.model.RankedTopic
import oolong.client.model.ThreadMeasure
import oolong.client.model.TopicRef
import oolong.client.navigation.ForumKey
import oolong.client.navigation.HotTopicListKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.TopicKey
import oolong.client.navigation.transition.pageOrigin
import oolong.client.navigation.transition.rememberPageOrigin
import oolong.client.ui.components.BlockableContent
import oolong.client.ui.components.ContentImage
import oolong.client.ui.components.EmoticonText
import oolong.client.ui.components.ErrorScreen
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.Chip
import oolong.client.ui.designsystem.LocalContextMenus
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.PullRefresh
import oolong.client.ui.designsystem.RankNumber
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.Reveal
import oolong.client.ui.designsystem.SegmentedControl
import oolong.client.ui.designsystem.states.DefaultLoadingScreen
import oolong.client.ui.designsystem.states.StateScreen
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.main.OnTabReselected
import oolong.client.ui.main.explore.FeedThreadEvents
import oolong.client.ui.main.explore.rememberFeedThreadEvents
import oolong.client.ui.topic.TopicTag
import oolong.client.util.StringUtil.getShortNumString
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.Spacing
import oolong.core.designsystem.theme.TextStyles
import oolong.core.designsystem.theme.pressScale

@Composable
fun HotPage(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: HotViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.open() }
    OnTabReselected("hot") { viewModel.refresh() }
    val navigator = LocalNavigator.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    HotScreen(
        state = state,
        events = rememberFeedThreadEvents(onAgree = {}),
        onRefresh = viewModel::refresh,
        onSelectThreadMeasure = viewModel::selectThreadMeasure,
        onSelectForumMeasure = viewModel::selectForumMeasure,
        onOpenTopics = { navigator.navigate(HotTopicListKey) },
        onOpenTopic = { navigator.navigate(TopicKey(it.id, it.name)) },
        onOpenForum = { navigator.navigate(ForumKey(it.name)) },
        onFollowForum = viewModel::follow,
        contentPadding = contentPadding,
    )
}

/**
 * The day's rankings, which pull down to refresh: the top hot topics, the threads by the measure
 * picked, ten at first and twenty opened, and the forums by theirs. A row has its place, what it
 * is, where, and why it is there; the rest is a long press away.
 */
@Composable
fun HotScreen(
    state: HotState,
    events: FeedThreadEvents,
    onRefresh: () -> Unit,
    onSelectThreadMeasure: (ThreadMeasure) -> Unit,
    onSelectForumMeasure: (ForumMeasure) -> Unit,
    onOpenTopics: () -> Unit,
    onOpenTopic: (TopicRef) -> Unit,
    onOpenForum: (ForumSummary) -> Unit,
    onFollowForum: (RankedForum) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val isLoading = state.load == LoadStatus.Loading
    val error = (state.load as? LoadStatus.Error)?.error
    StateScreen(
        isEmpty = state.topics.isEmpty() && state.threads.isEmpty() && state.forums == null,
        isError = error != null,
        isLoading = isLoading,
        modifier = Modifier.fillMaxSize(),
        onReload = onRefresh,
        errorScreen = { ErrorScreen(error = error, modifier = Modifier.padding(contentPadding)) },
        loadingScreen = { Box(modifier = Modifier.padding(contentPadding)) { DefaultLoadingScreen() } },
    ) {
        var expanded by rememberSaveable(state.threadMeasure) { mutableStateOf(false) }
        // A ranking being switched dims while the other loads, and lights up again as it comes
        val threadsDim by animateFloatAsState(if (state.switchingThreads) SwitchingAlpha else 1f, Motion.settle(), label = "threadsDim")
        val forumsDim by animateFloatAsState(if (state.switchingForums) SwitchingAlpha else 1f, Motion.settle(), label = "forumsDim")
        PullRefresh(
            refreshing = isLoading,
            revealUnpulled = true,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorTop = contentPadding.calculateTopPadding(),
        ) {
            MyLazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxWidth()) {
                if (state.topics.isNotEmpty()) topicsSection(state.topics, onOpenTopics, onOpenTopic)
                threadsSection(
                    state = state,
                    expanded = expanded,
                    onToggleExpanded = { expanded = !expanded },
                    dim = { threadsDim },
                    events = events,
                    onSelectMeasure = onSelectThreadMeasure,
                    onOpenTopic = onOpenTopic,
                )
                state.forums?.let { ranking ->
                    forumsSection(
                        measure = state.forumMeasure,
                        forums = ranking.forums.take(TopForums),
                        rule = ranking.rule,
                        dim = { forumsDim },
                        onSelectMeasure = onSelectForumMeasure,
                        onOpen = onOpenForum,
                        onFollow = onFollowForum,
                    )
                }
            }
        }
    }
}

@Composable
private fun HotThreadItem(item: HotThread, events: FeedThreadEvents, onOpenTopic: (TopicRef) -> Unit, dim: () -> Float) {
    BlockableContent(block = item.block, modifier = Modifier.padding(vertical = Spacing.xs, horizontal = Spacing.md)) {
        HotThreadRow(
            item = item,
            events = events,
            onOpenTopic = onOpenTopic,
            modifier = Modifier.graphicsLayer { alpha = dim() },
        )
    }
}

/** How far a ranking dims while another order of it loads. */
private const val SwitchingAlpha = 0.4f

private fun LazyListScope.topicsSection(
    topics: List<RankedTopic>,
    onOpenAll: () -> Unit,
    onOpen: (TopicRef) -> Unit,
) {
    item(key = "TopicsHeader") {
        SectionHeader(text = stringResource(R.string.hot_section_topics)) {
            val press = remember { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .pressScale(press)
                    .clickable(interactionSource = press, indication = PressFeedback.overlay(Corners.full), onClick = onOpenAll)
                    .padding(start = Spacing.xs, top = Spacing.xxs, bottom = Spacing.xxs),
            ) {
                Text(text = stringResource(R.string.text_all), style = TextStyles.caption, color = ExtendedTheme.colors.textSecondary)
                Icon(imageVector = Symbols.ChevronRight, contentDescription = null, tint = ExtendedTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
    itemsIndexed(topics, key = { _, topic -> "Topic_${topic.id}" }) { index, topic ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen(TopicRef(topic.id, topic.name)) }
                .padding(horizontal = Spacing.md, vertical = 10.dp),
        ) {
            RankNumber(rank = index + 1)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = topic.name,
                    style = TextStyles.bodyCompact,
                    color = ExtendedTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TopicTag(topic.tag)
            }
            Text(text = topic.discussCount.getShortNumString(), style = TextStyles.footnote, color = ExtendedTheme.colors.textSecondary)
        }
    }
}

private fun LazyListScope.threadsSection(
    state: HotState,
    expanded: Boolean,
    dim: () -> Float,
    onToggleExpanded: () -> Unit,
    events: FeedThreadEvents,
    onSelectMeasure: (ThreadMeasure) -> Unit,
    onOpenTopic: (TopicRef) -> Unit,
) {
    item(key = "ThreadsHeader") {
        Column {
            SectionHeader(text = stringResource(R.string.hot_section_threads))
            val measures = ThreadMeasure.entries
            SegmentedControl(
                options = measures.map { stringResource(it.label) }.toImmutableList(),
                selected = measures.indexOf(state.threadMeasure),
                onSelect = { onSelectMeasure(measures[it]) },
                modifier = Modifier.padding(horizontal = Spacing.md).fillMaxWidth(),
            )
            Caption(text = stringResource(R.string.hot_last_day))
        }
    }
    // Rows are known by their place: another order changes them in place, the list keeping its
    // scroll. Those past the first few are one item that opens and closes in place, so what
    // follows moves with it rather than gliding over it
    val (first, rest) = state.threads.partition { it.ranked.rank <= CollapsedThreads }
    itemsIndexed(first, key = { index, _ -> "Thread_$index" }) { _, item ->
        HotThreadItem(item, events, onOpenTopic, dim)
    }
    if (rest.isNotEmpty()) {
        item(key = "ThreadsRest") {
            Reveal(rest.takeIf { expanded }) { rows ->
                Column { rows.forEach { item -> HotThreadItem(item, events, onOpenTopic, dim) } }
            }
        }
    }
    val hidden = state.threads.count { it.ranked.rank > CollapsedThreads }
    if (hidden > 0) {
        item(key = "ThreadsMore") {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(vertical = Spacing.sm),
            ) {
                Refocus(targetState = expanded) { shown ->
                    Text(
                        text = if (shown) stringResource(R.string.hot_show_less) else stringResource(R.string.hot_show_more, hidden),
                        style = TextStyles.caption,
                        color = ExtendedTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.forumsSection(
    measure: ForumMeasure,
    forums: List<RankedForum>,
    rule: String,
    dim: () -> Float,
    onSelectMeasure: (ForumMeasure) -> Unit,
    onOpen: (ForumSummary) -> Unit,
    onFollow: (RankedForum) -> Unit,
) {
    item(key = "ForumsHeader") {
        Column {
            SectionHeader(text = stringResource(R.string.hot_section_forums))
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(ForumMeasure.entries.size) { index ->
                    val option = ForumMeasure.entries[index]
                    Chip(
                        text = stringResource(option.label),
                        invertColor = option == measure,
                        onClick = { onSelectMeasure(option) },
                    )
                }
            }
            if (rule.isNotEmpty()) Caption(text = rule)
        }
    }
    itemsIndexed(forums, key = { index, _ -> "Forum_$index" }) { _, item ->
        HotForumRow(
            item = item,
            onOpen = { onOpen(item.forum) },
            onFollow = { onFollow(item) },
            modifier = Modifier.graphicsLayer { alpha = dim() },
        )
    }
}

/**
 * A thread on the ranking: its place, title, a few lines of what it says and the topic it is
 * under, then its forum and why it is there, with its first picture beside. Tapped it opens; held,
 * it peeks as a card in a feed does.
 */
@Composable
private fun HotThreadRow(
    item: HotThread,
    events: FeedThreadEvents,
    onOpenTopic: (TopicRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thread = item.ranked.thread
    val menus = LocalContextMenus.current
    val origin = rememberPageOrigin()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressAt by remember { mutableStateOf(Offset.Zero) }
    var peeking by remember { mutableStateOf(false) }
    val peek = events.onPeek
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .pageOrigin(origin)
            .onGloballyPositioned { coordinates = it }
            .pointerInput(menus) {
                // The finger that opened the peek can slide onto its actions and lift
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressAt = coordinates?.localToWindow(down.position) ?: Offset.Zero
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        val position = coordinates?.localToWindow(change.position) ?: break
                        if (!change.pressed) {
                            if (peeking) menus?.liftFinger(position)
                            peeking = false
                            break
                        }
                        if (peeking) menus?.moveFinger(position)
                    }
                }
            }
            .combinedClickable(
                onClick = {
                    origin.leave()
                    events.onOpen(thread)
                },
                onLongClickLabel = stringResource(R.string.btn_preview),
                onLongClick = peek?.let {
                    {
                        coordinates?.let { layout ->
                            peeking = true
                            it(thread, Rect(layout.positionInWindow(), layout.size.toSize()), pressAt, origin)
                        }
                    }
                },
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        RankNumber(rank = item.ranked.rank, modifier = Modifier.padding(top = 1.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = thread.title.ifEmpty { thread.abstract },
                style = TextStyles.name,
                color = ExtendedTheme.colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (thread.title.isNotEmpty() && thread.abstract.isNotBlank()) {
                EmoticonText(
                    text = thread.abstract,
                    style = TextStyles.caption,
                    color = ExtendedTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.ranked.topic?.let { topic ->
                Text(
                    text = "# ${topic.name}",
                    style = TextStyles.footnote,
                    color = ExtendedTheme.colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .background(ExtendedTheme.colors.accent.copy(alpha = 0.1f), Corners.xs)
                        .clickable(interactionSource = null, indication = PressFeedback.overlay(Corners.xs)) { onOpenTopic(topic) }
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Text(
                text = listOf(stringResource(R.string.title_forum, thread.forum.name), rankReasonText(item.ranked.reason))
                    .filter(String::isNotBlank)
                    .joinToString(" · "),
                style = TextStyles.footnote,
                color = ExtendedTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        thread.images.firstOrNull()?.let { image ->
            ContentImage(
                imageUri = image.small,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(ThumbnailSize),
                shape = Corners.md,
            )
        }
    }
}

/** A forum on the ranking: its place, avatar, name and why it is there, and a button to follow it. */
@Composable
private fun HotForumRow(
    item: RankedForum,
    onOpen: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        RankNumber(rank = item.rank)
        Avatar(data = item.forum.avatar, size = 40.dp, contentDescription = null, shape = Corners.md)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.title_forum, item.forum.name),
                style = TextStyles.name,
                color = ExtendedTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(rankReasonText(item.reason), tightenUnits(stringResource(R.string.forum_rank_members, item.members.getShortNumString())))
                    .filter(String::isNotBlank)
                    .joinToString(" · "),
                style = TextStyles.footnote,
                color = ExtendedTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Refocus(targetState = item.liked) { liked ->
            Chip(
                text = stringResource(if (liked) R.string.button_followed else R.string.button_follow),
                onClick = if (liked) null else onFollow,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String, trailing: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.lg, bottom = Spacing.xs),
    ) {
        Text(text = text, style = TextStyles.title, color = ExtendedTheme.colors.text, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = TextStyles.footnote,
        color = ExtendedTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}

/** Why a thread or forum is on a ranking, in the app's words. */
@Composable
private fun rankReasonText(reason: RankReason): String = tightenUnits(rankReasonWords(reason))

/** No space between a count's 万 or 亿 and the word after it: 「2.7万人看过」, as 「2700 人看过」 has one. */
private fun tightenUnits(text: String) = text.replace(UnitSpace, "$1")

private val UnitSpace = Regex("([万亿]) ")

@Composable
private fun rankReasonWords(reason: RankReason): String = when (reason) {
    is RankReason.Readers -> stringResource(R.string.rank_reason_readers, reason.count.getShortNumString())
    is RankReason.Likes -> stringResource(R.string.rank_reason_likes, reason.count.getShortNumString())
    is RankReason.NewFollowers -> stringResource(R.string.rank_reason_new_followers, reason.count.getShortNumString())
    is RankReason.NewThreads -> stringResource(R.string.rank_reason_new_threads, reason.count.getShortNumString())
    is RankReason.AnswerMinutes -> stringResource(R.string.rank_reason_answer_minutes, "%.1f".format(reason.minutes).removeSuffix(".0"))
    is RankReason.Reads -> stringResource(R.string.rank_reason_reads, reason.count.getShortNumString())
    is RankReason.Text -> reason.text
}

private val ThreadMeasure.label: Int
    get() = when (this) {
        ThreadMeasure.MostRead -> R.string.thread_measure_read
        ThreadMeasure.MostLiked -> R.string.thread_measure_liked
    }

private val ForumMeasure.label: Int
    get() = when (this) {
        ForumMeasure.MostFollowed -> R.string.forum_measure_followed
        ForumMeasure.MostPosted -> R.string.forum_measure_posted
        ForumMeasure.FastestAnswered -> R.string.forum_measure_answered
        ForumMeasure.MostRead -> R.string.forum_measure_read
    }

private val ThumbnailSize = 88.dp
private const val CollapsedThreads = 10
private const val TopForums = 5
