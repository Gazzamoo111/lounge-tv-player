import postgres from "npm:postgres@3.4.7";
import {rankCandidates,discoveryCandidate} from "./matcher.mjs";

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
    const limit=Math.min(6,Math.max(1,Number(u.searchParams.get("limit")||6)||6));
    const event={
      eventId,sportId,competitionId,scheduledStartUtc,
      scheduledEndUtc:u.searchParams.get("scheduledEndUtc")||"",
      status:u.searchParams.get("status")||"",
      sport:u.searchParams.get("sport")||"",
      league:u.searchParams.get("league")||"",
      title:u.searchParams.get("title")||"",
      home:u.searchParams.get("home")||"",
      away:u.searchParams.get("away")||"",
      broadcasters:(u.searchParams.get("broadcasters")||"").split("|").filter(Boolean),
    };
    const rows=await sql`
      select provider_item_id,category_id,name,artwork_url,epg_channel_id
      from lounge_sync.best_inventory_items
      where kind='live' and is_present=true
        and name !~* '(NO EVENT|NO STREAM|NOT STREAMING|OFFLINE|PLACEHOLDER|REPLAY|HIGHLIGHTS)'
        and (
          name ~* '(BEIN|SKY SPORTS|TNT SPORTS?|PREMIER SPORTS?|STAN SPORT|PARAMOUNT|PPV|EVENT|LIVE|ESPN|FOX SPORT)'
          or category_id in (
            select category_id from lounge_sync.best_categories
            where kind='live' and is_present=true and category_name ~* '(sport|ppv|football|soccer|champions|premier)'
          )
        )
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
    const parsedEnd=Date.parse(event.scheduledEndUtc);
    const end=Number.isFinite(parsedEnd)&&parsedEnd>start?parsedEnd:start+4*60*60*1000;
    const selected=rankCandidates(event,rows,limit);
    // Catalogue names and client-supplied fixture metadata are discovery hints only.
    // No programme/assignment evidence producer is connected to this QA endpoint.
    // Never fabricate observedAt, event identity, or reviewed service status.
    const candidates=selected.map(({row,decision}:any)=>discoveryCandidate(row,decision,now));
    return Response.json({
      implementationId:"qa-sports-evidence-v3",contractVersion:2,eventId,generatedAtUtc:iso(now),
      catalogueRevision:latest?String(latest.poll_id):"unknown",
      importCompletedAtUtc:Number.isFinite(importMs)?iso(importMs):null,
      evidenceAsOfUtc:null,expiresAtUtc:iso(now+300000),catalogueStale,
      candidates,totalEligible:candidates.length,nextCursor:null,
    },{headers:{"Cache-Control":"private, no-store","Access-Control-Allow-Origin":"*"}});
  }catch(e){
    console.error("SPORT_CANDIDATES_QA unavailable");
    return Response.json({error:"sports_candidates_qa_unavailable"},{status:503});
  }
});