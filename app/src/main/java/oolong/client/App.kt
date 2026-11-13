package oolong.client

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.github.gzuliyujiang.oaid.DeviceID
import dagger.hilt.android.HiltAndroidApp
import oolong.client.components.AppStorage
import oolong.client.components.CrashLogs
import oolong.client.components.OAIDGetter
import oolong.client.components.images.newAppImageLoader
import oolong.client.migration.LegacyImport
import oolong.client.ui.widgets.compose.VoicePlayback
import oolong.client.utils.AppIconUtil
import oolong.client.utils.ClientUtils
import oolong.client.utils.EmoticonManager
import oolong.client.utils.SharedPreferencesUtil
import oolong.client.utils.ThemeUtil
import oolong.client.utils.packageInfo
import kotlin.concurrent.thread

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {
    @RequiresApi(api = 28)
    private fun setWebViewPath(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = getProcessName(context)
            if (applicationContext.packageName != processName) { // 判断不等于默认进程名称
                WebView.setDataDirectorySuffix(processName!!)
            }
        }
    }

    private fun getProcessName(context: Context): String? {
        val manager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (processInfo in manager.runningAppProcesses) {
            if (processInfo.pid == Process.myPid()) {
                return processInfo.processName
            }
        }
        return null
    }

    override fun attachBaseContext(base: Context) {
        // Before content providers, Hilt and anything else opens a database or preferences file
        LegacyImport.applyStaged(base)
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        INSTANCE = this
        super.onCreate()
        CrashLogs.install(this)
        Incognito.follow(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setWebViewPath(this)
        }
        // Before ClientUtils, whose sync request reads the install times set here
        Config.init(this)
        ClientUtils.init(this)
        AppIconUtil.setIcon()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        updateScreenInfo()
        registerActivityLifecycleCallbacks(ThemeAndVoiceCallbacks)
        registerActivityLifecycleCallbacks(OAIDGetter)
        thread {
            EmoticonManager.init(this@App)
        }
        thread { AppStorage.sweep(this@App) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenInfo()
    }

    /** The screen's size and density, for what reads them outside of Compose. */
    private fun updateScreenInfo() {
        val metrics = resources.displayMetrics
        ScreenInfo.EXACT_SCREEN_HEIGHT = metrics.heightPixels
        ScreenInfo.EXACT_SCREEN_WIDTH = metrics.widthPixels
        ScreenInfo.DENSITY = metrics.density
        ScreenInfo.SCREEN_HEIGHT = (metrics.heightPixels / metrics.density).toInt()
        ScreenInfo.SCREEN_WIDTH = (metrics.widthPixels / metrics.density).toInt()
    }

    /**
     * Works the theme out again as each activity starts or comes back, as the system may have
     * turned dark or light meanwhile, and stops a voice when an activity is left.
     */
    private object ThemeAndVoiceCallbacks : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = ThemeUtil.refresh()

        override fun onActivityResumed(activity: Activity) = ThemeUtil.refresh()

        override fun onActivityStopped(activity: Activity) = VoicePlayback.stop()

        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {}
    }

    // 解决魅族 Flyme 系统夜间模式强制反色
    @Keep
    fun mzNightModeUseOf(): Int = 2

    object Config {
        var inited: Boolean = false

        var isOAIDSupported: Boolean = false
        var statusCode: Int = -200
        var oaid: String = ""
        var encodedOAID: String = ""
        var isTrackLimited: Boolean = false
        var userAgent: String? = null
        var appFirstInstallTime: Long = 0L
        var appLastUpdateTime: Long = 0L

        fun init(context: Context) {
            if (!inited) {
                isOAIDSupported = DeviceID.supportedOAID(context)
                if (isOAIDSupported) {
                    DeviceID.getOAID(context, OAIDGetter)
                } else {
                    statusCode = -200
                    isTrackLimited = false
                }
                userAgent = loadWebViewUserAgent(context)
                appFirstInstallTime = context.packageInfo.firstInstallTime
                appLastUpdateTime = context.packageInfo.lastUpdateTime
                inited = true
            }
        }

        /**
         * WebView's default user agent. Asking WebView for it loads and starts Chromium, the
         * slowest part of startup, so it's cached until WebView or the system is updated, which
         * is all the user agent is made of.
         */
        private fun loadWebViewUserAgent(context: Context): String {
            val webViewPackage =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WebView.getCurrentWebViewPackage()
                else null
            if (webViewPackage == null) return WebSettings.getDefaultUserAgent(context)
            val cacheKey = listOf(
                webViewPackage.packageName,
                PackageInfoCompat.getLongVersionCode(webViewPackage),
                Build.FINGERPRINT
            ).joinToString("|")
            val prefs = context.getSharedPreferences("webview_user_agent", Context.MODE_PRIVATE)
            val cached = prefs.getString("user_agent", null)
            if (cached != null && prefs.getString("cache_key", null) == cacheKey) return cached
            return WebSettings.getDefaultUserAgent(context).also {
                prefs.edit {
                    putString("cache_key", cacheKey)
                    putString("user_agent", it)
                }
            }
        }
    }

    object ScreenInfo {
        @JvmField
        var EXACT_SCREEN_HEIGHT = 0

        @JvmField
        var EXACT_SCREEN_WIDTH = 0

        @JvmField
        var SCREEN_HEIGHT = 0

        @JvmField
        var SCREEN_WIDTH = 0

        @JvmField
        var DENSITY = 0f
    }

    companion object {
        const val TAG = "App"

        private val packageName: String
            get() = INSTANCE.packageName

        @JvmStatic
        lateinit var INSTANCE: App
            private set

        val isInitialized: Boolean
            get() = this::INSTANCE.isInitialized

        val isSystemNight: Boolean
            get() = nightMode == Configuration.UI_MODE_NIGHT_YES

        val isFirstRun: Boolean
            get() = SharedPreferencesUtil.get(SharedPreferencesUtil.SP_APP_DATA)
                .getBoolean("first", true)

        private val nightMode: Int
            get() = INSTANCE.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = newAppImageLoader(context)
}
