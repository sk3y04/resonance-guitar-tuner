# STATE — Resonance

- DSP COMPLETE → state machine + Android input next
- Last green checkpoint: ./scripts/test-unit.sh exit 0, "discovered tests: 74"
  (music 47 + DSP 27), commit after the preset-codec slice (DSP checkpoint).
- Next action: write TunerStateMachine RED tests with a fake
  detector driver + virtual clock: target hysteresis (stable target requires
  N consecutive agreeing estimates), time-aware smoothing (median/EMA with
  discontinuity reset on PitchEstimate(0,0)), stale expiration (no fresh
  estimate for X ms → STALE state), in-tune enter/exit band (e.g. ±5 cents
  enter, ±8 exit), then implement in shared/src/commonMain/.../presentation/.
- Versions: Kotlin 2.4.21, AGP 9.3.1, Gradle 9.7.0 (wrapper, sha256-pinned),
  CMP 1.12.2, CMP-Material3 1.9.0, coroutines 1.11.0, serialization 1.12.0,
  lifecycle 2.11.0, datastore 1.3.0, activity-compose 1.13.0, compileSdk 36,
  minSdk 26, JDK 21 toolchain (JVM_17 bytecode).
- DSP facts (measured): yin.detect median=949us p95=959us @48kHz/2048-sample
  frame, host Threadripper 5955WX (no Android latency claim).
- Key paths: repo root; Gradle
  user-local 9.7.0; SDK cmdline-tools at ANDROID_HOME
  (NO platforms — licenses not accepted).
- Blockers: Android SDK licenses; no display/Xvfb;
  no emulator/phone.
- Failing test: none.
- AudioRecord adapter: WRITTEN, compile BLOCKED (missing SDK). The state-machine/capture milestone
  otherwise COMPLETE (99/99 host tests: state machine, streaming, capture
  orchestration, concert pitch). Milestone exit = satisfied for everything
  locally achievable; Android compile + device validation remain explicit
  BLOCKED/NOT RUN gates, not claims.
- Next session resume point: Compose UI — behavior/semantics tests
  FIRST (denied permission, listening, flat E2, in-tune, clipping, stale,
  string select/lock, preset switch), then dial + theme tokens per spec §8.
