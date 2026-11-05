import {norm,discoveryCandidate,inventoryRow,excluded} from "./matcher.mjs";

const phrase=(hay,needle)=>needle&&(" "+norm(hay)+" ").includes(" "+norm(needle)+" ");
const badProgramme=/\b(?:REPLAY|HIGHLIGHTS|PREVIEW|CLASSIC|ARCHIVE|REPEAT)\b/i;
const badAlias=new Set(["UNITED","CITY","TEAM","SPORT","NEW ZEALAND","AUSTRALIA"]);
const textValue=v=>typeof v==="string"?v:String(v?.["#text"]||"");

/** XMLTV time with explicit timezone offset, not the viewer's local device clock. */
export function xmlUtc(value){
 const m=String(value||"").match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?\s*([+-]\d{4})$/);
 if(!m)return NaN;
 const zone=m[7].slice(0,3)+":"+m[7].slice(3);
 return Date.parse(m[1]+"-"+m[2]+"-"+m[3]+"T"+m[4]+":"+m[5]+":"+(m[6]||"00")+zone);
}
function exactFixture(event,programme){
 const start=Date.parse(event.scheduledStartUtc),ps=xmlUtc(programme["@_start"]),pe=xmlUtc(programme["@_stop"]);
 if(!Number.isFinite(start)||!(ps<pe&&ps<=start+15*60000&&pe>start))return false;
 const text=[textValue(programme.title),textValue(programme.desc)].join(" ");
 if(badProgramme.test(text))return false;
 const participants=["home","away"].map(side=>[event[side],...(event[side+"Aliases"]||[])]
   .map(norm).filter(v=>v.length>=4&&!badAlias.has(v)));
 if(participants.every(p=>p.length)){
  return participants.every(p=>p.some(alias=>phrase(text,alias)));
 }
 const title=norm(event.title);
 return title.length>=15&&phrase(text,title);
}
/** Match only an actual dated programme on a BEST provider row with the SAME XMLTV identity. */
export function nzProgrammeCandidates(event,rows,programmes,now=Date.now(),source="",limit=12){
 const matching=new Map();
 for(const p of programmes||[]){
  const id=String(p["@_channel"]||"").toLowerCase();
  if(!id||!exactFixture(event,p))continue;
  const arr=matching.get(id)||[];arr.push(p);matching.set(id,arr);
 }
 const candidates=[];
 for(const raw of rows){
  const row=inventoryRow(raw),epgId=String(row.epg_channel_id||"");
  const matches=matching.get(epgId.toLowerCase())||[];
  if(!epgId||!matches.length||excluded(row)||!/^NZ\s*[|:]\s*/i.test(String(row.name||"")))continue;
  const logicalServiceId="NZ:"+norm(epgId).replaceAll(" ","");
  for(const p of matches.slice(0,1)){
   const decision={
    tier:"plausible",logicalServiceId,market:"NZ",
    reason:"independent-fixture-specific-xmltv-programme",
    accepted:[{
      type:"programme",source,eventId:event.eventId,sportId:event.sportId,
      competitionId:event.competitionId,serviceId:logicalServiceId,market:"NZ",
      startUtc:new Date(xmlUtc(p["@_start"])).toISOString(),
      endUtc:new Date(xmlUtc(p["@_stop"])).toISOString(),
      observedAtUtc:new Date(now).toISOString(),
      programmeTitle:textValue(p.title),contentMismatch:false
    }]
   };
   candidates.push(discoveryCandidate(row,decision,now));
  }
 }
 return candidates.sort((a,b)=>a.providerItemId-b.providerItemId).slice(0,Math.max(1,Math.min(12,limit)));
}
