package oolong.client.ui.webview

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.location.LocationManager
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.getSystemService
import androidx.core.location.LocationManagerCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hjq.permissions.Permission
import oolong.client.App
import oolong.client.R
import oolong.client.data.link.isInternalHost
import oolong.client.data.link.isTiebaHost
import oolong.client.data.link.toTiebaKey
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.data.web.WebSessions
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.ui.components.LocalAccount
import oolong.client.ui.designsystem.AccompanistWebChromeClient
import oolong.client.ui.designsystem.AccompanistWebViewClient
import oolong.client.ui.designsystem.AppDialog
import oolong.client.ui.designsystem.AppDialogs
import oolong.client.ui.designsystem.ContextMenuItem
import oolong.client.ui.designsystem.rememberSaveableWebViewState
import oolong.client.ui.designsystem.rememberWebViewNavigator
import oolong.client.ui.designsystem.theme.Symbols
import oolong.client.ui.designsystem.theme.currentColors
import oolong.client.util.PermissionUtils
import oolong.client.util.PermissionUtils.PermissionData
import oolong.client.util.TiebaUtil
import oolong.client.util.findActivity
import java.lang.ref.WeakReference

@Composable
fun WebViewPage(
    initialUrl: String,
    navigator: Navigator = LocalNavigator.current,
) {
    val context = LocalContext.current
    val account by rememberUpdatedState(LocalAccount.current)
    val webViewState = rememberSaveableWebViewState()
    val webViewNavigator = rememberWebViewNavigator()
    val copyLink = stringResource(id = R.string.title_copy_link)
    val openInBrowser = stringResource(id = R.string.title_open_in_browser)
    val refresh = stringResource(id = R.string.title_refresh)
    WebPageScaffold(
        initialUrl = initialUrl,
        state = webViewState,
        navigator = webViewNavigator,
        client = remember(navigator) { MyWebViewClient(navigator) },
        chromeClient = rememberWebChromeClient(),
        onClose = { navigator.navigateUp() },
        attachSession = { WebSessions.instance.attach(it, account) },
        immersive = true,
        menuItems = {
            val url = webViewState.webView?.url ?: initialUrl
            listOf(
                ContextMenuItem(Symbols.Link, copyLink) { TiebaUtil.copyText(context, url) },
                ContextMenuItem(Symbols.OpenInBrowser, openInBrowser) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                },
                ContextMenuItem(Symbols.Refresh, refresh) { webViewNavigator.reload() },
            )
        },
    )
}

open class MyWebViewClient(
    protected val nativeNavigator: Navigator? = null,
) : AccompanistWebViewClient() {
    val context: Context
        get() = state.webView?.context ?: App.INSTANCE

    private fun interceptWebViewRequest(
        webView: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val newUri = request.url
        val scheme = newUri.scheme?.lowercase() ?: return false
        val host = newUri.host?.lowercase() ?: return false
        val path = newUri.path?.lowercase() ?: return false
        val isHttp = scheme.startsWith("http")
        val isTieba = isTiebaHost(host)
        val isInternal = isInternalHost(host)
        return when {
            isHttp && isTieba -> {
                newUri.toTiebaKey()?.let { direction ->
                    nativeNavigator?.navigate(direction)
                    true
                } ?: false
            }

            isHttp && !isInternal -> {
                if (Settings.of(context)[SettingKeys.UseWebView])
                    false
                else {
                    if (Settings.of(context)[SettingKeys.UseCustomTabs]) {
                        runCatching {
                            CustomTabsIntent.Builder()
                                .setShowTitle(true)
                                .setDefaultColorSchemeParams(
                                    CustomTabColorSchemeParams.Builder()
                                        .setToolbarColor(currentColors(context).sheet.toArgb())
                                        .setNavigationBarColor(currentColors(context).sheet.toArgb())
                                        .setNavigationBarDividerColor(
                                            currentColors(context).run { divider.compositeOver(sheet) }.toArgb()
                                        )
                                        .build()
                                )
                                .build()
                                .launchUrl(context, newUri)
                        }.onFailure {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    newUri
                                ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            )
                        }
                    } else {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                newUri
                            ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                    }
                    true
                }
            }

            !isHttp -> {
                val currentUri = webView.url?.toUri()
                val currentHost = currentUri?.host
                if (currentHost != null) {
                    launchThirdPartyApp(newUri, currentHost)
                }
                true
            }

            else -> false
        }
    }

    private fun launchThirdPartyApp(
        intent: Intent,
        host: String,
    ) {
        val resolves = context.packageManager.queryIntentActivities(intent, 0)
        if (resolves.isEmpty()) return
        val appName = if (resolves.size == 1) {
            resolves[0].loadLabel(context.packageManager)
        } else {
            context.getString(R.string.name_multi_app)
        }
        val scheme = intent.scheme ?: ""
        AppDialogs.ask(
            context,
            AppDialog.Permission(
                key = permissionKey("${host}_$scheme", PERMISSION_START_APP),
                title = context.getString(R.string.title_start_app_permission, host, appName),
                icon = Symbols.OpenInNew,
            ) { granted, _ -> if (granted) context.startActivity(intent) }
        )
    }

    private fun launchThirdPartyApp(
        uri: Uri,
        host: String,
    ) {
        if (uri.scheme.equals("intent", ignoreCase = true)) {
            launchThirdPartyApp(
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    .addCategory(Intent.CATEGORY_BROWSABLE), host
            )
        } else {
            launchThirdPartyApp(
                Intent(
                    Intent.ACTION_VIEW,
                    uri
                ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), host
            )
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest?): Boolean {
        if (request == null) return false
        return interceptWebViewRequest(view, request)
    }
}

/** A [MyWebChromeClient] whose file chooser opens through this composition's activity result registry. */
@Composable
fun rememberWebChromeClient(): MyWebChromeClient {
    val context = LocalContext.current
    val client = remember { MyWebChromeClient(context) }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        client.onFileChosen(it.resultCode, it.data)
    }
    SideEffect { client.chooseFile = chooser::launch }
    return client
}

class MyWebChromeClient(
    context: Context,
) : AccompanistWebChromeClient() {
    private val contextWeakReference = WeakReference(context)

    val context: Context
        get() = state.webView?.context ?: contextWeakReference.get() ?: App.INSTANCE

    private var uploadMessage: ValueCallback<Array<Uri>>? = null

    internal var chooseFile: ((Intent) -> Unit)? = null

    internal fun onFileChosen(resultCode: Int, data: Intent?) {
        uploadMessage?.onReceiveValue(FileChooserParams.parseResult(resultCode, data))
        uploadMessage = null
    }

    /** Whether a video on the page is full screen, over the whole window. */
    var isFullscreen by mutableStateOf(false)
        private set
    private var fullscreenCallback: CustomViewCallback? = null
    private var leaveFullscreen: (() -> Unit)? = null

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        val activity = context.findActivity()
        if (leaveFullscreen != null || activity == null) {
            callback.onCustomViewHidden()
            return
        }
        val decor = activity.window.decorView as ViewGroup
        view.setBackgroundColor(android.graphics.Color.BLACK)
        decor.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val controller = WindowCompat.getInsetsController(activity.window, decor)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        // Videos on the web are nearly all wide; the phone may be turned either way
        val orientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        fullscreenCallback = callback
        leaveFullscreen = {
            decor.removeView(view)
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = orientation
        }
        isFullscreen = true
    }

    override fun onHideCustomView() {
        leaveFullscreen?.invoke()
        leaveFullscreen = null
        fullscreenCallback = null
        isFullscreen = false
    }

    /** Leaves full screen, as on back or when the page goes. */
    fun exitFullscreen() {
        val callback = fullscreenCallback ?: return
        // The page is told first, and may already hide the view itself
        callback.onCustomViewHidden()
        onHideCustomView()
    }

    private fun isEnabledLocationFunction(): Boolean {
        val locationManager = context.getSystemService<LocationManager>()
        return locationManager != null && LocationManagerCompat.isLocationEnabled(locationManager)
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?,
    ) {
        if (origin == null || callback == null) return
        AppDialogs.ask(
            context,
            AppDialog.Permission(
                key = permissionKey(origin, PERMISSION_LOCATION),
                title = context.getString(
                    R.string.title_ask_permission,
                    // The site, as the address shows it, not the whole origin
                    origin.toUri().host ?: origin,
                    context.getString(R.string.common_permission_location)
                ),
                icon = Symbols.LocationOn,
            ) { granted, isForever ->
                if (!granted) {
                    callback.invoke(origin, false, false)
                } else PermissionUtils.askPermission(
                    context,
                    PermissionData(
                        listOf(
                            Permission.ACCESS_COARSE_LOCATION,
                            Permission.ACCESS_FINE_LOCATION
                        ),
                        context.getString(R.string.usage_webview_location_permission)
                    ),
                    R.string.tip_no_permission,
                    {
                        if (isEnabledLocationFunction()) {
                            callback.invoke(origin, true, isForever)
                        } else {
                            callback.invoke(origin, false, false)
                        }
                    }
                ) {
                    callback.invoke(origin, false, false)
                }
            }
        )
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        val chooseFile = chooseFile
        if (webView == null || filePathCallback == null || fileChooserParams == null || chooseFile == null) return false
        uploadMessage?.onReceiveValue(null)
        uploadMessage = filePathCallback
        chooseFile(fileChooserParams.createIntent())
        return true
    }

    override fun onJsAlert(
        view: WebView?,
        url: String?,
        message: String?,
        result: JsResult?,
    ): Boolean {
        AppDialogs.show(AppDialog.Message(message = message.orEmpty(), onConfirm = { result?.confirm() }))
        return true
    }

    override fun onJsConfirm(
        view: WebView,
        url: String?,
        message: String?,
        result: JsResult,
    ): Boolean {
        val context = view.context ?: context
        AppDialogs.show(
            AppDialog.Message(
                message = message.orEmpty(),
                onConfirm = { result.confirm() },
                cancel = context.getString(R.string.button_cancel),
                onCancel = { result.cancel() },
            )
        )
        return true
    }
}

/** The key a site's answer is kept under: what it asked about, and for whom. */
internal fun permissionKey(data: String, permission: Int) = "${data}_$permission"

private const val PERMISSION_LOCATION = 0
private const val PERMISSION_START_APP = 1
internal const val PERMISSION_CLIPBOARD_COPY = 2
