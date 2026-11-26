package oolong.client.data.identity

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.edit
import oolong.client.App
import oolong.client.App.Companion.INSTANCE
import oolong.client.data.settings.AppDataPreferences
import oolong.client.util.Base32
import oolong.client.util.toMD5
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

object UIDUtil {
    @get:SuppressLint("HardwareIds")
    val androidId: String
        get() = getAndroidId("")

    @SuppressLint("HardwareIds")
    fun getAndroidId(defaultValue: String): String {
        val androidId =
            Settings.Secure.getString(INSTANCE.contentResolver, Settings.Secure.ANDROID_ID)
        return androidId ?: defaultValue
    }

    fun getOAID(): String {
        if (App.Config.encodedOAID.isBlank()) return ""
        val raw = "A10-${App.Config.encodedOAID}-"
        val sign = Base32.encode(IdCheck.of(raw.toByteArray()))
        return "$raw$sign"
    }

    fun getAid(): String {
        val raw = "com.helios" + getAndroidId("000000000") + uUID
        val bytes = getSHA1(raw)
        val encoded = Base32.encode(bytes)
        val rawAid = "A00-$encoded-"
        val sign = Base32.encode(IdCheck.of(rawAid.toByteArray()))
        return "$rawAid$sign"
    }

    private fun getSHA1(str: String): ByteArray {
        var sha1: ByteArray = "".toByteArray()
        try {
            val digest = MessageDigest.getInstance("SHA1")
            sha1 = digest.digest(str.toByteArray(StandardCharsets.UTF_8))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sha1
    }

    val newCUID: String
        get() = "baidutiebaapp$uUID"

    val cUID: String
        get() {
            return "com.baidu$androidId".toMD5().uppercase()
        }

    val finalCUID: String
        get() {
            var imei = MobileInfoUtil.getIMEI(INSTANCE)
            if (TextUtils.isEmpty(imei)) {
                imei = "0"
            }
            return cUID + "|" + StringBuffer(imei).reverse()
        }

    @get:SuppressLint("ApplySharedPref")
    val uUID: String
        get() {
            var uuid = INSTANCE.getSharedPreferences(AppDataPreferences, Context.MODE_PRIVATE)
                .getString("uuid", null)
            if (uuid == null) {
                uuid = UUID.randomUUID().toString()
                INSTANCE.getSharedPreferences(AppDataPreferences, Context.MODE_PRIVATE)
                    .edit { putString("uuid", uuid) }
            }
            return uuid
        }
}
