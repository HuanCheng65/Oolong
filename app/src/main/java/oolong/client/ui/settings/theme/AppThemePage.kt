package oolong.client.ui.settings.theme

import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
import com.godaddy.android.colorpicker.HsvColor
import com.godaddy.android.colorpicker.harmony.ColorHarmonyMode
import com.godaddy.android.colorpicker.harmony.HarmonyColorPicker
import kotlinx.coroutines.flow.map
import oolong.client.R
import oolong.client.data.settings.Appearance
import oolong.client.data.settings.ColorStyle
import oolong.client.data.settings.MonetStyle
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.ThemeSettings
import oolong.client.data.settings.appPreferences
import oolong.client.data.settings.settingAsState
import oolong.client.navigation.AppThemeKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.navigation.TranslucentThemeKey
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.Dialog
import oolong.client.ui.designsystem.DialogNegativeButton
import oolong.client.ui.designsystem.DialogPositiveButton
import oolong.client.ui.designsystem.DialogState
import oolong.client.ui.designsystem.IconButton
import oolong.client.ui.designsystem.Switch
import oolong.client.ui.designsystem.clickMenu
import oolong.client.ui.designsystem.maskTo
import oolong.client.ui.designsystem.menuAnchor
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.rememberMenuTriggerState
import oolong.client.ui.designsystem.theme.ExtendedColors
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.designsystem.theme.ThemeUtil
import oolong.client.ui.designsystem.theme.TonalPalette
import oolong.client.ui.designsystem.theme.colorsOf
import oolong.client.ui.designsystem.theme.dynamicTonalPalette
import oolong.client.ui.designsystem.theme.monetPalette
import oolong.client.ui.designsystem.theme.pickedColor
import oolong.client.ui.designsystem.theme.rememberColorPicks
import oolong.client.ui.designsystem.theme.systemColor
import oolong.client.ui.designsystem.theme.themeColors
import oolong.client.ui.designsystem.theme.toHexString
import oolong.client.ui.settings.components.SettingsPage
import oolong.client.ui.settings.components.SettingsScope
import oolong.client.ui.settings.components.SettingsScreen
import oolong.client.ui.settings.components.prefs.PrefsChevron
import oolong.client.ui.settings.components.prefs.PrefsChoiceValue
import oolong.client.ui.settings.components.prefs.widgets.TextPref
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import java.io.File

/** What the theme page shows its choices with: the theme as set, the colours of each, and the wallpaper. */
class ThemeState(
    val settings: ThemeSettings,
    val picks: Any?,
    val wallpaper: File?,
    val customColorDialog: DialogState,
)

/**
 * The theme page: light or dark and the dark background, the colour and how it is used, and the
 * wallpaper. Only the two choices made most often are shown as pictures; the rest are rows.
 */
@OptIn(ExperimentalMaterialApi::class)
object ThemeSettingsPage : SettingsPage<ThemeState>(R.string.title_theme, AppThemeKey) {
    @Composable
    override fun rememberState(): ThemeState {
        val context = LocalContext.current
        val settings by remember { ThemeUtil.settingsState }
        val wallpaperPath by settingAsState(SettingKeys.TranslucentThemeBackgroundPath)
        return ThemeState(
            settings = settings,
            picks = rememberColorPicks(),
            wallpaper = wallpaperPath?.takeIf { it.isNotEmpty() }?.let(::File),
            customColorDialog = rememberDialogState(),
        )
    }

    @Composable
    override fun Effects(state: ThemeState) = CustomColorDialog(state.customColorDialog)

    override fun SettingsScope<ThemeState>.content() {
        group(R.string.title_appearance) {
            // Named for search by what it picks; the group's title names it on the page
            custom(R.string.title_appearance_mode, aliases = R.string.search_aliases_appearance) {
                AppearancePicker(
                    settings = settings,
                    light = colorsOf(settings, dark = false, picks = picks),
                    dark = colorsOf(settings, dark = true, picks = picks),
                    wallpaper = wallpaper.takeIf { settings.wallpaper },
                )
            }
            custom(R.string.title_dark_background) {
                DarkBackgroundRow(settings, monetDark = colorsOf(settings.copy(darkTheme = ThemeUtil.THEME_BLUE_DARK), dark = true, picks = picks).background)
            }
        }
        group(R.string.title_theme_color) {
            custom(R.string.title_theme_color) {
                AccentGrid(settings = settings, customColor = pickedColor(LocalContext.current, ThemeUtil.THEME_CUSTOM), onCustom = { customColorDialog.show() })
            }
            custom(R.string.title_color_style) { ColorStyleRow(settings.style) }
            custom(R.string.title_monet_style, shown = { settings.isMonet }) { MonetStyleRow(settings.monetStyle) }
        }
        group(R.string.title_background) {
            custom(R.string.title_wallpaper) { WallpaperRow(settings, wallpaper) }
            custom(R.string.title_wallpaper_settings, aliases = R.string.search_aliases_wallpaper, shown = { settings.wallpaper }) { WallpaperSettingsRow(wallpaper) }
        }
    }
}

@Composable
fun AppThemePage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(ThemeSettingsPage) }
}

/** Whether a wallpaper shows behind the pages; turning it on with none picked goes to pick one. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun WallpaperRow(settings: ThemeSettings, wallpaper: File?) {
    val navigator = LocalNavigator.current
    TextPref(
        title = stringResource(id = R.string.title_wallpaper),
        // Read as the switch it shows, though tapping may first pick a wallpaper
        modifier = Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(settings.wallpaper)
        },
        onClick = {
            when {
                settings.wallpaper -> ThemeUtil.setWallpaper(false)
                wallpaper == null -> navigator.navigate(TranslucentThemeKey)
                else -> ThemeUtil.setWallpaper(true)
            }
        },
        trailingContent = { Switch(checked = settings.wallpaper, onCheckedChange = null) },
    )
}

/** The wallpaper's own settings, shown by a small copy of it. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun WallpaperSettingsRow(wallpaper: File?) {
    val navigator = LocalNavigator.current
    TextPref(
        title = stringResource(id = R.string.title_wallpaper_settings),
        onClick = { navigator.navigate(TranslucentThemeKey) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = wallpaper,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).maskTo(SmoothCornerShape(6.dp)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                PrefsChevron()
            }
        },
    )
}

/** Light, dark or as the system is, each shown as a small home page in its colours. */
@Composable
private fun AppearancePicker(settings: ThemeSettings, light: ExtendedColors, dark: ExtendedColors, wallpaper: File?) {
    val nightOverlay = TranslucentWallpaper.nightOverlay(rememberWallpaperDim())
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppearanceOption(stringResource(id = R.string.appearance_light), settings.appearance == Appearance.Light, { ThemeUtil.setAppearance(Appearance.Light) }) {
            HomePreview(light, wallpaper)
        }
        AppearanceOption(stringResource(id = R.string.appearance_dark), settings.appearance == Appearance.Dark, { ThemeUtil.setAppearance(Appearance.Dark) }) {
            HomePreview(dark, wallpaper, nightOverlay)
        }
        AppearanceOption(stringResource(id = R.string.appearance_system), settings.appearance == Appearance.System, { ThemeUtil.setAppearance(Appearance.System) }) {
            HomePreview(light, wallpaper)
            // The lower right half dark: it turns with the system
            Box(modifier = Modifier.fillMaxSize().maskTo(LowerRightHalf)) { HomePreview(dark, wallpaper, nightOverlay) }
        }
    }
}

private val LowerRightHalf = GenericShape { size, _ ->
    moveTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

@Composable
internal fun RowScope.AppearanceOption(name: String, selected: Boolean, onClick: () -> Unit, preview: @Composable BoxScope.() -> Unit) {
    val colors = ExtendedTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clickable(interactionSource = null, indication = PressFeedback.overlay(SmoothCornerShape(16.dp)), onClickLabel = name, onClick = onClick)
            .padding(bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 17f)
                .border(2.dp, if (selected) colors.emphasis else Color.Transparent, SmoothCornerShape(16.dp))
                .padding(4.dp)
                .border(1.dp, colors.divider, Corners.md)
                .maskTo(Corners.md),
        ) {
            preview()
            if (selected) SelectedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.text else colors.textSecondary,
        )
    }
}

@Composable
private fun SelectedBadge(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(20.dp).background(ExtendedTheme.colors.emphasis, CircleShape),
    ) {
        Icon(Symbols.Check, contentDescription = null, tint = ExtendedTheme.colors.onEmphasis, modifier = Modifier.size(14.dp))
    }
}

/** A home page in miniature: the title, the search field, the tabs, two posts and the bottom bar. */
@Composable
private fun BoxScope.HomePreview(colors: ExtendedColors, wallpaper: File?, overlay: Float = 0f) {
    if (wallpaper != null) {
        AsyncImage(model = wallpaper, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        if (overlay > 0f) Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = overlay)))
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.matchParentSize().background(colors.background).padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Bar(colors.topBarContent, width = 0.45f, height = 6.dp)
        Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(colors.searchField, SmoothCornerShape.Capsule))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(width = 18.dp, height = 6.dp).background(colors.emphasis, SmoothCornerShape.Capsule))
            Box(modifier = Modifier.size(width = 14.dp, height = 6.dp).background(colors.chip, SmoothCornerShape.Capsule))
            Box(modifier = Modifier.size(width = 14.dp, height = 6.dp).background(colors.chip, SmoothCornerShape.Capsule))
        }
        repeat(2) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().background(colors.card, SmoothCornerShape(6.dp)).padding(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).background(colors.emphasis, CircleShape))
                    Bar(colors.text, width = 0.5f, height = 4.dp)
                }
                Bar(colors.textSecondary, width = 0.9f, height = 3.dp)
                Bar(colors.accent, width = 0.6f, height = 3.dp)
            }
        }
    }
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(12.dp)
            .background(colors.chip, SmoothCornerShape.Capsule),
    ) {
        Box(modifier = Modifier.size(5.dp).background(colors.emphasis, CircleShape))
        Box(modifier = Modifier.size(5.dp).background(colors.textSecondary, CircleShape))
        Box(modifier = Modifier.size(5.dp).background(colors.textSecondary, CircleShape))
    }
}

@Composable
internal fun Bar(color: Color, width: Float, height: Dp) {
    Box(modifier = Modifier.fillMaxWidth(width).height(height).background(color, SmoothCornerShape.Capsule))
}

/**
 * The dark background, chosen in a menu; a dot shows each one's colour. Monet makes its own dark
 * grey, so there it is that or pure black.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun DarkBackgroundRow(settings: ThemeSettings, monetDark: Color) {
    val options = if (settings.isMonet) {
        listOf(
            Triple(ThemeUtil.THEME_BLUE_DARK, stringResource(id = R.string.dark_background_default), monetDark),
            Triple(ThemeUtil.THEME_AMOLED_DARK, stringArrayResource(id = R.array.dark_theme_names).last(), Color.Black),
        )
    } else {
        val values = stringArrayResource(id = R.array.dark_theme_values)
        val names = stringArrayResource(id = R.array.dark_theme_names)
        values.mapIndexed { index, value -> Triple(value, names[index], themeColors(value).background) }
    }
    val selected = options.firstOrNull { it.first == settings.darkTheme } ?: options.first()
    val menu = rememberMenuTriggerState()
    Box(
        modifier = Modifier.clickMenu(
            state = menu,
            items = {
                options.map { (value, name, color) ->
                    ContextMenuItem(icon = null, label = name, leading = { BackgroundDot(color) }, selected = value == selected.first) {
                        // Monet's own grey leaves the theme picked for the other styles as it was
                        if (value != selected.first) ThemeUtil.selectDarkTheme(value)
                    }
                }
            },
        ),
    ) {
        TextPref(
            title = stringResource(id = R.string.title_dark_background),
            enabled = true,
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.menuAnchor(menu)) {
                    BackgroundDot(selected.third)
                    Spacer(modifier = Modifier.width(8.dp))
                    PrefsChoiceValue(text = selected.second)
                }
            },
        )
    }
}

@Composable
private fun BackgroundDot(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(color, CircleShape)
            .border(1.dp, ExtendedTheme.colors.divider, CircleShape),
    )
}

/**
 * The colours, four to a row, each a round swatch in its colours as the page now uses them: a
 * colour and how it is used, or under Monet the palette made from it, in four parts.
 */
@Composable
private fun AccentGrid(settings: ThemeSettings, customColor: Color?, onCustom: () -> Unit) {
    val names = stringArrayResource(id = R.array.accent_names)
    val context = LocalContext.current
    val dark = ExtendedTheme.colors.isNightMode
    val systemColor = remember { systemColor(context) }

    /** The swatch of [accent], whose colour is [color]. */
    fun fillOf(accent: String, color: Color): Pair<Brush, Color> {
        if (settings.isMonet) {
            val palette = if (accent == ThemeSettings.ACCENT_DYNAMIC && settings.monetStyle == MonetStyle.TonalSpot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicTonalPalette(context)
            } else {
                monetPalette(color, settings.monetStyle)
            }
            return palette.quarters(dark) to if (dark) palette.primary20 else Color.White
        }
        val light = themeColors(ThemeUtil.THEME_CUSTOM, color, plain = settings.style == ColorStyle.Plain)
        val colors = if (dark) themeColors(settings.darkTheme, darkAccent = color.takeUnless { accent == ThemeUtil.THEME_DEFAULT }) else light
        return Brush.radialGradient(listOf(colors.emphasis, colors.accent)) to colors.onEmphasis
    }

    val swatches = buildList<@Composable RowScope.() -> Unit> {
        ThemeSettings.ACCENTS.forEachIndexed { index, accent ->
            if (accent == ThemeUtil.THEME_CUSTOM) return@forEachIndexed
            add {
                val (fill, content) = remember(settings, dark) { fillOf(accent, themeColors(accent).accent) }
                Swatch(names[index], settings.accent == accent, fill, content, { ThemeUtil.selectAccent(accent) })
            }
        }
        add {
            val selected = settings.accent == ThemeUtil.THEME_CUSTOM
            val (fill, content) = remember(settings, dark, customColor) {
                fillOf(ThemeUtil.THEME_CUSTOM, customColor ?: themeColors(ThemeUtil.THEME_CUSTOM).accent)
            }
            Swatch(
                name = stringResource(id = R.string.title_custom_color),
                selected = selected,
                fill = fill,
                content = content,
                // Picked for the first time, or picked again, it asks for the colour
                onClick = { if (selected || customColor == null) onCustom() else ThemeUtil.selectAccent(ThemeUtil.THEME_CUSTOM) },
                icon = { Icon(Symbols.Palette, contentDescription = null, modifier = Modifier.size(20.dp)) },
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add {
                val (fill, content) = remember(settings, dark) { fillOf(ThemeSettings.ACCENT_DYNAMIC, systemColor) }
                Swatch(
                    name = stringResource(id = R.string.accent_system_wallpaper),
                    selected = settings.isSystemColor,
                    fill = fill,
                    content = content,
                    onClick = { ThemeUtil.selectAccent(ThemeSettings.ACCENT_DYNAMIC) },
                )
            }
        }
    }
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        swatches.chunked(4).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { it() }
                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** The palette in four parts, as the system shows its own: the three colours and the background. */
private fun TonalPalette.quarters(dark: Boolean): Brush {
    val parts = if (dark) {
        listOf(primary80, secondary80, neutralVariant30, tertiary80)
    } else {
        listOf(primary40, secondary40, neutralVariant90, tertiary40)
    }
    return Brush.sweepGradient(
        *parts.flatMapIndexed { index, color -> listOf(index / 4f to color, (index + 1) / 4f to color) }.toTypedArray(),
    )
}

@Composable
private fun RowScope.Swatch(
    name: String,
    selected: Boolean,
    fill: Brush,
    content: Color,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = ExtendedTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clickable(interactionSource = null, indication = PressFeedback.overlay(Corners.md), onClickLabel = name, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .border(2.dp, if (selected) colors.emphasis else Color.Transparent, CircleShape)
                .padding(4.dp)
                .background(fill, CircleShape)
                // Keeps the black swatch apart from a dark background
                .border(1.dp, colors.divider, CircleShape),
        ) {
            CompositionLocalProvider(LocalContentColor provides content) {
                when {
                    selected -> Icon(Symbols.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    icon != null -> icon()
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            fontSize = 12.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.text else colors.textSecondary,
        )
    }
}

/** Plain, bright or Monet. */
@Composable
private fun ColorStyleRow(selected: ColorStyle) {
    ChoiceRow(
        title = stringResource(id = R.string.title_color_style),
        selected = selected,
        options = listOf(
            Triple(ColorStyle.Plain, stringResource(id = R.string.color_style_plain), stringResource(id = R.string.color_style_plain_desc)),
            Triple(ColorStyle.Bright, stringResource(id = R.string.color_style_bright), stringResource(id = R.string.color_style_bright_desc)),
            Triple(ColorStyle.Monet, stringResource(id = R.string.color_style_monet), stringResource(id = R.string.color_style_monet_desc)),
        ),
        onSelect = { ThemeUtil.selectStyle(it) },
    )
}

/** How Monet makes its palette, in the styles the system offers. */
@Composable
private fun MonetStyleRow(selected: MonetStyle) {
    ChoiceRow(
        title = stringResource(id = R.string.title_monet_style),
        selected = selected,
        options = listOf(
            Triple(MonetStyle.TonalSpot, stringResource(id = R.string.monet_tonal_spot), stringResource(id = R.string.monet_tonal_spot_desc)),
            Triple(MonetStyle.Vibrant, stringResource(id = R.string.monet_vibrant), stringResource(id = R.string.monet_vibrant_desc)),
            Triple(MonetStyle.Expressive, stringResource(id = R.string.monet_expressive), stringResource(id = R.string.monet_expressive_desc)),
            Triple(MonetStyle.Neutral, stringResource(id = R.string.monet_neutral), stringResource(id = R.string.monet_neutral_desc)),
            Triple(MonetStyle.Rainbow, stringResource(id = R.string.monet_rainbow), stringResource(id = R.string.monet_rainbow_desc)),
            Triple(MonetStyle.FruitSalad, stringResource(id = R.string.monet_fruit_salad), stringResource(id = R.string.monet_fruit_salad_desc)),
            Triple(MonetStyle.Monochrome, stringResource(id = R.string.monet_monochrome), stringResource(id = R.string.monet_monochrome_desc)),
        ),
        onSelect = { ThemeUtil.selectMonetStyle(it) },
    )
}

/** A choice made in a menu that says what each option looks like. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun <T> ChoiceRow(title: String, selected: T, options: List<Triple<T, String, String>>, onSelect: (T) -> Unit) {
    val menu = rememberMenuTriggerState()
    Box(
        modifier = Modifier.clickMenu(
            state = menu,
            items = {
                options.map { (value, name, description) ->
                    ContextMenuItem(icon = null, label = name, detail = description, selected = value == selected) {
                        if (value != selected) onSelect(value)
                    }
                }
            },
        ),
    ) {
        TextPref(
            title = title,
            enabled = true,
            trailingContent = { PrefsChoiceValue(text = options.first { it.first == selected }.second, modifier = Modifier.menuAnchor(menu)) },
        )
    }
}

/** Picks the custom theme's colour on a wheel or as a hex code. */
@Composable
private fun CustomColorDialog(customPrimaryColorDialogState: DialogState) {
    val context = LocalContext.current
    var customPrimaryColor by remember { mutableStateOf(savedCustomColor(context)) }
    Dialog(
        dialogState = customPrimaryColorDialogState,
        title = { Text(text = stringResource(id = R.string.title_custom_theme)) },
        buttons = {
            DialogPositiveButton(
                text = stringResource(id = R.string.button_finish),
                onClick = {
                    context.appPreferences.customPrimaryColor = customPrimaryColor.toHexString()
                    ThemeUtil.selectAccent(ThemeUtil.THEME_CUSTOM)
                }
            )
            DialogNegativeButton(
                text = stringResource(id = R.string.button_cancel),
                onClick = {
                    customPrimaryColor = savedCustomColor(context)
                }
            )
        }
    ) {
        var useInput by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedContent(
                targetState = useInput,
                // One size change, the content's own, rather than its and the box's over each other
                transitionSpec = { fadeIn(Motion.fadeIn()) togetherWith fadeOut(Motion.fadeOut()) using SizeTransform(clip = false) { _, _ -> Motion.settle() } },
                label = "",
                modifier = Modifier.wrapContentHeight(),
            ) { input ->
                if (input) {
                    var inputHexColor by remember { mutableStateOf(customPrimaryColor.toHexString()) }
                    val lastValidColor by produceState(
                        initialValue = customPrimaryColor,
                        inputHexColor
                    ) {
                        if ("^#([0-9a-fA-F]{6})$".toRegex().matches(inputHexColor)) {
                            value = Color(inputHexColor.toColorInt())
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(lastValidColor)
                        )
                        OutlinedTextField(
                            value = inputHexColor,
                            onValueChange = {
                                if ("^#([0-9a-fA-F]{0,6})$".toRegex().matches(it)) {
                                    inputHexColor = it
                                }
                            },
                            maxLines = 1,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                cursorColor = ExtendedTheme.colors.emphasis,
                                focusedBorderColor = ExtendedTheme.colors.emphasis,
                                focusedLabelColor = ExtendedTheme.colors.emphasis
                            )
                        )
                        IconButton(
                            onClick = {
                                customPrimaryColor = Color(inputHexColor.toColorInt())
                                useInput = false
                            }
                        ) {
                            Icon(
                                imageVector = Symbols.Check,
                                contentDescription = stringResource(id = R.string.button_sure_default)
                            )
                        }
                    }
                } else {
                    Box {
                        HarmonyColorPicker(
                            harmonyMode = ColorHarmonyMode.ANALOGOUS,
                            color = HsvColor.from(customPrimaryColor),
                            onColorChanged = {
                                customPrimaryColor = it.toColor()
                            },
                            modifier = Modifier.sizeIn(maxWidth = 320.dp, maxHeight = 320.dp)
                        )

                        IconButton(
                            onClick = { useInput = true },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Symbols.BorderColor,
                                contentDescription = stringResource(id = R.string.desc_input_color)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The custom theme's colour as saved, or its default. */
private fun savedCustomColor(context: Context): Color =
    themeColors(ThemeUtil.THEME_CUSTOM, pickedColor(context, ThemeUtil.THEME_CUSTOM)).accent
