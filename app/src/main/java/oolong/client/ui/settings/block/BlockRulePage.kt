package oolong.client.ui.settings.block

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import oolong.client.R
import oolong.client.model.BlockRule
import oolong.client.model.BlockRule.Condition
import oolong.client.model.FlatCondition
import oolong.client.model.flat
import oolong.client.navigation.BlockRuleKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.Button
import oolong.client.ui.designsystem.ConfirmDialog
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.LocalContextMenus
import oolong.client.ui.designsystem.Stepper
import oolong.client.ui.designsystem.Switch
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.designsystem.verticalScrollUnderBar
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.Spacing
import oolong.core.designsystem.theme.TextStyles
import oolong.core.designsystem.theme.pressScale

@Composable
fun BlockRulePage(
    key: BlockRuleKey,
    navigator: Navigator = LocalNavigator.current,
    viewModel: BlockRuleViewModel = hiltViewModel<BlockRuleViewModel, BlockRuleViewModel.Factory> { it.create(key) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.closed.collect { navigator.navigateUp() } }
    BlockRuleScreen(
        state = state,
        onBack = navigator::navigateUp,
        onEdit = viewModel::edit,
        onJoin = viewModel::setJoin,
        onTest = viewModel::setTest,
        onAddTest = viewModel::addTest,
        onRemoveTest = viewModel::removeTest,
        onExpiry = viewModel::setExpiry,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

/**
 * A rule: the rule as one sentence whose underlined parts open their choices in a menu, its tests
 * one per row, and whether it is on.
 */
@Composable
fun BlockRuleScreen(
    state: BlockRuleState,
    onBack: () -> Unit,
    onEdit: ((BlockRule) -> BlockRule) -> Unit,
    onJoin: (Boolean) -> Unit,
    onTest: (Int, FlatCondition.Test) -> Unit,
    onAddTest: (ConditionField) -> Unit,
    onRemoveTest: (Int) -> Unit,
    onExpiry: (Int?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = ExtendedTheme.colors
    val deleteDialog = rememberDialogState()
    ConfirmDialog(
        dialogState = deleteDialog,
        onConfirm = onDelete,
        confirmText = stringResource(id = R.string.title_delete),
        title = { Text(text = stringResource(id = R.string.title_delete_rule)) },
    )
    GlassScaffold(
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = if (state.isNew) R.string.title_new_rule else R.string.title_edit_rule)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = onBack) },
                actionsInCapsule = false,
                actions = {
                    Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.padding(end = Spacing.xs)) {
                        Text(text = stringResource(id = R.string.button_save), style = TextStyles.label)
                    }
                },
            )
        },
    ) { padding ->
        val draft = state.draft ?: return@GlassScaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollUnderBar()
                .padding(padding)
                .padding(bottom = Spacing.xxl),
        ) {
            GroupTitle(stringResource(id = R.string.rule_field_rule))
            SentenceCard(draft, state.now, onEdit, onJoin, onExpiry)
            val conditions = state.conditions
            if (conditions == null) {
                Text(
                    text = stringResource(id = R.string.rule_nested),
                    style = TextStyles.footnote,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = Spacing.xs),
                )
            } else {
                GroupTitle(stringResource(id = R.string.rule_field_conditions))
                ConditionList(conditions, onTest, onRemoveTest)
                AddCondition(onAddTest)
            }
            Spacer(modifier = Modifier.height(Spacing.xl))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = Spacing.md)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .background(colors.chip, Corners.md)
                    .toggleable(
                        value = draft.enabled,
                        interactionSource = null,
                        indication = PressFeedback.overlay(Corners.md),
                        role = Role.Switch,
                    ) { checked -> onEdit { it.copy(enabled = checked) } }
                    .padding(horizontal = Spacing.md),
            ) {
                Text(text = stringResource(id = R.string.rule_enabled), style = TextStyles.bodyCompact, color = colors.text, modifier = Modifier.weight(1f))
                Switch(
                    checked = draft.enabled,
                    onCheckedChange = { checked -> onEdit { it.copy(enabled = checked) } },
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            if (!state.isNew) {
                // A destructive action at the end of its block, red, asking first
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = Spacing.md)
                        .padding(top = Spacing.md)
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .background(colors.chip, Corners.md)
                        .clickable(interactionSource = null, indication = PressFeedback.overlay(Corners.md)) { deleteDialog.show() }
                        .padding(horizontal = Spacing.md),
                ) {
                    Text(text = stringResource(id = R.string.rule_delete), style = TextStyles.bodyCompact, color = MaterialTheme.colors.error)
                }
            }
        }
    }
}

/** A group's small title, as the settings pages have them. */
@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        style = TextStyles.caption,
        color = ExtendedTheme.colors.textSecondary,
        modifier = Modifier
            .padding(start = 28.dp, end = 28.dp, top = Spacing.lg, bottom = Spacing.xs)
            .semantics { heading() },
    )
}

/** The tests in one group, a row each: what it looks at, how it judges, the value, and removing it. */
@Composable
private fun ConditionList(conditions: FlatCondition, onTest: (Int, FlatCondition.Test) -> Unit, onRemove: (Int) -> Unit) {
    val colors = ExtendedTheme.colors
    // The block grows and shrinks as tests are added and removed, its corners kept; with none it
    // has no height
    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.md)
            .fillMaxWidth()
            .animateContentSize(Motion.settle(IntSize.VisibilityThreshold))
            .background(colors.chip, Corners.md),
    ) {
        conditions.tests.forEachIndexed { index, test ->
            if (index > 0) Box(modifier = Modifier.padding(start = Spacing.sm).fillMaxWidth().height(1.dp).background(colors.divider))
            ConditionRow(test, onChange = { onTest(index, it) }, onRemove = { onRemove(index) })
        }
    }
}

@Composable
private fun ConditionRow(test: FlatCondition.Test, onChange: (FlatCondition.Test) -> Unit, onRemove: () -> Unit) {
    val colors = ExtendedTheme.colors
    val menus = LocalContextMenus.current
    val condition = test.condition
    val field = condition.field
    val op = condition.op
    val fieldItems = fieldMenuItems(selected = field) { chosen ->
        if (chosen != field) onChange(FlatCondition.Test(conditionOf(chosen)))
    }
    val opLabels = field.ops.associateWith { opName(it, negated = false) to opName(it, negated = true) }
    val kindLabels = ContentKinds.associateWith { kindName(it) }
    Column {
        ConditionRowLayout(
            valueFills = op.typed || field == ConditionField.Length,
            field = {
                Pick(text = fieldName(field)) { anchor -> menus?.show(items = fieldItems, anchor = anchor) }
            },
            op = {
                Pick(text = opName(op, test.negated), color = if (test.negated) MaterialTheme.colors.error else colors.text) { anchor ->
                    menus?.show(
                        items = field.ops.flatMapIndexed { group, choice ->
                            val (yes, no) = opLabels.getValue(choice)
                            listOf(false to yes, true to no).mapIndexed { i, (negated, label) ->
                                ContextMenuItem(
                                    icon = null,
                                    label = label,
                                    startsGroup = group > 0 && i == 0,
                                    selected = choice == op && negated == test.negated,
                                ) { onChange(FlatCondition.Test(condition.withOp(choice), negated)) }
                            }
                        },
                        anchor = anchor,
                    )
                }
            },
            value = when {
                op.typed -> {
                    {
                        ValueInput(
                            value = condition.typedValue,
                            hint = stringResource(id = hintOf(condition)),
                            regex = op == ConditionOp.Matches,
                        ) { onChange(test.copy(condition = condition.withTypedValue(it))) }
                    }
                }

                condition is Condition.LevelBelow -> {
                    { LevelStepper(condition.level) { onChange(test.copy(condition = Condition.LevelBelow(it))) } }
                }

                condition is Condition.ShorterThan -> {
                    { LengthInput(condition.length) { onChange(test.copy(condition = Condition.ShorterThan(it))) } }
                }

                condition is Condition.Has || condition is Condition.Only -> {
                    {
                        val kind = (condition as? Condition.Has)?.kind ?: (condition as Condition.Only).kind
                        Pick(text = kindLabels.getValue(kind)) { anchor ->
                            menus?.show(
                                items = kindLabels.map { (choice, label) ->
                                    ContextMenuItem(icon = null, label = label, selected = choice == kind) {
                                        onChange(test.copy(condition = if (condition is Condition.Has) Condition.Has(choice) else Condition.Only(choice)))
                                    }
                                },
                                anchor = anchor,
                            )
                        }
                    }
                }

                else -> null
            },
            remove = {
                val press = remember { MutableInteractionSource() }
                Icon(
                    Symbols.Close,
                    contentDescription = stringResource(id = R.string.rule_condition_remove),
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .pressScale(press)
                        .size(32.dp)
                        .clickable(interactionSource = press, indication = PressFeedback.overlay(Corners.full), onClick = onRemove)
                        .padding(8.dp),
                )
            },
        )
        val pattern = when (condition) {
            is Condition.Text -> condition.pattern
            is Condition.AuthorName -> condition.pattern
            is Condition.ForumName -> condition.pattern
            else -> null
        }
        if (pattern != null && !pattern.isValid) {
            Text(
                text = stringResource(id = R.string.rule_regex_invalid),
                style = TextStyles.footnote,
                color = MaterialTheme.colors.error,
                modifier = Modifier.padding(start = Spacing.sm, end = Spacing.sm, bottom = Spacing.xs),
            )
        }
    }
}

/**
 * A condition row: [field] and [op] first, [remove] at the end, and the [value] between them. A
 * value that [valueFills] the room left takes a line of its own when that room is too narrow.
 */
@Composable
private fun ConditionRowLayout(
    valueFills: Boolean,
    field: @Composable () -> Unit,
    op: @Composable () -> Unit,
    value: (@Composable () -> Unit)?,
    remove: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(field, op, value ?: {}, remove),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = Spacing.xxs, end = Spacing.xxs, top = 6.dp, bottom = 6.dp),
    ) { (fields, ops, values, removes), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = Spacing.xxs.roundToPx()
        val fieldPlaceable = fields.first().measure(loose)
        val opPlaceable = ops.first().measure(loose)
        val removePlaceable = removes.first().measure(loose)
        val width = constraints.maxWidth
        val left = fieldPlaceable.width + opPlaceable.width + gap
        val room = width - left - removePlaceable.width - gap * 2
        val valueMeasurable = values.firstOrNull()
        val inline = valueMeasurable == null ||
            if (valueFills) room >= MinValueWidth.roundToPx() else valueMeasurable.maxIntrinsicWidth(loose.maxHeight) <= room
        val valuePlaceable = valueMeasurable?.measure(
            when {
                !inline -> Constraints.fixedWidth(width)
                valueFills -> Constraints.fixedWidth(room.coerceAtLeast(0))
                else -> loose.copy(maxWidth = room.coerceAtLeast(0))
            },
        )
        val firstLine = maxOf(fieldPlaceable.height, opPlaceable.height, removePlaceable.height, if (inline) valuePlaceable?.height ?: 0 else 0)
        val height = maxOf(
            if (inline) firstLine else firstLine + gap + (valuePlaceable?.height ?: 0),
            constraints.minHeight,
        )
        val lineTop = if (inline) (height - firstLine) / 2 else 0
        layout(width, height) {
            fun center(p: androidx.compose.ui.layout.Placeable) = lineTop + (firstLine - p.height) / 2
            fieldPlaceable.place(0, center(fieldPlaceable))
            opPlaceable.place(fieldPlaceable.width + gap, center(opPlaceable))
            removePlaceable.place(width - removePlaceable.width, center(removePlaceable))
            valuePlaceable?.let {
                if (inline) {
                    // Values that don't fill the room sit at its end, next to removing the row
                    val x = if (valueFills) left + gap else width - removePlaceable.width - gap - it.width
                    it.place(x, center(it))
                } else {
                    // Across the row, as far from the card's sides as the row is, so its corners follow the card's
                    it.place(0, firstLine + gap)
                }
            }
        }
    }
}

private val MinValueWidth = 140.dp

/** A choice shown as its value with ⇕, opening its menu from where it is. */
@Composable
private fun Pick(text: String, color: Color = ExtendedTheme.colors.text, onClick: (anchor: Rect) -> Unit) {
    val colors = ExtendedTheme.colors
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .clickable(interactionSource = null, indication = PressFeedback.overlay(Corners.sm)) { onClick(bounds) }
            .padding(horizontal = Spacing.xs),
    ) {
        Text(text = text, style = TextStyles.bodyCompact, color = color, maxLines = 1)
        Icon(Symbols.UnfoldMore, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.padding(start = 2.dp).size(16.dp))
    }
}

/** A value typed in, on the page's own colour; a pattern sits between slashes. */
@Composable
private fun ValueInput(value: String, hint: String, regex: Boolean, onChange: (String) -> Unit) {
    val colors = ExtendedTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .background(colors.background, Corners.sm)
            .padding(horizontal = 10.dp),
    ) {
        if (regex) Slash()
        TextInput(value = value, hint = hint, monospace = regex, modifier = Modifier.weight(1f), onChange = onChange)
        if (regex) Slash()
    }
}

@Composable
private fun Slash() {
    Text(text = "/", style = TextStyles.bodyCompact.copy(fontFamily = FontFamily.Monospace), color = ExtendedTheme.colors.textDisabled)
}

@Composable
private fun LevelStepper(level: Int, onChange: (Int) -> Unit) = Stepper(
    value = level,
    range = LevelRange,
    text = stringResource(id = R.string.rule_level, level),
    down = stringResource(id = R.string.rule_level_down),
    up = stringResource(id = R.string.rule_level_up),
    onChange = onChange,
)

@Composable
private fun LengthInput(length: Int, onChange: (Int) -> Unit) {
    val colors = ExtendedTheme.colors
    // The number as typed, so it can be cleared before typing another
    var text by remember { mutableStateOf(length.toString()) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .background(colors.background, Corners.sm)
            .padding(horizontal = 10.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = text,
                onValueChange = { typed ->
                    val digits = typed.filter(Char::isDigit).take(4)
                    text = digits
                    digits.toIntOrNull()?.takeIf { it > 0 }?.let(onChange)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyles.bodyCompact.copy(color = colors.text, fontFeatureSettings = "tnum"),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(text = stringResource(id = R.string.rule_length_unit), style = TextStyles.bodyCompact, color = colors.textSecondary)
    }
}

/** Adding a test, from a menu of what it can look at. */
@Composable
private fun AddCondition(onAdd: (ConditionField) -> Unit) {
    val colors = ExtendedTheme.colors
    val menus = LocalContextMenus.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val items = fieldMenuItems(selected = null, onPick = onAdd)
    // A secondary button
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = Spacing.md)
            .padding(top = Spacing.sm)
            .heightIn(min = 36.dp)
            .background(colors.chip, Corners.full)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .clickable(interactionSource = null, indication = PressFeedback.overlay(Corners.full)) {
                // Out of the whole button, which keeps its icon
                menus?.show(items = items, anchor = bounds)
            }
            .padding(start = Spacing.sm, end = Spacing.md),
    ) {
        Icon(Symbols.Add, contentDescription = null, tint = colors.text, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(Spacing.xxs))
        Text(text = stringResource(id = R.string.rule_add_condition), style = TextStyles.label, color = colors.text)
    }
}

/** What a row can look at, in groups that open in place, so the menu stays short. */
@Composable
private fun fieldMenuItems(selected: ConditionField?, onPick: (ConditionField) -> Unit): List<ContextMenuItem> {
    @Composable
    fun item(field: ConditionField, label: Int) =
        ContextMenuItem(icon = null, label = stringResource(id = label), selected = field == selected) { onPick(field) }

    @Composable
    fun group(label: Int, vararg children: ContextMenuItem) =
        ContextMenuItem(icon = null, label = stringResource(id = label), selected = children.any { it.selected }, children = children.toList())

    return listOf(
        group(
            R.string.rule_group_text,
            item(ConditionField.TitleOrBody, R.string.rule_field_title_or_body),
            item(ConditionField.Title, R.string.rule_field_title),
            item(ConditionField.Body, R.string.rule_field_body),
        ),
        group(
            R.string.rule_group_author,
            item(ConditionField.Author, R.string.rule_field_user),
            item(ConditionField.Level, R.string.rule_field_level_short),
            item(ConditionField.ThreadAuthor, R.string.rule_field_identity),
            item(ConditionField.Ip, R.string.rule_field_ip),
        ),
        item(ConditionField.Forum, R.string.rule_field_forum),
        group(
            R.string.rule_group_content,
            item(ConditionField.Content, R.string.rule_field_kind),
            item(ConditionField.Length, R.string.rule_field_length),
        ),
    )
}

@Composable
private fun fieldName(field: ConditionField): String = stringResource(
    when (field) {
        ConditionField.TitleOrBody -> R.string.rule_field_title_or_body
        ConditionField.Title -> R.string.rule_field_title
        ConditionField.Body -> R.string.rule_field_body
        ConditionField.Author -> R.string.rule_field_author
        ConditionField.Level -> R.string.rule_field_level
        ConditionField.ThreadAuthor -> R.string.rule_field_thread_author
        ConditionField.Ip -> R.string.rule_field_ip
        ConditionField.Forum -> R.string.rule_field_forum
        ConditionField.Content -> R.string.rule_field_content
        ConditionField.Length -> R.string.rule_field_length
    },
)

@Composable
private fun opName(op: ConditionOp, negated: Boolean): String = stringResource(
    when (op) {
        ConditionOp.Contains -> if (negated) R.string.rule_op_not_contains else R.string.rule_op_contains
        ConditionOp.Matches -> if (negated) R.string.rule_op_not_matches else R.string.rule_op_matches
        ConditionOp.Is -> if (negated) R.string.rule_op_is_not else R.string.rule_op_is
        ConditionOp.Below -> if (negated) R.string.rule_op_not_below else R.string.rule_op_below
        ConditionOp.IsThreadAuthor -> if (negated) R.string.rule_op_not_thread_author else R.string.rule_op_thread_author
        ConditionOp.Has -> if (negated) R.string.rule_op_has_not else R.string.rule_op_has
        ConditionOp.Only -> if (negated) R.string.rule_op_not_only else R.string.rule_op_only
    },
)

private fun hintOf(condition: Condition): Int = when {
    condition.op == ConditionOp.Matches -> R.string.rule_hint_regex
    condition is Condition.User -> R.string.rule_hint_user
    condition is Condition.AuthorName || condition is Condition.ForumName -> R.string.rule_hint_name
    condition is Condition.Forum -> R.string.rule_hint_forum
    condition is Condition.Ip -> R.string.rule_hint_ip
    else -> R.string.rule_hint_keyword
}

@Composable
private fun TextInput(
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    onChange: (String) -> Unit,
) {
    val colors = ExtendedTheme.colors
    val style = TextStyles.bodyCompact.copy(color = colors.text, fontFamily = if (monospace) FontFamily.Monospace else null)
    Box(modifier = modifier.padding(vertical = 7.dp)) {
        if (value.isEmpty()) Text(text = hint, style = style.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The rule as one sentence; its scope, action, joining and expiry open their choices where they are. */
@Composable
private fun SentenceCard(
    rule: BlockRule,
    now: Long,
    onEdit: ((BlockRule) -> BlockRule) -> Unit,
    onJoin: (Boolean) -> Unit,
    onExpiry: (Int?) -> Unit,
) {
    val colors = ExtendedTheme.colors
    val menus = LocalContextMenus.current
    val runs = ruleSentence(rule, now, full = true)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var origin by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scopes = BlockRule.Scope.entries.associateWith { scopeName(it) }
    val actions = BlockRule.Action.entries.associateWith {
        if (it == BlockRule.Action.Default) stringResource(id = R.string.rule_action_default_hint) else actionName(it)
    }
    val joins = listOf(true to stringResource(id = R.string.rule_all), false to stringResource(id = R.string.rule_any))
    val all = rule.condition.flat()?.all ?: true
    val expiries = listOf(
        null to stringResource(id = R.string.rule_forever),
        7 to stringResource(id = R.string.rule_expiry_7),
        30 to stringResource(id = R.string.rule_expiry_30),
    )

    fun open(part: RulePart, start: Int, end: Int) {
        menus ?: return
        // The menu grows out of the words tapped
        val box = layout?.getPathForRange(start, end)?.getBounds() ?: Rect.Zero
        val at = origin?.positionInWindow() ?: Offset.Zero
        val anchor = box.translate(at)
        val items = when (part) {
            RulePart.Scope -> scopes.map { (scope, label) ->
                ContextMenuItem(icon = null, label = label, selected = rule.scope == scope) { onEdit { it.copy(scope = scope) } }
            }

            RulePart.Action -> actions.map { (action, label) ->
                ContextMenuItem(icon = null, label = label, selected = rule.action == action) { onEdit { it.copy(action = action) } }
            }

            RulePart.Expiry -> expiries.map { (days, label) ->
                val chosen = days == null && rule.expiresAt == null
                ContextMenuItem(icon = null, label = label, selected = chosen) { onExpiry(days) }
            }

            RulePart.Join -> joins.map { (join, label) ->
                ContextMenuItem(icon = null, label = label, selected = join == all) { onJoin(join) }
            }

            RulePart.Value -> return
        }
        menus.show(items = items, anchor = anchor)
    }

    val text = buildAnnotatedString {
        runs.forEach { run ->
            val part = run.part
            when {
                part == null -> append(run.text)
                part == RulePart.Value -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(run.text) }
                else -> {
                    val style = SpanStyle(
                        color = if (run.isDefault) colors.textSecondary else colors.accent,
                        fontWeight = if (run.isDefault) FontWeight.Normal else FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                    )
                    val start = length
                    val end = start + run.text.length
                    withLink(LinkAnnotation.Clickable(part.name, TextLinkStyles(style)) { open(part, start, end) }) { append(run.text) }
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .padding(horizontal = Spacing.md)
            .fillMaxWidth()
            .background(colors.chip, Corners.md)
            .padding(horizontal = Spacing.md, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = TextStyles.body.copy(fontSize = 17.sp, lineHeight = 32.sp),
            color = colors.text,
            onTextLayout = { layout = it },
            modifier = Modifier.onGloballyPositioned { origin = it },
        )
    }
}
