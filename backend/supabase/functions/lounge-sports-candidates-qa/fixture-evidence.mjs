import {norm} from "./matcher.mjs";

/**
 * Trust ESPN's server-side event record, not caller-supplied broadcaster names.
 * Require exact source event ID (when present), fixture start and both competitors.
 */
export async function findIndependentBroadcasts(event,requester=fetch){
  const route=String(event.competitionId||"");
  if(!/^[a-z][a-z0-9-]*\/[a-z0-9.-]+$/i.test(route))return {geo:[],source:"",status:"unsupported-competition"};
  const source="https://site.api.espn.com/apis/site/v2/sports/"+route+
    "/scoreboard?dates="+event.scheduledStartUtc.slice(0,10).replaceAll("-","")+"&limit=100";
  try{
    const response=await requester(source,{signal:AbortSignal.timeout(5000),headers:{"Accept":"application/json"}});
    if(!response.ok)return {geo:[],source,status:"fixture-source-http-"+response.status};
    const data=await response.json();
    const expectedId=String(event.eventId).match(/(?:#|:|\/)(\d{6,12})$/)?.[1]||null;
    const aliases=(side)=>[event[side],...(event[side+"Aliases"]||[])]
      .map(norm).filter((n)=>n.length>=4&&!["UNITED","CITY","NEW ZEALAND"].includes(n));
    const wantedHome=aliases("home"),wantedAway=aliases("away");
    if(!wantedHome.length||!wantedAway.length)return {geo:[],source,status:"fixture-identity-missing"};
    const selected=(data.events||[]).find((fixture)=>{
      const start=Date.parse(fixture.date);
      if(!Number.isFinite(start)||Math.abs(start-Date.parse(event.scheduledStartUtc))>15*60_000)return false;
      if(expectedId&&String(fixture.id)!==expectedId)return false;
      const competitors=fixture.competitions?.[0]?.competitors||[];
      if(competitors.length!==2)return false;
      const texts=competitors.map((c)=>norm([c.team?.displayName,c.team?.name,c.team?.shortDisplayName].filter(Boolean).join(" ")));
      const match=(wanted)=>texts.some((t)=>wanted.some(a=>(" "+t+" ").includes(" "+a+" ")));
      return match(wantedHome)&&match(wantedAway);
    });
    if(!selected)return {geo:[],source,status:"fixture-not-independently-confirmed"};
    return {geo:selected.competitions?.[0]?.geoBroadcasts||[],source,status:"fixture-confirmed"};
  }catch{return {geo:[],source,status:"fixture-source-unavailable"};}
}

