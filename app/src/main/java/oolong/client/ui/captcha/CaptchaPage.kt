package oolong.client.ui.captcha

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import oolong.client.R
import oolong.client.data.web.WebSessions
import oolong.client.navigation.CaptchaKey
import oolong.client.navigation.LocalNavigator
import oolong.client.navigation.Navigator
import oolong.client.ui.components.LocalAccount
import oolong.client.ui.designsystem.AccompanistWebViewClient
import oolong.client.ui.designsystem.rememberSaveableWebViewState
import oolong.client.ui.designsystem.rememberWebViewNavigator
import oolong.client.ui.webview.WebPageScaffold
import oolong.client.ui.webview.rememberWebChromeClient
import oolong.core.network.VerificationChallenge

/** A captcha passed: the answer to send with the post again, for the request [CaptchaKey.request] named. */
data class CaptchaPassed(val request: String, val vcode: String)

/** Where captcha pages report a pass, for whoever asked for it to send the post again. */
object CaptchaResults {
    private val passed = MutableSharedFlow<CaptchaPassed>(extraBufferCapacity = 4)

    val results: SharedFlow<CaptchaPassed> = passed.asSharedFlow()

    fun report(result: CaptchaPassed) {
        passed.tryEmit(result)
    }
}

/**
 * The server's captcha page, as a task done and left. When the page reports a pass, the answer
 * goes to [CaptchaResults] and the page closes; the post waiting on it is sent again by its owner.
 */
@Composable
fun CaptchaPage(
    key: CaptchaKey,
    navigator: Navigator = LocalNavigator.current,
) {
    val account by rememberUpdatedState(LocalAccount.current)
    val state = rememberSaveableWebViewState()
    val webNavigator = rememberWebViewNavigator()
    val client = remember(key) {
        CaptchaClient(onPassed = { vcode ->
            CaptchaResults.report(CaptchaPassed(key.request, vcode))
            navigator.navigateUp()
        })
    }
    WebPageScaffold(
        initialUrl = key.url,
        state = state,
        navigator = webNavigator,
        client = client,
        chromeClient = rememberWebChromeClient(),
        onClose = { navigator.navigateUp() },
        menuItems = { emptyList() },
        attachSession = { webView ->
            WebSessions.instance.attach(webView, account)
            client.install(webView)
        },
        title = stringResource(id = R.string.title_captcha),
    )
}

/**
 * Watches the captcha page's verification requests from inside it, as their answers are only
 * there: a script wraps the page's requests and hands the answers to verification to the app.
 */
private class CaptchaClient(private val onPassed: (String) -> Unit) : AccompanistWebViewClient() {
    private val main = Handler(Looper.getMainLooper())
    private var done = false

    @SuppressLint("JavascriptInterface", "AddJavascriptInterface")
    fun install(webView: WebView) {
        webView.addJavascriptInterface(Bridge(), BridgeName)
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view.evaluateJavascript(Script, null)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        view.evaluateJavascript(Script, null)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun answered(url: String, body: String) {
            if (!url.contains(VerifyPath)) return
            val vcode = captchaPassed(body) ?: return
            main.post {
                if (done) return@post
                done = true
                onPassed(vcode)
            }
        }
    }

    private companion object {
        const val BridgeName = "OolongCaptcha"

        /** Wraps the page's XMLHttpRequest and fetch, once, to pass every answer's address and body on. */
        val Script = """
            (function () {
              if (window.__oolongCaptcha || !window.$BridgeName) return;
              window.__oolongCaptcha = true;
              var open = XMLHttpRequest.prototype.open;
              XMLHttpRequest.prototype.open = function (method, url) {
                this.__oolongUrl = String(url);
                return open.apply(this, arguments);
              };
              var send = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.send = function () {
                var xhr = this;
                xhr.addEventListener('load', function () {
                  try { window.$BridgeName.answered(xhr.responseURL || xhr.__oolongUrl || '', String(xhr.responseText)); } catch (e) {}
                });
                return send.apply(this, arguments);
              };
              if (window.fetch) {
                var fetch0 = window.fetch;
                window.fetch = function () {
                  return fetch0.apply(this, arguments).then(function (response) {
                    try {
                      response.clone().text().then(function (text) { window.$BridgeName.answered(response.url, text); });
                    } catch (e) {}
                    return response;
                  });
                };
              }
            })();
        """.trimIndent()
    }
}

/** Whether the captcha is the kind done on a web page the app can open: type 6, with its address. */
val VerificationChallenge.Captcha.inApp: Boolean
    get() = vcodeType == WebCaptchaType && picUrl.startsWith("http")

private const val WebCaptchaType = "6"

/** Where the captcha page sends what the user did, to be told whether it passed. */
internal const val VerifyPath = "seccaptcha.baidu.com/v1/webapi/verint/verify"

/**
 * The answer to send with the post again, from the verification's answer [body]: its `data`, as
 * JSON, when it says the captcha was passed; null otherwise.
 */
internal fun captchaPassed(body: String): String? {
    val json = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    if (json["code"]?.jsonPrimitive?.intOrNull != 0) return null
    val data = json["data"] as? JsonObject ?: return null
    val pass = data["pass"]?.jsonPrimitive ?: return null
    if (pass.booleanOrNull != true && pass.intOrNull != 1) return null
    return data.toString()
}
