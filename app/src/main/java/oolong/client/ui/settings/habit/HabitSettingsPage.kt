package oolong.client.ui.settings.habit

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import oolong.client.R
import oolong.client.data.settings.SettingKeys
import oolong.client.data.thread.SubPostSort
import oolong.client.navigation.HabitSettingsKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.ui.settings.components.SettingsScreen
import oolong.client.ui.settings.components.settingsPage

/** Browsing: the home page, forums, threads and pictures. */
val HabitSettings = settingsPage(R.string.title_settings_read_habit, HabitSettingsKey) {
    group(R.string.settings_group_home) {
        switch(SettingKeys.ListSingle, R.string.settings_forum_single)
        switch(SettingKeys.HomePageShowHistoryForum, R.string.settings_home_page_show_history_forum)
        switch(SettingKeys.HideExplore, R.string.title_hide_explore)
        switch(SettingKeys.FeedAutoRefresh, R.string.title_feed_auto_refresh, summary = { stringResource(id = R.string.summary_feed_auto_refresh) })
    }
    group(R.string.settings_group_forum) {
        choice(SettingKeys.DefaultSortType, R.string.title_settings_default_sort_type,
            "0" to R.string.title_sort_by_reply,
            "1" to R.string.title_sort_by_send,
        )
        choice(SettingKeys.ForumFabFunction, R.string.settings_forum_fab_function,
            "refresh" to R.string.btn_refresh,
            "back_to_top" to R.string.btn_back_to_top,
            "hide" to R.string.btn_hide,
        )
    }
    group(R.string.settings_group_thread) {
        switch(SettingKeys.ShowBothUsernameAndNickname, R.string.title_show_both_username_and_nickname)
        choice(SettingKeys.SubPostSort, R.string.title_sub_post_sort,
            SubPostSort.Hot.value to R.string.sub_post_sort_hot,
            SubPostSort.Earliest.value to R.string.sub_post_sort_earliest,
        )
        switch(SettingKeys.HideIpLocation, R.string.title_hide_ip_location)
    }
    group(R.string.settings_group_collected) {
        switch(SettingKeys.CollectThreadSeeLz, R.string.settings_collect_thread_see_lz)
        switch(SettingKeys.CollectThreadDescSort, R.string.settings_collect_thread_desc_sort)
    }
    group(R.string.settings_group_image) {
        choice(SettingKeys.ImageLoadType, R.string.title_settings_image_load_type,
            "0" to R.string.title_image_load_type_smart_origin,
            "1" to R.string.title_image_load_type_smart_load,
            "2" to R.string.title_image_load_type_all_origin,
            "3" to R.string.title_image_load_type_all_no,
        )
        switch(SettingKeys.ImageDarkenWhenNightMode, R.string.settings_image_darken_when_night_mode)
        switch(SettingKeys.VideoAutoPip, R.string.settings_video_auto_pip)
        switch(SettingKeys.HideMedia, R.string.title_hide_media)
    }
}

@Composable
fun HabitSettingsPage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(HabitSettings) }
}
