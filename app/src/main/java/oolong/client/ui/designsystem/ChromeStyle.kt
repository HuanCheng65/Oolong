package oolong.client.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.first
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.settingAsState

/** How the app's bars are laid out, chosen in Settings › Appearance. */
enum class ChromeStyle {
    /** Glass capsules floating over the page, and a large title that docks into the top bar. */
    Floating,

    /**
     * The bars of before: bands across the top and the bottom of the window, their buttons bare
     * on them, tabs underlined, and the title in the bar.
     */
    Classic,
}

val LocalChromeStyle = compositionLocalOf { ChromeStyle.Floating }

/** Provides the chosen [ChromeStyle] to [content]. */
@Composable
fun ProvideChromeStyle(content: @Composable () -> Unit) {
    // Read from the first frame on: a page drawn in one style and redrawn in the other keeps
    // things it laid out for the first, such as a large title
    val chosen by settingAsState(SettingKeys.ChromeStyle)
    val style = ChromeStyle.entries.firstOrNull { it.name == chosen } ?: ChromeStyle.Floating
    CompositionLocalProvider(LocalChromeStyle provides style, content = content)
}

/**
 * Whether what is drawn sits on a classic bar, whose band is its ground: floating glass then
 * draws nothing of its own, so a bar's buttons and title are bare on it.
 */
internal val LocalOnClassicBar = staticCompositionLocalOf { false }
