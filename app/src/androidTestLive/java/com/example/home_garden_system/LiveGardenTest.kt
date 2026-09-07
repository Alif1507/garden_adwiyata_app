package com.example.home_garden_system

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.firebase.auth.FirebaseAuth
import org.junit.Rule
import org.junit.Test

/** Requires `npm run emulators` and `npm run seed` in firebase/. */
class LiveGardenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun emulatorLoginReadsSettingsAndRestoresSessionAfterRecreation() {
        compose.runOnIdle { FirebaseAuth.getInstance().signOut() }
        compose.onNodeWithText("Email administrator").performScrollTo().performTextInput("admin@garden.test")
        compose.onNodeWithText("Kata sandi").performScrollTo().performTextInput("GardenDemo123!")
        compose.onNodeWithText("Masuk ke kebun").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Kebun Zona A").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Otomatis").performScrollTo().assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Kebun Zona A").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Kebun Zona A").assertExists()
    }
}
