#!/usr/bin/env python3
"""Lounge TV EPG hub: match regional feeds, preserve guide, report actual horizons.

QA-only until source rights and measured end-to-end device accuracy are reviewed.
Input streams and channel catalogue are never changed.
"""
from __future__ import annotations
import argparse
from bisect import bisect_left
import gzip
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path
from lxml import etree as E

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "shared-epg"))
from repair_missing import dt, fnv, short

UTC = timezone.utc
IGNORE_CATEGORIES = {"24/7", "PPV"}
IGNORE_SUBCATEGORIES = {"CINEMANIA TV SHOWS", "Sky Store"}
BAD_TITLE = re.compile(r"^(?:no (?:programme|program) (?:data|information)|to be announced|dummy|tba)$", re.I)
QUALITY = re.compile(r"\b(?:8k|4k|uhd|fhd|hd|sd|hevc|h265|50fps|60fps)\b", re.I)
MARKET = re.compile(r"^(?:US|UK|UK-NOWTV|NZ|AU|USA|AUS)\s*[|:]\s*", re.I)
COUNTRIES = {"New Zealand": "nz", "Australia": "au"}
# Reviewed identities that genuinely describe the same UK broadcast. These
# are not time-shifted +1, East/West or other channel variants.
REVIEWED_UK_SAME_BROADCAST = {
    "DiscoveryHistory.uk": {"DISCOVERY HISTORY", "D+| DISCOVERY HISTORY"},
    "DiscoveryScience.uk": {"DISCOVERY SCIENCE", "D+| DISCOVERY SCIENCE"},
    "SkyPremiere.uk": {"SKY CINEMA PREMIER", "SKY CINEMA PREMIERE"},
    "SkySportsNews.uk": {"SKY SPORTS NEWS", "SKY SPORTS NEWS HQ"},
    "SkyArts.uk": {"SKY ARTS", "SKY ARTS 1"},
}


def norm(value):
    value = unicodedata.normalize("NFKD", value or "").encode("ascii", "ignore").decode()
    value = MARKET.sub("", value)
    value = QUALITY.sub("", value)
    return re.sub(r"[^a-z0-9+]", "", value.casefold())


def region(row):
    if row.get("region") in ("nz", "au", "uk", "us"):
        return row["region"]
    if row.get("category") in COUNTRIES:
        return COUNTRIES[row["category"]]
    m = re.match(r"^(US|UK|NZ|AU)(?:-NOWTV)?\s*[|:]", row.get("sourceName") or "", re.I)
    if m:
        return m[1].lower()
    text = (row.get("subcategory") or "").lower()
    if text.startswith("us ") or text.startswith("usa "):
        return "us"
    if text.startswith("uk "):
        return "uk"
    suffix = str(row.get("epgId") or "").rsplit(".", 1)[-1].lower()
    return suffix if suffix in ("nz", "au", "uk", "us") else None


def catalogue(path):
    raw = json.loads(Path(path).read_text())["rows"]
    owners = defaultdict(set)
    for row in raw:
        tid = row.get("epgId")
        if tid:
            owners[tid].add((norm(row.get("name")), region(row), row.get("category")))
    targets = {}
    conflicts = set()
    duplicate_names = Counter()
    for row in raw:
        country = region(row)
        tid = row.get("epgId")
        if (row.get("category") in IGNORE_CATEGORIES
            or row.get("subcategory") in IGNORE_SUBCATEGORIES
            or country not in ("nz", "au", "uk", "us") or not tid):
            continue
        # Streaming aggregator/title rows are not ordinary broadcast channels.
        # Only explicit country-qualified FAST identities are eligible.
        if row.get("category") == "Streaming" and not re.search(r"\.(?:us|uk)$", tid, re.I):
            continue
        # Same EPG key cannot carry two different channel identities/markets.
        identities = {(name, market) for name, market, _ in owners[tid]}
        if len(identities) > 1:
            approved = REVIEWED_UK_SAME_BROADCAST.get(tid)
            if not (approved
                    and all(market == "uk" for _, market in identities)
                    and {name for name, _ in identities} == {norm(n) for n in approved}):
                conflicts.add(tid)
                continue
        if tid not in targets:
            targets[tid] = {"id": tid, "name": row["name"], "region": country,
                            "category": row.get("category") or "",
                            "subcategory": row.get("subcategory") or ""}
            duplicate_names[(country, norm(row["name"]))] += 1
    # Never name-match indistinguishable catalogue channels with distinct IDs.
    return targets, conflicts, duplicate_names


def explicit_source_region(channel_id, labels):
    """Accept a market hint only when the source explicitly declares it.

    Generic BBC/ABC/Discovery names can occur in several countries.
    Never infer a country from an unqualified channel title.
    """
    markets = set()
    for text in [channel_id, *labels]:
        tail = re.search(r"\.(nz|au|us|uk)$", text or "", re.I)
        lead = re.match(r"^(NZ|AU|US|UK)(?:-NOWTV)?\s*[|:]\s*", text or "", re.I)
        if tail:
            markets.add(tail.group(1).lower())
        if lead:
            markets.add(lead.group(1).lower())
    return "conflict" if len(markets) > 1 else (next(iter(markets)) if markets else None)


def parse_source(path, region_code, targets, name_counts, now):
    opener = gzip.open if str(path).endswith(".gz") else open
    id_lookup = defaultdict(list)
    names = defaultdict(set)
    source_names = {}
    source_labels = {}
    with opener(path, "rb") as f:
        for _, el in E.iterparse(f, events=("end",), tag=("channel", "programme"),
                                 resolve_entities=False, no_network=True, huge_tree=True):
            if el.tag == "channel" and el.get("id"):
                cid = el.get("id")
                labels = [x.text.strip() for x in el.findall("display-name") if x.text]
                source_names[cid] = labels[0] if labels else cid
                source_labels[cid] = labels
                id_lookup[cid.casefold()].append(cid)
                for label in labels:
                    names[norm(label)].add(cid)
            el.clear()
            while el.getprevious() is not None:
                del el.getparent()[0]
    target_map = defaultdict(list)
    for tid, target in targets.items():
        market = target["region"]
        if region_code != "multi" and market != region_code:
            continue
        exact = id_lookup.get(tid.casefold(), [])
        if len(exact) == 1:
            cid, method = exact[0], "exact-id"
        else:
            key = norm(target["name"])
            matches = names.get(key, set())
            if region_code == "multi":
                # Cross-country feeds can only name-match explicitly
                # country-identified channels, never generic BBC/ABC/Sky.
                matches = {x for x in matches
                           if explicit_source_region(x, source_labels.get(x, [])) == market}
            # No fuzzy aliases, no generic reused names, no cross-market guesses.
            if len(matches) != 1 or name_counts[(market, key)] != 1:
                continue
            cid, method = next(iter(matches)), "unique-name"
        source_market = explicit_source_region(cid, source_labels.get(cid, []))
        if source_market and source_market != market:
            continue
        if region_code == "multi" and method != "exact-id" and not source_market:
            continue
        if tid.startswith("mjh-") and method != "exact-id":
            continue
        target_map[cid].append((tid, method))
    events = defaultdict(list)
    until = now + timedelta(days=15)
    with opener(path, "rb") as f:
        for _, el in E.iterparse(f, events=("end",), tag=("channel", "programme"),
                                 resolve_entities=False, no_network=True, huge_tree=True):
            cid = el.get("channel") if el.tag == "programme" else None
            if cid in target_map:
                st, en = dt(el.get("start")), dt(el.get("stop"))
                title = (el.findtext("title") or "").strip()
                if st and en and en > now and st < until and en > st and title and not BAD_TITLE.fullmatch(title):
                    if len(events[cid]) < 5000:
                        events[cid].append((st, en, E.tostring(el, with_tail=False)))
            el.clear()
            while el.getprevious() is not None:
                del el.getparent()[0]
    candidates = {}
    for cid, mapped in target_map.items():
        entries = sorted(events.get(cid, []), key=lambda p: (p[0], p[1]))
        if len(entries) < 2:
            continue
        if not any(st <= now < en for st, en, _ in entries):
            continue
        latest = max(en for _, en, _ in entries)
        for tid, method in mapped:
            candidates[tid] = {
                "sourceId": cid, "sourceName": source_names.get(cid, cid),
                "method": method, "latest": latest, "entries": entries,
            }
    return candidates, len(source_names)


def actual_coverage(programmes, targets, now, days):
    latest = defaultdict(lambda: now)
    current = set()
    for p in programmes:
        target = p.get("channel")
        if target not in targets:
            continue
        start, stop = dt(p.get("start")), dt(p.get("stop"))
        if not start or not stop or stop <= start:
            continue
        title = (p.findtext("title") or "").strip()
        if not title or BAD_TITLE.fullmatch(title) or title.startswith("24/7 • "):
            continue
        if start <= now < stop:
            current.add(target)
        if stop > latest[target]:
            latest[target] = stop
    return {"now": len(current),
            "3day": len({t for t in current if latest[t] >= now + timedelta(days=days)}),
            "7day": len({t for t in current if latest[t] >= now + timedelta(days=7)})}


def uncovered_three_day(programmes_by_id, targets, now, minimum_days):
    """Complete genuine US/UK three-day gap report, with no arbitrary item limit.

    An on-air programme with a short remaining schedule is a gap as well;
    rejecting fabricated 24/7 placeholders mirrors actual_coverage().
    """
    required = now + timedelta(days=minimum_days)
    missing = []
    for tid, target in sorted(targets.items()):
        if target["region"] not in ("us", "uk"):
            continue
        current = False
        latest = None
        for p in programmes_by_id.get(tid, []):
            start, stop = dt(p.get("start")), dt(p.get("stop"))
            title = (p.findtext("title") or "").strip()
            if (not start or not stop or stop <= start or not title
                    or BAD_TITLE.fullmatch(title) or title.startswith("24/7 • ")):
                continue
            if start <= now < stop:
                current = True
            if latest is None or stop > latest:
                latest = stop
        if current and latest is not None and latest >= required:
            continue
        missing.append({
            "id": tid, "name": target["name"], "region": target["region"],
            "category": target["category"], "subcategory": target["subcategory"],
            "syntheticId": tid.startswith("live:"),
            "gapReason": "no-current-listing" if not current else "short-horizon",
            "latestUtc": latest.isoformat() if latest else None,
        })
    return missing


def build(manifest, existing, source_entries, out, now=None, minimum_days=3):
    now = now or datetime.now(UTC)
    if now.tzinfo is None or minimum_days < 1:
        raise ValueError("UTC-aware time and positive minimum days required")
    targets, conflicts, name_counts = catalogue(manifest)
    opener = gzip.open if str(existing).endswith(".gz") else open
    with opener(existing, "rb") as f:
        root = E.parse(f, E.XMLParser(resolve_entities=False, no_network=True, huge_tree=True)).getroot()
    originals = root.findall("programme")
    baseline = actual_coverage(originals, targets, now, minimum_days)
    before_count = len(originals)
    choices = defaultdict(list)
    health = []
    for item in source_entries:
        label = item["id"]
        code = item["region"]
        path = item["path"]
        try:
            found, channel_count = parse_source(path, code, targets, name_counts, now)
            accepted = 0
            for tid, candidate in found.items():
                if tid in conflicts or candidate["latest"] < now + timedelta(days=minimum_days):
                    continue
                # Longer genuine schedules rank ahead of shorter ones, but
                # the same target can be supplemented by multiple sources.
                choices[tid].append((candidate["latest"], candidate["method"] == "exact-id",
                                     label, candidate))
                accepted += 1
            health.append({"id": label, "region": code, "status": "ok",
                           "sourceChannels": channel_count, "matchedWithMinHorizon": accepted})
        except (OSError, EOFError, E.XMLSyntaxError, ValueError, gzip.BadGzipFile) as exc:
            health.append({"id": label, "region": code, "status": "error",
                           "error": str(exc)[:200]})
    if not any(x["status"] == "ok" for x in health):
        raise ValueError("No valid XMLTV sources; no candidate generated")
    existing_by_id = defaultdict(list)
    for p in originals:
        if p.get("channel") in targets:
            existing_by_id[p.get("channel")].append(p)
    guide_channels = {ch.get("id") for ch in root.findall("channel")}
    amended = set()
    proven = []
    added = 0
    for tid in sorted(choices):
        target = targets[tid]
        current = existing_by_id[tid]
        intervals = sorted(
            (a, b) for p in current
            if (a := dt(p.get("start"))) and (b := dt(p.get("stop"))) and b > a)
        # Coalesce existing overlapping intervals once. Query the sorted ranges
        # by binary search rather than rescan every programme for each source.
        merged = []
        for a, b in intervals:
            if merged and a <= merged[-1][1]:
                merged[-1] = (merged[-1][0], max(merged[-1][1], b))
            else:
                merged.append((a, b))
        intervals = merged
        starts = [item[0] for item in intervals]
        # Preserve all existing intervals: no new record may replace a programme.
        accepted_sources = []
        new_entries = []
        for _, _, label, candidate in sorted(choices[tid], key=lambda x: (x[0], x[1]), reverse=True):
            accepted_sources.append(label)
            count = 0
            for start, stop, xml in candidate["entries"]:
                idx = bisect_left(starts, start)
                if ((idx > 0 and intervals[idx - 1][1] > start) or
                    (idx < len(intervals) and intervals[idx][0] < stop)):
                    continue
                item = E.fromstring(xml, E.XMLParser(resolve_entities=False, no_network=True))
                item.set("channel", tid)
                new_entries.append(item)
                starts.insert(idx, start)
                intervals.insert(idx, (start, stop))
                count += 1
            if count:
                proven.append({"epgId": tid, "channel": target["name"], "region": target["region"],
                               "source": label, "sourceId": candidate["sourceId"],
                               "match": candidate["method"], "newProgrammes": count,
                               "latestUtc": candidate["latest"].isoformat()})
        if not new_entries:
            continue
        if tid not in guide_channels:
            ch = E.Element("channel", id=tid)
            E.SubElement(ch, "display-name").text = target["name"]
            root.insert(0, ch)
            guide_channels.add(tid)
        for n in new_entries:
            root.append(n)
            current.append(n)
        existing_by_id[tid] = current
        amended.add(tid)
        added += len(new_entries)
    if len(root.findall("programme")) != before_count + added:
        raise AssertionError("Existing programmes were dropped")
    # We only append new programmes; no old programme is deleted or modified.
    all_programmes = root.findall("programme")
    if any(current is not old for current, old in zip(all_programmes, originals)):
        raise AssertionError("Original XMLTV record order was modified")
    out = Path(out)
    (out / "short").mkdir(parents=True, exist_ok=True)
    for tid in sorted(amended):
        short(existing_by_id[tid], tid, out / "short" / (fnv(tid) + ".json"), now)
    xml = E.tostring(root, encoding="UTF-8", xml_declaration=True)
    (out / "guide.xml").write_bytes(xml)
    with gzip.open(out / "guide.xml.gz", "wb", compresslevel=6) as f:
        f.write(xml)
    updated = actual_coverage(root.findall("programme"), targets, now, minimum_days)
    by_region = {}
    for r in ("nz", "au", "us", "uk"):
        subset = {k: v for k, v in targets.items() if v["region"] == r}
        before = actual_coverage(originals, subset, now, minimum_days)
        after = actual_coverage(root.findall("programme"), subset, now, minimum_days)
        by_region[r] = {"targets": len(subset), "before": before, "after": after}
    by_subcategory = []
    for region_code in ("us", "uk"):
        labels = sorted({t["subcategory"] for t in targets.values() if t["region"] == region_code})
        for sub in labels:
            subset = {k: t for k, t in targets.items()
                      if t["region"] == region_code and t["subcategory"] == sub}
            cov = actual_coverage(root.findall("programme"), subset, now, minimum_days)
            by_subcategory.append({"region": region_code, "subcategory": sub,
                                   "targets": len(subset), **cov})
    per_source = defaultdict(lambda: {"programmesAdded": 0, "channelIdsModified": set(),
                                       "usProgrammes": 0, "ukProgrammes": 0})
    for item in proven:
        value = per_source[item["source"]]
        value["programmesAdded"] += item["newProgrammes"]
        value["channelIdsModified"].add(item["epgId"])
        if item["region"] == "us":
            value["usProgrammes"] += item["newProgrammes"]
        if item["region"] == "uk":
            value["ukProgrammes"] += item["newProgrammes"]
    source_contributions = {
        sid: {"programmesAdded": v["programmesAdded"],
              "channelIdsModified": len(v["channelIdsModified"]),
              "usProgrammes": v["usProgrammes"], "ukProgrammes": v["ukProgrammes"]}
        for sid, v in sorted(per_source.items())}
    report = {"generatedAtUtc": now.isoformat(), "minimumRealDays": minimum_days,
              "preferredDays": 7, "scope": "NZ/AU/US/UK with unambiguous identities",
              "manifestTargets": len(targets), "conflictingEpgIds": sorted(conflicts),
              "sources": health, "sourceContributions": source_contributions,
              "before": baseline, "after": updated, "byRegion": by_region,
              "byUsUkSubcategory": by_subcategory,
              "addedProgrammes": added, "changedTargets": len(amended),
              "preservedProgrammes": before_count, "totalProgrammes": before_count + added,
              "selected": proven, "uncovered3day": uncovered_three_day(existing_by_id, targets, now, minimum_days)}
    (out / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps({k: v for k, v in report.items()
                      if k not in ("selected", "uncovered3day")},
                     ensure_ascii=False))
    return report


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--manifest", required=True)
    p.add_argument("--existing", required=True)
    p.add_argument("--source-list", required=True, help="JSON [{id, region, path}]")
    p.add_argument("--out", required=True)
    p.add_argument("--now", help="ISO8601 timestamp for reproducible QA")
    args = p.parse_args()
    now = datetime.fromisoformat(args.now.replace("Z", "+00:00")) if args.now else None
    build(args.manifest, args.existing, json.loads(Path(args.source_list).read_text()),
          args.out, now=now)
