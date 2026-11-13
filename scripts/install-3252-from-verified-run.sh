#!/usr/bin/env bash
# Install the validated 3,252-channel Lounge TV 2.0 QA build on Firestick.
# This script does NOT create commits, start workflows, merge a PR, activate
# production, uninstall apps, clear data or access developer credentials.
set -euo pipefail
REPO="Gazzamoo111/lounge-tv-player"
RUN_ID=43520062316
EXPECTED_HEAD="9bebf5000a3c6be84e7b0523a84da4a8552413c7"
ARTIFACT="lounge-tv-2-v2qa"
PACKAGE="com.loungetv.player.v2qa"
DEVICE="192.168.68.53:5555"

for cmd in gh adb; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "Missing $cmd"; exit 1; }
done

echo "Checking original PR #68 QA build #$RUN_ID..."
# A current in-progress workflow may be watched, but only a successfully
# completed and SHA-matched PR build may be installed.
status="$(gh run view "$RUN_ID" -R "$REPO" --json status --jq '.status')"
if [[ "$status" != "completed" ]]; then
  gh run watch "$RUN_ID" -R "$REPO" --exit-status || {
    echo "QA checks have not passed. No Firestick changes made."
    exit 1
  }
fi
meta="$(gh run view "$RUN_ID" -R "$REPO" --json headSha,event,conclusion --jq '[.headSha,.event,.conclusion] | join(" ")')"
if [[ "$meta" != "$EXPECTED_HEAD pull_request success" ]]; then
  echo "Refusing to install from wrong/failed workflow: $meta"
  exit 1
fi

echo "Checking Firestick connection..."
adb connect "$DEVICE"
if ! adb devices | grep -Eq "^$DEVICE[[:space:]]+device$"; then
  echo "ADB connection not available: $DEVICE"; exit 1
fi
if ! adb -s "$DEVICE" shell pm list packages "$PACKAGE" | grep -Fq "package:$PACKAGE"; then
  echo "The existing $PACKAGE QA app is not installed. No installation attempted."
  exit 1
fi

workdir="$(mktemp -d /tmp/lounge3252-qa.XXXXXX)"
trap 'rm -rf "$workdir"' EXIT
gh run download "$RUN_ID" -R "$REPO" -n "$ARTIFACT" -D "$workdir"
apk="$(find "$workdir" -type f -name '*.apk' -print -quit)"
if [[ -z "$apk" || ! -s "$apk" ]]; then
  echo "No verified QA APK found in run #$RUN_ID"
  exit 1
fi
echo "Installing the verified PR QA APK over the existing app..."
if ! adb -s "$DEVICE" install -r "$apk"; then
  echo "Android rejected the update. Existing QA app has not been uninstalled."
  echo "Do not clear application data or force a different signing certificate."
  exit 1
fi

echo "Starting QA Live sync against the isolated 3,252-channel endpoint..."
adb -s "$DEVICE" logcat -c || true
adb -s "$DEVICE" shell am force-stop "$PACKAGE"
adb -s "$DEVICE" shell monkey -p "$PACKAGE" 1 >/dev/null

success=0
for attempt in $(seq 1 65); do
  logs="$(adb -s "$DEVICE" logcat -d -s Lounge3252QA:I Lounge3252QA:W Lounge3252QA:E 2>/dev/null || true)"
  if grep -Fq "QA Live catalogue synchronised" <<< "$logs"; then
    success=1
    break
  fi
  if grep -Fq "No Lounge Xtream playlist found" <<< "$logs"; then
    echo "QA app launched but has no Lounge Xtream playlist to switch."
    exit 1
  fi
  if grep -Eq "QA sync unsuccessful|QA sync failed" <<< "$logs"; then
    echo "QA sync failed. The QA bootstrap restored the previous source URL."
    grep "Lounge3252QA" <<< "$logs" | tail -8
    exit 1
  fi
  sleep 3
done

if [[ "$success" != "1" ]]; then
  echo "APK installed but 3,252-channel Live refresh is not yet confirmed."
  echo "Customer production remains unchanged."
  exit 1
fi

adb -s "$DEVICE" shell am force-stop "$PACKAGE"
adb -s "$DEVICE" shell monkey -p "$PACKAGE" 1 >/dev/null
echo "SUCCESS: 3,252-channel QA catalogue synced; Lounge TV v2qa relaunched."
echo "Production remains unchanged. Verify playback and EPG on Firestick."
