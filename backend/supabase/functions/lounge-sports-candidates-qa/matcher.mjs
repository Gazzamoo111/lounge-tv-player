const INACTIVE=/NO EVENT|NO STREAM|NOT STREAMING|OFFLINE|PLACEHOLDER|####|^#+|ENDED|REPLAY|HIGHLIGHTS/i;
const BLOCKED_ESPN1_IDS=new Set(["646974","1520168"]);
const BLOCKED_ESPN1=/^AU\s*[|:]?\s*(?:KAYO\s*[-:]?\s*)?ESPN\s*1(?:\s|$)/i;


export const norm=v=>String(v??"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toUpperCase().replace(/[^A-Z0-9]+/g," ").trim();
const phrase=(hay,needle)=>needle && (" "+norm(hay)+" ").includes(" "+norm(needle)+" ");

function sideAliases(value){
  const n=norm(value); if(!n)return [];
  const words=n.split(/\s+/).filter(Boolean);
  const out=new Set([n]);
  if(words.length===1 && words[0].length>=3) out.add(words[0].slice(0,3));
  if(words.length>1){
    out.add(words.map(x=>x[0]).join(""));
    const last=words.at(-1);
    if(last.length>=3) out.add(last.slice(0,3));
    if(words[0].length && last.length>=2) out.add(words[0][0]+last.slice(0,2));
  }
  return [...out].filter(x=>x.length>=3);
}
const sideHit=(name,value)=>sideAliases(value).some(a=>phrase(name,a));

export function isSoccer(event){
  const s=norm(event.sportId||event.sport), l=norm(event.competitionId||event.league);
  return s.includes("SOCCER") || (
    (s.includes("FOOTBALL") || /UEFA|CHAMPIONS LEAGUE|PREMIER LEAGUE|EPL|LA LIGA|SERIE A|BUNDESLIGA|A LEAGUE/.test(l)) &&
    !/NFL|AMERICAN FOOTBALL/.test(s+" "+l)
  );
}
function quality(name){
  const n=norm(name);
  if(/\b(?:4K|UHD)\b/.test(n))return 40;
  if(/\bFHD\b/.test(n))return 30;
  if(/\bHD\b/.test(n))return 20;
  if(/\bSD\b/.test(n))return 0;
  return 10;
}
function market(name){
  const m=norm(name).match(/^(AU|UK|GB|US|USA|NZ|IE|CA)\b/);
  if(!m)return "INT";
  return m[1]==="GB"?"UK":m[1]==="USA"?"US":m[1];
}
function stableSoccer(name){
  const n=norm(name).replace(/^UK NOWTV /,"UK ");
  let m=n.match(/^AU BEIN SPORTS? ([123])(?:\s|$)/);
  if(m)return {key:"AU:BEIN:"+m[1],priority:10};
  m=n.match(/^UK SKY SPORTS (FOOTBALL|PREMIER LEAGUE|MAIN EVENTS?)(?:\s|$)/);
  if(m)return {key:"UK:SKY:"+m[1].replace(/\s+/g,"-"),priority:m[1]==="FOOTBALL"?20:22};
  m=n.match(/^UK TNT SPORTS? ([1-4])(?:\s|$)/);
  if(m)return {key:"UK:TNT:"+m[1],priority:24};
  m=n.match(/^UK PREMIER SPORTS? ([12])(?:\s|$)/);
  if(m)return {key:"UK:PREMIER:"+m[1],priority:28};
  m=n.match(/^UK BEIN SPORTS? ([123])(?:\s+(?:EN|PREMIUM))?(?:\s|$)/);
  if(m)return {key:"UK:BEIN:"+m[1],priority:30};
  if(/^US BEIN SPORTS(?:\s+HD|\s+XTRA)?(?:\s|$)/.test(n))return {key:"US:BEIN",priority:35};
  return null;
}
function stableNamed(name){
  const n=norm(name);
  return !/\b(?:PPV|EVENTS?\s*\d+|PLAY\s*\d+)\b/.test(n) && !INACTIVE.test(name);
}
function broadcasterHit(name,broadcasters=[]){
  const n=norm(name);
  return broadcasters.some(raw=>{
    const b=norm(raw);
    if(b.length<4)return false;
    return phrase(n,b);
  });
}
export function excluded(row){
  const id=String(row.providerItemId??row.provider_item_id??"");
  const n=norm(row.name||row.displayName||"");
  return BLOCKED_ESPN1_IDS.has(id)||BLOCKED_ESPN1.test(row.name||row.displayName||"")||
    /^(?:(?:AU|AUS|AUSTRALIA)(?: KAYO)? ESPN(?: (?:AU|AUS|AUSTRALIA))? 1|ESPN (?:AU|AUS|AUSTRALIA) 1|ESPN 1 (?:AU|AUS|AUSTRALIA))(?: |$)/.test(n);
}
export function classify(event,row){
  row=inventoryRow(row);
  const name=String(row.name||row.displayName||"");
  if(!name||INACTIVE.test(name)||excluded(row))return null;
  const soccer=isSoccer(event);

  const exact=sideHit(name,event.home)&&sideHit(name,event.away);
  if(exact)return {tier:"unverified",logicalServiceId:"event:"+row.providerItemId,priority:0,market:market(name),reason:"fixture-name-unconfirmed"};
  if(soccer){
    const stable=stableSoccer(name);
    if(stable)return {tier:"unverified",logicalServiceId:stable.key,priority:stable.priority,market:market(name),reason:"curated-soccer-service-unconfirmed"};
  }
  if(!soccer&&stableNamed(name)&&broadcasterHit(name,event.broadcasters)){
    return {tier:"unverified",logicalServiceId:"broadcaster:"+norm(name).replace(/\b(?:4K|UHD|FHD|HD|SD|HEVC|RAW)\b/g,"").trim(),priority:45,market:market(name),reason:"fixture-broadcaster-unconfirmed"};
  }
  return null;
}
export function inventoryRow(row){
  return {...row,providerItemId:row.providerItemId??row.provider_item_id};
}

// Only server-owned reviewed evidence can promote a service. Client broadcaster names cannot.
export function evidenceDecision(event,row,now){
  const start=Date.parse(event.scheduledStartUtc),end=Date.parse(event.scheduledEndUtc);
  if(!row.identityReviewed||!row.logicalServiceId||!row.serviceMarket||
    !Number.isFinite(start)||!Number.isFinite(end)||end<=start||end<=now||
    ["POST","CANCELLED","POSTPONED"].includes(event.status))return null;
  const evidence=(row.evidence||[]).filter(e=>{
    const at=Date.parse(e.observedAtUtc),es=Date.parse(e.startUtc),ee=Date.parse(e.endUtc);
    return e.source&&e.eventId===event.eventId&&e.sportId===event.sportId&&
      e.competitionId===event.competitionId&&e.serviceId===row.logicalServiceId&&
      e.market===row.serviceMarket&&Number.isFinite(at)&&now-at>=0&&
      now-at<=(start>now?86400000:300000)&&es<=start+900000&&ee>start&&ee>now&&es<ee;
  });
  if(evidence.some(e=>e.contentMismatch)||row.contentMismatch)return {reject:true};
  const accepted=evidence.filter(e=>["programme","event-feed","assignment"].includes(e.type));
  if(!accepted.length)return null;
  const verified=accepted.some(e=>["programme","event-feed"].includes(e.type));
  return {tier:verified?"verified":"plausible",logicalServiceId:row.logicalServiceId,
    priority:verified?-20:-10,market:row.serviceMarket,reason:"current-fixture-evidence",accepted};
}

export function rankCandidates(event,rows,limit=6,now=Date.now()){
  const admitted=rows.map(inventoryRow).map(row=>{
    if(excluded(row)||INACTIVE.test(row.name||""))return null;
    const proof=evidenceDecision(event,row,now);
    if(proof?.reject)return null;
    const decision=proof||classify(event,row);
    return decision?{row,decision}:null;
  }).filter(Boolean);
  admitted.sort((a,b)=>a.decision.priority-b.decision.priority || quality(b.row.name)-quality(a.row.name) || Number(a.row.providerItemId)-Number(b.row.providerItemId));
  const seen=new Set(),out=[];
  for(const item of admitted){
    const k=item.decision.logicalServiceId;
    if(seen.has(k))continue;
    seen.add(k);out.push(item);
    if(out.length>=Math.min(6,Math.max(1,limit)))break;
  }
  return out;
}
/** Adapt inventory discovery without inventing broadcast or playback evidence. */
export function discoveryCandidate(row,decision,now){
  return {
    sourceId:"best",
    providerItemId:Number(row.providerItemId??row.provider_item_id),
    categoryId:String(row.category_id),
    logicalServiceId:decision.logicalServiceId,
    displayName:String(row.name),
    logoUrl:row.artwork_url||null,
    epgId:null,
    serviceMarket:decision.market,
    identityReviewed:Boolean(decision.accepted?.length),
    tier:decision.accepted?.length?decision.tier:"unverified",
    reason:decision.reason,
    expiresAtUtc:new Date(now+300000).toISOString(),
    evidence:decision.accepted||[],
    playbackObservation:{status:"unknown"},
  };
}
