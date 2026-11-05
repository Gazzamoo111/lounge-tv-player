import {test} from "node:test";
import assert from "node:assert/strict";
import {linearAssignmentCandidates} from "../matcher.mjs";
import {findIndependentBroadcasts} from "../fixture-evidence.mjs";

const start="2026-11-05T00:00:00Z", now=Date.parse("2026-11-05T00:30:00Z");
const event={eventId:"basketball/nba#401999999",sportId:"basketball",competitionId:"basketball/nba",
 scheduledStartUtc:start,status:"IN",home:"Philadelphia 76ers",away:"Detroit Pistons",
 homeAliases:["Philadelphia 76ers","76ers"],awayAliases:["Detroit Pistons","Pistons"]};
const geo=[{region:"us",type:{shortName:"TV"},media:{shortName:"ESPN"}}];
const fixture={id:"401999999",date:start,competitions:[{competitors:[
 {team:{displayName:"Philadelphia 76ers",name:"76ers"}},
 {team:{displayName:"Detroit Pistons",name:"Pistons"}}],geoBroadcasts:geo}]};
const request=events=>async()=>({ok:true,json:async()=>({events})});

test("independent fixture lookup verifies event ID, teams and kickoff",async()=>{
 const ok=await findIndependentBroadcasts(event,request([fixture]));
 assert.equal(ok.status,"fixture-confirmed");assert.equal(ok.geo.length,1);
 assert.match(ok.source,/site\.api\.espn\.com/);
 for(const bad of [
  {...fixture,id:"401999998"},
  {...fixture,date:"2026-11-05T02:00:00Z"},
  {...fixture,competitions:[{...fixture.competitions[0],competitors:[
   {team:{displayName:"Boston Celtics"}},{team:{displayName:"Milwaukee Bucks"}}]}]}
 ])assert.equal((await findIndependentBroadcasts(event,request([bad]))).geo.length,0);
});

test("only source-confirmed US TV services match, all true provider variants remain",()=>{
 const rows=[
  {provider_item_id:1,name:"US| ESPN FHD",category_id:"1"},
  {provider_item_id:2,name:"US| ESPN HD BACKUP",category_id:"2"},
  {provider_item_id:3,name:"US| ESPN 2 HD",category_id:"3"},
  {provider_item_id:4,name:"AU| ESPN FHD",category_id:"4"},
  {provider_item_id:5,name:"US| TNT HD",category_id:"5"},
  {provider_item_id:6,name:"US| ESPN+ 101",category_id:"6"},
  {provider_item_id:7,name:"MOVIES| ESPN",category_id:"7"},
  {provider_item_id:8,name:"US| ESPN ᴴᴰ ⁶⁰ᶠᵖˢ",category_id:"8"}
 ];
 const found=linearAssignmentCandidates(event,rows,geo,"https://site.api.espn.com",now);
 assert.deepEqual(found.map(x=>x.providerItemId),[1,2,8]);
 assert.equal(found[0].tier,"plausible");
 assert.equal(found[0].evidence[0].type,"assignment");
 assert.equal(found[0].playbackObservation.status,"unknown");
 assert.deepEqual(found.map(x=>x.categoryId),["1","2","8"]);
});

test("broadcaster hints never invent a TV assignment",()=>{
 const rows=[{provider_item_id:1,name:"US| ESPN FHD"}];
 const source="https://site.api.espn.com";
 assert.equal(linearAssignmentCandidates(event,rows,[],source,now).length,0);
 assert.equal(linearAssignmentCandidates(event,rows,[{region:"us",type:{shortName:"Streaming"},media:{shortName:"ESPN"}}],source,now).length,0);
 assert.equal(linearAssignmentCandidates(event,rows,[{region:"au",type:{shortName:"TV"},media:{shortName:"ESPN"}}],source,now).length,0);
});


test("conflicting ESPN live TV assignments are withheld across simultaneous NBA fixtures",async()=>{
 const other={...fixture,id:"401888888",competitions:[{
  competitors:[{team:{displayName:"Orlando Magic"}},{team:{displayName:"Phoenix Suns"}}],
  geoBroadcasts:geo
 }]};
 const outcome=await findIndependentBroadcasts(event,request([fixture,other]));
 assert.equal(outcome.geo.length,0);
 assert.equal(outcome.status,"broadcast-assignments-absent-or-conflicted");
});

test("Lakers fixture includes BEST Spectrum SportsNet and TV RAW, no other regional sports networks",()=>{
 const lakers={...event,eventId:"basketball/nba#401909934",home:"Sacramento Kings",away:"Los Angeles Lakers",
   homeAliases:["Sacramento Kings","Kings"],awayAliases:["Los Angeles Lakers","Lakers"]};
 const spectrum=[{region:"us",type:{shortName:"TV"},media:{shortName:"Spectrum Sports Net"}}];
 const rows=[
  {provider_item_id:1696805,name:"US| SPECTRUM SPORTSNET",category_id:"680"},
  {provider_item_id:2151566,name:"TV| SPECTRUM SPORTSNET ᴿᴬᵂ",category_id:"2540"},
  {provider_item_id:1597044,name:"US| SPECTRUM NEWS 1",category_id:"1863"},
  {provider_item_id:45579,name:"US| ESPN HD",category_id:"680"},
  {provider_item_id:2151620,name:"TV| NHL NETWORK ᴿᴬᵂ",category_id:"2540"},
 ];
 const suggestions=linearAssignmentCandidates(lakers,rows,spectrum,"espn-confirmed",now);
 assert.deepEqual(suggestions.map(x=>x.providerItemId),[1696805,2151566]);
 assert.deepEqual(suggestions.map(x=>x.categoryId),["680","2540"]);
 assert.ok(suggestions.every(x=>x.evidence[0].type==="assignment"&&x.tier==="plausible"));
});

test("NHL regional TV abbreviations map only their explicitly assigned BEST service",()=>{
 const nhl={...event,eventId:"hockey/nhl#401892649",sportId:"hockey",competitionId:"hockey/nhl"};
 const rows=[
  {provider_item_id:2153431,name:"US| DETROIT SPORTSNET",category_id:"680"},
  {provider_item_id:2151648,name:"TV| WILLOW 2 ᴿᴬᵂ",category_id:"2540"},
  {provider_item_id:2151620,name:"TV| NHL NETWORK ᴿᴬᵂ",category_id:"2540"}
 ];
 const evidence=[{region:"us",type:{shortName:"TV"},media:{shortName:"DSN"}}];
 const found=linearAssignmentCandidates(nhl,rows,evidence,"espn-confirmed",now);
 assert.deepEqual(found.map(x=>x.providerItemId),[2153431]);
});

test("explicit non-US regional TV assignments find AU, NZ and UK variants without cross-market leakage",()=>{
 const eventForMarkets={...event,sportId:"cricket",competitionId:"cricket/test"};
 const schedule=[
  {region:"au",type:{shortName:"TV"},media:{shortName:"Fox Cricket"}},
  {region:"nz",type:{shortName:"TV"},media:{shortName:"Sky Sport 1"}},
  {region:"gb",type:{shortName:"TV"},media:{shortName:"Sky Sports Cricket"}},
 ];
 const rows=[
  {provider_item_id:646948,name:"AU| FOX CRICKET HD",category_id:"384"},
  {provider_item_id:1001,name:"NZ| SKY SPORT 1 HD",category_id:"401"},
  {provider_item_id:1002,name:"UK| SKY SPORTS CRICKET FHD",category_id:"500"},
  {provider_item_id:1003,name:"US| FOX CRICKET HD",category_id:"600"},
  {provider_item_id:1004,name:"AU| SKY SPORT 1 HD",category_id:"700"}
 ];
 const found=linearAssignmentCandidates(eventForMarkets,rows,schedule,"fixture-tv-guide",now,100);
 assert.deepEqual(found.map(x=>x.providerItemId),[1002,1001,646948]);
 assert.ok(found.every(x=>x.tier==="plausible"&&x.evidence[0].type==="assignment"));
 assert.deepEqual(found.map(x=>x.serviceMarket),["UK","NZ","AU"]);
});

test("ESPN 403 falls back only to fresh exact-fixture TV assignments collected on the GitHub runner",async()=>{
 const snapshot={
  contractVersion:1,collectedAtUtc:new Date(Date.now()-30000).toISOString(),
  expiresAtUtc:new Date(Date.now()+7200000).toISOString(),
  fixtures:[{eventId:event.eventId,competitionId:event.competitionId,scheduledStartUtc:event.scheduledStartUtc,
    participants:[{name:"Philadelphia 76ers",aliases:["76ers"]},{name:"Detroit Pistons",aliases:["Pistons"]}],
    geoBroadcasts:geo}]
 };
 const requester=async(url)=>url.includes("site.api.espn.com")
   ?{ok:false,status:403}:{ok:true,json:async()=>snapshot};
 const found=await findIndependentBroadcasts(event,requester);
 assert.equal(found.status,"fixture-confirmed-ci-snapshot");
 assert.deepEqual(found.geo,geo);
 assert.match(found.source,/raw\.githubusercontent\.com/);
 for(const bad of [
  {...snapshot,expiresAtUtc:new Date(Date.now()-10000).toISOString()},
  {...snapshot,fixtures:[{...snapshot.fixtures[0],eventId:"basketball/nba#401111111"}]},
  {...snapshot,fixtures:[{...snapshot.fixtures[0],participants:[{name:"Orlando Magic"},{name:"Phoenix Suns"}]}]}
 ]){
  const blocked=await findIndependentBroadcasts(event,async(url)=>url.includes("site.api.espn.com")
    ?{ok:false,status:403}:{ok:true,json:async()=>bad});
  assert.equal(blocked.geo.length,0);
 }
});
