package oolong.client.ui.picker

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.model.DeviceAlbum
import oolong.client.model.DeviceImage
import oolong.client.ui.components.TipScreen
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.Button
import oolong.client.ui.designsystem.ContentSwap
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.SecondaryButton
import oolong.client.ui.designsystem.clickMenu
import oolong.client.ui.designsystem.floatingGlass
import oolong.client.ui.designsystem.maskTo
import oolong.client.ui.designsystem.motionItem
import oolong.client.ui.designsystem.pageScroll
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.editor.EditorPlace
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** What the picker may read of the device's pictures. */
enum class PickerAccess {
    All,

    /** Only the pictures the user chose to share, on Android 14 and later. */
    Selected,
    Denied,

    /** Denied, and the system no longer asks, so it has to be granted in the settings. */
    DeniedForever,
}

/**
 * The pictures a viewer was opened on, the one it opened at, and the thumbnails it grows out of
 * and back into: the grid's, or the tray's.
 */
private class Viewing(val pictures: List<DeviceImage>, val start: Int, val from: Map<Uri, ThumbnailPlace>)

/** Where a thumbnail lies in the window, and how round its corners are. */
internal class ThumbnailPlace(val bounds: Rect, val corner: Dp, val visible: Boolean)

/** Keeps where the thumbnail of [uri] lies in [places], while it is on screen. */
@Composable
private fun Modifier.placeIn(places: MutableMap<Uri, ThumbnailPlace>, uri: Uri, corner: () -> Dp): Modifier {
    DisposableEffect(places, uri) { onDispose { places.remove(uri) } }
    return onGloballyPositioned {
        // The corners as drawn, scaled as a picked picture is
        val topLeft = it.localToRoot(Offset.Zero)
        val bottomRight = it.localToRoot(Offset(it.size.width.toFloat(), it.size.height.toFloat()))
        places[uri] = ThumbnailPlace(Rect(topLeft, bottomRight), corner(), !it.boundsInRoot().isEmpty)
    }
}

/**
 * The picker: the pictures of one album in a grid under a bar that switches albums, with what is
 * picked on a glass tray along the bottom. Picking works the same with one picture to pick as
 * with many: a picture's circle picks it, the picture itself opens it large, and the tray's
 * button gives the picks back. Dragging from a circle, or from a long press, picks every picture
 * passed over; pinching changes how many pictures a row holds.
 */
@Composable
fun ImagePickerScreen(
    state: ImagePickerState,
    access: PickerAccess,
    onBack: () -> Unit,
    onShowAlbum: (Long?) -> Unit,
    onToggle: (Uri) -> Unit,
    onSelect: (List<Uri>) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onDone: (List<Uri>) -> Unit,
    onTakePhoto: () -> Unit,
    onEdit: (Uri, EditorPlace?) -> Unit,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val readable = access == PickerAccess.All || access == PickerAccess.Selected
    var viewing by remember { mutableStateOf<Viewing?>(null) }
    var columns by rememberSaveable { mutableIntStateOf(MinColumns) }
    val gridPlaces = remember { HashMap<Uri, ThumbnailPlace>() }
    val trayPlaces = remember { HashMap<Uri, ThumbnailPlace>() }
    // The picture the viewer shows, for the grid under it to keep in view
    var following by remember { mutableStateOf<Uri?>(null) }
    GlassScaffold(
        topBar = {
            GlassTopBar(
                title = {
                    AlbumSwitcher(
                        albums = state.albums,
                        albumId = state.albumId,
                        enabled = readable,
                        onShowAlbum = onShowAlbum,
                    )
                },
                navigationIcon = { BackNavigationIcon(onBackPressed = onBack) },
            )
        },
        overlay = {
            viewing?.let { shown ->
                PickerViewer(
                    pictures = shown.pictures,
                    start = shown.start,
                    state = state,
                    onToggle = onToggle,
                    onMove = onMove,
                    places = shown.from,
                    onPage = { following = it.takeIf { shown.from === gridPlaces } },
                    onEdit = onEdit,
                    onDone = onDone,
                    onClose = {
                        viewing = null
                        following = null
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Asking for access, the empty note and the pictures replace one another as a whole
            val face = when {
                !readable -> PickerFace.Access
                state.loaded && state.images.isEmpty() && access == PickerAccess.All -> PickerFace.Empty
                else -> PickerFace.Pictures
            }
            ContentSwap(targetState = face, modifier = Modifier.fillMaxSize()) { now ->
                when (now) {
                    PickerFace.Access -> AccessTip(
                        forever = access == PickerAccess.DeniedForever,
                        onRequestAccess = onRequestAccess,
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )

                    PickerFace.Empty -> TipScreen(
                        title = { Text(text = stringResource(id = R.string.title_picker_empty)) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )

                    // Another album fades in over the one before, from its top
                    PickerFace.Pictures -> AnimatedContent(
                        targetState = state,
                        contentKey = { it.albumId },
                        transitionSpec = { fadeIn(Motion.fadeIn()) togetherWith fadeOut(Motion.fadeOut()) },
                        label = "album",
                    ) { shown ->
                        ImageGrid(
                            state = shown,
                            columns = columns,
                            onColumns = { columns = it },
                            selectedOnly = access == PickerAccess.Selected,
                            contentPadding = padding,
                            places = gridPlaces,
                            following = following,
                            onToggle = onToggle,
                            onSelect = onSelect,
                            onOpen = { viewing = Viewing(shown.images, it, gridPlaces) },
                            onTakePhoto = onTakePhoto,
                            onSelectMore = onRequestAccess,
                        )
                    }
                }
            }
            if (readable) {
                PickedTray(
                    state = state,
                    onOpen = { index -> viewing = Viewing(state.selected.map { pictureOf(state, it) }, index, trayPlaces) },
                    onMove = onMove,
                    onDone = onDone,
                    places = trayPlaces,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/** The picture at [uri] as listed, or one standing in for it where the album shown leaves it out. */
internal fun pictureOf(state: ImagePickerState, uri: Uri): DeviceImage =
    state.images.firstOrNull { it.uri == uri } ?: DeviceImage(uri, albumId = 0, albumName = "", mimeType = "")

@Composable
private fun AlbumSwitcher(
    albums: ImmutableList<DeviceAlbum>,
    albumId: Long?,
    enabled: Boolean,
    onShowAlbum: (Long?) -> Unit,
) {
    val current = albums.firstOrNull { it.id == albumId }
    val names = albums.map { albumName(it) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickMenu(
                items = {
                    albums.mapIndexed { index, album ->
                        ContextMenuItem(
                            icon = null,
                            label = names[index],
                            detail = album.count.toString(),
                            leading = { AlbumCover(album) },
                            selected = album.id == albumId,
                        ) { onShowAlbum(album.id) }
                    }
                },
                enabled = enabled && albums.isNotEmpty(),
                indication = PressFeedback.overlay(Corners.full),
            )
            .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            text = current?.let { albumName(it) } ?: stringResource(id = R.string.title_picker_all_images),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (enabled && albums.isNotEmpty()) {
            Icon(imageVector = Symbols.ArrowDropDown, contentDescription = null)
        }
    }
}

@Composable
private fun AlbumCover(album: DeviceAlbum) {
    DeviceThumbnail(
        uri = album.cover,
        modifier = Modifier
            .size(40.dp)
            .maskTo(SmoothCornerShape(CornerSize(6.dp))),
    )
}

@Composable
private fun albumName(album: DeviceAlbum): String = when {
    album.id == null -> stringResource(id = R.string.title_picker_all_images)
    album.id == DeviceAlbum.Favorites -> stringResource(id = R.string.title_picker_favorites)
    album.name.isEmpty() -> stringResource(id = R.string.title_picker_unnamed_album)
    else -> album.name
}

/** The grid's key for the camera, which comes first in the album of every picture. */
private const val CameraKey = "camera"

@Composable
private fun ImageGrid(
    state: ImagePickerState,
    columns: Int,
    onColumns: (Int) -> Unit,
    selectedOnly: Boolean,
    contentPadding: PaddingValues,
    places: MutableMap<Uri, ThumbnailPlace>,
    following: Uri?,
    onToggle: (Uri) -> Unit,
    onSelect: (List<Uri>) -> Unit,
    onOpen: (Int) -> Unit,
    onTakePhoto: () -> Unit,
    onSelectMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val current by rememberUpdatedState(state)
    val currentToggle by rememberUpdatedState(onToggle)
    val currentSelect by rememberUpdatedState(onSelect)
    val currentOpen by rememberUpdatedState(onOpen)
    val currentTakePhoto by rememberUpdatedState(onTakePhoto)
    val currentColumns by rememberUpdatedState(columns)
    val showCamera = state.albumId == null
    // The grid keeps the picture the viewer over it shows in view, for the viewer to close into
    LaunchedEffect(following) {
        val uri = following ?: return@LaunchedEffect
        val index = state.images.indexOfFirst { it.uri == uri }.takeIf { it >= 0 } ?: return@LaunchedEffect
        val item = index + (if (selectedOnly) 1 else 0) + (if (showCamera) 1 else 0)
        val info = gridState.layoutInfo
        val shown = info.visibleItemsInfo.firstOrNull { it.index == item }
        val room = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding
        if (shown == null || shown.offset.y < 0 || shown.offset.y + shown.size.height > room) {
            // Into the middle, where it lies clear of the bars
            gridState.scrollToItem(item)
            val height = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == item }?.size?.height ?: 0
            gridState.scrollBy(-(room - height) / 2f)
        }
    }

    // Where a drag across the grid is, so it scrolls on near the top or bottom; and what it picks
    // as the grid moves under a still finger
    var dragAt by remember { mutableStateOf<Offset?>(null) }
    var onDragOver by remember { mutableStateOf<((Offset) -> Unit)?>(null) }
    val edgePx = with(density) { AutoScrollEdge.toPx() }
    val topPx = with(density) { contentPadding.calculateTopPadding().toPx() }
    LaunchedEffect(dragAt != null) {
        if (dragAt == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val at = dragAt ?: break
            val height = gridState.layoutInfo.viewportSize.height
            val speed = when {
                at.y < topPx + edgePx -> -((topPx + edgePx - at.y) / edgePx).coerceAtMost(1f)
                at.y > height - edgePx * 2 -> ((at.y - (height - edgePx * 2)) / edgePx).coerceAtMost(1f)
                else -> 0f
            }
            if (speed != 0f) {
                gridState.scrollBy(speed * MaxAutoScroll * density.density)
                onDragOver?.invoke(at)
            }
        }
    }

    // Items lie from the end of the top padding, a finger from the grid's own top
    fun topOf(item: LazyGridItemInfo) = item.offset.y - gridState.layoutInfo.viewportStartOffset

    fun itemAt(position: Offset): LazyGridItemInfo? = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
        position.x >= it.offset.x && position.x < it.offset.x + it.size.width &&
            position.y >= topOf(it) && position.y < topOf(it) + it.size.height
    }

    fun indexAt(position: Offset): Int? {
        val key = itemAt(position)?.key as? String ?: return null
        return current.images.indexOfFirst { it.uri.toString() == key }.takeIf { it >= 0 }
    }

    // Pinching scales the grid with the fingers, around the point between them; past a step it
    // takes a row's worth more or fewer pictures, drawn at the scale that matches what was shown,
    // and once the fingers lift it settles to its own size
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var zoomAt by remember { mutableStateOf(Offset.Zero) }

    /** The grid settling back to its size, which a new pinch takes over from where it is. */
    var settling by remember { mutableStateOf<Job?>(null) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }

    /** Changes to [to] columns, keeping the picture at [focus] where it is. */
    fun changeColumns(to: Int, focus: Offset) {
        val from = currentColumns
        val anchor = itemAt(focus)
        onColumns(to)
        anchor ?: return
        val within = (focus.y - topOf(anchor)) / anchor.size.height
        scope.launch {
            // Laid out with the new columns by the next frame
            withFrameNanos { }
            val placed = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == anchor.index }
            if (placed == null) {
                gridState.scrollToItem(anchor.index)
                return@launch
            }
            gridState.scrollBy(topOf(placed) + within * placed.size.height - focus.y)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { gridSize = it }
            // Two fingers pinching change how many pictures a row holds. Here, outside the grid's
            // scale, so the fingers' distance is measured as on the screen
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var start = 0f
                    var base = 1f
                    var pinched = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            pinched = true
                            val focus = (pressed[0].position + pressed[1].position) / 2f
                            val distance = (pressed[0].position - pressed[1].position).getDistance()
                            if (start == 0f) {
                                settling?.cancel()
                                start = distance
                                base = zoom
                            }
                            val ratio = distance / start
                            val columns = currentColumns
                            val next = when {
                                ratio > PinchStep && columns > MinColumns -> columns - 1
                                ratio < 1 / PinchStep && columns < MaxColumns -> columns + 1
                                else -> columns
                            }
                            if (next != columns) {
                                // The new cells are drawn as large as the old ones looked
                                base *= ratio * next / columns
                                start = distance
                                changeColumns(next, focus)
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                zoom = base
                            } else {
                                // Past the last step it gives only a little
                                val atEnd = (ratio > 1f && columns == MinColumns) || (ratio < 1f && columns == MaxColumns)
                                zoom = base * if (atEnd) ratio.pow(EndResistance) else ratio
                            }
                            zoomAt = focus
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (pinched) {
                        settling = scope.launch { animate(zoom, 1f, animationSpec = Motion.settle()) { value, _ -> zoom = value } }
                    }
                }
            },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + TrayRoom,
            ),
            horizontalArrangement = Arrangement.spacedBy(CellGap),
            verticalArrangement = Arrangement.spacedBy(CellGap),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = if (gridSize.width > 0 && gridSize.height > 0) {
                        TransformOrigin(zoomAt.x / gridSize.width, zoomAt.y / gridSize.height)
                    } else TransformOrigin.Center
                }
                .pageScroll(gridState)
                // A tap opens a picture, or its circle picks it; a drag from the circle, or from a
                // long press, picks or puts back every picture it passes over
                .pointerInput(Unit) {
                    val circlePx = CircleTouch.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val item = itemAt(down.position) ?: return@awaitEachGesture
                        val state = current
                        if (item.key == CameraKey) {
                            if (awaitPress(down.id, down.position) == Press.Tap) currentTakePhoto()
                            return@awaitEachGesture
                        }
                        val index = state.images.indexOfFirst { it.uri.toString() == item.key }
                        if (index < 0) return@awaitEachGesture
                        val uri = state.images[index].uri
                        val onCircle = down.position.x >= item.offset.x + item.size.width - circlePx &&
                            down.position.y < topOf(item) + circlePx
                        val press = if (onCircle) {
                            down.consume()
                            if (state.multiple) Press.Hold else awaitPress(down.id, down.position, consume = true)
                        } else {
                            awaitPress(down.id, down.position)
                        }
                        when {
                            press == Press.Tap && onCircle -> currentToggle(uri)
                            press == Press.Tap -> currentOpen(index)
                            press == Press.Hold && state.multiple -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Picks, or puts back, from where it started to where it is
                                val base = state.selected
                                val adding = uri !in base
                                var at = index
                                fun cover(to: Int) {
                                    at = to
                                    val passed = current.images.subList(min(index, to), max(index, to) + 1).map { it.uri }
                                    currentSelect(if (adding) base + passed.filter { it !in base } else base - passed.toSet())
                                }
                                cover(index)
                                onDragOver = { position -> indexAt(position)?.takeIf { it != at }?.let(::cover) }
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        change.consume()
                                        dragAt = change.position
                                        indexAt(change.position)?.takeIf { it != at }?.let {
                                            cover(it)
                                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        }
                                    }
                                } finally {
                                    dragAt = null
                                    onDragOver = null
                                }
                            }

                            press == Press.Hold -> currentOpen(index)
                            else -> {}
                        }
                    }
                },
        ) {
            if (selectedOnly) {
                item(key = "selected_only", span = { GridItemSpan(maxLineSpan) }) {
                    SelectedOnlyBanner(onSelectMore = onSelectMore)
                }
            }
            if (showCamera) {
                item(key = CameraKey) { CameraCell(onTakePhoto = onTakePhoto) }
            }
            items(state.images, key = { it.uri.toString() }) { image ->
                val order = state.selected.indexOf(image.uri)
                ImageCell(
                    image = image,
                    shown = state.edits[image.uri] ?: image.uri,
                    edited = image.uri in state.edits,
                    order = order.takeIf { it >= 0 }?.plus(1),
                    outOfReach = state.full && order < 0,
                    small = columns > MinColumns,
                    places = places,
                    onOpen = { onOpen(state.images.indexOf(image)) },
                    onToggle = { onToggle(image.uri) },
                )
            }
        }
    }
}

private enum class Press { Tap, Hold, Moved }

/**
 * Waits for the finger [id] to lift, which is a tap, or to stay down past a long press, which is
 * a hold; one moving away first, or a second finger, is neither, and is left to the grid.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitPress(
    id: androidx.compose.ui.input.pointer.PointerId,
    start: Offset,
    consume: Boolean = false,
): Press {
    var press = Press.Moved
    val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } > 1) return@withTimeoutOrNull
            val change = event.changes.firstOrNull { it.id == id } ?: return@withTimeoutOrNull
            if (consume) change.consume()
            if (!change.pressed) {
                press = Press.Tap
                return@withTimeoutOrNull
            }
            if ((change.position - start).getDistance() > viewConfiguration.touchSlop) return@withTimeoutOrNull
        }
    }
    return if (ended == null) Press.Hold else press
}

@Composable
private fun CameraCell(onTakePhoto: () -> Unit) {
    val label = stringResource(id = R.string.button_picker_take_photo)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .background(ExtendedTheme.colors.chip)
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick {
                    onTakePhoto()
                    true
                }
            },
    ) {
        Icon(
            imageVector = Symbols.PhotoCamera,
            contentDescription = null,
            tint = ExtendedTheme.colors.textSecondary,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun SelectedOnlyBanner(onSelectMore: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(id = R.string.tip_picker_selected_only),
            color = ExtendedTheme.colors.textSecondary,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(onClick = onSelectMore, color = ExtendedTheme.colors.emphasis) {
            Text(text = stringResource(id = R.string.button_picker_select_more))
        }
    }
}

/**
 * A picture in the grid. Picked, it draws in from its edges to a rounded tile with its place in
 * the picks on its circle; once no more can be picked, the rest fade and lose their circles.
 */
@Composable
private fun ImageCell(
    image: DeviceImage,
    /** What shows: the picture, or its edit. */
    shown: Uri,
    edited: Boolean,
    /** Where it is among the picked pictures, from 1, or null when not picked. */
    order: Int?,
    outOfReach: Boolean,
    small: Boolean,
    places: MutableMap<Uri, ThumbnailPlace>,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val elastic = !LocalReduceMotion.current
    val picked by animateFloatAsState(
        targetValue = if (order != null) 1f else 0f,
        animationSpec = if (!elastic) Motion.snap() else if (order != null) Motion.pop() else Motion.dismiss(),
        label = "picked",
    )
    // Others dim as the picks fill up, and light again as one is let go
    val reach by animateFloatAsState(if (outOfReach) OutOfReachAlpha else 1f, Motion.settle(), label = "reach")
    val select = stringResource(id = R.string.desc_picker_select)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .semantics {
                onClick {
                    onOpen()
                    true
                }
            },
    ) {
        DeviceThumbnail(
            uri = shown,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - (1f - PickedScale) * picked
                    scaleX = scale
                    scaleY = scale
                    alpha = reach
                }
                .placeIn(places, image.uri) { PickedCorner * picked.coerceIn(0f, 1f) }
                .maskTo(SmoothCornerShape(CornerSize(PickedCorner * picked.coerceIn(0f, 1f)))),
        )
        val inset = TagInset + TagInset * picked.coerceIn(0f, 1f)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = inset, bottom = inset),
        ) {
            if (image.isGif) PictureTag("GIF")
            else if (image.isLong) PictureTag(stringResource(id = R.string.tag_picker_long))
            if (edited) PictureTag(stringResource(id = R.string.tag_picker_edited))
        }
        if (!outOfReach) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(CircleTouch)
                    .semantics {
                        role = Role.Checkbox
                        selected = order != null
                        contentDescription = select
                        onClick {
                            onToggle()
                            true
                        }
                    },
            ) {
                PickCircle(order = order, size = if (small) 20.dp else 24.dp)
            }
        }
    }
}

/** The circle on a picture: empty, or filled with its place among the picks. */
@Composable
internal fun PickCircle(order: Int?, size: androidx.compose.ui.unit.Dp, filled: Color = ExtendedTheme.colors.emphasis, onFilled: Color = ExtendedTheme.colors.onEmphasis) {
    // Picked, the fill pops out of the ring's middle, swells a touch past it and settles at its
    // size; unpicked, it shrinks back in. The number goes with the fill and refocuses as it changes
    val reduced = LocalReduceMotion.current
    val fill by animateFloatAsState(
        targetValue = if (order != null) 1f else 0f,
        animationSpec = if (order != null) Motion.pop(Motion.ProgressThreshold) else Motion.dismiss(Motion.ProgressThreshold),
        label = "pickFill",
    )
    val anyFill by remember { derivedStateOf { fill > 0f } }
    var lastOrder by remember { mutableStateOf(order) }
    if (order != null) lastOrder = order
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(Color.Black.copy(alpha = 0.18f), CircleShape)
            .border(1.5.dp, Color.White, CircleShape),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val f = fill.coerceAtLeast(0f)
                    alpha = f.coerceAtMost(1f)
                    if (!reduced) {
                        val scale = 0.5f + 0.5f * f
                        scaleX = scale
                        scaleY = scale
                    }
                }
                .background(filled, CircleShape),
        ) {
            val shown = lastOrder
            if (shown != null && anyFill) {
                Refocus(targetState = shown) {
                    Text(
                        text = it.toString(),
                        color = onFilled,
                        fontSize = if (size < 24.dp) 11.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PictureTag(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f), Corners.xs)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/**
 * What is picked, on a glass tray along the bottom, in the order it is given back: a tap opens a
 * picture large, a long press lifts it to drag into another place. The button gives them back.
 */
@Composable
private fun PickedTray(
    state: ImagePickerState,
    onOpen: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onDone: (List<Uri>) -> Unit,
    places: MutableMap<Uri, ThumbnailPlace>,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.selected.isNotEmpty(),
        // Rises with the first pick; with motion reduced it only fades in where it stands
        enter = if (LocalReduceMotion.current) fadeIn(Motion.fadeIn()) else slideInVertically(Motion.present()) { it * 2 } + fadeIn(Motion.fadeIn()),
        exit = if (LocalReduceMotion.current) fadeOut(Motion.fadeOut()) else slideOutVertically(Motion.dismiss()) { it * 2 } + fadeOut(Motion.fadeOut()),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(TrayHeight)
                .floatingGlass(SmoothCornerShape.Capsule, barButton = false)
                .padding(start = 10.dp, end = 10.dp),
        ) {
            PickedStrip(
                state = state,
                onOpen = onOpen,
                onMove = onMove,
                places = places,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { onDone(state.result) }, contentPadding = PaddingValues(horizontal = 18.dp)) {
                // The count refocuses as pictures are picked and let go
                Refocus(targetState = if (state.multiple) stringResource(id = R.string.button_picker_add, state.selected.size) else stringResource(id = R.string.button_picker_use)) {
                    Text(text = it)
                }
            }
        }
    }
}

/** The picks as small tiles, numbered, that a long press lifts to drag into another place. */
@Composable
internal fun PickedStrip(
    state: ImagePickerState,
    onOpen: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    places: MutableMap<Uri, ThumbnailPlace>? = null,
    current: Uri? = null,
    filled: Color = ExtendedTheme.colors.emphasis,
    onFilled: Color = ExtendedTheme.colors.onEmphasis,
) {
    val haptic = LocalHapticFeedback.current
    val picks by rememberUpdatedState(state.selected)
    val stepPx = with(LocalDensity.current) { (ThumbSize + ThumbGap).toPx() }
    var lifted by remember { mutableStateOf<Uri?>(null) }
    var liftedBy by remember { mutableFloatStateOf(0f) }
    // Let go, the tile glides from under the finger into its place and sinks back to its size
    var dropped by remember { mutableStateOf<Uri?>(null) }
    val droppedBy = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun drop(uri: Uri) {
        lifted = null
        dropped = uri
        scope.launch {
            droppedBy.snapTo(liftedBy)
            droppedBy.animateTo(0f, Motion.settle())
            if (dropped == uri) dropped = null
        }
    }
    val listState = rememberLazyListState()

    // The row keeps its first tile in view by default, and would follow it as it is dragged
    // into another place; it stays where it is instead
    fun move(from: Int, to: Int) {
        listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        onMove(from, to)
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(ThumbGap),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 2.dp),
        modifier = modifier,
    ) {
        items(state.selected, key = { it.toString() }) { uri ->
            val isLifted = lifted == uri
            val isDropped = dropped == uri
            val lift by animateFloatAsState(if (isLifted) 1f else 0f, if (isLifted) Motion.press() else Motion.release(), label = "lift")
            Box(
                modifier = Modifier
                    .then(if (isLifted || isDropped) Modifier.zIndex(1f) else Modifier.motionItem(this))
                    .graphicsLayer {
                        translationX = when {
                            isLifted -> liftedBy
                            isDropped -> droppedBy.value
                            else -> 0f
                        }
                        val scale = 1f + (LiftedScale - 1f) * lift
                        scaleX = scale
                        scaleY = scale
                    }
                    .size(ThumbSize)
                    .pointerInput(uri) {
                        detectTapGestures(onTap = { picks.indexOf(uri).takeIf { it >= 0 }?.let(onOpen) })
                    }
                    .pointerInput(uri) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dropped = null
                                lifted = uri
                                liftedBy = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = { drop(uri) },
                            onDragCancel = { drop(uri) },
                        ) { change, amount ->
                            change.consume()
                            liftedBy += amount.x
                            val from = picks.indexOf(uri)
                            // Past half a tile, it trades places with the next one that way
                            if (liftedBy > stepPx / 2 && from < picks.lastIndex) {
                                move(from, from + 1)
                                liftedBy -= stepPx
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            } else if (liftedBy < -stepPx / 2 && from > 0) {
                                move(from, from - 1)
                                liftedBy += stepPx
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        }
                    },
            ) {
                DeviceThumbnail(
                    uri = state.edits[uri] ?: uri,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (places != null) Modifier.placeIn(places, uri) { ThumbCorner } else Modifier)
                        .maskTo(SmoothCornerShape(CornerSize(ThumbCorner)))
                        .then(
                            if (uri == current) Modifier.border(2.dp, Color.White, SmoothCornerShape(CornerSize(ThumbCorner)))
                            else Modifier
                        ),
                )
                if (state.multiple) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(filled),
                    ) {
                        Text(
                            text = (state.selected.indexOf(uri) + 1).toString(),
                            color = onFilled,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccessTip(
    forever: Boolean,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TipScreen(
        title = { Text(text = stringResource(id = R.string.title_picker_no_access)) },
        message = { Text(text = stringResource(id = R.string.tip_picker_no_access)) },
        actions = {
            Button(
                onClick = if (forever) onOpenSettings else onRequestAccess,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    text = stringResource(
                        id = if (forever) R.string.button_picker_open_settings else R.string.button_picker_grant
                    )
                )
            }
        },
        modifier = modifier,
    )
}

/** A thumbnail of [uri] as the grid and the tray show it, so drawn again from memory. */
@Composable
internal fun rememberThumbnailRequest(uri: Uri): ImageRequest {
    val context = LocalContext.current
    val cellPx = with(LocalDensity.current) { ThumbnailSize.roundToPx() }
    // A fixed size keeps the grid from flickering and decodes no more than a cell shows
    return remember(uri, cellPx) { ImageRequest.Builder(context).data(uri).size(cellPx).build() }
}

/** About as large as a grid cell gets. */
private val ThumbnailSize = 160.dp

/** A picture on the device, cropped to fill [modifier]'s bounds. */
@Composable
internal fun DeviceThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    AsyncImage(
        model = rememberThumbnailRequest(uri),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(ExtendedTheme.colors.chip),
    )
}

private const val MinColumns = 3
private const val MaxColumns = 5

/** How far apart two fingers move, as a ratio, to change the columns by one. */
private const val PinchStep = 1.3f

/** How much of a pinch past the fewest or most columns still scales the grid, as a power. */
private const val EndResistance = 0.3f

private val CellGap = 2.dp

/** The circle's touch area, larger than it looks, in a picture's top end corner. */
private val CircleTouch = 48.dp

/** How a picked picture draws in from its edges. */
private const val PickedScale = 0.86f
private val PickedCorner = 12.dp
private val TagInset = 6.dp
private const val OutOfReachAlpha = 0.5f

/** How near the top or bottom a drag across the grid scrolls it on, and how fast at most, per frame. */
private val AutoScrollEdge = 72.dp
private const val MaxAutoScroll = 14f

private val TrayHeight = 64.dp

/** Room under the grid's last row for the tray. */
private val TrayRoom = TrayHeight + 24.dp
private val ThumbSize = 44.dp
private val ThumbGap = 6.dp
private val ThumbCorner = 10.dp
private const val LiftedScale = 1.15f

private enum class PickerFace { Access, Empty, Pictures }
