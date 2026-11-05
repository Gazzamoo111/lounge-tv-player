#!/usr/bin/env python3
"""Targeted rolling 7-day EPG: AU additions + honest 24/7 blocks only.

The published guide is the input of record. Other regions, catalogue rows,
stream IDs, categories, and playback mappings are never changed.
"""
from __future__ import annotations

import argparse
import base64
from collections import defaultdict
from datetime import datetime, timedelta, timezone
import gzip
import hashlib
import json
from pathlib import Path
import re
import unicodedata
from lxml import etree

UTC = timezone.utc
DAYS = 7
ALIAS = {
    "FOX SPORTS 501": "FoxCricket.au",
    "FOX SPORTS 502": "FoxLeague.au",
    "FOX SPORTS 503": "FoxSports503.au",
    "FOX SPORTS 504": "FoxFooty.au",
    "FOX SPORTS 505": "FoxSports505.au",
    "FOX SPORTS 506": "FoxSports506.au",
    "FOX SPORTS 507": "FoxSportsMore.au",
}
# Reviewed Australian broadcaster renames and Sydney guide identifiers.
# The stream names and customer catalogue are not changed.
AU_REVIEWED_IDS = {
    "SBS": ("xmltv-net", "251831auepg.com.au"),
    "SBS Viceland": ("xmltv-net", "431775auepg.com.au"),  # SBS2 since Aug 2026
    "SBS World Movies": ("xmltv-net", "432033auepg.com.au"),
    "SBS Food": ("xmltv-net", "431491auepg.com.au"),
    "10 BOLD": ("xmltv-net", "388609auepg.com.au"),  # 10 Drama since 2025
    "10 Peach": ("xmltv-net", "403769auepg.com.au"), # 10 Comedy since 2025
    "ABC": ("xmltv-net", "431733auepg.com.au"),
    "ABC Comedy": ("xmltv-net", "411316auepg.com.au"),
    "ABC NEWS SYDNEY": ("xmltv-net", "391135auepg.com.au"),
}
IGNORED = re.compile(r"\b(?:4k|uhd|fhd|hevc|hd|sd|50fps|60fps)\b", re.I)


def read_xml(path: str):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rb") as src:
        return etree.parse(src, etree.XMLParser(resolve_entities=False, no_network=True,
                                                  huge_tree=True)).getroot()


def dt(raw: str | None):
    if not raw:
        return None
    m = re.match(r"^(\d{14})(?:\s+([+-]\d{4}))?", raw)
    if not m:
        return None
    try:
        base = datetime.strptime(m.group(1), "%Y%m%d%H%M%S")
        off = m.group(2)
        if not off:
            return base.replace(tzinfo=UTC)
        sign = 1 if off[0] == "+" else -1
        mins = sign * (int(off[1:3])*60 + int(off[3:5]))
        return base.replace(tzinfo=timezone(timedelta(minutes=mins))).astimezone(UTC)
    except ValueError:
        return None


def norm(raw: str):
    s = unicodedata.normalize("NFKD", raw or "").encode("ascii", "ignore").decode()
    s = re.sub(r"^\s*(?:AU|AUS)\s*[|:]\s*", "", s, flags=re.I)
    s = IGNORED.sub("", s)
    return re.sub("[^a-z0-9]", "", s.lower())


def source_index(root, now, end):
    channels = {}
    names = defaultdict(list)
    for c in root.findall("channel"):
        cid = c.get("id") or ""
        if not cid:
            continue
        labels = [x.text.strip() for x in c.findall("display-name") if x.text]
        channels[cid.casefold()] = cid
        for label in labels:
            keys = {norm(label)}
            # Region suffixes from Sydney guides should not defeat an exact
            # broadcaster match. Never apply this to sports feed variants.
            if re.search(r"\s+(Sydney|NSW)$", label, re.I):
                keys.add(norm(re.sub(r"\s+(Sydney|NSW)$", "", label, flags=re.I)))
            for key in keys:
                if key and cid not in names[key]:
                    names[key].append(cid)
    programs = defaultdict(list)
    for p in root.findall("programme"):
        st, stop = dt(p.get("start")), dt(p.get("stop"))
        if st and stop and st < end and stop > now and stop > st:
            programs[p.get("channel")].append((st, stop, p))
    return channels, names, programs


def select_feed(row, feeds, now, end):
    desired = str(row.get("epgId") or "").strip()
    name = str(row.get("name") or "").strip()
    candidates = []
    for feed_name, (ids, names, programs) in feeds.items():
        cid = None
        method = None
        reviewed = AU_REVIEWED_IDS.get(name)
        if reviewed and feed_name == reviewed[0] and reviewed[1].casefold() in ids:
            cid, method = ids[reviewed[1].casefold()], "official-rename-reviewed-id"
        elif desired and desired.casefold() in ids:
            cid, method = ids[desired.casefold()], "exact-id"
        elif name.upper() in ALIAS and ALIAS[name.upper()].casefold() in ids:
            cid, method = ids[ALIAS[name.upper()].casefold()], "reviewed-alias"
        else:
            matching = names.get(norm(name), [])
            if len(matching) == 1:
                cid, method = matching[0], "unique-name"
        if not cid:
            continue
        rows = sorted(programs.get(cid, []), key=lambda e: e[0])
        if not rows:
            continue
        earliest = min(e[0] for e in rows)
        latest = max(e[1] for e in rows)
        # Seven days of *real* schedule is mandatory. Do not synthesize it.
        if earliest > now + timedelta(hours=3) or latest < end:
            continue
        candidates.append((latest, len(rows), feed_name, cid, method, rows))
    if not candidates:
        return None
    # Prefer widest proven coverage, with a deterministic tie break.
    candidates.sort(key=lambda c: (c[0], c[1], c[2]), reverse=True)
    return candidates[0]


def fnv1a(raw: str):
    h = 2166136261
    for b in raw.encode("utf-8"):
        h = (h ^ b) * 16777619 & 0xFFFFFFFF
    return f"{h:08x}"


def b64(raw: str):
    return base64.b64encode((raw or "").encode("utf-8")).decode("ascii")


def short_file(root, cid, now, path):
    entries = []
    seen = set()
    for p in root.findall("programme"):
        if p.get("channel") != cid:
            continue
        st, stop = dt(p.get("start")), dt(p.get("stop"))
        if not st or not stop or stop <= now - timedelta(minutes=15) or stop <= st:
            continue
        title = (p.findtext("title") or "").strip()
        desc = (p.findtext("desc") or "").strip()
        marker = (int(st.timestamp()), int(stop.timestamp()), title)
        if marker in seen:
            continue
        seen.add(marker)
        entries.append({
            "id": f"{cid}:{int(st.timestamp())}", "epg_id": cid, "title": b64(title),
            "lang": "en", "start": st.strftime("%Y-%m-%d %H:%M:%S"),
            "end": stop.strftime("%Y-%m-%d %H:%M:%S"),
            "description": b64(desc), "channel_id": cid,
            "start_timestamp": int(st.timestamp()), "stop_timestamp": int(stop.timestamp()),
            "now_playing": int(st <= now < stop), "has_archive": 0,
        })
    entries.sort(key=lambda r: r["start_timestamp"])
    path.write_text(json.dumps({"epg_id": cid, "epg_listings": entries},
                               separators=(",", ":"), ensure_ascii=False) + "\n")
    return len(entries)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--manifest", required=True)
    p.add_argument("--existing", required=True)
    p.add_argument("--au", action="append", default=[], help="name=local_XMLTV_path")
    p.add_argument("--out", required=True)
    p.add_argument("--now", help="ISO8601 UTC time for deterministic testing")
    args = p.parse_args()
    now = (datetime.fromisoformat(args.now.replace("Z", "+00:00"))
           if args.now else datetime.now(UTC)).astimezone(UTC)
    end = now + timedelta(days=DAYS)
    rows = json.loads(Path(args.manifest).read_text())["rows"]
    au_rows = [x for x in rows if x.get("category") == "Australia"]
    loop_rows = [x for x in rows if x.get("category") == "24/7"]
    root = read_xml(args.existing)
    # Prune only expired synthetic loop markers; preserve all real programme listings.
    loop_ids = {str(r.get("epgId") or r["key"]).strip() for r in loop_rows}
    for p in list(root.findall("programme")):
        if p.get("channel") not in loop_ids:
            continue
        stop = dt(p.get("stop"))
        if (stop and stop < now - timedelta(hours=4) and
                (p.findtext("title") or "").startswith("24/7 • ")):
            root.remove(p)
    before = {etree.tostring(p) for p in root.findall("programme")}
    existing_channels = {c.get("id") for c in root.findall("channel")}
    existing = defaultdict(list)
    for p in root.findall("programme"):
        st, stop = dt(p.get("start")), dt(p.get("stop"))
        if st and stop and stop > now and stop > st:
            existing[p.get("channel")].append((st, stop))
    feeds = {}
    errors = {}
    for entry in args.au:
        name, path = entry.split("=", 1)
        try:
            feeds[name] = source_index(read_xml(path), now, end)
        except Exception as exc:
            errors[name] = str(exc)
    if not feeds:
        raise SystemExit("No Australian source could be loaded. No publication.")
    for label, (ids, names, programmes) in feeds.items():
        horizon = max((max(item[1] for item in items)
                       for items in programmes.values() if items), default=None)
        print("AU_FEED_SUMMARY", json.dumps({
            "feed": label, "sourceIds": len(ids), "programmeChannelIds": len(programmes),
            "latest": horizon.isoformat() if horizon else None,
            "sample": [{
                "sourceId": cid, "names": [k for k, v in names.items() if cid in v][:2],
                "end": max((p[1] for p in programmes.get(cid, [])), default=now).isoformat()
            } for cid in list(ids.values())[:100]]
        }))

    changed = set()
    au_added = 0
    au_status = {}
    au_rejections = {}
    for row in au_rows:
        target = str(row.get("epgId") or row["key"]).strip()
        choice = select_feed(row, feeds, now, end)
        if not choice:
            au_status[row["name"]] = "no-seven-day-source"
            possible = []
            for feed_name, (ids, names, programs) in feeds.items():
                desired = str(row.get("epgId") or "").strip()
                match_id = None
                if desired and desired.casefold() in ids:
                    match_id = ids[desired.casefold()]
                elif row["name"].upper() in ALIAS and ALIAS[row["name"].upper()].casefold() in ids:
                    match_id = ids[ALIAS[row["name"].upper()].casefold()]
                elif len(names.get(norm(row["name"]), [])) == 1:
                    match_id = names[norm(row["name"])][0]
                if match_id and programs.get(match_id):
                    entries = programs[match_id]
                    first = min(p[0] for p in entries)
                    last = max(p[1] for p in entries)
                    possible.append({
                        "feed": feed_name, "sourceId": match_id, "programmes": len(entries),
                        "first": first.isoformat(), "last": last.isoformat(),
                        "daysAhead": round((last-now).total_seconds()/86400, 2),
                    })
            au_rejections[row["name"]] = possible
            continue
        _, _, feed, src_id, method, programs = choice
        intervals = existing[target][:]
        count = 0
        for st, stop, original in programs:
            if any(st < other_end and stop > other_start for other_start, other_end in intervals):
                continue
            node = etree.fromstring(etree.tostring(original))
            node.set("channel", target)
            root.append(node)
            intervals.append((st, stop))
            count += 1
        if count:
            if target not in existing_channels:
                c = etree.Element("channel", id=target)
                etree.SubElement(c, "display-name").text = row["name"]
                root.insert(0, c)
                existing_channels.add(target)
            changed.add(target)
            au_added += count
        au_status[row["name"]] = {
            "source": feed, "sourceId": src_id, "match": method,
            "added": count, "latest": max(p[1] for p in programs).isoformat()
        }

    loop_added = 0
    loop_covered = 0
    for row in loop_rows:
        cid = str(row.get("epgId") or row["key"]).strip()
        future_actual = any(
            p.get("channel") == cid and
            not (p.findtext("title") or "").startswith("24/7 • ") and
            (dt(p.get("stop")) or now) > now
            for p in root.findall("programme")
        )
        if future_actual:
            continue  # Never replace genuine schedules with continuous markers.
        start = max([now] + [stop for _, stop in existing[cid]])
        if start >= end:
            continue
        if cid not in existing_channels:
            c = etree.Element("channel", id=cid)
            etree.SubElement(c, "display-name").text = row["name"]
            root.insert(0, c)
            existing_channels.add(cid)
        while start < end:
            stop = min(start + timedelta(days=1), end)
            el = etree.SubElement(root, "programme", {
                "start": start.strftime("%Y%m%d%H%M%S +0000"),
                "stop": stop.strftime("%Y%m%d%H%M%S +0000"), "channel": cid})
            etree.SubElement(el, "title", lang="en").text = "24/7 • " + row["name"]
            etree.SubElement(el, "desc", lang="en").text = (
                "Continuous stream. Individual movie and episode times are not published.")
            loop_added += 1
            start = stop
        loop_covered += 1
        changed.add(cid)

    out = Path(args.out)
    (out / "short").mkdir(parents=True, exist_ok=True)
    short_count = {cid: short_file(root, cid, now, out / "short" / (fnv1a(cid)+".json"))
                   for cid in changed}
    original_preserved = sum(etree.tostring(p) in before for p in root.findall("programme"))
    if original_preserved != len(root.findall("programme")) - au_added - loop_added:
        raise SystemExit("Existing programme preservation failed. No publication.")
    if any(short_count[c] == 0 for c in changed):
        raise SystemExit("Missing short guide for changed channel. No publication.")
    guide = etree.tostring(root, encoding="UTF-8", xml_declaration=True)
    (out / "guide.xml").write_bytes(guide)
    with gzip.open(out / "guide.xml.gz", "wb", compresslevel=6) as f:
        f.write(guide)
    report = {
        "generatedAtUtc": now.isoformat(), "requiredThroughUtc": end.isoformat(),
        "sourceErrors": errors, "auChannels": len(au_rows),
        "auChannelsWithSevenDaySource": sum(isinstance(v, dict) for v in au_status.values()),
        "auProgrammesAdded": au_added, "auMatches": au_status,
        "auRejectedCandidates": au_rejections,
        "loopChannels": len(loop_rows), "loopChannelsNewlyCovered": loop_covered,
        "loopProgrammesAdded": loop_added, "changedEpgIds": sorted(changed),
        "untouchedExistingProgrammeEntries": original_preserved,
        "existingProgrammeEntries": len(before),
        "totalProgrammeEntries": len(root.findall("programme")),
        "shortFilesUpdated": len(short_count),
        "sourceCoverageClaim": "seven-day-only for accepted AU sources; other regions unchanged",
    }
    (out / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({k: v for k, v in report.items() if k not in
                      ("auMatches", "changedEpgIds")}, indent=2))
    print("AU_SOURCE_DIAGNOSTICS", json.dumps(au_rejections, indent=2))
    if len(loop_rows) < 100 or len(au_rows) < 20:
        raise SystemExit("Scope/source coverage guard failed. No publication.")


if __name__ == "__main__":
    main()
