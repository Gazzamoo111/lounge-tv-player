#!/usr/bin/env bash
# Read-only diagnosis of the Lounge TV v2qa 3252 catalogue on Firestick.
# No force-stop, app reset, reinstall, source sync or production changes.
set -uo pipefail
DEVICE="192.168.68.53:5555"
PACKAGE="com.loungetv.player.v2qa"
echo "=== Lounge TV QA 3252 diagnostic ==="
adb -s "$DEVICE" get-state >/dev/null 2>&1 || {
  echo "FAIL: Firestick ADB is not connected at $DEVICE"
  exit 1
}
echo "[APK]"
adb -s "$DEVICE" shell dumpsys package "$PACKAGE" | grep -m1 -E 'versionCode=' || true
echo "[Process]"
adb -s "$DEVICE" shell pidof "$PACKAGE" || echo "Not running"

echo "[Saved QA sync status]"
# Only show boolean keys, not customer credentials or provider URLs.
prefs="$(adb -s "$DEVICE" exec-out run-as "$PACKAGE" cat shared_prefs/loungetv_qa_catalogue_3252.xml 2>/dev/null || true)"
if grep -Eq 'synced_source_[[:digit:]]+.*value="true"' <<< "$prefs"; then
  grep -E 'synced_source_[[:digit:]]+' <<< "$prefs" | sed -E 's/^[[:space:]]*//'
elif grep -q 'synced_source_' <<< "$prefs"; then
  grep -E 'synced_source_[[:digit:]]+' <<< "$prefs" | sed -E 's/^[[:space:]]*//'
else
  echo "No persisted 3252 sync-success marker"
fi

echo "[Database snapshot]"
if ! command -v sqlite3 >/dev/null 2>&1; then
  echo "sqlite3 not installed on the Mac; skipping exact channel count."
else
  dir="$(mktemp -d /tmp/lounge3252-check.XXXXXX)"
  trap 'rm -rf "$dir"' EXIT
  if adb -s "$DEVICE" exec-out run-as "$PACKAGE" cat databases/owntv.db > "$dir/owntv.db" 2>/dev/null &&
     [ -s "$dir/owntv.db" ]; then
    # SQLite may have uncheckpointed Live changes in WAL. Copy both WAL and
    # SHM if available so we don't incorrectly label an in-flight sync as old.
    for suffix in -wal -shm; do
      adb -s "$DEVICE" exec-out run-as "$PACKAGE" cat "databases/owntv.db$suffix" \
        > "$dir/owntv.db$suffix" 2>/dev/null || rm -f "$dir/owntv.db$suffix"
    done
    if ! sqlite3 "$dir/owntv.db" <<'SQL'
SELECT 'Live sources=' || COUNT(*) FROM sources;
SELECT 'QA sources=' || COUNT(*) FROM sources
  WHERE url LIKE '%/lounge-core-xtream-qa-3252%';
SELECT 'QA Live channel rows=' || COUNT(*) FROM channels
  WHERE sourceId IN (SELECT id FROM sources
     WHERE url LIKE '%/lounge-core-xtream-qa-3252%');
SELECT 'Other Live channel rows=' || COUNT(*) FROM channels
  WHERE sourceId NOT IN (SELECT id FROM sources
     WHERE url LIKE '%/lounge-core-xtream-qa-3252%');
SQL
    then
      echo "Database was being written during the read-only copy; count inconclusive."
    fi
  else
    echo "Could not access app's debug database with run-as; count inconclusive."
  fi
fi
echo "[QA and sync error logs]"
adb -s "$DEVICE" logcat -d -v brief 2>/dev/null \
  | grep -Ei 'Lounge3252QA|sync wrapper (start|end)|QA sync failed|AndroidRuntime|FATAL EXCEPTION' \
  | tail -35 || true
echo "=== Done; no app settings or customer data changed ==="
