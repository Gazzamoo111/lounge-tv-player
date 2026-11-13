// Lounge TV isolated read-only QA projection for the 3252-channel draft.
// This endpoint is NOT the customer Xtream endpoint. It requires existing
// internal worker-token authentication and never activates a release.
import postgres from "npm:postgres@3.4.7";
const dbUrl=Deno.env.get("SUPABASE_DB_URL");
if(!dbUrl) throw new Error("database_url_missing");
const sql=postgres(dbUrl,{prepare:false,max:1,connect_timeout:10,idle_timeout:20});
const QA_RELEASE="53a5174a-3247-42b6-9384-1a83ff4d29f7";

async function authorised(req:Request):Promise<boolean>{
 const token=(req.headers.get("x-lounge-core-token")||"").trim();
 if(!token)return false;
 const hash=Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(token)))).map(b=>b.toString(16).padStart(2,"0")).join("");
 const hit=await sql.unsafe("select 1 from lounge_core.worker_auth where key_name='metadata_enrich' and key_hash=$1 limit 1",[hash]);
 return hit.length===1;
}
function presentationId(cat:string,sub:string):string{
 const raw=cat+"\u001f"+sub;
 let h=2166136261;
 for(let i=0;i<raw.length;i++){h^=raw.charCodeAt(i);h=Math.imul(h,16777619);}
 return String(1000000000+((h>>>0)%900000000));
}
function presentName(cat:string,sub:string):string{
 return !sub||sub.toLowerCase()===cat.toLowerCase()?cat:cat+" · "+sub;
}
function json(payload:unknown,status=200):Response{
 return Response.json(payload,{status,headers:{"Cache-Control":"no-store","X-Lounge-QA":"draft-only"}});
}
Deno.serve(async(req:Request)=>{
 if(req.method!=="POST")return json({error:"method_not_allowed"},405);
 try{
  if(!await authorised(req))return json({error:"unauthorised"},401);
  const body=await req.json().catch(()=>({}));
  const action=String(body?.action||"qa_summary");
  if(action==="qa_summary"){
   const rows=await sql.unsafe(
     "select r.state::text as state,r.release_key, "+
     "(select count(*)::int from lounge_delivery.live_release_items l where l.release_id=r.id and l.admission='keep') live, "+
     "(select count(*)::int from lounge_delivery.vod_release_items v where v.release_id=r.id and v.admission='keep' and v.content_type='movie') movies, "+
     "(select count(*)::int from lounge_delivery.vod_release_items v where v.release_id=r.id and v.admission='keep' and v.content_type='series') series, "+
     "(select count(*)::int from lounge_delivery.live_release_items l where l.release_id=r.id and l.category='PPV') ppv, "+
     "(select count(distinct (l.category,l.subcategory))::int from lounge_delivery.live_release_items l where l.release_id=r.id and l.admission='keep') subfolders "+
     "from lounge_delivery.catalogue_releases r where r.id=$1::uuid",[QA_RELEASE]);
   return json(rows[0]||{error:"missing_release"});
  }
  if(action==="get_live_categories"){
   const rows=await sql.unsafe(
    "select l.category,l.subcategory,min(c.sort_order) sort_order,min(l.sort_order) sort_sub "+
    "from lounge_delivery.live_release_items l join lounge_delivery.categories c "+
    "on c.release_id=l.release_id and c.kind='live' and c.display_name=l.category and c.enabled "+
    "where l.release_id=$1::uuid and l.admission='keep' "+
    "group by l.category,l.subcategory order by min(c.sort_order),min(l.sort_order)",[QA_RELEASE]);
   return json(rows.map((r:any)=>({category_id:presentationId(r.category,r.subcategory),category_name:presentName(r.category,r.subcategory)})));
  }
  if(action==="get_live_streams"){
   const wanted=String(body?.category_id||"");
   const limit=Math.max(1,Math.min(3500,Number(body?.limit||200)));
   const offset=Math.max(0,Math.min(3500,Number(body?.offset||0)));
   const rows=await sql.unsafe(
    "select l.display_name,l.canonical_key,l.category,l.subcategory,l.logo_url,l.epg_id,l.sort_order,"+
    "i.external_id,b.provider_stream_id,b.source_format,p.name provider "+
    "from lounge_delivery.live_release_items l "+
    "join lounge_delivery.item_ids i on i.item_kind='live' and i.canonical_id=l.live_channel_id "+
    "join lounge_delivery.live_source_bindings b on b.release_id=l.release_id and b.live_channel_id=l.live_channel_id and b.source_role='primary' "+
    "join public.providers p on p.id=b.provider_id "+
    "where l.release_id=$1::uuid and l.admission='keep' order by l.sort_order,i.external_id",[QA_RELEASE]);
   const selected=wanted?rows.filter((r:any)=>presentationId(r.category,r.subcategory)===wanted):rows;
   const visible=selected.slice(offset,offset+limit);
   return json({total:selected.length,offset,streams:visible.map((r:any,i:number)=>({
      num:offset+i+1,name:r.display_name,stream_type:"live",stream_id:Number(r.external_id),
      stream_icon:r.logo_url||"",epg_channel_id:r.epg_id||"",added:"0",
      category_id:presentationId(r.category,r.subcategory),
      custom_sid:"",tv_archive:0,direct_source:"",tv_archive_duration:0,
      qa:{canonical_key:r.canonical_key,provider:r.provider,source_stream_id:Number(r.provider_stream_id),format:r.source_format},
   }))});
  }
  if(action==="get_short_epg"){
   const id=Number(body?.stream_id||0),limit=Math.max(1,Math.min(20,Number(body?.limit||6)));
   if(!Number.isSafeInteger(id)||id<1)return json({error:"bad_stream_id"},400);
   const lookup=await sql.unsafe(
    "select l.epg_id,l.canonical_key from lounge_delivery.item_ids i "+
    "join lounge_delivery.live_release_items l on l.live_channel_id=i.canonical_id "+
    "where i.item_kind='live' and i.external_id=$1 and l.release_id=$2::uuid and l.admission='keep' limit 1",[id,QA_RELEASE]);
   const e=lookup[0];if(!e)return json({epg_listings:[]});
   const epgId=String(e.epg_id||e.canonical_key||"");
   const epg=await sql.unsafe(
     "select listings from lounge_delivery.shared_epg_channels where epg_id=$1 and expires_at>now() order by synced_at desc limit 1",[epgId]);
   const now=Math.floor(Date.now()/1000);
   const listings=Array.isArray(epg[0]?.listings)?epg[0].listings:[];
   return json({epg_listings:listings.filter((x:any)=>Number(x.stop_timestamp||0)>=now-900)
     .slice(0,limit).map((x:any)=>({...x,channel_id:"ltv-"+id,epg_id:id,
         now_playing:Number(x.start_timestamp||0)<=now&&Number(x.stop_timestamp||0)>now?1:0,has_archive:0}))});
  }
  return json({error:"unsupported_qa_action"},400);
 }catch(err){
   console.error("QA_PREVIEW_ERROR",String(err));
   return json({error:"qa_preview_error"},500);
 }
});
