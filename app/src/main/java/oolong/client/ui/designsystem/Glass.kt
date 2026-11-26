package oolong.client.ui.designsystem

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.settingAsState
import oolong.client.ui.designsystem.theme.ExtendedColors
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.ShadowAmbient
import oolong.client.ui.designsystem.theme.ShadowSpot
import oolong.client.ui.designsystem.theme.ThemeUtil
import oolong.core.designsystem.theme.Motion
import oolong.core.designsystem.theme.SmoothCornerShape
import kotlin.math.max

/**
 * How floating chrome (bars, pills) is rendered. Every tier lays out identically; only the
 * material behind the chrome changes.
 */
enum class GlassTier {
    /** Refraction, blur and highlights. Needs RuntimeShader. */
    Glass,

    /** Backdrop blur without refraction. Needs RenderEffect. */
    Blur,

    /** Opaque colour, no backdrop sampling. */
    Solid;

    companion object {
        fun forDevice(): GlassTier = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Glass
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Blur
            else -> Solid
        }
    }
}

/** Forces a tier, for previews and screenshot tests. */
val LocalGlassTier = staticCompositionLocalOf<GlassTier?> { null }

const val DefaultGlassClarity = 0.5f

/** The tiers this device can draw, best first. */
val GlassTier.Companion.available: List<GlassTier>
    get() = GlassTier.entries.drop(GlassTier.entries.indexOf(GlassTier.forDevice()))

/** The material and clarity chosen for chrome, as stored. */
@Immutable
private data class GlassChoice(val material: String, val reduceTransparency: Boolean, val clarity: Float)

private val LocalGlassChoice = compositionLocalOf { GlassChoice("", false, DefaultGlassClarity) }

/** Provides the chosen material and clarity to [content], read once for all of it. */
@Composable
fun ProvideGlassChoice(content: @Composable () -> Unit) {
    // Read from the first frame on. Chrome first drawn in the device's best material and redrawn in
    // the chosen one swaps its effect in place as the rest of its modifiers change around it, which
    // the effect does not survive: the page froze
    val material by settingAsState(SettingKeys.GlassMaterial)
    val reduceTransparency by settingAsState(SettingKeys.ReduceTransparency)
    val clarity by settingAsState(SettingKeys.GlassClarity)
    val choice = remember(material, reduceTransparency, clarity) { GlassChoice(material, reduceTransparency, clarity) }
    CompositionLocalProvider(LocalGlassChoice provides choice, content = content)
}

/** The material chosen for chrome, as far as the device draws it. */
@Composable
fun chosenGlassTier(): GlassTier {
    val choice = LocalGlassChoice.current
    val tier = GlassTier.entries.firstOrNull { it.name == choice.material }
        ?: if (choice.reduceTransparency) GlassTier.Solid else GlassTier.forDevice()
    return maxOf(tier, GlassTier.forDevice())
}

@Composable
internal fun currentGlassTier(): GlassTier = LocalGlassTier.current ?: chosenGlassTier()

/**
 * How much more than at [DefaultGlassClarity] glass blurs and tints: 1 there, more towards
 * frosted, less towards clear.
 */
@Composable
internal fun glassFrost(): Float = frostAt(LocalGlassChoice.current.clarity)

private fun frostAt(clarity: Float): Float = if (clarity < DefaultGlassClarity) {
    lerp(MostFrost, 1f, clarity / DefaultGlassClarity)
} else {
    lerp(1f, LeastFrost, (clarity - DefaultGlassClarity) / (1f - DefaultGlassClarity))
}

private const val MostFrost = 2f
private const val LeastFrost = 0.25f

/**
 * What floating chrome samples: the sources in [state] keyed [key], plus the translucent theme's
 * wallpaper if it is in the same state. A null key takes every source.
 */
@Immutable
class GlassSource(val state: HazeState, val key: Any? = null) {
    val input: HazeInput.Sources = if (key == null) {
        HazeInput.Sources(state)
    } else {
        HazeInput.Sources(state, HazeSourceSelection.Behind.where { it.key === key || it.key === WallpaperKey })
    }

    /**
     * [input] without chrome marked with [lensTarget], for the material of such chrome. Haze leaves
     * out the source a material is drawn inside only once it has found it among its ancestors,
     * which it can miss while the modifiers change, as the glass tier does, and drawing it then
     * throws; this never takes it.
     */
    val belowLensTargets: HazeInput.Sources = HazeInput.Sources(
        state,
        HazeSourceSelection.Behind.where { (key == null || it.key === key || it.key === WallpaperKey) && it.zIndex < LensTargetZIndex },
    )
}

/**
 * The content that floating chrome samples. Provided by [GlassScaffold]; outside one, chrome
 * samples only the wallpaper on the translucent theme, and otherwise falls back to
 * [GlassTier.Solid].
 *
 * Pages usually leave their background transparent and let the window draw it, so the captured
 * content alone is not opaque: every material composites the theme background behind it first,
 * or the sharp content would show through the blur.
 */
val LocalGlassSource = staticCompositionLocalOf<GlassSource?> { null }

/**
 * The state the translucent theme's wallpaper is captured in, once under every page, so that
 * chrome blurs the wallpaper along with its page and the wallpaper stays put as pages slide.
 * Null on other themes.
 */
val LocalWallpaperGlass = staticCompositionLocalOf<HazeState?> { null }

/** Captures the translucent theme's wallpaper for [LocalWallpaperGlass]. */
fun Modifier.wallpaperGlassSource(state: HazeState): Modifier =
    hazeSource(state, zIndex = WallpaperZIndex, key = WallpaperKey)

private object WallpaperKey

/** Below the scaffold content, which is captured at the default zIndex of 0. */
private const val WallpaperZIndex = -1f

@Composable
private fun currentGlassSource(): GlassSource? =
    LocalGlassSource.current ?: LocalWallpaperGlass.current?.let { remember(it) { GlassSource(it, WallpaperKey) } }

/**
 * What chrome is tinted with: the page's background, except on the translucent theme, whose
 * background is clear: black under its light text, white under its dark text.
 */
private val ExtendedColors.chromeTint: Color
    get() = when {
        !ThemeUtil.isTranslucentTheme(theme) -> background
        theme.contains("light") -> Color.Black
        else -> Color.White
    }

private val ExtendedColors.isTranslucent: Boolean
    get() = ThemeUtil.isTranslucentTheme(theme)

/**
 * Background for a bar pinned to the top edge, like iOS's scroll edge. At the top of the page
 * nothing is under the bar, so it is clear; as the content scrolls up under it (see
 * [ScrollEdgeState]) a veil of the page's background and a blur come in, following the scroll.
 * The veil eases out over the whole height, level at both ends so it shows no line. The blur is
 * full down to [TopBarBlurFade] above the bottom edge, which the bar sets [TopBarEdgeExtend] below
 * its own, so content is blurred soon after it passes under the bar and fully by the title.
 *
 * Without a blur, the veil alone is held the blur's way, thicker, and eases out where it would.
 */
@Composable
fun Modifier.topBarGlass(): Modifier {
    val scrollEdge = LocalPageScroll.current
    val revealPx = with(LocalDensity.current) { TopBarEdgeReveal.toPx() }
    val shown: () -> Float = remember(scrollEdge, revealPx) {
        { scrollEdge?.blended { (it / revealPx).coerceIn(0f, 1f) } ?: 1f }
    }
    return scrollEdgeGlass(top = true, shown = shown)
}

/**
 * [topBarGlass] upside down, for what floats at the bottom of a page over its content, such as a
 * page's main button: the content fades and blurs out towards the bottom edge. [left] is how far
 * the content still scrolls on, in pixels; the edge comes in over the last [TopBarEdgeReveal] of
 * it, so it is clear once the end of the content is in sight.
 */
@Composable
fun Modifier.bottomEdgeGlass(left: () -> Int): Modifier {
    val revealPx = with(LocalDensity.current) { TopBarEdgeReveal.toPx() }
    val shown: () -> Float = remember(left, revealPx) { { (left() / revealPx).coerceIn(0f, 1f) } }
    return scrollEdgeGlass(top = false, shown = shown)
}

/** A scroll edge along the [top] or bottom edge, drawn as much as [shown] says. */
@OptIn(ExperimentalHazeApi::class)
@Composable
private fun Modifier.scrollEdgeGlass(top: Boolean, shown: () -> Float): Modifier {
    val source = currentGlassSource()
    val tier = currentGlassTier()
    val colors = ExtendedTheme.colors
    val color = colors.chromeTint
    val frost = glassFrost()
    val tint = ((if (colors.isTranslucent) TranslucentTopBarTint else TopBarTint) * frost).coerceAtMost(MaxTint)
    val solid = source == null || tier == GlassTier.Solid
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val clear by remember(shown) { derivedStateOf { shown() == 0f } }
    // Where the fades start. The bar can change height, such as a header collapsing inside it,
    // so these follow its size; a bar shorter than a fade fades over all of it.
    val blurFadePx = with(density) { TopBarBlurFade.toPx() }
    val blurEdge = if (height > 0) (1f - blurFadePx / height).coerceIn(0f, 1f) else 0f
    val measured = onSizeChanged { height = it.height }
    // Nothing to draw, and no blur to run, while the bar is clear
    if (clear) return measured
    if (solid) {
        // Held as the blur is, as nothing blurs what it lets show, then eased out the tint's way
        val veil = if (colors.isTranslucent) TranslucentTopBarTint else SolidTopBarTint
        val brush = remember(color, veil, top, blurEdge) { scrollEdgeTint(color, veil, top, heldTo = blurEdge) }
        return measured.graphicsLayer { alpha = shown() }.background(brush)
    }
    // Read through a derived state, so the style is read again only when the strength changes and
    // not on every frame the content scrolls under a bar that is already fully in
    val strength by remember(shown) { derivedStateOf { shown() } }
    // Made once: one made each time the style is read never matches the blur Haze keeps, and the
    // blur would be made afresh on every frame
    val progressive = remember(top, blurEdge) {
        HazeProgressive.verticalGradient(
            // Full strength from the screen's edge to the stretch before the inner edge, then eased out
            easing = { t ->
                val fromEdge = if (top) t else 1f - t
                val x = if (fromEdge <= blurEdge) 0f else ((fromEdge - blurEdge) / (1f - blurEdge)).coerceIn(0f, 1f)
                x * x * (3f - 2f * x)
            },
            startIntensity = 1f,
            endIntensity = 0f,
        )
    }
    val style = remember(color, tint, frost, top, progressive) {
        HazeBlurStyle {
            // Read here, the style follows the scroll without being rebuilt
            backgroundColor(colors.background)
            blurRadius(TopBarBlur * frost * strength)
            noiseFactor(0f)
            progressive(progressive)
            colorEffects(listOf(HazeColorEffect.tint(scrollEdgeTint(color, tint * strength, top))))
        }
    }
    // At the lowest resolution: at full resolution this blur alone took about half a frame of an
    // older phone's GPU, and on a phone's screen the edge where it fades out looks the same.
    // It leaves out lens targets, as a top bar is one itself
    return measured.hazeBlur(
        input = source!!.belowLensTargets,
        style = style,
        performanceMode = HazePerformanceMode.Performance,
    )
}

/** How strongly the scroll edge blurs the content under it. */
private val TopBarBlur = 12.dp

/** How far above its bottom edge the scroll edge's blur eases out. */
private val TopBarBlurFade = 48.dp

/** How far the content scrolls before the scroll edge is fully in. */
internal val TopBarEdgeReveal = 32.dp

/** How far the scroll edge reaches below the bar, over the content. */
internal val TopBarEdgeExtend = 24.dp

/** The veil at the top of the scroll edge, of the page's background colour. */
private const val TopBarTint = 0.5f

/**
 * The unblurred top bar's veil at the screen's edge: thick enough to keep the status bar and the
 * title clear of the content under them, thin enough that the content still shows, as a tall
 * status bar takes room.
 */
private const val SolidTopBarTint = 0.85f

/**
 * A scroll edge's veil of [color], [alpha] at the [top] or bottom edge of the screen, and up to
 * [heldTo] of the way in, then gone at its inner edge by a smootherstep: no line where it ends.
 */
private fun scrollEdgeTint(color: Color, alpha: Float, top: Boolean, heldTo: Float = 0f) = Brush.verticalGradient(
    *Array(TopBarTintStops + 1) { i ->
        val t = i / TopBarTintStops.toFloat()
        val fromEdge = if (top) t else 1f - t
        val x = if (heldTo >= 1f) 0f else ((fromEdge - heldTo) / (1f - heldTo)).coerceIn(0f, 1f)
        val gone = x * x * x * (x * (x * 6f - 15f) + 10f)
        t to color.copy(alpha = alpha * (1f - gone))
    }
)

/** The translucent theme's lighter tint, which keeps the wallpaper showing through. */
private const val TranslucentTopBarTint = 0.45f

/** Stops used to draw the tint's curve as a gradient. */
private const val TopBarTintStops = 32

/**
 * What a floating top bar tells the glass of the buttons in its row: how far it has come in, from
 * bare at the top of a page to full once content is under the bar.
 */
@Immutable
internal class BarButtons(val shown: () -> Float)

/** The top bar's buttons' glass, while drawing them; null elsewhere. */
internal val LocalBarButtons = staticCompositionLocalOf<BarButtons?> { null }

/**
 * Whether what is drawn here sits on a piece of floating glass that answers presses as one piece,
 * such as a group of actions over a picture; its buttons then leave their own press feedback to it.
 */
internal val LocalPressedByGlass = staticCompositionLocalOf { false }

/** Whether the glass this sits on answers its presses, so a button on it shows none of its own. */
@Composable
internal fun pressedByGlass(): Boolean = LocalBarButtons.current != null || LocalPressedByGlass.current

/**
 * Background for a floating, rounded piece of chrome such as the navigation pill. It fades by
 * [alpha] where its owner dissolves what is on it apart from it. The content under it is frosted
 * all over, by [FloatingBlur] at the default clarity, and veiled in the page's colour only on the
 * frosted side of it.
 *
 * A [pressable] piece answers a press anywhere on it as one piece (design-system.md §8.1): it
 * swells a little towards the finger and settles back, lit where it was touched, or veiled where
 * the material cannot light. What is on it leaves its own press feedback to it. On a classic bar,
 * which is part of the page, it is pressed in as anything on the page is.
 *
 * As a [barButton] in a floating top bar's row it is always pressable, and its glass and shadow
 * come in as content scrolls under the bar, so at the top of a page the button is bare; a press
 * brings its glass up for as long as it lasts. A control with its own feedback, such as a tab row,
 * passes false.
 *
 * As a [lensTarget], it is also marked as one, for glass drawn over it, such as the bubble lifting
 * off the navigation pill; its own glass then leaves it out. [insideLensTarget] leaves it out the
 * same way for glass inside a part marked as one, such as a pill's glass laid out apart from what
 * is on it so it can swell alone.
 *
 * With [touchLight], the glass lights up from under the finger while it is pressed there, and the
 * light follows each press [touchLight] is given, without giving way as a pressed control does.
 */
@Composable
fun Modifier.floatingGlass(
    shape: Shape,
    barButton: Boolean = true,
    pressable: Boolean = false,
    alpha: Float = 1f,
    lensTarget: Boolean = false,
    insideLensTarget: Boolean = false,
    touchLight: InteractionSource? = null,
): Modifier = if (lensTarget) {
    lensTarget().floatingGlassOf(shape, barButton, pressable, alpha, onLensTarget = true, touchLight)
} else {
    floatingGlassOf(shape, barButton, pressable, alpha, onLensTarget = insideLensTarget, touchLight)
}

@OptIn(ExperimentalHazeApi::class)
@Composable
private fun Modifier.floatingGlassOf(
    shape: Shape,
    barButton: Boolean,
    pressable: Boolean,
    alpha: Float,
    onLensTarget: Boolean,
    touchLight: InteractionSource?,
): Modifier {
    val press = rememberGlassPress()
    // On a classic bar the band is the ground, and what is pressed on it is pressed in
    if (LocalOnClassicBar.current) return if (pressable) pressedIn(press, shape) else this
    if (alpha <= 0f) return this
    val bar = if (barButton) LocalBarButtons.current else null
    val answers = pressable || bar != null
    val source = currentGlassSource()
    val input = if (onLensTarget) source?.belowLensTargets else source?.input
    val tier = if (source == null) GlassTier.Solid else currentGlassTier()
    val colors = ExtendedTheme.colors
    val frost = glassFrost()
    // A row with nothing in it, as a bar without actions, has no glass: at no size its edge
    // would still draw as a dot
    var empty by remember { mutableStateOf(false) }
    // A bar button's glass comes in with the scroll, and with a press while it lasts
    val shown: () -> Float = if (bar == null) FullyShown else remember(bar, press) {
        { if (empty) 0f else max(bar.shown(), press.progress.value.coerceIn(0f, 1f)) }
    }
    val edge = if (colors.isNightMode) Color.White.copy(alpha = 0.10f)
    else Color.Black.copy(alpha = 0.06f)
    val swell: Modifier = if (answers) Modifier.swellUnder(press) else Modifier
    val chrome = if (bar == null) {
        // Glass stands off the page by its own light and refraction, so it casts no shadow; frosted
        // chrome has only a hairline and keeps its, while opaque chrome lies flat, as most of the
        // page does without glass. The layer stays: under the app's lookahead, a part without one that only moves, as the
        // keyboard moves a search field, is moved in the lookahead pass but left where it was drawn.
        if (tier == GlassTier.Glass) {
            // Cuts the glass, drawn with plain round corners, and what is on it, such as a
            // press's veil, to the shape. A mask rather than a clip: some GPUs leave the edge of
            // a clip to a path jagged
            then(swell).maskTo(shape)
        } else {
            // Faded by a layer that is always there: one added only while fading would change the
            // modifiers around the blur as it updates, which froze the page
            then(swell)
                .graphicsLayer { this.alpha = alpha }
                .shadow(elevation = if (tier == GlassTier.Blur) FloatingElevation else 0.dp, shape = shape, clip = false, ambientColor = ShadowAmbient, spotColor = ShadowSpot)
        }
    } else {
        onSizeChanged { empty = it.width == 0 || it.height == 0 }
            .then(swell)
            .graphicsLayer {
                // Only frosted chrome casts one, as other floating chrome
                shadowElevation = if (tier == GlassTier.Blur) FloatingElevation.toPx() * shown() else 0f
                this.shape = shape
                ambientShadowColor = ShadowAmbient
                spotShadowColor = ShadowSpot
            }
    }
    // Where the glass cannot light up a press, a veil shows it. Light barely shows on light glass,
    // so there the veil helps it at half strength
    val lights = tier == GlassTier.Glass && !colors.isTranslucent
    val veilShare = if (!lights) 1f else if (colors.isNightMode) 0f else LitVeilShare
    val veil = colors.text
    // Under a mouse or touchpad pointer the glass lights faintly where a press would light it, and
    // doesn't swell: it is only being pointed at. Light shows on dark glass, a veil on light
    val hover = rememberGlassHover()
    val hoverLight = if (colors.isNightMode) Color.White else veil
    val pressVeil: Modifier = if (!answers) Modifier else Modifier.glassHover(hover).drawBehind {
        val pressed = PressVeil * veilShare * press.progress.value.coerceIn(0f, 1f)
        if (pressed > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), veil.copy(alpha = pressed))
        val hovered = HoverLight * hover.value * (1f - press.progress.value.coerceIn(0f, 1f))
        if (hovered > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), hoverLight.copy(alpha = hovered))
    }
    val edged: Modifier = if (bar == null) Modifier.border(0.5.dp, edge, shape) else Modifier.drawBehind {
        drawOutline(shape.createOutline(size, layoutDirection, this), edge.copy(alpha = edge.alpha * shown()), style = Stroke(0.5.dp.toPx()))
    }
    if (colors.isTranslucent && tier != GlassTier.Solid) {
        // Over a wallpaper only a light blur, untinted, so the wallpaper still shows through
        val style = if (bar == null) TranslucentFloatingStyle else remember(shown) { TranslucentFloatingStyle.then { alpha(shown()) } }
        return chrome
            .maskTo(shape)
            .hazeBlur(input = input!!, style = style)
            .then(pressVeil)
            .then(edged)
    }
    return when (tier) {
        GlassTier.Glass -> {
            val style = remember(shape, colors.background, frost, bar, answers, touchLight != null, alpha, shown) {
                // Regular is the built-in material for surfaces that carry labels.
                GlassStyle.regular.then {
                    shape(shape.glassShape())
                    backgroundColor(colors.background)
                    lightPosition(LightAbove)
                    // Regular blurs a compact control only partly, keeping the content under it
                    // sharp; here all of it is frosted, evenly
                    optics { copy(depth = OpticalSizeValue.Fixed(1f), blurRadius = OpticalSizeValue.Fixed(FloatingBlur * frost)) }
                    if (frost > 1f) tint(colors.background.copy(alpha = FrostedFloatingTint * (frost - 1f) / (MostFrost - 1f)))
                    // Faded by its style, apart from what is on it
                    if (alpha < 1f) alpha(alpha)
                    // Read here, the glass follows the scroll without being rebuilt
                    if (bar != null) alpha(shown())
                    // Lit from under a finger moving over it, the light follows it at once rather
                    // than easing after it
                    if (!answers && touchLight != null) interactionPositionAnimationSpec(snap())
                    if (answers || touchLight != null) {
                        // Pressed, or touched where it lights from under the finger, the glass
                        // lights up where it was touched
                        pressed {
                            animate(toSpec = Motion.press(), fromSpec = Motion.release()) {
                                lightingIntensity(1f)
                                whitePointDelta(PressBrighten)
                            }
                        }
                    }
                }
            }
            chrome
                .hazeGlass(
                    input = input!!,
                    style = style,
                    interactionSource = if (answers) press.interactions else touchLight,
                    interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
                )
                .then(pressVeil)
        }

        GlassTier.Blur -> {
            val style = remember(colors.background, frost, bar, shown) {
                HazeBlurStyle {
                    backgroundColor(colors.background)
                    blurRadius(20.dp * frost)
                    noiseFactor(0f)
                    colorEffects(listOf(HazeColorEffect.tint(colors.background.copy(alpha = (0.72f * frost).coerceAtMost(MaxTint)))))
                    if (bar != null) alpha(shown())
                }
            }
            chrome
                .maskTo(shape)
                .hazeBlur(input = input!!, style = style)
                .then(pressVeil)
                .then(edged)
        }

        GlassTier.Solid -> {
            val fill = if (colors.isTranslucent) colors.chromeTint.copy(alpha = TranslucentSolidTint) else colors.background
            // Filled in its shape before the mask, which is only for what is on it
            chrome
                .then(
                    if (bar == null) Modifier.background(fill, shape) else Modifier.drawBehind {
                        drawOutline(shape.createOutline(size, layoutDirection, this), fill.copy(alpha = fill.alpha * shown()))
                    }
                )
                .maskTo(shape)
                .then(pressVeil)
                .then(edged)
        }
    }
}

/**
 * [floatingGlass] for a control floating over a picture or video: clear glass, which leaves what
 * is under it nearly as it is, over a dimming layer that keeps white icons readable on a bright
 * picture. A [pressable] one swells and lights up under a press, as floating glass does. Below
 * [GlassTier.Glass] it is [floatingGlass].
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.mediaGlass(shape: Shape, pressable: Boolean = false): Modifier {
    val source = currentGlassSource()
    if (source == null || currentGlassTier() != GlassTier.Glass) {
        return floatingGlass(shape, barButton = false, pressable = pressable)
    }
    val press = rememberGlassPress()
    val style = remember(shape, pressable) {
        GlassStyle.clear.then {
            shape(shape.glassShape())
            backgroundColor(Color.Black)
            tint(MediaDim)
            lightPosition(LightAbove)
            if (pressable) {
                pressed {
                    animate(toSpec = Motion.press(), fromSpec = Motion.release()) {
                        lightingIntensity(1f)
                        whitePointDelta(PressBrighten)
                    }
                }
            }
        }
    }
    return then(if (pressable) Modifier.swellUnder(press) else Modifier)
        .maskTo(shape)
        .hazeGlass(
            input = source.input,
            style = style,
            interactionSource = if (pressable) press.interactions else null,
            interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
        )
}

/**
 * The press on a piece of floating glass, followed from the touches on anything in it: its
 * progress, 0 at rest and 1 fully pressed, where the finger went down, and the same as
 * interactions, for the glass's own light.
 */
@Stable
private class GlassPress(private val scope: CoroutineScope) {
    val progress = Animatable(0f, visibilityThreshold = Motion.ProgressThreshold)
    var at by mutableStateOf(Offset.Unspecified)
        private set
    val interactions = MutableInteractionSource()
    private var current: PressInteraction.Press? = null
    private var pressing: Job? = null

    fun down(position: Offset) {
        at = position
        val press = PressInteraction.Press(position)
        current = press
        interactions.tryEmit(press)
        pressing = scope.launch { progress.animateTo(1f, Motion.press()) }
    }

    fun up() {
        val press = current ?: return
        current = null
        val job = pressing
        scope.launch {
            // A quick tap still shows most of its press
            withTimeoutOrNull(MinPressMillis) { job?.join() }
            interactions.emit(PressInteraction.Release(press))
            progress.animateTo(0f, Motion.release())
        }
    }
}

@Composable
private fun rememberGlassPress(): GlassPress {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassPress(scope) }
}

/** Follows the touches on this and anything in it into [press], without taking them. */
private fun Modifier.watchPresses(press: GlassPress): Modifier = pointerInput(press) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        press.down(down.position)
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        } finally {
            press.up()
        }
    }
}

/** Swells this towards where [press] went down, by [PressSwell] at full press. */
private fun Modifier.swellUnder(press: GlassPress): Modifier = watchPresses(press).graphicsLayer {
    val scale = 1f + PressSwell * press.progress.value
    scaleX = scale
    scaleY = scale
    val at = press.at
    transformOrigin = if (at.isSpecified && size.width > 0f) {
        TransformOrigin(at.x / size.width, at.y / size.height)
    } else TransformOrigin.Center
}

/**
 * The press of floating glass for chrome drawn without its material, such as a plain search field
 * standing in for glass: it swells towards the finger and a veil of the content colour covers its
 * [shape]. Apply it before the control's background, which it then covers too.
 */
@Composable
fun Modifier.floatingPress(shape: Shape): Modifier {
    val press = rememberGlassPress()
    val veil = ExtendedTheme.colors.text
    return swellUnder(press).drawWithContent {
        drawContent()
        val alpha = PressVeil * press.progress.value.coerceIn(0f, 1f)
        if (alpha > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), veil.copy(alpha = alpha))
    }
}

/**
 * Pressed in, as a control on the page is (design-system.md §8.1): its edges move in by
 * [PressedInset] and a veil of the content colour covers its [shape].
 */
@Composable
private fun Modifier.pressedIn(press: GlassPress, shape: Shape): Modifier {
    val veil = LocalContentColor.current
    return watchPresses(press)
        .graphicsLayer {
            val p = press.progress.value
            if (p != 0f) {
                val pressedScale = (1f - 2 * PressedInset.toPx() / size.maxDimension).coerceIn(0.9f, 0.985f)
                val s = 1f - (1f - pressedScale) * p
                scaleX = s
                scaleY = s
            }
        }
        .drawBehind {
            val alpha = PressVeil * press.progress.value.coerceIn(0f, 1f)
            if (alpha > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), veil.copy(alpha = alpha))
        }
}

/** How far a control on the page moves its edges in when pressed. */
private val PressedInset = 2.5.dp

/** How long a tap shows its press before letting go. */
private const val MinPressMillis = 100L

/** How much of the veil a press puts on light glass, besides lighting it. */
private const val LitVeilShare = 0.5f

/** The dimming layer under clear glass over media, as dark as a bright picture needs for white on it. */
private val MediaDim = Color.Black.copy(alpha = 0.35f)

private val FullyShown: () -> Float = { 1f }

/** How high floating glass stands over the content, by its shadow. */
private val FloatingElevation = 12.dp

/** How much floating glass swells under a press. */
private const val PressSwell = 0.06f

/** How much a press brightens floating glass, besides lighting it where it was touched. */
private const val PressBrighten = 0.06f

/** The veil a press puts on floating glass where it does not light up. */
private const val PressVeil = 0.08f

/** The light over glass under a pointer: half a press's veil. */
private const val HoverLight = PressVeil / 2

/** Whether a pointer is over a piece of glass, eased in and out. */
@Composable
private fun rememberGlassHover(): Animatable<Float, AnimationVector1D> = remember { Animatable(0f, visibilityThreshold = Motion.ProgressThreshold) }

/** Follows a pointer entering and leaving, without taking its events from what is under it. */
private fun Modifier.glassHover(hover: Animatable<Float, AnimationVector1D>): Modifier = pointerInput(hover) {
    coroutineScope {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val target = when (event.type) {
                    PointerEventType.Enter -> 1f
                    PointerEventType.Exit -> 0f
                    else -> continue
                }
                launch { hover.animateTo(target, if (target == 1f) Motion.fadeIn() else Motion.fadeOut()) }
            }
        }
    }
}

/**
 * The band behind a classic bar, across the window: the chosen material, with a hairline on its
 * edge towards the page. The top bar's comes in only once content has scrolled under it, so at
 * the top of a page the bar is the page's own ground, such as a forum's coloured header.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.classicBarGlass(top: Boolean): Modifier {
    val source = currentGlassSource()
    val tier = if (source == null) GlassTier.Solid else currentGlassTier()
    val colors = ExtendedTheme.colors
    val frost = glassFrost()
    val scrollEdge = LocalPageScroll.current
    val revealPx = with(LocalDensity.current) { TopBarEdgeReveal.toPx() }
    val line = colors.divider
    // A page that doesn't say how far it has scrolled keeps its bar
    val shown: () -> Float = { if (!top) 1f else scrollEdge?.blended { (it / revealPx).coerceIn(0f, 1f) } ?: 1f }
    val edged = graphicsLayer { alpha = shown() }.drawWithContent {
        drawContent()
        val y = if (top) size.height - ClassicBarLine.toPx() / 2 else ClassicBarLine.toPx() / 2
        drawLine(line, Offset(0f, y), Offset(size.width, y), ClassicBarLine.toPx())
    }
    return when {
        tier == GlassTier.Solid -> edged.background(
            if (colors.isTranslucent) colors.chromeTint.copy(alpha = TranslucentSolidTint) else colors.background
        )
        colors.isTranslucent -> edged.hazeBlur(input = source!!.input, style = TranslucentFloatingStyle)
        else -> {
            val style = remember(colors.background, frost) {
                HazeBlurStyle {
                    backgroundColor(colors.background)
                    blurRadius(ClassicBarBlur * frost)
                    noiseFactor(0f)
                    colorEffects(listOf(HazeColorEffect.tint(colors.background.copy(alpha = (ClassicBarTint * frost).coerceAtMost(MaxTint)))))
                }
            }
            edged.hazeBlur(input = source!!.input, style = style)
        }
    }
}

private val ClassicBarBlur = 20.dp
private const val ClassicBarTint = 0.8f
private val ClassicBarLine = 0.5.dp

/** How much floating glass blurs what is under it at the default clarity. */
private val FloatingBlur = 4.dp

/** The veil floating glass lays over what it shows at its most frosted, of the page's colour. */
private const val FrostedFloatingTint = 0.5f

/** No veil hides what is under the glass entirely. */
private const val MaxTint = 0.95f

/**
 * The one light, straight above (design-system.md §2.2), far enough that it falls evenly along a
 * capsule's top. Haze's default, the middle of each shape, lights a capsule's long edges at their
 * middles and leaves its ends dark, pinching it in. Its highlight falls on the edge away from the
 * light's bias, so the bias points down to light the top.
 */
private val LightAbove = BiasAlignment(horizontalBias = 0f, verticalBias = 8f)

@OptIn(ExperimentalHazeApi::class)
private val TranslucentFloatingStyle = HazeBlurStyle {
    backgroundColor(Color.Transparent)
    blurRadius(3.dp)
    noiseFactor(0f)
}

/** Without a blur, floating chrome on the translucent theme needs some tint to stand apart. */
private const val TranslucentSolidTint = 0.35f

/**
 * Lets glass drawn above this chrome see it along with the content behind it: a [glassLens] that
 * magnifies it, or buttons floating on a top bar. Apply before the chrome's own material so the
 * material is part of what that glass sees.
 */
@Composable
fun Modifier.lensTarget(): Modifier {
    val source = currentGlassSource()
    if (source == null || currentGlassTier() != GlassTier.Glass) return this
    return hazeSource(source.state, zIndex = LensTargetZIndex, key = source.key)
}

/**
 * A clear glass lens over chrome marked with [lensTarget], such as the bubble that lifts off the
 * navigation pill while it is pressed. Below [GlassTier.Glass] it is a veil in its shape.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.glassLens(shape: Shape, optics: GlassOptics = UnblurredClearOptics): Modifier {
    val source = currentGlassSource()
    if (source == null || currentGlassTier() != GlassTier.Glass) {
        // Without glass to look through, a veil the lens's shape follows the finger instead
        val veil = ExtendedTheme.colors.text.copy(alpha = PressVeil)
        return drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), veil) }
    }
    val background = ExtendedTheme.colors.background
    val style = remember(shape, background, optics) {
        GlassStyle.clear.then {
            shape(shape.glassShape())
            backgroundColor(background)
            // A lens magnifies what is under it; it does not frost it.
            optics(optics)
        }
    }
    // Not inside any source of this state, so it sees the content and the lens targets. A lens is
    // looked through, so it renders at full quality; it is small and only shown while pressed.
    return maskToSmooth(shape).hazeGlass(
        input = source.input,
        style = style,
        performanceMode = HazePerformanceMode.Quality,
    )
}

/**
 * Clear's optics as Haze 2.0.1 defines them, minus its shallow blur. Haze 2.0.1 keeps them
 * internal; later versions expose them as `GlassOptics.clear` for `copy(blurRadius = …)`.
 */
@OptIn(ExperimentalHazeApi::class)
private val UnblurredClearOptics = GlassOptics(
    refractionStrength = 0.85f,
    refractionHeightFraction = 0.35f,
    refractionDisplacement = 56.dp,
    refractionProfile = RefractionProfile.Edge(28.dp),
    depth = OpticalSizeValue.Fixed(1f),
    blurRadius = OpticalSizeValue.Fixed(0.dp),
    refractionDetailIntensity = 0.76f,
)

/**
 * Whether [glassLens] here is clear glass that can lift off and grow; otherwise it is a veil,
 * which should keep to the size of what was pressed.
 */
@Composable
fun isLensGlass(): Boolean = currentGlassSource() != null && currentGlassTier() == GlassTier.Glass

/** Whether glass is drawn: the device supports it and reduce transparency is off. */
@Composable
fun isGlassAvailable(): Boolean = currentGlassTier() == GlassTier.Glass

/**
 * A control's part that turns to glass while held, such as a switch's knob (design-system.md
 * §8.1): clear glass, like the navigation bar's lens, over [state], faded in by [alpha]. [state]
 * should capture everything the part can cover, transparent where the control draws nothing, so
 * what lies beyond the control shows through as it is. Only on [GlassTier.Glass].
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.controlLens(state: HazeState, shape: Shape, alpha: Float, optics: GlassOptics = ControlLensOptics): Modifier {
    if (alpha <= 0f || currentGlassTier() != GlassTier.Glass) return this
    val style = remember(shape, alpha, optics) {
        GlassStyle.clear.then {
            shape(shape.glassShape())
            backgroundColor(Color.Transparent)
            optics(optics)
            alpha(alpha)
        }
    }
    return maskToSmooth(shape).hazeGlass(input = HazeInput.Sources(state), style = style, performanceMode = HazePerformanceMode.Quality)
}

/**
 * [controlLens] for a control in floating chrome, marked with [lensTarget]: like the navigation
 * bar's lens, it sees the content behind the chrome and the chrome itself, on the page's ground,
 * so what it bends past the control's edge is the page rather than nothing. Outside chrome it is
 * [controlLens] over [fallback], which should capture the control.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.chromeControlLens(fallback: HazeState, shape: Shape, alpha: Float, optics: GlassOptics): Modifier {
    if (alpha <= 0f || currentGlassTier() != GlassTier.Glass) return this
    val source = currentGlassSource() ?: return controlLens(fallback, shape, alpha, optics)
    val background = ExtendedTheme.colors.background
    val style = remember(shape, alpha, optics, background) {
        GlassStyle.clear.then {
            shape(shape.glassShape())
            backgroundColor(background)
            optics(optics)
            alpha(alpha)
        }
    }
    return maskToSmooth(shape).hazeGlass(input = source.input, style = style, performanceMode = HazePerformanceMode.Quality)
}

/**
 * [UnblurredClearOptics] for a control a few dozen dp across: refraction only at the rim, so what is
 * under the part reads sharply, as through the navigation bar's lens.
 */
@OptIn(ExperimentalHazeApi::class)
private val ControlLensOptics = GlassOptics(
    refractionStrength = 0.5f,
    refractionHeightFraction = 0.35f,
    refractionDisplacement = 6.dp,
    refractionProfile = RefractionProfile.Edge(6.dp),
    depth = OpticalSizeValue.Fixed(1f),
    blurRadius = OpticalSizeValue.Fixed(0.dp),
    refractionDetailIntensity = 0.76f,
)

/**
 * The lens of the navigation bar, the tabs and the segmented controls, bent for a part [height]
 * tall, so they read as the same glass at each size.
 */
@OptIn(ExperimentalHazeApi::class)
fun liquidLensOptics(height: Dp): GlassOptics = UnblurredClearOptics.copy(
    refractionDisplacement = height * NavLensDisplacement,
    refractionProfile = RefractionProfile.Edge(height * NavLensRim),
)

/**
 * How far in a lens's rim reaches for what it shows, and how wide that rim is, as parts of its
 * height. A lens is a little larger than the item it lifts off, so a rim reaching much further in
 * would fold the item's label into the part that hangs past the bar.
 */
private const val NavLensDisplacement = 0.25f
private const val NavLensRim = 0.36f

/**
 * A large clear lens over [state], such as the one lifted off the icon on the about page: like
 * [controlLens], but bending what it covers from further in from its rim, as a thicker glass does.
 * Only on [GlassTier.Glass].
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.loupeLens(state: HazeState, shape: Shape): Modifier {
    if (currentGlassTier() != GlassTier.Glass) return this
    // On the page's ground, as pages leave theirs to the window and what lies under the lens would show
    val background = ExtendedTheme.colors.background
    val style = remember(shape, background) {
        GlassStyle.clear.then {
            shape(shape.glassShape())
            backgroundColor(background)
            optics(LoupeOptics)
        }
    }
    return maskToSmooth(shape).hazeGlass(input = HazeInput.Sources(state), style = style, performanceMode = HazePerformanceMode.Quality)
}

/** Clear glass, unblurred, with a deep rim for a lens a hundred dp or more across. */
@OptIn(ExperimentalHazeApi::class)
private val LoupeOptics = GlassOptics.clear.copy(
    refractionDisplacement = 40.dp,
    refractionProfile = RefractionProfile.Edge(40.dp),
    blurRadius = OpticalSizeValue.Fixed(0.dp),
)

/**
 * The shape as Haze's glass takes it, with circular corners: a smooth one lies just inside, and is
 * cut to by the glass's mask ([maskToSmooth] for a lens).
 */
private fun Shape.glassShape(): RoundedCornerShape = when (this) {
    is RoundedCornerShape -> this
    is SmoothCornerShape -> toCircular()
    else -> RoundedCornerShape(0)
}

/** Cuts a lens drawn by Haze, which knows only circular corners, to a smooth [shape]. */
private fun Modifier.maskToSmooth(shape: Shape): Modifier = if (shape is SmoothCornerShape) maskTo(shape) else this

/** Above the scaffold content, which is captured at the default zIndex of 0. */
private const val LensTargetZIndex = 1f

/**
 * Glass follows the light/dark appearance of [LocalConfiguration], but the app picks its own
 * theme independently of the system. Hosts glass in a configuration that matches the app theme.
 */
@Composable
fun ProvideGlassAppearance(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val night = ExtendedTheme.colors.isNightMode
    val adjusted = remember(configuration, night) {
        Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    }
    CompositionLocalProvider(LocalConfiguration provides adjusted, content = content)
}
