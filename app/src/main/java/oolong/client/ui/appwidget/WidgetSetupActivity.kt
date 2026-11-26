package oolong.client.ui.appwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import oolong.client.R
import oolong.client.arch.AppContent
import oolong.client.data.account.AccountRepository
import oolong.client.data.forum.FollowedForums
import oolong.client.data.forum.HomeRepository
import oolong.client.ui.designsystem.Avatar
import oolong.client.ui.designsystem.BackNavigationIcon
import oolong.client.ui.designsystem.GlassScaffold
import oolong.client.ui.designsystem.GlassTopBar
import oolong.client.ui.designsystem.GlassTopBarTitle
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.designsystem.theme.Symbols
import oolong.core.designsystem.theme.Corners
import oolong.core.designsystem.theme.Spacing
import oolong.core.designsystem.theme.TextStyles
import javax.inject.Inject

/**
 * Sets a widget up as it is added, or again later: which forum the forum widget shows, or
 * which forums the forums widget shows.
 */
@AndroidEntryPoint
class WidgetSetupActivity : AppCompatActivity() {
    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var home: HomeRepository

    @Inject
    lateinit var followedForums: FollowedForums

    private val appWidgetId by lazy {
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out leaves the widget unplaced, as the system expects of a setup
        setResult(Activity.RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val forumsWidget = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
            ?.provider?.className == ForumsWidgetReceiver::class.java.name
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            AppContent(this, accountRepository) {
                if (forumsWidget) SourcePage() else ForumPage()
            }
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun <T> save(key: Preferences.Key<T>, value: T) {
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@WidgetSetupActivity)
            val glanceId = manager.getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetSetupActivity, PreferencesGlanceStateDefinition, glanceId) {
                it.toMutablePreferences().apply { this[key] = value }
            }
            if (key == ForumsWidget.SourceKey) ForumsWidget().update(this@WidgetSetupActivity, glanceId)
            else ForumHotWidget().update(this@WidgetSetupActivity, glanceId)
            setResult(Activity.RESULT_OK, resultIntent())
            finish()
        }
    }

    @Composable
    private fun Page(title: String, content: @Composable (PaddingValues) -> Unit) {
        GlassScaffold(
            topBar = {
                GlassTopBar(
                    title = { GlassTopBarTitle(text = title) },
                    navigationIcon = { BackNavigationIcon(onBackPressed = { finish() }) },
                )
            },
            content = content,
        )
    }

    @Composable
    private fun SourcePage() {
        Page(stringResource(R.string.appwidget_setup_forums)) { padding ->
            LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                item {
                    ChoiceRow(stringResource(R.string.appwidget_source_followed)) { save(ForumsWidget.SourceKey, ForumsSource.Followed.name) }
                }
                item {
                    ChoiceRow(stringResource(R.string.appwidget_source_visited)) { save(ForumsWidget.SourceKey, ForumsSource.Visited.name) }
                }
            }
        }
    }

    @Composable
    private fun ForumPage() {
        // The forums the user follows, then any visited lately they don't follow
        var forums by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        LaunchedEffect(Unit) {
            // The visited ones are at hand at once; the followed ones come from the server
            val visited = home.visitedForums.first().map { it.name to it.avatar }
            forums = visited
            val uid = home.account.first()
            val followed = if (uid.isNotEmpty()) runCatching { followedForums.query.reload(uid) }.getOrDefault(emptyList()) else emptyList()
            forums = (followed.map { it.name to it.avatar } + visited).distinctBy { it.first }
        }
        var typed by rememberSaveable { mutableStateOf("") }
        Page(stringResource(R.string.appwidget_setup_forum)) { padding ->
            LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                item { NameField(typed, onChange = { typed = it }, onDone = { if (typed.isNotBlank()) save(ForumHotWidget.ForumNameKey, typed.trim().removeSuffix("吧")) }) }
                items(forums, key = { it.first }) { (name, avatar) ->
                    ChoiceRow(stringResource(R.string.appwidget_forum_title, name), avatar) { save(ForumHotWidget.ForumNameKey, name) }
                }
            }
        }
    }

    @Composable
    private fun ChoiceRow(text: String, avatar: String? = null, onClick: () -> Unit) {
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
            if (avatar != null) Avatar(data = avatar, size = 36.dp, contentDescription = null, shape = Corners.sm)
            Text(text = text, style = TextStyles.bodyCompact, color = colors.text)
        }
    }

    @Composable
    private fun NameField(value: String, onChange: (String) -> Unit, onDone: () -> Unit) {
        val colors = ExtendedTheme.colors
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .background(colors.inset, Corners.sm)
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
