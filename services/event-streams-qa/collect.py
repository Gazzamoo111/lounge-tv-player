"""Read-only QA collection. No Supabase client, provider credentials or production writes."""
import concurrent.futures as cf
import datetime as dt
import gzip
import hashlib
import json
import pathlib
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/'outputs/event-streams-qa'
OUT.mkdir(parents=True,exist_ok=True)
NOW=dt.datetime.now(dt.timezone.utc)
ISO=lambda value:value.isoformat().replace('+00:00','Z')
SOURCES=[('sky-sport-now','NZ','SkySportNow/epg.xml.gz'),('sky-go','NZ','SkyGo/epg.xml.gz'),('foxtel','AU','Foxtel/epg.xml.gz'),('kayo','AU','Kayo/epg.xml.gz'),('dstv','ZA','DStv/za.xml.gz'),('singtel','SG','Singtel/epg.xml.gz')]

def get(url,limit=100_000_000):
    req=urllib.request.Request(url,headers={'User-Agent':'LoungeTV-EventStreams-IsolatedQA/1.0','Accept':'application/json,application/xml,*/*'})
    with urllib.request.urlopen(req,timeout=30) as r:
        data=r.read(limit+1)
        if len(data)>limit:raise ValueError('response exceeds QA limit')
        return data

def instant(value):
    if not value:return None
    return dt.datetime.fromisoformat(value.replace('Z','+00:00')).astimezone(dt.timezone.utc)

def xmltime(value):
    if not value:return None
    for fmt in ['%Y%m%d%H%M%S %z','%Y%m%d%H%M %z']:
        try:return dt.datetime.strptime(value,fmt).astimezone(dt.timezone.utc)
        except ValueError:pass
    return None

def guide(spec):
    name,market,path=spec
    url='https://raw.githubusercontent.com/matthuisman/i.mjh.nz/master/'+path
    status={'source':name,'market':market,'url':url,'fetchedAtUtc':ISO(dt.datetime.now(dt.timezone.utc))}
    try:
        raw=get(url,15_000_000)
        revision=json.loads(get('https://api.github.com/repos/matthuisman/i.mjh.nz/commits?path='+path+'&per_page=1',1_000_000))[0]
        updated=revision['commit']['committer']['date']
        root=ET.fromstring(gzip.decompress(raw))
        channels=[{'id':c.get('id'),'names':[n.text or '' for n in c.findall('display-name')]} for c in root.findall('channel')]
        programmes=[]
        for p in root.findall('programme'):
            start,end=xmltime(p.get('start')),xmltime(p.get('stop'))
            if not start or not end or end<NOW-dt.timedelta(hours=6) or start>NOW+dt.timedelta(hours=72):continue
            programmes.append({'channelId':p.get('channel'),'startUtc':ISO(start),'endUtc':ISO(end),'title':p.findtext('title',''),'description':p.findtext('desc',''),'categories':[c.text or '' for c in p.findall('category')]})
        status.update(ok=True,revision=revision['sha'],sourceUpdatedAtUtc=updated,channels=len(channels),programmes=len(programmes),sha256=hashlib.sha256(raw).hexdigest())
        return {'source':name,'market':market,'url':url,'sourceUpdatedAtUtc':updated,'fetchedAtUtc':status['fetchedAtUtc'],'channels':channels,'programmes':programmes},status
    except Exception as e:
        status.update(ok=False,error=type(e).__name__+': '+str(e).split('?')[0][:180])
        return None,status

def fixtures(feed):
    path,league=feed['path'],feed['league']
    results=[];statuses=[]
    for delta in [-1,0,1,2]:
        day=(NOW+dt.timedelta(days=delta)).strftime('%Y%m%d')
        if feed.get('kind')=='cricket-header':
            url='https://site.web.api.espn.com/apis/v2/scoreboard/header?sport=cricket&league=all&dates='+day
        else:url=f'https://site.api.espn.com/apis/site/v2/sports/{path}/{league}/scoreboard?dates={day}&limit=100'
        try:
            raw=json.loads(get(url,12_000_000))
            (OUT/f'fixture-{path}-{league}-{day}.json').write_text(json.dumps(raw))
            events=raw.get('events',[])
            if feed.get('kind')=='cricket-header':
                events=[e for s in raw.get('sports',[]) for l in s.get('leagues',[]) for e in l.get('events',[])]
            for e in events:
                start=instant(e.get('date'))
                if not start or start<NOW-dt.timedelta(hours=6) or start>NOW+dt.timedelta(hours=48):continue
                competition=(e.get('competitions') or [{}])[0]
                sides=competition.get('competitors',e.get('competitors',[]))
                participants=[]
                for side in sides:
                    team=side.get('team') or side.get('athlete') or side
                    aliases=list(dict.fromkeys(str(team[k]) for k in ['displayName','name','shortDisplayName','shortName','location'] if team.get(k)))
                    participants.append({'id':str(team.get('id',side.get('id',''))),'name':aliases[0] if aliases else '', 'aliases':aliases})
                status_value=e.get('status',{})
                state=(status_value.get('type') or {}).get('state','unknown') if isinstance(status_value,dict) else status_value
                results.append({'eventId':f'{path}/{league}#{e.get("id")}','providerEventId':str(e.get('id')),'sportId':path,'competitionId':f'{path}/{league}','competitionName':feed['name'],'title':e.get('name') or e.get('shortName',''),'scheduledStartUtc':ISO(start),'status':{'pre':'PRE','in':'IN','post':'POST'}.get(state,state),'statusObservedAtUtc':ISO(dt.datetime.now(dt.timezone.utc)),'participants':participants,'fixtureSource':url,'fixtureFetchedAtUtc':ISO(dt.datetime.now(dt.timezone.utc)),'broadcasts':competition.get('broadcasts',[]),'geoBroadcasts':competition.get('geoBroadcasts',[])})
            statuses.append({'feed':path+'/'+league,'date':day,'ok':True,'events':len(events)})
        except Exception as e:statuses.append({'feed':path+'/'+league,'date':day,'ok':False,'error':type(e).__name__+': '+str(e).split('?')[0][:180]})
    return results,statuses

if __name__=='__main__':
    feed_code="import {FEEDS} from './services/sport-lounge/src/index.mjs';console.log(JSON.stringify(FEEDS))"
    feeds=json.loads(subprocess.check_output(['node','--input-type=module','-e',feed_code],cwd=ROOT,text=True))
    all_events={};feeds_status=[];guides=[];guide_status=[]
    with cf.ThreadPoolExecutor(max_workers=2) as pool:
        for events,status in pool.map(fixtures,feeds):
            for e in events:all_events[e['eventId']]=e
            feeds_status.extend(status)
        for data,status in pool.map(guide,SOURCES):
            if data:guides.append(data)
            guide_status.append(status)
    dataset={'collectedAtUtc':ISO(dt.datetime.now(dt.timezone.utc)),'fixtures':list(all_events.values()),'guides':guides,'feedStatus':feeds_status,'guideStatus':guide_status}
    (OUT/'evidence.json').write_text(json.dumps(dataset))
    summary={'collectedAtUtc':dataset['collectedAtUtc'],'fixtureCount':len(all_events),'sports':sorted({e['sportId'] for e in all_events.values()}),'fixtures':[{'eventId':e['eventId'],'title':e['title'],'startUtc':e['scheduledStartUtc'],'status':e['status'],'broadcasts':e['broadcasts'],'geoBroadcasts':e['geoBroadcasts']} for e in all_events.values()],'guides':guide_status,'feedErrors':[s for s in feeds_status if not s['ok']]}
    (OUT/'collection-summary.json').write_text(json.dumps(summary,indent=2))
    print(json.dumps({'fixtureCount':len(all_events),'sports':summary['sports'],'guideStatus':guide_status,'feedErrors':len(summary['feedErrors'])},indent=2))
