import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {rankCandidates,discoveryCandidate,excluded} from '../matcher.mjs';
const catalogue=JSON.parse(readFileSync(new URL('./fixtures/catalogue-352.json',import.meta.url)));
const now=Date.parse('2026-11-05T01:00:00Z');
// Synthetic fixture identity tests the contract. This is not a real broadcast observation.
const event={eventId:'regression:ucl',sportId:'soccer',competitionId:'soccer/uefa.champions',home:'Liverpool',away:'Real Madrid',scheduledStartUtc:'2026-11-05T00:00:00Z',scheduledEndUtc:'2026-11-05T03:00:00Z',status:'IN',broadcasters:['Stan Sport','Paramount Plus']};
const evidence={type:'programme',source:'regression-only',eventId:event.eventId,sportId:event.sportId,competitionId:event.competitionId,serviceId:'AU:BEIN:1',market:'AU',observedAtUtc:'2026-11-05T00:59:00Z',startUtc:event.scheduledStartUtc,endUtc:event.scheduledEndUtc};
const withProof=(name='AU| BEIN SPORTS 1 HD',id=646977,e=evidence)=>({provider_item_id:id,name,logicalServiceId:e.serviceId,serviceMarket:e.market,identityReviewed:true,evidence:[e]});
const run=rows=>rankCandidates(event,rows,6,now);
test('R1 R2 actual SQL-shaped catalogue does not flood generic networks',()=>{
 const found=run(catalogue.rows);
 assert.ok(found.length>0);assert.ok(found.length<=6);
 assert.equal(found.some(x=>/STAN|PARAMOUNT/i.test(x.row.name)),false);
 assert.equal(found.some(x=>!Number.isFinite(Number(x.row.providerItemId))),false);
 assert.equal(new Set(found.map(x=>x.decision.logicalServiceId)).size,found.length);
});
test('R3 beIN with valid fixture evidence outranks discovery',()=>{
 const found=run([...catalogue.rows,withProof()]);
 assert.equal(found[0].row.providerItemId,646977);assert.equal(found[0].decision.tier,'verified');
 assert.equal(discoveryCandidate(found[0].row,found[0].decision,now).evidence[0].source,'regression-only');
});
test('networks are evidence gated, not blacklisted',()=>{
 for(const [id,name,service,market] of [[900,'AU| STAN SPORT 1 HD','AU:STAN:1','AU'],[901,'US| PARAMOUNT PLUS 41 HD','US:PARAMOUNT:41','US']]){
  const row=withProof(name,id,{...evidence,serviceId:service,market});
  assert.equal(run([row])[0].decision.tier,'verified');
  assert.equal(run([{...row,evidence:[]}]).length,0);
 }
});
test('R6 R7 provider aliases exclude ESPN AU1 and retain ESPN AU2 and US ESPN',()=>{
 for(const row of [{provider_item_id:646974,name:'renamed'}, {provider_item_id:1520168,name:'renamed'}, {name:'ESPN Australia 1'}, {name:'ESPN AU 1'}, {name:'AU| ESPN 1 HD'}])assert.equal(excluded(row),true);
 for(const name of ['ESPN Australia 2','AU| ESPN 2 HD','US| ESPN 1 HD','US| ESPN HD'])assert.equal(excluded({name}),false);
});
test('R8 R15 contradictory proof cannot be rescued by a matching positive entry',()=>{
 const row=withProof();row.evidence.push({...evidence,contentMismatch:true});assert.deepEqual(run([row]),[]);
});
test('R14 expired proof cannot promote verified or plausible',()=>{
 const found=run([withProof(undefined,undefined,{...evidence,observedAtUtc:'2026-11-04T23:00:00Z'})]);
 assert.equal(found[0].decision.tier,'unverified');
 assert.deepEqual(rankCandidates({...event,status:'POST'},[withProof()],6,now).filter(x=>x.decision.tier==='verified'),[]);
});
test('market, fixture, service and interval mismatches never promote',()=>{
 for(const override of [{eventId:'other'},{market:'UK'},{serviceId:'wrong'},{endUtc:event.scheduledStartUtc},{observedAtUtc:'2026-11-05T02:00:00Z'}]){
  const row=withProof();row.evidence=[{...evidence,...override}];
  assert.equal(run([row])[0]?.decision.tier,'unverified');
 }
});
test('R13 SQL variants collapse while identity exclusions survive renaming',()=>{
 const found=run(catalogue.rows);assert.equal(found.filter(x=>x.decision.logicalServiceId==='AU:BEIN:1').length,1);
 assert.equal(run([{provider_item_id:646974,name:'UK| TNT SPORTS 1 HD'}]).length,0);
});
