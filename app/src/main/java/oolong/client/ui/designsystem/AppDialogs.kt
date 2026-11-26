package oolong.client.ui.designsystem

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import oolong.client.R
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.TextStyles

/** A dialog asked for by code outside of Compose, as the web page's callbacks, shown by [AppDialogHost]. */
sealed interface AppDialog {
    /** A message, with a button to confirm and, if [cancel] is given, one to cancel. */
    class Message(
        val message: String,
        val title: String? = null,
        val confirm: String? = null,
        val onConfirm: () -> Unit = {},
        val cancel: String? = null,
        val onCancel: () -> Unit = {},
    ) : AppDialog

    /**
     * Whether to let a site do something; with "do not ask again", the answer is kept under [key]
     * and given at once the next time.
     */
    class Permission(
        val key: String,
        val title: String,
        /** What is asked for, shown above the question. */
        val icon: ImageVector,
        val onResult: (granted: Boolean, remembered: Boolean) -> Unit,
    ) : AppDialog

    /** Work under way that cannot be left. */
    data object Loading : AppDialog

    /** What a permission the system is asking for is used for, shown above the system's prompt. */
    class PermissionTip(val title: String, val message: String) : AppDialog
}

/** The dialogs shown now, newest last. */
object AppDialogs {
    internal val shown = mutableStateListOf<AppDialog>()

    fun show(dialog: AppDialog) {
        shown += dialog
    }

    fun dismiss(dialog: AppDialog) {
        shown -= dialog
    }

    /** Asks with [dialog], unless the answer was kept, which is then given at once. */
    fun ask(context: Context, dialog: AppDialog.Permission) {
        when (permissions(context).getInt(dialog.key, PERMISSION_UNSET)) {
            PERMISSION_ALLOWED -> dialog.onResult(true, true)
            PERMISSION_DENIED -> dialog.onResult(false, true)
            else -> show(dialog)
        }
    }

    /** Runs [block] with the loading dialog shown. */
    suspend fun <T> withLoading(block: suspend () -> T): T {
        show(AppDialog.Loading)
        try {
            return block()
        } finally {
            dismiss(AppDialog.Loading)
        }
    }

    internal fun remember(context: Context, dialog: AppDialog.Permission, granted: Boolean) {
        permissions(context).edit { putInt(dialog.key, if (granted) PERMISSION_ALLOWED else PERMISSION_DENIED) }
    }

    private fun permissions(context: Context) = context.getSharedPreferences("permission", Context.MODE_PRIVATE)

    private const val PERMISSION_UNSET = 0
    private const val PERMISSION_ALLOWED = 1
    private const val PERMISSION_DENIED = 2
}

/** Shows what [AppDialogs] holds, over the whole window. */
@Composable
fun AppDialogHost() {
    Box(modifier = Modifier.fillMaxSize()) {
        val tip = AppDialogs.shown.filterIsInstance<AppDialog.PermissionTip>().lastOrNull()
        AnimatedVisibility(
            visible = tip != null,
            // With motion reduced the banner only fades in where it stands
            enter = if (LocalReduceMotion.current) fadeIn(Motion.fadeIn()) else slideInVertically(Motion.settle()) { -it } + fadeIn(Motion.fadeIn()),
            exit = if (LocalReduceMotion.current) fadeOut(Motion.fadeOut()) else slideOutVertically(Motion.dismiss()) { -it } + fadeOut(Motion.fadeOut()),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            // Kept through the exit, when the tip is gone already
            val shownTip = remember { mutableStateOf(tip) }.apply { if (tip != null) value = tip }.value
            if (shownTip != null) PermissionTipBanner(shownTip)
        }
        AppDialogs.shown.forEach { dialog ->
            when (dialog) {
                is AppDialog.Message -> MessageDialog(dialog)
                is AppDialog.Permission -> PermissionDialog(dialog)
                AppDialog.Loading -> LoadingDialog()
                is AppDialog.PermissionTip -> Unit
            }
        }
    }
}

/** Shows [dialog] with the app's dialog, and drops it once it is closed. */
@Composable
private fun HostedDialog(
    dialog: AppDialog,
    title: String?,
    icon: ImageVector? = null,
    cancelable: Boolean = true,
    buttons: @Composable DialogScope.() -> Unit,
    content: @Composable DialogScope.() -> Unit,
) {
    val state = rememberDialogState()
    LaunchedEffect(dialog) {
        state.show()
        snapshotFlow { state.show }.filter { !it }.first()
        AppDialogs.dismiss(dialog)
    }
    Dialog(
        dialogState = state,
        cancelable = cancelable,
        cancelableOnTouchOutside = cancelable,
        title = title?.let {
            {
                Column {
                    // Above the title, at its start, so the dialog is laid out as any other
                    if (icon != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .size(DialogIconSize)
                                .background(ExtendedTheme.colors.accent.copy(alpha = 0.12f), CircleShape),
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = ExtendedTheme.colors.accent, modifier = Modifier.size(22.dp))
                        }
                    }
                    Text(text = it)
                }
            }
        },
        buttons = buttons,
        content = content,
    )
}

@Composable
private fun MessageDialog(dialog: AppDialog.Message) {
    HostedDialog(
        dialog = dialog,
        title = dialog.title,
        // Something waits for the answer
        cancelable = false,
        buttons = {
            DialogPositiveButton(text = dialog.confirm ?: stringResource(id = R.string.button_sure_default), onClick = dialog.onConfirm)
            if (dialog.cancel != null) DialogNegativeButton(text = dialog.cancel, onClick = dialog.onCancel)
        },
    ) {
        Text(
            text = dialog.message,
            color = ExtendedTheme.colors.text,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun PermissionDialog(dialog: AppDialog.Permission) {
    val context = LocalContext.current
    var remember by remember { mutableStateOf(false) }
    fun answer(granted: Boolean) {
        if (remember) AppDialogs.remember(context, dialog, granted)
        dialog.onResult(granted, remember)
    }
    // As the app's other dialogs: the question as the title, under what is asked for, and the choice
    // to keep the answer under it as the block dialog's rows are, its box in line with the title
    HostedDialog(
        dialog = dialog,
        title = dialog.title,
        icon = dialog.icon,
        cancelable = false,
        buttons = {
            DialogPositiveButton(text = stringResource(id = R.string.button_allow), onClick = { answer(true) })
            DialogNegativeButton(text = stringResource(id = R.string.button_denied), onClick = { answer(false) })
        },
    ) {
        val colors = ExtendedTheme.colors
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = CheckboxRowInset)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(interactionSource = null, indication = PressFeedback.overlay(Corners.sm)) { remember = !remember },
        ) {
            Checkbox(
                checked = remember,
                onCheckedChange = { remember = it },
                colors = CheckboxDefaults.colors(checkedColor = colors.emphasis, uncheckedColor = colors.textSecondary),
            )
            Text(text = stringResource(id = R.string.title_not_ask), style = TextStyles.bodyCompact, color = colors.text)
        }
    }
}

@Composable
private fun LoadingDialog() {
    val colors = ExtendedTheme.colors
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(colors.sheet, Corners.xl)
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = stringResource(id = R.string.text_loading), color = colors.text, fontSize = 15.sp)
        }
    }
}

@Composable
private fun PermissionTipBanner(tip: AppDialog.PermissionTip) {
    val colors = ExtendedTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.captionBar))
            .padding(16.dp)
            .fillMaxWidth()
            .shadow(12.dp, Corners.xl, clip = false)
            .background(colors.sheet, Corners.xl)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(text = tip.title, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = tip.message, color = colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

private val DialogIconSize = 40.dp
