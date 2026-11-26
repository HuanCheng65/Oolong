package oolong.client.data.error

import android.content.Context
import oolong.client.R
import oolong.client.data.error.Error
import oolong.core.network.TiebaException
import oolong.core.network.TiebaServerException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

fun Throwable.getErrorCode(): Int {
    return if (this is TiebaException) {
        code
    } else {
        Error.ERROR_UNKNOWN
    }
}

fun Throwable.getErrorMessage(context: Context): String =
    message?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.error_unknown)

/** The error's message as the user should read it: a network error says so instead of its technical message. */
fun Throwable.userMessage(context: Context): String =
    if (isNetworkError()) context.getString(R.string.error_network) else getErrorMessage(context)

/**
 * Whether the request got no answer: no network, the host out of reach, or the connection dropped
 * or timed out. The app's own errors are IO exceptions too, but they carry an answer, so only
 * [NoConnectivityException] among them counts.
 */
fun Throwable.isNetworkError(): Boolean = generateSequence(this) { it.cause.takeIf { cause -> cause !== it } }
    .firstOrNull { it is IOException }
    ?.let { it is NoConnectivityException || it !is TiebaException } == true

/**
 * Whether the error is of no kind the app can explain: not a lost connection, not an error the
 * server named, not being signed out, and not a cancelled task. These are the ones the app shows
 * as 「发生了一个奇怪的错误」 and keeps a record of.
 */
fun Throwable.isUnexpectedError(): Boolean =
    this !is CancellationException && this !is TiebaServerException && this !is TiebaNotLoggedInException && !isNetworkError()
