package oolong.client.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oolong.core.designsystem.theme.Motion

/**
 * Fades a row that scrolls sideways out at an edge where there is more of it, so it shows it
 * goes on without a scroll bar; at an end the fade there goes, saying there is no more. Put it on
 * the row's viewport, outside the scrolling. A mouse wheel, which scrolls up and down, scrolls it
 * sideways.
 */
fun Modifier.scrollEdgeFade(scroll: ScrollState, width: Dp = ScrollEdgeFadeWidth): Modifier = composed {
    val start by animateFloatAsState(if (scroll.canScrollBackward) 1f else 0f, Motion.fadeIn(), label = "fadeStart")
    val end by animateFloatAsState(if (scroll.canScrollForward) 1f else 0f, Motion.fadeIn(), label = "fadeEnd")
    val scope = rememberCoroutineScope()
    this
        .pointerInput(scroll) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type != PointerEventType.Scroll || scroll.maxValue == 0 || event.changes.any { it.isConsumed }) continue
                    val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                    if (delta == 0f) continue
                    event.changes.forEach { it.consume() }
                    scope.launch { scroll.animateScrollBy(delta * WheelStep.toPx(), Motion.settle()) }
                }
            }
        }
        // Erasing needs a layer of its own, else it would cut through what is under the row too
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = width.toPx().coerceAtMost(size.width / 3)
            if (start > 0f) {
                drawRect(
                    Brush.horizontalGradient(0f to Color.Black.copy(alpha = start), 1f to Color.Transparent, startX = 0f, endX = fade),
                    size = Size(fade, size.height),
                    blendMode = BlendMode.DstOut,
                )
            }
            if (end > 0f) {
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = end), startX = size.width - fade, endX = size.width),
                    topLeft = Offset(size.width - fade, 0f),
                    size = Size(fade, size.height),
                    blendMode = BlendMode.DstOut,
                )
            }
        }
}

/** How far in from an edge a row fades out. */
val ScrollEdgeFadeWidth = 24.dp

/** How far a notch of a mouse wheel scrolls a row. */
private val WheelStep = 48.dp
