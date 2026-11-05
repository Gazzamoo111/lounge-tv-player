import {test} from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import {stripTypeScriptTypes} from "node:module";
import {rankCandidates,discoveryCandidate,linearAssignmentCandidates,sportFallbackCandidates} from "../matcher.mjs";
import {nzProgrammeCandidates} from "../programme-evidence.mjs";

const rows=[
 {provider_item_id:836373,category_id:"676",name:"Detroit Pistons vs Philadelphia 76ers @ Nov 4 7:00 PM :NBA 01"},
 {provider_item_id:1955374,category_id:"2076",name:"LIVE | DET - PISTONS VS PHI - 76ERS | US: NBA PASS PPV 2"},
 {provider_item_id:1949811,category_id:"1118",name:"US ESPN+ | Basketball: 76ers vs. Pistons"},
 {provider_item_id:1955373,category_id:"2076",name:"LIVE | BOS - CELTICS VS MIL - BUCKS | US: NBA PASS PPV 3"},
 {provider_item_id:749756,category_id:"676",name:"US| NBA TV HD"},
];
const source=fs.readFileSync(new URL("../index.ts",import.meta.url),"utf8");
const code=stripTypeScriptTypes(source.slice(source.indexOf("Deno.serve")));
async function request(query,inventory=rows,geo=[]){
 let handler,calls=0;
 vm.runInNewContext(code,{Deno:{serve:h=>handler=h},URL,Response,console,rankCandidates,discoveryCandidate,linearAssignmentCandidates,sportFallbackCandidates,
   findIndependentBroadcasts:async()=>({geo,source:"independent-qa-source",status:geo.length?"fixture-confirmed":"fixture-source-not-configured"}),
   loadNzSportsGuide:async()=>[], NZ_GUIDE_URL:"https://qa.invalid/skyguide", nzProgrammeCandidates,
   iso:ms=>new Date(ms).toISOString(),sql:async()=>++calls===1?inventory:[{poll_id:352,completed_at:new Date().toISOString()}]});
 return handler(new Request("https://qa.invalid/?"+query));
}

test("QA endpoint scans the internal pool and returns only exact fixture rows",async()=>{
 const q="contractVersion=2&eventId=basketball%2Fnba%23det-phi&sportId=basketball&competitionId=basketball%2Fnba"+
   "&scheduledStartUtc="+encodeURIComponent(new Date(Date.now()-60_000).toISOString())+
   "&status=IN&title=Detroit+Pistons+at+Philadelphia+76ers&home=Detroit+Pistons&away=Philadelphia+76ers"+
   "&homeAliases=Detroit+Pistons%7CPistons%7CDET&awayAliases=Philadelphia+76ers%7C76ers%7CPHI";
 const response=await request(q);
 assert.equal(response.status,200);
 const body=await response.json();
 assert.equal(body.implementationId,"qa-event-streams-v5");
 assert.equal(body.catalogueRevision,"352");
 assert.equal(body.totalEligible,5);
 assert.deepEqual(body.candidates.map(x=>x.providerItemId),[1955374,836373,1949811,749756]);
 assert.equal(body.coverage.broadcastConfirmed,0);
 assert.equal(body.coverage.playbackVerified,0);
 assert.equal(body.coverage.providerNameHints,3);
 assert.equal(body.coverage.sportFallbackOptions,1);
 assert.equal(body.coverage.coverageGap,true);
 assert.equal(body.candidates[3].evidence[0].type,"sport-option");
 assert.ok(body.candidates.every(x=>x.playbackObservation.status==="unknown"));
 assert.equal(body.coverage.broadcastConfirmed,0,"Sport-specific alternatives cannot count as confirmed broadcasts");
});

test("missing participant identity fails closed",async()=>{
 const response=await request("contractVersion=2&eventId=x&sportId=basketball&competitionId=basketball%2Fnba&scheduledStartUtc=2026-11-05T00%3A00%3A00Z");
 assert.equal(response.status,400);
});

test("fixture-assigned US regional backups outrank twelve provider-name hints without dropping their IDs",async()=>{
 const assigned=[
  {provider_item_id:1696805,category_id:"680",name:"US| SPECTRUM SPORTSNET HD"},
  {provider_item_id:2151566,category_id:"2540",name:"TV| SPECTRUM SPORTSNET RAW"}
 ];
 const named=Array.from({length:15},(_,n)=>({
   provider_item_id:2_500_000+n,category_id:"1334",
   name:"NBA | Detroit Pistons vs Philadelphia 76ers | backup "+n
 }));
 const geo=[{region:"us",type:{shortName:"TV"},media:{shortName:"Spectrum Sports Net"}}];
 const q="contractVersion=2&eventId=basketball%2Fnba%23det-phi&sportId=basketball&competitionId=basketball%2Fnba"+
   "&scheduledStartUtc="+encodeURIComponent(new Date(Date.now()-60_000).toISOString())+
   "&status=IN&title=Detroit+Pistons+vs+Philadelphia+76ers&home=Detroit+Pistons&away=Philadelphia+76ers"+
   "&homeAliases=Detroit+Pistons%7CPistons&awayAliases=Philadelphia+76ers%7C76ers";
 const response=await request(q,[...named,...assigned],geo);
 assert.equal(response.status,200);
 const body=await response.json();
 assert.deepEqual(body.candidates.slice(0,2).map(c=>c.providerItemId),[1696805,2151566]);
 assert.deepEqual(body.candidates.slice(0,2).map(c=>c.categoryId),["680","2540"]);
 assert.equal(body.coverage.independentlyAssignedVariants,2);
 assert.equal(body.coverage.providerNameHints,15);
 assert.equal(body.coverage.returned,12);
 assert.equal(body.coverage.playbackVerified,0);
});
