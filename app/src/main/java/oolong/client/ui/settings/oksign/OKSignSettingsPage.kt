package oolong.client.ui.settings.oksign

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import oolong.client.R
import oolong.client.background.sign.AutoSign
import oolong.client.data.settings.BackgroundSettings
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.rememberSettings
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.OKSignSettingsKey
import oolong.client.navigation.ProvideNavigator
import oolong.client.ui.settings.components.Availability
import oolong.client.ui.settings.components.SettingsHeader
import oolong.client.ui.settings.components.SettingsPage
import oolong.client.ui.settings.components.SettingsScope
import oolong.client.ui.settings.components.SettingsScreen
import oolong.client.ui.settings.components.prefs.PrefsExternal
import oolong.client.ui.settings.components.prefs.widgets.TextPref
import oolong.client.ui.settings.components.stored
import oolong.client.util.PermissionUtils
import oolong.client.util.isIgnoringBatteryOptimizations
import oolong.client.util.launchUrl
import oolong.client.util.requestIgnoreBatteryOptimizations
import oolong.client.util.requestPermission
import oolong.client.util.toastShort
import java.util.Date

/** What the sign-in page knows only while it shows: what the system allows, and ways to ask for it. */
class OKSignState(
    val conditions: RunConditions,
    /** The system's prompt for notifications, after a word on what they are for; its settings once it no longer asks. */
    val requestNotifications: () -> Unit,
    private val context: Context,
    private val testScheduled: String,
) {
    /** Says when the test sign-in will run. */
    fun sayTestScheduled(time: String) {
        context.toastShort(testScheduled.format(time))
    }
}

/** Signing in to every followed forum: at once, and each day by itself. */
@OptIn(ExperimentalMaterialApi::class)
object OKSignSettings : SettingsPage<OKSignState>(R.string.title_oksign, OKSignSettingsKey) {
    @Composable
    override fun rememberState(): OKSignState {
        val context = LocalContext.current
        // What the system allows is read again on coming back from its settings
        var resumes by remember { mutableIntStateOf(0) }
        LifecycleResumeEffect(Unit) {
            resumes++
            onPauseOrDispose {}
        }
        val conditions = remember(resumes) { RunConditions.read(context) }
        val requestNotifications: () -> Unit = {
            context.requestPermission {
                permissions = listOf(PermissionUtils.POST_NOTIFICATIONS)
                description = context.getString(R.string.desc_permission_post_notifications)
                onGranted = { resumes++ }
                onDeniedForever = { BackgroundSettings.openNotifications(context) }
            }
        }
        return OKSignState(
            conditions = conditions,
            requestNotifications = requestNotifications,
            context = context,
            testScheduled = stringResource(id = R.string.toast_auto_sign_test_scheduled),
        )
    }

    @Composable
    override fun Effects(state: OKSignState) {
        val context = LocalContext.current
        val settings = rememberSettings()
        // Reschedule the daily sign-in as soon as it is turned on or off or its time changes
        LaunchedEffect(settings) {
            settings.flow(SettingKeys.AutoSign)
                .combine(settings.flow(SettingKeys.AutoSignTime)) { on, time -> on to time }
                .distinctUntilChanged()
                .drop(1)
                .collect { AutoSign.schedule(context) }
        }
        // Turning signing in automatically on is when its results start to need notifications
        LaunchedEffect(settings) {
            settings.flow(SettingKeys.AutoSign)
                .drop(1)
                .collect { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !RunConditions.read(context).notifications) {
                        state.requestNotifications()
                    }
                }
        }
    }

    override fun SettingsScope<OKSignState>.content() {
        group(R.string.settings_group_sign_mode) {
            switch(SettingKeys.OksignSlowMode, R.string.title_oksign_slow_mode, summary = { stringResource(id = R.string.summary_oksign_slow_mode) })
            switch(SettingKeys.OksignUseOfficialOksign, R.string.title_oksign_use_official_oksign,
                summary = { stringResource(id = R.string.summary_oksign_use_official_oksign) },
            )
        }
        group(R.string.title_auto_sign) {
            switch(SettingKeys.AutoSign, R.string.title_auto_sign)
            time(SettingKeys.AutoSignTime, R.string.title_auto_sign_time, enabled = { stored(SettingKeys.AutoSign) })
            info(R.string.title_auto_sign_last, value = { lastRunText(conditions.lastRun) })
        }
        group(R.string.settings_group_auto_sign_conditions) {
            action(
                R.string.title_exact_alarms,
                value = { allowed(conditions.exactAlarms) },
                external = true,
                available = Availability.sdk(Build.VERSION_CODES.S),
                onClick = { BackgroundSettings.openExactAlarms(context) },
            )
            // The system's state, and the system's dialog to change it; on Xiaomi's systems this
            // opens its own battery saver page
            action(
                R.string.title_ignore_battery_optimization,
                value = {
                    stringResource(id = if (conditions.batteryIgnored) R.string.summary_battery_optimization_ignored else R.string.value_battery_optimization_on)
                },
                external = true,
                onClick = {
                    if (state.conditions.batteryIgnored) BackgroundSettings.openAppDetails(context) else context.requestIgnoreBatteryOptimizations()
                },
            )
            action(
                R.string.title_auto_start,
                value = {
                    when (conditions.autoStart) {
                        BackgroundSettings.State.On -> allowed(true)
                        BackgroundSettings.State.Off -> allowed(false)
                        BackgroundSettings.State.Unknown -> null
                    }
                },
                external = true,
                available = { BackgroundSettings.vendor != BackgroundSettings.Vendor.Other },
                onClick = { BackgroundSettings.openAutoStart(context) },
            )
            // Not yet allowed, the row asks here rather than leaving the app
            custom(R.string.title_system_notifications, available = Availability.sdk(Build.VERSION_CODES.TIRAMISU)) { modifier ->
                val context = LocalContext.current
                TextPref(
                    title = stringResource(id = R.string.title_system_notifications),
                    modifier = modifier,
                    summary = stringResource(id = R.string.summary_auto_sign_notifications),
                    trailingContent = if (conditions.notifications) {
                        { PrefsExternal(text = allowed(true)) }
                    } else {
                        { Text(text = allowed(false)) }
                    },
                    onClick = { if (conditions.notifications) BackgroundSettings.openNotifications(context) else requestNotifications() },
                )
            }
            action(
                R.string.title_auto_sign_test,
                summary = { stringResource(id = R.string.summary_auto_sign_test) },
                searchable = false,
                onClick = {
                    val time = AutoSign.test(context, TestDelayMillis)
                    state.sayTestScheduled(DateFormat.getTimeFormat(context).format(Date(time)))
                },
            )
            footer { vendorTip() }
        }
        group(SettingsHeader.Gap(0.dp)) {
            action(
                R.string.title_background_guide,
                external = true,
                onClick = { launchUrl(context, navigator, "https://dontkillmyapp.com/${BackgroundSettings.vendor.dontKillMyApp}") },
            )
        }
    }
}

@Composable
fun OKSignSettingsPage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(OKSignSettings) }
}

@Composable
private fun allowed(yes: Boolean) = stringResource(id = if (yes) R.string.value_allowed else R.string.value_not_allowed)

/** What the system lets the daily sign-in do, as it stands now. */
class RunConditions(
    val exactAlarms: Boolean,
    val batteryIgnored: Boolean,
    val autoStart: BackgroundSettings.State,
    val notifications: Boolean,
    val lastRun: AutoSign.LastRun?,
) {
    companion object {
        fun read(context: android.content.Context) = RunConditions(
            exactAlarms = AutoSign.canScheduleExactAlarms(context),
            batteryIgnored = context.isIgnoringBatteryOptimizations(),
            autoStart = BackgroundSettings.autoStart(context),
            notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            lastRun = AutoSign.lastRun(context),
        )
    }
}

@Composable
private fun lastRunText(run: AutoSign.LastRun?): String {
    if (run == null) return stringResource(id = R.string.value_auto_sign_never)
    val context = LocalContext.current
    val time = DateUtils.formatDateTime(
        context,
        run.time,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
    return when {
        !run.success -> stringResource(id = R.string.value_auto_sign_failed, time)
        else -> stringResource(id = R.string.value_auto_sign_done, time, run.signed, run.total)
    }
}

/** What else this phone's maker wants allowed, where the rows above cannot show it. */
@Composable
private fun vendorTip(): String = stringResource(
    id = when (BackgroundSettings.vendor) {
        BackgroundSettings.Vendor.Xiaomi -> R.string.tip_auto_sign_xiaomi
        BackgroundSettings.Vendor.Huawei, BackgroundSettings.Vendor.Honor -> R.string.tip_auto_sign_huawei
        BackgroundSettings.Vendor.Oppo -> R.string.tip_auto_sign_oppo
        BackgroundSettings.Vendor.Vivo -> R.string.tip_auto_sign_vivo
        BackgroundSettings.Vendor.Other -> R.string.tip_auto_sign_other
    },
)

private const val TestDelayMillis = 2 * 60 * 1000L
