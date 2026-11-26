package oolong.client.ui.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oolong.client.navigation.transition.PageOrigin
import oolong.client.navigation.transition.pageOriginStandIn
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.MenuShadow
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.designsystem.theme.glassEdge
import oolong.client.ui.designsystem.theme.scrim
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.Radius
import oolong.core.designsystem.theme.SmoothCornerShape
import oolong.core.designsystem.theme.addSmoothRect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One thing a context menu offers. */
@Immutable
class ContextMenuItem(
    val icon: ImageVector?,
    val label: String,
    /** Whether a rule parts it from the items before it. */
    val startsGroup: Boolean = false,
    /** A second line under the label, saying more about a choice. */
    val detail: String? = null,
    /** Drawn before the label in place of an icon, such as a colour or an avatar. */
    val leading: (@Composable () -> Unit)? = null,
    /** Whether it is the choice made, which a check after it marks. */
    val selected: Boolean = false,
    /** What it opens in place of running, over the menu, out of its own row; null for none. */
    val children: List<ContextMenuItem>? = null,
    /**
     * Whether running it takes away what the menu came out of, such as hiding the thread it is
     * on: it then runs once the menu is back home, so the menu has somewhere to go back to and
     * what follows starts from a still page.
     */
    val removesAnchor: Boolean = false,
    val onClick: () -> Unit = {},
) {
    fun copy(startsGroup: Boolean = this.startsGroup) =
        ContextMenuItem(icon, label, startsGroup, detail, leading, selected, children, removesAnchor, onClick)
}

/**
 * A larger look at what was long-pressed, shown above its menu: [content] grows out of [from],
 * the pressed thing's bounds in the window, and tapping it runs [onClick]. When that opens a page
 * from [origin], the page grows out of the preview, which stays until it has.
 */
@Immutable
class ContextMenuPreview(
    val from: Rect,
    val onClick: () -> Unit,
    val origin: PageOrigin? = null,
    val content: @Composable () -> Unit,
)

@Immutable
internal class ContextMenuRequest(
    val items: List<ContextMenuItem>,
    /** What the menu grows out of, in the window: a button, or an empty rect where a finger pressed. */
    val anchor: Rect,
    /** The button's icon, which the menu swallows as it opens and gives back as it closes. */
    val icon: ImageVector?,
    /** The icon's colour on the button; the secondary text colour when unspecified. */
    val iconTint: Color,
    val preview: ContextMenuPreview?,
    val id: Long,
)

/**
 * The window's context menus: one at a time, growing out of the button or the press that asked
 * for it, over everything else.
 */
@Stable
class ContextMenus internal constructor() {
    internal var request by mutableStateOf<ContextMenuRequest?>(null)
        private set

    /** How many things on screen can't be drawn twice, as a menu's glass would draw them ([KeepFromMenuGlass]). */
    internal var unsampleable by mutableIntStateOf(0)

    /** Where the finger that opened the menu is, in the window, while it stays down. */
    internal var finger by mutableStateOf<Offset?>(null)
        private set

    /** Where the menu showing now is in the window, once laid out; null while none shows. */
    var bounds: Rect? = null
        internal set

    /** Counts the times the menu was asked to go without running anything ([dismiss]). */
    internal var dismissals by mutableIntStateOf(0)
        private set

    /**
     * Closes the menu without running an item, as when its finger turns from the menu to dragging
     * the thing it was opened on.
     */
    fun dismiss() {
        if (request == null) return
        finger = null
        dismissals++
    }

    /** Where that finger last lifted, counted so each lift is heard once. */
    internal var lift by mutableStateOf<Pair<Int, Offset>?>(null)
        private set

    private var shown = 0L

    /**
     * Offers [items], growing out of [anchor] in the window: a button, whose [icon] the menu takes
     * in while it shows, or an empty rect where a long press landed. After a long press the finger
     * stays in charge until it lifts: report it with [moveFinger] and [liftFinger]. With a
     * [preview], the menu sits under it instead, on the side of the press.
     *
     * @return the menu's id, for [isShowing]
     */
    fun show(
        items: List<ContextMenuItem>,
        anchor: Rect,
        icon: ImageVector? = null,
        preview: ContextMenuPreview? = null,
        iconTint: Color = Color.Unspecified,
    ): Long {
        finger = null
        // Asked again from the same button while it shrinks away, the menu grows back from where
        // it is rather than vanishing and starting over
        val current = request
        if (current != null && closingId == current.id && !closingWithAction && preview == null && current.preview == null && current.anchor == anchor) {
            closingId = null
            reopened++
            return current.id
        }
        request = ContextMenuRequest(items, anchor, icon, iconTint, preview, ++shown)
        return shown
    }

    /** Counts the times the menu on its way out was asked for again, for it to grow back. */
    internal var reopened by mutableIntStateOf(0)
        private set

    /** Whether menu [id] is still on screen, opening, open or closing. */
    fun isShowing(id: Long?): Boolean = id != null && request?.id == id

    /** The menu on its way out, still drawn while it shrinks away. */
    private var closingId by mutableStateOf<Long?>(null)

    /**
     * Whether menu [id] is opening or open, and not yet on its way out: what the control that
     * opened it shows, so it turns back as soon as the menu starts to close.
     */
    fun isOpen(id: Long?): Boolean = isShowing(id) && closingId != id

    /** Whether the menu on its way out still has an item to run once home, so can't grow back. */
    private var closingWithAction = false

    internal fun closing(id: Long, withAction: Boolean = false) {
        closingId = id
        closingWithAction = withAction
    }

    /** The finger that opened the menu moved to [position] in the window. */
    fun moveFinger(position: Offset) {
        if (request != null) finger = position
    }

    /** The finger that opened the menu lifted at [position]; over an item, that item runs. */
    fun liftFinger(position: Offset) {
        if (request == null) return
        finger = null
        lift = ((lift?.first ?: 0) + 1) to position
    }

    internal fun closed(id: Long) {
        if (request?.id != id) return
        request = null
        finger = null
        bounds = null
    }
}

/**
 * Keeps menus from blurring the window while this is composed, for content that can't be drawn
 * twice: a web view drawn into a menu's glass as well alternates between two renders of its page.
 */
@Composable
fun KeepFromMenuGlass() {
    val menus = LocalContextMenus.current ?: return
    DisposableEffect(menus) {
        menus.unsampleable++
        onDispose { menus.unsampleable-- }
    }
}

/** The window's context menus; null where there are none, as in previews and tests. */
val LocalContextMenus = staticCompositionLocalOf<ContextMenus?> { null }

/** Draws [content] with room for context menus over it. */
@Composable
fun ContextMenuHost(content: @Composable () -> Unit) {
    val menus = remember { ContextMenus() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    // What a menu's glass blurs, captured only while one shows
    val backdrop = rememberHazeState()
    CompositionLocalProvider(LocalContextMenus provides menus) {
        Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
            val sampled = menus.unsampleable == 0
            Box(modifier = if (menus.request != null && sampled) Modifier.hazeSource(backdrop) else Modifier) {
                content()
            }
            menus.request?.let { request ->
                key(request.id) {
                    // Nothing to blur: the menu takes its opaque look
                    CompositionLocalProvider(LocalGlassTier provides if (sampled) LocalGlassTier.current else GlassTier.Solid) {
                        ContextMenuOverlay(menus = menus, request = request, origin = origin, backdrop = backdrop)
                    }
                }
            }
        }
    }
}

/**
 * The menu of [request]. Opening, it swells out of its anchor like a drop: a button's icon blurs
 * away inside it while the items, small and blurred at first, grow with it and only sharpen at
 * the end. Closing runs the other way, back into the icon.
 */
@Composable
private fun ContextMenuOverlay(menus: ContextMenus, request: ContextMenuRequest, origin: Offset, backdrop: HazeState) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val colors = ExtendedTheme.colors

    val fromButton = !request.anchor.isEmpty
    val preview = request.preview
    // Out of a button, a drop the size of its icon sets off towards the menu's place and grows
    // into the menu on the way, its size trailing its position. Out of a press there is nothing
    // to carry: the menu simply grows from where the finger is.
    val move = remember { Animatable(0f) }
    val grow = remember { Animatable(0f) }
    // A preview grows out of what was pressed, and its menu follows a moment later. With motion
    // reduced it only fades in where it ends up.
    val lift = remember { Animatable(0f) }
    val reduced = LocalReduceMotion.current
    var closing by remember { mutableStateOf(false) }
    // Once it is closing, taps go through to the page again while it shrinks away
    var letGo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (preview != null) {
            launch { lift.animateTo(1f, if (reduced) Motion.fadeIn() else Motion.morph(Motion.ProgressThreshold)) }
            delay(PreviewLead)
        }
        if (reduced) {
            // Nothing grows out of the button: the menu fades in where it ends up
            move.snapTo(1f)
            grow.animateTo(1f, Motion.fadeIn())
            return@LaunchedEffect
        }
        if (fromButton) {
            launch { move.animateTo(1f, MoveSpring) }
            delay(GrowDelay)
        }
        grow.animateTo(1f, Motion.morph(Motion.ProgressThreshold))
    }

    // Closing runs it backwards: a button's menu shrinks into a drop first, which flies home.
    // An item that takes the anchor away runs once the menu is home ([ContextMenuItem.removesAnchor]),
    // or as soon as the menu is gone some other way, so it is never lost.
    var closingJob by remember { mutableStateOf<Job?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    DisposableEffect(Unit) {
        onDispose { pendingAction?.invoke() }
    }
    fun dismiss(then: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        pendingAction = then
        menus.closing(request.id, withAction = then != null)
        letGo = true
        closingJob = scope.launch {
            if (reduced) {
                val sink = launch { lift.animateTo(0f, Motion.fadeOut()) }
                grow.animateTo(0f, Motion.fadeOut())
                sink.join()
            } else if (fromButton) {
                val shrink = launch { grow.animateTo(0f, ShrinkSpring) }
                delay(GrowDelay)
                // With an item to run, the drop lands a hair short and hands over to the button,
                // which stays on its own; the spring's last stretch would only hold things up
                move.animateTo(0f, Motion.dismiss(if (then != null) LandThreshold else Motion.ProgressThreshold))
                shrink.join()
            } else {
                val sink = launch { lift.animateTo(0f, if (reduced) Motion.fadeOut() else Motion.dismiss(Motion.ProgressThreshold)) }
                grow.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold))
                sink.join()
            }
            pendingAction = null
            then?.invoke()
            menus.closed(request.id)
        }
    }

    // Asked for again while closing: it stops shrinking and grows back from where it is
    val reopened = menus.reopened
    LaunchedEffect(reopened) {
        if (reopened == 0 || !closing) return@LaunchedEffect
        closingJob?.cancel()
        closingJob = null
        closing = false
        letGo = false
        launch { move.animateTo(1f, MoveSpring) }
        grow.animateTo(1f, Motion.morph(Motion.ProgressThreshold))
    }

    // Opening a page out of the preview: it becomes the page, so the rest fades away around it,
    // and the overlay stays until the page has grown out of it
    fun handOff(origin: PageOrigin) {
        if (closing) return
        closing = true
        menus.closing(request.id)
        scope.launch {
            launch { grow.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold)) }
            origin.awaitOpened()
            // Nothing grew out of it with motion reduced: it fades away over the page sliding in
            if (reduced) lift.animateTo(0f, Motion.fadeOut()) else lift.snapTo(0f)
            menus.closed(request.id)
        }
    }

    fun pick(item: ContextMenuItem) {
        if (item.removesAnchor) {
            dismiss(then = item.onClick)
        } else {
            item.onClick()
            dismiss()
        }
    }

    val items = request.items
    // The item whose children show over the menu, and how far they have come out of its row
    var opened by remember { mutableStateOf<Int?>(null) }
    val sub = remember { Animatable(0f) }
    fun openChildren(index: Int) {
        // Opened again while folding, they come back out from where they had got to
        val again = opened == index
        opened = index
        scope.launch {
            if (!again) sub.snapTo(0f)
            sub.animateTo(1f, Motion.morph(Motion.ProgressThreshold))
        }
    }
    fun foldChildren() {
        scope.launch {
            sub.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold))
            opened = null
        }
    }
    // Where each item sits in the open menu. Taken from layout, as the items grow by scaling,
    // which moves them on screen without laying them out again.
    val itemPlaces = remember { mutableStateListOf(*Array(items.size) { Rect.Zero }) }
    var itemsCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val geometry = remember { MenuGeometry() }
    fun itemAt(position: Offset): Int? {
        val local = position - origin - geometry.bounds.topLeft
        return itemPlaces.indexOfFirst { it.contains(local) }.takeIf { it >= 0 }
    }
    val run: (Int) -> Unit = { index ->
        when {
            closing -> Unit
            // Under open children, the menu only folds them away
            opened != null -> foldChildren()
            items[index].children != null -> openChildren(index)
            else -> pick(items[index])
        }
    }
    // A finger pressed on the open menu, as against the one still down from the long press
    var pressedOn by remember { mutableStateOf<Int?>(null) }
    val itemsScroll = rememberScrollState()
    val hovered = menus.finger?.let(::itemAt) ?: pressedOn
    LaunchedEffect(hovered) {
        if (hovered != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    // A finger that lifts where it pressed only lets go of the menu; one that slid there picks
    val slop = LocalViewConfiguration.current.touchSlop
    var fingerStart by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(menus.finger) {
        if (fingerStart == null) fingerStart = menus.finger
    }
    val liftsBefore = remember { menus.lift?.first }
    LaunchedEffect(menus.lift) {
        val (count, lifted) = menus.lift ?: return@LaunchedEffect
        if (count == liftsBefore) return@LaunchedEffect
        val start = fingerStart ?: return@LaunchedEffect
        if ((lifted - start).getDistance() > slop) itemAt(lifted)?.let(run)
    }

    val dismissalsBefore = remember { menus.dismissals }
    LaunchedEffect(menus.dismissals) {
        if (menus.dismissals != dismissalsBefore) dismiss()
    }

    BackHandler(enabled = !closing) { if (opened != null) foldChildren() else dismiss() }

    // Menus take no scrim. A preview is content over content, so behind it the page blurs and
    // dims as it rises, the way a lifted card stands apart. A tap anywhere else only closes them.
    if (preview != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = lift.value.coerceIn(0f, 1f) }
                .previewBackdrop(backdrop),
        )
    }
    if (!letGo) {
        Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { if (opened != null) foldChildren() else dismiss() } })
    }

    val insets = WindowInsets.safeDrawing
    val anchor = request.anchor.translate(-origin)

    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            // The glass
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val g = grow.value.coerceIn(0f, 1f)
                        shape = geometry.glassClip()
                        clip = true
                        shadowElevation = (if (colors.isNightMode) 32.dp else 24.dp).toPx() * g
                        // A button has no backing of its own, so the drop condenses out of nothing
                        // around its icon, and melts back into it; with motion reduced it only fades
                        alpha = if (reduced) g else if (fromButton) (g / DropFadeIn).coerceIn(0f, 1f) else 1f
                        ambientShadowColor = MenuShadow
                        spotShadowColor = MenuShadow
                    }
                    .menuGlass(backdrop)
                    .drawWithContent {
                        drawContent()
                        val edge = geometry.glassClip().createOutline(size, layoutDirection, this)
                        drawOutline(edge, colors.glassEdge, style = Stroke(width = 0.5.dp.toPx() * 2))
                    },
            )
            // The button's icon, swallowed by the glass
            Box {
                request.icon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = request.iconTint.takeOrElse { colors.textSecondary },
                        modifier = Modifier.graphicsLayer {
                            val g = grow.value.coerceIn(0f, 1f)
                            if (reduced) {
                                alpha = 1f - g
                                return@graphicsLayer
                            }
                            val glass = geometry.glass
                            // Rides in the middle of the drop, and melts into the items as it grows
                            translationX = glass.center.x - anchor.center.x
                            translationY = glass.center.y - anchor.center.y
                            val scale = lerp(1f, 1.6f, g)
                            scaleX = scale
                            scaleY = scale
                            val fade = ((g - 0.05f) / 0.3f).coerceIn(0f, 1f)
                            alpha = 1f - fade
                            val blur = IconBlur.toPx() * fade
                            renderEffect = if (blur > 0.5f) BlurEffect(blur, blur) else null
                        },
                    )
                }
            }
            // The items, at their final size and place, scaled into the glass and clipped by it
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        val g = grow.value
                        val bounds = geometry.bounds
                        val glass = geometry.glass
                        // As wide as the glass, from its top left corner
                        val scale = if (bounds.width > 0f) glass.width / bounds.width else 1f
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = glass.left - bounds.left
                        translationY = glass.top - bounds.top
                        shape = GlassClip(geometry, scale)
                        clip = true
                        alpha = (if (reduced) g.coerceIn(0f, 1f) else ((g - 0.2f) / 0.3f).coerceIn(0f, 1f)) * lerp(1f, UnderAlpha, sub.value)
                        // Under open children the menu stays in sight, blurred, as what they came from
                        val blur = (if (reduced) 0f else IconBlur.toPx() * (1f - ((g - 0.45f) / 0.4f).coerceIn(0f, 1f))) + (if (reduced) 0f else UnderBlur.toPx() * sub.value)
                        renderEffect = if (blur > 0.5f) BlurEffect(blur, blur) else null
                    }
                    // Inside the scaling, so items are measured against the menu as laid out
                    .onPlaced { itemsCoordinates = it }
                    .slideToPick(
                        scroll = itemsScroll,
                        at = { position -> itemPlaces.indexOfFirst { it.contains(position) }.takeIf { it >= 0 } },
                        onOver = { pressedOn = it },
                        onPick = run,
                    )
                    .widthIn(min = MenuMinWidth, max = MenuMaxWidth)
                    .width(IntrinsicSize.Max)
                    // A menu longer than the screen scrolls
                    .verticalScroll(itemsScroll)
                    .padding(vertical = 6.dp),
            ) {
                items.forEachIndexed { index, item ->
                    if (item.startsGroup && index > 0) {
                        Divider(color = colors.divider, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
                    }
                    MenuRow(
                        item = item,
                        highlighted = hovered == index,
                        onClick = { run(index) },
                        modifier = Modifier
                            .onGloballyPositioned { row ->
                                itemsCoordinates?.let { itemPlaces[index] = it.localBoundingBoxOf(row) }
                            }
                            // Its children's head stands in for it while they show
                            .graphicsLayer { alpha = if (opened == index) 0f else 1f },
                    )
                }
            }
            // The preview, at its final size and place, carried by the pressed thing's bounds
            Box(
                modifier = Modifier.graphicsLayer {
                    val final = geometry.preview
                    val now = geometry.previewNow
                    val scale = if (final.width > 0f) now.width / final.width else 1f
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = now.left - final.left
                    translationY = now.top - final.top
                    val corner = Radius.lg.toPx() * (if (reduced) 1f else lift.value.coerceIn(0f, 1f)) / scale
                    shape = PreviewClip(now.height / scale, corner)
                    clip = true
                    shadowElevation = (if (colors.isNightMode) 32.dp else 24.dp).toPx() * lift.value.coerceIn(0f, 1f)
                    ambientShadowColor = MenuShadow
                    spotShadowColor = MenuShadow
                    // Comes out of the card blurred and clears as it grows, so neither the card
                    // giving way nor the preview taking its place is a cut
                    val l = lift.value
                    alpha = if (reduced) l.coerceIn(0f, 1f) else (l / 0.35f).coerceIn(0f, 1f)
                    val blur = if (reduced) 0f else IconBlur.toPx() * (1f - ((l - 0.35f) / 0.5f).coerceIn(0f, 1f))
                    renderEffect = if (blur > 0.5f) BlurEffect(blur, blur) else null
                },
            ) {
                if (preview != null) {
                    Box(
                        modifier = Modifier
                            .then(if (preview.origin != null) Modifier.pageOriginStandIn(preview.origin) else Modifier)
                            .background(colors.card),
                    ) {
                        preview.content()
                        // Over the content, so a tap anywhere on it opens what it shows
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(enabled = !closing) {
                                    preview.onClick()
                                    if (preview.origin != null) handOff(preview.origin) else dismiss()
                                },
                        )
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val (glassMeasurable, iconMeasurable, itemsMeasurable, previewMeasurable) = measurables
        val gap = FingerGap.toPx()
        val margin = Margin.toPx()
        val top = insets.getTop(this) + margin
        val bottom = constraints.maxHeight - insets.getBottom(this) - margin
        val items = itemsMeasurable.measure(Constraints(maxHeight = (bottom - top).roundToInt().coerceAtLeast(0)))
        val size = Size(items.width.toFloat(), items.height.toFloat())
        // A button's menu covers it, from the side it sits on; a press's sits beside the finger
        val fromRight = anchor.center.x > constraints.maxWidth / 2
        val bounds: Rect
        val previewPlaceable = if (preview != null) {
            // The preview stays where the pressed thing was as far as it can, with the menu under it
            val menuGap = PreviewGap.toPx()
            val width = min(constraints.maxWidth - 2 * margin, PreviewMaxWidth.toPx())
            val maxHeight = min((bottom - top) * PreviewMaxHeight, bottom - top - menuGap - size.height)
            val placeable = previewMeasurable.measure(
                Constraints(
                    minWidth = width.roundToInt(),
                    maxWidth = width.roundToInt(),
                    maxHeight = maxHeight.roundToInt().coerceAtLeast(0),
                ),
            )
            val total = placeable.height + menuGap + size.height
            val previewTop = preview.from.translate(-origin).top.coerceIn(top, (bottom - total).coerceAtLeast(top))
            val previewLeft = (constraints.maxWidth - width) / 2
            val final = Rect(Offset(previewLeft, previewTop), Size(width, placeable.height.toFloat()))
            // With motion reduced it stays where it ends up, only fading
            val l = if (reduced) 1f else lift.value
            val from = preview.from.translate(-origin)
            geometry.preview = final
            geometry.previewNow = Rect(
                lerp(from.left, final.left, l),
                lerp(from.top, final.top, l),
                lerp(from.right, final.right, l),
                lerp(from.bottom, final.bottom, l),
            )
            bounds = Rect(Offset(if (fromRight) final.right - size.width else final.left, final.bottom + menuGap), size)
            placeable
        } else {
            val left = when {
                !fromButton -> anchor.center.x - size.width / 2
                fromRight -> anchor.right - size.width
                else -> anchor.left
            }.coerceIn(margin, (constraints.maxWidth - size.width - margin).coerceAtLeast(margin))
            val below = (if (fromButton) anchor.top else anchor.center.y + gap) + size.height <= bottom
            val y = when {
                below && fromButton -> anchor.top
                below -> anchor.center.y + gap
                fromButton -> anchor.bottom - size.height
                else -> anchor.center.y - gap - size.height
            }.coerceAtLeast(top)
            bounds = Rect(Offset(left, y), size)
            previewMeasurable.measure(Constraints.fixed(0, 0))
        }
        // With motion reduced the glass is where it ends up from the start, only fading in
        val g = if (reduced && grow.value > 0f) 1f else grow.value
        val glass: Rect
        val corner: Float
        if (fromButton) {
            // Starts as a round drop around the button's icon; out of something wide, such as a
            // setting's value and its arrows, as a capsule over all of it
            val start = if (anchor.isWide) anchor.size else Size(anchor.minDimension, anchor.minDimension)
            val m = if (reduced) 1f else move.value
            val center = Offset(lerp(anchor.center.x, bounds.center.x, m), lerp(anchor.center.y, bounds.center.y, m))
            val half = Offset(lerp(start.width, bounds.width, g), lerp(start.height, bounds.height, g)) / 2f
            glass = Rect(center - half, center + half)
            // Round while small, like a drop, easing into the menu's corners
            corner = min(glass.minDimension / 2, lerp(start.minDimension / 2, Radius.lg.toPx(), g.coerceIn(0f, 1f)))
        } else {
            // Grows evenly out of the point of the menu nearest the finger, or under a preview
            // out of its top edge
            val pivot = Offset(
                anchor.center.x.coerceIn(bounds.left, bounds.right),
                if (preview != null) bounds.top else anchor.center.y.coerceIn(bounds.top, bounds.bottom),
            )
            glass = Rect(
                lerp(pivot.x, bounds.left, g),
                lerp(pivot.y, bounds.top, g),
                lerp(pivot.x, bounds.right, g),
                lerp(pivot.y, bounds.bottom, g),
            )
            corner = Radius.lg.toPx() * g.coerceAtLeast(0f)
        }
        // All the room the glass takes, from a button's drop to the menu, and a little more for
        // the springs carrying it past
        val area = (if (fromButton) bounds.union(if (anchor.isWide) anchor else Rect(anchor.center, anchor.minDimension / 2)) else bounds)
            .inflate(GlassSlack.toPx())
        geometry.bounds = bounds
        menus.bounds = bounds
        geometry.glass = glass
        geometry.corner = corner
        geometry.area = area
        val glassPlaceable = glassMeasurable.measure(
            Constraints.fixed(area.width.roundToInt().coerceAtLeast(0), area.height.roundToInt().coerceAtLeast(0)),
        )
        val icon = iconMeasurable.measure(Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            glassPlaceable.place(area.left.roundToInt(), area.top.roundToInt())
            icon.place(
                (anchor.center.x - icon.width / 2f).roundToInt(),
                (anchor.center.y - icon.height / 2f).roundToInt(),
            )
            items.place(bounds.left.roundToInt(), bounds.top.roundToInt())
            previewPlaceable.place(
                (if (preview != null) geometry.preview.left else 0f).roundToInt(),
                (if (preview != null) geometry.preview.top else 0f).roundToInt(),
            )
        }
    }

    val open = opened
    val openRow = open?.let { itemPlaces.getOrNull(it) }
    if (open != null && openRow != null) {
        ChildMenu(
            item = items[open],
            from = openRow.translate(geometry.bounds.topLeft),
            menu = geometry.bounds,
            progress = { sub.value },
            backdrop = backdrop,
            onFold = ::foldChildren,
            onPick = { child ->
                if (!closing) pick(child)
            },
            // Closing the menu carries them along with its items, into the same drop
            modifier = Modifier.graphicsLayer {
                val bounds = geometry.bounds
                val glass = geometry.glass
                val scale = if (bounds.width > 0f) glass.width / bounds.width else 1f
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = glass.left - bounds.left * scale
                translationY = glass.top - bounds.top * scale
                alpha = ((grow.value - 0.2f) / 0.3f).coerceIn(0f, 1f)
            },
        )
    }
}

/**
 * Picking an item by pressing anywhere on a menu: the item under the finger lights up while it
 * slides, and the one under it when it lifts runs, as a long press that slid onto the menu does.
 * A press that scrolled the menu picks nothing. [at] finds the item at a point of this layout.
 */
@Composable
private fun Modifier.slideToPick(scroll: ScrollState, at: (Offset) -> Int?, onOver: (Int?) -> Unit, onPick: (Int) -> Unit): Modifier {
    val currentAt by rememberUpdatedState(at)
    val currentOver by rememberUpdatedState(onOver)
    val currentPick by rememberUpdatedState(onPick)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val scrolled = scroll.value
            var over = currentAt(down.position)
            currentOver(over)
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (scroll.value == scrolled) over?.let(currentPick)
                    break
                }
                over = currentAt(change.position)
                currentOver(over)
            }
            currentOver(null)
        }
    }
}

/**
 * One item of a menu: its icon, label and detail, and a check when chosen or › when it opens
 * more. [arrow] moves and turns the ›, for a head that grows out of its row.
 */
@Composable
private fun MenuRow(
    item: ContextMenuItem,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    arrow: (GraphicsLayerScope.() -> Unit)? = null,
) {
    val trailing = when {
        item.children != null -> Symbols.ChevronRight
        item.selected -> Symbols.Check
        else -> null
    }
    val colors = ExtendedTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ItemHeight)
            .padding(horizontal = 6.dp)
            .background(if (highlighted) colors.chip else Color.Transparent, ItemShape)
            // Presses are followed by the menu as a whole, so a finger can slide between items
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
            .padding(horizontal = 12.dp, vertical = if (item.detail != null) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            item.leading != null -> item.leading.invoke()
            item.icon != null -> Icon(item.icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                color = colors.text,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.detail?.let { detail ->
                Text(text = detail, color = colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        if (trailing != null) {
            Icon(
                trailing,
                contentDescription = null,
                tint = if (item.selected && item.children == null) colors.text else colors.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .then(if (arrow != null) Modifier.graphicsLayer(arrow) else Modifier),
            )
        }
    }
}

/**
 * The children of [item], on glass of their own over the menu: they come out of the item's row
 * at [from], a little wider than the [menu], their head where the row was. Tapping the head
 * folds them back into it.
 */
@Composable
private fun ChildMenu(
    item: ContextMenuItem,
    from: Rect,
    menu: Rect,
    progress: () -> Float,
    backdrop: HazeState,
    onFold: () -> Unit,
    onPick: (ContextMenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ExtendedTheme.colors
    val insets = WindowInsets.safeDrawing
    val children = item.children.orEmpty()
    val haptic = LocalHapticFeedback.current
    // The head and then each child, where they sit in the card, for a finger sliding over them
    val places = remember { mutableStateListOf(*Array(children.size + 1) { Rect.Zero }) }
    var column by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressedOn by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pressedOn) {
        if (pressedOn != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val scroll = rememberScrollState()
    val pick: (Int) -> Unit = { index -> if (index == 0) onFold() else onPick(children[index - 1]) }
    fun Modifier.place(index: Int) = onGloballyPositioned { row -> column?.let { places[index] = it.localBoundingBoxOf(row) } }
    // Where the card is now and when open, worked out in layout and drawn from
    var final by remember { mutableStateOf(Rect.Zero) }
    var now by remember { mutableStateOf(Rect.Zero) }
    var corner by remember { mutableFloatStateOf(0f) }
    // All the room the glass takes on its way, where it is laid out
    var area by remember { mutableStateOf(Rect.Zero) }
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        shape = RectClip(now.left - area.left, now.top - area.top, now.width, now.height, corner)
                        clip = true
                        shadowElevation = (if (colors.isNightMode) 32.dp else 24.dp).toPx() * progress().coerceIn(0f, 1f)
                        ambientShadowColor = MenuShadow
                        spotShadowColor = MenuShadow
                    }
                    .menuGlass(backdrop)
                    .drawWithContent {
                        drawContent()
                        val edge = RectClip(now.left - area.left, now.top - area.top, now.width, now.height, corner)
                            .createOutline(size, layoutDirection, this)
                        drawOutline(edge, colors.glassEdge, style = Stroke(width = 0.5.dp.toPx() * 2))
                    },
            )
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        // At their own size throughout: the head starts exactly on the row it
                        // came from and the glass unfolds from that row to show the rest
                        val p = progress()
                        val head = ChildPadding.toPx()
                        translationX = lerp(from.left - final.left, 0f, p)
                        translationY = lerp(from.top - head - final.top, 0f, p)
                        shape = RectClip(
                            left = now.left - final.left - translationX,
                            top = now.top - final.top - translationY,
                            width = now.width,
                            height = now.height,
                            corner = corner,
                        )
                        clip = true
                    }
                    .onPlaced { column = it }
                    .slideToPick(
                        scroll = scroll,
                        at = { position -> places.indexOfFirst { it.contains(position) }.takeIf { it >= 0 } },
                        onOver = { pressedOn = it },
                        onPick = pick,
                    )
                    .widthIn(min = MenuMinWidth, max = MenuMaxWidth + ChildWiden * 2)
                    .width(IntrinsicSize.Max)
                    .verticalScroll(scroll)
                    .padding(vertical = ChildPadding),
            ) {
                MenuRow(
                    item = item,
                    highlighted = pressedOn == 0,
                    onClick = onFold,
                    // The row's › stays where it was and turns down as the head widens
                    arrow = {
                        val p = progress()
                        translationX = lerp(from.width - final.width, 0f, p)
                        rotationZ = 90f * p
                    },
                    modifier = Modifier.place(0),
                )
                // The rest comes out of a blur as the glass reaches it; with motion reduced it only fades
                val childBlurs = !LocalReduceMotion.current
                Column(
                    modifier = Modifier.graphicsLayer {
                        val p = progress()
                        alpha = ((p - 0.1f) / 0.4f).coerceIn(0f, 1f)
                        val blur = if (childBlurs) IconBlur.toPx() * (1f - ((p - 0.25f) / 0.5f).coerceIn(0f, 1f)) else 0f
                        renderEffect = if (blur > 0.5f) BlurEffect(blur, blur) else null
                    },
                ) {
                    Divider(color = colors.divider, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
                    children.forEachIndexed { index, child ->
                        if (child.startsGroup && index > 0) {
                            Divider(color = colors.divider, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
                        }
                        MenuRow(
                            item = child,
                            highlighted = pressedOn == index + 1,
                            onClick = { onPick(child) },
                            modifier = Modifier.place(index + 1),
                        )
                    }
                }
            }
        },
    ) { (glassMeasurable, itemsMeasurable), constraints ->
        val margin = Margin.toPx()
        val top = insets.getTop(this) + margin
        val bottom = constraints.maxHeight - insets.getBottom(this) - margin
        val minWidth = (menu.width + ChildWiden.toPx() * 2).roundToInt().coerceAtMost(constraints.maxWidth - 2 * margin.roundToInt())
        val items = itemsMeasurable.measure(
            Constraints(minWidth = minWidth.coerceAtLeast(0), maxHeight = (bottom - top).roundToInt().coerceAtLeast(0)),
        )
        val width = items.width.toFloat()
        val height = items.height.toFloat()
        // The head lies over the row, as far as the screen allows
        val left = (menu.center.x - width / 2).coerceIn(margin, (constraints.maxWidth - width - margin).coerceAtLeast(margin))
        val y = (from.top - ChildPadding.toPx()).coerceIn(top, (bottom - height).coerceAtLeast(top))
        final = Rect(Offset(left, y), Size(width, height))
        val p = progress()
        now = Rect(
            lerp(from.left, final.left, p),
            lerp(from.top, final.top, p),
            lerp(from.right, final.right, p),
            lerp(from.bottom, final.bottom, p),
        )
        corner = lerp(12.dp.toPx(), Radius.lg.toPx(), p.coerceIn(0f, 1f))
        area = from.union(final).inflate(GlassSlack.toPx())
        val glass = glassMeasurable.measure(
            Constraints.fixed(area.width.roundToInt().coerceAtLeast(0), area.height.roundToInt().coerceAtLeast(0)),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            glass.place(area.left.roundToInt(), area.top.roundToInt())
            items.place(final.left.roundToInt(), final.top.roundToInt())
        }
    }
}

/**
 * [width] by [height] at [left], [top], with smooth corners; the rest is cut off. Glass is laid
 * out once over all the room it takes and cut down to where it is now, so its blur is worked out
 * at one size rather than again at every size it passes through.
 */
private class RectClip(
    private val left: Float,
    private val top: Float,
    private val width: Float,
    private val height: Float,
    private val corner: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(
            Path().apply {
                addSmoothRect(width, height, corner, SmoothCornerShape.DefaultSmoothing)
                translate(Offset(left, top))
            },
        )
}

/**
 * A menu's thick glass (design-system.md §2.5): the backdrop blurred under a veil of the sheet
 * colour, thicker at night; an opaque sheet where glass is off or unsupported.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
private fun Modifier.menuGlass(backdrop: HazeState): Modifier {
    val colors = ExtendedTheme.colors
    val tier = currentGlassTier()
    if (tier == GlassTier.Solid) return background(colors.sheet)
    // Clearer or more frosted with the chrome, always thicker than it
    val frost = glassFrost()
    val style = remember(colors.sheet, colors.isNightMode, frost) {
        HazeBlurStyle {
            backgroundColor(colors.sheet)
            blurRadius(MenuBlur * frost)
            noiseFactor(0f)
            colorEffects(listOf(HazeColorEffect.tint(colors.sheet.copy(alpha = ((if (colors.isNightMode) 0.7f else 0.4f) * frost).coerceAtMost(0.95f)))))
        }
    }
    return hazeBlur(input = HazeInput.Sources(backdrop), style = style, performanceMode = BigBlurPerformance)
}

/** Behind a preview: the page blurred and dimmed; dimmed only where glass is off or unsupported. */
@OptIn(ExperimentalHazeApi::class)
@Composable
private fun Modifier.previewBackdrop(backdrop: HazeState): Modifier {
    val colors = ExtendedTheme.colors
    val dim = Color.Black.copy(alpha = if (colors.isNightMode) PreviewDimNight else PreviewDim)
    val tier = currentGlassTier()
    if (tier == GlassTier.Solid) return background(colors.scrim)
    val style = remember(colors.background, dim) {
        HazeBlurStyle {
            backgroundColor(colors.background)
            blurRadius(PreviewBackdropBlur)
            noiseFactor(0f)
            colorEffects(listOf(HazeColorEffect.tint(dim)))
        }
    }
    return hazeBlur(input = HazeInput.Sources(backdrop), style = style, performanceMode = BigBlurPerformance)
}

/**
 * Where the menu's parts are, worked out in layout and drawn from. State, so that what draws them
 * follows every change: the drop keeps moving home after it has stopped shrinking.
 */
private class MenuGeometry {
    var bounds by mutableStateOf(Rect.Zero)
    var glass by mutableStateOf(Rect.Zero)
    var corner by mutableFloatStateOf(0f)

    /** Where the glass is laid out, over all the room it takes; it shows only at [glass]. */
    var area by mutableStateOf(Rect.Zero)

    fun glassClip() = RectClip(glass.left - area.left, glass.top - area.top, glass.width, glass.height, corner)

    /** Where a preview ends up, and where it is now. */
    var preview by mutableStateOf(Rect.Zero)
    var previewNow by mutableStateOf(Rect.Zero)
}

/** The top [height] of a preview scaled by its growing, rounded by [corner]; the rest is cut off. */
private class PreviewClip(private val height: Float, private val corner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(
            Path().apply { addSmoothRect(size.width, min(height, size.height), corner, SmoothCornerShape.DefaultSmoothing) },
        )
}

/** The glass, as seen from the items scaled by [scale] at its top left corner, so they show only within it. */
private class GlassClip(private val geometry: MenuGeometry, private val scale: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val glass = geometry.glass
        if (scale <= 0f || glass.isEmpty) return Outline.Rectangle(Rect.Zero)
        return Outline.Generic(
            Path().apply {
                addSmoothRect(glass.width / scale, glass.height / scale, geometry.corner / scale, SmoothCornerShape.DefaultSmoothing)
            },
        )
    }
}

/** The smallest rectangle holding both. */
private fun Rect.union(other: Rect) =
    Rect(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))

/** Whether a menu grows out of this anchor as a capsule over it, rather than a drop at its middle. */
private val Rect.isWide: Boolean get() = width > height * 1.5f

/** Room around the glass's way for springs to carry it past. */
private val GlassSlack = 16.dp

/** The drop's way to the menu's place, which runs ahead of its growing: as quick as a dialog coming up. */
private val MoveSpring get() = Motion.present(Motion.ProgressThreshold)

/** The menu shrinking back into a drop, ahead of its way home: at the pace of a press. */
private val ShrinkSpring get() = Motion.press(Motion.ProgressThreshold)

/** How far growing trails moving, and moving home trails shrinking. */
private const val GrowDelay = 40L

/** How near home a button's drop counts as landed when an item waits to run on its landing. */
private const val LandThreshold = 0.02f

/** How far into growing a button's drop has fully condensed. */
private const val DropFadeIn = 0.25f

/** How long a preview grows before its menu starts to. */
private const val PreviewLead = 60L

/** A preview's widest, on large screens. */
private val PreviewMaxWidth = 420.dp

/** A preview's tallest, as a share of the room on screen; more of it is cut off. */
private const val PreviewMaxHeight = 0.6f

private val PreviewGap = 12.dp
private val PreviewBackdropBlur = 20.dp
private const val PreviewDim = 0.12f
private const val PreviewDimNight = 0.3f
private val FingerGap = 20.dp
private val Margin = 12.dp
private val IconBlur = 20.dp
private val MenuMinWidth = 180.dp
private val MenuMaxWidth = 280.dp
private val ItemHeight = 44.dp
private val MenuBlur = 24.dp

/** An item's highlight, inset 6dp in the menu and so as round as the menu less that. */
private val ItemShape = SmoothCornerShape(Radius.lg - 6.dp)

/** Above a child menu's head and below its last item. */
private val ChildPadding = 6.dp

/** How much wider than its menu a child menu is, on each side. */
private val ChildWiden = 10.dp

/**
 * The menu's and a preview's backdrop blurs are wide, so drawn at half resolution they look the
 * same for a quarter of the pixels.
 */
private val BigBlurPerformance = HazePerformanceMode.Performance

/** A menu under its open children: faded and blurred. */
private const val UnderAlpha = 0.55f
private val UnderBlur = 3.dp
