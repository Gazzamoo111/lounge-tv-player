import {test} from "node:test";
import assert from "node:assert/strict";
import {xmlUtc,nzProgrammeCandidates} from "../programme-evidence.mjs";

const start="2026-11-04T11:00:00Z";
const now=Date.parse("2026-11-04T11:15:00Z");
const event={
 eventId:"basketball/nba#401999999",sportId:"basketball",competitionId:"basketball/nba",
 scheduledStartUtc:start,status:"IN",title:"Philadelphia 76ers vs Detroit Pistons",
 home:"Philadelphia 76ers",away:"Detroit Pistons",
 homeAliases:["Philadelphia 76ers","76ers"],awayAliases:["Detroit Pistons","Pistons"]
};
const programme=(title,channel="SkySport1.nz",startTime="20261105000000 +1300")=>({
 "@_channel":channel,"@_start":startTime,"@_stop":"20261105030000 +1300",
 title,desc:"NBA Basketball: live game"
});
const rows=[
 {provider_item_id:101,name:"NZ| SKY SPORT 1 FHD",epg_channel_id:"SkySport1.nz",category_id:"2401"},
 {provider_item_id:102,name:"NZ| SKY SPORTS NZ 1",epg_channel_id:"SkySport1.nz",category_id:"1606"},
 {provider_item_id:103,name:"NZ| SKY SPORT 2 HD",epg_channel_id:"SkySport2.nz",category_id:"1007"},
 {provider_item_id:104,name:"US| SKY SPORT 1 HD",epg_channel_id:"SkySport1.nz",category_id:"680"}
];

test("XMLTV offset parses fixture time independently of the device timezone",()=>{
 assert.equal(new Date(xmlUtc("20261105000000 +1300")).toISOString(),start);
 assert.ok(Number.isNaN(xmlUtc("20261105")));
});

test("dated programme identity retains every matching BEST provider variant from original categories",()=>{
 const actual=nzProgrammeCandidates(event,rows,[programme("Philadelphia 76ers vs Detroit Pistons")],now,"https://example.invalid/skyguide");
 assert.deepEqual(actual.map(x=>x.providerItemId),[101,102]);
 assert.deepEqual(actual.map(x=>x.categoryId),["2401","1606"]);
 assert.ok(actual.every(x=>x.tier==="plausible"&&x.playbackObservation.status==="unknown"));
 assert.ok(actual.every(x=>x.evidence[0].eventId===event.eventId&&x.evidence[0].type==="programme"));
});

test("other fixture, replay, wrong channel and wrong broadcast time cannot be promoted",()=>{
 for(const bad of [
   programme("Boston Celtics vs Milwaukee Bucks"),
   programme("Philadelphia 76ers vs Detroit Pistons REPLAY"),
   programme("Philadelphia 76ers vs Detroit Pistons","SkySport2.nz"),
   programme("Philadelphia 76ers vs Detroit Pistons","SkySport1.nz","20261105043000 +1300")
 ]){
   const found=nzProgrammeCandidates(event,[rows[0]],[bad],now,"https://example.invalid/skyguide");
   assert.deepEqual(found,[],bad.title);
 }
});
