// GPL-3.0-only. Port of features/sports/SportLoungeRules.kt from Lounge TV.
export const categories = Object.freeze({ALL: [], NFL:['football'], LEAGUE:['rugby-league'], RUGBY:['rugby'], CRICKET:['cricket'], BASKETBALL:['basketball'], FOOTBALL:['soccer'], COMBAT:['mma'], MOTORSPORT:['racing'], TENNIS:['tennis'], HOCKEY:['hockey'], BASEBALL:['baseball'], GOLF:['golf'], AFL:['australian-football'], VOLLEYBALL:['volleyball'], LACROSSE:['lacrosse']});
const primary = ['ALL','NFL','LEAGUE','RUGBY','CRICKET','BASKETBALL','FOOTBALL','COMBAT','MOTORSPORT'];
const rank={PRE:0,IN:1,POST:2,POSTPONED:3,CANCELLED:4};
export function localDate(ms,zone='Pacific/Auckland') {
 const parts=new Intl.DateTimeFormat('en-US',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(ms);
 const get=t=>parts.find(p=>p.type===t).value;
 return `${get('year')}-${get('month')}-${get('day')}`;
}
function afterDays(day,n){const d=new Date(`${day}T12:00:00Z`); d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10);}
export function belongsTo(m,filter,now,zone){
 const today=localDate(now,zone),day=localDate(m.startMs,zone);
 if(filter==='LIVE_NOW')return m.state==='IN';
 if(filter==='TODAY')return m.state!=='IN'&&day===today;
 if(filter==='UPCOMING')return m.state==='PRE'&&m.startMs>now&&day>=today&&day<afterDays(today,7);
 throw new Error('UNKNOWN_SPORT_FILTER');
}
export function dedupe(matches){
 const map=new Map();
 const score=m=>[rank[m.state]??-1,m.events?.length??0,m.competitors?.filter(s=>s.score!=null).length??0];
 for(const m of matches){const old=map.get(m.key);if(!old){map.set(m.key,m);continue;}const a=score(m),b=score(old);for(let i=0;i<a.length;i++){if(a[i]>b[i]){map.set(m.key,m);break;}if(a[i]<b[i])break;}}
 return [...map.values()];
}
function priority(m){const text=[m.feed,m.leagueName,m.title,...(m.competitors??[]).flatMap(s=>[s.team?.name,s.team?.location])].join(' ').toLowerCase();return ['rugby','rugby-league','australian-football'].includes(m.sport)||['new zealand',' nz ','nrl','super rugby','australia','australian','breakers','black caps','silver ferns'].some(x=>text.includes(x));}
export function selectSports(source,{filter='LIVE_NOW',category='ALL',now=Date.now(),zone='Pacific/Auckland',followedTeams=[],myTeamsOnly=false}={}){
 if(!categories[category])throw new Error('UNKNOWN_SPORT_CATEGORY');
 const all=dedupe(source),sports=new Set(all.map(m=>m.sport)),followed=new Set(followedTeams);
 const selected=all.filter(m=>(category==='ALL'||categories[category].includes(m.sport))&&(!myTeamsOnly||(m.competitors??[]).some(s=>followed.has(`${m.sport}/${s.team?.id}`))));
 const counts=Object.fromEntries(['LIVE_NOW','TODAY','UPCOMING'].map(f=>[f,selected.filter(m=>belongsTo(m,f,now,zone)).length]));
 const matches=selected.filter(m=>belongsTo(m,filter,now,zone)).sort((a,b)=>(a.state==='IN'?0:1)-(b.state==='IN'?0:1)||Number(priority(b))-Number(priority(a))||((a.startMs>=now?a.startMs:Number.MAX_SAFE_INTEGER-a.startMs)-(b.startMs>=now?b.startMs:Number.MAX_SAFE_INTEGER-b.startMs))||a.key.localeCompare(b.key));
 return {matches,counts,categories:[...primary,...Object.keys(categories).filter(c=>!primary.includes(c)&&categories[c].some(s=>sports.has(s)))]};
}
