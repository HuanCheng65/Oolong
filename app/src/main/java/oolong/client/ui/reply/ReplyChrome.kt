package oolong.client.ui.reply

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.rememberSettings
import oolong.client.ui.designsystem.LocalPressedByGlass
import oolong.client.ui.designsystem.MyBackHandler
import oolong.client.ui.designsystem.floatingGlass
import oolong.client.ui.designsystem.isGlassAvailable
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.SmoothCornerShape
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which of the bottom chrome's surfaces is open: none, the reply composer, or the menu. */
enum class ChromeSurface { None, Composer, Menu }

/** What the bottom chrome shows, held by the page so its events can open the composer. */
@Stable
class ReplyChromeState {
    var surface by mutableStateOf(ChromeSurface.None)
    var fullScreen by mutableStateOf(false)
    var replyTo by mutableStateOf<ReplyTo?>(null)

    fun openComposer(replyTo: ReplyTo?) {
        this.replyTo = replyTo
        fullScreen = false
        surface = ChromeSurface.Composer
    }

    fun openMenu() {
        surface = ChromeSurface.Menu
    }

    fun close() {
        surface = ChromeSurface.None
        fullScreen = false
    }
}

@Composable
fun rememberReplyChromeState(): ReplyChromeState = remember { ReplyChromeState() }

/** Height of each capsule at rest. */
val ReplyCapsuleHeight = 52.dp
private val CapsuleShape = SmoothCornerShape.Capsule
private val SideMargin = 16.dp
private val BottomMargin = 8.dp
private val ComposerMargin = 8.dp
private val ComposerRadius = 24.dp

/** The space the capsules take above the navigation bar, for a scaffold's bottom bar slot. */
val ReplyChromeBarHeight = ReplyCapsuleHeight + BottomMargin * 2

/**
 * The bottom chrome of a page one can reply on, drawn over the page: a reply capsule that opens
 * into the composer and, beside it, an actions capsule that may open into a menu.
 *
 * Each open surface is its capsule's glass growing in place, the way a context menu grows out of
 * its button: the glass swells from the capsule to the surface on one spring, with what the
 * capsule showed blurring away inside it while the surface's content, small and blurred at
 * first, grows with it and sharpens at the end. Closing runs it back. The other capsule
 * dissolves where it is.
 *
 * @param replyCapsule the reply capsule's content at rest, or null when the page offers no reply
 * @param actions the actions capsule's content at rest, or null for none
 * @param menu what the menu holds above its footer, or null when [actions] opens no menu
 * @param menuFooter the menu's bottom row, which sits where [actions] was
 * @param composer the composer's content, given the drag handle to put at its top and whether it
 *   fills a height set from outside, as when full screen or being dragged
 * @param panel what shows in place of the keyboard, such as the emoticons
 * @param panelShown whether [panel] shows instead of the keyboard
 * @param hiddenFraction how far the capsules have slid away for scrolling, 0 to 1
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplyChrome(
    state: ReplyChromeState,
    replyCapsule: (@Composable RowScope.() -> Unit)?,
    actions: (@Composable RowScope.() -> Unit)?,
    menu: (@Composable ColumnScope.() -> Unit)?,
    menuFooter: @Composable RowScope.() -> Unit,
    composer: @Composable (handle: @Composable () -> Unit, fill: Boolean) -> Unit,
    panel: @Composable () -> Unit,
    panelShown: Boolean,
    onOpenComposer: () -> Unit,
    onDismiss: () -> Unit,
    onExitFullScreen: () -> Unit,
    onEnterFullScreen: () -> Unit,
    hiddenFraction: () -> Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val colors = ExtendedTheme.colors
    val scope = rememberCoroutineScope()
    val surface = state.surface
    val composing = surface == ChromeSurface.Composer
    val menuOpen = surface == ChromeSurface.Menu
    val full = composing && state.fullScreen
    // With motion reduced, what the glass swallows and brings out only fades, without swelling or blurring
    val reduceMotion = LocalReduceMotion.current

    MyBackHandler(enabled = surface != ChromeSurface.None) {
        if (full) onExitFullScreen() else onDismiss()
    }

    var chrome by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(modifier = modifier.fillMaxSize().onGloballyPositioned { chrome = it }) {
        val maxHeightPx = constraints.maxHeight.toFloat()
        val bars = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
        val navBottom = bars.getBottom(density)
        val statusTop = WindowInsets.statusBars.union(WindowInsets.captionBar).getTop(density)
        val imeBottom = WindowInsets.ime.getBottom(density)
        val imeTarget = WindowInsets.imeAnimationTarget.getBottom(density)
        val rememberedImeHeight = rememberImeHeight(imeBottom, imeTarget)

        // How far a finger has pulled the composer down past its resting height, and what that
        // moves: the keyboard, the panel in its place, or with neither up the composer itself
        val lowered = remember { Animatable(0f) }
        val view = LocalView.current
        val keyboardDrag = remember(view) { KeyboardDrag(view) }

        // Leaving a panel for the keyboard, the panel holds its place until the keyboard is all the
        // way up, so the composer does not drop with the panel and then rise with the keyboard
        // Decided in the same frame the panel goes, as a frame without it would let the composer drop.
        // The keyboard is asked for at that moment but reports its height only once it moves, so
        // the panel holds until the keyboard is up, or briefly when no keyboard comes
        var holdingPanel by remember { mutableStateOf(false) }
        if (panelShown) {
            if (!holdingPanel) holdingPanel = true
        } else if (holdingPanel && imeTarget > 0 && imeBottom >= imeTarget) {
            holdingPanel = false
        }
        val currentImeTarget by rememberUpdatedState(imeTarget)
        LaunchedEffect(panelShown) {
            if (!panelShown) {
                delay(KeyboardWaitMillis)
                if (currentImeTarget == 0) holdingPanel = false
            }
        }
        // Closed with the panel up, the panel slides down the way the keyboard would, with the
        // composer on it going back into its capsule, instead of vanishing in one frame
        val panelHeld = composing && (panelShown || holdingPanel)
        var panelLeaving by remember { mutableStateOf(false) }
        var panelWasHeld by remember { mutableStateOf(false) }
        if (panelWasHeld && !composing && !panelLeaving) panelLeaving = true
        panelWasHeld = panelHeld
        LaunchedEffect(panelLeaving) {
            if (!panelLeaving) return@LaunchedEffect
            try {
                lowered.animateTo(rememberedImeHeight.toFloat(), Motion.dismiss())
            } finally {
                panelLeaving = false
                lowered.snapTo(0f)
            }
        }
        val panelUp = panelHeld || panelLeaving
        // The composer sits on the keyboard, following it frame by frame as it slides, or on the
        // panel that takes its place, which is as tall as the keyboard last was
        val keyboardTop = when {
            panelUp -> maxOf((rememberedImeHeight - lowered.value).roundToInt(), imeBottom, navBottom)
            else -> maxOf(imeBottom, navBottom)
        }
        // From floating over the navigation bar to resting on the keyboard as one piece with it,
        // over the first stretch of the keyboard's way up
        val keyboardDock = if (composing) ((keyboardTop - navBottom) / with(density) { DockDistance.toPx() }).coerceIn(0f, 1f) else 0f

        // Where things are, in this box's pixels: the capsules at rest and the open surfaces
        var replyCapsuleBounds by remember { mutableStateOf<Rect?>(null) }
        var actionsBounds by remember { mutableStateOf<Rect?>(null) }
        var composerBounds by remember { mutableStateOf<Rect?>(null) }
        var menuBounds by remember { mutableStateOf<Rect?>(null) }

        // How far each surface has grown out of its capsule. The composer's glass is drawn
        // between the capsule and wherever the composer is now, so the keyboard moving the
        // composer never restarts the spring. The menu also travels: a move ahead of the grow.
        val open = remember { Animatable(0f, Motion.ProgressThreshold) }
        val grow = remember { Animatable(0f, Motion.ProgressThreshold) }
        val move = remember { Animatable(0f, Motion.ProgressThreshold) }
        LaunchedEffect(composing) {
            if (composing) {
                lowered.snapTo(0f)
                open.animateTo(1f, Motion.morph(Motion.ProgressThreshold))
            } else {
                open.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold))
                // A panel going down lowers itself and puts this back when it is gone
                if (!panelLeaving) lowered.snapTo(0f)
            }
        }
        LaunchedEffect(menuOpen) {
            if (menuOpen) {
                launch { move.animateTo(1f, Motion.morph(Motion.ProgressThreshold)) }
                delay(GrowDelay)
                grow.animateTo(1f, Motion.morph(Motion.ProgressThreshold))
            } else {
                // Shrinks first, then flies home
                val shrink = launch { grow.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold)) }
                delay(GrowDelay)
                move.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold))
                shrink.join()
            }
        }
        val composerShown by remember { derivedStateOf { open.value > 0f } }
        val menuShown by remember { derivedStateOf { grow.value > 0f || move.value > 0f } }

        val replyGlass: () -> Rect = {
            val capsule = replyCapsuleBounds ?: Rect.Zero
            lerpRect(capsule, composerBounds ?: capsule, open.value)
        }
        val menuGlass: () -> Rect = {
            val capsule = actionsBounds ?: Rect.Zero
            val bounds = menuBounds ?: capsule
            val center = lerp(capsule.center, bounds.center, move.value)
            val size = Size(lerp(capsule.width, bounds.width, grow.value), lerp(capsule.height, bounds.height, grow.value))
            Rect(center.x - size.width / 2, center.y - size.height / 2, center.x + size.width / 2, center.y + size.height / 2)
        }

        // Going full screen and back, the composer grows and shrinks on a spring; the glass,
        // following it exactly, goes with it
        val fullProgress = remember { Animatable(0f, Motion.ProgressThreshold) }
        // A release of the handle starts the change at the finger's speed; a tap from rest
        var fullVelocity by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(full) {
            fullProgress.animateTo(if (full) 1f else 0f, Motion.snap(Motion.ProgressThreshold), fullVelocity)
            fullVelocity = 0f
        }
        // Full screen, it stays one piece with the bottom of the window when the keyboard goes down,
        // rather than floating off it as a card
        val dock = if (composing) maxOf(keyboardDock, fullProgress.value.coerceIn(0f, 1f)) else 0f

        // Behind a full-screen composer the page dims; tapping it goes back to the compact one.
        // Menus take no scrim.
        if (composerShown && fullProgress.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = fullProgress.value.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = if (colors.isNightMode) 0.5f else 0.28f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onExitFullScreen,
                    )
            )
        }

        // Tapping the page beside an open surface puts it away, keeping the draft
        if ((composing && !full) || menuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
            )
        }

        // The capsules at rest. The one that opened hides while its glass is out; the other
        // dissolves in place.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(bars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = SideMargin, vertical = BottomMargin)
                // Moved by placing; under the app's lookahead, only a layer of its own makes the
                // move show. Slid all the way off, they are not placed, so their glass stops
                // sampling the page.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        val hidden = hiddenFraction()
                        if (hidden < 1f) placeable.place(0, ((ReplyChromeBarHeight + 24.dp).toPx() * hidden).roundToInt())
                    }
                }
                .graphicsLayer(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (replyCapsule != null) {
                // Its glass is the composer's while that is out, and fades where it is while the
                // menu grows; what it shows dissolves in a layer of its own, as a layer's scale on
                // the glass would leave it sampling at that scale once back at rest
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(ReplyCapsuleHeight)
                        .onGloballyPositioned { replyCapsuleBounds = it.boundsIn(chrome) }
                        .then(
                            if (!composing && open.value == 0f) {
                                Modifier.floatingGlass(
                                    CapsuleShape,
                                    pressable = true,
                                    alpha = 1f - dissolveOf(grow.value),
                                )
                            } else {
                                Modifier
                            }
                        )
                        // The glass answers the press
                        .clickable(interactionSource = null, indication = null, onClick = onOpenComposer),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (open.value > 0f) {
                                    alpha = 0f
                                } else {
                                    dissolve(dissolveOf(grow.value), TransformOrigin(0f, 0.5f), reduceMotion)
                                }
                            }
                            .padding(start = 10.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        content = replyCapsule,
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f))
            }
            if (actions != null) {
                // The same for the actions: their glass is the menu's while that is out
                Box(
                    modifier = Modifier
                        .height(ReplyCapsuleHeight)
                        .onGloballyPositioned { actionsBounds = it.boundsIn(chrome) }
                        .then(
                            if (!menuOpen && grow.value == 0f && move.value == 0f) {
                                Modifier.floatingGlass(
                                    CapsuleShape,
                                    pressable = true,
                                    alpha = 1f - dissolveOf(open.value),
                                )
                            } else {
                                Modifier
                            }
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .graphicsLayer {
                                if (grow.value > 0f || move.value > 0f) {
                                    alpha = 0f
                                } else {
                                    dissolve(dissolveOf(open.value), TransformOrigin(1f, 0.5f), reduceMotion)
                                }
                            }
                            .fillMaxHeight()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Its glass answers presses on them as one piece
                        CompositionLocalProvider(LocalPressedByGlass provides true) { actions() }
                    }
                }
            }
        }

        // The composer
        if (composerShown || composing) {
            val capsuleRadius = ReplyCapsuleHeight / 2
            val topRadius = ComposerRadius * (1f - fullProgress.value.coerceIn(0f, 1f))
            val bottomRadius = ComposerRadius * (1f - dock)
            val progress = open.value.coerceIn(0f, 1f)
            val glassTop = androidx.compose.ui.unit.lerp(capsuleRadius, topRadius, progress)
            val glassBottom = androidx.compose.ui.unit.lerp(capsuleRadius, bottomRadius, progress)
            MorphingGlass(
                bounds = replyGlass(),
                top = glassTop,
                bottom = glassBottom,
                opacity = maxOf(dock, fullProgress.value).coerceIn(0f, 1f) * progress,
            )
            // What the capsule showed, swallowed by the glass from its bottom left
            if (replyCapsule != null) {
                val capsule = replyCapsuleBounds
                if (capsule != null) {
                    Ghost(
                        size = capsule.size,
                        modifier = Modifier.graphicsLayer {
                            val glass = replyGlass()
                            translationX = glass.left
                            translationY = glass.bottom - capsule.height
                            swallowed(open.value, TransformOrigin(0f, 1f), reduceMotion)
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            content = replyCapsule,
                        )
                    }
                }
            }

            var dragHeight by remember { mutableStateOf<Float?>(null) }
            // How tall the composer is when neither dragged nor full, the point drags are judged from
            var compactHeight by remember { mutableFloatStateOf(0f) }
            val marginPx = with(density) { ComposerMargin.toPx() } * (1f - dock)
            val bottom = keyboardTop + marginPx
            val fullHeight = maxHeightPx - statusTop - keyboardTop
            // How the pull is shared out: above the compact height it grows the composer, below it
            // it lowers what the composer rests on
            val keyboardUp = imeBottom > navBottom && !panelUp
            var pull by remember { mutableFloatStateOf(0f) }
            fun pullTo(value: Float) {
                pull = value
                if (value >= 0f) {
                    dragHeight = (compactHeight + value).coerceAtMost(fullHeight)
                    if (lowered.value != 0f) scope.launch { lowered.snapTo(0f) }
                    if (keyboardDrag.controlled) keyboardDrag.lower(0f)
                } else {
                    dragHeight = null
                    if (keyboardUp || keyboardDrag.controlled) {
                        keyboardDrag.start()
                        keyboardDrag.lower(-value)
                    } else {
                        scope.launch { lowered.snapTo(-value) }
                    }
                }
            }
            val dragState = rememberDraggableState { delta -> pullTo(pull - delta) }
            val closeDistance = with(density) { CloseDistance.toPx() }
            val flingVelocity = with(density) { FlingVelocity.toPx() }
            val handle: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Grows with the composer going full screen rather than when it gets there;
                        // read as it is laid out
                        .layout { measurable, constraints ->
                            val f = fullProgress.value.coerceIn(0f, 1f)
                            val height = (HandleCompact + (HandleFull - HandleCompact) * f).roundToPx()
                            val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                            layout(placeable.width, height) { placeable.place(0, 0) }
                        }
                        // Tapping the handle goes to full screen and back; dragging it resizes,
                        // and down past the compact size closes
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = stringResource(
                                id = if (full) R.string.reply_exit_full_screen else R.string.reply_full_screen
                            ),
                        ) { if (full) onExitFullScreen() else onEnterFullScreen() }
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStarted = {
                                val height = composerBounds?.height ?: compactHeight
                                pull = height - compactHeight
                            },
                            onDragStopped = { velocity ->
                                val released = pull
                                pull = 0f
                                if (released < 0f) {
                                    val close = -released > closeDistance || velocity > flingVelocity
                                    when {
                                        keyboardDrag.controlled -> {
                                            keyboardDrag.release(show = !close, velocity = velocity)
                                            if (close) onDismiss()
                                        }
                                        close && panelUp -> {
                                            lowered.animateTo(rememberedImeHeight.toFloat(), Motion.dismiss(), velocity)
                                            onDismiss()
                                        }
                                        // The composer goes back into its capsule from where the finger left it
                                        close -> onDismiss()
                                        else -> {
                                            keyboardDrag.release(show = true, velocity = velocity)
                                            lowered.animateTo(0f, Motion.settle(), -velocity)
                                        }
                                    }
                                } else {
                                    val height = dragHeight ?: compactHeight
                                    val span = (fullHeight - compactHeight).coerceAtLeast(1f)
                                    val fraction = ((height - compactHeight) / span).coerceIn(0f, 1f)
                                    val goFull = velocity < -flingVelocity ||
                                        (abs(velocity) < flingVelocity && fraction > FullThreshold)
                                    fullProgress.snapTo(fraction)
                                    dragHeight = null
                                    fullVelocity = -velocity / span
                                    if (goFull == full) {
                                        fullProgress.animateTo(if (goFull) 1f else 0f, Motion.snap(Motion.ProgressThreshold), fullVelocity)
                                        fullVelocity = 0f
                                    } else if (goFull) {
                                        onEnterFullScreen()
                                    } else {
                                        onExitFullScreen()
                                    }
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .background(colors.textDisabled.copy(alpha = 0.5f), CapsuleShape)
                    )
                }
            }
            val fullNow = fullProgress.value
            val heightPx = when {
                dragHeight != null -> dragHeight
                fullNow > 0f && compactHeight > 0f -> (compactHeight + (fullHeight - compactHeight) * fullNow).coerceAtLeast(0f)
                else -> null
            }
            // Pulled down with nothing under it, the composer itself moves
            val ownDrop = if (!panelUp && !keyboardUp && !keyboardDrag.controlled) lowered.value else 0f
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, (-bottom + ownDrop).roundToInt()) }
                    .windowInsetsPadding(bars.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = with(density) { marginPx.toDp() })
                    .fillMaxWidth()
                    .then(if (heightPx != null) Modifier.height(with(density) { heightPx.toDp() }) else Modifier)
                    .onGloballyPositioned {
                        val bounds = it.boundsIn(chrome)
                        composerBounds = bounds
                        if (composing && fullProgress.value == 0f && dragHeight == null) compactHeight = bounds.height
                    }
                    // At its own size, scaled into the glass from its bottom left and clipped by it
                    .graphicsLayer {
                        val own = composerBounds
                        if (own == null) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        val glass = replyGlass()
                        val scale = if (own.width > 0f) glass.width / own.width else 1f
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 1f)
                        translationX = glass.left - own.left
                        translationY = glass.bottom - own.bottom
                        shape = GlassCut(
                            top = (own.height - glass.height / scale).coerceAtLeast(0f),
                            topRadius = glassTop.toPx() / scale,
                            bottomRadius = glassBottom.toPx() / scale,
                        )
                        clip = true
                        condensed(open.value, reduceMotion)
                    }
                    // Full screen, its own ground: the glass is placed from where the composer was
                    // a frame before, which as the keyboard slides would leave its bottom bare
                    .drawBehind { if (fullNow > 0f) drawRect(colors.sheet.copy(alpha = fullNow.coerceIn(0f, 1f))) },
            ) {
                composer(handle, heightPx != null)
            }
        }

        // The menu
        if (menu != null && (menuShown || menuOpen)) {
            val capsule = actionsBounds
            MorphingGlass(bounds = menuGlass(), top = ReplyCapsuleHeight / 2, bottom = ReplyCapsuleHeight / 2, opacity = 0f)
            // The capsule's buttons, swallowed by the glass from its bottom right, where the
            // footer takes their place
            if (actions != null && capsule != null) {
                Ghost(
                    size = capsule.size,
                    modifier = Modifier.graphicsLayer {
                        val glass = menuGlass()
                        translationX = glass.right - capsule.width
                        translationY = glass.bottom - capsule.height
                        swallowed(grow.value, TransformOrigin(1f, 1f), reduceMotion)
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            }
        }
        if (menu != null) {
            // Composed and laid out while it is closed too, only not placed, so opening it doesn't
            // begin with a frame that builds all of it
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            if (state.surface == ChromeSurface.Menu || grow.value > 0f || move.value > 0f) placeable.place(0, 0)
                        }
                    }
                    .windowInsetsPadding(bars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = SideMargin, vertical = BottomMargin)
                    .width(MenuWidth.coerceAtMost(maxWidth - SideMargin * 2))
                    .onGloballyPositioned { menuBounds = it.boundsIn(chrome) }
                    // At its own size, scaled into the glass from its bottom right and clipped by it
                    .graphicsLayer {
                        val own = menuBounds
                        if (own == null) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        val glass = menuGlass()
                        val scale = if (own.width > 0f) glass.width / own.width else 1f
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(1f, 1f)
                        translationX = glass.right - own.right
                        translationY = glass.bottom - own.bottom
                        val radius = (ReplyCapsuleHeight / 2).toPx() / scale
                        shape = GlassCut(
                            top = (own.height - glass.height / scale).coerceAtLeast(0f),
                            topRadius = radius,
                            bottomRadius = radius,
                        )
                        clip = true
                        condensed(grow.value, reduceMotion)
                    },
            ) {
                menu()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ReplyCapsuleHeight)
                        .padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = menuFooter,
                )
            }
        }

        // Full screen, the composer reaches down over the navigation bar, and under the keyboard,
        // which as it slides is drawn a frame apart from where it says it is
        if (composerShown && !panelUp && fullProgress.value > 0f && keyboardTop > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(with(density) { keyboardTop.toDp() })
                    .graphicsLayer { alpha = (fullProgress.value * open.value).coerceIn(0f, 1f) }
                    .background(colors.sheet),
            )
        }

        // In place of the keyboard
        if (panelUp) {
            val shown = remember { Animatable(0f, Motion.ProgressThreshold) }
            LaunchedEffect(panelShown) {
                if (panelShown) {
                    shown.snapTo(0f)
                    shown.animateTo(1f, Motion.snap(Motion.ProgressThreshold))
                }
            }
            val fadeSpan = with(density) { PanelFadeSpan.toPx() }
            // Lowered, it slides down whole rather than growing shorter, so its content keeps its layout
            val panelHeight = maxOf(keyboardTop, rememberedImeHeight)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(with(density) { panelHeight.toDp() })
                    .graphicsLayer {
                        translationY = (panelHeight - keyboardTop).toFloat()
                        // Held for the keyboard, it fades as the keyboard arrives under it
                        if (!panelShown && imeTarget > 0) {
                            alpha = (1f - (imeBottom - (imeTarget - fadeSpan)) / fadeSpan).coerceIn(0f, 1f)
                        }
                    }
                    .background(colors.sheet)
                    .windowInsetsPadding(bars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        val s = shown.value
                        alpha = (s / 0.6f).coerceIn(0f, 1f)
                        val scale = if (reduceMotion) 1f else lerp(0.94f, 1f, s)
                        scaleX = scale
                        scaleY = scale
                        if (!reduceMotion) renderEffect = blurEffect(PanelBlur.toPx() * (1f - s).coerceIn(0f, 1f))
                    },
                ) {
                    panel()
                }
            }
        }
    }
}

/** How long a panel waits for a keyboard asked for in its place before it gives way anyway. */
private const val KeyboardWaitMillis = 600L

/** How long the menu's glass travels before it starts to grow. */
private const val GrowDelay = 40L

/** The menu's width on a wide window; on a phone it spans the window. */
private val MenuWidth = 420.dp

/** How far the keyboard rises before the composer rests on it as one piece. */
private val DockDistance = 40.dp

/** How far below its compact size the composer is pulled before letting go closes it. */
private val CloseDistance = 64.dp

/** A release at least this fast closes, opens full screen or leaves it, whatever the distance. */
private val FlingVelocity = 700.dp

/** How much of the way to full screen a release must have come to go on there. */
private const val FullThreshold = 0.4f

/** The last stretch of the keyboard's way up, over which a panel held for it fades. */
private val PanelFadeSpan = 60.dp

/** How blurred what a capsule showed gets as its glass swallows it, and a surface's content before it sharpens. */
private val SwallowBlur = 20.dp

private val PanelBlur = 12.dp

private val DissolveBlur = 6.dp

/** How much of a surface's growth it takes for the other capsule to dissolve. */
private fun dissolveOf(progress: Float) = (progress * 1.4f).coerceIn(0f, 1f)

/** The other capsule, dissolving in place [by] 0 to 1; with motion [reduced], only fading. */
private fun GraphicsLayerScope.dissolve(by: Float, origin: TransformOrigin, reduced: Boolean) {
    if (by == 0f) return
    alpha = 1f - by * 0.999f
    if (reduced) return
    val scale = 1f - 0.25f * by
    scaleX = scale
    scaleY = scale
    transformOrigin = origin
    renderEffect = blurEffect(DissolveBlur.toPx() * by)
}

/** What a capsule showed, as its glass grows [progress] of the way: swelling, blurring away; with motion [reduced], only fading. */
private fun GraphicsLayerScope.swallowed(progress: Float, origin: TransformOrigin, reduced: Boolean) {
    val fade = ((progress - 0.05f) / 0.3f).coerceIn(0f, 1f)
    if (reduced) {
        alpha = 1f - fade
        return
    }
    val scale = lerp(1f, 1.5f, progress.coerceIn(0f, 1f))
    scaleX = scale
    scaleY = scale
    transformOrigin = origin
    alpha = 1f - fade
    renderEffect = blurEffect(SwallowBlur.toPx() * fade)
}

/** A surface's content as its glass grows [progress] of the way: in after a fifth, sharp near the end; with motion [reduced], only fading in. */
private fun GraphicsLayerScope.condensed(progress: Float, reduced: Boolean) {
    alpha = ((progress - 0.2f) / 0.3f).coerceIn(0f, 1f)
    if (reduced) return
    renderEffect = blurEffect(SwallowBlur.toPx() * (1f - ((progress - 0.45f) / 0.4f).coerceIn(0f, 1f)))
}

/** A copy of a capsule's content, only to be seen: it takes no touches and says nothing. */
@Composable
private fun Ghost(size: Size, modifier: Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
            .clearAndSetSemantics {}
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
    ) {
        content()
    }
}

@Composable
private fun MorphingGlass(bounds: Rect, top: Dp, bottom: Dp, opacity: Float) {
    val density = LocalDensity.current
    val colors = ExtendedTheme.colors
    val glass = isGlassAvailable()
    if (bounds.isEmpty) return
    val shape = SmoothCornerShape(CornerSize(top), CornerSize(top), CornerSize(bottom), CornerSize(bottom))
    Box(
        modifier = Modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
            // Grown into a surface, frosted or opaque chrome reads as thicker: a deeper shadow under
            // it. Glass casts none.
            .then(
                if (glass) {
                    Modifier
                } else {
                    Modifier.shadow(
                        elevation = if (colors.isNightMode) 32.dp else 24.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = if (colors.isNightMode) 0.16f else 0.14f),
                        spotColor = Color.Black.copy(alpha = if (colors.isNightMode) 0.26f else 0.22f),
                    )
                }
            )
            .floatingGlass(shape)
            .then(if (opacity > 0f) Modifier.background(colors.sheet.copy(alpha = opacity), shape) else Modifier)
    )
}

/**
 * The part of a surface inside its glass, in the surface's own pixels: all but the [top] of it,
 * which the glass has not grown over yet, with the glass's corners.
 */
private class GlassCut(
    private val top: Float,
    private val topRadius: Float,
    private val bottomRadius: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rect = Rect(0f, top, size.width, size.height)
        val max = minOf(rect.width, rect.height).coerceAtLeast(0f) / 2f
        val t = CornerRadius(topRadius.coerceAtMost(max))
        val b = CornerRadius(bottomRadius.coerceAtMost(max))
        return Outline.Generic(Path().apply { addRoundRect(RoundRect(rect, t, t, b, b)) })
    }
}

private fun lerpRect(from: Rect, to: Rect, fraction: Float) = Rect(
    lerp(from.left, to.left, fraction),
    lerp(from.top, to.top, fraction),
    lerp(from.right, to.right, fraction),
    lerp(from.bottom, to.bottom, fraction),
)

/** These bounds in [container]'s pixels, where the chrome places its glass. */
private fun LayoutCoordinates.boundsIn(container: LayoutCoordinates?): Rect =
    if (container == null || !container.isAttached || !isAttached) Rect.Zero
    else container.localBoundingBoxOf(this, clipBounds = false)

internal fun blurEffect(radius: Float) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.5f) {
        BlurEffect(radius, radius)
    } else null

/**
 * The keyboard's height the last time it showed, so a panel in its place matches it. Taken only
 * once the keyboard has settled, not while it slides in or out.
 */
@Composable
private fun rememberImeHeight(imeBottom: Int, imeTarget: Int): Int {
    val settings = rememberSettings()
    var height by remember { mutableStateOf(settings[SettingKeys.ImeHeight]) }
    LaunchedEffect(imeBottom, imeTarget) {
        if (imeBottom > 0 && imeBottom == imeTarget && imeBottom != height) {
            height = imeBottom
            settings[SettingKeys.ImeHeight] = imeBottom
        }
    }
    return height.coerceAtLeast(1)
}

/** The drag handle's height, compact and full screen. */
private val HandleCompact = 20.dp
private val HandleFull = 28.dp
