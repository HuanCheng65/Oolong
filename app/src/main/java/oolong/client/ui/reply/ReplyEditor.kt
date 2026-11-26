package oolong.client.ui.reply

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.MimeTypeMap
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import oolong.client.data.emoticon.EmoticonSpanV2
import oolong.client.ui.designsystem.theme.ExtendedTheme
import oolong.client.util.StringUtil
import oolong.client.util.hideKeyboard
import oolong.client.util.showKeyboard
import java.io.File
import java.util.UUID

/**
 * The reply's text, and the keyboard, driven from Compose. The text keeps emoticons as their
 * `#(name)` codes, which is how they are sent; the field shows them as pictures.
 */
@Stable
class ReplyEditorState {
    internal var view: ReplyEditText? = null
        set(value) {
            field = value
            pending?.let { text -> value?.let { show(it, text) } }
            pending = null
            if (focusPending && value != null) {
                focusPending = false
                value.post { showKeyboard(value.context, value) }
            }
        }
    private var pending: String? = null
    private var focusPending = false

    /**
     * Takes pictures dropped on the field or sent by the keyboard, copied into the app; null
     * where the reply can't have pictures, which leaves them to the field as usual.
     */
    var onImages: ((List<Uri>) -> Unit)? = null

    /** Sends the reply, as Ctrl+Enter on a keyboard asks; null while it can't be sent. */
    var onSend: (() -> Unit)? = null

    /** What is written, emoticons as their codes. */
    var text by mutableStateOf("")
        internal set

    fun setText(value: String) {
        text = value
        val view = view
        if (view == null) pending = value else show(view, value)
    }

    /** Inserts [code], such as an emoticon's `#(name)`, where the cursor is. */
    fun insert(code: String) {
        val view = view ?: return setText(text + code)
        val start = view.selectionStart.coerceAtLeast(0)
        val end = view.selectionEnd.coerceAtLeast(start)
        val editable = view.text ?: return
        editable.replace(start, end, code)
        view.setText(StringUtil.getEmoticonContent(view, editable))
        view.setSelection((start + code.length).coerceAtMost(view.length()))
    }

    /** Shows the keyboard for the field, once the field is there if it is not yet. */
    fun showKeyboard() {
        val view = view
        if (view == null) focusPending = true else showKeyboard(view.context, view)
    }

    fun hideKeyboard() {
        focusPending = false
        view?.let {
            hideKeyboard(it.context, it)
            it.clearFocus()
        }
    }

    private fun show(view: ReplyEditText, value: String) {
        view.setText(StringUtil.getEmoticonContent(view, value))
        view.setSelection(view.length())
    }
}

@Composable
fun rememberReplyEditorState(): ReplyEditorState = remember { ReplyEditorState() }

/**
 * The reply's text field. [onFocus] fires when it is tapped, so a panel shown in place of the
 * keyboard can give way to it.
 */
@Composable
fun ReplyEditor(
    state: ReplyEditorState,
    hint: String,
    enabled: Boolean,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ExtendedTheme.colors
    AndroidView(
        factory = { context ->
            ReplyEditText(context).also { view ->
                view.onChange = { state.text = it }
                view.onFocused = onFocus
                view.onImages = { uris -> state.onImages?.invoke(uris) }
                view.takesImages = { state.onImages != null }
                view.onSend = { state.onSend?.invoke() != null }
                state.view = view
            }
        },
        update = { view ->
            view.hint = hint
            view.isEnabled = enabled
            view.onFocused = onFocus
            view.setTextColor(colors.text.toArgb())
            view.setHintTextColor(colors.textSecondary.toArgb())
            view.setCursorColor(colors.accent.toArgb())
        },
        onRelease = { if (state.view === it) state.view = null },
        modifier = modifier,
    )
}

/**
 * A plain multi-line field that shows emoticon codes as pictures and deletes a picture as a
 * whole: a backspace that would leave half of `#(name)` removes all of it.
 */
internal class ReplyEditText(context: Context) : AppCompatEditText(context) {
    var onChange: (String) -> Unit = {}
    var onFocused: () -> Unit = {}
    var onImages: (List<Uri>) -> Unit = {}
    var takesImages: () -> Boolean = { false }

    /** Sends the reply; false when it can't be sent. */
    var onSend: () -> Boolean = { false }
    private var fixing = false

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        setPadding(0, 0, 0, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setLineSpacing(0f, 1.35f)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        setOnFocusChangeListener { _, hasFocus -> if (hasFocus) onFocused() }
        // Pictures dragged in from another app, or sent by the keyboard, become the reply's
        // pictures rather than an address in its text
        ViewCompat.setOnReceiveContentListener(this, arrayOf("image/*")) { _, payload ->
            if (!takesImages()) return@setOnReceiveContentListener payload
            val split = payload.partition { item -> item.uri != null && payload.clip.description.hasMimeType("image/*") }
            val images = split.first?.clip
            val uris = images?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
            if (uris.isNotEmpty()) copyIn(uris)
            split.second
        }
        setOnClickListener { onFocused() }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) {
                if (fixing) return
                fixing = true
                removeBrokenEmoticons(s)
                fixing = false
                onChange(s.toString())
            }
        })
    }

    /**
     * Copies [uris] into the app before taking them: the right to read what another app hands
     * over ends with the drop, and the reply may be sent much later.
     */
    private fun copyIn(uris: List<Uri>) {
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        val resolver = context.contentResolver
        val folder = File(context.cacheDir, "dropped").apply { mkdirs() }
        scope.launch {
            val copies = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri)) ?: "jpg"
                        val file = File(folder, "${UUID.randomUUID()}.$extension")
                        resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
                        Uri.fromFile(file)
                    }.getOrNull()
                }
            }
            if (copies.isNotEmpty()) onImages(copies)
        }
    }

    // Ctrl+Enter sends, as in other apps' message fields; Enter alone starts a new line
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) && event.isCtrlPressed && onSend()) return true
        return super.onKeyDown(keyCode, event)
    }

    fun setCursorColor(color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textCursorDrawable = GradientDrawable().apply {
                setColor(color)
                setSize((2 * resources.displayMetrics.density).toInt(), 0)
            }
        }
    }

    /** Removes what is left of an emoticon whose code lost a character, along with its picture. */
    private fun removeBrokenEmoticons(text: Editable) {
        text.getSpans(0, text.length, EmoticonSpanV2::class.java).forEach { span ->
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (start < 0 || end <= start) return@forEach
            val code = text.subSequence(start, end).toString()
            if (!EmoticonCode.matches(code)) {
                text.removeSpan(span)
                text.delete(start, end)
            }
        }
        // Spans are exclusive, so text typed right after one is not swallowed by it
        text.getSpans(0, text.length, EmoticonSpanV2::class.java).forEach { span ->
            if (text.getSpanFlags(span) and Spanned.SPAN_EXCLUSIVE_EXCLUSIVE != Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) {
                val start = text.getSpanStart(span)
                val end = text.getSpanEnd(span)
                text.removeSpan(span)
                text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private companion object {
        val EmoticonCode = Regex("""#\([^()]+\)""")
    }
}
