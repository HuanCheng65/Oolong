package oolong.client.ui.designsystem.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.core.graphics.toColorInt
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.ui.designsystem.theme.ThemeUtil
import kotlin.math.hypot

private val TiebaBlue = Color(0xFF4477E0)
private val NightBlue = Color(0xFF5588D7)

/** What every light theme shares; they differ only in their accent and emphasis. */
private fun light(theme: String, accent: Color, emphasis: Color = accent) = ExtendedColors(
    theme = theme,
    isNightMode = false,
    accent = accent,
    onAccent = Color.White,
    emphasis = emphasis,
    background = Color.White,
    sheet = Color.White,
    card = Color.White,
    inset = Color(0xFFF8F8F8),
    chip = Color(0xFFF8F8F8),
    onChip = Color(0xFF808080),
    divider = Color(0xFFF5F5F5),
    text = Color(0xDE000000),
    textSecondary = Color(0x99000000),
    textDisabled = Color(0xFF696969),
    topBarContent = Color.Black,
    topBarContentSecondary = Color(0xDD545967),
    searchField = Color(0xFFF8F8F8),
    onSearchField = Color(0xFFBFBFBF),
    menu = Color.White,
    invertChip = emphasis,
    onInvertChip = Color.White,
)

/** A night theme by its surfaces; they share the accent, emphasis and text. */
private fun night(
    theme: String,
    background: Color,
    card: Color,
    inset: Color,
    chip: Color,
    onChip: Color,
    divider: Color,
    searchField: Color,
    onSearchField: Color,
) = ExtendedColors(
    theme = theme,
    isNightMode = true,
    accent = TiebaBlue,
    onAccent = background,
    emphasis = NightBlue,
    background = background,
    sheet = background,
    card = card,
    inset = inset,
    chip = chip,
    onChip = onChip,
    divider = divider,
    text = Color(0xDEEEEEEE),
    textSecondary = Color(0x99EEEEEE),
    textDisabled = Color(0xCC808080),
    topBarContent = Color(0xFFEEEEEE),
    topBarContentSecondary = Color(0xCCEEEEEE),
    searchField = searchField,
    onSearchField = onSearchField,
    menu = card,
    invertChip = NightBlue.copy(alpha = 0.3f),
    onInvertChip = NightBlue,
)

private val BlueDark = night(
    ThemeUtil.THEME_BLUE_DARK,
    background = Color(0xFF17212B),
    card = Color(0xFF202B37),
    inset = Color(0xFF242F3D),
    chip = Color(0xFF242F3D),
    onChip = Color(0xFF68737E),
    divider = Color(0xFF242F3D),
    searchField = Color(0xFF242F3D),
    onSearchField = Color(0xFF626C79),
)

private val GreyDark = night(
    ThemeUtil.THEME_GREY_DARK,
    background = Color(0xFF202020),
    card = Color(0xFF2A2A2A),
    inset = Color(0xFF1E1E1E),
    chip = Color(0xFF323232),
    onChip = Color(0xFF808080),
    divider = Color(0xFF1E1E1E),
    searchField = Color(0xFF2C2C2C),
    onSearchField = Color(0xFF5C5C5C),
)

private val AmoledDark = night(
    ThemeUtil.THEME_AMOLED_DARK,
    background = Color.Black,
    card = Color(0xFF101010),
    inset = Color(0xFF1E1E1E),
    chip = Color(0xFF1E1E1E),
    onChip = Color(0xFF808080),
    divider = Color(0xFF1E1E1E),
    searchField = Color(0xFF1E1E1E),
    onSearchField = Color(0xFF5C5C5C),
)

/** This dark theme with [accent], lifted to read on it, in place of the blue. */
private fun ExtendedColors.withAccent(accent: Color?): ExtendedColors {
    if (accent == null) return this
    val lifted = lifted(accent)
    return copy(
        accent = lifted,
        emphasis = lifted,
        // Lifted colours are light: what goes on them is dark
        onEmphasis = background,
        invertChip = lifted.copy(alpha = 0.3f),
        onInvertChip = lifted,
    )
}

/**
 * [color] as light as a dark background needs: the same hue and chroma, its lightness raised to
 * at least [MIN_DARK_LIGHTNESS]. Near-greys, as black, become a light grey.
 */
internal fun lifted(color: Color): Color {
    val lab = color.convert(ColorSpaces.CieLab)
    val (lightness, a, b) = Triple(lab.red, lab.green, lab.blue)
    if (hypot(a, b) < GREY_CHROMA) {
        return Color(GREY_LIGHTNESS, 0f, 0f, 1f, ColorSpaces.CieLab).convert(ColorSpaces.Srgb)
    }
    return Color(maxOf(lightness, MIN_DARK_LIGHTNESS), a, b, 1f, ColorSpaces.CieLab).convert(ColorSpaces.Srgb)
}

private const val MIN_DARK_LIGHTNESS = 70f
private const val GREY_LIGHTNESS = 82f
private const val GREY_CHROMA = 8f

/**
 * The translucent theme with light text over a dark wallpaper. The page itself is clear; what
 * must stay opaque, as sheets, menus and spinners, takes [ExtendedColors.sheet].
 */
private fun translucentLight(accent: Color) = ExtendedColors(
    theme = ThemeUtil.THEME_TRANSLUCENT_LIGHT,
    isNightMode = false,
    accent = accent,
    onAccent = Color.White,
    emphasis = accent,
    background = Color.Transparent,
    sheet = Color(0xFF202020),
    card = Color(0x10FFFFFF),
    inset = Color(0x15FFFFFF),
    chip = Color(0x18F8F8F8),
    onChip = Color(0xFF808080),
    divider = Color(0x10FFFFFF),
    text = Color(0xDEFFFFFF),
    textSecondary = Color(0x99FFFFFF),
    textDisabled = Color(0x61FFFFFF),
    topBarContent = Color(0xDFFFFFFF),
    topBarContentSecondary = Color(0x8AFFFFFF),
    searchField = Color(0x10FFFFFF),
    onSearchField = Color(0xFFBFBFBF),
    menu = Color(0xFF202020),
    invertChip = accent,
    onInvertChip = Color.White,
)

/** The translucent theme with dark text over a light wallpaper. */
private fun translucentDark(accent: Color) = ExtendedColors(
    theme = ThemeUtil.THEME_TRANSLUCENT_DARK,
    isNightMode = false,
    accent = accent,
    onAccent = Color(0xFF212121),
    emphasis = accent,
    background = Color.Transparent,
    sheet = Color(0xFFF8F8F8),
    card = Color(0x20000000),
    inset = Color(0x2A000000),
    chip = Color(0x2A323232),
    onChip = Color(0xFF808080),
    divider = Color(0x15000000),
    text = Color(0xDE000000),
    textSecondary = Color(0x99000000),
    textDisabled = Color(0x80000000),
    topBarContent = Color.Black,
    topBarContentSecondary = Color(0xB3000000),
    searchField = Color(0x20000000),
    onSearchField = Color(0xFF5C5C5C),
    menu = Color(0xFFF8F8F8),
    invertChip = accent,
    onInvertChip = Color(0xFF212121),
)

/**
 * The colours of [theme], a theme name as [ThemeUtil] gives it, with the translucent theme named
 * by its variant. [picked] is the colour the user picked for the custom or translucent theme;
 * other themes ignore it. A [plain] light theme is black and white, keeping its colour for links
 * and buttons; the default theme is plain unless asked otherwise, the others are not. A dark
 * theme takes [darkAccent], a light theme's colour, lifted to read on it; without it, the blue.
 */
fun themeColors(
    theme: String,
    picked: Color? = null,
    plain: Boolean = theme == ThemeUtil.THEME_DEFAULT,
    darkAccent: Color? = null,
): ExtendedColors {
    fun light(accent: Color) = light(theme, accent, emphasis = if (plain) Color.Black else accent)
    return when (theme) {
        ThemeUtil.THEME_BLACK -> light(accent = Color.Black)
        ThemeUtil.THEME_BLUE -> light(accent = TiebaBlue)
        ThemeUtil.THEME_PURPLE -> light(accent = Color(0xFF6E52C9))
        ThemeUtil.THEME_PINK -> light(accent = Color(0xFFD9577A))
        ThemeUtil.THEME_RED -> light(accent = Color(0xFFD6362C))
        ThemeUtil.THEME_GREEN -> light(accent = Color(0xFF2F8F5B))
        ThemeUtil.THEME_CUSTOM -> light(accent = picked ?: TiebaBlue)
        ThemeUtil.THEME_BLUE_DARK -> BlueDark.withAccent(darkAccent)
        ThemeUtil.THEME_GREY_DARK -> GreyDark.withAccent(darkAccent)
        ThemeUtil.THEME_AMOLED_DARK -> AmoledDark.withAccent(darkAccent)
        ThemeUtil.THEME_TRANSLUCENT_LIGHT -> translucentLight(picked ?: TiebaBlue)
        ThemeUtil.THEME_TRANSLUCENT_DARK -> translucentDark(picked ?: TiebaBlue)
        else -> light(ThemeUtil.THEME_DEFAULT, accent = TiebaBlue, emphasis = if (plain) Color.Black else TiebaBlue)
    }
}

/** The colour the user picked for [theme], if it is the custom theme. */
fun pickedColor(context: Context, theme: String): Color? {
    val saved = if (theme == ThemeUtil.THEME_CUSTOM) Settings.of(context)[SettingKeys.CustomPrimaryColor] else null
    return saved?.let { runCatching { Color(it.toColorInt()) }.getOrNull() }
}
