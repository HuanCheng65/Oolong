package oolong.client.ui.designsystem

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import oolong.client.R

/**
 * An icon button opening a menu of [items], which grows out of the button as it shows pressed
 * (design-system.md §9.3): its round glass when it stands alone in a top bar, its place in a
 * capsule it shares, or the round veil a press puts on it on the page. The menu takes the icon in
 * while it shows.
 */
@Composable
fun MenuIconButton(
    icon: ImageVector,
    contentDescription: String?,
    items: () -> List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
) {
    val menus = LocalContextMenus.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var menuId by remember { mutableStateOf<Long?>(null) }
    val open = menus?.isShowing(menuId) == true
    IconButton(
        onClick = {
            val list = items()
            if (menus != null && list.isNotEmpty()) menuId = menus.show(list, anchor = bounds, icon = icon, iconTint = tint)
        },
        modifier = modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.graphicsLayer { alpha = if (open) 0f else 1f },
        )
    }
}

/** Whether the menu something opened is on screen, e.g. to turn an arrow while it is. */
@Stable
class MenuTriggerState internal constructor() {
    internal var menus: ContextMenus? = null
    internal var id by mutableStateOf<Long?>(null)

    /** Where in the window the menu grows out of, when not the whole of what opens it. */
    internal var anchor: Rect? = null

    val open: Boolean get() = menus?.isShowing(id) == true
}

/** Makes this what [state]'s menu grows out of, such as the value of a setting rather than its row. */
fun Modifier.menuAnchor(state: MenuTriggerState): Modifier = onGloballyPositioned { state.anchor = it.boundsInWindow() }

@Composable
fun rememberMenuTriggerState() = remember { MenuTriggerState() }

/**
 * Opens a menu of [items] growing out of this when tapped, such as a setting with a few choices.
 * Presses go to [interactionSource] when given, for a press effect drawn elsewhere.
 */
@Composable
fun Modifier.clickMenu(
    items: () -> List<ContextMenuItem>,
    enabled: Boolean = true,
    indication: Indication? = LocalIndication.current,
    state: MenuTriggerState = rememberMenuTriggerState(),
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val menus = LocalContextMenus.current ?: return this
    state.menus = menus
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    return onGloballyPositioned { bounds = it.boundsInWindow() }
        .clickable(interactionSource = interaction, indication = indication, enabled = enabled) {
            val list = items()
            if (list.isNotEmpty()) state.id = menus.show(list, anchor = state.anchor ?: bounds)
        }
}

/**
 * Opens a menu of [items] where this is long-pressed, which the finger can slide onto and lift
 * over an item to pick it; a tap runs [onClick]. The menu grows out of the finger, as content has
 * no edge of its own to grow from, or out of all of this [fromWhole], for a control.
 */
@Composable
fun Modifier.longPressMenu(
    items: () -> List<ContextMenuItem>,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    indication: Indication? = LocalIndication.current,
    fromWhole: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val menus = LocalContextMenus.current
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressAt by remember { mutableStateOf(Offset.Zero) }
    // From the long press until the finger lifts, the menu follows it
    var holding by remember { mutableStateOf(false) }
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    return onGloballyPositioned { coordinates = it }
        // A right click opens the same menu where it lands, with no finger to follow
        .onSecondaryClick(enabled = enabled && menus != null) { at ->
            val list = items()
            if (menus != null && list.isNotEmpty()) {
                val whole = coordinates?.takeIf { fromWhole && it.isAttached }?.boundsInWindow()
                menus.show(list, anchor = whole ?: Rect(coordinates?.localToWindow(at) ?: at, Size.Zero))
            }
        }
        .pointerInput(menus) {
            if (menus == null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressAt = coordinates?.localToWindow(down.position) ?: Offset.Zero
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull() ?: break
                    val position = coordinates?.localToWindow(change.position) ?: break
                    if (!change.pressed) {
                        if (holding) menus.liftFinger(position)
                        holding = false
                        break
                    }
                    if (holding) menus.moveFinger(position)
                }
            }
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = indication,
            enabled = enabled,
            onLongClickLabel = stringResource(R.string.btn_more),
            onLongClick = menus?.let {
                {
                    val list = items()
                    if (list.isNotEmpty()) {
                        holding = true
                        val whole = coordinates?.takeIf { fromWhole && it.isAttached }?.boundsInWindow()
                        menus.show(list, anchor = whole ?: Rect(pressAt, Size.Zero))
                    }
                }
            },
            onClick = { onClick?.invoke() },
        )
}

/** One item per option of a choice, labelled by [options], the [selected] one checked. */
fun <T> choiceMenuItems(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit): List<ContextMenuItem> =
    options.map { (value, label) ->
        ContextMenuItem(icon = null, label = label, selected = value == selected) {
            if (value != selected) onSelect(value)
        }
    }
