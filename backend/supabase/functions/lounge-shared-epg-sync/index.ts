import postgres from "npm:postgres@3.4.7";
const raw=Deno.env.get("SUPABASE_DB_URL");
if(!raw)throw Error("SUPABASE_DB_URL missing");
const u=new URL(raw);u.hostname="aws-0-ap-southeast-2.pooler.supabase.com";u.port="5432";u.username="postgres.liqocisegeqzbyuqloql";
const sql=postgres(u.toString(),{prepare:false,max:1,idle_timeout:5,connect_timeout:10});
const base="https://raw.githubusercontent.com/Gazzamoo111/lounge-tv-player";
async function fetchJson(url:string){
 const r=await fetch(url,{signal:AbortSignal.timeout(20000)});
 if(!r.ok)throw Error("upstream_http_"+r.status);
 const text=await r.text();if(text.length>12_000_000)throw Error("oversized_shard");
 return {text,body:JSON.parse(text)};
}
Deno.serve(async(req:Request)=>{
 if(req.method!=="POST")return Response.json({error:"method_not_allowed"},{status:405});
 const token=req.headers.get("x-lounge-core-token")||"";
 if(!token)return Response.json({error:"unauthorised"},{status:401});
 const digest=await crypto.subtle.digest("SHA-256",new TextEncoder().encode(token));
 const hash=Array.from(new Uint8Array(digest)).map(b=>b.toString(16).padStart(2,"0")).join("");
 const auth=await sql`select 1 from lounge_core.worker_auth where key_name='metadata_enrich' and key_hash=${hash}`;
 if(!auth.length)return Response.json({error:"unauthorised"},{status:401});
 try{
  // Transaction lock serialises cursor advances and prevents stale concurrent imports.
  const result=await sql.begin(async tx=>{
   const lock=await tx`select pg_try_advisory_xact_lock(71320261113) as locked`;
   if(!lock[0].locked)return {ok:true,busy:true};
   const state=(await tx`select * from lounge_delivery.shared_epg_sync_state where singleton=true for update`)[0];
   // Pin the manifest and every shard to one immutable export commit.
   const ref=await fetchJson("https://api.github.com/repos/Gazzamoo111/lounge-tv-player/git/ref/heads/epg-data");
   const commit=ref.body?.object?.sha;if(!/^[a-f0-9]{40}$/.test(commit))throw Error("invalid_export_commit");
   const manifest=(await fetchJson(base+"/"+commit+"/epg/database/manifest.json")).body;
   if(!/^[a-f0-9]{40}$/.test(manifest.revision)||manifest.shards?.length!==16||manifest.channels<100)throw Error("invalid_manifest");
   if(state.revision===manifest.revision&&state.next_shard===0&&state.last_success_at)return {ok:true,unchanged:true,revision:manifest.revision};
   let cursor=state.revision===manifest.revision?state.next_shard:0;let channels=0;
   for(let batch=0;batch<4&&cursor<16;batch++,cursor++){
    const info=manifest.shards[cursor];if(info.file!==String(cursor).padStart(2,"0")+".json")throw Error("invalid_shard_name");
    const {text,body}=await fetchJson(base+"/"+commit+"/epg/database/"+info.file);
    const digest=await crypto.subtle.digest("SHA-256",new TextEncoder().encode(text));
    const sum=Array.from(new Uint8Array(digest)).map(b=>b.toString(16).padStart(2,"0")).join("");
    if(sum!==info.sha256||body.revision!==manifest.revision||body.shard!==cursor||body.channels.length!==info.channels)throw Error("shard_integrity_failed");
    for(const row of body.channels){
     if(!row.epg_id||!Array.isArray(row.listings)||row.listings.length!==row.programme_count||!Number.isFinite(Date.parse(row.expires_at)))throw Error("invalid_channel");
     await tx`insert into lounge_delivery.shared_epg_channels(epg_id,xmltv,listings,expires_at,programme_count,source_revision,synced_at)
      values(${row.epg_id},${row.xmltv},${sql.json(row.listings)},${row.expires_at}::timestamptz,${row.programme_count},${manifest.revision},now())
      on conflict(epg_id) do update set xmltv=excluded.xmltv,listings=excluded.listings,expires_at=excluded.expires_at,programme_count=excluded.programme_count,source_revision=excluded.source_revision,synced_at=now()`;
     channels++;
    }
   }
   if(cursor===16){
    const totals=(await tx`select count(*)::int as channels,coalesce(sum(programme_count),0)::int as programmes from lounge_delivery.shared_epg_channels where source_revision=${manifest.revision}`)[0];
    if(totals.channels!==manifest.channels||totals.programmes!==manifest.programmes)throw Error("incomplete_import");
    await tx`delete from lounge_delivery.shared_epg_channels where source_revision<>${manifest.revision}`;
   }
   await tx`update lounge_delivery.shared_epg_sync_state set revision=${manifest.revision},next_shard=${cursor===16?0:cursor},last_success_at=now(),updated_at=now() where singleton=true`;
   return {ok:true,revision:manifest.revision,channels,next_shard:cursor===16?0:cursor};
  });
  return Response.json(result);
 }catch(e){console.error("SHARED_EPG_SYNC_FAILED",e instanceof Error?e.message:"unknown");return Response.json({error:"sync_failed"},{status:502});}
});
