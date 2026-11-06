package dev.anima.resonance.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign

/**
 * Android shell entry point. Bootstrap screen only — the real instrument UI
 * (dial, modes, presets) is implemented in the shared Compose module and wired
 * in here during the UI phase.
 */
class ResonanceApp : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text(
                        text = "Resonance",
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
