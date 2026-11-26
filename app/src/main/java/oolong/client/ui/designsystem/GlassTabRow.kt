package oolong.client.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import oolong.core.designsystem.theme.pressScale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** A [GlassTabRow]'s height, the same as the top bar's buttons. */
val GlassTabRowHeight = 48.dp

/** Space between the capsule's edge and its tabs. */
private val TabInset = 4.dp

private val TabCapsule = SmoothCornerShape.Capsule

/** Where each tab was last laid out, for the bubble and for scrolling a tab into view. */
private class TabSpans {
    var starts = IntArray(0)
    var widths = IntArray(0)

    /** The start and width of the bubble at [position], between two tabs where it is fractional. */
    fun at(position: Float): Pair<Float, Float> {
        val count = starts.size
        val p = position.coerceIn(0f, (count - 1).toFloat())
        val from = floor(p).toInt()
        val to = (from + 1).coerceAtMost(count - 1)
        val fraction = p - from
        return lerp(starts[from].toFloat(), starts[to].toFloat(), fraction) to lerp(widths[from].toFloat(), widths[to].toFloat(), fraction)
    }

    fun indexAt(x: Float): Int? = starts.indices.firstOrNull { x >= starts[it] && x < starts[it] + widths[it] }

    /** Where the bubble is with its middle at [x], fractional between two tabs' middles. */
    fun positionAt(x: Float): Float {
        val last = starts.size - 1
        fun middle(i: Int) = starts[i] + widths[i] / 2f
        if (x <= middle(0)) return 0f
        if (x >= middle(last)) return last.toFloat()
        val i = (0 until last).first { x < middle(it + 1) }
        return i + (x - middle(i)) / (middle(i + 1) - middle(i))
    }
}

/** Whether the tabs are pressed through a lens, which then stands in for their own press feedback. */
private val LocalTabLens = staticCompositionLocalOf { false }

/**
 * Tabs on a floating glass capsule. The chosen tab sits in a bubble like the bottom bar's chosen
 * item. The capsule is as wide as its tabs, up to the space it has, and scrolls them sideways
 * when they don't fit, keeping the chosen one in view. With [pagerState] the bubble follows the
 * pages as they are swiped.
 *
 * Pressing a tab lifts the bubble off as clear glass, a little larger than the tab, as a switch's
 * knob does, and carries it to that tab. With [onSelect], a drag then carries the lens across the
 * tabs, scrolling the row near its ends, and picks the tab it is let go on. In a row that scrolls,
 * only a drag from the chosen tab carries the lens, as a segmented control's thumb is dragged;
 * any other drag, and every drag without [onSelect], scrolls the row and puts the lens back.
 *
 * Tabs are laid out at the capsule's inner height; [GlassTab]s fill it. With [fill], the capsule
 * takes the whole width and the tabs share it equally, for a few tabs that fit, as a profile's.
 */
@Composable
fun GlassTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    pagerState: PagerState? = null,
    onSelect: ((Int) -> Unit)? = null,
    fill: Boolean = false,
    tabs: @Composable () -> Unit,
) {
    if (LocalChromeStyle.current == ChromeStyle.Classic) {
        ClassicTabRow(selectedTabIndex, modifier, pagerState, tabs)
        return
    }
    val scroll = rememberScrollState()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val selectedTabIndexState by rememberUpdatedState(selectedTabIndex)
    val haptics = LocalHapticFeedback.current
    val spans = remember { TabSpans() }
    val animated by animateFloatAsState(selectedTabIndex.toFloat(), Motion.snap(), label = "tabBubble")
    val bubble = ExtendedTheme.colors.emphasis.copy(alpha = BubbleAlpha)
    // Between two tabs while a page is half swiped
    val chosen: () -> Float = { pagerState?.let { it.currentPage + it.currentPageOffsetFraction } ?: animated }

    val glassAvailable = isGlassAvailable()
    val glassState = rememberHazeState()
    val scope = rememberCoroutineScope()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    // While the lens has the bubble, it is where the lens is, until the chosen tab catches up
    val lens = remember { Animatable(0f) }
    var lensActive by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    val held by animateFloatAsState(
        targetValue = if (holding) 1f else 0f,
        animationSpec = if (holding) Motion.press() else Motion.release(),
        label = "tabHeld",
    )
    val position: () -> Float = { if (lensActive) lens.value else chosen() }
    // Outside the gesture, whose scope allows only its own suspending calls
    val letGo: suspend (released: Boolean) -> Unit = { released ->
        if (released) {
            // The tab picks itself; the bubble stays with the lens until the chosen tab gets there
            withTimeoutOrNull(LensHandOverMillis) {
                snapshotFlow { chosen() }.first { abs(it - lens.value) < 0.02f }
            }
        } else {
            lens.animateTo(chosen(), Motion.snap())
        }
        if (!holding) lensActive = false
    }
    val inset = with(LocalDensity.current) { TabInset.roundToPx() }
    // As much as the navigation bar's lens outgrows its item, the same on both sides: a wide tab
    // grown by its width would reach far past the capsule
    val growth = with(LocalDensity.current) { (GlassTabRowHeight - TabInset * 2).toPx() * (LensScale - 1f) }
    val lensOptics = remember { liquidLensOptics((GlassTabRowHeight - TabInset * 2) * LensScale) }

    Box(modifier = modifier.height(GlassTabRowHeight)) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .then(if (fill) Modifier.fillMaxWidth() else Modifier)
                // What the lens sees, with what is behind the capsule: the capsule, its tabs and the
                // bubble, which fades from under it
                .floatingGlass(TabCapsule, barButton = false, lensTarget = true)
                .maskTo(TabCapsule)
                // The same outside floating chrome, without what is behind
                .hazeSource(glassState),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(LocalTabLens provides glassAvailable) {
                Layout(
                    content = tabs,
                    modifier = Modifier
                        .then(if (fill) Modifier.fillMaxWidth() else Modifier.scrollEdgeFade(scroll).horizontalScroll(scroll))
                        .padding(horizontal = TabInset)
                        .selectableGroup()
                        .drawBehind {
                            if (spans.starts.isEmpty()) return@drawBehind
                            val (start, width) = spans.at(position())
                            drawRoundRect(
                                color = bubble.copy(alpha = bubble.alpha * if (glassAvailable) 1f - held else 1f),
                                topLeft = Offset(start, 0f),
                                size = Size(width, size.height),
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        }
                        .then(
                            if (!glassAvailable) {
                                Modifier
                            } else {
                                // Watches presses without taking them: the tabs still get their clicks
                                // and the row its scrolling
                                // Ahead of the tabs and the row's scrolling: once the lens is dragged it takes
                                // the moves, so the row doesn't scroll and the tab under the press isn't clicked
                                Modifier.pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        val index = spans.indexAt(down.position.x) ?: return@awaitEachGesture
                                        // In a row that scrolls, a drag from another tab is the row's
                                        val carriesLens = currentOnSelect != null &&
                                            (scroll.maxValue == 0 || index == selectedTabIndexState)
                                        val from = position()
                                        lensActive = true
                                        holding = true
                                        scope.launch {
                                            lens.snapTo(from)
                                            lens.animateTo(index.toFloat(), Motion.snap())
                                        }
                                        var released = false
                                        var dragging = false
                                        var last = index
                                        while (true) {
                                            val change = awaitPointerEvent(PointerEventPass.Initial).changes
                                                .firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                released = true
                                                if (dragging) {
                                                    change.consume()
                                                    val target = lens.value.roundToInt()
                                                    if (target != selectedTabIndexState) currentOnSelect?.invoke(target)
                                                    scope.launch { lens.animateTo(target.toFloat(), Motion.snap()) }
                                                }
                                                break
                                            }
                                            if (change.isConsumed) break
                                            val moved = change.position - down.position
                                            if (!dragging && (abs(moved.x) > touchSlop || abs(moved.y) > touchSlop)) {
                                                // Up and down is the page's to scroll; a drag that doesn't carry
                                                // the lens is the row's own scrolling
                                                if (abs(moved.y) > abs(moved.x) || !carriesLens) break
                                                dragging = true
                                            }
                                            if (dragging) {
                                                change.consume()
                                                val x = change.position.x
                                                scope.launch { lens.snapTo(spans.positionAt(x)) }
                                                if (spans.positionAt(x).roundToInt() != last) {
                                                    last = spans.positionAt(x).roundToInt()
                                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                                }
                                                // Near an end of a row that scrolls, the row moves under the lens
                                                val inView = x - scroll.value
                                                val edge = LensScrollEdge.toPx()
                                                when {
                                                    inView < edge -> scope.launch { scroll.scrollBy(-LensScrollStep.toPx()) }
                                                    inView > scroll.viewportSize - edge -> scope.launch { scroll.scrollBy(LensScrollStep.toPx()) }
                                                }
                                            }
                                        }
                                        holding = false
                                        scope.launch { letGo(released) }
                                    }
                                }
                            }
                        ),
                ) { measurables, constraints ->
                    val height = (GlassTabRowHeight - TabInset * 2).roundToPx()
                    // Shared out equally where the width is known; a tab wider than its share keeps its width
                    val share = if (fill && constraints.hasBoundedWidth && measurables.isNotEmpty()) constraints.maxWidth / measurables.size else 0
                    val placeables = measurables.map {
                        it.measure(Constraints(minWidth = share, minHeight = height, maxHeight = height))
                    }
                    val starts = IntArray(placeables.size)
                    var x = 0
                    placeables.forEachIndexed { i, placeable ->
                        starts[i] = x
                        x += placeable.width
                    }
                    spans.starts = starts
                    spans.widths = IntArray(placeables.size) { placeables[it].width }
                    layout(x, height) {
                        placeables.forEachIndexed { i, placeable -> placeable.place(starts[i], 0) }
                    }
                }
            }
        }
        if (glassAvailable && held > 0f) {
            // Over the capsule, which it grows past, so outside its clip and its size
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset {
                            if (spans.starts.isEmpty()) return@offset IntOffset.Zero
                            val (start, width) = spans.at(lens.value)
                            IntOffset(
                                (inset + start - scroll.value - growth * held / 2).roundToInt(),
                                (inset - growth * held / 2).roundToInt(),
                            )
                        }
                        .layout { measurable, _ ->
                            if (spans.starts.isEmpty()) return@layout layout(0, 0) {}
                            val (_, width) = spans.at(lens.value)
                            val tabHeight = GlassTabRowHeight.toPx() - inset * 2
                            val w = (width + growth * held).roundToInt()
                            val h = (tabHeight + growth * held).roundToInt()
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout(w, h) { placeable.place(0, 0) }
                        }
                        // Bent as the navigation bar's lens is, for its size
                        .chromeControlLens(glassState, TabCapsule, held, lensOptics)
                )
            }
        }
    }

    // Brings the chosen tab to the middle of the capsule, as far as the ends allow. Tabs sharing
    // the width don't scroll: their scroll state, attached to nothing, would take any value, which
    // the lens's place then counts as scrolled
    if (!fill) LaunchedEffect(selectedTabIndex) {
        // Laid out by the next frame, also when the row first appears
        withFrameNanos { }
        if (selectedTabIndex !in spans.starts.indices) return@LaunchedEffect
        val center = inset + spans.starts[selectedTabIndex] + spans.widths[selectedTabIndex] / 2
        scroll.animateScrollTo((center - scroll.viewportSize / 2).coerceIn(0, scroll.maxValue), Motion.settle())
    }
}

/** How much the lens outgrows the tab, as the navigation bar's outgrows its item. */
private const val LensScale = 1.4f

/** How near an end of the row the dragged lens scrolls it, and by how much a move. */
private val LensScrollEdge = 32.dp
private val LensScrollStep = 12.dp

/** How long the lens keeps the bubble after a tap, at most, for the chosen tab to get there. */
private const val LensHandOverMillis = 800L

/** How much of the bottom bar's emphasis the bubble takes, as the bottom bar's chosen item does. */
private const val BubbleAlpha = 0.12f

/**
 * A tab for [GlassTabRow]. Pressed, the row's lens shows it; where glass isn't drawn, it shrinks a
 * little under a veil the shape of the bubble, as the app's other controls do. [content] is
 * centred in a row.
 */
@Composable
fun GlassTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedContentColor: Color = ExtendedTheme.colors.text,
    unselectedContentColor: Color = ExtendedTheme.colors.textSecondary,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val classic = LocalClassicTabs.current
    val color by animateColorAsState(if (selected) selectedContentColor else unselectedContentColor, Motion.snap(), label = "tabColor")
    val lens = LocalTabLens.current
    CompositionLocalProvider(LocalContentColor provides color, LocalContentAlpha provides 1f) {
        Row(
            modifier = modifier
                .then(if (lens || classic) Modifier else Modifier.pressScale(interactionSource))
                .then(if (classic) Modifier.clipToBounds() else Modifier)
                .selectable(
                    selected = selected,
                    interactionSource = interactionSource,
                    indication = when {
                        lens -> null
                        classic -> PressFeedback.Overlay
                        else -> PressFeedback.overlay(TabCapsule)
                    },
                    enabled = enabled,
                    role = Role.Tab,
                    onClick = onClick,
                )
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** A [GlassTab] of [text], set and spaced as a Material tab's text is. */
@Composable
fun GlassTab(
    selected: Boolean,
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selectedContentColor: Color = ExtendedTheme.colors.text,
    unselectedContentColor: Color = ExtendedTheme.colors.textSecondary,
) {
    GlassTab(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        selectedContentColor = selectedContentColor,
        unselectedContentColor = unselectedContentColor,
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp)) { text() }
    }
}

/**
 * A tab's name, the same on every tab row: in the label style, bold while [selected]. It keeps the
 * bold name's width either way, so choosing a tab doesn't shift the row as the bubble or line moves.
 */
@Composable
fun TabText(text: String, selected: Boolean, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.button
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = style.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = Modifier
                .alpha(0f)
                .clearAndSetSemantics {},
        )
        Text(text = text, style = style.copy(fontWeight = if (selected) FontWeight.Bold else style.fontWeight), maxLines = 1)
    }
}
