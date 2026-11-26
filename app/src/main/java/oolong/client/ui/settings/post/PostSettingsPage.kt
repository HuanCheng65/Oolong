package oolong.client.ui.settings.post

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import oolong.client.R
import oolong.client.data.settings.SettingKeys
import oolong.client.media.isPhotoPickerAvailable
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.PostSettingsKey
import oolong.client.navigation.ProvideNavigator
import oolong.client.ui.settings.components.SettingsScreen
import oolong.client.ui.settings.components.settingsPage

/** Posting and replying. */
val PostSettings = settingsPage(R.string.title_settings_post, PostSettingsKey) {
    rows {
        switch(SettingKeys.PostOrReplyWarning, R.string.title_post_or_reply_warning)
        switch(SettingKeys.HideReply, R.string.title_hide_reply)
        choice(SettingKeys.PicWatermarkType, R.string.title_settings_image_watermark,
            "0" to R.string.title_image_watermark_none,
            "1" to R.string.title_image_watermark_user_name,
            "2" to R.string.title_image_watermark_forum_name,
        )
        switch(SettingKeys.DoNotUsePhotoPicker, R.string.title_do_not_use_photo_picker,
            // Only said when there is no system picker to choose instead
            summary = { stringResource(id = R.string.summary_photo_picker_not_supported).takeUnless { isPhotoPickerAvailable() } },
            enabled = { isPhotoPickerAvailable() },
        )
    }
}

@Composable
fun PostSettingsPage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(PostSettings) }
}
