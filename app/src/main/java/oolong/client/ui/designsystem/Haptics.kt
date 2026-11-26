package oolong.client.ui.designsystem

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Haptic feedback by the kind of moment, as the design system's feedback rules name them. Each
 * is one of the system's own kinds, which the phone's maker tunes to its motor and which follow
 * the user's touch feedback setting; none is a vibration of the app's own.
 */
@Stable
class Haptics(private val view: View, private val feedback: HapticFeedback) {
    /** A drag crossed the point that decides what letting go does: over it when [over], back under it otherwise. */
    fun threshold(over: Boolean) {
        if (over) {
            feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE)
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val feedback = LocalHapticFeedback.current
    return remember(view, feedback) { Haptics(view, feedback) }
}

/**
 * Follows a drag against one threshold and gives [Haptics.threshold] each time it crosses, once
 * each way, however long the finger stays on either side.
 */
class ThresholdHaptics(private val haptics: Haptics) {
    private var over = false

    /** Where the drag is now: past the threshold or not. */
    fun update(past: Boolean) {
        if (past == over) return
        over = past
        haptics.threshold(past)
    }

    /** The drag ended; the next one starts short of the threshold. */
    fun reset() {
        over = false
    }
}
