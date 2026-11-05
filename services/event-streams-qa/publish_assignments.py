"""Publish a short-lived, read-only fixture TV assignment snapshot from the existing QA collector.
No provider data, viewer credentials, external secrets, or Supabase writes are included.
"""
import datetime as dt
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
INPUT = ROOT / "outputs/event-streams-qa/evidence.json"
OUTPUT = ROOT / "backend/supabase/functions/lounge-sports-candidates-qa/data/espn-assignments.json"
raw = json.loads(INPUT.read_text())
now = dt.datetime.now(dt.timezone.utc)
collection = dt.datetime.fromisoformat(raw["collectedAtUtc"].replace("Z", "+00:00"))
if not dt.timedelta(0) <= now - collection <= dt.timedelta(minutes=30):
    raise SystemExit("QA fixture evidence is stale, refusing to publish")

fixtures = []
for item in raw.get("fixtures", []):
    src = str(item.get("fixtureSource", ""))
    if not src.startswith(("https://site.api.espn.com/apis/site/v2/sports/",
                           "https://site.web.api.espn.com/apis/v2/scoreboard/header")):
        continue
    participants = item.get("participants") or []
    if len(participants) != 2 or not all(p.get("name") for p in participants):
        continue
    geo = [
        {"region": b["region"], "type": {"shortName": "TV"},
         "media": {"shortName": b["media"]["shortName"]}}
        for b in item.get("geoBroadcasts", [])
        if str(b.get("type", {}).get("shortName", "")).upper() == "TV"
        and b.get("region") and b.get("media", {}).get("shortName")
    ]
    fixtures.append({
        "eventId": item["eventId"],
        "competitionId": item["competitionId"],
        "scheduledStartUtc": item["scheduledStartUtc"],
        "participants": [
            {"name": p["name"], "aliases": p.get("aliases", [])[:6]}
            for p in participants
        ],
        "geoBroadcasts": geo,
        "source": src,
    })

if len(fixtures) < 4:
    raise SystemExit("Insufficient ESPN fixture evidence; will not overwrite last good QA snapshot")
payload = {
    "contractVersion": 1,
    "collectedAtUtc": raw["collectedAtUtc"],
    "expiresAtUtc": (collection + dt.timedelta(hours=2)).isoformat().replace("+00:00", "Z"),
    "fixtureCount": len(fixtures),
    "fixtures": fixtures,
}
OUTPUT.parent.mkdir(parents=True, exist_ok=True)
OUTPUT.write_text(json.dumps(payload, separators=(",", ":")) + "\n")
print(json.dumps({"publishedQAOnly": True, "fixtureCount": len(fixtures),
                  "linearTvAssignments": sum(len(f["geoBroadcasts"]) for f in fixtures),
                  "expiresAtUtc": payload["expiresAtUtc"]}))
