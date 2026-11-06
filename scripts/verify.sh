#!/usr/bin/env bash
# Full local verification: host tests + available mandatory lint + debug assembly.
# Exits non-zero if any mandatory, locally-available gate fails.
# Gates that are externally blocked (SDK, display) are reported as BLOCKED,
# never as green. Optional hardware checks are reported separately.
set -u
cd "$(dirname "$0")/.."
FAILED=0

echo "=== 1/3 host unit tests ==="
./scripts/test-unit.sh || FAILED=1

echo "=== 2/3 Kotlin/Compose lint (host-visible subset) ==="
# lintDebug lives in :androidApp; only available when the Android SDK gate is open.
if [ -n "$(./gradlew -q projects --console=plain 2>/dev/null | grep ':androidApp' || true)" ]; then
  ./gradlew :androidApp:lintDebug --console=plain || FAILED=1
else
  echo "[verify] BLOCKED: :androidApp not configured (no Android SDK platform)"
fi

echo "=== 3/3 debug assembly ==="
if [ -n "$(./gradlew -q projects --console=plain 2>/dev/null | grep ':androidApp' || true)" ]; then
  ./gradlew :androidApp:assembleDebug --console=plain || FAILED=1
else
  echo "[verify] BLOCKED: debug assembly (no Android SDK platform)"
fi

echo "=== optional: hardware checks ==="
echo "[verify] NOT RUN: device/emulator tests. Run ./scripts/test-device.sh when a device exists."

if [ "$FAILED" -ne 0 ]; then
  echo "[verify] FAIL: at least one mandatory available gate failed."
  exit 1
fi
echo "[verify] PASS for all mandatory locally-available gates (blocked gates listed above)."
exit 0
