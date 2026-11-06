#!/usr/bin/env bash
# Fast host tests: DSP/domain/presentation/persistence. No phone, no display.
# Fails on test failures AND on unexpectedly zero discovered tests.
set -u
cd "$(dirname "$0")/.."
LOG_DIR="artifacts/logs"
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/test-unit-$(date +%Y%m%dT%H%M%SZ).log"

set -o pipefail
./gradlew :shared:jvmTest --console=plain 2>&1 | tee "$LOG"
EXIT=${PIPESTATUS[0]}

# Guard against vacuous green: at least one test must have been discovered.
TESTS=$(grep -rhoE 'tests="[0-9]+"' shared/build/test-results/jvmTest/*.xml 2>/dev/null \
         | grep -oE '[0-9]+' | awk '{s+=$1} END {print s+0}')
echo "[test-unit] discovered tests: $TESTS (log: $LOG)"
if [ "$TESTS" -eq 0 ]; then
  echo "[test-unit] FAIL: zero tests discovered — this is not a pass."
  exit 2
fi
if [ "$EXIT" -ne 0 ]; then
  echo "[test-unit] FAIL (gradle exit $EXIT)"
  exit "$EXIT"
fi
echo "[test-unit] PASS"
exit 0
