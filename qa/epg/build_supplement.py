#!/usr/bin/env python3
"""Generate a QA-only XMLTV supplement from EPGShare. Never modifies the live guide."""
from __future__ import annotations
import argparse
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
import gzip
import json
from pathlib import Path
import re
import sys
import unicodedata
from lxml import etree

REGIONS = {"New Zealand":"nz", "Australia":"au"}
# Only well-established Australian network numbering; never infer a fixture from a channel name.
AU_NETWORK_ALIASES = {
    "FOX SPORTS 501": "FoxCricket.au",
    "FOX SPORTS 502": "FoxLeague.au",
    "FOX SPORTS 504": "FoxFooty.au",
    "FOX SPORTS 507": "FoxSportsMore.au",
}
SKIP_CATEGORIES = {"24/7", "Streaming", "PPV"}
BAD_SUBCATEGORIES = {"Sky Store", "CINEMANIA TV SHOWS"}
BAD_NAME = re.compile(r"\b(?:replay|nonstop|24/7)\b", re.I)
QUALITY = re.compile(r"\b(?:HEVC|RAW|UHD|FHD|HD|SD|4K|8K|H265|H264|50FPS|60FPS)\b", re.I)
PREFIX = re.compile(r"^(?:(?:US|UK|NZ|AU|TV|UK-NOWTV|USA|AUS)\s*[|:]\s*)+", re.I)

def normalize(text):
    value = unicodedata.normalize("NFKD", str(text or "")).encode("ascii","ignore").decode().lower()
    return re.sub("[^a-z0-9]","",value)

def name_key(text):
    return normalize(QUALITY.sub("", PREFIX.sub("",str(text or ""))))

def region_for(row):
    if row["category"] in REGIONS:return REGIONS[row["category"]]
    sub = (row.get("subcategory") or "").lower()
    if sub.startswith("uk ") or " uk " in " "+sub+" ":return "uk"
    if sub.startswith("us ") or sub.startswith("usa "):return "us"
    m = re.match(r"^(UK|NZ|AU|US|USA|AUS)\s*[|:]\s*", (row.get("name") or "").strip(),re.I)
    if m:return {"usa":"us","aus":"au"}.get(m.group(1).lower(),m.group(1).lower())
    suffix = str(row.get("epgId") or "").rsplit(".",1)[-1].lower()
    return suffix if suffix in {"nz","au","uk","us","ie","ca"} else None

def allowed(row):
    return not (row.get("category") in SKIP_CATEGORIES or
        row.get("subcategory") in BAD_SUBCATEGORIES or BAD_NAME.search(row.get("name") or ""))

def scan_source_channels(path):
    channels={}
    with gzip.open(path,"rb") as src:
        for _,node in etree.iterparse(src,events=("end",),tag=("channel","programme"),
                                         recover=True,huge_tree=True):
            if node.tag=="programme":break
            cid=node.get("id")
            if cid and ".dummy." not in cid.casefold():
                label=next((n.text for n in node.iter("display-name") if n.text),"")
                channels[cid]=label
            node.clear()
            while node.getprevious() is not None:del node.getparent()[0]
    return channels

def indexes(channels):
    normalized_ids=defaultdict(list);names=defaultdict(list);names_region=defaultdict(list)
    for cid,name in channels.items():
        normalized_ids[normalize(cid)].append(cid)
        names[name_key(name)].append(cid)
        names_region[(cid.rsplit(".",1)[-1].lower(),name_key(name))].append(cid)
    return normalized_ids,names,names_region

def match(row,channels,ids,names,names_region):
    target=str(row.get("epgId") or "").strip()
    if target and target in channels:return target,"exact-id"
    region=region_for(row)
    # Explicit Australian network-number equivalences have been checked separately.
    named=str(row.get("name") or "").strip().upper()
    if region=="au" and named in AU_NETWORK_ALIASES:
        cid=AU_NETWORK_ALIASES[named]
        if cid in channels:return cid,"reviewed-au-network"
    if target:
        choices=ids.get(normalize(target),[])
        choices=[x for x in choices if not region or x.rsplit(".",1)[-1].lower()==region]
        if len(choices)==1:return choices[0],"normalized-id"
    name=name_key(row.get("name") or "")
    if len(name)<4:return None,"too-short"
    if region:
        choices=names_region.get((region,name),[])
        if len(choices)==1:return choices[0],"exact-name-region"
        if len(choices)>1:return None,"ambiguous-region"
        # Named FAST services often use their own service namespace, such as .plex.
        if region=="us" and (row.get("subcategory") or "")=="US FAST TV":
            choices=names.get(name,[])
            safe=[cid for cid in choices if cid.rsplit(".",1)[-1].lower() in
                  {"plex","us","us2","us_locals1","pluto","samsung"}]
            if len(safe)==1:return safe[0],"us-fast-brand"
        return None,"wrong-or-missing-region"
    # Do not reuse country-specific linear networks without a known market.
    # Regionless, exact-brand FAST channels alone may safely use an independent namespace.
    choices=names.get(name,[])
    if len(choices)==1 and choices[0].rsplit(".",1)[-1].lower() in {"plex","pluto","samsung"}:
        return choices[0],"unique-fast-brand"
    return None,"ambiguous-or-missing"

def baseline_schedule(path,targets,now,horizon):
    if not path:return Counter()
    counts=Counter()
    with gzip.open(path,"rb") as f:
        for _,node in etree.iterparse(f,events=("end",),tag="programme",recover=True,huge_tree=True):
            cid=node.get("channel")
            if cid in targets:
                start=node.get("start","")
                if len(start)>=14:
                    try:
                        st=datetime.strptime(start[:14],"%Y%m%d%H%M%S").replace(tzinfo=timezone.utc)
                        if now-timedelta(hours=1)<=st<horizon:counts[cid]+=1
                    except ValueError:pass
            node.clear()
            while node.getprevious() is not None:del node.getparent()[0]
    return counts

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--source",required=True)
    ap.add_argument("--manifest",required=True)
    ap.add_argument("--existing-guide")
    ap.add_argument("--out",default="qa/epg/out")
    ap.add_argument("--now")
    args=ap.parse_args()
    now=datetime.now(timezone.utc) if not args.now else datetime.fromisoformat(args.now.replace("Z","+00:00"))
    horizon=now+timedelta(hours=72)
    payload=json.loads(Path(args.manifest).read_text());rows=payload["rows"]
    channels=scan_source_channels(args.source)
    ids,names,names_region=indexes(channels)
    reasons=Counter();matches={};wanted={}
    for row in rows:
        if not allowed(row):reasons["excluded-nonscheduled"]+=1;continue
        target=str(row.get("epgId") or row["key"]).strip()
        cid,reason=match(row,channels,ids,names,names_region)
        reasons[reason]+=1
        if cid:
            matches[target]={"source":cid,"method":reason,"name":row["name"],"category":row["category"]}
            wanted[target]=cid
    baseline=baseline_schedule(args.existing_guide,set(wanted),now,horizon)
    missing={t:src for t,src in wanted.items() if baseline[t]<2}
    reverse=defaultdict(list)
    for target,src in missing.items():reverse[src].append(target)
    out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
    programmes=Counter();earliest=None;latest=None
    with gzip.open(out/"guide-supplement.xml.gz","wt",encoding="utf-8",compresslevel=6) as dst:
        dst.write('<?xml version="1.0" encoding="UTF-8"?>\n<tv generator-info-name="Lounge TV EPG QA Supplement">\n')
        for target in missing:
            ch=etree.Element("channel",id=target)
            etree.SubElement(ch,"display-name").text=matches[target]["name"]
            dst.write(etree.tostring(ch,encoding="unicode")+"\n")
        with gzip.open(args.source,"rb") as f:
            for _,node in etree.iterparse(f,events=("end",),tag="programme",recover=True,huge_tree=True):
                targets=reverse.get(node.get("channel"))
                if targets:
                    start=node.get("start","");stop=node.get("stop","")
                    try:
                        s=datetime.strptime(start[:14],"%Y%m%d%H%M%S").replace(tzinfo=timezone.utc)
                        e=datetime.strptime(stop[:14],"%Y%m%d%H%M%S").replace(tzinfo=timezone.utc)
                    except ValueError:s=e=now
                    if e>now and s<horizon and e>s:
                        for target in targets:
                            node.set("channel",target)
                            dst.write(etree.tostring(node,encoding="unicode")+"\n")
                            programmes[target]+=1
                        earliest=s if earliest is None else min(earliest,s)
                        latest=e if latest is None else max(latest,e)
                node.clear()
                while node.getprevious() is not None:del node.getparent()[0]
        dst.write("</tv>\n")
    report={"generatedAtUtc":now.isoformat(),"release":payload["activeRelease"],
        "sourceChannels":len(channels),"releaseRows":len(rows),"eligibleRows":sum(bool(allowed(x)) for x in rows),
        "matchingMethods":dict(reasons),"matchedTargets":len(matches),
        "baselineWithUpcoming":sum(baseline[t]>=2 for t in wanted),
        "targetsNeedingSupplement":len(missing),
        "supplementedChannels":sum(v>0 for v in programmes.values()),
        "supplementedProgrammes":sum(programmes.values()),
        "earliestProgrammeUtc":earliest.isoformat() if earliest else None,
        "latestProgrammeUtc":latest.isoformat() if latest else None,
        "unmatchedExamples":[{"name":r["name"],"category":r["category"],"epgId":r.get("epgId")}
             for r in rows if allowed(r) and str(r.get("epgId") or r["key"]) not in wanted][:90],
        "supplementSamples":[{**matches[t],"target":t,"programmeCount":programmes[t]}
             for t in list(programmes)[:40]]}
    (out/"coverage.json").write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps({k:v for k,v in report.items()
        if k not in ("unmatchedExamples","supplementSamples")},indent=2))
    if len(channels)<10000 or sum(programmes.values())<500:
        sys.exit("QA FAILED: insufficient source/usable current listings; no publication")

if __name__=="__main__":main()
