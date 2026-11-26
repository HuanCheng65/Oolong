package oolong.client.ui.designsystem

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.unit.dp
import java.util.WeakHashMap

@Composable
fun MyLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        // A list laid out from the bottom has nothing to say about its top
        modifier = (if (reverseLayout) modifier else modifier.pageScroll(state))
            .pageShortcuts(list = state)
            .highFrameRateWhile(state.isScrollInProgress),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}

@Composable
fun MyLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = (if (reverseLayout) modifier else modifier.pageScroll(state))
            .highFrameRateWhile(state.isScrollInProgress),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}

/**
 * Asks for the screen's highest frame rate while [scrolling], from a drag to the end of a fling,
 * and leaves it to the system otherwise. Lists don't ask on their own as views' lists do, and from
 * Android 15 a phone that changes its refresh rate keeps a list that doesn't ask at its normal
 * rate, often 60Hz, when it moves slowly.
 */
private fun Modifier.highFrameRateWhile(scrolling: Boolean): Modifier =
    preferredFrameRate(if (scrolling) FrameRateCategory.High else FrameRateCategory.Default)

/**
 * A sticky header that pins below the list's top content padding instead of at the top edge of
 * the list, so it is not hidden behind a [GlassScaffold] top bar.
 *
 * Lazy lists pin sticky headers at the top of the viewport and ignore content padding. Each header
 * is translated to where it would pin if the padding were the top: no higher than the end of the
 * padding, and pushed up by the next header of the same list as that one arrives.
 */
fun LazyListScope.paddedStickyHeader(
    state: LazyListState,
    key: Any,
    content: @Composable () -> Unit,
) {
    stickyHeaderKeys.getOrPut(state) { mutableSetOf() }.add(key)
    stickyHeader(key = key) {
        Box(
            modifier = Modifier.graphicsLayer {
                val layoutInfo = state.layoutInfo
                val items = layoutInfo.visibleItemsInfo
                val header = items.firstOrNull { it.key == key } ?: return@graphicsLayer
                val padding = -layoutInfo.viewportStartOffset
                val top = header.offset + padding
                var pinnedTop = maxOf(top, padding)
                val keys = stickyHeaderKeys[state]
                val next = items.firstOrNull { it.index > header.index && keys?.contains(it.key) == true }
                if (next != null) pinnedTop = minOf(pinnedTop, next.offset + padding - header.size)
                translationY = (pinnedTop - top).toFloat()
            }
        ) {
            content()
        }
    }
}

/** Keys of the [paddedStickyHeader]s in each list, to find the header that pushes another. */
private val stickyHeaderKeys = WeakHashMap<LazyListState, MutableSet<Any>>()
