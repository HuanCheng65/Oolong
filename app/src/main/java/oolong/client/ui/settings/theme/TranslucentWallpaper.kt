package oolong.client.ui.settings.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.palette.graphics.Palette
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.data.settings.ThemeSettings
import oolong.client.data.settings.rememberSettings
import oolong.client.media.ImageUtil
import oolong.client.media.image.BlurTransformation
import oolong.client.media.image.loadBitmap
import oolong.client.ui.designsystem.theme.ThemeUtil
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Whether the text over the wallpaper is light or dark, or chosen for how light the wallpaper is. */
enum class WallpaperText(val value: String) {
    Auto("auto"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun of(value: String?) = entries.firstOrNull { it.value == value }
    }
}

/**
 * How the translucent theme draws its wallpaper. The picked image is kept as is; the blurred and
 * darkened copy the app draws is made from it, so the settings can be changed again later.
 *
 * @param text the text colour over it
 * @param dim how far the wallpaper is darkened, 0 to [MAX_DIM]
 * @param blur how strong the blur is, 0 to [MAX_BLUR]
 * @param accent a colour picked from the wallpaper for the theme as ARGB, saved as the custom
 *   theme colour; null leaves the theme colour as it is
 */
data class TranslucentWallpaper(
    val text: WallpaperText = WallpaperText.Auto,
    val dim: Float = DEFAULT_DIM,
    val blur: Int = 0,
    val accent: Int? = null,
) {
    /**
     * Whether the text is dark over a wallpaper whose average relative luminance is [lightness],
     * once darkened. Chosen, it is the text that stands out more: black text and white text read
     * equally well at a luminance of [EVEN_LUMINANCE].
     */
    fun darkText(lightness: Float): Boolean = when (text) {
        WallpaperText.Light -> false
        WallpaperText.Dark -> true
        // Darkening scales the colour's sRGB values, which luminance follows at about their 2.2th power
        WallpaperText.Auto -> lightness * (1 - dim).pow(2.2f) > EVEN_LUMINANCE
    }

    /**
     * The blur for [blur], or null for none: up to [MAX_RADIUS] a plain radius, beyond it the image
     * shrunk first so that the same radius covers more of it.
     */
    fun blurTransformation(context: Context): BlurTransformation? {
        if (blur <= 0) return null
        val sampling = (blur + MAX_RADIUS - 1) / MAX_RADIUS
        return BlurTransformation(context, radius = (blur / sampling).coerceIn(1, MAX_RADIUS), sampling = sampling)
    }

    /** Where [blur] sits on a slider from 0 to 1, as [blurAt] places it. */
    val blurPosition: Float get() = sqrt(blur.toFloat() / MAX_BLUR)

    /** The theme name whose colours go with the text over a wallpaper as light as [lightness]. */
    fun variant(lightness: Float): String =
        if (darkText(lightness)) ThemeUtil.THEME_TRANSLUCENT_DARK else ThemeUtil.THEME_TRANSLUCENT_LIGHT

    companion object {
        const val DEFAULT_DIM = 0f

        /** How dark the wallpaper is at night at the least. */
        private const val NIGHT_DIM = 0.6f

        /**
         * The black laid over a wallpaper darkened by [dim] at night, so that it is darkened by
         * [NIGHT_DIM] at the least.
         */
        fun nightOverlay(dim: Float): Float = ((NIGHT_DIM - dim) / (1 - dim)).coerceIn(0f, 1f)
        const val MAX_DIM = 0.8f
        const val MAX_BLUR = 100

        /** Where black text and white text contrast equally with what is behind them. */
        private const val EVEN_LUMINANCE = 0.18f

        /** The largest radius the blur takes; stronger blurs shrink the image first instead. */
        private const val MAX_RADIUS = 25

        /**
         * The blur at [position] on a slider from 0 to 1. How blurred an image looks grows ever
         * more slowly with the radius, so the slider gives its first half to the light blurs,
         * where each step shows, and squeezes the strong ones into the rest.
         */
        fun blurAt(position: Float): Int = (MAX_BLUR * position * position).roundToInt().coerceIn(0, MAX_BLUR)

        /** The image picked for the wallpaper, before blurring and dimming. */
        fun source(context: Context) = File(context.filesDir, "cropped_background.jpg")

        suspend fun saved(context: Context): TranslucentWallpaper {
            val prefs = Settings.of(context).all.value
            // Before the text could be chosen for the wallpaper, it was always picked by hand, and
            // dark text faded the wallpaper to white, which is no longer done
            val legacyDark = prefs[TextKey] == null && prefs[ThemeKey] == ThemeUtil.TRANSLUCENT_THEME_DARK
            val legacyText = when {
                prefs[ThemeKey] == null -> null
                legacyDark -> WallpaperText.Dark
                else -> WallpaperText.Light
            }
            return TranslucentWallpaper(
                text = WallpaperText.of(prefs[TextKey]) ?: legacyText ?: WallpaperText.Auto,
                dim = if (legacyDark) 0f else prefs[DimKey] ?: DEFAULT_DIM,
                blur = (prefs[BlurKey] ?: 0).coerceIn(0, MAX_BLUR),
            )
        }

        // Read as stored, where whether one is set at all tells an old wallpaper apart
        private val PathKey = SettingKeys.TranslucentThemeBackgroundPath.key
        private val ThemeKey = SettingKeys.TranslucentBackgroundTheme.key
        private val TextKey = stringPreferencesKey("translucent_wallpaper_text")
        internal val DimKey = floatPreferencesKey("translucent_wallpaper_dim")
        private val BlurKey = SettingKeys.TranslucentBackgroundBlur.key

        /**
         * Whether the drawn wallpaper is from before dark text stopped fading it to white, and is
         * to be drawn again.
         */
        suspend fun isOutdated(context: Context): Boolean {
            val prefs = Settings.of(context).all.value
            return prefs[TextKey] == null && prefs[ThemeKey] == ThemeUtil.TRANSLUCENT_THEME_DARK && prefs[PathKey] != null
        }

        /** Whether [path] is still the drawn wallpaper saved, not one replaced since. */
        suspend fun isCurrent(context: Context, path: String): Boolean = Settings.of(context).all.value[PathKey] == path

        /**
         * Saves [wallpaper] with the drawn wallpaper at [path], whose text is [darkText], all at
         * once and awaited: the theme switch that follows reads them straight away. Returns the
         * path it replaces.
         */
        internal suspend fun save(context: Context, wallpaper: TranslucentWallpaper, path: String, darkText: Boolean): String? {
            val settings = Settings.of(context)
            val old = settings.all.value[PathKey]
            settings.save(PathKey, TextKey, ThemeKey, DimKey, BlurKey, ThemeSettings.CustomColorKey) {
                it[PathKey] = path
                it[TextKey] = wallpaper.text.value
                it[ThemeKey] = if (darkText) ThemeUtil.TRANSLUCENT_THEME_DARK else ThemeUtil.TRANSLUCENT_THEME_LIGHT
                it[DimKey] = wallpaper.dim
                it[BlurKey] = wallpaper.blur
                if (wallpaper.accent != null) it[ThemeSettings.CustomColorKey] = String.format("#%08X", wallpaper.accent)
            }
            return old
        }
    }
}

/** How far the wallpaper is darkened, as saved. */
@Composable
fun rememberWallpaperDim(): Float {
    val settings = rememberSettings()
    val dim by remember(settings) {
        settings.all.map { it[TranslucentWallpaper.DimKey] ?: TranslucentWallpaper.DEFAULT_DIM }
    }.collectAsState(initial = settings.all.value[TranslucentWallpaper.DimKey] ?: TranslucentWallpaper.DEFAULT_DIM)
    return dim
}

/** The wallpaper as the app draws it: [source] at screen size, blurred and darkened. */
suspend fun Context.renderWallpaper(source: File, wallpaper: TranslucentWallpaper): Bitmap? {
    val metrics: DisplayMetrics = resources.displayMetrics
    val image = loadBitmap(source) {
        size(metrics.widthPixels, metrics.heightPixels)
        scale(Scale.FILL)
        precision(Precision.EXACT)
        memoryCacheKey("translucent:${source.lastModified()}:${wallpaper.blur}")
        wallpaper.blurTransformation(this@renderWallpaper)?.let { transformations(it) }
    } ?: return null
    return withContext(Dispatchers.Default) {
        // A strong blur comes back shrunk; draw it at screen size again
        val out = createBitmap(metrics.widthPixels, metrics.heightPixels)
        Canvas(out).apply {
            drawBitmap(image, null, Rect(0, 0, out.width, out.height), Paint(Paint.FILTER_BITMAP_FLAG))
            drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (wallpaper.dim * 255).toInt()))
        }
        out
    }
}

/** Colours taken from the wallpaper to offer as the accent, most prominent first. */
suspend fun Context.wallpaperColors(source: File): List<Int> {
    // Keyed by when the file changed: a newly picked image is saved under the same name
    val image = loadBitmap(source) {
        size(256)
        memoryCacheKey("translucent-colors:${source.lastModified()}")
    } ?: return emptyList()
    return withContext(Dispatchers.Default) {
        val palette = Palette.from(image).generate()
        listOfNotNull(
            palette.vibrantSwatch,
            palette.lightVibrantSwatch,
            palette.darkVibrantSwatch,
            palette.mutedSwatch,
            palette.lightMutedSwatch,
            palette.darkMutedSwatch,
        ).map { it.rgb }.distinct()
    }
}

/** The average relative luminance of [source], 0 for black to 1 for white, or null if it cannot be read. */
suspend fun Context.wallpaperLightness(source: File): Float? {
    val image = loadBitmap(source) {
        size(64)
        memoryCacheKey("translucent-lightness:${source.lastModified()}")
    } ?: return null
    return withContext(Dispatchers.Default) {
        val small = image.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        pixels.map { ColorUtils.calculateLuminance(it).toFloat() }.average().toFloat()
    }
}

/**
 * Draws the wallpaper again from its settings, without changing the theme, if it is from before
 * dark text stopped fading it to white. The image picked is needed for that; without it, the
 * wallpaper is left as it is.
 */
suspend fun Context.redrawOutdatedWallpaper() {
    if (!TranslucentWallpaper.isOutdated(this)) return
    val source = TranslucentWallpaper.source(this)
    if (!source.exists()) return
    drawWallpaper(source, TranslucentWallpaper.saved(this))
}

/**
 * Draws the wallpaper again from the image picked when the drawn one at [path] can't be shown,
 * as when its file is gone, as applying it again in the settings would. Without the image
 * picked, nothing is done.
 */
suspend fun Context.repairWallpaper(path: String) {
    if (path.isEmpty() || !TranslucentWallpaper.isCurrent(this, path)) return
    val source = TranslucentWallpaper.source(this)
    if (!withContext(Dispatchers.IO) { source.length() > 0 }) return
    drawWallpaper(source, TranslucentWallpaper.saved(this))
}

/**
 * Saves [wallpaper] and shows it: the drawn wallpaper goes to a new file, which replaces the old
 * one. False when [source] cannot be drawn.
 */
suspend fun Context.applyWallpaper(source: File, wallpaper: TranslucentWallpaper): Boolean {
    if (!drawWallpaper(source, wallpaper)) return false
    ThemeUtil.setWallpaper(true)
    // A colour picked from the wallpaper becomes the theme colour, as a custom one
    if (wallpaper.accent != null) ThemeUtil.selectAccent(ThemeUtil.THEME_CUSTOM)
    return true
}

/** Draws [wallpaper] from [source] and saves both. False when [source] cannot be drawn. */
private suspend fun Context.drawWallpaper(source: File, wallpaper: TranslucentWallpaper): Boolean {
    val bitmap = renderWallpaper(source, wallpaper) ?: return false
    val darkText = wallpaper.darkText(wallpaperLightness(source) ?: 0f)
    val file = withContext(Dispatchers.IO) {
        ImageUtil.compressImage(
            bitmap,
            File(filesDir, "background_${System.currentTimeMillis()}.jpg"),
            maxSizeKb = 512,
            initialQuality = 97,
        )
    }
    val old = TranslucentWallpaper.save(this, wallpaper, file.absolutePath, darkText)
    if (old != null && old != file.absolutePath) File(old).delete()
    return true
}
