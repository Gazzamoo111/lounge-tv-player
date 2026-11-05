#!/usr/bin/env python3
"""QA-only merge of verified XMLTV additions. Keeps channel IDs and current schedules."""
from __future__ import annotations
import argparse,base64,collections,datetime,gzip,json,pathlib,re
from lxml import etree
UTC=datetime.timezone.utc

def timestamp(value):
    if not value:return None
    v=value.strip()
    try:
        if re.match(r"^\d{14} [+-]\d{4}$",v):
            return datetime.datetime.strptime(v,"%Y%m%d%H%M%S %z")
        if re.match(r"^\d{14}$",v):
            return datetime.datetime.strptime(v,"%Y%m%d%H%M%S").replace(tzinfo=UTC)
        if re.match(r"^\d{12} [+-]\d{4}$",v):
            return datetime.datetime.strptime(v,"%Y%m%d%H%M %z")
    except ValueError:return None
    return None

def short_key(name):
    h=2166136261
    for b in name.encode("utf-8"):
        h^=b
        h=(h*16777619)&0xffffffff
    return f"{h:08x}"

def xml_line(node):
    return etree.tostring(node,encoding="unicode",with_tail=False)+"\n"

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--existing",required=True)
    ap.add_argument("--supplement",required=True)
    ap.add_argument("--coverage",required=True)
    ap.add_argument("--out",required=True)
    ap.add_argument("--now")
    args=ap.parse_args()
    now=datetime.datetime.fromisoformat(args.now.replace("Z","+00:00")) if args.now else datetime.datetime.now(UTC)
    horizon=now+datetime.timedelta(days=5)
    coverage=json.load(open(args.coverage))
    out=pathlib.Path(args.out)
    (out/"short").mkdir(parents=True,exist_ok=True)
    supplement_channels={}
    with gzip.open(args.supplement,"rb") as f:
        for _,node in etree.iterparse(f,events=("end",),tag="channel",recover=True,huge_tree=True):
            cid=node.get("id")
            if cid:supplement_channels[cid]=xml_line(node)
            node.clear()
            while node.getprevious() is not None:del node.getparent()[0]
    existing_channels={}
    with gzip.open(args.existing,"rb") as f:
        for _,node in etree.iterparse(f,events=("end",),tag="channel",recover=True,huge_tree=True):
            cid=node.get("id")
            if cid:existing_channels[cid]=xml_line(node)
            node.clear()
            while node.getprevious() is not None:del node.getparent()[0]
    supplemented_ids=set(supplement_channels)
    retained=0;added=0;new_ids=0
    seen=set()
    short=collections.defaultdict(list)
    guide=out/"guide.xml"
    with guide.open("w",encoding="utf-8") as dst:
        dst.write('<?xml version="1.0" encoding="UTF-8"?>\n<tv generator-info-name="Lounge TV canonical guide + audited supplement">\n')
        for cid,line in existing_channels.items():dst.write(line)
        for cid,line in supplement_channels.items():
            if cid not in existing_channels:
                dst.write(line);new_ids+=1
        with gzip.open(args.existing,"rb") as f:
            for _,node in etree.iterparse(f,events=("end",),tag="programme",recover=True,huge_tree=True):
                cid=node.get("channel")
                start=timestamp(node.get("start"));stop=timestamp(node.get("stop"))
                if (cid not in supplemented_ids and start and stop and
                    stop>now-datetime.timedelta(hours=3) and start<horizon and stop>start):
                    dst.write(xml_line(node));retained+=1
                node.clear()
                while node.getprevious() is not None:del node.getparent()[0]
        with gzip.open(args.supplement,"rb") as f:
            for _,node in etree.iterparse(f,events=("end",),tag="programme",recover=True,huge_tree=True):
                cid=node.get("channel")
                start=timestamp(node.get("start"));stop=timestamp(node.get("stop"))
                title=(node.findtext("title") or "").strip()
                if (cid in supplemented_ids and start and stop and
                    stop>now-datetime.timedelta(hours=3) and start<horizon and stop>start and title):
                    marker=(cid,int(start.timestamp()),int(stop.timestamp()),title)
                    if marker not in seen:
                        seen.add(marker)
                        dst.write(xml_line(node));added+=1
                        desc=(node.findtext("desc") or "").strip()
                        short[cid].append({
                            "id":f"{cid}:{int(start.timestamp())}",
                            "epg_id":cid,
                            "title":base64.b64encode(title.encode("utf-8")).decode(),
                            "lang":"en",
                            "start":start.astimezone(UTC).strftime("%Y-%m-%d %H:%M:%S"),
                            "end":stop.astimezone(UTC).strftime("%Y-%m-%d %H:%M:%S"),
                            "description":base64.b64encode(desc.encode("utf-8")).decode(),
                            "channel_id":cid,
                            "start_timestamp":int(start.timestamp()),
                            "stop_timestamp":int(stop.timestamp()),
                            "now_playing":int(start<=now<stop),
                            "has_archive":0,
                        })
                node.clear()
                while node.getprevious() is not None:del node.getparent()[0]
        dst.write("</tv>\n")
    for cid,entries in short.items():
        entries.sort(key=lambda x:x["start_timestamp"])
        (out/"short"/f"{short_key(cid)}.json").write_text(json.dumps({
            "epg_id":cid,"epg_listings":entries
        },ensure_ascii=False,separators=(",",":"))+"\n")
    with gzip.open(out/"guide.xml.gz","wb",compresslevel=6) as dst:
        with guide.open("rb") as src:
            for chunk in iter(lambda:src.read(1<<20),b""):dst.write(chunk)
    all_ids=set()
    programme_count=0
    with guide.open("rb") as f:
        for _,node in etree.iterparse(f,events=("end",),tag=("channel","programme")):
            if node.tag=="channel":
                cid=node.get("id")
                assert cid not in all_ids,f"duplicate channel: {cid}"
                all_ids.add(cid)
            else:programme_count+=1
            node.clear()
            while node.getprevious() is not None:del node.getparent()[0]
    assert programme_count==retained+added
    assert added>5000 and len(short)>100 and len(all_ids)>200,(added,len(short),len(all_ids))
    stats={
        "mergedAtUtc":now.isoformat(),
        "activeRelease":coverage["release"],
        "previousGuideChannelIds":len(existing_channels),
        "newGuideChannelIds":new_ids,
        "retainedCurrentProgrammes":retained,
        "addedProgrammes":added,
        "newShortEpgChannels":len(short),
        "totalGuideProgrammes":programme_count,
        "generatedGuideBytes":guide.stat().st_size,
        "generatedGzBytes":(out/"guide.xml.gz").stat().st_size,
        "sampleNewIds":list(short)[:20]
    }
    (out/"merge-report.json").write_text(json.dumps(stats,indent=2)+"\n")
    print(json.dumps(stats,indent=2))

if __name__=="__main__":main()
