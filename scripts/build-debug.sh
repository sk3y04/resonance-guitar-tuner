#!/usr/bin/env bash
# Repeatable debug build: produces the APK, copies it to artifacts/, records
# sha256 + commit + build metadata. Requires the Android SDK platform.
set -u
cd "$(dirname "$0")/.."
LOG_DIR="artifacts/logs"
mkdir -p "$LOG_DIR" "$artifacts"

if ! ./gradlew -q projects --console=plain 2>/dev/null | grep -q ':androidApp'; then
  echo "[build-debug] BLOCKED: :androidApp not configured (no Android SDK platform)"
  exit 3
fi

set -o pipefail
./gradlew :androidApp:assembleDebug --console=plain 2>&1 | tee "$LOG_DIR/build-debug-$(date +%Y%m%dT%H%M%SZ).log"
EXIT=${PIPESTATUS[0]}
[ "$EXIT" -ne 0 ] && { echo "[build-debug] FAIL (exit $EXIT)"; exit "$EXIT"; }

APK=$(ls -t androidApp/build/outputs/apk/debug/*.apk 2>/dev/null | head -1)
[ -z "$APK" ] && { echo "[build-debug] FAIL: no APK produced"; exit 1; }

DEST="artifacts/resonance-debug-$(date +%Y%m%d).apk"
cp "$APK" "$DEST"
{
  echo "artifact: $DEST"
  echo "sha256: $(sha256sum "$DEST" | cut -d' ' -f1)"
  echo "commit: $(git rev-parse HEAD 2>/dev/null || echo 'no git')"
  echo "date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "size: $(stat -c%s "$DEST") bytes"
} | tee "artifacts/LATEST_BUILD.txt"
echo "[build-debug] PASS: $DEST"
exit 0
