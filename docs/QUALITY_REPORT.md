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
