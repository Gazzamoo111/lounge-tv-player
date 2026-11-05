import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {stripTypeScriptTypes} from 'node:module';
import {rankCandidates,discoveryCandidate} from '../matcher.mjs';
const catalogue=JSON.parse(fs.readFileSync(new URL('./fixtures/catalogue-352.json',import.meta.url)));
const source=fs.readFileSync(new URL('../index.ts',import.meta.url),'utf8');
const code=stripTypeScriptTypes(source.slice(source.indexOf('Deno.serve')));
async function request(query){
 let handler,calls=0;
 vm.runInNewContext(code,{Deno:{serve:h=>handler=h},URL,Response,console,rankCandidates,discoveryCandidate,iso:ms=>new Date(ms).toISOString(),sql:async()=>++calls===1?catalogue.rows:[{poll_id:352,completed_at:new Date().toISOString()}]});
 return handler(new Request('https://qa.invalid/?'+query));
}
test('QA HTTP adapter exercises actual SQL-shaped inventory and returns client marker',async()=>{
 const response=await request('contractVersion=2&eventId=regression-ucl&sportId=soccer&competitionId=soccer%2Fuefa.champions&scheduledStartUtc=2026-11-05T00:00:00Z&home=Liverpool&away=Real+Madrid&broadcasters=Stan+Sport%7CParamount+Plus');
 assert.equal(response.status,200);const body=await response.json();
 assert.equal(body.implementationId,'qa-sports-evidence-v3');assert.equal(body.catalogueRevision,'352');
 assert.ok(body.candidates.length>0);assert.ok(body.candidates.length<=6);
 assert.ok(body.candidates.every(x=>Number.isSafeInteger(x.providerItemId)&&x.providerItemId>0));
 assert.equal(body.candidates.some(x=>/STAN|PARAMOUNT/i.test(x.displayName)),false);
 assert.equal(body.candidates.filter(x=>x.logicalServiceId==='AU:BEIN:1').length,1);
 assert.equal(body.candidates.some(x=>x.tier!=='unverified'),false);
});
