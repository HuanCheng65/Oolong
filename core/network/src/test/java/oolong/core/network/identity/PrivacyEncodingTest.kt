package oolong.core.network.identity

import kotlin.test.Test
import kotlin.test.assertEquals

class PrivacyEncodingTest {
    @Test
    fun `encodes whole and partial groups`() {
        assertEquals("_a2q8_aq28_qa28qALNqA", PrivacyEncoding.encode("000000000000000"))
        assertEquals("Npm-oNCwA", PrivacyEncoding.encode("google"))
        assertEquals("pJGgzNjOoqqqB", PrivacyEncoding.encode("Pixel 9"))
        assertEquals("gavu8gaiv8jcOEzOI4E8oqqSB", PrivacyEncoding.encode("0123456789abcdef"))
        assertEquals("mmqqC", PrivacyEncoding.encode("16"))
        assertEquals("uqqSB", PrivacyEncoding.encode("a"))
        assertEquals("uoqAC", PrivacyEncoding.encode("ab"))
    }

    @Test
    fun `leaves blank values empty`() {
        assertEquals("", PrivacyEncoding.encode(""))
        assertEquals("", PrivacyEncoding.encode(" "))
    }
}
