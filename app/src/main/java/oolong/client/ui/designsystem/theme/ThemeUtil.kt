package oolong.client.ui.designsystem.theme

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.edit
import oolong.client.App
import oolong.client.App.Companion.INSTANCE
import oolong.client.data.settings.Appearance
import oolong.client.data.settings.ColorStyle
import oolong.client.data.settings.MonetStyle
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.data.settings.ThemeSettings
import java.util.Locale

object ThemeUtil {
    /** The theme choices; change them through [updateSettings]. */
    val settingsState: MutableState<ThemeSettings> by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        mutableStateOf(ThemeSettings()).also { if (App.isInitialized) load(it) }
    }

    /** Whether [settingsState] holds the saved settings rather than the defaults. */
    @Volatile
    private var loaded = false

    /**
     * Reads the saved settings into [state]. Asked for before the app has started, the defaults
     * stand in until it has; saving them then would lose the saved ones.
     */
    private fun load(state: MutableState<ThemeSettings>) {
        state.value = ThemeSettings.from(Settings.of(context).all.value)
        loaded = true
    }

    private fun ensureLoaded() {
        if (!loaded && App.isInitialized) synchronized(this) { if (!loaded) load(settingsState) }
    }

    /** The name of the theme drawn now, worked out from [settingsState]; see [refresh]. */
    val themeState: MutableState<String> by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        mutableStateOf(settingsState.value.themeName(isSystemDark()))
    }

    const val TAG = "ThemeUtil"

    const val KEY_THEME = "theme"
    const val KEY_DARK_THEME = "dark_theme"
    const val KEY_OLD_THEME = "old_theme"
    const val KEY_USE_DYNAMIC_THEME = "useDynamicColorTheme"
    const val KEY_SWITCH_REASON = "switch_reason"
    const val KEY_TRANSLUCENT_PRIMARY_COLOR = "translucent_primary_color"
    const val KEY_TRANSLUCENT_THEME_BACKGROUND_PATH = "translucent_theme_background_path"

    const val THEME_TRANSLUCENT = "translucent"
    const val THEME_TRANSLUCENT_LIGHT = "translucent_light"
    const val THEME_TRANSLUCENT_DARK = "translucent_dark"
    const val THEME_CUSTOM = "custom"
    const val THEME_DEFAULT = "tieba"
    const val THEME_BLACK = "black"
    const val THEME_BLUE = "blue"
    const val THEME_PURPLE = "purple"
    const val THEME_PINK = "pink"
    const val THEME_RED = "red"
    const val THEME_GREEN = "green"
    const val THEME_BLUE_DARK = "blue_dark"
    const val THEME_GREY_DARK = "grey_dark"
    const val THEME_AMOLED_DARK = "amoled_dark"

    const val TRANSLUCENT_THEME_LIGHT = 0
    const val TRANSLUCENT_THEME_DARK = 1

    private val context: Context
        get() = INSTANCE

    private fun isSystemDark(): Boolean = App.isInitialized && App.isSystemNight

    /** Whether the app is dark now, as [settingsState] and the system say. */
    fun isDark(): Boolean = settingsState.value.isDark(isSystemDark())

    /** Works out the theme drawn now again, as after the system turns dark or light. */
    fun refresh() {
        ensureLoaded()
        val systemDark = isSystemDark()
        val settled = settingsState.value.settled(systemDark)
        if (settled != settingsState.value) {
            settingsState.value = settled
            save()
        }
        val name = settled.themeName(systemDark)
        if (themeState.value != name) themeState.value = name
    }

    /** Changes the theme choices and saves them. */
    fun updateSettings(transform: (ThemeSettings) -> ThemeSettings) {
        ensureLoaded()
        val settings = transform(settingsState.value)
        if (settings == settingsState.value) return
        settingsState.value = settings
        refresh()
        save()
    }

    private fun save() {
        // Each save writes the settings as they are by then, so that the last holds the last change
        Settings.of(context).edit(*ThemeSettings.Keys.toTypedArray()) { settingsState.value.writeTo(it) }
    }

    /** Uses the colour [name], dark or light. */
    fun selectAccent(name: String) = updateSettings { it.copy(accent = name) }

    fun selectStyle(style: ColorStyle) = updateSettings { it.copy(style = style) }

    fun selectDarkTheme(name: String) = updateSettings { it.copy(darkTheme = name) }

    /** Shows the picked wallpaper behind the pages while light, or stops showing it. */
    fun setWallpaper(on: Boolean) = updateSettings { it.copy(wallpaper = on) }

    fun selectMonetStyle(style: MonetStyle) = updateSettings { it.copy(monetStyle = style) }

    fun setAppearance(appearance: Appearance) = updateSettings { it.copy(appearance = appearance, oppositeWhile = null) }

    /** Switches between light and dark; see [ThemeSettings.toggledDark]. */
    fun toggleDark() = updateSettings { it.toggledDark(isSystemDark()) }

    @JvmStatic
    fun isNightMode(): Boolean {
        return isNightMode(getRawTheme())
    }

    @JvmStatic
    fun isNightMode(theme: String): Boolean {
        return theme.lowercase(Locale.getDefault()).contains("dark") && !theme.contains(
            THEME_TRANSLUCENT,
            ignoreCase = true
        )
    }

    fun isTranslucentTheme(): Boolean {
        return isTranslucentTheme(getRawTheme())
    }

    @JvmStatic
    fun isTranslucentTheme(theme: String): Boolean {
        return theme.equals(
            THEME_TRANSLUCENT,
            ignoreCase = true
        ) || theme.contains(
            THEME_TRANSLUCENT,
            ignoreCase = true
        )
    }

    fun isStatusBarFontDark(): Boolean {
        val theme = getRawTheme()
        return if (isTranslucentTheme(theme)) {
            // The saved theme is plain "translucent"; whether its text is dark is a separate setting
            getCurrentTheme(theme) == THEME_TRANSLUCENT_DARK
        } else {
            !isNightMode(theme)
        }
    }

    fun isNavigationBarFontDark(): Boolean {
        return !isDark()
    }

    /** [theme] as drawn: the translucent theme by its variant, which the saved text colour picks. */
    fun getCurrentTheme(theme: String = getRawTheme()): String {
        // The bare name leaves the text to the saved setting; the variants say it themselves
        if (!theme.equals(THEME_TRANSLUCENT, ignoreCase = true)) return theme
        val colorTheme = Settings.of(context)[SettingKeys.TranslucentBackgroundTheme]
        return if (colorTheme == TRANSLUCENT_THEME_DARK) THEME_TRANSLUCENT_DARK else THEME_TRANSLUCENT_LIGHT
    }

    @JvmStatic
    fun getRawTheme(): String {
        val theme = themeState.value
        return when (theme.lowercase(Locale.getDefault())) {
            THEME_TRANSLUCENT,
            THEME_TRANSLUCENT_LIGHT,
            THEME_TRANSLUCENT_DARK,
            THEME_CUSTOM,
            THEME_DEFAULT,
            THEME_BLUE,
            THEME_BLACK,
            THEME_PURPLE,
            THEME_PINK,
            THEME_RED,
            THEME_GREEN,
            THEME_BLUE_DARK,
            THEME_GREY_DARK,
            THEME_AMOLED_DARK,
            -> theme.lowercase(Locale.getDefault())

            else -> THEME_DEFAULT
        }
    }
}
