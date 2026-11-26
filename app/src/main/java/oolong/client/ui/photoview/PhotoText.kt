package oolong.client.ui.photoview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.R
import oolong.client.media.automask.Ability
import oolong.client.media.automask.AutoMaskDownload
import oolong.client.media.automask.AutoMaskFiles
import oolong.client.media.automask.PictureText
import oolong.client.media.automask.ReadLine
import oolong.client.media.image.downloadImage
import oolong.client.ui.designsystem.MediaGlassPill
import oolong.client.ui.designsystem.Refocus
import oolong.client.util.TiebaUtil
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Picking text out of the picture shown: the picture read off its original, the lines found
 * lit as they are, then read, the line pressed first; a selection from one character to
 * another, across lines, to copy.
 */
@Stable
internal class PhotoTextState(
    private val context: Context,
    private val files: AutoMaskFiles,
    private val scope: CoroutineScope,
) {
    /** The picture whose text is picked from; null when none is. */
    var url by mutableStateOf<String?>(null)
        private set

    /** The picture as read, the selection's and the lines' pixels its own. */
    var picture by mutableStateOf<Bitmap?>(null)
        private set

    /** Where the lines light up from, in the picture's pixels: where it was pressed, or its top. */
    var origin by mutableStateOf<Offset?>(null)
        private set

    /** The lines found, lit while they are read. */
    var found by mutableStateOf<List<RectF>>(emptyList())
        private set

    var text by mutableStateOf<PictureText?>(null)
        private set

    /** The characters picked, first and last, in [text]'s order. */
    var selection by mutableStateOf<IntRange?>(null)

    var reading by mutableStateOf(false)
        private set

    /** The handle a finger drags, and where the finger is on screen. */
    var held by mutableStateOf<Handle?>(null)
    var finger by mutableStateOf<Offset?>(null)

    /** Asked for before text recognition was downloaded: picked from once it is. */
    var waiting by mutableStateOf<String?>(null)
        private set

    val active: Boolean get() = url != null || waiting != null

    private var job: Job? = null

    /** A long press on a line not read yet, as a share of the picture: picked once it is. */
    private var pending: Offset? = null

    val download get() = files.state(Ability.Text)

    val supported: Boolean get() = files.supported

    /**
     * Picks text out of the picture at [url], [pressed] where on it a long press was, as a share
     * of its width and height, or null for all of it; false when it cannot be read yet.
     */
    fun start(url: String, pressed: Offset?, onMissed: () -> Unit = {}): Boolean {
        if (files.state(Ability.Text).value != AutoMaskDownload.Ready) return false
        if (this.url == url) {
            // Still being read, the line is picked once it is
            if (pressed != null && !selectLineAt(pressed)) pending = pressed
            return true
        }
        stop()
        this.url = url
        reading = true
        job = scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { context.downloadImage(url)?.let(::decode) } ?: return@launch stopAfterMiss(onMissed)
                picture = bitmap
                val finder = files.finder() ?: return@launch stopAfterMiss(onMissed)
                val reader = files.reader() ?: return@launch stopAfterMiss(onMissed)
                val lines = withContext(Dispatchers.Default) { finder.find(bitmap) }
                if (lines.isEmpty()) return@launch stopAfterMiss(onMissed)
                origin = pressed?.let { Offset(it.x * bitmap.width, it.y * bitmap.height) } ?: Offset(bitmap.width / 2f, 0f)
                found = lines
                // The line pressed first, picked whole, the rest after
                val at = pressed?.let { Offset(it.x * bitmap.width, it.y * bitmap.height) }
                val reach = bitmap.width * 0.04f
                val first = at?.let { p -> lines.minByOrNull { gap(it, p) }?.takeIf { gap(it, p) <= reach } }
                if (pressed != null && first == null) return@launch stopAfterMiss(onMissed)
                val read = mutableListOf<ReadLine>()
                if (first != null) {
                    read += withContext(Dispatchers.Default) { reader.read(bitmap, first) }
                    show(read)
                    selection = text?.takeIf { !it.isEmpty }?.let { 0..it.glyphs.lastIndex }
                }
                for ((i, line) in lines.withIndex()) {
                    if (line === first) continue
                    read += withContext(Dispatchers.Default) { reader.read(bitmap, line) }
                    if (i % 6 == 5) show(read)
                }
                show(read)
                if (text?.isEmpty != false) stopAfterMiss(onMissed)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("PhotoText", "could not read the picture", e)
                stop()
            } finally {
                reading = false
            }
        }
        return true
    }

    /** Offers text recognition for download, the picture at [url] read once it is. */
    fun startAfterDownload(url: String) {
        stop()
        waiting = url
    }

    fun fetch() = files.start(Ability.Text)

    /** Called as the download goes: picks text out of the picture waited for once it is done. */
    fun downloaded() {
        val url = waiting ?: return
        waiting = null
        start(url, null)
    }

    fun cancelWaiting() {
        waiting = null
    }

    private fun stopAfterMiss(onMissed: () -> Unit) {
        stop()
        onMissed()
    }

    /** The text read so far, the selection kept on the same characters. */
    private fun show(read: List<ReadLine>) {
        val before = text
        val picked = selection
        val anchors = if (before != null && picked != null) before.glyphs[picked.first] to before.glyphs[picked.last] else null
        val now = PictureText(read.toList())
        text = now
        pending?.let { if (selectLineAt(it)) pending = null }
        if (anchors != null) {
            val from = now.nearestGlyph(anchors.first.centerX, anchors.first.centerY)
            val to = now.nearestGlyph(anchors.second.centerX, anchors.second.centerY)
            selection = if (from != null && to != null) min(from, to)..max(from, to) else null
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        url = null
        picture = null
        found = emptyList()
        origin = null
        text = null
        selection = null
        reading = false
        waiting = null
        pending = null
    }

    /** Picks the line at [share] of the picture's width and height; true when there was one. */
    fun selectLineAt(share: Offset): Boolean {
        val bitmap = picture ?: return false
        val text = text ?: return false
        val glyph = text.glyphAt(share.x * bitmap.width, share.y * bitmap.height, reach = bitmap.width * 0.02f) ?: return false
        selection = text.lineOf(glyph)
        return true
    }

    fun selectAll() {
        val text = text ?: return
        if (!text.isEmpty) selection = 0..text.glyphs.lastIndex
    }

    /** Where what is picked shows on screen, the picture shown in [display]; null when nothing is. */
    fun selectionBounds(display: Rect): Rect? {
        val text = text ?: return null
        val picture = picture ?: return null
        val picked = selection ?: return null
        if (display.isEmpty) return null
        val k = display.width / picture.width
        val glyphs = picked.map { text.glyphs[it] }
        return Rect(
            display.left + glyphs.minOf { it.left } * k,
            display.top + glyphs.minOf { it.top } * k,
            display.left + glyphs.maxOf { it.right } * k,
            display.top + glyphs.maxOf { it.bottom } * k,
        )
    }

    val selectedText: String
        get() = selection?.let { text?.text(it.first, it.last) }.orEmpty()

    private fun gap(r: RectF, p: Offset) = max(max(r.left - p.x, 0f), p.x - r.right) + max(max(r.top - p.y, 0f), p.y - r.bottom)

    companion object {
        /** Most pixels a picture is read at: a long one is halved until it fits. */
        private const val MostPixels = 16_000_000

        private fun decode(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth.toLong() * bounds.outHeight / (sample * sample) > MostPixels) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }
}

/** Which end of a selection a finger holds. */
internal enum class Handle { Start, End }

private val SelectionColor = Color(0xFF4C8DFF)

private val ToolbarColor = Color(0xF2242426)

/** How long the lines found take to light up, from the press out to the furthest. */
private const val SweepMillis = 450

/**
 * Lets a finger drag the selection's handles, character by character, the text as read at each
 * step, the other end held where it is on the picture. Put on what holds the picture, it sees a
 * press before the picture does and takes it only when it is on a handle: the picture still
 * pans, zooms and answers taps and long presses everywhere else.
 */
internal fun Modifier.selectionHandles(state: PhotoTextState, display: () -> Rect, view: View): Modifier = pointerInput(state) {
    val grab = 28.dp.toPx()
    val aim = 12.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val text = state.text ?: return@awaitEachGesture
        val picture = state.picture ?: return@awaitEachGesture
        val picked = state.selection ?: return@awaitEachGesture
        val rect = display()
        val k = rect.width / picture.width
        fun bar(g: PictureText.Glyph, end: Boolean): Pair<Offset, Offset> {
            val x = rect.left + (if (end) g.right else g.left) * k
            return Offset(x, rect.top + g.top * k) to Offset(x, rect.top + g.bottom * k)
        }

        // How far a press is from a handle: its bar along the line, and the dot past it
        fun distance(p: Offset, ends: Pair<Offset, Offset>): Float {
            val y = p.y.coerceIn(ends.first.y - grab, ends.second.y + grab)
            return (p - Offset(ends.first.x, y)).getDistance()
        }
        val first = text.glyphs[picked.first]
        val last = text.glyphs[picked.last]
        val toEnd = distance(down.position, bar(last, end = true))
        val toStart = distance(down.position, bar(first, end = false))
        val handle = when {
            toEnd <= grab && toEnd <= toStart -> Handle.End
            toStart <= grab -> Handle.Start
            else -> return@awaitEachGesture
        }
        down.consume()
        state.held = handle
        state.finger = down.position
        // The other end, as a place on the picture: the text may be laid out again meanwhile
        val other = if (handle == Handle.End) first else last
        val fixedAt = Offset(other.centerX, other.centerY)
        var lastGlyph = -1
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                change.consume()
                state.finger = change.position
                val now = display()
                val current = state.text ?: break
                val scale = now.width / picture.width
                // Aimed a little above the finger, at the line it is held under
                val x = (change.position.x - now.left) / scale
                val y = (change.position.y - aim - now.top) / scale
                val glyph = current.nearestGlyph(x, y) ?: continue
                val fixed = current.nearestGlyph(fixedAt.x, fixedAt.y) ?: continue
                if (glyph != lastGlyph) {
                    if (lastGlyph >= 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    lastGlyph = glyph
                }
                state.selection = min(fixed, glyph)..max(fixed, glyph)
            }
        } finally {
            state.held = null
            state.finger = null
        }
    }
}

/**
 * The text picked from over the picture, which shows in [display] on screen: the lines found,
 * lit, brighter while they are read; the selection with a handle at each end, and a loupe over
 * the finger dragging one. It only draws; [selectionHandles] takes the handles.
 */
@Composable
internal fun PhotoTextOverlay(state: PhotoTextState, display: () -> Rect, modifier: Modifier = Modifier) {
    // With motion reduced the lines being read hold one brightness rather than pulsing
    val reduced = LocalReduceMotion.current
    // Pulsing only while reading, and easing back to rest when done rather than dropping to it
    val pulse = remember { Animatable(RestingLight) }
    LaunchedEffect(state.reading, reduced) {
        if (state.reading && !reduced) {
            while (true) {
                pulse.animateTo(ReadingLight, tween(PulseMillis))
                pulse.animateTo(RestingLight, tween(PulseMillis))
            }
        } else {
            pulse.animateTo(RestingLight, Motion.settle())
        }
    }
    // The lines found light up outward from where it was pressed, as a ripple through the text
    val sweep = remember(state.found) { Animatable(0f) }
    LaunchedEffect(state.found) { if (state.found.isNotEmpty()) sweep.animateTo(1f, tween(SweepMillis)) }
    Canvas(modifier = modifier.fillMaxSize()) {
        val picture = state.picture ?: return@Canvas
        val rect = display()
        if (rect.isEmpty) return@Canvas
        val k = rect.width / picture.width
        fun box(r: RectF) = Rect(rect.left + r.left * k, rect.top + r.top * k, rect.left + r.right * k, rect.top + r.bottom * k)
        // The lines found: brighter while being read
        val lit = pulse.value
        val from = state.origin ?: Offset.Zero
        val reach = hypot(max(from.x, picture.width - from.x), max(from.y, picture.height - from.y)).coerceAtLeast(1f)
        for (line in state.found) {
            val r = box(line)
            // Lit once the ripple reaches it, over the last part of its way
            val distance = hypot(line.centerX() - from.x, line.centerY() - from.y) / reach
            val shown = ((sweep.value * 1.25f - distance) / 0.25f).coerceIn(0f, 1f)
            if (shown > 0f) drawRoundRect(Color.White.copy(alpha = lit * shown), r.topLeft, r.size, CornerRadius(r.height * 0.2f))
        }
        val text = state.text ?: return@Canvas
        val picked = state.selection ?: return@Canvas
        // The selection, a piece of each line it covers
        var i = picked.first
        while (i <= picked.last) {
            val line = text.glyphs[i].line
            var j = i
            while (j + 1 <= picked.last && text.glyphs[j + 1].line == line) j++
            val a = text.glyphs[i]
            val b = text.glyphs[j]
            val r = box(RectF(a.left, a.top, b.right, b.bottom))
            drawRect(SelectionColor.copy(alpha = 0.38f), r.topLeft, r.size)
            i = j + 1
        }
        val first = text.glyphs[picked.first]
        val last = text.glyphs[picked.last]
        drawHandle(box(RectF(first.left, first.top, first.left, first.bottom)), start = true)
        drawHandle(box(RectF(last.right, last.top, last.right, last.bottom)), start = false)
        state.finger?.let { drawLoupe(it, picture, rect, state.held != null) }
    }
}

/** A selection's end: a bar along the line's height, a dot past it. */
private fun DrawScope.drawHandle(bar: Rect, start: Boolean) {
    val width = 2.dp.toPx()
    drawRect(SelectionColor, Offset(bar.left - width / 2, bar.top), Size(width, bar.height))
    val dot = 6.dp.toPx()
    drawCircle(SelectionColor, dot, Offset(bar.left, if (start) bar.top - dot * 0.6f else bar.bottom + dot * 0.6f))
}

/** The picture around the finger, bigger, over it, so what is under the finger can be seen. */
private fun DrawScope.drawLoupe(at: Offset, picture: Bitmap, rect: Rect, shown: Boolean) {
    if (!shown) return
    val radius = 52.dp.toPx()
    val zoom = 1.8f
    val centre = Offset(at.x, (at.y - radius - 40.dp.toPx()).coerceAtLeast(radius))
    val k = rect.width / picture.width
    // The picture's pixels around the finger, a little above it where it aims
    val px = (at.x - rect.left) / k
    val py = (at.y - 12.dp.toPx() - rect.top) / k
    val half = radius / (k * zoom)
    val src = IntOffset((px - half).roundToInt(), (py - half).roundToInt())
    val srcSize = IntSize((half * 2).roundToInt().coerceAtLeast(1), (half * 2).roundToInt().coerceAtLeast(1))
    val circle = Path().apply { addOval(Rect(centre, radius)) }
    drawCircle(Color.Black, radius, centre)
    clipPath(circle) {
        drawImage(
            picture.asImageBitmap(),
            srcOffset = src,
            srcSize = srcSize,
            dstOffset = IntOffset((centre.x - radius).roundToInt(), (centre.y - radius).roundToInt()),
            dstSize = IntSize((radius * 2).roundToInt(), (radius * 2).roundToInt()),
        )
    }
    drawCircle(Color.White.copy(alpha = 0.8f), radius, centre, style = Stroke(1.5.dp.toPx()))
}

/** Where [p] falls in [rect], as a share of its width and height; null outside it. */
internal fun shareOf(rect: Rect, p: Offset): Offset? {
    if (rect.isEmpty || !rect.contains(p)) return null
    return Offset((p.x - rect.left) / rect.width, (p.y - rect.top) / rect.height)
}

/** What the bar at the bottom says while text is picked, each refocusing into the next. */
private enum class BarPhase { Missing, Failed, Downloading, Reading, Picking }

/**
 * The bar at the bottom while text is picked from a picture: what text recognition still needs,
 * that the picture is being read, or, nothing picked yet, picking all. What is picked is acted
 * on beside it instead, by [SelectionToolbar].
 */
@Composable
internal fun PhotoTextBar(state: PhotoTextState, modifier: Modifier = Modifier) {
    val download by state.download.collectAsState()
    val phase = when {
        !state.active -> null
        state.waiting != null -> when (download) {
            is AutoMaskDownload.Missing -> BarPhase.Missing
            is AutoMaskDownload.Failed -> BarPhase.Failed
            is AutoMaskDownload.Running -> BarPhase.Downloading
            AutoMaskDownload.Ready -> BarPhase.Reading
        }
        state.text == null -> BarPhase.Reading
        state.selection == null -> BarPhase.Picking
        else -> null
    }
    AnimatedVisibility(
        visible = phase != null,
        enter = if (LocalReduceMotion.current) fadeIn(Motion.fadeIn()) else fadeIn(Motion.fadeIn()) + scaleIn(Motion.settle(), initialScale = 0.92f),
        exit = if (LocalReduceMotion.current) fadeOut(Motion.fadeOut()) else fadeOut(Motion.fadeOut()) + scaleOut(Motion.settle(), targetScale = 0.92f),
        modifier = modifier.navigationBarsPadding().padding(bottom = 24.dp),
    ) {
        // Kept while it leaves, so it fades out as it was
        val shown = remember { mutableStateOf(BarPhase.Reading) }
        if (phase != null) shown.value = phase
        MediaGlassPill {
            Refocus(targetState = shown.value) { now ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (now) {
                        BarPhase.Missing -> {
                            Label(stringResource(R.string.photo_extract_download, ((((download as? AutoMaskDownload.Missing)?.bytes ?: 0L) + 999_999) / 1_000_000).toInt()))
                            Action(stringResource(R.string.photo_download), state::fetch)
                        }
                        BarPhase.Failed -> {
                            Label(stringResource(R.string.photo_download_failed))
                            Action(stringResource(R.string.photo_retry), state::fetch)
                        }
                        BarPhase.Downloading -> Label(stringResource(R.string.photo_downloading, (((download as? AutoMaskDownload.Running)?.progress ?: 0f) * 100).toInt()))
                        BarPhase.Reading -> Label(stringResource(R.string.photo_reading))
                        BarPhase.Picking -> Action(stringResource(R.string.photo_select_all), state::selectAll)
                    }
                }
            }
        }
    }
}

/**
 * What can be done with what is picked, beside it: above it, or below it near the top of the
 * screen. It moves with the picture as it pans, glides to a new selection, and makes way while
 * a handle is dragged.
 */
@Composable
internal fun SelectionToolbar(state: PhotoTextState, display: () -> Rect, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.union(WindowInsets.captionBar).getTop(density)
    val bottom = WindowInsets.navigationBars.getBottom(density)
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Where it goes, given the picture as shown now and how big it is
    fun target(width: Int, height: Int): Offset? {
        val bounds = state.selectionBounds(display()) ?: return null
        val gap = with(density) { 14.dp.toPx() }
        val margin = with(density) { 16.dp.toPx() }
        val x = (bounds.center.x - size.width / 2f).coerceIn(margin, (width - size.width - margin).coerceAtLeast(margin))
        val highest = top + margin
        val lowest = (height - bottom - margin - size.height).coerceAtLeast(highest)
        val above = bounds.top - gap - size.height
        val below = bounds.bottom + gap * 2
        // Above it, else below it; a selection filling the screen has it over its own top
        val y = when {
            above >= highest -> above
            below <= lowest -> below
            else -> (bounds.top + gap).coerceIn(highest, lowest)
        }
        return Offset(x, y)
    }
    val place = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var placed by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        // A new selection is glided to; the picture panning is followed at once
        LaunchedEffect(state.selection, size) {
            val to = target(width, height) ?: return@LaunchedEffect
            // Its first place, or its size just known: put there, not glided to
            if (placed && size != IntSize.Zero) place.animateTo(to, Motion.settle()) else place.snapTo(to)
            placed = size != IntSize.Zero
        }
        LaunchedEffect(Unit) {
            snapshotFlow { display() }.collect { if (!place.isRunning) target(width, height)?.let { place.snapTo(it) } }
        }
        AnimatedVisibility(
            visible = state.selection != null && state.held == null,
            enter = if (LocalReduceMotion.current) fadeIn(Motion.fadeIn()) else fadeIn(Motion.fadeIn()) + scaleIn(Motion.settle(), initialScale = 0.9f),
            exit = if (LocalReduceMotion.current) fadeOut(Motion.fadeOut()) else fadeOut(Motion.fadeOut()) + scaleOut(Motion.settle(), targetScale = 0.9f),
            modifier = Modifier
                .offset { place.value.round() }
                .onSizeChanged { size = it },
        ) {
            // Solid, as a system's text menu is: it sits right on the text, over whatever the
            // picture has there, and clear glass over a light picture would leave it unreadable
            Row(
                modifier = Modifier
                    .shadow(8.dp, SmoothCornerShape.Capsule, clip = false)
                    .background(ToolbarColor, SmoothCornerShape.Capsule),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Copied, it is done with
                Action(stringResource(R.string.photo_copy)) {
                    TiebaUtil.copyText(context, state.selectedText)
                    state.stop()
                }
                Action(stringResource(R.string.photo_select_all), state::selectAll)
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text = text, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
}

@Composable
private fun Action(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clickable(interactionSource = null, indication = PressFeedback.overlay(SmoothCornerShape.Capsule), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** The buzz of something that did not take. */
internal fun View.reject() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
}

/** How bright the lines found are at rest, and at the top of a pulse while being read. */
private const val RestingLight = 0.12f
private const val ReadingLight = 0.3f
private const val PulseMillis = 700
