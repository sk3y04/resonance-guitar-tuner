package dev.anima.resonance.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app launches and renders its main surface on a real device
 * (visual/device gate, in headless semantics form).
 *
 * These nodes exist in every permission/measurement state. The note glyph is
 * deliberately NOT asserted: with no trustworthy pitch it is absent, and
 * pretending otherwise would hide the exact behavior this audit fixes.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {

    @get:org.junit.Rule
    val compose = createAndroidComposeRule<ResonanceApp>()

    @Test
    fun mainScreenRendersStableSurface() {
        compose.onNodeWithTag("resonance.scroll").assertExists()
        compose.onNodeWithTag("resonance.preset").assertExists()
        compose.onNodeWithTag("resonance.micStatus").assertExists()
        compose.onNodeWithTag("resonance.mode").assertExists()
        compose.onNodeWithTag("resonance.openTunings").assertExists()
    }
}