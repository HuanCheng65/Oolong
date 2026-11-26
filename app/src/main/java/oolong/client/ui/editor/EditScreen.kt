package oolong.client.ui.editor

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import oolong.client.R
import oolong.client.media.automask.AutoMaskDownload
import oolong.client.ui.designsystem.Button
import oolong.client.ui.designsystem.ConfirmDialog
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.IconButton
import oolong.client.ui.designsystem.LocalPressedByGlass
import oolong.client.ui.designsystem.floatingGlass
import oolong.client.ui.designsystem.mediaChromeColors
import oolong.client.ui.designsystem.pageScroll
import oolong.client.ui.designsystem.pressedByGlass
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.LocalExtendedColors
import oolong.client.ui.designsystem.theme.Symbols
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import kotlin.math.abs
import kotlin.math.max

/** The space kept between the picture and the bars. */
private val PictureMargin = 16.dp

/** How near an edge or corner of the frame a finger takes hold of it. */
private val FrameGrip = 24.dp

/** What the editor writes: the picture as framed, with its strokes and captions. */
class EditResult(
    val framing: Framing,
    val strokes: List<Edit>,
    val captions: List<Edit.Caption>,
    /** The steps that led here, kept so the picture can be edited again from where it was left. */
    val steps: List<Edit>,
    val adjustments: Map<Adjustment, Float> = NoAdjustments,
)

/**
 * The editor: the picture between the bars, cancel, undo and redo, and done above, the tools
 * below; picking a tool opens its options from it.
 */
@Composable
fun EditScreen(
    renderer: EditRenderer,
    saving: Boolean,
    onCancel: () -> Unit,
    onDone: (EditResult) -> Unit,
    initial: List<Edit> = emptyList(),
    pictureHidden: Boolean = false,
    onReady: (EditorState) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state = remember(renderer) { EditorState(renderer, scope, initial) }
    state.hidden = pictureHidden
    // Once the picture has its place, whoever opened the editor may fly a copy to it
    LaunchedEffect(state, state.laidOut) { if (state.laidOut) onReady(state) }
    // The screen shows an adjusted copy, made again as the ruler moves: from a small copy while it
    // moves, so each change shows as the finger goes, and as large as a screen needs once it rests.
    // Each copy is finished and shown before the latest change is taken up.
    LaunchedEffect(state) {
        val picture = renderer.picture
        val (moving, resting) = withContext(Dispatchers.Default) {
            val resting = fitted(picture, previewSide(picture.width, picture.height))
            fitted(resting, MovingSide) to resting
        }
        snapshotFlow { state.adjustments to (state.draftAdjustments != null) }
            .conflate()
            .collect { (values, dragging) ->
                renderer.base = if (values.changesNothing()) {
                    picture
                } else {
                    withContext(Dispatchers.Default) { adjust(if (dragging) moving else resting, values) }
                }
            }
    }
    state.density = density.density
    val discardDialog = rememberDialogState()
    val leave = {
        state.finishCaption()
        if (state.edited) discardDialog.show() else onCancel()
    }

    BackHandler {
        when {
            state.draft != null -> state.finishCaption()
            state.tool != null -> state.select(null)
            else -> leave()
        }
    }

    ConfirmDialog(
        dialogState = discardDialog,
        onConfirm = onCancel,
        confirmText = stringResource(id = R.string.editor_discard),
        cancelText = stringResource(id = R.string.editor_keep_editing),
    ) {
        Text(text = stringResource(id = R.string.editor_discard_title))
    }

    // The bars are glass on black, toned as over any picture
    val colors = ExtendedTheme.colors
    val chrome = remember(colors) { mediaChromeColors(colors) }
    CompositionLocalProvider(LocalExtendedColors provides chrome) {
        BoxWithConstraints {
            val layout = when {
                maxWidth > maxHeight && maxHeight < 600.dp -> EditorLayout.Landscape
                maxWidth >= 600.dp -> EditorLayout.Side
                else -> EditorLayout.Bottom
            }
            Scaffold(state, saving, layout, leave, onDone)
        }
    }
}

/** Where the editor's controls go. */
private enum class EditorLayout {
    /** A phone upright: the bar above, the tools along the bottom. */
    Bottom,

    /** Wide, as a tablet: the bar above, the tools down the end. */
    Side,

    /** A phone on its side, short of height: no bar; cancel and undo down the start, done and the tools down the end. */
    Landscape,
}

@Composable
private fun Scaffold(
    state: EditorState,
    saving: Boolean,
    layout: EditorLayout,
    leave: () -> Unit,
    onDone: (EditResult) -> Unit,
) {
    val density = LocalDensity.current
    var railWidth by remember { mutableIntStateOf(0) }
    var startWidth by remember { mutableIntStateOf(0) }
    val done: () -> Unit = {
        state.finishCaption()
        val history = state.history
        onDone(EditResult(history.framing, history.strokes, history.captions, history.applied.toList(), history.adjustments))
    }
    GlassScaffold(
        backgroundColor = Color.Black,
        topBar = {
            if (layout != EditorLayout.Landscape) {
                GlassTopBar(
                    title = { UndoRedo(state) },
                    centerTitle = true,
                    navigationIcon = { CancelButton(leave) },
                    actionsInCapsule = false,
                    actions = { DoneButton(saving, done) },
                )
            }
        },
        bottomBar = { if (layout == EditorLayout.Bottom) EditorBottom(state) },
        overlay = {
            if (layout != EditorLayout.Bottom) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp)
                        .padding(top = if (layout == EditorLayout.Side) TopBarRoom else 8.dp, bottom = 8.dp),
                ) {
                    EditorTools(state, side = true, onRail = { railWidth = it })
                    if (layout == EditorLayout.Landscape) {
                        CancelButton(leave, Modifier.align(Alignment.TopStart))
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .onSizeChanged { startWidth = it.width },
                        ) { UndoRedo(state, vertical = true) }
                        DoneButton(saving, done, Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        },
    ) { padding ->
        val direction = LocalLayoutDirection.current
        val safe = WindowInsets.safeDrawing.asPaddingValues()
        val insets = when (layout) {
            EditorLayout.Bottom -> padding
            EditorLayout.Side -> PaddingValues(
                start = padding.calculateStartPadding(direction),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(direction) + with(density) { railWidth.toDp() } + 16.dp,
                bottom = padding.calculateBottomPadding(),
            )
            // The picture takes the whole height between the two sides
            EditorLayout.Landscape -> PaddingValues(
                start = safe.calculateStartPadding(direction) + 16.dp + with(density) { startWidth.toDp() },
                top = safe.calculateTopPadding(),
                end = safe.calculateEndPadding(direction) + 16.dp + with(density) { railWidth.toDp() },
                bottom = safe.calculateBottomPadding(),
            )
        }
        Picture(state, insets)
    }
}

/** Room above the side tools for the bar. */
private val TopBarRoom = 64.dp

/** The room around the cancel's text to press it by. */
private val CancelRoom = 16.dp

/**
 * Bare text with room around it to press, the room reaching out past where it is put: what
 * shows of it, its text, is on the page's margin, as the done button's pill is on the other side.
 */
@Composable
private fun CancelButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(id = R.string.button_cancel),
        color = ExtendedTheme.colors.text,
        style = MaterialTheme.typography.button,
        modifier = modifier
            .offset(x = -CancelRoom)
            // On the bar's glass, which answers the press
            .clickable(interactionSource = null, indication = if (pressedByGlass()) null else PressFeedback.overlay(SmoothCornerShape.Capsule), onClick = onClick)
            .padding(horizontal = CancelRoom, vertical = 12.dp),
    )
}

@Composable
private fun DoneButton(saving: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = !saving,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        modifier = modifier,
    ) {
        Text(text = stringResource(id = R.string.button_finish))
    }
}

@Composable
private fun UndoRedo(state: EditorState, vertical: Boolean = false) {
    val buttons: @Composable () -> Unit = {
        IconButton(onClick = state::undo, enabled = state.history.canUndo || state.draft != null) {
            Icon(imageVector = Symbols.Undo, contentDescription = stringResource(id = R.string.editor_undo))
        }
        IconButton(onClick = state::redo, enabled = state.history.canRedo) {
            Icon(imageVector = Symbols.Redo, contentDescription = stringResource(id = R.string.editor_redo))
        }
    }
    val glass = Modifier.floatingGlass(SmoothCornerShape.Capsule, barButton = false, pressable = true)
    if (vertical) Column(modifier = glass) { buttons() } else Row(modifier = glass) { buttons() }
}

/**
 * The picture in the room [padding] leaves, which grows and shrinks smoothly as the tools' options
 * open and close; and the caption being typed, lifted above the keyboard.
 */
@Composable
private fun Picture(state: EditorState, padding: PaddingValues) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val margin = with(density) { PictureMargin.toPx() }
    val grip = with(density) { FrameGrip.toPx() }
    val imeBottom = WindowInsets.ime.getBottom(density)
    val top = with(density) { padding.calculateTopPadding().toPx() }
    val bottom = with(density) { padding.calculateBottomPadding().toPx() }
    val left = with(density) { padding.calculateLeftPadding(direction).toPx() }
    val right = with(density) { padding.calculateRightPadding(direction).toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // The insets around the picture; the options' room already moves with their own motion
        val room = Rect(left + margin, top + margin, right + margin, bottom + margin)
        // Typing near the bottom, everything rises so the caption stays above the keyboard
        val lift by animateFloatAsState(
            targetValue = state.draft?.let { draft ->
                val below = state.toScreen(state.renderer.captionBounds(draft, state.history.framing).bottomCenter).y +
                    with(density) { 24.dp.toPx() } - (state.area.bottom + bottom + margin - imeBottom)
                below.coerceAtLeast(0f)
            } ?: 0f,
            animationSpec = Motion.settle(),
            label = "lift",
        )
        val autoMask = rememberAutoMaskFiles()
        val autoMaskDownload by autoMask.download.collectAsState()
        TapToHide(state, autoMask)
        val pickEffect = rememberPickEffect(state, autoMaskDownload == AutoMaskDownload.Ready)
        // The thirds come in as the frame is dragged and fade as it is let go; read as it draws
        val thirds = animateFloatAsState(if (state.draftRect != null) 1f else 0f, if (state.draftRect != null) Motion.fadeIn() else Motion.fadeOut(), label = "thirds")
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Nothing scrolls under the bar: it stays clear over the black
                .pageScroll(null) { 0 }
                .graphicsLayer { translationY = -lift }
                .pointerInput(state) { gestures(state, grip) },
        ) {
            state.area = Rect(room.left, room.top, size.width - room.right, size.height - room.bottom)
            showHomeOnce(state)
            if (state.hidden) return@Canvas
            // A turn or a mirroring just made, still coming round
            withTransform({
                val fit = state.turningFit()
                rotate(state.turning.value, pivot = state.area.center)
                scale(state.mirroring.value * fit, fit, pivot = state.area.center)
            }) {
                if (state.tool == EditorTool.Crop) {
                    drawEdits(state)
                    drawFrame(state, thirds.value)
                } else {
                    // Only the kept part shows; what lies outside it is kept for later
                    val kept = state.history.framing.rect
                    val shown = Rect(state.toScreen(kept.topLeft), state.toScreen(kept.bottomRight))
                    // While a finger aims: what it would hide added, or what it would take away left out
                    val aimed = pickEffect.aimedEdits(state)
                    val drawAimed: DrawScope.() -> Unit = {
                        clipRect(shown.left, shown.top, shown.right, shown.bottom) {
                            drawEdits(state, without = aimed?.second, plus = aimed?.first.orEmpty())
                            pickEffect.drawAimed(this, state)
                        }
                    }
                    clipRect(shown.left, shown.top, shown.right, shown.bottom) {
                        // A tap's mask spreads out of the finger, or draws back into it: shown inside the circle only
                        val spread = pickEffect.spread(state)
                        if (spread == null) {
                            drawEdits(state, without = aimed?.second, plus = aimed?.first.orEmpty())
                            pickEffect.drawAimed(this, state)
                        } else {
                            val circle = Path().apply { addOval(Rect(spread.first, spread.second)) }
                            val removed = state.picked?.removed
                            if (removed == null) {
                                clipPath(circle, ClipOp.Difference) { drawEdits(state, without = state.picked?.id) }
                                clipPath(circle) { drawEdits(state) }
                            } else {
                                // Taken away: still shown inside the circle, as it draws back
                                clipPath(circle, ClipOp.Difference) { drawEdits(state) }
                                clipPath(circle) { drawEdits(state, plus = listOf(removed)) }
                            }
                        }
                        pickEffect.drawLooking(this, state)
                        pickEffect.drawTap(this, state)
                    }
                    drawSelection(state)
                    pickEffect.drawLoupe(this, state, drawAimed)
                }
            }
        }
        state.draft?.let { draft ->
            CaptionField(state, draft, lift)
        }
        // What is picked up can be taken away, from a button just above it
        val selection = state.selection
        if (selection != null && state.dragged == null && state.tool != EditorTool.Crop) {
            DeleteButton(state, selection)
        }
    }
}

/** Shows the picture's home the first time there is room for it. */
private fun showHomeOnce(state: EditorState) {
    if (!state.laidOut && state.area.width > 0f) {
        state.laidOut = true
        state.goHome(animate = false)
    }
}

private fun DrawScope.drawEdits(state: EditorState, without: Int? = null, plus: List<Edit> = emptyList()) {
    val moving = state.moving
    val captions = state.history.captions.map { caption ->
        if (moving != null && moving.first == caption.id) state.movedCaption(caption, moving.second) else caption
    }
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        native.withMatrix(state.screenMatrix()) {
            val strokes = state.shownStrokes.filter { without == null || it.objectId != without } + plus
            state.renderer.draw(native, state.framing, strokes, captions, state.live, state.draft?.id)
        }
    }
}

/** Round what is picked up, a dashed outline, and a handle on each end that moves alone. */
private fun DrawScope.drawSelection(state: EditorState) {
    val edit = state.selection ?: return
    val bounds = state.screenBounds(edit).inflate(6.dp.toPx())
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    drawRect(Color.Black.copy(alpha = 0.4f), bounds.topLeft, bounds.size, style = Stroke(2.5.dp.toPx(), pathEffect = dash))
    drawRect(Color.White, bounds.topLeft, bounds.size, style = Stroke(1.dp.toPx(), pathEffect = dash))
    for (handle in state.handles(edit)) {
        val at = state.pictureToScreen(handle)
        drawCircle(Color.Black.copy(alpha = 0.3f), 8.dp.toPx(), at)
        drawCircle(Color.White, 6.5.dp.toPx(), at)
    }
}

/**
 * The frame being cut to: the picture outside it dimmed, its edges and corners, thirds while it
 * is dragged. Only the picture is dimmed: beyond it is black already, and a dark layer there
 * would show through the glass over it and turn with the picture.
 */
private fun DrawScope.drawFrame(state: EditorState, thirds: Float) {
    val rect = state.framing.rect
    val frame = Rect(state.toScreen(rect.topLeft), state.toScreen(rect.bottomRight))
    val size = state.renderer.size
    val picture = listOf(Offset.Zero, Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height))
        .map(state::pictureToScreen)
    val outside = Path().apply {
        fillType = PathFillType.EvenOdd
        moveTo(picture[0].x, picture[0].y)
        picture.drop(1).forEach { lineTo(it.x, it.y) }
        close()
        addRect(frame)
    }
    drawPath(outside, Color.Black.copy(alpha = 0.6f))
    drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(1.dp.toPx()))
    if (thirds > 0f) {
        val line = Color.White.copy(alpha = 0.5f * thirds)
        for (i in 1..2) {
            val x = frame.left + frame.width * i / 3
            val y = frame.top + frame.height * i / 3
            drawLine(line, Offset(x, frame.top), Offset(x, frame.bottom), 0.5.dp.toPx())
            drawLine(line, Offset(frame.left, y), Offset(frame.right, y), 0.5.dp.toPx())
        }
    }
    val arm = 16.dp.toPx()
    val width = 3.dp.toPx()
    val corners = Path()
    for ((corner, sx, sy) in listOf(
        Triple(frame.topLeft, 1f, 1f),
        Triple(frame.topRight, -1f, 1f),
        Triple(frame.bottomLeft, 1f, -1f),
        Triple(frame.bottomRight, -1f, -1f),
    )) {
        val c = corner + Offset(-sx * width / 2, -sy * width / 2)
        corners.moveTo(c.x + sx * arm, c.y)
        corners.lineTo(c.x, c.y)
        corners.lineTo(c.x, c.y + sy * arm)
    }
    drawPath(corners, Color.White, style = Stroke(width, join = StrokeJoin.Round))
}

/** The caption being typed, where it will lie, in its look. */
@Composable
private fun CaptionField(state: EditorState, draft: Edit.Caption, lift: Float) {
    val density = LocalDensity.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(draft.id) { focus.requestFocus() }
    val center = state.toScreen(state.turnedCenter(draft))
    val px = draft.size * state.scale
    val style = TextStyle(
        color = draft.textColor(),
        fontSize = with(density) { px.toSp() },
        lineHeight = with(density) { (px * 1.17f).toSp() },
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    val padX = with(density) { (px * 0.35f).toDp() }
    val padY = with(density) { (px * 0.18f).toDp() }
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        (center.x - placeable.width / 2f).toInt(),
                        (center.y - lift - placeable.height / 2f).toInt(),
                    )
                }
            }
            .then(
                if (draft.style == CaptionStyle.Filled) {
                    Modifier.background(draft.color, SmoothCornerShape(with(density) { (px * 0.3f).toDp() }))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = padX, vertical = padY),
    ) {
        if (draft.style == CaptionStyle.Outline) {
            Text(
                text = state.draftText.text,
                style = style.copy(
                    color = draft.outlineColor(),
                    drawStyle = Stroke(width = px * 0.16f, join = StrokeJoin.Round),
                ),
            )
        }
        BasicTextField(
            value = state.draftText,
            onValueChange = { state.draftText = it },
            textStyle = style,
            cursorBrush = SolidColor(draft.textColor()),
            modifier = Modifier
                .focusRequester(focus)
                .width(IntrinsicSize.Min)
                .widthIn(min = 2.dp),
        )
    }
}

/** What a finger does on the picture, by the tool in hand. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.gestures(state: EditorState, grip: Float) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // A tap away from the caption being typed ends it
        if (state.draft != null) {
            state.finishCaption()
            transform(state, down)
            return@awaitEachGesture
        }
        when (state.tool) {
            // Adjusting is done on the ruler; on the picture a finger moves the view
            EditorTool.Adjust -> transform(state, down)
            EditorTool.Crop -> {
                val hold = frameHold(state, down.position, grip)
                if (hold == null) transform(state, down) else dragFrame(state, down, hold)
            }

            EditorTool.Mask, EditorTool.Mark -> {
                // Strokes start on the picture; elsewhere a finger moves the view
                val kept = state.history.framing.rect
                val shown = Rect(state.toScreen(kept.topLeft), state.toScreen(kept.bottomRight))
                when {
                    !shown.contains(down.position) -> transform(state, down)
                    // A tap hides what it lands on; a drag moves the view
                    state.tool == EditorTool.Mask && state.maskPicking -> tapToHide(state, down, viewConfiguration.touchSlop, density)
                    else -> drawOrPick(state, down, viewConfiguration.touchSlop, grip)
                }
            }
            EditorTool.Text -> caption(state, down, viewConfiguration.touchSlop)
            null -> tapOrTransform(state, down, viewConfiguration.touchSlop)
        }
    }
}

/** Which edge or corner of the frame is under [point], or the inside: -1, 0 or 1 across and down. */
private fun frameHold(state: EditorState, point: Offset, grip: Float): Pair<Int, Int>? {
    val rect = state.framing.rect
    val frame = Rect(state.toScreen(rect.topLeft), state.toScreen(rect.bottomRight))
    if (!frame.inflate(grip).contains(point)) return null
    var dx = when {
        abs(point.x - frame.left) < grip -> -1
        abs(point.x - frame.right) < grip -> 1
        else -> 0
    }
    var dy = when {
        abs(point.y - frame.top) < grip -> -1
        abs(point.y - frame.bottom) < grip -> 1
        else -> 0
    }
    // A fixed shape is changed by its corners only; an edge moves the frame
    if (state.aspect() != null && (dx == 0 || dy == 0)) {
        dx = 0
        dy = 0
    }
    return dx to dy
}

private suspend fun AwaitPointerEventScope.dragFrame(state: EditorState, down: PointerInputChange, hold: Pair<Int, Int>) {
    val start = state.framing.rect
    val least = state.leastFrame()
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        if (change.positionChanged()) {
            state.draftRect = state.dragFrame(start, hold.first, hold.second, (change.position - down.position) / state.scale, least)
            change.consume()
        }
    }
    state.commitFrame()
}

/**
 * With markup or redaction in hand: on what is picked up, a finger moves it, or one end of it by
 * its handle; a tap on something drawn picks it up, a tap elsewhere puts it down; a drag draws.
 */
private suspend fun AwaitPointerEventScope.drawOrPick(state: EditorState, down: PointerInputChange, slop: Float, grip: Float) {
    val selection = state.selection
    if (selection != null) {
        val handle = state.handles(selection).indexOfFirst { (state.pictureToScreen(it) - down.position).getDistance() < grip }
        if (handle >= 0 || state.objectAt(down.position)?.objectId == selection.objectId) {
            move(state, down, selection, handle.takeIf { it >= 0 })
            return
        }
    }
    val hit = state.objectAt(down.position)
    // Until the finger moves, it may be a tap
    val path = mutableListOf(down.position)
    var drawing = false
    while (!drawing) {
        val event = awaitPointerEvent()
        if (event.changes.count { it.pressed } > 1) {
            transform(state, null)
            return
        }
        val change = event.changes.firstOrNull { it.id == down.id } ?: return
        if (!change.pressed) break
        path += change.position
        if ((change.position - down.position).getDistance() > slop) drawing = true
    }
    if (!drawing) {
        when {
            hit != null -> state.pick(hit)
            selection != null -> state.deselect()
            // A tap draws a dot, or puts a numbered one
            else -> {
                state.beginStroke(down.position)
                state.endStroke()
            }
        }
        return
    }
    state.deselect()
    state.beginStroke(down.position)
    path.drop(1).forEach(state::extendStroke)
    stroke(state, down)
}

/** Moves [edit], or one end of it, with the finger, and makes it a step once lifted. */
private suspend fun AwaitPointerEventScope.move(state: EditorState, down: PointerInputChange, edit: Edit, handle: Int?) {
    val from = state.toPicture(state.toTurned(down.position))
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        if (change.positionChanged()) {
            state.dragged = state.moved(edit, state.toPicture(state.toTurned(change.position)) - from, handle)
            change.consume()
        }
    }
    state.commitDrag()
}

/** With no tool in hand: a tap on something drawn or written picks it up with its tool; else the view moves. */
private suspend fun AwaitPointerEventScope.tapOrTransform(state: EditorState, down: PointerInputChange, slop: Float) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id }
        if (event.changes.count { it.pressed } > 1 || change == null || (change.pressed && (change.position - down.position).getDistance() > slop)) {
            transform(state, null)
            return
        }
        if (!change.pressed) break
    }
    val caption = state.captionAt(down.position)
    val hit = state.objectAt(down.position)
    when {
        caption != null -> {
            state.select(EditorTool.Text)
            state.editCaption(caption)
        }
        hit != null -> state.pick(hit)
    }
}

/**
 * A tap hands its point to be looked into. Held or moved, the finger aims: what it would hide
 * shows, with a loupe, and it is hidden once the finger lets go; near an edge with more of the
 * picture past it, the picture moves in. A second finger moves the view instead.
 */
private suspend fun AwaitPointerEventScope.tapToHide(state: EditorState, down: PointerInputChange, slop: Float, density: Float) {
    // Nothing to aim at until the picture is looked over
    if (!state.pickReady) return transform(state, down)
    // While what was found all at once shows, a tap turns one off or on again
    if (state.spotted != null || state.spotting) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (event.changes.count { it.pressed } > 1 || change == null || (change.pressed && (change.position - down.position).getDistance() > slop)) {
                return transform(state, null)
            }
            if (!change.pressed) break
        }
        state.toggleSpotted(down.position)
        return
    }
    state.aimed = null
    state.swept = emptyList()
    state.aim = down.position
    // How far the finger has gone from where it went down, to tell a slide brought back
    var farthest = 0f
    var at = down.position
    var held = false
    // The edges move the picture only once the finger has come to them, not when it starts there
    var armed = edgePull(state, at, density) == Offset.Zero
    var pullSince = -1L
    var tick = SystemClock.uptimeMillis()
    while (true) {
        val event = withTimeoutOrNull(FrameMillis) { awaitPointerEvent() }
        val now = SystemClock.uptimeMillis()
        if (event != null) {
            val change = event.changes.firstOrNull { it.id == down.id }
            if (event.changes.count { it.pressed } > 1 || change == null) {
                state.stopAiming()
                transform(state, null)
                return
            }
            if (!change.pressed) break
            if (change.positionChanged()) change.consume()
            at = change.position
            state.aim = at
            farthest = max(farthest, (at - down.position).getDistance())
            if ((at - down.position).getDistance() > slop) held = true
        }
        if (now - down.uptimeMillis >= HoldMillis) held = true
        state.aiming = held
        val pull = edgePull(state, at, density)
        if (pull == Offset.Zero) {
            armed = true
            pullSince = -1L
        } else if (armed && held) {
            if (pullSince < 0) pullSince = now
            // Eased in, so a finger reaching the edge to aim there has time to stop it
            val ramp = ((now - pullSince) / EdgeRampMillis.toFloat()).coerceIn(0f, 1f)
            val step = pull * (EdgeMostSpeed * density * ramp * ramp * (now - tick).coerceAtMost(32L) / 1000f)
            state.transform(at, state.edgeRoom(step), 1f)
        }
        tick = now
    }
    // Brought back to where it began, after going out: let go of it all
    if (farthest > CancelReach * 3 * density && (at - down.position).getDistance() < CancelReach * density) {
        state.stopAiming()
        return
    }
    state.pendingPick = EditorState.Pick(at, held)
}

/** How near where it began a slide brought back lets go of what it passed over, in dp. */
private const val CancelReach = 24f

/**
 * How hard the edges pull a finger [at] a point, each way from -1 to 1, as the way the picture
 * moves: deeper into an edge, quicker, gently at first.
 */
private fun edgePull(state: EditorState, at: Offset, density: Float): Offset {
    val zone = EdgeZone * density
    val area = state.area
    fun pull(near: Float, far: Float) = when {
        near < zone -> ((zone - near) / zone).coerceAtMost(1f).let { it * it }
        far < zone -> -((zone - far) / zone).coerceAtMost(1f).let { it * it }
        else -> 0f
    }
    return Offset(pull(at.x - area.left, area.right - at.x), pull(at.y - area.top, area.bottom - at.y))
}

/** A frame's wait while a finger is held still, so the edges keep moving the picture. */
private const val FrameMillis = 16L

/** How long a finger is down before it aims rather than taps. */
private const val HoldMillis = 160L

/** How far in from the edges they pull, in dp, the most they move the picture, in dp a second, and how long they take to get going. */
private const val EdgeZone = 56f
private const val EdgeMostSpeed = 900f
private const val EdgeRampMillis = 350L

/** Draws the stroke begun, following the finger until it lifts. */
private suspend fun AwaitPointerEventScope.stroke(state: EditorState, down: PointerInputChange) {
    if (state.live == null) state.beginStroke(down.position)
    while (true) {
        val event = awaitPointerEvent()
        // A second finger zooms instead
        if (event.changes.count { it.pressed } > 1) {
            state.live = null
            transform(state, null)
            return
        }
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        if (change.positionChanged()) {
            state.extendStroke(change.position)
            change.consume()
        }
    }
    state.endStroke()
}

private suspend fun AwaitPointerEventScope.caption(state: EditorState, down: PointerInputChange, slop: Float) {
    val caption = state.captionAt(down.position)
    var dragged = false
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.count { it.pressed } > 1) {
            state.moving = null
            transform(state, null)
            return
        }
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        val by = change.position - down.position
        if (caption != null && (dragged || by.getDistance() > slop)) {
            dragged = true
            state.moving = caption.id to by
            change.consume()
        }
    }
    val moving = state.moving
    state.moving = null
    when {
        caption != null && dragged && moving != null -> state.history.push(state.movedCaption(caption, moving.second))
        caption != null -> state.editCaption(caption)
        else -> state.editCaption(screen = down.position)
    }
}

/** Zooms and moves the view with the fingers until they all lift. */
private suspend fun AwaitPointerEventScope.transform(state: EditorState, down: PointerInputChange?) {
    if (down != null && !down.pressed) return
    // The fingers' speed as they lift, for the view to glide on
    val tracker = VelocityTracker()
    var moved = Offset.Zero
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.none { it.pressed }) break
        val zoom = event.calculateZoom()
        val pan = event.calculatePan()
        if (zoom != 1f || pan != Offset.Zero) {
            state.transform(event.calculateCentroid(useCurrent = false), pan, zoom)
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
        moved += pan
        // A second finger coming or going restarts the reading, or the centroid's jump reads as speed
        if (event.changes.any { it.changedToDown() || it.changedToUp() }) tracker.resetTracking()
        event.changes.firstOrNull()?.let { tracker.addPosition(it.uptimeMillis, moved) }
    }
    state.settle(tracker.calculateVelocity().let { Offset(it.x, it.y) })
}

@Composable
private fun EditorBottom(state: EditorState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) { EditorTools(state) }
}

@Composable
private fun DeleteButton(state: EditorState, selection: Edit) {
    val density = LocalDensity.current
    val bounds = state.screenBounds(selection)
    Box(
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val gap = with(density) { 14.dp.roundToPx() }
                // Above, or below when there is no room above
                val above = bounds.top - gap - placeable.height
                val y = if (above >= state.area.top) above else bounds.bottom + gap
                placeable.place((bounds.center.x - placeable.width / 2f).toInt(), y.toInt())
            }
        },
    ) {
        Box(modifier = Modifier.floatingGlass(SmoothCornerShape.Capsule, barButton = false, pressable = true)) {
            CompositionLocalProvider(LocalPressedByGlass provides true) {
                IconButton(onClick = state::deleteSelection) {
                    Icon(imageVector = Symbols.Delete, contentDescription = stringResource(id = R.string.editor_delete), tint = ExtendedTheme.colors.text)
                }
            }
        }
    }
}
