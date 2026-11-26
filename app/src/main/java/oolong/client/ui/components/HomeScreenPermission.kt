package oolong.client.ui.components

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import oolong.client.R
import oolong.client.data.settings.BackgroundSettings
import oolong.client.ui.designsystem.ConfirmDialog
import oolong.client.ui.designsystem.DialogState
import oolong.client.ui.designsystem.rememberDialogState

/**
 * Putting something on the home screen, a widget or a shortcut, once the system lets the app:
 * Xiaomi's asks for a permission of its own and drops the request silently without it, so then
 * the user is told and taken to it instead.
 */
@Stable
class HomeScreenPermission internal constructor(private val allowed: () -> Boolean, private val dialog: DialogState) {
    /** Runs [put] when the app may put things on the home screen, or as far as can be told. */
    fun ensure(put: () -> Unit) {
        if (allowed()) put() else dialog.show()
    }
}

@Composable
fun rememberHomeScreenPermission(): HomeScreenPermission {
    val context = LocalContext.current
    val dialog = rememberDialogState()
    ConfirmDialog(
        dialogState = dialog,
        onConfirm = { BackgroundSettings.openAppPermissions(context) },
        confirmText = stringResource(R.string.button_go_to_settings),
        title = { Text(text = stringResource(R.string.title_home_screen_permission)) },
    ) {
        Text(text = stringResource(R.string.message_home_screen_permission))
    }
    return remember(context, dialog) {
        HomeScreenPermission({ BackgroundSettings.homeScreenShortcuts(context) != BackgroundSettings.State.Off }, dialog)
    }
}
