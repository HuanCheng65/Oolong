package oolong.client.ui.captcha

import oolong.core.network.VerificationChallenge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptchaPassedTest {
    @Test
    fun `a pass gives its data as the answer`() {
        assertEquals("""{"pass":true,"ds":"x","tk":"y"}""", captchaPassed("""{"code":0,"data":{"pass":true,"ds":"x","tk":"y"}}"""))
    }

    @Test
    fun `a failure, an error or something else gives no answer`() {
        assertNull(captchaPassed("""{"code":0,"data":{"pass":false}}"""))
        assertNull(captchaPassed("""{"code":1,"data":{"pass":true}}"""))
        assertNull(captchaPassed("""{"code":0}"""))
        assertNull(captchaPassed("not json"))
    }

    @Test
    fun `only type 6 with an address opens in the app`() {
        assertTrue(VerificationChallenge.Captcha("m", "6", "https://seccaptcha.baidu.com/x", null).inApp)
        assertFalse(VerificationChallenge.Captcha("m", "4", "https://x", null).inApp)
        assertFalse(VerificationChallenge.Captcha("m", "6", "", null).inApp)
    }
}
