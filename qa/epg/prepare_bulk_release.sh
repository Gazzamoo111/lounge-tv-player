#!/usr/bin/env bash
set -euo pipefail
umask 077

run="$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
package="${RESUME_PACKAGE:-$run}"
[[ "$package" =~ ^[1-9][0-9]{5,15}-[1-9][0-9]{0,3}$ ]]
authorization="Authorization: Bearer $LOUNGE_QA_OIDC"
request() {
  curl --silent --show-error --max-time 130 -H "$authorization" "$@"
}
target() {
  printf '%s/%s?run=%s' "$BROKER_URL" "$1" "${2:-$package}"
}

mkdir -p "$WORK"
request --fail "$BROKER_URL/bulk-release-status" |
  python -c 'import json,sys; assert json.load(sys.stdin)["international_release_enabled"] is False'
if [ -n "${RESUME_PACKAGE:-}" ]; then
  mkdir -p "$WORK/candidate"
  request --fail -D "$WORK/international.headers" "$BROKER_URL/bulk-baseline" -o "$WORK/international-before.xml.gz"
  request --fail "$(target bulk-stage-international-rollback)" -o "$WORK/private-backup"
  cmp "$WORK/international-before.xml.gz" "$WORK/private-backup"
  request --fail "$(target bulk-stage-international)" -o "$WORK/candidate/international-master.xml.gz"
  request --fail "$(target bulk-stage-shared)" -o "$WORK/shared.tar.gz"
  printf 'bulk-stage-shared %s SHA-256 %s\n' "$package" "$(sha256sum "$WORK/shared.tar.gz" | cut -d' ' -f1)"
else
  sha256sum "$WORK/canonical.xml.gz" | cut -d' ' -f1 > "$WORK/candidate/canonical.sha256"
  tar -C "$WORK/candidate" -czf "$WORK/shared.tar.gz" guide.xml.gz manifest.json canonical.sha256 short
  mkdir -p "$WORK/shared-preview/epg"
  cp "$WORK/canonical.xml.gz" "$WORK/shared-preview/epg/guide.xml.gz"
  python qa/epg/stage_shared_publication.py "$WORK/shared.tar.gz" \
    "$(sha256sum "$WORK/shared.tar.gz" | cut -d' ' -f1)" "$WORK/shared-preview"
  cmp "$WORK/candidate/guide.xml.gz" "$WORK/shared-preview/epg/bulk/guide.xml.gz"
  cmp "$WORK/candidate/manifest.json" "$WORK/shared-preview/epg/bulk/manifest.json"
  diff -qr "$WORK/candidate/short" "$WORK/shared-preview/epg/bulk/short"

  stage() {
    local endpoint="$1" source="$2"
    request --fail -X POST -H 'Content-Type: application/gzip' \
      --data-binary "@$source" "$(target "$endpoint")" -o /dev/null
    request --fail "$(target "$endpoint")" -o "$WORK/staged-readback"
    cmp "$source" "$WORK/staged-readback"
    printf '%s %s SHA-256 %s\n' "$endpoint" "$run" "$(sha256sum "$source" | cut -d' ' -f1)"
  }
  stage bulk-stage-shared "$WORK/shared.tar.gz"
  stage bulk-stage-international "$WORK/candidate/international-master.xml.gz"
  stage bulk-stage-international-rollback "$WORK/international-before.xml.gz"
fi

rehearsal="$(target bulk-restore-rehearsal "$run")"
version() {
  awk 'tolower($1)=="x-lounge-guide-version:" {gsub(/\r/,"",$2); print $2}' "$1"
}
rehearsal_read() {
  request --fail -D "$WORK/qa.headers" "$rehearsal" -o "$WORK/qa.xml.gz"
  qa_version="$(version "$WORK/qa.headers")"
  [[ "$qa_version" =~ ^[a-f0-9-]{36}$ ]]
}
rehearsal_write() {
  request -X PUT -H 'Content-Type: application/gzip' -H "X-Lounge-Guide-Version: $1" \
    --data-binary "@$2" "$rehearsal" -o /dev/null -w '%{http_code}'
}
test "$(rehearsal_write missing "$WORK/international-before.xml.gz")" = 200
rehearsal_read
cmp "$WORK/international-before.xml.gz" "$WORK/qa.xml.gz"
old_version="$qa_version"
test "$(rehearsal_write "$old_version" "$WORK/candidate/international-master.xml.gz")" = 200
rehearsal_read
cmp "$WORK/candidate/international-master.xml.gz" "$WORK/qa.xml.gz"
test "$old_version" != "$qa_version"
test "$(rehearsal_write "$old_version" "$WORK/international-before.xml.gz")" = 409
printf 'Stale QA version %s rejected with HTTP 409.\n' "$old_version"
test "$(rehearsal_write "$qa_version" "$WORK/international-before.xml.gz")" = 200
rehearsal_read
cmp "$WORK/international-before.xml.gz" "$WORK/qa.xml.gz"

request --fail -D "$WORK/live-after.headers" "$BROKER_URL/bulk-baseline" -o "$WORK/live-after.xml.gz"
test "$(version "$WORK/international.headers")" = "$(version "$WORK/live-after.headers")"
cmp "$WORK/international-before.xml.gz" "$WORK/live-after.xml.gz"
printf 'QA version %s restored with exact backup bytes. Serving object %s retained exact bytes.\n' \
  "$qa_version" "$(version "$WORK/live-after.headers")"
