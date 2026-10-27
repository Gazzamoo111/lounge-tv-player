#!/usr/bin/env bash
# Lounge TV read-only Live TV audit. No changes to the device or provider.
set -euo pipefail
TV="192.168.68.53:5555"
if [ "$#" -gt 0 ]; then TV="$1"; fi
PACKAGE="com.loungetv.player.recoveryqa"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$HOME/Downloads/LoungeTV-LiveAudit-$STAMP"
TMP="$(mktemp -d -t lounge-live-audit)"
trap 'rm -rf "$TMP"' EXIT

echo "=== CONNECT TO FIRE STICK ==="
adb connect "$TV" </dev/null
echo "=== CHECK READ ACCESS ==="
FILES="$(adb -s "$TV" shell run-as "$PACKAGE" ls databases </dev/null | tr -d '\r')"
if ! printf '%s\n' "$FILES" | grep -Fxq 'owntv.db'; then
  echo "STOP: Recovery QA database not found/readable. Nothing changed."
  exit 1
fi

echo "=== COPY TEMPORARY DATABASE SNAPSHOT ==="
adb -s "$TV" exec-out run-as "$PACKAGE" cat databases/owntv.db </dev/null > "$TMP/owntv.db"
test -s "$TMP/owntv.db" || { echo "STOP: Database copy empty."; exit 1; }
for SUFFIX in '-wal' '-shm' '-journal'; do
  if printf '%s\n' "$FILES" | grep -Fxq "owntv.db$SUFFIX"; then
    adb -s "$TV" exec-out run-as "$PACKAGE" cat "databases/owntv.db$SUFFIX" </dev/null > "$TMP/owntv.db$SUFFIX"
  fi
done

mkdir -p "$OUT"
echo "=== GENERATE SANITISED CSV AUDIT ==="
python3 - "$TMP/owntv.db" "$OUT" <<'PY'
import csv, re, sqlite3, sys
from collections import Counter
from pathlib import Path

db, folder = Path(sys.argv[1]), Path(sys.argv[2])
con = sqlite3.connect(db.resolve().as_uri() + '?mode=ro', uri=True)
con.execute('PRAGMA query_only = ON')
tables = {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")}
if not {'sources', 'categories', 'channels'} <= tables:
    raise SystemExit('STOP: Catalogue tables unavailable. No data changed.')

categories = con.execute("""
    SELECT s.name, s.type, c.id, c.name, COUNT(ch.id)
    FROM categories c
    JOIN sources s ON s.id = c.sourceId
    LEFT JOIN channels ch ON ch.categoryId = c.id
    WHERE c.mediaType = 'LIVE' OR EXISTS
        (SELECT 1 FROM channels ch2 WHERE ch2.categoryId = c.id)
    GROUP BY c.id
    ORDER BY s.name COLLATE NOCASE, c.name COLLATE NOCASE
""").fetchall()
channels = con.execute("""
    SELECT s.name, s.type, COALESCE(c.name, '(Uncategorised)'),
           ch.name, COALESCE(ch.epgChannelId, ''),
           COALESCE(ch.number, ''),
           CASE WHEN ch.drmConfig IS NULL OR ch.drmConfig = '' THEN 0 ELSE 1 END,
           CASE WHEN ch.logoUrl IS NULL OR ch.logoUrl = '' THEN 0 ELSE 1 END
    FROM channels ch
    JOIN sources s ON s.id = ch.sourceId
    LEFT JOIN categories c ON c.id = ch.categoryId
    ORDER BY s.name COLLATE NOCASE, c.name COLLATE NOCASE, ch.sortOrder, ch.name COLLATE NOCASE
""").fetchall()
con.close()

def flag(name, count):
    n = name.casefold()
    if re.search(r'\b(sky\s*sports?|sky\s*sp|nz\s*raw)\b', n):
        return 'PROTECT_NZ_SKY'
    if re.search(r'\b(adult|xxx|porn|erotic|18\+)\b', n):
        return 'REVIEW_HIDE_ADULT'
    if re.search(r'\b(religious|religion|biblical|bible|church|christian|gospel|islamic|quran|faith)\b', n):
        return 'REVIEW_HIDE_RELIGIOUS'
    if re.search(r'\b(ppv|pay.per.view|events?\s*ppv|24/?7\s*ppv)\b', n):
        return 'REVIEW_HIDE_PPV'
    if count < 5:
        return 'REVIEW_UNDER_5'
    if count < 10:
        return 'REVIEW_UNDER_10'
    if re.search(r'\b(turkey|turkish|india|indian|pakistan|urdu|arabic|hindi|punjabi|spanish|french|russian|chinese|korean)\b', n):
        return 'REVIEW_FOREIGN_LANGUAGE'
    return 'KEEP_REVIEW'

def safe(v):
    s = '' if v is None else str(v)
    return "'" + s if s.startswith(('=', '+', '-', '@')) else s

category_path = folder / 'LiveTV-Categories.csv'
channel_path = folder / 'LiveTV-Channels.csv'
with category_path.open('w', newline='', encoding='utf-8-sig') as f:
    w = csv.writer(f)
    w.writerow(['Source', 'Source type', 'Category ID', 'Provider category', 'Channel count', 'Initial review flag'])
    for source, typ, cid, name, count in categories:
        w.writerow([safe(source), safe(typ), cid, safe(name), count, flag(name, count)])

with channel_path.open('w', newline='', encoding='utf-8-sig') as f:
    w = csv.writer(f)
    w.writerow(['Source', 'Source type', 'Provider category', 'Channel name', 'EPG ID', 'Channel number', 'Uses DRM', 'Has logo'])
    for row in channels:
        w.writerow([safe(v) for v in row])

flags = Counter(flag(name, count) for _, _, _, name, count in categories)
sources = Counter(source for source, *_ in channels)
print(f'Categories: {len(categories)}')
print(f'Channels: {len(channels)}')
print('Sources:')
for k,v in sources.most_common(): print(f'  {k}: {v} channels')
print('Category review flags:')
for k,v in sorted(flags.items()): print(f'  {k}: {v}')
print('NZ five detected in channel names (not playback proof):')
patterns = [
  ('TVNZ 1', r'\btvnz\s*1\b'),
  ('TVNZ 2', r'\btvnz\s*2\b'),
  ('Three', r'\bthree\b|^nz\s*\|\s*tv\s*3\b'),
  ('Whakaata Māori', r'\bwhakaata\b|\bmaori\s*tv\b'),
  ('Te Reo', r'\bte\s*reo\b')
]
for label, pat in patterns:
    matches = [(r[0], r[3]) for r in channels if re.search(pat, r[3], re.I)]
    print(f'  {label}: {len(matches)} candidates')
print(f'CSV categories: {category_path}')
print(f'CSV channels: {channel_path}')
print('No source URLs, login details, streams or DRM licence details exported.')
PY

echo "=== AUDIT COMPLETE: NO APP CHANGES ==="
echo "Upload both CSV files from $OUT"
