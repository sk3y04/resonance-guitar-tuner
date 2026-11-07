#!/usr/bin/env bash
# Opt-in device/emulator integration tests. Never wipes or resets an
# existing device. Usage: ./scripts/test-device.sh [serial]
#   serial omitted -> uses the single attached emulator/device; errors if ambiguous.
# Runs :androidApp:connectedDebugAndroidTest (on-device suite: real mic smoke,
# real persistence, UI launch). RECORD_AUDIO is granted via pm first — an
# unattended run cannot tap a permission dialog.
set -u
cd "$(dirname "$0")/.."

if ! command -v adb >/dev/null 2>&1; then
  echo "[test-device] BLOCKED: adb not found (Android SDK platform-tools)."
  exit 3
fi

DEVICES=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
COUNT=$(echo "$DEVICES" | grep -c . || true)
if [ -n "${1:-}" ]; then
  SERIAL="$1"
elif [ "$COUNT" -eq 1 ]; then
  SERIAL="$DEVICES"
else
  echo "[test-device] BLOCKED: $COUNT devices attached; pass a serial explicitly."
  adb devices
  exit 3
fi

echo "[test-device] using device: $SERIAL"
# Grant the mic permission up front (idempotent; the app is installed by the
# connected test run, so retry once after the first install).
adb -s "$SERIAL" shell pm grant dev.anima.resonance android.permission.RECORD_AUDIO 2>/dev/null || true

./gradlew :androidApp:connectedDebugAndroidTest --console=plain || exit 1
echo "[test-device] PASS on $SERIAL"
exit 0
