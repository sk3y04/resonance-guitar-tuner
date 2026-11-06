# TDD log (RED/GREEN/REFACTOR evidence)

Format: slice ID | test(s) | command | initial failure | final exit | report.

## Note frequency math (test written, implementation pending)
- Tests: NoteMathTest (a4EqualsConcertPitch, standardLowEIsE2AtAbout8241Hz,
  frequencyScalesExactlyWithConcertPitch, octaveIsOneTwelfthRatio,
  rejectsOutOfRangeConcertPitch)
- Tolerance basis: 1e-3 Hz on absolute E2 (well above FP64 error ~1e-13,
  guards against wrong-formula rounding); 1e-12 on ratios; 1e-9 on A4.
- RED run: PENDING (command: ./gradlew :shared:jvmTest)
  Expected: 5 assertion failures (stub returns 0.0), exit code 1.
- GREEN: PENDING
