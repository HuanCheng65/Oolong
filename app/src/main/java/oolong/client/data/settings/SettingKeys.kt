package oolong.client.data.settings

import oolong.client.ui.designsystem.theme.ThemeUtil
import oolong.client.ui.designsystem.theme.ThemeUtil.TRANSLUCENT_THEME_LIGHT

/** Every setting kept in [Settings], under the key it has always been stored by. */
object SettingKeys {
    /** The key of [UsageStats], which the settings switch writes too. */
    const val UsageStatsKey = "usage_stats"

    val UserLikeLastRequestUnix = Setting.long("userLikeLastRequestUnix", 0L)

    val IgnoreBatteryOptimizationsDialog = Setting.boolean("ignoreBatteryOptimizationsDialog", false)

    /** Whether a forum's rules and pinned threads are folded into one row, in every forum. */
    val ForumTopsFolded = Setting.boolean("forum_tops_folded", false)

    /** Whether the first-launch landing page has been seen through. */
    val LandingShown = Setting.boolean("landing_shown", false)

    /** Whether usage statistics are sent (see UsageStats); asked on first launch, or once after an update. */
    val UsageStats = Setting.boolean(UsageStatsKey, false)

    val UsageStatsAsked = Setting.boolean("usage_stats_asked", false)

    val AppIcon = Setting.string(AppIconUtil.PREF_KEY_APP_ICON, LauncherIcons.DEFAULT_ICON)

    val UseThemedIcon = Setting.boolean("useThemedIcon", false)

    val AutoSign = Setting.boolean("auto_sign", false)

    val AutoSignTime = Setting.string("auto_sign_time", "09:00")

    val BlockVideo = Setting.boolean("blockVideo", false)

    /** Whether a Tieba link copied elsewhere is offered when the app comes to the front. */
    val ClipboardLinks = Setting.boolean("clipboard_links", true)

    val CollectThreadSeeLz = Setting.boolean("collect_thread_see_lz", true)

    val CollectThreadDescSort = Setting.boolean("collect_thread_desc_sort", false)

    val CustomPrimaryColor = Setting.optionalString("custom_primary_color")

    val DefaultSortType = Setting.string("default_sort_type", "0")

    val DoNotUsePhotoPicker = Setting.boolean("doNotUsePhotoPicker", false)

    /** Whether motion is reduced; read once at start, before the setting's flow arrives. */
    val ReduceMotion = Setting.boolean("reduceMotion", false)

    val ForumFabFunction = Setting.string("forumFabFunction", "hide")

    /** The name of a [oolong.client.ui.thread.SubPostStyle]. */
    val SubPostStyle = Setting.string("subPostStyle", "Timeline")

    val SubPostsCollapsed = Setting.boolean("subPostsCollapsed", false)

    val HideBlockedContent = Setting.boolean("hideBlockedContent", false)

    val HideExplore = Setting.boolean("hideExplore", false)

    /** Whether the feeds load anew when the app opens, rather than showing what was last loaded. */
    val FeedAutoRefresh = Setting.boolean("feedAutoRefresh", true)

    val HideMedia = Setting.boolean("hideMedia", false)

    val HideReply = Setting.boolean("hideReply", false)

    val HomePageShowHistoryForum = Setting.boolean("homePageShowHistoryForum", true)

    val ImageDarkenWhenNightMode = Setting.boolean("imageDarkenWhenNightMode", true)

    /** Whether a playing video shrinks into a picture-in-picture window when the app is left. */
    val VideoAutoPip = Setting.boolean("videoAutoPip", true)

    val ImageLoadType = Setting.string("image_load_type", "0")

    val ImeHeight = Setting.int("imeHeight", 800)

    val ListItemsBackgroundIntermixed = Setting.boolean("listItemsBackgroundIntermixed", true)

    val ListSingle = Setting.boolean("listSingle", false)

    val LittleTail = Setting.optionalString("little_tail")

    val OldTheme = Setting.optionalString("old_theme")

    val OksignSlowMode = Setting.boolean("oksign_slow_mode", true)

    val OksignUseOfficialOksign = Setting.boolean("oksign_use_official_oksign", true)

    val PicWatermarkType = Setting.string("pic_watermark_type", "2")

    val PostOrReplyWarning = Setting.boolean("postOrReplyWarning", true)

    val Radius = Setting.int("radius", 8)

    val SignDay = Setting.int("sign_day", -1)

    val SubPostSort = Setting.string("subPostSort", "hot")

    val HideIpLocation = Setting.boolean("hideIpLocation", false)

    val ShowBothUsernameAndNickname = Setting.boolean("show_both_username_and_nickname", false)

    val Theme = Setting.string("theme", ThemeUtil.THEME_DEFAULT)

    val TranslucentBackgroundAlpha = Setting.int("translucent_background_alpha", 255)

    val TranslucentBackgroundBlur = Setting.int("translucent_background_blur")

    val TranslucentBackgroundTheme = Setting.int("translucent_background_theme", TRANSLUCENT_THEME_LIGHT)

    val TranslucentThemeBackgroundPath = Setting.optionalString("translucent_theme_background_path")

    val TranslucentPrimaryColor = Setting.optionalString("translucent_primary_color")

    val UseCustomTabs = Setting.boolean("use_custom_tabs", true)

    val UseWebView = Setting.boolean("use_webview", true)
}
