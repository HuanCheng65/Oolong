package oolong.client.ui.settings.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import oolong.client.navigation.Key
import oolong.client.navigation.LocalNavigator
import oolong.client.ui.components.LocalAccount
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.settings.components.prefs.GroupHeader
import oolong.client.ui.settings.components.prefs.PrefsScope
import oolong.client.ui.settings.components.prefs.PrefsScreen
import oolong.core.designsystem.theme.Motion
import kotlin.math.roundToInt

/** [page] as a page of its own: its title large over its groups, and a back button. */
@Composable
fun <S> SettingsScreen(page: SettingsPage<S>) {
    val navigator = LocalNavigator.current
    GlassScaffold(
        largeTitle = true,
        backgroundColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { GlassTopBarTitle(text = stringResource(id = page.title)) },
                navigationIcon = { BackNavigationIcon(onBackPressed = { navigator.navigateUp() }) },
            )
        },
    ) { padding ->
        SettingsContent(page, contentPadding = padding)
    }
}

/**
 * [page]'s groups, for a page that draws its own frame around them. [header] goes above them, as
 * a search field; [replacement], when given, is shown instead of them, as what a search found.
 */
@Composable
fun <S> SettingsContent(
    page: SettingsPage<S>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: (@Composable () -> Unit)? = null,
    replacement: (PrefsScope.() -> Unit)? = null,
) {
    val state = page.rememberState()
    page.Effects(state)
    val environment = rememberSettingsEnvironment()
    // Which rows show now, read here: the lists of rows are built outside composition
    val hidden = buildSet {
        page.groups.forEach { group ->
            group.items.forEach { item -> if (item is SettingsRow && item.shown?.invoke(state) == false) add(item) }
        }
    }
    val scroll = rememberScrollState()
    val highlight = remember(scroll) { RowHighlight(page.key, scroll) }
    highlight.topPadding = with(LocalDensity.current) { contentPadding.calculateTopPadding().toPx() }
    // A highlighted row that doesn't show now has nothing to light up
    LaunchedEffect(page.key) {
        val target = SettingsHighlight.target ?: return@LaunchedEffect
        if (target.first == page.key && page.groups.none { group -> group.items.any { it is SettingsRow && it.title == target.second && it !in hidden } }) {
            SettingsHighlight.target = null
        }
    }
    PrefsScreen(
        dividerThickness = 0.dp,
        modifier = modifier.fillMaxSize().onGloballyPositioned { highlight.viewportTop = it.positionInRoot().y },
        contentPadding = contentPadding,
        scrollState = scroll,
    ) {
        if (header != null) prefsGroup(header = { header() }) {}
        if (replacement != null) {
            replacement()
        } else {
            page.groups.forEach { group ->
                if (group.available.isMet(environment)) group(group, state, environment, hidden, highlight)
            }
        }
    }
}

@Composable
fun rememberSettingsEnvironment() = SettingsEnvironment(LocalContext.current, signedIn = LocalAccount.current != null)

private fun <S> PrefsScope.group(
    group: SettingsGroup<S>,
    state: S,
    environment: SettingsEnvironment,
    hidden: Set<SettingsRow<S>>,
    highlight: RowHighlight,
) {
    val rows: PrefsScope.() -> Unit = {
        group.items.filterIsInstance<SettingsRow<S>>().forEach { row ->
            // One shown only under a condition opens and closes in place as that changes
            if (row.available.isMet(environment)) {
                prefsItem(key = row, shown = row !in hidden) { Box(modifier = highlight.of(row.title)) { row.content(state, Modifier) } }
            }
        }
    }
    when (val header = group.header) {
        null -> rows()
        is SettingsHeader.Title -> prefsGroup(header = { GroupHeader(stringResource(id = header.text)) }, items = rows)
        is SettingsHeader.Gap -> prefsGroup(header = { Spacer(modifier = Modifier.height(header.height)) }, items = rows)
        is SettingsHeader.Custom -> prefsGroup(header = { header.content(state) }, items = rows)
    }
    // Notes go under the group's block and the gap after it
    group.items.filterIsInstance<SettingsFooter<S>>().forEach { footer -> prefsFooter { footer.text(state) } }
}

/**
 * Brings the row [SettingsHighlight] names on this page into view, a little below the bar, and
 * once it has stopped there lights it up twice.
 */
private class RowHighlight(private val page: Key, private val scroll: ScrollState) {
    var viewportTop = 0f
    var topPadding = 0f

    @Composable
    fun of(@StringRes title: Int?): Modifier {
        val target = SettingsHighlight.target
        if (title == null || target == null || target != page to title) return Modifier
        val glow = remember { Animatable(0f) }
        val tint = ExtendedTheme.colors.text
        val gap = with(LocalDensity.current) { HighlightGap.toPx() }
        var top by remember { mutableStateOf<Float?>(null) }
        LaunchedEffect(top) {
            val rowTop = top ?: return@LaunchedEffect
            scroll.animateScrollTo((scroll.value + rowTop - viewportTop - topPadding - gap).roundToInt().coerceAtLeast(0), Motion.settle())
            repeat(2) {
                glow.animateTo(1f, Motion.settle(Motion.ProgressThreshold))
                glow.animateTo(0f, Motion.dismiss(Motion.ProgressThreshold))
            }
            SettingsHighlight.target = null
        }
        return Modifier
            .onGloballyPositioned { if (top == null) top = it.positionInRoot().y }
            .drawBehind { drawRect(tint.copy(alpha = HighlightAlpha * glow.value)) }
    }
}

private val HighlightGap = 16.dp
private const val HighlightAlpha = 0.1f
