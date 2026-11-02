#!/usr/bin/env python3
"""Build the Lounge TV canonical XMLTV guide outside the live request path."""

from __future__ import annotations

import copy
import datetime as dt
import gzip
import json
import re
import shutil
import tempfile
import unicodedata
import urllib.request
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGETS_FILE = ROOT / "backend" / "epg" / "canonical-targets.json"
OUT_DIR = ROOT / "backend" / "epg" / "generated"
GUIDE_XML = OUT_DIR / "guide.xml"
GUIDE_GZ = OUT_DIR / "guide.xml.gz"
COVERAGE_JSON = OUT_DIR / "coverage.json"

EPGSHARE = "https://epgshare01.online/epgshare01"
SOURCES = [
    ("NZ", f"{EPGSHARE}/epg_ripper_NZ1.xml.gz"),
    ("AU", f"{EPGSHARE}/epg_ripper_AU1.xml.gz"),
    ("UK", f"{EPGSHARE}/epg_ripper_UK1.xml.gz"),
    ("US", f"{EPGSHARE}/epg_ripper_US_SPORTS1.xml.gz"),
    ("US", f"{EPGSHARE}/epg_ripper_US2.xml.gz"),
]
FREEVIEW_URL = "https://i.mjh.nz/nz/epg.xml.gz"
FREEVIEW_IDS = {
    "mjh-tvnz-1",
    "mjh-tvnz-2",
    "mjh-three",
    "mjh-maori-tv",
    "mjh-te-reo",
}

COUNTRY_SUFFIX = re.compile(r"\.(?:us2|us|uk|au|nz)$", re.I)
NOISE = {
    "hd", "fhd", "uhd", "sd", "4k", "raw", "feed", "channel", "network",
    "television", "tv",
}
AU_MARKETS = {"sydney", "melbourne", "brisbane", "adelaide", "perth", "hobart"}
LOOSE_DIRECTIONS = {"e", "w", "pacific", "atlantic"}
WORD_REPLACEMENTS = {
    "one": "1",
    "two": "2",
    "three": "3",
    "four": "4",
    "five": "5",
    "north": "n",
    "south": "s",
    "east": "e",
    "west": "w",
    "midlands": "mid",
    "london": "lon",
    "scotland": "scot",
    "wales": "wal",
    "premiere": "prem",
    "premier": "prem",
    "sports": "sport",
    "football": "fball",
}
CAMEL = re.compile(r"([a-z])([A-Z])")
TOKEN = re.compile(r"[^a-z0-9]+")


def local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def ascii_text(value: str) -> str:
    return "".join(
        ch for ch in unicodedata.normalize("NFKD", value)
        if not unicodedata.combining(ch)
    )


def norm(raw: str, loose: bool = False) -> str:
    s = ascii_text(raw or "")
    s = COUNTRY_SUFFIX.sub("", s)
    s = CAMEL.sub(r"\1 \2", s)
    s = s.replace("+", " plus ").replace("&", " and ").lower()
    s = re.sub(r"\bsky\s*sp\b", "sky sport", s)
    s = re.sub(r"\bskysp\b", "sky sport", s)
    s = re.sub(r"\bmain\s*event\b", "main ev", s)
    s = re.sub(r"\bmainevent\b", "main ev", s)

    tokens = [t for t in TOKEN.split(s) if t]
    cooked: list[str] = []
    for token in tokens:
        token = WORD_REPLACEMENTS.get(token, token)
        if token in NOISE:
            continue
        if loose and (token in AU_MARKETS or token in LOOSE_DIRECTIONS):
            continue
        cooked.append(token)
    return "".join(cooked)


def keys(values: list[str], loose: bool = False) -> set[str]:
    return {k for value in values if (k := norm(value, loose)) and len(k) >= 2}


def region_of(target: dict) -> str:
    epg_id = target["id"].lower()
    category = target.get("category", "")
    subcategory = target.get("subcategory", "")
    if epg_id.endswith(".nz") or category == "New Zealand":
        return "NZ"
    if epg_id.endswith(".au") or category == "Australia":
        return "AU"
    if epg_id.endswith(".uk") or re.search(r"\bUK\b", subcategory, re.I):
        return "UK"
    if epg_id.endswith(".us") or re.search(r"\bUS\b", subcategory, re.I):
        return "US"
    return "OTHER"


def build_indexes(targets: list[dict]):
    strict: dict[str, list[dict]] = defaultdict(list)
    loose: dict[str, list[dict]] = defaultdict(list)
    for target in targets:
        values = [target["id"], target["name"]]
        for key in keys(values, False):
            if all(x["id"] != target["id"] for x in strict[key]):
                strict[key].append(target)
        for key in keys(values, True):
            if all(x["id"] != target["id"] for x in loose[key]):
                loose[key].append(target)
    return strict, loose


def source_names(channel: ET.Element) -> list[str]:
    out: list[str] = []
    for child in channel:
        if local(child.tag) == "display-name" and child.text:
            value = child.text.strip()
            if value:
                out.append(value)
    return out


def match_channel(source_id: str, names: list[str], targets: list[dict]) -> tuple[dict, int] | None:
    strict, loose = build_indexes(targets)
    source_values = [source_id, *names]
    source_strict = keys(source_values, False)
    source_loose = keys(source_values, True)

    for key in source_strict:
        hits = strict.get(key, [])
        if len(hits) == 1:
            target = hits[0]
            score = 100
            if norm(source_id, False) == norm(target["id"], False):
                score += 40
            if norm(target["name"], False) in source_strict:
                score += 20
            return target, score

    for key in source_loose:
        hits = loose.get(key, [])
        if len(hits) == 1:
            target = hits[0]
            score = 60
            if norm(source_id, True) == norm(target["id"], True):
                score += 20
            return target, score
    return None


def download(url: str, dest: Path) -> None:
    req = urllib.request.Request(url, headers={"User-Agent": "LoungeTV-EPG-Builder/1.0"})
    with urllib.request.urlopen(req, timeout=60) as response, dest.open("wb") as out:
        shutil.copyfileobj(response, out)


def choose_mappings(gz_path: Path, targets: list[dict], already: set[str]) -> dict[str, str]:
    wanted = [t for t in targets if t["id"] not in already]
    if not wanted:
        return {}

    best: dict[str, tuple[str, int]] = {}
    with gzip.open(gz_path, "rb") as source:
        for _, elem in ET.iterparse(source, events=("end",)):
            if local(elem.tag) == "channel":
                source_id = elem.attrib.get("id", "")
                found = match_channel(source_id, source_names(elem), wanted)
                if found:
                    target, score = found
                    current = best.get(target["id"])
                    if current is None or score > current[1]:
                        best[target["id"]] = (source_id, score)
                elem.clear()
            elif local(elem.tag) == "programme":
                elem.clear()

    mapping = {source_id: target_id for target_id, (source_id, _) in best.items()}
    already.update(best)
    return mapping


def parse_xmltv_time(raw: str) -> dt.datetime | None:
    if not raw or len(raw) < 14:
        return None
    try:
        base = dt.datetime.strptime(raw[:14], "%Y%m%d%H%M%S")
        m = re.search(r"([+-])(\d{2})(\d{2})", raw[14:])
        if not m:
            return base.replace(tzinfo=dt.timezone.utc)
        minutes = int(m.group(2)) * 60 + int(m.group(3))
        if m.group(1) == "-":
            minutes = -minutes
        tz = dt.timezone(dt.timedelta(minutes=minutes))
        return base.replace(tzinfo=tz).astimezone(dt.timezone.utc)
    except ValueError:
        return None


def write_programmes(gz_path: Path, mapping: dict[str, str], out, now: dt.datetime) -> int:
    if not mapping:
        return 0
    earliest = now - dt.timedelta(hours=12)
    latest = now + dt.timedelta(days=4)
    count = 0
    with gzip.open(gz_path, "rb") as source:
        for _, elem in ET.iterparse(source, events=("end",)):
            if local(elem.tag) == "programme":
                source_id = elem.attrib.get("channel", "")
                target_id = mapping.get(source_id)
                if target_id:
                    start = parse_xmltv_time(elem.attrib.get("start", ""))
                    stop = parse_xmltv_time(elem.attrib.get("stop", ""))
                    if (stop is None or stop > earliest) and (start is None or start < latest):
                        elem.attrib["channel"] = target_id
                        out.write(ET.tostring(elem, encoding="unicode"))
                        out.write("\n")
                        count += 1
                elem.clear()
            elif local(elem.tag) == "channel":
                elem.clear()
    return count


def extract_freeview(gz_path: Path):
    channels: dict[str, str] = {}
    programmes: list[str] = []
    now = dt.datetime.now(dt.timezone.utc)
    earliest = now - dt.timedelta(hours=12)
    latest = now + dt.timedelta(days=4)
    with gzip.open(gz_path, "rb") as source:
        for _, elem in ET.iterparse(source, events=("end",)):
            tag = local(elem.tag)
            if tag == "channel":
                cid = elem.attrib.get("id", "")
                if cid in FREEVIEW_IDS:
                    channels[cid] = ET.tostring(elem, encoding="unicode")
                elem.clear()
            elif tag == "programme":
                cid = elem.attrib.get("channel", "")
                if cid in FREEVIEW_IDS:
                    start = parse_xmltv_time(elem.attrib.get("start", ""))
                    stop = parse_xmltv_time(elem.attrib.get("stop", ""))
                    if (stop is None or stop > earliest) and (start is None or start < latest):
                        programmes.append(ET.tostring(elem, encoding="unicode"))
                elem.clear()
    return channels, programmes


def main() -> None:
    cfg = json.loads(TARGETS_FILE.read_text())
    targets: list[dict] = cfg["targets"]
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    matched: set[str] = set()
    mappings: list[tuple[str, Path, dict[str, str]]] = []
    now = dt.datetime.now(dt.timezone.utc)

    with tempfile.TemporaryDirectory(prefix="lounge-epg-") as td:
        tmp = Path(td)

        for index, (region, url) in enumerate(SOURCES):
            path = tmp / f"{index}-{region}.xml.gz"
            download(url, path)

            regional = [t for t in targets if region_of(t) == region]
            mapping = choose_mappings(path, regional, matched)

            residual = [t for t in targets if region_of(t) == "OTHER" and t["id"] not in matched]
            if residual:
                mapping.update(choose_mappings(path, residual, matched))

            mappings.append((region, path, mapping))

        freeview_path = tmp / "freeview.xml.gz"
        download(FREEVIEW_URL, freeview_path)
        freeview_channels, freeview_programmes = extract_freeview(freeview_path)

        target_by_id = {t["id"]: t for t in targets}
        programme_count = 0

        with GUIDE_XML.open("w", encoding="utf-8", newline="\n") as out:
            out.write('<?xml version="1.0" encoding="UTF-8"?>\n')
            out.write('<tv generator-info-name="Lounge TV Canonical EPG">\n')

            for target in targets:
                if target["id"] not in matched:
                    continue
                tid = target["id"].replace("&", "&amp;").replace('"', "&quot;")
                name = target["name"].replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                out.write(f'<channel id="{tid}"><display-name>{name}</display-name></channel>\n')

            for cid in sorted(freeview_channels):
                out.write(freeview_channels[cid])
                out.write("\n")

            for region, path, mapping in mappings:
                programme_count += write_programmes(path, mapping, out, now)

            for block in freeview_programmes:
                out.write(block)
                out.write("\n")
                programme_count += 1

            out.write("</tv>\n")

        with GUIDE_XML.open("rb") as src, gzip.open(GUIDE_GZ, "wb", compresslevel=9) as dst:
            shutil.copyfileobj(src, dst)

        unmatched = [t for t in targets if t["id"] not in matched]
        by_region: dict[str, dict[str, int]] = {}
        for region in ["NZ", "AU", "UK", "US", "OTHER"]:
            region_targets = [t for t in targets if region_of(t) == region]
            by_region[region] = {
                "targets": len(region_targets),
                "matched": sum(t["id"] in matched for t in region_targets),
            }

        coverage = {
            "release": cfg.get("release"),
            "generated_at": now.isoformat(),
            "targets": len(targets),
            "matched": len(matched),
            "unmatched": len(unmatched),
            "programmes": programme_count,
            "freeview_channels": len(freeview_channels),
            "by_region": by_region,
            "unmatched_ids": [t["id"] for t in unmatched],
        }
        COVERAGE_JSON.write_text(json.dumps(coverage, indent=2) + "\n")
        print(json.dumps(coverage, indent=2))


if __name__ == "__main__":
    main()
