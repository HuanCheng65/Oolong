package oolong.client.ui.settings.components.prefs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.core.designsystem.theme.Motion

/**
 * A setting's row: its title over an optional summary, an icon before them on the settings hub
 * only, and on the end whatever sets it (a switch, the chosen value) or an arrow to go further.
 */
@Composable
@ExperimentalMaterialApi
fun PrefsListItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    darkenOnDisable: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    secondaryText: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    textColor: Color = ExtendedTheme.colors.text,
    minimalHeight: Boolean = false,
    text: @Composable () -> Unit
) {
    // Turning unavailable, as when a setting it depends on goes off, it fades rather than greys in a frame
    val alpha by animateFloatAsState(if (enabled || !darkenOnDisable) 1f else DisabledAlpha, Motion.settle(), label = "prefsEnabled")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (minimalHeight) 32.dp else 52.dp)
            .padding(horizontal = 16.dp, vertical = if (secondaryText == null) 14.dp else 12.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            CompositionLocalProvider(LocalContentColor provides ExtendedTheme.colors.text) {
                Box(modifier = Modifier.sizeIn(minWidth = 24.dp, minHeight = 24.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ProvideTextStyle(TextStyle(color = textColor, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)) {
                text()
            }
            if (secondaryText != null) {
                ProvideTextStyle(TextStyle(color = ExtendedTheme.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)) {
                    secondaryText()
                }
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(16.dp))
            ProvideTextStyle(TextStyle(color = ExtendedTheme.colors.textSecondary, fontSize = 14.sp)) {
                CompositionLocalProvider(LocalContentColor provides ExtendedTheme.colors.textSecondary) {
                    trailing()
                }
            }
        }
    }
}

/** The arrow on a row that opens another page. */
@Composable
fun PrefsChevron() {
    Icon(
        imageVector = Symbols.ChevronRight,
        contentDescription = null,
        tint = ExtendedTheme.colors.textSecondary.copy(alpha = 0.6f),
        modifier = Modifier.size(20.dp),
    )
}

/** A setting's current value with the arrow to the page that changes it. */
@Composable
fun PrefsLinkValue(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Refocus(targetState = text, contentAlignment = Alignment.CenterEnd) { Text(text = it, maxLines = 1) }
        Spacer(modifier = Modifier.width(4.dp))
        PrefsChevron()
    }
}

/** The mark on a row that leaves the app, for a web page or the system's settings. */
@Composable
fun PrefsExternal(text: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (text != null) {
            Text(text = text, maxLines = 1)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            imageVector = Symbols.OpenInNew,
            contentDescription = null,
            tint = ExtendedTheme.colors.textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** A choice's value on the end of its row, with the up-and-down mark of a menu of values. */
@Composable
fun PrefsChoiceValue(text: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        // Another choice refocuses in place as the menu closes
        Refocus(targetState = text, contentAlignment = Alignment.CenterEnd) { Text(text = it, maxLines = 1) }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Symbols.UnfoldMore,
            contentDescription = null,
            tint = ExtendedTheme.colors.textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}

private const val DisabledAlpha = 0.38f
