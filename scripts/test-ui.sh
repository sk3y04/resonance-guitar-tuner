#!/usr/bin/env bash
# Compose UI tests on the JVM host. The screen uses Compose's skiko test
# backend and ImageComposeScene for render smoke tests, both of which run
# headlessly here (verified 2026-11-15); no X11 server is required.
# Distinguishes BLOCKED (prereq missing) from FAIL (tests failed): exit 3 = BLOCKED.
set -u
cd "$(dirname "$0")/.."
LOG_DIR="artifacts/logs"
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/test-ui-$(date +%Y%m%dT%H%M%SZ).log"

set -o pipefail
./gradlew :shared:jvmTest --tests 'dev.anima.resonance.ui.*' --console=plain 2>&1 | tee "$LOG"
EXIT=${PIPESTATUS[0]}
if [ "$EXIT" -ne 0 ]; then
  echo "[test-ui] FAIL (exit $EXIT, log: $LOG)"
  exit "$EXIT"
fi
echo "[test-ui] PASS (headless semantics + render smoke; log: $LOG)"
exit 0
