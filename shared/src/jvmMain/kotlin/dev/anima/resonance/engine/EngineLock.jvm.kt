package dev.anima.resonance.engine

import kotlin.synchronized

internal actual fun <T> engineLocked(lock: Any, block: () -> T): T =
    synchronized(lock, block)
