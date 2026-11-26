package oolong.client.ui.landing

import android.content.Intent
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.SPLASH_ICON_MILLIS
import oolong.client.data.legacy.LegacyImport
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.diagnostics.UsageStats
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.LoginKey
import oolong.client.ui.designsystem.AlertDialog
import oolong.client.ui.designsystem.Aurora
import oolong.client.ui.designsystem.AuroraState
import oolong.client.ui.designsystem.Button
import oolong.client.ui.designsystem.DialogState
import oolong.client.ui.designsystem.TextButton
import oolong.client.ui.designsystem.condense
import oolong.client.ui.designsystem.rememberDialogState
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.settings.about.LensCenter
import oolong.client.ui.settings.about.LensRadius
import oolong.client.util.findActivity
import oolong.client.util.toastShort
import oolong.core.designsystem.theme.LocalReduceMotion
import oolong.core.designsystem.theme.TextStyles
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class Stage {
    /** First launch: the name, and a way in. */
    Welcome,

    /** The old app's data coming in; the app starts again when it is in. */
    Importing,

    /** Started again after an import. */
    Done,

    Gone,
}

/**
 * The page shown over everything on first launch, and once after an import: the mark and the
 * name come out of slowly moving light, then the way in. Leaving it fades it away over the home
 * page, which is already there under it.
 */
@Composable
fun LandingHost() {
    val context = LocalContext.current
    var stage by rememberSaveable {
        mutableStateOf(
            when {
                LegacyImport.consumeImported(context) -> Stage.Done
                !Settings.of(context)[SettingKeys.LandingShown] -> Stage.Welcome
                else -> Stage.Gone
            }
        )
    }
    if (stage == Stage.Gone) return
    Landing(stage = stage, onStage = { stage = it })
}

@Composable
private fun Landing(stage: Stage, onStage: (Stage) -> Unit) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val dark = ExtendedTheme.colors.isNightMode
    val paper = painterResource(R.drawable.ic_mark_paper)
    val state = remember { LandingState() }

    var source by remember { mutableStateOf(LegacyImport.source(context)) }
    // Back from the system's uninstall screen, the old app may be gone
    LifecycleResumeEffect(Unit) {
        source = LegacyImport.source(context)
        onPauseOrDispose {}
    }

    // Usage statistics are asked on the welcome only; after an import the update's own question comes later.
    // Checked to start with: they carry nothing that tells devices apart, and can be turned off here or in settings
    var usageStats by rememberSaveable { mutableStateOf(true) }
    val usageStatsDialog = rememberDialogState()
    UsageStatsContentDialog(usageStatsDialog)

    fun leave(then: () -> Unit = {}) {
        if (state.leaving) return
        state.leaving = true
        if (stage == Stage.Welcome) {
            Settings.of(context)[SettingKeys.UsageStats] = usageStats
            Settings.of(context)[SettingKeys.UsageStatsAsked] = true
            if (usageStats) UsageStats.sendSoon(context)
        }
        then()
        scope.launch {
            state.leave.animateTo(1f, Leave)
            Settings.of(context)[SettingKeys.LandingShown] = true
            onStage(Stage.Gone)
        }
    }

    val export = rememberLauncherForActivityResult(LegacyImport.Export) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        onStage(Stage.Importing)
        scope.launch {
            val ok = state.import { LegacyImport.receive(context, uri) }
            val activity = context.findActivity()
            if (ok && activity != null) {
                LegacyImport.restart(activity)
            } else {
                context.toastShort(R.string.toast_import_legacy_failed)
                onStage(Stage.Welcome)
                state.enter(this, waitForSplash = false, quick = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        // A stage that comes back with the activity is already shown
        if (stage == Stage.Importing) return@LaunchedEffect
        state.enter(this, waitForSplash = true, quick = stage == Stage.Done)
    }
    // The light holds still with motion reduced
    val reduced = LocalReduceMotion.current
    LaunchedEffect(reduced) { if (!reduced) state.aurora.drift() }

    val text = ExtendedTheme.colors.text
    val textSecondary = ExtendedTheme.colors.textSecondary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - state.leave.value }
            // Nothing reaches the page under it; a finger draws the light a little towards it
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach {
                            state.aurora.touch(it.position)
                            it.consume()
                        }
                    }
                }
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            Aurora(state.aurora, Modifier.fillMaxSize(), dark)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Canvas(
                    modifier = Modifier
                        .size(MarkSize)
                        .onGloballyPositioned { state.mark = it }
                        .condense({ state.paper.value }, blur = 28.dp, scale = .96f)
                        // The icon floats a little above the light, its shadow tinted with its own blue
                        .shadow(
                            elevation = 28.dp,
                            shape = CircleShape,
                            ambientColor = if (dark) Color.Black.copy(alpha = .5f) else MarkShadow.copy(alpha = .45f),
                            spotColor = if (dark) Color.Black.copy(alpha = .7f) else MarkShadow.copy(alpha = .8f),
                        ),
                ) {
                    with(paper) { draw(size) }
                }
                // At least as tall as the name and the line under it, so the mark stays put between stages
                Box(Modifier.heightIn(min = TextsHeight).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    when (stage) {
                        Stage.Importing -> Text(
                            text = stringResource(R.string.landing_importing),
                            modifier = Modifier.padding(top = 40.dp).condense({ state.progress.value }, blur = 6.dp, rise = 4.dp),
                            style = TextStyles.body.copy(color = textSecondary),
                        )

                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(if (stage == Stage.Done) R.string.landing_imported else R.string.app_name),
                                modifier = Modifier
                                    .padding(top = 32.dp)
                                    .condense({ state.name.value }, blur = 16.dp, rise = 8.dp, fadeFaster = 1.15f),
                                style = if (stage == Stage.Done) DoneTitle.copy(color = text) else Name.copy(color = text),
                            )
                            Text(
                                text = stringResource(if (stage == Stage.Done) R.string.landing_imported_body else R.string.landing_slogan),
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .condense({ state.tag.value }, blur = 8.dp, rise = 6.dp),
                                style = TextStyles.body.copy(color = textSecondary),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1.4f))
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .condense({ state.actions.value }, blur = 4.dp, rise = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val legacy = source == LegacyImport.Source.Ready
                    when (stage) {
                        Stage.Welcome -> {
                            if (source != LegacyImport.Source.None) {
                                Text(
                                    text = stringResource(if (legacy) R.string.landing_import_hint else R.string.tip_import_legacy_update),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    style = TextStyles.caption.copy(color = textSecondary, textAlign = TextAlign.Center),
                                )
                            }
                            UsageStatsConsent(
                                checked = usageStats,
                                onCheckedChange = { usageStats = it },
                                onView = { usageStatsDialog.show() },
                            )
                            if (legacy) {
                                Action(stringResource(R.string.landing_import), primary = true) { export.launch(Unit) }
                                Action(stringResource(R.string.landing_skip_import), primary = false) { leave() }
                            } else {
                                Action(stringResource(R.string.title_login), primary = true) { leave { navigator.navigate(LoginKey) } }
                                Action(stringResource(R.string.landing_skip_login), primary = false) { leave() }
                            }
                        }

                        Stage.Done -> {
                            Action(stringResource(R.string.landing_start), primary = true) { leave() }
                            if (source != LegacyImport.Source.None) {
                                Action(stringResource(R.string.landing_uninstall_legacy), primary = false) {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", LegacyImport.PACKAGE, null)))
                                    }
                                }
                            } else {
                                Spacer(Modifier.height(ActionHeight))
                            }
                        }

                        else -> Spacer(Modifier.height(ActionHeight * 2 + 6.dp))
                    }
                }
            }
        }
        Lens(state)
    }
}

/** Whether to send usage statistics, unticked until the person ticks it; what is sent opens in a dialog. */
@Composable
private fun UsageStatsConsent(checked: Boolean, onCheckedChange: (Boolean) -> Unit, onView: () -> Unit) {
    val colors = ExtendedTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = colors.emphasis, uncheckedColor = colors.textSecondary),
        )
        Column(modifier = Modifier.padding(top = 12.dp, end = 8.dp)) {
            Text(text = stringResource(R.string.title_usage_stats), style = TextStyles.label.copy(color = colors.text))
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.usage_stats_note))
                    withLink(LinkAnnotation.Clickable("view", TextLinkStyles(SpanStyle(color = colors.accent))) { onView() }) {
                        append(stringResource(R.string.usage_stats_view))
                    }
                },
                style = TextStyles.caption.copy(color = colors.textSecondary),
            )
        }
    }
}

/** What usage statistics send, as 诊断信息 lists it. */
@Composable
internal fun UsageStatsContentDialog(state: DialogState) {
    AlertDialog(
        dialogState = state,
        confirmText = stringResource(R.string.button_ok),
        title = { Text(text = stringResource(R.string.diagnostics_usage_stats)) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.diagnostics_daily to R.string.diagnostics_daily_fields,
                    R.string.diagnostics_monthly to R.string.diagnostics_monthly_fields,
                    R.string.diagnostics_never to R.string.diagnostics_never_fields,
                ).forEach { (title, fields) ->
                    Column {
                        Text(text = stringResource(title), style = TextStyles.label)
                        Text(text = stringResource(fields), style = TextStyles.caption.copy(color = ExtendedTheme.colors.textSecondary))
                    }
                }
            }
        },
    )
}

/**
 * The mark's lens, the launcher icon's own, over the paper. It is drawn apart from the paper so it
 * can come in on its own.
 */
@Composable
private fun Lens(state: LandingState) {
    val density = LocalDensity.current
    val painter = painterResource(R.drawable.ic_mark_lens)
    val reduced = LocalReduceMotion.current
    val root = remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { root.value = it })
    Box(
        modifier = Modifier
            .offset {
                val mark = state.mark
                val parent = root.value
                if (mark == null || parent == null || !mark.isAttached || !parent.isAttached) return@offset IntOffset.Zero
                val origin = parent.localPositionOf(mark, Offset.Zero)
                // It settles onto the paper from a little way up and to the right; with motion
                // reduced it only fades in where it rests
                val drift = if (reduced) 0f else (1f - state.lens.value) * with(density) { 10.dp.toPx() }
                IntOffset((origin.x + drift).roundToInt(), (origin.y - drift).roundToInt())
            }
            .size(MarkSize)
            .condense({ state.lens.value }, blur = 20.dp, fadeFaster = 1.2f)
            .drawBehind {
                val unit = size.width / 100f
                val center = LensCenter * unit
                val radius = LensRadius * unit
                // While importing, the glass fills like a cup, white as the paper, under its surface
                val fill = state.fill.value
                if (fill > 0f) {
                    clipPath(Path().apply { addOval(Rect(center, radius)) }) {
                        val top = center.y + radius - radius * 2 * fill
                        val wave = sin(state.aurora.time * 4f) * radius * .06f
                        val left = center.x - radius
                        val right = center.x + radius
                        val surface = Path().apply {
                            moveTo(left, top + wave)
                            quadraticTo(center.x, top - wave * 2, right, top - wave)
                            lineTo(right, center.y + radius)
                            lineTo(left, center.y + radius)
                            close()
                        }
                        drawPath(surface, Brush.verticalGradient(listOf(Color(0xE6FFFFFF), Color(0xB3DCE7F8)), startY = top, endY = center.y + radius))
                    }
                }
                with(painter) { draw(size) }
            },
    )
}

/** A full-width button of the page's two: the way in, filled, and the other, its label only. */
@Composable
private fun Action(text: String, primary: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth()
    if (primary) {
        Button(onClick = onClick, modifier = modifier, minHeight = ActionHeight) { Text(text, style = ActionLabel) }
    } else {
        TextButton(onClick = onClick, modifier = modifier, color = ExtendedTheme.colors.text) { Text(text, style = ActionLabel.copy(fontWeight = FontWeight.Normal)) }
    }
}

/** The light behind the page: soft colours drifting slowly, opening out of the middle at first. */

@Stable
private class LandingState {
    val aurora = AuroraState(spread = 0f)
    val paper = Animatable(0f)
    val lens = Animatable(0f)
    val name = Animatable(0f)
    val tag = Animatable(0f)
    val actions = Animatable(0f)
    val progress = Animatable(0f)
    val fill = Animatable(0f)
    val leave = Animatable(0f)

    var leaving = false
    var mark: LayoutCoordinates? by mutableStateOf(null)

    /**
     * The entrance: the light opens, the paper comes out of it, the lens settles on it, then the
     * words and the way in. After the splash, so none of it plays under the icon; [quick] when
     * the page was seen a moment ago, as after the restart that ends an import.
     */
    fun enter(scope: CoroutineScope, waitForSplash: Boolean, quick: Boolean) {
        scope.launch {
            if (waitForSplash) {
                delay((Process.getStartUptimeMillis() + SPLASH_ICON_MILLIS - SystemClock.uptimeMillis()).coerceAtLeast(0))
            }
            val steps = if (quick) QuickSteps else Steps
            launch { aurora.spread.animateTo(1f, Spread) }
            launch {
                delay(steps[0])
                paper.animateTo(1f, Emerge)
            }
            launch {
                delay(steps[1])
                lens.animateTo(1f, Drift)
            }
            launch {
                delay(steps[2])
                name.animateTo(1f, Emerge)
            }
            launch {
                delay(steps[3])
                tag.animateTo(1f, Rise)
            }
            launch {
                delay(steps[4])
                actions.animateTo(1f, Rise)
            }
        }
    }

    /**
     * The words and buttons go, the glass fills while [work] runs, for long enough to be seen
     * whatever its speed; returns what [work] did.
     */
    suspend fun import(work: suspend () -> Boolean): Boolean = coroutineScope {
        launch { actions.animateTo(0f, Leave) }
        launch { tag.animateTo(0f, Leave) }
        launch { name.animateTo(0f, Leave) }
        launch {
            delay(300)
            progress.animateTo(1f, Rise)
        }
        val result = async { runCatching { work() }.getOrDefault(false) }
        fill.animateTo(1f, tween(FillMillis))
        result.await().also { ok ->
            if (!ok) {
                // Failed, the importing line fades away with the fill rather than vanishing
                launch { progress.animateTo(0f, Leave) }
                fill.animateTo(0f, Leave)
            }
        }
    }
}

/** Critically damped, as slow as [response] seconds: seen once, the page takes its time. */
private fun <T> slow(response: Float): AnimationSpec<T> {
    val omega = 2 * PI.toFloat() / response
    return spring(dampingRatio = 1f, stiffness = omega * omega)
}

private val Spread = spring<Float>(dampingRatio = 1f, stiffness = 1.21f)
private val Emerge = slow<Float>(1.5f)
private val Drift = slow<Float>(1.9f)
private val Rise = slow<Float>(1.2f)
private val Leave = slow<Float>(.6f)

/** When the paper, lens, name, words and buttons start, in milliseconds. */
private val Steps = longArrayOf(350, 650, 1150, 1750, 2150)
private val QuickSteps = longArrayOf(100, 300, 800, 1200, 1550)
private const val FillMillis = 2200

private val MarkSize = 120.dp
private val MarkShadow = Color(0xFF2F57B0)
private val ActionHeight = 52.dp

/** The name, its gap and the line under it. */
private val TextsHeight = 32.dp + 56.dp + 8.dp + 28.dp
private val Name = TextStyle(fontSize = 46.sp, lineHeight = 56.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
private val DoneTitle = TextStyle(fontSize = 30.sp, lineHeight = 56.sp, fontWeight = FontWeight.SemiBold)
private val ActionLabel = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
