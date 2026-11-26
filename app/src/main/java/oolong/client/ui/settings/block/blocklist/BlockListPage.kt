package oolong.client.ui.settings.block.blocklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.Text
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import oolong.client.R
import oolong.client.model.BlockRule
import oolong.client.model.tests
import oolong.client.navigation.BlockRuleKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.ui.components.relativeTime
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.Button
import oolong.client.ui.designsystem.ConfirmDialog
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.IconButton
import oolong.client.ui.designsystem.MyLazyColumn
import oolong.client.ui.designsystem.SegmentedControl
import oolong.client.ui.designsystem.maskTo
import oolong.client.ui.designsystem.motionItem
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.settings.block.LocalBlockShortcuts
import oolong.client.ui.settings.block.RulePart
import oolong.client.ui.settings.block.ruleSentence
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.Radius
import oolong.core.designsystem.theme.SmoothCornerShape
import oolong.core.designsystem.theme.Spacing
import oolong.core.designsystem.theme.TextStyles
import oolong.core.designsystem.theme.pressScale

@Composable
fun BlockListPage(
    navigator: Navigator = LocalNavigator.current,
    viewModel: BlockListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BlockListScreen(
        state = state,
        onBack = navigator::navigateUp,
        onOpen = { navigator.navigate(BlockRuleKey(it?.id ?: 0)) },
        onDelete = viewModel::delete,
        onRestore = viewModel::restore,
    )
}

/**
 * The rules, one sentence each, newest first, narrowed by kind and by what they match. Rules that
 * have done nothing for a long time can be cleared at once; any rule can be swiped away.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BlockListScreen(
    state: BlockListState,
    onBack: () -> Unit,
    onOpen: (BlockRule?) -> Unit,
    onDelete: (List<BlockRule>) -> Unit,
    onRestore: (List<BlockRule>) -> Unit,
) {
    val deletedOne = stringResource(id = R.string.toast_rule_deleted)
    val deletedMany = stringResource(id = R.string.toast_rules_deleted)
    val shortcuts = LocalBlockShortcuts.current
    var filter by rememberSaveable { mutableStateOf(RuleFilter.All) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = state.rules.filter { it.matches(filter) && it.matches(query) }
    val stale = state.stale

    fun delete(rules: List<BlockRule>) {
        onDelete(rules)
        shortcuts?.showUndo(if (rules.size == 1) deletedOne else deletedMany.format(rules.size)) { onRestore(rules) }
    }

    val cleanDialog = rememberDialogState()
    ConfirmDialog(
        dialogState = cleanDialog,
        onConfirm = { delete(stale) },
        confirmText = stringResource(id = R.string.title_delete),
        title = { Text(text = pluralStringResource(id = R.plurals.title_clean_up_rules, count = stale.size, stale.size)) },
    )

    GlassScaffold(
        largeTitle = true,
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = R.string.title_block_rules)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = onBack) },
                actions = {
                    IconButton(onClick = { onOpen(null) }) {
                        Icon(Symbols.Add, contentDescription = stringResource(id = R.string.title_new_rule))
                    }
                },
            )
        },
    ) { padding ->
        MyLazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "search") { SearchField(query, onChange = { query = it }) }
            item(key = "filter") {
                SegmentedControl(
                    options = RuleFilter.entries.map { stringResource(id = it.label) }.toImmutableList(),
                    selected = filter.ordinal,
                    onSelect = { filter = RuleFilter.entries[it] },
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs).fillMaxWidth(),
                )
            }
            if (stale.isNotEmpty() && filter == RuleFilter.All && query.isBlank()) {
                item(key = "stale") { Box(modifier = Modifier.motionItem(this)) { StaleBanner(stale.size, onClean = { cleanDialog.show() }) } }
            }
            if (shown.isEmpty() && !state.loading) {
                item(key = "empty") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.motionItem(this).fillMaxWidth().padding(vertical = 48.dp),
                    ) {
                        Text(
                            text = stringResource(id = if (state.rules.isEmpty()) R.string.rules_empty else R.string.rules_none_found),
                            style = TextStyles.label,
                            color = ExtendedTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        if (state.rules.isEmpty()) {
                            Button(onClick = { onOpen(null) }, modifier = Modifier.padding(top = Spacing.md)) {
                                Text(text = stringResource(id = R.string.title_new_rule), style = TextStyles.label)
                            }
                        }
                    }
                }
            }
            itemsIndexed(shown, key = { _, rule -> rule.id }) { index, rule ->
                val dismissState = rememberDismissState(
                    confirmStateChange = {
                        if (it == DismissValue.DismissedToStart) delete(listOf(rule))
                        it == DismissValue.DismissedToStart
                    },
                )
                val shape = when {
                    shown.size == 1 -> Corners.md
                    index == 0 -> SmoothCornerShape(Radius.md, bottom = false)
                    index == shown.lastIndex -> SmoothCornerShape(Radius.md, top = false)
                    else -> RectangleShape
                }
                // The first and last rows' rounded ends are masked: a clip to them is jagged on some GPUs
                Box(modifier = Modifier.motionItem(this).padding(horizontal = Spacing.md).then(if (shape == RectangleShape) Modifier.clipToBounds() else Modifier.maskTo(shape))) {
                    SwipeToDismiss(
                        state = dismissState,
                        directions = setOf(DismissDirection.EndToStart),
                        background = {
                            // Only while swiping, or it shows at the rounded corners
                            if (dismissState.dismissDirection != null) Box(
                                contentAlignment = Alignment.CenterEnd,
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.error)
                                    .padding(horizontal = 20.dp),
                            ) {
                                Text(text = stringResource(id = R.string.title_delete), color = Color.White, style = TextStyles.label)
                            }
                        },
                    ) {
                        RuleRow(rule, now = state.now, onClick = { onOpen(rule) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val colors = ExtendedTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(colors.inset, Corners.sm)
            .padding(horizontal = Spacing.sm),
    ) {
        Icon(Symbols.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(Spacing.xs))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) Text(text = stringResource(id = R.string.rules_search), style = TextStyles.bodyCompact, color = colors.textSecondary)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyles.bodyCompact.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val RuleFilter.label: Int
    get() = when (this) {
        RuleFilter.All -> R.string.rule_kind_all
        RuleFilter.User -> R.string.rule_kind_user
        RuleFilter.Keyword -> R.string.rule_kind_keyword
        RuleFilter.Forum -> R.string.rule_kind_forum
        RuleFilter.Other -> R.string.rule_kind_other
    }

@Composable
private fun StaleBanner(count: Int, onClean: () -> Unit) {
    val colors = ExtendedTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(colors.chip, Corners.md)
            .padding(start = Spacing.md, end = Spacing.xs),
    ) {
        Text(
            text = pluralStringResource(id = R.plurals.rules_stale, count = count, count),
            style = TextStyles.caption,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        // A text button: no background of its own
        val press = remember { MutableInteractionSource() }
        Text(
            text = stringResource(id = R.string.button_clean_up),
            style = TextStyles.label,
            color = colors.accent,
            modifier = Modifier
                .pressScale(press)
                .clickable(interactionSource = press, indication = PressFeedback.overlay(Corners.full), onClick = onClean)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun RuleRow(rule: BlockRule, now: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    val colors = ExtendedTheme.colors
    val runs = ruleSentence(rule, now)
    val sentence: AnnotatedString = buildAnnotatedString {
        runs.forEach { run ->
            if (run.part == RulePart.Value) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(run.text) }
            } else {
                append(run.text)
            }
        }
    }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .alpha(if (rule.enabled) 1f else 0.45f)
                .size(30.dp)
                .background(colors.card, Corners.sm),
        ) {
            Icon(rule.icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(17.dp))
        }
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sentence,
                    style = TextStyles.bodyCompact,
                    color = colors.text,
                    modifier = Modifier.weight(1f, fill = false).alpha(if (rule.enabled) 1f else 0.45f),
                )
                if (rule.action == BlockRule.Action.Allow) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.rule_exception),
                        style = TextStyles.micro,
                        color = colors.textSecondary,
                        modifier = Modifier.background(colors.card, Corners.xs).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                text = when {
                    !rule.enabled -> stringResource(id = R.string.rule_disabled)
                    rule.hits > 0 && rule.lastHitAt != null ->
                        pluralStringResource(id = R.plurals.rule_hits, count = rule.hits.toInt(), rule.hits, relativeTime(rule.lastHitAt))
                    else -> stringResource(id = R.string.rule_no_hits)
                },
                style = TextStyles.footnote,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private val BlockRule.icon: ImageVector
    get() = when (val test = condition.tests.singleOrNull()) {
        null -> Symbols.Layers
        is BlockRule.Condition.User, is BlockRule.Condition.AuthorName -> Symbols.Person
        is BlockRule.Condition.Text -> Symbols.TextFields
        is BlockRule.Condition.Forum, is BlockRule.Condition.ForumName -> Symbols.Forum
        is BlockRule.Condition.LevelBelow -> Symbols.BarChart
        else -> if (test is BlockRule.Condition.ShorterThan) Symbols.ShortText else Symbols.Category
    }
