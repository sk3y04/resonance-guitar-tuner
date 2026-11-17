# Contributing to Resonance

Thanks for considering a contribution. This is a small maintainer-led
project; the process is intentionally lightweight.

## Prerequisites

- JDK 21 (Temurin 21 is the tested toolchain; bytecode targets JVM 17).
- Git.
- Optional: an Android SDK with at least one installed platform, for the
  Android app and the Android gates. Without it the build auto-disables the
  Android target and all JVM work still runs (`docs/DECISIONS.md`, ADR-003).
  Point the build at the SDK with `ANDROID_HOME` or `local.properties`
  (`sdk.dir=...`). `local.properties` is git-ignored.
- Linux x64 is the tested host: the headless Compose UI tests depend on a
  linux-x64 skiko runtime. Other hosts can run the non-UI JVM tests but are
  untested for the render suite.

## Setup and commands

```sh
git clone <your-fork-or-the-repository-url>
cd resonance-tuner
./gradlew projects          # shows which modules are active (SDK gate)
```

All commands are inspected shell wrappers in `scripts/`:

| Command | Purpose |
|---|---|
| `./scripts/test-unit.sh` | Shared JVM tests (DSP, domain, state, persistence, UI mappers, headless Compose). Fails on zero discovered tests. |
| `./scripts/test-ui.sh` | Headless Compose semantics + render-smoke subset. |
| `./scripts/run-demo.sh` | Full-pipeline host demo (synthetic mic), 5 assertive scenarios. No device/display/SDK. |
| `./scripts/verify.sh` | The 7-gate suite: unit tests, demo, offline-contract, lint, debug assembly, Robolectric host tests, instrumented APK compile. |
| `./scripts/build-debug.sh` | Debug APK to `artifacts/` + sha256/commit metadata. |
| `./scripts/test-device.sh [serial]` | Opt-in on-device suite (real mic, persistence, launch). Grants RECORD_AUDIO via `pm`; never wipes or resets a device. |
| `./scripts/check-offline-contract.sh` | Merged-manifest permission allowlist gate (no INTERNET, no cleartext). |

`verify.sh` distinguishes `BLOCKED` (prerequisite missing, e.g. no SDK) from
`FAIL` (a gate ran and failed) — blocked gates are never reported as green.
There is currently **no code formatter or style linter configured**
(`kotlin.code.style=official` is an IDE hint only); match the surrounding
style and do not reformat unrelated code. Type checking is Kotlin compilation
itself, exercised by the build/test gates. Android lint runs as gate 4.

## Component map

```
shared/            Kotlin Multiplatform module (the product logic + UI)
  src/commonMain/    dsp/ (YIN + tracker), engine/ (AppTuner, controller),
                     music/ (notes, presets, JSON codec), persistence/,
                     presentation/ (pure UI-state mappers), ui/ (Compose)
  src/commonTest/    host tests for everything above
  src/jvmMain/       JVM actuals (file-backed DataStore, engine lock)
  src/jvmTest/       real-file persistence, real-thread concurrency,
                     headless Compose semantics/render tests
  src/androidMain/   Android actuals (AudioRecord, Context DataStore,
                     ViewModel/SAF bridge, engine lock)
  src/androidHostTest/  Robolectric tests of the Android bridge on the JVM
androidApp/        Android shell: ResonanceApp activity, manifest,
                   src/androidTest/ on-device suite (compile-checked in CI)
hostDemo/        Runnable full-pipeline demo (synthetic mic, virtual clock)
scripts/         The gate wrappers above
docs/            Decisions (ADRs), dependency evidence, pipeline contract,
                 quality report, development log, architecture/development
gradle/          Version catalog + sha256-pinned wrapper
```

Data flow and the two-thread engine contract: `docs/development.md` and
`docs/PITCH_PIPELINE_CONTRACT.md`.

## Reporting bugs

Use the bug-report template. Include: the commit or version, OS/device (or
host JDK for JVM issues), reproduction steps, expected vs actual behavior,
and relevant logs from `artifacts/logs/`. **Redact credentials and personal
information** from everything you attach.

## Pull requests

1. For substantial changes (new features, DSP/algorithm changes, dependency
   or architecture changes), open an issue first and agree on the approach.
   Small fixes can go straight to a PR.
2. Keep diffs focused; no unrelated reformatting or drive-by refactors.
3. Tests: this project practices test-driven development — write the failing
   test first, then the minimal implementation (the development log in
   `docs/TDD_LOG.md` records this history). Never weaken assertions, skip
   tests, or special-case fixtures to get green. New behavior needs tests;
   bug fixes need a regression test.
4. Update docs when behavior, commands, or contracts change.
5. Run `./scripts/verify.sh` before submitting and note the result in the PR
   (list any `BLOCKED` gates and why, e.g. no Android SDK on your machine).
6. Fill in the PR template: purpose, related issue, validation performed,
   documentation impact.

## Commit messages

Match existing practice: a short imperative subject, optionally prefixed with
a type already used in the history (`feat(music):`, `docs:`, `chore:`), e.g.

```
Fix G3 dropout under sympathetic D3 (sub-threshold YIN recovery)
docs: state + TDD log (signal honesty, charcoal faceplate)
```

Use the body to explain *why* (root cause, measurements, tradeoffs). Keep
messages self-contained — no references to private planning artifacts.

## Licensing of contributions

The project is licensed GPL-3.0-only (see `LICENSE`). Contributions are
accepted under that same license; by submitting a contribution you confirm
you have the right to license it under those terms. There is no CLA or DCO
sign-off requirement. Do not copy code from GPL-incompatible sources or
competitor assets; algorithms may be studied and independently implemented
(see `docs/DECISIONS.md`, ADR-002).

## Device tests

`./scripts/test-device.sh` is opt-in and requires real hardware (or an
emulator). It is deliberately not part of CI. Never run it against a device
you do not control; it installs the app and grants the microphone permission.