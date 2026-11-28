package oolong.client.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.scrim
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.Radius
import oolong.core.designsystem.theme.SmoothCornerShape

/**
 * Shows the top entry in a modal bottom sheet over the pages below, when it asks for one. The sheet
 * leaves through [onBack] when swiped down or dismissed.
 */
class BottomSheetSceneStrategy<T : Any>(private val onBack: () -> Unit) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val entry = entries.last()
        if (BottomSheetKey !in entry.metadata) return null
        return BottomSheetScene(entry = entry, previousEntries = entries.dropLast(1), onBack = this@BottomSheetSceneStrategy.onBack)
    }

    companion object {
        object BottomSheetKey : NavMetadataKey<Unit>

        fun bottomSheet(): Map<String, Any> = metadata { put(BottomSheetKey, Unit) }
    }
}

private class BottomSheetScene<T : Any>(
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    override val key: Any = entry.contentKey
    override val entries = listOf(entry)
    override val overlaidEntries = previousEntries

    /** Completed when the entry leaves the back stack, so the sheet slides away first. */
    private val removed = CompletableDeferred<Unit>()
    private val hidden = CompletableDeferred<Unit>()

    @OptIn(ExperimentalMaterialApi::class)
    override val content: @Composable () -> Unit = {
        val state = rememberModalBottomSheetState(
            ModalBottomSheetValue.Hidden,
            animationSpec = Motion.settle(),
            skipHalfExpanded = true,
        )
        LaunchedEffect(state) {
            state.show()
            // Swiped down or the scrim tapped: leave the page as back would
            snapshotFlow { state.currentValue }.first { it == ModalBottomSheetValue.Hidden }
            if (!removed.isCompleted) onBack()
        }
        // NavDisplay's own handler would pop everything above the page under the sheet
        BackHandler(onBack = onBack)
        LaunchedEffect(state) {
            removed.await()
            state.hide()
            hidden.complete(Unit)
        }
        // The sheet stops short of the top, below the status bar, so the page under it shows above
        // its corners rather than in slivers beside them at the window's own corners
        val topGap = WindowInsets.statusBars.union(WindowInsets.captionBar).union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding() + SheetTopGap
        BoxWithConstraints {
            val sheetHeight = maxHeight - topGap
            ModalBottomSheetLayout(
                sheetContent = {
                    Column(modifier = Modifier.heightIn(max = sheetHeight)) {
                        // The handle says the sheet can be dragged down to close
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 8.dp)
                                .size(width = 36.dp, height = 5.dp)
                                .background(ExtendedTheme.colors.text.copy(alpha = 0.18f), SmoothCornerShape.Capsule)
                        )
                        entry.Content()
                    }
                },
                sheetState = state,
                sheetShape = SmoothCornerShape(Radius.xl, bottom = false),
                sheetBackgroundColor = ExtendedTheme.colors.sheet,
                scrimColor = ExtendedTheme.colors.scrim,
                content = {},
            )
        }
    }

    override suspend fun onRemove() {
        removed.complete(Unit)
        withTimeoutOrNull(HIDE_TIMEOUT_MILLIS) { hidden.await() }
    }

    private companion object {
        const val HIDE_TIMEOUT_MILLIS = 1_000L
        val SheetTopGap = 8.dp
    }
}
