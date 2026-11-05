import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {decide,rank,excluded,parseRequest} from '../matcher.mjs';
const f=JSON.parse(fs.readFileSync(new URL('./fixtures/reliability.json',import.meta.url)));
const now=Date.parse(f.now), fixture={...f,contractVersion:2};
const evidence=(extra={})=>({type:'programme',source:'synthetic',eventId:'F1',sportId:'soccer',competitionId:'SYN-PREM',division:'men-senior',startUtc:f.scheduledStartUtc,endUtc:f.scheduledEndUtc,observedAtUtc:f.statusObservedAtUtc,...extra});
const row=(id='101',extra={})=>({sourceId:'best',providerItemId:id,logicalServiceId:'GB:service:'+id,serviceMarket:'GB',identityReviewed:true,present:true,displayName:'service '+id,evidence:[evidence()],...extra});
test('R01 generic networks cannot establish fixture verification',()=>{
 assert.equal(decide(fixture,row(),now).tier,'verified');
 for(const displayName of ['AU Paramount 4K','AU Stan HD']) assert.equal(decide(fixture,row('102',{displayName,evidence:[]}),now).tier,'unverified');
});
test('R02/R04/R05/R07 contradictions suppress rather than fuzzy promotion',()=>{
 for(const extra of [{eventId:'other'},{sportId:'basketball'},{competitionId:'women'},{division:'women'},{startUtc:'2026-11-04T07:00:00Z',endUtc:'2026-11-04T09:00:00Z'}])
 assert.equal(decide(fixture,row('104',{evidence:[evidence(extra)]}),now),null);
 assert.equal(decide(fixture,row('105',{displayName:'AU NO EVENT PPV'}),now),null);
});
test('R03/R12 assignments are service/market scoped',()=>{
 const a=evidence({type:'assignment',serviceId:'GB:service:108',market:'GB'});
 assert.equal(decide(fixture,row('108',{evidence:[a]}),now).tier,'plausible');
 assert.equal(decide(fixture,row('109',{serviceMarket:'NZ',evidence:[a]}),now).tier,'unverified');
});
test('R10 strict overlap and event-feed 15 minute boundary',()=>{
 for(const extra of [{endUtc:f.scheduledStartUtc},{startUtc:f.scheduledEndUtc}]) assert.equal(decide(fixture,row('1',{evidence:[evidence(extra)]}),now),null);
 for(const delta of [900000,900001,960000]){
 const e=evidence({type:'event-feed',declaredStartUtc:new Date(now+delta).toISOString()});
 assert.equal(decide(fixture,row('1',{evidence:[e]}),now)?.tier,delta===900000?'verified':'unverified');
 }
 assert.equal(decide(fixture,row('1',{evidence:[evidence({startUtc:new Date(now-1800000).toISOString()})]}),now).tier,'verified');
});
test('R11 live freshness and stale catalogue cannot assert verified',()=>{
 for(const age of [299000,301000]) assert.equal(decide(fixture,row('1',{evidence:[evidence({observedAtUtc:new Date(now-age).toISOString()})]}),now).tier,age<300000?'verified':'unverified');
 assert.equal(decide({...fixture,importCompletedAtUtc:new Date(now-86460000).toISOString()},row(),now).tier,'unverified');
});
test('R09 stable logical service dedupe, six initial, alternatives <=12',()=>{
 const rows=Array.from({length:20},(_,i)=>decide(fixture,row(''+i,{logicalServiceId:'service:'+i%6}),now));
 rows.push(decide(fixture,row('x',{evidence:[evidence({type:'assignment',serviceId:'GB:service:x',market:'GB'})]}),now));
 const result=rank(rows);assert.equal(result.initial.length,6);assert(result.initial.every(x=>x.tier==='verified'));assert.equal(result.all.length,7);
});
test('E01 source-scoped ESPN1 exclusion preserves ESPN2 and ESPN10',()=>{
 for(const id of ['646974','1520168'])assert(excluded({sourceId:'best',providerItemId:id}));
 for(const name of ['AU | ESPN 1 HD','AU | Kayo ESPN1'])assert(excluded({sourceId:'best',displayName:name}));
 for(const name of ['AU | ESPN 2','US | ESPN 1','AU | ESPN 10','AU | ESPN 11'])assert(!excluded({sourceId:'best',displayName:name}));
 assert(!excluded({sourceId:'other',providerItemId:'646974',displayName:'AU | ESPN 1'}));
});
test('bounded input rejects invalid/unknown versions and ranges',()=>{
 for(const query of ['contractVersion=3','contractVersion=2&eventId=F1','title='+ 'a'.repeat(257)])assert.throws(()=>parseRequest(new URL('https://example.invalid/?'+query)));
});
