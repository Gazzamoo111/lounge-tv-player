import {test} from 'node:test';import assert from 'node:assert/strict';
import fs from 'node:fs';import vm from 'node:vm';import {stripTypeScriptTypes} from 'node:module';
import {parseRequest,decide,rank,excluded} from '../matcher.mjs';
const source=fs.readFileSync(new URL('../index.ts',import.meta.url),'utf8');
const pure=stripTypeScriptTypes(source.slice(source.indexOf('const SPORT_CATEGORY_IDS')));
async function request(query){let handler,queryCount=0;const context={Deno:{serve:h=>handler=h},URL,Response,console,parseRequest,decide,rank,excluded,sql:async()=>{
 queryCount++;if(queryCount===1)return [];if(queryCount===2)return [{provider_item_id:101,category_id:'1308',name:'Arsenal vs Chelsea'}];return [{poll_id:138,completed_at:'2026-11-01T12:35:00Z'}];
}};vm.runInNewContext(pure,context);return handler(new Request('https://example.invalid/?'+query));}
test('R08 legacy endpoint cannot promote names to definite/likely',async()=>{const r=await request('sport=soccer&home=Arsenal&away=Chelsea');assert.equal(r.status,200);const body=await r.json();assert(body.candidates.every(r=>r.confidence==='maybe'));});
test('v2 preserves opaque string ids, bounded results and honest unavailable evidence',async()=>{const r=await request('contractVersion=2&eventId=F1&sportId=soccer&competitionId=SYN-PREM&scheduledStartUtc=2026-11-05T07:00:00Z&home=Arsenal&away=Chelsea');assert.equal(r.status,200);const body=await r.json();assert.equal(body.eventId,'F1');assert.equal(body.candidates[0].providerItemId,'101');assert.equal(body.candidates[0].tier,'unverified');assert(body.catalogueStale);});
test('invalid inputs rejected before catalogue queries',async()=>{for(const q of ['contractVersion=99','contractVersion=2&eventId=F1','limit=13'])assert.equal((await request(q)).status,400);});
