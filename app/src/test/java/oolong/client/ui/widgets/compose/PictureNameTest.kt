package oolong.client.ui.widgets.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PictureNameTest {
    @Test
    fun `every size of a picture shares its name`() {
        val name = "034f8344ad3459828bc0ea9a4af431adcaef8486"
        val sizes = listOf(
            "https://tiebapic.baidu.com/forum/w%3D1080%3Bq%3D95%3Bg%3D0/sign=ae5cee3f/$name.jpg?tbpicau=2026-11-20-05_2145a552",
            "https://tiebapic.baidu.com/forum/w%3D960%3Bq%3D60/sign=a1554836/$name.jpg?tbpicau=2026-11-20-05_1abb8613",
            "https://tiebapic.baidu.com/forum/pic/item/$name.jpg?tbpicau=2026-11-20-05_0d363d62",
            "http://tiebapic.baidu.com/forum/pic/item/$name.png",
        )
        sizes.forEach { assertEquals(name, pictureName(it), it) }
    }

    @Test
    fun `addresses not named by a picture's hash have no name`() {
        assertNull(pictureName("https://gss0.bdstatic.com/6LZ1dD3d1sgCo2Kml5_Y_D3/sys/portrait/item/tb.1.e84bc6e4.fqpFKXLQx6oIQ9OUR5rg1Q"))
        assertNull(pictureName("https://host/meme/xyz.jpg"))
        assertNull(pictureName("https://host/034f8344ad3459828bc0ea9a4af431adcaef8486.jpg.mp4"))
    }
}
