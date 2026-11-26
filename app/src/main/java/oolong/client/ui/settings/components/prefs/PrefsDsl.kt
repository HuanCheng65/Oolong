package oolong.client.ui.settings.components.prefs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Receiver scope which is used by [PrefsScreen].
 */
interface PrefsScope {
    /**
     * Adds a setting. Give a [key] that stays with it when settings above it come and go, as
     * rows shown only under a condition do, or the state of one row passes to another.
     *
     * A keyed setting not [shown] stays in the list, closed: it opens and closes in place as its
     * condition changes, and what follows moves rather than jumps.
     */
    fun prefsItem(key: Any? = null, shown: Boolean = true, content: @Composable PrefsScope.() -> Unit)

    /**
     * Adds a group of Prefs with a title.
     *
     * @param title Group header text. Will be shown above the list of Prefs
     * @param items All the prefs in this group
     */
    fun prefsGroup(title: String, items: PrefsScope.() -> Unit)

    /**
     * Adds a group of Prefs. This overload is intended for passing in a [GroupHeader] if you want more control over the header.
     *
     * @param header Group header. Will be shown above the list of Prefs
     * @param items All the prefs in this group
     */
    fun prefsGroup(header: @Composable PrefsScope.() -> Unit, items: PrefsScope.() -> Unit)

    /** Adds a note under the settings before it, outside their block, for what their titles cannot say. */
    fun prefsFooter(text: @Composable () -> String)
}

internal class PrefsScopeImpl : PrefsScope {

    private var _headerIndexes: MutableList<Int> = mutableListOf()
    val headerIndexes: List<Int> get() = _headerIndexes

    private var _footerIndexes: MutableList<Int> = mutableListOf()
    val footerIndexes: List<Int> get() = _footerIndexes

    private var _prefsItems: MutableList<PrefsItem> = mutableListOf()
    val prefsItems: List<PrefsItem> get() = _prefsItems

    override fun prefsItem(key: Any?, shown: Boolean, content: @Composable PrefsScope.() -> Unit) {
        _prefsItems.add(
            PrefsItem(
                content = { @Composable { content() } },
                isSetting = true,
                key = key,
                shown = shown || key == null,
            )
        )
    }

    /** Adds a group's header or the space after it, which are not settings themselves. */
    private fun addChrome(content: @Composable () -> Unit) {
        _prefsItems.add(PrefsItem(content = { content }, isSetting = false))
    }

    override fun prefsGroup(title: String, items: PrefsScope.() -> Unit) {
        // Exceptions to when divider should be drawn
        // - if last item
        // - if next item starts a new group
        // - if current current item is header of new group
        // - if current item is end of group

        // add header index so we know where each group starts
        _headerIndexes.add(this.prefsItems.size)

        addChrome { GroupHeader(title) }

        // add all children to hierarchy
        this.apply(items)

        addChrome { Spacer(modifier = Modifier.height(GroupSpacing)) }

        // add totalSize -2/-1 to footerIndexes as that is the index of the last item added and the spacer respectively
        _footerIndexes.add(this.prefsItems.size - 2)
        _footerIndexes.add(this.prefsItems.size - 1)
    }

    override fun prefsGroup(
        header: @Composable PrefsScope.() -> Unit,
        items: PrefsScope.() -> Unit
    ) {
        // add header index so we know where each group starts
        _headerIndexes.add(this.prefsItems.size)

        addChrome { header() }

        // add all children to hierarchy
        this.apply(items)

        addChrome { Spacer(modifier = Modifier.height(GroupSpacing)) }

        // add totalSize -2/-1 to footerIndexes as that is the index of the last item added and the spacer respectively
        _footerIndexes.add(this.prefsItems.size - 2)
        _footerIndexes.add(this.prefsItems.size - 1)
    }

    override fun prefsFooter(text: @Composable () -> String) {
        addChrome { PrefsFooter(text()) }
    }

    fun getPrefsItem(index: Int): @Composable () -> Unit {
        val prefsItem = prefsItems[index]
        return prefsItem.content.invoke(this, index)
    }
}

internal class PrefsItem(
    val content: PrefsScope.(index: Int) -> @Composable () -> Unit,
    /** Whether it is a setting, which [PrefsScreen] sets in its group's block, rather than a header or a gap. */
    val isSetting: Boolean,
    /** What the setting is known by in composition; its place in the list when null. */
    val key: Any? = null,
    /** Whether the setting shows now; one that doesn't is closed, or closing. */
    val shown: Boolean = true,
)

private val GroupSpacing = 8.dp
