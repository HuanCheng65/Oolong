package oolong.client.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import oolong.client.model.Account
import oolong.client.navigation.NoOpNavigator
import oolong.client.testing.LegacyAppRule
import oolong.client.testing.captureScreenshot
import oolong.client.ui.components.LocalAccount
import oolong.client.ui.components.LocalAllAccounts
import oolong.client.ui.settings.account.AccountManagePage
import oolong.client.ui.settings.custom.CustomSettingsPage
import oolong.client.ui.settings.custom.FontSizePage
import oolong.client.ui.settings.habit.HabitSettingsPage
import oolong.client.ui.settings.more.MoreSettings
import oolong.client.ui.settings.more.MoreSettingsPage
import oolong.client.ui.settings.oksign.OKSignSettingsPage
import oolong.client.ui.settings.post.PostSettingsPage
import oolong.client.ui.settings.privacy.PrivacySettingsPage
import oolong.client.ui.settings.theme.AppThemePage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+w393dp-h851dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val legacyApp = LegacyAppRule()

    @Test
    fun hub() {
        captureScreenshot { SettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    @Config(qualifiers = "+en")
    fun hubEnglish() {
        captureScreenshot { SettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun appearance() {
        captureScreenshot { CustomSettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    @Config(qualifiers = "+en")
    fun appearanceEnglish() {
        captureScreenshot { CustomSettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun fontSize() {
        captureScreenshot { FontSizePage(navigator = NoOpNavigator) }
    }

    @Test
    fun browsing() {
        captureScreenshot { HabitSettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun posting() {
        captureScreenshot { PostSettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun theme() {
        captureScreenshot { AppThemePage(navigator = NoOpNavigator) }
    }

    @Test
    fun privacy() {
        captureScreenshot { PrivacySettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun other() {
        withFixedStorage { captureScreenshot { MoreSettingsPage(navigator = NoOpNavigator) } }
    }

    @Test
    @Config(qualifiers = "+en")
    fun otherEnglish() {
        withFixedStorage { captureScreenshot { MoreSettingsPage(navigator = NoOpNavigator) } }
    }

    @Test
    fun signIn() {
        captureScreenshot { OKSignSettingsPage(navigator = NoOpNavigator) }
    }

    @Test
    fun account() {
        captureScreenshot { AccountManagePage(navigator = NoOpNavigator) }
    }

    @Test
    fun accountLoggedIn() {
        val current = Account(id = 1, uid = "1", name = "echo", nameShow = "幻星夜Echo")
        val other = Account(id = 2, uid = "2", name = "alt", nameShow = "小号")
        captureScreenshot {
            CompositionLocalProvider(
                LocalAccount provides current,
                LocalAllAccounts provides listOf(current, other),
            ) {
                AccountManagePage(navigator = NoOpNavigator)
            }
        }
    }

    /** Shows the storage row with a fixed size, not the test build's own, which changes with the code. */
    private fun withFixedStorage(capture: () -> Unit) {
        val measure = MoreSettings.measureStorage
        MoreSettings.measureStorage = { 2_600_000L }
        try {
            capture()
        } finally {
            MoreSettings.measureStorage = measure
        }
    }
}
