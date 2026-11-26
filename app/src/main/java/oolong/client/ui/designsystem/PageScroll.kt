package oolong.client.ui.designsystem

import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import oolong.core.designsystem.theme.Motion
import kotlin.math.PI
import kotlin.math.abs

/**
 * How a page's content is scrolling, for everything on the page that changes with it (see
 * [ScrollProgress]): how far it is from its top, which lists report with [pageScroll], on a page
 * of pages the one in view; and which way it is going, which the [GlassScaffold] it is given to
 * watches for. Hoist it with [rememberPageScrollState] to read it outside the scaffold, as for a
 * bottom bar that a page's tabs share.
 */
@Stable
class PageScrollState internal constructor() {
    internal class Source(val scrolled: () -> Int, val scrollable: ScrollableState?, val toTop: (suspend () -> Unit)?) {
        /** Where the list starts across the page, so the page in view can be told from its neighbours. */
        var start by mutableFloatStateOf(0f)
    }

    private val sources = mutableStateListOf<Source>()

    /** What follows which way the content goes, rather than where it is. */
    private val directed = mutableListOf<DirectedProgress>()

    internal fun add(progress: DirectedProgress) = directed.add(progress)

    internal fun remove(progress: DirectedProgress) = directed.remove(progress)

    /** Watches every scroll on the page, taking none of it. */
    internal val scrollWatcher = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            directed.forEach { it.onScroll(consumed.y, available.y) }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity =
            if (snapFling(-available.y)) available else Velocity.Zero

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            directed.forEach { it.onScrollEnd() }
            return Velocity.Zero
        }
    }

    /** How a fling slows down, to tell where one let go now would stop; set by the scaffold. */
    internal var flingDecay: DecayAnimationSpec<Float>? = null

    /**
     * A fling that would stop inside a range that snaps goes instead to the range's nearer end,
     * carrying the fling's speed into a spring, so it slows into its place as one move rather than
     * stopping and then setting off again. Returns whether it took the fling.
     */
    private suspend fun snapFling(velocity: Float): Boolean {
        val source = inView ?: return false
        val scrollable = source.scrollable ?: return false
        val decay = flingDecay ?: return false
        val scrolled = source.scrolled()
        // Far down a list the distance to its top isn't known, nor near a range anyway
        if (scrolled >= FarScroll) return false
        val from = scrolled.toFloat()
        val end = decay.calculateTargetValue(from, velocity)
        val range = snapRanges.firstOrNull { end > it.start && end < it.endInclusive } ?: return false
        val target = if (end - range.start < range.endInclusive - end) range.start else range.endInclusive
        // No faster than the spring can take without passing its end: snapping never bounces
        val most = SettleOmega * abs(target - from)
        val speed = velocity.coerceIn(-most, most)
        scrollable.scroll {
            var last = from
            animate(from, target, speed, Motion.settle()) { value, _ ->
                scrollBy(value - last)
                last = value
            }
        }
        return true
    }

    /** Where the scaffold starts, so a page in a pane beside another still counts as in view. */
    internal var originX by mutableFloatStateOf(0f)

    internal fun add(source: Source) = sources.add(source)

    internal fun remove(source: Source) = sources.remove(source)

    /** The content in view: on a page of pages, the page nearest the scaffold's start. */
    internal val inView: Source? get() = sources.minByOrNull { abs(it.start) }

    /** Scrolls the content in view back to its top, as a double tap on the top bar does. */
    suspend fun scrollToTop() {
        inView?.toTop?.invoke()
    }

    /**
     * Pixels scrolled under the bar, or null when no list reports, so the edge simply shows. While
     * a page of pages is swiped, it blends between the two pages by how far the swipe has gone, so
     * what follows it, such as a header the pages share, moves smoothly from one to the other.
     */
    val scrolled: Int?
        get() = blended { it.coerceAtMost(FarScroll).toFloat() }?.toInt()

    /**
     * What [of] makes of how far the content in view has scrolled, blended between two pages while
     * a page of pages is swiped, or null when no list reports. Blending what is drawn rather than
     * the pixels keeps an effect that is full after a short scroll, such as the scroll edge, from
     * holding until the very end of the swipe and then dropping.
     */
    fun blended(of: (Int) -> Float): Float? {
        val near = sources.sortedBy { abs(it.start) }
        val a = near.getOrNull(0) ?: return null
        val b = near.getOrNull(1)
        val aValue = of(a.scrolled())
        if (b == null || a.start == 0f) return aValue
        val span = abs(a.start) + abs(b.start)
        if (span <= 0f) return aValue
        return lerp(aValue, of(b.scrolled()), abs(a.start) / span)
    }

    /**
     * The scaffold, which the bar and the content are laid out in. What docks into the bar is
     * placed by it rather than by the window, as the whole page may be scaled and moved, as while
     * it is peeked past, and a move taken in the window would be scaled a second time.
     */
    internal var frame: LayoutCoordinates? = null

    /** Where [coordinates] lie in the [frame], whole even past the screen's edge. */
    internal fun boundsOf(coordinates: LayoutCoordinates): Rect {
        val frame = frame?.takeIf { it.isAttached } ?: return coordinates.boundsInWindow(clipBounds = false)
        return frame.localBoundingBoxOf(coordinates, clipBounds = false)
    }

    /** The top bar's row, in the [frame], for things that dock into it; NaN before it is laid out. */
    internal var barRowTop by mutableFloatStateOf(Float.NaN)
    internal var barRowBottom by mutableFloatStateOf(Float.NaN)

    /** Where each [matchedGeometryAnchor] was last laid out, in the [frame]. */
    internal val anchors = mutableStateMapOf<Any, Rect>()

    private val snapRanges = mutableStateListOf<ClosedFloatingPointRange<Float>>()

    internal fun addSnapRange(range: ClosedFloatingPointRange<Float>) = snapRanges.add(range)

    internal fun removeSnapRange(range: ClosedFloatingPointRange<Float>) = snapRanges.remove(range)

    /**
     * Whenever the content in view stops inside a range that snaps, scrolls it on to the nearer
     * end, so nothing scroll-driven is left halfway.
     */
    internal suspend fun settle() {
        snapshotFlow { inView }.collectLatest { source ->
            val scrollable = source?.scrollable ?: return@collectLatest
            snapshotFlow { scrollable.isScrollInProgress }.filter { !it }.collect {
                val scrolled = source.scrolled().toFloat()
                val range = snapRanges.firstOrNull { scrolled > it.start && scrolled < it.endInclusive }
                    ?: return@collect
                val target = if (scrolled - range.start < range.endInclusive - scrolled) range.start else range.endInclusive
                scrollable.animateScrollBy(target - scrolled, Motion.dismiss())
            }
        }
    }
}

/** How quickly [Motion.settle] closes in, in radians a second: 2π over its response. */
private const val SettleOmega = 2 * PI.toFloat() / 0.4f

/** Far enough that nothing driven by the scroll is still moving, but small enough to blend. */
private const val FarScroll = 1 shl 14

/** The scroll edge a [GlassScaffold] shares between its top bar and its content. */
val LocalPageScroll = compositionLocalOf<PageScrollState?> { null }

/** A [PageScrollState] to give a [GlassScaffold], for reading the page's scroll outside it. */
@Composable
fun rememberPageScrollState(): PageScrollState = remember { PageScrollState() }

/**
 * Reports to the page's [PageScrollState] how far this scrolling content is from its top, as
 * [scrolled] pixels, the [scrollable] that moves it, which settles a large title left halfway, and
 * how it goes back [toTop].
 */
@Composable
fun Modifier.pageScroll(
    scrollable: ScrollableState?,
    toTop: (suspend () -> Unit)? = null,
    scrolled: () -> Int,
): Modifier {
    val state = LocalPageScroll.current ?: return this
    val source = remember(state, scrollable) { PageScrollState.Source(scrolled, scrollable, toTop) }
    DisposableEffect(state, source) {
        state.add(source)
        onDispose { state.remove(source) }
    }
    return onGloballyPositioned { source.start = it.positionInRoot().x - state.originX }
}

/**
 * [pageScroll] for a lazy list: how far its first item has moved up. Items under the bar
 * are still laid out, so the first item stays known well past the bar and a large title; once it
 * is gone the content counts as far. On a page of a [SharedHeaderPager], the list is kept in line
 * with the header.
 */
@Composable
fun Modifier.pageScroll(state: LazyListState): Modifier {
    RegisterWithSharedHeader(state)
    return pageScroll(state, toTop = { state.animateScrollToItem(0) }) {
        // Item offsets start at the end of the top padding
        state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.let { -it.offset } ?: Int.MAX_VALUE
    }
}

/** [pageScroll] for a lazy grid, as for a lazy list. */
@Composable
fun Modifier.pageScroll(state: LazyGridState): Modifier = pageScroll(state, toTop = { state.animateScrollToItem(0) }) {
    state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.let { -it.offset.y } ?: Int.MAX_VALUE
}

/** [pageScroll] for a scrolling column. */
@Composable
fun Modifier.pageScroll(state: ScrollState): Modifier = pageScroll(state, toTop = { state.animateScrollTo(0) }) { state.value }

/** [verticalScroll] that also reports to the top bar's scroll edge. */
@Composable
fun Modifier.verticalScrollUnderBar(state: ScrollState = rememberScrollState()): Modifier =
    pageScroll(state).verticalScroll(state)

/**
 * Lets dragging this scroll the page's content in view, for something drawn over the content
 * rather than in it, such as a header the pages of a page share, so it scrolls like the content
 * under it would.
 */
@Composable
fun Modifier.scrollsPageContent(): Modifier {
    val page = LocalPageScroll.current ?: return this
    val forwarding = remember(page) { PageContentScrollable(page) }
    return scrollable(forwarding, Orientation.Vertical, reverseDirection = true)
}

/** Passes scrolling on to whichever content is in view. */
private class PageContentScrollable(private val page: PageScrollState) : ScrollableState {
    private val target: ScrollableState? get() = page.inView?.scrollable

    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) {
        target?.scroll(scrollPriority, block)
    }

    override fun dispatchRawDelta(delta: Float): Float = target?.dispatchRawDelta(delta) ?: 0f

    override val isScrollInProgress: Boolean get() = target?.isScrollInProgress ?: false
}
