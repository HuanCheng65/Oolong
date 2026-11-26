package oolong.client.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SecondaryClickTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var secondary: Offset? = null
    private var clicks = 0

    private fun setContent() {
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .testTag("box")
                    .onSecondaryClick { secondary = it }
                    .clickable { clicks++ },
            )
        }
    }

    @Test
    fun rightClickOpensWithoutClicking() {
        setContent()
        composeRule.onNodeWithTag("box").performMouseInput { rightClick(Offset(10f, 20f)) }
        composeRule.waitForIdle()
        assertEquals(Offset(10f, 20f), secondary)
        assertEquals(0, clicks)
    }

    @Test
    fun leftClickOnlyClicks() {
        setContent()
        composeRule.onNodeWithTag("box").performMouseInput { click(Offset(10f, 20f)) }
        composeRule.waitForIdle()
        assertNull(secondary)
        assertEquals(1, clicks)
    }

    @Test
    fun touchOnlyClicks() {
        setContent()
        composeRule.onNodeWithTag("box").performTouchInput { click(Offset(10f, 20f)) }
        composeRule.waitForIdle()
        assertNull(secondary)
        assertEquals(1, clicks)
    }
}
