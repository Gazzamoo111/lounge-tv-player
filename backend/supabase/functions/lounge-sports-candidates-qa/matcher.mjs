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
  const duration=/CRICKET/i.test(event.sportId)?18: /GOLF/i.test(event.sportId)?36:6;
  const end=Number.isFinite(parsedEnd)&&parsedEnd>start?parsedEnd:start+duration*60*60_000;
  // A stale event label must not be recycled as live merely because both teams recur.
  if(now>end||start-now>7*24*60*60_000)return null;
  const datedName=name.match(/\b(20\d{2}-\d{2}-\d{2})\b/);
  if(datedName&&Math.abs(Date.parse(datedName[1])-start)>2*24*60*60_000)return null;
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
    if(out.length>=Math.min(rows.length,Math.max(1,limit)))break;
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
/** Normalise exact linear-TV identities while preserving network numbers and qualifiers. */
function networkKey(name){
  return norm(String(name||"").replace(/[\u1d00-\u1dbf\u2070-\u209f]+/gu," "))
    .replace(/\b(?:4K|8K|UHD|FHD|HD|SD|RAW|HEVC|H264|H265|BACKUP|BK|25FPS|30FPS|50FPS|60FPS)\b/g,"")
    .replace(/\s+/g," ").trim();
}
const networkAliases={
  "SPECTRUM SPORTS NET":["SPECTRUM SPORTSNET","SPECTRUM SPORTS NET"],
  "SPECTRUM SPORTS NET +":["SPECTRUM SPORTSNET PLUS","SPECTRUM SPORTS NET PLUS"],
  "MSGSN":["MSG SPORTSNET","MSGSN"],
  "DSN":["DETROIT SPORTSNET"],
  "NBC SPORTS PHIL":["NBC SPORTS PHILADELPHIA","NBC SPORTS PHIL"],
  "CHSN":["CHICAGO SPORTS NETWORK"],
  "NBA TV":["NBA TV"],
  "NHL NETWORK":["NHL NETWORK","NHL NET"],
  "ESPN2":["ESPN 2"],
  "FS1":["FOX SPORTS 1"],
  "FS2":["FOX SPORTS 2"],
};
function approvedServiceNames(assigned){
  const canonical=networkKey(assigned);
  const aliases=networkAliases[canonical]||[];
  return new Set([canonical,...aliases.map(networkKey)]);
}

/**
 * Only admit BEST linear networks that ESPN independently assigns to THIS fixture.
 * Search all BEST source categories, including TV| RAW regional networks, with exact
 * network identity, not substring/sport-wide fallback.
 */
export function linearAssignmentCandidates(event,rows,geoBroadcasts,source,now=Date.now(),limit=12){
  // Only independent event-specific linear-TV assignments. Never infer that a
  // country's rights carry over to another country's ESPN/Fox/Sky variant.
  const regionCodes={us:"US",usa:"US",au:"AU",aus:"AU",nz:"NZ",gb:"UK",uk:"UK",ie:"IE",ca:"CA"};
  const assignments=[];
  for(const b of geoBroadcasts||[]){
    const assignedMarket=regionCodes[String(b.region||"").toLowerCase()];
    if(!assignedMarket||String(b.type?.shortName||"").toUpperCase()!=="TV")continue;
    const name=networkKey(b.media?.shortName);
    if(!name||name.length<3)continue;
    if(assignments.some(a=>a.name===name&&a.market===assignedMarket))continue;
    assignments.push({name,market:assignedMarket,variants:approvedServiceNames(name)});
  }
  const result=[];
  for(const raw of rows){
    const row=inventoryRow(raw),name=String(row.name||"");
    if(!name||INACTIVE.test(name)||excluded(row))continue;
    const prefix=name.match(/^(US|AU|NZ|UK|IE|CA|TV)\s*[|:]\s*/i);
    if(!prefix)continue;
    // TV| RAW is an established US source family in BEST; preserve its distinct IDs.
    const serviceMarket=prefix[1].toUpperCase()==="TV"?"US":prefix[1].toUpperCase();
    const service=networkKey(name.slice(prefix[0].length));
    const assigned=assignments.find(a=>a.market===serviceMarket&&a.variants.has(service));
    if(!assigned)continue;
    const serviceId=serviceMarket+":"+assigned.name.replaceAll(" ","");
    const end=Date.parse(event.scheduledEndUtc),start=Date.parse(event.scheduledStartUtc);
    const decision={
      tier:"plausible",logicalServiceId:serviceId,market:serviceMarket,
      reason:"independent-fixture-specific-linear-tv-assignment",
      accepted:[{
        type:"assignment",source,eventId:event.eventId,sportId:event.sportId,
        competitionId:event.competitionId,serviceId,market:serviceMarket,
        startUtc:new Date(start).toISOString(),
        endUtc:new Date(Number.isFinite(end)&&end>start?end:start+6*3600000).toISOString(),
        observedAtUtc:new Date(now).toISOString(),contentMismatch:false,
        assignedNetwork:assigned.name
      }]
    };
    result.push(discoveryCandidate(row,decision,now));
  }
  result.sort((a,b)=>quality(b.displayName)-quality(a.displayName||"")||a.providerItemId-b.providerItemId);
  return result.slice(0,Math.min(rows.length,Math.max(1,limit)));
}


/**
 * QA-only sport-specific discovery across every BEST live source category.
 * These are optional channel choices, NOT evidence that the selected game is on air.
 * They must never be counted as an assigned network or a verified match.
 * Never add all generic networks (ESPN, Stan, Paramount) by sport.
 */
export function sportFallbackCandidates(event,rows,now=Date.now(),limit=6){
  const sport=norm(event.sportId),league=norm(event.competitionId);
  const start=Date.parse(event.scheduledStartUtc);
  if(!Number.isFinite(start)||start-now>7*86400_000||
     ["POST","CANCELLED","POSTPONED"].includes(String(event.status).toUpperCase()))return [];
  const end=Date.parse(event.scheduledEndUtc);
  if((Number.isFinite(end)&&end<=now)||(!Number.isFinite(end)&&start+36*3600_000<=now))return [];
  const rules=[
    [/TENNIS/, /^(?:(?:US|UK|UK NOWTV|TV|AU|NZ|DE|IT)\s+)?(?:SKY SPORTS? TENNIS|TENNIS CHANNEL(?: PLUS)?(?: [0-9]+)?|SUPERSPORTS TENNIS|BEIN SPORTS TENNIS)\b/],
    [/BASKETBALL/, /\b(?:NBA TEAM|NBA TV|NBA LEAGUE PASS|NBA PASS|NBL TV)\b/],
    [/FOOTBALL/, /\b(?:NFL TEAMS?|NFL NETWORK|NFL GAME PASS)\b/],
    [/HOCKEY/, /\b(?:NHL NETWORK|NHL TEAM|NHL CENTER ICE|NHL TV)\b/],
    [/BASEBALL/, /\b(?:MLB NETWORK|MLB EXTRA INNINGS|MLB TEAM|MLB TV)\b/],
    [/CRICKET/, /\b(?:SKY SPORTS? CRICKET|FOX CRICKET|WILLOW(?: TV)?|SUPERSPORT CRICKET)\b/],
    [/SOCCER/, /\b(?:BEIN SPORTS?|TNT SPORTS?|SKY SPORTS (?:FOOTBALL|PREMIER LEAGUE)|OPTUS SPORT|PREMIER SPORTS)\b/],
    [/RUGBY LEAGUE/, /\b(?:FOX LEAGUE|SKY SPORTS? LEAGUE|NRL TV)\b/],
    [/RUGBY/, /\b(?:SKY SPORTS? RUGBY|RUGBY PASS|SUPERSPORT RUGBY)\b/],
    [/MMA|UFC/, /\b(?:UFC FIGHT PASS|UFC TV|UFC PPV)\b/],
    [/BOX/, /\b(?:DAZN BOXING|BOXING PPV|SKY SPORTS? BOXING)\b/],
    [/GOLF/, /\b(?:GOLF CHANNEL|SKY SPORTS? GOLF|PGA TOUR)\b/],
    [/RACING|FORMULA|F1|MOTOGP/, /\b(?:SKY SPORTS? F1|F1 TV|MOTOGP TV|SKY SPORTS? RACING)\b/],
  ];
  const rule=rules.find(([key])=>key.test(sport))?.[1];
  if(!rule)return [];
  const options=[];
  for(const raw of rows){
    const row=inventoryRow(raw),name=String(row.name||"");
    if(!name||INACTIVE.test(name)||excluded(row))continue;
    const n=norm(name);
    // Prevent different live fixture or replay channels from being offered as backups.
    if(/\b(?:VS|VERSUS| AT |REPLAY|ENDED|NEXT)\b/.test(n))continue;
    if(!rule.test(n))continue;
    if(sport.includes("BASKETBALL")&&league.includes("NBA")&&/\bNBL\b/.test(n))continue;
    if(sport.includes("FOOTBALL")&&!league.includes("NFL")&&/\bNFL\b/.test(n))continue;
    // Preserve the source identity, but deduplicate equivalent HD/FHD/RAW variants.
    const family=n.replace(/\b(?:8K|4K|UHD|HEVC|FHD|HD|SD|RAW|BACKUP|25FPS|30FPS|50FPS|60FPS)\b/g,"")
      .replace(/\s+/g," ").trim();
    const logicalServiceId="best-sport-option:"+row.providerItemId;
    const decision={
      tier:"plausible",logicalServiceId,market:market(name),reason:"sport-specific-unverified",
      accepted:[{
        type:"sport-option",source:"best-live-inventory-sport-channel",
        eventId:event.eventId,sportId:event.sportId,competitionId:event.competitionId,
        serviceId:logicalServiceId,market:market(name),
        startUtc:new Date(start).toISOString(),
        endUtc:new Date(Number.isFinite(end)&&end>start?end:start+36*3600_000).toISOString(),
        observedAtUtc:new Date(now).toISOString(),contentMismatch:false,
        providerObservedName:name,fixtureBroadcastConfirmed:false,
      }],
    };
    options.push({row,decision,family,quality:quality(name)});
  }
  options.sort((a,b)=>b.quality-a.quality||Number(a.row.providerItemId)-Number(b.row.providerItemId));
  const unique=new Set(),output=[];
  for(const option of options){
    if(unique.has(option.family))continue;
    unique.add(option.family);
    output.push(discoveryCandidate(option.row,option.decision,now));
    if(output.length>=Math.max(0,Math.min(6,limit)))break;
  }
  return output;
}
