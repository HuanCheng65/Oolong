package oolong.client.ui.designsystem.theme

import android.content.Context
import androidx.compose.material.MaterialTheme
import androidx.compose.material.primarySurface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import oolong.client.data.settings.Appearance
import oolong.client.data.settings.ColorStyle
import oolong.client.data.settings.MonetStyle
import oolong.client.data.settings.Settings
import oolong.client.data.settings.ThemeSettings
import oolong.client.testing.LegacyAppRule
import oolong.client.ui.designsystem.theme.ThemeUtil
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Every colour every theme gives, against [GOLDEN]. Restructuring the palette must not change
 * a single one; run with the environment variable `UPDATE_PALETTE=true` to write the file anew
 * after a deliberate change.
 */
@RunWith(RobolectricTestRunner::class)
class PaletteSnapshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * A theme as the user sets it up: the theme choices, the translucent text setting and the
     * colour picked for the custom or translucent theme.
     */
    private data class Setup(
        val name: String,
        val settings: ThemeSettings,
        val darkText: Boolean = false,
        val accent: String? = null,
    )

    /** A light theme by its old name, which held its colour and its style. */
    private fun light(theme: String) = ThemeSettings.fromLightTheme(theme).let { (accent, style) ->
        ThemeSettings(appearance = Appearance.Light, accent = accent, style = style)
    }
    private fun dark(theme: String, accent: String = ThemeUtil.THEME_DEFAULT) =
        ThemeSettings(appearance = Appearance.Dark, accent = accent, style = ThemeSettings.fromLightTheme(accent).second, darkTheme = theme)
    private fun monet(accent: String, style: MonetStyle, dark: Boolean = false) = ThemeSettings(
        appearance = if (dark) Appearance.Dark else Appearance.Light,
        accent = accent,
        style = ColorStyle.Monet,
        monetStyle = style,
    )
    private val wallpaper = ThemeSettings(appearance = Appearance.Light, wallpaper = true)

    /** The wallpaper with a colour picked for it, which is the custom colour. */
    private val pickedWallpaper = wallpaper.copy(accent = ThemeUtil.THEME_CUSTOM, style = ColorStyle.Bright)
    private val dynamic = ThemeSettings.ACCENT_DYNAMIC

    private val setups = listOf("tieba", "black", "blue", "purple", "pink", "red", "custom").map { Setup(it, light(it)) } +
        listOf("blue_dark", "grey_dark", "amoled_dark").map { Setup(it, dark(it)) } +
        listOf(
            Setup("translucent_light", wallpaper, darkText = false),
            Setup("translucent_dark", wallpaper, darkText = true),
            Setup("custom_picked", light(ThemeUtil.THEME_CUSTOM), accent = PICKED),
            Setup("translucent_light_picked", pickedWallpaper, darkText = false, accent = PICKED),
            Setup("translucent_dark_picked", pickedWallpaper, darkText = true, accent = PICKED),
            Setup("dynamic_light", light(dynamic)),
            Setup("dynamic_dark", dark(ThemeUtil.THEME_BLUE_DARK, dynamic)),
            Setup("dynamic_amoled", dark(ThemeUtil.THEME_AMOLED_DARK, dynamic)),
            Setup("pink_dark", dark(ThemeUtil.THEME_AMOLED_DARK, "pink")),
            Setup("black_dark", dark(ThemeUtil.THEME_GREY_DARK, "black")),
            Setup("purple_dark", dark(ThemeUtil.THEME_BLUE_DARK, "purple")),
            Setup("custom_picked_dark", dark(ThemeUtil.THEME_BLUE_DARK, ThemeUtil.THEME_CUSTOM), accent = PICKED),
            Setup("tieba_monet", monet(ThemeUtil.THEME_DEFAULT, MonetStyle.TonalSpot)),
            Setup("tieba_monet_dark", monet(ThemeUtil.THEME_DEFAULT, MonetStyle.TonalSpot, dark = true)),
            Setup("pink_vibrant", monet("pink", MonetStyle.Vibrant)),
            Setup("red_rainbow", monet("red", MonetStyle.Rainbow)),
            Setup("dynamic_expressive", monet(dynamic, MonetStyle.Expressive)),
            Setup("translucent_night", wallpaper.copy(appearance = Appearance.Dark), darkText = true),
        )

    @Test
    fun `no theme's colours change`() {
        var latest: Pair<ExtendedColors, androidx.compose.material.Colors>? = null
        val captured = mutableMapOf<String, String>()
        composeRule.setContent {
            TiebaLiteTheme {
                val colors = ExtendedTheme.colors
                val material = MaterialTheme.colors
                SideEffect { latest = colors to material }
            }
        }
        for (next in setups) {
            runBlocking {
                val keys = listOf("custom_primary_color", ThemeUtil.KEY_TRANSLUCENT_PRIMARY_COLOR).map(::stringPreferencesKey)
                Settings.of(context).save(intPreferencesKey("translucent_background_theme"), *keys.toTypedArray()) {
                    it[intPreferencesKey("translucent_background_theme")] =
                        if (next.darkText) ThemeUtil.TRANSLUCENT_THEME_DARK else ThemeUtil.TRANSLUCENT_THEME_LIGHT
                    for (key in keys) {
                        if (next.accent != null) it[key] = next.accent else it.remove(key)
                    }
                }
            }
            // Through another theme first, so that every setup is drawn afresh
            composeRule.runOnIdle { ThemeUtil.updateSettings { light(if (next.name == "tieba") "black" else "tieba") } }
            composeRule.waitForIdle()
            composeRule.runOnIdle { ThemeUtil.updateSettings { next.settings } }
            composeRule.waitForIdle()
            val (colors, material) = checkNotNull(latest)
            captured[next.name] = describe(next.name, colors, material)
        }
        val actual = setups.joinToString("\n") { captured.getValue(it.name) } + "\n"

        File(ACTUAL).apply { parentFile?.mkdirs() }.writeText(actual)
        val golden = File(GOLDEN)
        val update = System.getenv("UPDATE_PALETTE") == "true"
        if (update || !golden.exists()) {
            golden.parentFile?.mkdirs()
            golden.writeText(actual)
            if (!update) fail("Wrote $GOLDEN; check it in")
            return
        }
        assertEquals(golden.readText(), actual)
    }

    private fun describe(name: String, colors: ExtendedColors, material: androidx.compose.material.Colors): String {
        val fields = ExtendedColors::class.java.declaredFields
            .filter { it.type == Long::class.javaPrimitiveType }
            .sortedBy { it.name }
            .map { field ->
                field.isAccessible = true
                field.name to Color(field.getLong(colors).toULong())
            }
        val derived = listOf(
            "m.primary" to material.primary,
            "m.secondary" to material.secondary,
            "m.onSecondary" to material.onSecondary,
            "m.background" to material.background,
            "m.onBackground" to material.onBackground,
            "m.surface" to material.surface,
            "m.onSurface" to material.onSurface,
            "m.primarySurface" to material.primarySurface,
        )
        return (fields + derived).joinToString("\n") { (field, color) ->
            "$name ${field.padEnd(22)} ${"%08X".format(color.toArgb())}"
        } + "\n$name isNightMode ${colors.isNightMode} isLight ${material.isLight}"
    }

    private companion object {
        const val GOLDEN = "src/test/resources/theme/palette.txt"
        const val ACTUAL = "build/palette-actual.txt"
        const val PICKED = "#FF336699"
    }
}
