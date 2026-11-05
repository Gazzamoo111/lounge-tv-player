import postgres from "npm:postgres@3.4.7";
import {rankCandidates,discoveryCandidate,linearAssignmentCandidates} from "./matcher.mjs";
import {findIndependentBroadcasts} from "./fixture-evidence.mjs";

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
