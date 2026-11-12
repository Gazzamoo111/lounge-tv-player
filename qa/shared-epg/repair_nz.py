#!/usr/bin/env python3
"""Repair *only* verified NZ XMLTV listings in Lounge TV's shared guide.

No synthetic schedules, catalogue changes, playback changes or publication.
Published guide updates are handled separately by a guarded GitHub workflow.
"""
import argparse
import gzip
import json
import re
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path
from lxml import etree as E
from repair_missing import dt, fnv, short

UTC = timezone.utc
CORE = {"mjh-tvnz-1", "mjh-tvnz-2", "mjh-three",
        "mjh-maori-tv", "mjh-te-reo"}
SKIP = re.compile(r"^(?:no (?:programme|program) (?:data|information)|to be announced|dummy)$", re.I)
QUAL = re.compile(r"\b(?:hd|fhd|uhd|sd|4k|hevc|h265|50fps|60fps)\b", re.I)


def name_key(value):
    value = QUAL.sub("", value or "").casefold()
    return re.sub(r"[^a-z0-9]", "", value)


def load_guide(path):
    opener = gzip.open if str(path).endswith(".gz") else open
    with opener(path, "rb") as f:
        return E.parse(f, E.XMLParser(no_network=True, resolve_entities=False,
                                       huge_tree=True)).getroot()


def index_source(path, now):
    root = load_guide(path)
    channels, by_name, programmes = {}, defaultdict(set), defaultdict(list)
    for c in root.findall("channel"):
        cid = c.get("id")
        if not cid:
            continue
        labels = [n.text.strip() for n in c.findall("display-name") if n.text]
        channels[cid.casefold()] = (cid, labels)
        for label in labels:
            by_name[name_key(label)].add(cid)
    end = now + timedelta(days=8)
    for p in root.findall("programme"):
        start, stop = dt(p.get("start")), dt(p.get("stop"))
        title = (p.findtext("title") or "").strip()
        if start and stop and start < end and stop > now and stop > start and title and not SKIP.fullmatch(title):
            programmes[p.get("channel")].append((start, stop, p))
    return channels, by_name, programmes


def choose(row, sources, now):
    target = str(row["epgId"])
    choices = []
    for label, (channels, names, programmes) in sources.items():
        hit = channels.get(target.casefold())
        if hit:
            cid, method = hit[0], "exact-id"
        elif label == "nz1" and target not in CORE:
            # NZ-only guide: accept a unique broadcaster-name match, never a
            # guessed channel number, coast/delay or non-NZ catalogue match.
            possible = names.get(name_key(row["name"]), set())
            if len(possible) != 1:
                continue
            cid, method = next(iter(possible)), "unique-nz-name"
        else:
            continue
        entries = sorted(programmes.get(cid, []), key=lambda item: item[0])
        if len(entries) < 2 or not any(a <= now < b for a, b, _ in entries):
            continue
        latest = max(b for _, b, _ in entries)
        if latest < now + timedelta(hours=18):
            continue
        # The five Freeview channels must use the matching MJH identity.
        if target in CORE and (label != "mjh" or method != "exact-id"):
            continue
        choices.append((method == "exact-id", latest, len(entries), label, cid, method, entries))
    return max(choices, key=lambda x: x[:3]) if choices else None


def make(manifest, existing, sources, out, now=None):
    now = now or datetime.now(UTC)
    if now.tzinfo is None:
        raise ValueError("now must include a timezone")
    rows = json.loads(Path(manifest).read_text())["rows"]
    nz = [r for r in rows if r.get("category") == "New Zealand" and r.get("epgId")]
    if not nz:
        raise ValueError("No NZ release channels")
    all_owners = defaultdict(set)
    for row in rows:
        if row.get("epgId"):
            all_owners[row["epgId"]].add((row.get("name"), row.get("category")))
    nz_owners = Counter(r["epgId"] for r in nz)
    conflicts = {r["epgId"] for r in nz
                 if nz_owners[r["epgId"]] != 1 or len(all_owners[r["epgId"]]) != 1}
    root = load_guide(existing)
    source_index, errors = {}, {}
    for label, path in sources:
        try:
            source_index[label] = index_source(path, now)
        except (OSError, E.XMLSyntaxError, EOFError) as exc:
            errors[label] = str(exc)
    if "mjh" not in source_index:
        raise ValueError("NZ primary guide missing or invalid; aborting candidate")
    initial = [E.tostring(p, with_tail=False) for p in root.findall("programme")]
    existing_channels = {c.get("id") for c in root.findall("channel")}
    by_target = defaultdict(list)
    for p in root.findall("programme"):
        by_target[p.get("channel")].append(p)
    changes, matched, pruned, added = set(), [], 0, 0
    for row in nz:
        target = row["epgId"]
        if target in conflicts:
            continue
        choice = choose(row, source_index, now)
        if not choice:
            continue
        _, latest, _, label, cid, method, entries = choice
        old = by_target[target][:]
        # Prune only expired entries belonging to a verified NZ target.
        expired = [p for p in old if dt(p.get("stop"))
                   and dt(p.get("stop")) < now - timedelta(hours=48)]
        for p in expired:
            root.remove(p)
        pruned += len(expired)
        old = [p for p in old if p not in expired]
        intervals = [(dt(p.get("start")), dt(p.get("stop"))) for p in old]
        count = 0
        for start, stop, p in entries:
            if any(a and b and start < b and stop > a for a, b in intervals):
                continue
            node = E.fromstring(E.tostring(p, with_tail=False),
                                E.XMLParser(no_network=True, resolve_entities=False))
            node.set("channel", target)
            root.append(node)
            old.append(node)
            intervals.append((start, stop))
            count += 1
        if count and target not in existing_channels:
            ch = E.Element("channel", id=target)
            E.SubElement(ch, "display-name").text = row["name"]
            root.insert(0, ch)
            existing_channels.add(target)
        if count or expired:
            changes.add(target)
        added += count
        by_target[target] = old
        matched.append(dict(name=row["name"], epgId=target, source=label,
                            sourceId=cid, match=method, added=count,
                            latestUtc=latest.isoformat()))
    # Unchanged programmes retain exact XML serialisation and their relative order.
    preserved = [E.tostring(p, with_tail=False) for p in root.findall("programme")
                 if p.get("channel") not in changes]
    expected = [E.tostring(p, with_tail=False) for p in load_guide(existing).findall("programme")
                if p.get("channel") not in changes]
    if preserved != expected:
        raise AssertionError("Unrelated programme modified")
    current = {cid for cid in CORE if any(
        (dt(p.get("start")) or now) <= now < (dt(p.get("stop")) or now)
        for p in by_target[cid])}
    if current != CORE:
        raise ValueError("NZ Freeview core current coverage incomplete: " + repr(sorted(CORE - current)))
    out = Path(out)
    (out / "short").mkdir(parents=True, exist_ok=True)
    for target in sorted(changes):
        short(by_target[target], target, out / "short" / (fnv(target) + ".json"), now)
    xml = E.tostring(root, encoding="UTF-8", xml_declaration=True)
    (out / "guide.xml").write_bytes(xml)
    with gzip.open(out / "guide.xml.gz", "wb", compresslevel=6) as f:
        f.write(xml)
    report = dict(generatedAtUtc=now.isoformat(), nzChannels=len(nz),
                  conflicts=sorted(conflicts), sourceErrors=errors,
                  coreNowCovered=len(current), matchedChannels=len(matched),
                  changedChannels=len(changes), addedProgrammes=added,
                  prunedExpired=pruned, originalProgrammes=len(initial),
                  finalProgrammes=len(root.findall("programme")),
                  sevenDayChannels=sum(datetime.fromisoformat(x["latestUtc"]) >=
                                       now + timedelta(days=7) for x in matched),
                  matched=matched)
    (out / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({k: v for k, v in report.items() if k != "matched"}, indent=2))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--existing", required=True)
    parser.add_argument("--source", action="append", required=True, help="mjh=FILE or nz1=FILE")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    make(args.manifest, args.existing, [s.split("=", 1) for s in args.source], args.out)
