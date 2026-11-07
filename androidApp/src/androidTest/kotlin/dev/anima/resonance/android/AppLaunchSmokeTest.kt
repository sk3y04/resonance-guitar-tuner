package dev.anima.resonance.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app launches and renders its main reading surface on a real
 * device (visual gate, in headless semantics form). The
 * asserted nodes exist in EVERY state — with or without the mic
 * permission — so the test is independent of grant order.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {

    @get:org.junit.Rule
    val compose = createAndroidComposeRule<ResonanceApp>()

    @Test
    fun mainScreenRenders() {
        compose.onNodeWithTag("resonance.note").assertExists()
        compose.onNodeWithTag("resonance.preset").assertExists()
        compose.onNodeWithTag("resonance.mode").assertExists()
    }
}
