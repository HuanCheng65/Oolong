package oolong.client.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import oolong.client.api.models.protos.PbContent
import oolong.client.model.PostContent
import oolong.client.model.TextSpan
import oolong.client.utils.EmoticonUtil.normalizeEmoticonId

/** Learns the emoticons that appear in posts, so they can be used when replying. */
fun interface EmoticonRegistry {
    fun register(id: String, name: String)
}

/**
 * The blocks of a post body. Adjacent inline pieces are merged into one text block; pieces of
 * unknown types are left out.
 */
fun List<PbContent>.toPostContent(emoticons: EmoticonRegistry): ImmutableList<PostContent> {
    val blocks = mutableListOf<PostContent>()
    val spans = mutableListOf<TextSpan>()
    fun flushText() {
        if (spans.isNotEmpty()) {
            blocks += PostContent.Text(spans.toImmutableList())
            spans.clear()
        }
    }
    for (piece in this) {
        val span = piece.toTextSpan()
        if (span != null) {
            if (span is TextSpan.Emoticon) emoticons.register(span.id, span.name)
            spans += span
            continue
        }
        val block = piece.toBlock() ?: continue
        flushText()
        blocks += block
    }
    flushText()
    return blocks.toImmutableList()
}

private fun PbContent.toTextSpan(): TextSpan? = when (type) {
    // 35: ad-like text, 40: keyword links in replies; both are shown as plain text
    0, 9, 27, 35, 40 -> TextSpan.Plain(text)
    1 -> TextSpan.Link(text, link, actualHost = misleadingLinkHost(text, link))
    2 -> TextSpan.Emoticon(id = normalizeEmoticonId(text), name = c)
    4 -> TextSpan.Mention(text, uid)
    18 -> topicName(text, link)?.let { TextSpan.Topic(text, it) } ?: TextSpan.Plain(text)
    5 -> if (src.isBlank()) TextSpan.VideoLink(text, url = text) else null
    else -> null
}

private fun PbContent.toBlock(): PostContent? {
    val (width, height) = size()
    return when (type) {
        3 -> {
            val urls = persistentListOf(bigCdnSrc, bigSrc, dynamic_, cdnSrc, cdnSrcActive, src)
            // Replies in a page of a thread come without their original; the largest copy stands in.
            val origin = originSrc.ifBlank { urls.firstOrNull { it.isNotBlank() }.orEmpty() }
            PostContent.Image(
                id = picId(origin),
                urls = urls,
                originUrl = origin,
                width = width,
                height = height,
                showOriginButton = showOriginalBtn == 1 && originSrc.isNotBlank(),
                originSize = originSize,
            )
        }

        20 -> PostContent.Image(
            id = picId(src),
            urls = persistentListOf(src),
            originUrl = src,
            width = width,
            height = height,
            showOriginButton = showOriginalBtn == 1,
            originSize = originSize,
            sticker = true,
        )

        5 -> PostContent.Video(url = link, coverUrl = src, webUrl = text, width = width, height = height, duration = duringTime.toInt())
        10 -> PostContent.Voice(md5 = voiceMD5, duration = duringTime)
        else -> null
    }
}

/** `bsize` is `width,height`. */
private fun PbContent.size(): Pair<Int, Int> {
    val parts = bsize.split(",")
    return (parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0)
}

/** The file name of an image URL without its `.jpg` extension, which identifies the image. */
internal fun picId(url: String): String =
    url.substringBefore('#').substringBefore('?').substringAfterLast('/').replace(".jpg", "")

/**
 * Where [url] really goes when [text] reads as an address on another site, or null. Links out of
 * Tieba pass through its check page, which names the real address in `url`.
 */
internal fun misleadingLinkHost(text: String, url: String): String? {
    val shown = AddressLike.matchEntire(text.trim())?.groupValues?.get(1)?.lowercase() ?: return null
    val target = url.toHttpUrlOrNull() ?: return null
    val actual = target.takeIf { it.encodedPath == "/mo/q/checkurl" }?.queryParameter("url")
        ?.replace("http://https://", "https://")?.toHttpUrlOrNull() ?: target
    val host = actual.host.lowercase()
    return host.takeIf { !sameSite(it, shown) }
}

private val AddressLike = Regex("""(?i)(?:https?://)?((?:[a-z0-9-]+\.)+[a-z]{2,})(?::\d+)?(?:[/?#]\S*)?""")

private fun sameSite(a: String, b: String) = siteOf(a) == siteOf(b)

/** The registered domain: the last two labels, three under a country's second level as com.cn. */
private fun siteOf(host: String): String {
    val labels = host.split('.')
    val count = if (labels.size >= 3 && labels.last().length == 2 && labels[labels.size - 2] in SecondLevels) 3 else 2
    return labels.takeLast(count).joinToString(".")
}

private val SecondLevels = setOf("com", "net", "org", "gov", "edu", "co", "ac")

/** The topic a `#topic#` tag names: the link's `topic_name`, or the tag's text between its #s. */
private fun topicName(text: String, link: String): String? {
    val fromLink = link.toHttpUrlOrNull()?.queryParameter("topic_name")
    return (fromLink ?: text.trim().removePrefix("#").removeSuffix("#")).trim().takeIf(String::isNotEmpty)
}
