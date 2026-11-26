package oolong.client.navigation

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import oolong.client.PageWindowActivity

/**
 * Pages opened in a window of their own, beside the app's window: two threads side by side, or
 * a thread kept open while reading on. Only where windows sit side by side, in split screen, a
 * free window or a wide screen; on a phone at full screen a new window would only be another
 * task behind this one.
 */
object NewWindow {
    /** Whether windows can sit beside the one [context] is in, for "open in new window" to offer. */
    fun available(context: Context): Boolean {
        val activity = context.findActivity()
        return activity?.isInMultiWindowMode == true || context.resources.configuration.smallestScreenWidthDp >= LargeScreenDp
    }

    /**
     * Opens [link], one of the app's oolong:// links, in a new window beside this one: a free
     * window the size of a phone where the system has free windows, as in desktop windowing or
     * DeX, and the other half of a split screen everywhere else. Makers' own floating windows
     * offer apps no way in, so there it is split screen too.
     */
    fun open(context: Context, link: String) {
        val free = inFreeWindow(context)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .setClass(context, PageWindowActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        // Beside this one in split screen. From a free window it would split the screen instead
        // of opening another free window, leaving this one hidden
        if (!free) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        // Ignored by the system where windows aren't free
        val options = freeWindowBounds(context)?.let { ActivityOptions.makeBasic().setLaunchBounds(it).toBundle() }
        context.startActivity(intent, options)
    }

    /**
     * Whether [context]'s window is a free one, as in desktop windowing: only free windows have
     * a caption bar of the system's.
     */
    private fun inFreeWindow(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val insets = context.findActivity()?.window?.decorView?.rootWindowInsets ?: return false
        return insets.getInsets(WindowInsets.Type.captionBar()).top > 0
    }

    /** Where a free window opens: phone-sized, at the right of the screen, clear of its edges. */
    private fun freeWindowBounds(context: Context): Rect? {
        // Free windows come with Android 11's window metrics
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val screen = context.getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds ?: return null
        val density = context.resources.displayMetrics.density
        val margin = (FreeWindowMarginDp * density).toInt()
        val width = minOf((FreeWindowWidthDp * density).toInt(), screen.width() - 2 * margin)
        val height = minOf((FreeWindowHeightDp * density).toInt(), screen.height() - 2 * margin)
        if (width <= 0 || height <= 0) return null
        val left = screen.right - margin - width
        val top = screen.top + (screen.height() - height) / 2
        return Rect(left, top, left + width, top + height)
    }

    fun openThread(context: Context, threadId: Long) = open(context, "oolong://thread/$threadId")

    /** The width from which a screen is large enough to put windows side by side. */
    private const val LargeScreenDp = 600

    /** A free window's size, a phone's, and how far it keeps from the screen's edges. */
    private const val FreeWindowWidthDp = 412
    private const val FreeWindowHeightDp = 860
    private const val FreeWindowMarginDp = 48

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
