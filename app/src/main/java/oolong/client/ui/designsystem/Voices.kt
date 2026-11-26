package oolong.client.ui.designsystem

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import kotlinx.coroutines.delay
import oolong.client.R
import oolong.client.media.FileUtil
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape

/**
 * A voice post: a capsule with a play button and its length, filled a shade darker as far as it
 * has played. Longer voices draw longer capsules. Dragging along it moves the playing position;
 * one voice plays at a time, wherever it is; a long press saves it.
 *
 * @param duration its length in milliseconds
 */
@Composable
fun VoicePlayer(
    url: String,
    duration: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = ExtendedTheme.colors
    val playing = VoicePlayback.url == url
    val state = if (playing) VoicePlayback.state else VoiceState.Idle
    val position = if (playing) VoicePlayback.position else 0L
    val length = duration.toLong().takeIf { it > 0 } ?: VoicePlayback.duration.takeIf { playing } ?: 0L
    // Where a drag along the capsule has moved the position to, as a fraction, while dragging
    var dragged by remember { mutableStateOf<Float?>(null) }
    val played = if (length > 0) (position.toFloat() / length).coerceIn(0f, 1f) else 0f
    val fraction = dragged ?: played
    // The gesture outlives this composition; it reads where the voice is now through this
    val playedNow by rememberUpdatedState(played)

    // Stops when the voice leaves the screen
    DisposableEffect(url) {
        onDispose { if (VoicePlayback.url == url) VoicePlayback.stop() }
    }
    LaunchedEffect(playing, state) {
        while (playing && state == VoiceState.Playing) {
            VoicePlayback.updatePosition()
            delay(PositionInterval)
        }
    }

    val save = stringResource(id = R.string.menu_save_audio)
    Box(
        modifier = modifier
            .longPressMenu(
                items = { listOf(ContextMenuItem(Symbols.Download, save) { saveVoice(context, url) }) },
                onClick = { VoicePlayback.toggle(context, url) },
                indication = PressFeedback.overlay(SmoothCornerShape.Capsule),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .width(widthOf(length))
                .height(40.dp)
                .background(colors.accent.copy(alpha = 0.10f), SmoothCornerShape.Capsule)
                .drawBehind {
                    val outline = SmoothCornerShape.Capsule.createOutline(size, layoutDirection, this)
                    clipRect(right = size.width * fraction) { drawOutline(outline, colors.accent.copy(alpha = 0.22f)) }
                }
                .pointerInput(url, length) {
                    if (length <= 0) return@pointerInput
                    // By how far the finger moves, not where it lands: a drag that starts on the
                    // play button does not jump to the start
                    detectHorizontalDragGestures(
                        onDragStart = { dragged = playedNow },
                        onDragEnd = {
                            dragged?.let { VoicePlayback.seek(context, url, (it * length).toLong()) }
                            dragged = null
                        },
                        onDragCancel = { dragged = null },
                    ) { change, amount ->
                        change.consume()
                        dragged = ((dragged ?: playedNow) + amount / size.width).coerceIn(0f, 1f)
                    }
                }
                .padding(start = 6.dp, end = 14.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(28.dp).background(colors.accent, CircleShape),
            ) {
                when (state) {
                    VoiceState.Loading -> CircularProgressIndicator(
                        color = colors.onAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                    VoiceState.Playing -> Icon(Symbols.PauseFill, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
                    VoiceState.Idle -> Icon(Symbols.PlayArrowFill, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatTime(
                    when {
                        dragged != null -> (fraction * length).toLong()
                        state == VoiceState.Idle -> length
                        else -> position
                    }
                ),
                color = colors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

private const val PositionInterval = 100L

/** Longer for longer voices, between a short and a long capsule; a minute or more is the longest. */
private fun widthOf(length: Long) = (MinWidth.value + (MaxWidth.value - MinWidth.value) * (length / 60_000f).coerceIn(0f, 1f)).dp

private val MinWidth = 140.dp
private val MaxWidth = 240.dp

private fun formatTime(millis: Long): String {
    val seconds = (millis / 1000).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun saveVoice(context: Context, url: String) {
    val md5 = url.toUri().getQueryParameter("voice_md5")
    FileUtil.downloadBySystem(context, FileUtil.FILE_TYPE_AUDIO, url, (md5 ?: System.currentTimeMillis().toString()) + ".mp3")
}

enum class VoiceState { Idle, Loading, Playing }

/** The one voice playing, if any: starting another stops it. */
@Stable
object VoicePlayback {
    var url by mutableStateOf<String?>(null)
        internal set
    var state by mutableStateOf(VoiceState.Idle)
        internal set
    var position by mutableLongStateOf(0L)
        internal set
    var duration by mutableLongStateOf(0L)
        private set

    private var player: ExoPlayer? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> duration = player?.duration?.coerceAtLeast(0) ?: 0
                Player.STATE_ENDED -> stop()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) state = VoiceState.Playing else if (state == VoiceState.Playing) state = VoiceState.Idle
        }
    }

    /** Plays [url], or pauses it if it is the one playing. */
    fun toggle(context: Context, url: String) {
        val current = player
        if (this.url == url && current != null) {
            if (current.isPlaying) current.pause() else current.play()
            return
        }
        stop()
        this.url = url
        state = VoiceState.Loading
        position = 0
        duration = 0
        player = buildPlayer(context).apply {
            addListener(listener)
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }
    }

    /** Plays [url] from [position], from where it is if it is the one playing. */
    fun seek(context: Context, url: String, position: Long) {
        if (this.url != url || player == null) toggle(context, url)
        player?.run {
            seekTo(position)
            play()
        }
        this.position = position
    }

    fun updatePosition() {
        player?.let { position = it.currentPosition }
    }

    fun stop() {
        player?.run {
            removeListener(listener)
            release()
        }
        player = null
        url = null
        state = VoiceState.Idle
        position = 0
    }

    /**
     * Speech that takes the audio focus and pauses when the headphones are pulled out, decoded
     * with an extension such as FFmpeg where there is one, for formats the system cannot play.
     * Voices are short, so seeking reads the file up to the position instead of estimating where
     * it lies, and works in files without a seek table.
     */
    @OptIn(UnstableApi::class)
    private fun buildPlayer(context: Context): ExoPlayer {
        val app = context.applicationContext
        return ExoPlayer.Builder(app)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setRenderersFactory(DefaultRenderersFactory(app).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
            .setMediaSourceFactory(DefaultMediaSourceFactory(app, DefaultExtractorsFactory().setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)))
            .build()
    }
}
