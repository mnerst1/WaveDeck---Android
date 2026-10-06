package dev.wavedeck

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetOnboarding() {
        runBlocking {
            (rule.activity.application as WaveDeckApp).settings.flag("onboarded", false)
            (rule.activity.application as WaveDeckApp).settings.string("mode", "material")
        }
        rule.waitForIdle()
    }

    @Test
    fun onboardingCanOpenOfflineApp() {
        rule
            .onNodeWithText(rule.activity.getString(R.string.continue_action))
            .performScrollTo()
            .performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.settings)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.appearance)).assertIsDisplayed()
    }

    @Test
    fun designSwitchTransformsLibraryChromeAndPlayer() {
        val app = rule.activity.application as WaveDeckApp
        runBlocking { dev.wavedeck.data.Demo.create(app, app.database.music()) }
        rule
            .onNodeWithText(rule.activity.getString(R.string.continue_action))
            .performScrollTo()
            .performClick()
        rule.onNodeWithTag("material-navigation").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.settings)).performClick()
        rule.onNodeWithTag("design-glass").performScrollTo().performClick()
        rule.waitUntil(5000) {
            runCatching {
                    rule.onAllNodesWithTag("glass-navigation").fetchSemanticsNodes().isNotEmpty()
                }
                .getOrDefault(false)
        }
        rule.onNodeWithTag("material-navigation").assertDoesNotExist()
        rule.onNodeWithTag("design-glass").assertIsSelected()
        capture("appearance")
        rule.onNodeWithText(rule.activity.getString(R.string.library)).performClick()
        rule.onNodeWithTag("glass-library").assertIsDisplayed()
        capture("glass-library")
        rule.onNodeWithText(rule.activity.getString(R.string.home)).performClick()
        rule.onNodeWithTag("glass-home").assertIsDisplayed()
        capture("glass-home")
        rule
            .onNodeWithTag("glass-home")
            .performScrollToNode(hasText(rule.activity.getString(R.string.demo_title)))
        rule.onNodeWithText(rule.activity.getString(R.string.demo_title)).performClick()
        rule.waitUntil(10000) {
            runCatching {
                    rule.onAllNodesWithTag("glass-player").fetchSemanticsNodes().isNotEmpty()
                }
                .getOrDefault(false)
        }
        rule.onNodeWithTag("glass-player").assertIsDisplayed()
        capture("liquid-glass")
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.close)).performClick()
        rule.onNodeWithTag("glass-mini").assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.settings)).performClick()
        rule.onNodeWithTag("design-material").performScrollTo().performClick()
        rule.waitUntil(5000) {
            runCatching {
                    rule.onAllNodesWithTag("material-navigation").fetchSemanticsNodes().isNotEmpty()
                }
                .getOrDefault(false)
        }
        rule.onNodeWithTag("glass-navigation").assertDoesNotExist()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val descriptor =
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .executeShellCommand("screencap -p /sdcard/wavedeck-$name.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}
