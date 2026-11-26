package oolong.client.data.settings

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri

/**
 * The settings, outside Android's own, that phone makers add to stop apps working in the
 * background, and the way to them. None of this is public on any system: states are read where a
 * system lets them be, and each way to a setting falls back to the next, then to the app's page
 * in the system's settings.
 */
object BackgroundSettings {
    enum class Vendor(val dontKillMyApp: String) {
        Xiaomi("xiaomi"),
        Huawei("huawei"),
        Honor("honor"),
        Oppo("oppo"),
        Vivo("vivo"),
        Other(""),
    }

    enum class State { On, Off, Unknown }

    val vendor: Vendor by lazy {
        val maker = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        when {
            listOf("xiaomi", "redmi", "poco").any { it in maker } || systemProperty("ro.mi.os.version.name").isNotEmpty() ||
                systemProperty("ro.miui.ui.version.name").isNotEmpty() -> Vendor.Xiaomi
            "honor" in maker -> Vendor.Honor
            "huawei" in maker -> Vendor.Huawei
            listOf("oppo", "oneplus", "realme").any { it in maker } -> Vendor.Oppo
            listOf("vivo", "iqoo").any { it in maker } -> Vendor.Vivo
            else -> Vendor.Other
        }
    }

    /** Whether the app may start in the background, where the system says; only Xiaomi's does. */
    fun autoStart(context: Context): State {
        if (vendor != Vendor.Xiaomi) return State.Unknown
        return runCatching {
            val result = Class.forName("android.miui.AppOpsUtils")
                .getMethod("getApplicationAutoStart", Context::class.java, String::class.java)
                .invoke(null, context, context.packageName) as Int
            when (result) {
                0 -> State.On
                1 -> State.Off
                else -> null
            }
        }.getOrNull() ?: xiaomiAutoStartOp(context)
    }

    /** Opens where the app is let start in the background, or its page in the system's settings. */
    fun openAutoStart(context: Context) {
        val intents = when (vendor) {
            Vendor.Xiaomi -> listOf(
                Intent("miui.intent.action.OP_AUTO_START")
                    .setPackage("com.miui.securitycenter")
                    .putExtra("package_name", context.packageName)
                    .putExtra("extra_pkgname", context.packageName),
                component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            Vendor.Huawei -> listOf(
                component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                component("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            )
            Vendor.Honor -> listOf(
                component("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            )
            Vendor.Oppo -> listOf(
                component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            )
            Vendor.Vivo -> listOf(
                component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            )
            Vendor.Other -> emptyList()
        }
        if (intents.none { context.tryStart(it) }) openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        context.tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }

    /** Opens where the app is allowed to set alarms that go off on time. */
    fun openExactAlarms(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
            if (!context.tryStart(intent)) openAppDetails(context)
        }
    }

    fun openNotifications(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (!context.tryStart(intent)) openAppDetails(context)
    }

    /**
     * Whether the app may put shortcuts and widgets on the home screen, where the system says:
     * Xiaomi's asks for a permission of its own, and without it drops such requests silently.
     */
    fun homeScreenShortcuts(context: Context): State =
        if (vendor != Vendor.Xiaomi) State.Unknown else xiaomiOp(context, XiaomiShortcutsOp)

    /** Opens the app's permissions in Xiaomi's security centre, or its page in the system's settings. */
    fun openAppPermissions(context: Context) {
        val editor = Intent("miui.intent.action.APP_PERM_EDITOR")
            .setPackage("com.miui.securitycenter")
            .putExtra("extra_pkgname", context.packageName)
        if (vendor != Vendor.Xiaomi || !context.tryStart(editor)) openAppDetails(context)
    }

    private fun xiaomiAutoStartOp(context: Context): State = xiaomiOp(context, XiaomiAutoStartOp)

    private fun xiaomiOp(context: Context, op: Int): State = runCatching {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = AppOpsManager::class.java
            .getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
            .invoke(appOps, op, Process.myUid(), context.packageName) as Int
        when (mode) {
            AppOpsManager.MODE_ALLOWED -> State.On
            AppOpsManager.MODE_IGNORED -> State.Off
            else -> State.Unknown
        }
    }.getOrDefault(State.Unknown)

    private fun component(packageName: String, className: String) =
        Intent().setComponent(ComponentName(packageName, className))

    private fun Context.tryStart(intent: Intent): Boolean = runCatching {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    private fun systemProperty(name: String): String = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
    }.getOrDefault("")

    private const val XiaomiAutoStartOp = 10008
    private const val XiaomiShortcutsOp = 10017
}
