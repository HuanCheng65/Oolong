package oolong.client.ui.page.photoview

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import oolong.client.model.PicturePage
import oolong.client.model.ThreadPicture
import oolong.client.models.LoadPicPageData
import oolong.client.models.PhotoViewData
import oolong.client.models.PicItem
import oolong.client.repository.PhotoRepository
import oolong.client.testing.MainDispatcherRule
import org.junit.Rule
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoViewViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<PhotoRepository>()

    private fun item(id: String) = PicItem(picId = id, picIndex = 0, url = "u$id", originUrl = "o$id", showOriginBtn = false, originSize = 0)

    private fun picture(id: String, index: Int) = ThreadPicture(id, "o$id", null, index)

    private val source = LoadPicPageData(
        forumId = 52, forumName = "测试", seeLz = false, objType = "pb", picId = "c", picIndex = 3,
        threadId = 9, postId = 0, originUrl = null,
    )

    @Test
    fun `numbers the given pictures before the loaded ones and opens on the one tapped`() = runTest {
        coEvery { repository.pictures(source, "c", 3, false) } returns
            PicturePage(total = 20, pictures = listOf(picture("c", 13), picture("d", 14)))
        val viewModel = PhotoViewViewModel(repository)

        val data = PhotoViewData(data = source, picItems = listOf(item("a"), item("b"), item("c")), index = 2)
        viewModel.start(data)
        // Shown again, as when the window is recreated: nothing starts over
        viewModel.start(data)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf("a" to 11, "b" to 12, "c" to 13, "d" to 14), state.pictures.map { it.picId to it.overallIndex })
        assertEquals(2, state.currentIndex)
        assertEquals(true, state.hasPrevious)
        assertEquals(true, state.hasNext)
        coVerify(exactly = 1) { repository.pictures(any(), any(), any(), any()) }
    }

    @Test
    fun `given pictures the loaded ones already have are not shown twice`() = runTest {
        coEvery { repository.pictures(source, "c", 3, false) } returns
            PicturePage(total = 20, pictures = listOf(picture("b", 12), picture("c", 13), picture("d", 14)))
        val viewModel = PhotoViewViewModel(repository)

        viewModel.start(PhotoViewData(data = source, picItems = listOf(item("a"), item("b"), item("c")), index = 2))
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf("a" to 11, "b" to 12, "c" to 13, "d" to 14), state.pictures.map { it.picId to it.overallIndex })
        assertEquals(2, state.currentIndex)
    }

    @Test
    fun `loading earlier pictures keeps the current one on screen`() = runTest {
        coEvery { repository.pictures(source, "c", 3, false) } returns PicturePage(20, listOf(picture("c", 3)))
        coEvery { repository.pictures(source, "a", 1, true) } returns PicturePage(20, emptyList())
        coEvery { repository.pictures(source, "c", 3, true) } returns PicturePage(20, listOf(picture("x", 1), picture("y", 2)))
        val viewModel = PhotoViewViewModel(repository)
        viewModel.start(PhotoViewData(data = source, picItems = emptyList()))
        runCurrent()

        viewModel.loadPrevious(viewModel.state.value.pictures.single())
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf("x", "y", "c"), state.pictures.map { it.picId })
        assertEquals(2, state.currentIndex)
        assertEquals(false, state.hasPrevious)
    }

    @Test
    fun `opening other pictures drops the ones shown before`() = runTest {
        coEvery { repository.pictures(source, "c", 3, false) } returns PicturePage(20, listOf(picture("c", 3)))
        val viewModel = PhotoViewViewModel(repository)

        viewModel.start(PhotoViewData(data = source, picItems = listOf(item("a"), item("b"), item("c")), index = 2))
        viewModel.start(PhotoViewData(picItems = listOf(item("x"), item("y")), index = 1))
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf("x", "y"), state.pictures.map { it.picId })
        assertEquals(1, state.currentIndex)
        assertEquals(null, state.source)
    }

    @Test
    fun `falls back to the given pictures when loading fails`() = runTest {
        coEvery { repository.pictures(any(), any(), any(), any()) } throws IOException()
        val viewModel = PhotoViewViewModel(repository)

        viewModel.start(PhotoViewData(data = source, picItems = listOf(item("a"), item("b")), index = 1))
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf("a", "b"), state.pictures.map { it.picId })
        assertEquals(1, state.currentIndex)
        assertEquals(null, state.source)
    }
}
