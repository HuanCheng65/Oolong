package oolong.client.data.thread

import oolong.client.model.BawuType
import oolong.client.model.PostContent
import oolong.client.model.TextSpan
import oolong.core.network.protos.Agree
import oolong.core.network.protos.feed.ComponentFactory
import oolong.core.network.protos.feed.ContentComponent
import oolong.core.network.protos.feed.FeedAuthorSocial
import oolong.core.network.protos.feed.FeedContentEmoji
import oolong.core.network.protos.feed.FeedContentResource
import oolong.core.network.protos.feed.FeedContentText
import oolong.core.network.protos.feed.FeedHeadComponent
import oolong.core.network.protos.feed.FeedHeadImg
import oolong.core.network.protos.feed.FeedHeadSymbol
import oolong.core.network.protos.feed.FeedHeadText
import oolong.core.network.protos.feed.FeedKV
import oolong.core.network.protos.feed.FeedLayout
import oolong.core.network.protos.feed.FeedPicComponent
import oolong.core.network.protos.feed.FeedSocialComponent
import oolong.core.network.protos.feed.FrsTopThreadCardLayout
import oolong.core.network.protos.feed.FrsTopThreadComponent
import oolong.core.network.protos.feed.LayoutFactory
import oolong.core.network.protos.feed.PicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedThreadMappingTest {
    private val registered = mutableListOf<Pair<String, String>>()
    private val emoticons = EmoticonRegistry { id, name -> registered += id to name }

    private fun text(text: String, type: Int = 1) = FeedContentResource(type = type, text_info = FeedContentText(text = text))

    private fun kv(key: String, value: String) = FeedKV(key = key, value_ = value)

    private val thread = LayoutFactory(
        layout = "feed",
        feed = FeedLayout(
            components = listOf(
                ComponentFactory(
                    component = "feed_head",
                    feed_head = FeedHeadComponent(
                        image_data = FeedHeadImg(img_url = "http://tb.himg.baidu.com/sys/portrait/item/tb.1.abc?t=1"),
                        main_data = listOf(
                            FeedHeadSymbol(type = 1, text = FeedHeadText(text = "作者"), business_info = listOf(kv("icon_type", "name_show"))),
                            FeedHeadSymbol(type = 2, business_info = listOf(kv("icon_type", "forum_manager"))),
                        ),
                        extra_data = listOf(FeedHeadSymbol(type = 1, text = FeedHeadText(text = "200", type = 3))),
                    ),
                ),
                ComponentFactory(component = "feed_title", feed_title = ContentComponent(data_ = listOf(text("精华", type = 6), text("标题")))),
                ComponentFactory(
                    component = "feed_abstract",
                    feed_abstract = ContentComponent(
                        data_ = listOf(
                            text("说  了"),
                            FeedContentResource(type = 3, emoji_info = FeedContentEmoji(name = "image_emoticon9", c = "#(泪)")),
                        ),
                    ),
                ),
                ComponentFactory(
                    component = "feed_pic",
                    feed_pic = FeedPicComponent(pics = listOf(PicInfo(small_pic_url = "s", big_pic_url = "b", origin_pic_url = "https://a/pic/item/o.jpg", width = 560, height = 297))),
                ),
                ComponentFactory(
                    component = "feed_social",
                    feed_social = FeedSocialComponent(agree = Agree(agreeNum = 7, hasAgree = 1), comment_num = 3, share_num = 2, tid = 42, fid = 9, first_post_id = 99),
                ),
            ),
            business_info = listOf(
                kv("thread_id", "42"),
                kv("user_id", "5"),
                kv("title", "标题"),
                kv("create_time", "100"),
                kv("forum_id", "9"),
                kv("forum_name", "吧"),
            ),
        ),
    )

    @Test
    fun `a thread's card gives the thread`() {
        val summary = listOf(thread).toThreadSummaries(emoticons).single()

        assertEquals(42L, summary.id)
        assertEquals("标题", summary.title)
        assertEquals(9L, summary.forum.id)
        assertEquals("吧", summary.forum.name)
        assertEquals(5L, summary.author.id)
        assertEquals("作者", summary.author.displayName)
        assertEquals("tb.1.abc", summary.author.portrait)
        assertEquals(BawuType.Manager, summary.author.bawuType)
        assertEquals(100L, summary.createTime)
        assertEquals(200L, summary.lastReplyTime)
        assertEquals("说 了#(泪)", summary.abstract)
        assertEquals(99L, summary.firstPostId)
        assertEquals(3, summary.replyCount)
        assertEquals(2L, summary.shareCount)
        assertEquals(7L, summary.agree.count)
        assertTrue(summary.agree.agreed)
        assertTrue(summary.isGood)
        assertEquals(false, summary.isTop)
        assertEquals(560, summary.images.single().width)
        assertEquals("https://a/pic/item/o.jpg", summary.images.single().original)
        assertEquals(listOf("image_emoticon9" to "泪"), registered.distinct())
        val first = summary.firstPost
        assertEquals(TextSpan.Emoticon("image_emoticon9", "泪"), (first[0] as PostContent.Text).spans.last())
        assertTrue(first[1] is PostContent.Image)
    }

    @Test
    fun `a card without the facts' title and counts takes them from its components`() {
        val feed = thread.feed!!
        val card = thread.copy(
            feed = feed.copy(
                components = feed.components.filter { it.component != "feed_social" } +
                    ComponentFactory(component = "feed_author_social", feed_author_social = FeedAuthorSocial(agree = Agree(agreeNum = 54), comment_num = 88)),
                business_info = feed.business_info.filter { it.key != "title" && it.key != "create_time" },
            ),
        )

        val summary = listOf(card).toThreadSummaries(emoticons).single()

        assertEquals("标题", summary.title)
        assertEquals(200L, summary.createTime)
        assertEquals(88, summary.replyCount)
        assertEquals(54L, summary.agree.count)
    }

    @Test
    fun `a card with nothing but its author is left out`() {
        val feed = thread.feed!!
        val card = thread.copy(
            feed = feed.copy(
                components = feed.components.filter { it.component == "feed_head" },
                business_info = listOf(kv("thread_id", "43")),
            ),
        )

        assertEquals(emptyList<Any>(), listOf(card).toThreadSummaries(emoticons))
    }

    @Test
    fun `pinned threads come first, and cards of other kinds are left out`() {
        val pinned = LayoutFactory(
            layout = "frs_top_thread_card",
            frs_top_thread_card = FrsTopThreadCardLayout(
                components = listOf(
                    ComponentFactory(
                        component = "frs_top_thread",
                        frs_top_thread = FrsTopThreadComponent(
                            title = listOf(text("置顶", type = 6), text("公告")),
                            author_social = FeedAuthorSocial(tid = 7, comment_num = 1),
                        ),
                    ),
                ),
            ),
        )
        val other = LayoutFactory(layout = "sideway_vertical")

        val threads = listOf(pinned, other, thread).toThreadSummaries(emoticons)

        assertEquals(listOf(7L, 42L), threads.map { it.id })
        assertEquals("公告", threads[0].title)
        assertEquals(true, threads[0].isTop)
    }

    @Test
    fun `a card without a thread id is no thread`() {
        val card = thread.copy(feed = thread.feed!!.copy(business_info = emptyList()))

        assertEquals(emptyList<Any>(), listOf(card).toThreadSummaries(emoticons))
    }

    @Test
    fun `adverts laid out as threads are left out`() {
        val feed = thread.feed!!
        val brand = thread.copy(feed = feed.copy(log_param = listOf(kv("card_type", "brandLotteryAd"))))
        val network = thread.copy(feed = feed.copy(log_param = listOf(kv("card_type", "afd"))))
        val promoted = thread.copy(feed = feed.copy(business_info = feed.business_info + kv("thread_type", "76")))

        assertEquals(emptyList<Any>(), listOf(brand, network, promoted).toThreadSummaries(emoticons))
    }

    @Test
    fun `Tieba's own events are marked as such`() {
        val feed = thread.feed!!
        val event = thread.copy(feed = feed.copy(business_info = feed.business_info + kv("thread_type", "41")))

        assertTrue(listOf(event).toThreadSummaries(emoticons).single().isActivity)
        assertEquals(false, listOf(thread).toThreadSummaries(emoticons).single().isActivity)
    }
}
