const INACTIVE=/NO EVENT|NO STREAM|NOT STREAMING|OFFLINE|PLACEHOLDER|####|^#+|ENDED|REPLAY|HIGHLIGHTS/i;
const BLOCKED_ESPN1_IDS=new Set(["646974","1520168"]);
const BLOCKED_ESPN1=/^AU\s*[|:]?\s*(?:KAYO\s*[-:]?\s*)?ESPN\s*1(?:\s|$)/i;

export const norm=v=>String(v??"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toUpperCase().replace(/[^A-Z0-9]+/g," ").trim();
const phrase=(hay,needle)=>needle && (" "+norm(hay)+" ").includes(" "+norm(needle)+" ");
const GENERIC_ALIAS=new Set(["TEAM","SPORT","SPORTS","UNITED","CITY","NEW ZEALAND","AUSTRALIA","ENGLAND"]);

function cleanAliases(values=[]){
  return [...new Set(values.map(norm).filter(v=>v.length>=3&&!GENERIC_ALIAS.has(v)))];
}
function aliasHit(name,aliases=[]){
  const n=norm(name);
  return cleanAliases(aliases).some(alias=>phrase(n,alias));
}
function aliasesFor(event,side){
  const explicit=event[side+"Aliases"];
  if(Array.isArray(explicit)&&explicit.length)return cleanAliases(explicit);
  const value=event[side];
  return cleanAliases(value?[value]:[]);
}
function sportContextHit(event,name){
  const n=norm(name),sport=norm(event.sportId),competition=norm(event.competitionId);
  if(sport.includes("BASKETBALL")){
    if(competition.includes("NBA")&&/\bWNBA\b/.test(n))return false;
    return /\bNBA\b|\bNBL\b|\bBASKETBALL\b/.test(n);
  }
  if(sport.includes("SOCCER")){
    if(/\bNFL\b|\bNCAA(?:F| FOOTBALL)\b|AMERICAN FOOTBALL/.test(n))return false;
    return /SOCCER|UEFA|CHAMPIONS LEAGUE|PREMIER LEAGUE|EPL|LA LIGA|SERIE A|BUNDESLIGA|FOOTBALL/.test(n);
  }
  if(sport.includes("FOOTBALL"))return /\bNFL\b|\bNCAAF\b|NCAA FOOTBALL|AMERICAN FOOTBALL/.test(n);
  if(sport.includes("HOCKEY"))return /\bNHL\b|HOCKEY/.test(n);
  if(sport.includes("BASEBALL"))return /\bMLB\b|BASEBALL/.test(n);
  if(sport.includes("RUGBY LEAGUE"))return /RUGBY LEAGUE|\bNRL\b/.test(n);
  if(sport.includes("RUGBY"))return /RUGBY/.test(n)&&!/RUGBY LEAGUE|\bNRL\b/.test(n);
  if(sport.includes("CRICKET"))return /CRICKET|\bT20\b|\bODI\b|TEST MATCH/.test(n);
  if(sport.includes("TENNIS"))return /TENNIS|\bATP\b|\bWTA\b/.test(n);
  if(sport.includes("GOLF"))return /GOLF|\bPGA\b|\bLPGA\b/.test(n);
  if(sport.includes("MMA"))return /\bUFC\b|\bMMA\b|\bBKFC\b/.test(n);
  if(sport.includes("BOX"))return /BOXING/.test(n);
  if(sport.includes("RACING"))return /FORMULA|\bF1\b|MOTOGP|RACING/.test(n);
  return false;
}
function fixtureNamed(event,row){
  const name=String(row.name||row.displayName||"");
  const home=aliasesFor(event,"home"),away=aliasesFor(event,"away");
  const fullHome=norm(event.home),fullAway=norm(event.away);
  if(fullHome.length>=4&&fullAway.length>=4&&phrase(name,fullHome)&&phrase(name,fullAway))return true;
  if(home.length&&away.length){
    // Partial team names, nicknames and abbreviations are only usable when the provider row
    // also proves the selected sport. This blocks shared-city and cross-sport collisions.
    return aliasHit(name,home)&&aliasHit(name,away)&&sportContextHit(event,name);
  }
  const title=norm(event.title);
  return title.length>=10&&phrase(name,title)&&sportContextHit(event,name);
}
function market(name){
  const m=norm(name).match(/^(AU|AUS|AUSTRALIA|UK|GB|US|USA|NZ|IE|CA)\b/);
  if(!m)return "INT";
  const value=m[1];
  if(value==="GB")return "UK";
  if(value==="USA")return "US";
  if(value==="AUS"||value==="AUSTRALIA")return "AU";
  return value;
}
function quality(name){
  const n=norm(name);
  if(/\b(?:8K)\b/.test(n))return 50;
  if(/\b(?:4K|UHD)\b/.test(n))return 40;
  if(/\bFHD\b/.test(n))return 30;
  if(/\bHD\b/.test(n))return 20;
  if(/\bSD\b/.test(n))return 0;
  return 10;
}
function eventPriority(name){
  const n=norm(name);
  if(/^LIVE\b/.test(n)||/\bLIVE\s*\|/.test(n))return 0;
  if(/^NOW\b/.test(n))return 2;
  if(/^NEXT\b/.test(n))return 5;
  return 10;
}
export function inventoryRow(row){
  return {...row,providerItemId:row.providerItemId??row.provider_item_id};
}
export function excluded(row){
  const id=String(row.providerItemId??row.provider_item_id??"");
  const n=norm(row.name||row.displayName||"");
  return BLOCKED_ESPN1_IDS.has(id)||BLOCKED_ESPN1.test(row.name||row.displayName||"")||
    /^(?:(?:AU|AUS|AUSTRALIA)(?: KAYO)? ESPN(?: (?:AU|AUS|AUSTRALIA))? 1|ESPN (?:AU|AUS|AUSTRALIA) 1|ESPN 1 (?:AU|AUS|AUSTRALIA))(?: |$)/.test(n);
}

/** Provider item names identify investigation leads only. They never prove a broadcast or playback. */
export function classify(event,raw,now=Date.now()){
  const row=inventoryRow(raw);
  const name=String(row.name||row.displayName||"");
  if(!name||INACTIVE.test(name)||excluded(row)||!fixtureNamed(event,row))return null;
  const start=Date.parse(event.scheduledStartUtc);
  if(!Number.isFinite(start)||["POST","CANCELLED","POSTPONED"].includes(String(event.status).toUpperCase()))return null;
  const parsedEnd=Date.parse(event.scheduledEndUtc);
  const end=Number.isFinite(parsedEnd)&&parsedEnd>start?parsedEnd:Math.max(start+6*60*60_000,now+30*60_000);
  const serviceMarket=market(name);
  const logicalServiceId="best-event:"+row.providerItemId;
  return {
    tier:"plausible",
    logicalServiceId,
    priority:eventPriority(name),
    market:serviceMarket,
    reason:"exact-fixture-provider-name-unverified",
    accepted:[{
      type:"name-hint",
      source:"best-live-inventory-name-only",
      eventId:event.eventId,
      sportId:event.sportId,
      competitionId:event.competitionId,
      serviceId:logicalServiceId,
      market:serviceMarket,
      startUtc:new Date(start).toISOString(),
      endUtc:new Date(end).toISOString(),
      observedAtUtc:new Date(now).toISOString(),
      declaredStartUtc:new Date(start).toISOString(),
      contentMismatch:false,
      providerObservedName:name,
    }],
  };
}

export function rankCandidates(event,rows,limit=12,now=Date.now()){
  const admitted=rows.map(inventoryRow).map(row=>({row,decision:classify(event,row,now)})).filter(x=>x.decision);
  admitted.sort((a,b)=>a.decision.priority-b.decision.priority||
    quality(b.row.name)-quality(a.row.name)||
    Number(a.row.providerItemId)-Number(b.row.providerItemId));
  const seen=new Set(),out=[];
  for(const item of admitted){
    const id=String(item.row.providerItemId);
    if(seen.has(id))continue;
    seen.add(id);
    out.push(item);
    if(out.length>=Math.min(12,Math.max(1,limit)))break;
  }
  return out;
}

export function discoveryCandidate(raw,decision,now){
  const row=inventoryRow(raw);
  return {
    sourceId:"best",
    providerItemId:Number(row.providerItemId),
    categoryId:row.category_id==null?null:String(row.category_id),
    logicalServiceId:decision.logicalServiceId,
    displayName:String(row.name),
    logoUrl:row.artwork_url||null,
    epgId:row.epg_channel_id||null,
    serviceMarket:decision.market,
    identityReviewed:true,
    tier:decision.tier,
    reason:decision.reason,
    expiresAtUtc:new Date(now+300000).toISOString(),
    evidence:decision.accepted,
    playbackObservation:{status:"unknown",reason:"device-acceptance-required"},
  };
}

/**
 * Independent, event-specific ESPN regional linear TV assignments.
 * No generic network, package subscription, or client broadcaster hint is admitted.
 * Assignments are plausible until the exact broadcast and video decode are confirmed.
 */
export function linearAssignmentCandidates(event,rows,geoBroadcasts,source,now=Date.now(),limit=12){
  const approved={
    "ESPN":["ESPN"],"ESPN2":["ESPN 2","ESPN2"],"ESPNU":["ESPNU","ESPN U"],
    "TNT":["TNT"],"TBS":["TBS"],"NBA TV":["NBA TV"],"NHL NETWORK":["NHL NETWORK","NHL NET"],
    "NFL NETWORK":["NFL NETWORK"],"FS1":["FOX SPORTS 1","FS1"],"FS2":["FOX SPORTS 2","FS2"]
  };
  const assignments=new Set();
  for(const b of geoBroadcasts||[]){
    if(String(b.region||"").toLowerCase()!=="us"||b.type?.shortName!=="TV")continue;
    const name=norm(b.media?.shortName);
    if(approved[name])assignments.add(name);
  }
  const result=[];
  for(const raw of rows){
    const row=inventoryRow(raw),name=String(row.name||"");
    if(!name||INACTIVE.test(name)||excluded(row))continue;
    if(!/^US\s*[|:]\s*/i.test(name))continue;
    const service=norm(name.replace(/^US\s*[|:]\s*/i,"")
      .replace(/[\u1d00-\u1dbf\u2070-\u209f]+/gu," "))
      .replace(/\b(?:4K|UHD|FHD|HD|SD|RAW|HEVC|H264|H265|BACKUP|BK)\b/g,"").trim();
    const assigned=[...assignments].find(network=>approved[network].some(alias=>norm(alias)===service));
    if(!assigned)continue;
    const serviceId="US:"+norm(assigned).replaceAll(" ","");
    const end=Date.parse(event.scheduledEndUtc);
    const start=Date.parse(event.scheduledStartUtc);
    const decision={
      tier:"plausible",logicalServiceId:serviceId,market:"US",
      reason:"independent-fixture-specific-linear-tv-assignment",
      accepted:[{
        type:"assignment",source,eventId:event.eventId,sportId:event.sportId,
        competitionId:event.competitionId,serviceId,market:"US",
        startUtc:new Date(start).toISOString(),
        endUtc:new Date(Number.isFinite(end)&&end>start?end:start+6*3600000).toISOString(),
        observedAtUtc:new Date(now).toISOString(),contentMismatch:false,
        assignedNetwork:assigned
      }]
    };
    result.push(discoveryCandidate(row,decision,now));
  }
  result.sort((a,b)=>quality(b.displayName)-quality(a.displayName)||a.providerItemId-b.providerItemId);
  return result.slice(0,Math.max(1,Math.min(12,limit)));
}
