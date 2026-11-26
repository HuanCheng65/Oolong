package oolong.client.media

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Base64
import android.webkit.URLUtil
import android.widget.ImageView
import androidx.annotation.IntDef
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import coil3.dispose
import coil3.load
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import coil3.transform.RoundedCornersTransformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.App.Companion.INSTANCE
import oolong.client.R
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.media.image.downloadImage
import oolong.client.media.upload.PictureFormat
import oolong.client.ui.designsystem.theme.ThemeUtil
import oolong.client.util.DisplayUtil
import oolong.client.util.NetworkUtil
import oolong.client.util.PermissionUtils
import oolong.client.util.PermissionUtils.PermissionData
import oolong.client.util.PermissionUtils.askPermission
import oolong.client.util.Util
import oolong.client.util.dpToPxFloat
import oolong.client.util.toastShort
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel

object ImageUtil {
    /**
     * 智能省流
     */
    const val SETTINGS_SMART_ORIGIN = 0

    /**
     * 智能无图
     */
    const val SETTINGS_SMART_LOAD = 1

    /**
     * 始终高质量
     */
    const val SETTINGS_ALL_ORIGIN = 2

    /**
     * 始终无图
     */
    const val SETTINGS_ALL_NO = 3
    const val LOAD_TYPE_SMALL_PIC = 0
    const val LOAD_TYPE_AVATAR = 1
    const val LOAD_TYPE_NO_RADIUS = 2
    const val LOAD_TYPE_ALWAYS_ROUND = 3
    const val TAG = "ImageUtil"
    private fun isGifFile(file: File?): Boolean {
        if (file == null) return false
        try {
            return FileInputStream(file).use { isGifFile(it) }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        return false
    }

    // 判断是否为GIF文件
    private fun isGifFile(inputStream: InputStream?): Boolean {
        if (inputStream == null) return false
        val bytes = ByteArray(4)
        try {
            inputStream.read(bytes)
            val str = String(bytes)
            return str.equals("GIF8", ignoreCase = true)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    fun compressImage(
        bitmap: Bitmap,
        quality: Int = 100
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(CompressFormat.JPEG, quality, baos)
        return baos.use { it.toByteArray() }
    }

    @JvmOverloads
    fun compressImage(
        bitmap: Bitmap,
        output: File,
        maxSizeKb: Int = 100,
        initialQuality: Int = 100
    ): File {
        val baos = ByteArrayOutputStream()
        var quality = initialQuality
        bitmap.compress(CompressFormat.JPEG, quality, baos) // 质量压缩方法，这里100表示不压缩，把压缩后的数据存放到baos中
        while (baos.toByteArray().size / 1024 > maxSizeKb && quality > 0) { // 循环判断如果压缩后图片是否大于设置的最大值,大于继续压缩
            baos.reset() // 重置baos即清空baos
            quality -= 5 // 每次都减少5
            bitmap.compress(CompressFormat.JPEG, quality, baos) // 这里压缩options%，把压缩后的数据存放到baos中
        }
        try {
            val fos = FileOutputStream(output)
            try {
                fos.write(baos.toByteArray())
                fos.flush()
                fos.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        return output
    }

    @JvmOverloads
    fun bitmapToFile(
        bitmap: Bitmap,
        output: File,
        format: CompressFormat = CompressFormat.JPEG
    ): File {
        val baos = ByteArrayOutputStream()
        bitmap.compress(format, 100, baos)
        try {
            val fos = FileOutputStream(output)
            try {
                fos.write(baos.toByteArray())
                fos.flush()
                fos.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        return output
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        val bitmap = createBitmap(
            drawable.intrinsicWidth, drawable.intrinsicHeight,
            if (drawable.opacity != PixelFormat.OPAQUE) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(canvas)
        return bitmap
    }

    fun copyFile(src: FileInputStream?, dest: FileOutputStream?): Boolean {
        if (src == null || dest == null) {
            return false
        }
        val srcChannel: FileChannel?
        val dstChannel: FileChannel?
        try {
            srcChannel = src.channel
            dstChannel = dest.channel
            srcChannel.transferTo(0, srcChannel.size(), dstChannel)
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
        try {
            srcChannel.close()
            dstChannel.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return true
    }

    fun copyFile(src: File?, dest: File?): Boolean {
        if (src == null || dest == null) {
            return false
        }
        if (dest.exists()) {
            dest.delete()
        }
        try {
            dest.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val srcChannel: FileChannel?
        val dstChannel: FileChannel?
        try {
            srcChannel = FileInputStream(src).channel
            dstChannel = FileOutputStream(dest).channel
            srcChannel.transferTo(0, srcChannel.size(), dstChannel)
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
        try {
            srcChannel.close()
            dstChannel.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return true
    }

    private fun changeBrightness(imageView: ImageView, brightness: Int) {
        val cMatrix = ColorMatrix()
        cMatrix.set(
            floatArrayOf(
                1f, 0f, 0f, 0f, brightness.toFloat(), 0f, 1f, 0f, 0f, brightness.toFloat(), // 改变亮度
                0f, 0f, 1f, 0f, brightness.toFloat(), 0f, 0f, 0f, 1f, 0f
            )
        )
        imageView.colorFilter = ColorMatrixColorFilter(cMatrix)
    }

    @SuppressLint("StaticFieldLeak")
    fun download(context: Context, url: String?) {
        download(context, url, false, null)
    }

    private fun downloadForShare(context: Context, url: String?, taskCallback: ShareTaskCallback) {
        if (url == null) return
        CoroutineScope(Dispatchers.IO).launch {
            val shareUri = runCatching {
                val image = context.downloadImage(url) ?: return@runCatching null
                val folder = File(context.cacheDir, ".shareTemp").apply { mkdirs() }
                // Named for what it really is, as apps it goes to tell its kind by the name
                val format = PictureFormat.of(image) ?: PictureFormat.Jpeg
                val file = File(folder, "share_${System.currentTimeMillis()}.${format.extension}")
                file.writeBytes(image)
                FileProvider.getUriForFile(context, context.packageName + ".share.FileProvider", file)
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (shareUri != null) taskCallback.onGetUri(shareUri) else context.toastShort(R.string.toast_share_pic_failed)
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    fun download(
        context: Context,
        url: String?,
        forShare: Boolean,
        taskCallback: ShareTaskCallback?
    ) {
        if (forShare) {
            if (taskCallback != null) downloadForShare(context, url, taskCallback)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadAboveQ(context, url)
            return
        }
        askPermission(
            context,
            PermissionData(
                listOf(
                    PermissionUtils.READ_EXTERNAL_STORAGE,
                    PermissionUtils.WRITE_EXTERNAL_STORAGE
                ),
                context.getString(R.string.tip_permission_storage)
            ),
            R.string.toast_no_permission_save_photo
        ) {
            downloadBelowQ(context, url)
        }
    }

    /** Saves [file], a picture of the app's own such as an edited one, to the gallery. */
    fun saveToGallery(context: Context, file: File) {
        val save = {
            CoroutineScope(Dispatchers.IO).launch {
                writeToGallery(context, file.readBytes(), file.name, "image/jpeg")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            save()
            return
        }
        askPermission(
            context,
            PermissionData(
                listOf(
                    PermissionUtils.READ_EXTERNAL_STORAGE,
                    PermissionUtils.WRITE_EXTERNAL_STORAGE
                ),
                context.getString(R.string.tip_permission_storage)
            ),
            R.string.toast_no_permission_save_photo
        ) { save() }
    }

    private fun downloadAboveQ(context: Context, url: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        url ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val image = context.downloadImage(url) ?: return@launch saveFailed(context)
            writeDownloadToGallery(context, url, image)
        }
    }

    private fun downloadBelowQ(context: Context, url: String?) {
        url ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val image = context.downloadImage(url) ?: return@launch saveFailed(context)
            writeDownloadToGallery(context, url, image)
        }
    }

    /** Writes [image], downloaded from [url], to the gallery named and typed as what it really is. */
    private suspend fun writeDownloadToGallery(context: Context, url: String, image: ByteArray) {
        // The address may say JPEG for a WebP or a GIF
        val format = PictureFormat.of(image) ?: PictureFormat.Jpeg
        val fileName = FileUtil.changeFileExtension(URLUtil.guessFileName(url, null, format.mimeType), ".${format.extension}")
        writeToGallery(context, image, fileName, format.mimeType)
    }

    /** Writes [image] to the app's folder in the gallery as [fileName], and says where. */
    private suspend fun writeToGallery(context: Context, image: ByteArray, fileName: String, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = Environment.DIRECTORY_PICTURES + File.separator + FileUtil.FILE_FOLDER
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.DESCRIPTION, fileName)
            }
            val cr = context.contentResolver
            val uri: Uri = runCatching {
                cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }.getOrNull() ?: return saveFailed(context)
            try {
                cr.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { outputStream -> outputStream.write(image) }
                }
                withContext(Dispatchers.Main) {
                    context.toastShort(context.getString(R.string.toast_photo_saved, relativePath))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                cr.delete(uri, null, null)
                saveFailed(context)
            }
        } else {
            val pictureFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val appDir = File(pictureFolder, FileUtil.FILE_FOLDER)
            if (!appDir.exists() && !appDir.mkdirs()) return saveFailed(context)
            val destFile = File(appDir, fileName)
            if (runCatching { destFile.writeBytes(image) }.isFailure) return saveFailed(context)
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile)))
            withContext(Dispatchers.Main) {
                context.toastShort(context.getString(R.string.toast_photo_saved, destFile.path))
            }
        }
    }

    private suspend fun saveFailed(context: Context) = withContext(Dispatchers.Main) {
        context.toastShort(R.string.toast_save_pic_failed)
    }

    private fun checkGifFile(file: File) {
        if (isGifFile(file)) {
            val gifFile = File(file.parentFile, FileUtil.changeFileExtension(file.name, ".gif"))
            if (gifFile.exists()) {
                file.delete()
            } else {
                file.renameTo(gifFile)
            }
        }
    }

    fun getPicId(picUrl: String?): String {
        val fileName = URLUtil.guessFileName(picUrl, null, "image/jpeg")
        return fileName.replace(".jpg", "")
    }

    @JvmStatic
    fun getNonNullString(vararg strings: String?): String? {
        for (url in strings) {
            if (!TextUtils.isEmpty(url)) {
                return url
            }
        }
        return null
    }

    @JvmStatic
    fun getRadiusPx(context: Context): Int {
        return DisplayUtil.dp2px(context, getRadiusDp(context).toFloat())
    }

    private fun getRadiusDp(context: Context): Int {
        return Settings.of(context)[SettingKeys.Radius]
    }

    fun clear(imageView: ImageView?) {
        imageView?.dispose()
    }

    fun getPlaceHolder(context: Context, radius: Int): Drawable {
        val drawable = GradientDrawable()
        val colorResId =
            if (ThemeUtil.isDark()) R.color.color_place_holder_night else R.color.color_place_holder
        val color = ContextCompat.getColor(context, colorResId)
        drawable.setColor(color)
        drawable.cornerRadius =
            DisplayUtil.dp2px(context, radius.toFloat()).toFloat()
        return drawable
    }

    fun getPlaceHolder(context: Context, radius: Float): Drawable {
        val drawable = GradientDrawable()
        val colorResId =
            if (ThemeUtil.isDark()) R.color.color_place_holder_night else R.color.color_place_holder
        val color = ContextCompat.getColor(context, colorResId)
        drawable.setColor(color)
        drawable.cornerRadius = radius.dpToPxFloat()
        return drawable
    }

    @SuppressLint("CheckResult")
    fun load(
        imageView: ImageView,
        @LoadType type: Int,
        url: String?,
        skipNetworkCheck: Boolean,
        noTransition: Boolean,
    ) {
        if (!Util.canLoadImage(imageView.context)) {
            return
        }
        if (ThemeUtil.isDark()) {
            changeBrightness(imageView, -35)
        } else {
            imageView.clearColorFilter()
        }
        val radius = getRadiusDp(imageView.context).toFloat()
        val fromNetwork = skipNetworkCheck ||
            type == LOAD_TYPE_AVATAR ||
            imageLoadSettings == SETTINGS_ALL_ORIGIN ||
            imageLoadSettings == SETTINGS_SMART_ORIGIN ||
            (imageLoadSettings == SETTINGS_SMART_LOAD && NetworkUtil.isWifiConnected(imageView.context))
        imageView.setTag(R.id.image_load_tag, fromNetwork)
        imageView.load(url) {
            if (!fromNetwork) {
                networkCachePolicy(CachePolicy.DISABLED)
            }
            when (type) {
                LOAD_TYPE_SMALL_PIC -> {
                    placeholder(getPlaceHolder(imageView.context, radius))
                    transformations(RoundedCornersTransformation(radius.dpToPxFloat()))
                }

                LOAD_TYPE_AVATAR -> {
                    placeholder(getPlaceHolder(imageView.context, 6f))
                    transformations(RoundedCornersTransformation(6f.dpToPxFloat()))
                }

                LOAD_TYPE_NO_RADIUS -> placeholder(getPlaceHolder(imageView.context, 0f))

                LOAD_TYPE_ALWAYS_ROUND -> {
                    placeholder(getPlaceHolder(imageView.context, 100f))
                    transformations(CircleCropTransformation())
                }
            }
            crossfade(!noTransition)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun load(
        imageView: ImageView,
        @LoadType type: Int,
        url: String?,
        skipNetworkCheck: Boolean = false
    ) {
        load(imageView, type, url, skipNetworkCheck, false)
    }

    /**
     * 获取要加载的图片 Url
     *
     * @param isSmallPic   加载的是否为缩略图
     * @param originUrl    原图 Url
     * @param smallPicUrls 缩略图 Url，按照画质从好到差排序
     * @return 要加载的图片 Url
     */
    @JvmStatic
    fun getUrl(
        context: Context,
        isSmallPic: Boolean,
        originUrl: String,
        vararg smallPicUrls: String?
    ): String {
        val urls = mutableListOf(*smallPicUrls)
        if (isSmallPic) {
            if (needReverse(context)) {
                urls.reverse()
            }
            return urls.firstOrNull { !it.isNullOrEmpty() } ?: originUrl
        }
        return originUrl
    }

    private fun needReverse(context: Context): Boolean {
        return if (imageLoadSettings == SETTINGS_SMART_ORIGIN &&
            NetworkUtil.isWifiConnected(context)
        ) false
        else imageLoadSettings != SETTINGS_ALL_ORIGIN
    }

    @get:ImageLoadSettings
    private val imageLoadSettings: Int
        get() = Settings.of(INSTANCE)[SettingKeys.ImageLoadType].toInt()

    fun imageToBase64(inputStream: InputStream?): String? {
        if (inputStream == null) {
            return null
        }
        return runCatching {
            inputStream.use {
                Base64.encodeToString(inputStream.readBytes(), Base64.DEFAULT)
            }
        }.getOrNull()
    }

    fun imageToBase64(file: File?): String? {
        if (file == null) {
            return null
        }
        var result: String? = null
        try {
            val `is`: InputStream = FileInputStream(file)
            result = imageToBase64(`is`)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return result
    }

    interface ShareTaskCallback {
        fun onGetUri(uri: Uri)
    }

    @IntDef(LOAD_TYPE_SMALL_PIC, LOAD_TYPE_AVATAR, LOAD_TYPE_NO_RADIUS, LOAD_TYPE_ALWAYS_ROUND)
    @Retention(AnnotationRetention.SOURCE)
    annotation class LoadType

    @IntDef(SETTINGS_SMART_ORIGIN, SETTINGS_SMART_LOAD, SETTINGS_ALL_ORIGIN, SETTINGS_ALL_NO)
    annotation class ImageLoadSettings
}

fun ImageUtil.download(
    context: Context,
    url: String?,
    forShare: Boolean,
    taskCallback: (Uri) -> Unit
) {
    download(context, url, forShare, object : ImageUtil.ShareTaskCallback {
        override fun onGetUri(uri: Uri) {
            taskCallback(uri)
        }
    })
}
