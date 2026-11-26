package oolong.client.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import oolong.client.ui.designsystem.theme.ThemeUtil

/** Light or dark, or whichever the system is in. */
enum class Appearance(val value: String) {
    Light("light"),
    Dark("dark"),
    System("system"),
    ;

    companion object {
        fun of(value: String?) = entries.firstOrNull { it.value == value }
    }
}

/** How the light theme uses its colour. */
enum class ColorStyle(val value: String) {
    /** Black and white, with the colour only for links and buttons. */
    Plain("plain"),

    /** The colour throughout, for what is selected too. */
    Bright("bright"),

    /** A whole palette made from the colour, in a [MonetStyle], the backgrounds tinted too. */
    Monet("monet"),
    ;

    companion object {
        fun of(value: String?) = entries.firstOrNull { it.value == value }
    }
}

/** How [ColorStyle.Monet] makes its palette from the colour, as Material names the styles. */
enum class MonetStyle(val value: String) {
    TonalSpot("tonal_spot"),
    Vibrant("vibrant"),
    Expressive("expressive"),
    Neutral("neutral"),
    Rainbow("rainbow"),
    FruitSalad("fruit_salad"),
    Monochrome("monochrome"),
    ;

    companion object {
        fun of(value: String?) = entries.firstOrNull { it.value == value }
    }
}

/**
 * How the app is themed: three choices that do not depend on each other. Which colours are drawn
 * follows from them and whether the system is dark; switching between light and dark never
 * changes a choice.
 *
 * @param accent the colour: a light theme's name, [ThemeUtil.THEME_CUSTOM], or [ACCENT_DYNAMIC]
 * @param style how the light theme uses [accent]
 * @param monetStyle how [ColorStyle.Monet] makes its palette
 * @param darkTheme the dark theme's name, used whenever the app is dark
 * @param wallpaper whether the page shows the picked wallpaper
 * @param oppositeWhile while following the system, the system's darkness for which the other look
 *   is shown instead, as the night switch asks; it lapses once the system turns
 */
data class ThemeSettings(
    val appearance: Appearance = Appearance.System,
    val accent: String = ThemeUtil.THEME_DEFAULT,
    val style: ColorStyle = ColorStyle.Plain,
    val monetStyle: MonetStyle = MonetStyle.TonalSpot,
    val darkTheme: String = ThemeUtil.THEME_AMOLED_DARK,
    val wallpaper: Boolean = false,
    val oppositeWhile: Boolean? = null,
) {
    /** Whether the colour is the system's, as the system takes it from its wallpaper. */
    val isSystemColor: Boolean get() = accent == ACCENT_DYNAMIC

    val isMonet: Boolean get() = style == ColorStyle.Monet

    /** Whether the system's own palette is drawn, as the system makes it from its colour. */
    val usesSystemPalette: Boolean get() = isSystemColor && isMonet && monetStyle == MonetStyle.TonalSpot

    fun isDark(systemDark: Boolean): Boolean = when (appearance) {
        Appearance.Light -> false
        Appearance.Dark -> true
        Appearance.System -> if (oppositeWhile == systemDark) !systemDark else systemDark
    }

    /**
     * Light made dark or dark made light. A fixed look is swapped for the other; while following
     * the system the other look is shown only until the system turns, and the choice is kept.
     */
    fun toggledDark(systemDark: Boolean): ThemeSettings = when (appearance) {
        Appearance.Light -> copy(appearance = Appearance.Dark)
        Appearance.Dark -> copy(appearance = Appearance.Light)
        Appearance.System -> copy(oppositeWhile = if (oppositeWhile == systemDark) null else systemDark)
    }

    /** These settings once the system is [systemDark], the other look dropped if it has turned since. */
    fun settled(systemDark: Boolean): ThemeSettings =
        if (oppositeWhile != null && oppositeWhile != systemDark) copy(oppositeWhile = null) else this

    /** The name of the theme to draw, as the colours are looked up by. */
    fun themeName(systemDark: Boolean): String = themeNameWhen(isDark(systemDark))

    /**
     * The name of the theme drawn while the app is [dark] or light. The wallpaper stays at night,
     * darker and with light text.
     */
    fun themeNameWhen(dark: Boolean): String = when {
        wallpaper -> if (dark) ThemeUtil.THEME_TRANSLUCENT_LIGHT else ThemeUtil.THEME_TRANSLUCENT
        dark -> darkTheme
        isSystemColor -> ThemeUtil.THEME_DEFAULT
        // The bright blue was a theme of its own before the style was
        accent == ThemeUtil.THEME_DEFAULT && style == ColorStyle.Bright -> ThemeUtil.THEME_BLUE
        else -> accent
    }

    internal fun writeTo(prefs: MutablePreferences) {
        prefs[AppearanceKey] = appearance.value
        prefs[AccentKey] = accent
        prefs[StyleKey] = style.value
        prefs[MonetStyleKey] = monetStyle.value
        prefs[DarkThemeKey] = darkTheme
        prefs[BackgroundKey] = if (wallpaper) BACKGROUND_WALLPAPER else BACKGROUND_DEFAULT
        if (oppositeWhile != null) prefs[OppositeWhileKey] = oppositeWhile else prefs.remove(OppositeWhileKey)
    }

    companion object {
        const val ACCENT_DYNAMIC = "dynamic"

        /** The colours to pick from, in the order they are shown. */
        val ACCENTS = listOf(
            ThemeUtil.THEME_DEFAULT,
            ThemeUtil.THEME_BLACK,
            ThemeUtil.THEME_PINK,
            ThemeUtil.THEME_RED,
            ThemeUtil.THEME_GREEN,
            ThemeUtil.THEME_PURPLE,
            ThemeUtil.THEME_CUSTOM,
        )

        /**
         * A light theme's old name as a colour and the style it had: the bright blue is the default
         * blue used throughout, and the themes other than the default and black used their colour
         * throughout too.
         */
        internal fun fromLightTheme(name: String?): Pair<String, ColorStyle> = when (name) {
            ThemeUtil.THEME_DEFAULT, ThemeUtil.THEME_BLACK -> name to ColorStyle.Plain
            ThemeUtil.THEME_BLUE -> ThemeUtil.THEME_DEFAULT to ColorStyle.Bright
            // Dynamic colour was the system's palette
            ACCENT_DYNAMIC -> name to ColorStyle.Monet
            in ACCENTS -> name!! to ColorStyle.Bright
            else -> ThemeUtil.THEME_DEFAULT to ColorStyle.Plain
        }
        private val DARK_THEMES = listOf(ThemeUtil.THEME_BLUE_DARK, ThemeUtil.THEME_GREY_DARK, ThemeUtil.THEME_AMOLED_DARK)

        private fun darkOrNull(name: String?) = when (name) {
            in DARK_THEMES -> name
            // The grey theme's name before it was renamed
            "dark" -> ThemeUtil.THEME_GREY_DARK
            else -> null
        }
        private const val BACKGROUND_DEFAULT = "default"
        private const val BACKGROUND_WALLPAPER = "wallpaper"

        internal val AppearanceKey = stringPreferencesKey("theme_appearance")
        internal val AccentKey = stringPreferencesKey("theme_accent")
        internal val DarkThemeKey = stringPreferencesKey(ThemeUtil.KEY_DARK_THEME)
        internal val BackgroundKey = stringPreferencesKey("theme_background")
        internal val StyleKey = stringPreferencesKey("theme_color_style")
        internal val MonetStyleKey = stringPreferencesKey("theme_monet_style")
        private val OppositeWhileKey = booleanPreferencesKey("theme_opposite_while_dark")

        /** The keys [writeTo] writes, which change together. */
        internal val Keys = listOf(AppearanceKey, AccentKey, StyleKey, MonetStyleKey, DarkThemeKey, BackgroundKey, OppositeWhileKey)
        val CustomColorKey = stringPreferencesKey("custom_primary_color")
        private val OldWallpaperColorKey = stringPreferencesKey(ThemeUtil.KEY_TRANSLUCENT_PRIMARY_COLOR)

        /** Whether [prefs] hold a colour picked for the wallpaper alone, from before it took the theme colour. */
        fun hasWallpaperColor(prefs: Preferences): Boolean = prefs[OldWallpaperColorKey] != null

        /**
         * Moves the colour picked for the wallpaper alone to the theme colour, as a custom one, if
         * the wallpaper is shown and no custom colour is kept; otherwise it is dropped.
         */
        fun moveWallpaperColor(prefs: MutablePreferences) {
            val color = prefs.remove(OldWallpaperColorKey) ?: return
            if (prefs[BackgroundKey] != BACKGROUND_WALLPAPER || prefs[CustomColorKey] != null) return
            prefs[CustomColorKey] = color
            prefs[AccentKey] = ThemeUtil.THEME_CUSTOM
            // It was used throughout over the wallpaper
            prefs[StyleKey] = ColorStyle.Bright.value
        }

        private val OldThemeKey = stringPreferencesKey(ThemeUtil.KEY_THEME)
        private val OldLightThemeKey = stringPreferencesKey(ThemeUtil.KEY_OLD_THEME)
        private val OldFollowSystemKey = booleanPreferencesKey("follow_system_night")
        private val OldDynamicKey = booleanPreferencesKey(ThemeUtil.KEY_USE_DYNAMIC_THEME)

        fun from(prefs: Preferences): ThemeSettings {
            val defaults = ThemeSettings()
            val (accent, impliedStyle) = fromLightTheme(prefs[AccentKey] ?: defaults.accent)
            return ThemeSettings(
                appearance = Appearance.of(prefs[AppearanceKey]) ?: defaults.appearance,
                accent = accent,
                style = ColorStyle.of(prefs[StyleKey]) ?: impliedStyle,
                monetStyle = MonetStyle.of(prefs[MonetStyleKey]) ?: defaults.monetStyle,
                darkTheme = darkOrNull(prefs[DarkThemeKey]) ?: defaults.darkTheme,
                wallpaper = prefs[BackgroundKey] == BACKGROUND_WALLPAPER,
                oppositeWhile = prefs[OppositeWhileKey],
            )
        }

        /** Whether [prefs] still hold the settings as one theme name, from before they were split. */
        fun needsMigration(prefs: Preferences): Boolean = prefs[AppearanceKey] == null

        /**
         * The settings that show what the single theme name in [prefs] showed. That name was
         * overwritten on every switch between light and dark, keeping the light theme aside meanwhile.
         */
        fun migrate(prefs: Preferences): ThemeSettings {
            val theme = prefs[OldThemeKey] ?: ThemeUtil.THEME_DEFAULT
            val night = ThemeUtil.isNightMode(theme)
            fun lightOrDefault(name: String?) = name?.takeUnless { ThemeUtil.isNightMode(it) } ?: ThemeUtil.THEME_DEFAULT
            val aside = lightOrDefault(prefs[OldLightThemeKey])
            val light = if (night) aside else theme
            val wallpaper = ThemeUtil.isTranslucentTheme(light)
            // The translucent theme kept the theme picked before it aside
            val (base, style) = fromLightTheme(if (wallpaper) aside else light)
            val dynamic = prefs[OldDynamicKey] == true && !wallpaper
            return ThemeSettings(
                appearance = when {
                    prefs[OldFollowSystemKey] ?: true -> Appearance.System
                    night -> Appearance.Dark
                    else -> Appearance.Light
                },
                accent = if (dynamic) ACCENT_DYNAMIC else base,
                style = if (dynamic) ColorStyle.Monet else style,
                darkTheme = (if (night) darkOrNull(theme) else null) ?: darkOrNull(prefs[DarkThemeKey]) ?: ThemeUtil.THEME_AMOLED_DARK,
                wallpaper = wallpaper,
            )
        }
    }
}
