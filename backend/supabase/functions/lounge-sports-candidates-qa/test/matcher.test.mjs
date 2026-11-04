import {test} from "node:test";
import assert from "node:assert/strict";
import {classify,rankCandidates,discoveryCandidate} from "../matcher.mjs";
const soccer={sportId:"soccer",competitionId:"uefa-champions-league",home:"Fenerbahce",away:"Liverpool",broadcasters:[]};
test("Champions League does not flood Stan or Paramount",()=>{
  for(const name of ["AU| STAN SPORT 1 HD","US| PARAMOUNT PLUS 41 HD"])assert.equal(classify(soccer,{providerItemId:1,name}),null);
});
test("AU beIN 1-3 are curated soccer fallbacks",()=>{
  for(const id of [1,2,3])assert.equal(classify(soccer,{providerItemId:id,name:"AU| BEIN SPORTS "+id+" HD"})?.tier,"unverified");
});
test("fixture-specific event beats generic networks",()=>{
  const rows=[
    {providerItemId:10,name:"FEN vs LIV LIVE"},
    {providerItemId:11,name:"AU| BEIN SPORTS 1 HD"},
    {providerItemId:12,name:"UK| SKY SPORTS FOOTBALL FHD"},
  ];
  const result=rankCandidates(soccer,rows,6);
  assert.equal(result[0].row.providerItemId,10);
  assert.equal(result[0].decision.tier,"unverified");
});
test("recommendations are capped and quality variants dedupe",()=>{
  const rows=[
    {providerItemId:1,name:"AU| BEIN SPORTS 1 HD"},
    {providerItemId:2,name:"AU| BEIN SPORTS 1 FHD"},
    {providerItemId:3,name:"AU| BEIN SPORTS 2 HD"},
    {providerItemId:4,name:"AU| BEIN SPORTS 3 HD"},
    {providerItemId:5,name:"UK| TNT SPORTS 1 HD"},
    {providerItemId:6,name:"UK| TNT SPORTS 2 HD"},
    {providerItemId:7,name:"UK| TNT SPORTS 3 HD"},
    {providerItemId:8,name:"UK| TNT SPORTS 4 HD"},
  ];
  const result=rankCandidates(soccer,rows,6);
  assert.equal(result.length,6);
  assert.equal(result.filter(x=>x.decision.logicalServiceId==="AU:BEIN:1").length,1);
});
test("AU ESPN1 is excluded",()=>{
  assert.equal(classify(soccer,{providerItemId:646974,name:"AU| ESPN 1 HD"}),null);
  assert.equal(classify(soccer,{providerItemId:1520168,name:"AU| KAYO - ESPN 1"}),null);
});
test("all sports: catalogue names and broadcaster hints never prove a live broadcast",()=>{
  const fixtures=[
    {sportId:"rugby",competitionId:"international",home:"New Zealand",away:"England"},
    {sportId:"basketball",competitionId:"nba",home:"Lakers",away:"Celtics"},
    {sportId:"cricket",competitionId:"international",home:"India",away:"Australia"},
    {sportId:"mma",competitionId:"ufc",home:"Fighter One",away:"Fighter Two"},
  ];
  for(const fixture of fixtures){
    const named=classify(fixture,{providerItemId:123,name:fixture.home+" vs "+fixture.away+" LIVE"});
    assert.equal(named?.tier,"unverified");
    assert.equal(classify(fixture,{providerItemId:456,name:"AU | ESPN 1 HD"}),null);
  }
});

test("QA HTTP adapter never manufactures reviewed identity or programme evidence",()=>{
  const candidate=discoveryCandidate({provider_item_id:123,category_id:"sport",name:"AU | BEIN SPORTS 1 HD"},
    {tier:"verified",logicalServiceId:"AU:BEIN:1",market:"AU",reason:"fixture-name"},
    Date.parse("2026-11-05T00:00:00Z"));
  assert.equal(candidate.tier,"unverified");
  assert.equal(candidate.identityReviewed,false);
  assert.deepEqual(candidate.evidence,[]);
  assert.equal(candidate.playbackObservation.status,"unknown");
});
