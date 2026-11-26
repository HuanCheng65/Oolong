package oolong.client.ui.appwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.palette.graphics.Palette
import coil3.size.Scale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import oolong.client.media.image.loadBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pictures for widgets, cut and rounded before they go: a widget's views can't clip a picture
 * to rounded corners before Android 12, and every bitmap is copied into the launcher, so they
 * are kept no larger than they show.
 */
internal object WidgetPictures {
    /** [url] cut to [width]×[height] px from its middle, its corners rounded by [radius] px; null when it can't load. */
    suspend fun load(context: Context, url: String, width: Int, height: Int, radius: Float): Bitmap? {
        if (url.isEmpty()) return null
        val source = runCatching {
            context.loadBitmap(url) {
                size(width, height)
                scale(Scale.FILL)
            }
        }.getOrNull() ?: return null
        return cropRounded(source, width, height, radius)
    }

    /** Each of [urls] as [load] makes it, loaded side by side, in order. */
    suspend fun loadAll(context: Context, urls: List<String>, width: Int, height: Int, radius: Float): List<Bitmap?> =
        coroutineScope { urls.map { async { load(context, it, width, height, radius) } }.map { it.await() } }

    /**
     * A wash for the top of a widget about a forum, in the forum avatar's own colour fading
     * down to nothing, as the forum page's header has. Null when [avatar] has no colour to give.
     */
    fun wash(avatar: Bitmap?, height: Int): Bitmap? {
        val swatch = avatar?.let { Palette.from(it).generate() }?.let { it.vibrantSwatch ?: it.dominantSwatch } ?: return null
        val color = swatch.rgb
        val bitmap = createBitmap(4, height)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                ColorUtils.setAlphaComponent(color, (255 * 0.30f).roundToInt()),
                ColorUtils.setAlphaComponent(color, 0),
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(bitmap).drawRect(0f, 0f, 4f, height.toFloat(), paint)
        return bitmap
    }

    private fun cropRounded(source: Bitmap, width: Int, height: Int, radius: Float): Bitmap {
        val scale = max(width / source.width.toFloat(), height / source.height.toFloat())
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((width - source.width * scale) / 2f, (height - source.height * scale) / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
        }
        val output = createBitmap(width, height)
        Canvas(output).drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
        return output
    }
}
