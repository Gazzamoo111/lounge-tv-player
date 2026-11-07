import { appendXmltv } from "./stream-xmltv.ts";
import postgres from "npm:postgres@3.4.7";

// LOUNGE_SUPAVISOR_TRANSACTION_POOL
const rawDbUrl=Deno.env.get("SUPABASE_DB_URL");
if(!rawDbUrl) throw new Error("SUPABASE_DB_URL missing");
function transactionPoolUrl(raw:string){
  const u=new URL(raw);
  const parts=u.hostname.split(".");
  const ref=parts.length>1?parts[1]:"liqocisegeqzbyuqloql";
  u.hostname="aws-0-ap-southeast-2.pooler.supabase.com";
  u.port="5432";
  u.username="postgres."+ref;
  return u.toString();
}
const dbUrl=transactionPoolUrl(rawDbUrl);
const sql=postgres(dbUrl,{prepare:false,max:1,idle_timeout:5,connect_timeout:10});

const xmlEsc=(s:unknown)=>String(s??"")
  .replace(/&/g,"&amp;")
  .replace(/</g,"&lt;")
  .replace(/>/g,"&gt;")
  .replace(/"/g,"&quot;")
  .replace(/'/g,"&apos;");

const terrestrialIds=["mjh-tvnz-1","mjh-tvnz-2","mjh-three","mjh-maori-tv","mjh-te-reo"];
let xmlCache:{until:number,body:string}|null=null;
const LOUNGE_STATIC_EPG_URL="https://raw.githubusercontent.com/Gazzamoo111/lounge-tv-player/epg-data/epg/guide.xml";

const skySportTargets=[
  {id:"SkySport1.nz",rx:/\bsky\s*sport\s*1\b/i},
  {id:"SkySport2.nz",rx:/\bsky\s*sport\s*2\b/i},
  {id:"SkySport3.nz",rx:/\bsky\s*sport\s*3\b/i},
  {id:"SkySport4.nz",rx:/\bsky\s*sport\s*4\b/i},
  {id:"SkySport5.nz",rx:/\bsky\s*sport\s*5\b/i},
  {id:"SkySport6.nz",rx:/\bsky\s*sport\s*6\b/i},
  {id:"SkySport7.nz",rx:/\bsky\s*sport\s*7\b/i},
  {id:"SkySport8.nz",rx:/\bsky\s*sport\s*8\b/i},
  {id:"SkySport9.nz",rx:/\bsky\s*sport\s*9\b/i},
  {id:"SkySportSelect.nz",rx:/\bsky\s*sport\s*select\b/i},
];

function fmt(d:unknown){
  const x=new Date(String(d)),z=(n:number)=>String(n).padStart(2,"0");
  return `${x.getUTCFullYear()}${z(x.getUTCMonth()+1)}${z(x.getUTCDate())}${z(x.getUTCHours())}${z(x.getUTCMinutes())}${z(x.getUTCSeconds())} +0000`;
}

function reEsc(s:string){
  return s.replace(/[.*+?^$()|[\]{}\\]/g,"\\$&");
}

async function fetchGzXml(sourceUrl:string):Promise<string>{
  const r=await fetch(sourceUrl,{
    headers:{"User-Agent":"LoungeTV-EPG-Merge/1.1"},
    signal:AbortSignal.timeout(25000),
  });
  if(!r.ok) return "";
  const enc=(r.headers.get("content-encoding")||"").toLowerCase();
  if(enc.includes("gzip")) return await r.text();
  const bytes=new Uint8Array(await r.arrayBuffer());
  if(bytes[0]===0x1f&&bytes[1]===0x8b){
    const ds=new DecompressionStream("gzip");
    const stream=new Blob([bytes]).stream().pipeThrough(ds);
    return await new Response(stream).text();
  }
  return new TextDecoder().decode(bytes);
}

function extractByIds(xml:string,ids:string[]){
  const channels:string[]=[];
  const programmes:string[]=[];
  for(const id of ids){
    const safe=reEsc(id);
    const ch=new RegExp(`<channel\\b[^>]*\\bid=["']${safe}["'][\\s\\S]*?<\\/channel>`,"i").exec(xml)?.[0];
    if(ch) channels.push(ch);
    const rx=new RegExp(`<programme\\b(?=[^>]*\\bchannel=["']${safe}["'])[^>]*>[\\s\\S]*?<\\/programme>`,"gi");
    let m:RegExpExecArray|null;
    while((m=rx.exec(xml))!==null) programmes.push(m[0]);
  }
  return {channels,programmes};
}

let ukMovieSupplementCache:{until:number,signature:string,body:string}|null=null;

function maybeDecodeShort(value:unknown):string{
  if(value==null) return "";
  const raw=String(value).trim();
  if(!raw) return "";
  try{
    if(/^[A-Za-z0-9+/=]+$/.test(raw)&&raw.length%4===0){
      const bin=atob(raw);
      const bytes=Uint8Array.from(bin,c=>c.charCodeAt(0));
      const decoded=new TextDecoder().decode(bytes).trim();
      if(decoded&&/[A-Za-z0-9]/.test(decoded)) return decoded;
    }
  }catch{}
  return raw;
}

function shortDate(value:unknown):Date|null{
  const n=Number(value);
  if(Number.isFinite(n)&&n>0){
    const d=new Date(n>10_000_000_000?n:n*1000);
    if(!Number.isNaN(d.getTime())) return d;
  }
  if(typeof value==="string"&&value.trim()){
    const raw=value.trim();
    const d=new Date(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(raw)?raw.replace(" ","T")+"Z":raw);
    if(!Number.isNaN(d.getTime())) return d;
  }
  return null;
}


const SKY_CINEMA_SIDS:Record<string,string>={
  "SkyCinemaAction.uk":"4014",
  "SkyCinemaGreats.uk":"4015",
  "SkyCinemaDrama.uk":"4016",
  "SkyCinemaSciFiHorror.uk":"4017",
  "SkyCinemaFamily.uk":"4018",
  "SkyCinemaComedy.uk":"4019",
  "SkyCinemaSelect.uk":"4020",
  "SkyCinemaPremiere.uk":"4021",
  "SkyPremiere.uk":"4021",
  "SkyCinemaHits.uk":"4033",
  "SkyCinemaThriller.uk":"4062",
  "SkyThriller.uk":"4062",
  "SkyAction.uk":"4014",
};

function skyScheduleDate(offset:number){
  const d=new Date(Date.now()+offset*86400000);
  const z=(n:number)=>String(n).padStart(2,"0");
  return `${d.getUTCFullYear()}${z(d.getUTCMonth()+1)}${z(d.getUTCDate())}`;
}

async function buildSkyCinemaSupplement(missing:any[],staticXml:string){
  const targets=missing
    .map((r:any)=>({...r,_epg:String(r.epg_id||r.canonical_key||"").trim()}))
    .filter((r:any)=>r._epg&&SKY_CINEMA_SIDS[r._epg]);
  const covered=new Set<string>();
  if(!targets.length) return {body:"",covered};

  const sids=[...new Set(targets.map((r:any)=>SKY_CINEMA_SIDS[r._epg]))];
  const dates=[skyScheduleDate(0),skyScheduleDate(1),skyScheduleDate(2)];
  const payloads=await Promise.all(dates.map(async(date)=>{
    try{
      const r=await fetch(
        `https://awk.epgsky.com/hawk/linear/schedule/${date}/${sids.join(",")}`,
        {
          headers:{
            "X-SkyOTT-Territory":"GB",
            "Accept":"application/json",
            "User-Agent":"LoungeTV-SkyCinema-EPG/1.0",
          },
          signal:AbortSignal.timeout(10000),
        },
      );
      if(!r.ok) return null;
      return await r.json().catch(()=>null);
    }catch{return null;}
  }));

  const eventsBySid=new Map<string,any[]>();
  for(const payload of payloads){
    if(!Array.isArray(payload?.schedule)) continue;
    for(const sched of payload.schedule){
      const sid=String(sched?.sid||"");
      if(!sid||!Array.isArray(sched?.events)) continue;
      const list=eventsBySid.get(sid)||[];
      list.push(...sched.events);
      eventsBySid.set(sid,list);
    }
  }

  const now=Math.floor(Date.now()/1000);
  const horizon=now+3*86400;
  const pieces:string[]=[];
  for(const r of targets){
    const id=r._epg;
    const sid=SKY_CINEMA_SIDS[id];
    const raw=eventsBySid.get(sid)||[];
    const seen=new Set<string>();
    const events=raw
      .filter((e:any)=>{
        const st=Number(e?.st),dur=Number(e?.d);
        if(!Number.isFinite(st)||!Number.isFinite(dur)||dur<=0||!e?.t) return false;
        const stop=st+dur;
        return stop>=now-1800&&st<=horizon;
      })
      .filter((e:any)=>{
        const key=String(e?.eid||"")+""+String(e?.st||"");
        if(seen.has(key)) return false;
        seen.add(key);
        return true;
      })
      .sort((a:any,b:any)=>Number(a.st)-Number(b.st));
    if(!events.length) continue;

    covered.add(id);
    const safe=reEsc(id);
    if(!new RegExp(`<channel\\b[^>]*\\bid=["']${safe}["']`,"i").test(staticXml)){
      let ch=`<channel id="${xmlEsc(id)}"><display-name>${xmlEsc(r.display_name)}</display-name>`;
      if(r.logo_url) ch+=`<icon src="${xmlEsc(r.logo_url)}"/>`;
      ch+="</channel>\n";
      pieces.push(ch);
    }

    for(const e of events){
      const start=new Date(Number(e.st)*1000);
      const stop=new Date((Number(e.st)+Number(e.d))*1000);
      let p=`<programme channel="${xmlEsc(id)}" start="${fmt(start.toISOString())}" stop="${fmt(stop.toISOString())}"><title>${xmlEsc(e.t)}</title>`;
      if(e.sy) p+=`<desc>${xmlEsc(e.sy)}</desc>`;
      p+="<category>Movies</category>";
      if(e.programmeuuid){
        p+=`<icon src="https://images.metadata.sky.com/pd-image/${xmlEsc(e.programmeuuid)}/16-9/640"/>`;
      }
      p+="</programme>\n";
      pieces.push(p);
    }
  }
  return {body:pieces.join(""),covered};
}

async function buildUkMovieSupplement(staticXml:string):Promise<string>{
  const rows=await sql`
    with rel as (
      select id from lounge_delivery.catalogue_releases
      where state='active'
      order by activated_at desc nulls last
      limit 1
    )
    select l.canonical_key,l.display_name,l.logo_url,l.epg_id,l.primary_provider_stream_id,l.sort_order
    from lounge_delivery.live_release_items l,rel
    where l.release_id=rel.id and l.admission='keep'
      and l.category='Movies' and l.subcategory='UK Movies'
      and l.primary_provider_stream_id is not null
    order by l.sort_order,l.display_name
  `;
  const missing=rows.filter((r:any)=>{
    const id=String(r.epg_id||r.canonical_key||"").trim();
    if(!id) return false;
    const safe=reEsc(id);
    return !new RegExp(`<programme\\b(?=[^>]*\\bchannel=["']${safe}["'])`,"i").test(staticXml);
  });
  if(!missing.length) return "";

  const signature=missing
    .map((r:any)=>String(r.epg_id||r.canonical_key||"")+":"+String(r.primary_provider_stream_id))
    .join(",");
  if(ukMovieSupplementCache&&ukMovieSupplementCache.until>Date.now()&&ukMovieSupplementCache.signature===signature){
    return ukMovieSupplementCache.body;
  }

  const pieces:string[]=[];
  const sky=await buildSkyCinemaSupplement(missing,staticXml);
  if(sky.body) pieces.push(sky.body);

  const remaining=missing.filter((r:any)=>{
    const id=String(r.epg_id||r.canonical_key||"").trim();
    return id&&!sky.covered.has(id);
  });

  if(remaining.length){
    const creds=await sql`
      select decrypted_secret::jsonb as c
      from vault.decrypted_secrets
      where name='lounge_best_xtream_catalogue_credentials'
      limit 1
    `;
    const c=creds[0]?.c;
    if(c?.server_url&&c?.username&&c?.password){
      const base=String(c.server_url).replace(/\/+$/,"");
      const username=String(c.username),password=String(c.password);
      const work=remaining.slice(0,40);
      for(let offset=0;offset<work.length;offset+=8){
        const chunk=work.slice(offset,offset+8);
        const results=await Promise.all(chunk.map(async(r:any)=>{
          const id=String(r.epg_id||r.canonical_key||"").trim();
          if(!id) return "";
          const q=new URL(base+"/player_api.php");
          q.searchParams.set("username",username);
          q.searchParams.set("password",password);
          q.searchParams.set("action","get_short_epg");
          q.searchParams.set("stream_id",String(r.primary_provider_stream_id));
          q.searchParams.set("limit","60");
          try{
            const rr=await fetch(q,{
              headers:{Accept:"application/json","User-Agent":"LoungeTV-UKMovies-EPG/1.1"},
              signal:AbortSignal.timeout(8000),
            });
            if(!rr.ok) return "";
            const payload=await rr.json().catch(()=>null);
            const list=Array.isArray(payload?.epg_listings)?payload.epg_listings:[];
            if(!list.length) return "";
            let out="";
            const safe=reEsc(id);
            if(!new RegExp(`<channel\\b[^>]*\\bid=["']${safe}["']`,"i").test(staticXml)){
              out=`<channel id="${xmlEsc(id)}"><display-name>${xmlEsc(r.display_name)}</display-name>`;
              if(r.logo_url) out+=`<icon src="${xmlEsc(r.logo_url)}"/>`;
              out+="</channel>\n";
            }
            for(const item of list){
              const start=shortDate(item?.start_timestamp??item?.start);
              const stop=shortDate(item?.stop_timestamp??item?.end_timestamp??item?.end??item?.stop);
              const title=maybeDecodeShort(item?.title);
              if(!start||!stop||stop<=start||!title) continue;
              const desc=maybeDecodeShort(item?.description);
              const category=maybeDecodeShort(item?.category);
              out+=`<programme channel="${xmlEsc(id)}" start="${fmt(start.toISOString())}" stop="${fmt(stop.toISOString())}"><title>${xmlEsc(title)}</title>`;
              if(desc) out+=`<desc>${xmlEsc(desc)}</desc>`;
              if(category) out+=`<category>${xmlEsc(category)}</category>`;
              out+="</programme>\n";
            }
            return out;
          }catch{return "";}
        }));
        pieces.push(...results.filter(Boolean));
      }
    }
  }

  const body=pieces.join("");
  ukMovieSupplementCache={until:Date.now()+15*60*1000,signature,body};
  return body;
}

function extractSkySportFull(xml:string){
  const channels:string[]=[];
  const programmes:string[]=[];
  const mappings:{sourceId:string,targetId:string,block:string,score:number}[]=[];
  const chRx=/<channel\b[^>]*\bid=["']([^"']+)["'][^>]*>[\s\S]*?<\/channel>/gi;
  let m:RegExpExecArray|null;
  while((m=chRx.exec(xml))!==null){
    const block=m[0];
    const sourceId=m[1];
    const display=[...block.matchAll(/<display-name(?:\s[^>]*)?>([\s\S]*?)<\/display-name>/gi)]
      .map(x=>x[1].replace(/<[^>]+>/g," ").replace(/&amp;/g,"&").trim())
      .join(" ");
    const target=skySportTargets.find(x=>x.rx.test(display));
    if(!target) continue;
    const normalized=display.toLowerCase().replace(/\s+/g," ").trim();
    const score=(normalized.includes("uhd")||normalized.includes("4k"))?1:2;
    const existing=mappings.find(x=>x.targetId===target.id);
    if(!existing||score>existing.score){
      if(existing) mappings.splice(mappings.indexOf(existing),1);
      mappings.push({sourceId,targetId:target.id,block,score});
    }
  }

  for(const map of mappings){
    const safe=reEsc(map.sourceId);
    channels.push(map.block.replace(
      new RegExp(`(<channel\\b[^>]*\\bid=["'])${safe}(["'])`,"i"),
      `$1${map.targetId}$2`,
    ));
    const rx=new RegExp(`<programme\\b(?=[^>]*\\bchannel=["']${safe}["'])[^>]*>[\\s\\S]*?<\\/programme>`,"gi");
    let p:RegExpExecArray|null;
    while((p=rx.exec(xml))!==null){
      programmes.push(p[0].replace(
        new RegExp(`(\\bchannel=["'])${safe}(["'])`,"i"),
        `$1${map.targetId}$2`,
      ));
    }
  }
  return {channels,programmes};
}

Deno.serve(async(req:Request)=>{
  if(req.method!=="GET"&&req.method!=="HEAD") return new Response("Method not allowed",{status:405});
  const requestUrl=new URL(req.url);
  if(req.method==="GET"&&requestUrl.searchParams.get("probe")==="skygo-movies"){
    try{
      const xml=await fetchGzXml("https://i.mjh.nz/SkyGo/epg.xml.gz");
      if(!xml) return Response.json({ok:false,error:"skygo_empty"},{status:502});
      const rows:any[]=[];
      const rx=/<channel\b[^>]*\bid=["']([^"']+)["'][^>]*>[\s\S]*?<\/channel>/gi;
      let m:RegExpExecArray|null;
      while((m=rx.exec(xml))!==null){
        const block=m[0];
        const names=[...block.matchAll(/<display-name(?:\s[^>]*)?>([\s\S]*?)<\/display-name>/gi)]
          .map(x=>x[1].replace(/<[^>]+>/g," ").replace(/&amp;/g,"&").trim())
          .filter(Boolean);
        if(names.some(n=>/sky\s*cinema|sky\s*premiere|sky\s*action|sky\s*thriller/i.test(n))){
          const safe=reEsc(m[1]);
          const count=(xml.match(new RegExp(`<programme\\b(?=[^>]*\\bchannel=["']${safe}["'])`,"gi"))||[]).length;
          rows.push({id:m[1],names,programmes:count});
        }
      }
      return Response.json({ok:true,channels:rows.length,rows},{headers:{"Cache-Control":"no-store"}});
    }catch(err){
      return Response.json({ok:false,error:String(err)},{status:502});
    }
  }
  if(req.method==="GET"&&requestUrl.searchParams.get("probe")==="uk-movies"){
    try{
      const upstream=await fetch(LOUNGE_STATIC_EPG_URL,{
        headers:{"User-Agent":"LoungeTV-EPG-Probe/1.0","Accept":"application/xml,text/xml,*/*"},
        signal:AbortSignal.timeout(12000),
      });
      if(!upstream.ok) return Response.json({ok:false,error:"static_http_"+upstream.status},{status:502});
      const staticXml=await upstream.text();
      const supplement=await buildUkMovieSupplement(staticXml);
      const channels=(supplement.match(/<channel\b/gi)||[]).length;
      const programmes=(supplement.match(/<programme\b/gi)||[]).length;
      return Response.json({ok:true,source:"canonical-static+sky+best-uk-movies",channels,programmes,supplement_bytes:supplement.length},{
        headers:{"Cache-Control":"no-store","Access-Control-Allow-Origin":"*"},
      });
    }catch(err){
      return Response.json({ok:false,error:String(err)},{status:502});
    }
  }

  // The expensive regional merge is built off-path. This request path only streams
  // the finished canonical guide. If that artifact is unavailable, the existing
  // lightweight NZ/Sky/core fallback below remains intact.
  try{
    const upstream=await fetch(LOUNGE_STATIC_EPG_URL,{
      method:req.method,
      headers:{"User-Agent":"LoungeTV-EPG-Serve/1.0","Accept":"application/xml,text/xml,*/*"},
      signal:AbortSignal.timeout(12000),
    });
    if(upstream.ok){
      if(req.method==="HEAD"){
        return new Response(null,{
          status:200,
          headers:{
            "Content-Type":"application/xml; charset=utf-8",
            "Cache-Control":"public, max-age=900, stale-while-revalidate=3600",
            "Access-Control-Allow-Origin":"*",
            "X-Lounge-EPG-Source":"canonical-static+sky+uk-movie-fallback",
          },
        });
      }
      if(!upstream.body) throw Error("Canonical guide has no body");
      const body=appendXmltv(upstream.body,async(presence)=>{
        let supplement="";
        try{supplement=await buildUkMovieSupplement(presence);}
        catch(err){console.error("UK_MOVIE_SUPPLEMENT_UNAVAILABLE",String(err));}
        let skyNzSupplement="";
        try{
          const skyResponse=await fetch("https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-core-sky-nz-epg",{
            signal:AbortSignal.timeout(18000),
            headers:{Accept:"application/xml,text/xml,*/*"},
          });
          if(skyResponse.ok)skyNzSupplement=await skyResponse.text();
        }catch(err){console.error("SKY_NZ_SUPPLEMENT_UNAVAILABLE",String(err));}
        return supplement+skyNzSupplement;
      });
      return new Response(body,{
        status:200,
        headers:{
          "Content-Type":"application/xml; charset=utf-8",
          "Cache-Control":"public, max-age=900, stale-while-revalidate=3600",
          "Access-Control-Allow-Origin":"*",
          "X-Lounge-EPG-Source":"canonical-stream+sky+uk-movies",
        },
      });
    }
  }catch(err){
    console.error("LOUNGE_STATIC_EPG_FETCH_FAIL",String(err));
  }

  if(xmlCache&&xmlCache.until>Date.now()){
    return new Response(req.method==="HEAD"?"":xmlCache.body,{
      headers:{
        "Content-Type":"application/xml; charset=utf-8",
        "Cache-Control":"public, max-age=600, stale-while-revalidate=1800",
        "Access-Control-Allow-Origin":"*",
      },
    });
  }

  let rows:any[]=[];
  try{
    rows=await sql`
    select
      c.canonical_key,c.display_name,c.logo_url,c.epg_id,
      p.start_at,p.end_at,p.title,p.description,p.category
    from lounge_core.live_channels c
    join lounge_core.live_customer_taxonomy t
      on t.live_channel_id=c.id and t.enabled
    join lounge_core.live_epg_programmes p on p.live_channel_id=c.id
    where c.status in ('staging','approved')
      and p.end_at>now()-interval '12 hours'
      and p.start_at<now()+interval '3 days'
    order by c.id,p.start_at,p.end_at,p.title
  `;
  }catch(err){
    console.error("LOUNGE_XMLTV_DB_BYPASS",err);
  }

  const externallyManaged=new Set([...terrestrialIds,...skySportTargets.map(x=>x.id)]);
  const channels=new Map<string,{name:string,logo:string|null}>();
  for(const r of rows){
    const id=String(r.epg_id||r.canonical_key);
    if(!externallyManaged.has(id)){
      channels.set(id,{name:String(r.display_name),logo:r.logo_url?String(r.logo_url):null});
    }
  }

  let out='<?xml version="1.0" encoding="UTF-8"?>\n<tv generator-info-name="Lounge TV Core">\n';
  for(const [id,c] of channels){
    out+=`<channel id="${xmlEsc(id)}"><display-name>${xmlEsc(c.name)}</display-name>`;
    if(c.logo) out+=`<icon src="${xmlEsc(c.logo)}"/>`;
    out+='</channel>\n';
  }

  for(const r of rows){
    const id=String(r.epg_id||r.canonical_key);
    if(externallyManaged.has(id)) continue;
    out+=`<programme channel="${xmlEsc(id)}" start="${fmt(r.start_at)}" stop="${fmt(r.end_at)}"><title>${xmlEsc(r.title)}</title>`;
    if(r.description) out+=`<desc>${xmlEsc(r.description)}</desc>`;
    if(r.category) out+=`<category>${xmlEsc(r.category)}</category>`;
    out+='</programme>\n';
  }

  try{
    const nz=extractByIds(await fetchGzXml("https://i.mjh.nz/nz/epg.xml.gz"),terrestrialIds);
    for(const ch of nz.channels) out+=ch+"\n";
    for(const p of nz.programmes) out+=p+"\n";
  }catch{}

  try{
    const sky=extractSkySportFull(await fetchGzXml("https://m3u-edit.com/epg-source.php?file=NZ1.xml.gz"));
    for(const ch of sky.channels) out+=ch+"\n";
    for(const p of sky.programmes) out+=p+"\n";
  }catch{}

  out+='</tv>\n';
  xmlCache={until:Date.now()+900000,body:out};
  return new Response(req.method==="HEAD"?"":out,{
    headers:{
      "Content-Type":"application/xml; charset=utf-8",
      "Cache-Control":"public, max-age=600, stale-while-revalidate=1800",
      "Access-Control-Allow-Origin":"*",
    },
  });
});