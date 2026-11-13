// Lounge TV | secure isolated transport probe. HTTP byte evidence != device playback.
import postgres from "npm:postgres@3.4.7";
const dbUrl=Deno.env.get("SUPABASE_DB_URL");
if(!dbUrl) throw Error("database_url_missing");
const db=postgres(dbUrl,{prepare:false,max:1,idle_timeout:20,connect_timeout:10});
async function auth(req:Request):Promise<boolean>{
  const token=(req.headers.get("x-lounge-core-token")||"").trim();
  if(!token) return false;
  const digest=await crypto.subtle.digest("SHA-256",new TextEncoder().encode(token));
  const hash=Array.from(new Uint8Array(digest)).map(x=>x.toString(16).padStart(2,"0")).join("");
  const r=await db.unsafe("select 1 from lounge_core.worker_auth where key_name='metadata_enrich' and key_hash=$1 limit 1",[hash]);
  return r.length===1;
}
function mediaType(b:Uint8Array,type:string|null):string {
  for(let k=0;k<Math.min(188,b.length-188);k++){
    if(b[k]===0x47&&b[k+188]===0x47) return "mpeg_ts";
  }
  const h=new TextDecoder().decode(b.subarray(0,Math.min(512,b.length))).trimStart();
  if(h.startsWith("#EXTM3U")) return "hls_manifest";
  if(b.length>12&&b[4]===0x66&&b[5]===0x74&&b[6]===0x79&&b[7]===0x70) return "mp4_header";
  if(type?.includes("text/html")||h.toLowerCase().includes("<html")) return "html_not_video";
  return b.length?"unknown_bytes":"empty";
}
type Result={ok:boolean;kind:string;status:number|null;elapsed:number;bytes:number;type:string|null;error:string|null};
async function probe(url:string):Promise<Result>{
  const start=Date.now(),ctrl=new AbortController();
  const timer=setTimeout(()=>ctrl.abort(),8000);
  let reader:ReadableStreamDefaultReader<Uint8Array>|undefined;
  try{
    const res=await fetch(url,{headers:{"Accept":"*/*","User-Agent":"LoungeTV-Catalogue-QA/2.0"},redirect:"follow",signal:ctrl.signal});
    reader=res.body?.getReader();
    const chunks:Uint8Array[]=[];let total=0;
    if(reader){for(let i=0;i<3&&total<2048;i++){
      const item=await reader.read();if(item.done)break;
      if(item.value){chunks.push(item.value);total+=item.value.length;}
    }}
    const b=new Uint8Array(total);let off=0;
    for(const chunk of chunks){b.set(chunk,off);off+=chunk.length;}
    const type=res.headers.get("content-type"),kind=mediaType(b,type);
    const ok=res.ok&&["mpeg_ts","hls_manifest","mp4_header"].includes(kind);
    return {ok,kind,status:res.status,elapsed:Date.now()-start,bytes:total,type,error:ok?null:(res.ok?"unrecognised_media_payload":"http_error")};
  }catch(e){
    return {ok:false,kind:"network_error",status:null,elapsed:Date.now()-start,bytes:0,type:null,
      error:e instanceof DOMException&&e.name==="AbortError"?"timeout":"network_failure"};
  }finally{
    try{await reader?.cancel()}catch{}
    ctrl.abort();clearTimeout(timer);
  }
}
Deno.serve(async(req:Request)=>{
  if(req.method!=="POST")return Response.json({error:"method_not_allowed"},{status:405});
  try{
    if(!await auth(req))return Response.json({error:"unauthorised"},{status:401});
    const body=await req.json().catch(()=>({}));
    const limit=Math.max(1,Math.min(10,Number(body?.limit||6)));
    const rows=await db.unsafe(
      "select c.provider_stream_id,c.proposed_canonical_key,c.proposed_display_name,c.proposed_category "+
      "from lounge_core.live_rebuild_source_candidates_20261113 c "+
      "join lounge_sync.best_inventory_items i on i.kind='live' and i.provider_item_id=c.provider_stream_id "+
      "where c.shortlist_for_probe and c.probe_status='not_probed' and i.is_present and not coalesce(i.is_adult,false) "+
      "order by case c.proposed_category when 'Australia' then 0 when 'Entertainment' then 1 "+
      "when 'Movie Channels' then 2 when 'Streaming Channels' then 3 else 4 end, "+
      "c.candidate_rank asc nulls last,c.provider_stream_id limit $1",[limit]);
    const creds=await db.unsafe(
      "select decrypted_secret::jsonb as c from vault.decrypted_secrets "+
      "where name='lounge_best_xtream_catalogue_credentials' limit 1");
    const c=creds[0]?.c;
    if(!c?.server_url||!c?.username||!c?.password)return Response.json({error:"credentials_missing"},{status:503});
    const base=String(c.server_url).replace(/\/+$/,"");
    const results=[];
    for(const r of rows){
      const id=Number(r.provider_stream_id);
      const url=base+"/live/"+encodeURIComponent(String(c.username))+"/"+encodeURIComponent(String(c.password))+"/"+id+".ts";
      const ev=await probe(url);
      const state=ev.ok?(ev.kind==="hls_manifest"?"manifest_only":"transport_ok"):"transport_failed";
      await db.unsafe(
        "insert into lounge_core.live_candidate_probe_evidence_20261114 "+
        "(provider_stream_id,proposed_canonical_key,attempt,ok,transport_kind,status_code,first_byte_ms,bytes_read,content_type,error_code) "+
        "values ($1,$2,1,$3,$4,$5,$6,$7,$8,$9) on conflict (provider_stream_id,attempt) do update set "+
        "ok=excluded.ok,transport_kind=excluded.transport_kind,status_code=excluded.status_code,"+
        "first_byte_ms=excluded.first_byte_ms,bytes_read=excluded.bytes_read,content_type=excluded.content_type,"+
        "error_code=excluded.error_code,checked_at=now()",
        [id,r.proposed_canonical_key,ev.ok,ev.kind,ev.status,ev.elapsed,ev.bytes,ev.type,ev.error]);
      await db.unsafe(
        "update lounge_core.live_rebuild_source_candidates_20261113 set probe_status=$1 "+
        "where provider_stream_id=$2 and shortlist_for_probe",[state,id]);
      results.push({id,key:r.proposed_canonical_key,name:r.proposed_display_name,status:state,ok:ev.ok,
        kind:ev.kind,http:ev.status,first_byte_ms:ev.elapsed,bytes:ev.bytes,error:ev.error});
    }
    const left=await db.unsafe(
      "select count(*)::int n from lounge_core.live_rebuild_source_candidates_20261113 "+
      "where shortlist_for_probe and probe_status='not_probed'");
    return Response.json({ok:true,kind:"staging_transport_qa",checked:results.length,remaining:Number(left[0]?.n||0),results},
      {headers:{"Cache-Control":"no-store"}});
  }catch{
    return Response.json({error:"qa_internal_error"},{status:500});
  }
});
