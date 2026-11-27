package oolong.core.network.identity

/**
 * The encoding device fields are sent in when requests say `need_decrypt=1`: the bytes are
 * mixed with a fixed key a 32-bit little-endian word at a time, then written six bits per
 * character from a shuffled alphabet, three bytes to four characters, and closed with a letter
 * saying how many bytes the last group held (`A` for a full group). Blank values stay empty.
 */
object PrivacyEncoding {
    private const val ALPHABET = "qogjOuCRNkfil5p4SQ3LAmxGKZTdesvB6z_YPahMI9t80rJyHW1DEwFbc7nUVX2-"
    private const val KEY = 0x2d382324

    fun encode(value: String): String {
        if (value.isBlank()) return ""
        val data = value.encodeToByteArray()
        val length = data.size
        val words = IntArray((length + 3) / 4)
        for (i in 0 until length) words[i / 4] = words[i / 4] or ((data[i].toInt() and 0xff) shl (8 * (i % 4)))
        for (i in 0 until length / 4) words[i] = KEY xor words[i].rotateRight(3)
        if (length % 4 != 0) words[length / 4] = words[length / 4] xor KEY
        fun byte(i: Int) = if (i < length) (words[i / 4] ushr (8 * (i % 4))) and 0xff else 0
        val out = StringBuilder()
        var i = 0
        while (i < length) {
            val b0 = byte(i)
            val b1 = byte(i + 1)
            val b2 = byte(i + 2)
            out.append(ALPHABET[b0 and 0x3f])
            out.append(ALPHABET[b1 and 0x3f])
            out.append(ALPHABET[b2 and 0x3f])
            out.append(ALPHABET[((b1 shr 6) shl 2) or ((b0 shr 6) shl 4) or (b2 shr 6)])
            i += 3
        }
        out.append('A' + length % 3)
        return out.toString()
    }
}
