package oolong.client.ui.photoview

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.CoilZoomState
import com.github.panpf.zoomimage.compose.zoom.mouseZoom
import com.github.panpf.zoomimage.rememberCoilZoomState
import com.github.panpf.zoomimage.util.IntSizeCompat
import com.github.panpf.zoomimage.zoom.Edge
import com.github.panpf.zoomimage.zoom.ReadMode
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.media.ImageUtil
import oolong.client.media.automask.AutoMaskDownload
import oolong.client.media.download
import oolong.client.media.image.rememberImageDownloadProgress
import oolong.client.model.ThreadPicture
import oolong.client.navigation.MaxReadingWidth
import oolong.client.ui.components.dimFilter
import oolong.client.ui.components.nightImageDim
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.IconButton
import oolong.client.ui.designsystem.MediaGlassActions
import oolong.client.ui.designsystem.MediaGlassPill
import oolong.client.ui.designsystem.ProvideMediaGlass
import oolong.client.ui.designsystem.ThresholdHaptics
import oolong.client.ui.designsystem.clickMenu
import oolong.client.ui.designsystem.rememberHaptics
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.designsystem.window.FullColorWindow
import oolong.client.ui.designsystem.window.isHdr
import oolong.client.ui.editor.EditRequest
import oolong.client.ui.editor.EditorPlace
import oolong.client.ui.editor.LocalImageEditor
import oolong.client.ui.editor.rememberAutoMaskFiles
import oolong.client.util.TiebaUtil
import oolong.client.util.toastShort
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Phase { Opening, Open, Closing }

/**
 * The pictures of [opening], over the whole window. The picture grows out of its thumbnail, pages
 * sideways through the thread's pictures, and closes on a tap, the back button or a drag down,
 * shrinking back into the thumbnail of the picture showing when there is one on screen.
 */
@Composable
internal fun PhotoViewerOverlay(viewer: PhotoViewer, opening: PhotoOpening, origin: Offset) {
    // The window's store, not a page's: the viewer outlives none of them and serves them all
    val viewModel: PhotoViewViewModel = hiltViewModel()
    LaunchedEffect(opening.data) { viewModel.start(opening.data) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pictures = state.pictures
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val openedItem = opening.data.picItems.getOrNull(opening.data.index)

    LightSystemBars()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val container = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        // With motion reduced the picture fades in and out in place instead of flying
        val reduced = LocalReduceMotion.current
        val from = remember { opening.from?.translate(-origin) }
        val aspect = opening.aspect.takeIf { it > 0f } ?: from?.let { it.height / it.width } ?: 0f
        val readWidth = with(density) { MaxReadingWidth.toPx() }

        // 0 at the thumbnail, 1 open; closing runs it back to 0
        val progress = remember { Animatable(0f) }
        var phase by remember { mutableStateOf(Phase.Opening) }
        // The picture flies between the thumbnail's side (near; null to fade in place) and the viewer's (far)
        var near by remember { mutableStateOf(from?.takeUnless { reduced }) }
        var far by remember { mutableStateOf(shownRect(container, aspect, readWidth)) }
        var flying by remember {
            mutableStateOf(openedItem?.let { request(context, it.originUrl, opening.thumbnail) })
        }
        var flyingCrops by remember { mutableStateOf(aspect > 0f) }
        // How far a drag down has carried the picture, and how much of the black is left
        var drag by remember { mutableStateOf(Offset.Zero) }

        /** The drag springing back into place, which a new drag stops. */
        var rebound by remember { mutableStateOf<Job?>(null) }
        var dragging by remember { mutableStateOf(false) }
        var closingFade by remember { mutableStateOf(1f) }
        val dragScale = 1f - (drag.y.coerceAtLeast(0f) / container.height).coerceIn(0f, 1f) * 0.45f
        val dragFade = 1f - (abs(drag.y) / (container.height * 0.5f)).coerceIn(0f, 1f)

        val glass = rememberHazeState()
        val view = LocalView.current
        val autoMask = rememberAutoMaskFiles()
        val text = remember { PhotoTextState(context, autoMask, scope) }
        val zoomStates = remember { mutableStateMapOf<Int, CoilZoomState>() }
        val loadedKeys = remember { mutableStateMapOf<String, MemoryCache.Key>() }
        // The pager reads its pages, their keys and their pictures from this one state, so that
        // they agree while more pictures arrive
        val pagedPictures by rememberUpdatedState(pictures)
        val pageKeys = remember(pictures) { pageKeys(pictures) }
        // Made again once the pictures arrive, to start at the one opened
        val pagerState = remember(pictures.isNotEmpty()) { PagerState(currentPage = state.currentIndex) { pagedPictures.size } }
        val current = pictures.getOrNull(pagerState.currentPage)
        // The pictures seen to be HDR, by id: the window turns HDR only while one of them shows
        val hdrPictures = remember { mutableStateMapOf<String, Boolean>() }
        FullColorWindow(hdr = current?.let { hdrPictures[it.picId] } == true)
        val currentZoom = zoomStates[pagerState.currentPage]

        LaunchedEffect(Unit) {
            progress.animateTo(1f, Motion.present(Motion.ProgressThreshold))
            phase = Phase.Open
        }
        // Text is picked from the picture shown only
        LaunchedEffect(pagerState.currentPage) { text.stop() }
        LaunchedEffect(text.waiting) {
            if (text.waiting == null) return@LaunchedEffect
            text.download.first { it == AutoMaskDownload.Ready }
            text.downloaded()
        }
        LaunchedEffect(state.currentIndex, pictures.isNotEmpty()) {
            if (pictures.isNotEmpty() && pagerState.currentPage != state.currentIndex) {
                pagerState.scrollToPage(state.currentIndex)
            }
        }
        LaunchedEffect(pagerState.currentPage, pictures.size, state.source) {
            val picture = pictures.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
            if (state.source == null) return@LaunchedEffect
            if (pagerState.currentPage == 0 && state.hasPrevious) {
                viewModel.loadPrevious(picture)
            } else if (pagerState.currentPage == pictures.lastIndex && state.hasNext) {
                viewModel.loadMore(picture)
            }
        }

        /** Flies the picture back, carrying on at the speed of the [velocity] it was let go with. */
        fun close(velocity: Velocity = Velocity.Zero) {
            if (phase == Phase.Closing) return
            val picture = current
            val picId = picture?.picId ?: openedItem?.picId
            val shown = currentZoom?.displayIn(container)?.takeIf { !it.isEmpty } ?: far
            far = shown.dragged(drag, dragScale, container)
            near = picId?.let { viewer.thumbnails[it] }?.takeIf { it.visible && !reduced }?.bounds?.translate(-origin)
            val placeholder = picId?.let { loadedKeys[it] } ?: opening.thumbnail.takeIf { picId == openedItem?.picId }
            flying = (picture?.originUrl ?: openedItem?.originUrl)?.let { request(context, it, placeholder) }
            flyingCrops = true
            closingFade = dragFade
            drag = Offset.Zero
            dragging = false
            phase = Phase.Closing
            val flight = near?.let { flightSpeed(far, it, velocity) } ?: 0f
            scope.launch {
                // Closed while still opening, it turns back from where it got to
                progress.snapTo(progress.value.coerceAtMost(1f))
                progress.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold), initialVelocity = flight)
                viewer.closed()
            }
        }

        val onClose by rememberUpdatedState<(Velocity) -> Unit> { close(it) }
        BackHandler(enabled = phase != Phase.Closing) { close() }
        // Picking text, back leaves it first
        BackHandler(enabled = phase != Phase.Closing && text.active) { text.stop() }

        val p = progress.value
        val black = if (phase == Phase.Closing) p * closingFade else p.coerceIn(0f, 1f) * dragFade
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = black }
                .background(Color.Black)
                // Taps do not fall through to the page under the viewer
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        )

        if (pictures.isNotEmpty() && phase != Phase.Closing) {
            val haptics = rememberHaptics()
            // Felt where letting go starts to close the viewer, and where it stops doing so
            val dismissFeel = remember(haptics) { ThresholdHaptics(haptics) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // What the controls' glass shows through
                    .hazeSource(glass)
                    .graphicsLayer {
                        alpha = if (phase == Phase.Open) 1f else 0f
                        translationX = drag.x
                        translationY = drag.y
                        scaleX = dragScale
                        scaleY = dragScale
                        transformOrigin = TransformOrigin.Center
                    }
                    .pointerInput(pagerState) {
                        val slop = viewConfiguration.touchSlop
                        val dismissPx = DragDismissDistance.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            dismissFeel.reset()
                            if (phase != Phase.Open || text.active || !zoomStates[pagerState.currentPage].atRest()) return@awaitEachGesture
                            val tracker = VelocityTracker()
                            var total = Offset.Zero
                            // Caught on its way back, the picture stays under the finger from where
                            // it is, and goes back again when let go
                            val caught = rebound?.isActive == true
                            rebound?.cancel()
                            var active = caught
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                // A second finger is a pinch, for the picture to zoom
                                if (event.changes.count { it.pressed } > 1) {
                                    if (active) {
                                        rebound = scope.launch {
                                            release(drag, Offset.Zero) { drag = it }
                                            dragging = false
                                        }
                                    }
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (active) {
                                        val velocity = tracker.calculateVelocity()
                                        val dismiss = drag.y > dismissPx || velocity.y > 900.dp.toPx()
                                        // Back into place at the speed it was let go with
                                        if (dismiss) onClose(Velocity(velocity.x, velocity.y)) else rebound = scope.launch {
                                            release(drag, Offset.Zero, Offset(velocity.x, velocity.y)) { drag = it }
                                            dragging = false
                                        }
                                    }
                                    break
                                }
                                val delta = change.positionChange()
                                if (!active) {
                                    total += delta
                                    if (total.y > slop && abs(total.y) > abs(total.x) * 1.5f) {
                                        active = true
                                        dragging = true
                                    } else if (abs(total.x) > slop || total.y < -slop) {
                                        break
                                    }
                                }
                                if (active) {
                                    change.consume()
                                    drag += delta
                                    dismissFeel.update(drag.y > dismissPx)
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                }
                            }
                        }
                    },
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !text.active,
                    pageSpacing = 16.dp,
                    key = { page -> pageKeys.getOrNull(page) ?: page },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val picture = pagedPictures.getOrNull(page) ?: return@HorizontalPager
                    val zoomState = rememberCoilZoomState()
                    LaunchedEffect(zoomState) { zoomState.zoomable.setReadMode(TallReadMode) }
                    DisposableEffect(page, zoomState) {
                        zoomStates[page] = zoomState
                        onDispose { if (zoomStates[page] === zoomState) zoomStates.remove(page) }
                    }
                    Photo(
                        picture = picture,
                        placeholder = loadedKeys[picture.picId]
                            ?: opening.thumbnail.takeIf { picture.picId == openedItem?.picId },
                        zoomState = zoomState,
                        onLoaded = { key, hdr ->
                            loadedKeys[picture.picId] = key
                            hdrPictures[picture.picId] = hdr
                        },
                        onTap = { at ->
                            val share = zoomState.zoomable.contentDisplayRectF.let { shareOf(it, at) }
                            // Picking text, a tap picks a line or leaves; otherwise it closes
                            if (text.url == picture.originUrl) {
                                if (share == null || !text.selectLineAt(share)) text.stop()
                            } else if (!text.active) {
                                onClose(Velocity.Zero)
                            }
                        },
                        onLongPress = { at ->
                            val share = shareOf(zoomState.zoomable.contentDisplayRectF, at)
                            if (share != null && text.start(picture.originUrl, share, onMissed = { view.reject() })) {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                        },
                        handles = if (text.url == picture.originUrl) {
                            Modifier.selectionHandles(text, display = { zoomState.displayIn(container) }, view = view)
                        } else Modifier,
                        overlay = {
                            if (text.url == picture.originUrl) {
                                PhotoTextOverlay(text, display = { zoomState.displayIn(container) })
                            }
                        },
                    )
                }
            }
        }

        // The thumbnail's night dim, lifting as the picture flies out and settling back as it lands
        val nightDim = nightImageDim()
        if (phase != Phase.Open || pictures.isEmpty()) {
            val request = flying
            if (request != null) {
                val rect = near?.let { lerp(it, far, p) } ?: far.scaledAround(if (reduced) 1f else lerp(0.9f, 1f, p))
                val corner = if (near != null) lerp(8f, 0f, p.coerceIn(0f, 1f)) else 0f
                Box(
                    modifier = Modifier
                        .offsetTo(rect)
                        .graphicsLayer {
                            alpha = if (near == null) p.coerceIn(0f, 1f) else 1f
                            shape = SmoothCornerShape(corner.dp)
                            clip = true
                        },
                ) {
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = if (flyingCrops) ContentScale.Crop else ContentScale.Fit,
                        colorFilter = dimFilter(nightDim * (1f - p.coerceIn(0f, 1f))),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val chrome by animateFloatAsState(
            targetValue = if (phase == Phase.Open && !dragging && !text.active && currentZoom.atRest(edge = false)) 1f else 0f,
            animationSpec = if (phase == Phase.Open && !dragging) Motion.fadeIn() else Motion.fadeOut(),
            label = "chrome",
        )
        val editor = LocalImageEditor.current
        if (current != null && chrome > 0f && phase != Phase.Closing) ProvideMediaGlass(glass) {
            val floors = opening.floors
            val postId = current.postId
            PhotoChrome(
                picture = current,
                total = state.totalAmount,
                floor = postId?.let { floors?.floorOf(it) },
                hasFloor = floors != null && postId != null,
                // The thread scrolls to the floor under the viewer, which then closes back into it
                onFloor = {
                    if (floors != null && postId != null) {
                        floors.show(postId)
                        scope.launch {
                            delay(FloorCloseDelayMillis)
                            close()
                        }
                    }
                },
                onShare = { share(context, current.originUrl) },
                onSave = { ImageUtil.download(context, current.originUrl) },
                onCopyLink = { TiebaUtil.copyText(context, current.originUrl) },
                // Not offered where the models cannot run
                onExtract = if (text.supported) {
                    {
                        if (text.download.value == AutoMaskDownload.Ready) text.start(current.originUrl, null, onMissed = { context.toastShort(R.string.photo_no_text) })
                        else text.startAfterDownload(current.originUrl)
                    }
                } else null,
                onEdit = {
                    // The edited picture goes to the gallery once done
                    val output = File(context.cacheDir, "edited/${UUID.randomUUID()}.jpg")
                    // Out of the picture as shown; back into it only if nothing was saved, for the
                    // viewer still shows the original, the edit going to the gallery instead
                    val place = current.takeIf { it.width > 0 && it.height > 0 }
                        ?.let { EditorPlace(shownRect(container, it.height.toFloat() / it.width, readWidth).translate(origin)) }
                    editor?.open(EditRequest(current.originUrl.toUri(), output), from = { place }, back = { written -> place.takeUnless { written } }) { written ->
                        if (written) ImageUtil.saveToGallery(context, output)
                    }
                },
                modifier = Modifier.graphicsLayer { alpha = chrome },
            )
        }
        // Always there, so the bar comes and goes in place of the actions rather than popping
        if (current != null && phase == Phase.Open) ProvideMediaGlass(glass) {
            if (text.url == current.originUrl) {
                SelectionToolbar(text, display = { currentZoom?.displayIn(container) ?: Rect.Zero })
            }
            PhotoTextBar(text, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** One picture, zoomable, showing [placeholder] until it arrives. */
@Composable
private fun Photo(
    picture: ThreadPicture,
    placeholder: MemoryCache.Key?,
    zoomState: CoilZoomState,
    onLoaded: (key: MemoryCache.Key, hdr: Boolean) -> Unit,
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
    handles: Modifier,
    overlay: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember(picture.originUrl) { mutableStateOf(true) }
    val progress by rememberImageDownloadProgress(picture.originUrl)
    // Made once: the placeholder only matters until the picture is in
    val request = remember(picture.originUrl) { request(context, picture.originUrl, placeholder) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().then(handles), contentAlignment = Alignment.Center) {
        // A long picture is read down a column no wider than text is, as on a phone, rather than
        // across a wide window; its size is known from the post, or once it is in
        val loaded = zoomState.zoomable.contentSize
        val aspect = when {
            picture.width > 0 && picture.height > 0 -> picture.height.toFloat() / picture.width
            loaded.width > 0 && loaded.height > 0 -> loaded.height.toFloat() / loaded.width
            else -> 0f
        }
        val container = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val column = readColumn(container, aspect, with(LocalDensity.current) { MaxReadingWidth.toPx() })
        CoilZoomAsyncImage(
            model = request,
            contentDescription = null,
            // A mouse wheel zooms, as pinching does
            modifier = if (column < container.width) {
                Modifier.fillMaxHeight().width(with(LocalDensity.current) { column.toDp() })
            } else {
                Modifier.fillMaxSize()
            }.mouseZoom(zoomState.zoomable),
            zoomState = zoomState,
            onTap = { onTap(it) },
            onLongPress = { onLongPress(it) },
            onState = { state ->
                loading = state is AsyncImagePainter.State.Loading
                if (state is AsyncImagePainter.State.Success) {
                    val hdr = (state.result.image as? BitmapImage)?.bitmap?.isHdr() == true
                    state.result.memoryCacheKey?.let { onLoaded(it, hdr) }
                }
            },
        )
        overlay()
        if (loading && placeholder == null) {
            val fraction = progress
            if (fraction != null) {
                CircularProgressIndicator(progress = fraction, color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(40.dp))
            } else {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(40.dp))
            }
        }
    }
}

/**
 * The floor a picture is on, as a link to it: the thread scrolls there and the viewer closes into
 * it. A floor the thread hasn't loaded yet goes unnumbered, and the thread loads from it.
 */
@Composable
private fun FloorLink(floor: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (floor != null) stringResource(id = R.string.tip_post_floor, floor) else stringResource(id = R.string.photo_go_to_floor)
    Row(
        modifier = modifier
            .clickable(role = Role.Button, onClickLabel = label, interactionSource = null, indication = PressFeedback.overlay(SmoothCornerShape.Capsule), onClick = onClick)
            .background(Color.White.copy(alpha = 0.16f), SmoothCornerShape.Capsule)
            .padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Color.White, fontSize = 12.sp, maxLines = 1)
        Icon(Symbols.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
    }
}

/** How long the thread gets to scroll to a floor before the viewer closes back into it. */
private const val FloorCloseDelayMillis = 280L

/**
 * Which picture of how many, who posted it with what they wrote, and what can be done with it:
 * editing, picking its text, sharing and saving it, and copying its link behind a menu.
 */
@Composable
private fun PhotoChrome(
    picture: ThreadPicture,
    total: Int,
    /** The floor the picture is on, when the thread has loaded it. */
    floor: Int?,
    /** Whether the picture's floor can be gone to, known or not; tapped, it goes there. */
    hasFloor: Boolean,
    onFloor: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onCopyLink: () -> Unit,
    onEdit: () -> Unit,
    onExtract: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (total > 1) {
            MediaGlassPill(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = "${picture.overallIndex} / $total",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        // What was written with it on a row of its own, the actions under it: neither crowds the other
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (picture.author != null || picture.text != null) {
                // As wide as text is read at most, centred over the actions: in a wide window a
                // caption held to the left edge sat apart from both the picture and its actions
                Column(modifier = Modifier.widthIn(max = MaxReadingWidth).fillMaxWidth().padding(bottom = 12.dp)) {
                    var expanded by remember(picture.picId) { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        picture.author?.let {
                            Text(
                                text = it,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        if (hasFloor) FloorLink(floor, onFloor, modifier = Modifier.padding(start = if (picture.author != null) 8.dp else 0.dp))
                    }
                    picture.text?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            maxLines = if (expanded) 8 else 2,
                            overflow = TextOverflow.Ellipsis,
                            // Opening and closing, the caption grows and shrinks rather than jumping
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .animateContentSize(Motion.settle(IntSize.VisibilityThreshold))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = PressFeedback.overlay(Corners.xs)) {
                                    expanded = !expanded
                                },
                        )
                    }
                }
            }
            MediaGlassActions {
                PhotoAction(Symbols.Edit, stringResource(id = R.string.editor_edit_picture), onEdit)
                if (onExtract != null) PhotoAction(Symbols.DocumentScanner, stringResource(id = R.string.photo_extract_text), onExtract)
                PhotoAction(Symbols.Share, stringResource(id = R.string.title_share_pic), onShare)
                PhotoAction(Symbols.Download, stringResource(id = R.string.desc_download_pic), onSave)
                val copyLink = stringResource(id = R.string.title_copy_link)
                // What is done less often, behind one button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickMenu(items = { listOf(ContextMenuItem(Symbols.Link, copyLink, onClick = onCopyLink)) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = Symbols.MoreHoriz, contentDescription = stringResource(id = R.string.photo_more), tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun PhotoAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** Light status and navigation bar icons over the black, while the viewer shows. */
@Composable
internal fun LightSystemBars() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = status
            controller.isAppearanceLightNavigationBars = navigation
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Whether the picture shows whole and unzoomed, so a drag down may close it. */
private fun CoilZoomState?.atRest(edge: Boolean = true): Boolean {
    val zoomable = this?.zoomable ?: return true
    if (zoomable.userTransform.scaleX > 1.01f) return false
    // A long picture scrolled down drags back up first
    return !edge || zoomable.scrollEdge.vertical.let { it == Edge.START || it == Edge.BOTH }
}

private suspend fun release(from: Offset, to: Offset, velocity: Offset = Offset.Zero, onFrame: (Offset) -> Unit) {
    Animatable(from, Offset.VectorConverter).animateTo(to, Motion.settle(), initialVelocity = velocity) {
        onFrame(value)
    }
}

internal fun request(context: Context, url: String, placeholder: MemoryCache.Key?) =
    ImageRequest.Builder(context).data(url).placeholderMemoryCacheKey(placeholder).build()

internal fun share(context: Context, url: String) {
    context.toastShort(R.string.toast_preparing_share_pic)
    ImageUtil.download(context, url, true) { uri: Uri ->
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.title_share_pic))) }
    }
}

/** How far down a picture is dragged before letting go closes the viewer. */
private val DragDismissDistance = 96.dp

/**
 * Pictures tall enough to be read down open filling the width from the top. Wide ones open whole,
 * however wide: a strip filling the height would show only its start, and they are mostly seen at
 * a glance.
 */

private val TallReadMode = ReadMode.Default.copy(sizeType = ReadMode.SIZE_TYPE_VERTICAL)

/**
 * Where a picture of height over width [aspect] shows when open: whole and centred, or, when it
 * is long enough to be read down, filling the width from the top.
 */
private fun shownRect(container: Size, aspect: Float, readWidth: Float): Rect {
    if (aspect <= 0f || container.width <= 0f || container.height <= 0f) return Rect(Offset.Zero, container)
    val containerAspect = container.height / container.width
    val column = readColumn(container, aspect, readWidth)
    return when {
        column < container.width || isLong(Size(column, container.height), aspect) -> {
            val left = (container.width - column) / 2
            Rect(left, 0f, left + column, column * aspect)
        }
        aspect > containerAspect -> {
            val width = container.height / aspect
            Rect((container.width - width) / 2, 0f, (container.width + width) / 2, container.height)
        }

        else -> {
            val height = container.width * aspect
            Rect(0f, (container.height - height) / 2, container.width, (container.height + height) / 2)
        }
    }
}

/** Whether a picture of height over width [aspect] is long enough to be read down [container]. */
private fun isLong(container: Size, aspect: Float): Boolean = aspect > 0f && TallReadMode.accept(
    IntSizeCompat(1000, (1000 * aspect).roundToInt().coerceAtLeast(1)),
    IntSizeCompat(container.width.roundToInt(), container.height.roundToInt()),
)

/**
 * How wide a picture of height over width [aspect] shows in [container]: a long one, read down,
 * no wider than [readWidth]; anything else across the whole of it.
 */
private fun readColumn(container: Size, aspect: Float, readWidth: Float): Float {
    if (container.width <= readWidth) return container.width
    return if (isLong(Size(readWidth, container.height), aspect)) readWidth else container.width
}

/** Where the picture shows in the viewer, the zoomed view laid out in the middle of [container]. */
private fun CoilZoomState.displayIn(container: Size): Rect {
    val rect = zoomable.contentDisplayRectF
    val width = zoomable.containerSize.width
    return if (width > 0 && width < container.width) rect.translate((container.width - width) / 2f, 0f) else rect
}

/** This rect as a drag of [offset], scaled by [scale] about the container's centre, leaves it. */
private fun Rect.dragged(offset: Offset, scale: Float, container: Size): Rect {
    val centre = Offset(container.width / 2, container.height / 2)
    val topLeft = centre + (topLeft - centre) * scale + offset
    return Rect(topLeft, Size(width * scale, height * scale))
}

private fun Rect.scaledAround(scale: Float): Rect =
    Rect(center - Offset(width * scale / 2, height * scale / 2), Size(width * scale, height * scale))

internal fun Modifier.offsetTo(rect: Rect): Modifier = this
    .layout { measurable, _ ->
        val width = rect.width.roundToInt().coerceAtLeast(0)
        val height = rect.height.roundToInt().coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) { placeable.place(IntOffset(rect.left.roundToInt(), rect.top.roundToInt())) }
    }

/** A key per page; a picture shown twice in one post is told apart by how often it came before. */
internal fun pageKeys(pictures: List<ThreadPicture>): List<String> {
    val seen = HashMap<String, Int>()
    return pictures.map {
        val key = "${it.picId}_${it.postId}"
        val count = seen.merge(key, 1, Int::plus)!!
        if (count == 1) key else "${key}_$count"
    }
}

/**
 * How fast a flight from [from] to [to], measured as progress from 1 down to 0, starts when the
 * picture is let go at [velocity]: the finger's speed along the way to [to], over its length. Only
 * speed towards [to] carries over, and no more than a flight would sensibly start with.
 */
internal fun flightSpeed(from: Rect, to: Rect, velocity: Velocity): Float {
    val way = to.center - from.center
    val length = way.getDistance()
    if (length < 1f) return 0f
    val along = (velocity.x * way.x + velocity.y * way.y) / length
    return -(along.coerceAtLeast(0f) / length).coerceAtMost(MaxFlightSpeed)
}

/** Progress a second a flight may start at, so a hard fling still reads as flying back. */
private const val MaxFlightSpeed = 8f
