# Resonance

Offline chromatic guitar tuner for Android. A vintage analog-meter instrument
with a calm, readable display. No login, no ads, no analytics, no network —
microphone audio is processed on-device and never stored or transmitted.

Built with Kotlin Multiplatform (shared pitch-detection engine + Compose UI)
and a thin Android shell.

## Status

First public release (`1.0.0`). Heavily tested on the JVM host;
the UI has been verified on an Android 15 phone and an API 36 emulator
(development log: `docs/STATE.md`). **No published releases yet** — build from
source as described below. The automated real-microphone device suite is
compile-checked on every CI run but has not yet been run green on hardware.

## Features

- Chromatic mode (any note in the ~55–1200 Hz design range) and instrument
  mode (preset string targets)
- Automatic target-string selection with manual string lock
- Analog needle meter + digital readout (note, cents, frequency) with honest
  no-signal states — never a fake reading
- Built-in tunings plus custom tunings: create/edit/delete, favorites, and
  versioned-JSON import/export through the Android document picker
- Reference pitch A4 adjustable 400–480 Hz in 0.1 Hz steps (steppers or direct
  entry)
- Sharp / flat / natural accidental-spelling preference
- On-device persistence (DataStore), reduced-motion support, predictive back

## Requirements

- Android 8.0+ (minSdk 26; targetSdk 36)
- To build: JDK 21. An Android SDK with at least one installed platform is
  needed for the app and the Android gates; without it, the JVM tests and demo
  still build and run (the Android target is auto-disabled — see
  `docs/DECISIONS.md`, ADR-003).
- Tested development host: Linux x64. The headless Compose UI tests use a
  linux-x64 skiko runtime; other hosts are untested.

## Quick start (no phone needed)

With JDK 21 installed:

```sh
./scripts/run-demo.sh
```

This runs the full pipeline — synthetic microphone → pitch detection → engine
→ UI state — as 5 assertive scenarios. Expected result: `5/5 scenarios passed`.

## Build and install the Android app

1. Install JDK 21 and an Android SDK with a platform (Android Studio, or
   `sdkmanager`). Point the build at it:

   ```sh
   export ANDROID_HOME=/path/to/android-sdk
   # or: echo "sdk.dir=/path/to/android-sdk" > local.properties
   ```

2. Build the debug APK:

   ```sh
   ./scripts/build-debug.sh
   ```

   Produces `artifacts/resonance-debug-<date>.apk` and
   `artifacts/LATEST_BUILD.txt` (sha256, source commit, size).

3. Install and run:

   ```sh
   adb install -r artifacts/resonance-debug-<date>.apk
   ```

   Launch Resonance, allow the microphone when prompted, and pluck a string.

## Usage

- Pluck a string: the needle shows cents deviation around the target; the
  readout shows note name, cents, and frequency, with an in-tune zone on the
  dial. With no reliable pitch the needle fades out and the readout says so.
- The MODE key switches Chromatic / Instrument. In instrument mode the target
  string is selected automatically; tap a string key to lock it manually.
- The gear key opens settings: preset selection and favorites, A4 calibration
  (±1 Hz / ±0.1 Hz steppers or direct numeric entry, reset to 440), accidental
  spelling, and a Manage area with the custom-tuning editor and import/export.
- Custom tunings, favorites, and settings persist across restarts. Export
  writes a versioned JSON file that can be re-imported on any device.

## Tests

| Command | What it covers | Requires |
|---|---|---|
| `./scripts/test-unit.sh` | 447 shared JVM tests: DSP correctness matrix, fuzz/property suites, state machine, persistence, UI mappers, headless Compose | JDK 21 (Linux x64 for render tests) |
| `./scripts/test-ui.sh` | Headless Compose semantics + render-smoke subset | JDK 21, Linux x64 |
| `./scripts/run-demo.sh` | Full-pipeline demo, 5 assertive scenarios | JDK 21 |
| `./scripts/verify.sh` | 7 gates: unit tests, demo, offline-contract, lint, debug assembly, Robolectric host tests (380), instrumented test APK compile | JDK 21; Android SDK for gates 3–7 |
| `./scripts/test-device.sh [serial]` | On-device suite: real-mic smoke, persistence, app launch. Opt-in; grants RECORD_AUDIO via `pm`; never wipes a device | Attached device/emulator |

Counts are from a local run on 2026-11-17 (Linux x64, Temurin 21).
`verify.sh` reports missing-prerequisite gates as `BLOCKED` — never as green —
and fails if any available gate fails. Test logs land in `artifacts/logs/`.

## Known limitations

- Monophonic tuning only; design range ~55–1200 Hz. Polyphony/chords,
  reference tones, and bass/extended-range guarantees are out of scope.
- Documented low-register stress limit under heavy noise (10 dB SNR); a mix in
  which another string is louder than the played note is refused rather than
  guessed. Measurements: `docs/QUALITY_REPORT.md`.
- One-octave sympathetic-bleed recovery only.
- Accuracy evidence is synthetic plus limited on-device UI verification; the
  automated real-mic suite has not yet been run green on hardware.
- No published releases or packages yet.

## Troubleshooting

- `[verify] BLOCKED: ...` — the Android SDK is missing or not visible. Set
  `ANDROID_HOME` (or `local.properties`) with at least one installed platform.
  JVM gates still run without it.
- The app shows "Open app settings" — microphone permission was permanently
  denied; grant it in Android's app settings.
- Headless UI test failures on macOS/Windows — the pinned skiko runtime is
  linux-x64; use Linux x64 (or WSL2).
- A test run that discovers zero tests is treated as a failure, not a pass.

## Building from source

The Gradle 9.7.0 wrapper is included (distribution sha256-pinned in
`gradle/wrapper/gradle-wrapper.properties`). Direct dependency versions are
pinned in `gradle/libs.versions.toml`.

```sh
./gradlew :shared:jvmTest            # shared host tests
./gradlew :hostDemo:run              # full-pipeline demo
./gradlew :androidApp:assembleDebug  # debug APK (needs Android SDK)
./scripts/verify.sh                  # the full gate suite
```

Toolchain rationale and verified dependency metadata: `docs/DECISIONS.md`,
`docs/DEPENDENCIES.md`.

## Development

Architecture, testing strategy, and the manual release process:
`docs/development.md`. Contribution workflow: `CONTRIBUTING.md`.

## Privacy

Microphone audio is processed on-device and never stored, transmitted, or
logged; no recordings are retained. The merged manifest is enforced by
`scripts/check-offline-contract.sh` to contain only `RECORD_AUDIO` (plus one
AGP-injected package-private receiver permission) — no INTERNET, no
network-adjacent permissions, no cleartext traffic.

## License

GPL-3.0-only — GNU General Public License version 3, **without** the
"or (at your option) any later version" option.

Copyright (c) 2026 Edgar Tadevosyan.

Full license text: [LICENSE](LICENSE). SPDX identifier: `GPL-3.0-only`.
