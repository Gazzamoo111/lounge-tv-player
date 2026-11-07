#!/usr/bin/env python3
"""Append verified US/UK schedules; preserve all existing programme records and catalogue."""
import argparse,base64,copy,gzip,hashlib,json,re,unicodedata
from collections import defaultdict,Counter
from datetime import datetime,timedelta,timezone
from pathlib import Path
from lxml import etree as E
UTC=timezone.utc

def dt(raw):
 try:return datetime.strptime(raw.strip(),'%Y%m%d%H%M%S %z').astimezone(UTC)
 except (ValueError,AttributeError):return None

def key(s):
 s=unicodedata.normalize('NFKC',s or '').lower()
 s=re.sub(r'^(us|uk|usa)\s*[-|:]\s*','',s)
 s=re.sub(r'\b(uhd|fhd|hd|sd|4k|raw|hevc)\b','',s)
 s=s.replace('pacific','west')
 return re.sub(r'[^a-z0-9+]','',s).replace('disneyjr','disneyjunior')

def source_key(s,region):
 s=re.sub(r'\bUS\s*(?:HD)?$','',s,flags=re.I) if region=='us' else s
 return key(s)

def region(c):
 if c.get('region') in ('us','uk'):return c['region']
 for value in [c.get('epg_id',''),c.get('subcategory','')]:
  m=re.match(r'^(us|uk)(?:[ .|:-]|$)',value or '',re.I)
  if m:return m[1].lower()
 return None

def eligible(c):
 return c['category'] not in ('24/7','Streaming','PPV','New Zealand','Australia') and c['subcategory'] not in ('CINEMANIA TV SHOWS','Sky Store') and region(c) in ('us','uk')

def scoped_name(name,reg,label):
 # EPGTalk UK includes some non-UK stations. Generic unqualified names are unsafe.
 if not label.startswith('talk'):return True
 if reg=='us':return True
 return bool(re.match(r'^UK\s*[-|:]',name,re.I) or re.match(r'^(Sky Sports|Sky Cinema|Sky History|BBC One|BBC Two|BBC Three|BBC Four)',name,re.I) or '+1' in name or key(name) in {'dave','drama','alibi','w','gold','really','yesterday','e4','more4','4seven','film4','itv1','itv2','itv3','itv4','itvbe','channel4','channel5','5usa','5star'})

def fnv(s):
 h=2166136261
 for b in s.encode():h=((h^b)*16777619)&0xffffffff
 return f'{h:08x}'

def short(entries,target,path,now):
 out=[]
 for p in entries:
  a,b=dt(p.get('start')),dt(p.get('stop'))
  if not a or not b or b<=now-timedelta(minutes=15):continue
  enc=lambda s:base64.b64encode((s or '').encode()).decode()
  out.append(dict(id=f'{target}:{int(a.timestamp())}',epg_id=target,title=enc(p.findtext('title')),description=enc(p.findtext('desc')),start=a.strftime('%Y-%m-%d %H:%M:%S'),end=b.strftime('%Y-%m-%d %H:%M:%S'),start_timestamp=int(a.timestamp()),stop_timestamp=int(b.timestamp()),now_playing=int(a<=now<b),has_archive=0,channel_id=target,lang='en'))
 out.sort(key=lambda p:p['start_timestamp']);path.write_text(json.dumps(dict(epg_id=target,epg_listings=out),separators=(',',':'))+'\n')

def make(manifest,existing,sources,out,now=None):
 now=now or datetime.now(UTC);end=now+timedelta(days=7)
 rows=[r for r in json.loads(Path(manifest).read_text())['rows'] if eligible(r)]
 targets=defaultdict(list)
 for c in rows:targets[c['epgId']].append(c)
 root=E.parse(gzip.open(existing,'rb'),E.XMLParser(no_network=True,resolve_entities=False,huge_tree=True)).getroot()
 original=[E.tostring(n,with_tail=False) for n in root.findall('programme')]
 original_channels={n.get('id') for n in root.findall('channel')}
 baseline=defaultdict(list)
 for n in root.findall('programme'):baseline[n.get('channel')].append(n)
 options=defaultdict(list);health=[]
 for label,reg,path in sources:
  channels={};programmes=defaultdict(list)
  with gzip.open(path,'rb')as f:
   for _,n in E.iterparse(f,events=['end'],tag=['channel','programme'],huge_tree=True,resolve_entities=False,no_network=True):
    if n.tag=='channel':channels[n.get('id')]=n.findtext('display-name','')
    else:
     a,b=dt(n.get('start')),dt(n.get('stop'));title=n.findtext('title','').strip()
     if a and b and now<b and a<end and b>a and title and not re.search(r'no (?:programme|program) (?:data|information)|to be announced|dummy',title,re.I):programmes[n.get('channel')].append((a,b,title,E.tostring(n,with_tail=False)))
    n.clear()
    while n.getprevious()is not None:del n.getparent()[0]
  by_name=defaultdict(list)
  for cid,name in channels.items():
   if programmes.get(cid) and scoped_name(name,reg,label):by_name[source_key(name,reg)].append(cid)
  matched=0
  for target,cs in targets.items():
   if any(region(c)!=reg for c in cs):continue
   # Explicit country-qualified identifiers take precedence over name aliases.
   candidates=[target] if target in channels and programmes.get(target) and target.lower().endswith('.'+reg) else sorted(set(cid for c in cs for cid in by_name.get(key(c['name']),[])))
   if not candidates:continue
   if any('PBS' in c['name'].upper() and any(t in (c.get('epg_id') or '').lower() for t in ('seattle','kcts','boston','new york')) for c in cs):continue
   viable=[cid for cid in candidates if any(a<=now<b for a,b,_,_ in programmes[cid]) and len(programmes[cid])>=2]
   if not viable:continue
   # Duplicate SD/HD sources must agree over the next day. Different regions/delays stay separate.
   fingerprints={tuple(sorted((a,b,t)for a,b,t,_ in programmes[cid] if a<now+timedelta(days=1)))for cid in viable}
   if len(fingerprints)>1:continue
   cid=max(viable,key=lambda c:max(b for _,b,_,_ in programmes[c]));entries=programmes[cid]
   options[target].append((max(b for _,b,_,_ in entries),label,cid,channels[cid],entries,reg));matched+=1
  health.append(dict(source=label,region=reg,channels=len(channels),matched=matched))
 # Bound retained history for validated targets; keep all other data untouched.
 expired=[n for n in root.findall('programme') if n.get('channel') in options and dt(n.get('stop')) and dt(n.get('stop')) < now-timedelta(days=2)]
 expired_ids={id(n) for n in expired}
 expected=[E.tostring(n,with_tail=False) for n in root.findall('programme') if id(n) not in expired_ids]
 for n in expired:root.remove(n)
 for target in options:baseline[target]=[n for n in baseline[target] if id(n) not in expired_ids]
 repaired=[];added=0
 for target,choices in options.items():
  # Prefer the longest validated horizon. Only fill gaps, never replace an existing interval.
  _,label,cid,name,entries,reg=max(choices,key=lambda x:x[0])
  prior=baseline[target];intervals=[(dt(p.get('start')),dt(p.get('stop')))for p in prior];append=[]
  for a,b,t,raw in sorted(entries,key=lambda x:x[:3]):
   if any(x and y and a<y and b>x for x,y in intervals):continue
   n=E.fromstring(raw,E.XMLParser(resolve_entities=False,no_network=True));n.set('channel',target);append.append(n);intervals.append((a,b))
  if not append:continue
  if target not in original_channels:
   ch=E.Element('channel',id=target);E.SubElement(ch,'display-name').text=targets[target][0]['name'];root.insert(0,ch);original_channels.add(target)
  for n in append:root.append(n)
  baseline[target].extend(append);added+=len(append)
  repaired.append(dict(epgId=target,name=targets[target][0]['name'],region=reg,source=label,sourceId=cid,sourceName=name,newProgrammes=len(append),newlyCurrent=not any(a and b and a<=now<b for a,b in [(dt(p.get('start')),dt(p.get('stop')))for p in prior[:-len(append)]])))
 # Preserve every input programme in its original order and bytes, including NZ/AU/loops.
 after=[E.tostring(n,with_tail=False)for n in root.findall('programme')]
 assert after[:len(expected)]==expected,'Non-expired programme preservation failed'
 out=Path(out);(out/'short').mkdir(parents=True,exist_ok=True)
 for r in repaired:short(baseline[r['epgId']],r['epgId'],out/'short'/(fnv(r['epgId'])+'.json'),now)
 xml=E.tostring(root,encoding='UTF-8',xml_declaration=True);(out/'guide.xml').write_bytes(xml)
 with gzip.open(out/'guide.xml.gz','wb',compresslevel=6)as f:f.write(xml)
 report=dict(generatedAt=now.isoformat(),eligible=len(rows),sources=health,modifiedTargets=len(repaired),newlyCurrentTargets=sum(r['newlyCurrent']for r in repaired),addedProgrammes=added,preservedProgrammes=len(expected),inputProgrammes=len(original),prunedExpiredProgrammes=len(expired),guideProgrammes=len(after),repaired=repaired)
 (out/'report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items()if k!='repaired'},indent=2));return report

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--manifest',required=True);p.add_argument('--existing',required=True);p.add_argument('--source',action='append',required=True,help='label:region=path');p.add_argument('--out',required=True);a=p.parse_args()
 sources=[]
 for spec in a.source:
  label,path=spec.split('=',1);label,reg=label.split(':',1);sources.append((label,reg,path))
 make(a.manifest,a.existing,sources,a.out)
