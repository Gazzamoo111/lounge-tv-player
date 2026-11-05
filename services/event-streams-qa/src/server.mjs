import http from 'node:http';
import {readFileSync} from 'node:fs';
import {gunzipSync} from 'node:zlib';
import {fileURLToPath} from 'node:url';
import {resolveEvent} from './engine.mjs';
export function createHandler({catalogue,dataset,health={},now=Date.now}){
 return async request=>{
  const url=new URL(request.url);
  if(request.method!=='GET'||url.pathname!=='/candidates')return Response.json({error:'not_found'},{status:404});
  const p=url.searchParams;
  if(p.get('contractVersion')!=='2')return Response.json({error:'contract_required'},{status:400});
  const requested=p.get('eventId'),sport=p.get('sportId'),competition=p.get('competitionId'),start=Date.parse(p.get('scheduledStartUtc'));
  if(!requested||!sport||!competition||!Number.isFinite(start))return Response.json({error:'identity_required'},{status:400});
  const found=dataset.fixtures.find(e=>(e.eventId===requested||e.providerEventId===requested.split('#').at(-1))&&e.sportId===sport&&e.competitionId===competition&&Date.parse(e.scheduledStartUtc)===start);
  if(!found)return Response.json({error:'fixture_not_observed',coverageGap:true},{status:404});
  // Alias the public application key only after authoritative id, feed, sport and kickoff agree.
  const event={...found,eventId:requested};
  return Response.json(resolveEvent(event,catalogue,dataset,health,now(),Number(p.get('limit'))||12),{headers:{'cache-control':'no-store'}});
 };
}
export function startServer(options,port=0){
 const handler=createHandler(options);
 const server=http.createServer(async(req,res)=>{try{const response=await handler(new Request('http://127.0.0.1'+req.url,{method:req.method}));res.writeHead(response.status,Object.fromEntries(response.headers));res.end(await response.text());}catch{res.writeHead(503);res.end('{"error":"qa_unavailable"}');}});
 return new Promise(resolve=>server.listen(port,'127.0.0.1',()=>resolve(server)));
}
if(process.argv[1]===fileURLToPath(import.meta.url)){
 const catalogue=JSON.parse(gunzipSync(readFileSync(new URL('../data/catalogue.json.gz',import.meta.url))));
 const dataset=JSON.parse(readFileSync(process.env.QA_EVENT_DATA||'outputs/event-streams-qa/evidence.json','utf8'));
 const health=process.env.QA_HEALTH_DATA?JSON.parse(readFileSync(process.env.QA_HEALTH_DATA,'utf8')):{};
 const server=await startServer({catalogue,dataset,health},Number(process.env.PORT)||18764);
 console.log(JSON.stringify({qaEndpoint:'http://127.0.0.1:'+server.address().port+'/candidates',productionConnections:0}));
}
