package oolong.client.ui.designsystem.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.settingAsState

/** Whether motion is reduced: by the setting, or with the system's animations off. */
@Composable
fun rememberReduceMotion(): Boolean {
    val setting by settingAsState(SettingKeys.ReduceMotion)
    return setting || rememberSystemAnimationsOff()
}

/** Whether the system's animator duration scale is 0, as 「移除动画」 and the developer options set it. */
@Composable
private fun rememberSystemAnimationsOff(): Boolean {
    val context = LocalContext.current.applicationContext
    val off = remember { mutableStateOf(animationsOff(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                off.value = animationsOff(context)
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        // Changed while nothing listened
        off.value = animationsOff(context)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return off.value
}

private fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
