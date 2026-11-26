package oolong.client.ui.components

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.media.ImageUtil
import oolong.client.model.PhotoViewData
import oolong.client.ui.designsystem.LocalContextMenus
import oolong.client.ui.designsystem.ShapedPicture
import oolong.client.ui.designsystem.shapedPicture
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.photoview.LocalPhotoFloors
import oolong.client.ui.photoview.LocalPhotoViewer
import oolong.client.ui.photoview.photoMenuItems
import oolong.client.util.NetworkUtil
import oolong.core.designsystem.theme.Motion

/** How much black lies over pictures in night mode, when the user asks for it. */
private const val NightDim = 0.3f

private val DarkenFilter = ColorFilter.tint(Color.Black.copy(alpha = NightDim), BlendMode.SrcAtop)

/** The night dim pictures in pages carry now: 0 by day or when the user turned it off. */
@Composable
fun nightImageDim(): Float {
    val context = LocalContext.current
    return if (ExtendedTheme.colors.isNightMode && Settings.of(context)[SettingKeys.ImageDarkenWhenNightMode]) NightDim else 0f
}

/** A picture darkened by [dim] of black, or null when there is nothing to darken. */
fun dimFilter(dim: Float): ColorFilter? =
    if (dim <= 0f) null else ColorFilter.tint(Color.Black.copy(alpha = dim.coerceAtMost(1f)), BlendMode.SrcAtop)

/** What images show while they load, or when they cannot: a flat block of the placeholder colour. */
@Composable
fun rememberImagePlaceholder(): Painter {
    val color = colorResource(
        id = if (ExtendedTheme.colors.isNightMode) R.color.color_place_holder_night else R.color.color_place_holder
    )
    return remember(color) { ColorPainter(color) }
}

/**
 * The name every size of a Tieba picture shares: the 40 hex digits of its file name, as in
 * `…/sign=…/24c3da19…e512.jpg?tbpicau=…`. Null for addresses not named that way.
 */
internal fun pictureName(url: String): String? = PictureName.find(url)?.groupValues?.get(1)

/**
 * The name every copy of an avatar shares: its portrait, as in `…/portrait/item/tb.1.xxx?t=…` or
 * `…/portraith/item/tb.1.xxx`. Null for addresses not named that way.
 */
internal fun avatarName(url: String): String? = AvatarName.find(url)?.groupValues?.get(1)?.let { "avatar:$it" }

private val AvatarName = Regex("""/portraith?/item/([^/?#]+)""")

private val PictureName = Regex("""/([0-9a-f]{40})\.(?:jpg|jpeg|png|gif|webp)(?:[?#]|$)""", RegexOption.IGNORE_CASE)

/**
 * Where the last picture drawn under each name sits in the memory cache. Lists, a thread's first
 * look and the thread itself ask for different sizes of the same picture, so the one already shown
 * stands in while the next loads, rather than the placeholder.
 */
internal object ShownPictures {
    private val keys = LruCache<String, MemoryCache.Key>(256)

    operator fun get(name: String): MemoryCache.Key? = keys.get(name)

    operator fun set(name: String, key: MemoryCache.Key?) {
        if (key != null) keys.put(name, key)
    }
}

fun shouldLoadImage(context: Context, skipNetworkCheck: Boolean): Boolean {
    val imageLoadSettings =
        Settings.of(context)[SettingKeys.ImageLoadType].toIntOrNull() ?: ImageUtil.SETTINGS_SMART_ORIGIN
    return skipNetworkCheck ||
        imageLoadSettings == ImageUtil.SETTINGS_SMART_ORIGIN ||
        imageLoadSettings == ImageUtil.SETTINGS_ALL_ORIGIN ||
        (imageLoadSettings == ImageUtil.SETTINGS_SMART_LOAD && NetworkUtil.isWifiConnected(
            context
        ))
}

/**
 * A picture in a post or a list, as every one is drawn: the placeholder colour while it loads or
 * when it can't, darkened in night mode when the user asks for it, and, while [load] is false
 * (the data-saving setting holding it back), only what is cached already.
 */
@Composable
fun ContentImage(
    imageUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    load: Boolean = true,
    shape: Shape? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
) {
    val context = LocalContext.current
    val darken = nightImageDim() > 0f
    val placeholder = rememberImagePlaceholder()
    val placeholderColor = (placeholder as ColorPainter).color
    val name = remember(imageUri) { pictureName(imageUri) }
    // In a shape: the still picture drawn, the stand-in from the memory cache until it is in
    var shown by remember(imageUri, shape != null) {
        mutableStateOf(
            if (shape == null) {
                null
            } else {
                name?.let { ShownPictures[it] }?.let { (SingletonImageLoader.get(context).memoryCache?.get(it)?.image as? BitmapImage)?.bitmap }
            }
        )
    }
    var animated by remember(imageUri) { mutableStateOf(false) }
    // A picture fetched rather than already in memory fades in, as Coil's own crossfade does
    val appear = remember(imageUri) { Animatable(1f) }
    val appearScope = rememberCoroutineScope()
    val request = remember(imageUri, load, placeholderColor) {
        ImageRequest.Builder(context)
            .data(imageUri)
            // Another size of the same picture, drawn before, shows until this one is in
            .placeholderMemoryCacheKey(name?.let { ShownPictures[it] })
            .placeholder(ColorDrawable(placeholderColor.toArgb()).asImage())
            .apply { if (!load) networkCachePolicy(CachePolicy.DISABLED) }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        error = placeholder,
        onSuccess = { state ->
            name?.let { ShownPictures[it] = state.result.memoryCacheKey }
            if (shape != null) {
                val image = state.result.image
                if (shown == null && state.result.dataSource != DataSource.MEMORY_CACHE) {
                    appearScope.launch {
                        appear.snapTo(0f)
                        appear.animateTo(1f, Motion.fadeIn())
                    }
                }
                shown = (image as? BitmapImage)?.bitmap
                animated = image !is BitmapImage
            }
            onSuccess?.invoke(state)
        },
        onError = if (shape != null) {
            { shown = null }
        } else {
            null
        },
        colorFilter = if (darken) DarkenFilter else null,
        contentScale = contentScale,
        modifier = if (shape == null) {
            modifier
        } else {
            modifier.shapedPicture(shape, contentScale, if (darken) DarkenFilter else null, appear = { appear.value }) {
                ShapedPicture(shown, animated, placeholderColor)
            }
        },
    )
}

@Composable
fun NetworkImage(
    imageUri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    photoViewData: PhotoViewData? = null,
    contentScale: ContentScale = ContentScale.Fit,
    skipNetworkCheck: Boolean = false,
    shape: Shape? = null,
) {
    val context = LocalContext.current

    var shouldLoad by remember { mutableStateOf(shouldLoadImage(context, skipNetworkCheck)) }
    val enableClick = remember(photoViewData, shouldLoad) { photoViewData != null || !shouldLoad }

    var layoutSize by remember { mutableStateOf(IntSize.Zero) }

    var layoutOffset by remember { mutableStateOf(Offset.Zero) }

    var imageAspectRatio by remember { mutableFloatStateOf(0f) }
    var memoryCacheKey by remember { mutableStateOf<MemoryCache.Key?>(null) }

    // The viewer opens out of this picture and closes back into it
    val viewer = LocalPhotoViewer.current
    val picId = photoViewData?.picItems?.getOrNull(photoViewData.index)?.picId
    if (viewer != null && picId != null) {
        DisposableEffect(viewer, picId) { onDispose { viewer.place(picId, null) } }
    }
    val haptic = LocalHapticFeedback.current
    val menus = LocalContextMenus.current
    val originUrl = photoViewData?.picItems?.getOrNull(photoViewData.index)?.originUrl
    val menuItems = originUrl?.let { photoMenuItems(it) }
    // After a long press, the picture's menu follows this finger until it lifts
    var menuOpen by remember { mutableStateOf(false) }
    var boxOffset by remember { mutableStateOf(Offset.Zero) }
    val floors = LocalPhotoFloors.current
    // Loads the picture if it waits for a tap, then opens it in the viewer
    val open = {
        if (!shouldLoad) {
            shouldLoad = true
        } else if (photoViewData != null) {
            viewer?.open(
                data = photoViewData,
                from = Rect(layoutOffset, layoutSize.toSize()),
                thumbnail = memoryCacheKey,
                aspect = imageAspectRatio,
                floors = floors,
            )
        }
    }
    val moreLabel = stringResource(R.string.btn_more)

    Box(
        modifier = Modifier
            .pointerInput(enableClick, photoViewData, viewer, menuItems) {
                if (enableClick) {
                    detectTapGestures(
                        onLongPress = if (menus != null && menuItems != null) {
                            { offset ->
                                if (shouldLoad) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuOpen = true
                                    menus.show(menuItems, anchor = Rect(boxOffset + offset, Size.Zero))
                                }
                            }
                        } else {
                            null
                        },
                        onTap = { open() },
                    )
                }
            }
            // The taps above, for screen readers too; the menu opens at the picture's middle
            .semantics {
                if (enableClick) {
                    onClick {
                        open()
                        true
                    }
                    if (menus != null && menuItems != null && shouldLoad) {
                        onLongClick(label = moreLabel) {
                            val middle = Offset(layoutSize.width / 2f, layoutSize.height / 2f)
                            menus.show(menuItems, anchor = Rect(boxOffset + middle, Size.Zero))
                            true
                        }
                    }
                }
            }
            .pointerInput(menus) {
                if (menus == null) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        val position = boxOffset + change.position
                        if (!change.pressed) {
                            if (menuOpen) menus.liftFinger(position)
                            menuOpen = false
                            break
                        }
                        if (menuOpen) menus.moveFinger(position)
                    }
                }
            }
            .onGloballyPositioned { boxOffset = it.positionInWindow() }
            .then(modifier)
    ) {
        ContentImage(
            imageUri = imageUri,
            load = shouldLoad,
            shape = shape,
            onSuccess = {
                imageAspectRatio = it.result.image.height.toFloat() / it.result.image.width
                memoryCacheKey = it.result.memoryCacheKey
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    layoutSize = it
                }
                .onGloballyPositioned {
                    layoutOffset = it.positionInWindow()
                    if (viewer != null && picId != null) {
                        val shown = it.boundsInWindow()
                        viewer.place(
                            picId = picId,
                            bounds = Rect(layoutOffset, it.size.toSize()),
                            visible = shown.height >= it.size.height / 2f && shown.width >= it.size.width / 2f,
                        )
                    }
                },
            contentDescription = contentDescription,
            contentScale = contentScale,
        )
    }
}

@Composable
fun NetworkImage(
    imageUriProvider: () -> String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    photoViewDataProvider: (() -> PhotoViewData)? = null,
    contentScale: ContentScale = ContentScale.Fit,
    skipNetworkCheck: Boolean = false,
    shape: Shape? = null,
) {
    val imageUri by rememberUpdatedState(newValue = imageUriProvider())
    val photoViewData by rememberUpdatedState(newValue = photoViewDataProvider?.invoke())

    NetworkImage(
        imageUri = imageUri,
        contentDescription = contentDescription,
        modifier = modifier,
        photoViewData = photoViewData,
        contentScale = contentScale,
        skipNetworkCheck = skipNetworkCheck,
        shape = shape,
    )
}
