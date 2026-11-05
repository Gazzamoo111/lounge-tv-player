/** One all-sport evidence policy. Catalogue text discovers identities, never broadcasts. */
export const IMPLEMENTATION_ID='qa-event-streams-v4';
const MINUTE=60_000,DAY=86_400_000;
export const norm=v=>String(v??'').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toUpperCase().replace(/[^A-Z0-9]+/g,' ').trim();
const key=v=>norm(v).replaceAll(' ','');
const instant=v=>typeof v==='string'?Date.parse(v):NaN;
const phrase=(text,alias)=>(' '+norm(text)+' ').includes(' '+norm(alias)+' ');
const marketAlias={GB:'UK',USA:'US',AUS:'AU'};
const badName=/NO EVENT|NO STREAM|NOT STREAMING|OFFLINE|PLACEHOLDER|####|ENDED|REPLAY|HIGHLIGHTS/i;
const knownMismatch=new Set(['646977','646976','646975']);
function marketOf(row){
 const prefix=String(row.name).match(/^(UK-NOWTV|[A-Z]{2,3})\s*[|:]/i)?.[1]?.toUpperCase().replace('-NOWTV','');
 const suffix=String(row.epg||'').match(/\.(nz|au|uk|us|za|sg|qa)$/i)?.[1].toUpperCase();
 const market=marketAlias[prefix]||prefix||suffix;
 if(suffix&&market&&market!==suffix)return null;
 return market;
}
function serviceName(value){return norm(String(value).replace(/^(?:UK-NOWTV|[A-Z]{2,3})\s*[|:]\s*/i,''))
 .replace(/^SKY NZ (ESPN)/,'$1').replace(/^SKY SPORTS NZ (\d+)/,'SKY SPORT $1')
 .replace(/^KAYO\s+/,'').replace(/\b(?:4K|8K|UHD|FHD|HD|SD|HEVC|H264|H265|RAW|BACKUP|BK)\b/g,'').trim().replace(/^PREMIER(?: SPORTS?)? ([12])$/,'PREMIER SPORT $1');}
const equivalent=(a,b)=>key(a)===key(b);
function excluded(row){return ['646974','1520168'].includes(String(row.id))||/^AU\s*[|:]?\s*(?:KAYO\s*[-:]?\s*)?ESPN\s*1(?:\s|$)/i.test(row.name);}
function identity(row,channel,guide){
 if(marketOf(row)!==guide.market)return false;
 return channel.names.some(name=>equivalent(serviceName(row.name),serviceName(name)));
}
const SPORT_TERMS={soccer:['SOCCER','FOOTBALL','UEFA','CHAMPIONS LEAGUE','PREMIER LEAGUE'],football:['NFL','AMERICAN FOOTBALL'],basketball:['BASKETBALL','NBA','WNBA','NBL'],baseball:['BASEBALL','MLB'],hockey:['HOCKEY','NHL'],rugby:['RUGBY','RUGBY UNION'], 'rugby-league':['RUGBY LEAGUE','NRL','RLWC'],cricket:['CRICKET','T20','ODI','TEST MATCH'],mma:['UFC','MMA','BOXING','WWE','WRESTLING','BKFC'],boxing:['BOXING'],racing:['FORMULA 1','FORMULA ONE','F1','MOTOGP'],tennis:['TENNIS','ATP','WTA'],golf:['GOLF','PGA','LPGA'],netball:['NETBALL']};
const FAMILIES={soccer:'football',football:'gridiron',basketball:'basketball',baseball:'baseball',hockey:'hockey',rugby:'rugby', 'rugby-league':'league',cricket:'cricket',mma:'combat',boxing:'combat',racing:'racing',tennis:'tennis',golf:'golf',netball:'netball'};
function sportHit(event,text){return (SPORT_TERMS[event.sportId]||[event.competitionName]).some(t=>t&&phrase(text,t));}
function contradiction(event,text){
 if(phrase(text,'WOMEN')&&!phrase(event.competitionName+' '+event.title,'WOMEN'))return true;
 if(event.sportId==='rugby'&&/\bRUGBY LEAGUE|\bNRL\b/.test(norm(text)))return true;
 if(event.sportId==='soccer'&&/\bNFL\b|\bAMERICAN FOOTBALL\b/.test(norm(text)))return true;
 if(event.competitionId?.endsWith('/nba')&&phrase(text,'WNBA'))return true;
 if(event.competitionId?.endsWith('/atp')&&phrase(text,'WTA'))return true;
 const found=Object.entries(SPORT_TERMS).filter(([s,terms])=>s!==event.sportId&&terms.some(t=>phrase(text,t)));
 return !sportHit(event,text)&&found.some(([s])=>FAMILIES[s]!==FAMILIES[event.sportId]);
}
function participantHit(person,text){return (person.aliases||[person.name]).some(a=>norm(a).length>=4&&!['UNITED','CITY','SPORT','TEAM','NEW ZEALAND'].includes(norm(a))&&phrase(text,a))||phrase(text,person.name)&&norm(person.name).length>=4;}
export function programmeMatches(event,p){
 const start=instant(event.scheduledStartUtc),ps=instant(p.startUtc),pe=instant(p.endUtc);
 const tournamentEnd=instant(event.scheduledEndUtc);
 const multiDay=event.sportId==='golf'&&Number.isFinite(tournamentEnd)&&tournamentEnd>start;
 if(!(ps<pe&&(multiDay?ps>=start&&ps<tournamentEnd:ps<=start+15*MINUTE&&pe>start)))return false;
 const text=[p.title,p.description,...(p.categories||[])].join(' ');
 if(/\bREPLAY|\bHIGHLIGHTS|\bMINI\b|\bPREVIEW|\bCLASSIC|\bARCHIVE|\bREPEAT/i.test(text)||contradiction(event,text))return false;
 if(multiDay&&!phrase(text,'LIVE'))return false;
 const sides=event.participants||[];
 if(sides.length===2)return sides.every(s=>participantHit(s,text))&&sportHit(event,text);
 // Race sessions, golf rounds and event cards need their full specific event title, not league names.
 return norm(event.title).length>=16&&norm(event.title)!==norm(event.competitionName)&&phrase(text,event.title)&&sportHit(event,text);
}
function quality(row){const n=norm(row.name);return /\b4K|\bUHD/.test(n)?4:/\bFHD/.test(n)?3:/\bHD/.test(n)?2:1;}
function playbackFor(row,service,health,now){
 const h=health[String(row.id)];
 if(!h)return {status:'unknown',reason:'not-probed'};
 const age=now-instant(h.observedAtUtc);
 if(h.providerItemId!==String(row.id)||h.serviceId!==service||!(age>=0&&age<=5*MINUTE))return {status:'unknown',reason:'stale-or-wrong-stream'};
 if(h.status==='verified'&&h.decodedFrames>=1&&h.httpAccessible===true&&/^[a-f0-9]{64}$/.test(h.streamFingerprint||''))return h;
 return {...h,status:'failed'};
}
export function resolveEvent(event,catalogue,dataset,health={},now=Date.now(),limit=12){
 const coverage={minimumTarget:3,desiredTarget:5,catalogueScanned:catalogue.rows.length,broadcastConfirmed:0,playbackVerified:0,returned:0,coverageGap:true,reasons:[],programmeMatches:0,unmappedProgrammeServices:[],unresolvedBroadcasters:[],namedEventHints:[],rejections:{inactive:0,policy:0,knownContentMismatch:0,identity:0,contradiction:0,expiredGuide:0}};
 const envelope={implementationId:IMPLEMENTATION_ID,contractVersion:2,eventId:event.eventId,generatedAtUtc:new Date(now).toISOString(),catalogueRevision:catalogue.revision,importCompletedAtUtc:catalogue.completedAtUtc,expiresAtUtc:new Date(now+MINUTE).toISOString(),catalogueStale:!(now-instant(catalogue.completedAtUtc)>=0&&now-instant(catalogue.completedAtUtc)<=DAY),candidates:[],broadcastCandidates:[],coverage};
 if(envelope.catalogueStale){coverage.reasons.push('catalogue-stale');return envelope;}
 const fixtureAge=now-instant(event.fixtureFetchedAtUtc),start=instant(event.scheduledStartUtc);
 if(!event.fixtureSource||!event.eventId||!Number.isFinite(start)||!(fixtureAge>=0&&fixtureAge<=(start>now?DAY:5*MINUTE))||['POST','CANCELLED','POSTPONED','finished','cancelled','postponed'].includes(event.status)){
  coverage.reasons.push('fixture-unavailable-stale-or-ended');return envelope;
 }
 const byService=new Map();let activeGuides=0;
 // ESPN exposes event-specific regional TV assignments. Streaming bundles are deliberately absent:
 // a Prime/Stan/Paramount subscription is not an identified linear channel in the catalogue.
 const linearAliases={'ESPN':['ESPN','ESPN 1'],'ESPN2':['ESPN2','ESPN 2'],'ESPNU':['ESPNU','ESPN U'],'ABC':['ABC'],'CBS':['CBS'],'NBC':['NBC'],'FOX':['FOX'],'TNT':['TNT'],'TBS':['TBS'],'NBA TV':['NBA TV'],'NHL NETWORK':['NHL NETWORK'],'NHL NET':['NHL NETWORK'],'NESN':['NESN'],'NFL NETWORK':['NFL NETWORK']};
 if(/^https:\/\/site\.api\.espn\.com\/apis\/site\/v2\/sports\//.test(event.fixtureSource)){
  for(const b of event.geoBroadcasts||[]){
   const aliases=linearAliases[norm(b.media?.shortName)];
   if(b.region?.toLowerCase()!=='us'||b.type?.shortName!=='TV'||!aliases){coverage.unresolvedBroadcasters.push({name:b.media?.shortName,region:b.region,type:b.type?.shortName,reason:'no-reviewed-linear-service-alias'});continue;}
   const service='US:'+key(aliases[0]);
   for(const row of catalogue.rows){
    if(marketOf(row)!=='US'||!aliases.some(a=>equivalent(serviceName(row.name),a))||badName.test(row.name)||excluded(row))continue;
    const type=event.status==='IN'?'event-feed':'assignment';
    const evidence=[{type,source:event.fixtureSource,eventId:event.eventId,sportId:event.sportId,competitionId:event.competitionId,serviceId:service,market:'US',startUtc:event.scheduledStartUtc,endUtc:new Date(Math.max(start+MINUTE,now+5*MINUTE)).toISOString(),observedAtUtc:event.fixtureFetchedAtUtc,declaredStartUtc:event.scheduledStartUtc,contentMismatch:false,broadcastStatus:event.status==='IN'?'live':'scheduled',intervalMeaning:'fixture start and observation validity; not a programme end time'}];
    const candidate={sourceId:'best',providerItemId:Number(row.id),categoryId:row.category,logicalServiceId:service,displayName:row.name,epgId:row.epg||null,serviceMarket:'US',identityReviewed:true,identityEvidence:{catalogueName:row.name,assignedService:b.media.shortName,market:b.region,method:'reviewed-exact-linear-alias'},tier:type==='event-feed'?'verified':'plausible',reason:'fixture-specific-regional-linear-assignment',expiresAtUtc:new Date(now+MINUTE).toISOString(),evidence,playbackObservation:playbackFor(row,service,health,now),quality:quality(row)};
    const variants=byService.get(service)||[];variants.push(candidate);byService.set(service,variants);
   }
  }
 }
 for(const guide of dataset.guides||[]){
  const guideAge=now-instant(guide.sourceUpdatedAtUtc),fetchAge=now-instant(guide.fetchedAtUtc);
  if(!(guideAge>=0&&guideAge<=DAY&&fetchAge>=0&&fetchAge<=15*MINUTE)){coverage.rejections.expiredGuide++;continue;}
  activeGuides++;
  const programmesByChannel=new Map();
  for(const p of guide.programmes){if(instant(p.endUtc)<=now)continue;const list=programmesByChannel.get(p.channelId)||[];list.push(p);programmesByChannel.set(p.channelId,list);}
  for(const channel of guide.channels){
   const current=programmesByChannel.get(channel.id)||[];
   const proof=current.filter(p=>programmeMatches(event,p));if(!proof.length)continue;
   coverage.programmeMatches+=proof.length;
   if(!catalogue.rows.some(row=>identity(row,channel,guide)))coverage.unmappedProgrammeServices.push({market:guide.market,names:channel.names,guideChannelId:channel.id,programme:proof[0].title});
   const service=guide.market+':'+key(serviceName(channel.names[0]||channel.id));
   const currentContradiction=current.some(p=>instant(p.startUtc)<=now&&contradiction(event,[p.title,p.description,...(p.categories||[])].join(' ')));
   for(const row of catalogue.rows){
    if(!identity(row,channel,guide))continue;
    if(badName.test(row.name)){coverage.rejections.inactive++;continue;}
    if(excluded(row)){coverage.rejections.policy++;continue;}
    if(knownMismatch.has(String(row.id))){coverage.rejections.knownContentMismatch++;continue;}
    if(currentContradiction){coverage.rejections.contradiction++;continue;}
    const evidence=proof.map(p=>({type:'programme',source:guide.url,eventId:event.eventId,sportId:event.sportId,competitionId:event.competitionId,serviceId:service,market:guide.market,startUtc:p.startUtc,endUtc:p.endUtc,observedAtUtc:guide.fetchedAtUtc,sourceUpdatedAtUtc:guide.sourceUpdatedAtUtc,programmeTitle:p.title,fixtureStartUtc:event.scheduledStartUtc,fixtureEndUtc:event.scheduledEndUtc||null,contentMismatch:false}));
    const playback=playbackFor(row,service,health,now);
    const candidate={sourceId:'best',providerItemId:Number(row.id),categoryId:row.category,logicalServiceId:service,displayName:row.name,epgId:row.epg||null,serviceMarket:guide.market,identityReviewed:true,identityEvidence:{catalogueName:row.name,guideNames:channel.names,guideChannelId:channel.id,method:'exact-market-service-alias'},tier:'verified',reason:'fixture-specific-programme',expiresAtUtc:new Date(Math.min(now+MINUTE,...proof.map(p=>instant(p.endUtc)))).toISOString(),evidence,playbackObservation:playback,quality:quality(row)};
    const variants=byService.get(service)||[];variants.push(candidate);byService.set(service,variants);
   }
  }
 }
 const broadcast=[...byService.values()].map(variants=>variants.sort((a,b)=>(b.playbackObservation.status==='verified')-(a.playbackObservation.status==='verified')||b.quality-a.quality||a.providerItemId-b.providerItemId)[0]);
 coverage.broadcastConfirmed=broadcast.length;
 if((event.participants||[]).length===2)coverage.namedEventHints=catalogue.rows.filter(r=>!badName.test(r.name)&&!excluded(r)&&event.participants.every(p=>participantHit(p,r.name))).slice(0,12).map(r=>({providerItemId:r.id,name:r.name,reason:'catalogue-name-only; not event or playback verified'}));
 const working=broadcast.filter(c=>c.playbackObservation.status==='verified').sort((a,b)=>instant(b.playbackObservation.observedAtUtc)-instant(a.playbackObservation.observedAtUtc)||b.quality-a.quality||a.logicalServiceId.localeCompare(b.logicalServiceId));
 coverage.playbackVerified=working.length;envelope.candidates=working.slice(0,Math.min(12,Math.max(1,limit)));envelope.broadcastCandidates=broadcast;
 coverage.returned=envelope.candidates.length;coverage.coverageGap=working.length<3;
 if(!activeGuides)coverage.reasons.push('no-fresh-programme-sources');
 if(!broadcast.length)coverage.reasons.push('no-exact-fixture-programme-and-service-match');
 if(broadcast.length>working.length)coverage.reasons.push('playback-unverified-or-failed');
 if(working.length<3)coverage.reasons.push('below-minimum-three');
 return envelope;
}
