package oolong.client.navigation.transition

import android.content.res.Resources
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import oolong.client.BuildConfig
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.settingAsState
import oolong.core.designsystem.theme.Motion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

/**
 * Back by gesture as a peek: while the finger drags, the page on top shrinks a little toward it and
 * shows the page underneath, and only goes back once let go. How far it shrinks has its own limit,
 * whatever the system makes of the drag, so a page held halfway never leaves.
 *
 * The frames of the two pages read this to draw themselves, [PageDisplay] lays the page underneath
 * out while it [holding]s, and [PageBackHandler] drives it.
 */
@Stable
class PagePeek {
    /** The page peeked past, by content key; null when not peeking. */
    var topKey: Any? by mutableStateOf(null)
        private set

    /** The page shown underneath while peeking. */
    var underKey: Any? by mutableStateOf(null)
        private set

    /** 0 at rest, 1 at the furthest peek. Held where it is once back goes ahead. */
    var amount by mutableFloatStateOf(0f)
        internal set

    /** The screen edge the gesture came from, as [NavigationEvent.swipeEdge]. */
    var edge by mutableIntStateOf(NavigationEvent.EDGE_LEFT)
        private set

    /** Whether the page underneath is wanted, from a gesture's start until the peek has settled back. */
    var holding by mutableStateOf(false)
        private set

    /** Whether back went ahead, so the page on top slides out from the peek. */
    var committed by mutableStateOf(false)
        internal set

    /**
     * How fast the gesture was moving when back went ahead, in screen widths a second, for the
     * page to slide out at the finger's pace rather than from rest.
     */
    var releaseSpeed = 0f
        internal set

    /** The page on top while it rests with nothing moving; only such a page can be peeked past. */
    internal var restingKey: Any? by mutableStateOf(null)

    internal val isPeeking: Boolean get() = topKey != null

    internal fun start(top: Any, under: Any, swipeEdge: Int) {
        topKey = top
        underKey = under
        holding = true
        committed = false
        releaseSpeed = 0f
        edge = if (swipeEdge == NavigationEvent.EDGE_RIGHT) NavigationEvent.EDGE_RIGHT else NavigationEvent.EDGE_LEFT
        amount = 0f
    }

    /** Lets the page underneath go once a peek called off has settled back. */
    internal fun release() {
        holding = false
    }

    internal fun reset() {
        holding = false
        committed = false
        topKey = null
        underKey = null
        amount = 0f
    }

    internal companion object {
        /** The share of the system's back progress at which the peek is all the way. */
        private const val FullPeekProgress = 0.4f

        /** How far to peek for the system's back progress: quick at first, then flattening out. */
        fun amountFor(progress: Float): Float {
            val p = (progress / FullPeekProgress).coerceIn(0f, 1f)
            return 1f - (1f - p) * (1f - p)
        }
    }
}

/** The [PagePeek] of the pages around, for their frames to draw. */
val LocalPagePeek = staticCompositionLocalOf<PagePeek?> { null }

/**
 * Handles back for [scenes], popping with [onBack] as many entries as the scene on top adds.
 *
 * A gesture on a page that rests alone drives [peek] once the finger moves, which has the display
 * lay the page underneath out; letting go pops, and the page slides off from the peek. Anything
 * else, such as a back key, a quick flick, or a gesture while pages move, just pops. Handlers that
 * pages add later take back first, so sheets, menus and the composer close before any peek.
 */
@Composable
fun <T : Any> PageBackHandler(
    scenes: PageScenes<T>,
    peek: PagePeek,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val handler = remember(peek) { PeekBackHandler(peek, scope) }
    val peeks by settingAsState(SettingKeys.PredictiveBack)
    val currentOnBack by rememberUpdatedState(onBack)
    SideEffect {
        handler.peeks = peeks
        handler.scenes = scenes
        handler.pop = { repeat(scenes.entries.size - scenes.currentScene.previousEntries.size) { currentOnBack() } }
        handler.isBackEnabled = scenes.currentScene.previousEntries.isNotEmpty()
    }
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current) {
        "No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner"
    }.navigationEventDispatcher
    DisposableEffect(dispatcher, handler) {
        dispatcher.addHandler(handler)
        onDispose { handler.remove() }
    }
}

private class PeekBackHandler(
    private val peek: PagePeek,
    private val scope: CoroutineScope,
) : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, isBackEnabled = false, isForwardEnabled = false) {
    var scenes: PageScenes<*>? = null
    var peeks = true
    var pop: () -> Unit = {}

    /**
     * The peek as shown. It follows the gesture through a quick spring rather than step for step,
     * as systems report the drag in jumps: one called off may drop straight to nothing.
     */
    private var shown = 0f
    private var velocity = 0f
    private var target = 0f
    private var moving: Job? = null

    /** Run once the peek arrives where it is moving to. */
    private var onArrived: () -> Unit = {}

    /** Whether a gesture is under way. */
    private var dragging = false

    /** Whether the peek is settling back after a gesture was called off. */
    private var settling = false

    /** Where a peek still settling back was when a new gesture took it up again. */
    private var resumedFrom = 0f

    /**
     * The furthest the gesture has gone, and the peek there. Coming back from it, the peek comes
     * back in step, rather than staying put until the gesture is back under [PagePeek.amountFor]'s
     * flat end, as when the system winds a gesture called off back to its start.
     */
    private var furthest = 0f
    private var furthestAmount = 0f

    /** The finger's last place and when it was there, and how fast it has been moving, smoothed. */
    private var lastX = 0f
    private var lastXTime = 0L
    private var fingerSpeed = 0f

    override fun onBackStarted(event: NavigationEvent) {
        log { "started edge=${event.swipeEdge} x=${event.touchX} peekable=${peek.restingKey}" }
        if (settling && peek.isPeeking) {
            // Taken up again before it settled: the display still holds the page underneath
            settling = false
            dragging = true
            resumedFrom = shown
            furthest = 0f
            furthestAmount = 0f
            onArrived = {}
            return
        }
        resumedFrom = 0f
        furthest = 0f
        furthestAmount = 0f
        lastXTime = 0L
        fingerSpeed = 0f
        // Peeks once the finger moves: a back key starts and completes at once, and pops as usual
        dragging = true
    }

    /** Starts peeking if the page on top rests alone over another. */
    private fun tryPeek(event: NavigationEvent) {
        if (!peeks) return
        val state = scenes ?: return
        val scene = state.currentScene as? FramedSinglePaneScene<*> ?: return
        val previous = state.previousScenes.lastOrNull() as? FramedSinglePaneScene<*> ?: return
        if (state.overlayScenes.isNotEmpty() || peek.restingKey != scene.key) return
        peek.start(top = scene.key, under = previous.key, swipeEdge = event.swipeEdge)
        moving?.cancel()
        shown = 0f
        velocity = 0f
        target = 0f
        onArrived = {}
    }

    override fun onBackProgressed(event: NavigationEvent) {
        trackSpeed(event.touchX, event.swipeEdge)
        // Also a gesture begun while pages still moved, once they rest
        if (dragging && !peek.isPeeking && event.progress > 0f) tryPeek(event)
        if (!peek.isPeeking || settling) return
        val progress = event.progress
        if (progress >= furthest) {
            furthest = progress
            furthestAmount = PagePeek.amountFor(progress)
        }
        val amount = if (furthest > 0f) furthestAmount * progress / furthest else 0f
        moveTo(amount + (1f - amount) * resumedFrom)
        log { "progress=$progress x=${event.touchX} target=${amount + (1f - amount) * resumedFrom}" }
    }

    override fun onBackCompleted() {
        log { "completed peeking=${peek.isPeeking} amount=${peek.amount} speed=$fingerSpeed" }
        // The page slides out from where the peek is now
        moving?.cancel()
        onArrived = {}
        settling = false
        dragging = false
        if (peek.isPeeking) {
            // Only speed onward carries on, and no more than a slide would sensibly start with
            peek.releaseSpeed = fingerSpeed.coerceIn(0f, MaxReleaseSpeed)
            peek.committed = true
        }
        pop()
    }

    /**
     * Follows the finger's speed away from its edge, in screen widths a second. Taken from where
     * the finger is rather than the progress: after the finger lifts the system winds the progress
     * on by itself, with the finger where it was, which reads as a very fast move.
     */
    private fun trackSpeed(x: Float, swipeEdge: Int) {
        if (x == lastX && lastXTime != 0L) return
        val now = System.nanoTime()
        val dt = (now - lastXTime) / 1_000_000_000f
        val width = Resources.getSystem().displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val away = if (swipeEdge == NavigationEvent.EDGE_RIGHT) lastX - x else x - lastX
        // Weighted toward the latest moves: what the finger did as it let go matters most
        fingerSpeed = if (lastXTime != 0L && dt in 0.001f..0.1f) fingerSpeed * 0.4f + away / width / dt * 0.6f else 0f
        lastX = x
        lastXTime = now
    }

    override fun onBackCancelled() {
        log { "cancelled peeking=${peek.isPeeking} amount=${peek.amount}" }
        dragging = false
        if (!peek.isPeeking) return
        settling = true
        moveTo(0f) {
            settling = false
            // The page's frame ends the peek once the display has it back at rest
            peek.release()
        }
    }

    /** Moves the peek toward [to] frame by frame, taking a new target up from where it is and how fast it moves. */
    private fun moveTo(to: Float, then: () -> Unit = {}) {
        target = to
        onArrived = then
        if (moving?.isActive == true) return
        moving = scope.launch {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = (now - last) / 1_000_000_000f
                last = now
                // A critically damped spring, stepped exactly: quick to follow the finger, gentler
                // settling back, as Motion.press and Motion.dismiss
                val omega = 2 * PI.toFloat() / (if (settling) SettleResponse else FollowResponse)
                val offset = shown - target
                val decay = exp(-omega * dt)
                val moved = velocity + omega * offset
                shown = target + (offset + moved * dt) * decay
                velocity = (velocity - omega * moved * dt) * decay
                if (abs(shown - target) < Motion.ProgressThreshold && abs(velocity) < Motion.ProgressThreshold) {
                    shown = target
                    velocity = 0f
                }
                peek.amount = shown
                if (shown == target && velocity == 0f) break
            }
            onArrived().also { onArrived = {} }
        }
    }

    private companion object {
        const val FollowResponse = 0.2f
        const val SettleResponse = 0.3f

        /** Screen widths a second a committed page may start sliding out at. */
        const val MaxReleaseSpeed = 4f
    }

    /** The gesture as the system reports it, in debug builds, to compare how devices report it. */
    private inline fun log(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d("PagePeek", message())
    }
}
