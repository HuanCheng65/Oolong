package oolong.client.data.emoticon

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import coil3.compose.AsyncImage
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import oolong.client.R
import oolong.client.media.FileUtil
import oolong.client.media.ImageUtil
import oolong.client.media.image.loadBitmap
import oolong.client.model.EmoticonCache
import oolong.client.ui.components.rememberTextSize
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Composable
fun calcLineHeightPx(style: TextStyle): Int =
    rememberTextSize(stringResource(id = R.string.single_chinese_char), style).height

data class Emoticon(
    val id: String,
    val name: String
)

object EmoticonManager {
    private val DEFAULT_EMOTICON_MAPPING: Map<String, String> = mapOf(
        "呵呵" to "image_emoticon1",
        "哈哈" to "image_emoticon2",
        "吐舌" to "image_emoticon3",
        "啊" to "image_emoticon4",
        "酷" to "image_emoticon5",
        "怒" to "image_emoticon6",
        "开心" to "image_emoticon7",
        "汗" to "image_emoticon8",
        "泪" to "image_emoticon9",
        "黑线" to "image_emoticon10",
        "鄙视" to "image_emoticon11",
        "不高兴" to "image_emoticon12",
        "真棒" to "image_emoticon13",
        "钱" to "image_emoticon14",
        "疑问" to "image_emoticon15",
        "阴险" to "image_emoticon16",
        "吐" to "image_emoticon17",
        "咦" to "image_emoticon18",
        "委屈" to "image_emoticon19",
        "花心" to "image_emoticon20",
        "呼~" to "image_emoticon21",
        "笑眼" to "image_emoticon22",
        "冷" to "image_emoticon23",
        "太开心" to "image_emoticon24",
        "滑稽" to "image_emoticon25",
        "勉强" to "image_emoticon26",
        "狂汗" to "image_emoticon27",
        "乖" to "image_emoticon28",
        "睡觉" to "image_emoticon29",
        "惊哭" to "image_emoticon30",
        "生气" to "image_emoticon31",
        "惊讶" to "image_emoticon32",
        "喷" to "image_emoticon33",
        "爱心" to "image_emoticon34",
        "心碎" to "image_emoticon35",
        "玫瑰" to "image_emoticon36",
        "礼物" to "image_emoticon37",
        "彩虹" to "image_emoticon38",
        "星星月亮" to "image_emoticon39",
        "太阳" to "image_emoticon40",
        "钱币" to "image_emoticon41",
        "灯泡" to "image_emoticon42",
        "茶杯" to "image_emoticon43",
        "蛋糕" to "image_emoticon44",
        "音乐" to "image_emoticon45",
        "haha" to "image_emoticon46",
        "胜利" to "image_emoticon47",
        "大拇指" to "image_emoticon48",
        "弱" to "image_emoticon49",
        "OK" to "image_emoticon50",
        "生气" to "image_emoticon61",
        "沙发" to "image_emoticon77",
        "手纸" to "image_emoticon78",
        "香蕉" to "image_emoticon79",
        "便便" to "image_emoticon80",
        "药丸" to "image_emoticon81",
        "红领巾" to "image_emoticon82",
        "蜡烛" to "image_emoticon83",
        "三道杠" to "image_emoticon84",
    )

    /** The application's context, which lives as long as the process; set by [attach]. */
    @Volatile
    private var appContext: Context? = null

    // Posts register emoticons while they are mapped, off the main thread, as the UI reads these
    private val emoticonIds = CopyOnWriteArrayList<String>()
    private val emoticonMapping = ConcurrentHashMap<String, String>()
    private val drawableCache = ConcurrentHashMap<String, Drawable>()
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Inline content for the emoticons [ids], [sizePx] across in the [density] of the text they
     * sit in. Each is described by its name, taken from the alternate text `#(name)`.
     */
    fun getEmoticonInlineContent(
        ids: Set<String>,
        sizePx: Float,
        density: Density,
    ): Map<String, InlineTextContent> {
        if (ids.isEmpty()) return emptyMap()
        val size = with(density) { sizePx.toSp() }
        val placeholder = Placeholder(size, size, PlaceholderVerticalAlign.TextCenter)
        return ids.associate { id ->
            EmoticonUtil.emoticonInlineId(id) to InlineTextContent(placeholder) { alternateText ->
                AsyncImage(
                    model = rememberEmoticon(id = id),
                    contentDescription = stringResource(
                        id = R.string.emoticon,
                        alternateText.removePrefix("#(").removeSuffix(")")
                    ),
                    modifier = Modifier.size(with(density) { sizePx.toDp() })
                )
            }
        }
    }

    /** Gives the emoticons the app's context, before anything shows one; [init] loads them later. */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun init(context: Context) {
        attach(context)
        val emoticonCache = getEmoticonDataCache()
        if (emoticonCache.ids.isEmpty()) {
            emoticonIds.addAllAbsent(((1..50) + (61..101) + (125..137)).map { "image_emoticon$it" })
        } else {
            emoticonIds.addAllAbsent(emoticonCache.ids)
        }
        if (emoticonCache.mapping.isEmpty()) {
            emoticonMapping.putAll(DEFAULT_EMOTICON_MAPPING)
        } else {
            emoticonMapping.putAll(emoticonCache.mapping)
        }
        updateCache()
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { fetchEmoticons(context) }
            }
        }
    }

    private fun updateCache() {
        runCatching {
            val emoticonDataCacheFile = File(getEmoticonCacheDir(), "emoticon_data_cache")
            if (emoticonDataCacheFile.exists() || emoticonDataCacheFile.createNewFile()) {
                FileUtil.writeFile(
                    emoticonDataCacheFile,
                    Json.encodeToString(EmoticonCache(emoticonIds.toList(), emoticonMapping.toMap())),
                    false
                )
            }
        }
    }

    private fun getContext(): Context = checkNotNull(appContext) { "EmoticonManager used before attach" }

    private fun getEmoticonDataCache(): EmoticonCache {
        return runCatching {
            File(getEmoticonCacheDir(), "emoticon_data_cache").takeIf { it.exists() }
                ?.let { Json.decodeFromString<EmoticonCache>(it.readText()) }
        }.getOrNull() ?: EmoticonCache()
    }

    private fun getEmoticonCacheDir(): File {
        return File(getContext().externalCacheDir ?: getContext().cacheDir, "emoticon").apply {
            if (exists() && isFile) {
                delete()
                mkdirs()
            } else if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Where downloaded emoticons are kept: with the app's files, not its cache, so clearing the
     * cache does not take them from threads saved to read offline.
     */
    private fun getEmoticonDir(): File = File(getContext().filesDir, "emoticon").apply { mkdirs() }

    /** A downloaded emoticon's file: the kept one, or one downloaded into the cache before they were kept. */
    private fun getEmoticonFile(id: String): File {
        val kept = File(getEmoticonDir(), "$id.png")
        if (kept.exists()) return kept
        return File(getEmoticonCacheDir(), "$id.png").takeIf { it.exists() } ?: kept
    }

    /**
     * Makes sure emoticon [id] can be shown without the network: bundled, or downloaded and kept.
     * Returns false when it could not be downloaded.
     */
    suspend fun keep(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val realId = EmoticonUtil.normalizeEmoticonId(id)
        if (getEmoticonResId(context, realId) != 0) return@withContext true
        val kept = File(getEmoticonDir(), "$realId.png")
        if (kept.exists()) return@withContext true
        val cached = File(getEmoticonCacheDir(), "$realId.png")
        if (cached.exists()) return@withContext cached.copyTo(kept, overwrite = true).exists()
        val bitmap = context.loadBitmap(emoticonUrl(realId)) ?: return@withContext false
        ImageUtil.bitmapToFile(bitmap, kept, Bitmap.CompressFormat.PNG)
        kept.exists()
    }

    private fun emoticonUrl(id: String) = "http://static.tieba.baidu.com/tb/editor/images/client/$id.png"

    fun getAllEmoticon(): List<Emoticon> {
        return emoticonIds.map { id ->
            Emoticon(
                id = id,
                name = getEmoticonNameById(id) ?: ""
            )
        }
    }

    fun getEmoticonIdByName(name: String): String? {
        return emoticonMapping[name]
    }

    private fun getEmoticonNameById(id: String): String? {
        return emoticonMapping.firstNotNullOfOrNull { (name, emoticonId) ->
            if (emoticonId == id) {
                name
            } else {
                null
            }
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun getEmoticonResId(context: Context, id: String): Int {
        return context.resources.getIdentifier(id, "drawable", context.packageName)
    }

    fun getEmoticonDrawable(context: Context, id: String?): Drawable? {
        if (id == null) {
            return null
        }
        drawableCache[id]?.let { return it }
        val resId = getEmoticonResId(context, id)
        if (resId != 0) {
            return AppCompatResources.getDrawable(context, resId).also { drawableCache[id] = it!! }
        }
        val emoticonFile = getEmoticonFile(id)
        if (!emoticonFile.exists()) {
            return null
        }
        return BitmapDrawable(
            getContext().resources,
            emoticonFile.inputStream()
        ).also { drawableCache[id] = it }
    }

    /** The emoticon's image, bundled or downloaded, or null when it has neither. */
    private fun getEmoticon(context: Context, id: String?): Any? {
        id ?: return null
        val resId = getEmoticonResId(context, id)
        if (resId != 0) {
            return resId
        }
        return getEmoticonFile(id).takeIf { it.exists() }
    }

    @Composable
    fun rememberEmoticonPainter(id: String): Painter {
        val context = LocalContext.current
        val drawable = remember(id) { getEmoticonDrawable(context, id) }
        return rememberDrawablePainter(drawable = drawable)
    }

    @Composable
    fun rememberEmoticon(id: String): Any? {
        val context = LocalContext.current
        return remember(id) { getEmoticon(context, id) }
    }

    private suspend fun fetchEmoticons(context: Context) {
        emoticonIds.forEach {
            val resId = getEmoticonResId(context, it)
            val emoticonFile = getEmoticonFile(it)
            if (resId == 0 && !emoticonFile.exists()) {
                val bitmap = context.loadBitmap(emoticonUrl(it))
                if (bitmap != null) {
                    ImageUtil.bitmapToFile(bitmap, emoticonFile, Bitmap.CompressFormat.PNG)
                }
            }
        }
    }

    fun registerEmoticon(id: String, name: String) {
        val realId = EmoticonUtil.normalizeEmoticonId(id)
        val newId = emoticonIds.addIfAbsent(realId)
        val newName = emoticonMapping.putIfAbsent(name, realId) == null
        if (newId || newName) {
            coroutineScope.launch {
                updateCache()
            }
        }
    }
}
