package oolong.client.ui.thread

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import oolong.client.R
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.ToggleIcon
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape
import oolong.core.designsystem.theme.TextStyles
import oolong.core.designsystem.theme.pressScale

/**
 * The thread menu, as it opens out of the actions capsule: one grid of tiles, how to read the
 * thread on the first row and what to do with it on the second. Its bottom row, with the like
 * and the close button, belongs to the capsule and is not part of it.
 *
 * Tiles that switch something leave the menu open, so the change is seen; the rest close it
 * through [onClose].
 */
@Composable
internal fun ColumnScope.ThreadMenu(
    seeLz: Boolean,
    stored: Boolean,
    newestFirst: Boolean,
    currentPage: Int,
    totalPage: Int,
    onEvent: (ThreadEvent) -> Unit,
    onJumpToPage: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The same icons as the switches in the top bar: the box filled, its letters cut out of
            // it, when only the author's posts show; the order turned over for newest first
            val colors = ExtendedTheme.colors
            val cut = if (colors.background.alpha < 1f) colors.sheet else colors.background
            MenuTile(
                text = stringResource(id = R.string.title_see_lz),
                checked = seeLz,
                role = Role.Switch,
                onClick = { onEvent(ThreadEvent.SetSeeLz(!seeLz)) },
            ) { tint ->
                val letters by animateColorAsState(if (seeLz) cut else tint, Motion.snap(), label = "lzLetters")
                Box(contentAlignment = Alignment.Center) {
                    ToggleIcon(checked = seeLz, on = LzIcon.BoxFill, off = LzIcon.Box, contentDescription = null, tint = tint)
                    Icon(LzIcon.Letters, contentDescription = null, tint = letters)
                }
            }
            MenuTile(
                text = stringResource(id = R.string.title_sort),
                checked = newestFirst,
                role = Role.Switch,
                onClick = { onEvent(ThreadEvent.ToggleSort) },
            ) { tint -> SortIcon(flipped = newestFirst, tint = tint) }
            MenuTile(
                text = stringResource(id = R.string.title_pure_read),
                icon = Symbols.ChromeReaderMode,
                onClick = {
                    onClose()
                    onEvent(ThreadEvent.OpenReader)
                },
            )
            // Pages run the other way when the newest come first, where there is nothing to jump to
            MenuTile(
                text = stringResource(id = R.string.title_jump_page),
                icon = Symbols.SwitchAccessShortcut,
                detail = "$currentPage / $totalPage",
                enabled = !newestFirst,
                onClick = {
                    onClose()
                    onJumpToPage()
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuTile(
                text = stringResource(id = if (stored) R.string.title_collected else R.string.title_collect),
                checked = stored,
                role = Role.Switch,
                onClick = { onEvent(if (stored) ThreadEvent.RemoveStore else ThreadEvent.StoreAtLastRead) },
            ) { tint -> ToggleIcon(checked = stored, on = Symbols.StarFill, off = Symbols.Star, contentDescription = null, tint = tint) }
            MenuTile(stringResource(id = R.string.title_share), Symbols.Share) {
                onClose()
                onEvent(ThreadEvent.Share)
            }
            MenuTile(stringResource(id = R.string.title_copy_link), Symbols.Link) {
                onClose()
                onEvent(ThreadEvent.CopyLink)
            }
            MenuTile(stringResource(id = R.string.reply_official_client), Symbols.OpenInNew) {
                onClose()
                onEvent(ThreadEvent.OpenInOfficialClient)
            }
        }
    }
}

/** One tile of the menu: its icon over its name, filled while what it switches is on. */
@Composable
private fun RowScope.MenuTile(
    text: String,
    icon: ImageVector,
    checked: Boolean = false,
    detail: String? = null,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
) {
    MenuTile(text, checked, detail, enabled, role, onClick) { tint -> Icon(imageVector = icon, contentDescription = null, tint = tint) }
}

/** [MenuTile] drawing its own [icon] in the tint it is given. */
@Composable
private fun RowScope.MenuTile(
    text: String,
    checked: Boolean = false,
    detail: String? = null,
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
) {
    val colors = ExtendedTheme.colors
    // Tinted, not filled, so the menu's glass shows through the tiles
    val rest = colors.text.copy(alpha = if (colors.isNightMode) TileTintNight else TileTint)
    val background by animateColorAsState(if (checked) colors.emphasis.copy(alpha = TileCheckedTint) else rest, Motion.snap(), label = "tileBackground")
    val content by animateColorAsState(if (checked) colors.emphasis else colors.text, Motion.snap(), label = "tileContent")
    val interactionSource = remember { MutableInteractionSource() }
    val disabledFade = animateFloatAsState(if (enabled) 1f else DisabledAlpha, Motion.fadeIn(), label = "tileEnabled")
    Column(
        modifier = Modifier
            .weight(1f)
            .height(TileHeight)
            .graphicsLayer { alpha = disabledFade.value }
            .pressScale(interactionSource)
            .background(background, TileShape)
            .clickable(
                interactionSource = interactionSource,
                indication = PressFeedback.overlay(TileShape),
                enabled = enabled,
                role = role,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        icon(content)
        // A label that changes with the tile, as collect to collected, refocuses in place
        Refocus(targetState = text) {
            Text(text = it, style = TextStyles.footnote, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (detail != null) {
            Text(
                text = detail,
                style = TextStyles.micro,
                color = content.copy(alpha = 0.6f),
                maxLines = 1,
            )
        }
    }
}

/** The menu's lower row beside the like and the close button: reporting the thread, or deleting one's own. */
@Composable
internal fun RowScope.ThreadMenuActions(canDelete: Boolean, onEvent: (ThreadEvent) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    FooterButton(stringResource(id = R.string.title_report)) {
        onClose()
        onEvent(ThreadEvent.ReportThread)
    }
    if (canDelete) {
        FooterButton(stringResource(id = R.string.title_delete), danger = true) {
            onClose()
            onDelete()
        }
    }
}

@Composable
private fun FooterButton(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val colors = ExtendedTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = text,
        style = TextStyles.label,
        color = if (danger) DangerColor else colors.textSecondary,
        modifier = Modifier
            .pressScale(interactionSource)
            .clickable(interactionSource = interactionSource, indication = PressFeedback.overlay(TileShape), role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

private val TileHeight = 72.dp

/** Concentric with the menu's corners, 12dp inside them. */
private val TileShape = SmoothCornerShape(14.dp)

private const val DisabledAlpha = 0.38f

/** How strongly the tiles tint the menu glass: by the text colour at rest, by the emphasis colour when on. */
private const val TileTint = 0.05f
private const val TileTintNight = 0.08f
private const val TileCheckedTint = 0.16f

private val DangerColor = Color(0xFFD23B3B)
