import postgres from "npm:postgres@3.4.7";

const rawDbUrl=Deno.env.get("SUPABASE_DB_URL");
if(!rawDbUrl) throw new Error("SUPABASE_DB_URL missing");
function poolUrl(raw:string){
  const u=new URL(raw);
  const ref=u.hostname.split(".")[1]||"liqocisegeqzbyuqloql";
  u.hostname="aws-0-ap-southeast-2.pooler.supabase.com";u.port="5432";u.username=`postgres.${ref}`;
  return u.toString();
}
const sql=postgres(poolUrl(rawDbUrl),{prepare:false,max:2,idle_timeout:5,connect_timeout:10});

function text(v:any){return v==null?"":String(v)}
function asMs(v:any){const n=Date.parse(text(v));return Number.isFinite(n)?n:null}
function competitor(side:any,index:number){
  const name=text(side?.name); if(!name)return null;
  return {id:text(side?.id||`boxer-${index}-${name}`),name,shortName:name,abbreviation:"",crestUrl:null,home:index===0,score:null};
}
function broadcastsOf(event:any,override:any){
  const base=Array.isArray(event?.broadcasts)?event.broadcasts.map((b:any)=>({name:text(b?.platform),market:text(b?.region)||null})).filter((b:any)=>b.name):[];
  const extra=Array.isArray(override?.broadcasts)?override.broadcasts:[];
  const out:any[]=[];
  for(const b of [...extra,...base]) if(b?.name&&!out.some(x=>x.name===b.name&&x.market===b.market)) out.push({name:text(b.name),market:b.market==null?null:text(b.market)});
  return out;
}
function headline(event:any,overrides:Map<string,any>){
  const bouts=Array.isArray(event?.bouts)?event.bouts:[];
  if(!bouts.length)return null;
  const explicit=bouts.map((b:any)=>({b,o:overrides.get(text(b?.id))})).filter((x:any)=>x.o).sort((a:any,b:any)=>(b.o.priority||0)-(a.o.priority||0))[0];
  return explicit?.b || bouts[0];
}
Deno.serve(async(req)=>{
  if(req.method!=="GET")return Response.json({error:"method_not_allowed"},{status:405});
  try{
    const url=new URL(req.url);
    const from=Number(url.searchParams.get("from")||Date.now()-6*3600_000);
    const to=Number(url.searchParams.get("to")||Date.now()+7*24*3600_000);
    const rows=await sql`select source,external_id,title,start_utc,broadcasts,venue,subtitle,priority from lounge_core.sports_event_overrides where source in ('boxerbook','rlwc') and active=true`;
    const boxerRows=rows.filter((r:any)=>text(r.source)==="boxerbook");
    const editorialRows=rows.filter((r:any)=>text(r.source)==="rlwc");
    const overrides=new Map(boxerRows.map((r:any)=>[text(r.external_id),r]));
    let payload:any=null;
    let feedError:string|null=null;
    try {
      const response=await fetch("https://boxerbook.com/v1/schedule?when=30d",{headers:{accept:"application/json","user-agent":"LoungeTV-SportLounge/1.0"},signal:AbortSignal.timeout(12000)});
      if(!response.ok)throw new Error("boxerbook_http_"+response.status);
      payload=await response.json();
    } catch(e) {
      feedError=String(e);
      console.warn("SPORTS_CALENDAR_FEED",feedError);
    }
    const events:any[]=[];
    for(const card of (Array.isArray(payload?.events)?payload.events:[])){
      const bout=headline(card,overrides); if(!bout)continue;
      const override=overrides.get(text(bout.id));
      const start=override?.start_utc?asMs(override.start_utc):asMs(bout.start_utc||card.start_utc);
      if(start==null||start<from||start>to)continue;
      const bcasts=broadcastsOf(card,override);
      const a=competitor(bout.a,0),b=competitor(bout.b,1); if(!a||!b)continue;
      const title=override?.title||`${a.name} vs ${b.name}`;
      const subtitle=override?.subtitle||(
        bcasts.length?bcasts.map((x:any)=>x.name).join(" • "):
        [card.city,card.country].filter(Boolean).join(", ")||"Broadcast TBC"
      );
      events.push({
        id:`boxerbook:mma/boxing#${text(bout.id)}`,
        source:"boxerbook",externalId:text(bout.id),sport:"mma",
        leagueId:"mma/boxing",leagueName:"Boxing",feedId:"mma/boxing",leagueSlug:"boxing",
        title,subtitle,startUtcMs:start,state:"scheduled",statusText:"Upcoming",
        venue:override?.venue||card.venue||null,broadcasts:bcasts,
        competitors:[a,b],sourceUpdatedAtMs:Date.now(),lastSuccessfulUpdateMs:Date.now()
      });
    }
    // Confirmed editorial events must stay visible even when the provider feed is late or unavailable.
    for(const override of boxerRows){
      const id=text(override.external_id);
      if(events.some(e=>e.externalId===id))continue;
      const start=asMs(override.start_utc);
      if(start==null||start<from||start>to)continue;
      const title=text(override.title);
      const names=title.split(/\s+vs?\.?\s+/i).map(s=>s.trim());
      if(names.length!==2||!names[0]||!names[1])continue;
      const a=competitor({name:names[0]},0),b=competitor({name:names[1]},1);
      events.push({
        id:`boxerbook:mma/boxing#${id}`,source:"boxerbook",externalId:id,
        sport:"mma",leagueId:"mma/boxing",leagueName:"Boxing",feedId:"mma/boxing",leagueSlug:"boxing",
        title,subtitle:text(override.subtitle)||(Array.isArray(override.broadcasts)?override.broadcasts.map((b:any)=>text(b.name)).filter(Boolean).join(" • "):"")||"Broadcast TBC",
        startUtcMs:start,state:"scheduled",statusText:"Upcoming",venue:override.venue||null,
        broadcasts:Array.isArray(override.broadcasts)?override.broadcasts:[],
        competitors:[a,b],sourceUpdatedAtMs:Date.now(),lastSuccessfulUpdateMs:Date.now()
      });
    }
    for(const override of editorialRows){
      const id=text(override.external_id);
      const start=asMs(override.start_utc);
      if(start==null||start<from||start>to)continue;
      const title=text(override.title);
      const names=title.split(/\s+vs?\.?\s+/i).map(s=>s.trim());
      if(names.length!==2||!names[0]||!names[1])continue;
      const a=competitor({id:"rlwc-"+names[0],name:names[0]},0),b=competitor({id:"rlwc-"+names[1],name:names[1]},1);
      events.push({
        id:`rlwc:rugby-league/rlwc#${id}`,source:"rlwc",externalId:id,
        sport:"rugby-league",leagueId:"rugby-league/rlwc",leagueName:"Rugby League World Cup",
        feedId:"rugby-league/rlwc",leagueSlug:"rlwc",
        title,subtitle:text(override.subtitle)||(Array.isArray(override.broadcasts)?override.broadcasts.map((b:any)=>text(b.name)).filter(Boolean).join(" • "):"")||"Broadcast TBC",
        startUtcMs:start,state:"scheduled",statusText:"Upcoming",venue:override.venue||null,
        broadcasts:Array.isArray(override.broadcasts)?override.broadcasts:[],
        competitors:[a,b],sourceUpdatedAtMs:Date.now(),lastSuccessfulUpdateMs:Date.now()
      });
    }
    events.sort((a,b)=>a.startUtcMs-b.startUtcMs);
    return Response.json({
      events,
      status:feedError?"partial":"ok",
      lastUpdated:new Date().toISOString(),
      diagnostics:{failedFeeds:feedError?[{feed:"boxerbook"}]:[]},
      source:"Boxerbook",
      attribution:payload?.attribution||null,
      asOf:payload?.as_of||null
    },{
      headers:{"Cache-Control":"public, max-age=300, stale-while-revalidate=1800"}
    });
  }catch(e){
    console.error("SPORTS_CALENDAR",e);
    return Response.json({error:"calendar_unavailable"},{status:503,headers:{"Cache-Control":"no-store"}});
  }
});