package oolong.client.testing

import android.content.Context
import android.content.ContextWrapper
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import oolong.client.App
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.RuntimeEnvironment
import java.util.Locale
import java.util.TimeZone

/**
 * Provides what legacy UI code reads from globals ([App.INSTANCE])
 * without starting [App] itself, whose `onCreate` opens the database and syncs with the server.
 *
 * Also pins the time zone and locale, which formatted dates in screenshots depend on, and keeps
 * images from loading, so that they show their placeholders instead of going to the network.
 */
class LegacyAppRule : TestWatcher() {
    private lateinit var timeZone: TimeZone
    private lateinit var locale: Locale

    override fun starting(description: Description) {
        timeZone = TimeZone.getDefault()
        locale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)

        val context = RuntimeEnvironment.getApplication()
        val app = App()
        ContextWrapper::class.java
            .getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }
            .invoke(app, context)
        App::class.java.getDeclaredField("INSTANCE")
            .apply { isAccessible = true }
            .set(null, app)
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components {
                    add(Interceptor { chain -> ErrorResult(null, chain.request, IllegalStateException("No images in tests")) })
                }
                .build()
        )
    }

    override fun finished(description: Description) {
        TimeZone.setDefault(timeZone)
        Locale.setDefault(locale)
    }
}
