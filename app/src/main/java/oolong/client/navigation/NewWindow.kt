package oolong.client.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
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

    /** Opens [link], one of the app's oolong:// links, in a new window beside this one. */
    fun open(context: Context, link: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .setClass(context, PageWindowActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT,
            )
        context.startActivity(intent)
    }

    fun openThread(context: Context, threadId: Long) = open(context, "oolong://thread/$threadId")

    /** The width from which a screen is large enough to put windows side by side. */
    private const val LargeScreenDp = 600

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
