package oolong.client.repository

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import oolong.client.api.models.protos.PbContent
import oolong.client.model.PostContent
import oolong.client.model.TextSpan
import oolong.client.model.plainText
import kotlin.test.Test
import kotlin.test.assertEquals

class PostContentMappingTest {
    private val registered = mutableListOf<Pair<String, String>>()
    private val emoticons = EmoticonRegistry { id, name -> registered += id to name }

    private fun map(vararg pieces: PbContent) = pieces.toList().toPostContent(emoticons)

    @Test
    fun `adjacent inline pieces form one text block`() {
        val content = map(
            PbContent(type = 0, text = "看 "),
            PbContent(type = 4, text = "@楼主", uid = 10),
            PbContent(type = 0, text = " 的链接 "),
            PbContent(type = 1, text = "tieba.baidu.com", link = "https://tieba.baidu.com"),
            PbContent(type = 2, text = "image_emoticon25", c = "滑稽"),
        )

        val expected = PostContent.Text(
            listOf<TextSpan>(
                TextSpan.Plain("看 "),
                TextSpan.Mention("@楼主", userId = 10),
                TextSpan.Plain(" 的链接 "),
                TextSpan.Link("tieba.baidu.com", url = "https://tieba.baidu.com"),
                TextSpan.Emoticon(id = "image_emoticon25", name = "滑稽"),
            ).toImmutableList()
        )
        assertEquals(listOf<PostContent>(expected), content)
        assertEquals("看 @楼主 的链接 tieba.baidu.com#(滑稽)", content.plainText)
    }

    @Test
    fun `a topic tag names its topic, from its link or else its text`() {
        val content = map(
            PbContent(type = 18, text = "#话题一#", link = "https://tieba.baidu.com/mo/q/hotMessage?topic_id=0&topic_name=%E8%AF%9D%E9%A2%98%E4%B8%80"),
            PbContent(type = 18, text = "#话题二#", link = ""),
        )

        val spans = (content.single() as PostContent.Text).spans
        assertEquals(listOf<TextSpan>(TextSpan.Topic("#话题一#", "话题一"), TextSpan.Topic("#话题二#", "话题二")), spans)
    }

    @Test
    fun `emoticons are registered so they can be used in replies`() {
        map(PbContent(type = 2, text = "image_emoticon25", c = "滑稽"))
        assertEquals(listOf("image_emoticon25" to "滑稽"), registered)
    }

    @Test
    fun `text types shown as plain text`() {
        val content = map(
            PbContent(type = 9, text = "a"),
            PbContent(type = 27, text = "b"),
            PbContent(type = 35, text = "c"),
            PbContent(type = 40, text = "d"),
        )
        assertEquals("abcd", content.plainText)
        assertEquals(1, content.size)
    }

    @Test
    fun `an image splits the text around it and keeps its urls in decreasing size`() {
        val content = map(
            PbContent(type = 0, text = "前"),
            PbContent(
                type = 3,
                originSrc = "https://tiebapic.baidu.com/forum/pic/item/abc123.jpg?tbpicau=x",
                bigCdnSrc = "big-cdn", bigSrc = "big", dynamic_ = "dynamic",
                cdnSrc = "cdn", cdnSrcActive = "cdn-active", src = "src",
                bsize = "1080,720", showOriginalBtn = 1, originSize = 2048,
            ),
            PbContent(type = 0, text = "后"),
        )

        assertEquals(3, content.size)
        val image = content[1] as PostContent.Image
        assertEquals("abc123", image.id)
        assertEquals(listOf("big-cdn", "big", "dynamic", "cdn", "cdn-active", "src"), image.urls)
        assertEquals("https://tiebapic.baidu.com/forum/pic/item/abc123.jpg?tbpicau=x", image.originUrl)
        assertEquals(1080 to 720, image.width to image.height)
        assertEquals(true, image.showOriginButton)
        assertEquals(2048, image.originSize)
        assertEquals("前\n[图片]\n后", content.plainText)
    }

    @Test
    fun `an image without its original opens its largest copy`() {
        val image = map(
            PbContent(
                type = 3,
                bigCdnSrc = "https://tiebapic.baidu.com/forum/w%3D1080/sign=s/abc123.jpg?tbpicau=y",
                cdnSrc = "cdn",
                showOriginalBtn = 1,
            ),
        ).single() as PostContent.Image
        assertEquals("abc123", image.id)
        assertEquals("https://tiebapic.baidu.com/forum/w%3D1080/sign=s/abc123.jpg?tbpicau=y", image.originUrl)
        assertEquals(false, image.showOriginButton)
    }

    @Test
    fun `a type 20 image only has its src`() {
        val image = map(PbContent(type = 20, src = "https://host/meme/xyz.jpg", bsize = "200,200"))
            .single() as PostContent.Image
        assertEquals("xyz", image.id)
        assertEquals(listOf("https://host/meme/xyz.jpg"), image.urls)
        assertEquals("https://host/meme/xyz.jpg", image.originUrl)
    }

    @Test
    fun `a video with a cover plays in place, one without is a web link`() {
        val content = map(
            PbContent(type = 5, link = "https://video/v.mp4", src = "cover", text = "https://web/v", bsize = "640,360"),
            PbContent(type = 5, text = "https://web/only"),
        )

        assertEquals(PostContent.Video("https://video/v.mp4", "cover", "https://web/v", 640, 360), content[0])
        assertEquals(
            PostContent.Text(persistentListOf(TextSpan.VideoLink("https://web/only", "https://web/only"))),
            content[1],
        )
        assertEquals("[视频]\nhttps://web/only", content.plainText)
    }

    @Test
    fun `voice messages and unknown types`() {
        val content = map(PbContent(type = 10, voiceMD5 = "md5", duringTime = 3000), PbContent(type = 999, text = "?"))
        assertEquals(listOf<PostContent>(PostContent.Voice("md5", 3000)), content)
        assertEquals("[语音]", content.plainText)
    }

    @Test
    fun `a malformed size is read as zero rather than crashing`() {
        val image = map(PbContent(type = 3, originSrc = "o.jpg", bsize = "")).single() as PostContent.Image
        assertEquals(0 to 0, image.width to image.height)
    }

    @Test
    fun `a link whose text names another site says where it really goes`() {
        val check = "http://tieba.baidu.com/mo/q/checkurl?url=https%3A%2F%2Fexample.com%2Fa&urlrefer=x"

        assertEquals("example.com", misleadingLinkHost("https://tieba.baidu.com/p/1", check))
        assertEquals("example.com", misleadingLinkHost("tieba.baidu.com", "https://example.com/"))
        assertEquals(null, misleadingLinkHost("https://example.com/a", check))
        assertEquals(null, misleadingLinkHost("www.example.com", "https://m.example.com/"))
        assertEquals(null, misleadingLinkHost("tieba.baidu.com/p/1", "https://wapp.baidu.com/p/1"))
        assertEquals(null, misleadingLinkHost("点击这里", check))
        assertEquals("b.com.cn", misleadingLinkHost("a.com.cn", "https://b.com.cn/"))
    }

    @Test
    fun `link text naming another site is marked when mapped`() {
        val content = listOf(PbContent(type = 1, text = "tieba.baidu.com", link = "https://example.com/")).toPostContent { _, _ -> }

        val link = (content.single() as PostContent.Text).spans.single() as TextSpan.Link
        assertEquals("example.com", link.actualHost)
    }

    @Test
    fun `a bare emoticon id is the first emoticon`() {
        val content = map(PbContent(type = 2, text = "image_emoticon", c = "呵呵"))

        assertEquals(TextSpan.Emoticon(id = "image_emoticon1", name = "呵呵"), (content.single() as PostContent.Text).spans.single())
    }
}
