#!/usr/bin/env python3
"""Repair expired US EPG only; do not change AU/UK/NZ/24-7 programmes or catalogue.

Uses EPGShare's current programme data and existing, audited region matcher.
Never invents programme titles and never rewrites non-US programme entries.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
import gzip
import json
from pathlib import Path

from lxml import etree
from build_supplement import (scan_source_channels, indexes, match, allowed,
                              region_for, effective_epg_id)
from seven_day_targeted import dt, fnv1a, short_file

UTC=timezone.utc


def make(args):
    now=(datetime.fromisoformat(args.now.replace("Z","+00:00"))
         if args.now else datetime.now(UTC)).astimezone(UTC)
    end=now+timedelta(days=7)
    payload=json.loads(Path(args.manifest).read_text())
    rows=payload["rows"]
    selected=[r for r in rows if allowed(r) and region_for(r)=="us"]
    # Shared EPG IDs must never be replaced by a US-only process.
    other_ids={effective_epg_id(r) for r in rows if region_for(r)!="us"}
    source_channels=scan_source_channels(args.source)
    if len(source_channels)<10000:
        raise RuntimeError(f"Source incomplete ({len(source_channels)} channels). Not publishing.")
    ids,names,names_region=indexes(source_channels)
    matches={}; rejected=Counter(); target_sub={}
    for row in selected:
        target=effective_epg_id(row)
        if target in other_ids:
            rejected["shared-with-non-us"]+=1
            continue
        cid,method=match(row,source_channels,ids,names,names_region)
        if cid:
            if target in matches and matches[target][0]!=cid:
                rejected["ambiguous-target"]+=1
                del matches[target]
                continue
            matches[target]=(cid,method,row["name"])
            target_sub[target]=row.get("subcategory") or ""
        else:
            rejected[method]+=1
    reverse=defaultdict(list)
    for target,(cid,_,_) in matches.items():
        reverse[cid].append(target)

    # Programme scan is streaming: the full 28k-channel guide is never
    # expanded in memory. XMLTV +/-hhmm offsets are converted correctly.
    fresh=defaultdict(list)
    with gzip.open(args.source,"rb") as src:
        for _, node in etree.iterparse(src,events=("end",),tag="programme",
                                        recover=True,huge_tree=True):
            wanted=reverse.get(node.get("channel"))
            if wanted:
                start,stop=dt(node.get("start")),dt(node.get("stop"))
                title=(node.findtext("title") or "").strip()
                if start and stop and title and stop>now and start<end and stop>start:
                    serial=etree.tostring(node,with_tail=False)
                    for target in wanted:
                        fresh[target].append((start,stop,title,serial))
            node.clear()
            while node.getprevious() is not None:
                del node.getparent()[0]
    repair={}
    for target,entries in fresh.items():
        unique={}
        for st,stop,title,raw in entries:
            unique[(st,stop,title)]=(st,stop,raw)
        entries=sorted(unique.values())
        if len(entries)<2: continue
        if min(st for st,_,_ in entries)>now+timedelta(hours=4): continue
        if max(stop for _,stop,_ in entries)<now+timedelta(hours=12): continue
        repair[target]=entries

    by_sub=Counter(target_sub.get(k,"") for k in repair)
    count=sum(len(x) for x in repair.values())
    # A one-feed outage must not wipe the US guide. Reject partial replacements.
    if len(repair)<40 or count<1000:
        raise RuntimeError(f"US provider incomplete: {len(repair)} channels, "
                           f"{count} programmes. Publication stopped.")
    root=etree.parse(gzip.open(args.existing,"rb"),
                     etree.XMLParser(no_network=True,resolve_entities=False,
                                     huge_tree=True)).getroot()
    original_prog=root.findall("programme")
    non_us_original=[etree.tostring(p,with_tail=False) for p in original_prog
                     if p.get("channel") not in repair]
    removed=0
    for p in original_prog:
        if p.get("channel") in repair:
            root.remove(p);removed+=1
    channel_ids={c.get("id") for c in root.findall("channel")}
    for target,entries in repair.items():
        if target not in channel_ids:
            ch=etree.Element("channel",id=target)
            etree.SubElement(ch,"display-name").text=matches[target][2]
            root.insert(0,ch)
            channel_ids.add(target)
        for _,_,raw in entries:
            p=etree.fromstring(raw)
            p.set("channel",target)
            root.append(p)
    non_us_after=[etree.tostring(p,with_tail=False) for p in root.findall("programme")
                  if p.get("channel") not in repair]
    if non_us_after!=non_us_original:
        raise RuntimeError("Non-US EPG preservation failed. No publication.")
    out=Path(args.out);(out/"short").mkdir(parents=True,exist_ok=True)
    for target in repair:
        n=short_file(root,target,now,out/"short"/f"{fnv1a(target)}.json")
        if n<2:raise RuntimeError(f"No current short EPG generated for {target}")
    xml=etree.tostring(root,encoding="UTF-8",xml_declaration=True)
    (out/"guide.xml").write_bytes(xml)
    with gzip.open(out/"guide.xml.gz","wb",compresslevel=6) as f:f.write(xml)
    coverage={
        "generatedAtUtc":now.isoformat(),"source":args.source,
        "region":"US only","usEligible":len(selected),
        "usMatched":len(matches),"usRepaired":len(repair),
        "usNewProgrammes":count,"usRemovedOldProgrammes":removed,
        "preservedNonUsProgrammes":len(non_us_original),
        "targetCategoryCounts":dict(by_sub),
        "usUnmatchedReasons":dict(rejected),
        "usLatestProgrammeUtc":max(v[1] for values in repair.values() for v in values).isoformat(),
        "usEarliestProgrammeUtc":min(v[0] for values in repair.values() for v in values).isoformat(),
        "guideProgrammeCount":len(root.findall("programme")),
        "repairedIds":sorted(repair),
        "repairedSamples":[{"id":cid,"name":matches[cid][2],
                            "sourceId":matches[cid][0],
                            "method":matches[cid][1],
                            "programmes":len(entries)}
                           for cid,entries in list(sorted(repair.items()))[:30]]
    }
    (out/"report.json").write_text(json.dumps(coverage,indent=2)+"\n")
    print(json.dumps({k:v for k,v in coverage.items()
                      if k not in ("source","repairedIds","repairedSamples")},
                     indent=2))
    return coverage


if __name__=="__main__":
    ap=argparse.ArgumentParser()
    ap.add_argument("--source",required=True)
    ap.add_argument("--existing",required=True)
    ap.add_argument("--manifest",required=True)
    ap.add_argument("--out",required=True)
    ap.add_argument("--now")
    make(ap.parse_args())
