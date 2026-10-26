#!/usr/bin/env bash
# Install a GitHub PR preflight APK with one persistent LOCAL debug signature.
# NEVER uninstalls an app or clears user data. Does not touch production.
#
# Usage: bash scripts/install_local_qa.sh <successful-run-id> [fire-tv-ip:5555]
set -euo pipefail

RUN="${1:?Pass the successful GitHub Actions run ID}"
TV="${2:-192.168.68.50:5555}"
REPO="Gazzamoo111/lounge-tv-player"
QA="com.loungetv.player.eventqa"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
KEY="$HOME/.android/lounge-tv-eventqa-debug.keystore"
ALIAS="loungetv-eventqa"
DEST="$(mktemp -d "$HOME/Downloads/LoungeTV-RecoveryQA-XXXXXX")"

command -v gh >/dev/null || { echo "GitHub CLI (gh) not found"; exit 1; }
command -v adb >/dev/null || { echo "Android adb not found"; exit 1; }
command -v keytool >/dev/null || { echo "Java keytool not found"; exit 1; }
APKSIGNER="$(find "$SDK/build-tools" -maxdepth 2 -type f -name apksigner -print 2>/dev/null | sort | tail -n 1)"
[ -n "$APKSIGNER" ] || { echo "Android SDK apksigner not found in $SDK/build-tools"; exit 1; }

echo "=== VERIFY GITHUB RUN ==="
test "$(gh run view "$RUN" -R "$REPO" --json conclusion -q '.conclusion')" = success ||
  { echo "Refusing an unverified/failed QA run"; exit 1; }
gh run download "$RUN" -R "$REPO" -n lounge-tv-eventqa-preflight -D "$DEST"
APK="$(find "$DEST" -type f -name '*.apk' -print -quit)"
[ -s "$APK" ] || { echo "No QA APK in artifact"; exit 1; }

# Make this key only on the developer's Mac; NEVER commit it to GitHub.
if [ ! -s "$KEY" ]; then
  mkdir -p "$(dirname "$KEY")"
  keytool -genkeypair -noprompt -keystore "$KEY" -storepass android -keypass android \
    -alias "$ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=Lounge TV Event QA, O=Local Testing, C=NZ"
  chmod 600 "$KEY"
fi

SIGNED="$DEST/LoungeTV-LocalSigned-QA.apk"
"$APKSIGNER" sign \
  --ks "$KEY" --ks-key-alias "$ALIAS" \
  --ks-pass pass:android --key-pass pass:android \
  --out "$SIGNED" "$APK"
"$APKSIGNER" verify "$SIGNED"

echo "=== INSTALL LOCAL-SIGNED QA ON $TV ==="
adb connect "$TV"
# Crucially, never auto-uninstall on signature/version errors.
if ! adb -s "$TV" install -r "$SIGNED"; then
  echo
  echo "STOP: QA update was rejected. Your existing QA data was NOT deleted."
  echo "The first switch to this persistent local key may require ONE intentional"
  echo "QA-only reset after you have saved anything important."
  echo "DO NOT uninstall com.loungetv.player (production)."
  echo "Local QA key: $KEY"
  exit 1
fi
adb -s "$TV" shell monkey -p "$QA" 1
echo "RECOVERY QA INSTALLED; PRODUCTION AND QA DATA PRESERVED"
