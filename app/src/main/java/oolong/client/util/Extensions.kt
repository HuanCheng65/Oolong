package oolong.client.util

import android.animation.LayoutTransition
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK
import android.content.res.Resources
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import oolong.client.ui.designsystem.AppToast
import oolong.client.util.MD5Util
import kotlin.math.roundToInt

private val Context.scaledDensity: Float
    get() = resources.displayMetrics.scaledDensity

/** The screen's density, as the system's resources have it, which need no context to read. */
private val density: Float
    get() = Resources.getSystem().displayMetrics.density

fun Float.dpToPx(): Int =
    dpToPxFloat().roundToInt()

fun Float.dpToPxFloat(): Float =
    this * density + 0.5f

fun Float.pxToDp(): Int =
    (this / density + 0.5f).roundToInt()

fun Float.pxToDpFloat(): Float =
    this / density + 0.5f

fun Float.pxToSp(context: Context): Int =
    (this / context.scaledDensity + 0.5f).roundToInt()

fun Int.dpToPx(): Int = this.toFloat().dpToPx()

fun Int.pxToDp(): Int = this.toFloat().pxToDp()

fun Int.pxToSp(context: Context): Int = this.toFloat().pxToSp(context)

fun Int.pxToDpFloat(): Float =
    this.toFloat().pxToDpFloat()

fun String.toMD5(): String = MD5Util.toMd5(this)

fun ByteArray.toMD5(): String = MD5Util.toMd5(this)

fun Context.getColorCompat(@ColorRes id: Int): Int {
    return ContextCompat.getColor(this, id)
}

fun Context.getColorStateListCompat(id: Int): ColorStateList {
    return AppCompatResources.getColorStateList(this, id)
}

inline fun <reified T : Activity> Context.goToActivity() {
    startActivity(Intent(this, T::class.java))
}

inline fun <reified T : Activity> Context.goToActivity(pre: Intent.() -> Unit) {
    startActivity(Intent(this, T::class.java).apply(pre))
}

inline fun <reified T : Activity> Fragment.goToActivity() {
    startActivity(Intent(requireContext(), T::class.java))
}

inline fun <reified T : Activity> Fragment.goToActivity(pre: Intent.() -> Unit) {
    startActivity(Intent(requireContext(), T::class.java).apply(pre))
}

/** Shows [text] briefly: over the app when it is in front ([AppToast]), else as the system's toast. */
fun Context.toastShort(text: String) {
    if (AppToast.post(text)) return
    runCatching { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
}

fun Context.toastShort(resId: Int, vararg args: Any) {
    toastShort(getString(resId, *args))
}

fun ViewGroup.enableChangingLayoutTransition() {
    this.layoutTransition = LayoutTransition()
    this.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
}

fun View.getLocationInWindow(): IntArray {
    return IntArray(2).apply { getLocationInWindow(this) }
}

val Configuration.isPortrait: Boolean
    get() = orientation == Configuration.ORIENTATION_PORTRAIT

val Configuration.isLandscape: Boolean
    get() = orientation == Configuration.ORIENTATION_LANDSCAPE

val Configuration.isTablet: Boolean
    get() = (screenLayout and SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE

val Context.isTablet: Boolean
    get() = resources.configuration.isTablet

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun pendingIntentFlagMutable(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_MUTABLE
    } else {
        0
    }
}

fun pendingIntentFlagImmutable(): Int = PendingIntent.FLAG_IMMUTABLE

fun <T> ImmutableList<T>.removeAt(index: Int): ImmutableList<T> {
    return this.toMutableList().apply { removeAt(index) }.toImmutableList()
}
