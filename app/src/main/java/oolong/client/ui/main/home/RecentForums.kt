package oolong.client.ui.main.home

import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.model.VisitedForum
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.rememberHaptics
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.PressFeedback
import oolong.core.designsystem.theme.Spacing
import oolong.core.designsystem.theme.TextStyles

/**
 * How far the recent forums are opened, from their first row (0) to all of them (1), and the
 * pull that opens them: pulling down at the top of the list opens them before anything else, and
 * pushing up closes them before the list scrolls. A pull that starts with them closed opens them
 * first; once they are all open it holds for [detentPx], marked by a tick, so a pull meant only
 * to open them stops there, and beyond that it goes on to refresh the page, in one movement.
 */
@Stable
class RecentForumsExpansion(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set

    /** How much taller the forums are when open than closed. */
    var extraPx by mutableFloatStateOf(0f)

    /** How far a pull holds once the forums are all open, before it goes on to refresh. */
    var detentPx by mutableFloatStateOf(0f)

    /** Told when a pull has opened the forums all the way, for a tick under the finger. */
    var onOpenedByPull: () -> Unit = {}

    /** How much of the hold the pull has used. */
    private var held = 0f

    /** Whether there are more forums than the first row, so that there is anything to open. */
    var canExpand by mutableStateOf(false)

    /**
     * Whether a pull may refresh: between pulls, whether the forums are all open (or there are no
     * more to open); during one, what it was when the pull started, however far it goes.
     */
    val refreshAllowed: Boolean get() = if (inGesture) gestureMayRefresh else !canExpand || open

    private var gestureMayRefresh by mutableStateOf(true)

    /** Whether the forums are in view, so pushing up closes them rather than scrolling. */
    var inView: () -> Boolean = { true }

    private var inGesture by mutableStateOf(false)
    private var animation: Job? = null

    val open: Boolean get() = progress >= 1f - Motion.ProgressThreshold

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            begin()
            if (available.y < 0 && progress > 0f && inView()) return Offset(0f, drag(available.y))
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y <= 0f || !canExpand) return Offset.Zero
            begin()
            if (refreshAllowed) return Offset.Zero
            // Pulling from closed: it opens the forums, then holds a moment, then the rest goes on
            // to refresh
            val wasOpen = open
            val used = drag(available.y)
            var rest = available.y - used
            if (open && !wasOpen) onOpenedByPull()
            if (open && rest > 0f) {
                val take = rest.coerceAtMost(detentPx - held)
                held += take
                rest -= take
                if (held >= detentPx) gestureMayRefresh = true
            }
            return Offset(0f, available.y - rest)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            inGesture = false
            if (progress <= 0f || progress >= 1f) return Velocity.Zero
            settle(available.y)
            return available
        }
    }

    fun toggle() = animateTo(if (open) 0f else 1f, 0f)

    /** Opens a little way and closes again, once, to show there is more to pull out. */
    fun peek(distance: Dp, density: Float) {
        if (extraPx <= 0f || progress > 0f) return
        animation?.cancel()
        animation = scope.launch {
            animate(0f, (distance.value * density / extraPx).coerceAtMost(0.6f), animationSpec = Motion.morph()) { value, _ -> progress = value }
            kotlinx.coroutines.delay(PeekHold)
            animate(progress, 0f, animationSpec = Motion.dismiss()) { value, _ -> progress = value }
        }
    }

    private fun begin() {
        if (inGesture) return
        gestureMayRefresh = !canExpand || open
        held = 0f
        inGesture = true
        animation?.cancel()
    }

    private fun drag(dy: Float): Float {
        if (extraPx <= 0f) return 0f
        val before = progress
        progress = (before + dy / extraPx).coerceIn(0f, 1f)
        return (progress - before) * extraPx
    }

    /** Opens past a third or on a fling down, otherwise closes, carrying on at the finger's speed. */
    private fun settle(velocity: Float) {
        val opening = velocity > FlingVelocity || (progress > OpenThreshold && velocity > -FlingVelocity)
        animateTo(if (opening) 1f else 0f, if (extraPx > 0f) velocity / extraPx else 0f)
    }

    private fun animateTo(target: Float, velocity: Float) {
        animation?.cancel()
        val spec: AnimationSpec<Float> = if (target > progress) Motion.morph() else Motion.dismiss()
        animation = scope.launch {
            animate(progress, target, velocity, spec) { value, _ -> progress = value.coerceIn(0f, 1.04f) }
            progress = target
        }
    }

    private companion object {
        const val OpenThreshold = 0.35f
        const val FlingVelocity = 600f
        const val PeekHold = 650L
    }
}

@Composable
fun rememberRecentForumsExpansion(): RecentForumsExpansion {
    val scope = rememberCoroutineScope()
    val expansion = remember(scope) { RecentForumsExpansion(scope) }
    val haptics = rememberHaptics()
    expansion.detentPx = with(LocalDensity.current) { PullDetent.toPx() }
    expansion.onOpenedByPull = { haptics.threshold(over = true) }
    return expansion
}

/** How far a pull holds once the forums are open, before it starts to refresh. */
private val PullDetent = 32.dp

/**
 * The forums visited lately: a row of them, the rest opening below as the page is pulled down
 * or the arrow is tapped. Rows come into focus as they are uncovered.
 */
@Composable
fun RecentForums(
    visited: ImmutableList<VisitedForum>,
    expansion: RecentForumsExpansion,
    columns: Int,
    onOpen: (VisitedForum) -> Unit,
) {
    // With motion reduced the rows only fade in as they are revealed, without growing out of a blur
    val reduced = LocalReduceMotion.current
    val rows = visited.take(columns * MaxRows).chunked(columns)
    expansion.canExpand = rows.size > 1
    val progress = expansion.progress
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = rows.size > 1,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = PressFeedback.Overlay,
                    role = Role.Button,
                ) { expansion.toggle() }
                .padding(end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xxs),
        ) {
            Header(text = stringResource(R.string.title_history_forum))
            Spacer(modifier = Modifier.weight(1f))
            if (rows.size > 1) {
                val description = stringResource(if (expansion.open) R.string.desc_collapse_recent_forums else R.string.desc_expand_recent_forums)
                Icon(
                    imageVector = Symbols.ExpandMore,
                    contentDescription = null,
                    tint = ExtendedTheme.colors.textSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(progress * 180f)
                        .semantics { contentDescription = description },
                )
            }
        }
        rows.firstOrNull()?.let { TileRow(it, columns, onOpen) }
        if (rows.size > 1) {
            Column(
                modifier = Modifier
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Int.MAX_VALUE))
                        expansion.extraPx = placeable.height.toFloat()
                        val height = (placeable.height * progress.coerceIn(0f, 1f)).toInt()
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    },
            ) {
                rows.drop(1).forEachIndexed { index, row ->
                    // How much of this row is uncovered, by where the opening has got to
                    val reveal = ((progress * (rows.size - 1)) - index).coerceIn(0f, 1f)
                    TileRow(
                        row,
                        columns,
                        onOpen,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = reveal
                                val scale = if (reduced) 1f else lerp(0.92f, 1f, reveal)
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                            }
                            .then(
                                if (!reduced && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && reveal < 1f) {
                                    Modifier.blur(((1f - reveal) * 6f).dp, BlurredEdgeTreatment.Unbounded)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
    }
}

@Composable
private fun TileRow(forums: List<VisitedForum>, columns: Int, onOpen: (VisitedForum) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xs)) {
        forums.forEach { forum ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpen(forum) }
                    .padding(vertical = 6.dp),
            ) {
                Avatar(data = forum.avatar, size = TileAvatar, contentDescription = null, shape = Corners.lg)
                Text(
                    text = forum.name,
                    style = TextStyles.footnote,
                    color = ExtendedTheme.colors.text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
        repeat(columns - forums.size) { Spacer(modifier = Modifier.weight(1f)) }
    }
}

private val TileAvatar = 56.dp
private const val MaxRows = 3
