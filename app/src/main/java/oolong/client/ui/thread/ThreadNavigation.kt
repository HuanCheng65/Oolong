package oolong.client.ui.thread

import oolong.client.data.thread.ThreadQuery
import oolong.client.data.thread.ThreadSort
import oolong.client.model.ThreadSummary
import oolong.client.model.threadId
import oolong.client.navigation.Navigator
import oolong.client.navigation.ThreadKey

/**
 * Opens [thread]. The page shows what the list knows about it until its first page loads, as the
 * list keeps the thread in the app's cache.
 */
fun Navigator.navigateToThread(thread: ThreadSummary, key: ThreadKey = thread.key()) {
    navigate(key)
}

/** Where opening [this] thread from a list goes. */
fun ThreadSummary.key() = ThreadKey(threadId, forum.id)

/** What the page opened at this key asks for. */
fun ThreadKey.query() = ThreadQuery(
    threadId = threadId,
    sort = ThreadSort.entries.firstOrNull { it.value == sortType } ?: ThreadSort.OldestFirst,
    seeLz = seeLz,
    forumId = forumId,
    fromStore = from == ThreadPageFrom.FROM_STORE,
    offline = offline,
)
