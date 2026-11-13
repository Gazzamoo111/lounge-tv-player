"""Export the existing customer guide into bounded, immutable database import shards."""
import argparse,base64,gzip,hashlib,json
from collections import defaultdict
from datetime import datetime,timedelta,timezone
from pathlib import Path
from lxml import etree as E
from repair_missing import dt,fnv
SHARDS=16

def export(guide,revision,out,now=None):
 now=now or datetime.now(timezone.utc)
 source_revision=revision
 # An export's time window can change even while the source guide stays the same.
 # Every immutable export therefore has its own revision for resumable imports.
 revision=hashlib.sha1((source_revision+':'+now.isoformat()).encode()).hexdigest()
 root=E.parse(gzip.open(guide),E.XMLParser(no_network=True,resolve_entities=False,huge_tree=True)).getroot()
 channels={c.get('id'):E.tostring(c,encoding='unicode',with_tail=False) for c in root.findall('channel')}
 groups=defaultdict(list)
 for p in root.findall('programme'):
  start,stop=dt(p.get('start')),dt(p.get('stop'))
  if start and stop and stop>start and stop>now-timedelta(hours=12) and start<now+timedelta(days=8):groups[p.get('channel')].append((start,stop,p))
 shards=[[] for _ in range(SHARDS)]
 for cid,entries in sorted(groups.items()):
  if cid not in channels:continue
  entries.sort(key=lambda x:x[:2]); listings=[]; pieces=[channels[cid]]
  for start,stop,p in entries:
   enc=lambda s:base64.b64encode((s or '').encode()).decode()
   listings.append(dict(id=f'{cid}:{int(start.timestamp())}',channel_id=cid,epg_id=cid,title=enc(p.findtext('title')),description=enc(p.findtext('desc')),start_timestamp=int(start.timestamp()),stop_timestamp=int(stop.timestamp()),start=start.strftime('%Y-%m-%d %H:%M:%S'),end=stop.strftime('%Y-%m-%d %H:%M:%S'),has_archive=0))
   pieces.append(E.tostring(p,encoding='unicode',with_tail=False))
  shards[int(fnv(cid),16)%SHARDS].append(dict(epg_id=cid,xmltv='\n'.join(pieces),listings=listings,expires_at=max(e[1] for e in entries).isoformat(),programme_count=len(entries)))
 out=Path(out);out.mkdir(parents=True,exist_ok=True);files=[]
 for i,rows in enumerate(shards):
  data=json.dumps(dict(revision=revision,shard=i,channels=rows),separators=(',',':'),ensure_ascii=False).encode()
  name=f'{i:02d}.json.gz';(out/name).write_bytes(gzip.compress(data,mtime=0));files.append(dict(file=name,sha256=hashlib.sha256(data).hexdigest(),channels=len(rows),bytes=len(data)))
 manifest=dict(revision=revision,source_guide_revision=source_revision,generated_at=now.isoformat(),shards=files,channels=sum(len(s)for s in shards),programmes=sum(r['programme_count']for s in shards for r in s))
 for stale in out.glob('[0-9][0-9].json'):stale.unlink()
 (out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');return manifest
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--guide',required=True);p.add_argument('--revision',required=True);p.add_argument('--out',required=True);a=p.parse_args();print(json.dumps(export(a.guide,a.revision,a.out),indent=2))
