#!/usr/bin/env bash
# Runnable host demo: full pipeline (synthetic mic → engine → controller →
# UI state), assertive (exits 1 on any scenario failure). No device/display/SDK.
set -u
cd "$(dirname "$0")/.."
./gradlew :hostDemo:run --console=plain
status=$?
if [ $status -ne 0 ]; then
  echo "[run-demo] FAIL (exit $status)"
else
  echo "[run-demo] PASS"
fi
exit $status
