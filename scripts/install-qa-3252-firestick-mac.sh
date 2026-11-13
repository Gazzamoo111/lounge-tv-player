#!/usr/bin/env bash
# Lounge TV | single-command QA 3252 build, install, refresh and relaunch.
# Keep installed v2qa data; never uninstall on version/signature mismatch.
set -euo pipefail

REPO="Gazzamoo111/lounge-tv-player"
BRANCH="feature/lounge-tv-2.0"
WORKFLOW="ci.yml"
TARGET="192.168.68.53:5555"
PACKAGE="com.loungetv.player.v2qa"
ARTIFACT="lounge-tv-2-v2qa"
TARGET_RELEASE="live-3252-20261114-qa-draft"

for cmd in gh adb; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "ERROR: '$cmd' is not available on this Mac."
    exit 1
  fi
done

if ! gh auth status >/dev/null 2>&1; then
  echo "ERROR: GitHub CLI is not signed in. Run 'gh auth login'."
  exit 1
fi

echo "Connecting to Firestick at $TARGET..."
adb connect "$TARGET"
if ! adb devices | grep -Eq "^$TARGET[[:space:]]+device$"; then
  echo "ERROR: Firestick is not accepting ADB at $TARGET. Check its IP and ADB debugging."
  exit 1
fi
if ! adb shell pm list packages "$PACKAGE" | grep -q "package:$PACKAGE"; then
  echo "ERROR: Existing v2qa package not found. Refusing to overwrite another app."
  exit 1
fi

# The QA gateway must be responding BEFORE installing a build that switches to it.
# HTTPS failure is a hard stop, not a reason to change production.
GATEWAY="https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-core-xtream-qa-3252/player_api.php"
if command -v curl >/dev/null 2>&1; then
  code="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 8 --max-time 20 "$GATEWAY?action=get_live_categories" || true)"
  if [[ "$code" != "401" ]]; then
    echo "ERROR: Isolated QA gateway unexpected status '$code' (expected 401 without credentials)."
    exit 1
  fi
fi

echo "Requesting the current QA APK build from GitHub Actions..."
previous="$(gh run list -R "$REPO" -w "$WORKFLOW" --branch "$BRANCH" --event workflow_dispatch -L 1 --json databaseId --jq '.[0].databaseId // 0')"
previous="${previous:-0}"
gh workflow run "$WORKFLOW" -R "$REPO" --ref "$BRANCH"

run_id=""
for attempt in $(seq 1 45); do
  candidate="$(gh run list -R "$REPO" -w "$WORKFLOW" --branch "$BRANCH" --event workflow_dispatch -L 1 --json databaseId --jq '.[0].databaseId // 0' || true)"
  candidate="${candidate:-0}"
  if [[ "$candidate" =~ ^[0-9]+$ ]] && (( candidate > previous )); then
    run_id="$candidate"
    break
  fi
  sleep 2
done

if [[ -z "$run_id" ]]; then
  echo "ERROR: Could not identify the new GitHub build; no installation attempted."
  exit 1
fi
echo "QA workflow run: $run_id"
gh run watch "$run_id" -R "$REPO" --exit-status

workdir="$(mktemp -d "${TMPDIR:-/tmp}/loungetv3252.XXXXXX")"
trap 'rm -rf "$workdir"' EXIT
gh run download "$run_id" -R "$REPO" -n "$ARTIFACT" -D "$workdir"
apk="$(find "$workdir" -type f -name '*.apk' -print -quit)"
if [[ -z "$apk" || ! -s "$apk" ]]; then
  echo "ERROR: Build completed but no APK was downloaded."
  exit 1
fi

echo "Installing signed QA APK without removing existing data..."
if ! adb install -r "$apk"; then
  echo "INSTALLATION FAILED. The existing app and its saved data have NOT been uninstalled."
  exit 1
fi

echo "Starting Lounge TV QA. It will switch the existing Lounge Xtream source"
echo "to the isolated 3,252-channel QA release and refresh Live channels only."
adb logcat -c || true
adb shell am force-stop "$PACKAGE"
adb shell monkey -p "$PACKAGE" 1 >/dev/null

ok=0
for attempt in $(seq 1 70); do
  log="$(adb logcat -d -s Lounge3252QA:I Lounge3252QA:W Lounge3252QA:E 2>/dev/null || true)"
  if echo "$log" | grep -q 'QA Live catalogue synchronised'; then
    ok=1
    break
  fi
  if echo "$log" | grep -q 'No Lounge Xtream playlist found'; then
    echo "BLOCKED: The app has no existing Lounge Xtream account to switch."
    echo "No account or favourite was deleted. Review the existing playlist URL."
    exit 1
  fi
  if echo "$log" | grep -qE 'QA sync unsuccessful|QA sync failed'; then
    echo "QA Live sync failed. Existing source URL was restored."
    echo "$log" | grep 'Lounge3252QA' | tail -5
    exit 1
  fi
  sleep 3
done

if (( ok == 0 )); then
  echo "The QA APK installed and launched, but automatic Live refresh is not confirmed."
  echo "No customer production release was changed. Inspect the Firestick before publishing."
  exit 1
fi

echo "Live catalogue refreshed successfully. Relaunching to clear the old screen state..."
adb shell am force-stop "$PACKAGE"
adb shell monkey -p "$PACKAGE" 1 >/dev/null
echo "SUCCESS: Lounge TV QA is running the 3,252-channel draft ($TARGET_RELEASE)."
echo "Movies and series retain their previous content; customer production is unchanged."
