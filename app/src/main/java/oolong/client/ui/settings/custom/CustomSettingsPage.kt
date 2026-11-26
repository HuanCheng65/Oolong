package oolong.client.ui.settings.custom

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import oolong.client.R
import oolong.client.data.settings.AppIconUtil
import oolong.client.data.settings.LauncherIcons
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.ThemeSettings
import oolong.client.data.settings.settingAsMutableState
import oolong.client.navigation.AppThemeKey
import oolong.client.navigation.CustomSettingsKey
import oolong.client.navigation.FontSizeKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.navigation.SearchPlacementKey
import oolong.client.ui.designsystem.ChromeStyle
import oolong.client.ui.designsystem.GlassTier
import oolong.client.ui.designsystem.Sizes
import oolong.client.ui.designsystem.Slider
import oolong.client.ui.designsystem.available
import oolong.client.ui.designsystem.chosenGlassTier
import oolong.client.ui.designsystem.theme.TextScale
import oolong.client.ui.designsystem.theme.ThemeUtil
import oolong.client.ui.settings.components.Availability
import oolong.client.ui.settings.components.SettingsHeader
import oolong.client.ui.settings.components.SettingsScreen
import oolong.client.ui.settings.components.prefs.widgets.ListPref
import oolong.client.ui.settings.components.prefs.widgets.TextPref
import oolong.client.ui.settings.components.settingsPage
import oolong.client.ui.settings.components.stored
import oolong.client.ui.thread.SubPostStyle
import kotlin.math.roundToInt

/** Appearance: the theme, the language and layout, text size and the app's icon, and the glass effects. */
val CustomSettings = settingsPage(R.string.title_settings_custom, CustomSettingsKey) {
    group(R.string.settings_group_theme) {
        link(R.string.title_theme, to = AppThemeKey, value = { currentThemeName() })
    }
    group(SettingsHeader.Gap(8.dp)) {
        choice(
            R.string.title_language,
            options = listOf("" to R.string.language_system, "zh-CN" to R.string.language_zh_cn, "en" to R.string.language_en),
            value = { appLanguage() },
            onSelect = { tag -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
        )
        link(R.string.title_search_placement, to = SearchPlacementKey, value = {
            stringResource(id = if (stored(SettingKeys.SearchAtTop)) R.string.search_place_top else R.string.search_place_bottom)
        })
        choice(
            R.string.title_sub_post_style,
            options = listOf(SubPostStyle.Timeline.name to R.string.sub_post_style_timeline, SubPostStyle.Classic.name to R.string.sub_post_style_classic),
            value = { stored(SettingKeys.SubPostStyle) },
            onSelect = { style -> settings[SettingKeys.SubPostStyle] = style },
        )
        switch(SettingKeys.SubPostsCollapsed, R.string.title_sub_posts_collapsed)
    }
    group(R.string.settings_group_text_and_icon) {
        link(R.string.title_custom_font_size, to = FontSizeKey, aliases = R.string.search_aliases_font_size, value = { fontSizeValue() })
        custom(R.string.settings_app_icon) { modifier -> AppIconPref(modifier) }
        switch(SettingKeys.UseThemedIcon, R.string.title_settings_use_themed_icon,
            summary = { stringResource(id = R.string.tip_settings_use_themed_icon_summary_not_supported).takeUnless { themedIconSupported() } },
            enabled = { themedIconSupported() },
            onChange = { AppIconUtil.setIcon(isThemed = it) },
            available = Availability.sdk(Build.VERSION_CODES.S),
        )
    }
    group(R.string.settings_group_effects) {
        choice(
            R.string.title_chrome_style,
            options = listOf(ChromeStyle.Floating.name to R.string.chrome_style_floating, ChromeStyle.Classic.name to R.string.chrome_style_classic),
            value = { stored(SettingKeys.ChromeStyle) },
            onSelect = { style -> settings[SettingKeys.ChromeStyle] = style },
        )
        // Where the device draws only one material, there is nothing to choose
        choice(
            R.string.title_glass_material,
            options = GlassTier.available.map { it to it.title },
            value = { chosenGlassTier() },
            onSelect = { tier -> settings[SettingKeys.GlassMaterial] = tier.name },
            available = SeveralMaterials,
        )
        custom(R.string.title_glass_clarity, available = SeveralMaterials, shown = { chosenGlassTier() != GlassTier.Solid }) { GlassClarityPref() }
        switch(SettingKeys.PredictiveBack, R.string.title_settings_predictive_back,
            summary = { stringResource(id = R.string.summary_settings_predictive_back) },
        )
        switch(SettingKeys.ReduceMotion, R.string.title_settings_reduce_motion)
        switch(SettingKeys.MainTabSwipe, R.string.title_settings_main_tab_swipe)
    }
}

@Composable
fun CustomSettingsPage(navigator: Navigator = LocalNavigator.current) {
    ProvideNavigator(navigator) { SettingsScreen(CustomSettings) }
}

private val SeveralMaterials = Availability { GlassTier.available.size > 1 }

private val GlassTier.title: Int
    get() = when (this) {
        GlassTier.Glass -> R.string.glass_material_glass
        GlassTier.Blur -> R.string.glass_material_blur
        GlassTier.Solid -> R.string.glass_material_solid
    }

/**
 * The language the app is shown in: the system's (""), or one chosen for the app alone, which the
 * system keeps as the app's language and shows in its own settings on Android 13 and later.
 */
private fun appLanguage(): String {
    val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return listOf("zh-CN", "en").firstOrNull { tags.startsWith(it.substringBefore('-')) } ?: ""
}

/** Whether the launcher icon in use has a themed form; only the new one does. */
@Composable
private fun themedIconSupported(): Boolean = stored(SettingKeys.AppIcon) == LauncherIcons.NEW_ICON

/** The colour in use, by the name the theme page gives it. */
@Composable
internal fun currentThemeName(): String {
    val settings by ThemeUtil.settingsState
    val names = stringArrayResource(id = R.array.accent_names)
    return when {
        settings.isSystemColor -> stringResource(id = R.string.accent_system_wallpaper)
        settings.accent == ThemeUtil.THEME_CUSTOM -> stringResource(id = R.string.title_custom_color)
        else -> names.getOrNull(ThemeSettings.ACCENTS.indexOf(settings.accent)).orEmpty()
    }
}

/** The launcher icon, chosen in a dialog showing each. */
@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun AppIconPref(modifier: Modifier) {
    ListPref(
        setting = SettingKeys.AppIcon,
        title = stringResource(id = R.string.settings_app_icon),
        modifier = modifier,
        entries = mapOf(
            LauncherIcons.NEW_ICON to stringResource(id = R.string.app_icon_new),
            LauncherIcons.NEW_ICON_INVERT to stringResource(id = R.string.app_icon_new_inverted),
            LauncherIcons.OLD_ICON to stringResource(id = R.string.app_icon_old),
        ),
        icons = mapOf(
            LauncherIcons.NEW_ICON to {
                Image(
                    painter = rememberDrawablePainter(
                        drawable = LocalContext.current.getDrawable(
                            R.drawable.ic_launcher_new_round
                        )
                    ),
                    contentDescription = stringResource(id = R.string.app_icon_new),
                    modifier = Modifier.size(Sizes.Medium)
                )
            },
            LauncherIcons.NEW_ICON_INVERT to {
                Image(
                    painter = rememberDrawablePainter(
                        drawable = LocalContext.current.getDrawable(
                            R.drawable.ic_launcher_new_invert_round
                        )
                    ),
                    contentDescription = stringResource(id = R.string.app_icon_new_inverted),
                    modifier = Modifier.size(Sizes.Medium)
                )
            },
            LauncherIcons.OLD_ICON to {
                Image(
                    painter = rememberDrawablePainter(
                        drawable = LocalContext.current.getDrawable(
                            R.drawable.ic_launcher_round
                        )
                    ),
                    contentDescription = stringResource(id = R.string.app_icon_old),
                    modifier = Modifier.size(Sizes.Medium)
                )
            },
        ),
        onValueChange = { AppIconUtil.setIcon(icon = it) },
        useSelectedAsSummary = true,
    )
}

/** How clear the glass is, from frosted to clear; the chrome on the page shows it as it moves. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun GlassClarityPref() {
    var clarity by settingAsMutableState(SettingKeys.GlassClarity)
    Column {
        TextPref(title = stringResource(id = R.string.title_glass_clarity), minimalHeight = true, darkenOnDisable = false)
        Slider(
            value = clarity,
            onValueChange = { clarity = it },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
}

/** The text size as the appearance page shows it: following the system, or the percentage chosen. */
@Composable
private fun fontSizeValue(): String {
    val chosen = stored(TextScale.ScaleKey, 1f)
    return if (stored(TextScale.FollowSystemKey, TextScale.followsByDefault(chosen))) {
        stringResource(id = R.string.title_follow_system_font_size)
    } else {
        "${(chosen * 100).roundToInt()}%"
    }
}
