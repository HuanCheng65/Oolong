package oolong.client.ui.videoview

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.ui.components.LocalAccount
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.IconButton
import oolong.client.ui.designsystem.LocalContextMenus
import oolong.client.ui.designsystem.MediaGlassActions
import oolong.client.ui.designsystem.MediaGlassIconButton
import oolong.client.ui.designsystem.MediaGlassPill
import oolong.client.ui.designsystem.ProvideMediaGlass
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.WavyLoader
import oolong.client.ui.designsystem.controlLens
import oolong.client.ui.designsystem.isGlassAvailable
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.designsystem.window.FullColorWindow
import oolong.client.ui.photoview.LightSystemBars
import oolong.client.ui.photoview.flightSpeed
import oolong.client.ui.photoview.offsetTo
import oolong.client.util.TiebaUtil
import oolong.client.util.findActivity
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Phase { Opening, Open, Closing }

/** What a finger on the video turned out to be doing. */
private enum class Gesture { Tap, Hold, Seek, Dismiss, None }

/** The player's state, as composition reads it. */
@Stable
private class Playback(player: Player) : Player.Listener {
    var playWhenReady by mutableStateOf(player.playWhenReady)
    var state by mutableIntStateOf(player.playbackState)
    var error by mutableStateOf(player.playerError)
    var firstFrame by mutableStateOf(false)

    /** Height over width of the picture, once it is known. */
    var aspect by mutableFloatStateOf(0f)

    /** Whether the video playing is HDR, which only an HDR window shows brighter than white. */
    var hdr by mutableStateOf(false)

    val ended get() = state == Player.STATE_ENDED

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        this.playWhenReady = playWhenReady
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        state = playbackState
    }

    override fun onPlayerErrorChanged(error: PlaybackException?) {
        this.error = error
    }

    override fun onRenderedFirstFrame() {
        firstFrame = true
    }

    @OptIn(UnstableApi::class)
    override fun onTracksChanged(tracks: Tracks) {
        hdr = tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_VIDEO && (0 until group.length).any { index ->
                group.isTrackSelected(index) && ColorInfo.isTransferHdr(group.getTrackFormat(index).colorInfo)
            }
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (videoSize.width > 0 && videoSize.height > 0) {
            aspect = videoSize.height / (videoSize.width * videoSize.pixelWidthHeightRatio)
        }
    }
}

/**
 * The video of [opening], over the whole window. It grows out of its cover, plays with sound, and
 * closes on the close button, the back button or a drag down, shrinking back into its cover. A tap
 * shows or hides the controls, a double tap pauses, holding plays at double speed and a sideways
 * drag seeks.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoViewerOverlay(viewer: VideoViewer, opening: VideoOpening, origin: Offset) {
    val player = viewer.player ?: return
    val video = opening.video
    val context = LocalContext.current
    val shareTitle = stringResource(id = R.string.title_share)
    val scope = rememberCoroutineScope()
    val playback = remember(player) { Playback(player) }
    DisposableEffect(player) {
        player.addListener(playback)
        onDispose { player.removeListener(playback) }
    }

    // Shrunk to a window over the pages, which go on as they were
    var mini by remember { mutableStateOf(false) }
    val shrink = remember { Animatable(0f) }

    // Turning sideways is the viewer's own, and ends with it
    val activity = remember(context) { context.findActivity() }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val requested = remember { activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    // Whether the viewer turned the window sideways, as against a window already so, as a tablet's
    // held or locked that way: only a turn of its own does the viewer, and back, turn back
    var turned by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = requested }
    }
    val view = LocalView.current
    DisposableEffect(landscape, activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (landscape && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val autoPip = remember { Settings.of(context)[SettingKeys.VideoAutoPip] }
    // Shrunk into a window of its own, the viewer shows only the video
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }
    DisposableEffect(activity) {
        val host = activity as? ComponentActivity
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        host?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { host?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    // In the system's own window the video fills it, mini or not
    val shrunk = mini && !inPip
    if (!shrunk) {
        LightSystemBars()
        FullColorWindow(hdr = playback.hdr)
    }

    var position by remember { mutableLongStateOf(player.currentPosition) }
    var buffered by remember { mutableLongStateOf(player.bufferedPosition) }
    var duration by remember { mutableLongStateOf(video.duration * 1000L) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            buffered = player.bufferedPosition
            player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.let { duration = it }
            delay(200)
        }
    }

    // Each video starts at normal speed; holding plays it at double for as long as the finger stays
    var speed by remember { mutableFloatStateOf(1f) }
    var fast by remember { mutableStateOf(false) }
    LaunchedEffect(speed, fast) { player.setPlaybackSpeed(if (fast) 2f else speed) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player.pause()
        viewer.leave(video.key, player.currentPosition, duration)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val container = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val aspect = playback.aspect.takeIf { it > 0f } ?: video.aspect.takeIf { it > 0f } ?: (9f / 16)
        val fitted = fitRect(container, aspect)

        val progress = remember { Animatable(0f) }
        var phase by remember { mutableStateOf(Phase.Opening) }
        // With motion reduced the video fades in and out in place instead of flying
        val reduced = LocalReduceMotion.current
        var near by remember { mutableStateOf(opening.from?.translate(-origin)?.takeUnless { reduced }) }
        var far by remember { mutableStateOf<Rect?>(null) }
        var drag by remember { mutableStateOf(Offset.Zero) }
        var dragging by remember { mutableStateOf(false) }
        var closingFade by remember { mutableFloatStateOf(1f) }
        val dragScale = 1f - (drag.y.coerceAtLeast(0f) / container.height).coerceIn(0f, 1f) * 0.45f
        val dragFade = 1f - (abs(drag.y) / (container.height * 0.5f)).coerceIn(0f, 1f)

        // Where a seek is headed while a finger drags it, in milliseconds
        var seekTarget by remember { mutableStateOf<Long?>(null) }
        var chrome by remember { mutableStateOf(true) }
        var touched by remember { mutableIntStateOf(0) }
        val menus = LocalContextMenus.current
        var speedMenu by remember { mutableStateOf<Long?>(null) }
        val sticky = !playback.playWhenReady || playback.ended || playback.error != null ||
            seekTarget != null || menus?.isShowing(speedMenu) == true
        val currentSticky by rememberUpdatedState(sticky)
        // Coming out of a pause, an end or a seek, the controls stay a while before hiding
        LaunchedEffect(sticky) {
            if (!sticky) chrome = true
        }
        LaunchedEffect(chrome, sticky, touched, phase) {
            if (chrome && !sticky && phase == Phase.Open) {
                delay(ChromeTimeout)
                chrome = false
            }
        }

        LaunchedEffect(Unit) {
            progress.animateTo(1f, Motion.present(Motion.ProgressThreshold))
            phase = Phase.Open
        }

        // The mini window: in one of the corners of the space the pages leave clear, or under a finger
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val safe = WindowInsets.safeDrawing
        val clear = with(density) {
            val margin = MiniMargin.toPx()
            Rect(
                safe.getLeft(density, direction) + margin,
                safe.getTop(density) + MiniTopClearance.toPx() + margin,
                container.width - safe.getRight(density, direction) - margin,
                container.height - safe.getBottom(density) - MiniBottomClearance.toPx() - margin,
            )
        }
        val miniSize = with(density) {
            if (aspect <= 1f) {
                Size(MiniWide.toPx(), MiniWide.toPx() * aspect)
            } else {
                val height = minOf(MiniTall.toPx() * aspect, MiniTallest.toPx())
                Size(height / aspect, height)
            }
        }
        fun corners() = listOf(
            Offset(clear.left, clear.top),
            Offset(clear.right - miniSize.width, clear.top),
            Offset(clear.left, clear.bottom - miniSize.height),
            Offset(clear.right - miniSize.width, clear.bottom - miniSize.height),
        )
        fun nearestCorner(to: Offset) = corners().minBy { (it - to).getDistanceSquared() }
        val miniAt = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        var miniPlaced by remember { mutableStateOf(false) }
        var miniHeld by remember { mutableStateOf<Offset?>(null) }
        // Turned or resized, the window glides to keep to its corner rather than jumping there
        LaunchedEffect(clear, miniSize) {
            if (miniPlaced && miniHeld == null) miniAt.animateTo(nearestCorner(miniAt.value), Motion.settle())
        }
        val miniRect = Rect(miniHeld ?: miniAt.value, miniSize)

        fun close(velocity: Velocity = Velocity.Zero) {
            if (phase == Phase.Closing) return
            viewer.leave(video.key, player.currentPosition, duration)
            player.pause()
            far = fitted.dragged(drag, dragScale, container)
            // Turned sideways, the cover is no longer where it was: fade out in place instead
            near = viewer.thumbnails[video.key]?.takeIf { it.visible && !landscape && !reduced }?.bounds?.translate(-origin)
            closingFade = dragFade
            drag = Offset.Zero
            dragging = false
            phase = Phase.Closing
            // Let go with a fling, the video carries on at the finger's speed
            val from = far
            val to = near
            val flight = if (from != null && to != null) flightSpeed(from, to, velocity) else 0f
            scope.launch {
                // Closed while still opening, it turns back from where it got to
                progress.snapTo(progress.value.coerceAtMost(1f))
                progress.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold), initialVelocity = flight)
                viewer.closed()
            }
        }

        fun minimize() {
            if (phase != Phase.Open || mini) return
            // The pages are upright; so is the window over them
            if (turned) {
                activity?.requestedOrientation = requested
                turned = false
            }
            mini = true
            chrome = false
            scope.launch {
                if (!miniPlaced) {
                    miniAt.snapTo(corners().last())
                    miniPlaced = true
                }
                shrink.animateTo(1f, Motion.settle(Motion.ProgressThreshold))
            }
        }

        fun expand() {
            if (phase != Phase.Open || !mini) return
            mini = false
            chrome = true
            touched++
            scope.launch { shrink.animateTo(0f, Motion.settle(Motion.ProgressThreshold)) }
        }

        // Closed from the window: it fades and shrinks where it is
        fun closeMini() {
            if (phase == Phase.Closing) return
            viewer.leave(video.key, player.currentPosition, duration)
            player.pause()
            far = miniRect
            near = null
            closingFade = 0f
            phase = Phase.Closing
            scope.launch {
                // Closed while still opening, it turns back from where it got to
                progress.snapTo(progress.value.coerceAtMost(1f))
                progress.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold))
                viewer.closed()
            }
        }

        fun setLandscape(on: Boolean) {
            turned = on
            activity?.requestedOrientation =
                if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        fun togglePlaying() {
            touched++
            when {
                playback.error != null -> return
                playback.ended -> {
                    player.seekTo(0)
                    player.play()
                }

                player.playWhenReady -> player.pause()
                else -> player.play()
            }
        }

        fun retry() {
            player.prepare()
            player.play()
        }

        val onClose by rememberUpdatedState<(Velocity) -> Unit> { close(it) }
        BackHandler(enabled = phase != Phase.Closing && !mini) {
            if (turned) setLandscape(false) else close()
        }

        // Leaving the app takes the video, full or mini, into the system's window, when the user wants that
        pictureInPicture(
            activity = activity,
            enabled = autoPip && phase == Phase.Open && playback.playWhenReady && !playback.ended && playback.error == null,
            playing = playback.playWhenReady && !playback.ended,
            aspect = aspect,
            bounds = (if (shrunk) miniRect else fitted).translate(origin),
            onToggle = { togglePlaying() },
        )

        val p = progress.value
        val m = if (inPip) 0f else shrink.value
        val black = (if (phase == Phase.Closing) p * closingFade else p.coerceIn(0f, 1f) * dragFade) * (1f - m)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = black }
                .background(Color.Black),
        )
        // Full, the viewer takes every touch; mini, the pages under it get them
        if (!shrunk) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            )
        }

        // The video, flying between its cover (near; null to fade in place) and where it plays
        val full = fitted.dragged(drag, dragScale, container)
        val open = if (m > 0f) lerp(full, miniRect, m) else full
        val target = if (phase == Phase.Closing) far ?: open else open
        val rect = near?.let { lerp(it, target, p) } ?: target.scaledAround(if (reduced) 1f else lerp(0.9f, 1f, p))
        // The cover's corners as it grows out of it, the mini window's as it shrinks
        val corner = maxOf(if (near != null) lerp(8f, 0f, p.coerceIn(0f, 1f)) else 0f, MiniCorner * m)
        // The window's shadow, which the clipped video can't cast itself
        if (m > 0f) {
            Box(
                modifier = Modifier
                    .offsetTo(rect)
                    .graphicsLayer {
                        shadowElevation = MiniElevation.toPx() * m * p.coerceIn(0f, 1f)
                        shape = SmoothCornerShape(corner.dp)
                    },
            )
        }
        val coverAlpha by animateFloatAsState(
            targetValue = if (playback.firstFrame) 0f else 1f,
            animationSpec = Motion.fadeOut(),
            label = "cover",
        )
        val glass = rememberHazeState()
        Box(
            modifier = Modifier
                .offsetTo(rect)
                // What the controls' glass shows through
                .hazeSource(glass)
                .graphicsLayer {
                    alpha = if (near == null) p.coerceIn(0f, 1f) else 1f
                    shape = SmoothCornerShape(corner.dp)
                    clip = true
                },
        ) {
            Box(modifier = Modifier.covering(aspect)) {
                AndroidView(
                    factory = { TextureView(it).also { view -> player.setVideoTextureView(view) } },
                    // The player may be gone by now, released as the viewer closed
                    onRelease = { runCatching { player.clearVideoTextureView(it) } },
                    modifier = Modifier.fillMaxSize(),
                )
                if (coverAlpha > 0f) {
                    AsyncImage(
                        model = video.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = coverAlpha },
                    )
                }
            }
        }

        // Ended or failed, the video dims under what to do next, and lights again on replay
        val endDim by animateFloatAsState(
            if (phase == Phase.Open && !mini && (playback.ended || playback.error != null)) 0.35f else 0f,
            Motion.settle(),
            label = "endDim",
        )
        Box(modifier = Modifier.fillMaxSize().drawBehind { if (endDim > 0f) drawRect(Color.Black.copy(alpha = endDim)) })

        // Fingers on the video
        if (!shrunk) Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    val doubleTap = viewConfiguration.doubleTapTimeoutMillis
                    var lastTap = 0L
                    var pendingTap: Job? = null
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (phase != Phase.Open || inPip) return@awaitEachGesture
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var total = Offset.Zero
                        var gesture = Gesture.Tap
                        // Until it moves or lifts, a finger held still becomes a hold
                        val decided = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) break
                                total += change.positionChange()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (total.getDistance() > slop) {
                                    gesture = when {
                                        abs(total.x) > abs(total.y) -> Gesture.Seek
                                        total.y > 0 -> Gesture.Dismiss
                                        else -> Gesture.None
                                    }
                                    change.consume()
                                    break
                                }
                            }
                        }
                        if (decided == null) gesture = Gesture.Hold
                        when (gesture) {
                            Gesture.Tap -> {
                                if (down.uptimeMillis - lastTap < doubleTap && pendingTap?.isActive == true) {
                                    pendingTap?.cancel()
                                    lastTap = 0L
                                    togglePlaying()
                                } else {
                                    lastTap = down.uptimeMillis
                                    pendingTap = scope.launch {
                                        delay(doubleTap)
                                        chrome = !(chrome || currentSticky)
                                        touched++
                                    }
                                }
                            }

                            Gesture.Hold -> {
                                if (player.playWhenReady && !playback.ended) {
                                    fast = true
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) break
                                        change.consume()
                                    }
                                    fast = false
                                }
                            }

                            Gesture.Seek -> {
                                val start = player.currentPosition
                                val length = duration.coerceAtLeast(1L)
                                // The width spans the whole of a short video, a minute and a half of a long one
                                val span = minOf(length, 90_000L).toFloat()
                                seekTarget = start
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) break
                                    total += change.positionChange()
                                    change.consume()
                                    seekTarget = (start + total.x / size.width * span).toLong().coerceIn(0L, length)
                                }
                                seekTarget?.let { player.seekTo(it) }
                                seekTarget = null
                                touched++
                            }

                            Gesture.Dismiss -> {
                                dragging = true
                                drag = total
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) break
                                    change.consume()
                                    drag += change.positionChange()
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                }
                                val velocity = tracker.calculateVelocity()
                                if (drag.y > 96.dp.toPx() || velocity.y > 900.dp.toPx()) {
                                    onClose(Velocity(velocity.x, velocity.y))
                                } else {
                                    // Back into place at the speed it was let go with
                                    scope.launch {
                                        Animatable(drag, Offset.VectorConverter).animateTo(Offset.Zero, Motion.settle(), initialVelocity = Offset(velocity.x, velocity.y)) { drag = value }
                                        dragging = false
                                    }
                                }
                            }

                            Gesture.None -> Unit
                        }
                    }
                },
        )

        val showing = phase == Phase.Open && !dragging && !fast && !inPip && !mini && (chrome || sticky)
        val chromeAlpha by animateFloatAsState(
            targetValue = if (showing) 1f else 0f,
            animationSpec = if (showing) Motion.fadeIn() else Motion.fadeOut(),
            label = "chrome",
        )

        ProvideMediaGlass(glass) {
            // The middle: where a seek is going, or why the video is not playing
            if (phase == Phase.Open && !dragging && !inPip && !mini) {
                var loading by remember { mutableStateOf(false) }
                val waiting = playback.error == null && playback.playWhenReady &&
                    (playback.state == Player.STATE_BUFFERING || !playback.firstFrame)
                LaunchedEffect(waiting) {
                    loading = false
                    if (waiting) {
                        delay(LoaderDelay)
                        loading = true
                    }
                }
                val error = playback.error
                val signedIn = LocalAccount.current != null
                val center = when {
                    seekTarget != null -> CenterShows.Seek
                    error != null -> CenterShows.Failure
                    playback.ended -> CenterShows.Replay
                    !playback.playWhenReady -> CenterShows.Play
                    loading -> CenterShows.Loading
                    else -> CenterShows.Nothing
                }
                // Pausing, playing on, finishing and failing refocus one into the other in place
                Refocus(targetState = center, modifier = Modifier.align(Alignment.Center)) { shown ->
                    when (shown) {
                        CenterShows.Seek -> SeekTime(seekTarget ?: 0L, duration)
                        CenterShows.Failure -> error?.let { Failure(it.message(signedIn), onRetry = ::retry) }
                        CenterShows.Replay -> BigButton(Symbols.ReplayFill, stringResource(id = R.string.btn_play)) { togglePlaying() }
                        CenterShows.Play -> BigButton(Symbols.PlayArrowFill, stringResource(id = R.string.btn_play)) { togglePlaying() }
                        CenterShows.Loading -> WavyLoader(color = Color.White, modifier = Modifier.size(44.dp))
                        CenterShows.Nothing -> Unit
                    }
                }
            }

            // Only says what the hold does, so it is no control, and no glass: a dim capsule
            Refocus(
                targetState = fast && phase == Phase.Open,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 48.dp),
            ) { held ->
                if (held) Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), SmoothCornerShape.Capsule),
                ) {
                    Icon(
                        imageVector = Symbols.FastForwardFill,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(start = 14.dp).size(16.dp),
                    )
                    Text(text = "2×", color = Color.White, style = TimeStyle.copy(fontSize = 13.sp), modifier = Modifier.padding(end = 14.dp, top = 6.dp, bottom = 6.dp))
                }
            }

            if (phase == Phase.Open && !dragging && !inPip && !mini && chromeAlpha < 1f) {
                ProgressLine(
                    fraction = if (duration > 0) position.toFloat() / duration else 0f,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 6.dp)
                        .graphicsLayer { alpha = 1f - chromeAlpha },
                )
            }

            if (chromeAlpha > 0f && phase != Phase.Closing) {
                VideoChrome(
                    video = video,
                    landscape = landscape,
                    playing = playback.playWhenReady && !playback.ended,
                    ended = playback.ended,
                    position = seekTarget ?: position,
                    buffered = buffered,
                    duration = duration,
                    speed = speed,
                    onClose = { close() },
                    onTogglePlaying = { togglePlaying() },
                    onSeek = { seekTarget = it },
                    onSeekEnd = {
                        player.seekTo(it)
                        seekTarget = null
                        touched++
                    },
                    onSpeed = { anchor ->
                        touched++
                        speedMenu = menus?.show(
                            items = Speeds.map { value ->
                                ContextMenuItem(icon = null, label = speedLabel(value), selected = value == speed) { speed = value }
                            },
                            anchor = anchor,
                        )
                    },
                    turned = turned,
                    onLandscape = { setLandscape(!turned) },
                    onCopyLink = { TiebaUtil.copyText(context, video.url) },
                    onShare = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, video.url)
                        }
                        // Not a leaving of the app, which would shrink the video into its own window
                        val chooser = Intent.createChooser(intent, shareTitle)
                            .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                        runCatching { context.startActivity(chooser) }
                    },
                    onPip = { minimize() },
                    saving = viewer.saving[video.key],
                    onSave = { viewer.save(context, video) },
                    modifier = Modifier.graphicsLayer { alpha = chromeAlpha },
                )
            }

            if (shrunk && phase == Phase.Open) {
                MiniWindow(
                    playing = playback.playWhenReady && !playback.ended,
                    ended = playback.ended,
                    controlsAlpha = m,
                    onTogglePlaying = { togglePlaying() },
                    onExpand = { expand() },
                    onClose = { closeMini() },
                    onMove = { by -> miniHeld = (miniHeld ?: miniAt.value) + by },
                    onRelease = { velocity ->
                        val from = miniHeld ?: miniAt.value
                        // Where the throw would carry it: off a side closes it, else the nearest corner
                        val thrown = from + velocity * MiniThrow
                        val middle = thrown.x + miniSize.width / 2
                        if (middle < 0f || middle > container.width) {
                            closeMini()
                        } else {
                            scope.launch {
                                miniAt.snapTo(from)
                                miniHeld = null
                                miniAt.animateTo(nearestCorner(thrown), Motion.settle(), initialVelocity = velocity)
                            }
                        }
                    },
                    modifier = Modifier.offsetTo(miniRect),
                )
            }
        }
    }
}

/**
 * The video shrunk to a window over the pages: a tap opens it again, a drag moves it, and it is
 * closed by its button or thrown off a side. It holds only a play and pause button and a close
 * button.
 */
@Composable
private fun MiniWindow(
    playing: Boolean,
    ended: Boolean,
    controlsAlpha: Float,
    onTogglePlaying: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onMove: (Offset) -> Unit,
    onRelease: (velocity: Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentMove by rememberUpdatedState(onMove)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentExpand by rememberUpdatedState(onExpand)
    val expandLabel = stringResource(id = R.string.btn_full_screen)
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var total = Offset.Zero
                    var moving = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // A button under the finger took the tap
                            if (!moving && !change.isConsumed) currentExpand()
                            break
                        }
                        val delta = change.positionChange()
                        total += delta
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!moving && total.getDistance() > slop) {
                            moving = true
                            currentMove(total)
                        } else if (moving) {
                            currentMove(delta)
                        }
                        if (moving) change.consume()
                    }
                    if (moving) {
                        val v = tracker.calculateVelocity()
                        currentRelease(Offset(v.x, v.y))
                    }
                }
            }
            .semantics {
                onClick(label = expandLabel) {
                    currentExpand()
                    true
                }
            },
    ) {
        Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = controlsAlpha }) {
            MediaGlassIconButton(
                icon = Symbols.Close,
                description = stringResource(id = R.string.btn_close),
                onClick = onClose,
                size = 30.dp,
                iconSize = 18.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
            MediaGlassIconButton(
                icon = when {
                    ended -> Symbols.ReplayFill
                    playing -> Symbols.PauseFill
                    else -> Symbols.PlayArrowFill
                },
                description = stringResource(id = if (playing) R.string.btn_pause else R.string.btn_play),
                onClick = onTogglePlaying,
                size = 30.dp,
                iconSize = 18.dp,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }
    }
}

/** The mini window's width for a wide video, and its height for a tall one, at most [MiniTallest]. */
private val MiniWide = 208.dp
private val MiniTall = 128.dp
private val MiniTallest = 228.dp
private const val MiniCorner = 12f
private val MiniElevation = 12.dp
private val MiniMargin = 16.dp

/** Kept clear of the top bar and the navigation bar, whose buttons the window would cover. */
private val MiniTopClearance = 56.dp
private val MiniBottomClearance = 72.dp

/** How far ahead of a throw, in seconds of its speed, the window is taken to land. */
private const val MiniThrow = 0.12f

/**
 * Lets the video shrink into a picture-in-picture window, with a play and pause button, when the
 * user leaves the app while it plays: by itself from Android 12, on leaving before that.
 *
 * @return what shrinks it there at once, or null where the device has no such windows
 */
@Composable
private fun pictureInPicture(
    activity: Activity?,
    enabled: Boolean,
    playing: Boolean,
    aspect: Float,
    bounds: Rect,
    onToggle: () -> Unit,
): (() -> Unit)? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity !is ComponentActivity) return null
    val context = LocalContext.current
    val supported = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) }
    if (!supported) return null
    val toggle by rememberUpdatedState(onToggle)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = toggle()
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PipToggle), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val playLabel = stringResource(id = R.string.btn_play)
    val pauseLabel = stringResource(id = R.string.btn_pause)
    val params = remember(enabled, playing, aspect, bounds) {
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(PipToggle).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = if (playing) pauseLabel else playLabel
        val icon = Icon.createWithResource(context, if (playing) R.drawable.symbol_pause_fill else R.drawable.symbol_play_arrow_fill)
        // The window takes ratios between 1:2.39 and 2.39:1 only
        val ratio = (1f / aspect).coerceIn(1f / 2.39f, 2.39f)
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational((ratio * 1000).roundToInt(), 1000))
            .setActions(listOf(RemoteAction(icon, label, label, intent)))
            .setSourceRectHint(android.graphics.Rect(bounds.left.roundToInt(), bounds.top.roundToInt(), bounds.right.roundToInt(), bounds.bottom.roundToInt()))
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(enabled) }
            .build()
    }
    LaunchedEffect(params) { runCatching { activity.setPictureInPictureParams(params) } }
    val currentParams by rememberUpdatedState(params)
    val currentEnabled by rememberUpdatedState(enabled)
    DisposableEffect(activity) {
        // Before Android 12 the window opens on leaving; from 12 the params above open it
        val leave = Runnable {
            if (currentEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                runCatching { activity.enterPictureInPictureMode(currentParams) }
            }
        }
        activity.addOnUserLeaveHintListener(leave)
        onDispose {
            activity.removeOnUserLeaveHintListener(leave)
            // Closed, the viewer no longer takes the app into a window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).setActions(emptyList()).build()) }
            }
        }
    }
    return { runCatching { activity.enterPictureInPictureMode(currentParams) } }
}

private const val PipToggle = "oolong.client.video.PIP_TOGGLE"

/** The close button on top; who posted it, the actions and the playing controls along the bottom. */
@Composable
private fun BoxScope.VideoChrome(
    video: ViewerVideo,
    landscape: Boolean,
    playing: Boolean,
    ended: Boolean,
    position: Long,
    buffered: Long,
    duration: Long,
    speed: Float,
    onClose: () -> Unit,
    onTogglePlaying: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    onSpeed: (anchor: Rect) -> Unit,
    /** Whether the viewer turned the window sideways, which its button then turns back. */
    turned: Boolean,
    onLandscape: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    /** Shrinks the video into a window over the pages; null for no such button. */
    onPip: (() -> Unit)?,
    /** How much of the video is saved, 0–1 or -1 when not known; null when it is not being saved. */
    saving: Float?,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sides = if (landscape) 40.dp else 0.dp
    Box(modifier = modifier.matchParentSize()) {
        TopButton(
            icon = Symbols.Close,
            description = stringResource(id = R.string.btn_close),
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp + sides, top = 8.dp),
        )
        if (onPip != null) {
            TopButton(
                icon = Symbols.PictureInPictureAlt,
                description = stringResource(id = R.string.video_pip),
                onClick = onPip,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 12.dp + sides, top = 8.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                .navigationBarsPadding()
                .padding(start = 16.dp + sides, end = 8.dp + sides, top = 48.dp, bottom = 8.dp),
        ) {
            if (!landscape) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f).padding(bottom = 6.dp)) {
                        var expanded by remember { mutableStateOf(false) }
                        video.author?.takeIf { it.isNotBlank() }?.let {
                            Text(text = it, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        video.text?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                maxLines = if (expanded) 8 else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = PressFeedback.overlay(Corners.xs)) { expanded = !expanded },
                            )
                        }
                    }
                    MediaGlassActions {
                        RoundAction(Symbols.Link, stringResource(id = R.string.title_copy_link), onCopyLink)
                        RoundAction(Symbols.Share, stringResource(id = R.string.title_share), onShare)
                        SaveAction(saving = saving, onClick = onSave)
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = if (landscape) 0.dp else 10.dp).height(40.dp),
            ) {
                ControlButton(
                    icon = when {
                        ended -> Symbols.ReplayFill
                        playing -> Symbols.PauseFill
                        else -> Symbols.PlayArrowFill
                    },
                    description = stringResource(id = if (playing) R.string.btn_pause else R.string.btn_play),
                    onClick = onTogglePlaying,
                )
                Text(text = formatDuration(position), color = Color.White.copy(alpha = 0.9f), style = TimeStyle, modifier = Modifier.widthIn(min = 32.dp))
                Scrubber(
                    position = position,
                    buffered = buffered,
                    duration = duration,
                    onSeek = onSeek,
                    onSeekEnd = onSeekEnd,
                    modifier = Modifier.weight(1f),
                )
                Text(text = formatDuration(duration), color = Color.White.copy(alpha = 0.9f), style = TimeStyle)
                var speedBounds by remember { mutableStateOf(Rect.Zero) }
                MediaGlassPill(
                    onClick = { onSpeed(speedBounds) },
                    onClickLabel = stringResource(id = R.string.video_speed),
                    modifier = Modifier.onGloballyPositioned { speedBounds = it.boundsInWindow() },
                ) {
                    Refocus(targetState = speedLabel(speed), modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)) {
                        Text(text = it, color = Color.White, style = TimeStyle.copy(fontSize = 12.sp))
                    }
                }
                // A window already sideways has nothing to turn to
                if ((video.aspect in 0.01f..0.99f && !landscape) || turned) {
                    ControlButton(
                        icon = if (turned) Symbols.FullscreenExit else Symbols.Fullscreen,
                        description = stringResource(id = if (turned) R.string.reply_exit_full_screen else R.string.btn_full_screen),
                        onClick = onLandscape,
                    )
                }
            }
        }
    }
}

/**
 * A seek bar: thin while left alone, thicker under a finger, which may tap or drag along it. Held,
 * its knob grows and turns to clear glass over the track, as the app's slider's does.
 */
@Composable
private fun Scrubber(
    position: Long,
    buffered: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val held by animateFloatAsState(if (pressed) 1f else 0f, Motion.snap(), label = "held")
    val glassAvailable = isGlassAvailable()
    val track = 3.dp + 4.dp * held
    val length = duration.coerceAtLeast(1L)
    val fraction = (position.toFloat() / length).coerceIn(0f, 1f)
    val currentSeek by rememberUpdatedState(onSeek)
    val currentEnd by rememberUpdatedState(onSeekEnd)
    // What the held knob sees: the track, and clear space around it
    val glassState = remember { HazeState() }
    BoxWithConstraints(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(KnobHeldHeight)
            .pointerInput(length) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    fun at(x: Float) = (x / size.width).coerceIn(0f, 1f).let { (it * length).toLong() }
                    var target = at(down.position.x)
                    currentSeek(target)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) break
                        change.consume()
                        target = at(change.position.x)
                        currentSeek(target)
                    }
                    pressed = false
                    currentEnd(target)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize().hazeSource(glassState)) {
            val h = track.toPx()
            val top = (size.height - h) / 2
            val radius = CornerRadius(h / 2)
            val loaded = (buffered.toFloat() / length).coerceIn(0f, 1f) * size.width
            drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(0f, top), Size(size.width, h), radius)
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(0f, top), Size(loaded, h), radius)
            drawRoundRect(Color.White, Offset(0f, top), Size(fraction * size.width, h), radius)
        }
        val knobWidth = KnobSize + (KnobHeldWidth - KnobSize) * held
        val knobHeight = KnobSize + (KnobHeldHeight - KnobSize) * held
        val knobShape = SmoothCornerShape.Capsule
        val travel = maxWidth
        Box(
            Modifier
                .offset { IntOffset((travel * fraction - knobWidth / 2).roundToPx(), 0) }
                .requiredSize(knobWidth, knobHeight)
                .shadow(2.dp * (1f - held), knobShape, clip = false)
                // The white knob fades out as the glass fades in, where glass is drawn
                .background(Color.White.copy(alpha = if (glassAvailable) 1f - held else 1f), knobShape)
                .controlLens(glassState, knobShape, held),
        )
    }
}

private val KnobSize = 12.dp
private val KnobHeldWidth = 34.dp
private val KnobHeldHeight = 22.dp

/** How far the video has played, along the bottom while the controls are hidden. */
@Composable
private fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(2.dp)) {
        drawRect(Color.White.copy(alpha = 0.2f))
        drawRect(Color.White.copy(alpha = 0.85f), size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
    }
}

@Composable
private fun SeekTime(target: Long, duration: Long) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = formatDuration(target), color = Color.White, style = TimeStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold))
        Text(text = " / ${formatDuration(duration)}", color = Color.White.copy(alpha = 0.7f), style = TimeStyle.copy(fontSize = 16.sp), modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun Failure(message: Int, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = stringResource(id = message), color = Color.White, fontSize = 15.sp)
        MediaGlassPill(onClick = onRetry) {
            Text(
                text = stringResource(id = R.string.button_retry),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun BigButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    MediaGlassIconButton(icon = icon, description = description, onClick = onClick, size = 64.dp, iconSize = 34.dp)
}

@Composable
private fun ControlButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Refocus(targetState = icon) { Icon(imageVector = it, contentDescription = description, tint = Color.White, modifier = Modifier.size(26.dp)) }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** A button along the top, over the video. */
@Composable
private fun TopButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MediaGlassIconButton(icon = icon, description = description, onClick = onClick, modifier = modifier, size = 40.dp)
}

/** Saves the video; while it downloads, a ring fills in the button's place, which then does nothing. */
@Composable
private fun SaveAction(saving: Float?, onClick: () -> Unit) {
    val description = stringResource(id = R.string.video_save)
    // Download, waiting, the ring and back each refocus into the next; the ring fills smoothly
    val face = when {
        saving == null -> 0
        saving < 0f -> 1
        else -> 2
    }
    val filled by animateFloatAsState((saving ?: 0f).coerceAtLeast(0f), Motion.settle(Motion.ProgressThreshold), label = "saveRing")
    IconButton(onClick = onClick, enabled = saving == null) {
        Refocus(targetState = face) { now -> SaveFace(now, description) { filled } }
    }
}

/** One of the save button's faces: 0 the arrow, 1 waiting, 2 the ring at [filled]. */
@Composable
private fun SaveFace(face: Int, description: String, filled: () -> Float) {
    if (face == 0) {
        Icon(imageVector = Symbols.Download, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    } else if (face == 1) {
        WavyLoader(color = Color.White, modifier = Modifier.size(26.dp))
    } else {
        Canvas(modifier = Modifier.size(24.dp)) {
            val stroke = 2.5.dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.3f), radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 360f * filled(),
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
            )
        }
    }
}

/** Why the video stopped, as the user can act on it. */
@OptIn(UnstableApi::class)
private fun PlaybackException.message(signedIn: Boolean): Int = when {
    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> R.string.video_offline

    !signedIn && (cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode in setOf(401, 403) -> R.string.video_sign_in
    else -> R.string.video_play_failed
}

private fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×"

private val Speeds = listOf(2f, 1.5f, 1.25f, 1f, 0.75f, 0.5f)

private val TimeStyle = TextStyle(fontSize = 12.5.sp, fontFeatureSettings = "tnum")

private const val ChromeTimeout = 3_000L

/** Buffering shorter than this shows nothing, so quick stalls don't flash a loader. */
private const val LoaderDelay = 300L

/** A video of height over width [aspect], whole and centred in [container]. */
private fun fitRect(container: Size, aspect: Float): Rect {
    if (container.width <= 0f || container.height <= 0f) return Rect(Offset.Zero, container)
    return if (aspect > container.height / container.width) {
        val width = container.height / aspect
        Rect((container.width - width) / 2, 0f, (container.width + width) / 2, container.height)
    } else {
        val height = container.width * aspect
        Rect(0f, (container.height - height) / 2, container.width, (container.height + height) / 2)
    }
}

/** This rect as a drag of [offset], scaled by [scale] about the container's centre, leaves it. */
private fun Rect.dragged(offset: Offset, scale: Float, container: Size): Rect {
    val centre = Offset(container.width / 2, container.height / 2)
    val topLeft = centre + (topLeft - centre) * scale + offset
    return Rect(topLeft, Size(width * scale, height * scale))
}

private fun Rect.scaledAround(scale: Float): Rect =
    Rect(center - Offset(width * scale / 2, height * scale / 2), Size(width * scale, height * scale))

/** Lays the content out at height over width [aspect], covering the space it is given, centred. */
private fun Modifier.covering(aspect: Float): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val (w, h) = when {
        aspect <= 0f || width == 0 -> width to height
        height.toFloat() / width > aspect -> (height / aspect).toInt() to height
        else -> width to (width * aspect).toInt()
    }
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(width, height) { placeable.place((width - w) / 2, (height - h) / 2) }
}

/** What the middle of the video shows over the picture. */
private enum class CenterShows { Nothing, Seek, Failure, Replay, Play, Loading }
