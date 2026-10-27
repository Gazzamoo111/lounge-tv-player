#!/usr/bin/env bash
# Install a GitHub PR preflight APK with one persistent LOCAL debug signature.
# NEVER uninstalls an app or clears user data. Does not touch production.
#
# Usage: bash scripts/install_local_qa.sh <successful-run-id> [fire-tv-ip:5555]
set -euo pipefail

RUN="${1:?Pass the successful GitHub Actions run ID}"
TV="${2:-192.168.68.50:5555}"
REPO="Gazzamoo111/lounge-tv-player"
QA="com.loungetv.player.recoveryqa"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
KEY="$HOME/.android/lounge-tv-recoveryqa-debug.keystore"
ALIAS="loungetv-recoveryqa"
DEST="$(mktemp -d "$HOME/Downloads/LoungeTV-SideBySide-XXXXXX")"

command -v gh >/dev/null || { echo "GitHub CLI (gh) not found"; exit 1; }
command -v adb >/dev/null || { echo "Android adb not found"; exit 1; }
command -v keytool >/dev/null || { echo "Java keytool not found"; exit 1; }
APKSIGNER="$(find "$SDK/build-tools" -maxdepth 2 -type f -name apksigner -print 2>/dev/null | sort | tail -n 1)"
[ -n "$APKSIGNER" ] || { echo "Android SDK apksigner not found in $SDK/build-tools"; exit 1; }

echo "=== VERIFY GITHUB RUN ==="
test "$(gh run view "$RUN" -R "$REPO" --json conclusion -q '.conclusion')" = success ||
  { echo "Refusing an unverified/failed QA run"; exit 1; }
gh run download "$RUN" -R "$REPO" -n lounge-tv-recoveryqa-preflight -D "$DEST"
APK="$(find "$DEST" -type f -name '*.apk' -print -quit)"
[ -s "$APK" ] || { echo "No QA APK in artifact"; exit 1; }

# Make this key only on the developer's Mac; NEVER commit it to GitHub.
if [ ! -s "$KEY" ]; then
  mkdir -p "$(dirname "$KEY")"
  keytool -genkeypair -noprompt -keystore "$KEY" -storepass android -keypass android \
    -alias "$ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=Lounge TV Recovery QA, O=Local Testing, C=NZ"
  chmod 600 "$KEY"
fi

SIGNED="$DEST/LoungeTV-LocalSigned-QA.apk"
"$APKSIGNER" sign \
  --ks "$KEY" --ks-key-alias "$ALIAS" \
  --ks-pass pass:android --key-pass pass:android \
  --out "$SIGNED" "$APK"
"$APKSIGNER" verify "$SIGNED"

echo "=== INSTALL LOCAL-SIGNED QA ON $TV ==="
adb connect "$TV" </dev/null
STATUS_PACKAGE="$(adb -s "$TV" shell service check package </dev/null 2>&1 || true)"
if [[ "$STATUS_PACKAGE" != *"Service package: found"* ]]; then
  echo "STOP: Fire Stick Android Package Manager is not available. No app data changed."
  exit 1
fi
# Never automatically delete existing data. The first switch to this
# machine's stable QA certificate needs deliberate consent, exactly once.
set +e
RESULT="$(adb -s "$TV" install --no-incremental -r "$SIGNED" </dev/null 2>&1)"
STATUS=$?
set -e
echo "$RESULT"
if [ "$STATUS" -ne 0 ]; then
  echo "STOP: Installation rejected. Nothing was uninstalled or deleted."
  echo "Existing QA and production apps remain unchanged."
  exit 1
fi

adb -s "$TV" shell monkey -p "$QA" 1 </dev/null
echo "RECOVERY QA INSTALLED; PRODUCTION AND QA DATA PRESERVED"
