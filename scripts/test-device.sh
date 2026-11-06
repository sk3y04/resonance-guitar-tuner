#!/usr/bin/env bash
# Opt-in device/emulator integration tests. Never wipes or resets an existing device.
# Usage: ./scripts/test-device.sh [serial]
#   serial omitted -> uses the single attached emulator/device; errors if ambiguous.
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
# Device tests live in :shared android target (host tests) — task name discovered
# from the live project; do not hardcode assumptions here.
TASKS=$(./gradlew :shared:tasks --all --console=plain 2>/dev/null | grep -E '^connectedDevice[^ ]*Test' | awk '{print $1}' | sort -u)
if [ -z "$TASKS" ]; then
  echo "[test-device] BLOCKED: no connected-device test task discovered in :shared (Android target off?)."
  exit 3
fi
for t in $TASKS; do
  echo "[test-device] running $t on $SERIAL"
  ./gradlew "$t" -PandroidInstrumentationTestArgumentProvider=serial="$SERIAL" --console=plain || exit 1
done
echo "[test-device] PASS on $SERIAL"
exit 0
