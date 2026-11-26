package oolong.client.data.link

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import oolong.client.R
import oolong.client.data.link.ClipBoardForumLink
import oolong.client.data.link.ClipBoardLink
import oolong.client.data.link.ClipBoardThreadLink
import oolong.client.data.link.linkPreviewRepository
import oolong.client.util.StringUtil

object QuickPreviewUtil {
    private fun isTiebaUrl(host: String?): Boolean {
        return host != null && (host.equals("wapp.baidu.com", ignoreCase = true) ||
            host.equals("tieba.baidu.com", ignoreCase = true) ||
            host.equals("tiebac.baidu.com", ignoreCase = true))
    }

    @JvmStatic
    fun isForumUrl(uri: Uri?): Boolean {
        if (uri == null || uri.host == null || uri.path == null) {
            return false
        }
        val path = uri.path
        val kw = uri.getQueryParameter("kw")
        val word = uri.getQueryParameter("word")
        return (path.equals("/f", ignoreCase = true) || path.equals(
            "/mo/q/m",
            ignoreCase = true
        )) &&
            kw != null || word != null
    }

    @JvmStatic
    fun isThreadUrl(uri: Uri?): Boolean {
        if (uri == null || uri.host == null || uri.path == null) {
            return false
        }
        val path = uri.path
        val kz = uri.getQueryParameter("kz")
        return (path.equals("/f", ignoreCase = true) || path.equals(
            "/mo/q/m",
            ignoreCase = true
        )) &&
            kz != null || path!!.startsWith("/p/")
    }

    @JvmStatic
    fun getForumName(uri: Uri?): String? {
        if (uri == null || uri.host == null || uri.path == null) {
            return null
        }
        val path = uri.path
        val kw = uri.getQueryParameter("kw")
        val word = uri.getQueryParameter("word")
        if (path.equals("/f", ignoreCase = true) || path.equals("/mo/q/m", ignoreCase = true)) {
            if (kw != null) {
                return kw
            } else if (word != null) {
                return word
            }
        }
        return null
    }

    private fun getThreadPreviewInfoFlow(
        context: Context,
        link: ClipBoardThreadLink,
        lifeCycle: Lifecycle? = null,
    ): Flow<PreviewInfo> =
        flow { emit(linkPreviewRepository(context).thread(link.threadId.toLong())) }
            .map {
                PreviewInfo(
                    clipBoardLink = link,
                    url = link.url,
                    title = it.title,
                    subtitle = context.getString(R.string.subtitle_quick_preview_thread, it.forumName, it.replyCount.toString()),
                    icon = Icon(StringUtil.getAvatarUrl(it.authorPortrait))
                )
            }
            .catch { it.printStackTrace() }
            .apply {
                if (lifeCycle != null) {
                    flowWithLifecycle(lifeCycle)
                }
            }

    private fun getForumPreviewInfoFlow(
        context: Context,
        link: ClipBoardForumLink,
        lifeCycle: Lifecycle? = null,
    ): Flow<PreviewInfo> =
        flow { emit(linkPreviewRepository(context).forum(link.forumName)) }
            .map {
                PreviewInfo(
                    clipBoardLink = link,
                    url = link.url,
                    title = context.getString(
                        R.string.title_forum,
                        link.forumName
                    ),
                    subtitle = it.slogan,
                    icon = Icon(it.avatar)
                )
            }
            .catch { it.printStackTrace() }
            .apply {
                if (lifeCycle != null) {
                    flowWithLifecycle(lifeCycle)
                }
            }

    fun getPreviewInfoFlow(
        context: Context,
        clipBoardLink: ClipBoardLink,
        lifeCycle: Lifecycle? = null,
    ): Flow<PreviewInfo?> {
        val detailFlow = when (clipBoardLink) {
            is ClipBoardForumLink -> getForumPreviewInfoFlow(context, clipBoardLink, lifeCycle)
            is ClipBoardThreadLink -> getThreadPreviewInfoFlow(context, clipBoardLink, lifeCycle)
            else -> null
        }
        val flow = flowOf(
            PreviewInfo(
                clipBoardLink = clipBoardLink,
                url = clipBoardLink.url,
                title = clipBoardLink.url,
                subtitle = context.getString(R.string.subtitle_link),
                icon = Icon(R.drawable.ic_link)
            )
        )
        return listOfNotNull(flow, detailFlow).merge()
            .apply { if (lifeCycle != null) flowWithLifecycle(lifeCycle) }
    }

    @Immutable
    data class PreviewInfo(
        val clipBoardLink: ClipBoardLink,
        val url: String? = null,
        val title: String? = null,
        val subtitle: String? = null,
        val icon: Icon? = null,
    )

    @Immutable
    data class Icon(
        val type: Int,
        val url: String? = null,
        @DrawableRes
        val res: Int = 0,
    ) {

        constructor(url: String?) : this(
            type = TYPE_URL,
            url = url
        )

        constructor(@DrawableRes res: Int) : this(
            type = TYPE_DRAWABLE_RES,
            res = res
        )

        companion object {
            const val TYPE_DRAWABLE_RES = 0
            const val TYPE_URL = 1
        }
    }
}
