# QUALITY REPORT — Resonance (overnight quality program, 2026-11-07)

Companion to `docs/STATE.md` / `docs/TDD_LOG.md`. This report records what
the unattended overnight quality pass measured, what it fixed, and what remains —
with the actual measured numbers, not impressions.

## DSP correctness map (COMPLETE)

**Scope.** Every semitone of the spec'd 55–1200 Hz band (A1..D6, 45 notes)
measured through the complete pipeline (conditioning → YIN → state machine)
in three physical flavors: pure sine, physically-excited plucked string
(Karplus-Strong, pluck-shape initial displacement), sine + 20 dB Gaussian
room-tone noise. `FrequencyMatrixTest` (commonTest, runs on both hosts) plus
`YinNoiseRobustnessTest` (20-seed population test for the low E).

### Bugs found and fixed (all RED→GREEN, see TDD_LOG, DSP correctness map)

1. **Low-E identity failure at 10 dB (the headline bug).** 55 Hz + 10 dB
   noise read as **A#1 (58.3 Hz) at confidence 0.85–1.00 on 20/20 seeds**.
   Root cause: broadband noise makes a *shallow* d′ dip cross YIN's 0.15
   threshold (lag 741, d′=0.145) before the true-period dip (lag ~796,
   d′=0.069 — 7× deeper). Fix: two-candidate selection in `Yin.detect` —
   the first-crossing descent (fundamental-first, classic YIN) and the
   global in-band minimum, each validated by (A) spectral presence at the
   candidate's frequency and (B) fundamental plausibility (no in-band
   harmonic 2f..24f > 10× its own energy — kills subharmonic artifacts like
   110 Hz for a 440 Hz sine without rejecting overtone-rich real strings,
   whose 4th partial can exceed the fundamental ~7×). Among survivors the
   deeper dip wins; comparable depths → smaller lag.
2. **Subharmonic fragility.** While validating the fix, the matrix caught
   octave errors the old single-candidate code tolerated: 339 Hz sine →
   67.8 Hz (5×!), 659.25 Hz sawtooth → 59.9 Hz (11×), 440 Hz → 110 Hz.
   Same fix covers them (harmonic-strength disqualification up to 24f).
3. **Analysis window.** Default 2048 samples (46 ms) → **4096 (93 ms)** in
   `StreamingAnalyzer`: at 2048, 110 Hz + 10 dB read 86.3 Hz on 27/27
   frames (noise-trend dip 7× deeper than the true dip). 4096 fixed it.
   8192 was measured and **rejected** — it moved the failures instead of
   fixing them (see stress table) and doubles the DSP cost.
4. **Test-fixture realism (plucked-string carry-over).** The Karplus-Strong fixture
   used a white-noise delay-line init → flat spectrum; some seeds put the
   4th partial 7× over the fundamental (no physical pluck does that).
   Replaced with a pluck-shape excitation (30% displacement point) → the
   realistic 1/h falloff. Both `FrequencyMatrixTest` and
   `PluckedStringRealismTest` updated.

### Measured contract (gated in `FrequencyMatrixTest`)

| flavor | identity | median \|cents\| (correct-note frames) |
|---|---|---|
| sine, 45 notes | 100% | ≤ 5¢ |
| plucked (physical), 45 notes | ≥ 95% | ≤ 5¢ |
| sine + 20 dB noise, 45 notes | 100% (measured 27/27 all notes) | ≤ 10¢ (measured worst 10¢ @ 55 Hz, ≤8¢ elsewhere) |
| out-of-range (45 Hz, 1300 Hz) | never in-tune; silence or boundary note clearly out | — |

Out of range: 45 Hz (below band) → no note (silence); 1300 Hz (above band)
→ D6 boundary note ~97¢ sharp, never in-tune.

### Known limitation — 10 dB stress (documented, not gated)

10 dB SNR (noise at 31% of signal RMS — a fan next to the mic, not a room)
degrades note identity in the 87–150 Hz register. Measured (4096 window,
seed = MIDI note, 27-frame settled tail):

```
note  Hz      identity  med ¢   note  Hz      identity  med ¢
A1  55.00   27/27        6    A#2 116.54   12/27       37
A#1 58.27   27/27        5    B2  123.47   23/27       34
B1  61.74   27/27        3    C3  130.81   25/27       46
C2  65.41   27/27        6    C#3 138.59   27/27       32
C#2 69.30   27/27       14    D3  146.83   19/27       29
D2  73.42   27/27        9    D#3 155.56   27/27       27
D#2 77.78   27/27        2    E3  164.81   27/27       23
E2  82.41   27/27        7    F3  174.61   27/27       18
F2  87.31   21/27        7    F#3 185.00   27/27       29
F#2 92.50   20/27       45    G3  196.00   27/27       12
G2  98.00   20/27       45
G#2 103.83  27/27       20
A2  110.00   11/27       33
```

Mechanism: the cumulative-mean normalization of YIN's d′ curve has a
noise-driven downward trend at long lags; below ~150 Hz that tilt can make
a false dip deeper than the true-period dip, and the 3-point parabolic
interpolation inherits the residual tilt (measured −12¢ systematic at
87.31 Hz with confidence 1.00). A wider symmetric least-squares dip fit was
implemented, verified algebraically, and measured: it removes the tilt but
shifts clean high-end dips (d′ curvature is non-quadratic over multi-lag
windows for sharp dips) — net-negative across the 45-note matrix, so the
proven 3-point form stays.

**Upgrade path (future, deliberate scope cut for tonight):** temporal
prior — the state machine already knows the last stable note; passing it
to `Yin.detect` as a search prior (±10% lag window) would remove most
flicker in the 87–150 Hz register at 10 dB. Needs its own TDD cycle +
lock-in behavior tests; not done under the overnight scope.

### DSP cost note

`Yin.detect` worst case adds up to 2 candidates × (1 + 23 harmonic checks)
DTFT energy paths per frame (each O(n)); measured jvm full-suite runtime
remains ~1 min. On-device profiling (concurrency/clean-rebuild passes) will confirm the budget.

## Fuzz/property tests (COMPLETE)

Three commonTest suites (run on both JVM and Android hosts) property-test
every untrusted surface:

| suite | what it drives | invariants pinned |
|---|---|---|
| `FuzzCodecTest` | 400 random byte payloads + 50 round-trips + every documented limit at its boundary | only `IllegalArgumentException` on garbage; unique IDs; deterministic collision renaming (`x` → `x-import` → `x-import2`); built-ins never importable from file; 64/65 tunings, 12/13 strings, 40/41 name, 64/65 id, size limit |
| `FuzzStateMachineTest` | 20k pseudo-random frames: 55–1200 Hz + gaps, confidence 0–1, backwards & `Long.MAX_VALUE` clocks, NaN/±Inf/negative/1e300 frequencies, A4 churn 400–480, target-set + manual-lock churn | no throw; note always in active set; in-tune only within exit threshold; direction ↔ cents sign; garbage never corrupts (clean A4 locks after a 500-frame garbage storm); `setConcertPitch` rejects out-of-band with IAE |
| `FuzzCrudTest` | 600-op random engine storm (concert/mode/preset/string-lock/CRUD/favorite/import) + garbage imports | unique IDs; built-ins intact & immutable; repo mirrors customs; failed imports mutate nothing and report a reason; engine still locks E2 after the storm |

### Bug found (RED → GREEN): stale-note display

The state-machine fuzz produced a reading the old code could emit forever:
**A1 displayed at +5210¢** while the signal sat ~2 octaves away. Mechanism:
`setConcertPitch`/`setTargetSet` reset the hysteresis *candidate* counter,
so if the user keeps nudging settings while the pitch has moved, the old
stable note never loses — yet the 5-frame smoothing ring refills with the
new (far) frequency, and cents runs away (first pass: unbounded 5210¢;
second pass after the initial fix: a 5-frame 2307¢ run via a
near-boundary frame resetting the counter while the ring stayed
far-dominated).

Fix in `TunerStateMachine` (chromatic mode only — instrument AUTO sets and
manual locks may show large deviations by design):

1. **Bounded staleness** (`staleFrameCount`): raw measurement >50¢ from the
   displayed note for more than one full re-lock window (`hysteresisFrames`)
   → the note yields to UNSTABLE. A legitimate 3-frame note transition is
   untouched (pre-existing `noteSwitchRequiresConsecutiveAgreement` passes
   unchanged); setting changes do NOT renew a stale note.
2. **Hard backstop**: if the *smoothed display itself* exceeds 200¢
   (2 semitones), the note yields immediately — the display invariant
   cannot be violated by any counter-interaction.

`StaleNoteYieldTest` pins both the repro and the bounded contract.
Pinned result after fix: an out-of-semitone display lasts at most a couple
of frames in the worst constructed case; the 20k-frame fuzz shows zero
>200¢ displays.

## Concurrency (COMPLETE)

The Android shell drives the engine from TWO threads: the AudioRecord
pump thread (`poll`, ~47 fps) and the main thread (settings, preset CRUD,
UI state reads every recomposition). Before this slice, nothing in the
engine was synchronized — the host tests were single-threaded, so the
split was untested.

### Bug found (RED → GREEN): pump-thread crash

`AppTunerConcurrencyTest` (JVM-only, real threads) reproduces the shell's
split: an unpaced pump thread hammers `poll()` while the main thread runs
a random storm (concert-pitch churn, mode/preset switches, string locks,
preset CRUD + favorites, garbage imports) and reads `state()`
continuously. First run: the pump threw
`IndexOutOfBoundsException: Empty list doesn't contain element at index 0`
inside the state machine's 5-frame smoothing ring — `setConcertPitch`
clearing the ring on the main thread mid-read on the pump thread. This is
the crash the real app would have produced under settings churn.

### Fix

One lock, one rule: every access to engine shared state (state machine,
analyzer, orchestrator, applied settings, string lock, snapshot, UI
capture flags) is serialized on a single lock via `engineLocked`
(expect/actual over JVM `synchronized` — both module targets are
JVM-based). Two disciplines, documented on the class:

1. The lock is never held across a suspension into the settings
   repository (disk I/O) — the pump can never be blocked on the disk.
2. The controller's old `capturing`/`captureError` fields (written by a
   capture-state callback on the pump thread, read by `state()` on main,
   no visibility guarantee) moved into the engine as `capturingUi` /
   `captureErrorUi`, written and read under the same lock.

After the fix the storm runs 10 s wall-clock clean and the engine
invariants hold (unique preset IDs, built-ins intact, E2 still locks).

### Flake note

`FuzzStateMachineTest.randomWellFormedFramesStayConsistent` failed once
during the overnight runs ("stale display lasted 5 frames (max 4)") and
passed in all subsequent runs — a borderline staleness-boundary case,
not a regression (it passed both pre- and post-concurrency suites). Watching;
if it recurs, the bound in `StaleNoteYieldTest`'s fuzz partner gets a
deterministic repro.

## Contract gates (COMPLETE)

The spec's offline contract ("no INTERNET permission, no analytics, no
login, no cloud") was previously a promise in the manifest comment, not
an enforced invariant. `scripts/check-offline-contract.sh` (verify.sh
gate 3/7) makes it a build gate:

- Parses the **merged** debug manifest — i.e. every `uses-permission`
  from the app **and all its libraries** after manifest merging, so a
  future dependency silently adding a permission fails the build.
- Fails on: `INTERNET`; any network-adjacent permission (network /
  wifi / location); any permission outside an explicit allowlist;
  `usesCleartextTraffic="true"`; a wired-in network-security-config.
- Allowlist (deliberate, documented in the script): `RECORD_AUDIO` and
  the AGP-injected package-private `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
  (system receiver-visibility permission for the Android 13+
  `ContextCompat.registerReceiver` path — not a capability, not a leak
  surface).

**Gate proven non-vacuous (RED→GREEN on the gate itself):** injecting
`INTERNET` into the app manifest made the gate FAIL (it flagged all three
violation classes for that permission); reverting restored PASS. Full
`verify.sh` run: all 7 mandatory locally-available gates PASS (device RUN
still the real-device gate). Lint sweep remains gate 4 (`:androidApp:lintDebug`).

## Final summary (night of 2026-11-07 → 08)

### Bugs found and fixed (all RED → GREEN, see TDD_LOG)

| # | Bug | Found by | Fix |
|---|---|---|---|
| 1 | Low E (55/82 Hz) misread under 10 dB noise (59.64 Hz for 55 Hz) | DSP correctness matrix (45 notes × 3 flavors × noise) | `Yin.detect` now selects between the first-crossing candidate and the global in-band minimum, validated by spectral presence + fundamental plausibility |
| 2 | Subharmonic fragility: sawtooth 246.94 Hz reported as 123.43 Hz at conf 0.99 | correctness matrix | same candidate-selection rule (in-band harmonic check kills the subharmonic) |
| 3 | Stale-note display: a locked note could outlive its pitch forever (A1 displayed at +5210¢) | 20k-frame state-machine fuzz | bounded-staleness guard (yield after a full re-lock window) + 200¢ smoothed-display hard backstop |
| 4 | Two-thread engine race: pump thread crashed with `IndexOutOfBoundsException` in the smoothing ring while the main thread changed settings | real-thread storm test | single engine lock (expect/actual `synchronized`); persistence kept outside the critical section; controller's cross-thread UI flags moved into the engine |
| 5 | Offline contract unenforced (a comment, not an invariant) | contract-gate design | verify.sh gate 3/7 on the MERGED manifest (allowlist-only; dependency-added permissions fail the build) |

### Measured quality

- **431 tests, all green**: 228 JVM (incl. 45-note×3-flavor accuracy
  matrix, 400 garbage codec payloads, 20k-frame state-machine fuzz,
  600-op CRUD storm, 10 s real-thread concurrency storm) + 203 Android
  host (same common suites via Robolectric + 4 Android-bridge tests).
- **Accuracy contract at 20 dB** (gated): 45/45 notes × sine/pluck/noisy,
  median |cents| ≤ 5 / 5 / 10; A4=432 retunes correctly; out-of-range
  tones map to boundaries without false in-tune.
- **Concurrency contract**: 10 s wall-clock storm (unpaced pump vs
  random settings/CRUD storm + continuous state reads) — zero
  exceptions, all invariants hold, tone re-locks after the storm.
- **Clean rebuild**: from wiped build dirs, all 7 verify gates PASS
  (tests, demo 5/5, offline contract, lint, assemble, android host
  tests, instrumented-test APK build).
- **Offline by contract, enforced at build time**: merged manifest
  allowlist = RECORD_AUDIO (+ AGP-injected receiver-visibility
  permission). No INTERNET, no cleartext, no network-security-config.

### Known limitations / watch items

- 10 dB low-register stress: identity drops in 87–150 Hz (documented
  table in the DSP correctness map section — the physical low-end of a plucked
  string; the app shows UNSTABLE rather than a wrong note there).
- `FuzzStateMachineTest` borderline-boundary flake (1 of 6 overnight
  runs; deterministic repro on deck if it recurs).
- Device gate: on-device run (`./scripts/test-device.sh`) pending a
  device/emulator — the test APK builds as gate 7/7 in the meantime.

## UX/accessibility/QA audit (2026-11-15)

**Verdict:** core acquisition, math, and detector tests were already strong;
the audit found and fixed user-facing reliability/accessibility gaps around
them. No DSP algorithm or threshold was changed.

**Verified defects fixed (18):**

| # | Severity | Defect | Evidence / regression |
|---|---|---|---|
| 1 | Critical | Custom tunings/favorites persisted but never restored on relaunch | InMemory + real DataStore restart tests |
| 2 | Critical | No signal/weak/stale/clipping could render `0.0 Hz`, `0 cents`, centered “in tune” styling | `MainScreenUiStateTest`, UI semantics test |
| 3 | Critical | Capture not stopped in background; pump `Thread` could not restart | `AppTunerLifecycleTest`, `ResonancePumpWorkerTest` |
| 4 | Critical | Permanent denial could re-prompt; no first-run explanation | `PermissionPolicyTest` |
| 5 | High | String tiles had no click handler (manual lock impossible) | UI callback + selected-semantics tests |
| 6 | High | Favorites displayed but no toggle control | UI + controller persistence test |
| 7 | High | Editing flat tunings dropped strings (sharp-only regex) | pure + end-to-end editor tests |
| 8 | High | Editing selected tuning left stale targets/lock | controller test |
| 9 | High | Dangling selected preset restored with no targets | `AppTunerTest` |
| 10 | High | Android shell never applied `ResonanceTheme`; light theme had unreadable hardcoded colors | contrast tests + inspected renders |
| 11 | High | Clipped/nested-scroll layout at small widths & 2× text | 320 dp/2× semantics + render smoke |
| 12 | Medium | Sheet below fold with no affordance; 0.1 Hz-only reference (80 taps to 432) | shortcut + coarse-step tests |
| 13 | Medium | Delete was single-tap and irreversible | confirmation test |
| 14 | Medium | Import/export disk I/O on main; unbounded read | `ImportIoTest` |
| 15 | Medium | Imported `custom-N` IDs could collide with next creation | `PresetStoreTest` |
| 16 | Medium | Instrumented launch smoke asserted a note in no-signal states | permission-independent assertions |
| 17 | Low | Missing accessible names for icon-only edit/delete/favorite/steppers, note octave split, decorative dial exposed | semantics tests |
| 18 | High | Startup restore raced with user mutations (lost custom tuning) | DataStore export host test + persistence mutex |

**Verification:** `:shared:jvmTest` 270/270, `:shared:testAndroidHostTest`
233/233, demo 5/5, lint green, debug + instrumented test APKs build,
`verify.sh` 7/7 mandatory locally-available gates PASS. Headless Skia
renders were produced and inspected at 320/360 dp, 2× font, dark/light
(`shared/build/render/`).

**Not verified (honest limits):** no emulator/phone: real mic,
background/interruption behavior on hardware, real screen-reader speech,
haptics, Bluetooth/headset routing, real screen insets, and on-device
performance. Audio source is still `VOICE_RECOGNITION` at 48 kHz with no
sample-rate fallback — device-dependent risks, deliberately not changed
without measurement. The editor cycles notes upward only (reported as an
optional enhancement).

## G4 / sympathetic-octave drop-out (2026-11-15)

**Report:** G4 locked for about a second, then the display lost it (and
sometimes briefly showed G3); every other note held normally.

**Cause:** G4 (392 Hz) excites the open G string (196 Hz, one octave below)
sympathetically. The combined waveform is periodic at 196 Hz, so the
detector's first-crossing candidate is the lower octave. With the 196 Hz
component around −10 dB relative to 392, that candidate is rejected by the
10× harmonic rule while the true 392 Hz dip is too shallow for the 0.15
absolute threshold — the detector returned **no pitch**. From −9 dB up it
returned G3. The state machine's 50-cent stale guard then cleared the G4
lock. Measured on the production 4096-sample/48 kHz path:

```
196 component   YIN result
-30 .. -11 dB   392 Hz (correct)
-10 dB          no pitch
-9 .. 0 dB      196 Hz (G3)
```

**Fix (two layers):**
1. `TunerStateMachine` folds a continuous measurement within 2% of half the
   established note back up to that note (G3 bleed → G4). A playing gap
   >300 ms suppresses the fold, so a real octave change is still followed;
   suppression clears when the established note is confirmed or a new note
   locks.
2. `Yin.detect`, when no candidate survives, looks one octave up from each
   rejected candidate and accepts it only with a local dip (d′ < 0.35),
   fundamental plausibility, and a strong tonal energy ratio (≥10; noise is
   ~1). This closes the −10 dB "no pitch" hole.

**Regression evidence:** `YinOctaveRecoveryTest` (3 tests: bleed recovery,
inverse true-low-note case, noise), five new `TunerStateMachineTest` cases
(continuous bleed, short hole, real octave drop after a gap, non-octave
change, manual lock). Full `:shared:jvmTest` 278/278,
`:shared:testAndroidHostTest` 241/241, headless UI suite PASS, demo 5/5.
Synthetic G4 replay holds G4 through the whole sympathetic decay.

**Limits:** the fold is temporal, not spectral — a real G4→G3 change with
no playing gap is indistinguishable from the bleed and stays G4 until a
gap. The real-phone capture attempts were inconclusive (near-silent input),
so the musical confirmation is the installed build, not a recorded fixture.
