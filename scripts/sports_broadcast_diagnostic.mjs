// Diagnostic only. Public ESPN fixture metadata and full-catalogue resolver context.
const urls=[
 ["nba","https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates=20261105&limit=100"],
 ["nba-prior","https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates=20261104&limit=100"],
 ["nhl","https://site.api.espn.com/apis/site/v2/sports/hockey/nhl/scoreboard?dates=20261105&limit=100"],
 ["cricket","https://site.web.api.espn.com/apis/v2/scoreboard/header?sport=cricket&league=all&dates=20261105"],
];
for(const [name,url] of urls){
 try{
  const res=await fetch(url,{signal:AbortSignal.timeout(12000)});
  if(!res.ok){console.log(name,"HTTP",res.status);continue}
  const json=await res.json();
  const events=name==="cricket"?(json.sports||[]).flatMap(s=>(s.leagues||[]).flatMap(l=>l.events||[])):json.events||[];
  console.log("=== "+name+" "+events.length+" events ===");
  for(const e of events.slice(0,20)){
   const c=e.competitions?.[0]||{};
   const teams=c.competitors?.map(k=>({name:k.team?.displayName||k.team?.name||k.athlete?.displayName,short:k.team?.abbreviation,homeAway:k.homeAway}))||e.competitors?.map(k=>({name:k.team?.displayName||k.team?.name}))||[];
   console.log(JSON.stringify({id:e.id,date:e.date,name:e.name||e.shortName,teams,competitionsKeys:Object.keys(c),broadcasts:c.broadcasts,geoBroadcasts:c.geoBroadcasts,notes:c.notes,status:e.status?.type?.state||e.status},null,0).slice(0,1650));
  }
 }catch(error){console.log(name,"ERROR",String(error))}
}
