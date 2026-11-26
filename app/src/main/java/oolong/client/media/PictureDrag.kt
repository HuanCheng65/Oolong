package oolong.client.media

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Starts dragging the picture at [original] out of the app, for another app in split screen or
 * a window to take. It carries [shown], the copy on screen, under the finger; the original is
 * only fetched once an app it is dropped on reads it ([PictureDragProvider]).
 *
 * @param shownUrl the address [shown] was loaded from, given out should the original fail
 * @return whether the drag started
 */
fun View.startPictureDrag(original: String, shownUrl: String, shown: Bitmap?): Boolean {
    val uri = PictureDragProvider.uriFor(context, original, shownUrl)
    val clip = ClipData(ClipDescription("picture", arrayOf(PictureDragProvider.DRAG_MIME_TYPE)), ClipData.Item(uri))
    val flags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
    return startDragAndDrop(clip, PictureShadow(this, shown), null, flags)
}

/**
 * The picture as a small rounded card under the finger, or a blank card of the same kind without
 * one; never the system's own shadow, which would be the whole window the gesture came from.
 */
private class PictureShadow(view: View, picture: Bitmap?) : View.DragShadowBuilder(view) {
    private val density = view.resources.displayMetrics.density
    private val bitmap = picture?.let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    private val width: Int
    private val height: Int

    init {
        val longest = MAX_SIDE_DP * density
        val source = bitmap
        if (source != null && source.width > 0 && source.height > 0) {
            val scale = longest / max(source.width, source.height)
            width = (source.width * scale).roundToInt().coerceAtLeast(1)
            height = (source.height * scale).roundToInt().coerceAtLeast(1)
        } else {
            width = longest.roundToInt()
            height = width
        }
    }

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(width, height)
        outShadowTouchPoint.set(width / 2, height / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val radius = CORNER_DP * density
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val source = bitmap
        if (source == null) {
            canvas.drawRoundRect(bounds, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLANK_COLOR })
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply { setScale(width / source.width.toFloat(), height / source.height.toFloat()) })
            }
            alpha = (255 * 0.9f).roundToInt()
        }
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }

    private companion object {
        const val MAX_SIDE_DP = 160f
        const val CORNER_DP = 12f
        const val BLANK_COLOR = 0x66808080
    }
}
