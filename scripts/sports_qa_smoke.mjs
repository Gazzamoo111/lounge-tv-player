import assert from "node:assert/strict";

const endpoint="https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-sports-candidates-qa";
const DAY=86_400_000;
const dateKey=offset=>new Date(Date.now()+offset*DAY).toISOString().slice(0,10).replaceAll("-","");
const days=[dateKey(-1),dateKey(0),dateKey(1)];

function aliases(team={}){
  return [...new Set([
    team.displayName,team.name,team.shortDisplayName,team.abbreviation,
  ].filter(Boolean))].join("|");
}
function requestStatus(event){
  return event.status?.type?.state==="in"?"IN":"PRE";
}
function candidateHasFixtureEvidence(candidate,eventId,sport,competitionId){
  return candidate.evidence?.some(e=>
    e.eventId===eventId && e.sportId===sport && e.competitionId===competitionId
  );
}
const norm=value=>String(value||"").toUpperCase().replace(/[^A-Z0-9]+/g," ").trim();
const hasAlias=(text,team={})=>{
  const hay=" "+norm(text)+" ";
  return [team.displayName,team.name,team.shortDisplayName,team.abbreviation]
    .filter(Boolean).map(norm).filter(a=>a.length>=3)
    .some(a=>hay.includes(" "+a+" "));
};

const events=[];
const seen=new Set();
for(const day of days){
  const url=`https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates=${day}&limit=100`;
  const resp=await fetch(url,{signal:AbortSignal.timeout(12000)});
  if(!resp.ok)continue;
  const data=await resp.json();
  for(const event of data.events||[]){
    if(seen.has(event.id)||event.competitions?.[0]?.competitors?.length!==2)continue;
    if(!["pre","in"].includes(event.status?.type?.state))continue;
    seen.add(event.id);events.push(event);
  }
}
events.sort((a,b)=>
  (a.status?.type?.state==="in"?0:1)-(b.status?.type?.state==="in"?0:1) ||
  Math.abs(Date.parse(a.date)-Date.now())-Math.abs(Date.parse(b.date)-Date.now())
);
assert.ok(events.length>=3,"Need at least three live/upcoming NBA fixtures for QA smoke");

const verifiedFixtures=[];
for(const event of events){
  const competitors=event.competitions[0].competitors;
  const home=competitors.find(c=>c.homeAway==="home")||competitors[0];
  const away=competitors.find(c=>c.homeAway==="away")||competitors[1];
  const eventId=`basketball/nba#${event.id}`;
  const competitionId="basketball/nba";
  const params=new URLSearchParams({
    contractVersion:"2",eventId,sportId:"basketball",competitionId,
    scheduledStartUtc:event.date,status:requestStatus(event),title:event.name,
    home:home.team?.displayName||"",away:away.team?.displayName||"",
    homeAliases:aliases(home.team),awayAliases:aliases(away.team),limit:"12",
  });
  const result=await fetch(endpoint+"?"+params.toString(),{
    signal:AbortSignal.timeout(25000),headers:{Accept:"application/json"}
  });
  const raw=await result.text();
  assert.equal(result.status,200,`QA endpoint HTTP ${result.status}: ${raw.slice(0,350)}`);
  const body=JSON.parse(raw);
  assert.equal(body.implementationId,"qa-event-streams-v5");
  assert.equal(body.contractVersion,2);
  assert.equal(body.eventId,eventId);
  assert.equal(body.catalogueStale,false,"Full BEST inventory must be refreshed");
  assert.ok(body.coverage.catalogueScanned>=10000,"QA must scan the full BEST internal inventory");
  assert.ok(body.candidates.length<=12,"No hundreds of unrelated alternatives");
  assert.equal(body.coverage.returned,body.candidates.length);
  assert.ok(body.candidates.every(c=>c.tier==="plausible"),
    "Unverified streams must never be labeled verified");
  assert.ok(body.candidates.every(c=>candidateHasFixtureEvidence(c,eventId,"basketball",competitionId)),
    "Every suggestion must carry the exact fixture identity");
  assert.ok(body.candidates.every(c=>{
    const kind=c.evidence?.[0]?.type;
    return kind!=="name-hint" || (hasAlias(c.displayName,home.team)&&hasAlias(c.displayName,away.team));
  }),"Provider-name suggestions must visibly identify both selected NBA teams");
  assert.equal(body.coverage.playbackVerified,0,"No playback probe credentials configured");
  assert.equal(body.coverage.coverageGap,true,"Unverified video is not three verified streams");
  if(body.candidates.length){
    verifiedFixtures.push({
      fixture:event.name,eventId,
      independentlyAssignedServices:body.coverage.broadcastConfirmed,
      providerTitleHints:body.coverage.providerNameHints,
      suggestions:body.candidates.map(c=>({
        id:c.providerItemId,name:c.displayName,tier:c.tier,evidence:c.evidence?.[0]?.type
      })),
    });
  }
  if(verifiedFixtures.length===3)break;
}
assert.equal(verifiedFixtures.length,3,
  `Expected relevant candidates for three NBA fixtures; found ${verifiedFixtures.length}`);

console.log(JSON.stringify({
  catalogueScanned:"full BEST live inventory",
  fixtures:verifiedFixtures,
  playbackVerified:0,
  note:"Remote QA API relevance gate only. Actual decoded game content still requires Fire TV acceptance testing."
},null,2));
