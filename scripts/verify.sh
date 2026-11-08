#!/usr/bin/env bash
# Full local verification: host tests + available mandatory lint + debug assembly.
# Exits non-zero if any mandatory, locally-available gate fails.
# Gates that are externally blocked (SDK, display) are reported as BLOCKED,
# never as green. Optional hardware checks are reported separately.
set -u
cd "$(dirname "$0")/.."
FAILED=0

echo "=== 1/7 host unit tests ==="
./scripts/test-unit.sh || FAILED=1

echo "=== 2/7 runnable host demo (full pipeline, synthetic mic) ==="
./scripts/run-demo.sh || FAILED=1

echo "=== 3/7 offline-by-contract (merged manifest: no INTERNET, allowlist only) ==="
# The offline contract is enforced from the MERGED manifest (app +
# all library permissions after merging). Adding any non-allowlisted
# permission — including in a dependency — fails this gate.
if [ -n "$(./gradlew -q projects --console=plain 2>/dev/null | grep ':androidApp' || true)" ]; then
  ./scripts/check-offline-contract.sh || FAILED=1
else
  echo "[verify] BLOCKED: offline-contract (no Android SDK platform)"
fi

echo "=== 4/7 Kotlin/Compose lint (host-visible subset) ==="
# lintDebug lives in :androidApp; only available when the Android SDK gate is open.
if [ -n "$(./gradlew -q projects --console=plain 2>/dev/null | grep ':androidApp' || true)" ]; then
  ./gradlew :androidApp:lintDebug --console=plain || FAILED=1
else
  echo "[verify] BLOCKED: :androidApp not configured (no Android SDK platform)"
fi

echo "=== 5/7 debug assembly ==="
if [ -n "$(./gradlew -q projects --console=plain 2>/dev/null | grep ':androidApp' || true)" ]; then
  ./gradlew :androidApp:assembleDebug --console=plain || FAILED=1
else
  echo "[verify] BLOCKED: debug assembly (no Android SDK platform)"
fi

echo "=== 6/7 Android host tests (Robolectric: real Context/files on the JVM) ==="
# The androidMain bridge (DataStore, ViewModel/SAF seams) is
# behavior-tested without a device — the same SDK gate as lint/assemble.
if [ -n "$(./gradlew -q projects --console=plain 2>/dev/null | grep ':androidApp' || true)" ]; then
  ./gradlew :shared:testAndroidHostTest --console=plain || FAILED=1
else
  echo "[verify] BLOCKED: android host tests (no Android SDK platform)"
fi

echo "=== 7/7 instrumented test APK (compile-check; RUN needs a device) ==="
# The on-device suite (real mic, real UI) must stay buildable even
# without a device; running it is ./scripts/test-device.sh.
if [ -n "$(./gradlew -q projects --console=plain 2>/dev/null | grep ':androidApp' || true)" ]; then
  ./gradlew :androidApp:assembleDebugAndroidTest --console=plain || FAILED=1
else
  echo "[verify] BLOCKED: instrumented test APK (no Android SDK platform)"
fi

echo "=== optional: hardware checks ==="
echo "[verify] NOT RUN: device/emulator tests. Run ./scripts/test-device.sh when a device exists."

if [ "$FAILED" -ne 0 ]; then
  echo "[verify] FAIL: at least one mandatory available gate failed."
  exit 1
fi
echo "[verify] PASS for all mandatory locally-available gates (blocked gates listed above)."
exit 0
