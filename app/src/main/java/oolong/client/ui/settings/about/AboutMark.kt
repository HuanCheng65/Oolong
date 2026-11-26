package oolong.client.ui.settings.about

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.ui.designsystem.isGlassAvailable
import oolong.client.ui.designsystem.loupeLens
import kotlin.math.roundToInt

/**
 * The lens of the app's mark, which can be lifted off it: held, it grows into a glass lens over
 * the page that follows the finger, and let go, it springs back into the mark.
 */
@Stable
class MarkLens {
    /** Whether the lens is off the mark, from being picked up until it is back in place. */
    internal var lifted by mutableStateOf(false)

    /** How far it has grown from its size in the mark, 0 to 1. */
    internal var grown by mutableFloatStateOf(0f)

    /** How far it is from its place in the mark. */
    internal var moved by mutableStateOf(Offset.Zero)

    /** Where its place in the mark is, in what [LensOverlay] is laid out in. */
    internal var home by mutableStateOf(Offset.Zero)

    internal var markCoordinates: LayoutCoordinates? = null
    internal var overlayCoordinates: LayoutCoordinates? = null
}

/**
 * The app's mark, [size] across: the launcher icon's own drawing, a blue disc, the white paper on
 * it and the glass lens over the paper's corner, which can be picked up with [lens].
 */
@Composable
fun AboutMark(lens: MarkLens, size: Dp, modifier: Modifier = Modifier) {
    val paper = painterResource(R.drawable.ic_mark_paper)
    val glass = painterResource(R.drawable.ic_mark_lens)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Canvas(
        modifier = modifier
            .size(size)
            .onGloballyPositioned {
                lens.markCoordinates = it
                lens.updateHome()
            }
            .pointerInput(lens) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val unit = this.size.width / 100f
                    if (lens.lifted || (down.position - LensCenter * unit).getDistance() > LensRadius * unit * 1.2f) return@awaitEachGesture
                    down.consume()
                    lens.lifted = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val grow = scope.launch {
                        Animatable(lens.grown).animateTo(1f, LiftSpring) { lens.grown = value }
                    }
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        lens.moved = change.position - down.position
                    }
                    // Back into its place, shrinking on the way, and in the mark again once there
                    grow.cancel()
                    scope.launch {
                        val shrink = launch {
                            Animatable(lens.grown).animateTo(0f, returnSpring()) { lens.grown = value }
                        }
                        Animatable(lens.moved, Offset.VectorConverter).animateTo(Offset.Zero, returnSpring()) { lens.moved = value }
                        shrink.join()
                        lens.lifted = false
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }
            },
    ) {
        with(paper) { draw(this@Canvas.size) }
        if (!lens.lifted) with(glass) { draw(this@Canvas.size) }
    }
}

/**
 * The lens while it is off the mark, over the page captured in [page], which must not contain
 * this. Where glass is not drawn, it is the mark's own lens, grown.
 */
@Composable
fun BoxScope.LensOverlay(lens: MarkLens, page: HazeState, markSize: Dp) {
    val painter = painterResource(R.drawable.ic_mark_lens)
    val density = LocalDensity.current
    val glass = isGlassAvailable()
    Box(
        modifier = Modifier
            .matchParentSize()
            .onGloballyPositioned {
                lens.overlayCoordinates = it
                lens.updateHome()
            },
    )
    if (!lens.lifted) return
    val grown = lens.grown.coerceAtLeast(0f)
    val diameter = lerp(markSize * (LensRadius * 2 / 100f), LiftedDiameter, grown)
    val center = lens.home + lens.moved
    // The mark's own lens shows while small, so it lands back on the mark unchanged
    val painted = if (glass) (1f - grown * 2f).coerceIn(0f, 1f) else 1f
    Box(
        modifier = Modifier
            .offset {
                val half = with(density) { diameter.toPx() } / 2
                IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt())
            }
            .size(diameter)
            // The mark's lens brings its own shadow
            .drawBehind { drawLensShadow(this.size.width / 2, alpha = 1f - painted) }
            .loupeLens(page, CircleShape)
            .drawWithContent {
                drawContent()
                drawMarkLens(painter, painted)
            },
    )
}

private fun MarkLens.updateHome() {
    val mark = markCoordinates ?: return
    val overlay = overlayCoordinates ?: return
    if (!mark.isAttached || !overlay.isAttached) return
    home = overlay.localPositionOf(mark, LensCenter * (mark.size.width / 100f))
}

/**
 * The mark's lens ([R.drawable.ic_mark_lens], drawn in the mark's hundredths) at [alpha], scaled
 * so its glass fills this, which is the lens's size.
 */
private fun DrawScope.drawMarkLens(painter: Painter, alpha: Float) {
    if (alpha <= 0f) return
    val unit = size.width / (LensRadius * 2)
    translate(-(LensCenter.x - LensRadius) * unit, -(LensCenter.y - LensRadius) * unit) {
        with(painter) { draw(Size(100 * unit, 100 * unit), alpha = alpha) }
    }
}

/** The lens's soft shadow, a little below it, for a lens of [radius]. */
private fun DrawScope.drawLensShadow(radius: Float, alpha: Float) {
    if (alpha <= 0f) return
    val center = Offset(size.width / 2, size.height / 2 + radius * 0.06f)
    drawCircle(
        brush = Brush.radialGradient(
            0.8f to Color(0x1F000000),
            1f to Color(0x00000000),
            center = center,
            radius = radius * 1.12f,
        ),
        radius = radius * 1.12f,
        center = center,
        alpha = alpha,
    )
}

/** The lens's place and size in the mark, in hundredths of its width. */
internal val LensCenter = Offset(64.75f, 35.25f)
internal const val LensRadius = 20f

private val LiftedDiameter = 128.dp
private val LiftSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 400f)
private fun <T> returnSpring() = spring<T>(dampingRatio = 0.6f, stiffness = 300f)
