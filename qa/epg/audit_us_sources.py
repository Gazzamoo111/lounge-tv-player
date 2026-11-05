#!/usr/bin/env python3
"""Audit real, future US EPG coverage by Lounge TV manifest rows.

No EPG merge, no publication, no catalogue writes.
"""
from __future__ import annotations
import argparse, gzip, json, re
from collections import Counter, defaultdict
from datetime import datetime, timezone, timedelta
from pathlib import Path
from lxml import etree
from build_supplement import (scan_source_channels, indexes, match, allowed,
                              region_for, effective_epg_id, name_key, normalize)
from seven_day_targeted import dt
UTC=timezone.utc
def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--manifest",required=True)
    ap.add_argument("--source",action="append",default=[],help="name=source.gz")
    ap.add_argument("--out",required=True)
    args=ap.parse_args()
    now=datetime.now(UTC)
    rows=json.loads(Path(args.manifest).read_text())["rows"]
    targets=[r for r in rows if allowed(r) and region_for(r)=="us"]
    report={"when":now.isoformat(),"usManifestCount":len(targets),"sources":{}}
    for entry in args.source:
        label,path=entry.split("=",1)
        try:
            channels=scan_source_channels(path)
            ids,names,names_region=indexes(channels)
            matched=defaultdict(list)
            for row in targets:
                cid,method=match(row,channels,ids,names,names_region)
                if not cid:
                    normalized=name_key(row["name"])
                    options=names.get(normalized,[])
                    if len(options)==1:
                        cid,method=options[0],"unique-name-in-us-only-feed"
                if cid:
                    matched[cid].append((effective_epg_id(row),row.get("subcategory"),row["name"],method))
            prog=Counter()
            latest=defaultdict(lambda:now)
            earliest=defaultdict(lambda:now+timedelta(days=100))
            with gzip.open(path,"rb") as stream:
                for _,node in etree.iterparse(stream,events=("end",),tag="programme",
                                                 recover=True,huge_tree=True):
                    cid=node.get("channel")
                    if cid in matched:
                        a,b=dt(node.get("start")),dt(node.get("stop"))
                        if a and b and b>now and a<now+timedelta(days=8) and b>a and (node.findtext("title") or "").strip():
                            prog[cid]+=1
                            latest[cid]=max(latest[cid],b)
                            earliest[cid]=min(earliest[cid],a)
                    node.clear()
                    while node.getprevious() is not None:del node.getparent()[0]
            good=[cid for cid in matched if prog[cid]>=2 and earliest[cid]<now+timedelta(hours=4)
                  and latest[cid]>now+timedelta(hours=12)]
            kinds=Counter(kind for cid in good for _,kind,_,_ in matched[cid])
            fullweek=[cid for cid in good if latest[cid]>=now+timedelta(days=7)]
            samples=[{"id":cid,"target":targets[0],"method":method,
                      "programmes":prog[cid],"end":latest[cid].isoformat()}
                     for cid in good[:0] for targets in [matched[cid]] for _,_,_,method in targets[:1]]
            representative=[{"id":cid,"channel":matched[cid][0][2],
                             "sub":matched[cid][0][1],"programmes":prog[cid],
                             "last":latest[cid].isoformat()}
                            for cid in good[:35]]
            report["sources"][label]={"sourceChannels":len(channels),
                "matchedChannelIds":len(matched),
                "freshChannelIds":len(good),
                "sevenDayChannelIds":len(fullweek),
                "freshPrograms":sum(prog[cid] for cid in good),
                "freshBySubcategory":dict(kinds),
                "examples":representative}
            print(label,json.dumps({k:v for k,v in report["sources"][label].items()
                                     if k!="examples"}))
        except Exception as exc:
            report["sources"][label]={"error":str(exc)}
            print(label,"ERROR",repr(exc))
    Path(args.out).write_text(json.dumps(report,indent=2)+"\n")
if __name__=="__main__":main()
