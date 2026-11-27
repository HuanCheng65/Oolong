package oolong.client.ui.videoview

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.R
import oolong.client.data.identity.getUserAgent
import oolong.client.media.FileUtil
import oolong.client.util.PermissionUtils
import oolong.client.util.toastShort
import oolong.core.network.TiebaProfile
import java.io.File
import java.io.OutputStream

/** A video as a card shows it and the viewer plays it. */
@Immutable
data class ViewerVideo(
    val url: String,
    val cover: String,
    val width: Int,
    val height: Int,
    /** In seconds, or 0 when not known. */
    val duration: Int = 0,
    /** Who posted it, shown with [text] under it in the viewer. */
    val author: String? = null,
    val text: String? = null,
) {
    /** Names the video across pages and loads. */
    val key: String get() = url.substringBefore('?')

    /** Height over width, or 0 when not known. */
    val aspect: Float get() = if (width > 0 && height > 0) height.toFloat() / width else 0f
}

@Immutable
internal data class VideoOpening(
    val video: ViewerVideo,
    /** The cover's bounds in the window, or null to open in place. */
    val from: Rect?,
    val id: Long,
)

/** Where a cover is in the window, and whether enough of it shows to close back into. */
@Immutable
internal data class VideoThumbnail(val bounds: Rect, val visible: Boolean)

/**
 * The video viewer over the whole window, and the one player every video plays in. Covers ask it
 * to start loading when pressed and to open when tapped, and tell it where they are, so it can
 * grow out of them and shrink back into them.
 */
@Stable
class VideoViewer internal constructor(private val context: Context) {
    internal var opening by mutableStateOf<VideoOpening?>(null)
        private set

    internal val thumbnails = mutableStateMapOf<String, VideoThumbnail>()

    /** How far into each video it was left, as a fraction, for its cover to show. */
    internal val watched = mutableStateMapOf<String, Float>()

    /** Where each video was left, in milliseconds; the least recent first. */
    private val positions = LinkedHashMap<String, Long>(16, 0.75f, true)

    internal var player: ExoPlayer? = null
        private set

    /** The video [player] holds. */
    private var loaded: String? = null

    private var opened = 0L

    /** Starts loading [video], so it is ready by the time the viewer has grown. */
    fun preload(video: ViewerVideo) {
        val player = player ?: createPlayer().also { player = it }
        if (loaded == video.key) return
        loaded = video.key
        player.setMediaItem(MediaItem.fromUri(video.url), positions[video.key] ?: 0L)
        player.prepare()
    }

    /** Stops loading [video] when the press that started it did not open it. */
    fun cancelPreload(video: ViewerVideo) {
        if (opening == null && loaded == video.key) releasePlayer()
    }

    /** Plays [video], growing out of [from] when given. */
    fun open(video: ViewerVideo, from: Rect? = null) {
        preload(video)
        player?.playWhenReady = true
        opening = VideoOpening(video, from, ++opened)
    }

    /** Notes where [key]'s cover is; [bounds] null when it leaves the screen. */
    fun place(key: String, bounds: Rect?, visible: Boolean = true) {
        if (bounds == null) thumbnails.remove(key) else thumbnails[key] = VideoThumbnail(bounds, visible)
    }

    /** Keeps where [key] was left, [position] of [duration]; a video seen to the end starts over. */
    internal fun leave(key: String, position: Long, duration: Long) {
        if (duration <= 0) return
        val ended = position >= duration - EndMargin
        if (ended || position < StartMargin) {
            positions.remove(key)
            watched.remove(key)
            return
        }
        positions[key] = position
        watched[key] = position.toFloat() / duration
        while (positions.size > Remembered) {
            val oldest = positions.keys.first()
            positions.remove(oldest)
            watched.remove(oldest)
        }
    }

    /** Videos being saved to the gallery, and how much of each is in: 0–1, or -1 while the size is not known. */
    internal val saving = mutableStateMapOf<String, Float>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Saves [video] to the gallery, saying where once done. It goes on when the viewer closes. */
    fun save(context: Context, video: ViewerVideo) {
        if (video.key in saving) return
        val start = {
            saving[video.key] = -1f
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching { VideoSaver.save(this@VideoViewer.context, video) { saving[video.key] = it } }.getOrNull()
                }
                saving.remove(video.key)
                if (saved != null) {
                    context.toastShort(context.getString(R.string.toast_photo_saved, saved))
                } else {
                    context.toastShort(R.string.video_save_failed)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            start()
            return
        }
        PermissionUtils.askPermission(
            context,
            PermissionUtils.PermissionData(
                listOf(PermissionUtils.READ_EXTERNAL_STORAGE, PermissionUtils.WRITE_EXTERNAL_STORAGE),
                context.getString(R.string.tip_permission_storage),
            ),
            R.string.video_save_no_permission,
        ) { start() }
    }

    internal fun closed() {
        opening = null
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        loaded = null
    }

    @OptIn(UnstableApi::class)
    private fun createPlayer(): ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(VideoCache.dataSource(context)))
        // Give way to calls and other players, and stop when headphones are unplugged
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private companion object {
        const val Remembered = 300

        /** Left this close to either end, a video counts as not started or as seen through. */
        const val StartMargin = 3_000L
        const val EndMargin = 3_000L
    }
}

/** Writes videos to the gallery's movies, in the app's folder. */
@OptIn(UnstableApi::class)
private object VideoSaver {
    /**
     * Downloads [video] into the gallery, through the cache so a video already played is not
     * downloaded again, reporting how much of it is in to [onProgress].
     *
     * @return where it went, as the user would look for it
     */
    fun save(context: Context, video: ViewerVideo, onProgress: (Float) -> Unit): String {
        val name = Uri.parse(video.key).lastPathSegment?.takeIf { it.isNotBlank() }
            ?.let { if (it.endsWith(".mp4", ignoreCase = true)) it else "$it.mp4" }
            ?: "video_${System.currentTimeMillis()}.mp4"
        val folder = Environment.DIRECTORY_MOVIES + File.separator + FileUtil.FILE_FOLDER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, folder)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("no gallery entry")
            try {
                resolver.openOutputStream(uri)!!.use { copy(context, video, it, onProgress) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Throwable) {
                resolver.delete(uri, null, null)
                throw e
            }
            return folder
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), FileUtil.FILE_FOLDER)
        if (!dir.exists() && !dir.mkdirs()) error("no folder")
        val file = File(dir, name)
        try {
            file.outputStream().use { copy(context, video, it, onProgress) }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        @Suppress("DEPRECATION")
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
        return file.path
    }

    private fun copy(context: Context, video: ViewerVideo, out: OutputStream, onProgress: (Float) -> Unit) {
        val source = VideoCache.dataSource(context).createDataSource()
        val length = source.open(DataSpec(Uri.parse(video.url)))
        try {
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            var shown = -1
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                out.write(buffer, 0, read)
                total += read
                if (length > 0) {
                    val percent = (total * 100 / length).toInt()
                    if (percent != shown) {
                        shown = percent
                        onProgress(percent / 100f)
                    }
                }
            }
        } finally {
            source.close()
        }
    }
}

/** Videos seen before, kept on disk so opening one again or seeking back does not download it again. */
@OptIn(UnstableApi::class)
internal object VideoCache {
    private const val MaxBytes = 256L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null

    private fun cache(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "video"),
            LeastRecentlyUsedCacheEvictor(MaxBytes),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
    }

    /** How much of the videos is kept, not counting the cache's own records. */
    fun size(context: Context): Long = cache(context.applicationContext).cacheSpace

    /** Forgets every video kept; one playing reads on from the network. */
    fun clear(context: Context) {
        val cache = cache(context.applicationContext)
        cache.keys.toList().forEach { runCatching { cache.removeResource(it) } }
    }

    fun dataSource(context: Context): DataSource.Factory {
        val app = context.applicationContext
        // The video servers may refuse requests that do not come from the app
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(getUserAgent("tieba/${TiebaProfile.PROTO.version}"))
        return CacheDataSource.Factory()
            .setCache(cache(app))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(app, http))
            .setCacheKeyFactory { spec -> spec.uri.buildUpon().clearQuery().build().toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}

/** The window's video viewer; null where there is none, as in previews and tests. */
val LocalVideoViewer = staticCompositionLocalOf<VideoViewer?> { null }

/** Makes the window's video viewer. */
@Composable
internal fun rememberVideoViewer(context: Context): VideoViewer =
    remember(context) { VideoViewer(context.applicationContext) }
