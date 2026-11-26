package oolong.client.debug

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.composeForPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.ui.appwidget.WidgetData
import java.io.File
import kotlin.math.roundToInt

/**
 * Draws each widget's preview, as the launcher's list would get it on Android 15, into a light
 * and a dark picture: the previews that launchers without generated previews, such as HyperOS's,
 * show instead. `adb shell am broadcast -n oolong.client/.debug.WidgetPreviewExport`, then pull
 * the pictures from the app's external files, `widget-previews/`.
 */
class WidgetPreviewExport : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Lossy WebP and the package's own widget list; the emulator it runs on is newer anyway
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withContext(WidgetData.StandInContext(WidgetSamples)) { export(context.applicationContext) }
            } catch (e: Throwable) {
                Log.e(TAG, "failed", e)
            } finally {
                result.finish()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun export(context: Context) {
        val dir = File(context.getExternalFilesDir(null), "widget-previews").apply { mkdirs() }
        val providers = AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(context.packageName, null)
        for (info in providers) {
            val receiver = Class.forName(info.provider.className).getDeclaredConstructor().newInstance() as? GlanceAppWidgetReceiver ?: continue
            val size = (receiver.glanceAppWidget.previewSizeMode as? SizeMode.Responsive)?.sizes?.first() ?: DpSize(360.dp, 220.dp)
            val views = receiver.glanceAppWidget.composeForPreview(context, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, info)
            val name = info.provider.shortClassName.substringAfterLast('.').removeSuffix("WidgetReceiver")
                .replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
            for (night in listOf(false, true)) {
                val themed = themed(context, night)
                val bitmap = withContext(Dispatchers.Main) {
                    val parent = FrameLayout(themed)
                    val view = views.apply(themed, parent)
                    draw(view, size, themed)
                }
                // Each goes in as res/drawable(-night)-nodpi/appwidget_preview_<name>.webp
                val file = File(dir, "${if (night) "night/" else ""}appwidget_preview_$name.webp").apply { parentFile?.mkdirs() }
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, it) }
                Log.i(TAG, "${file.name} ${bitmap.width}x${bitmap.height}")
            }
        }
        Log.i(TAG, "done")
    }

    private fun themed(context: Context, night: Boolean): Context {
        val config = Configuration(context.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        return context.createConfigurationContext(config)
    }

    /** The preview at the size its widget draws previews at. */
    private fun draw(view: View, size: DpSize, context: Context): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = (size.width.value * density).roundToInt()
        val height = (size.height.value * density).roundToInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val drawn = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(drawn))
        // A picture has no system corners to be clipped to, so it carries its own
        val radius = CornerDp * density
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(drawn, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(bitmap).drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
        return bitmap
    }

    private companion object {
        const val TAG = "WidgetPreviewExport"
        const val CornerDp = 20f
    }
}
