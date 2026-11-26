package oolong.client.data.identity

import android.content.res.Resources
import oolong.client.App

/**
 * Used until WebView's own user agent is read: a current WebView's, which no longer names the
 * device or the full Chrome version.
 */
private const val defaultUserAgent: String =
    "Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/135.0.0.0 Mobile Safari/537.36"

fun getUserAgent(appendString: String? = null): String {
    val append = " ${appendString?.trim()}".takeIf { !appendString.isNullOrEmpty() }.orEmpty()
    return "${App.Config.userAgent ?: defaultUserAgent}$append"
}

// The system's resources, which hold the screen from the start, before any activity
fun getScreenHeight(): Int = Resources.getSystem().displayMetrics.heightPixels

fun getScreenWidth(): Int = Resources.getSystem().displayMetrics.widthPixels

fun getScreenDensity(): Float = Resources.getSystem().displayMetrics.density

fun Boolean.booleanToString(): String = if (this) "1" else "0"
