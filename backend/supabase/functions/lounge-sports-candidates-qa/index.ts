import postgres from "npm:postgres@3.4.7";
import {rankCandidates,discoveryCandidate,linearAssignmentCandidates,norm} from "./matcher.mjs";

const rawDbUrl=Deno.env.get("SUPABASE_DB_URL");
if(!rawDbUrl)throw new Error("SUPABASE_DB_URL missing");
function poolUrl(raw:string){
  const u=new URL(raw),ref=u.hostname.split(".")[1]||"liqocisegeqzbyuqloql";
  u.hostname="aws-0-ap-southeast-2.pooler.supabase.com";
  u.port="5432";
  u.username="postgres."+ref;
  return u.toString();
}
const sql=postgres(poolUrl(rawDbUrl),{prepare:false,max:2,idle_timeout:5,connect_timeout:10});
const iso=(ms:number)=>new Date(ms).toISOString();

/**
 * Trust ESPN's server-side event record, not caller-supplied broadcaster names.
 * Require exact source event ID (when present), fixture start and both competitors.
 */
async function findIndependentBroadcasts(event:any){
  const route=String(event.competitionId||"");
  if(!/^[a-z][a-z0-9-]*\/[a-z0-9.-]+$/i.test(route))return {geo:[],source:"",status:"unsupported-competition"};
  const source="https://site.api.espn.com/apis/site/v2/sports/"+route+
    "/scoreboard?dates="+event.scheduledStartUtc.slice(0,10).replaceAll("-","")+"&limit=100";
  try{
    const response=await fetch(source,{signal:AbortSignal.timeout(5000),headers:{"Accept":"application/json"}});
    if(!response.ok)return {geo:[],source,status:"fixture-source-http-"+response.status};
    const data=await response.json();
    const expectedId=String(event.eventId).match(/(?:#|:|\/)(\d{6,12})$/)?.[1]||null;
    const aliases=(side:string)=>[event[side],...(event[side+"Aliases"]||[])]
      .map(norm).filter((n:string)=>n.length>=4&&!["UNITED","CITY","NEW ZEALAND"].includes(n));
    const wantedHome=aliases("home"),wantedAway=aliases("away");
    if(!wantedHome.length||!wantedAway.length)return {geo:[],source,status:"fixture-identity-missing"};
    const selected=(data.events||[]).find((fixture:any)=>{
      const start=Date.parse(fixture.date);
      if(!Number.isFinite(start)||Math.abs(start-Date.parse(event.scheduledStartUtc))>15*60_000)return false;
      if(expectedId&&String(fixture.id)!==expectedId)return false;
      const competitors=fixture.competitions?.[0]?.competitors||[];
      if(competitors.length!==2)return false;
      const texts=competitors.map((c:any)=>norm([c.team?.displayName,c.team?.name,c.team?.shortDisplayName].filter(Boolean).join(" ")));
      const match=(wanted:string[])=>texts.some((t:string)=>wanted.some(a=>(" "+t+" ").includes(" "+a+" ")));
      return match(wantedHome)&&match(wantedAway);
    });
    if(!selected)return {geo:[],source,status:"fixture-not-independently-confirmed"};
    return {geo:selected.competitions?.[0]?.geoBroadcasts||[],source,status:"fixture-confirmed"};
  }catch{return {geo:[],source,status:"fixture-source-unavailable"};}
}

Deno.serve(async(req)=>{
  if(req.method!=="GET")return Response.json({error:"method_not_allowed"},{status:405});
  try{
    const u=new URL(req.url);
    if(u.searchParams.get("contractVersion")!=="2")return Response.json({error:"contract_v2_required"},{status:400});
    const eventId=u.searchParams.get("eventId")||"";
    const sportId=u.searchParams.get("sportId")||"";
    const competitionId=u.searchParams.get("competitionId")||"";
    const scheduledStartUtc=u.searchParams.get("scheduledStartUtc")||"";
    const start=Date.parse(scheduledStartUtc);
    if(!eventId||!sportId||!competitionId||!Number.isFinite(start))return Response.json({error:"missing_event_identity"},{status:400});
    const home=u.searchParams.get("home")||"";
    const away=u.searchParams.get("away")||"";
    const title=u.searchParams.get("title")||"";
    if(!((home&&away)||title))return Response.json({error:"missing_fixture_participants"},{status:400});
    const limit=Math.min(12,Math.max(1,Number(u.searchParams.get("limit")||12)||12));
    const event={
      eventId,sportId,competitionId,scheduledStartUtc,
      scheduledEndUtc:u.searchParams.get("scheduledEndUtc")||"",
      status:u.searchParams.get("status")||"",
      title,home,away,
      homeAliases:(u.searchParams.get("homeAliases")||home).split("|").filter(Boolean),
      awayAliases:(u.searchParams.get("awayAliases")||away).split("|").filter(Boolean),
    };

    // Internal QA broadcast pool: scan the full current, non-adult BEST live inventory.
    // This is independent of Lounge TV customer-facing categories, ordering and curation.
    const rows=await sql`
      select provider_item_id,category_id,name,artwork_url,epg_channel_id,last_seen_at,last_changed_at
      from lounge_sync.best_inventory_items
      where kind='live' and is_present=true and coalesce(is_adult,false)=false
    `;
    const imports=await sql`
      select poll_id,completed_at
      from lounge_sync.best_inventory_imports
      where kind='live' and status='complete'
      order by completed_at desc limit 1
    `;
    const latest=imports[0];
    const importMs=latest?.completed_at?Date.parse(latest.completed_at):NaN;
    const now=Date.now();
    const catalogueStale=!Number.isFinite(importMs)||now-importMs>86400000;
    const independent=catalogueStale?{geo:[],source:"",status:"catalogue-stale"}:await findIndependentBroadcasts(event);
    const assigned=catalogueStale?[]:linearAssignmentCandidates(event,rows,independent.geo,independent.source,now,limit);
    const selected=catalogueStale?[]:rankCandidates(event,rows,limit,now);
    const named=selected.map(({row,decision}:any)=>discoveryCandidate(row,decision,now));
    // Preserve provider stream variants but never promote a title match to confirmed coverage.
    const known=new Set(assigned.map((c:any)=>c.providerItemId));
    const candidates=[...assigned,...named.filter((c:any)=>!known.has(c.providerItemId))].slice(0,limit);
    const reasons=[];
    if(catalogueStale)reasons.push("catalogue-stale");
    if(!assigned.length)reasons.push("no-independent-fixture-broadcast-assignments");
    if(named.length)reasons.push("provider-name-matches-unverified");
    if(candidates.length)reasons.push("playback-unverified");
    return Response.json({
      implementationId:"qa-event-streams-v5",contractVersion:2,eventId,generatedAtUtc:iso(now),
      catalogueRevision:latest?String(latest.poll_id):"unknown",
      importCompletedAtUtc:Number.isFinite(importMs)?iso(importMs):null,
      evidenceAsOfUtc:iso(now),expiresAtUtc:iso(now+300000),catalogueStale,
      candidates,totalEligible:rows.length,nextCursor:null,
      coverage:{
        minimumTarget:3,desiredTarget:5,catalogueScanned:rows.length,
        broadcastConfirmed:new Set(assigned.map((c:any)=>c.logicalServiceId)).size,
        providerNameHints:named.length,playbackVerified:0,returned:candidates.length,
        coverageGap:true,independentFixtureStatus:independent.status,reasons,
      },
    },{headers:{"Cache-Control":"private, no-store","Access-Control-Allow-Origin":"*"}});
  }catch(e){
    console.error("SPORT_CANDIDATES_QA unavailable",e instanceof Error?e.name:"error");
    return Response.json({error:"sports_candidates_qa_unavailable"},{status:503});
  }
});
