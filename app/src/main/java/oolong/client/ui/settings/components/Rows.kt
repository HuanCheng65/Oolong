package oolong.client.ui.settings.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.map
import oolong.client.data.settings.Setting
import oolong.client.data.settings.Settings
import oolong.client.data.settings.rememberSettings
import oolong.client.data.settings.settingAsState
import oolong.client.navigation.Key
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.ui.designsystem.choiceMenuItems
import oolong.client.ui.designsystem.clickMenu
import oolong.client.ui.designsystem.menuAnchor
import oolong.client.ui.designsystem.rememberMenuTriggerState
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.ui.settings.LeadingIcon
import oolong.client.ui.settings.components.prefs.PrefsChevron
import oolong.client.ui.settings.components.prefs.PrefsChoiceValue
import oolong.client.ui.settings.components.prefs.PrefsExternal
import oolong.client.ui.settings.components.prefs.PrefsLinkValue
import oolong.client.ui.settings.components.prefs.widgets.EditTextPref
import oolong.client.ui.settings.components.prefs.widgets.ListPref
import oolong.client.ui.settings.components.prefs.widgets.SwitchPref
import oolong.client.ui.settings.components.prefs.widgets.TextPref
import oolong.client.ui.settings.components.prefs.widgets.TimePickerPerf

/** What a row's action has at hand: the page's state, and what to act with. */
class ActionScope<S>(val state: S, val context: Context, val navigator: Navigator, val settings: Settings)

/** [setting]'s value, following its changes. */
@Composable
fun <T> stored(setting: Setting<T>): T = settingAsState(setting).value

/** The setting stored as [key], one of a group read together, or [default] while there is none. */
@Composable
fun <T> stored(key: Preferences.Key<T>, default: T): T {
    val settings = rememberSettings()
    return remember(settings, key) { settings.all.map { it[key] ?: default } }.collectAsState(initial = settings.all.value[key] ?: default).value
}

/**
 * Declares a group's rows. Every row takes, besides its own parameters:
 * - `available`: whether it exists here at all (see [Availability]); search obeys it too
 * - `shown`: whether it shows now, given the other settings; search ignores it
 * - `aliases`: a string of more words search finds it by
 * - `searchable`: off for rows search shouldn't offer (docs/settings-declarative.md §4)
 */
@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeUiApi::class)
@SettingsDsl
class GroupScope<S> internal constructor() {
    internal val items = mutableListOf<SettingsItem<S>>()

    private fun row(
        @StringRes title: Int?,
        kind: SettingsRow.Kind,
        available: Availability,
        shown: (@Composable S.() -> Boolean)?,
        @StringRes aliases: Int?,
        searchable: Boolean,
        target: Key? = null,
        content: @Composable S.(Modifier) -> Unit,
    ) {
        items += SettingsRow(title, kind, available, shown, aliases, searchable, content, target)
    }

    /** A [setting] that is on or off. */
    fun switch(
        setting: Setting<Boolean>,
        @StringRes title: Int,
        summary: (@Composable S.(on: Boolean) -> String?)? = null,
        enabled: @Composable S.() -> Boolean = { true },
        onChange: (ActionScope<S>.(Boolean) -> Unit)? = null,
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = true,
    ) = row(title, SettingsRow.Kind.Value, available, shown, aliases, searchable) { modifier ->
        val state = this
        val scope = actionScope(state)
        SwitchPref(
            setting = setting,
            title = stringResource(id = title),
            modifier = modifier,
            summary = { on -> summary?.invoke(state, on) },
            onCheckedChange = onChange?.let { change -> { on -> scope.change(on) } },
            enabled = enabled(state),
        )
    }

    /** [setting] as one of [options]: values with their names. */
    fun choice(
        setting: Setting<String>,
        @StringRes title: Int,
        vararg options: Pair<String, Int>,
        enabled: @Composable S.() -> Boolean = { true },
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = true,
    ) = row(title, SettingsRow.Kind.Value, available, shown, aliases, searchable) { modifier ->
        ListPref(
            setting = setting,
            title = stringResource(id = title),
            modifier = modifier,
            entries = options.associate { (value, name) -> value to stringResource(id = name) },
            useSelectedAsSummary = true,
            enabled = enabled(this),
        )
    }

    /**
     * One of [options], kept however [value] reads it and [onSelect] writes it, for a choice that
     * isn't one stored string.
     */
    fun <T> choice(
        @StringRes title: Int,
        options: List<Pair<T, Int>>,
        value: @Composable S.() -> T,
        onSelect: ActionScope<S>.(T) -> Unit,
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = true,
    ) = row(title, SettingsRow.Kind.Value, available, shown, aliases, searchable) { modifier ->
        val scope = actionScope(this)
        val current = value(this)
        val named = options.map { (option, name) -> option to stringResource(id = name) }
        val menu = rememberMenuTriggerState()
        // Chosen in a menu growing out of the value, as a stored choice is
        Box(modifier = Modifier.clickMenu(state = menu, items = { choiceMenuItems(named, current) { scope.onSelect(it) } })) {
            TextPref(
                title = stringResource(id = title),
                modifier = modifier,
                enabled = true,
                trailingContent = {
                    PrefsChoiceValue(text = named.firstOrNull { it.first == current }?.second.orEmpty(), modifier = Modifier.menuAnchor(menu))
                },
            )
        }
    }

    /** A row that opens page [to], with the [value] it is set to beside it, if any. */
    fun link(
        @StringRes title: Int,
        to: Key,
        icon: ImageVector? = null,
        value: (@Composable S.() -> String?)? = null,
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = true,
    ) = row(title, SettingsRow.Kind.Link, available, shown, aliases, searchable, target = to) { modifier ->
        val navigator = LocalNavigator.current
        val shownValue = value?.invoke(this)
        TextPref(
            title = stringResource(id = title),
            modifier = modifier,
            leadingIcon = icon?.let { { LeadingIcon { Icon(imageVector = it, contentDescription = null) } } },
            onClick = { navigator.navigate(to) },
            trailingContent = if (shownValue != null) ({ PrefsLinkValue(text = shownValue) }) else ({ PrefsChevron() }),
        )
    }

    /**
     * A row that does something: [onClick]. [external] marks one that leaves the app, for the
     * system's settings or a web page; [more] one that goes on to a dialog; [destructive] colours
     * one that deletes.
     */
    fun action(
        @StringRes title: Int,
        summary: (@Composable S.() -> String?)? = null,
        value: (@Composable S.() -> String?)? = null,
        external: Boolean = false,
        more: Boolean = false,
        destructive: Boolean = false,
        enabled: @Composable S.() -> Boolean = { true },
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = true,
        onClick: ActionScope<S>.() -> Unit,
    ) = row(title, SettingsRow.Kind.Action, available, shown, aliases, searchable) { modifier ->
        val scope = actionScope(this)
        val shownValue = value?.invoke(this)
        TextPref(
            title = stringResource(id = title),
            modifier = modifier,
            summary = summary?.invoke(this),
            onClick = { scope.onClick() },
            enabled = enabled(this),
            textColor = if (destructive) MaterialTheme.colors.error else ExtendedTheme.colors.text,
            trailingContent = when {
                external -> ({ PrefsExternal(text = shownValue) })
                shownValue != null -> ({ Text(text = shownValue) })
                more -> ({ PrefsChevron() })
                else -> null
            },
        )
    }

    /** What something is, shown and not set: the last time something ran, a size. */
    fun info(
        @StringRes title: Int,
        value: @Composable S.() -> String,
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        searchable: Boolean = false,
    ) = row(title, SettingsRow.Kind.Info, available, shown, aliases = null, searchable) { modifier ->
        val shownValue = value(this)
        TextPref(
            title = stringResource(id = title),
            modifier = modifier,
            darkenOnDisable = false,
            trailingContent = { Text(text = shownValue) },
        )
    }

    /** A time of day, [setting] as "HH:mm". */
    fun time(
        setting: Setting<String>,
        @StringRes title: Int,
        enabled: @Composable S.() -> Boolean = { true },
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = true,
    ) = row(title, SettingsRow.Kind.Value, available, shown, aliases, searchable) { modifier ->
        val text = stringResource(id = title)
        TimePickerPerf(
            setting = setting,
            title = text,
            modifier = modifier,
            dialogTitle = text,
            enabled = enabled(this),
        )
    }

    /** A line of text, [setting], with a [summary] under its title. */
    fun text(
        setting: Setting<String?>,
        @StringRes title: Int,
        summary: (@Composable S.() -> String?)? = null,
        @StringRes dialogTitle: Int = title,
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = true,
    ) = row(title, SettingsRow.Kind.Value, available, shown, aliases, searchable) { modifier ->
        EditTextPref(
            setting = setting,
            title = stringResource(id = title),
            modifier = modifier,
            summary = summary?.invoke(this),
            dialogTitle = stringResource(id = dialogTitle),
        )
    }

    /**
     * A row drawn its own way, as a slider or a picker. Its [title] names it for search; without
     * one it isn't searched. One that [opens] a page is found as that page, as a link is.
     */
    fun custom(
        @StringRes title: Int?,
        opens: Key? = null,
        available: Availability = Availability.Always,
        shown: (@Composable S.() -> Boolean)? = null,
        @StringRes aliases: Int? = null,
        searchable: Boolean = title != null,
        content: @Composable S.(Modifier) -> Unit,
    ) = row(title, SettingsRow.Kind.Custom, available, shown, aliases, searchable, target = opens, content = content)

    /** A note under the group, outside its block; it goes after the group's rows wherever it is declared. */
    fun footer(text: @Composable S.() -> String) {
        items += SettingsFooter(text)
    }
}

@Composable
private fun <S> actionScope(state: S) = ActionScope(state, LocalContext.current, LocalNavigator.current, rememberSettings())
