package oolong.client.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.navigation.transition.BesideLeadingChrome
import oolong.client.navigation.transition.PageTransitions
import oolong.client.ui.designsystem.ThresholdHaptics
import oolong.client.ui.designsystem.adaptive.LocalWindowSizeClass
import oolong.client.ui.designsystem.adaptive.WindowSizeClass
import oolong.client.ui.designsystem.rememberHaptics
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Motion

/**
 * How the two panes share the window: the list pane's width, set by dragging the divider, and
 * whether the list is folded away so the open page has the whole width.
 */
@Stable
class ListDetailState(listWidth: Dp = DefaultListWidth) {
    var listWidth by mutableStateOf(listWidth)

    /** Lasts until the list is the top page again, as opening something new from it would need it shown. */
    var collapsed by mutableStateOf(false)

    companion object {
        val DefaultListWidth = 400.dp
        val MinListWidth = 320.dp
        val MaxListWidth = 560.dp
        val MinDetailWidth = 400.dp

        /** Releasing the divider left of this folds the list away. */
        val CollapseBelow = 200.dp
    }
}

/**
 * Shows the last list on the stack on the left and the last page it opened on the right, or an
 * empty pane while there is none. Pages over the whole window, such as logging in, fall through
 * to the single pane.
 */
class ListDetailSceneStrategy<T : Any>(
    private val state: ListDetailState,
    private val onListWidthChange: (Dp?) -> Unit,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val top = entries.last()
        if (top.paneRole != PaneRole.List && top.paneRole != PaneRole.Detail) return null
        val list = entries.lastOrNull { it.paneRole == PaneRole.List } ?: return null
        val detail = entries.lastOrNull { it.paneRole == PaneRole.Detail }
        return ListDetailScene(
            list = list,
            detail = detail,
            listOnTop = top === list,
            previousEntries = entries.dropLast(1),
            state = state,
            onListWidthChange = onListWidthChange,
        )
    }
}

private class ListDetailScene<T : Any>(
    private val list: NavEntry<T>,
    private val detail: NavEntry<T>?,
    private val listOnTop: Boolean,
    // Back removes the top page only, whichever pane it is in
    override val previousEntries: List<NavEntry<T>>,
    private val state: ListDetailState,
    private val onListWidthChange: (Dp?) -> Unit,
) : Scene<T> {
    // One key for every pair, so the display keeps this scene and each pane animates its own change
    override val key: Any = ListDetailScene::class

    override val entries: List<NavEntry<T>> = listOfNotNull(list, detail)

    override val content: @Composable () -> Unit = {
        BesideLeadingChrome(entry = null) { ListDetailLayout(list, detail, listOnTop, state, onListWidthChange) }
    }

    override fun equals(other: Any?): Boolean =
        other is ListDetailScene<*> && list == other.list && detail == other.detail &&
            listOnTop == other.listOnTop && previousEntries == other.previousEntries

    override fun hashCode(): Int = listOf(list, detail, listOnTop, previousEntries).hashCode()
}

@Composable
private fun <T : Any> ListDetailLayout(
    list: NavEntry<T>,
    detail: NavEntry<T>?,
    listOnTop: Boolean,
    state: ListDetailState,
    onListWidthChange: (Dp?) -> Unit,
) {
    LaunchedEffect(listOnTop, detail) {
        if (listOnTop || detail == null) state.collapsed = false
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ExtendedTheme.colors.background)
    ) {
        val maxListWidth = (maxWidth - ListDetailState.MinDetailWidth)
            .coerceIn(ListDetailState.MinListWidth, ListDetailState.MaxListWidth)
        val listWidth = state.listWidth.coerceIn(ListDetailState.MinListWidth, maxListWidth)
        val target = if (state.collapsed) 0.dp else listWidth
        val shown = remember { Animatable(target, Dp.VectorConverter) }
        var dragging by remember { mutableStateOf(false) }
        LaunchedEffect(target) {
            if (!dragging) shown.animateTo(target, DividerSpring)
        }
        val shownWidth = shown.value

        // The list keeps its width while it folds away, sliding under the edge, so its text does
        // not reflow on every frame
        Box(
            modifier = Modifier
                .width(listWidth)
                .fillMaxHeight()
                .offset(x = shownWidth - listWidth)
                .consumeWindowInsets(PaneInsets.only(WindowInsetsSides.End))
                .clipToBounds()
        ) {
            if (shownWidth > 0.dp) Pane(list)
        }
        Box(
            modifier = Modifier
                .offset(x = shownWidth)
                .width(maxWidth - shownWidth)
                .fillMaxHeight()
                .consumeWindowInsets(PaneInsets.only(WindowInsetsSides.Start))
                .clipToBounds()
        ) {
            Pane(detail)
        }

        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current
        // Felt where letting go starts to fold the list away, and where it stops doing so
        val haptics = rememberHaptics()
        val foldFeel = remember(haptics) { ThresholdHaptics(haptics) }
        val dragState = rememberDraggableState { delta ->
            val width = shown.value + with(density) { delta.toDp() }
            if (detail != null) foldFeel.update(width < ListDetailState.CollapseBelow)
            coroutineScope.launch { shown.snapTo(width.coerceIn(0.dp, maxListWidth)) }
        }
        // The touch area stays inside the scene: folded away, half of it lay past the scene's edge,
        // under the rail, where no touch reaches it, and the list could not be pulled back out
        val dividerX = (shownWidth - DividerTouchWidth / 2).coerceAtLeast(0.dp)
        Divider(
            lineAt = shownWidth - dividerX,
            modifier = Modifier
                .offset(x = dividerX)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        dragging = true
                        foldFeel.reset()
                    },
                    onDragStopped = { velocity ->
                        dragging = false
                        val released = shown.value
                        val speed = with(density) { velocity.toDp() }
                        // Where the divider is headed, not only where it was let go: a flick
                        // toward the edge folds the list away even from far out
                        val headedTo = released + speed * FlickLookAhead
                        if (headedTo < ListDetailState.CollapseBelow && detail != null) {
                            state.collapsed = true
                            shown.animateTo(0.dp, DividerSpring, initialVelocity = speed)
                        } else {
                            val width = released.coerceIn(ListDetailState.MinListWidth, maxListWidth)
                            state.collapsed = false
                            state.listWidth = width
                            onListWidthChange(width)
                            shown.animateTo(width, DividerSpring, initialVelocity = speed)
                        }
                    },
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            state.collapsed = false
                            state.listWidth = ListDetailState.DefaultListWidth
                            onListWidthChange(null)
                        }
                    )
                },
        )
    }
}

/**
 * The latest change to the back stack, by page ids: the panes tell a step deeper or back, which
 * slides, from a page swapped for another, which fades. [jump] marks the rail clearing the stack.
 */
@Immutable
class BackStackChange(val before: Set<Any>, val after: Set<Any>, val jump: Boolean)

val LocalBackStackChange = compositionLocalOf { BackStackChange(emptySet(), emptySet(), jump = false) }

/** A pane showing [entry], or the empty pane while there is none, animating as pages change in it. */
@Composable
private fun <T : Any> Pane(entry: NavEntry<T>?) {
    val change = LocalBackStackChange.current
    val overlap = PageTransitions.rememberOverlap()
    val layers = remember { PaneLayers() }
    val key = entry?.contentKey
    layers.arrive(key, back = layers.shown.let { it != NoPage && stepsBack(it, key, change) })
    // Pages size themselves by the window; in a pane, the pane is their window
    BoxWithConstraints {
        val paneSizeClass = WindowSizeClass.calculateFromSize(DpSize(maxWidth, maxHeight))
        AnimatedContent(
            targetState = entry,
            contentKey = { it?.contentKey },
            transitionSpec = {
                val from = initialState?.contentKey
                val to = targetState?.contentKey
                // The empty pane counts as the bottom of the pane: the first page opened slides in
                // over it, and closing the last slides back to it
                when {
                    change.jump -> PageTransitions.crossFade(overlap.value)
                    // The page stays below the new one: a step deeper
                    (from == null || from in change.after) && to != null && to !in change.before ->
                        PageTransitions.transitionSpec<NavEntry<T>?>(overlap)()
                    stepsBack(from, to, change) -> PageTransitions.popTransitionSpec<NavEntry<T>?>(overlap)()
                    // One page swapped for another, as a list opening a different thread
                    else -> PageTransitions.crossFade(overlap.value)
                }.apply { targetContentZIndex = layers.of(to) }
            },
            modifier = Modifier.fillMaxSize(),
            label = "Pane",
        ) { shown ->
            CompositionLocalProvider(
                LocalNavAnimatedContentScope provides this,
                LocalWindowSizeClass provides paneSizeClass,
            ) {
                PageTransitions.PageFrame {
                    if (shown == null) EmptyPane() else shown.Content()
                }
            }
        }
    }
}

/** Whether the page [from] left and the one below, [to], shows again: a step back. */
private fun stepsBack(from: Any?, to: Any?, change: BackStackChange): Boolean =
    from != null && from !in change.after && (to == null || to in change.before)

private val NoPage = Any()

/**
 * Which page of a pane draws over which while they change. A page keeps the layer it came in
 * on, so the next change must place the page coming in by it: above for a step deeper or a page
 * swapped in, below for a step back, whatever came before. By order alone, a page come back to
 * would draw over the one leaving, and one swapped in, lifted to fade in over the old, would stay
 * over the next page opened from it.
 */
private class PaneLayers {
    var shown: Any? = NoPage
        private set
    private var layer = 0f
    private var top = 0f
    private var bottom = 0f

    /** Notes the page now [shown], coming in by a step [back] or not. */
    fun arrive(key: Any?, back: Boolean) {
        if (key == shown) return
        layer = if (back) --bottom else ++top
        shown = key
    }

    /** The layer of the page coming in; a page already in keeps its own. */
    fun of(key: Any?): Float = if (key == shown) layer else 0f
}

/** The right pane before anything is open: the app's mark, faintly. */
@Composable
private fun EmptyPane() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Drained of colour rather than tinted flat, so the mark keeps its inner lines
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_new_monochrome),
            contentDescription = null,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            alpha = if (ExtendedTheme.colors.isNightMode) 0.12f else 0.25f,
            modifier = Modifier.size(160.dp),
        )
    }
}

/** A hairline between the panes with a grip in the middle; wider to touch than it looks. */
@Composable
private fun Divider(lineAt: Dp, modifier: Modifier) {
    // The line and the grip centred on [lineAt], from the touch area's start
    Box(
        modifier = modifier
            .width(DividerTouchWidth)
            .fillMaxHeight(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = lineAt - 0.5.dp)
                .width(1.dp)
                .fillMaxHeight()
                .background(ExtendedTheme.colors.divider)
        )
        Box(
            modifier = Modifier
                .offset(x = (lineAt - 2.dp).coerceAtLeast(0.dp))
                .size(width = 4.dp, height = 36.dp)
                .background(ExtendedTheme.colors.textDisabled.copy(alpha = 0.5f), GripShape)
        )
    }
}

private val PaneInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)
private val DividerTouchWidth = 16.dp
private val GripShape = Corners.full
private val DividerSpring get() = Motion.snap(Dp.VisibilityThreshold)

/** How far ahead, in seconds of its speed, a flicked divider is judged by. */
private const val FlickLookAhead = 0.15f
