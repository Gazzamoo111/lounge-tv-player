
import postgres from "npm:postgres@3.4.7";

const rawDbUrl=Deno.env.get("SUPABASE_DB_URL");
if(!rawDbUrl) throw new Error("SUPABASE_DB_URL missing");
function poolUrl(raw:string){
  const u=new URL(raw);
  const ref=u.hostname.split(".")[1]||"liqocisegeqzbyuqloql";
  u.hostname="aws-0-ap-southeast-2.pooler.supabase.com";
  u.port="5432";
  u.username="postgres."+ref;
  return u.toString();
}
const sql=postgres(poolUrl(rawDbUrl),{prepare:false,max:2,idle_timeout:5,connect_timeout:10});

const SPORT_CATEGORY_IDS=[
"2202","1118","2243","1334","2436","1596","1612","2336","2424","1532","2495","1523","1521","860",
"1960","675","401","677","678","1309","676","680","2213","2540","2270","2170","2219","804","1661",
"2223","1900","1367","1999","2178","2187","2420","384","1621","1007","2426","2425","2076","1916",
"2186","1571","1592","1857","1124","2511","2224","1631","668","2022","1606","1308","1753","2332",
"2528","1751","2401","1658","2183","2496","2232","2261","1528","1655","1580","1527","2541","1073",
"614","1117","1566","1570","1568","1504","1503","1581","2214","1795","1307","1211","622","1863",
"1165","2222","2175","1346","1347"
];

const PLACEHOLDER=/NO EVENT|NO STREAM|NOT STREAMING|OFFLINE|PLACEHOLDER|####|^#+|ENDED|REPLAY|HIGHLIGHTS/i;
const FIXTURE=/\b(vs?\.?|@| at )\b/i;
function norm(v:any){return String(v??"").normalize("NFD").replace(/[\u0300-\u036f]/g,"").toUpperCase().replace(/[^A-Z0-9]+/g," ").trim();}
function words(v:any){return norm(v).split(/\s+/).filter(Boolean);}
function hasPhrase(hay:string,needle:string){if(!needle)return false;return (" "+norm(hay)+" ").includes(" "+norm(needle)+" ");}
const aliasMap:Record<string,string[]>={
 "NEW ZEALAND":["NEW ZEALAND","NZ","NZL","KIWIS","ALL BLACKS"],
 "AUSTRALIA":["AUSTRALIA","AUS","KANGAROOS","WALLABIES"],
 "TONGA":["TONGA","TON"],"SAMOA":["SAMOA","SAM"],"SCOTLAND":["SCOTLAND","SCO"],
 "ENGLAND":["ENGLAND","ENG"],"WALES":["WALES","WAL"],"IRELAND":["IRELAND","IRL"],
 "FRANCE":["FRANCE","FRA"],"FIJI":["FIJI","FIJ"],"JAPAN":["JAPAN","JPN"],
 "ARGENTINA":["ARGENTINA","ARG","PUMAS"],"SOUTH AFRICA":["SOUTH AFRICA","RSA","SPRINGBOKS"],
 "ITALY":["ITALY","ITA"]
};
const teamAbbr:Record<string,string[]>={
 "SAN ANTONIO SPURS":["SPURS","SAS"],"UTAH JAZZ":["JAZZ","UTA"],
 "NEW ORLEANS PELICANS":["PELICANS","NOP"],"LOS ANGELES LAKERS":["LAKERS","LAL"],
 "GOLDEN STATE WARRIORS":["WARRIORS","GSW"],"LOS ANGELES CLIPPERS":["CLIPPERS","LAC"]
};
function aliases(side:string){
  const n=norm(side);if(!n)return [];
  const out=new Set<string>([n]);
  const ws=n.split(" ");if(ws.length>1&&ws[ws.length-1].length>=3)out.add(ws[ws.length-1]);
  (aliasMap[n]||[]).forEach(x=>out.add(norm(x)));
  (teamAbbr[n]||[]).forEach(x=>out.add(norm(x)));
  return [...out].filter(x=>x.length>=2);
}
function matchesAny(name:string,as:string[],allowShort:boolean){return as.some(a=>(allowShort||a.length>3)&&hasPhrase(name,a));}
function titleOverlap(a:string,b:string){
  const A=new Set(words(a).filter(x=>x.length>=3)),B=new Set(words(b).filter(x=>x.length>=3));
  if(!A.size||!B.size)return 0;
  let n=0;for(const x of A)if(B.has(x))n++;
  return n/Math.max(1,Math.min(A.size,B.size));
}
function broadcasterMatchScore(name:string,broadcaster:string){
  const n=norm(name),b=norm(broadcaster),market=n.split(" ")[0]||"";
  if(!b)return 0;
  // Ambiguous broadcaster names must respect their real market. This prevents, for example,
  // Australia's "Main Event" from promoting UK's "Sky Sports Main Event".
  if(b==="MAIN EVENT") return market==="AU" && hasPhrase(n,"MAIN EVENT") && !hasPhrase(n,"SKY SPORTS MAIN EVENT") ? 280 : 0;
  if(b==="KAYO SPORTS"||b==="KAYO") return market==="AU" && hasPhrase(n,"KAYO") ? 280 : 0;
  if(b==="FOXTEL") return market==="AU" && (hasPhrase(n,"FOXTEL")||hasPhrase(n,"MAIN EVENT")) ? 250 : 0;
  if(b==="SEVEN"||b==="7PLUS"||b==="7 PLUS") return market==="AU" && (/\\bCHANNEL 7\\b/.test(n)||/\\b7PLUS\\b/.test(n)) ? 250 : 0;
  if(b==="THREENOW"||b==="THREE NOW") return market==="NZ" && (hasPhrase(n,"THREE")||hasPhrase(n,"THREENOW")) ? 250 : 0;
  if(b.startsWith("SKY SPORT")) return market==="NZ" && hasPhrase(n,b) ? 290 : 0;
  return hasPhrase(n,b) ? 240 : 0;
}
function sportNetworkScore(name:string,sport:string,league:string,broadcasters:string[]){
  const n=norm(name),s=norm(sport),l=norm(league);let score=0;
  for(const b of broadcasters)score=Math.max(score,broadcasterMatchScore(name,b));
  const is=(...terms:string[])=>terms.some(t=>s.includes(t)||l.includes(t));
  if(is("BASKETBALL","NBA","WNBA","NBL")){
    if(l.includes("WNBA")){
      if(/\bWNBA\b/i.test(n))score=Math.max(score,130);
    } else if(l.includes("NBL")){
      if(/\bNBL\b/i.test(n))score=Math.max(score,130);
    } else if(l.includes("NBA")){
      if(/\bNBA\s*(TV|PASS|TEAM|FAST)|^:?NBA\b/i.test(n))score=Math.max(score,130);
    }
    if(/\bESPN\b|\bTNT\b|\bPEACOCK\b|\bPRIME\b|\bABC\b/i.test(n))score=Math.max(score,70);
  } else if(is("RUGBY LEAGUE","RLWC","NRL","SUPER LEAGUE")){
    if(/\bSKY SPORT\b|\bFOX LEAGUE\b|\bSUPER LEAGUE PLUS\b|\bSTAN SPORT\b|\bPREMIER SPORTS?\b|\bRUGBYPASS\b/i.test(n))score=Math.max(score,125);
  } else if(is("RUGBY")){
    if(/\bSKY SPORT\b|\bSTAN SPORT\b|\bPREMIER SPORTS?\b|\bRUGBYPASS\b|\bSUPERSPORT\b|\bITV\b/i.test(n))score=Math.max(score,120);
  } else if(is("CRICKET")){
    if(/\bSKY SPORT\b|\bSKY SPORTS CRICKET\b|\bWILLOW\b|\bFOX CRICKET\b|\bSTAN SPORT\b/i.test(n))score=Math.max(score,125);
  } else if(is("NFL","FOOTBALL")){
    if(/\bNFL\b|\bREDZONE\b|\bESPN\b|\bCBS\b|\bFOX\b|\bNBC\b|\bPEACOCK\b|\bPRIME\b/i.test(n))score=Math.max(score,120);
  } else if(is("HOCKEY","NHL")){
    if(/\bNHL\b|\bESPN\b|\bTNT\b/i.test(n))score=Math.max(score,120);
  } else if(is("BASEBALL","MLB")){
    if(/\bMLB\b|\bESPN\b|\bFOX SPORTS?\b|\bTBS\b/i.test(n))score=Math.max(score,120);
  } else if(is("SOCCER")){
    if(/\bSKY SPORTS (FOOTBALL|PREMIER LEAGUE|MAIN EVENT)\b|\bTNT SPORTS?\b|\bBEIN\b|\bDAZN\b|\bPARAMOUNT\b|\bPEACOCK\b/i.test(n))score=Math.max(score,115);
  } else if(is("MMA","BOXING","COMBAT")){
    if(/\bUFC\b|\bBOXING\b|\bDAZN\b|\bFITE\b|\bTRILLER\b|\bTNT SPORTS?\b|\bSKY SPORTS?\b/i.test(n))score=Math.max(score,125);
  }
  return score;
}

Deno.serve(async req=>{
  if(req.method!=="GET")return Response.json({error:"method_not_allowed"},{status:405});
  try{
    const u=new URL(req.url);
    const sport=u.searchParams.get("sport")||"",league=u.searchParams.get("league")||"",title=u.searchParams.get("title")||"";
    const home=u.searchParams.get("home")||"",away=u.searchParams.get("away")||"";
    const broadcasters=(u.searchParams.get("broadcasters")||"").split("|").map(norm).filter(Boolean);
    const homeAliases=aliases(home),awayAliases=aliases(away);
    // Build the candidate pool from the CURRENT BEST live catalogue, not a frozen list.
    // SPORT_CATEGORY_IDS remains as a compatibility floor for market/general folders that carry sport,
    // while dynamic category discovery automatically picks up new/renamed sports and PPV groups.
    const categoryRows=await sql`
      select category_id
      from lounge_sync.best_categories
      where kind='live' and is_present=true
        and category_name ~* '(sport|ppv|ufc|boxing|fight|nfl|nba|nhl|mlb|milb|ncaa|rugby|nrl|league|cricket|football|soccer|tennis|f1|motogp|golf|dazn|espn|stan|paramount|peacock|bein|tnt|fox)'
        and category_name !~* '(replay)'
    `;
    const dynamicCategoryIds=categoryRows.map((r:any)=>String(r.category_id));
    const sportCategoryIds=[...new Set([...SPORT_CATEGORY_IDS,...dynamicCategoryIds])];

    const rows=await sql`
      select provider_item_id,category_id,name,artwork_url,epg_channel_id
      from lounge_sync.best_inventory_items
      where kind='live' and is_present=true
        and (
          category_id=any(${sportCategoryIds}::text[])
          or name ~* '(sport|nba|nfl|nhl|mlb|milb|ncaa|rugby|nrl|league|cricket|football|soccer|tennis|f1|motogp|golf|ufc|boxing|ppv|dazn|espn|sky sport|stan sport|premier sport|bein|tnt sport|fox sport|main event|kayo)'
        )
    `;
    const candidates:any[]=[];
    for(const r of rows){
      const name=String(r.name||"");if(!name||PLACEHOLDER.test(name))continue;
      const fixtureName=FIXTURE.test(name);
      const eventish=fixtureName||/\bLIVE\b|\bNEXT\b|\bPPV\b|\bEVENT\b/i.test(name);
      const h=homeAliases.length>0&&matchesAny(name,homeAliases,fixtureName),a=awayAliases.length>0&&matchesAny(name,awayAliases,fixtureName);
      const both=h&&a,one=h||a,overlap=titleOverlap(name,title),net=sportNetworkScore(name,sport,league,broadcasters);
      const genericLeagueWords=new Set(["WORLD","CUP","LEAGUE","CHAMPIONSHIP","CHAMPIONS","MEN","WOMEN"]);
      const leagueHit=words(league).filter(x=>x.length>=3&&!genericLeagueWords.has(x)).some(x=>hasPhrase(name,x));
      const foreignPrefix=/^(DE|IT|FR|ES|PT|NL|PL|RU|TR|AR|LAT|AF)\s*\|/i.test(name.trim());
      if(foreignPrefix&&!both)continue;
      const marketBoost=/^(NZ)\s*\|/i.test(name.trim())?90:/^(AU|UK|GB|US|USA|IE|CA)\s*\|/i.test(name.trim())?45:0;
      const qualityBoost=/\b(4K|UHD)\b/i.test(name)?35:/\bFHD\b/i.test(name)?25:/\bHD\b/i.test(name)?15:/\bRAW\b/i.test(name)?20:0;
      if(/\bTEAM\b/i.test(name)&&!one)continue;
      const teamsKnown=homeAliases.length>0&&awayAliases.length>0;
      let confidence:"definite"|"likely"|"maybe"|null=null,score=0;
      if(both||(!teamsKnown&&eventish&&overlap>=0.60)){confidence="definite";score=1000+(both?250:0)+Math.round(overlap*100)+(/\bLIVE\b/i.test(name)?70:/\bNEXT\b/i.test(name)?45:0);}
      else if((one&&(leagueHit||net>=90||/TEAM|PASS|PACKAGE|PPV|EVENT/i.test(name)))||net>=200||(eventish&&overlap>=0.45)){confidence="likely";score=650+(one?120:0)+net+Math.round(overlap*80);}
      else if(net>=90||leagueHit){confidence="maybe";score=250+net+(leagueHit?50:0);}
      if(!confidence)continue;
      // A named fixture involving only one target team is a different game, never a fallback.
      if(fixtureName&&teamsKnown&&!both)continue;
      score+=marketBoost+qualityBoost;
      candidates.push({providerItemId:Number(r.provider_item_id),categoryId:String(r.category_id),name,logoUrl:r.artwork_url||null,epgId:r.epg_channel_id||null,confidence,score});
    }
    candidates.sort((x,y)=>{
      const tier:Record<string,number>={definite:0,likely:1,maybe:2};
      return tier[x.confidence]-tier[y.confidence]||y.score-x.score||x.name.localeCompare(y.name);
    });
    const seen=new Set<number>();
    const unique=candidates.filter(x=>!seen.has(x.providerItemId)&&(seen.add(x.providerItemId),true));
    return Response.json({event:{sport,league,title,home,away},candidates:unique,total:unique.length},{headers:{"Cache-Control":"private, max-age=30","Access-Control-Allow-Origin":"*"}});
  }catch(e){console.error("SPORT_CANDIDATES",e);return Response.json({error:"sports_candidates_unavailable"},{status:503});}
});