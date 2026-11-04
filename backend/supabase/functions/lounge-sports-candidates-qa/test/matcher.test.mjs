import {test} from "node:test";
import assert from "node:assert/strict";
import {classify,rankCandidates} from "../matcher.mjs";
const soccer={sportId:"soccer",competitionId:"uefa-champions-league",home:"Fenerbahce",away:"Liverpool",broadcasters:[]};
test("Champions League does not flood Stan or Paramount",()=>{
  for(const name of ["AU| STAN SPORT 1 HD","US| PARAMOUNT PLUS 41 HD"])assert.equal(classify(soccer,{providerItemId:1,name}),null);
});
test("AU beIN 1-3 are curated soccer fallbacks",()=>{
  for(const id of [1,2,3])assert.equal(classify(soccer,{providerItemId:id,name:"AU| BEIN SPORTS "+id+" HD"})?.tier,"plausible");
});
test("fixture-specific event beats generic networks",()=>{
  const rows=[
    {providerItemId:10,name:"FEN vs LIV LIVE"},
    {providerItemId:11,name:"AU| BEIN SPORTS 1 HD"},
    {providerItemId:12,name:"UK| SKY SPORTS FOOTBALL FHD"},
  ];
  const result=rankCandidates(soccer,rows,6);
  assert.equal(result[0].row.providerItemId,10);
  assert.equal(result[0].decision.tier,"verified");
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