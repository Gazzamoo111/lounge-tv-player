#!/usr/bin/env bash
# Lounge TV 2.0 | PR-cache-signed QA APK, Firestick update, isolated Live refresh.
# Manual workflow_dispatch cannot sign because QA secrets are absent.
# The PR #68 test workflow verifies a pinned QA certificate. Fail closed.
set -euo pipefail

REPO="Gazzamoo111/lounge-tv-player"
BRANCH="feature/lounge-tv-2.0"
PR_NUMBER="68"
WORKFLOW="ci.yml"
TARGET="192.168.68.53:5555"
PACKAGE="com.loungetv.player.v2qa"
ARTIFACT="lounge-tv-2-v2qa"
GATEWAY="https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-core-xtream-qa-3252/player_api.php"

for cmd in gh git adb curl; do
  command -v "$cmd" >/dev/null 2>&1 || {
    echo "ERROR: Missing required command: $cmd"; exit 1;
  }
done
gh auth status >/dev/null 2>&1 || {
  echo "ERROR: Please authenticate the GitHub CLI first using gh auth login."; exit 1;
}

echo "Connecting to Firestick $TARGET..."
adb connect "$TARGET"
if ! adb devices | grep -Eq "^$TARGET[[:space:]]+device$"; then
  echo "ERROR: Firestick is not accessible via ADB."; exit 1
fi
if ! adb shell pm list packages "$PACKAGE" | grep -Fq "package:$PACKAGE"; then
  echo "ERROR: Existing QA package not found. Won't remove or overwrite another app."; exit 1
fi
code="$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 8 --max-time 20 "$GATEWAY?action=get_live_categories" || true)"
if [[ "$code" != "401" ]]; then
  echo "ERROR: QA gateway did not return the expected authentication response: $code."
  exit 1
fi

pr_head="$(gh pr view "$PR_NUMBER" -R "$REPO" --json headRefName,state --jq '.headRefName + " " + .state')"
if [[ "$pr_head" != "$BRANCH OPEN" ]]; then
  echo "ERROR: QA PR #68 is not open with the expected head: $pr_head"
  exit 1
fi

workdir="$(mktemp -d /tmp/lounge3252-pr.XXXXXX)"
trap 'rm -rf "$workdir"' EXIT

echo "Cloning corrected QA source..."
gh repo clone "$REPO" "$workdir/source" -- --branch "$BRANCH" --single-branch --depth 1
last_id="$(gh run list -R "$REPO" -w "$WORKFLOW" --branch "$BRANCH" --event pull_request -L 10 --json databaseId --jq 'map(.databaseId) | max // 0')"
if [[ -z "$last_id" ]]; then last_id=0; fi

# A real user-authenticated git push creates pull_request.synchronize.
# Unlike workflow_dispatch, that event uses the PR-scoped cached debug signing
# identity. This harmless empty commit does not alter source or the draft PR.
login="$(gh api user --jq .login)"
cd "$workdir/source"
git -c user.name="$login" -c user.email="$login@users.noreply.github.com" \
  commit --allow-empty -m "ci: rebuild Lounge TV 3252 Firestick QA using verified PR signer"
echo "Triggering signing-compatible PR #68 QA workflow..."
git push origin "HEAD:$BRANCH"
cd "$workdir"

run_id=""
for attempt in $(seq 1 90); do
  candidate="$(gh run list -R "$REPO" -w "$WORKFLOW" --branch "$BRANCH" --event pull_request -L 10 --json databaseId --jq 'map(.databaseId) | max // 0' || true)"
  if [[ "$candidate" =~ ^[0-9]+$ ]] && (( candidate > last_id )); then
    run_id="$candidate"; break
  fi
  sleep 2
done
if [[ -z "$run_id" ]]; then
  echo "ERROR: A new PR QA workflow did not start. Nothing was installed."; exit 1
fi

echo "Monitoring QA run $run_id..."
gh run watch "$run_id" -R "$REPO" --exit-status || {
  echo "ERROR: QA tests, signing check or compilation failed."
  echo "Inspect https://github.com/$REPO/actions/runs/$run_id"
  echo "Existing Firestick installation is untouched."
  exit 1
}

mkdir -p "$workdir/apk"
echo "Downloading certificate-checked QA APK..."
gh run download "$run_id" -R "$REPO" -n "$ARTIFACT" -D "$workdir/apk"
apk="$(find "$workdir/apk" -type f -name '*.apk' -print -quit)"
if [[ -z "$apk" || ! -s "$apk" ]]; then
  echo "ERROR: No QA APK artifact found. Nothing installed."; exit 1
fi

# CI explicitly checks package com.loungetv.player.v2qa and certificate
# 4c398a1c5614b1975ba5c64bb756125e7c39abf6b0193b3e30e0b14ad1c3949b.
# adb install -r also verifies against the currently installed APK.
echo "Updating the existing v2qa package without uninstalling..."
if ! adb install -r "$apk"; then
  echo "ERROR: Android refused the update (check certificate/version)."
  echo "No uninstall, reset or data deletion attempted."
  exit 1
fi

echo "Starting the 3,252-channel QA Live sync..."
adb logcat -c || true
adb shell am force-stop "$PACKAGE"
adb shell monkey -p "$PACKAGE" 1 >/dev/null

success=0
for attempt in $(seq 1 70); do
  logs="$(adb logcat -d -s Lounge3252QA:I Lounge3252QA:W Lounge3252QA:E 2>/dev/null || true)"
  if grep -Fq 'QA Live catalogue synchronised' <<< "$logs"; then
    success=1; break
  fi
  if grep -Fq 'No Lounge Xtream playlist found' <<< "$logs"; then
    echo "ERROR: Existing Lounge Xtream source not found. Nothing was deleted."
    exit 1
  fi
  if grep -Eq 'QA sync unsuccessful|QA sync failed' <<< "$logs"; then
    echo "ERROR: New Live sync failed; existing source URL was restored."
    grep 'Lounge3252QA' <<< "$logs" | tail -8
    exit 1
  fi
  sleep 3
done
if (( success != 1 )); then
  echo "ERROR: APK installed but new Live sync was not confirmed."
  echo "Customer production remains untouched."
  exit 1
fi

adb shell am force-stop "$PACKAGE"
adb shell monkey -p "$PACKAGE" 1 >/dev/null
echo "SUCCESS: Lounge TV QA restarted with 3,252-channel isolated Live catalogue."
echo "Movies, series and the customer production release remain unchanged."
