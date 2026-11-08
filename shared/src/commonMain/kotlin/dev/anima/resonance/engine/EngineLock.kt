package dev.anima.resonance.engine

/**
 * Engine-level mutual exclusion. On Android the engine is driven
 * from TWO threads — the audio pump thread (poll, ~47 fps) and the main
 * thread (settings, CRUD, UI state reads) — so every access to the
 * engine's shared state must be serialized. Both targets of this module
 * (jvm, android) are JVM-based, where `synchronized` is the primitive;
 * this expect/actual keeps the lock available from commonMain.
 *
 * Discipline: never hold the lock across a suspension into the settings
 * repository (disk I/O) — persistence calls run outside the critical
 * section so the pump is never blocked on the disk.
 */
internal expect fun <T> engineLocked(lock: Any, block: () -> T): T
