package oolong.client.data.link

import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassifier
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import oolong.client.MainActivity
import oolong.client.data.link.QuickPreviewUtil
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.util.TiebaUtil
import oolong.client.util.clipBoardManager
import java.util.regex.Pattern

open class ClipBoardLink(
    val url: String,
)

class ClipBoardForumLink(
    url: String,
    val forumName: String,
) : ClipBoardLink(url)

class ClipBoardThreadLink(
    url: String,
    val threadId: String,
) : ClipBoardLink(url)

/**
 * Offers to open a Tieba link the user copied elsewhere, once for each copy: looked at when the
 * app comes to the front, and only read when the clipboard holds something new that may be a
 * link. What was looked at is kept across launches, so the same copy is never offered again.
 */
object ClipBoardLinkDetector {
    private val mutablePreviewInfoStateFlow = MutableStateFlow<QuickPreviewUtil.PreviewInfo?>(null)
    val previewInfoStateFlow
        get() = mutablePreviewInfoStateFlow.asStateFlow()

    /** Whether the app has come to the front since the clipboard was last looked at. */
    private var due = false

    /** Loading what the offered link shows, which is stopped once the offer is answered. */
    private var loading: Job? = null

    /** The app came to the front: the clipboard is looked at once the window has focus. */
    fun onStart() {
        due = true
    }

    /** The offer was answered or dismissed. */
    fun consume() {
        loading?.cancel()
        loading = null
        mutablePreviewInfoStateFlow.value = null
    }

    /**
     * Looks at the clipboard if the app came to the front since it last did. Called once the
     * window has focus, as without it the system gives apps nothing from the clipboard.
     */
    fun check(activity: MainActivity) {
        if (!due) return
        due = false
        if (!Settings.of(activity)[SettingKeys.ClipboardLinks]) return
        val manager = activity.clipBoardManager
        val description = manager.primaryClipDescription ?: return
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) && !description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)) return
        val seen = activity.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
        // Each copy has its own time; before Android 8 the text itself tells copies apart
        val stamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) description.timestamp.toString() else null
        if (stamp != null && seen.getString(SeenKey, null) == stamp) return
        // What the app copied itself, such as a thread's link, is not offered back
        val unlikelyLink = description.label == TiebaUtil.ClipLabel ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            description.classificationStatus == ClipDescription.CLASSIFICATION_COMPLETE &&
            description.getConfidenceScore(TextClassifier.TYPE_URL) < UrlConfidence
        if (unlikelyLink && stamp != null) {
            seen.edit { putString(SeenKey, stamp) }
            return
        }
        val text = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() ?: return
        val key = stamp ?: text.hashCode().toString()
        if (stamp == null && seen.getString(SeenKey, null) == key) return
        seen.edit { putString(SeenKey, key) }
        if (unlikelyLink) return
        val url = UrlPattern.matcher(text).takeIf { it.find() }?.group() ?: return
        val link = parseLink(url) ?: return
        // Collected once, not again each time the app comes back, which would offer it again
        loading?.cancel()
        loading = activity.lifecycleScope.launch {
            QuickPreviewUtil.getPreviewInfoFlow(activity, link).collect { mutablePreviewInfoStateFlow.value = it }
        }
    }

    private fun isTiebaDomain(host: String?): Boolean {
        return host != null && (host.equals("wapp.baidu.com", ignoreCase = true) ||
            host.equals("tieba.baidu.com", ignoreCase = true) ||
            host.equals("tiebac.baidu.com", ignoreCase = true))
    }

    private fun parseLink(url: String): ClipBoardLink? {
        val uri = url.toUri()
        if (!isTiebaDomain(uri.host)) {
            return null
        }
        val path = uri.path
        return when {
            path.isNullOrEmpty() -> null
            path.startsWith("/p/") -> ClipBoardThreadLink(url, path.substring(3))
            path.equals("/f", ignoreCase = true) || path.equals("/mo/q/m", ignoreCase = true) -> {
                val kw = uri.getQueryParameter("kw")
                val word = uri.getQueryParameter("word")
                val kz = uri.getQueryParameter("kz")
                kw?.let { ClipBoardForumLink(url, it) }
                    ?: (word?.let { ClipBoardForumLink(url, it) }
                        ?: kz?.let { ClipBoardThreadLink(url, it) })
            }

            else -> ClipBoardLink(url)
        }
    }

    private const val Prefs = "clipboard_link"
    private const val SeenKey = "seen"

    /** How sure the system must be that a copy holds a link for it to be read. */
    private const val UrlConfidence = 0.5f

    private val UrlPattern = Pattern.compile(
        "((http|https)://)(([a-zA-Z0-9._-]+\\.[a-zA-Z]{2,6})|([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}))(:[0-9]{1,4})*(/[a-zA-Z0-9&%_./-~-]*)?",
    )
}
