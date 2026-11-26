package oolong.client.ui.designsystem.theme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.material.Colors
import androidx.compose.material.LocalRippleConfiguration
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import oolong.client.data.settings.ColorStyle
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.ThemeSettings
import oolong.client.data.settings.rememberSettings
import oolong.client.data.settings.settingAsState
import oolong.client.ui.designsystem.ProvideChromeStyle
import oolong.client.ui.designsystem.ProvideGlassChoice
import oolong.client.ui.designsystem.theme.ThemeUtil
import oolong.client.ui.designsystem.theme.darken
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback

/**
 * A theme's colours. System colours are named for what they are for and suit any page; component
 * colours belong to one kind of component, as the top bar, and differ from the system colours in
 * at least one theme. A colour named `onXxx` goes on top of `xxx`.
 */
@Stable
data class ExtendedColors(
    val theme: String,
    val isNightMode: Boolean,
    // Brand
    /** The brand or theme colour: links, buttons. */
    val accent: Color = Color.Unspecified,
    val onAccent: Color = Color.Unspecified,
    /** Emphasis and selection: black in the default light theme. */
    val emphasis: Color = Color.Unspecified,
    val onEmphasis: Color = Color.White,
    // Surfaces
    /** Behind the page; clear on the translucent theme. */
    val background: Color = Color.Unspecified,
    /** Behind what must stay opaque, as sheets and dialogs, on the translucent theme too. */
    val sheet: Color = Color.Unspecified,
    val card: Color = Color.Unspecified,
    /** A block nested in a card: a quote, a reply, an original post. */
    val inset: Color = Color.Unspecified,
    val chip: Color = Color.Unspecified,
    val onChip: Color = Color.Unspecified,
    val divider: Color = Color.Unspecified,
    // Content
    val text: Color = Color.Unspecified,
    val textSecondary: Color = Color.Unspecified,
    val textDisabled: Color = Color.Unspecified,
    // Components
    /** Top bar titles and icons: solid, darker than body text. */
    val topBarContent: Color = Color.Unspecified,
    val topBarContentSecondary: Color = Color.Unspecified,
    val searchField: Color = Color.Unspecified,
    val onSearchField: Color = Color.Unspecified,
    /** Behind the pull-to-refresh and load-more spinners. */
    val menu: Color = Color.Unspecified,
    /** A chip that stands out, as the thread store mark. */
    val invertChip: Color = Color.Unspecified,
    val onInvertChip: Color = Color.Unspecified,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        ThemeUtil.THEME_DEFAULT,
        false,
    )
}

/** Material's colours for [extendedColors]: its dark ones at night and for light text over a wallpaper. */
@SuppressLint("ConflictingOnColor")
fun getColorPalette(extendedColors: ExtendedColors): Colors {
    val darkTheme = extendedColors.isNightMode || extendedColors.theme == ThemeUtil.THEME_TRANSLUCENT_LIGHT
    // Crossed over: Material's primary is our accent, and its secondary our emphasis
    val primaryColor = extendedColors.accent
    val secondaryColor = extendedColors.emphasis
    return if (darkTheme) {
        darkColors(
            primary = primaryColor,
            primaryVariant = primaryColor.darken(),
            secondary = secondaryColor,
            secondaryVariant = Color(0xFF3F310A),
            onSecondary = Color(0xFFFFFFFF),
            background = extendedColors.background,
            onBackground = extendedColors.text,
            surface = extendedColors.card,
        )
    } else {
        lightColors(
            primary = primaryColor,
            primaryVariant = primaryColor.darken(),
            secondary = secondaryColor,
            secondaryVariant = Color(0xFF000000),
            onSecondary = Color(0xFFFFFFFF),
            background = extendedColors.background,
            onBackground = extendedColors.text,
            surface = extendedColors.card,
        )
    }
}

private fun getDynamicColor(
    theme: String,
    tonalPalette: TonalPalette,
): ExtendedColors {
    val isDarkColorPalette = ThemeUtil.isNightMode(theme)
    return if (isDarkColorPalette) {
        if (theme == ThemeUtil.THEME_AMOLED_DARK) {
            getBlackDarkDynamicColor(tonalPalette)
        } else {
            getDarkDynamicColor(tonalPalette)
        }
    } else {
        getLightDynamicColor(tonalPalette)
    }
}

private fun getLightDynamicColor(tonalPalette: TonalPalette): ExtendedColors {
    val emphasis = tonalPalette.primary40
    return ExtendedColors(
        theme = "dynamic",
        isNightMode = false,
        accent = tonalPalette.secondary40,
        onAccent = tonalPalette.secondary100,
        emphasis = emphasis,
        background = tonalPalette.neutralVariant99,
        sheet = tonalPalette.neutralVariant99,
        card = tonalPalette.neutralVariant99,
        inset = tonalPalette.neutralVariant95,
        chip = tonalPalette.neutralVariant95,
        onChip = tonalPalette.neutralVariant40,
        divider = tonalPalette.neutralVariant95,
        text = tonalPalette.neutralVariant10,
        textSecondary = tonalPalette.neutralVariant40,
        textDisabled = tonalPalette.neutralVariant70,
        topBarContent = tonalPalette.neutralVariant10,
        topBarContentSecondary = tonalPalette.neutralVariant40,
        searchField = tonalPalette.neutralVariant95,
        onSearchField = tonalPalette.neutralVariant30,
        menu = tonalPalette.neutralVariant99,
        invertChip = emphasis,
        onInvertChip = tonalPalette.primary100,
    )
}

private fun getDarkDynamicColor(tonalPalette: TonalPalette): ExtendedColors {
    val emphasis = tonalPalette.primary80
    return ExtendedColors(
        theme = "dynamic",
        isNightMode = true,
        accent = tonalPalette.secondary80,
        onAccent = tonalPalette.secondary20,
        emphasis = emphasis,
        background = tonalPalette.neutralVariant10,
        sheet = tonalPalette.neutralVariant10,
        card = tonalPalette.neutralVariant20,
        inset = tonalPalette.neutralVariant20,
        chip = tonalPalette.neutralVariant20,
        onChip = tonalPalette.neutralVariant60,
        divider = tonalPalette.neutralVariant20,
        text = tonalPalette.neutralVariant90,
        textSecondary = tonalPalette.neutralVariant70,
        textDisabled = tonalPalette.neutralVariant50,
        topBarContent = tonalPalette.neutralVariant90,
        topBarContentSecondary = tonalPalette.neutralVariant70,
        searchField = tonalPalette.neutralVariant20,
        onSearchField = tonalPalette.neutralVariant70,
        menu = tonalPalette.neutralVariant20,
        invertChip = emphasis,
        onInvertChip = tonalPalette.primary10,
    )
}

private fun getBlackDarkDynamicColor(tonalPalette: TonalPalette): ExtendedColors {
    val emphasis = tonalPalette.primary80
    return ExtendedColors(
        theme = "dynamic",
        isNightMode = true,
        accent = tonalPalette.secondary80,
        onAccent = tonalPalette.secondary20,
        emphasis = emphasis,
        background = tonalPalette.neutralVariant0,
        sheet = tonalPalette.neutralVariant0,
        card = tonalPalette.neutralVariant10,
        inset = tonalPalette.neutralVariant10,
        chip = tonalPalette.neutralVariant10,
        onChip = tonalPalette.neutralVariant50,
        divider = tonalPalette.neutralVariant10,
        text = tonalPalette.neutralVariant90,
        textSecondary = tonalPalette.neutralVariant70,
        textDisabled = tonalPalette.neutralVariant50,
        topBarContent = tonalPalette.neutralVariant90,
        topBarContentSecondary = tonalPalette.neutralVariant70,
        searchField = tonalPalette.neutralVariant10,
        onSearchField = tonalPalette.neutralVariant70,
        menu = tonalPalette.neutralVariant10,
        invertChip = emphasis,
        onInvertChip = tonalPalette.primary10,
    )
}

/**
 * The colours [settings] give when the app is [dark] or light. [picks] changes when a colour that
 * is set apart from them is, as the custom theme's colour.
 */
@Composable
fun colorsOf(settings: ThemeSettings, dark: Boolean, picks: Any? = null): ExtendedColors {
    val context = LocalContext.current
    return remember(settings, dark, picks) { colorsFor(context, settings, dark) }
}

/** The colours [settings] give when the app is [dark] or light, outside of Compose too. */
fun colorsFor(context: Context, settings: ThemeSettings, dark: Boolean): ExtendedColors {
    val theme = settings.themeNameWhen(dark)
    if (settings.isMonet && !ThemeUtil.isTranslucentTheme(theme)) {
        val palette = if (settings.usesSystemPalette && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicTonalPalette(context)
        } else {
            monetPalette(colorOf(context, settings), settings.monetStyle)
        }
        return getDynamicColor(theme, palette)
    }
    // The system's colour is drawn as a custom one
    val nowTheme = if (settings.isSystemColor && !dark) ThemeUtil.THEME_CUSTOM else ThemeUtil.getCurrentTheme(theme)
    val colors = themeColors(
        nowTheme,
        // The wallpaper takes the theme colour
        if (settings.isSystemColor || ThemeUtil.isTranslucentTheme(nowTheme)) colorOf(context, settings) else pickedColor(context, nowTheme),
        plain = settings.style == ColorStyle.Plain,
        darkAccent = darkAccentOf(context, settings),
    )
    // The wallpaper at night has its light text colours, but it is night all the same
    return if (dark && !colors.isNightMode) colors.copy(isNightMode = true) else colors
}

/** The colours drawn now, for what is drawn outside of Compose. */
fun currentColors(context: Context): ExtendedColors = colorsFor(context, ThemeUtil.settingsState.value, ThemeUtil.isDark())

/** The colour picked in [settings]: a light theme's, the custom one or the system's. */
fun colorOf(context: Context, settings: ThemeSettings): Color =
    if (settings.isSystemColor) systemColor(context) else themeColors(settings.accent, pickedColor(context, settings.accent)).accent

/**
 * The colour [settings] give the dark themes: the light theme's own, but none for the default
 * blue, whose dark themes have their own blue.
 */
fun darkAccentOf(context: Context, settings: ThemeSettings): Color? =
    colorOf(context, settings).takeUnless { settings.accent == ThemeUtil.THEME_DEFAULT }

/** The translucent theme's text colour and the picked colours, which the colours depend on too. */
@Composable
fun rememberColorPicks(): Any? {
    // Settings of their own: changing them leaves the theme name as it was
    val text by settingAsState(SettingKeys.TranslucentBackgroundTheme)
    val customColor by settingAsState(SettingKeys.CustomPrimaryColor)
    return remember(text, customColor) { listOf(text, customColor) }
}

/** The app's theme; [dark] set makes it dark or light whatever the settings say, as for an editor. */
@Composable
fun TiebaLiteTheme(
    dark: Boolean? = null,
    content: @Composable () -> Unit
) {
    val theme by remember { ThemeUtil.themeState }
    val settings by remember { ThemeUtil.settingsState }
    // At night the wallpaper's theme is not a dark one by name: ask the settings
    val settingsDark = remember(theme, settings) { ThemeUtil.isDark() }
    val dark = dark ?: settingsDark
    val targetColors = colorsOf(settings, dark = dark, picks = rememberColorPicks())
    ThemeChangeFade(target = targetColors) { extendedColors -> ThemedContent(extendedColors, content) }
}

@Composable
private fun ThemedContent(extendedColors: ExtendedColors, content: @Composable () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    // Read as springs are made, which happens in composition, so set before anything below is composed
    Motion.bounceless = reduceMotion
    val colors = getColorPalette(extendedColors)

    // The system's text size, or the app's own in its place
    val settings = rememberSettings()
    val textScale by remember(settings) {
        settings.all.map { it.textScaleChoice() }.distinctUntilChanged()
    }.collectAsState(initial = remember(settings) { settings.all.value.textScaleChoice() })
    val systemDensity = LocalDensity.current
    val scale = if (textScale.followSystem) LocalConfiguration.current.fontScale else textScale.scale
    val density = if (textScale.followSystem) systemDensity else textScaledDensity(systemDensity.density, scale)

    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors,
        LocalDensity provides density,
        LocalTextScale provides scale,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colors = colors,
            typography = Typography,
            shapes = Shapes,
        ) {
            // Inside MaterialTheme, which provides its own ripple. Material's own components ask
            // for a ripple directly rather than the indication: without a configuration they draw none
            CompositionLocalProvider(LocalIndication provides PressFeedback.Overlay, LocalRippleConfiguration provides null) {
                ProvideChromeStyle { ProvideGlassChoice(content) }
            }
        }
    }
}

object ExtendedTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
