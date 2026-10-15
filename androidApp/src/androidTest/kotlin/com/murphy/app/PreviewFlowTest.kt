package com.murphy.app

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.Assert.assertTrue

class RadioFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun approveEmulatorPermissions() {
        assertTrue("This automated no-peer test is emulator-only", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val permissions = if (Build.VERSION.SDK_INT >= 31) listOf("BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE") else listOf("ACCESS_FINE_LOCATION")
        permissions.forEach { permission ->
            instrumentation.uiAutomation.executeShellCommand("pm grant ${instrumentation.targetContext.packageName} android.permission.$permission").use {
                java.io.FileInputStream(it.fileDescriptor).readBytes()
            }
        }
    }

    @Test fun noPeerNeverClaimsTransmissionAndRotationKeepsMemoryState() {
        openGroup()
        rule.onNodeWithText("direct peers").assertIsDisplayed()
        rule.onNodeWithText("0").assertIsDisplayed()
        keepScreenshot("android-group.png")
        rule.onNodeWithTag("tab-CHAT").performClick()
        rule.onNodeWithText("Message").performScrollTo().performTextInput("Hello trail")
        rule.onNodeWithText("Send message").performScrollTo().performClick()
        rule.onNodeWithText("Hello trail").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Not sent · no connected peers").performScrollTo().assertIsDisplayed()
        keepScreenshot("android-chat.png")
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("tab-CHAT").performClick()
        rule.onNodeWithText("Hello trail").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Not sent · no connected peers").performScrollTo().assertIsDisplayed()
    }

    @Test fun sosIsUnavailableAndLeavingClearsGroup() {
        openGroup()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("SOS not available yet"))
        rule.onNodeWithText("SOS not available yet").performClick()
        rule.onNodeWithText("SOS is not available yet").assertIsDisplayed()
        rule.onNodeWithText("Got it").performClick()
        rule.onNodeWithContentDescription("Open settings").performClick()
        rule.onNodeWithText("Leave group").performClick()
        rule.onNodeWithText("Leave this group?").assertIsDisplayed()
        rule.onNodeWithText("Leave group").performClick()
        rule.onNodeWithText("Create group").performScrollTo().assertIsDisplayed()
    }

    private fun openGroup() {
        rule.onNodeWithText("Your name").performTextInput("Alex")
        rule.onNodeWithText("Group name").performTextInput("Trail")
        rule.onNodeWithText("Create group").performScrollTo().performClick()
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
