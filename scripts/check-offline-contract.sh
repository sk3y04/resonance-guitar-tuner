#!/usr/bin/env bash
# OFFLINE-BY-CONTRACT gate (no INTERNET, no analytics, no
# login, no cloud). Reads the MERGED debug manifest — i.e. every
# <uses-permission> from the app AND all its libraries after manifest
# merging — and fails if:
#   1. android.permission.INTERNET (or any *network* permission) is present,
#   2. any permission outside the explicit allowlist is present,
#   3. cleartext traffic is enabled or a network-security-config is wired in.
# The allowlist is the whole point: adding a permission in any dependency
# fails the build here until the spec is amended AND the allowlist updated
# deliberately. Exits non-zero on any violation.
set -u
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"

MANIFEST="androidApp/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"

if [ ! -f "$MANIFEST" ]; then
  echo "[contract] merged manifest not found — building it..."
  ./gradlew :androidApp:processDebugManifest --console=plain || exit 1
fi

python3 - "$MANIFEST" <<'EOF'
import sys, xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
ALLOWED = {
    # The entire permission allowlist of the app (spec §Offline-by-contract).
    # Microphone capture for live tuning — and nothing else.
    'android.permission.RECORD_AUDIO',
    # Injected by AGP for ContextCompat.registerReceiver compatibility
    # (Android 13+): a package-private, system-managed receiver-visibility
    # permission. Not a capability, not a leak surface — but declared
    # explicitly so it cannot hide a real one.
    'dev.anima.resonance.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION',
}
NETWORKISH = ('INTERNET', 'ACCESS_NETWORK', 'ACCESS_WIFI', 'ACCESS_FINE_LOCATION',
              'ACCESS_COARSE_LOCATION')  # location = a network-adjacent leak surface

path = sys.argv[1]
root = ET.parse(path).getroot()
perms = [e.get(ANDROID + 'name') for e in root.iter() if e.tag.endswith('uses-permission')]
failures = []

if 'android.permission.INTERNET' in perms:
    failures.append('INTERNET permission present — offline-by-contract VIOLATED')
for p in perms:
    if any(k in p for k in NETWORKISH):
        failures.append(f'network-adjacent permission present: {p}')
for p in perms:
    if p not in ALLOWED:
        failures.append(f'permission outside the allowlist: {p} '
                        f'(amend the spec + ALLOWED in scripts/check-offline-contract.sh on purpose)')

for e in root.iter():
    if e.tag.endswith('application'):
        if e.get(ANDROID + 'usesCleartextTraffic') == 'true':
            failures.append('application usesCleartextTraffic="true"')
    if e.tag.endswith('meta-data') and 'network_security_config' in (e.get(ANDROID + 'resource') or ''):
        failures.append('network-security-config wired in (cleartext path)')

uniq = sorted(set(perms))
print(f"[contract] merged manifest permissions ({len(uniq)}): {', '.join(uniq) if uniq else '(none)'}")
if failures:
    for f in failures:
        print(f'  VIOLATION: {f}')
    print('[contract] FAIL')
    sys.exit(1)
print('[contract] PASS: offline-by-contract holds (allowlist only, no INTERNET, no cleartext).')
EOF
