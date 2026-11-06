# Verification matrix (observed, never assumed)

Legend: PASS / FAIL / BLOCKED (id) / NOT RUN. Updated after each run.

| Check | Status | Evidence |
|---|---|---|
| Dependency metadata verification | PASS | docs/DEPENDENCIES.md (live fetches 2026-11-06) |
| Gradle 9.7.0 distribution sha256 | PASS | 84fbba45… match (official .sha256) |
| Scaffold + wrapper | PASS | git history; wrapper sha256 pinned |
| :shared:jvmTest host suite (final) | PASS | 167/167 (test-unit-20261106T161014Z.log); ./scripts/test-unit.sh exit 0 |
| Music domain RED→GREEN | PASS | 47/47; TDD_LOG music-domain sections |
| DSP (YIN + spectral guard) RED→GREEN | PASS | 74/74; yin.detect median 949µs p95 959µs @48k/2048 |
| State machine + streaming + capture | PASS | 99/99 |
| UI state + headless Compose semantics | PASS | 119/119 (5 UI tests, skiko native 0.150.2) |
| Persistence (DataStore, real files) | PASS | 146/146 (8 DataStore tests round-trip real files) |
| Preset sheet in real screen | PASS | 151/151 (scroll-reachability via performScrollToNode) |
| AppTuner end-to-end engine | PASS | 158/158 (7 tests: capture→YIN→state→presets→settings) |
| TunerController (permissions/errors/UI-state) | PASS | 164/164 (6 tests) |
| MainScreen host + DataStore→commonMain | PASS | 167/167 (3 headless tests: real controller → rendered semantics) |
| Android debug assembly | BLOCKED (missing SDK) | no SDK platform; verify.sh reports BLOCKED, never green |
| Android lint | BLOCKED (missing SDK) | :androidApp not configured without SDK platform |
| Compose screenshots / visual inspection | BLOCKED (no display) | no display; CMP 1.12.2 desktop test jar has no capture API (javap-verified) |
| Device/emulator tuning (real mic) | NOT RUN (no device) | no device; ./scripts/test-device.sh ready |
| Offline-by-contract (manifest) | PASS (static) | AndroidManifest.xml: RECORD_AUDIO only; no INTERNET permission present |
| GPL/competitor code | PASS (static) | YIN is own implementation (TarsosDSP studied as concept only); no third-party assets |

Test counts (nonzero required for PASS): 167 discovered, 167 passing (final).
