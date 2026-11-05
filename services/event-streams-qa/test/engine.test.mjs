import {test} from 'node:test';import assert from 'node:assert/strict';
import {resolveEvent,programmeMatches,IMPLEMENTATION_ID} from '../src/engine.mjs';
import {startServer} from '../src/server.mjs';
const now=Date.parse('2026-11-05T01:00:00Z'),iso=ms=>new Date(ms).toISOString();
const fixture=(sport='basketball')=>({eventId:sport+'/test#1',providerEventId:'1',sportId:sport,competitionId:sport+'/test',competitionName:sport,title:'Alpha Athletic v Beta United',participants:[{name:'Alpha Athletic',aliases:['Alpha Athletic']},{name:'Beta United',aliases:['Beta United']}],scheduledStartUtc:iso(now-60_000),fixtureFetchedAtUtc:iso(now),fixtureSource:'https://site.api.espn.com/apis/site/v2/sports/'+sport+'/test/scoreboard',status:'IN'});
const programme=(sport='basketball')=>({channelId:'s1',startUtc:iso(now-60_000),endUtc:iso(now+3_600_000),title:'Live '+({football:'NFL', 'rugby-league':'NRL'}[sport]||sport)+': Alpha Athletic v Beta United',description:'',categories:[sport]});
const catalogue=rows=>({revision:'test-only',completedAtUtc:iso(now-60_000),rows});
const row=(id=1,name='NZ| SKY SPORT 1 HD',category='general')=>({id,name,epg:'SkySport1.nz',category});
const guide=(sport='basketball')=>({market:'NZ',url:'https://guide.invalid/schedule',sourceUpdatedAtUtc:iso(now),fetchedAtUtc:iso(now),channels:[{id:'s1',names:['Sky Sport 1']}],programmes:[programme(sport)]});
const dataset=(sport='basketball')=>({fixtures:[fixture(sport)],guides:[guide(sport)]});
const health=(ids=[1])=>Object.fromEntries(ids.map(id=>[String(id),{status:'verified',decodedFrames:1,httpAccessible:true,streamFingerprint:'a'.repeat(64),providerItemId:String(id),serviceId:'NZ:SKYSPORT1',observedAtUtc:iso(now)}]));
test('all supported sports require exact event evidence and decoded playback',()=>{
 for(const sport of ['soccer','football','basketball','hockey','baseball','rugby','rugby-league','cricket','mma','tennis','golf','netball']){
  const d=dataset(sport);let r=resolveEvent(d.fixtures[0],catalogue([row()]),d,{},now);
  assert.equal(r.coverage.broadcastConfirmed,1,sport);assert.equal(r.candidates.length,0);assert.equal(r.coverage.coverageGap,true);
  r=resolveEvent(d.fixtures[0],catalogue([row()]),d,health(),now);assert.equal(r.candidates.length,1,sport);assert.equal(r.coverage.coverageGap,true);
 }
});
test('search includes general and international catalogue folders, never a sports category gate',()=>{
 const d=dataset();const rows=[row(1,undefined,'unrelated-folder'),...Array.from({length:400},(_,i)=>row(i+2,'US| PARAMOUNT PLUS '+i,'sport'))];
 const r=resolveEvent(d.fixtures[0],catalogue(rows),d,health(),now);assert.equal(r.coverage.catalogueScanned,401);assert.deepEqual(r.candidates.map(c=>c.providerItemId),[1]);
});
test('broadcaster branding and fixture text in a channel name never establish event evidence',()=>{
 const e=fixture();const rows=[row(1,'NZ| Alpha Athletic vs Beta United LIVE'),row(2,'AU| STAN SPORT 1'),row(3,'US| ESPN HD')];
 assert.equal(resolveEvent(e,catalogue(rows),{fixtures:[e],guides:[]},health([1,2,3]),now).candidates.length,0);
});
test('wrong opponent, sport, dates, stale source and expired fixture reject recommendations',()=>{
 const e=fixture();
 for(const mutate of [d=>d.guides[0].programmes[0].title='Live Basketball: Alpha Athletic v Other Team',d=>d.guides[0].programmes[0]=programme('tennis'),d=>d.guides[0].programmes[0].endUtc=iso(now-120000),d=>d.guides[0].sourceUpdatedAtUtc=iso(now-90_000_000),d=>d.fixtures[0].fixtureFetchedAtUtc=iso(now-900000)]){
  const d=dataset();mutate(d);assert.equal(resolveEvent(d.fixtures[0],catalogue([row()]),d,health(),now).candidates.length,0);
 }
});
test('replays, previews and minis are rejected even when both teams match',()=>{
 for(const word of ['Replay','Highlights','Preview','Mini','Classic']){const p=programme();p.title=word+' '+p.title;assert.equal(programmeMatches(fixture(),p),false);}
});
test('current contradictory guide cancels an otherwise matching programme on that service',()=>{
 const d=dataset();d.guides[0].programmes.push({...programme('tennis'),title:'Live ATP Tennis'});
 assert.equal(resolveEvent(d.fixtures[0],catalogue([row()]),d,health(),now).candidates.length,0);
});
test('variants collapse by logical service; a working variant beats unprobed higher quality',()=>{
 const d=dataset(),r=resolveEvent(d.fixtures[0],catalogue([row(1),row(2,'NZ| SKY SPORT 1 4K')]),d,health(),now);
 assert.equal(r.coverage.broadcastConfirmed,1);assert.deepEqual(r.candidates.map(c=>c.providerItemId),[1]);
});
test('probe status must belong to the exact provider id/service and be fresh',()=>{
 for(const override of [{serviceId:'NZ:SKYSPORT2'},{providerItemId:'2'},{observedAtUtc:iso(now-900000)},{decodedFrames:0},{status:'failed'},{httpAccessible:false}]){
  const d=dataset(),h=health();Object.assign(h['1'],override);assert.equal(resolveEvent(d.fixtures[0],catalogue([row()]),d,h,now).candidates.length,0);
 }
});
test('ESPN national TV is event specific and region scoped, bundles are not linear channels',()=>{
 const e=fixture();e.geoBroadcasts=[{region:'us',type:{shortName:'TV'},media:{shortName:'ESPN'}}];
 const c=catalogue([{id:45579,name:'US| ESPN HD'},{id:646974,name:'AU| ESPN 1 HD'}]);
 const r=resolveEvent(e,c,{guides:[]},{},now);assert.deepEqual(r.broadcastCandidates.map(c=>c.providerItemId),[45579]);assert.equal(r.candidates.length,0);
 e.geoBroadcasts=[{region:'us',type:{shortName:'Streaming'},media:{shortName:'Prime Video'}}];assert.equal(resolveEvent(e,c,{guides:[]},{},now).coverage.broadcastConfirmed,0);
});
test('one or zero verified streams stays honest instead of filling to minimum three',()=>{
 const d=dataset();for(const h of [{},health()]){const r=resolveEvent(d.fixtures[0],catalogue([row()]),d,h,now);assert.ok(r.candidates.length<3);assert.equal(r.coverage.coverageGap,true);assert.ok(r.coverage.reasons.includes('below-minimum-three'));}
});
test('HTTP handler ignores client broadcaster/evidence injection and validates event identity',async()=>{
 const d=dataset(),server=await startServer({catalogue:catalogue([row()]),dataset:d,health:health(),now:()=>now});
 try{
  const url=new URL('http://127.0.0.1:'+server.address().port+'/candidates');const e=d.fixtures[0];
  for(const [k,v] of Object.entries({contractVersion:2,eventId:e.eventId,sportId:e.sportId,competitionId:e.competitionId,scheduledStartUtc:e.scheduledStartUtc,broadcasters:'Stan Sport|Paramount',evidence:'invented'}))url.searchParams.set(k,v);
  const result=await(await fetch(url)).json();assert.equal(result.implementationId,IMPLEMENTATION_ID);assert.deepEqual(result.candidates.map(c=>c.providerItemId),[1]);
  url.searchParams.set('competitionId','other');assert.equal((await fetch(url)).status,404);
  assert.equal((await fetch(url,{method:'POST'})).status,404);
 }finally{await new Promise(resolve=>server.close(resolve));}
});
test('three and five distinct verified services meet targets without counting variants twice',()=>{
 const e=fixture(),g=guide(),rows=[],h={};g.channels=[];g.programmes=[];
 for(let i=1;i<=5;i++){
  rows.push({...row(i,'NZ| SKY SPORT '+i+' HD'),epg:'SkySport'+i+'.nz'});
  g.channels.push({id:'s'+i,names:['Sky Sport '+i]});g.programmes.push({...programme(),channelId:'s'+i});
  h[String(i)]={...health()[1],providerItemId:String(i),serviceId:'NZ:SKYSPORT'+i};
 }
 const r=resolveEvent(e,catalogue(rows),{guides:[g]},h,now);
 assert.equal(r.candidates.length,5);assert.equal(r.coverage.coverageGap,false);assert.equal(r.coverage.desiredTarget,5);
 const fewer=resolveEvent(e,catalogue(rows),{guides:[g]},Object.fromEntries(Object.entries(h).slice(0,2)),now);
 assert.equal(fewer.candidates.length,2);assert.equal(fewer.coverage.coverageGap,true);
});
test('known nominal-service content contradictions cannot be overridden by a matching guide',()=>{
 const e=fixture('soccer'),g=guide('soccer');g.market='AU';g.channels=[{id:'s1',names:['beIN Sports 1']}];
 const r=resolveEvent(e,catalogue([{id:646977,name:'AU| BEIN SPORTS 1 HD',epg:'beINSports1.au'}]),{guides:[g]},health([646977]),now);
 assert.equal(r.coverage.broadcastConfirmed,0);assert.equal(r.coverage.rejections.knownContentMismatch,1);
});
test('Sky direct Premier 1 alias discovers actual UK Premier Sports variants',()=>{
 const d=dataset('hockey');d.guides[0].market='UK';d.guides[0].channels=[{id:'1289',names:['Premier 1 HD']}];
 const rows=[{id:163607,name:'UK| PREMIER SPORT 1 FHD',epg:'ViaplaySports1.uk'},{id:45235,name:'UK| PREMIER SPORTS 1 HD',epg:'PremierSports1.uk'}];
 assert.equal(resolveEvent(d.fixtures[0],catalogue(rows),d,{},now).coverage.broadcastConfirmed,0);
 d.guides[0].programmes[0].channelId='1289';
 const r=resolveEvent(d.fixtures[0],catalogue(rows),d,{},now);assert.equal(r.coverage.broadcastConfirmed,1);assert.equal(r.broadcastCandidates[0].providerItemId,163607);assert.equal(r.candidates.length,0);
});
