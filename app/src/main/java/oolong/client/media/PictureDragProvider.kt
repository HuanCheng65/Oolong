package oolong.client.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.runBlocking
import oolong.client.media.image.downloadImage
import oolong.client.media.upload.PictureFormat
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/**
 * A picture dragged out of the app, to whatever app it is dropped on. Dragging only hands out an
 * address; the picture is fetched when the other app asks about it, which only happens once it is
 * dropped, so a drag let go anywhere else costs nothing. It is the original, from the image cache when it was shown before and from the
 * network otherwise; should that fail, the picture as shown, which is always cached.
 *
 * Not exported: only an app the drag granted it to can read an address, and only that one.
 */
class PictureDragProvider : ContentProvider() {
    /** What each address fetched to, so the type, the name and the file all come from one fetch. */
    private val fetched = ConcurrentHashMap<Uri, File>()

    override fun onCreate(): Boolean = true

    // Each of these is only asked once the picture is dropped, so fetching for it is no waste
    override fun getType(uri: Uri): String = fetch(uri)?.let(::formatOf)?.mimeType ?: "image/*"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val file = fetch(uri)
        val row = columns.map<String, Any?> {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> file?.name
                OpenableColumns.SIZE -> file?.length()
                else -> null
            }
        }.toTypedArray()
        return MatrixCursor(columns, 1).apply { addRow(row) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = fetch(uri) ?: throw FileNotFoundException("The picture couldn't be loaded")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /**
     * The picture at [uri], fetched once: the original, or the copy shown should it fail. Named
     * by its real kind, read from its first bytes: Tieba names every picture .jpg, animated ones too.
     */
    private fun fetch(uri: Uri): File? {
        fetched[uri]?.takeIf { it.exists() }?.let { return it }
        val context = context ?: return null
        val (original, shown) = decode(uri) ?: return null
        // Asked on a binder thread of ours, never the asking app's main thread, so waiting is fine
        val bytes = runBlocking { context.downloadImage(original) ?: context.downloadImage(shown) } ?: return null
        val format = PictureFormat.of(bytes) ?: PictureFormat.Jpeg
        val folder = File(context.cacheDir, SHARE_FOLDER).apply { mkdirs() }
        val file = File(folder, "picture_${System.currentTimeMillis()}.${format.extension}").apply { writeBytes(bytes) }
        fetched[uri] = file
        return file
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        /** Where shared pictures are written, cleared with them. */
        private const val SHARE_FOLDER = ".shareTemp"

        private fun authority(context: Context) = "${context.packageName}.pictures"

        /** The address of the picture at [original], shown as [shown]. */
        fun uriFor(context: Context, original: String, shown: String): Uri {
            val key = Base64.encodeToString("$original\n$shown".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return Uri.Builder().scheme("content").authority(authority(context)).appendPath(key).build()
        }

        /** What a drag declares before anything is fetched: a picture, of a kind known only once it is. */
        const val DRAG_MIME_TYPE = "image/*"

        private fun formatOf(file: File): PictureFormat? =
            PictureFormat.entries.firstOrNull { it.extension == file.extension }

        private fun decode(uri: Uri): Pair<String, String>? {
            val key = uri.pathSegments.firstOrNull() ?: return null
            val text = runCatching { String(Base64.decode(key, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)) }.getOrNull() ?: return null
            val parts = text.split('\n')
            return if (parts.size == 2) parts[0] to parts[1] else null
        }
    }
}
