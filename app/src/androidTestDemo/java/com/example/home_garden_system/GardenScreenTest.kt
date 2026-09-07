package com.example.home_garden_system

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class GardenScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun demoCanEnterGardenAndChooseManualWatering() {
        compose.onNodeWithText("Jelajahi kebun demo").performClick()
        compose.onNodeWithText("SIMULASI DEMO").assertIsDisplayed()
        compose.onNodeWithText("Manual", useUnmergedTree = true).performScrollTo().performClick()
        // The mode-save snackbar temporarily overlays the bottom button's touch target.
        compose.waitUntil(7000) {
            compose.onAllNodesWithText("Pengaturan berhasil disimpan.").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Siram sekarang").performScrollTo().assertIsEnabled().performClick()
        try {
            compose.waitUntil(5000) { compose.onAllNodesWithText("Pompa menyala").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError(compose.onRoot().printToString(), error)
        }
        compose.onNodeWithText("Pompa menyala").performScrollTo().assertIsDisplayed()
    }

    @Test fun automaticModeDoesNotAllowManualWatering() {
        compose.onNodeWithText("Jelajahi kebun demo").performClick()
        compose.onNodeWithText("Siram sekarang").performScrollTo().assertIsNotEnabled()
    }
}
