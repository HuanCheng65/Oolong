package oolong.client.ui.thread

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.settingAsState

/** How a floor shows the replies inside it. */
enum class SubPostStyle {
    /** Each reply on a branch off the floor's rule, in full, more of them opening in place. */
    Timeline,

    /** The first replies a few lines each, in a block under the floor, the rest on their own page. */
    Classic,
}

// Each floor reads these as it comes into the list, so they start from what is stored, not from
// the default: read later, a floor would first show the other way for a frame

@Composable
fun rememberSubPostStyle(): SubPostStyle {
    val name by settingAsState(SettingKeys.SubPostStyle)
    return SubPostStyle.entries.firstOrNull { it.name == name } ?: SubPostStyle.Timeline
}

@Composable
fun rememberSubPostsCollapsed(): Boolean {
    val collapsed by settingAsState(SettingKeys.SubPostsCollapsed)
    return collapsed
}
