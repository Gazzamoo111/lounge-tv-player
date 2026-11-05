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
      independentFixtureStatus:body.coverage.independentFixtureStatus,
      independentFixtureNetworkVariants:body.coverage.independentlyAssignedVariants,
      independentFixtureNetworkServices:body.coverage.independentlyAssignedServices,
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


// NHL-and-cricket real inventory acceptance. These are fixture-specific sources,
// never general NHL Network/Willow guesses or claims of decoded video playback.
const cases=[
 {eventId:"hockey/nhl#401892654",sportId:"hockey",competitionId:"hockey/nhl",
  scheduledStartUtc:"2026-11-06T00:00:00Z",status:"PRE",
  home:"Boston Bruins",away:"Pittsburgh Penguins",expected:[45528,1479774],
  title:"Pittsburgh Penguins at Boston Bruins",source:"ESPN NHL scoreboard"},
 {eventId:"hockey/nhl#401892649",sportId:"hockey",competitionId:"hockey/nhl",
  scheduledStartUtc:"2026-11-06T00:00:00Z",status:"PRE",
  home:"Vegas Golden Knights",away:"Detroit Red Wings",expected:[2153431],
  title:"Detroit Red Wings at Vegas Golden Knights",source:"ESPN NHL scoreboard"},
 {eventId:"cricket/odi#NZ-IND-20261107",sportId:"cricket",competitionId:"cricket/odi",
  scheduledStartUtc:"2026-11-07T02:00:00Z",status:"PRE",
  home:"New Zealand",away:"India",expected:[1960720],
  title:"New Zealand vs India 2nd ODI",
  source:"New Zealand Cricket official 2026-27 home summer fixture schedule; 7 Nov 2026 3pm NZDT"},
];
const multiSport=[];
for(const fixture of cases){
 const params=new URLSearchParams({
   contractVersion:"2",eventId:fixture.eventId,sportId:fixture.sportId,
   competitionId:fixture.competitionId,scheduledStartUtc:fixture.scheduledStartUtc,
   status:fixture.status,title:fixture.title,home:fixture.home,away:fixture.away,
   homeAliases:fixture.home,awayAliases:fixture.away,limit:"12",
 });
 const response=await fetch(endpoint+"?"+params.toString(),
   {signal:AbortSignal.timeout(25000),headers:{Accept:"application/json"}});
 assert.equal(response.status,200,fixture.title+": QA endpoint HTTP "+response.status);
 const payload=await response.json();
 assert.equal(payload.catalogueStale,false,fixture.title+": current BEST inventory required");
 assert.equal(payload.coverage.catalogueScanned,16140,fixture.title+": full inventory expected");
 const ids=payload.candidates.map(c=>c.providerItemId);
 multiSport.push({fixture:fixture.title,ids,expected:fixture.expected,
   fixtureSourceStatus:payload.coverage.independentFixtureStatus,
   confirmedVariants:payload.coverage.independentlyAssignedVariants,
   playbackVerified:payload.coverage.playbackVerified,
   evidence:payload.candidates.map(c=>({id:c.providerItemId,type:c.evidence[0]?.type,tier:c.tier}))});
 for(const id of fixture.expected){
   assert.ok(ids.includes(id),fixture.title+" missing explicitly expected BEST stream "+id+
     ": "+JSON.stringify(multiSport.at(-1)));
 }
 assert.ok(payload.candidates.length<=12,fixture.title+": no hundreds of alternatives");
 assert.ok(payload.candidates.every(c=>c.tier==="plausible"&&
   candidateHasFixtureEvidence(c,fixture.eventId,fixture.sportId,fixture.competitionId)),
   fixture.title+": only scoped evidence and unverified candidate labels");
}
console.log("MULTISPORT_QA "+JSON.stringify(multiSport,null,2));


// System-wide QA regression: no sport may disappear merely because an external
// fixture feed has no network assignment. These synthetic lookups establish
// discoverability only, NEVER that any feed is broadcasting or decoding a game.
const futureUtc=new Date(Date.now()+45*60_000).toISOString();
const discoveryCases=[
  {
    key:"wta",eventId:"tennis/wta#qa-discovery-coverage",sportId:"tennis",
    competitionId:"tennis/wta",home:"QA Player One",away:"QA Player Two",
    minimum:3,kind:/\bTENNIS\b/,
  },
  {
    key:"nba",eventId:"basketball/nba#qa-discovery-coverage",sportId:"basketball",
    competitionId:"basketball/nba",home:"LA Clippers",away:"Miami Heat",
    minimum:2,expectedIds:[836342,836345],kind:/\bNBA\b/,
  },
  {
    key:"nfl",eventId:"football/nfl#qa-discovery-coverage",sportId:"football",
    competitionId:"football/nfl",home:"Baltimore Ravens",away:"Jacksonville Jaguars",
    minimum:2,expectedIds:[836261,836265],kind:/\bNFL\b/,
  },
];
const discovery=[];
for(const spec of discoveryCases){
  const params=new URLSearchParams({
    contractVersion:"2",eventId:spec.eventId,sportId:spec.sportId,
    competitionId:spec.competitionId,scheduledStartUtc:futureUtc,
    status:"PRE",title:spec.home+" vs "+spec.away,
    home:spec.home,away:spec.away,homeAliases:spec.home,
    awayAliases:spec.away,limit:"12",
  });
  const response=await fetch(endpoint+"?"+params,{
    signal:AbortSignal.timeout(25_000),headers:{Accept:"application/json"}});
  assert.equal(response.status,200,spec.key+": QA backend HTTP "+response.status);
  const body=await response.json();
  assert.ok(body.coverage.catalogueScanned>=10000,spec.key+": complete BEST inventory");
  const sportOptions=body.candidates.filter(c=>c.reason==="sport-specific-unverified");
  assert.ok(sportOptions.length>=spec.minimum,
    spec.key+": only "+sportOptions.length+" sport-specific alternatives");
  assert.ok(sportOptions.every(c=>c.evidence?.[0]?.type==="sport-option"&&
    c.playbackObservation?.status==="unknown"&&
    spec.kind.test(c.displayName.toUpperCase())),
    spec.key+": sport fallback must be scoped and explicitly unverified");
  const ids=body.candidates.map(c=>c.providerItemId);
  for(const id of spec.expectedIds||[]){
    assert.ok(ids.includes(id),spec.key+": missing participating-team provider id "+id);
  }
  assert.equal(body.coverage.playbackVerified,0,spec.key+": no fake video verification");
  assert.equal(body.coverage.coverageGap,true,spec.key+": no guaranteed streams");
  discovery.push({sport:spec.key,options:sportOptions.map(c=>({
    id:c.providerItemId,name:c.displayName,reason:c.reason
  })),confirmed:body.coverage.broadcastConfirmed,playbackVerified:body.coverage.playbackVerified});
}
console.log("SYSTEMIC_SPORT_DISCOVERY_QA "+JSON.stringify(discovery,null,2));
