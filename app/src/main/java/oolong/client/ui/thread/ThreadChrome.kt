package oolong.client.ui.thread

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import oolong.client.R
import oolong.client.model.Agree
import oolong.client.model.ForumSummary
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarItemHeight
import oolong.client.ui.designsystem.Refocus
import oolong.client.ui.designsystem.ToggleIcon
import oolong.client.ui.designsystem.floatingGlass
import oolong.client.ui.designsystem.pressedByGlass
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.util.StringUtil.getShortNumString
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.SmoothCornerShape

/**
 * Back, the forum the thread is in, which opens the forum, and the reply [filter] once it has
 * docked in the bar.
 */
@Composable
internal fun ThreadTopBar(
    forum: ForumSummary?,
    onEvent: (ThreadEvent) -> Unit,
    filter: (@Composable () -> Unit)?,
    hiddenFraction: () -> Float,
) {
    GlassTopBar(
        title = {
            if (forum != null && forum.name.isNotBlank()) {
                // The forum's name is a button, so it floats on glass like the bar's other buttons
                Row(
                    modifier = Modifier
                        .height(GlassTopBarItemHeight)
                        .floatingGlass(ForumCapsule, pressable = true)
                        // The glass answers the press
                        .clickable(interactionSource = null, indication = null) { onEvent(ThreadEvent.OpenForum) }
                        .padding(start = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(
                        data = forum.avatar,
                        size = 28.dp,
                        contentDescription = forum.name,
                    )
                    Text(
                        text = stringResource(id = R.string.title_forum, forum.name),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ExtendedTheme.colors.text,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = { BackNavigationIcon { onEvent(ThreadEvent.Back) } },
        actions = { filter?.invoke() },
        actionsInCapsule = false,
        hiddenFraction = hiddenFraction,
    )
}

private val ForumCapsule = SmoothCornerShape.Capsule

private val CapsuleShape = Corners.full

/** The actions capsule's content at rest: liking the thread, and the menu. */
@Composable
internal fun RowScope.ThreadActions(agree: Agree, onEvent: (ThreadEvent) -> Unit) {
    ThreadAgreeButton(agree = agree, onClick = { onEvent(ThreadEvent.AgreeThread) })
    MenuButton(open = false, onClick = { onEvent(ThreadEvent.OpenMenu) })
}

/**
 * The open menu's bottom row: [actions] at its start, and where the actions capsule was the like,
 * which stays, and ⋮ turned into ×.
 */
@Composable
internal fun RowScope.ThreadMenuFooter(
    agree: Agree,
    onEvent: (ThreadEvent) -> Unit,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    actions()
    Spacer(modifier = Modifier.weight(1f))
    ThreadAgreeButton(agree = agree, onClick = { onEvent(ThreadEvent.AgreeThread) })
    MenuButton(open = true, onClick = onClose)
}

@Composable
private fun MenuButton(open: Boolean, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (open) 1f else 0f, Motion.snap(), label = "menuButton")
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(interactionSource = null, indication = if (pressedByGlass()) null else PressFeedback.overlay(CapsuleShape), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = ExtendedTheme.colors.textSecondary
        Icon(
            imageVector = Symbols.MoreVert,
            contentDescription = if (open) null else stringResource(id = R.string.btn_more),
            tint = tint,
            modifier = Modifier.graphicsLayer {
                alpha = 1f - turn
                rotationZ = 90f * turn
            },
        )
        Icon(
            imageVector = Symbols.Close,
            contentDescription = if (open) stringResource(id = R.string.reply_menu_close) else null,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                alpha = turn
                rotationZ = -90f * (1f - turn)
            },
        )
    }
}

@Composable
private fun ThreadAgreeButton(agree: Agree, onClick: () -> Unit) {
    val color by animateColorAsState(
        if (agree.agreed) ExtendedTheme.colors.accent else ExtendedTheme.colors.textSecondary,
        Motion.snap(),
        label = "threadAgreeColor"
    )
    Row(
        modifier = Modifier
            .height(40.dp)
            .clickable(interactionSource = null, indication = if (pressedByGlass()) null else PressFeedback.overlay(CapsuleShape), onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleIcon(
            checked = agree.agreed,
            on = Symbols.FavoriteFill,
            off = Symbols.Favorite,
            contentDescription = stringResource(id = R.string.title_agree),
            tint = color,
        )
        Refocus(targetState = agree.count) { count ->
            if (count > 0) {
                Text(
                    text = count.getShortNumString(),
                    color = color,
                    fontSize = 13.sp,
                    // The gap comes and goes with the count
                    modifier = Modifier.padding(start = 3.dp),
                )
            }
        }
    }
}
