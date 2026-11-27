package oolong.client.ui.reply

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.data.post.CaptchaAnswer
import oolong.client.data.post.PostRepository
import oolong.client.data.post.PostedReply
import oolong.client.data.post.ReplyTarget
import oolong.client.testing.MainDispatcherRule
import oolong.client.ui.captcha.CaptchaPassed
import oolong.client.ui.captcha.CaptchaResults
import oolong.core.network.VerificationChallenge
import oolong.core.network.VerificationRequiredException
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReplyViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<PostRepository>()
    private val target = ReplyTarget(52, "测试", 9)

    @Test
    fun `uploads the pictures first and posts them after the text`() = runTest {
        coEvery { repository.uploadPictures("测试", listOf("content://a"), true) } returns listOf("#(pic,1,2,3)")
        coEvery { repository.reply(target, "文字\n#(pic,1,2,3)", null) } returns PostedReply(9, 100, "")
        val viewModel = ReplyViewModel(repository, mockk(relaxed = true), backgroundScope)
        viewModel.addImages(listOf("content://a"))
        viewModel.setOriginalImages(true)

        viewModel.send(target, "文字", tbs = null)
        runCurrent()

        assertEquals(PostedReply(9, 100, ""), viewModel.sent.first())
        assertEquals(true, viewModel.state.value.sent)
        assertEquals(false, viewModel.state.value.busy)
    }

    @Test
    fun `nothing is posted when a picture fails to upload`() = runTest {
        coEvery { repository.uploadPictures(any(), any(), any()) } throws IOException()
        val viewModel = ReplyViewModel(repository, mockk(relaxed = true), backgroundScope)
        viewModel.addImages(listOf("content://a", "content://b"))
        viewModel.removeImage(0)

        viewModel.send(target, "文字", tbs = null)
        runCurrent()

        coVerify { repository.uploadPictures("测试", listOf("content://b"), false) }
        coVerify(exactly = 0) { repository.reply(any(), any(), any()) }
        assertIs<ReplyError.Upload>(viewModel.state.value.error)
        assertEquals(false, viewModel.state.value.busy)
    }

    @Test
    fun `the little tail goes after the text unless turned off`() = runTest {
        coEvery { repository.reply(target, any(), null) } returns PostedReply(9, 100, "")
        val viewModel = ReplyViewModel(repository, mockk(relaxed = true), backgroundScope)

        viewModel.send(target, "文字", tbs = null, tail = "尾巴")
        runCurrent()
        viewModel.begin()
        viewModel.setWithTail(false)
        viewModel.send(target, "再来", tbs = null, tail = "尾巴")
        runCurrent()

        coVerify { repository.reply(target, "文字\n尾巴", null) }
        coVerify { repository.reply(target, "再来", null) }
    }

    @Test
    fun `a request to verify is kept for the composer to show`() = runTest {
        val challenge = VerificationChallenge.Security(code = 3250021)
        coEvery { repository.reply(any(), any(), any()) } throws VerificationRequiredException(3250021, "", challenge)
        val viewModel = ReplyViewModel(repository, mockk(relaxed = true), backgroundScope)

        viewModel.send(target, "文字", tbs = null)
        runCurrent()

        assertEquals(ReplyError.Verification(challenge), viewModel.state.value.error)
        assertEquals(false, viewModel.state.value.sent)
        assertEquals(false, viewModel.state.value.busy)
    }

    @Test
    fun `a captcha the app can show is passed and the same reply sent again with its answer`() = runTest {
        val challenge = VerificationChallenge.Captcha("md5", "6", "https://seccaptcha.baidu.com/page", null)
        coEvery { repository.reply(target, "文字", null, null) } throws VerificationRequiredException(6, "", challenge)
        coEvery { repository.reply(target, "文字", null, CaptchaAnswer("{}", "md5", "6")) } returns PostedReply(9, 100, "")
        val viewModel = ReplyViewModel(repository, mockk(relaxed = true), backgroundScope)

        viewModel.send(target, "文字", tbs = null)
        runCurrent()
        assertEquals(true, (viewModel.state.value.error as ReplyError.Verification).canVerify)
        val key = viewModel.captcha()!!
        assertEquals("https://seccaptcha.baidu.com/page", key.url)

        CaptchaResults.report(CaptchaPassed(key.request, "{}"))
        runCurrent()

        assertEquals(PostedReply(9, 100, ""), viewModel.sent.first())
    }

    @Test
    fun `a captcha of another kind still points to the official client`() = runTest {
        coEvery { repository.reply(target, "文字", null, null) } throws
            VerificationRequiredException(5, "", VerificationChallenge.Captcha("md5", "4", "https://x", null))
        val viewModel = ReplyViewModel(repository, mockk(relaxed = true), backgroundScope)

        viewModel.send(target, "文字", tbs = null)
        runCurrent()

        assertEquals(false, (viewModel.state.value.error as ReplyError.Verification).canVerify)
        assertEquals(null, viewModel.captcha())
    }
}
