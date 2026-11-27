package oolong.client.data.offline

import android.content.Context
import oolong.client.ui.components.avatarName
import oolong.client.ui.components.pictureName
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Where saved threads live: a folder each under the app's files, out of reach of clearing the
 * cache. Pages are kept as the server sent them; pictures by the name every copy of them shares,
 * so they are found again whatever size or signature an address asks for.
 */
internal object OfflineFiles {
    private const val Root = "offline"
    private const val Pages = "pages"
    private const val Pictures = "pictures"

    fun root(context: Context) = File(context.filesDir, Root)

    fun threadDir(context: Context, threadId: Long) = File(root(context), threadId.toString())

    fun pageFile(context: Context, threadId: Long, page: Int) = File(File(threadDir(context, threadId), Pages), "%04d.bin".format(page))

    /** The saved pages of [threadId] in order. */
    fun pageFiles(context: Context, threadId: Long): List<File> =
        File(threadDir(context, threadId), Pages).listFiles { file -> file.name.endsWith(".bin") }.orEmpty().sortedBy { it.name }

    fun picturesDir(context: Context, threadId: Long) = File(threadDir(context, threadId), Pictures)

    /** What [file] and everything in it take, in bytes. */
    fun sizeOf(file: File): Long = if (file.isDirectory) file.listFiles().orEmpty().sumOf(::sizeOf) else file.length()

    /** The name a picture at [url] is kept under, or null when its address does not name it. */
    fun nameOf(url: String): String? = avatarName(url) ?: pictureName(url)

    /** [name] as a file name: avatars' names have a colon. */
    fun fileName(name: String) = name.replace(':', '_')

    private fun nameOfFile(fileName: String) = if (fileName.startsWith("avatar_")) "avatar:" + fileName.removePrefix("avatar_") else fileName

    @Volatile
    private var index: ConcurrentHashMap<String, File>? = null

    /** The saved picture the address [url] shows, if one is saved. */
    fun picture(context: Context, url: String): File? {
        val name = nameOf(url) ?: return null
        return pictures(context)[name]?.takeIf { it.exists() }
    }

    fun register(context: Context, name: String, file: File) {
        pictures(context)[name] = file
    }

    /** Forgets every saved picture, to be found again from the files: after deleting a thread. */
    fun reindex() {
        index = null
    }

    private fun pictures(context: Context): ConcurrentHashMap<String, File> = index ?: synchronized(this) {
        index ?: ConcurrentHashMap<String, File>().also { map ->
            root(context).listFiles().orEmpty().forEach { thread ->
                File(thread, Pictures).listFiles().orEmpty().forEach { map.putIfAbsent(nameOfFile(it.name), it) }
            }
            index = map
        }
    }
}
