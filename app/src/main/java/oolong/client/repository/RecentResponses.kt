package oolong.client.repository

import android.content.Context
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The last few pages of each kind of list as the server sent them, kept so that something a list
 * showed, as an advert passed off as a thread, can be looked into afterwards: exported from the
 * diagnostics page by the user, never sent by the app.
 */
fun interface RecentResponses {
    /** Keeps [page], a response of [kind], in place of the oldest one of that kind past the limit. */
    fun keep(kind: String, page: ByteArray)

    companion object {
        /** Keeps nothing, for where nothing needs looking into. */
        val None = RecentResponses { _, _ -> }

        const val Recommended = "recommended"
        const val ForumPage = "forum-page"
        const val ForumThreads = "forum-threads"
        const val ForumTab = "forum-tab"

        /** Where the app's [FileRecentResponses] keep them. */
        fun dir(context: Context) = File(context.cacheDir, "responses")

        /** Writes every kept page to [out] as a zip, a folder for each kind; false when none is kept. */
        fun export(context: Context, out: OutputStream): Boolean {
            val files = dir(context).listFiles().orEmpty().filter { it.isDirectory }
                .flatMap { kind -> kind.listFiles().orEmpty().map { kind.name to it } }
            if (files.isEmpty()) return false
            ZipOutputStream(out).use { zip ->
                files.sortedBy { it.second.name }.forEach { (kind, file) ->
                    zip.putNextEntry(ZipEntry("$kind/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            return true
        }
    }
}

/**
 * [RecentResponses] as files under [dir], a folder for each kind holding at most [perKind] pages,
 * each named by when it came.
 */
class FileRecentResponses(
    private val dir: File,
    private val perKind: Int = 8,
) : RecentResponses {
    private var last = 0L

    @Synchronized
    override fun keep(kind: String, page: ByteArray) {
        runCatching {
            val folder = File(dir, kind).apply { mkdirs() }
            // Two pages in the same millisecond keep their order
            val time = maxOf(System.currentTimeMillis(), last + 1).also { last = it }
            File(folder, "$time.bin").writeBytes(page)
            folder.listFiles().orEmpty().sortedByDescending { it.name }.drop(perKind).forEach { it.delete() }
        }
    }
}
