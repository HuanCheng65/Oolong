package oolong.client.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/*
 * A header shared by the pages of a pager, such as a forum's or a user's above their tabs. It is
 * drawn over the pages' lists rather than in any of them, and moves up as the list in view
 * scrolls until it has gone under the bar; rows pinned below the bar, such as the tabs, follow it
 * up and then stay. Every other list is kept in line with it, so swiping to another page neither
 * moves the header nor shows that list somewhere else under it.
 */

/**
 * The state of a [SharedHeaderPager], for the bar above it: [pinnedOffset] for its pinned rows,
 * [gone] for what should wait till the header has gone, such as the bar's row leaving.
 */
@Stable
class SharedHeaderState internal constructor(internal val page: PageScrollState) {
    /** The header's height, and its pinned rows' with their margins, in pixels. */
    internal var headerHeight by mutableIntStateOf(0)
    internal var pinnedHeight by mutableIntStateOf(0)

    /** The room above the content, under the bar, which the header goes on through as it leaves. */
    internal var clearance by mutableIntStateOf(0)

    /**
     * How far the header had moved up when the list in view last stopped, which every list is kept
     * in line with, and what holds the header while no list tells where it is, as while one loads.
     */
    internal var held by mutableIntStateOf(0)

    /** How far a pull to refresh has taken the header down, which the pinned rows follow. */
    internal var pull: () -> Float = { 0f }

    /** Each page's list, by page, to keep in line with the header. */
    internal val lists = mutableStateMapOf<Int, LazyListState>()

    /**
     * How far the header has moved up: past the content's top and on under the bar until it has
     * left the screen, so none of it stays behind the bar's glass.
     */
    internal val moved: Int get() = (page.scrolled ?: held).coerceIn(0, headerHeight + clearance)

    /** How much of the header has gone under the bar, in pixels. */
    val collapsed: Int get() = moved.coerceAtMost(headerHeight)

    /** Whether the header has all gone under the bar, as of when the list in view last moved it. */
    val gone: Boolean get() = headerHeight > 0 && held >= headerHeight

    /** How far below the bar's rows its pinned rows sit: under the header, as much of it as shows. */
    val pinnedOffset: Int get() = headerHeight - collapsed + pull().roundToInt()
}

/**
 * A [SharedHeaderState] following [page], the [PageScrollState] given to the page's scaffold.
 * Where the header was is saved: back from a page opened on top, the lists are put in line with
 * it again, and a header taken for back at the top would take them there too. So are the heights
 * the lists leave room for, so they come back where they were from the first frame, before the
 * header and the pinned rows are measured again; a page morphing back into one of its items
 * would otherwise follow the item as it moves down.
 */
@Composable
fun rememberSharedHeaderState(page: PageScrollState): SharedHeaderState = rememberSaveable(
    page,
    saver = Saver(
        save = { intArrayOf(it.held, it.headerHeight, it.pinnedHeight) },
        restore = { (held, headerHeight, pinnedHeight) ->
            SharedHeaderState(page).apply {
                this.held = held
                this.headerHeight = headerHeight
                this.pinnedHeight = pinnedHeight
            }
        },
    ),
) { SharedHeaderState(page) }

/** Marks the bar's pinned rows, its margins included, which the pages' lists leave room for. */
fun Modifier.sharedHeaderPinned(state: SharedHeaderState): Modifier = onSizeChanged { state.pinnedHeight = it.height }

/** The page of a [SharedHeaderPager] that content is on, which its list registers with. */
private class SharedHeaderPage(val state: SharedHeaderState, val index: Int)

private val LocalSharedHeaderPage = staticCompositionLocalOf<SharedHeaderPage?> { null }

/**
 * Pages under a header they share. Each page's content gets [LocalChromePadding] to pad its list
 * with, room for the header and the pinned rows above and [contentPadding] around, and its lazy
 * list, through [MyLazyColumn], is kept in line with the header. Dragging the header scrolls the
 * list in view.
 *
 * @param contentPadding the scaffold's padding, which the scaffold's [PageScrollState] must be the
 * one [state] follows
 * @param wash a colour washed from the top of the page down the header, behind everything and
 * going up with it; it stays where it is while the page is pulled to refresh, if [content] is
 * wrapped in a pull
 * @param frame wraps the pages and the header, as in a pull to refresh
 */
@Composable
fun SharedHeaderPager(
    state: SharedHeaderState,
    pagerState: PagerState,
    contentPadding: PaddingValues,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    wash: Color = Color.Unspecified,
    key: ((index: Int) -> Any)? = null,
    frame: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
    pageContent: @Composable (page: Int) -> Unit,
) {
    val density = LocalDensity.current
    SideEffect { state.clearance = with(density) { contentPadding.calculateTopPadding().roundToPx() } }
    val listPadding = remember(contentPadding, density) {
        PaddingAbove(contentPadding) { with(density) { (state.headerHeight + state.pinnedHeight).toDp() } }
    }
    // Every other list is kept in line with the header as the open one scrolls. Lists not laid out
    // yet are put in place too, and load there.
    LaunchedEffect(state, pagerState) {
        var wasScrolling = false
        snapshotFlow {
            val scrolling = state.lists[pagerState.currentPage]?.isScrollInProgress == true
            scrolling to state.moved
        }.collect { (scrolling, moved) ->
            // While scrolling, and once more as it stops, for where it stopped; not when a list
            // that has just loaded reports its top
            val follow = scrolling || wasScrolling
            wasScrolling = scrolling
            if (!follow || pagerState.isScrollInProgress) return@collect
            state.held = moved
            state.lists.forEach { (index, list) ->
                if (index != pagerState.currentPage) list.alignWithHeader(moved, state.headerHeight + state.clearance)
            }
        }
    }
    Box(modifier = modifier) {
        if (wash.isSpecified) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val height = contentPadding.calculateTopPadding().roundToPx() + state.headerHeight
                        val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                        layout(placeable.width, placeable.height) { placeable.place(0, -state.moved) }
                    }
                    .background(Brush.verticalGradient(listOf(wash, Color.Transparent)))
            )
        }
        frame {
            val pull = LocalPullOffset.current
            SideEffect { state.pull = pull }
            Box {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = key,
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    // Only the open page's list tells the page how far it has scrolled
                    CompositionLocalProvider(
                        LocalPageScroll provides state.page.takeIf { page == pagerState.currentPage },
                        LocalSharedHeaderPage provides SharedHeaderPage(state, page),
                        LocalChromePadding provides listPadding,
                    ) {
                        Box(modifier = Modifier.pagerPageFocus(showing = page == pagerState.currentPage), propagateMinConstraints = true) {
                            pageContent(page)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, contentPadding.calculateTopPadding().roundToPx() - state.moved) }
                        .onSizeChanged { state.headerHeight = it.height }
                        .scrollsPageContent(),
                ) {
                    header()
                }
            }
        }
    }
}

/**
 * Something on a [SharedHeaderPager]'s page drawn before its list is, such as placeholders while
 * it loads: moved up with the header as far as it has gone, so it shows under it.
 */
@Composable
fun Modifier.underSharedHeader(): Modifier {
    val page = LocalSharedHeaderPage.current ?: return this
    return offset { IntOffset(0, -page.state.held.coerceAtMost(page.state.headerHeight)) }
}

/**
 * Pads what stands in for a list, such as its loading or empty screen, by [LocalChromePadding] as
 * the list would be, and on a [SharedHeaderPager] page keeps it under the header.
 */
@Composable
fun Modifier.chromePadding(): Modifier = underSharedHeader().padding(LocalChromePadding.current)

/**
 * Registers [list] with the [SharedHeaderPager] page it is on, if it is on one, and puts it in
 * line with the header as soon as it has items.
 */
@Composable
internal fun RegisterWithSharedHeader(list: LazyListState) {
    val page = LocalSharedHeaderPage.current ?: return
    DisposableEffect(page.state, page.index, list) {
        page.state.lists[page.index] = list
        onDispose { if (page.state.lists[page.index] === list) page.state.lists.remove(page.index) }
    }
    LaunchedEffect(page.state, list) {
        snapshotFlow { list.layoutInfo.totalItemsCount > 0 }.first { it }
        list.alignWithHeader(page.state.held, page.state.headerHeight + page.state.clearance)
    }
}

/**
 * Puts this list where it belongs under a header that has [moved] up of the [away] it takes to
 * leave the screen: just as far down while any of it shows, since a header in view means the top
 * of every page; once it has left, anywhere below it, so a list further down keeps its place.
 */
private fun LazyListState.alignWithHeader(moved: Int, away: Int) {
    if (away <= 0) return
    // Item offsets start at the end of the top padding. A list not laid out yet, or out of view,
    // goes by its own scroll position; past the first item it is far down.
    val scrolled = layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset?.let { -it }
        ?: if (firstVisibleItemIndex == 0) firstVisibleItemScrollOffset else Int.MAX_VALUE
    val target = if (moved < away) moved else maxOf(scrolled, away)
    if (target != scrolled) requestScrollToItem(0, target)
}

/**
 * Takes [list] back to its start under the header as it is now, for a list about to stand for
 * something else, such as another filter: just under the header while any of it shows, else just
 * under the pinned rows, the header kept out of view.
 */
fun SharedHeaderState.toListStart(list: LazyListState) {
    // Held there too, or the list, put in line again once its items come, goes as far as a
    // header that has left the screen, which takes its first item under the bar
    val start = collapsed
    held = start
    list.requestScrollToItem(0, start)
}

/** [base], with [extra] more room above. */
private class PaddingAbove(private val base: PaddingValues, private val extra: () -> Dp) : PaddingValues {
    override fun calculateTopPadding(): Dp = base.calculateTopPadding() + extra()
    override fun calculateBottomPadding(): Dp = base.calculateBottomPadding()
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = base.calculateLeftPadding(layoutDirection)
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = base.calculateRightPadding(layoutDirection)
}
