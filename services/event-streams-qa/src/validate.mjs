import {readFileSync,writeFileSync} from 'node:fs';
import {gunzipSync} from 'node:zlib';
import {resolveEvent} from './engine.mjs';
import {startServer} from './server.mjs';
const catalogue=JSON.parse(gunzipSync(readFileSync(new URL('../data/catalogue.json.gz',import.meta.url))));
const dataset=JSON.parse(readFileSync('outputs/event-streams-qa/evidence.json','utf8'));
const health=process.env.QA_HEALTH_DATA?JSON.parse(readFileSync(process.env.QA_HEALTH_DATA,'utf8')):{};
const now=process.env.QA_REPLAY_AT?Date.parse(process.env.QA_REPLAY_AT):Date.now();
const server=await startServer({catalogue,dataset,health,now:()=>now});
const cases=[];
try{
 for(const event of dataset.fixtures){
  if(!['PRE','IN'].includes(event.status))continue;
  const url=new URL('http://127.0.0.1:'+server.address().port+'/candidates');
  for(const [k,v] of Object.entries({contractVersion:'2',eventId:event.eventId,sportId:event.sportId,competitionId:event.competitionId,scheduledStartUtc:event.scheduledStartUtc}))url.searchParams.set(k,v);
  const response=await fetch(url);const result=await response.json();
  if(response.status!==200||result.eventId!==event.eventId)throw new Error('QA event identity integration failed');
  cases.push({event:{...event,startNz:new Intl.DateTimeFormat('en-NZ',{timeZone:'Pacific/Auckland',dateStyle:'medium',timeStyle:'short'}).format(new Date(event.scheduledStartUtc))},coverage:result.coverage,recommendations:result.candidates,broadcastCandidates:result.broadcastCandidates,implementationId:result.implementationId});
 }
}finally{await new Promise(resolve=>server.close(resolve));}
const summary={mode:process.env.QA_REPLAY_AT?'recorded-live-source-replay':'live-source-validation',generatedAtUtc:new Date(now).toISOString(),scope:'all supported sports; no production credentials or writes',catalogueRevision:catalogue.revision,catalogueRows:catalogue.rows.length,realFixtureCount:cases.length,sports:[...new Set(cases.map(c=>c.event.sportId))],eventConfirmedCases:cases.filter(c=>c.coverage.broadcastConfirmed>0).length,playbackVerifiedCases:cases.filter(c=>c.coverage.playbackVerified>0).length,minimumThreeCases:cases.filter(c=>c.coverage.playbackVerified>=3).length,desiredFiveCases:cases.filter(c=>c.coverage.playbackVerified>=5).length,ready:cases.length>0&&new Set(cases.filter(c=>c.coverage.playbackVerified>=3).map(c=>c.event.sportId)).size>=3,sourceStatus:dataset.guideStatus,fixtureFeedErrors:dataset.feedStatus.filter(s=>!s.ok),cases};
writeFileSync('outputs/event-streams-qa/coverage.json',JSON.stringify(summary,null,2));
writeFileSync('outputs/event-streams-qa/android-cases.json',JSON.stringify(cases.map(c=>({event:c.event,expectedCandidates:c.recommendations.map(r=>r.providerItemId),coverageGap:c.coverage.coverageGap}))));
console.log(JSON.stringify({...summary,cases:cases.slice(0,12).map(c=>({title:c.event.title,sport:c.event.sportId,startNz:c.event.startNz,coverage:c.coverage,channels:c.broadcastCandidates.map(x=>({id:x.providerItemId,name:x.displayName,programme:x.evidence[0].programmeTitle,playback:x.playbackObservation.status}))}))},null,2));
