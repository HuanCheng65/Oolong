package oolong.client.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.util.UUID

/**
 * One page on the back stack. [id] tells apart two entries for the same [key], such as a thread
 * opened again from its author's profile, so that each keeps its own state.
 */
@Serializable
data class Page(val key: Key, val id: String = UUID.randomUUID().toString()) : NavKey

private val BackStackConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) { subclass(Page::class, Page.serializer()) }
    }
}

/**
 * The app's back stack, starting at [MainKey] with [linked] above it when the app was opened by
 * a link. It survives process death, when [linked] is not applied again.
 */
@Composable
fun rememberAppBackStack(linked: Key? = null, alone: Boolean = false): NavBackStack<NavKey> =
    rememberNavBackStack(
        BackStackConfiguration,
        // A window opened on one page holds that page alone, closing as it is left
        *(if (alone && linked != null) listOf(Page(linked)) else listOfNotNull(Page(MainKey), linked?.let(::Page))).toTypedArray(),
    )

val NavKey.key: Key
    get() = (this as Page).key

/** How a page shows: filling the window, or over the pages below it. */
enum class Presentation { Page, Sheet }

val Key.presentation: Presentation
    get() = when (this) {
        is SubPostsSheetKey -> Presentation.Sheet
        else -> Presentation.Page
    }

/**
 * Navigates by changing [backStack]. Pages get the navigator from [forPage], which acts for the
 * page wherever it shows: with two panes the page on the left is not the top of the stack.
 */
class BackStackNavigator(private val backStack: NavBackStack<NavKey>) : Navigator {
    /**
     * Whether two panes show. Sub-posts then open as a page beside the thread, where there is
     * room for them, instead of a sheet over both panes.
     */
    var twoPane = false

    override fun navigate(key: Key) = navigate(key, from = null)

    override fun navigateUp() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** The navigator for [page]: what it opens goes above it, and leaving closes it. */
    fun forPage(page: Page): Navigator = object : Navigator {
        override fun navigate(key: Key) = navigate(key, from = page)

        override fun navigateUp() = close(page)
    }

    private fun navigate(target: Key, from: Page?) {
        // A page already closed may still be sliding away, and a quick second tap may come
        // before the page it opened shows: neither opens anything more
        if (from != null && from !in backStack) return
        if (backStack.lastOrNull()?.key == target) return
        val key = if (twoPane && target is SubPostsSheetKey) target.asPage() else target
        if (key.presentation == Presentation.Page) {
            // A list opens pages in place of what it opened before, so back leaves the list in
            // one step instead of retracing every thread read from it. With one pane the list is
            // on top and nothing is above it
            if (from != null && from.key.paneRole == PaneRole.List && key.paneRole == PaneRole.Detail) {
                val index = backStack.indexOf(from)
                if (index >= 0) {
                    while (backStack.lastIndex > index) backStack.removeAt(backStack.lastIndex)
                }
            }
            // A page opened from a sheet takes the sheet's place, so back leads past it
            while (backStack.size > 1 && backStack.last().key.presentation == Presentation.Sheet) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
        backStack.add(Page(key))
    }

    /** Removes [page], and for a list also the pages opened beside it, which go with it. */
    private fun close(page: Page) {
        val index = backStack.indexOf(page)
        if (index <= 0) return
        var end = index + 1
        if (page.key.paneRole == PaneRole.List) {
            while (end < backStack.size && backStack[end].key.paneRole != PaneRole.List) end++
        }
        repeat(end - index) { backStack.removeAt(index) }
    }
}
