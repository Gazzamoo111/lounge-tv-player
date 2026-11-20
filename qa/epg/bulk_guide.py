"""Extend the existing Lounge TV XMLTV and per-ID short guides."""
from __future__ import annotations

import argparse
import base64
from bisect import bisect_left
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from functools import lru_cache
import gzip
from html import unescape
import json
from pathlib import Path
import re
import subprocess
import time
import unicodedata

from lxml import etree

from seven_day_targeted import AU_SOURCE_BINDINGS, fnv1a, short_file

UTC = timezone.utc
MAX_DOWNLOAD = 50_000_000
MAX_XML = 750_000_000
COUNTRIES = dict(zip(
    "au nz uk us ca ie in de fr es it nl pt pl ro ph sg my id vn th tr gr jp kr cn dk se no fi cz hu bg rs hr mx br ar co ch at be ua ru za pk bd np af tw ee cy mt sk si by al mk me ba xk ge am az uz tj kz il ir cl sr".split(),
    ("Australia|New Zealand|UK TV|US TV|Canada|Ireland|India|Germany|France|Spain|Italy|Netherlands|Portugal|Poland|Romania|Philippines|Singapore|Malaysia|Indonesia|Vietnam|Thailand|Türkiye|Greece|Japan|South Korea|China|Denmark|Sweden|Norway|Finland|Czech Republic|Hungary|Bulgaria|Serbia|Croatia|Mexico|Brazil|Argentina|Colombia|Switzerland|Austria|Belgium|Ukraine|Russia|South Africa|Pakistan|Bangladesh|Nepal|Afghanistan|Taiwan|Estonia|Cyprus|Malta|Slovakia|Slovenia|Belarus|Albania|North Macedonia|Montenegro|Bosnia|Kosovo|Georgia|Armenia|Azerbaijan|Uzbekistan|Tajikistan|Kazakhstan|Israel|Iran|Chile|Suriname").split("|"),
    strict=True,
))
GROUP_COUNTRY = {name.casefold(): cc for cc, name in COUNTRIES.items()}
GROUP_COUNTRY.update({"uk sports": "uk", "us sports": "us", "australia tv": "au", "us tv & entertainment": "us"})
REGION_CODES = {"gb": "uk", "usa": "us", "aus": "au"}
PREFIX = re.compile(r"^([a-z]{2,3})(?:\s*[|:]\s*|\s+-\s+)", re.I)
MARKUP = re.compile(r"[ᴿᴬᵂⱽᴵᴾᴴᴰᵁᶠ⁶⁰ᵖˢ◉★]+")
QUALITY = re.compile(r"\b(?:4k|uhd|fhd|hevc|hd|sd|50fps|60fps)\b", re.I)
UNSCHEDULED = re.compile(r"24\s*/\s*7|\b247\b|\blive[- ]events?\b|\bppv\b|\bevent sources\b", re.I)
SHIFTED = re.compile(r"(?:\+\s*\d|\btimeshift\b|\bdelay(?:ed)?\b)", re.I)
MARKET = re.compile(r"\b(?:east(?:ern)?|west(?:ern)?|pacific|central|mountain|atlantic|"
                    r"sydney|melbourne|brisbane|perth|adelaide|darwin|hobart|canberra|nsw|vic|qld|"
                    r"scotland|wales|northern ireland|suisse|schweiz)\b", re.I)
PLATFORM = re.compile(r"\b(pluto(?:\s*tv)?|samsung(?:\s*tv\s*plus)?|plex|tubi|prime)\b", re.I)
GENERIC = {"abc", "nbc", "fox", "cbs", "pbs", "cw", "bbc1", "bbc2", "itv1",
           "seven", "nine", "ten", "sbs", "channel9", "channel7", "channel10",
           "channel4", "5", "rtl", "tv1", "tv2", "tv3"}
ALIASES = {
    "uk": [("BBC ONE", "BBC 1"), ("BBC TWO", "BBC 2"), ("BBC THREE", "BBC 3"),
           ("BBC FOUR", "BBC 4"), ("ITV ONE", "ITV 1"), ("ITV 2", "ITV TWO"),
           ("SKY SPORTS PL", "SKY SPORTS PREMIER LEAGUE"), ("MORE 4", "MORE FOUR")],
    "us": [("DISNEY JR", "DISNEY JUNIOR"), ("NICK JR", "NICK JUNIOR"),
           ("GOLF CHANNEL", "NBC GOLF"), ("TENNIS CHANNEL", "THE TENNIS CHANNEL")],
    "ie": [("RTÉ ONE", "RTÉ 1"), ("RTÉ TWO", "RTÉ 2")],
    "au": [("FOX SPORTS 501", "FOX CRICKET"), ("FOX SPORTS 502", "FOX LEAGUE"),
           ("FOX SPORTS 504", "FOX FOOTY")],
}


def country(raw):
    code = REGION_CODES.get(raw.lower(), raw.lower())
    return code if code in COUNTRIES else ""


def prefix(raw):
    match = PREFIX.match(raw)
    if match and (cc := country(match[1])):
        return cc, raw[match.end():]
    return "", raw


def id_country(raw):
    suffix = re.search(r"\.([a-z]{2})$", raw, re.I)
    if suffix:
        return country(suffix[1])
    legacy = re.match(r"^([A-Z]{2})-", raw)
    return country(legacy[1]) if legacy else prefix(raw)[0]


def clean(raw):
    raw = MARKUP.sub(" ", raw)
    raw = re.sub(r"^(?:TV|4K)\s*\|\s*", "", raw, flags=re.I)
    return prefix(raw.strip())[1]


def normalise(raw):
    raw = QUALITY.sub(" ", clean(raw))
    # Punctuation-only differences are common, but + distinguishes timeshifts
    # and simulcasts from different branded services.
    return "".join(c for c in unicodedata.normalize("NFKD", raw.casefold())
                   if c.isalnum() or c == "+")


ALIAS_INDEX = {(region, normalise(name)): normalise(names[0])
               for region, pairs in ALIASES.items() for names in pairs for name in names}


def platform_name(raw):
    match = PLATFORM.search(raw)
    if not match:
        return ""
    return next(platform for platform in ("pluto", "samsung", "plex", "tubi", "prime")
                if match[1].lower().startswith(platform))


def identity(raw, region):
    raw = re.sub(r"^(?:TUBI|PRIME|PLUTO(?: TV)?|PLEX|SAMSUNG(?: TV PLUS)?)\s*\|\s*", "",
                 clean(raw), flags=re.I)
    raw = re.sub(r"\s*\((?:Tubi|Pluto TV|Plex|Samsung TV Plus)\)$", "", raw, flags=re.I)
    name = normalise(raw)
    return ALIAS_INDEX.get((region, name), name)


@lru_cache(maxsize=131072)
def timestamp(raw):
    # XMLTV defines an omitted timezone as UTC. Explicit offsets, including
    # half-hour and quarter-hour offsets, are converted before comparing.
    match = re.fullmatch(r"(\d{14})(?:\s+(Z|UTC|[+-]\d{4}))?", raw or "")
    if not match:
        return None
    zone = match[2]
    if zone and zone[0] in "+-" and int(zone[-2:]) >= 60:
        return None
    try:
        return datetime.strptime(match[1] + (zone if zone and zone[0] in "+-" else "+0000"),
                                 "%Y%m%d%H%M%S%z").astimezone(UTC)
    except ValueError:
        return None


@dataclass(frozen=True)
class Row:
    key: str
    name: str
    category: str
    guide: str
    provider: str = ""
    provider_group: str = ""

    @property
    def region(self):
        return (prefix(self.name)[0] or GROUP_COUNTRY.get(self.category.casefold(), "")
                or id_country(self.provider) or id_country(self.guide)
                or prefix(self.provider_group)[0] or ("us" if self.platform == "tubi" else ""))

    @property
    def eligible(self):
        return not UNSCHEDULED.search(self.category + " " + self.name)

    @property
    def platform(self):
        if self.category.casefold() != "fast tv":
            return ""
        return platform_name(self.name) or platform_name(self.provider_group)


@dataclass(frozen=True)
class Source:
    name: str
    region: str
    platform: str
    url: str


def read_sources(path):
    return [Source(*line.split("\t")) for line in path.read_text().splitlines()
            if line and not line.startswith("#")]


def load_catalogue(path):
    if path.suffix == ".json":
        return [Row(str(r["key"]), r["name"], r["category"], r["guide"],
                    r.get("provider", ""), r.get("provider_group", ""))
                for r in json.loads(path.read_text())]
    rows = []
    with path.open(encoding="utf-8-sig") as file:
        if not file.readline().startswith("#EXTM3U"):
            raise ValueError("not an M3U catalogue")
        for line in file:
            if not line.startswith("#EXTINF:"):
                continue
            attrs = dict(re.findall(r'([\w-]+)="([^"]*)"', line))
            label = re.sub(r'"[^"]*"', '""', line).partition(",")[2].strip()
            group = unescape(attrs.get("group-title", ""))
            group = re.sub(r"^\d+\s+[^A-Za-z0-9]*", "", group)
            rows.append(Row(attrs.get("channel-id", str(len(rows))), unescape(label),
                            group, unescape(attrs.get("tvg-id", ""))))
    return rows


def download(source, cache):
    path = cache / (source.name + ".gz")
    if path.is_file():
        return path, {"status": "cached", "bytes": path.stat().st_size}
    temp = path.with_suffix(".part")
    result = subprocess.run(
        ["curl", "--fail", "--silent", "--location", "--proto", "=https",
         "--max-filesize", str(MAX_DOWNLOAD), "--connect-timeout", "10", "--max-time", "120",
         "--retry", "1", "--output", str(temp), "--write-out", "%{http_code}", source.url],
        capture_output=True, text=True, check=False,
    )
    if result.returncode:
        temp.unlink(missing_ok=True)
        return None, {"status": "download-failed", "http": result.stdout, "curl": result.returncode}
    temp.replace(path)
    return path, {"status": "downloaded", "bytes": path.stat().st_size}


class BoundedReader:
    def __init__(self, file):
        self.file = file
        self.remaining = MAX_XML

    def read(self, size):
        data = self.file.read(min(size, self.remaining + 1))
        self.remaining -= len(data)
        if self.remaining < 0:
            raise ValueError("XML byte limit exceeded")
        return data


def elements(path):
    with path.open("rb") as raw:
        compressed = raw.read(2) == b"\x1f\x8b"
        raw.seek(0)
        with gzip.GzipFile(fileobj=raw) if compressed else raw as file:
            parse = etree.iterparse(BoundedReader(file), events=("start", "end"),
                                    resolve_entities=False, no_network=True)
            _, root = next(parse)
            if root.tag != "tv" or root.getroottree().docinfo.internalDTD is not None and (
                    root.getroottree().docinfo.internalDTD.entities()):
                raise ValueError("invalid XMLTV root or entity declaration")
            for event, node in parse:
                if event == "end" and node.getparent() is root:
                    yield node
                    node.clear()
                    while node.getprevious() is not None:
                        del root[0]


def programmes(node, cutoff, horizon):
    title = (node.findtext("title") or "").strip()
    start, stop = timestamp(node.get("start")), timestamp(node.get("stop"))
    if not title or not start or not stop or start >= stop:
        return "invalid", ()
    if title.startswith("24/7 • "):
        return "continuous-label", ()
    if stop <= cutoff or start >= horizon:
        return "outside-window", ()
    return "valid", (start, stop)


def compact(node, start, stop):
    node.set("start", start.strftime("%Y%m%d%H%M%S +0000"))
    node.set("stop", stop.strftime("%Y%m%d%H%M%S +0000"))
    return etree.tostring(node, with_tail=False)


def baseline(path, cutoff, horizon, *, legacy=False, continuous_labels=None):
    channels, records, counts = {}, defaultdict(dict), Counter()
    for element in elements(path):
        if element.tag == "channel":
            key = element.get("id")
            if not key:
                raise ValueError("missing baseline channel ID")
            if key in channels:
                counts["duplicate_channel_tags"] += 1
                if not legacy:
                    raise ValueError("duplicate output channel ID")
            else:
                channels[key] = etree.tostring(element, with_tail=False)
        if element.tag == "programme":
            counts["programmes_read"] += 1
            status, window = programmes(element, cutoff, horizon)
            counts[status] += 1
            if status == "valid":
                key = element.get("channel")
                if window in records[key]:
                    counts["duplicate"] += 1
                else:
                    records[key][window] = compact(element, *window)
            elif status == "continuous-label" and continuous_labels is not None:
                start, stop = timestamp(element.get("start")), timestamp(element.get("stop"))
                if stop > cutoff and start < horizon:
                    labels = continuous_labels[element.get("channel")]
                    if (start, stop) in labels:
                        raise ValueError("duplicate continuous label")
                    labels[start, stop] = compact(element, start, stop)
    if (not channels or not records or records.keys() - channels.keys() or counts["invalid"] or
            (continuous_labels is not None and continuous_labels.keys() - channels.keys())):
        raise ValueError("baseline XMLTV failed integrity check")
    return channels, records, dict(counts)


def market_tokens(labels):
    return {value.casefold() for label in labels for value in MARKET.findall(label)}


def source_region(source, cid, labels):
    if source.platform == "multi":
        return source.region
    if source.platform == "samsung" and not source.region:
        return country(cid[:2])
    explicit = {cc for raw in labels[:1] if (cc := prefix(raw)[0])}
    inferred = id_country(cid)
    if inferred:
        explicit.add(inferred)
    if len(explicit) > 1 or (source.region and explicit and source.region not in explicit):
        return ""
    return next(iter(explicit), source.region)


def safe_match(row, source, cid, labels, method):
    region = source_region(source, cid, labels)
    raw = clean(row.name)
    if source.platform:
        platform = source.platform if source.platform != "multi" else platform_name(cid.rsplit(".", 1)[-1])
        if source.platform == "multi" and not platform:
            platform = {"PlutoTV": "pluto", "SamsungTVPlus": "samsung"}.get(cid.rsplit(".", 1)[-1], "")
        if not row.platform or row.platform != platform:
            return False
    elif row.platform:
        return False
    if region != row.region or (not region and method != "exact"):
        return False
    if not raw or bool(SHIFTED.search(raw)) != any(SHIFTED.search(label) for label in labels):
        return False
    if market_tokens([raw]) != market_tokens([cid, *labels]):
        return False
    if method == "exact":
        # Existing IDs can be wrong. Require a corroborating label or an ID
        # spelling of the same service before adding a new source schedule.
        names = {identity(label, region) for label in labels}
        if identity(raw, region) not in names:
            return False
    elif identity(raw, region) in GENERIC or len(identity(raw, region)) < 3:
        return False
    return True


def current_next(windows, now):
    # A distant later listing cannot serve as a useful next programme.
    return (any(start <= now < stop for start, stop in windows),
            any(now < start <= now + timedelta(hours=24) for start, _ in windows))


def read_source(source, path, index, aliases, now, cutoff, horizon):
    valid_ids, discovered, matched, records = set(), set(), {}, defaultdict(dict)
    duplicates = set()
    counts = Counter()
    latest = None
    for element in elements(path):
        if element.tag == "channel":
            cid = element.get("id")
            labels = [(node.text or "").strip() for node in element.findall("display-name") if node.text]
            if not cid or not labels:
                counts["invalid_channels"] += 1
                continue
            if cid in discovered:
                duplicates.add(cid)
            discovered.add(cid)
            region = source_region(source, cid, labels)
            for target in aliases.get((source.name, cid), ()):
                matched.setdefault(cid, {})[target] = "reviewed"
            strict = {(row, "exact") for row in index.get(("id", cid.casefold()), ())}
            by_name = {(row, "name") for label in labels
                       for row in index.get(("name", region, identity(label, region)), ()) if region}
            for row, method in sorted(strict | by_name, key=lambda item: item[1]):
                if safe_match(row, source, cid, labels, method):
                    choices = matched.setdefault(cid, {})
                    choices.setdefault(row.guide, method)
        elif element.tag == "programme":
            key = element.get("channel")
            status, window = programmes(element, cutoff, horizon)
            counts[status] += 1
            if status == "valid" and key in discovered:
                valid_ids.add(key)
                latest = max(latest or window[1], window[1])
                if key in matched and key not in duplicates:
                    if window not in records[key]:
                        records[key][window] = compact(element, *window)
                    else:
                        counts["duplicate"] += 1
    by_target = defaultdict(dict)
    for source_id, targets in matched.items():
        if source_id in duplicates:
            continue
        for target, method in targets.items():
            by_target[target][source_id] = method
    selections, reasons = {}, {}
    for target, choices in by_target.items():
        strength = min(METHODS[method] for method in choices.values())
        top = [(cid, method) for cid, method in choices.items() if METHODS[method] == strength]
        if len(top) > 1:
            reasons[target] = "ambiguous-in-source"
            continue
        cid, method = top[0]
        line = records.get(cid, {})
        if not all(current_next(line, now)) or (method == "reviewed" and
                max(stop for _, stop in line) < now + timedelta(days=3)):
            reasons[target] = "schedule-not-current-and-next-or-short-horizon"
            continue
        selections[target] = (cid, method, line)
    health = {"status": "usable" if latest and latest > now else "stale-or-empty",
              "channels": len(discovered), "scheduled_ids": len(valid_ids),
              "duplicate_ids": len(duplicates), "records": dict(counts),
              "latest_stop": latest.isoformat() if latest else None,
              "accepted_ids": len(selections), "ambiguous_ids": sum(
                  reason == "ambiguous-in-source" for reason in reasons.values())}
    return selections, reasons, health


METHODS = {"reviewed": 0, "exact": 1, "name": 2}


def candidates(rows, channels, records, now):
    owners = defaultdict(set)
    for row in rows:
        if row.guide not in {"", "0"}:
            owners[row.guide].add(row)
    index, reviewed, reasons = defaultdict(set), defaultdict(set), {}
    for target, same in owners.items():
        schedule = records.get(target, {})
        if all(current_next(schedule, now)) and any(
                end >= now + timedelta(days=1) for _, end in schedule):
            continue
        # Reject new schedules if a shared output ID names multiple distinct
        # services, markets, timeshifts or mixes loop and scheduled channels.
        identities = {(r.region, identity(r.name, r.region), r.platform) for r in same}
        if len(identities) != 1 or not all(row.eligible for row in same):
            reasons[target] = "shared-id-conflict"
            continue
        row = min(same, key=lambda entry: (entry.name, entry.key))
        if target in AU_SOURCE_BINDINGS:
            reviewed["iptv-au", AU_SOURCE_BINDINGS[target]].add(target)
            continue
        for entry in same:
            for cid in {entry.guide, entry.provider} - {"", "0"}:
                index["id", cid.casefold()].add(row)
        if row.region:
            index["name", row.region, identity(row.name, row.region)].add(row)
        reasons[target] = "no-safe-source-match" if row.region else "region-unverified"
    return owners, index, reviewed, reasons


def add_schedule(records, incoming, target=None):
    # Keep the baseline authoritative where it has valid entries. Resolve
    # overlapping imports deterministically without making up partial slots.
    ordered = sorted(records)
    accepted = 0
    # Prefix maxima are unnecessary because records admitted by this path
    # are non-overlapping. Legacy primary records are normalized before merge.
    for window, data in sorted(incoming.items()):
        start, stop = window
        pos = bisect_left(ordered, window)
        if (pos and ordered[pos - 1][1] > start) or (
                pos < len(ordered) and ordered[pos][0] < stop):
            continue
        if target is not None:
            node = etree.fromstring(data)
            node.set("channel", target)
            data = etree.tostring(node, with_tail=False)
        records[window] = data
        ordered.insert(pos, window)
        accepted += 1
    return accepted


def coverage(rows, channels, records, now, reasons):
    groups, regions = defaultdict(Counter), defaultdict(Counter)
    remaining = []
    for row in rows:
        if not row.eligible:
            continue
        mapped = row.guide in channels
        current, upcoming = current_next(records.get(row.guide, {}), now)
        for count in (groups["TOTAL"], groups[row.category], regions[row.region or "unknown"]):
            count.update(eligible=1, mapped=mapped, current=current, next=upcoming,
                         both=current and upcoming)
        if not (current and upcoming):
            reason = ("no-usable-playlist-id" if row.guide in {"", "0"} else
                      reasons.get(row.guide, "schedule-gap"))
            remaining.append({"key": row.key, "name": row.name, "guide": row.guide,
                              "region": row.region, "category": row.category, "reason": reason})
    return {"categories": groups, "regions": regions, "remaining": remaining}


def write_guide(path, channels, records):
    with gzip.open(path, "wb", compresslevel=5) as file:
        file.write(b'<?xml version="1.0" encoding="UTF-8"?>\n<tv generator-info-name="Lounge TV">\n')
        for data in channels.values():
            file.write(data + b"\n")
        for schedule in records.values():
            for _, data in sorted(schedule.items()):
                file.write(data + b"\n")
        file.write(b"</tv>\n")


def verify_short_guides(directory, channels, records, now):
    verified = 0
    for path in directory.glob("*.json"):
        document = json.loads(path.read_text())
        channel = document["epg_id"]
        if channel not in channels or path.stem != fnv1a(channel):
            raise ValueError("incorrect short guide channel ID")
        expected = []
        for (start, stop), xml in sorted(records[channel].items()):
            if stop > now - timedelta(minutes=15):
                programme = etree.fromstring(xml)
                expected.append((int(start.timestamp()), int(stop.timestamp()),
                                 (programme.findtext("title") or "").strip()))
        actual = []
        for listing in document["epg_listings"]:
            if listing["channel_id"] != channel or listing["epg_id"] != channel:
                raise ValueError("incorrect short listing channel ID")
            title = base64.b64decode(listing["title"], validate=True).decode()
            base64.b64decode(listing["description"], validate=True).decode()
            actual.append((listing["start_timestamp"], listing["stop_timestamp"], title))
        if actual != expected:
            raise ValueError("short guide listings differ from XMLTV")
        verified += len(actual)
    return verified


def build(args):
    started = time.monotonic()
    now = datetime.fromisoformat(args.now).astimezone(UTC) if args.now else datetime.now(UTC)
    cutoff, horizon = now - timedelta(hours=12), now + timedelta(days=7)
    profiles = {name: load_catalogue(Path(path)) for name, path in
                (item.split("=", 1) for item in args.profile)}
    before_profiles = {name: load_catalogue(Path(path)) for name, path in
                       (item.split("=", 1) for item in args.before_profile)}
    if set(profiles) != {"nz-au", "plus", "international"}:
        raise ValueError("full catalogues for all three profiles are required")
    if any(not rows or len({row.key for row in rows}) != len(rows) for rows in profiles.values()):
        raise ValueError("empty catalogue or duplicate stable channel IDs")
    for name, before in before_profiles.items():
        if [(r.key, r.name, r.category) for r in before] != [(r.key, r.name, r.category) for r in profiles[name]]:
            raise ValueError("catalogue identity, name or order changed")
    continuous_labels = defaultdict(dict)
    channels, records, baseline_health = baseline(
        args.baseline, cutoff, horizon, continuous_labels=continuous_labels)
    canonical_ids = set(channels)
    intl_ids = {row.guide for row in profiles["international"] if row.guide not in {"", "0"}}
    intl_channels, intl_records, intl_health = (
        baseline(args.international_baseline, cutoff, horizon, legacy=True)
        if args.international_baseline else ({}, {}, {}))
    before = {}
    for name, rows in profiles.items():
        c, p = ((intl_channels, intl_records) if name == "international" and args.international_baseline
                else (channels, records))
        before[name] = coverage(before_profiles.get(name, rows), c, p, now, {})
    if args.previous_bulk:
        saved_channels, saved_records, _ = baseline(args.previous_bulk, cutoff, horizon)
        retained = {r.guide for name in ("nz-au", "plus") for r in profiles[name]}
        for target in saved_records.keys() & retained:
            channels.setdefault(target, saved_channels[target])
            add_schedule(records.setdefault(target, {}), saved_records[target])
    # An International-only working schedule is preserved in that profile.
    # Shared publishing never uploads the private compiled baseline to GitHub.
    all_rows = [row for rows in profiles.values() for row in rows]
    owners, index, aliases, reasons = candidates(all_rows, channels, records, now)
    wanted_regions = {key[1] for key in index if key[0] == "name"}
    sources = [source for source in read_sources(args.sources) if not source.region
               or source.region in wanted_regions or any(key[0] == source.name for key in aliases)]
    args.cache.mkdir(parents=True, exist_ok=True)
    args.out.mkdir(parents=True, exist_ok=True)
    stage = args.out / "short"
    stage.mkdir(exist_ok=False)
    ranked, health = defaultdict(list), {}
    with ThreadPoolExecutor(max_workers=8) as pool:
        jobs = {source: pool.submit(download, source, args.cache) for source in sources}
        for source, job in jobs.items():
            path, summary = job.result()
            health[source.name] = summary
            if not path:
                continue
            try:
                choices, notes, detail = read_source(source, path, index, aliases, now, cutoff, horizon)
            except (OSError, ValueError, etree.XMLSyntaxError, StopIteration, EOFError) as error:
                summary.update(status="invalid-feed", error=type(error).__name__)
                continue
            summary.update(detail)
            for target, (cid, method, schedule) in choices.items():
                ranked[target].append((source.name, cid, method, schedule))
            for target, note in notes.items():
                reasons[target] = note
            print(source.name, summary["status"], len(choices), flush=True)

    contributions = defaultdict(Counter)
    additions = {}
    bindings = {}
    for target, choices in ranked.items():
        # Choose one coherent schedule per channel. Exact/verified identity
        # wins before the longest programme horizon, then configured priority.
        source, cid, method, schedule = min(
            choices, key=lambda c: (METHODS[c[2]], -max(end for _, end in c[3]).timestamp(),
                                    c[0]))
        previous = records.get(target, {})
        proposed = dict(previous)
        added = add_schedule(proposed, schedule, target)
        if not added or not all(current_next(proposed, now)):
            reasons[target] = "conflicting-programme-windows"
            continue
        if target not in channels:
            element = etree.Element("channel", id=target)
            etree.SubElement(element, "display-name").text = min(row.name for row in owners[target])
            channels[target] = etree.tostring(element)
        records[target] = proposed
        additions[target] = proposed.keys() - previous.keys()
        contributions[source].update(guide_ids=1, programmes=added)
        bindings[target] = {"source": source, "source_id": cid, "method": method}
        reasons.pop(target, None)

    # The canonical branch feeds the shared database. Keep International-only
    # supplemental schedules in its existing private Storage artifact.
    shared_ids = ({r.guide for profile in ("nz-au", "plus") for r in profiles[profile]}
                  | canonical_ids)
    shared_channels = {key: data for key, data in channels.items() if key in shared_ids}
    shared_records = {key: data for key, data in records.items() if key in shared_ids}
    # Retain existing 24/7 labels in the shared XML without treating them as
    # programme coverage or using them to match source schedules.
    shared_output_records = dict(shared_records)
    for key, labels in continuous_labels.items():
        scheduled = dict(shared_records.get(key, {}))
        if add_schedule(scheduled, labels) != len(labels):
            raise ValueError("existing 24/7 labels overlap the shared schedule")
        shared_output_records[key] = scheduled
    write_guide(args.out / "guide.xml.gz", shared_channels, shared_output_records)
    international_out_channels = dict(intl_channels)
    international_out_records = {}
    for key, old in intl_records.items():
        selected = {}
        # Resolve conflicting provider/legacy overlaps before adding schedules.
        # Prefer the window currently on air, then the earliest valid future start.
        current = {w: data for w, data in old.items() if w[0] <= now < w[1]}
        add_schedule(selected, current)
        add_schedule(selected, old)
        intl_health["overlapping_windows_removed"] = (
            intl_health.get("overlapping_windows_removed", 0) + len(old) - len(selected))
        international_out_records[key] = selected
    for key in sorted(intl_ids & channels.keys()):
        international_out_channels.setdefault(key, channels[key])
        add_schedule(international_out_records.setdefault(key, {}), records.get(key, {}))
    write_guide(args.out / "international-master.xml.gz", international_out_channels,
                international_out_records)
    for target, windows in additions.items():
        source = bindings[target]["source"]
        if target in shared_ids:
            contributions[source].update(shared_guide_ids=1, shared_programmes=len(windows))
        intl_added = sum(window in international_out_records.get(target, {})
                         and international_out_records[target][window] == records[target][window]
                         for window in windows)
        if intl_added:
            contributions[source].update(international_guide_ids=1, international_programmes=intl_added)
    # Generate Xtream short files with the existing format and namespacing.
    hashes = Counter(map(fnv1a, shared_channels))
    for target in sorted(bindings.keys() & shared_ids):
        digest = fnv1a(target)
        if hashes[digest] > 1:
            raise ValueError("short guide hash collision")
        root = etree.Element("tv")
        for _, data in sorted(records[target].items()):
            root.append(etree.fromstring(data))
        short_file(root, target, now, stage / (digest + ".json"))

    after = {}
    for name, rows in profiles.items():
        c, p = ((international_out_channels, international_out_records) if name == "international"
                else (shared_channels, shared_records))
        after[name] = coverage(rows, c, p, now, reasons)
        if any(after[name]["categories"]["TOTAL"][key] < before[name]["categories"]["TOTAL"][key]
               for key in ("mapped", "current", "next", "both")):
            raise ValueError(f"programme coverage fell for {name}")
    # Verify the complete serialized output, including every programme reference.
    for name in ("guide.xml.gz", "international-master.xml.gz"):
        retained_labels = defaultdict(dict)
        output_channels, output_records, stats = baseline(
            args.out / name, cutoff, horizon, continuous_labels=retained_labels)
        if stats.get("duplicate"):
            raise ValueError(f"duplicate programme in {name}")
        for channel in output_records.keys() | retained_labels.keys():
            windows = sorted([*output_records.get(channel, ()), *retained_labels.get(channel, ())])
            if any(end > next_start for (_, end), (next_start, _) in zip(windows, windows[1:])):
                raise ValueError(f"overlapping programme windows in {name}")
        if name == "guide.xml.gz":
            if retained_labels != continuous_labels:
                raise ValueError("canonical 24/7 labels changed")
            short_verified = verify_short_guides(stage, output_channels, output_records, now)
    report = {
        "generated_at": now.isoformat(), "elapsed_seconds": round(time.monotonic() - started, 1),
        "downloaded_source_bytes": sum(s.get("bytes", 0) for s in health.values()),
        "baseline": baseline_health, "international_baseline": intl_health,
        "international_compiled_baseline_used": bool(args.international_baseline),
        "continuous_labels_retained": sum(map(len, continuous_labels.values())),
        "short_listings_verified": short_verified,
        "guides": {"shared": {"channels": len(shared_channels), "programmes": sum(map(len, shared_output_records.values()))},
                   "international": {"channels": len(international_out_channels),
                                     "programmes": sum(map(len, international_out_records.values()))}},
        "catalogue_rows": {name: len(rows) for name, rows in profiles.items()},
        "before": before, "after": after, "source_health": health,
        "contributions": contributions, "bindings": bindings,
    }
    (args.out / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"catalogue_rows": report["catalogue_rows"], "guides": report["guides"],
                      "coverage": {name: {"before": before[name]["categories"]["TOTAL"],
                                         "after": after[name]["categories"]["TOTAL"]}
                                   for name in profiles},
                      "contributions": contributions, "seconds": report["elapsed_seconds"]}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", action="append", required=True, metavar="NAME=PATH")
    parser.add_argument("--before-profile", action="append", default=[], metavar="NAME=PATH")
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--international-baseline", type=Path)
    parser.add_argument("--previous-bulk", type=Path)
    parser.add_argument("--sources", type=Path, default=Path(__file__).with_name("bulk_sources.tsv"))
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--now")
    build(parser.parse_args())


if __name__ == "__main__":
    main()
