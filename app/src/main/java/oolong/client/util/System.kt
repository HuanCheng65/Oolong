package oolong.client.util

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import oolong.client.R
import oolong.client.data.link.isInternalHost
import oolong.client.data.settings.SettingKeys
import oolong.client.media.image.loadBitmap
import oolong.client.navigation.Navigator
import oolong.client.navigation.ThreadKey
import oolong.client.navigation.WebViewKey
import oolong.client.ui.designsystem.theme.currentColors
import oolong.client.util.toastShort
import org.json.JSONObject
import oolong.client.data.settings.Settings as AppSettings

fun launchUrl(
    context: Context,
    navigator: Navigator,
    url: String,
) {
    val uri = url.toUri()
    val host = uri.host
    val path = uri.path
    val scheme = uri.scheme
    if (host == null || scheme == null || path == null) {
        return
    }
    if (scheme == "tiebaclient") {
        when (uri.getQueryParameter("action")) {
            "preview_file" -> {
                val realUrl = uri.getQueryParameter("url")
                if (realUrl.isNullOrEmpty()) {
                    return
                }
                launchUrl(context, navigator, realUrl)
            }

            else -> {
                context.toastShort(R.string.toast_feature_unavailable)
            }
        }
        return
    }
    if (scheme == "tiebaapp") {
        // Portal links carry the page they open as JSON in `params`
        val pageUrl = uri.getQueryParameter("params")
            ?.takeIf { host == "router" && path == "/portal" }
            ?.let { runCatching { JSONObject(it).getJSONObject("pageParams").getString("url") }.getOrNull() }
        if (pageUrl.isNullOrEmpty()) {
            context.toastShort(R.string.toast_feature_unavailable)
        } else {
            launchUrl(context, navigator, pageUrl)
        }
        return
    }
    if (!path.contains("android_asset")) {
        if (path == "/mo/q/checkurl") {
            launchUrl(
                context,
                navigator,
                uri.getQueryParameter("url")?.replace("http://https://", "https://").orEmpty()
            )
            return
        }
        if (host == "tieba.baidu.com" && path.startsWith("/p/")) {
            val threadId = path.substring(3).toLongOrNull()
            if (threadId != null) {
                navigator.navigate(
                    ThreadKey(threadId)
                )
            }
            return
        }
        if (isInternalHost(host) || AppSettings.of(context)[SettingKeys.UseWebView]) {
            navigator.navigate(
                WebViewKey(url)
            )
        } else {
            if (AppSettings.of(context)[SettingKeys.UseCustomTabs]) {
                val intentBuilder = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setDefaultColorSchemeParams(
                        CustomTabColorSchemeParams.Builder()
                            .setToolbarColor(currentColors(context).sheet.toArgb())
                            .build()
                    )
                try {
                    intentBuilder.build().launchUrl(context, uri)
                } catch (e: ActivityNotFoundException) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }
    }
}

val Context.powerManager: PowerManager
    get() = getSystemService(Context.POWER_SERVICE) as PowerManager

@SuppressLint("BatteryLife")
fun Context.requestIgnoreBatteryOptimizations() {
    if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = "package:$packageName".toUri()
        startActivity(intent)
    }
}

fun Context.isIgnoringBatteryOptimizations(): Boolean =
    powerManager.isIgnoringBatteryOptimizations(packageName)

suspend fun requestPinShortcut(
    context: Context,
    shortcutId: String,
    iconImageUri: String,
    label: String,
    shortcutIntent: Intent,
    onSuccess: () -> Unit = {},
    onFailure: (String) -> Unit = {}
) {
    if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        val icon = context.loadBitmap(iconImageUri)
        if (icon != null) {
            val shortcutInfo = ShortcutInfoCompat.Builder(context, shortcutId)
                .setIcon(IconCompat.createWithBitmap(icon))
                .setIntent(shortcutIntent)
                .setShortLabel(label)
                .build()
            val result = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
            if (result) onSuccess() else onFailure(context.getString(R.string.launcher_not_support_pin_shortcut))
        } else {
            onFailure(context.getString(R.string.load_shortcut_icon_fail))
        }
    } else {
        onFailure(context.getString(R.string.launcher_not_support_pin_shortcut))
    }
}
