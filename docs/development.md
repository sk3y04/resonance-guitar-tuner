# Development guide

Architecture, testing strategy, and the manual release process for Resonance.
Onboarding and contribution workflow live in `README.md` and
`CONTRIBUTING.md`; this document is the durable technical reference.

## Modules

- `shared` — Kotlin Multiplatform module containing essentially the whole
  product: pitch detection, tuner state, presets/settings, and the Compose UI.
  Source sets: `commonMain`/`commonTest` (platform-independent),
  `jvmMain`/`jvmTest` (host actuals + real-file/real-thread/headless-UI tests),
  `androidMain`/`androidHostTest` (Android actuals + Robolectric bridge tests).
- `androidApp` — thin Android shell: `ResonanceApp` activity (permission and
  lifecycle policy), manifest, and the on-device `androidTest` suite.
- `hostDemo` — executable full-pipeline demo with a synthetic microphone and
  virtual clock; assertive (exits non-zero on any scenario failure).

The Android target is conditionally enabled: `settings.gradle.kts` and
`shared/build.gradle.kts` include it only when `ANDROID_HOME` (or
`ANDROID_SDK_ROOT`) points at an SDK with at least one installed platform
(ADR-003 in `docs/DECISIONS.md`). JVM development is never blocked by a
missing SDK. `gradle.properties` currently sets
`android.experimental.disableCompileSdkChecks=true` as a documented workaround
for the CMP 1.12.2 AAR metadata floor; its removal condition is recorded there.

## Data flow

```
AudioRecord (androidMain)  ──pump thread──▶ CaptureOrchestrator
    start/stop idempotence, bounded recovery, chunk delivery
        │
        ▼
StreamingAnalyzer (commonMain dsp/)
    conditioning (DC/level) → FrameAssembler ring → 4096-sample window
    → YIN detector (+ harmonic-energy refinement, spectral guards)
    → PitchEstimate {hz, confidence, PitchReason}
        │  fixed analysis hop (cadence independent of chunk size)
        ▼
PitchTracker (presentation/)
    temporal decisions: evidence floors, elapsed-time confirmation,
    one-octave fold with gap release, log-domain time-aware smoothing,
    LIVE / HELD / NONE validity + age
        │
        ▼
TunerStateMachine — thin adapter over PitchTracker (stable public API)
        │
        ▼
AppTuner (engine/) — the single deterministic engine: capture → detection
    → state → presets/settings; produces TunerSnapshot
        │
        ▼
TunerController (engine/) — Compose-free bridge: permission gating,
    error surfacing, UI-state mapping
        │
        ▼
MainScreenUi (presentation/) — pure, tested mapper → MainScreenUiState
        │
        ▼
Compose UI (ui/) — MainScreen, instrument meter/readout, settings overlay
```

The UI-facing semantic contract (what each field drives, per-state behavior,
where smoothing happens) is specified in `docs/PITCH_PIPELINE_CONTRACT.md`.

### Threading contract

On Android two threads touch the engine: the audio pump and the main thread.
All shared-state access is serialized by a single engine lock (`engineLocked`
expect/actual: JVM `synchronized`, Android equivalent). Persistence calls stay
outside the critical section so the pump is never blocked on disk I/O.
`AppTunerConcurrencyTest` (jvmTest) hammers this with real threads.

## Algorithms and assumptions

- Pitch detection: an independent Kotlin implementation of YIN
  (de Cheveigné & Kawahara, 2002, "YIN, a fundamental frequency estimator for
  speech and music"), extended with: two-candidate selection (first-crossing
  vs global in-band minimum) validated by spectral presence + fundamental
  plausibility; sub-threshold dip recovery for aperiodic sympathetic mixes;
  one-octave-up recovery with strict tonal guards; harmonic-energy refinement
  of the period estimate; a gated Hann-windowed spectral answer when no
  usable periodicity exists. No third-party DSP code is used (ADR-002).
- Test signals include Karplus-Strong plucked-string synthesis (a standard
  published algorithm) with physical excitation, plus sine/noise matrices.
- Monophonic assumption: a mix dominated by another string is refused
  (reading goes invalid) rather than guessed. One-octave fold only.
- Design range ~55–1200 Hz; 4096-sample analysis window at 48 kHz.
  Measured limits (10 dB low-register stress, residual low-E error) are
  documented in `docs/QUALITY_REPORT.md`.

## Configuration and persistence

- Settings (concert pitch 400–480 Hz, accidental preference, selection, lock)
  and custom presets persist via androidx DataStore preferences.
  `DataStoreSettingsRepository` lives in `commonMain`; platform factories in
  `jvmMain` (file-backed) and `androidMain` (context-backed, per-process
  singleton — DataStore 1.3.0 enforces one active store per file).
- Custom presets are stored as versioned JSON with corruption recovery and
  no-clobber writes. Import/export uses the same codec
  (`TuningPresetJson`: size/count/length/version limits, deterministic
  duplicate-id renaming, built-ins never imported or overwritten) through the
  Android SAF document picker; the shell is read-only by contract (no storage
  permission).
- The app is offline by contract: `scripts/check-offline-contract.sh` parses
  the merged manifest and fails on INTERNET, network-adjacent permissions,
  anything outside the allowlist, cleartext traffic, or a wired
  network-security-config.

## Testing strategy

Deterministic host tests (no device, no display, no network):

- `commonTest`: DSP correctness matrix (45 notes × 3 signal flavors),
  noise-robustness populations, fuzz/property suites (codec payloads,
  20k-frame state machine, CRUD storms), state-machine contracts, persistence
  round trips, pure UI mappers, headless Compose semantics.
- `jvmTest`: real-file DataStore persistence, real-thread concurrency,
  headless Compose render smoke + palette contrast (skiko linux-x64).
- `androidHostTest`: Robolectric — the Android bridge (Context DataStore,
  ViewModel import/export staging, pump lifecycle) behavior-tested on the JVM.
- `hostDemo`: five assertive end-to-end scenarios.

Device-dependent (opt-in, never in CI): `androidApp/src/androidTest` —
real-microphone smoke (CaptureOrchestrator reaches CAPTURING, delivers PCM,
ambient RMS > 0), real-framework persistence, app launch. The suite is
compile-checked by `verify.sh` gate 7 so it cannot rot; run it with
`./scripts/test-device.sh [serial]` on hardware you control.

Gate semantics: `BLOCKED` (prerequisite missing) ≠ `FAIL` (ran and failed) ≠
`NOT RUN` (optional hardware). Zero discovered tests is a failure. Current
counts and the verification matrix: `docs/VERIFICATION.md`.

## Extension points

- `AudioInput` interface: swap the capture backend without touching the
  pipeline (the Android AudioRecord adapter is one implementation).
- `BuiltInTunings` / `TuningPresetJson`: add presets or evolve the codec
  (versioned; keep untrusted-input limits).
- `PitchTrace`: bounded, off-by-default diagnostics ring (no audio stored)
  for pipeline debugging — see `docs/PITCH_PIPELINE_CONTRACT.md` §7.
- Instrument UI primitives (`ui/instrument/`): palette, geometry, motion
  tokens are pure and host-tested; new controls compose from them.

## Known limitations

See `docs/QUALITY_REPORT.md` ("Honest limitations") and the README. Summary:
monophonic only; documented low-register noise limit; dominated mixes refused;
one-octave fold; accuracy evidence is synthetic plus limited device UI
verification; the automated real-mic suite has not yet run green on hardware.

## Release process (manual)

No release automation exists yet; releases are manual and maintainer-driven.

Versioning:
- `versionName` in `androidApp/build.gradle.kts` follows semantic versioning
  (currently `1.0.0`); `versionCode` is a monotonically
  increasing integer, bumped for every distributed APK.
- A release corresponds to an exact source commit, tagged `v<versionName>`
  (no tags exist yet). The tag is the authoritative source reference for
  binaries and for the corresponding-source obligation below.

Build and artifacts:
- Debug: `./scripts/build-debug.sh` → `artifacts/resonance-debug-<date>.apk`
  plus `artifacts/LATEST_BUILD.txt` recording sha256, source commit, date,
  size. Checksums prove integrity, **not** publisher identity.
- A `release` build type exists (minification off) but no signing config is
  set up; signed release builds are future work. Do not distribute unsigned
  release builds as if they were official.

GPL obligations when distributing binaries (GPL-3.0-only, see `LICENSE`):
- Convey the license text and appropriate legal notices with the binary.
- Provide the Corresponding Source for the exact tagged commit (GPLv3 §6):
  either accompany the binary with the source, or a written offer valid for
  at least three years to provide it. Publishing the tagged source repository
  alongside the download is the simplest compliant path.
- Dependency notices: the app links Apache-2.0 libraries (Kotlin, Compose
  Multiplatform, androidx, kotlinx, skiko) plus MIT (Robolectric, test-only)
  and EPL-1.0 (JUnit, test-only — not shipped in the APK). Apache-2.0
  components that carry NOTICE files require those notices to be retained in
  distributions; check the resolved artifacts per release. This is a
  metadata-level reminder, not a legal determination.
- No third-party code or assets are vendored in this repository; the Gradle
  wrapper jar is Gradle Inc.'s Apache-2.0-licensed wrapper (standard practice).

Manual publication checklist:
1. `./scripts/verify.sh` green on Linux x64 with the SDK (no BLOCKED gates).
2. Opt-in device run (`./scripts/test-device.sh`) on real hardware; record
   the outcome honestly in `docs/VERIFICATION.md`.
3. Bump `versionName`/`versionCode`; update docs if behavior changed.
4. Build the artifact; record sha256 + commit (`build-debug.sh` does this).
5. Tag the exact commit `v<versionName>` (maintainer action).
6. Publish the binary together with the license text, notices, and the
   corresponding source (or a compliant written offer).
7. Write release notes from the commit log: what changed, measured evidence,
   known limitations. No fabricated benchmarks.

Possible future improvements (not promises): signing config, reproducible
build verification, checksums signed by a maintainer key, CI-attached
attestations, F-Droid metadata.