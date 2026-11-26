package oolong.client.ui.designsystem

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Surface
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.contentColorFor
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import oolong.client.ui.designsystem.theme.Symbols
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Motion

/**
 * Class holding onto state needed for [ScrollableTabRow]
 */
private class ScrollableTabData(
    private val scrollState: ScrollState,
    private val coroutineScope: CoroutineScope,
) {
    private var selectedTab: Int? = null

    fun onLaidOut(
        density: Density,
        edgeOffset: Int,
        tabPositions: List<TabPosition>,
        selectedTab: Int,
    ) {
        // Animate if the new tab is different from the old tab, or this is called for the first
        // time (i.e selectedTab is `null`).
        if (this.selectedTab != selectedTab) {
            this.selectedTab = selectedTab
            tabPositions.getOrNull(selectedTab)?.let {
                // Scrolls to the tab with [tabPosition], trying to place it in the center of the
                // screen or as close to the center as possible.
                val calculatedOffset = it.calculateTabOffset(density, edgeOffset, tabPositions)
                if (scrollState.value != calculatedOffset) {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(
                            calculatedOffset,
                            animationSpec = ScrollableTabRowScrollSpec
                        )
                    }
                }
            }
        }
    }

    /**
     * @return the offset required to horizontally center the tab inside this TabRow.
     * If the tab is at the start / end, and there is not enough space to fully centre the tab, this
     * will just clamp to the min / max position given the max width.
     */
    private fun TabPosition.calculateTabOffset(
        density: Density,
        edgeOffset: Int,
        tabPositions: List<TabPosition>,
    ): Int = with(density) {
        val totalTabRowWidth = tabPositions.last().right.roundToPx() + edgeOffset
        val visibleWidth = totalTabRowWidth - scrollState.maxValue
        val tabOffset = left.roundToPx()
        val scrollerCenter = visibleWidth / 2
        val tabWidth = width.roundToPx()
        val centeredTabOffset = tabOffset - (scrollerCenter - tabWidth / 2)
        // How much space we have to scroll. If the visible width is <= to the total width, then
        // we have no space to scroll as everything is always visible.
        val availableSpace = (totalTabRowWidth - visibleWidth).coerceAtLeast(0)
        return centeredTabOffset.coerceIn(0, availableSpace)
    }
}

/**
 * [AnimationSpec] used when scrolling to a tab that is not fully visible.
 */
private val ScrollableTabRowScrollSpec: AnimationSpec<Float> = Motion.settle()

@Composable
@UiComposable
fun TabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colors.primarySurface,
    contentColor: Color = contentColorFor(backgroundColor),
    indicator: @Composable @UiComposable (tabPositions: List<TabPosition>) -> Unit = @Composable { tabPositions ->
        TabRowDefaults.Indicator(
            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex])
        )
    },
    divider: @Composable @UiComposable () -> Unit =
        @Composable {
            TabRowDefaults.Divider()
        },
    tabs: @Composable @UiComposable () -> Unit,
) {
    Surface(
        modifier = modifier.selectableGroup(),
        color = backgroundColor,
        contentColor = contentColor
    ) {
        SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
            val tabRowWidth = constraints.maxWidth
            val tabMeasurables = subcompose(TabSlots.Tabs, tabs)
            val tabCount = tabMeasurables.size
            val tabWidth = (tabRowWidth / tabCount)
            val tabPlaceables = tabMeasurables.map {
                it.measure(constraints.copy(minWidth = tabWidth, maxWidth = tabWidth))
            }

            val tabRowHeight = tabPlaceables.maxByOrNull { it.height }?.height ?: 0

            val tabPositions = List(tabCount) { index ->
                TabPosition(tabWidth.toDp() * index, tabWidth.toDp())
            }

            // Subcomposing while placing breaks layout inside the glass top bar, so the divider
            // and indicator are measured here rather than in the placement block.
            val dividerPlaceables = subcompose(TabSlots.Divider, divider).map {
                it.measure(constraints.copy(minHeight = 0))
            }
            val indicatorPlaceables = subcompose(TabSlots.Indicator) {
                indicator(tabPositions)
            }.map {
                it.measure(Constraints.fixed(tabRowWidth, tabRowHeight))
            }

            layout(tabRowWidth, tabRowHeight) {
                tabPlaceables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(index * tabWidth, 0)
                }
                dividerPlaceables.forEach {
                    it.placeRelative(0, tabRowHeight - it.height)
                }
                indicatorPlaceables.forEach {
                    it.placeRelative(0, 0)
                }
            }
        }
    }
}

/**
 * <a href="https://material.io/components/tabs#scrollable-tabs" class="external" target="_blank">Material Design scrollable tabs</a>.
 *
 * When a set of tabs cannot fit on screen, use scrollable tabs. Scrollable tabs can use longer text
 * labels and a larger number of tabs. They are best used for browsing on touch interfaces.
 *
 * ![Scrollable tabs image](https://developer.android.com/images/reference/androidx/compose/material/scrollable-tabs.png)
 *
 * A ScrollableTabRow contains a row of [Tab]s, and displays an indicator underneath the currently
 * selected tab. A ScrollableTabRow places its tabs offset from the starting edge, and allows
 * scrolling to tabs that are placed off screen. For a fixed tab row that does not allow
 * scrolling, and evenly places its tabs, see [TabRow].
 *
 * @param selectedTabIndex the index of the currently selected tab
 * @param modifier optional [Modifier] for this ScrollableTabRow
 * @param backgroundColor The background color for the ScrollableTabRow. Use [Color.Transparent] to
 * have no color.
 * @param contentColor The preferred content color provided by this ScrollableTabRow to its
 * children. Defaults to either the matching content color for [backgroundColor], or if
 * [backgroundColor] is not a color from the theme, this will keep the same value set above this
 * ScrollableTabRow.
 * @param edgePadding the padding between the starting and ending edge of ScrollableTabRow, and
 * the tabs inside the ScrollableTabRow. This padding helps inform the user that this tab row can
 * be scrolled, unlike a [TabRow].
 * @param indicator the indicator that represents which tab is currently selected. By default this
 * will be a [TabRowDefaults.Indicator], using a [TabRowDefaults.tabIndicatorOffset]
 * modifier to animate its position. Note that this indicator will be forced to fill up the
 * entire ScrollableTabRow, so you should use [TabRowDefaults.tabIndicatorOffset] or similar to
 * animate the actual drawn indicator inside this space, and provide an offset from the start.
 * @param divider the divider displayed at the bottom of the ScrollableTabRow. This provides a layer
 * of separation between the ScrollableTabRow and the content displayed underneath.
 * @param tabs the tabs inside this ScrollableTabRow. Typically this will be multiple [Tab]s. Each
 * element inside this lambda will be measured and placed evenly across the TabRow, each taking
 * up equal space.
 */
@Composable
@UiComposable
fun ScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colors.primarySurface,
    contentColor: Color = contentColorFor(backgroundColor),
    edgePadding: Dp = TabRowDefaults.ScrollableTabRowPadding,
    indicator: @Composable @UiComposable (tabPositions: List<TabPosition>) -> Unit = @Composable { tabPositions ->
        TabRowDefaults.Indicator(
            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex])
        )
    },
    divider: @Composable @UiComposable () -> Unit =
        @Composable {
            TabRowDefaults.Divider()
        },
    tabs: @Composable @UiComposable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = backgroundColor,
        contentColor = contentColor
    ) {
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        val scrollableTabData = remember(scrollState, coroutineScope) {
            ScrollableTabData(
                scrollState = scrollState,
                coroutineScope = coroutineScope
            )
        }
        SubcomposeLayout(
            Modifier
                .fillMaxWidth()
                .wrapContentSize(align = Alignment.CenterStart)
                .horizontalScroll(scrollState)
                .selectableGroup()
                .clipToBounds()
        ) { constraints ->
            val padding = edgePadding.roundToPx()
            val tabConstraints = constraints.copy()

            val tabPlaceables = subcompose(TabSlots.Tabs, tabs)
                .map { it.measure(tabConstraints) }

            var layoutWidth = padding * 2
            var layoutHeight = 0
            tabPlaceables.forEach {
                layoutWidth += it.width
                layoutHeight = maxOf(layoutHeight, it.height)
            }

            val tabPositions = mutableListOf<TabPosition>()
            var tabLeft = padding
            tabPlaceables.forEach {
                tabPositions.add(TabPosition(left = tabLeft.toDp(), width = it.width.toDp()))
                tabLeft += it.width
            }

            // The divider is measured with its own height, and width equal to the total width of
            // the tab row. Subcomposing while placing breaks layout inside the glass top bar, so
            // the divider and indicator are measured here rather than in the placement block.
            val dividerPlaceables = subcompose(TabSlots.Divider, divider).map {
                it.measure(
                    constraints.copy(
                        minHeight = 0,
                        minWidth = layoutWidth,
                        maxWidth = layoutWidth
                    )
                )
            }

            // The indicator container is measured to fill the entire space occupied by the tab row.
            val indicatorPlaceables = subcompose(TabSlots.Indicator) {
                indicator(tabPositions)
            }.map {
                it.measure(Constraints.fixed(layoutWidth, layoutHeight))
            }

            // Position the children.
            layout(layoutWidth, layoutHeight) {
                var left = padding
                tabPlaceables.forEach {
                    it.placeRelative(left, 0)
                    left += it.width
                }

                // The divider goes on top of the tabs, and the indicator on top of the divider.
                dividerPlaceables.forEach {
                    it.placeRelative(0, layoutHeight - it.height)
                }
                indicatorPlaceables.forEach {
                    it.placeRelative(0, 0)
                }

                scrollableTabData.onLaidOut(
                    density = this@SubcomposeLayout,
                    edgeOffset = padding,
                    tabPositions = tabPositions,
                    selectedTab = selectedTabIndex
                )
            }
        }
    }
}

/**
 * [Modifier] that takes up all the available width inside the [TabRow], and then animates
 * the offset of the indicator it is applied to, depending on the [currentTabPosition].
 *
 * @param currentTabPosition [TabPosition] of the currently selected tab. This is used to
 * calculate the offset of the indicator this modifier is applied to, as well as its width.
 */
fun Modifier.tabIndicatorOffset(
    currentTabPosition: TabPosition,
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "tabIndicatorOffset"
        value = currentTabPosition
    }
) {
    val currentTabWidth by animateDpAsState(
        targetValue = currentTabPosition.width,
        animationSpec = Motion.snap()
    )
    val indicatorOffset by animateDpAsState(
        targetValue = currentTabPosition.left,
        animationSpec = Motion.snap()
    )
    fillMaxWidth()
        .wrapContentSize(Alignment.BottomStart)
        .offset(x = indicatorOffset)
        .width(currentTabWidth)
}

/**
 * Data class that contains information about a tab's position on screen, used for calculating
 * where to place the indicator that shows which tab is selected.
 *
 * @property left the left edge's x position from the start of the [TabRow]
 * @property right the right edge's x position from the start of the [TabRow]
 * @property width the width of this tab
 */
@Immutable
class TabPosition internal constructor(val left: Dp, val width: Dp) {
    val right: Dp get() = left + width

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TabPosition) return false

        if (left != other.left) return false
        return width == other.width
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + width.hashCode()
        return result
    }

    override fun toString(): String {
        return "TabPosition(left=$left, right=$right, width=$width)"
    }
}

private enum class TabSlots {
    Tabs,
    Divider,
    Indicator
}

@Composable
fun TabIndicator(
    selectedTabIndex: Int,
    tabPositions: List<TabPosition>
) {
    if (tabPositions.isNotEmpty()) {
        val tabIndicatorWidth = 16.dp
        val currentTab = tabPositions[selectedTabIndex]
        val currentTabLeft = currentTab.left + (currentTab.width / 2 - tabIndicatorWidth / 2)
        val animatedIndicatorOffset by animateDpAsState(targetValue = currentTabLeft, animationSpec = Motion.snap())
        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.BottomStart)
                .offset(x = animatedIndicatorOffset, y = (-8).dp)
                .width(tabIndicatorWidth)
                .height(3.dp)
                .background(color = LocalContentColor.current, shape = Corners.full)
        )
    }
}

/**
 * A tab that, tapped again while selected, opens a menu of [items], such as the orders its list
 * can take; an arrow marks it while selected and turns while the menu shows.
 */
@Composable
fun TabClickMenu(
    selected: Boolean,
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    items: () -> List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedContentColor: Color = LocalContentColor.current,
    unselectedContentColor: Color = selectedContentColor.copy(alpha = ContentAlpha.medium),
) {
    val menus = LocalContextMenus.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var menuId by remember { mutableStateOf<Long?>(null) }
    // Turns back as soon as the menu starts to close, not once it has gone
    val open = menus?.isOpen(menuId) == true
    GlassTab(
        selected = selected,
        onClick = {
            when {
                !selected -> onClick()
                menus != null -> menuId = menus.show(items(), anchor = bounds)
            }
        },
        modifier = modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
        enabled = enabled,
        selectedContentColor = selectedContentColor,
        unselectedContentColor = unselectedContentColor,
    ) {
        val rotate by animateFloatAsState(targetValue = if (open) 180f else 0f, animationSpec = Motion.snap(), label = "ArrowIndicatorRotate")
        val alpha by animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = if (selected) Motion.fadeIn() else Motion.fadeOut(),
            label = "ArrowIndicatorAlpha",
        )
        // The arrow takes room only while the tab is chosen, so other tabs keep their usual width
        // The snap spring overshoots: a width going to none would dip below zero
        val arrowWidthAnimated by animateDpAsState(if (selected) ArrowSize else 0.dp, Motion.snap(), label = "ArrowWidth")
        val arrowWidth = arrowWidthAnimated.coerceAtLeast(0.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(48.dp)
                // A name that changes, as to the class chosen in its menu, takes its new width smoothly
                // A name growing or shrinking settles without overshooting
                .animateContentSize(Motion.settle())
                .padding(start = 16.dp, end = 16.dp - arrowWidth * 0.75f)
        ) {
            text()
            Icon(
                imageVector = Symbols.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier
                    .requiredWidth(arrowWidth)
                    .height(ArrowSize)
                    .rotate(rotate)
                    .alpha(alpha)
            )
        }
    }
}

private val ArrowSize = 16.dp
