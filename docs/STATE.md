# STATE — Resonance

- Phase: 18 COMPLETE — coherent physical-instrument refinement + honest signal
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
- Last green checkpoint (2026-11-15, vintage overhaul): :shared:jvmTest 333/333,
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
