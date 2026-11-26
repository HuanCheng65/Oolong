package oolong.client.util

import android.content.Context
import android.icu.text.CompactDecimalFormat
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import oolong.client.data.emoticon.EmoticonManager.getEmoticonDrawable
import oolong.client.data.emoticon.EmoticonManager.getEmoticonIdByName
import oolong.client.data.emoticon.EmoticonSpanV2
import oolong.client.data.emoticon.EmoticonUtil
import oolong.client.data.emoticon.EmoticonUtil.emoticonString
import oolong.client.data.settings.SettingKeys
import oolong.client.data.settings.Settings
import oolong.client.ui.components.TextLinkActions
import oolong.client.ui.designsystem.theme.currentColors
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.roundToInt

object StringUtil {
    @JvmStatic
    fun getEmoticonContent(
        tv: TextView,
        source: CharSequence?,
        emoticonType: Int = EmoticonUtil.EMOTICON_ALL_TYPE
    ): SpannableString {
        return try {
            if (source == null) {
                return SpannableString("")
            }
            val spannableString: SpannableString = if (source is SpannableString) {
                source
            } else {
                SpannableString(source)
            }
            val regexEmoticon = EmoticonUtil.getRegex(emoticonType)
            val patternEmoticon = Pattern.compile(regexEmoticon)
            val matcherEmoticon = patternEmoticon.matcher(spannableString)
            while (matcherEmoticon.find()) {
                val key = matcherEmoticon.group()
                val start = matcherEmoticon.start()
                val group1 = matcherEmoticon.group(1) ?: ""
                val emoticonDrawable = getEmoticonDrawable(tv.context, getEmoticonIdByName(group1))
                if (emoticonDrawable != null) {
                    val paint = tv.paint
                    val size = (-paint.ascent() + paint.descent()).roundToInt()
                    val span = EmoticonSpanV2(emoticonDrawable, size)
                    spannableString.setSpan(
                        span,
                        start,
                        start + key.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            spannableString
        } catch (e: Exception) {
            e.printStackTrace()
            val spannableString: SpannableString = if (source is SpannableString) {
                source
            } else {
                SpannableString(source)
            }
            spannableString
        }
    }

    @JvmStatic
    fun getUsernameString(context: Context, username: String, nickname: String?): CharSequence {
        val showBoth = Settings.of(context)[SettingKeys.ShowBothUsernameAndNickname]
        if (TextUtils.isEmpty(nickname)) {
            return if (TextUtils.isEmpty(username)) "" else username
        } else if (showBoth && !TextUtils.isEmpty(username) && !TextUtils.equals(
                username,
                nickname
            )
        ) {
            val builder = SpannableStringBuilder(nickname)
            builder.append(
                "($username)",
                ForegroundColorSpan(currentColors(context).textDisabled.toArgb()),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            return builder
        }
        return nickname ?: ""
    }

    @Stable
    fun getUsernameAnnotatedString(
        context: Context,
        username: String,
        nickname: String?,
        color: Color = Color.Unspecified
    ): AnnotatedString {
        val showBoth = Settings.of(context)[SettingKeys.ShowBothUsernameAndNickname]
        return buildAnnotatedString {
            if (showBoth && !nickname.isNullOrBlank() && username != nickname && username.isNotBlank()) {
                append(nickname)
                withStyle(SpanStyle(color = color)) {
                    append("($username)")
                }
            } else {
                append(nickname?.takeIf { it.isNotBlank() } ?: username)
            }
        }
    }

    /** `@name: content`, the name opening the user's profile through [links]. */
    @Stable
    fun buildAnnotatedStringWithUser(
        userId: Long,
        username: String,
        nickname: String?,
        content: String,
        links: TextLinkActions,
        context: Context,
    ): AnnotatedString {
        return buildAnnotatedString {
            withLink(links.user(userId, TextLinkStyles(SpanStyle(color = currentColors(context).emphasis)))) {
                append("@")
                append(getUsernameAnnotatedString(context, username, nickname))
            }
            append(": ")
            append(content.emoticonString)
        }
    }

    @JvmStatic
    @Stable
    fun getAvatarUrl(portrait: String?): String {
        if (portrait.isNullOrEmpty()) {
            return ""
        }
        return if (portrait.startsWith("http://") || portrait.startsWith("https://")) {
            portrait
        } else "http://tb.himg.baidu.com/sys/portrait/item/$portrait"
    }

    @JvmStatic
    fun getBigAvatarUrl(portrait: String?): String {
        if (portrait.isNullOrEmpty()) {
            return ""
        }
        return if (portrait.startsWith("http://") || portrait.startsWith("https://")) {
            portrait
        } else "http://tb.himg.baidu.com/sys/portraith/item/$portrait"
    }

    fun String.getShortNumString(): String {
        val long = toLongOrNull() ?: return ""
        return long.getShortNumString()
    }

    fun Int.getShortNumString(): String {
        return toLong().getShortNumString()
    }

    /** A count for running text: in full below 10,000, then as 1.2万 or 1.2亿, worded for the language. */
    fun Long.getShortNumString(): String {
        if (this < 10_000) return "$this"
        return CompactDecimalFormat.getInstance(Locale.getDefault(), CompactDecimalFormat.CompactStyle.SHORT).format(this)
    }

    fun Int.getShortNumeralString(): String {
        return toLong().getShortNumeralString()
    }

    /**
     * A count in the numeral face, which has no Chinese: in Chinese 1.2W from 10,000 and whole W
     * from 10,000,000; otherwise 12.3K from 10,000 and 1.2M from 1,000,000.
     */
    fun Long.getShortNumeralString(): String = when {
        this < 10_000 -> "$this"
        Locale.getDefault().language != "zh" ->
            if (this < 1_000_000) "${this / 100 / 10f}K" else "${this / 100_000 / 10f}M"
        this < 10_000_000 -> "${this / 1_000 / 10f}W"
        else -> "${this / 10_000}W"
    }
}
