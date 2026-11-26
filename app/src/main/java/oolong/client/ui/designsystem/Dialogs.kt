package oolong.client.ui.designsystem

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import oolong.client.R
import oolong.client.ui.designsystem.dialogs.AnyPopDialog
import oolong.client.ui.designsystem.dialogs.AnyPopDialogProperties
import oolong.client.ui.designsystem.dialogs.DirectionState
import oolong.client.ui.designsystem.picker.TimePicker
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.glassEdge
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.Radius
import oolong.core.designsystem.theme.SmoothCornerShape

@Composable
fun DialogScope.DialogPositiveButton(
    text: String,
    onClick: () -> Unit = {}
) {
    Button(
        onClick = {
            onClick()
            dismiss()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
fun DialogScope.DialogNegativeButton(
    text: String,
    onClick: (() -> Unit)? = null
) {
    SecondaryButton(
        onClick = {
            dismiss()
            onClick?.invoke()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
fun TimePickerDialog(
    title: @Composable (DialogScope.() -> Unit),
    currentTime: String,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
    dialogState: DialogState = rememberDialogState(),
    onValueChange: ((String) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    is24TimeFormat: Boolean = true,
    confirmText: String = stringResource(id = R.string.button_sure_default),
    cancelText: String = stringResource(id = R.string.button_cancel),
    content: @Composable (DialogScope.() -> Unit) = {},
) {
    var timeVal by remember { mutableStateOf(currentTime) }
    Dialog(
        modifier = modifier,
        dialogState = dialogState,
        onDismiss = onCancel,
        title = title,
        buttons = {
            DialogPositiveButton(text = confirmText, onClick = { onConfirm.invoke(timeVal) })
            DialogNegativeButton(text = cancelText, onClick = onCancel)
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            ProvideTextStyle(value = MaterialTheme.typography.body1) {
                ProvideContentColor(color = ExtendedTheme.colors.text) {
                    content()
                }
            }
            TimePicker(
                currentTime = timeVal,
                onTimeChanged = {
                    timeVal = it
                    onValueChange?.invoke(it)
                },
                is24TimeFormat = is24TimeFormat,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AlertDialog(
    dialogState: DialogState,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    confirmText: String = stringResource(id = R.string.button_ok),
    title: @Composable (DialogScope.() -> Unit) = {},
    content: @Composable (DialogScope.() -> Unit) = {},
) {
    Dialog(
        modifier = modifier,
        dialogState = dialogState,
        onDismiss = onDismiss,
        title = title,
        buttons = {
            DialogPositiveButton(text = confirmText)
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            ProvideTextStyle(value = MaterialTheme.typography.body1) {
                ProvideContentColor(color = ExtendedTheme.colors.text) {
                    content()
                }
            }
        }
    }
}

@Composable
fun AlertDialog(
    dialogState: DialogState,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    title: @Composable (DialogScope.() -> Unit) = {},
    content: @Composable (DialogScope.() -> Unit) = {},
    buttons: @Composable (DialogScope.() -> Unit) = {},
) {
    Dialog(
        modifier = modifier,
        dialogState = dialogState,
        onDismiss = onDismiss,
        title = title,
        buttons = buttons,
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            ProvideTextStyle(value = MaterialTheme.typography.body1) {
                ProvideContentColor(color = ExtendedTheme.colors.text) {
                    content()
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    dialogState: DialogState,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    confirmText: String = stringResource(id = R.string.button_sure_default),
    cancelText: String = stringResource(id = R.string.button_cancel),
    title: @Composable (DialogScope.() -> Unit)? = null,
    content: @Composable (DialogScope.() -> Unit) = {},
) {
    Dialog(
        modifier = modifier,
        dialogState = dialogState,
        onDismiss = onDismiss,
        title = title,
        buttons = {
            DialogPositiveButton(text = confirmText, onClick = onConfirm)
            DialogNegativeButton(text = cancelText, onClick = onCancel)
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            ProvideTextStyle(value = MaterialTheme.typography.body1) {
                ProvideContentColor(color = ExtendedTheme.colors.text) {
                    content()
                }
            }
        }
    }
}

/**
 * 带输入框的对话框
 *
 * @param onValueChange 输入框内容变化时的回调，返回true表示允许变化，false表示不允许变化
 */
@Composable
fun PromptDialog(
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
    dialogState: DialogState = rememberDialogState(),
    initialValue: String = "",
    onValueChange: (newVal: String, oldVal: String) -> Boolean = { _, _ -> true },
    onCancel: (() -> Unit)? = null,
    confirmText: String = stringResource(id = R.string.button_sure_default),
    cancelText: String = stringResource(id = R.string.button_cancel),
    title: @Composable (DialogScope.() -> Unit)? = null,
    content: @Composable (DialogScope.() -> Unit) = {},
) {
    var textVal by remember { mutableStateOf(initialValue) }
    // 每次显示时重置输入框内容
    LaunchedEffect(dialogState.show) {
        textVal = initialValue
    }
    Dialog(
        modifier = modifier,
        dialogState = dialogState,
        onDismiss = onCancel,
        title = title,
        buttons = {
            DialogPositiveButton(text = confirmText, onClick = { onConfirm.invoke(textVal) })
            DialogNegativeButton(text = cancelText, onClick = onCancel)
        },
    ) {
        val focusRequester = remember { FocusRequester() }
        val softwareKeyboardController = LocalSoftwareKeyboardController.current
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.body1) {
                ProvideContentColor(color = ExtendedTheme.colors.text) {
                    content()
                }
            }
            OutlinedTextField(
                value = textVal,
                onValueChange = {
                    if (onValueChange.invoke(it, textVal)) textVal = it
                },
                maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { softwareKeyboardController?.hide() }
                ),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    cursorColor = ExtendedTheme.colors.emphasis,
                    focusedBorderColor = ExtendedTheme.colors.emphasis,
                    focusedLabelColor = ExtendedTheme.colors.emphasis
                )
            )
            // The keyboard comes up with the dialog: as soon as its window has the focus, which
            // it needs to take the keyboard, rather than after a fixed wait
            val windowInfo = LocalWindowInfo.current
            LaunchedEffect(focusRequester) {
                focusRequester.requestFocus()
                snapshotFlow { windowInfo.isWindowFocused }.first { it }
                softwareKeyboardController?.show()
            }
        }
    }
}

@Composable
fun BaseDialog(
    modifier: Modifier = Modifier,
    dialogState: DialogState = rememberDialogState(),
    onDismiss: (() -> Unit)? = null,
    direction: DirectionState = DirectionState.BOTTOM,
    cancelable: Boolean = true,
    cancelableOnTouchOutside: Boolean = true,
    imePadding: Boolean = true,
    content: @Composable (DialogScope.() -> Unit),
) {
    var showDialog by remember {
        mutableStateOf(false)
    }
    var isActiveClose by remember {
        mutableStateOf(false)
    }
    LaunchedEffect(dialogState.show) {
        if (dialogState.show) {
            showDialog = true
            isActiveClose = false
        } else {
            isActiveClose = true
        }
    }
    if (showDialog) {
        val dialogScope = DialogScope(
            onDismiss = {
                isActiveClose = true
            },
        )
        AnyPopDialog(
            isActiveClose = isActiveClose,
            onDismiss = {
                onDismiss?.invoke()
                dialogState.show = false
                showDialog = false
            },
            properties = AnyPopDialogProperties(
                direction = direction,
                dismissOnBackPress = cancelable,
                dismissOnClickOutside = cancelableOnTouchOutside,
                imePadding = imePadding
            )
        ) {
            ProvideContentColor(color = ExtendedTheme.colors.text) {
                dialogScope.content()
            }
        }
    }
}

/**
 * How far a row with a checkbox at its start sits in from a dialog's edges, so the box, past the
 * checkbox's own touch margin, lines up with the title.
 */
val CheckboxRowInset = 8.dp

/** Below this the window is a phone's, where dialogs rise from the bottom edge. */
private val CompactWindowMaxWidth = 600.dp

/** Wider, a dialog's few lines and buttons spread too far apart. */
private val MaxDialogWidth = 560.dp

@Composable
fun Dialog(
    modifier: Modifier = Modifier,
    dialogState: DialogState = rememberDialogState(),
    onDismiss: (() -> Unit)? = null,
    cancelable: Boolean = true,
    cancelableOnTouchOutside: Boolean = true,
    title: @Composable (DialogScope.() -> Unit)? = null,
    buttons: @Composable (DialogScope.() -> Unit) = {},
    content: @Composable (DialogScope.() -> Unit),
) {
    // A dialog covers the whole window, whatever pane opened it
    val compactWindow = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp() < CompactWindowMaxWidth
    }
    BaseDialog(
        modifier = modifier,
        dialogState = dialogState,
        onDismiss = onDismiss,
        direction = if (compactWindow) {
            DirectionState.BOTTOM
        } else {
            DirectionState.CENTER
        },
        cancelable = cancelable,
        cancelableOnTouchOutside = cancelableOnTouchOutside,
    ) {
        Column(
            modifier = modifier
                .padding(horizontal = DialogMargin)
                .padding(bottom = if (compactWindow) DialogMargin else 0.dp)
                .widthIn(max = MaxDialogWidth)
                .fillMaxWidth()
                .dialogSurface()
                .animateContentSize(Motion.settle()),
        ) {
            if (title != null) {
                Box(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp)) {
                    ProvideTextStyle(value = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)) {
                        title()
                    }
                }
            }
            Column(modifier = Modifier.padding(top = if (title != null) 8.dp else 24.dp)) {
                content()
            }
            DialogButtonRow {
                buttons()
            }
        }
    }
}

/** Space between a dialog and the window's edges, and above the navigation bar on phones. */
private val DialogMargin = 16.dp

/** The radius of dialogs; buttons [ButtonInset] in from it sit concentric. */
private val DialogShape = SmoothCornerShape(Radius.xl)

/** How far buttons sit from the dialog's edges. */
private val ButtonInset = 14.dp

/** A dialog's panel (design-system.md §2.5): the temporary layer, opaque in its own window. */
@Composable
private fun Modifier.dialogSurface(): Modifier = temporarySurface(DialogShape)

/** The temporary layer's opaque panel in [shape], as a dialog's, for panels like one shown on a page. */
@Composable
fun Modifier.temporarySurface(shape: Shape): Modifier {
    val colors = ExtendedTheme.colors
    val night = colors.isNightMode
    return this
        .shadow(
            elevation = if (night) 32.dp else 24.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (night) 0.16f else 0.14f),
            spotColor = Color.Black.copy(alpha = if (night) 0.26f else 0.22f),
        )
        // Filled in its shape rather than clipped to it: some GPUs anti-alias a clip's edge
        // coarsely or not at all. What is inside is cut by a mask.
        .background(colors.sheet, shape)
        .border(0.5.dp, colors.glassEdge, shape)
        .maskTo(shape)
}

/**
 * Lays out a dialog's buttons: two short ones side by side, cancel on the left and confirm on the
 * right (they are given confirm first); otherwise one per line, as given.
 */
@Composable
private fun DialogButtonRow(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(0, 0) {}
        val inset = ButtonInset.roundToPx()
        val top = 6.dp.roundToPx()
        val gap = 10.dp.roundToPx()
        val width = constraints.maxWidth - inset * 2
        val half = (width - gap) / 2
        val sideBySide = measurables.size == 2 && measurables.all { it.maxIntrinsicWidth(Int.MAX_VALUE) <= half }
        if (sideBySide) {
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(half)) }
            val height = placeables.maxOf { it.height }
            layout(constraints.maxWidth, inset * 2 + top + height) {
                placeables[1].placeRelative(inset, inset + top)
                placeables[0].placeRelative(inset + half + gap, inset + top)
            }
        } else {
            val lineGap = 8.dp.roundToPx()
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(width)) }
            val height = placeables.sumOf { it.height } + lineGap * (placeables.size - 1)
            layout(constraints.maxWidth, inset * 2 + top + height) {
                var y = inset + top
                placeables.forEach {
                    it.placeRelative(inset, y)
                    y += it.height + lineGap
                }
            }
        }
    }
}

@Composable
fun rememberDialogState(): DialogState {
    return rememberSaveable(saver = DialogState.Saver) {
        DialogState()
    }
}

@Stable
class DialogState private constructor(
    show: Boolean
) {
    constructor() : this(show = false)

    var show by mutableStateOf(show)

    fun show() {
        show = true
    }

    companion object {
        val Saver: Saver<DialogState, *> = listSaver(
            save = {
                listOf<Any>(
                    it.show,
                )
            },
            restore = {
                DialogState(
                    show = it[0] as Boolean
                )
            }
        )
    }
}

class DialogScope(
    private val onDismiss: () -> Unit,
) {
    fun dismiss() {
        onDismiss()
    }
}
