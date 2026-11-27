package oolong.client.media.image

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.intercept.Interceptor
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import oolong.client.data.offline.OfflineFiles

/**
 * The app's image loader: downloads through OkHttp, reporting progress to [ImageDownloadProgress],
 * and plays GIFs, and on newer systems animated WebP and HEIF too. Pictures are cached under their
 * address without the [ExpiringParameter], so one fetched again with a new one is not downloaded again.
 *
 * With that parameter expired or wrong, the picture server still answers with a picture: a
 * placeholder of its own, marked only by a header ([NotAuthorisedHeader]). Taken for the picture,
 * it was cached under the address without the parameter and then shown for good, also where the
 * address came with a valid one, as a forum's avatar fetched once from an old visit. Such an
 * answer counts as a failure instead.
 */
fun newAppImageLoader(context: Context): ImageLoader {
    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val agent = System.getProperty("http.agent")
            val request = if (agent.isNullOrEmpty()) chain.request() else chain.request().newBuilder().header("User-Agent", agent).build()
            chain.proceed(request)
        }
        .addInterceptor(RefusedPictures)
        .addInterceptor(ImageDownloadProgress.interceptor)
        .build()
    return ImageLoader.Builder(context)
        // As quick as the app's other fades in (design-system.md §7.2), instead of Coil's 200 ms
        .crossfade(120)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
            // A picture saved with a thread is read from its file, whatever address asks for it
            add(
                Interceptor { chain ->
                    val saved = (chain.request.data as? String)?.let { OfflineFiles.picture(context, it) }
                    if (saved == null) chain.proceed() else chain.withRequest(chain.request.newBuilder().data(saved).build()).proceed()
                },
            )
            add(
                Interceptor { chain ->
                    val key = (chain.request.data as? String)?.let(::diskCacheKey)
                    if (key == null || chain.request.diskCacheKey != null) {
                        chain.proceed()
                    } else {
                        chain.withRequest(chain.request.newBuilder().diskCacheKey(key).build()).proceed()
                    }
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()
}

/** Pictures' addresses carry a signature that expires and changes between responses. */
private const val ExpiringParameter = "tbpicau"

/** Set, as "img not auth", on the placeholder the picture server sends for a refused address. */
private const val NotAuthorisedHeader = "Error-Message"

/** Turns the picture server's placeholder for a refused address into the failure it stands for. */
internal val RefusedPictures = okhttp3.Interceptor { chain ->
    val response = chain.proceed(chain.request())
    val refused = response.header(NotAuthorisedHeader)
    if (refused == null) response else response.newBuilder().code(403).message(refused).build()
}

/**
 * Before the keys, so pictures cached before refused answers were told apart are not used: some
 * of them are the server's placeholder.
 */
private const val DiskCacheKeyVersion = "v2:"

/** The address without the [ExpiringParameter], or null when it has none. */
internal fun diskCacheKey(url: String): String? {
    if (!url.contains(ExpiringParameter)) return null
    val stripped = url.toHttpUrlOrNull()?.newBuilder()?.removeAllQueryParameters(ExpiringParameter)?.build()?.toString() ?: return null
    return DiskCacheKeyVersion + stripped
}
