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
function fixtureNamed(event,row){
  const name=String(row.name||row.displayName||"");
  const home=aliasesFor(event,"home"),away=aliasesFor(event,"away");
  if(home.length&&away.length)return aliasHit(name,home)&&aliasHit(name,away);
  const title=norm(event.title);
  return title.length>=10&&phrase(name,title);
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

/**
 * Fixture-specific BEST event feeds are evidence because the currently-present provider row itself
 * names both competitors (or the full single-event title). Generic networks, team channels and
 * same-sport feeds never pass this gate.
 */
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
    tier:"verified",
    logicalServiceId,
    priority:eventPriority(name),
    market:serviceMarket,
    reason:"exact-fixture-provider-event-feed",
    accepted:[{
      type:"event-feed",
      source:"best-live-inventory-exact-fixture",
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
