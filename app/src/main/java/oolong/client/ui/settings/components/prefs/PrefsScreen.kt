package oolong.client.ui.settings.components.prefs

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import oolong.client.ui.designsystem.Container
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.maskTo
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.verticalScrollUnderBar
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.Radius
import oolong.core.designsystem.theme.SmoothCornerShape
import kotlin.math.roundToInt

/**
 * Main preference screen which holds [PrefsListItem]s
 *
 * @param modifier Modifier applied to the column holding the list of Prefs
 * @param contentPadding padding around the list, such as the bars of a [GlassScaffold]
 *
 */
@Composable
fun PrefsScreen(
    modifier: Modifier = Modifier,
    dividerThickness: Dp = 1.dp, // 0 for no divider
    dividerIndent: Dp = 0.dp, // indents on both sides
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollState: ScrollState = rememberScrollState(),
    content: PrefsScope.() -> Unit
) {
    val prefsScope = PrefsScopeImpl().apply(content)

    Container {
        // A plain column rather than a lazy list: settings pages are short, and with every row
        // laid out a search result can scroll to any of them. Long data lists don't belong here.
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScrollUnderBar(scrollState)
                .padding(contentPadding),
        ) {
            val items = prefsScope.prefsItems
            // How open each keyed setting is: 1 shown, 0 closed. One still closing keeps its
            // place in its block, so the block's corners don't move till it has gone
            val openness = remember { mutableMapOf<Any, Openness>() }
            items.forEach { item -> if (item.isSetting && item.key != null) openness.getOrPut(item.key) { Openness(item.shown) } }
            val present = items.map { item -> !item.isSetting || (item.key?.let { openness[it]?.present } ?: true) }
            items.forEachIndexed { index, item ->
                key(item.key ?: index) {
                    if (!item.isSetting) {
                        prefsScope.getPrefsItem(index)()
                    } else {
                        val open = item.key?.let { openness[it] }
                        if (open != null) LaunchedEffect(item.shown) { open.follow(item.shown) }
                        if (present[index]) {
                            SettingInGroup(items, present, index, dividerThickness, dividerIndent, open) { prefsScope.getPrefsItem(index)() }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The setting at [index] of [items]: settings next to each other form one group, set apart by
 * headers and gaps, and sit in one grey block.
 */
@Composable
private fun SettingInGroup(
    items: List<PrefsItem>,
    present: List<Boolean>,
    index: Int,
    dividerThickness: Dp,
    dividerIndent: Dp,
    openness: Openness?,
    content: @Composable () -> Unit,
) {
    // Its neighbours in the block are the settings that show, or are still closing; a closed
    // one between two others doesn't part them
    val closed = { i: Int -> items[i].isSetting && !present[i] }
    val before = (index - 1 downTo 0).firstOrNull { !closed(it) }
    val after = (index + 1..items.lastIndex).firstOrNull { !closed(it) }
    val first = before == null || !items[before].isSetting
    val last = after == null || !items[after].isSetting
    val shape = SmoothCornerShape(CardRadius, top = first, bottom = last)
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = if (first && index == 0) 8.dp else 0.dp)
            .background(ExtendedTheme.colors.chip, shape)
            .then(if (first || last) Modifier.maskTo(shape) else Modifier.clipToBounds())
            .padding(top = if (first) 4.dp else 0.dp, bottom = if (last) 4.dp else 0.dp)
            .then(if (openness != null) Modifier.opening(openness) else Modifier)
    ) {
        content()
    }
    if (dividerThickness != 0.dp && !last) {
        Divider(thickness = dividerThickness, indent = dividerIndent)
    }
}

private val CardRadius = Radius.md

/** How open a setting shown only under a condition is, on the design system's springs (§7.2). */
@Stable
private class Openness(shown: Boolean) {
    val progress = Animatable(if (shown) 1f else 0f, Motion.ProgressThreshold)

    /** Whether any of it is open: it holds its place in its block. */
    val present by derivedStateOf { progress.value > 0f }

    suspend fun follow(shown: Boolean) {
        progress.animateTo(if (shown) 1f else 0f, if (shown) Motion.settle(Motion.ProgressThreshold) else Motion.dismiss(Motion.ProgressThreshold))
    }
}

/** Takes [openness] of the row's height, and fades it in over the latter part of opening. */
private fun Modifier.opening(openness: Openness): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val f = openness.progress.value.coerceIn(0f, 1f)
        layout(placeable.width, (placeable.height * f).roundToInt()) { placeable.place(0, 0) }
    }
    .graphicsLayer { alpha = ((openness.progress.value - 0.3f) / 0.7f).coerceIn(0f, 1f) }
