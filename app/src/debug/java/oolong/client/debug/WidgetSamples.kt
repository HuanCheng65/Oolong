package oolong.client.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import oolong.client.model.HotTopic
import oolong.client.ui.appwidget.ForumContent
import oolong.client.ui.appwidget.ForumShows
import oolong.client.ui.appwidget.ForumsContent
import oolong.client.ui.appwidget.RankingContent
import oolong.client.ui.appwidget.TopicsContent
import oolong.client.ui.appwidget.WidgetData

/**
 * What the still widget previews show: everyday, made-up content, the same in every build, and
 * soft colour washes for pictures, as the app ships these previews for good.
 */
internal object WidgetSamples : WidgetData.StandIn {
    /** A fixed time, so the previews' clocks read 09:41. */
    private val loadedAt = java.util.Calendar.getInstance().apply { set(2026, 0, 1, 9, 41, 0) }.timeInMillis

    override val forumName = "摄影"

    private val forumNames = listOf("摄影", "旅行", "美食", "读书", "电影", "数码", "健身", "猫")

    override fun topics() = TopicsContent(
        loadedAt,
        listOf(
            TopicsContent.Item(1, "城市夜景随手拍", "sample:topic:1", 2_710_000, HotTopic.Tag.Hot),
            TopicsContent.Item(2, "冬天第一顿火锅吃什么", "sample:topic:2", 1_980_000, HotTopic.Tag.New),
            TopicsContent.Item(3, "周末去哪儿徒步", "sample:topic:3", 1_520_000, HotTopic.Tag.None),
            TopicsContent.Item(4, "最近在读的一本书", "sample:topic:4", 1_260_000, HotTopic.Tag.New),
            TopicsContent.Item(5, "猫咪的迷惑行为大赏", "sample:topic:5", 980_000, HotTopic.Tag.None),
            TopicsContent.Item(6, "年度最想重看的电影", "sample:topic:6", 870_000, HotTopic.Tag.None),
            TopicsContent.Item(7, "入门相机怎么选", "sample:topic:7", 640_000, HotTopic.Tag.None),
            TopicsContent.Item(8, "一个人的早餐", "sample:topic:8", 510_000, HotTopic.Tag.None),
        ),
    )

    override fun ranking() = RankingContent(
        loadedAt,
        listOf(
            RankingContent.Item(1, "第一次拍到这么完整的银河", "摄影", "sample:avatar:摄影", 26_000, "sample:thread:1"),
            RankingContent.Item(2, "骑行环湖三天，路上的风景分享", "旅行", "sample:avatar:旅行", 18_000, "sample:thread:2"),
            RankingContent.Item(3, "在家复刻了一碗牛肉面", "美食", "sample:avatar:美食", 15_000, "sample:thread:3"),
            RankingContent.Item(4, "今年读过最好的五本小说", "读书", "sample:avatar:读书", 9_800, "sample:thread:4"),
            RankingContent.Item(5, "老电影修复版观后感", "电影", "sample:avatar:电影", 7_600, ""),
            RankingContent.Item(6, "三个月体脂从 25 降到 18", "健身", "sample:avatar:健身", 6_100, "sample:thread:6"),
            RankingContent.Item(7, "家里的猫学会了开门", "猫", "sample:avatar:猫", 5_400, "sample:thread:7"),
            RankingContent.Item(8, "旧手机当相机用的可能性", "数码", "sample:avatar:数码", 4_300, ""),
        ),
    )

    override fun forum(name: String, shows: ForumShows) = ForumContent(
        loadedAt = loadedAt,
        name = name,
        avatar = "sample:avatar:$name",
        signedIn = false,
        id = 1,
        liked = true,
        level = 9,
        threads = listOf(
            ForumContent.Item(1, "本周主题：雨天的街道", 170, "sample:forum:1", "光与影", seconds(9, 38)),
            ForumContent.Item(2, "老镜头转接新机身的一些体会", 714, "sample:forum:2", "镜头收藏家", seconds(9, 23)),
            ForumContent.Item(3, "清晨的菜市场，烟火气", 183, "sample:forum:3", "早起的人", seconds(7, 40)),
            ForumContent.Item(4, "后期调色前后对比", 96, "sample:forum:4", "调色板", seconds(7, 2)),
            ForumContent.Item(5, "新人报到，请多指教", 52, "", "初来乍到", seconds(6, 15)),
        ),
    )

    /** A time of today, in seconds, so the forum's threads read as active today. */
    private fun seconds(hour: Int, minute: Int) = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
    }.timeInMillis / 1000

    override fun forums() = ForumsContent(
        loadedAt,
        forumNames.mapIndexed { index, name -> ForumsContent.Item(name, "sample:avatar:$name", signedIn = index % 3 != 2) },
    )

    /** A soft wash for a picture; a forum's avatar carries its name's first character. */
    override fun picture(url: String, width: Int, height: Int, radius: Float): Bitmap? {
        if (!url.startsWith("sample:")) return null
        val seed = url.hashCode()
        // Neighbouring samples differ by a character, so their hues are spread well apart
        val hue = (((seed * 47L) % 360 + 360) % 360).toFloat()
        val from = Color.HSVToColor(floatArrayOf(hue, 0.28f, 0.95f))
        val to = Color.HSVToColor(floatArrayOf((hue + 40f) % 360f, 0.45f, 0.82f))
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), from, to, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
        if (url.startsWith("sample:avatar:")) {
            val text = url.removePrefix("sample:avatar:").take(1)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = height * 0.48f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            val y = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(text, width / 2f, y, textPaint)
        }
        return bitmap
    }
}
