# Resonance

Offline-first, Android-first chromatic guitar tuner. Dark instrument aesthetic,
calm readable display. No login, no ads, no analytics, no network.

Kotlin Multiplatform (shared logic + Compose UI) with an Android shell app.

## Setup
Prereqs: JDK 21. For Android builds: Android SDK with a platform installed
(see `local.properties` or ANDROID_HOME). Without an SDK, only the JVM target
and host tests build (the Android target is auto-disabled — see
`docs/DECISIONS.md` ADR-003).

## Commands
- `./scripts/test-unit.sh` — fast shared tests (DSP/domain/state/persistence), no phone/display
- `./scripts/test-ui.sh` — Compose UI semantics + headless render smoke tests (no display needed)
- `./scripts/run-demo.sh` — runnable host demo: the FULL pipeline (synthetic mic → engine → UI state), 5 assertive scenarios, no device/display/SDK needed
- `./scripts/verify.sh` — host tests + host demo + available lint + debug assembly
- `./scripts/build-debug.sh` — debug APK to artifacts/ + checksum
- `./scripts/test-device.sh` — opt-in device/emulator integration tests

## Privacy
Microphone audio is processed on-device and never stored, transmitted, or
logged. No recordings are retained. No INTERNET permission in the manifest.

## Known limitations
First release targets monophonic guitar tuning (~55–1200 Hz design range).
Polyphonic/chord tuning, reference tones, bass/extended-range guarantees,
iOS, and publishing are deferred.
