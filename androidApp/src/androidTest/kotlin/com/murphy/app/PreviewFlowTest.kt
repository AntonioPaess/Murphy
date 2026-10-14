package com.murphy.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test

class PreviewFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun demoNeverClaimsARealSend() {
        rule.onNodeWithText("DEMO MODE · Bluetooth not connected").assertIsDisplayed()
        rule.onNodeWithText("Explore the demo").performClick()
        rule.onNodeWithText("Your name").performTextInput("Alex")
        rule.onNodeWithText("Group name").performTextInput("Trail")
        rule.onNodeWithText("Open demo group").performScrollTo().performClick()
        rule.onNodeWithText("direct peers").assertIsDisplayed()
        keepScreenshot("android-group.png")
        rule.onNodeWithTag("tab-CHAT").performClick()
        rule.onNodeWithText("Demo message").performScrollTo().performTextInput("Hello trail")
        rule.onNodeWithText("Save unsent draft").performScrollTo().performClick()
        rule.onNodeWithText("Hello trail").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Not sent · demo only").performScrollTo().assertIsDisplayed()
        keepScreenshot("android-chat.png")
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("tab-CHAT").performClick()
        rule.onNodeWithText("Hello trail").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Not sent · demo only").performScrollTo().assertIsDisplayed()
    }

    @Test fun sosRequiresConfirmationAndIsNeverMarkedAsSent() {
        openGroup()
        rule.onNodeWithText("Preview SOS").performScrollTo().performClick()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("SOS preview · not sent").assertDoesNotExist()
        rule.onNodeWithText("Preview SOS").performClick()
        rule.onNodeWithText("Open preview").performClick()
        rule.onNodeWithText("SOS preview · not sent").assertIsDisplayed()
        rule.onNodeWithText("Close preview").performClick()
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("SOS preview closed").assertIsDisplayed()
    }

    private fun openGroup() {
        rule.onNodeWithText("Explore the demo").performClick()
        rule.onNodeWithText("Your name").performTextInput("Alex")
        rule.onNodeWithText("Group name").performTextInput("Trail")
        rule.onNodeWithText("Open demo group").performScrollTo().performClick()
    }

    private fun keepScreenshot(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Semantic interactions can pass behind an OS error dialog. Refuse that QA capture.
        rule.waitUntil(timeoutMillis = 5_000) {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() ==
                instrumentation.targetContext.packageName
        }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        FileOutputStream(File(instrumentation.targetContext.cacheDir, name)).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
}
