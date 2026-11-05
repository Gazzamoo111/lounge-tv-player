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
    # Country-specific EPGShare US2 is the primary cable feed; the national
    # EPGTalk guide extends several networks further into the week. The
    # combined EPGShare feed provides FAST channels omitted by those sources.
    # Each match is checked within its own source, never across regions.
    source_specs=[]
    for spec in args.source:
        label,path=spec.split("=",1)
        source_specs.append((label,path))
    if len(source_specs)<2:
        raise RuntimeError("At least two independently refreshed US sources required.")
    by_target=defaultdict(list)
    target_sub={}
    source_health={}
    now_upper=now+timedelta(days=7)
    for label,path in source_specs:
        try:
            channels=scan_source_channels(path)
            if len(channels)<100:
                raise RuntimeError(f"{len(channels)} channels, source incomplete")
            ids,names,names_region=indexes(channels)
            source_matches={}
            methods=Counter()
            for row in selected:
                target=effective_epg_id(row)
                if target in other_ids: continue
                cid,method=match(row,channels,ids,names,names_region)
                if not cid:
                    from build_supplement import name_key
                    options=names.get(name_key(row["name"]),[])
                    if len(options)==1:
                        cid,method=options[0],"unique-name-in-us-only-feed"
                if not cid: continue
                if target in source_matches and source_matches[target][0]!=cid:
                    # Multiple competing mappings of one target within a feed.
                    source_matches.pop(target,None)
                    continue
                source_matches[target]=(cid,method,row["name"])
                target_sub[target]=row.get("subcategory") or ""
                methods[method]+=1
            reverse=defaultdict(list)
            for target,(cid,_,_) in source_matches.items():
                reverse[cid].append(target)
            fresh_local=defaultdict(list)
            with gzip.open(path,"rb") as src:
                for _,node in etree.iterparse(src,events=("end",),tag="programme",
                                                recover=True,huge_tree=True):
                    targets=reverse.get(node.get("channel"))
                    if targets:
                        start,stop=dt(node.get("start")),dt(node.get("stop"))
                        title=(node.findtext("title") or "").strip()
                        if start and stop and title and stop>now and start<now_upper and stop>start:
                            serial=etree.tostring(node,with_tail=False)
                            for target in targets:
                                fresh_local[target].append((start,stop,title,serial))
                    node.clear()
                    while node.getprevious() is not None:
                        del node.getparent()[0]
            eligible=0
            for target,entries in fresh_local.items():
                dedup={(st,stop,title):(st,stop,serial)
                       for st,stop,title,serial in entries}
                ordered=sorted(dedup.values())
                if len(ordered)<2:continue
                first=min(st for st,_,_ in ordered)
                last=max(stop for _,stop,_ in ordered)
                if first>now+timedelta(hours=4) or last<now+timedelta(hours=12):
                    continue
                by_target[target].append((last,len(ordered),label,source_matches[target],ordered))
                eligible+=1
            source_health[label]={"sourceChannels":len(channels),
                                  "matched":len(source_matches),"fresh":eligible}
        except Exception as exc:
            source_health[label]={"error":str(exc)}
    repair={};matches={};source_counts=Counter()
    for target, candidates in by_target.items():
        # Prefer the source that demonstrably extends furthest ahead. A fresh
        # short source cannot overwrite a longer real programme schedule.
        candidates.sort(key=lambda x:(x[0],x[1]),reverse=True)
        last,n,label,found,entries=candidates[0]
        repair[target]=entries
        matches[target]=(*found,label)
        source_counts[label]+=1
    count=sum(len(v) for v in repair.values())
    if len(repair)<140 or count<9000:
        raise RuntimeError(f"US source coverage insufficient: {len(repair)} channels, "
                           f"{count} programmes, by source {dict(source_health)}")
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
    with gzip.open(out/"guide.xml.gz","wb",compresslevel=6) as dst:dst.write(xml)
    by_sub=Counter(target_sub.get(k,"") for k in repair)
    count=sum(len(x) for x in repair.values())
    coverage={
        "generatedAtUtc":now.isoformat(),"sources":list(source_health),
        "sourceHealth":source_health,"selectedSources":dict(source_counts),
        "region":"US only","usEligible":len(selected),
        "usMatched":len(matches),"usRepaired":len(repair),
        "usNewProgrammes":count,"usRemovedOldProgrammes":removed,
        "preservedNonUsProgrammes":len(non_us_original),
        "targetCategoryCounts":dict(by_sub),
        "usUnmatchedReasons":{},
        "usLatestProgrammeUtc":max(v[1] for values in repair.values() for v in values).isoformat(),
        "usEarliestProgrammeUtc":min(v[0] for values in repair.values() for v in values).isoformat(),
        "guideProgrammeCount":len(root.findall("programme")),
        "repairedIds":sorted(repair),
        "repairedSamples":[{"id":cid,"name":matches[cid][2],
                            "sourceId":matches[cid][0],
                            "method":matches[cid][1], "feed":matches[cid][3],
                            "programmes":len(entries)}
                           for cid,entries in list(sorted(repair.items()))[:30]]
    }
    (out/"report.json").write_text(json.dumps(coverage,indent=2)+"\n")
    print(json.dumps({k:v for k,v in coverage.items()
                      if k not in ("repairedIds","repairedSamples")},
                     indent=2))
    return coverage


if __name__=="__main__":
    ap=argparse.ArgumentParser()
    ap.add_argument("--source",required=True,action="append",help="name=XMLTV_GZ_PATH")
    ap.add_argument("--existing",required=True)
    ap.add_argument("--manifest",required=True)
    ap.add_argument("--out",required=True)
    ap.add_argument("--now")
    make(ap.parse_args())
