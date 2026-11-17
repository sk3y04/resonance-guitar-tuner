package dev.anima.resonance.ui.instrument

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Android: the global animator duration scale is the system's reduced-motion
 * signal (Settings → Accessibility → "Remove animations", or the developer
 * option). 0 means animations are off; anything else keeps the tuned motion.
 * Read once per composition — it is a cheap resolver lookup, not a listener.
 */
@Composable
actual fun systemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale == 0f
}
