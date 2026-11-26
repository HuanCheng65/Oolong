package oolong.client.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.ui.designsystem.ChromeStyle
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.LocalChromeStyle
import oolong.client.ui.designsystem.LocalContextMenus
import oolong.client.ui.designsystem.OwnAvatar
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.classicBarGlass
import oolong.client.ui.designsystem.floatingGlass
import oolong.client.ui.designsystem.glassLens
import oolong.client.ui.designsystem.isLensGlass
import oolong.client.ui.designsystem.lensTarget
import oolong.client.ui.designsystem.liquidLensOptics
import oolong.client.ui.designsystem.longPressMenu
import oolong.client.ui.designsystem.rememberElasticPress
import oolong.client.ui.designsystem.theme.ExtendedColors
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.designsystem.theme.White
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import oolong.core.designsystem.theme.pressScale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import androidx.compose.ui.util.lerp as lerpFloat

/**
 * The main navigation on wider windows: the floating pill stood upright beside the panes. It
 * stays while pages are open, highlighting the tab they were opened from; choosing a tab goes
 * back to the main page on that tab, and choosing the shown one again refreshes it. Labels go
 * where the window is too short for them.
 */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun NavigationRail(
    currentPosition: Int,
    onSelect: (position: Int) -> Unit,
    navigationItems: ImmutableList<NavigationItem>,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
    themeColors: ExtendedColors = ExtendedTheme.colors,
) {
    val itemWidth = if (showLabels) NavItemWidth else NavItemCollapsedWidth
    val itemHeight = if (showLabels) NavItemHeight else NavItemCollapsedWidth
    val indicator = remember { Animatable(currentPosition.toFloat()) }
    LaunchedEffect(currentPosition) { indicator.animateTo(currentPosition.toFloat(), Motion.snap()) }
    // A lens lifts off the item held, as off the floating pill
    val sources = remember(navigationItems.size) { List(navigationItems.size) { MutableInteractionSource() } }
    var held by remember { mutableIntStateOf(-1) }
    val press = remember { Animatable(0f) }
    LaunchedEffect(sources) {
        sources.forEachIndexed { index, source ->
            launch {
                source.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> {
                            held = index
                            launch { press.animateTo(1f, Motion.press()) }
                        }
                        is PressInteraction.Release, is PressInteraction.Cancel -> launch { press.animateTo(0f, Motion.release()) }
                    }
                }
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
            )
            .padding(horizontal = NavPillMargin),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .floatingGlass(NavRailShape, lensTarget = true)
                .padding(NavPillPadding),
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, (indicator.value * itemHeight.toPx()).roundToInt()) }
                    .size(width = itemWidth, height = itemHeight)
                    .background(ExtendedTheme.colors.emphasis.copy(alpha = 0.12f), NavPillShape)
            )
            Column {
                navigationItems.fastForEachIndexed { index, navigationItem ->
                    val selected = index == currentPosition
                    val title = navigationItem.title(selected)
                    // Lit as the indicator comes over it, so colour and indicator move together
                    val near = (1f - abs(indicator.value - index)).coerceIn(0f, 1f)
                    val contentColor = lerp(themeColors.textSecondary, ExtendedTheme.colors.emphasis, near)
                    val menu = navigationItem.menu
                    Column(
                        modifier = Modifier
                            .size(width = itemWidth, height = itemHeight)
                            .then(
                                if (menu == null) {
                                    Modifier.selectable(
                                        selected = selected,
                                        interactionSource = sources[index],
                                        // The lens shows the press
                                        indication = null,
                                        role = Role.Tab,
                                        onClick = { onSelect(index) },
                                    )
                                } else {
                                    Modifier
                                        .semantics {
                                            role = Role.Tab
                                            this.selected = selected
                                        }
                                        .longPressMenu(items = menu, onClick = { onSelect(index) }, indication = null, fromWhole = true, interactionSource = sources[index])
                                }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        NavItemIcon(navigationItem, selected, title, contentColor)
                        if (showLabels) {
                            Text(
                                text = title,
                                color = contentColor,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            // Drawn above the items, so it magnifies them and the rail
            val lensGrows = if (isLensGlass()) LensScale else 1f
            val lensWidth = itemWidth * lerpFloat(1f, lensGrows, press.value)
            val lensHeight = itemHeight * lerpFloat(1f, lensGrows, press.value)
            val lensOptics = remember(itemWidth) { liquidLensOptics(itemWidth * LensScale) }
            // Outside the rail's size, which it grows past: the rail would widen with it, and the
            // pages beside it would be laid out narrower on every press
            if (held >= 0) Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .offset {
                            IntOffset(
                                -((lensWidth - itemWidth) / 2).roundToPx(),
                                (held * itemHeight.toPx()).roundToInt() - ((lensHeight - itemHeight) / 2).roundToPx(),
                            )
                        }
                        .size(width = lensWidth, height = lensHeight)
                        .graphicsLayer { alpha = press.value.coerceIn(0f, 1f) }
                        .glassLens(NavPillShape, lensOptics),
                )
            }
        }
    }
}

/** An item's icon, animating to its selected form, with the item's badge on its corner. */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
private fun NavItemIcon(
    navigationItem: NavigationItem,
    selected: Boolean,
    title: String,
    tint: Color,
) {
    Box {
        if (navigationItem.avatar != null) {
            // Ringed in the selected colour when chosen, as the icons are tinted
            // The snap spring overshoots: a width going to none would dip below zero
            val ringAnimated by animateDpAsState(if (selected) AvatarRing else 0.dp, Motion.snap(), label = "ring")
            val ring = ringAnimated.coerceAtLeast(0.dp)
            OwnAvatar(
                data = navigationItem.avatar,
                contentDescription = title,
                modifier = Modifier
                    .size(24.dp)
                    .border(ring, tint, CircleShape)
                    .padding(ring * 2),
            )
        } else {
            Icon(
                painter = rememberAnimatedVectorPainter(
                    animatedImageVector = navigationItem.icon(),
                    atEnd = selected
                ),
                contentDescription = title,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        // Pops in when something new comes, shrinks away once read, still showing its last count;
        // a new count refocuses. A circle for one figure, a capsule as wide as the count needs for more
        var badgeShown by remember { mutableStateOf("") }
        if (navigationItem.badge) badgeShown = navigationItem.badgeText.orEmpty()
        AnimatedVisibility(
            visible = navigationItem.badge,
            enter = if (LocalReduceMotion.current) fadeIn(Motion.fadeIn()) else BadgeEnter,
            exit = if (LocalReduceMotion.current) fadeOut(Motion.fadeOut()) else BadgeExit,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = BadgeSize)
                    .widthIn(min = BadgeSize)
                    .background(color = ExtendedTheme.colors.emphasis, shape = CircleShape)
                    .padding(horizontal = 3.dp),
            ) {
                Refocus(targetState = badgeShown) {
                    Text(text = it, textAlign = TextAlign.Center, fontSize = 10.sp, lineHeight = 14.sp, color = White, maxLines = 1)
                }
            }
        }
    }
}

private val AvatarRing = 1.5.dp

private val BadgeSize = 14.dp
private val BadgeEnter get() = fadeIn(Motion.fadeIn()) + scaleIn(Motion.pop(), initialScale = 0.4f)
private val BadgeExit get() = fadeOut(Motion.fadeOut()) + scaleOut(Motion.dismiss(), targetScale = 0.4f)

private val NavPillShape = SmoothCornerShape.Capsule
private val NavPillPadding = 6.dp
private val NavItemWidth = 68.dp
private val NavItemCollapsedWidth = 52.dp
private val NavItemHeight = 52.dp
private val NavItemCollapsedHeight = 40.dp
internal val NavPillMargin = 12.dp

/**
 * The rail's ends, concentric with the item's capsule in from them: rounded like a capsule across
 * the rail's width, they would leave a wider gap at the ends than along the sides.
 */
private val NavRailShape = SmoothCornerShape(NavItemHeight / 2 + NavPillPadding)
private val SearchButtonGap = 8.dp

/** Height the pill and its margins take above the navigation bar, expanded. */
val FloatingNavBarHeight = NavItemHeight + NavPillPadding * 2 + NavPillMargin

/**
 * The main navigation as a pill floating over the content, above the system navigation bar.
 * Each item shows its icon and a short label; while [collapsedFraction] goes to 1 (the content
 * is being scrolled forward) the labels fade out and the pill shrinks to its icons.
 *
 * The selection indicator slides between items. While the pill is pressed the indicator lifts
 * off as a clear glass lens, larger than the pill, that magnifies the item under it; dragging
 * moves the lens with the finger and releasing selects the item it ends on. Held still on an item
 * with a menu, the lens settles back and the menu opens under the finger, which can slide onto
 * an item of it and lift to pick it.
 *
 * The bar always measures at its expanded height so content padding does not change while it
 * collapses.
 *
 * With [onSearchButtonPlaced] the bar leaves a round space beside the pill, the pill's height and
 * never collapsing away, for the search button drawn over it; its items narrow where the window
 * is too narrow for both. While [searching] the pill is left out of what glass sees, so the
 * search field over it doesn't show it.
 */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun FloatingNavBar(
    currentPosition: Int,
    onChangePosition: (position: Int) -> Unit,
    onReselected: (position: Int) -> Unit,
    navigationItems: ImmutableList<NavigationItem>,
    collapsedFraction: () -> Float,
    modifier: Modifier = Modifier,
    themeColors: ExtendedColors = ExtendedTheme.colors,
    onSearchButtonPlaced: ((boundsInRoot: Rect) -> Unit)? = null,
    searching: Boolean = false,
    onSearch: () -> Unit = {},
    swipePosition: () -> Float? = { null },
) {
    if (LocalChromeStyle.current == ChromeStyle.Classic) {
        ClassicNavBar(currentPosition, onChangePosition, onReselected, navigationItems, modifier, themeColors, onSearchButtonPlaced, onSearch)
        return
    }
    val coroutineScope = rememberCoroutineScope()
    val lens = rememberElasticPress(
        initialValue = currentPosition.toFloat(),
        valueRange = 0f..(navigationItems.size - 1).coerceAtLeast(1).toFloat(),
        pressedScale = LensScale,
        velocityScale = LensVelocityScale,
    )
    var indicatorDragged by remember { mutableStateOf(false) }
    LaunchedEffect(currentPosition) {
        if (!indicatorDragged) lens.glideTo(currentPosition.toFloat())
    }
    // How far the lens is dragged past either end, resisted; the whole pill is pulled that way
    val overdrag = remember { Animatable(0f) }
    // Presses for the pill's glass, which lights up from under them
    val touch = remember { MutableInteractionSource() }
    // While the pages are swiped, the indicator moves with them, and springs to the tab they settle on
    val shownTab by rememberUpdatedState(currentPosition)
    LaunchedEffect(Unit) {
        var swiping = false
        snapshotFlow(swipePosition).collect { position ->
            if (indicatorDragged) return@collect
            if (position != null) {
                swiping = true
                lens.snapTo(position)
            } else if (swiping) {
                swiping = false
                lens.glideTo(shownTab.toFloat())
            }
        }
    }
    val menus = LocalContextMenus.current
    val haptic = LocalHapticFeedback.current
    val items by rememberUpdatedState(navigationItems)
    // The gesture outlives recompositions, so it reads the shown tab through this
    val shown by rememberUpdatedState(currentPosition)
    var pill by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val itemBounds = remember { mutableMapOf<Int, Rect>() }
    val select by rememberUpdatedState { index: Int ->
        if (index == currentPosition) {
            onReselected(index)
        } else {
            onChangePosition(index)
        }
        navigationItems[index].onClick?.invoke()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            )
            .height(FloatingNavBarHeight),
        contentAlignment = Alignment.TopCenter,
    ) {
        val searchSpace = if (onSearchButtonPlaced != null) NavItemHeight + NavPillPadding * 2 + SearchButtonGap else 0.dp
        val fullItemWidth = minOf(
            NavItemWidth,
            (maxWidth - NavPillMargin * 2 - searchSpace - NavPillPadding * 2) / navigationItems.size,
        )
        val fraction = collapsedFraction()
        val itemWidth = lerp(fullItemWidth, NavItemCollapsedWidth, fraction)
        val itemHeight = lerp(NavItemHeight, NavItemCollapsedHeight, fraction)
        val density = LocalDensity.current
        val itemWidthPx = with(density) { itemWidth.toPx() }
        // Changes while the bar collapses, which a press during a scroll overlaps, so the
        // gesture reads it rather than restarting on it
        val gestureItemWidthPx by rememberUpdatedState(itemWidthPx)
        // The item under the indicator, so colours follow it while it is dragged.
        val highlighted = lens.value.roundToInt()
        val swellPx = with(density) { PillSwell.toPx() }
        val maxOverdragPx = with(density) { MaxOverdrag.toPx() }
        val padPx = with(density) { NavPillPadding.toPx() }
        // Where the glass is drawn from, past the pill's own top left, as it swells
        val glassShift = remember { floatArrayOf(0f, 0f) }
        // How far the pill is moved, so the gesture reads the finger where it would be unmoved
        val pillShift = remember { floatArrayOf(0f) }
        Row(verticalAlignment = Alignment.Bottom) {
            Box(
                modifier = Modifier
                    .padding(top = (NavItemHeight - itemHeight))
                    .offset {
                        val shift = overdrag.value
                        pillShift[0] = shift
                        IntOffset(shift.roundToInt(), 0)
                    },
            ) {
                Box(modifier = if (!searching) Modifier.lensTarget() else Modifier) {
                    // The pill's glass, laid out apart from what is on it, so it alone swells while
                    // pressed. By layout rather
                    // than a layer scale, so what the lens sees of it is where it is drawn.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .layout { measurable, constraints ->
                                val w = constraints.maxWidth
                                val h = constraints.maxHeight
                                val swellX = swellPx * lens.pressProgress
                                val swellY = swellX * h / w.coerceAtLeast(1)
                                val placeable = measurable.measure(
                                    Constraints.fixed((w + swellX).roundToInt(), (h + swellY).roundToInt())
                                )
                                layout(w, h) {
                                    val left = -swellX / 2
                                    val top = -swellY / 2
                                    glassShift[0] = -left
                                    glassShift[1] = -top
                                    placeable.place(left.roundToInt(), top.roundToInt())
                                }
                            }
                            .floatingGlass(NavPillShape, insideLensTarget = !searching, touchLight = touch),
                    )
                    Box(
                        modifier = Modifier
                            .padding(NavPillPadding)
                            .onGloballyPositioned { pill = it }
                            .pointerInput(navigationItems.size, menus) {
                                val last = navigationItems.size - 1
                                fun indexAt(x: Float) = (x / gestureItemWidthPx).toInt().coerceIn(0, last)
                                fun positionAt(x: Float) = (x / gestureItemWidthPx - 0.5f).coerceIn(0f, last.toFloat())
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    // The press the glass is lit from, moved along with the finger
                                    var lit: PressInteraction.Press? = null
                                    fun light(at: Offset) {
                                        val press = PressInteraction.Press(at + Offset(padPx + glassShift[0], padPx + glassShift[1]))
                                        touch.tryEmit(press)
                                        lit?.let { touch.tryEmit(PressInteraction.Release(it)) }
                                        lit = press
                                    }
                                    fun settle() {
                                        coroutineScope.launch { overdrag.animateTo(0f, OverdragSpring) }
                                        lit?.let { touch.tryEmit(PressInteraction.Release(it)) }
                                        lit = null
                                    }
                                    // Where the finger is along the pill as it would lie unmoved
                                    val downX = down.position.x + pillShift[0]
                                    try {
                                        light(down.position)
                                        lens.press()
                                        lens.glideTo(positionAt(downX).roundToInt().toFloat())
                                        var dragging = false
                                        var lastX = downX
                                        var released = false
                                        var releasedAt = 0L
                                        var lost = false
                                        fun follow(event: PointerEvent) {
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            if (change == null) {
                                                lost = true
                                                return
                                            }
                                            if (!change.pressed) {
                                                released = true
                                                releasedAt = change.uptimeMillis
                                                return
                                            }
                                            val dx = change.position.x + pillShift[0] - lastX
                                            lastX += dx
                                            light(change.position)
                                            if (!dragging &&
                                                abs(lastX - downX) > viewConfiguration.touchSlop
                                            ) {
                                                dragging = true
                                                indicatorDragged = true
                                            }
                                            if (dragging) {
                                                change.consume()
                                                lens.follow(positionAt(lastX))
                                                // Past the first or last item, pulled after it as by a
                                                // rubber band: the further, the harder
                                                val reach = lastX / gestureItemWidthPx - 0.5f
                                                val pastPx = (reach - reach.coerceIn(0f, last.toFloat())) * gestureItemWidthPx
                                                val resisted = sign(pastPx) * maxOverdragPx * (1f - 1f / (abs(pastPx) * OverdragGive / maxOverdragPx + 1f))
                                                coroutineScope.launch { overdrag.snapTo(resisted) }
                                            }
                                        }
                                        val menu = items[indexAt(downX)].menu
                                        // Until the finger moves off or lifts, a menu waits for the press to last
                                        val held = menu != null && menus != null &&
                                            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                                while (!dragging && !released && !lost) follow(awaitPointerEvent())
                                            } == null
                                        if (held && menu != null && menus != null) {
                                            lens.glideTo(shown.toFloat())
                                            lens.release()
                                            settle()
                                            val coordinates = pill ?: return@awaitEachGesture
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            // Out of the item held, as from the rail
                                            val item = itemBounds[indexAt(down.position.x)] ?: Rect(coordinates.localToWindow(down.position), Size.Zero)
                                            menus.show(menu(), anchor = item)
                                            while (true) {
                                                val change = awaitPointerEvent().changes
                                                    .firstOrNull { it.id == down.id } ?: break
                                                change.consume()
                                                val position = coordinates.localToWindow(change.position)
                                                if (!change.pressed) {
                                                    menus.liftFinger(position)
                                                    break
                                                }
                                                menus.moveFinger(position)
                                            }
                                            return@awaitEachGesture
                                        }
                                        while (!released && !lost) follow(awaitPointerEvent())
                                        indicatorDragged = false
                                        settle()
                                        if (released) {
                                            val target = if (dragging) {
                                                positionAt(lastX).roundToInt()
                                            } else {
                                                indexAt(downX)
                                            }
                                            // Carried there lifted, settling only as it arrives
                                            lens.moveTo(target.toFloat())
                                            // Only a tap on the shown tab asks it again; a lens dragged
                                            // or held there and let go just goes back
                                            val tapped = !dragging &&
                                                releasedAt - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                                            if (target != shown || tapped) select(target)
                                        } else {
                                            lens.glideTo(shown.toFloat())
                                            lens.release()
                                        }
                                    } catch (e: CancellationException) {
                                        // Restarted or taken off mid-press, the gesture would leave the lens up
                                        lens.glideTo(shown.toFloat())
                                        lens.release()
                                        settle()
                                        indicatorDragged = false
                                        throw e
                                    }
                                }
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset((lens.value * itemWidthPx).roundToInt(), 0) }
                                .size(width = itemWidth, height = itemHeight)
                                .graphicsLayer { alpha = 1f - lens.pressProgress }
                                .background(ExtendedTheme.colors.emphasis.copy(alpha = 0.12f), NavPillShape)
                        )
                        Row {
                            navigationItems.fastForEachIndexed { index, navigationItem ->
                                val selected = index == currentPosition
                                val title = navigationItem.title(selected)
                                val contentColor =
                                    if (index == highlighted) ExtendedTheme.colors.emphasis
                                    else themeColors.textSecondary
                                Column(
                                    modifier = Modifier
                                        .size(width = itemWidth, height = itemHeight)
                                        .onGloballyPositioned { itemBounds[index] = it.boundsInWindow() }
                                        .semantics(mergeDescendants = true) {
                                            role = Role.Tab
                                            this.selected = selected
                                            onClick(label = title) {
                                                select(index)
                                                true
                                            }
                                            val menu = navigationItem.menu
                                            if (menu != null && menus != null) {
                                                onLongClick(label = navigationItem.menuLabel) {
                                                    val bounds = itemBounds[index] ?: return@onLongClick false
                                                    menus.show(menu(), anchor = bounds)
                                                    true
                                                }
                                            }
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    NavItemIcon(navigationItem, selected, title, contentColor)
                                    if (fraction < 1f) {
                                        Text(
                                            text = title,
                                            color = contentColor,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp,
                                            maxLines = 1,
                                            modifier = Modifier
                                                .height(14.dp * (1f - fraction))
                                                .graphicsLayer { alpha = 1f - fraction * 2f },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // Lifts off the pill while it is pressed: drawn above it, so it magnifies the pill too.
                // Grows by layout rather than a layer scale so the glass samples the area it covers,
                // but outside the pill's size, which would otherwise grow with it and push the
                // search button down. It swells a little apart on each axis, so it wobbles, and
                // is drawn out along the way it moves when moved fast. A veil, without glass to
                // lift, keeps to the item
                val lensGlass = isLensGlass()
                val lensWidth = itemWidth * if (lensGlass) lens.scaleX * lens.stretchX else 1f
                val lensHeight = itemHeight * if (lensGlass) lens.scaleY * lens.stretchY else 1f
                val lensOptics = remember(itemHeight) { liquidLensOptics(itemHeight * LensScale) }
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .offset {
                                val padding = NavPillPadding.roundToPx()
                                val grownX = ((lensWidth - itemWidth) / 2).roundToPx()
                                val grownY = ((lensHeight - itemHeight) / 2).roundToPx()
                                IntOffset(
                                    (lens.value * itemWidthPx).roundToInt() + padding - grownX,
                                    padding - grownY,
                                )
                            }
                            .size(width = lensWidth, height = lensHeight)
                            .graphicsLayer { alpha = lens.pressProgress }
                            .glassLens(NavPillShape, lensOptics)
                    )
                }
            }
            if (onSearchButtonPlaced != null) {
                Spacer(modifier = Modifier.width(SearchButtonGap))
                Box(
                    modifier = Modifier
                        .size(itemHeight + NavPillPadding * 2)
                        .onGloballyPositioned { onSearchButtonPlaced(it.boundsInRoot()) }
                )
            }
        }
    }
}

/**
 * The main navigation as a classic bar: a band along the bottom of the window, above and behind
 * the system navigation bar, its items sharing it, the chosen one's icon on a pill. With
 * [onSearchButtonPlaced], search is the last item, and the field grows out of its pill.
 */
@Composable
private fun ClassicNavBar(
    currentPosition: Int,
    onChangePosition: (position: Int) -> Unit,
    onReselected: (position: Int) -> Unit,
    navigationItems: ImmutableList<NavigationItem>,
    modifier: Modifier,
    themeColors: ExtendedColors,
    onSearchButtonPlaced: ((boundsInRoot: Rect) -> Unit)?,
    onSearch: () -> Unit,
) {
    val menus = LocalContextMenus.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .classicBarGlass(top = false)
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            )
            .height(ClassicNavBarHeight)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigationItems.fastForEachIndexed { index, item ->
            val selected = index == currentPosition
            val title = item.title(selected)
            var bounds by remember { mutableStateOf(Rect.Zero) }
            val menu = item.menu
            ClassicNavItem(
                selected = selected,
                title = title,
                color = if (selected) ExtendedTheme.colors.emphasis else themeColors.textSecondary,
                onClick = {
                    if (selected) onReselected(index) else onChangePosition(index)
                    item.onClick?.invoke()
                },
                onLongClick = if (menu != null && menus != null) {
                    { menus.show(menu(), anchor = bounds) }
                } else null,
                modifier = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
            ) { tint -> NavItemIcon(item, selected, title, tint) }
        }
        if (onSearchButtonPlaced != null) {
            val title = stringResource(id = R.string.title_search)
            ClassicNavItem(
                selected = false,
                title = title,
                color = themeColors.textSecondary,
                onClick = onSearch,
                onLongClick = null,
                pillModifier = Modifier.onGloballyPositioned { onSearchButtonPlaced(it.boundsInRoot()) },
            ) { tint -> Icon(imageVector = Symbols.Search, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.ClassicNavItem(
    selected: Boolean,
    title: String,
    color: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    pillModifier: Modifier = Modifier,
    icon: @Composable (tint: Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pill by animateFloatAsState(if (selected) 1f else 0f, Motion.snap(), label = "classicPill")
    val emphasis = ExtendedTheme.colors.emphasis
    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClickLabel = title,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .then(pillModifier)
                .pressScale(interactionSource)
                .size(width = ClassicPillWidth, height = ClassicPillHeight)
                .drawBehind { drawRoundRect(emphasis.copy(alpha = 0.12f * pill), cornerRadius = CornerRadius(size.height / 2)) }
                // The bar is part of the page: its item is pressed in, as anything on it is
                .indication(interactionSource, PressFeedback.overlay(Corners.full)),
            contentAlignment = Alignment.Center,
        ) { icon(color) }
        Text(text = title, color = color, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
    }
}

private val ClassicNavBarHeight = 64.dp
private val ClassicPillWidth = 56.dp
private val ClassicPillHeight = 30.dp

/** How much the lens outgrows the item it lifts off from. */
private const val LensScale = 1.4f

/** Tab ranges a second at which the lens is drawn out the most. */
private const val LensVelocityScale = 10f

/** How much wider the pill's glass swells while pressed; it grows as much taller in proportion. */
private val PillSwell = 6.dp

/**
 * How the whole pill is pulled after a lens dragged past either end: never past [MaxOverdrag],
 * giving [OverdragGive] of the finger's move at first and less the further it goes, and springing
 * back with a little bounce.
 */
private val MaxOverdrag = 48.dp
private const val OverdragGive = 0.3f
private val OverdragSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 380f)

@Immutable
data class NavigationItem @OptIn(ExperimentalAnimationGraphicsApi::class) constructor(
    val id: String,
    val icon: @Composable () -> AnimatedImageVector,
    val title: @Composable (selected: Boolean) -> String,
    val badge: Boolean = false,
    val badgeText: String? = null,
    val onClick: (() -> Unit)? = null,
    /** Shown in place of the icon, such as the signed-in account's avatar. */
    val avatar: String? = null,
    /** What a long press on it offers, labelled [menuLabel] for accessibility; null for nothing. */
    val menu: (() -> List<ContextMenuItem>)? = null,
    val menuLabel: String? = null,
)
