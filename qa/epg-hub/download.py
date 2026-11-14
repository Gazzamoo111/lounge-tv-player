#!/usr/bin/env python3
"""Bounded EPG hub downloads. Candidate input only; never publishes."""
import argparse
import gzip
import json
import re
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from urllib.parse import urlparse


def fetch(row, target):
    source_id = row["id"]
    url = row["url"]
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname or not re.fullmatch(r"[a-z0-9-]+", source_id):
        return {"id": source_id, "region": row["region"], "status": "rejected-url"}
    # The seven GitHub fixture URLs were checked against repository blob sizes.
    # A narrowly scoped compressed-size allowance avoids lifting all caps.
    reviewed_ferteque = {"ferteque-epg6", "ferteque-epg2", "ferteque-epg66",
                        "ferteque-epg52", "ferteque-epg41", "ferteque-epg63",
                        "ferteque-epg37"}
    cap = (110 if source_id == "us-iptv-epg-large" else
           48 if source_id in reviewed_ferteque else 35)
    size_limit = min(int(row.get("maxMb", 15)), cap) * 1024 * 1024
    suffix = ".xml.gz" if parsed.path.endswith(".gz") else ".xml"
    path = target / (source_id + suffix)
    temp = target / (source_id + ".part")
    written = 0
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "LoungeTV-EPG-QA/1.0",
                                                   "Accept": "application/xml,application/gzip,*/*"})
        with urllib.request.urlopen(req, timeout=55) as response, temp.open("wb") as out:
            if response.status != 200:
                raise ValueError("HTTP status not 200")
            claimed = response.headers.get("Content-Length")
            if claimed and int(claimed) > size_limit:
                raise ValueError("Source exceeds max compressed size")
            while chunk := response.read(256 * 1024):
                written += len(chunk)
                if written > size_limit:
                    raise ValueError("Source exceeded max compressed size")
                out.write(chunk)
        if written < 80:
            raise ValueError("XMLTV file too small")
        opener = gzip.open if suffix.endswith(".gz") else open
        with opener(temp, "rb") as f:
            sample = f.read(2048)
        if b"<?xml" not in sample and b"<tv" not in sample:
            raise ValueError("Not a valid XMLTV header")
        temp.replace(path)
        return {"id": source_id, "region": row["region"], "path": str(path),
                "bytes": written, "status": "ok"}
    except Exception as error:
        return {"id": source_id, "region": row["region"], "status": "unavailable",
                "error": (type(error).__name__ + ": " + str(error))[:180]}
    finally:
        if temp.exists():
            temp.unlink()


def run(registry, directory):
    config = json.loads(Path(registry).read_text())
    target = Path(directory)
    target.mkdir(parents=True, exist_ok=True)
    sources = [s for s in config["sources"] if s.get("enabled")]
    with ThreadPoolExecutor(max_workers=5) as pool:
        results = list(pool.map(lambda row: fetch(row, target), sources))
    accepted = [{k: value for k, value in r.items() if k in ("id", "region", "path")}
                for r in results if r["status"] == "ok"]
    (target / "source-list.json").write_text(json.dumps(accepted, indent=2) + "\n")
    (target / "download-report.json").write_text(json.dumps(results, indent=2) + "\n")
    summary = {"configured": len(sources), "downloaded": len(accepted),
               "failures": [r for r in results if r["status"] != "ok"]}
    print(json.dumps(summary, indent=2))
    if len(accepted) < 4:
        raise ValueError("Fewer than four usable regional feeds")
    return accepted


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--config", required=True)
    p.add_argument("--dir", required=True)
    args = p.parse_args()
    run(args.config, args.dir)
