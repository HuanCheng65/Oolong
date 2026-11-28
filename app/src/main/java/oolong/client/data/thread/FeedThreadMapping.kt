package oolong.client.data.thread

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import oolong.client.model.Agree
import oolong.client.model.BawuType
import oolong.client.model.ForumSummary
import oolong.client.model.PostContent
import oolong.client.model.TextSpan
import oolong.client.model.ThreadImage
import oolong.client.model.ThreadSummary
import oolong.client.model.ThreadVideo
import oolong.client.model.User
import oolong.core.network.protos.feed.ComponentFactory
import oolong.core.network.protos.feed.FeedContentResource
import oolong.core.network.protos.feed.FeedHeadSymbol
import oolong.core.network.protos.feed.FeedKV
import oolong.core.network.protos.feed.FeedLayout
import oolong.core.network.protos.feed.LayoutFactory
import oolong.core.network.protos.feed.PicInfo
import oolong.core.network.protos.feed.VideoField

/**
 * The threads of a list laid out by the server, in its order: pinned threads first where it
 * has them, then the threads' cards. Cards of other kinds, as recommended forums, are left out.
 */
fun List<LayoutFactory>.toThreadSummaries(emoticons: EmoticonRegistry): List<ThreadSummary> = flatMap { entry ->
    when (entry.layout) {
        FeedLayoutName -> listOfNotNull(entry.feed?.takeUnless { it.isAdvert() }?.let { feed -> feed.components.toThreadSummary(feed.business_info, emoticons) })
        TopThreadsLayoutName -> entry.frs_top_thread_card?.components.orEmpty().mapNotNull { it.toTopThreadSummary(emoticons) }
        else -> emptyList()
    }
}

/**
 * Whether a card laid out as a thread is an advert: by its card's kind, as the advert networks'
 * own (`afd`, `bear`) and brands' (`brandLotteryAd` and the like), or by a thread type kept for
 * promotions.
 */
private fun FeedLayout.isAdvert(): Boolean {
    val cardType = log_param.firstOrNull { it.key == "card_type" }?.value_.orEmpty()
    val threadType = business_info.firstOrNull { it.key == "thread_type" }?.value_?.toIntOrNull()
    return cardType in AdvertCards || cardType.endsWith("Ad") || threadType in PromotedThreadTypes
}

/** Whether the list has the forum's pinned threads, which only some of its answers carry. */
fun List<LayoutFactory>.hasTopThreads(): Boolean = any { it.layout == TopThreadsLayoutName }

/** A thread's card: its components, and the facts the card gives as text under [info]. */
private fun List<ComponentFactory>.toThreadSummary(info: List<FeedKV>, emoticons: EmoticonRegistry): ThreadSummary? {
    val facts = info.associate { it.key to it.value_ }
    val id = facts["thread_id"]?.toLongOrNull() ?: return null
    val head = firstNotNullOfOrNull { it.feed_head }
    val social = firstNotNullOfOrNull { it.feed_social }
    val authorSocial = firstNotNullOfOrNull { it.feed_author_social }
    val title = firstNotNullOfOrNull { it.feed_title }?.data_.orEmpty()
    val abstract = firstNotNullOfOrNull { it.feed_abstract }?.data_.orEmpty()
    val pictures = firstNotNullOfOrNull { it.feed_pic }?.pics.orEmpty()
    val video = firstNotNullOfOrNull { it.feed_video }?.video_info
    val titleText = facts["title"]?.takeIf { it.isNotBlank() } ?: title.filter { it.type != TagType }.text(emoticons)
    // A card with nothing to show but its author, as some forums' headline cards come, is left out
    if (titleText.isBlank() && abstract.isEmpty() && pictures.isEmpty() && video == null) return null
    val firstPostId = social?.first_post_id ?: 0
    val lastReplyTime = head?.extra_data?.firstOrNull { it.text?.type == TimeTextType }?.text?.text?.toLongOrNull() ?: 0
    val author = User(
        id = facts["user_id"]?.toLongOrNull() ?: 0,
        name = "",
        nameShow = head?.main_data?.named(NameShow)?.text?.text.orEmpty(),
        portrait = head?.image_data?.img_url?.portrait().orEmpty(),
        bawuType = if (head?.main_data?.named(ForumManager) != null) BawuType.Manager else null,
    )
    return ThreadSummary(
        id = id,
        forum = facts.forum(),
        title = titleText,
        author = author,
        createTime = facts["create_time"]?.toLongOrNull() ?: lastReplyTime,
        // The time the card shows, which is when the thread last had a reply
        lastReplyTime = lastReplyTime,
        abstract = abstract.text(emoticons),
        firstPostId = firstPostId,
        firstPost = abstract.toPostContent(emoticons, pictures, firstPostId),
        firstPostShort = true,
        images = pictures.map { it.toThreadImage(firstPostId) }.toImmutableList(),
        video = video?.toThreadVideo(),
        replyCount = social?.comment_num ?: authorSocial?.comment_num ?: 0,
        shareCount = (social?.share_num ?: authorSocial?.share_num ?: 0).toLong(),
        agree = (social?.agree ?: authorSocial?.agree).let { Agree(count = it?.agreeNum ?: 0, agreed = it?.hasAgree == 1) },
        isGood = title.any { it.type == TagType && it.text_info?.text == GoodTag },
        isActivity = facts["thread_type"]?.toIntOrNull() == ActivityThreadType,
        isTop = false,
        tabName = title.firstOrNull { it.type == TagType && it.text_info?.text != GoodTag }?.text_info?.text.orEmpty(),
    )
}

/** A pinned thread, as the card of the forum's pinned threads lists it. */
private fun ComponentFactory.toTopThreadSummary(emoticons: EmoticonRegistry): ThreadSummary? {
    val top = frs_top_thread ?: return null
    val facts = top.business_info.associate { it.key to it.value_ }
    val id = facts["thread_id"]?.toLongOrNull() ?: top.author_social?.tid?.takeIf { it != 0L } ?: return null
    val social = top.author_social
    val pictures = top.pic_abstract?.pics.orEmpty()
    val abstract = top.pic_abstract?.abstract_.orEmpty()
    return ThreadSummary(
        id = id,
        forum = facts.forum(),
        title = facts["title"] ?: top.title.filter { it.type != TagType }.text(emoticons),
        author = User(
            id = facts["user_id"]?.toLongOrNull() ?: 0,
            name = "",
            nameShow = social?.main_data?.named(NameShow)?.text?.text.orEmpty(),
            portrait = social?.image_data?.img_url?.portrait().orEmpty(),
        ),
        createTime = facts["create_time"]?.toLongOrNull() ?: 0,
        abstract = abstract.text(emoticons),
        firstPostId = 0,
        firstPost = abstract.toPostContent(emoticons, pictures, 0),
        firstPostShort = true,
        images = pictures.map { it.toThreadImage(0) }.toImmutableList(),
        replyCount = social?.comment_num ?: 0,
        shareCount = (social?.share_num ?: 0).toLong(),
        agree = Agree(count = social?.agree?.agreeNum ?: 0, agreed = social?.agree?.hasAgree == 1),
        isGood = top.title.any { it.type == TagType && it.text_info?.text == GoodTag },
        isTop = true,
    )
}

private fun Map<String, String>.forum() = ForumSummary(
    id = get("forum_id")?.toLongOrNull() ?: 0,
    name = get("forum_name").orEmpty(),
    avatar = get("forum_avatar").orEmpty(),
)

/** The symbol a head names [kind], as its own `icon_type`. */
private fun List<FeedHeadSymbol>.named(kind: String): FeedHeadSymbol? =
    firstOrNull { symbol -> symbol.business_info.any { it.key == IconType && it.value_ == kind } }

/** The portrait id in an avatar's address, `…/portrait/item/<id>?t=…`, or the address itself. */
private fun String.portrait(): String = substringAfter("/portrait/item/", this).substringBefore('?')

/** The pieces as one line: text with runs of spaces collapsed, emoticons as their `#(name)` codes. */
private fun List<FeedContentResource>.text(emoticons: EmoticonRegistry): String = joinToString(separator = "") { piece ->
    when (piece.type) {
        TextType -> piece.text_info?.text.orEmpty().replace(Spaces, " ")
        EmoticonType -> piece.emoji_info?.let { emoji ->
            val name = emoji.c.removeSurrounding("#(", ")")
            emoticons.register(emoji.name, name)
            "#($name)"
        }.orEmpty()

        else -> ""
    }
}

/**
 * What the card shows of the first floor, standing in for it until the thread page loads it:
 * the abstract's words and emoticons, then its pictures.
 */
private fun List<FeedContentResource>.toPostContent(emoticons: EmoticonRegistry, pictures: List<PicInfo>, postId: Long): kotlinx.collections.immutable.ImmutableList<PostContent> {
    val spans = mapNotNull { piece ->
        when (piece.type) {
            TextType -> piece.text_info?.text?.takeIf { it.isNotEmpty() }?.let { TextSpan.Plain(it) }
            EmoticonType -> piece.emoji_info?.let { emoji ->
                val name = emoji.c.removeSurrounding("#(", ")")
                emoticons.register(emoji.name, name)
                TextSpan.Emoticon(emoji.name, name)
            }

            else -> null
        }
    }
    if (spans.isEmpty() && pictures.isEmpty()) return persistentListOf()
    val text = listOfNotNull(spans.takeIf { it.isNotEmpty() }?.let { PostContent.Text(it.toImmutableList()) })
    val images = pictures.map { picture ->
        PostContent.Image(
            id = "$postId-${picture.origin_pic_url.substringAfterLast('/').substringBefore('.')}",
            urls = persistentListOf(picture.small_pic_url, picture.big_pic_url, picture.origin_pic_url),
            originUrl = picture.origin_pic_url,
            width = picture.width,
            height = picture.height,
            showOriginButton = false,
            originSize = 0,
        )
    }
    return (text + images).toImmutableList()
}

/** `small_pic_url` is sized for the card, `big_pic_url` larger, `origin_pic_url` the upload itself. */
private fun PicInfo.toThreadImage(postId: Long) = ThreadImage(
    small = small_pic_url.ifEmpty { big_pic_url }.ifEmpty { origin_pic_url },
    large = big_pic_url.ifEmpty { origin_pic_url }.ifEmpty { small_pic_url },
    original = origin_pic_url.ifEmpty { big_pic_url }.ifEmpty { small_pic_url },
    animated = "",
    width = width,
    height = height,
    isLong = is_long_pic == 1,
    postId = postId,
    originalSize = 0,
    showOriginalButton = false,
)

private fun VideoField.toThreadVideo() = ThreadVideo(
    url = url,
    cover = thumbnail?.url.orEmpty(),
    coverWidth = thumbnail?.width ?: width,
    coverHeight = thumbnail?.height ?: height,
    duration = duration,
)

private const val FeedLayoutName = "feed"
private val AdvertCards = setOf("afd", "bear")

/** Promotions: a brand's event (76), goods offered in a thread (79), a game to book (80). */
private val PromotedThreadTypes = setOf(76, 79, 80)
private const val TopThreadsLayoutName = "frs_top_thread_card"
private const val IconType = "icon_type"
private const val NameShow = "name_show"
private const val ForumManager = "forum_manager"
private const val TextType = 1
private const val EmoticonType = 3
private const val TagType = 6
private const val TimeTextType = 3
private const val GoodTag = "精华"
private val Spaces = Regex("""\s+""")
