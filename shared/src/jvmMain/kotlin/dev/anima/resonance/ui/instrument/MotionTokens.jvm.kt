package dev.anima.resonance.ui.instrument

import androidx.compose.runtime.Composable

/** JVM host: no system animation preference exists; full motion. */
@Composable
actual fun systemReducedMotion(): Boolean = false
