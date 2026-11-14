#!/usr/bin/env bash
# Lounge TV 2.0 sports NBA QA: exact PR #68 build only, no extra workflow.
# Keeps existing 3252 channel DB, login, saved settings, favourites, VOD and EPG.
set -euo pipefail
REPO="Gazzamoo111/lounge-tv-player"
RUN_ID="43539492439"
EXPECTED_SHA="631ab3d8461193a1668479c2ff78f57dda401d3a"
PACKAGE="com.loungetv.player.v2qa"
DEVICE="192.168.68.53:5555"

for cmd in gh adb; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "ERROR: missing $cmd"; exit 1; }
done

echo "Checking the exact sports-fix QA build..."
meta="$(gh run view "$RUN_ID" -R "$REPO" --json headSha,event,status,conclusion \
  --jq '[.headSha,.event,.status,.conclusion] | join(" ")')"
if [[ "$meta" != "$EXPECTED_SHA pull_request completed success" ]]; then
  echo "ERROR: Sports QA build not verified yet: $meta"
  echo "No change made to the Firestick."
  exit 1
fi

adb connect "$DEVICE"
if ! adb devices | grep -Eq "^$DEVICE[[:space:]]+device$"; then
  echo "ERROR: Firestick ADB unavailable at $DEVICE"; exit 1
fi
if ! adb -s "$DEVICE" shell pm list packages "$PACKAGE" | grep -Fq "package:$PACKAGE"; then
  echo "ERROR: Existing v2qa app not installed. Nothing will be removed."; exit 1
fi

tmp="$(mktemp -d /tmp/loungesportsqa.XXXXXX)"
trap 'rm -rf "$tmp"' EXIT

gh run download "$RUN_ID" -R "$REPO" -n lounge-tv-2-v2qa -D "$tmp"
apk="$(find "$tmp" -type f -name '*.apk' -print -quit)"
if [[ -z "$apk" || ! -s "$apk" ]]; then
  echo "ERROR: Missing signed APK. No install attempted."; exit 1
fi

echo "Updating installed QA app without clearing its data..."
if ! adb -s "$DEVICE" install -r "$apk"; then
  echo "ERROR: Android refused the update. Existing app and data preserved."
  exit 1
fi

adb -s "$DEVICE" shell am force-stop "$PACKAGE"
adb -s "$DEVICE" shell monkey -p "$PACKAGE" 1 >/dev/null
echo "SUCCESS: NBA discovery guard and new NFL source are now available in Lounge TV QA."
echo "The 3252 Live catalogue, EPG and saved data were not reset."
echo "Note: provider inventory is currently stale; no game is claimed unless its feed is evidenced."
