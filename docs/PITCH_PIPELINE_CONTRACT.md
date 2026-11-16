# Pitch pipeline → presentation contract

Audience: the UI layer (screen, meter, readout). This is the authoritative
semantic stream. Nothing here is visual design: colors, layout, needle drawing
and animation belong to the UI.

## 1. What the pipeline promises

One analyzed frame per **21.3 ms** (hop = 1024 samples at 48 kHz; the analyzer
owns the hop, so the rate does not depend on the platform's chunk size).
Per frame the engine produces a `TunerSnapshot`:

```
TunerSnapshot(
  reading: TunerReading,     // the presentation contract (below)
  rms: Double,               // level of the analysis window
  clipping: Boolean,
  sampleRate: Int,
  rawHz: Double,             // detector's own answer this frame — DIAGNOSTICS ONLY
  rawConfidence: Double,
  rawReason: PitchReason,    // FIRST_CROSSING | GLOBAL_DIP | OCTAVE_RECOVERY |
                             // SUBTHRESHOLD_RECOVERY | HARMONIC_SPECTRAL | NONE
)
```

`rawHz` must never drive the display: the tracker folds and rejects raw
estimates on purpose. Use it only for on-device trace/QA output.

## 2. `TunerReading` — which value drives what

| field | meaning | who consumes it |
| --- | --- | --- |
| `validity` | `LIVE` / `HELD` / `NONE` | **status presentation, needle enable** |
| `note` | stable note identity (null when nothing is current) | note glyph, string highlight |
| `targetHz` | reference the cents are measured against (0 with no note) | tooltip/diagnostics |
| `centsFine: Double` | continuous signed deviation, unclamped | **the needle** (proportional, no steps) |
| `cents: Int` | the same value rounded | the numeric readout |
| `frequencyHz` | display-smoothed frequency, 0 unless `LIVE` | the numeric Hz readout |
| `inTune` / `direction` | hysteresis state / direction | in-tune styling |
| `confidence` | strength of this frame's evidence (0..1) | optional quality hint |
| `ageMs` | ms since the last accepted evidence (0 when `LIVE`) | hold fade timing |
| `status` | coarse state machine (`MEASURED`, `UNSTABLE`, `STALE`, …) | secondary copy |

Rules that hold on every frame (enforced by `PitchTrackerTest` and
`PitchPipelineAcceptanceTest`):

- `LIVE` ⇒ `note != null`, `frequencyHz > 0`, `ageMs == 0`, and
  `cents == round(centsFine)` agree with `frequencyHz`/`targetHz`.
- `HELD`/`NONE` ⇒ `frequencyHz == 0`, `cents == 0`, `inTune == false`.
  There is never a "live-looking" reading without a valid pitch.
- `HELD` carries a `note` only in one case — a pending identity change, where the
  label is kept stable while the numeric reading is withheld; `ageMs` is then the
  age of the evidence behind that label, never 0.
- `ageMs` grows only while evidence is missing; a held reading never refreshes
  its own validity.

## 3. What the UI should do per state

| validity | readout | needle | notes |
| --- | --- | --- | --- |
| `LIVE` | note + Hz + `cents` + direction | active, driven by `centsFine` | one coherent reading |
| `HELD` | fade the last `LIVE` reading (see `SignalHold`, `readingAlpha`) | **inactive** — never move on held data | `ageMs` says how old it is; the window is the state machine's stale window (600 ms), after which the state becomes `NONE` |
| `NONE` | placeholders / guidance copy | hidden or at rest | genuinely nothing to show |
| capture error / mic off | the existing error surface | hidden | unchanged from the signal-honesty pass |

`SignalPhase` (`SignalPresentation`) remains the single visual signal
state; map `HELD` onto its `HOLDING` phase and `NONE` onto `LOST`/`WAITING` as
today. Do not add a second validity model.

## 4. Where smoothing happens (and what NOT to add)

- Detector: no temporal smoothing (per-frame estimate only).
- Tracker (`PitchTracker`): a 3-frame median (single-frame outlier rejection)
  then a time-aware one-pole in log-pitch space, with a snap for real moves.
  Measured: 1.5-cent input jitter → ≤0.45 cent displayed; an 18-cent step lands
  within ±1 cent in ≤2 frames; a 25-cent ramp lags ≤2 cents.
- Therefore: **do not add another averaging filter** to the needle value. If the
  needle needs velocity continuity, use a *damped spring / easing* on the
  already-smoothed `centsFine` — do **not** restart a fixed-duration tween per
  update (that is what makes a needle stutter at irregular frame arrivals), and
  do not stack a second low-pass.
- Do not interpolate across an invalid gap: on `HELD`/`NONE` the needle must
  stop (or rest), not glide to the next value.

## 5. Reference pitch, presets, mode changes

- Cents are always computed against `targetHz`, which already accounts for the
  configured A4 (400..480 Hz, default 440) and the active tuning/manual lock.
  The UI never does its own cents math.
- Changing mode/preset/selected string/reference does **not** reset a pending
  identity change: a reference change must not renew a note the measurement has
  left. The engine re-applies the target set immediately after a reference
  change (`AppTuner.applyTarget`), so the next frame is already correct.
- Manual string locks change `targetHz` only; the measurement is untouched.

## 6. Minimal UI-side changes still required (not made by me)

1. `MainScreenUi.toUiState` now feeds `meterPosition(centsFine)` — done in the
   shared mapper (one additive overload + two call sites). If the UI keeps
   its own mapping, feed it `centsFine`, not `cents`.
2. If the needle animates with `animateFloatAsState(...)`, keep it but make the
   spring stiff (settle ≤120 ms) and never pass the raw `rawHz`/`cents` (Int).
3. On `HELD`, disable pointer movement (`meterActive` is already false in
   `MainScreenUiState`) and let `SignalHold` fade the readout.

## 7. Diagnostics

`PitchTrace` is off by default and bounded (ring of `capacity` frames, no audio
stored). To debug on a device:

```kotlin
val trace = PitchTrace(capacity = 512).apply { enabled = true }
val analyzer = StreamingAnalyzer(sampleRate, trace = trace)
// ... later
trace.snapshot().forEach { f ->
    println("${f.nowMs}ms rms=${f.rms} raw=${f.rawHz}/${f.rawConfidence} ${f.rawReason} " +
        "→ ${f.validity} ${f.noteLabel} ${f.centsFine}c (target ${f.targetHz}) age=${f.ageMs} ${f.status}")
}
```

Offline reproduction: `./gradlew :shared:jvmTest --tests "*PitchLabProbe*"` writes
`shared/build/pitch-lab/report.md` (metrics) and the acceptance tests replay
deterministic synthetic plucks through the exact production path.