package oolong.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How pressed things respond, in place of Material's ripple (design-system.md §8.1): a veil of the
 * content colour. Controls that stand on their own also shrink, with [pressScale]. Under a mouse
 * or touchpad pointer the veil shows at half strength, to say what a click would press.
 */
object PressFeedback {
    /** The veil over the whole bounds. The app's default indication. */
    val Overlay: IndicationNodeFactory = PressIndication(null)

    /**
     * The veil in [shape], for a control of that shape: it needs no clip around it, whose edge
     * some GPUs leave jagged where the shape is a path.
     */
    fun overlay(shape: Shape): IndicationNodeFactory = PressIndication(shape)
}

/**
 * Shrinks a control while [interactionSource] is pressed, its edges moving in by the same 2.5dp
 * whatever its size: an icon button to about 0.9, a capsule button to 0.95, a card to 0.985.
 *
 * Apply it first, before the control's clip and background, so the whole shape shrinks; the veil
 * comes from the clickable's indication inside.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val progress = remember { Animatable(0f, visibilityThreshold = Motion.ProgressThreshold) }
    LaunchedEffect(interactionSource) { followPresses(interactionSource.interactions, progress) {} }
    return graphicsLayer {
        val p = progress.value
        if (p != 0f) {
            val pressedScale = (1f - 2 * PressedInset.toPx() / size.maxDimension).coerceIn(MinPressedScale, MaxPressedScale)
            val s = 1f - (1f - pressedScale) * p
            scaleX = s
            scaleY = s
        }
    }
}

private val PressedInset = 2.5.dp

private const val MinPressedScale = 0.9f
private const val MaxPressedScale = 0.985f

/** The veil's opacity at full press, of the content colour. */
private const val PressedOverlayAlpha = 0.08f

/** The veil's opacity under a pointer: half a press. */
private const val HoveredOverlayAlpha = PressedOverlayAlpha / 2

/** How long a tap shows its press before letting go, about 80% of the way in. */
private const val MinPressMillis = 100L

/**
 * Animates [progress] to 1 on each press and back to 0 on release, quick in and slower out; a
 * quick tap still shows most of its press.
 */
private suspend fun CoroutineScope.followPresses(
    interactions: Flow<Interaction>,
    progress: Animatable<Float, *>,
    onFrame: () -> Unit,
) {
    var pressing: Job? = null
    interactions.collect { interaction ->
        when (interaction) {
            is PressInteraction.Press -> {
                pressing = launch { progress.animateTo(1f, Motion.press()) { onFrame() } }
            }

            is PressInteraction.Release, is PressInteraction.Cancel -> {
                val press = pressing
                launch {
                    withTimeoutOrNull(MinPressMillis) { press?.join() }
                    progress.animateTo(0f, Motion.release()) { onFrame() }
                }
            }
        }
    }
}

private class PressIndication(private val shape: Shape?) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource, shape)

    override fun equals(other: Any?): Boolean = other is PressIndication && other.shape == shape

    override fun hashCode(): Int = shape.hashCode()
}

private class PressNode(
    private val interactionSource: InteractionSource,
    private val shape: Shape?,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    /** 0 at rest, 1 fully pressed; the release spring may swing a little past 0. */
    private val progress = Animatable(0f, visibilityThreshold = Motion.ProgressThreshold)

    /** 0 with no pointer over it, 1 with one. */
    private val hover = Animatable(0f, visibilityThreshold = Motion.ProgressThreshold)

    override fun onAttach() {
        coroutineScope.launch { followPresses(interactionSource.interactions, progress) { invalidateDraw() } }
        coroutineScope.launch {
            var hovers = 0
            var moving: Job? = null
            interactionSource.interactions.collect { interaction ->
                val before = hovers
                when (interaction) {
                    is HoverInteraction.Enter -> hovers++
                    is HoverInteraction.Exit -> hovers = (hovers - 1).coerceAtLeast(0)
                    else -> return@collect
                }
                if ((before > 0) == (hovers > 0)) return@collect
                moving?.cancel()
                moving = launch { hover.animateTo(if (hovers > 0) 1f else 0f, if (hovers > 0) Motion.fadeIn() else Motion.fadeOut()) { invalidateDraw() } }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val pressed = (PressedOverlayAlpha * progress.value).coerceIn(0f, PressedOverlayAlpha)
        val alpha = maxOf(pressed, HoveredOverlayAlpha * hover.value)
        if (alpha <= 0f) return
        val color = currentValueOf(LocalContentColor).copy(alpha = alpha)
        if (shape == null) drawRect(color) else drawOutline(shape.createOutline(size, layoutDirection, this), color)
    }
}
