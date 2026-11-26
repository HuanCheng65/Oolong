package oolong.client.ui.appwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.arch.AppContent
import oolong.client.data.account.AccountRepository
import oolong.client.data.forum.FollowedForums
import oolong.client.data.forum.HomeRepository
import oolong.client.data.search.SearchRepository
import oolong.client.ui.appwidget.ForumHotWidget.Companion.toShows
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.Button
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.SegmentedControl
import oolong.client.ui.designsystem.maskTo
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.util.StringUtil.getShortNumString
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Radius
import oolong.core.designsystem.theme.SmoothCornerShape
import oolong.core.designsystem.theme.Spacing
import oolong.core.designsystem.theme.TextStyles
import javax.inject.Inject

/**
 * Sets a widget up as it is added, or again later: which forum the forum widget shows and what
 * of it, or which forums the forums widget shows. The widget itself shows at the top, drawn as it
 * will be, and follows each choice; nothing is kept until the button at the bottom is pressed.
 */
@AndroidEntryPoint
class WidgetSetupActivity : AppCompatActivity() {
    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var home: HomeRepository

    @Inject
    lateinit var followedForums: FollowedForums

    @Inject
    lateinit var search: SearchRepository

    private val appWidgetId by lazy {
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    /** Setting up a forum widget before it is placed, from the app's widget settings: there is no widget yet. */
    private val pinning by lazy { intent.getBooleanExtra(EXTRA_PIN, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out leaves the widget unplaced, as the system expects of a setup
        setResult(Activity.RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !pinning) {
            finish()
            return
        }
        val forumsWidget = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
            ?.provider?.className == ForumsWidgetReceiver::class.java.name
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            AppContent(this, accountRepository) {
                if (forumsWidget) ForumsSetup() else ForumSetup()
            }
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private suspend fun state(): Preferences {
        if (pinning) return emptyPreferences()
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        return getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId)
    }

    /** Keeps [values] for the widget, draws it again and closes. */
    private fun save(vararg values: Pair<Preferences.Key<String>, String>, forums: Boolean) {
        if (pinning) {
            // The launcher asks the user where to put it; the setup is kept once it is placed
            val forum = values.firstOrNull { it.first == ForumHotWidget.ForumNameKey }?.second ?: return
            val shows = values.firstOrNull { it.first == ForumHotWidget.ShowsKey }?.second.toShows()
            WidgetPins.pinForum(this, forum, shows)
            finish()
            return
        }
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetSetupActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetSetupActivity, PreferencesGlanceStateDefinition, glanceId) {
                it.toMutablePreferences().apply { values.forEach { (key, value) -> this[key] = value } }
            }
            if (forums) ForumsWidget().update(this@WidgetSetupActivity, glanceId) else ForumHotWidget().update(this@WidgetSetupActivity, glanceId)
            setResult(Activity.RESULT_OK, resultIntent())
            finish()
        }
    }

    /** A forum to choose: followed ones carry the user's level, searched ones their member count. */
    private data class Choice(val name: String, val avatar: String, val level: Int = 0, val members: Long = 0)

    @Composable
    private fun ForumSetup() {
        var name by rememberSaveable { mutableStateOf<String?>(null) }
        var shows by rememberSaveable { mutableStateOf(ForumShows.Latest.name) }
        var editing by rememberSaveable { mutableStateOf(false) }
        var wide by rememberSaveable { mutableStateOf(true) }
        var query by rememberSaveable { mutableStateOf("") }
        var followed by remember { mutableStateOf<List<Choice>>(emptyList()) }
        var visited by remember { mutableStateOf<List<Choice>>(emptyList()) }
        LaunchedEffect(Unit) {
            val state = state()
            state[ForumHotWidget.ForumNameKey]?.let {
                if (name == null) name = it
                editing = true
            }
            state[ForumHotWidget.ShowsKey]?.let { shows = it }
            // The visited ones are at hand at once; the followed ones come from the server
            visited = home.visitedForums.first().map { Choice(it.name, it.avatar) }
            val uid = home.account.first()
            if (uid.isNotEmpty()) {
                followed = runCatching { followedForums.query.reload(uid) }.getOrDefault(emptyList()).map { Choice(it.name, it.avatar, level = it.level) }
            }
        }
        // Searched for a moment after typing stops, so each letter doesn't send a request
        val found by produceState<List<Choice>>(emptyList(), query) {
            val keyword = query.trim().removeSuffix("吧")
            if (keyword.isEmpty()) {
                value = emptyList()
                return@produceState
            }
            delay(SearchDelayMillis)
            val matches = runCatching { search.forums(keyword) }.getOrNull()
            value = listOfNotNull(matches?.exact).plus(matches?.fuzzy.orEmpty()).map { Choice(it.name, it.avatar, members = it.memberCount) }
        }
        val choices = run {
            val keyword = query.trim().removeSuffix("吧")
            if (keyword.isEmpty()) {
                // The one chosen first, then the followed forums, then those only visited
                val chosen = name?.let { chosen -> (followed + visited).firstOrNull { it.name == chosen } ?: Choice(chosen, "") }
                (listOfNotNull(chosen) + followed + visited).distinctBy { it.name }
            } else {
                // The followed forums whose names match first, as they are the likely ones
                (followed.filter { it.name.contains(keyword, ignoreCase = true) } + found).distinctBy { it.name }
            }
        }
        val forumShows = shows.toShows()
        val preview by produceState<Shown<ForumHotWidget.Loaded>?>(null, name, forumShows) {
            val chosen = name ?: return@produceState
            value = Shown(value?.value, loading = true)
            value = Shown(ForumHotWidget.load(this@WidgetSetupActivity, chosen, forumShows, fresh = true), loading = false)
        }
        val previewSize = if (wide) WideSize else NarrowSize
        // Typing, the field goes up under the top bar, so the results have the room below it
        val listState = rememberLazyListState()
        val searching = query.isNotBlank()
        // Again once the results come, when there is the room to go up into
        LaunchedEffect(searching, found.isNotEmpty()) {
            if (searching) listState.animateScrollToItem(SearchFieldIndex)
        }
        SetupPage(
            listState = listState,
            title = stringResource(R.string.appwidget_forum_name),
            button = stringResource(if (editing) R.string.appwidget_setup_done else R.string.appwidget_setup_add),
            buttonEnabled = name != null,
            onButton = { name?.let { save(Pair(ForumHotWidget.ForumNameKey, it), Pair(ForumHotWidget.ShowsKey, forumShows.name), forums = false) } },
        ) {
            item {
                PreviewStage(previewSize, preview, name) {
                    ForumHotWidget().Content(name, AppWidgetManager.INVALID_APPWIDGET_ID, preview ?: Shown(null, loading = true), signing = false)
                }
            }
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), contentAlignment = Alignment.Center) {
                    SegmentedControl(
                        options = persistentListOf("4×2", "2×2"),
                        selected = if (wide) 0 else 1,
                        onSelect = { wide = it == 0 },
                        modifier = Modifier.width(160.dp),
                    )
                }
            }
            item { GroupTitle(stringResource(R.string.appwidget_setup_shows)) }
            item {
                Card(first = true, last = true) {
                    SegmentedControl(
                        options = persistentListOf(
                            stringResource(R.string.tab_forum_latest),
                            stringResource(R.string.tab_forum_hot),
                            stringResource(R.string.tab_forum_good),
                        ),
                        selected = forumShows.ordinal,
                        onSelect = { shows = ForumShows.entries[it].name },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    )
                }
            }
            item { GroupTitle(stringResource(R.string.appwidget_setup_forum)) }
            item {
                Card(first = true, last = choices.isEmpty()) {
                    SearchField(query, onChange = { query = it })
                }
            }
            itemsIndexed(choices, key = { _, it -> it.name }) { index, choice ->
                Card(first = false, last = index == choices.lastIndex) {
                    ForumRow(choice, chosen = choice.name == name) { name = choice.name }
                }
            }
        }
    }

    @Composable
    private fun ForumsSetup() {
        var source by rememberSaveable { mutableStateOf(ForumsSource.Followed.name) }
        LaunchedEffect(Unit) {
            state()[ForumsWidget.SourceKey]?.let { source = it }
        }
        val widget = remember { ForumsWidget() }
        val chosen = with(widget) { source.toSource() }
        val preview by produceState<Shown<ForumsWidget.Loaded>?>(null, chosen) {
            value = Shown(value?.value, loading = true)
            value = Shown(widget.load(this@WidgetSetupActivity, chosen, fresh = true), loading = false)
        }
        SetupPage(
            title = stringResource(R.string.appwidget_forums_name),
            // Added without setting up from Android 12 on, so it is almost always edited here
            button = stringResource(R.string.appwidget_setup_done),
            buttonEnabled = true,
            onButton = { save(Pair(ForumsWidget.SourceKey, chosen.name), forums = true) },
        ) {
            item {
                PreviewStage(WideSize, preview, chosen) {
                    widget.Content(chosen, preview ?: Shown(null, loading = true))
                }
            }
            item { GroupTitle(stringResource(R.string.appwidget_setup_forums)) }
            val options = listOf(
                Triple(ForumsSource.Followed, R.string.appwidget_source_followed, R.string.appwidget_source_followed_note),
                Triple(ForumsSource.Visited, R.string.appwidget_source_visited, R.string.appwidget_source_visited_note),
            )
            itemsIndexed(options) { index, (option, title, note) ->
                Card(first = index == 0, last = index == options.lastIndex) {
                    RadioRow(stringResource(title), stringResource(note), selected = option == chosen) { source = option.name }
                }
            }
        }
    }

    /** The page: its list under a top bar, and the button that keeps the choices floating at the bottom. */
    @Composable
    private fun SetupPage(
        title: String,
        button: String,
        buttonEnabled: Boolean,
        onButton: () -> Unit,
        listState: LazyListState = rememberLazyListState(),
        content: LazyListScope.() -> Unit,
    ) {
        val colors = ExtendedTheme.colors
        Box(modifier = Modifier.fillMaxSize()) {
            GlassScaffold(
                topBar = {
                    GlassTopBar(
                        title = { GlassTopBarTitle(text = title) },
                        navigationIcon = { BackNavigationIcon(onBackPressed = { finish() }) },
                    )
                },
            ) { padding ->
                // Above the keyboard as well, so search results under the field stay in view
                val keyboard = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = maxOf(padding.calculateBottomPadding() + 96.dp, keyboard)),
                    modifier = Modifier.fillMaxSize(),
                    content = content,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background, colors.background)))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = Spacing.sm),
            ) {
                Button(onClick = onButton, enabled = buttonEnabled, modifier = Modifier.fillMaxWidth()) {
                    Text(text = button, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    /** The widget as it will look, on a neutral wash standing in for the wallpaper. */
    @Composable
    private fun PreviewStage(size: DpSize, shown: Any?, vararg keys: Any?, content: @Composable () -> Unit) {
        val night = ExtendedTheme.colors.isNightMode
        val wash = if (night) listOf(Color(0xFF10151F), Color(0xFF1B2131), Color(0xFF2A2033)) else listOf(Color(0xFFCFDCF2), Color(0xFFE9D9EF), Color(0xFFF4E3D3))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = Spacing.xs)
                .fillMaxWidth()
                .height(StageHeight)
                .background(Brush.linearGradient(wash), SmoothCornerShape(Radius.lg)),
        ) {
            WidgetPreview(size, shown, *keys, content = content)
        }
    }

    /** [content], a widget's, drawn by Glance into the views a launcher would show; taps go nowhere. */
    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    @Composable
    private fun WidgetPreview(size: DpSize, vararg keys: Any?, content: @Composable () -> Unit) {
        val context = LocalContext.current
        val views by produceState<RemoteViews?>(null, size, *keys) {
            value = runCatching { GlanceRemoteViews().compose(context, size) { content() }.remoteViews }.getOrNull()
        }
        Box(modifier = Modifier.size(size)) {
            AndroidView(
                factory = { FrameLayout(it) },
                update = { frame ->
                    frame.removeAllViews()
                    // Inflated as a launcher would, without AppCompat swapping in its own views,
                    // which RemoteViews refuses; and in the system's light or dark, as widgets are
                    views?.let { frame.addView(it.apply(frame.context.applicationContext, frame)) }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    },
            )
        }
    }

    @Composable
    private fun GroupTitle(text: String) {
        Text(
            text = text,
            color = ExtendedTheme.colors.textSecondary,
            style = TextStyles.caption,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = Spacing.md, bottom = Spacing.xs),
        )
    }

    /** One stretch of a card: cards are made of their items, rounded at the first and last. */
    @Composable
    private fun Card(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
        val shape = SmoothCornerShape(Radius.md, top = first, bottom = last)
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .background(ExtendedTheme.colors.chip, shape)
                .then(if (first || last) Modifier.maskTo(shape) else Modifier)
                .padding(top = if (first) 4.dp else 0.dp, bottom = if (last) 4.dp else 0.dp),
        ) {
            content()
        }
    }

    @Composable
    private fun SearchField(value: String, onChange: (String) -> Unit) {
        val colors = ExtendedTheme.colors
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .background(colors.background.copy(alpha = 0.6f), Corners.sm)
                .padding(horizontal = Spacing.sm),
        ) {
            Icon(Symbols.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(Spacing.xs))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) Text(text = stringResource(R.string.appwidget_setup_search), style = TextStyles.bodyCompact, color = colors.textSecondary)
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyles.bodyCompact.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    @Composable
    private fun ForumRow(choice: Choice, chosen: Boolean, onClick: () -> Unit) {
        val colors = ExtendedTheme.colors
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 56.dp)
                .padding(horizontal = Spacing.md),
        ) {
            Avatar(data = choice.avatar, size = 34.dp, contentDescription = null, shape = Corners.sm)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.appwidget_forum_title, choice.name), style = TextStyles.bodyCompact, color = colors.text)
                if (choice.members > 0) {
                    Text(
                        text = stringResource(R.string.appwidget_member_count, choice.members.getShortNumString()),
                        style = TextStyles.caption,
                        color = colors.textSecondary,
                    )
                }
            }
            if (choice.level > 0) {
                Text(
                    text = stringResource(R.string.appwidget_level, choice.level),
                    style = TextStyles.caption,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                    modifier = Modifier.background(colors.background.copy(alpha = 0.6f), Corners.xs).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (chosen) {
                    Box(modifier = Modifier.size(22.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Symbols.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun RadioRow(title: String, note: String, selected: Boolean, onClick: () -> Unit) {
        val colors = ExtendedTheme.colors
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = TextStyles.bodyCompact, color = colors.text)
                Text(text = note, style = TextStyles.caption, color = colors.textSecondary)
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .then(if (selected) Modifier.background(colors.accent, CircleShape) else Modifier.border(2.dp, colors.textSecondary.copy(alpha = 0.5f), CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(modifier = Modifier.size(8.dp).background(colors.onAccent, CircleShape))
            }
        }
    }

    companion object {
        /** Set up a forum widget to be placed, rather than one already on the home screen. */
        const val EXTRA_PIN = "oolong.client.extra.WIDGET_PIN"

        private const val SearchDelayMillis = 300L

        /** Where the forum group starts in the forum widget's list: after the preview, sizes and shows. */
        private const val SearchFieldIndex = 4
        private val StageHeight = 220.dp

        /** A widget four cells by two, and two by two, about as large as on a phone. */
        private val WideSize = DpSize(330.dp, 170.dp)
        private val NarrowSize = DpSize(165.dp, 170.dp)
    }
}
