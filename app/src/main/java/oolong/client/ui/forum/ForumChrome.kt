package oolong.client.ui.forum

import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import oolong.client.R
import oolong.client.model.ForumDetail
import oolong.client.model.ForumSort
import oolong.client.model.ForumTab
import oolong.client.model.GoodClass
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.AvatarPlaceholder
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.GlassTab
import oolong.client.ui.designsystem.GlassTabRow
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.IconButton
import oolong.client.ui.designsystem.MenuIconButton
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.TabClickMenu
import oolong.client.ui.designsystem.TabText
import oolong.client.ui.designsystem.WavyLoader
import oolong.client.ui.designsystem.choiceMenuItems
import oolong.client.ui.designsystem.matchedGeometry
import oolong.client.ui.designsystem.matchedGeometryAnchor
import oolong.client.ui.designsystem.onPageAccent
import oolong.client.ui.designsystem.pageAccentOrPrimary
import oolong.client.ui.designsystem.placeholder.PlaceholderHighlight
import oolong.client.ui.designsystem.placeholder.fade
import oolong.client.ui.designsystem.placeholder.placeholder
import oolong.client.ui.designsystem.rememberScrollProgress
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.util.StringUtil.getShortNumString
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import oolong.core.designsystem.theme.pressScale
import kotlin.math.max

/** The keys the header's avatar and name, and the bar's copies that dock from them, match by. */
internal object ForumAvatarAnchor
internal object ForumNameAnchor

/**
 * The forum's front: its avatar and name over its slogan, how many follow it and post in it, the
 * viewer's title and level in its colour, and a full-width button to follow or sign in.
 *
 * The avatar and name are drawn by the bar ([ForumBarTitle]), which docks them as the header
 * scrolls up under it; here they only hold their places. The slogan fades as the name leaves.
 */
@Composable
internal fun ForumHeader(
    forum: ForumDetail,
    loggedIn: Boolean,
    joining: Boolean,
    onEvent: (ForumEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nameDock = rememberScrollProgress(ForumNameAnchor)
    Column(modifier = modifier.padding(HeaderPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(ForumAvatarSize)
                    .matchedGeometryAnchor(ForumAvatarAnchor)
            )
            Spacer(modifier = Modifier.width(NameGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.title_forum, forum.name),
                    fontSize = NameFontSize,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .matchedGeometryAnchor(ForumNameAnchor)
                        .graphicsLayer { alpha = 0f },
                )
                if (forum.slogan.isNotBlank()) {
                    Text(
                        text = forum.slogan,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = ExtendedTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = 1f - nameDock.fraction(0f..SloganFade) },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(DetailsGap))
        ForumHeaderDetails(forum, loggedIn, joining, onEvent)
    }
}

/**
 * The forum's avatar and name in the bar. Each is drawn at its place in the header ([ForumHeader])
 * until it scrolls up into the bar's row, and docks there as it does. Tapping them opens the
 * forum's details.
 */
@Composable
internal fun ForumBarTitle(forum: ForumDetail, onEvent: (ForumEvent) -> Unit) {
    val avatarDock = rememberScrollProgress(ForumAvatarAnchor)
    val nameDock = rememberScrollProgress(ForumNameAnchor)
    val openDetail = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { onEvent(ForumEvent.OpenDetail) },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(
            data = forum.avatar,
            size = DockedAvatarSize,
            contentDescription = null,
            modifier = Modifier
                .matchedGeometry(ForumAvatarAnchor, avatarDock)
                .then(openDetail),
        )
        Spacer(modifier = Modifier.width(DockedNameGap))
        Text(
            text = stringResource(id = R.string.title_forum, forum.name),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .matchedGeometry(ForumNameAnchor, nameDock)
                .then(openDetail),
        )
    }
}

/**
 * Below the forum's name: its figures and the viewer's level, then a full-width button to follow
 * or sign in. A visitor also sees how many signed in today; once the viewer has signed in, the
 * button turns tonal and stays where it was, so the header keeps its shape. The rules are on the
 * forum's details page, which its name opens, not a button here.
 */
@Composable
private fun ForumHeaderDetails(
    forum: ForumDetail,
    loggedIn: Boolean,
    joining: Boolean,
    onEvent: (ForumEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = pageAccentOrPrimary()
    Column(modifier = modifier) {
        // Figures on the left, the viewer's level on the right, on one baseline
        Row(verticalAlignment = Alignment.Bottom) {
            ForumStat(forum.memberCount.getShortNumString(), stringResource(id = R.string.forum_stat_members))
            Spacer(modifier = Modifier.width(24.dp))
            ForumStat(forum.threadCount.getShortNumString(), stringResource(id = R.string.forum_stat_threads))
            // Following swaps today's sign-ins for the viewer's level; both fade where they are
            AnimatedVisibility(visible = !forum.liked, enter = StatEnter, exit = StatExit) {
                Row {
                    Spacer(modifier = Modifier.width(24.dp))
                    ForumStat(forum.todaySignCount.getShortNumString(), stringResource(id = R.string.forum_stat_signed_today))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            AnimatedVisibility(visible = forum.liked, enter = StatEnter, exit = StatExit) {
                ForumLevel(forum, accent)
            }
        }
        if (loggedIn) {
            Spacer(modifier = Modifier.height(SectionGap))
            val done = forum.liked && forum.signedIn
            val interaction = remember { MutableInteractionSource() }
            val background by animateColorAsState(
                if (done) accent.copy(alpha = if (ExtendedTheme.colors.isNightMode) 0.2f else 0.12f) else accent,
                Motion.snap(),
                label = "joinButton",
            )
            val content by animateColorAsState(if (done) accent else onPageAccent(), Motion.snap(), label = "joinButtonContent")
            // Only a slow answer shows the loader, so a quick one goes straight to the result
            var slow by remember { mutableStateOf(false) }
            LaunchedEffect(joining) {
                slow = false
                if (joining) {
                    delay(SlowAnswerMillis)
                    slow = true
                }
            }
            val face = when {
                joining && slow -> JoinFace.Busy
                !forum.liked -> JoinFace.Follow
                done -> JoinFace.Signed(forum.signedDays)
                else -> JoinFace.SignIn
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight)
                    .pressScale(interaction)
                    .background(background, SmoothCornerShape.Capsule)
                    .clickable(interactionSource = interaction, indication = PressFeedback.overlay(SmoothCornerShape.Capsule), enabled = !done && !joining) { onEvent(ForumEvent.FollowOrSignIn) },
                contentAlignment = Alignment.Center,
            ) {
                Refocus(targetState = face) { shown ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (shown) {
                            JoinFace.Busy -> WavyLoader(color = content)
                            is JoinFace.Signed -> {
                                Icon(
                                    imageVector = Symbols.Check,
                                    contentDescription = null,
                                    tint = content,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                JoinText(pluralStringResource(id = R.plurals.button_signed_in, count = shown.days, shown.days), content)
                            }
                            JoinFace.Follow -> JoinText(stringResource(id = R.string.button_follow), content)
                            JoinFace.SignIn -> JoinText(stringResource(id = R.string.button_sign_in), content)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinText(text: String, color: Color) {
    Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
}

/** What the follow and sign-in button shows. */
private sealed interface JoinFace {
    data object Follow : JoinFace
    data object SignIn : JoinFace
    data class Signed(val days: Int) : JoinFace
    data object Busy : JoinFace
}

/** How long an answer may take before the button shows it is waiting (design-system.md §7.2). */
private const val SlowAnswerMillis = 300L

private val StatEnter: EnterTransition
    @Composable get() = if (LocalReduceMotion.current) fadeIn(Motion.fadeIn()) else fadeIn(Motion.fadeIn()) + scaleIn(Motion.snap(), initialScale = 0.9f)
private val StatExit get() = fadeOut(Motion.fadeOut())

private val HeaderPadding = 16.dp
private val NameGap = 14.dp
private val DetailsGap = 14.dp
private val SectionGap = 14.dp
private val ButtonHeight = 38.dp
private val NameFontSize = 24.sp
private val DockedAvatarSize = 28.dp
private val DockedNameGap = 10.dp

/** How much of its docking the name spends taking the slogan with it. */
private const val SloganFade = 0.6f

/** How far into docking the slogan has faded out. */
private const val SloganFadeEnd = 0.4f

@Composable
private fun ForumStat(value: String, label: String) {
    Column {
        Text(text = value, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = ExtendedTheme.colors.text)
        Text(text = label, style = MaterialTheme.typography.caption, color = ExtendedTheme.colors.textSecondary)
    }
}

/**
 * The viewer's title and level in the forum, in the forum's colour, on one baseline, with the
 * progress to the next level under both.
 */
@Composable
private fun ForumLevel(forum: ForumDetail, accent: Color, modifier: Modifier = Modifier) {
    val numerals = rememberNumerals()
    Column(modifier = modifier.width(IntrinsicSize.Max), horizontalAlignment = Alignment.End) {
        Row {
            if (forum.levelName.isNotBlank()) {
                Text(
                    text = forum.levelName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = "LV", fontFamily = numerals, fontSize = 14.sp, color = accent, modifier = Modifier.alignByBaseline())
            // A new level refocuses in place
            Refocus(targetState = forum.level, modifier = Modifier.alignByBaseline()) { level ->
                Text(text = "$level", fontFamily = numerals, fontSize = 30.sp, lineHeight = 30.sp, color = accent)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Points gained, as on signing in, fill the bar on rather than jump it; drawn only
        val share = (forum.score / max(1f, forum.levelUpScore.toFloat())).coerceIn(0f, 1f)
        val fill by animateFloatAsState(share, Motion.settle(Motion.ProgressThreshold), label = "levelFill")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(min = 88.dp)
                .height(4.dp)
                .background(accent.copy(alpha = 0.2f), SmoothCornerShape.Capsule)
                .drawBehind {
                    val width = size.width * fill.coerceIn(0f, 1f)
                    if (width > 0f) drawRoundRect(accent, size = size.copy(width = width), cornerRadius = CornerRadius(size.height / 2))
                }
        )
    }
}

@Composable
private fun rememberNumerals(): FontFamily {
    val context = LocalContext.current
    return remember { FontFamily(Typeface.createFromAsset(context.assets, "bebas.ttf")) }
}

private val ForumAvatarSize = 64.dp

@Composable
internal fun ForumHeaderPlaceholder(
    forumName: String,
    loggedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    // Laid out as the header is, so nothing moves when the forum arrives in its place
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarPlaceholder(size = ForumAvatarSize)
            Spacer(modifier = Modifier.width(NameGap))
            Text(
                text = stringResource(id = R.string.title_forum, forumName),
                fontSize = NameFontSize,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(DetailsGap))
        Row {
            repeat(2) {
                if (it > 0) Spacer(modifier = Modifier.width(24.dp))
                Column {
                    Text(text = "0000", fontSize = 20.sp, lineHeight = 24.sp, modifier = Modifier.placeholder(visible = true, shape = Corners.xs, highlight = PlaceholderHighlight.fade()))
                    Text(text = "0000", style = MaterialTheme.typography.caption, modifier = Modifier.padding(top = 2.dp).placeholder(visible = true, shape = Corners.xs, highlight = PlaceholderHighlight.fade()))
                }
            }
        }
        if (loggedIn) {
            Spacer(modifier = Modifier.height(SectionGap))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight)
                    .placeholder(visible = true, shape = SmoothCornerShape.Capsule, highlight = PlaceholderHighlight.fade()),
            )
        }
    }
}

/**
 * The top bar: back, search and the menu, with [title], and pinned rows such as the tabs below
 * it, [pinnedOffset] further down while the header above them is still in view. The forum's
 * colour comes from the page under it.
 */
@Composable
internal fun ForumTopBar(
    onEvent: (ForumEvent) -> Unit,
    canSearch: Boolean = true,
    menu: (() -> List<ContextMenuItem>)? = null,
    title: @Composable () -> Unit = {},
    hiddenFraction: () -> Float = { 0f },
    pinnedOffset: () -> Int = { 0 },
    pinnedContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    GlassTopBar(
        title = title,
        navigationIcon = { BackNavigationIcon(onBackPressed = { onEvent(ForumEvent.Back) }) },
        actions = {
            if (canSearch) {
                IconButton(onClick = { onEvent(ForumEvent.Search) }) {
                    Icon(
                        imageVector = Symbols.Search,
                        contentDescription = stringResource(id = R.string.btn_search_in_forum)
                    )
                }
            }
            if (menu != null) {
                MenuIconButton(
                    icon = Symbols.MoreVert,
                    contentDescription = stringResource(id = R.string.btn_more),
                    items = menu,
                )
            }
        },
        hiddenFraction = hiddenFraction,
        pinnedOffset = pinnedOffset,
        pinnedContent = pinnedContent,
    )
}

/** The forum's ⋮ menu: sharing it, putting it on the home screen and, if followed, unfollowing it. */
@Composable
internal fun forumMenu(onEvent: (ForumEvent) -> Unit, liked: Boolean): () -> List<ContextMenuItem> {
    val share = stringResource(id = R.string.title_share)
    val toDesktop = stringResource(id = R.string.title_send_to_desktop)
    val unfollow = stringResource(id = R.string.title_unfollow)
    return {
        listOfNotNull(
            ContextMenuItem(Symbols.Share, share) { onEvent(ForumEvent.Share) },
            ContextMenuItem(Symbols.AddToHomeScreen, toDesktop) { onEvent(ForumEvent.SendToDesktop) },
            ContextMenuItem(Symbols.HeartBroken, unfollow) { onEvent(ForumEvent.Unfollow) }.takeIf { liked },
        )
    }
}

@Composable
private fun tabTextStyle() = MaterialTheme.typography.button.copy(fontWeight = FontWeight.Bold)

/**
 * The forum's tabs on a glass capsule. Tapping the selected tab again opens its choices: how the
 * latest threads are ordered, or which class of the good threads shows, which then joins the
 * tab's name.
 */
@Composable
internal fun ForumTabRow(
    tabs: ImmutableList<ForumTab>,
    pagerState: PagerState,
    sortType: Int,
    goodClasses: ImmutableList<GoodClass>,
    goodClassId: Int,
    onSelect: (Int) -> Unit,
    onEvent: (ForumEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = pagerState.currentPage.coerceIn(0, max(0, tabs.lastIndex))
    GlassTabRow(selectedTabIndex = selectedIndex, pagerState = pagerState, onSelect = onSelect, modifier = modifier) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            if (tab == ForumTab.Good && goodClasses.isNotEmpty()) {
                // The class shows only while the tab is chosen, and not for the first, "all"
                val goodClass = goodClasses.firstOrNull { it.id == goodClassId }?.takeIf { it != goodClasses.first() }
                val name = if (selected && goodClass != null) stringResource(R.string.tab_forum_good_class, tab.title(), goodClass.name) else tab.title()
                TabClickMenu(
                    selected = selected,
                    onClick = { onSelect(index) },
                    text = {
                        // The class chosen joins the name, refocusing it in place
                        Refocus(targetState = name) { TabText(it, selected) }
                    },
                    items = { choiceMenuItems(goodClasses.map { it.id to it.name }, goodClassId) { onEvent(ForumEvent.SetGoodClass(it)) } },
                    selectedContentColor = ExtendedTheme.colors.text,
                    unselectedContentColor = ExtendedTheme.colors.textSecondary
                )
            } else if (tab == ForumTab.Latest) {
                val sorts = SortTypes.zip(
                    listOf(stringResource(id = R.string.title_sort_by_reply), stringResource(id = R.string.title_sort_by_send)),
                )
                TabClickMenu(
                    selected = selected,
                    onClick = { onSelect(index) },
                    text = { TabText(tab.title(), selected) },
                    items = { choiceMenuItems(sorts, sortType) { onEvent(ForumEvent.SetSort(it)) } },
                    selectedContentColor = ExtendedTheme.colors.text,
                    unselectedContentColor = ExtendedTheme.colors.textSecondary
                )
            } else {
                GlassTab(
                    selected = selected,
                    onClick = { onSelect(index) },
                    selectedContentColor = ExtendedTheme.colors.text,
                    unselectedContentColor = ExtendedTheme.colors.textSecondary
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 16.dp)
                    ) {
                        TabText(tab.title(), selected)
                    }
                }
            }
        }
    }
}

private val SortTypes = persistentListOf(ForumSort.REPLY_TIME, ForumSort.POST_TIME)

@Composable
internal fun ForumTab.title(): String = when (this) {
    ForumTab.Good -> stringResource(id = R.string.tab_forum_good)
    ForumTab.Hot -> stringResource(id = R.string.tab_forum_hot)
    ForumTab.Latest -> stringResource(id = R.string.tab_forum_latest)
    is ForumTab.Custom -> name
}

/** The tab names as placeholders, while the forum loads. */
@Composable
internal fun ForumTabRowPlaceholder(modifier: Modifier = Modifier) {
    Row(modifier = modifier.height(48.dp)) {
        ForumTab.Fixed.forEach { tab ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.title(),
                    modifier = Modifier.placeholder(visible = true, highlight = PlaceholderHighlight.fade()),
                    style = tabTextStyle(),
                )
            }
        }
    }
}
