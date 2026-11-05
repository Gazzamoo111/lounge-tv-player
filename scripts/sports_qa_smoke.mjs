import assert from "node:assert/strict";

const endpoint="https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-sports-candidates-qa";
const day=new Date().toISOString().slice(0,10).replaceAll("-","");
const feeds=[["basketball","nba"],["hockey","nhl"],["soccer","eng.1"]];
let event,sport,league;
for(const [s,l] of feeds){
  const url=`https://site.api.espn.com/apis/site/v2/sports/${s}/${l}/scoreboard?dates=${day}&limit=100`;
  const resp=await fetch(url,{signal:AbortSignal.timeout(12000)});
  if(!resp.ok)continue;
  const data=await resp.json();
  const candidate=(data.events||[]).find(e=>e.competitions?.[0]?.competitors?.length===2&&
    ["pre","in"].includes(e.status?.type?.state));
  if(candidate){event=candidate;sport=s;league=l;break;}
}
assert.ok(event,"No ESPN NBA/NHL/football two-team fixture found today for live QA smoke");
const competitors=event.competitions[0].competitors;
const home=competitors.find(c=>c.homeAway==="home")||competitors[0];
const away=competitors.find(c=>c.homeAway==="away")||competitors[1];
const params=new URLSearchParams({
  contractVersion:"2",eventId:`${sport}/${league}#${event.id}`,sportId:sport,
  competitionId:`${sport}/${league}`,scheduledStartUtc:event.date,
  status:event.status?.type?.state==="in"?"IN":"PRE",title:event.name,
  home:home.team?.displayName||"",away:away.team?.displayName||"",limit:"12",
});
const url=endpoint+"?"+params.toString();
const result=await fetch(url,{signal:AbortSignal.timeout(25000),headers:{Accept:"application/json"}});
const raw=await result.text();
assert.equal(result.status,200,`QA endpoint HTTP ${result.status}: ${raw.slice(0,350)}`);
const body=JSON.parse(raw);
assert.equal(body.implementationId,"qa-event-streams-v5");
assert.equal(body.contractVersion,2);
assert.equal(body.eventId,params.get("eventId"));
assert.equal(body.catalogueStale,false,"Full BEST inventory must be refreshed");
assert.ok(body.coverage.catalogueScanned>=10000,"QA must scan the full BEST internal inventory");
assert.ok(body.candidates.length<=12,"No hundreds of unrelated alternatives");
assert.equal(body.coverage.returned,body.candidates.length);
assert.ok(body.candidates.every(c=>c.tier==="plausible"),"Unverified streams must never be labeled verified");
assert.ok(body.candidates.every(c=>c.evidence?.some(e=>e.eventId===body.eventId&&
  e.sportId===sport&&e.competitionId===params.get("competitionId"))),
  "Every suggestion must carry the exact fixture identity");
assert.equal(body.coverage.playbackVerified,0,"No playback probe credentials configured");
assert.equal(body.coverage.coverageGap,true,"Unverified video is not 3 verified streams");
console.log(JSON.stringify({
  fixture:event.name,eventId:body.eventId,catalogueScanned:body.coverage.catalogueScanned,
  independentlyAssignedServices:body.coverage.broadcastConfirmed,
  providerTitleHints:body.coverage.providerNameHints,
  suggestions:body.candidates.map(c=>({id:c.providerItemId,name:c.displayName,tier:c.tier,evidence:c.evidence[0].type})),
  playbackVerified:body.coverage.playbackVerified,
  note:"Remote QA API contract only, not decoded-stream validation"
},null,2));
