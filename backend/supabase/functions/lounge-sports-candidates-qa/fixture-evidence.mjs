import {norm} from "./matcher.mjs";

/**
 * Trust ESPN's server-side event record, not caller-supplied broadcaster names.
 * Require exact source event ID (when present), fixture start and both competitors.
 */
const SNAPSHOT_URL="https://raw.githubusercontent.com/Gazzamoo111/lounge-tv-player/001-live-sports-reliability/backend/supabase/functions/lounge-sports-candidates-qa/data/espn-assignments.json";

/** ESPN blocks some edge servers (403); GitHub's isolated QA runner publishes a
 * time-limited first-party capture from ESPN. This is never a client-provided hint. */
async function freshFixtureSnapshot(requester,route,now=Date.now()){
  try{
    const r=await requester(SNAPSHOT_URL,{signal:AbortSignal.timeout(6000),headers:{Accept:"application/json"}});
    if(!r.ok)return {status:"snapshot-http-"+r.status};
    const body=await r.json();
    const at=Date.parse(body.collectedAtUtc),expires=Date.parse(body.expiresAtUtc);
    if(body.contractVersion!==1||!Array.isArray(body.fixtures)||
       !Number.isFinite(at)||!Number.isFinite(expires)||
       now-at<0||now-at>2*60*60_000||expires<=now)return {status:"snapshot-stale"};
    const events=body.fixtures.filter(f=>f.competitionId===route).map(f=>({
      id:String(f.eventId||"").split("#").at(-1),
      date:f.scheduledStartUtc,
      competitions:[{competitors:(f.participants||[]).map(t=>({
        team:{displayName:t.name,name:t.name,shortDisplayName:t.aliases?.[0]||t.name}
      })),geoBroadcasts:f.geoBroadcasts||[]}],
    }));
    return {data:{events},source:SNAPSHOT_URL,status:"snapshot-fresh"};
  }catch{return {status:"snapshot-unavailable"};}
}

/** Independently verify exact ESPN fixture ID, competition, participants and kickoff. */
export async function findIndependentBroadcasts(event,requester=fetch){
  const route=String(event.competitionId||"");
  if(!/^[a-z][a-z0-9-]*\/[a-z0-9.-]+$/i.test(route))
    return {geo:[],source:"",status:"unsupported-competition"};
  const primary="https://site.api.espn.com/apis/site/v2/sports/"+route+
    "/scoreboard?dates="+event.scheduledStartUtc.slice(0,10).replaceAll("-","")+"&limit=100";
  let data=null,source=primary,fromSnapshot=false,primaryStatus="fixture-source-unavailable";
  try{
    const response=await requester(primary,{signal:AbortSignal.timeout(5000),headers:{"Accept":"application/json"}});
    if(response.ok)data=await response.json();
    else primaryStatus="fixture-source-http-"+response.status;
  }catch{primaryStatus="fixture-source-unavailable";}
  if(!data){
    const backup=await freshFixtureSnapshot(requester,route);
    if(!backup.data)return {geo:[],source:primary,status:primaryStatus+";"+backup.status};
    data=backup.data;source=backup.source;fromSnapshot=true;
  }
  const expectedId=String(event.eventId).match(/(?:#|:|\/)(\d{6,12})$/)?.[1]||null;
  if(fromSnapshot&&!expectedId)return {geo:[],source,status:"snapshot-event-id-missing"};
  const aliases=(side)=>[event[side],...(event[side+"Aliases"]||[])]
    .map(norm).filter(n=>n.length>=4&&!["UNITED","CITY","NEW ZEALAND"].includes(n));
  const wantedHome=aliases("home"),wantedAway=aliases("away");
  if(!wantedHome.length||!wantedAway.length)return {geo:[],source,status:"fixture-identity-missing"};
  const selected=(data.events||[]).find(fixture=>{
    const start=Date.parse(fixture.date);
    if(!Number.isFinite(start)||Math.abs(start-Date.parse(event.scheduledStartUtc))>15*60_000)return false;
    if(expectedId&&String(fixture.id)!==expectedId)return false;
    const competitors=fixture.competitions?.[0]?.competitors||[];
    if(competitors.length!==2)return false;
    const texts=competitors.map(c=>norm([c.team?.displayName,c.team?.name,c.team?.shortDisplayName].filter(Boolean).join(" ")));
    const match=wanted=>texts.some(t=>wanted.some(a=>(" "+t+" ").includes(" "+a+" ")));
    return match(wantedHome)&&match(wantedAway);
  });
  if(!selected)return {geo:[],source,status:"fixture-not-independently-confirmed"};
  const targetStart=Date.parse(selected.date);
  const keyOf=b=>String(b.type?.shortName).toUpperCase()==="TV"&&b.region&&b.media?.shortName
    ?norm(b.region)+":"+norm(b.media.shortName):null;
  const conflicts=new Set();
  for(const other of data.events||[]){
    if(String(other.id)===String(selected.id)||Math.abs(Date.parse(other.date)-targetStart)>60*60_000)continue;
    for(const b of other.competitions?.[0]?.geoBroadcasts||[]){
      const key=keyOf(b);if(key)conflicts.add(key);
    }
  }
  const geo=(selected.competitions?.[0]?.geoBroadcasts||[]).filter(b=>!conflicts.has(keyOf(b)));
  return {geo,source,status:geo.length?(fromSnapshot?"fixture-confirmed-ci-snapshot":"fixture-confirmed"):"broadcast-assignments-absent-or-conflicted"};
}
