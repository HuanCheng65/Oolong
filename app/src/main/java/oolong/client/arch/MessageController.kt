package oolong.client.arch

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import oolong.client.data.error.userMessage
import oolong.client.diagnostics.ErrorRecords
import oolong.client.ui.designsystem.AppToast
import javax.inject.Inject
import javax.inject.Singleton

/** Shows short messages to the user, from code that has no context, such as a ViewModel. */
interface MessageController {
    fun show(@StringRes message: Int, vararg args: Any)

    /** Shows [message] with the error's own message as its argument. */
    fun showError(@StringRes message: Int, error: Throwable) = show(message, error.message.orEmpty())
}

@Singleton
class ToastMessageController @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) : MessageController {
    override fun show(message: Int, vararg args: Any) {
        scope.launch {
            val text = context.getString(message, *args)
            // Over the app when it is in front; the system's toast only when it is not
            if (!AppToast.post(text)) Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    override fun showError(message: Int, error: Throwable) {
        show(message, error.userMessage(context))
        // An error the app can't explain is kept, to be found under 错误记录
        scope.launch(Dispatchers.IO) { runCatching { ErrorRecords.record(context, error) } }
    }
}
