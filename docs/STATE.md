# STATE — Resonance

- SMOOTHNESS — frame-cost pass + idle needle wander (2026-11-17). UI only:
  palette, materials, layout, copy, DSP, tracking and thresholds untouched.
  (1) ROOT CAUSE, measured on the phone (ASUS AI2302, 1080x2400, 120 Hz):
  every animated frame re-recorded the ENTIRE window. Compose invalidates the
  nearest render layer; the animated nodes had none, so the invalidation reached
  the window root. `atrace`: `Record View#draw()` 4.04 ms/frame while the needle
  moved and 3.18 ms while a 7dp dot pulsed; 62% of needle frames missed the
  8.35 ms budget, and a fixed tap/settings script showed 6.7 ms median UI time
  with 42% of frames over budget.
  (2) FIX — LAYERS, not timings (MotionTokens durations unchanged): the dial
  paints in TWO render layers (static glass/frame/scale/numerals recorded once
  per size; live zone arc + needle + escutcheon), and every other animated paint
  gets its own layer through `animationLayer()` — instrument key caps, jewel
  lamps, the readout, its pulsing dot, the settings scrim and the tuning-row
  wash. Dial geometry, brushes, paths and text layouts are built once per
  size/palette instead of per record.
  Result (same script, two runs each): needle frame draw 4.04 → 0.58 ms (7x);
  interaction UI median 6.7 → 1.6 ms; frames over budget 42% → 10%; legacy jank
  55% → 20%. Idle with the sway running: 1.7 ms UI/frame, 107 fps, 9% over
  budget.
  (3) IDLE NEEDLE: with no signal the needle is no longer hidden — it wanders
  slowly around the flat-side rest (two harmonics of one 5.6 s loop, ±14°,
  seamless at the loop point, always left of −15°), written one frame at a time
  into the needle layer so it costs no recomposition, and it hands off to the
  same critically-damped spring when a reading starts (no teleport). Reduced
  motion parks it at rest. This deliberately reverses the earlier hidden-needle
  contract: motion plus the flat-side position plus the readout's own no-signal
  copy keep it from reading as a measurement — a FROZEN needle is the thing that
  would.
  Tests: AnalogMeterGeometryTest (amplitude bound, continuity, seamless loop,
  more turnarounds than a sine, never 0¢), MeterRenderTest (needle present
  without signal; needle angle ±27° at ±0.6; ivory face; brass escutcheon;
  byte-identical repeat renders), SignalPresentationTest (needle always shown).
  Gate counts and the device installation check: see docs/TDD_LOG.md (SMOOTHNESS).

- POLISH — production interaction pass + matte faceplate (2026-11-17).
  Approved design preserved (palette, meter/needle, composition, settings IA,
  straight seam); no DSP/tracking/threshold change. UI + Android shell only.
  (1) SHARED MOTION SYSTEM: `MotionTokens` (press 100 / selection 170 / content
  220 / sheet 300 ms; FastOutSlowIn controls, EaseOutCubic sheet; every spec snaps
  under reduced motion). `LocalReducedMotion` ← expect/actual
  `systemReducedMotion()` (Android animator-duration-scale; JVM false), provided
  by ResonanceTheme. All ad-hoc tweens replaced; needle spring (1.0/280) kept as
  measurement smoothing, snaps under reduced motion.
  (2) TOUCH: one warm cream `LocalIndication` ripple theme-wide (was petrol
  fallback flashing teal); parchment PrimaryButton scopes dark-ink ripple;
  shared `pressLayerColor`/`selectionFill`/`focusHairline`/`selectedCapEdge` in
  `Interactions.kt`. String targets had NO press feedback (unused interaction
  source) — fixed. Deck rows run full-bleed press layers with content on the one
  gutter line (deck no longer pads horizontally; alignment regression test).
  (3) TRANSITIONS: mode body + settings destination fade-in on change
  (`FadeInOnChange`: outgoing removed immediately, first composition full
  opacity); sheet slide + scrim share ONE animation value (300 ms in / 220 ms
  out via host AnimatedVisibility, interruptible); selection/favorite/lamp/
  readout colors on tokens. Detected-string marker stays immediate.
  (4) FACEPLATE: `faceplateFinish` = gentle top-lit gradient (deckTop 36281C →
  deck 2B1F18 @45% → deckBottom 221812) + deterministic static grain
  (`FaceplateGrain`, fixed seed, alpha ≤ 12/255, warm-only, uploaded once) + 7dp
  seam contact shadow. Pixel profile 45.7→53.2→46.3→35.0, identical in both
  modes, byte-identical across renders (`FaceplateRenderTest`). Mode selector in
  a shallow milled track (MODE_ROW_DP 54, keys keep 48dp); selected caps get the
  shared edge light; sheet shares the gradient family, no grain.
  (5) SYSTEM/BACK: common BackHandler/PredictiveBackHandler (ui-backhandler +
  navigationevent-compose deps) — Back cancels the editor before closing the
  sheet; predictive progress drives sheet offset/scrim; platformBack kept as
  no-dispatcher fallback; manifest enableOnBackInvokedCallback. Edge-to-edge bars
  forced dark-style (light icons) — app materials are theme-constant.
  (6) KEYBOARD: exact entry selects the whole prefill (TextFieldValue — typing
  appended "442.5440.2" before), autofocus, IME Done applies, keyboard hides on
  apply/cancel, warm OutlinedTextField colors; sheet insets applied BEFORE
  height measurement (IME can't push it off-screen); editor name IME Done
  clears focus.
  (7) DEVICE-FOUND FIXES: sheet empty-area touches fell through to the scrim and
  dismissed it — sheet surface now consumes unhandled gestures (no semantics
  merge). Verified: taps/swipes on empty area keep the sheet; children unaffected.
  (8) PERF: MainScreen handlers remembered against controller; ControlBlock/
  StatusStrip take pitch-free slices; overlay gets a derivedStateOf-normalized
  state slice — 20 Hz ticks no longer invalidate deck/overlay.
  (9) A11Y/HAPTICS: direction liveRegion removed (flaps near boundary); cents
  slot width-stable (right-anchored tnum); one system-gated LongPress haptic on
  deliberate string lock / preset select / favorite (vibrator log confirmed);
  no auto-note-change or in-tune haptics. Contrast floors extended to gradient
  ends + pressed parchment (all ≥ 4.5:1 text / 3:1 graphics).
  Gates: `:shared:jvmTest` 447/447, `:shared:testAndroidHostTest` 380/380
  (re-run AFTER the final two fixes), `./scripts/verify.sh` 7/7 PASS.
  Device: API-36 emulator, APK sha256-verified; frame analysis at ~59fps proved
  press dip, retargeting selection tweens under 10 rapid taps, mode fade,
  sheet slide 525→113px/290ms + coordinated scrim, exit fade; press-drag-cancel,
  Back chain (IME→editor→sheet), delete dialog Cancel/Back/Confirm, keyboard
  apply, font_scale 2.0 scroll fallback, reduced-motion snap — all verified.
  Vision review rate-limited (pixel analysis substituted). Predictive-back
  GESTURE not driven (plain Back verified). Real-device verification (phone/mic/screen reader)
  still open. Details: docs/TDD_LOG.md (production interaction polish).

- POLISH — approved design kept, three defects fixed (2026-11-17). UI only.
  (1) SEAM: the control deck's top corner radii are gone (`ControlDeck` = square
  top edge, no clip), and its brass lip line was removed so the readout's own
  bottom edge is the only divider — one continuous horizontal seam, nothing doubled.
  (2) SETTINGS HEADER root cause: the shared `PrimaryButton`/`TonalButton` wrappers
  put a `fillMaxWidth()` Box INSIDE the Surface, so in a Row the button expanded to
  the remaining width and the weighted title was squeezed to ~0 (one letter per
  line). Both wrappers are now `wrapContentSize()` + padding (callers add
  `fillMaxWidth()` when they actually want a full-width action), the header title is
  `weight(1f)` + `maxLines = 1` + ellipsis, and Done stays content-sized (min 76dp).
  (3) BUTTON CONTRAST root cause: button labels inherited `InstrumentType.label`,
  which hard-codes `textPrimary` (cream) — invisible on the parchment primary fill.
  Labels now state their ink explicitly (`accentInk` on parchment, `textPrimary` on
  tonal, caller colour on text actions), so "New tuning" is dark-on-parchment and
  Import/Export stay cream-on-brown at equal height.
  Unchanged: palette, meter, readout, mode selector, tuning row, switch, string row,
  reference row, density, destination model, and all tracking/DSP code.
  Gates: `:shared:jvmTest` 431/431, `:shared:testAndroidHostTest` 368/368,
  `./scripts/verify.sh` 7/7 PASS (device tests NOT RUN). Regression test
  `theSettingsHeaderKeepsItsTitleOnOneLineAndDoneContentSized` covers the header.

- CORRECTION — back to the vintage palette, wood wallpaper removed, elements
  fill the window (2026-11-17). UI-only; no DSP/capture/estimation/tracking change.
  (1) PALETTE REVERTED to warm vintage: espresso `background 1B1310`, `deck 2B1F18`,
  raised `segmentCap 3B2A20`, hairline `deckEdge 5B4433`, recess `170F0C`, ivory
  `F1E7CF`, cream type `F4EAD6`, beige `C6B49B`, parchment SELECTED key `E8D9BC`
  (ink `2A2016`), brass `AD925B` for small indicators only (deck seam, switch track,
  detected marker, meter hardware). No petrol/mint/blue anywhere. Contrast floors
  re-verified (`InstrumentPaletteContrastTest` 6/6).
  (2) WOODEN BACKDROP DELETED: `WoodenInstrumentPanel.kt` (WoodenEnclosure),
  `WoodGrain.kt` and `WoodGrainTest.kt` are gone. Nothing draws grain or a wallpaper.
  (3) FULL-BLEED COMPOSITION: the app background is the enclosure colour; the
  acquisition status line sits on it; the ivory meter + readout band spans the full
  width (`cornerRadiusDp = 0`); the espresso control deck runs edge to edge with a
  rounded top shelf, a brass-dark hairline seam, and continues to the bottom of the
  display. Only `WindowInsets.safeDrawing` is applied, as content padding. No outer
  inset, no frame, no floating card.
  (4) Meter sizing: the band is full-bleed on phones, and on wide/short windows it is
  width-limited from the height the readout needs ((avail - 112dp) x 1.6), so an
  over-wide arc can never starve the readout. Same adaptive gauge aspect rule as the control deck.
  (5) Unchanged: reading order (mode → tuning → auto-select → one row of six 48dp
  string targets → reference pitch), one overlay with three mutually exclusive
  destinations, selection semantics, compact/scroll fallbacks, meter/needle/cents
  mapping.
  Gates: `:shared:jvmTest` 430/430, `:shared:testAndroidHostTest` 368/368
  (7 fewer tests: `WoodGrainTest` deleted with its subject). Device shots NOT
  captured — the phone was locked/dozing during this pass.

- COMPLETE — contemporary precision instrument: petrol control surfaces,
  ivory meter preserved, settings rebuilt as real destinations (2026-11-17).
  UI-only; no DSP/capture/estimator/tracker/smoothing change (pitch tracking untouched).
  (1) DIRECTION FLIP: the brown/wood control system is gone. `InstrumentPalette`
  now defines walnut (enclosure only), deep desaturated petrol surfaces
  (`deck 172E32`, `deckDeep 10242A`, `segmentCap 234147`, `deckEdge 2E4A50`),
  warm off-white type (`F2EFE5` / `B8C8C7`), ONE mint selection accent (`A6D9C8`,
  ink `10242A`) and brass only on meter hardware (`AD925B`). `InstrumentTokens`
  owns the whole scale (4/8/12/16/24/32, one gutter 16, corner family 8/12/20/28,
  48dp touch floor, `contentMaxWidth 420dp`); `InstrumentType` is the full type
  scale. Nothing is styled ad hoc any more.
  (2) COMPOSITION: one walnut case (8dp) around ONE petrol instrument body
  (`InstrumentBody`, 18dp gutter, 420dp cap, one hairline edge). On that single
  surface, aligned to one gutter: acquisition status → ivory meter + readout →
  control block. No card stacks, no nested padding, no charcoal slab.
  (3) CONTROL BLOCK (rows, not cards): mode (two even keys, mint fill on the
  chosen one, 48dp) → active tuning (small label + preset name + chevron, the
  WHOLE row opens the Tuning destination) → "Auto-select string" switch →
  six equal 48dp string targets in ONE row (mint fill = chosen/locked, mint label
  + foot marker = merely detected) → quiet "Reference pitch  A4 = 440 Hz ›" row
  that opens the Calibration destination. The gear is gone; there is exactly one
  entry per destination.
  (4) SETTINGS IA: `SettingsOverlay.kt` replaces the scrolling anchor page with
  three MUTUALLY EXCLUSIVE destinations (Tuning / Calibration / Manage) in a
  stable near-full-height sheet (92%), one viewport, one scroll position per
  destination, no section bleed (a tuning row can no longer sit above Calibration).
  Tuning rows = name + note sequence + single check + separate favourite key;
  Calibration = reference pitch with −/+ steppers (tap the value for exact entry),
  labelled "Adjustment step" and Naturals/Sharps/Flats; Manage = New tuning,
  Import/Export, custom rows with edit/delete, empty state.
  (5) METER PRESERVED: same dial geometry, ticks, needle, pivot, cents mapping and
  hierarchy. The only change is adaptive assembly sizing: the arc keeps its deep
  aspect when the slot can also hold the full readout, and flattens to 1.85 when
  it cannot, so the readout's hint line is never crushed. Meter/readout and the
  mode row have identical bounds in both modes (asserted).
  (6) Responsive/a11y: full block → compact block (drops ONLY the reference row)
  → scrollable fallback (`resonance.scroll`) instead of clipping; large text
  falls back immediately; every interactive row keeps 48dp; selection never
  relies on colour alone (`selected`/`toggleable` semantics, fill + weight).
  (7) Deleted obsolete implementations: `ControlDeck.kt`,
  `InstrumentStringSelector.kt`, `CalibrationControls.kt`, `TuningsOverlay.kt`
  (superseded), plus the chromatic/isCompact/balanced helpers of the old deck
  maths. New: `Controls.kt`, `ControlSurface.kt`, `StringTargets.kt`,
  `CalibrationPanel.kt`, `SettingsOverlay.kt`.
  Gates: `:shared:jvmTest` 437/437, `:shared:testAndroidHostTest` 375/375,
  `./scripts/verify.sh` 7/7 PASS (device/emulator tests NOT RUN).

- COMPLETE — lower control deck + settings information architecture
  (2026-11-16). UI-only; no DSP/capture/tracking change (pitch tracking untouched).
  (1) Deck recomposed with an unmistakable mode control: two keys in ONE recessed
  track, the active key a raised lighter-walnut cap with a brass indicator bar and
  a bold label (`SegmentControl.kt`; `selected` semantics, keyboard focus ring,
  >=48dp). The old faint underline is gone.
  (2) Reading order fixed with real hierarchy: mode → tuning name (+ gear) →
  explicit "Automatic string selection" On/Off (state word now names the locked
  string: "Off · D3") → one shallow string rail (locked = ivory inset, detected =
  ivory wash + brass underline) → quiet calibration line that looks interactive
  ("Calibration  A4 440 Hz  ›").
  (3) Chromatic mode no longer reserves a guitar panel: ONE settings entry
  ("Calibration & note names ›") plus a read-only configuration plate (reference
  pitch, note names) and one explanatory line. Still the same deck height, so the
  meter/readout do not move when switching modes.
  (4) Settings IA rebuilt: ONE panel with three task destinations (Tuning ·
  Calibration · Manage) in a header destination strip; deep links from the deck
  land on the right destination (one-shot snap scroll via `withFrameNanos`, no
  animation). Creation/import/export moved into "Custom tunings" (no permanent
  footer competing with calibration).
  (5) Tuning picker rows: name (16sp) → string sequence with subscript octaves →
  selected treatment (filled surface + brass edge + "Selected" badge) with a
  separate 48dp favourite key; restrained dividers instead of nested gold boxes.
  (6) Calibration: "A4 = 440.0 Hz" as one value with explicit − / + (52dp), a
  labelled "Adjust by" precision selector (1 Hz / 0.1 Hz) with helper text, exact
  entry on tapping the value, reset only when != 440. Note names is now a
  three-way explicit choice (Naturals / Sharps ♯ / Flats ♭) matching the state
  model — previously NATURAL left both keys unselected.
  (7) Readout re-hierarchised: note (dominant) with octave and a "target" caption
  when a manual lock is active, signed cents in a tabular column, the action badge,
  then frequency as quiet secondary text. Out-of-range shows "off scale"; a
  retained reading shows "held"; FLAT/SHARP dial markers now use the secondary-ink
  contrast role (they were 30% ink and effectively invisible).
  (8) State contradiction fixed at the source: `SignalPresentation.micStatusText`
  reported "Listening" for HOLDING — a retained E4/439.1 Hz/+497¢/"Tune down" could
  therefore appear under a "Listening" header. HOLDING now reads "Reading held",
  and `ReadoutCopy` derives every caption from the same `SignalPhase` (tested).
  (9) Composition: top margin reduced to 14dp (the old 44dp balanced band above the
  meter), residual at the bottom of the enclosure.
  Gates: see the verification block below (jvmTest/androidHostTest/verify.sh).

- Prior: milestone COMPLETE — warm wooden control deck + section-focused configuration
  (2026-11-16). UI-only; no DSP/capture/tracking change (pitch tracking untouched).
  (1) Charcoal faceplate GONE: `InstrumentFaceplate` deleted. The meter/readout are
  mounted straight into the walnut enclosure and the lower controls sit on ONE
  shared wooden deck (`ControlDeck`: same walnut, sanded a shade lighter, low-contrast
  deterministic horizontal figure, drawn once per size). Dark warm brown survives only
  in real recesses (string rail, readout, milled slots); brass only as accent (tab
  underline, switch knob, chevron, detected-string marker); ivory = dial + the single
  locked string position (dark ink on it).
  (2) Deck recomposed in reading order: mode tabs (brass underline + `selected`
  semantics, no tray, no lamp dots) → tuning name + gear (one 48dp row; the NAME is the
  hit target, long/custom names ellipsize) → explicit "Automatic string selection"
  On/Off switch → ONE shallow rail of six 48x48dp positions (letter main, octave
  secondary; locked = ivory inset, detected = ivory wash + brass underline) → quiet
  "A4 <value> Hz" line. The old mode tray, the framed string cards and the full-width
  bottom config card are removed, as is the duplicated note summary.
  (3) Mode stability by construction: `ControlDeckLayout` sizes the deck from its
  taller instrument composition and holds that height in BOTH modes (the preset's
  string count is reserved in chromatic), so the meter/readout do not move or resize
  (`switchingModesDoesNotMoveOrResizeTheMeterAndReadout`). Chromatic composes its own
  content (settings summary, reference, spelling) inside that height instead of a
  blank guitar panel.
  (4) One overlay, three entry points: tuning name → preset list; gear → settings;
  calibration line → reference pitch. `SheetSection`/`SheetFocus` + a one-shot snap
  scroll (`initialSection`; null = open at top, the old behaviour).
  (5) Automatic-vs-manual maps onto the EXISTING engine semantics — no new state
  model, no controller API change: `StringSelectionUi.intent` turns On/Off into the
  engine's lock/unlock call and the switch state is derived from `stringLocked`.
  (6) Short screens (<700dp inner height) drop the quiet calibration row instead of
  crushing the meter (`isCompact`); surplus height becomes a balanced, capped bezel
  (measured 45dp top/bottom at 360x800). Touch floor asserted for every string target,
  gear, auto row and mode tab.
  Gates: jvmTest 429/429 (was 390), testAndroidHostTest 368/368, run-demo 5/5,
  verify.sh 7/7 PASS.

- Prior: milestone COMPLETE — stable pitch tracking + smooth presentation (2026-11-16).
  DSP/tracking only (the UI is untouched).
  Root causes (measured with a new offline harness, not assumed):
  (a) aperiodic sympathetic mixes biased the pitch estimate — a 4:3 G3/D3 mix is
  periodic only at 49 Hz, below the 55 Hz band edge, so the dip sat 10-50 cents
  off and drifted 20+ cents with the beat phase; E4+B3 at -12 dB shared a
  partial coincidence and lost the note entirely (93/205 frames silent);
  (b) inharmonic (stiff) strings read +7..+11 cents;
  (c) a rectangular-window DTFT peak is displaced when the window holds a
  non-integer number of periods (-5.2 cents at 82.4 Hz in 4096 samples);
  (d) display smoothing was a cleared-on-dropout 5-frame median (≈105 ms lag,
  still 6.3 cents std on low E) emitted as an integer, which quantized the
  needle to 2%-of-scale steps.
  Fixes: `Yin` harmonic-energy refinement (±60 cents, magnitude-only) + boundary
  climb + a gated spectral band scan instead of "no pitch" + Goertzel guards +
  Hann-windowed spectral evaluation + `PitchReason`; new `PitchTracker`
  (evidence floors 0.5/0.35, 40 ms time-based confirmation, one-octave fold
  released after a gap, log-domain time-aware smoothing, `validity`/`ageMs`/
  `targetHz`); `TunerStateMachine` is now a pure adapter over it (same API);
  `StreamingAnalyzer` owns the analysis hop (fixed cadence regardless of chunk
  size) and exposes the raw estimate; `PitchTrace` bounded opt-in diagnostics;
  `MainScreenUi` meters `centsFine` (additive overload).
  Measured (205 frames/scenario, window to the 10 dB SNR horizon): G3+D3 -14 dB
  lost frames 15 → 0 (median error 11 → 0.0 cents, p95 26 → 0.2); E4+B3 -12 dB
  93 → 0; low-E jitter @20 dB pink 6.3 → 1.9 cents (p95 17 → 4.3); inharmonic
  bias +7..+11 → 1.0 cents; ramp lag 1.83 → 1.91 cents (still ≤3); re-acquire
  72 → 51 ms; live reading ends 16 ms after silence (400 ms HELD fade before
  NONE); detector cost 3.68 → 3.17 ms/frame with a fixed hop.
  Gates: jvmTest 390/390, androidHostTest 342/342, demo 5/5, verify.sh 7/7 PASS.
  Details: docs/TDD_LOG.md (pitch tracking stability), docs/QUALITY_REPORT.md (pitch-tracking stability section), and the UI-facing
  contract in docs/PITCH_PIPELINE_CONTRACT.md.
- Prior: milestone COMPLETE — coherent physical-instrument refinement + honest signal
  presentation (2026-11-16). UI-only; no DSP/detection changes.
  (1) Visual hierarchy: flat warm-charcoal faceplate (`InstrumentFaceplate`,
  `InstrumentPalette.faceplate`), wood only at the outer perimeter; restrained
  recessed glass bezel (brass reserved for the needle pivot + a few accents);
  shared `InstrumentTokens` spacing/shape set.
  (2) Meter rebalanced (`AnalogTuningMeter`, `MeterAssemblyLayout` GAUGE_ASPECT
  1.6): the width-limited scale fills the ivory face (no tall dead band above the
  arc; ~36dp clearance on a tall phone, ~8dp on a small phone vs ~82dp before);
  decorative "CENTS • 440Hz CALIBRATED" stamp removed; numerals 14dp.
  (3) Signal honesty (core): explicit `SignalPhase`
  (waiting/tracking/holding/lost/mic-off/mic-error) via pure `SignalPresentation`;
  the needle is live ONLY with a reliable pitch and fades out otherwise
  (`AnalogMeterGeometry.needleShown(active)=active`) — never a fake reading at a
  scale stop; header status agrees with the readout (lost reads "No signal", not
  "Listening" beside "Signal lost"); a bounded 400ms `SignalHold` shows the last
  reading visibly faded after a brief interruption, then invalidates it.
  (4) Stable lower area: meter/readout geometry IDENTICAL across modes (the
  string rail is reserved in chromatic as modest negative space via
  `railStringCount`); a compact informative `ConfigurationEntry` replaces the
  oversized settings button (preset + notes + A4, or A4 + spelling), whole-area
  tap target via semantics onClick (child preset/concert tags stay unmerged).
  (5) Readout simplified (`NoteReadout`): one type hierarchy, tabular numerals,
  redundant direction reticle dropped (badge + needle carry direction); held
  readings render faded, never announced live.
  (6) Settings overlay (`TuningsOverlay`, `CalibrationControls`): mode-aware order
  (presets first in Instrument; reference + spelling first in Chromatic with the
  preset list labelled "Applies to Instrument mode"); selected checkmark distinct
  from the favorite star; calibration rebuilt as A4 [−][value][+] with an explicit
  1/0.1 Hz step selector, direct validated numeric entry (`parseConcertPitch`),
  and Reset to 440; Import/Export in a secondary "More" area; Close → Done.
  Gates: UI suite jvmTest 361/361; verified 344/344 against the committed DSP.
  Needle pixel-verified hidden in no-signal (score 5) vs live when tracking
  (41–44); gauge geometry identical across modes. Overlay vision-verified.
  NOTE: the working tree also carries a separate, in-progress pitch-tracking DSP changeset
  (PitchTracker + spectral Yin, uncommitted) with failing/flaky octave-detection
  tests; it was left untouched and is NOT part of the UI refinement. Details: docs/TDD_LOG.md (physical-instrument refinement + signal honesty).
- Prior: milestone COMPLETE — flagship vintage-analog overhaul (2026-11-15). Full
  visual/structural pass to one convincing hardware instrument; DSP/mic/
  persistence, G3 fix, calibration, favorites, import/export, alternate tunings,
  settings-overlay structure and the semantic/test contract all preserved.
  (1) Header (`TunerScreen.LabelStrip`): preset selector now interactive with a
  brass `▾` chevron (click lives on the preset Text so its tag/heading stay in the
  merged semantics tree), live status lamp + Tracking/Listening, A4 reference —
  one baseline, 20dp effective horizontal padding, `tnum` reference.
  (2) Unified recessed housing (`MeterAssembly`): dial + readout in ONE 14dp
  beveled module (1.5dp brass rim highlight + 1dp dark inner inset), rounded top
  dial / crisp divider seam / rounded bottom display.
  (3) Dial (`AnalogTuningMeter`, `AnalogMeterGeometry`): wide landscape window
  (aspect 1.5, deepening to 1.2 instrument / 1.0 chromatic on tall slots), pivot
  BELOW the face (+18dp) hidden behind a brass escutcheon, exact 90° sweep
  (±45°), numerals OUTSIDE the arc, 3-tier ticks (major 14dp/2dp + ▼ index over 0,
  medium 9dp/1.25dp, fine 2¢ vernier 5dp/0.8dp in −10..+10), 18dp parallax hairline
  @18%, radial ivory→parchment glass + 8dp top inner shadow, animated −3..+3¢
  target zone (sage→emerald #3D8B5E), FLAT/SHARP corners + CENTS calibration stamp.
  (4) Needle: strict sign sync (negative→LEFT), NEVER hidden — damped-spring
  `animateFloatAsState` rest at −52¢ off-scale when no signal; tapered 3.5→1dp
  polygon + 14% black drop shadow (+2.5,+2dp). Geometry/no-signal tests updated.
  (5) Illuminated readout (`NoteReadout`): fixed 112dp (zero layout jump), 1dp
  inset + warm radial backlight (#291D15→#160F0B) + 12% emerald ambient when in
  tune; balanced 3 columns — TARGET/DETECTED micro-label + 54sp note (accidental
  superscript, brass octave, emerald #58C48A glow when in tune) | direction
  reticle ▸▸/▸●◂/◂◂ + 13sp tnum frequency | 28sp tnum cents + ✓/▲/▼ status badge.
  No-signal keeps the same grid at 35% alpha: ghosted `--`, centered "Pluck a
  string" + pulsing dot, `-- cents`.
  (6) Tactile controls: recessed rocker mode switch; string keys with the number
  strictly centered above the note in BOTH states and the active jewel in the
  top-right corner (no text shift) + 1.5dp brass active border; settings
  push-button with 1dp bronze top bevel + gear icon + press feedback.
  (7) Layout root-cause fixes: `widthIn(max=480)` moved BEFORE `fillMaxSize` (the
  old order fixed the width at the full viewport, ballooning the gauge and
  crushing the readout on wide test viewports); the vertical space freed by the
  wide dial is redistributed as balanced breathing room (dynamic capped gaps) +
  coherent dial deepening, leaving ONE modest wood footer in both modes.
  Gates: jvmTest 333/333, androidHostTest 290/290, demo 5/5, verify.sh 7/7.
  Details: docs/TDD_LOG.md (flagship vintage overhaul).
- Prior: milestone COMPLETE — composition correction: meter dominant, readout and
  string controls compact (2026-11-15). Reverses the finishing-pass proportion regression.
  Regression cause (from source): the finishing pass gave the string grid `weight(1f)` with
  weight-distributed rows (oversized, separated buttons), expanded the chromatic
  readout to a 260dp empty rectangle, and the meter used a conservative radius
  that wasted ~37% of the face width. Fixes:
  (1) Meter rebuilt as the primary instrument — sweep 90°→70° (±35°) and a
  sweep-aware radius with numerals INSIDE the arc rim so the arc uses the full
  face width (`AnalogMeterGeometry`, `AnalogTuningMeter`); gauge aspect 1.0
  (~square face, pivot raised to clear the needle tail) → gauge ~310dp on a
  360dp phone (was ~195dp), long needle, readable numerals, balanced pivot.
  Cents mapping stays linear/center-zero (geometry tests updated to ±35°).
  (2) Readout compact (100dp) and content-driven in BOTH modes — no expansion
  when strings are hidden; stable listening↔tracking; honest no-signal (hidden
  needle, one "Play a note" message, intentional placeholder).
  (3) String selector redesigned as a compact strip (`InstrumentStringSelector`):
  one row of six equal keys when ≥48dp touch targets fit, else a tight grid
  (fixed 6dp gap, never stretched); note name leads, small number, jewel
  indicator (green AUTO / amber LOCKED) + one group-level caption — no per-button
  AUTO text line. Adaptive to any string count; semantics preserved.
  (4) One predictable Column (`spacedBy(10dp)`): status → meter+readout → mode →
  strings → settings → ONE modest bottom wood enclosure margin. No
  SpaceBetween/SpaceEvenly, no vertical weight on readout/strings; the meter is
  the only section sized to absorb spare height up to its coherent width-bound
  maximum. Settings overlay, DSP/mic/persistence, G3 fix, calibration, favorites,
  import/export, alternate tunings and the semantic contract all preserved.
  Details: docs/TDD_LOG.md (composition correction).
- Prior: milestone COMPLETE — restrained finishing pass: proportions, legibility,
  materials, status consistency (2026-11-15). No redesign; overlay structure
  untouched; DSP/mic/persistence untouched.
  (1) Readout rebalanced to content size (`MeterAssemblyLayout.MAX_READOUT_DP`
  200→104, note glyph capped 78sp) — no tall empty panel; geometry stable
  listening↔tracking. Spare height now grows the STRING KEYS (instrument,
  `InstrumentStringSelector` fillHeight, keys centred in their row up to a
  104dp cap) leaving ONE modest fixed 8dp bottom margin; chromatic (no grid)
  uses a bounded spacious readout (cap 260dp) plus ONE wood enclosure margin.
  Both assemblies bounded by available height so no viewport overflows.
  (2) Legibility at phone size: meter numerals 13→14dp SemiBold, longer major
  ticks (15dp) distinct from minor (9dp); string note names 17sp lead over
  muted 9sp numbers; frequency 15sp, direction status 16sp, AUTO/LOCKED 10sp;
  unselected mode label brighter (ivory 0.9α) with the engaged key still
  unmistakable. (3) Status strip meaningful: `micStatusText` now "Tracking"
  for a live reading vs "Listening" while waiting (mic activity ≠ pitch
  tracking); "In tune" lives only in the readout, not repeated in the strip
  (`P15StatusWordingTest` 4). (4) Walnut: quieter uniform base + stronger
  cached grain (52 streaks) so the body reads as wood, not a brown gradient;
  grain never over ivory/text, no per-tick redraw. Settings overlay, alternate
  tunings, string counts, calibration, favorites, import/export, persistence
  and the semantic contract all preserved. Details: docs/TDD_LOG.md (finishing pass).
- Prior: milestone COMPLETE — focused layout + no-signal correction (2026-11-15).
  Root cause of the recurring empty bands found in the source: the controls
  sat in a `Column(weight(1f))` with TWO `Spacer(weight(1f))` that distributed
  all leftover height as gaps between groups (TunerScreen.kt). Replaced with
  one top-aligned column, small consistent `spacedBy(10dp)` group gaps, the
  meter assembly taking surplus up to its arc-filling limit, and ONE bottom
  enclosure margin — no distributed internal gaps. No-signal corrected: the
  needle is now HIDDEN (faded, hub stays) instead of parked at the flat stop
  (`AnalogMeterGeometry.needleShown` + test); the "NO SIGNAL" face text is
  removed; the readout shows ONE short primary message ("Play a note" /
  "Signal lost — play again" / "Signal too weak — move closer" / "Signal
  clipping") + dash placeholders, never a fake reading. Legibility: brighter
  inactive string names + idle mode labels. Settings overlay structure left
  intact. DSP/state/permissions/persistence + semantic contract unchanged.
  Details: docs/TDD_LOG.md (focused layout + no-signal).
- Prior: milestone COMPLETE — spatial composition + overlay refinement (2026-11-15).
  Main screen: the meter+readout are ONE brass-rimmed measurement assembly
  (`MeterAssembly`, height-aware `MeterAssemblyLayout` pure logic + tests) —
  the ivory gauge keeps a natural arc-filling aspect, the readout adjoins it
  directly, and the lower controls absorb surplus height (settings anchored
  low). This removes the empty bands above/below the meter at their source
  (the old width-capped, centre-aligned meter in an oversized weight slot).
  Scale labels enlarged (13dp / FLAT-SHARP 12dp), longer ticks; stronger
  walnut grain on the exposed rails; faceplate width capped (480dp) so the
  gauge stays arc-filling on wide screens. Overlay: three regions (header /
  scrolling CALIBRATION+TUNINGS content / fixed New-Import-Export footer),
  clear "A4 · 440 Hz" reference value with labelled ±1 Hz / ±0.1 Hz stepper
  groups, segmented Sharps ♯ / Flats ♭, refined tuning rows (bigger note
  text, brass active highlight, separate star favorite), brass rim. DSP /
  state / permissions / persistence and the semantic contract unchanged.
  Details: docs/TDD_LOG.md (spatial composition + overlay).
- Prior: milestone COMPLETE — structural faceplate redesign + G3 tracking fix
  (2026-11-15). G3 dropout fix (DSP, the G3 dropout fix): G3 dropped after ~1 s because
  sympathetic D3 makes the ~4:3 mix aperiodic and the YIN dip never
  crossed 0.15; added a guarded sub-threshold recovery + spectral-peak
  refinement on the recovery path only (normal path untouched), with
  `G3SympatheticTrackingTest`. Faceplate redesign (UI): one fixed wooden faceplate
  (enclosure → shared inset faceplate → mounted components), header
  removed, compact tuning/A4 label area + labeled TUNINGS & SETTINGS key,
  configuration moved to a scrolling modal `TuningsOverlay` (platform
  back via a hook, no new dependency); explicit scroll fallback for large
  fonts. DSP/state/permissions/persistence and the semantic contract
  preserved. Details: docs/TDD_LOG.md (G3 dropout fix + faceplate redesign).
- Prior: milestone COMPLETE — instrument refinement pass (2026-11-15). The analog redesign read
  as stacked cards; the refinement makes it one wooden body: horizontal flat-sawn
  walnut grain + tonal bands (deterministic, cached), upper-left lighting,
  thin aged-brass meter frame with larger face / finer needle / cast
  shadow, compact recessed readout strip (note cap 64sp), gold outlines
  removed, proud/flat/pressed/disabled key states, recessed mode track,
  collapsible Tunings & settings sheet (default expanded), tighter
  spacing + bottom inset breathing room. DSP/state/permissions/
  persistence and the semantic contract unchanged. Details:
  docs/TDD_LOG.md (instrument refinement).
- Prior: milestone COMPLETE — analog instrument UI redesign (2026-11-15).
  Walnut enclosure, ivory analog meter, physical needle, brass hardware,
  tactile keys; ui/instrument/ design system; CentsDial removed.
- Prior: milestone COMPLETE — G4 octave-bleed fix (2026-11-15). Playing G4
  (392 Hz) excited the open G string (196 Hz) sympathetically; the detector
  returned 196 Hz or no pitch at −10 dB and the stale guard dropped the G4
  lock. Added a continuous octave-fold in `TunerStateMachine` (a >300 ms
  playing gap still allows a real octave change) and an upper-octave
  recovery in `Yin.detect` when the lower candidate is rejected. Details:
  docs/TDD_LOG.md (G4 octave-bleed fix) + docs/QUALITY_REPORT.md (G4 octave-bleed section). Existing DSP thresholds
  unchanged.
- Prior: milestone COMPLETE — UX/accessibility/reliability/QA audit
  (2026-11-15). 18 verified defects fixed (persistence restore, false
  zero readings, permission/lifecycle, string lock/favorites, flat-note
  editing, theme, responsive layout, a11y names, import I/O bounds,
  ID collisions, destructive delete). Details: docs/TDD_LOG.md (UX/accessibility/QA audit) +
  docs/QUALITY_REPORT.md; host render evidence in shared/build/render/.
  Headless host renders RESOLVED; real-device verification (mic/
  screen reader) is the only remaining external gate.
- Last green checkpoint (2026-11-16, pitch tracking): :shared:jvmTest 390/390,
  :shared:testAndroidHostTest 342/342, run-demo 5/5, verify.sh 7/7 PASS.
  New DSP/tracking tests: YinSpectralTest 11, PitchTrackerTest 16,
  PitchPipelineAcceptanceTest 10, PitchLab harness + PitchLabProbe
  (shared/build/pitch-lab/report.md). Before/after numbers and pass criteria in
  docs/QUALITY_REPORT.md (pitch-tracking stability section). Real-device verification (mic/screen reader) and the
  live-guitar G3 check remain open; synthetic audio is not real-guitar proof.
- Prior checkpoint (2026-11-15, vintage overhaul): :shared:jvmTest 333/333,
  :shared:testAndroidHostTest 290/290, run-demo 5/5, verify.sh 7/7 PASS
  (offline contract + lint included). Geometry tests updated to the 90° sweep
  (±45°) + always-visible needle resting at −52¢; MeterAssemblyLayout aspect
  range 1.2..1.85 with mode-aware deepening. Root-cause layout fix: widthIn
  before fillMaxSize (the reversed order crushed the readout on wide viewports).
  Dial vision-verified: wide landscape, numerals outside the arc, 3-tier ticks +
  ▼ index, hidden pivot + brass escutcheon, radial glass + top shadow, illuminated
  target zone, FLAT/SHARP + CENTS stamp. Instrument screen vision-verified:
  cohesive flagship instrument, modest wood footer, balanced breathing room, no
  stretched controls. Readout pixel-verified at a stable 112dp in EVERY state
  (zero layout jump), compact in both modes; in-tune zone exact emerald #3D8B5E.
  Needle pixel-mapped: −3¢→−2.5° (LEFT), 0¢→0°, +120¢→+45° stop, no-signal rests
  −47° (visible, span 38). Small phone 360×640dp + large-text fallback unclipped;
  all nine render states fit. Chromatic/small-phone vision reviews rate-limited
  (pixel-verified instead). APK installed on the ASUS phone; on-device
  capture still blocked by the secure lock screen (headless renders + tests stand
  in). Real-device verification (mic/screen reader) + live-guitar G3 remain unverified.
- Prior checkpoint (2026-11-15, composition correction): :shared:jvmTest 332/332,
  :shared:testAndroidHostTest 289/289, run-demo 5/5, verify.sh 7/7 PASS
  (offline contract + lint included). Geometry tests updated to the 70° sweep
  (±35°); MeterAssemblyLayout aspect/fixtures aligned to the larger dial.
  Four states vision-verified (instrument valid, instrument no-signal,
  chromatic valid, chromatic no-signal): meter dominant (~310dp gauge,
  full-width arc, long clean needle), readout compact in BOTH modes, six
  string keys in one compact row with prominent note names, no-signal needle
  hidden (hub only) with one "Play a note" message, bottom reads as the wood
  casing (no internal blank bands). Needle pixel-mapped: −3¢→−2°, +120¢→+35°
  stop, no-signal hidden (span 20 vs 54 tracking). Small phone 360×640dp
  unclipped (gauge 250dp, six keys one row, ≥48dp targets); large-text scroll
  fallback unclipped. APK installed on the ASUS phone; on-device capture
  still blocked by the secure lock screen (headless renders + tests stand in).
  Real-device verification (mic/screen reader) + live-guitar G3 remain unverified.
- Prior checkpoint (2026-11-15, finishing pass): :shared:jvmTest 332/332,
  :shared:testAndroidHostTest 289/289, run-demo 5/5, verify.sh 7/7 PASS
  (offline contract + lint included). New tests: P15StatusWordingTest 4
  (Tracking vs Listening, no repeated "In tune", mic off/error distinct).
  Vision confirmed the instrument render: wood grain reads, readout compact
  and balanced, group gaps small/consistent, string names prominent over
  numbers, status strip "Tracking". Pixel-verified: readout content-sized
  (~104dp), bottom enclosure margin modest (~37dp below the settings key,
  fixed — no oversized footer), small phone 360×640dp unclipped with all six
  string keys + settings visible, large-text uses the scroll fallback.
  No-signal needle still hidden (radial span 3 vs 19–31 tracking). APK
  installed on the ASUS phone; on-device capture still blocked by the secure
  lock screen (headless renders + tests stand in). Real-device verification (mic/
  screen reader) + live-guitar G3 remain unverified.
- Prior checkpoint (2026-11-15, layout/no-signal): :shared:jvmTest 328/328,
  :shared:testAndroidHostTest 285/285, run-demo 5/5, verify.sh 7/7 PASS
  (offline contract + lint included). New tests: needleShown (geometry) +
  NoSignalPresentationTest 4 (concise no-signal messages). No-signal needle
  pixel-confirmed HIDDEN (radial-continuity span drops 25→7, hub stays);
  tracking needle still maps 0¢→~0°, +120¢→stop. Vision confirmed the
  no-signal render: no needle at flat stop, no "NO SIGNAL" on the face, one
  primary message in the readout. Internal group gaps reduced to spacedBy
  10dp (the two weighted spacers removed); one bottom enclosure margin
  remains (larger in the no-inset headless render and in chromatic mode,
  which has no string grid). APK installed on the ASUS phone; on-device
  screenshot blocked this pass by the secure lock screen (headless renders +
  tests stand in). Real-device verification (mic/screen reader) + live-guitar G3
  remain unverified.
- Prior checkpoint (2026-11-15, spatial composition): :shared:jvmTest 323/323,
  :shared:testAndroidHostTest 280/280, run-demo 5/5, verify.sh 7/7 PASS
  (offline contract + lint included). New tests: MeterAssemblyLayoutTest 8,
  ReferencePitchValueTest 3. Needle pixel-reverified in the embedded gauge:
  0¢→+1°, −3¢→−2°, +120¢→+46° (stop), no-signal→−46° (rest). Main screen
  pixel-checked: no empty bands around the meter, meter+readout contiguous,
  small phone 360×640 dp unclipped (128 px bottom margin). APK installed
  on the ASUS phone (Android 15) and the new layout confirmed on-device.
  Vision review of the main render positive (rate-limited intermittently);
  overlay verified via semantics tests + PIL.
- Prior checkpoint (2026-11-15, faceplate redesign): :shared:jvmTest 312/312,
  :shared:testAndroidHostTest 269/269, run-demo 5/5, verify.sh 7/7 PASS
  (offline contract + lint included). Needle pixel-reverified in the
  faceplate (radial-continuity isolation): 0¢→+1°, −3¢→−2°, +120¢→+44°
  (stop), no-signal→−48° (rest). Small phone 360×640 dp fixed, content
  ends at 93.6% height (no clipping). Device QA NOT run that pass: the
  ASUS phone dropped off wireless adb and was not rediscoverable; the
  vision model was rate-limited (429), so UI evidence was headless Skia
  renders + PIL geometry checks.
- Prior checkpoint (2026-11-15, instrument refinement): :shared:jvmTest 300/300,
  :shared:testAndroidHostTest 258/258, run-demo 5/5, verify.sh 7/7 PASS.
  Needle mapping pixel-reverified after refinement: 0¢→−0.05°,
  −3¢→−2.7°, +120¢→+45.6° (stop), no-signal→−45.7° (rest). Device QA on
  the ASUS phone (1080x2400): grain/readout/bottom-clearance PASS; the
  dimmed band in the no-signal state is intentional (honest rest state).
- Prior checkpoint (2026-11-15, analog redesign): :shared:jvmTest 297/297,
  :shared:testAndroidHostTest 256/256, run-demo 5/5, verify.sh 7/7 PASS;
  debug APK installed on the phone (Android 15) and the API 36 emulator.
- Prior checkpoint (2026-11-15, G4 octave fix): :shared:jvmTest 278/278,
  :shared:testAndroidHostTest 241/241, run-demo 5/5, headless UI PASS,
  debug APK installed on the phone (Android 15) and the API 36 emulator;
  verify.sh gates were last run at the QA audit (see below).
- Prior: device/SDK follow-up COMPLETE + overnight quality program (all five passes)
  (2026-11-08) fixed five DSP/engine/contract bugs (low-E noise,
  subharmonic, stale-note display, two-thread race, offline gate).
  Details + measured quality: docs/QUALITY_REPORT.md.
- DSP correctness facts (2026-11-07): Yin.detect now selects between the
  first-crossing candidate and the global in-band minimum, validated by
  spectral presence (ratio ≥ 0.25) + fundamental plausibility (no in-band
  harmonic 2f..24f > 10x own energy); deeper dip wins, tie → smaller lag.
  StreamingAnalyzer default window = 4096 (93 ms). Pluck fixtures use a
  physical pluck-shape excitation. Contract at 20 dB: 45/45 notes ×
  3 flavors, identity 100%/95%/100%, median ≤5¢/5¢/10¢. 10 dB = documented
  stress limit (identity 41–100% in 87–150 Hz; table in QUALITY_REPORT.md).
- Test breakdown (verify per-run via test-results XML, not this snapshot):
  214 jvm = 204 pre-device-follow-up core + 3 PluckedStringRealism + 4 FrequencyMatrix
  + 3 YinNoiseRobustness. 190 host = same common + 4 Robolectric.
- Runnable demo: ./scripts/run-demo.sh → :hostDemo (5 assertive scenarios;
  [2] = instrument AUTO → lock E2 → unlock; mandatory verify.sh gate 2/4).
- Architecture: AppTuner (engine, common: capture orchestration, YIN,
  state machine, PresetStore targets, custom CRUD + persistCustoms hook,
  AUTO/lock targeting, ±0.1 Hz concert steppers) → TunerController
  (permissions/errors/UI mapping + CRUD facade, common) → MainScreen
  (Compose host, common) → Android shell (ViewModel + pump thread +
  Activity, androidMain; bridge behavior-tested via Robolectric).
- Robolectric host-test facts: AGP-kmp android target gets the
  test source set via `android.withHostTest { }` (no generated accessor —
  deps go in `named("androidHostTest")`); task = :shared:testAndroidHostTest;
  the 176-test common suite ALSO runs there; viewModelScope needs
  `shadowOf(Looper.getMainLooper()).idle()` pumping; Robolectric apps are
  per-test → reset process singletons in @Before (internal test hook).
- DataStore 1.3.0 android facts (Robolectric host suite): filename MUST end in
  preferences_pb (hard extension check); ONE active store per file per
  process → createContextDataStore is a per-process singleton (synchronized).
- Editor UI note: form state lives in MainScreen (remember), not in
  the composable — headless semantics tests drive it via callbacks.
- DataStoreSettingsRepository is commonMain (injected DataStore); file
  factory jvmMain, Context factory androidMain.
- Compose test facts: performScrollToNode = top-level extension in
  androidx.compose.ui.test (import it); assertIsDisplayed is viewport-based —
  assert top-of-container nodes BEFORE scrolling, use performScrollToNode
  for below-fold nodes; Surface(onClick=...) exists in m3 1.9.
- DataStore 1.3.0 facts: key factories doublePreferencesKey/stringPreferencesKey
  (Kotlin names; bytecode @JvmName doubleKey/stringKey); File factory =
  jvmAndAndroid API; updateData = durable write (edit is fire-and-forget);
  repository lives in jvmMain (Android twin pending the SDK); never give the
  store a TestScope (runTest hangs on DataStore's long-lived jobs); trailing
  lambdas bind to the LAST parameter (design signatures accordingly).
- UI facts: headless Compose SEMANTICS tests work (skiko native 0.150.2 in
  jvmTest). Headless PNG rendering works via the public
  `ImageComposeScene.render()` API (2026-11-15); no Xvfb/display needed.
  `TunerScreenRenderSmokeTest` writes dark/light/permission/editor/2x PNGs.
- Versions: Kotlin 2.4.21, AGP 9.3.1, Gradle 9.7.0 (wrapper, sha256-pinned),
  CMP 1.12.2, CMP-Material3 1.9.0, coroutines 1.11.0, serialization 1.12.0,
  lifecycle 2.11.0, datastore + datastore-preferences 1.3.0, activity-compose
  1.13.0, skiko native 0.150.2 (jvmTest), compileSdk 37 (flag suppressed —
  repo has no platforms;android-37, see gradle.properties), minSdk 26, JDK 21
  (JVM_17 bytecode).
- Key paths: repo root; Gradle
  user-local 9.7.0; SDK at ANDROID_HOME (platforms:
  android-36; ANDROID_HOME must be exported for builds).
- Blockers: Android SDK RESOLVED; headless host renders RESOLVED
  (ImageComposeScene); device now attached (ASUS Android 15 over
  wireless adb + API 36 emulator), but the instrumented
  `RealMicSmokeTest` is structurally invalid — it builds
  `CaptureOrchestrator` directly and never calls `poll()`, so it fails on
  both phone and emulator (found 2026-11-15, not fixed here). Real-guitar
  audio/route/screen-reader checks remain manual.
- Failing test: none in the host suites. Device suite: `RealMicSmokeTest`
  fails everywhere for the missing pump (see device-gate note).
