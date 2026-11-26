package oolong.client.ui.settings.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import oolong.client.R
import oolong.client.data.settings.rememberSettings
import oolong.client.model.Agree
import oolong.client.model.ForumSummary
import oolong.client.model.Post
import oolong.client.model.PostContent
import oolong.client.model.SubPost
import oolong.client.model.TextSpan
import oolong.client.model.ThreadSummary
import oolong.client.model.User
import oolong.client.navigation.FontSizeKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.navigation.ProvideNavigator
import oolong.client.ui.components.FeedCard
import oolong.client.ui.components.LocalAccount
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.MyBackHandler
import oolong.client.ui.designsystem.Slider
import oolong.client.ui.designsystem.Switch
import oolong.client.ui.designsystem.maskTo
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.LocalTextScale
import oolong.client.ui.designsystem.theme.TextScale
import oolong.client.ui.designsystem.theme.textScaleChoice
import oolong.client.ui.designsystem.theme.textScaledDensity
import oolong.client.ui.settings.components.SettingsContent
import oolong.client.ui.settings.components.SettingsHeader
import oolong.client.ui.settings.components.SettingsPage
import oolong.client.ui.settings.components.SettingsScope
import oolong.client.ui.settings.components.prefs.widgets.TextPref
import oolong.client.ui.thread.PostCardOptions
import oolong.client.ui.thread.PostItem
import oolong.client.ui.thread.ThreadFloor
import oolong.core.designsystem.theme.Corners
import kotlin.math.roundToInt

/**
 * The text size being chosen on the page: whether the system's is followed, and the step chosen
 * for when it isn't. The page shows it at once and saves it on leaving, so the app isn't laid
 * out again at every step.
 */
@Stable
class FontSizeDraft(followSystem: Boolean, step: Int) {
    var followSystem by mutableStateOf(followSystem)
    var step by mutableIntStateOf(step)

    companion object {
        val Saver = listSaver<FontSizeDraft, Any>(
            save = { listOf(it.followSystem, it.step) },
            restore = { FontSizeDraft(it[0] as Boolean, it[1] as Int) },
        )
    }
}

private val LocalFontSizeDraft = staticCompositionLocalOf<FontSizeDraft> { error("The text size page provides its draft") }

/** The text size page's draft, and the system's own size it may follow. */
class FontSizeState(val draft: FontSizeDraft, val systemScale: Float) {
    /** The size the draft comes to. */
    val scale: Float get() = if (draft.followSystem) systemScale else TextScale.Steps[draft.step]
}

/**
 * The app's text size: the system's, or one of [TextScale.Steps]. A post shows it as it is
 * chosen; dragging the slider stops following the system, and following it again goes back.
 */
object FontSizeSettings : SettingsPage<FontSizeState>(R.string.title_custom_font_size, FontSizeKey) {
    @Composable
    override fun rememberState() = FontSizeState(LocalFontSizeDraft.current, LocalConfiguration.current.fontScale)

    override fun SettingsScope<FontSizeState>.content() {
        group(SettingsHeader.Custom { FontSizePreview(scale) }) {}
        group(SettingsHeader.Gap(0.dp)) {
            custom(R.string.title_follow_system_font_size) { FollowSystemRow(this) }
        }
        group(SettingsHeader.Gap(8.dp)) {
            // The page's own title names it for search
            custom(null) { FontSizeSlider(this) }
        }
    }
}

@Composable
fun FontSizePage(navigator: Navigator = LocalNavigator.current) {
    val settings = rememberSettings()
    val initial = remember(settings) { settings.all.value.textScaleChoice() }
    val draft = rememberSaveable(saver = FontSizeDraft.Saver) {
        FontSizeDraft(initial.followSystem, TextScale.stepOf(initial.scale))
    }

    fun leave() {
        navigator.navigateUp()
        val step = TextScale.Steps[draft.step]
        if (draft.followSystem != initial.followSystem || step != initial.scale) {
            settings.edit(TextScale.FollowSystemKey, TextScale.ScaleKey) {
                it[TextScale.FollowSystemKey] = draft.followSystem
                it[TextScale.ScaleKey] = step
            }
        }
    }
    MyBackHandler(enabled = true) { leave() }

    ProvideNavigator(navigator) {
        CompositionLocalProvider(LocalFontSizeDraft provides draft) {
            GlassScaffold(
                largeTitle = true,
                backgroundColor = Color.Transparent,
                topBar = {
                    GlassTopBar(
                        title = { GlassTopBarTitle(text = stringResource(id = FontSizeSettings.title)) },
                        navigationIcon = { BackNavigationIcon(onBackPressed = { leave() }) },
                    )
                },
            ) { padding ->
                SettingsContent(FontSizeSettings, contentPadding = padding)
            }
        }
    }
}

/** Whether the system's size is followed, and what it is, as a percentage: makers name their steps differently. */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FollowSystemRow(state: FontSizeState) {
    val draft = state.draft
    TextPref(
        title = stringResource(id = R.string.title_follow_system_font_size),
        summary = stringResource(id = R.string.summary_system_font_size, percent(state.systemScale)),
        onClick = { draft.followSystem = !draft.followSystem },
        trailingContent = { Switch(checked = draft.followSystem, onCheckedChange = null) },
    )
}

/**
 * The size, from small A to large A over [TextScale.Steps]. While following the system it rests
 * at the system's size, between steps if that falls between them; dragging it chooses a step and
 * stops following. A mark under it shows where the system's size is, and a longer tick the default.
 */
@Composable
private fun FontSizeSlider(state: FontSizeState) {
    val draft = state.draft
    val colors = ExtendedTheme.colors
    val last = TextScale.Steps.lastIndex
    val label = if (draft.followSystem) {
        stringResource(id = R.string.font_size_following, percent(state.systemScale))
    } else {
        stringResource(id = R.string.font_size_step, stringResource(id = TextScale.nameOf(draft.step)), percent(state.scale))
    }
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(id = R.string.title_custom_font_size),
                color = colors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(text = label, color = colors.textSecondary, fontSize = 14.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "A", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = if (draft.followSystem) positionOf(state.systemScale) else draft.step.toFloat(),
                    onValueChange = { position ->
                        draft.followSystem = false
                        draft.step = position.roundToInt().coerceIn(0, last)
                    },
                    valueRange = 0f..last.toFloat(),
                    steps = last - 1,
                )
                Ticks(systemAt = positionOf(state.systemScale) / last)
            }
            Text(text = "A", color = colors.textSecondary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** A tick under each step, a longer one under the default, and the word for the system's size under where it is. */
@Composable
private fun Ticks(systemAt: Float) {
    val colors = ExtendedTheme.colors
    val default = TextScale.Steps.indexOf(1f)
    val last = TextScale.Steps.lastIndex
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = KnobHalfWidth)) {
        val travel = maxWidth
        TextScale.Steps.indices.forEach { step ->
            Box(
                modifier = Modifier
                    .offset(x = travel * (step.toFloat() / last) - 1.dp)
                    .size(width = 2.dp, height = if (step == default) 8.dp else 4.dp)
                    .background(colors.text.copy(alpha = if (step == default) 0.4f else 0.2f), Corners.full),
            )
        }
        Text(
            text = stringResource(id = R.string.font_size_system_mark),
            color = colors.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = travel * systemAt - 12.dp),
        )
    }
}

/** Where [scale] falls along the steps, between two of them if it isn't one. */
private fun positionOf(scale: Float): Float {
    val steps = TextScale.Steps
    if (scale <= steps.first()) return 0f
    if (scale >= steps.last()) return steps.lastIndex.toFloat()
    val upper = steps.indexOfFirst { it >= scale }
    val lower = upper - 1
    return lower + (scale - steps[lower]) / (steps[upper] - steps[lower])
}

private fun percent(scale: Float) = (scale * 100).roundToInt()

/** A thread in a list and a floor of it, as they show at [scale]. Only a picture: taps do nothing. */
@Composable
private fun FontSizePreview(scale: Float) {
    val colors = ExtendedTheme.colors
    val account = LocalAccount.current
    val me = User(
        id = 1,
        name = account?.name.orEmpty(),
        nameShow = account?.nameShow ?: account?.name ?: stringResource(id = R.string.app_name),
        portrait = account?.portrait.orEmpty(),
    )
    val app = User(id = 2, name = "", nameShow = stringResource(id = R.string.app_name), portrait = "")
    val title = stringResource(id = R.string.font_size_preview_title)
    val body = stringResource(id = R.string.font_size_preview_body)
    val reply = stringResource(id = R.string.font_size_preview_reply)
    val forum = stringResource(id = R.string.app_name)
    val now = remember { System.currentTimeMillis() / 1000 }
    val thread = remember(me, title, body) {
        ThreadSummary(
            id = 1,
            forum = ForumSummary(id = 1, name = forum, avatar = ""),
            title = title,
            author = me,
            createTime = now,
            abstract = body,
            firstPostId = 1,
            replyCount = 1,
            agree = Agree(count = 0, agreed = false),
        )
    }
    val floor = remember(app, me, reply) {
        PostItem(
            Post(
                id = 2,
                floor = 2,
                title = "",
                time = now,
                author = app,
                content = text(reply),
                agree = Agree(count = 0, agreed = false),
                subPosts = persistentListOf(SubPost(id = 3, author = me, content = text(body), time = now)),
                subPostCount = 1,
            )
        )
    }
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(PreviewHeight)
            .border(1.dp, colors.divider, Corners.lg)
            .maskTo(Corners.lg),
    ) {
        CompositionLocalProvider(
            LocalDensity provides textScaledDensity(density.density, scale),
            LocalTextScale provides scale,
        ) {
            Column(
                modifier = Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                },
            ) {
                FeedCard(thread = thread, onClick = {}, onAgree = {}, showForum = true)
                ThreadFloor(
                    item = floor,
                    options = PostCardOptions(threadAuthorId = 1),
                    onEvent = {},
                    listState = rememberLazyListState(),
                    key = "Preview",
                    startsRule = true,
                )
            }
        }
        // The preview runs on past its box, fading out
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(40.dp)
                .background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0f), colors.background))),
        )
    }
}

private fun text(value: String) = persistentListOf<PostContent>(PostContent.Text(persistentListOf(TextSpan.Plain(value))))

private val PreviewHeight = 380.dp
private val KnobHalfWidth = 15.dp
