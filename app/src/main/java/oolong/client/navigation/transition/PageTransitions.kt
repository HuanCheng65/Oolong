package oolong.client.navigation.transition

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.ThemeUtil
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.Radius
import oolong.core.designsystem.theme.SmoothCornerShape
import kotlin.math.roundToInt

/** Whether the latest change to the back stack removed pages, as opposed to adding them. */
val LocalIsPop = compositionLocalOf { false }

/**
 * Page transitions in the style of a navigation stack: the new page slides in
 * over the current one, which drifts a little the same way and darkens.
 *
 * The translucent theme keeps full-width slides with no overlap, because its
 * pages are transparent over a shared wallpaper.
 */
object PageTransitions {
    /** How far the page underneath moves, as a fraction of the container width. */
    private const val UnderPageOffset = 0.3f

    /**
     * Black overlay alpha on the page underneath once it is fully covered.
     * Dark themes need more: their background is already near black, so only
     * text and images darken.
     */
    private const val UnderPageDim = 0.12f
    private const val UnderPageDimNight = 0.35f

    private val ShadowWidth = 12.dp
    private const val ShadowAlpha = 0.12f

    /** A spring, so a transition interrupted halfway carries on from where it is. */
    private fun <T> spec(): FiniteAnimationSpec<T> = Motion.page()

    /** Whether pages overlap as they slide, which follows the theme. */
    @Composable
    fun rememberOverlap(): State<Boolean> {
        val overlap = !ThemeUtil.isTranslucentTheme(ExtendedTheme.colors.theme)
        val overlapState = remember { mutableStateOf(overlap) }
        SideEffect { overlapState.value = overlap }
        return overlapState
    }

    private fun underPageOffset(fullOffset: Int, overlap: Boolean): Int =
        if (overlap) (fullOffset * UnderPageOffset).roundToInt() else fullOffset

    /** Opening a page, for scenes or for the content of one pane. */
    fun <S> transitionSpec(
        overlap: State<Boolean>,
        animationSpec: FiniteAnimationSpec<IntOffset> = spec(),
    ): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = animationSpec,
            initialOffset = { it }
        ) togetherWith slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = animationSpec,
            targetOffset = { -underPageOffset(it, overlap.value) }
        )
    }

    fun <S> popTransitionSpec(
        overlap: State<Boolean>,
        animationSpec: FiniteAnimationSpec<IntOffset> = spec(),
    ): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = animationSpec,
            initialOffset = { -underPageOffset(it, overlap.value) }
        ) togetherWith slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = animationSpec,
            targetOffset = { it }
        )
    }

    /**
     * For animations told by [PageDisplay]'s transitions: linear over one fixed length, so where
     * they are follows the fraction the display drives, and the pace is the driving spring's.
     * Every animation on those transitions must use it, or the fractions drift apart. One that
     * moves over only part of the transition runs from fraction [start] to [end].
     */
    fun <T> seeked(start: Float = 0f, end: Float = 1f): FiniteAnimationSpec<T> = tween(
        durationMillis = ((end - start) * SeekMillis).roundToInt(),
        delayMillis = (start * SeekMillis).roundToInt(),
        easing = LinearEasing,
    )

    private const val SeekMillis = 1000

    /** What drives [PageDisplay] through a step deeper or back. */
    fun drive(): FiniteAnimationSpec<Float> = Motion.page(Motion.ProgressThreshold)

    /** Nothing moves, for scenes that animate their own content, such as the panes side by side. */
    fun none(): ContentTransform = EnterTransition.None togetherWith ExitTransition.None

    /**
     * How [PageDisplay] moves from one scene to another: a step deeper or back by how many pages are
     * under each, sliding as [transitionSpec] and [popTransitionSpec] do; a quick cross-fade for
     * the same pages laid out again ([relayout]); and, for a [peek], only the page on top sliding
     * off, as the page underneath is already in place. A page opened from one of [origins] grows
     * out of it instead, and shrinks into it going back, while the page underneath stays put.
     */
    fun <T : Any> between(
        from: Scene<T>,
        to: Scene<T>,
        overlap: State<Boolean>,
        relayout: Boolean,
        peek: Boolean,
        origins: PageOrigins? = null,
        chrome: LeadingChrome? = null,
    ): PageTransition {
        val fromDepth = from.previousEntries.size
        val toDepth = to.previousEntries.size
        val morph = origins != null && when {
            // Over the translucent theme's wallpaper pages have no ground of their own to morph
            relayout || !overlap.value || from !is FramedSinglePaneScene<*> || to !is FramedSinglePaneScene<*> -> false.also { origins.clear() }
            else -> origins.morphs(
                from = from.key,
                to = to.key,
                opening = toDepth > fromDepth,
                peek = peek,
                kept = (to.previousEntries + to.entries).mapTo(mutableSetOf()) { it.contentKey },
            )
        }
        // Worked out alongside the morph, above
        val heroFlies = origins?.flight != null
        return when {
            // Opaque pages fade in over the layout going, which stays till then, so the ground
            // never shows through half way; over the wallpaper the two cross-fade
            relayout && overlap.value -> PageTransition(
                { fadeIn(seeked()) togetherWith ExitTransition.KeepUntilTransitionsFinished },
                drive = tween(CrossFadeMillis),
                zIndex = 1f,
            )
            relayout -> PageTransition(
                { fadeIn(seeked()) togetherWith fadeOut(seeked()) },
                drive = tween(CrossFadeMillis),
                zIndex = 0f,
            )
            // Let go of, the page slides off as from any peek, unless it shrinks into its origin
            peek && !morph -> PageTransition(
                {
                    EnterTransition.None togetherWith
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, seeked(), targetOffset = { it })
                },
                drive = drive(),
                zIndex = -1f,
            )
            // The page and its origin carry the motion; the pages underneath stay put
            morph && toDepth > fromDepth -> PageTransition(
                { EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished },
                drive = drive(),
                zIndex = 1f,
            )
            // The page coming back goes over the one going, so taps reach it at once: the one
            // going shrinks above everything, or, its origin gone meanwhile, the one coming back
            // fades in over it instead ([PageOrigins.comebackAlpha])
            morph -> PageTransition(
                { EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished },
                drive = drive(),
                zIndex = 1f,
            )
            // An image taken along flies on its own, so the page only fades in and out around
            // it, in place: sliding as well would send the eye two ways at once
            heroFlies && toDepth > fromDepth -> PageTransition(
                { fadeIn(seeked()) togetherWith ExitTransition.KeepUntilTransitionsFinished },
                drive = drive(),
                zIndex = 1f,
            )
            // Fading in over the page going, not that page fading out over it, which looks the
            // same, so taps reach the page coming back at once
            heroFlies -> PageTransition(
                { fadeIn(seeked()) togetherWith ExitTransition.KeepUntilTransitionsFinished },
                drive = drive(),
                zIndex = 1f,
            )
            // A page over the whole window, chrome and all, comes in over the page it covers,
            // which stays where it is beside the chrome, and goes off it the same way. Over the
            // translucent theme's wallpaper pages have no ground to cover with, and slide as any
            chrome != null && overlap.value && toDepth > fromDepth && chrome.covers(to) -> PageTransition(
                {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, seeked(), initialOffset = { it }) togetherWith
                        ExitTransition.KeepUntilTransitionsFinished
                },
                drive = drive(),
                zIndex = 1f,
            )
            chrome != null && overlap.value && toDepth < fromDepth && chrome.covers(from) -> PageTransition(
                {
                    EnterTransition.None togetherWith
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, seeked(), targetOffset = { it })
                },
                drive = drive(),
                zIndex = -1f,
            )
            toDepth < fromDepth -> PageTransition(popTransitionSpec(overlap, seeked()), drive(), zIndex = -1f)
            else -> PageTransition(transitionSpec(overlap, seeked()), drive(), zIndex = 1f)
        }
    }

    /** The page on top at the furthest peek: its scale, its shift toward the finger as a fraction of its width, and its corners. */
    private const val PeekScale = 0.9f
    private const val PeekShift = 0.05f
    private val PeekRadius = Radius.xl

    /** How many sizes the peeked page's corners grow through, each 1/[PeekShapeSteps] of [PeekRadius]. */
    private const val PeekShapeSteps = 8
    private val PeekShapes = List(PeekShapeSteps + 1) { SmoothCornerShape(PeekRadius * it / PeekShapeSteps) }
    private val PeekElevation = 16.dp

    /** How much of the dim on the page underneath lifts at the furthest peek. */
    private const val PeekDimRelief = 0.3f

    /**
     * A quick cross-fade, for changes that are not a step deeper or back: one page swapped for
     * another at the same level, or the same pages laid out again for a new window size.
     */
    fun crossFade(overlap: Boolean = false): ContentTransform = if (overlap) {
        // Opaque, the new page fades in over the old, which stays till then, so the ground
        // never shows through half way
        (fadeIn(tween(CrossFadeMillis)) togetherWith fadeOut(tween(durationMillis = 1, delayMillis = CrossFadeMillis))).apply { targetContentZIndex = 1f }
    } else {
        fadeIn(tween(CrossFadeMillis)) togetherWith fadeOut(tween(CrossFadeMillis))
    }

    private const val CrossFadeMillis = 200

    /**
     * Gives a page an opaque background, so the page underneath doesn't show
     * through while the two overlap, plus the dim and the edge shadow. Works on
     * whatever area the page is given, so a scene can frame each of its panes.
     *
     * A page framed alone passes its content [key], so that it can be peeked past ([PagePeek]).
     */
    @Composable
    fun PageFrame(key: Any? = null, content: @Composable () -> Unit) {
        if (ThemeUtil.isTranslucentTheme(ExtendedTheme.colors.theme)) {
            content()
            return
        }

        val underPageDim = if (ExtendedTheme.colors.isNightMode) UnderPageDimNight else UnderPageDim
        val transition = LocalNavAnimatedContentScope.current.transition
        // A pane of a wider scene dims as its own content changes. A page framed alone dims by
        // how far the display has it covered ([LocalPageCover])
        val dim = if (key == null) {
            // Entering on a pop means coming back from underneath; leaving on a push means being covered
            val isPop = LocalIsPop.current
            transition.animateFloat(
                transitionSpec = { spec() },
                label = "PageDim",
            ) { state ->
                when (state) {
                    EnterExitState.PreEnter -> if (isPop) underPageDim else 0f
                    EnterExitState.Visible -> 0f
                    EnterExitState.PostExit -> if (isPop) 0f else underPageDim
                }
            }
        } else {
            null
        }
        val covered = LocalPageCover.current
        val origins = if (key != null) LocalPageOrigins.current else null
        val peek = if (key != null) LocalPagePeek.current else null
        if (peek != null) {
            // Rests once the scenes are still and this page is the one shown
            LaunchedEffect(peek, key) {
                snapshotFlow {
                    val scenes = transition.parentTransition
                    val still = if (scenes != null) scenes.currentState == scenes.targetState else !transition.isRunning
                    still && transition.targetState == EnterExitState.Visible
                }.collect { resting ->
                    if (resting) {
                        peek.restingKey = key
                        // Back at rest after a peek called off
                        if (peek.topKey == key && !peek.committed) peek.reset()
                    } else if (peek.restingKey == key) {
                        peek.restingKey = null
                    }
                }
            }
            DisposableEffect(peek, key) {
                onDispose {
                    if (peek.restingKey == key) peek.restingKey = null
                    if (peek.topKey == key) peek.reset()
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (key != null) Modifier.pageMorph(key) else Modifier)
                .then(
                    if (key != null) {
                        Modifier.graphicsLayer {
                            alpha = origins?.comebackAlpha(key, transition.targetState == EnterExitState.Visible, covered()) ?: 1f
                            val amount = if (peek != null && peek.topKey == key) peek.amount else 0f
                            if (amount > 0f) {
                                val scale = 1f - (1f - PeekScale) * amount
                                scaleX = scale
                                scaleY = scale
                                val toward = if (peek?.edge == NavigationEvent.EDGE_RIGHT) -1f else 1f
                                translationX = toward * size.width * PeekShift * amount
                                // In steps rather than anew each frame: a new outline makes the
                                // clip and the shadow of the whole page be worked out again
                                shape = PeekShapes[(amount * PeekShapeSteps).roundToInt().coerceIn(1, PeekShapeSteps)]
                                clip = true
                                shadowElevation = PeekElevation.toPx() * amount
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .drawWithContent {
                    // Whether pages are moving, by the scene transition: a page's own transition
                    // can be left mid-way, still running, when a push is interrupted by back
                    val scenes = transition.parentTransition
                    val moving = if (scenes != null) {
                        scenes.currentState != scenes.targetState
                    } else {
                        transition.currentState != transition.targetState
                    }
                    if (moving) {
                        val shadowWidth = ShadowWidth.toPx()
                        val ltr = layoutDirection == LayoutDirection.Ltr
                        val left = if (ltr) -shadowWidth else size.width
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = if (ltr) {
                                    listOf(Color.Transparent, Color.Black.copy(alpha = ShadowAlpha))
                                } else {
                                    listOf(Color.Black.copy(alpha = ShadowAlpha), Color.Transparent)
                                },
                                startX = left,
                                endX = left + shadowWidth,
                            ),
                            topLeft = Offset(left, 0f),
                            size = Size(shadowWidth, size.height),
                        )
                    }
                    drawContent()
                    val alpha = if (dim != null) {
                        // A pane of a wider scene: its own animation, only while pages move
                        if (moving) dim.value else 0f
                    } else {
                        // Under a peek it lifts a little as the peek grows, before the page on top goes
                        val relief = if (peek != null && peek.underKey == key) 1f - PeekDimRelief * peek.amount else 1f
                        underPageDim * covered() * relief
                    }
                    if (alpha > 0f) {
                        drawRect(Color.Black.copy(alpha = alpha))
                    }
                }
                .background(ExtendedTheme.colors.background)
        ) {
            CompositionLocalProvider(LocalFramedPage provides key) { content() }
        }
    }
}

/** Shows the top entry alone, in a [PageTransitions.PageFrame]. */
class FramedSinglePaneSceneStrategy<T : Any> : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T> =
        FramedSinglePaneScene(entry = entries.last(), previousEntries = entries.dropLast(1))
}

internal data class FramedSinglePaneScene<T : Any>(
    val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
) : Scene<T> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<T>> = listOf(entry)
    override val content: @Composable () -> Unit = {
        BesideLeadingChrome(entry) { PageTransitions.PageFrame(key = entry.contentKey) { entry.Content() } }
    }
}
