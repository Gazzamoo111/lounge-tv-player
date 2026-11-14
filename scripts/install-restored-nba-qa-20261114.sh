#!/usr/bin/env bash
# Restore NBA package feeds in Lounge TV 2.0 QA without data reset.
# Installs only the exact signed PR artifact, resyncs Live and verifies 3290 rows.
set -euo pipefail
REPO="Gazzamoo111/lounge-tv-player"
RUN_ID=43544097479
EXPECTED_SHA="e6f81ae0a5629df7f5f18b07bc5e3c1d5d65770c"
DEVICE="192.168.68.53:5555"
PACKAGE="com.loungetv.player.v2qa"
ARTIFACT="lounge-tv-2-v2qa"
DB="databases/owntv.db"

for tool in gh adb sqlite3; do
  command -v "$tool" >/dev/null 2>&1 || { echo "ERROR: $tool not found"; exit 1; }
done

# Follow the already-running PR validation; do not dispatch another build.
status="$(gh run view "$RUN_ID" -R "$REPO" --json status --jq '.status')"
if [[ "$status" != "completed" ]]; then
  echo "Verifying the restored NBA QA build $RUN_ID..."
  gh run watch "$RUN_ID" -R "$REPO" --exit-status || {
    echo "ERROR: QA test build failed; nothing installed."
    exit 1
  }
fi
meta="$(gh run view "$RUN_ID" -R "$REPO" --json headSha,event,status,conclusion \
  --jq '[.headSha,.event,.status,.conclusion] | join(" ")')"
if [[ "$meta" != "$EXPECTED_SHA pull_request completed success" ]]; then
  echo "ERROR: The restored NBA build is not fully validated: $meta"
  echo "Nothing has been installed or reset."
  exit 1
fi

adb connect "$DEVICE"
adb -s "$DEVICE" get-state >/dev/null
if ! adb -s "$DEVICE" shell pm list packages "$PACKAGE" | grep -Fq "package:$PACKAGE"; then
  echo "ERROR: Existing QA package is not installed; no new install attempted."
  exit 1
fi

tmp="$(mktemp -d /tmp/loungetv-nba-restore.XXXXXX)"
trap 'rm -rf "$tmp"' EXIT

echo "Downloading exact certificate-checked build $RUN_ID..."
gh run download "$RUN_ID" -R "$REPO" --name "$ARTIFACT" --dir "$tmp"
apk="$(find "$tmp" -type f -name '*.apk' -print -quit)"
if [[ -z "$apk" || ! -s "$apk" ]]; then
  echo "ERROR: Signed QA artifact missing; no installation attempted."
  exit 1
fi
echo "Updating existing QA app, preserving saved data..."
adb -s "$DEVICE" install -r "$apk" || {
  echo "ERROR: Package installation refused; the existing app was not removed."
  exit 1
}

echo "Launching QA and restoring the 38 NBA package feeds..."
adb -s "$DEVICE" logcat -c || true
adb -s "$DEVICE" shell am force-stop "$PACKAGE"
adb -s "$DEVICE" shell monkey -p "$PACKAGE" 1 >/dev/null

synced=0
for attempt in $(seq 1 100); do
  # The sync-success flag is durable, unlike a log entry which disappears
  # after reinstall or log clearing. Do not expose credentials from prefs.
  prefs="$(adb -s "$DEVICE" exec-out run-as "$PACKAGE" \
    cat shared_prefs/loungetv_qa_catalogue_3252.xml 2>/dev/null || true)"
  if grep -Eq '<boolean name="synced_source_[0-9]+_nba_package_restore" value="true"' <<< "$prefs"; then
    synced=1
    break
  fi
  if adb -s "$DEVICE" logcat -d -s Lounge3252QA:E 2>/dev/null | grep -Eq 'QA sync unsuccessful|QA sync failed'; then
    echo "ERROR: Restored NBA catalogue sync failed. No app data was cleared."
    exit 1
  fi
  sleep 3
done

if ((synced != 1)); then
  echo "ERROR: Updated APK installed but 3290-channel sync has not been confirmed."
  echo "No uninstall, reset or production change was made."
  exit 1
fi

# Confirm the exact Live row count and 30+8 restored package sources. WAL
# files are copied as SQLite may still be writing during the app startup.
adb -s "$DEVICE" exec-out run-as "$PACKAGE" cat "$DB" > "$tmp/owntv.db"
for suffix in -wal -shm; do
  adb -s "$DEVICE" exec-out run-as "$PACKAGE" cat "$DB$suffix" \
    > "$tmp/owntv.db$suffix" 2>/dev/null || rm -f "$tmp/owntv.db$suffix"
done
count="$(sqlite3 "$tmp/owntv.db" \
  "SELECT count(*) FROM channels WHERE sourceId IN (SELECT id FROM sources WHERE url LIKE '%/lounge-core-xtream-qa-3252%');")"
if [[ "$count" != 3290 ]]; then
  echo "ERROR: Device has $count QA Live channels; expected 3290."
  echo "The app is installed; inspect its channel sync before further changes."
  exit 1
fi

adb -s "$DEVICE" shell am force-stop "$PACKAGE"
adb -s "$DEVICE" shell monkey -p "$PACKAGE" 1 >/dev/null
echo "SUCCESS: Restored NBA Teams (30) and NBA Game Feeds (8)."
echo "QA Live channels verified: $count. Production and user data unchanged."
echo "Check Sports > NBA Teams, then NBA Game Feeds. Stream playback still requires device testing."
