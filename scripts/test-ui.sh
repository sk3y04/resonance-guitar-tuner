#!/usr/bin/env bash
# Compose UI tests on the JVM host. Requires an X11 display (Xvfb works).
# Distinguishes BLOCKED (prereq missing) from FAIL (tests failed): exit 3 = BLOCKED.
set -u
cd "$(dirname "$0")/.."
LOG_DIR="artifacts/logs"
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/test-ui-$(date +%Y%m%dT%H%M%SZ).log"

if [ -z "${DISPLAY:-}" ] && ! command -v Xvfb >/dev/null 2>&1 && ! command -v xvfb-run >/dev/null 2>&1; then
  echo "[test-ui] BLOCKED: no display (set DISPLAY, or install xvfb). Prereq missing — not a test failure."
  exit 3
fi

RUNNER="./gradlew :shared:jvmTest --tests 'dev.anima.resonance.ui.*' --console=plain"
# Prefer xvfb-run when available and no real display.
if [ -z "${DISPLAY:-}" ] && command -v xvfb-run >/dev/null 2>&1; then
  RUNNER="xvfb-run -a $RUNNER"
fi

set -o pipefail
$RUNNER 2>&1 | tee "$LOG"
EXIT=${PIPESTATUS[0]}
if [ "$EXIT" -ne 0 ]; then
  echo "[test-ui] FAIL (exit $EXIT, log: $LOG)"
  exit "$EXIT"
fi
echo "[test-ui] PASS (log: $LOG)"
exit 0
