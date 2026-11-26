package oolong.client.ui.settings.theme

import android.content.Context
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import oolong.client.data.settings.Settings
import oolong.client.data.settings.ThemeSettings
import oolong.client.data.settings.dataStore
import oolong.client.testing.LegacyAppRule
import oolong.client.ui.designsystem.theme.ThemeUtil
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class TranslucentWallpaperTest {
    private val textTheme = intPreferencesKey("translucent_background_theme")
    private val dim = floatPreferencesKey("translucent_wallpaper_dim")

    @get:Rule
    val legacyApp = LegacyAppRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() = runTest {
        Settings.of(context).clear()
    }

    @Test
    fun `chosen text is dark over a light wallpaper, as darkened`() {
        val auto = TranslucentWallpaper()
        assertTrue(auto.darkText(lightness = 0.6f))
        assertFalse(auto.darkText(lightness = 0.1f))
        // Darkened by 60%, a wallpaper at 0.55 is as dark as one at about 0.07
        assertFalse(auto.copy(dim = 0.6f).darkText(lightness = 0.55f))
        assertTrue(auto.copy(dim = 0.2f).darkText(lightness = 0.55f))
        assertEquals(ThemeUtil.THEME_TRANSLUCENT_DARK, auto.variant(lightness = 0.6f))
        assertEquals(ThemeUtil.THEME_TRANSLUCENT_LIGHT, auto.variant(lightness = 0.1f))
    }

    @Test
    fun `text picked by hand stays whatever the wallpaper`() {
        assertFalse(TranslucentWallpaper(text = WallpaperText.Light).darkText(lightness = 1f))
        assertTrue(TranslucentWallpaper(text = WallpaperText.Dark).darkText(lightness = 0f))
    }

    @Test
    fun `the blur slider gives its first half to the light blurs`() {
        assertEquals(0, TranslucentWallpaper.blurAt(0f))
        assertEquals(25, TranslucentWallpaper.blurAt(0.5f))
        assertEquals(TranslucentWallpaper.MAX_BLUR, TranslucentWallpaper.blurAt(1f))
        for (blur in listOf(0, 9, 25, 64, 100)) {
            assertEquals(blur, TranslucentWallpaper.blurAt(TranslucentWallpaper(blur = blur).blurPosition))
        }
    }

    @Test
    fun `strong blurs shrink the image instead of passing the largest radius`() {
        assertNull(TranslucentWallpaper(blur = 0).blurTransformation(context))
        assertEquals("BlurTransformation(radius=25, sampling=1)", TranslucentWallpaper(blur = 25).blurTransformation(context)?.cacheKey)
        assertEquals("BlurTransformation(radius=13, sampling=2)", TranslucentWallpaper(blur = 26).blurTransformation(context)?.cacheKey)
        assertEquals("BlurTransformation(radius=25, sampling=4)", TranslucentWallpaper(blur = 100).blurTransformation(context)?.cacheKey)
    }

    @Test
    fun `starts from the defaults when nothing was saved`() = runTest {
        assertEquals(TranslucentWallpaper(), TranslucentWallpaper.saved(context))
    }

    @Test
    fun `reads back what was saved and replaces the old wallpaper`() = runTest {
        val first = TranslucentWallpaper(text = WallpaperText.Dark, dim = 0.5f, blur = 12)
        assertNull(TranslucentWallpaper.save(context, first, "/a.jpg", darkText = true))
        assertEquals(first, TranslucentWallpaper.saved(context))

        val second = first.copy(text = WallpaperText.Auto)
        assertEquals("/a.jpg", TranslucentWallpaper.save(context, second, "/b.jpg", darkText = false))
        assertEquals(second, TranslucentWallpaper.saved(context))
        assertFalse(TranslucentWallpaper.isOutdated(context))
    }

    @Test
    fun `dark text saved before it was chosen no longer fades the wallpaper to white`() = runTest {
        val path = stringPreferencesKey(ThemeUtil.KEY_TRANSLUCENT_THEME_BACKGROUND_PATH)
        Settings.of(context).save(path, textTheme, dim) {
            it[path] = "/a.jpg"
            it[textTheme] = ThemeUtil.TRANSLUCENT_THEME_DARK
            it[dim] = 0.3f
        }

        assertEquals(TranslucentWallpaper(text = WallpaperText.Dark, dim = 0f), TranslucentWallpaper.saved(context))
        assertTrue(TranslucentWallpaper.isOutdated(context))
    }

    @Test
    fun `light text saved before it was chosen keeps its darkening`() = runTest {
        Settings.of(context).save(textTheme, dim) {
            it[textTheme] = ThemeUtil.TRANSLUCENT_THEME_LIGHT
            it[dim] = 0.3f
        }

        assertEquals(TranslucentWallpaper(text = WallpaperText.Light, dim = 0.3f), TranslucentWallpaper.saved(context))
        assertFalse(TranslucentWallpaper.isOutdated(context))
    }

    @Test
    fun `a colour picked from the wallpaper is saved as the custom colour`() = runTest {
        TranslucentWallpaper.save(context, TranslucentWallpaper(accent = 0xFF336699.toInt()), "/a.jpg", darkText = false)

        assertEquals("#FF336699", context.dataStore.data.first()[ThemeSettings.CustomColorKey])
        assertNull(TranslucentWallpaper.saved(context).accent)
    }
}
