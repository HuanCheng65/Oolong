package oolong.client.ui.designsystem

import android.os.SystemClock
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Where a keyboard's page shortcuts go: refreshing, moving to the next or previous item, paging
 * down or up. Lists and pull-to-refresh areas sign up as they show. A key goes to one on screen,
 * the one the pointer or a finger was over last, which in two panes is the pane being used.
 */
@Stable
class PageShortcuts {
    private val targets = mutableListOf<Target>()

    internal class Target(
        val refresh: () -> (() -> Unit)?,
        val list: LazyListState?,
        val scope: CoroutineScope,
    ) {
        var coordinates: LayoutCoordinates? = null
        var usedAt = 0L

        val area: Float
            get() = coordinates?.takeIf { it.isAttached }?.boundsInWindow()?.let { if (it.isEmpty) 0f else it.width * it.height } ?: 0f
    }

    internal fun add(target: Target) = targets.add(target)

    internal fun remove(target: Target) = targets.remove(target)

    /** Refreshes the page in use; false when nothing on screen refreshes. */
    fun refresh(): Boolean {
        val refresh = pick { it.refresh() != null }?.refresh?.invoke() ?: return false
        refresh()
        return true
    }

    /** Scrolls the list in use to the item [step] items on, or back; false when there is none. */
    fun moveItem(step: Int): Boolean {
        val target = pick { it.list != null } ?: return false
        val list = target.list ?: return false
        val from = list.firstVisibleItemIndex + if (step < 0 && list.firstVisibleItemScrollOffset > 0) 1 else 0
        val to = (from + step).coerceIn(0, (list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        target.scope.launch { list.animateScrollToItem(to) }
        return true
    }

    /** Scrolls the list in use by most of what it shows, down or up; false when there is none. */
    fun page(down: Boolean): Boolean {
        val target = pick { it.list != null } ?: return false
        val list = target.list ?: return false
        // Keeps a line of what was shown, to read on from
        val by = list.layoutInfo.viewportSize.height * PageFraction
        target.scope.launch { list.animateScrollBy(if (down) by else -by) }
        return true
    }

    private fun pick(can: (Target) -> Boolean): Target? =
        targets.filter { can(it) && it.area > 0f }.maxWithOrNull(compareBy<Target>({ it.usedAt }, { it.area }))

    private companion object {
        const val PageFraction = 0.85f
    }
}

val LocalPageShortcuts = staticCompositionLocalOf<PageShortcuts?> { null }

/**
 * Signs this up for the keyboard's page shortcuts while it shows: [list] to move through and
 * page, [onRefresh] to refresh, when given. It counts as in use whenever a pointer or finger is
 * over it.
 */
@Composable
fun Modifier.pageShortcuts(list: LazyListState? = null, onRefresh: (() -> Unit)? = null): Modifier {
    val shortcuts = LocalPageShortcuts.current ?: return this
    val scope = rememberCoroutineScope()
    val refresh by rememberUpdatedState(onRefresh)
    val target = remember(list, scope) { PageShortcuts.Target({ refresh }, list, scope) }
    DisposableEffect(shortcuts, target) {
        shortcuts.add(target)
        onDispose { shortcuts.remove(target) }
    }
    return onGloballyPositioned { target.coordinates = it }
        .pointerInput(target) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    target.usedAt = SystemClock.uptimeMillis()
                }
            }
        }
}
