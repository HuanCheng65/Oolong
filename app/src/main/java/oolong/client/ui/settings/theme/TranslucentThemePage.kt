package oolong.client.ui.settings.theme

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import com.godaddy.android.colorpicker.HsvColor
import com.godaddy.android.colorpicker.harmony.ColorHarmonyMode
import com.godaddy.android.colorpicker.harmony.HarmonyColorPicker
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.media.PickMediasContract
import oolong.client.model.Agree
import oolong.client.model.Post
import oolong.client.model.PostContent
import oolong.client.model.SubPost
import oolong.client.model.TextSpan
import oolong.client.model.User
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.ui.components.LocalAccount
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.Dialog
import oolong.client.ui.designsystem.DialogNegativeButton
import oolong.client.ui.designsystem.DialogPositiveButton
import oolong.client.ui.designsystem.Slider
import oolong.client.ui.designsystem.maskTo
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.LocalExtendedColors
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.designsystem.theme.ThemeUtil
import oolong.client.ui.designsystem.theme.colorOf
import oolong.client.ui.designsystem.theme.getColorPalette
import oolong.client.ui.designsystem.theme.themeColors
import oolong.client.ui.designsystem.window.rememberSystemUiController
import oolong.client.ui.editor.CropRequest
import oolong.client.ui.editor.LocalImageEditor
import oolong.client.ui.thread.PostCardOptions
import oolong.client.ui.thread.PostItem
import oolong.client.ui.thread.ThreadFloor
import oolong.client.util.toastShort
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.Radius
import oolong.core.designsystem.theme.SmoothCornerShape
import java.io.File
import kotlin.math.roundToInt

/**
 * Sets up the translucent theme: the wallpaper fills the page as the app will draw it, with a
 * post over it in the chosen colours, and the settings sit in a panel at the bottom. Nothing is
 * saved until it is applied.
 */
@Composable
fun TranslucentThemePage(
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = remember { TranslucentWallpaper.source(context) }
    // Bumped whenever a new image is cropped into [source], so the preview reloads it
    var sourceVersion by rememberSaveable { mutableIntStateOf(if (source.exists()) 1 else 0) }
    var text by rememberSaveable { mutableStateOf(WallpaperText.Auto) }
    var dim by rememberSaveable { mutableStateOf(TranslucentWallpaper.DEFAULT_DIM) }
    var blur by rememberSaveable { mutableIntStateOf(0) }
    var accent by rememberSaveable { mutableStateOf<Int?>(null) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        val saved = TranslucentWallpaper.saved(context)
        text = saved.text
        dim = saved.dim
        blur = saved.blur
        accent = saved.accent
        loaded = true
    }
    var applying by remember { mutableStateOf(false) }
    val wallpaper = TranslucentWallpaper(text, dim, blur, accent)

    var swatches by remember { mutableStateOf(emptyList<Int>()) }
    var lightness by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sourceVersion) {
        if (sourceVersion > 0) {
            swatches = context.wallpaperColors(source)
            lightness = context.wallpaperLightness(source) ?: 0f
        }
    }
    val darkText = wallpaper.darkText(lightness)

    val pickImage = rememberWallpaperPicker(source) { sourceVersion++ }

    // Status bar icons follow the preview's text colour while the page is open
    val systemUiController = rememberSystemUiController()
    DisposableEffect(systemUiController, darkText) {
        systemUiController.setStatusBarColor(Color.Transparent, darkIcons = darkText)
        onDispose { systemUiController.setStatusBarColor(Color.Transparent, darkIcons = ThemeUtil.isStatusBarFontDark()) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (sourceVersion > 0) {
            WallpaperPreview(source, sourceVersion, wallpaper)
        }
        PreviewColors(wallpaper, darkText) {
            Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.captionBar))) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                ) {
                    BackNavigationIcon(onBackPressed = { navigator.navigateUp() })
                    Text(
                        text = stringResource(id = R.string.title_theme_translucent),
                        color = ExtendedTheme.colors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (sourceVersion > 0) SamplePost()
            }
        }
        SettingsPanel(modifier = Modifier.align(Alignment.BottomCenter)) {
            SettingsBlock {
                PanelRow(
                    title = stringResource(id = R.string.title_translucent_background),
                    onClick = pickImage,
                    trailing = { Text(text = "›", color = ExtendedTheme.colors.textSecondary, fontSize = 20.sp) },
                )
            }
            if (sourceVersion > 0) {
                SettingsBlock {
                    PanelRow(title = stringResource(id = R.string.title_translucent_text_color)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice(stringResource(id = R.string.text_color_auto), selected = text == WallpaperText.Auto) { text = WallpaperText.Auto }
                            Choice(stringResource(id = R.string.light_color), selected = text == WallpaperText.Light) { text = WallpaperText.Light }
                            Choice(stringResource(id = R.string.dark_color), selected = text == WallpaperText.Dark) { text = WallpaperText.Dark }
                        }
                    }
                    AccentRow(accent, swatches) { accent = it }
                    SliderRow(
                        title = stringResource(id = R.string.title_translucent_theme_dim),
                        value = dim,
                        range = 0f..TranslucentWallpaper.MAX_DIM,
                        label = "${(dim * 100).roundToInt()}%",
                        onChange = { dim = it },
                    )
                    var blurDraft by remember(blur) { mutableStateOf(wallpaper.blurPosition) }
                    SliderRow(
                        title = stringResource(id = R.string.title_translucent_theme_blur),
                        value = blurDraft,
                        range = 0f..1f,
                        label = "${(blurDraft * 100).roundToInt()}%",
                        onChange = { blurDraft = it },
                        // Blurring redraws the image, so only once the thumb is let go
                        onChangeFinished = { blur = TranslucentWallpaper.blurAt(blurDraft) },
                    )
                }
                ApplyButton(enabled = !applying) {
                    applying = true
                    scope.launch {
                        val applied = context.applyWallpaper(source, wallpaper)
                        applying = false
                        if (applied) navigator.navigateUp() else context.toastShort(R.string.text_load_failed)
                    }
                }
            }
        }
    }
}

/** The wallpaper blurred and dimmed as [wallpaper] says, filling the page. */
@Composable
private fun WallpaperPreview(source: File, version: Int, wallpaper: TranslucentWallpaper) {
    val context = LocalContext.current
    val request = remember(version, wallpaper.blur) {
        ImageRequest.Builder(context)
            .data(source)
            .memoryCacheKey("translucent-preview:$version:${wallpaper.blur}")
            .diskCachePolicy(CachePolicy.DISABLED)
            .apply { wallpaper.blurTransformation(context)?.let { transformations(it) } }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = wallpaper.dim)))
}

/** The translucent theme's colours for [wallpaper] with [darkText] or light text, whatever theme the app uses now. */
@Composable
private fun PreviewColors(wallpaper: TranslucentWallpaper, darkText: Boolean, content: @Composable () -> Unit) {
    val variant = if (darkText) ThemeUtil.THEME_TRANSLUCENT_DARK else ThemeUtil.THEME_TRANSLUCENT_LIGHT
    // Without a colour picked here, the theme colour
    val context = LocalContext.current
    val settings by remember { ThemeUtil.settingsState }
    val colors = themeColors(variant, wallpaper.accent?.let { Color(it) } ?: colorOf(context, settings))
    CompositionLocalProvider(LocalExtendedColors provides colors) {
        MaterialTheme(colors = getColorPalette(colors)) {
            CompositionLocalProvider(LocalContentColor provides colors.text, content = content)
        }
    }
}

/** A floor and a reply in it, to judge how well text reads on the wallpaper. */
@Composable
private fun SamplePost() {
    val account = LocalAccount.current
    val appName = stringResource(id = R.string.app_name)
    val author = User(
        id = 1,
        name = account?.name.orEmpty(),
        nameShow = account?.nameShow ?: account?.name ?: appName,
        portrait = account?.portrait.orEmpty(),
    )
    val app = User(id = 2, name = "", nameShow = appName, portrait = "")
    val question = stringResource(id = R.string.sample_translucent_question)
    val answer = stringResource(id = R.string.sample_translucent_answer)
    val item = remember(author, question, answer) {
        PostItem(
            Post(
                id = 2,
                floor = 2,
                title = "",
                time = System.currentTimeMillis() / 1000,
                author = author,
                content = text(question),
                agree = Agree(count = 0, agreed = false),
                subPosts = persistentListOf(SubPost(id = 3, author = app, content = text(answer), time = 0)),
                subPostCount = 1,
            )
        )
    }
    // Only a picture of a post: taps and long presses do nothing
    Box(
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    ) {
        ThreadFloor(
            item = item,
            options = PostCardOptions(threadAuthorId = 0),
            onEvent = {},
            listState = rememberLazyListState(),
            key = "Preview",
            startsRule = true,
        )
    }
}

private fun text(value: String) = persistentListOf<PostContent>(PostContent.Text(persistentListOf(TextSpan.Plain(value))))

/** The opaque panel at the bottom, as sheets are opaque on the translucent theme. */
@Composable
private fun BoxScope.SettingsPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .background(ExtendedTheme.colors.sheet, SmoothCornerShape(Radius.xl, bottom = false))
            .maskTo(SmoothCornerShape(Radius.xl, bottom = false))
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A grey block of rows, as in the settings pages. */
@Composable
private fun SettingsBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(ExtendedTheme.colors.chip, Corners.md)
            .maskTo(Corners.md)
            .padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
private fun PanelRow(
    title: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
    ) {
        Text(text = title, color = ExtendedTheme.colors.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = ExtendedTheme.colors
    Text(
        text = text,
        color = if (selected) colors.onAccent else colors.textSecondary,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .background(if (selected) colors.accent else colors.divider, SmoothCornerShape.Capsule)
            .clickable(interactionSource = null, indication = PressFeedback.overlay(SmoothCornerShape.Capsule), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** The emphasis colour: the theme's default, colours from the wallpaper, or one of the user's own. */
@Composable
private fun AccentRow(accent: Int?, swatches: List<Int>, onChange: (Int?) -> Unit) {
    val dialog = rememberDialogState()
    var custom by remember { mutableStateOf(Color(accent ?: swatches.firstOrNull() ?: Color.Gray.toArgb())) }
    Dialog(
        dialogState = dialog,
        title = { Text(text = stringResource(id = R.string.title_select_color)) },
        buttons = {
            DialogPositiveButton(text = stringResource(id = R.string.button_sure_default)) { onChange(custom.toArgb()) }
            DialogNegativeButton(text = stringResource(id = R.string.button_cancel))
        },
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            HarmonyColorPicker(
                harmonyMode = ColorHarmonyMode.NONE,
                color = HsvColor.from(custom),
                onColorChanged = { custom = it.toColor() },
                modifier = Modifier.sizeIn(maxWidth = 280.dp, maxHeight = 280.dp),
            )
        }
    }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = stringResource(id = R.string.title_select_color),
            color = ExtendedTheme.colors.text,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            item {
                Swatch(color = null, selected = accent == null, onClick = { onChange(null) }) {
                    Icon(Symbols.FormatColorReset, contentDescription = null, tint = ExtendedTheme.colors.textSecondary)
                }
            }
            items(swatches) { color ->
                Swatch(color = Color(color), selected = accent == color, onClick = { onChange(color) })
            }
            item {
                val isCustom = accent != null && accent !in swatches
                Swatch(color = if (isCustom) Color(accent!!) else null, selected = isCustom, onClick = { dialog.show() }) {
                    if (!isCustom) Icon(Symbols.Add, contentDescription = null, tint = ExtendedTheme.colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun Swatch(
    color: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = ExtendedTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color ?: colors.divider)
            .border(if (selected) 2.dp else 0.dp, if (selected) colors.text else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
    ) {
        if (selected && color != null) Icon(Symbols.Check, contentDescription = null, tint = Color.White)
        content()
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit,
    onChangeFinished: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, color = ExtendedTheme.colors.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(text = label, color = ExtendedTheme.colors.textSecondary, fontSize = 14.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = range,
        )
    }
}

@Composable
private fun ApplyButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = ExtendedTheme.colors
    Text(
        text = stringResource(id = R.string.button_apply),
        color = colors.onAccent,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(if (enabled) colors.accent else colors.accent.copy(alpha = 0.5f), SmoothCornerShape.Capsule)
            .clickable(interactionSource = null, indication = PressFeedback.overlay(SmoothCornerShape.Capsule), enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

/**
 * Picks an image and crops it to the screen's shape into [output], then calls [onPicked].
 * Returns what starts it.
 */
@Composable
private fun rememberWallpaperPicker(output: File, onPicked: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val editor = LocalImageEditor.current
    val pickLauncher = rememberLauncherForActivityResult(PickMediasContract) { uris ->
        val picked = uris.firstOrNull() ?: return@rememberLauncherForActivityResult
        val metrics = context.resources.displayMetrics
        val request = CropRequest(
            source = picked,
            output = output,
            aspect = metrics.widthPixels.toFloat() / metrics.heightPixels,
            maxWidth = metrics.widthPixels,
            quality = 95,
        )
        editor?.open(request) { written -> if (written) onPicked() }
    }
    return { pickLauncher.launch(1) }
}
