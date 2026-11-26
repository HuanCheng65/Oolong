package oolong.client.data.settings

import android.content.Context
import kotlin.reflect.KProperty

/** The settings as properties of one object, over [Settings]; new code reads [Settings] instead. */
class AppPreferencesUtils private constructor(private val settings: Settings) {
    companion object {
        /** The key of [usageStats], which the settings switch writes too. */
        const val UsageStatsKey = SettingKeys.UsageStatsKey

        @Volatile
        private var instance: AppPreferencesUtils? = null

        fun getInstance(context: Context): AppPreferencesUtils =
            instance ?: AppPreferencesUtils(Settings.of(context)).also { instance = it }
    }

    private operator fun <T> Setting<T>.getValue(thisRef: Any?, property: KProperty<*>): T = settings[this]

    private operator fun <T> Setting<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        settings[this] = value
    }

    var userLikeLastRequestUnix by SettingKeys.UserLikeLastRequestUnix

    var ignoreBatteryOptimizationsDialog by SettingKeys.IgnoreBatteryOptimizationsDialog

    var forumTopsFolded by SettingKeys.ForumTopsFolded

    var landingShown by SettingKeys.LandingShown

    var usageStats by SettingKeys.UsageStats

    var usageStatsAsked by SettingKeys.UsageStatsAsked

    var appIcon: String?
        get() = settings[SettingKeys.AppIcon]
        set(value) = if (value == null) settings.remove(SettingKeys.AppIcon) else settings[SettingKeys.AppIcon] = value

    var useThemedIcon by SettingKeys.UseThemedIcon

    var autoSign by SettingKeys.AutoSign

    var autoSignTime: String?
        get() = settings[SettingKeys.AutoSignTime]
        set(value) = if (value == null) settings.remove(SettingKeys.AutoSignTime) else settings[SettingKeys.AutoSignTime] = value

    var blockVideo by SettingKeys.BlockVideo

    var clipboardLinks by SettingKeys.ClipboardLinks

    var collectThreadSeeLz by SettingKeys.CollectThreadSeeLz

    var collectThreadDescSort by SettingKeys.CollectThreadDescSort

    var customPrimaryColor by SettingKeys.CustomPrimaryColor

    var defaultSortType: String?
        get() = settings[SettingKeys.DefaultSortType]
        set(value) = if (value == null) settings.remove(SettingKeys.DefaultSortType) else settings[SettingKeys.DefaultSortType] = value

    var doNotUsePhotoPicker by SettingKeys.DoNotUsePhotoPicker

    var reduceMotion by SettingKeys.ReduceMotion

    var forumFabFunction: String?
        get() = settings[SettingKeys.ForumFabFunction]
        set(value) = if (value == null) settings.remove(SettingKeys.ForumFabFunction) else settings[SettingKeys.ForumFabFunction] = value

    var subPostStyle: String?
        get() = settings[SettingKeys.SubPostStyle]
        set(value) = if (value == null) settings.remove(SettingKeys.SubPostStyle) else settings[SettingKeys.SubPostStyle] = value

    var subPostsCollapsed by SettingKeys.SubPostsCollapsed

    var hideBlockedContent by SettingKeys.HideBlockedContent

    var hideExplore by SettingKeys.HideExplore

    var feedAutoRefresh by SettingKeys.FeedAutoRefresh

    var hideMedia by SettingKeys.HideMedia

    var hideReply by SettingKeys.HideReply

    var homePageShowHistoryForum by SettingKeys.HomePageShowHistoryForum

    var imageDarkenWhenNightMode by SettingKeys.ImageDarkenWhenNightMode

    var videoAutoPip by SettingKeys.VideoAutoPip

    var imageLoadType: String?
        get() = settings[SettingKeys.ImageLoadType]
        set(value) = if (value == null) settings.remove(SettingKeys.ImageLoadType) else settings[SettingKeys.ImageLoadType] = value

    var imeHeight by SettingKeys.ImeHeight

    var listItemsBackgroundIntermixed by SettingKeys.ListItemsBackgroundIntermixed

    var listSingle by SettingKeys.ListSingle

    var littleTail by SettingKeys.LittleTail

    var oldTheme by SettingKeys.OldTheme

    var oksignSlowMode by SettingKeys.OksignSlowMode

    var oksignUseOfficialOksign by SettingKeys.OksignUseOfficialOksign

    var picWatermarkType: String?
        get() = settings[SettingKeys.PicWatermarkType]
        set(value) = if (value == null) settings.remove(SettingKeys.PicWatermarkType) else settings[SettingKeys.PicWatermarkType] = value

    var postOrReplyWarning by SettingKeys.PostOrReplyWarning

    var radius by SettingKeys.Radius

    var signDay by SettingKeys.SignDay

    var subPostSort: String?
        get() = settings[SettingKeys.SubPostSort]
        set(value) = if (value == null) settings.remove(SettingKeys.SubPostSort) else settings[SettingKeys.SubPostSort] = value

    var hideIpLocation by SettingKeys.HideIpLocation

    var showBothUsernameAndNickname by SettingKeys.ShowBothUsernameAndNickname

    var theme: String?
        get() = settings[SettingKeys.Theme]
        set(value) = if (value == null) settings.remove(SettingKeys.Theme) else settings[SettingKeys.Theme] = value

    var translucentBackgroundAlpha by SettingKeys.TranslucentBackgroundAlpha

    var translucentBackgroundBlur by SettingKeys.TranslucentBackgroundBlur

    var translucentBackgroundTheme by SettingKeys.TranslucentBackgroundTheme

    var translucentThemeBackgroundPath by SettingKeys.TranslucentThemeBackgroundPath

    var translucentPrimaryColor by SettingKeys.TranslucentPrimaryColor

    var useCustomTabs by SettingKeys.UseCustomTabs

    var useWebView by SettingKeys.UseWebView
}

val Context.appPreferences: AppPreferencesUtils
    get() = AppPreferencesUtils.getInstance(this)
