package oolong.client.ui.designsystem

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A mouse's or touchpad's secondary click, which does what a long press does: a long press is
 * "this thing's menu" across the app, and a right click is how a pointer asks for one. [onClick]
 * gets where the click landed, in this element's coordinates, as a long press would. The press
 * is taken, so it neither clicks the element nor starts its long press.
 */
fun Modifier.onSecondaryClick(enabled: Boolean = true, onClick: (Offset) -> Unit): Modifier =
    if (!enabled) this else composed {
        // The latest handler, without restarting the gesture whenever a new lambda comes
        val current by rememberUpdatedState(onClick)
        secondaryClicks { current(it) }
    }

private fun Modifier.secondaryClicks(onClick: (Offset) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // The buttons are on the event, not the change. A touchpad's cursor may not report as
            // a mouse, so only the button decides; a finger on the screen presses none
            val event = currentEvent
            if (!event.buttons.isSecondaryPressed) return@awaitEachGesture
            down.consume()
            onClick(down.position)
            // The rest of the press belongs to the click, not to whatever is under it
            while (true) {
                val next = awaitPointerEvent(PointerEventPass.Initial)
                next.changes.forEach { it.consume() }
                if (next.changes.none { it.pressed }) break
            }
        }
    }
