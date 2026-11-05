/** Pure decisions: never opens streams and never trusts catalogue names as fixture evidence. */
const LIVE_AGE=300000, DAY=86400000;
const instant=v=>typeof v==='string' && /Z$/.test(v) ? Date.parse(v) : NaN;
const inactive=/NO EVENT|NO STREAM|NOT STREAMING|OFFLINE|PLACEHOLDER|####|^#+|ENDED|REPLAY|HIGHLIGHTS/i;
export function excluded(r){
 if(r.sourceId!=='best')return false;
 if(['646974','1520168'].includes(String(r.providerItemId)))return true;
 return /^AU\s*[|:]?\s*(?:KAYO\s*[-:]?\s*)?ESPN\s*1(?:\s|$)/i.test(r.displayName||'');
}
export function parseRequest(url){
 const p=url.searchParams,version=Number(p.get('contractVersion')||1);
 if(![1,2].includes(version))throw new RangeError('unsupported_contract_version');
 const out={contractVersion:version};
 for(const key of ['eventId','sportId','competitionId','division','homeId','awayId','title','home','away','sport','league','scheduledStartUtc','scheduledEndUtc','status','statusObservedAtUtc']){
  const v=p.get(key);if(v?.length>256)throw new RangeError('input_too_long');if(v)out[key]=v;
 }
 const broadcasters=(p.get('broadcasters')||'').split('|').filter(Boolean);
 if(broadcasters.length>16||broadcasters.some(x=>x.length>256))throw new RangeError('broadcaster_bounds');
 out.limit=Number(p.get('limit')||12);if(!Number.isInteger(out.limit)||out.limit<1||out.limit>12)throw new RangeError('limit_bounds');
 if(version===2){
  if(!out.eventId||!out.sportId||!out.competitionId||!Number.isFinite(instant(out.scheduledStartUtc)))throw new RangeError('missing_identity');
  if(out.scheduledEndUtc && !(instant(out.scheduledEndUtc)>instant(out.scheduledStartUtc)))throw new RangeError('invalid_interval');
 }
 return out;
}
export function decide(f,r,now){
 if(excluded(r)||r.present===false||inactive.test(r.displayName||'')||r.contentMismatch)return null;
 if(['POST','CANCELLED','POSTPONED'].includes(f.status))return null;
 let tier='unverified'; const accepted=[];
 const start=instant(f.scheduledStartUtc),end=instant(f.scheduledEndUtc);
 const importAt=instant(f.importCompletedAtUtc);
 const catalogueFresh=Number.isFinite(importAt)&&now-importAt>=0&&now-importAt<=DAY;
 let expiresAt=now+LIVE_AGE;
 for(const e of r.evidence||[]){
  const es=instant(e.startUtc),ee=instant(e.endUtc),at=instant(e.observedAtUtc);
  if(!e.source||!Number.isFinite(at)||at>now)continue;
  if(e.market && e.market!==r.serviceMarket)continue;
  if(e.serviceId && e.serviceId!==r.logicalServiceId)continue;
  if(e.contentMismatch)return null;
  if((e.eventId && e.eventId!==f.eventId)||(e.sportId && e.sportId!==f.sportId)||(e.competitionId && e.competitionId!==f.competitionId)||(e.division && f.division && e.division!==f.division))return null;
  const exact=e.eventId===f.eventId || (e.homeId && e.awayId && e.competitionId===f.competitionId && e.division===f.division && ((e.homeId===f.homeId&&e.awayId===f.awayId)||(e.homeId===f.awayId&&e.awayId===f.homeId)));
  const overlap=Number.isFinite(start)&&Number.isFinite(end)&&Number.isFinite(es)&&Number.isFinite(ee)&&Math.max(start,es)<Math.min(end,ee);
  if(exact&&Number.isFinite(end)&&Number.isFinite(es)&&Number.isFinite(ee)&&!overlap)return null;
  const future=f.status==='PRE'&&start>now;
  const fresh=now-at<=(future?DAY:LIVE_AGE);
  const statusFresh=future || (f.status==='IN'&&start<=now&&end>now&&now-instant(f.statusObservedAtUtc)>=0&&now-instant(f.statusObservedAtUtc)<=LIVE_AGE);
  const kickoffOK=e.type!=='event-feed'||Math.abs(instant(e.declaredStartUtc)-start)<=900000;
  if(exact&&overlap&&kickoffOK&&r.identityReviewed&&r.serviceMarket&&fresh&&statusFresh&&catalogueFresh&&['programme','event-feed'].includes(e.type)){
   tier='verified';accepted.push(e);expiresAt=Math.min(expiresAt,ee,end,at+(future?DAY:LIVE_AGE));
  } else if(tier!=='verified'&&e.type==='assignment'&&e.serviceId===r.logicalServiceId&&e.market===r.serviceMarket&&r.identityReviewed&&exact&&overlap&&fresh){
   tier='plausible';accepted.push(e);expiresAt=Math.min(expiresAt,ee,at+DAY);
  }
 }
 return {...r,tier,evidence:accepted,reason:tier==='unverified'?'No fresh fixture-specific evidence':tier==='verified'?'Fresh fixture and interval evidence':'Current regional assignment; programme unconfirmed',expiresAtUtc:new Date(expiresAt).toISOString(),playbackObservation:{status:'unknown'}};
}
export function rank(rows){
 const order={verified:0,plausible:1,unverified:2};
 const sorted=rows.filter(Boolean).sort((a,b)=>order[a.tier]-order[b.tier] || Math.max(0,...b.evidence.map(e=>instant(e.observedAtUtc)||0))-Math.max(0,...a.evidence.map(e=>instant(e.observedAtUtc)||0)) || a.logicalServiceId.localeCompare(b.logicalServiceId));
 const services=new Map();
 for(const r of sorted){const k=r.sourceId+':'+r.logicalServiceId;const existing=services.get(k);if(existing)existing.variantIds.push(String(r.providerItemId));else services.set(k,{...r,variantIds:[String(r.providerItemId)]});}
 const all=[...services.values()];
 const initial=all.filter(r=>r.tier!=='unverified').slice(0,6);
 if(initial.length===6 && initial.every(r=>r.networkId && r.networkId===initial[0].networkId)){
  const alternate=all.find(r=>r.tier===initial[5].tier && r.networkId && r.networkId!==initial[0].networkId && !initial.includes(r));
  if(alternate)initial[5]=alternate;
 }
 return {initial,all};
}
