package oolong.client.ui.widgets.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import oolong.client.arch.GlobalEvent
import oolong.client.arch.emitGlobalEvent
import oolong.client.ui.common.theme.compose.ExtendedTheme
import oolong.client.ui.common.theme.compose.SmoothCornerShape
import kotlin.math.roundToInt

/** The height of a [GlassTopBar]'s row of icons and title, below the status bar. */
internal val GlassTopBarRowHeight = 56.dp

/** The height of everything floating in that row: buttons, capsules, a title that is a button. */
val GlassTopBarItemHeight = 48.dp

/** The row's padding: the page's margin, so the buttons' glass lines up with the large title and the cards. */
private val TopBarRowPadding = 16.dp
private val TitlePadding = 8.dp

/** The navigation icon's and the actions' glass. */
private val TopBarCapsule = SmoothCornerShape.Capsule

/** Where the title starts without a navigation icon, past the row's padding. */
private val BareTitlePadding = 4.dp

/**
 * The bottom edge of a top bar's rows, without its pinned rows. [GlassScaffold] pads its content
 * down to this line, so pinned rows float over the content and can come and go without moving it.
 */
val GlassTopBarEdge = HorizontalAlignmentLine { a, b -> maxOf(a, b) }

/**
 * A top bar that floats over the content of a [GlassScaffold], from the top edge of the screen.
 * Its background blurs the content behind it, strongest at the top, and fades out within the
 * bar to nothing at its bottom edge instead of ending at a hard line. The glass takes no space of
 * its own below the rows. The navigation icon and the actions float on their own glass: a round
 * button and a capsule. Double-tapping the bar scrolls the page to the top.
 *
 * @param hiddenFraction 0 shows the bar, 1 hides it: everything between the status bar and the
 * pinned rows slides away. Glass stays behind the status bar and the pinned rows, so content
 * never scrolls by sharp under them.
 * The title becomes the page's large title when its [GlassScaffold] has one; see [LargeTitleState].
 *
 * @param title null leaves out the title row, with [navigationIcon] and [actions], for a bar of
 * [content] alone, such as tabs whose page is named elsewhere.
 * @param content extra rows under the bar, such as tabs. They share the bar's background.
 * @param pinnedOffset how far below the bar's rows the pinned rows sit, in pixels, for rows that
 * float below something on the page until it scrolls away, such as tabs under a forum's header.
 * The bar's glass reaches down over them only as they come up to it.
 * @param pinnedContent rows below [content] that stay when the bar hides, such as a list's sticky
 * header. They share the bar's background and float over the content: the scaffold does not pad
 * the content for them.
 * @param centerTitle centres the title on the whole row rather than starting it after the
 * navigation icon, for tabs that are the page's title. It does not take part in a large title.
 * @param actionsInCapsule false leaves [actions] off the capsule, for an action that is a button
 * of its own, such as Save.
 * @param wash a colour washed over the glass, strongest at the top and gone at the bottom edge,
 * such as a forum's own colour on its page.
 * @param glassButtons keeps the buttons on glass at the top of the page too, for a page whose
 * content never comes under the bar, where they would otherwise always be bare.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassTopBar(
    title: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    actionsInCapsule: Boolean = true,
    centerTitle: Boolean = false,
    hiddenFraction: () -> Float = { 0f },
    pinnedOffset: () -> Int = { 0 },
    wash: Color = Color.Unspecified,
    glassButtons: Boolean = false,
    pinnedContent: (@Composable ColumnScope.() -> Unit)? = null,
    // Last, so a trailing lambda passes the bar's rows, which the content is padded for
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val largeTitle = LocalLargeTitle.current
    val scrollEdge = LocalPageScroll.current
    val largeTitleProgress = largeTitle?.let { rememberScrollProgress(0f..it.height.toFloat(), snapping = true) }
    val statusBar = WindowInsets.statusBars.union(WindowInsets.displayCutout)
    // A classic bar is a band, its buttons bare on it
    val classic = LocalChromeStyle.current == ChromeStyle.Classic
    val besideRail = LocalNavigationRailShown.current
    // The buttons are bare at the top of a page, and float on glass once content is under the
    // bar. Over a large title they keep their glass: its edge is what lines up with the title
    val revealPx = with(LocalDensity.current) { TopBarEdgeReveal.toPx() }
    // Beside the rail they also keep it next to what the host puts at the end, which is always glass
    val glassAlways = glassButtons || largeTitle != null || (besideRail && LocalTopBarEnd.current != null)
    val barButtons = remember(scrollEdge, revealPx, glassAlways) {
        BarButtons { if (glassAlways) 1f else scrollEdge?.scrolled?.let { (it / revealPx).coerceIn(0f, 1f) } ?: 1f }
    }
    Layout(
        content = {
            Box(
                modifier = Modifier
                    .layoutId(TopBarLayer.Background)
                    .then(
                        if (classic) {
                            Modifier.classicBarGlass(top = true)
                        } else {
                            // The buttons' glass sees the bar's blur too, so it never shows the
                            // content sharper than the bar around it. The blur renders once into its
                            // own layer, which the buttons reuse instead of blurring the content again.
                            Modifier
                                .lensTarget()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                // Beside the rail the glass fades in from the rail's edge, rather
                                // than starting in a line along it
                                .drawWithContent {
                                    drawContent()
                                    if (besideRail) {
                                        val fade = RailEdgeFade.toPx()
                                        drawRect(
                                            brush = Brush.horizontalGradient(0f to Color.Transparent, fade / size.width to Color.Black),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    }
                                }
                                .topBarGlass()
                        }
                    )
                    .then(
                        if (wash.isSpecified) {
                            Modifier.background(Brush.verticalGradient(listOf(wash, wash.copy(alpha = 0f))))
                        } else Modifier
                    )
            )
            CompositionLocalProvider(LocalOnClassicBar provides classic) {
                Column(
                    modifier = Modifier
                        .layoutId(TopBarLayer.Foreground)
                        // Double-tapping the bar scrolls the page to its top: a touch shortcut only, so
                        // screen readers don't take the whole bar for a button that does nothing
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { coroutineScope.emitGlobalEvent(GlobalEvent.ScrollToTop) }) }
                        .windowInsetsPadding(
                            statusBar.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        )
                ) {
                    if (title != null) Box(
                        modifier = Modifier
                            .then(
                                if (scrollEdge != null) {
                                    // Published for what docks in the bar as it scrolls up to it
                                    Modifier.onGloballyPositioned {
                                        val row = scrollEdge.boundsOf(it)
                                        scrollEdge.barRowTop = row.top
                                        scrollEdge.barRowBottom = row.bottom
                                    }
                                } else Modifier
                            )
                            .fillMaxWidth()
                            .height(GlassTopBarRowHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        TitleRow(
                            navigationIcon = navigationIcon?.let { icon ->
                                { Box(modifier = Modifier.floatingGlass(TopBarCapsule)) { icon() } }
                            },
                            title = {
                                ProvideTextStyle(value = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)) {
                                    // The page's title, which screen readers can jump to
                                    Box(
                                        modifier = Modifier.semantics(mergeDescendants = true) { heading() },
                                        propagateMinConstraints = true,
                                    ) {
                                        if (largeTitleProgress == null || centerTitle) {
                                            title()
                                        } else {
                                            Box(modifier = Modifier.matchedGeometry(LargeTitleAnchor, largeTitleProgress)) { title() }
                                        }
                                    }
                                }
                            },
                            actions = {
                                if (actionsInCapsule) {
                                    // With no actions the row measures nothing, so no glass shows
                                    Row(
                                        modifier = Modifier.floatingGlass(TopBarCapsule),
                                        verticalAlignment = Alignment.CenterVertically,
                                        content = actions,
                                    )
                                } else {
                                    // Bare, with no glass to answer for them
                                    CompositionLocalProvider(LocalBarButtons provides null) {
                                        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
                                    }
                                }
                            },
                            end = LocalTopBarEnd.current,
                            centerTitle = centerTitle,
                            barButtons = barButtons,
                        )
                    }
                    content?.invoke(this)
                }
                if (pinnedContent != null) {
                    Column(
                        modifier = Modifier
                            .layoutId(TopBarLayer.Pinned)
                            .windowInsetsPadding(statusBar.only(WindowInsetsSides.Horizontal)),
                        content = pinnedContent,
                    )
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val foreground = measurables.first { it.layoutId == TopBarLayer.Foreground }.measure(loose)
        val pinned = measurables.firstOrNull { it.layoutId == TopBarLayer.Pinned }?.measure(loose)
        val height = foreground.height + (pinned?.height ?: 0)
        // Hiding slides away the rows below the status bar, and the pinned rows move up after
        // them. The glass shrinks rather than slides, so it still covers the status bar and the
        // pinned rows and fades out at their bottom edge.
        val hidden = ((foreground.height - statusBar.getTop(this)) * hiddenFraction()).roundToInt()
        // The edge reaches a little past the bar, over the content, so the blur comes in before
        // the bar's edge instead of ending in a line there. It shows only once content is under it.
        val extend = if (classic) 0 else TopBarEdgeExtend.roundToPx()
        val offset = pinnedOffset().coerceAtLeast(0)
        val uncovered = minOf(offset, pinned?.height ?: 0)
        val background = measurables.first { it.layoutId == TopBarLayer.Background }
            .measure(Constraints.fixed(foreground.width, height - hidden - uncovered + extend))
        layout(foreground.width, height, mapOf(GlassTopBarEdge to foreground.height)) {
            background.place(0, 0)
            // Hidden all the way, it is not placed at all: faded out, its buttons' glass would still
            // sample the page on every frame
            if (hiddenFraction() < 1f) foreground.placeWithLayer(0, -hidden) { alpha = 1f - hiddenFraction() }
            pinned?.place(0, foreground.height - hidden + offset)
        }
    }
}

/**
 * Glass behind the status bar only, for pages without a top bar whose content scrolls up under
 * the status bar. Like [GlassTopBar], it fades out to nothing at its bottom edge.
 */
@Composable
fun GlassStatusBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .topBarGlass()
    )
}

/**
 * What the page's host puts at the end of every [GlassTopBar] under it, past the bar's own
 * actions, such as search beside the rail. It is given the room it may take and sizes itself to it.
 */
val LocalTopBarEnd = staticCompositionLocalOf<(@Composable (room: Dp) -> Unit)?> { null }

/**
 * Whether the navigation rail shows beside the pages: the main page then leaves out its own bar,
 * and top bars fade their glass in from the rail's edge.
 */
val LocalNavigationRailShown = compositionLocalOf { false }

/** How far from the rail's edge the bar's glass fades in. */
private val RailEdgeFade = 40.dp

/**
 * Keeps what [LocalTopBarEnd] puts at the end of a set of pages' bars, such as search on the main
 * tabs, one size across them. Each bar gives it the least room any of them has had at the same
 * width, and no more than it would have beside a centred title [CentreAllowance] wide, so it has
 * that size from the first page shown on, whichever page has a centred title.
 */
@Stable
class TopBarEndRoom {
    private var forWidth = -1
    private var least by mutableIntStateOf(Int.MAX_VALUE)

    internal fun room(width: Int, own: Int, besideCentre: Int): Int {
        // A new width, as on turning the device, starts over
        if (width != forWidth) {
            forWidth = width
            least = Int.MAX_VALUE
        }
        if (own < least) least = own
        return minOf(least, besideCentre)
    }
}

/** Shared by the bars under it, see [TopBarEndRoom]; without it each bar gives its end all its room. */
val LocalTopBarEndRoom = staticCompositionLocalOf<TopBarEndRoom?> { null }

/** The width of a centred title [TopBarEndRoom] keeps room for, about that of three tabs. */
private val CentreAllowance = 200.dp

/** Between the bar's actions and what [LocalTopBarEnd] puts after them. */
private val EndGap = 12.dp

/** What [LocalTopBarEnd] is kept at least room for, as a round button. */
private val EndLeast = GlassTopBarItemHeight

/**
 * The bar's row: the navigation icon at the start, the title after it or centred on the whole row,
 * the actions at the end and [end] past them, each centred on the row's height. The title passes
 * under the buttons, as what docks into it does on its way.
 */
@Composable
private fun TitleRow(
    navigationIcon: (@Composable () -> Unit)?,
    title: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    end: (@Composable (room: Dp) -> Unit)?,
    centerTitle: Boolean,
    barButtons: BarButtons,
) {
    val endRoom = LocalTopBarEndRoom.current
    // The bar's own buttons, and any glass in its title, come in with the scroll
    ProvideContentColor(color = ExtendedTheme.colors.text) {
        CompositionLocalProvider(LocalBarButtons provides barButtons) {
            SubcomposeLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = TopBarRowPadding),
            ) { constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val loose = constraints.copy(minWidth = 0, minHeight = 0)
                val nav = navigationIcon?.let { subcompose(TopBarSlot.Navigation, it).first().measure(loose) }
                val acts = subcompose(TopBarSlot.Actions, actions).first().measure(loose)
                val gap = if (end != null && acts.width > 0) EndGap.roundToPx() else 0
                val least = if (end != null) EndLeast.roundToPx() else 0
                val titleStart = (nav?.width ?: 0) + (if (nav != null) TitlePadding else BareTitlePadding).roundToPx()
                val titleRoom = if (centerTitle) width else width - titleStart - TitlePadding.roundToPx() - acts.width - gap - least
                val text = subcompose(TopBarSlot.Title) { Box { title() } }.first().measure(loose.copy(maxWidth = titleRoom.coerceAtLeast(0)))
                val own = if (centerTitle) {
                    (width - text.width) / 2 - TitlePadding.roundToPx() - acts.width - gap
                } else {
                    width - titleStart - text.width - TitlePadding.roundToPx() - acts.width - gap
                }
                val besideCentre = (width - CentreAllowance.roundToPx()) / 2 - TitlePadding.roundToPx()
                val shared = endRoom?.takeIf { end != null }?.room(width, own, besideCentre)
                val room = (shared ?: own).coerceAtLeast(least)
                val ending = end?.let { slot ->
                    subcompose(TopBarSlot.End) {
                        // Its own glass, not a bar button's that comes in with the scroll
                        CompositionLocalProvider(LocalBarButtons provides null) { Box { slot(room.toDp()) } }
                    }.first().measure(loose.copy(maxWidth = room))
                }
                layout(width, height) {
                    fun Placeable.centredY() = ((height - this.height) / 2f).roundToInt()
                    val endWidth = ending?.width ?: 0
                    nav?.place(0, nav.centredY())
                    ending?.place(width - endWidth, ending.centredY())
                    acts.place(width - endWidth - gap - acts.width, acts.centredY())
                    // Centred on the whole row, whatever buttons sit at either end. Under them, as a
                    // classic tab row takes the row's whole width and would take their taps
                    if (centerTitle) {
                        text.place(((width - text.width) / 2f).roundToInt(), text.centredY(), zIndex = -1f)
                    } else {
                        text.place(titleStart, text.centredY(), zIndex = -1f)
                    }
                }
            }
        }
    }
}

private enum class TopBarSlot { Navigation, Title, Actions, End }

/** Single-line title text for [GlassTopBar]. */
@Composable
fun GlassTopBarTitle(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private enum class TopBarLayer { Background, Foreground, Pinned }
