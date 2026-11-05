import {test} from "node:test";
import assert from "node:assert/strict";
import {classify,rankCandidates,discoveryCandidate,sportFallbackCandidates} from "../matcher.mjs";

const nba={
  eventId:"basketball/nba#det-phi",
  sportId:"basketball",
  competitionId:"basketball/nba",
  scheduledStartUtc:"2026-11-04T23:00:00Z",
  scheduledEndUtc:"2026-11-05T02:00:00Z",
  status:"IN",
  title:"Detroit Pistons at Philadelphia 76ers",
  home:"Detroit Pistons",
  away:"Philadelphia 76ers",
  homeAliases:["Detroit Pistons","Pistons","DET","Detroit"],
  awayAliases:["Philadelphia 76ers","76ers","PHI","Philadelphia"],
};
const now=Date.parse("2026-11-05T00:00:00Z");

test("exact NBA fixture feeds match while another NBA game does not",()=>{
  const exact=[
    {provider_item_id:836373,name:"Detroit Pistons vs Philadelphia 76ers @ Nov 4 7:00 PM :NBA 01"},
    {provider_item_id:1955374,name:"LIVE | DET - PISTONS VS PHI - 76ERS | Wed 04 Nov 18:00 EST (US) | US: NBA PASS PPV 2"},
    {provider_item_id:1949811,name:"US (ESPN+ 101) | Basketball: 76ers vs. Pistons (76ers vs. Pistons)"},
  ];
  for(const row of exact)assert.equal(classify(nba,row,now)?.tier,"plausible");
  assert.equal(classify(nba,{provider_item_id:1955373,name:"LIVE | BOS - CELTICS VS MIL - BUCKS | US: NBA PASS PPV 3"},now),null);
  assert.equal(classify(nba,{provider_item_id:1949790,name:"US ESPN+ | NCAA Football: PHI vs. DET • CMU vs. EMU"},now),null);
});

test("generic NBA and single-team channels never become fixture recommendations",()=>{
  for(const row of [
    {provider_item_id:749756,name:"US| NBA TV HD"},
    {provider_item_id:836349,name:"NBA TEAM| DETROIT PISTONS"},
    {provider_item_id:836335,name:"NBA TEAM| PHILADELPHIA 76ERS"},
    {provider_item_id:1040604,name:"NBA REPLAY 1 HD"},
  ])assert.equal(classify(nba,row,now),null);
});

test("all exact provider variants are retained instead of one logical-service representative",()=>{
  const rows=[
    {provider_item_id:836373,name:"Detroit Pistons vs Philadelphia 76ers @ Nov 4 7:00 PM :NBA 01"},
    {provider_item_id:1955374,name:"LIVE | DET - PISTONS VS PHI - 76ERS | US: NBA PASS PPV 2"},
    {provider_item_id:1949811,name:"US ESPN+ | Basketball: 76ers vs. Pistons"},
    {provider_item_id:1949810,name:"US ESPN+ | Basketball: 76ers vs. Pistons ESP"},
  ];
  const found=rankCandidates(nba,rows,12,now);
  assert.equal(found.length,4);
  assert.equal(new Set(found.map(x=>x.row.providerItemId)).size,4);
});

test("provider-title evidence remains unverified and playback stays unclaimed",()=>{
  const row={provider_item_id:1955374,category_id:"2076",name:"LIVE | DET - PISTONS VS PHI - 76ERS | US: NBA PASS PPV 2"};
  const decision=classify(nba,row,now);
  const candidate=discoveryCandidate(row,decision,now);
  assert.equal(candidate.identityReviewed,true);
  assert.equal(candidate.tier,"plausible");
  assert.equal(candidate.evidence[0].type,"name-hint");
  assert.equal(candidate.evidence[0].eventId,nba.eventId);
  assert.equal(candidate.playbackObservation.status,"unknown");
});

test("shared city or nickname text cannot match the wrong sport or team",()=>{
  const lakersKings={...nba,eventId:"basketball/nba#lal-sac",title:"Los Angeles Lakers at Sacramento Kings",
    home:"Sacramento Kings",away:"Los Angeles Lakers",homeAliases:["Sacramento Kings","Kings","SAC"],
    awayAliases:["Los Angeles Lakers","Lakers","LAL"]};
  assert.equal(classify(lakersKings,{provider_item_id:836392,name:"NHL TEAM| LOS ANGELES KINGS"},now),null);
  assert.equal(classify(lakersKings,{provider_item_id:999001,name:"NBA | LA CLIPPERS VS SACRAMENTO KINGS"},now),null);
  assert.equal(classify(lakersKings,{provider_item_id:1955363,name:"NEXT | SAC - KINGS VS LAL - LAKERS | NBA PASS PPV 13"},now)?.tier,"plausible");
});

test("ended, replay and AU ESPN1 rows stay excluded",()=>{
  assert.equal(classify({...nba,status:"POST"},{provider_item_id:1955374,name:"LIVE | DET - PISTONS VS PHI - 76ERS"},now),null);
  assert.equal(classify(nba,{provider_item_id:1,name:"REPLAY | DET PISTONS VS PHI 76ERS"},now),null);
  assert.equal(classify(nba,{provider_item_id:646974,name:"AU| ESPN 1 HD | DET PISTONS VS PHI 76ERS"},now),null);
});

test("dated event hints from an old fixture cannot be recycled for a current cricket match",()=>{
 const cricket={...nba,sportId:"cricket",competitionId:"cricket/worldcup",
  scheduledStartUtc:"2026-11-05T00:00:00Z",scheduledEndUtc:"2026-11-05T05:00:00Z",
  home:"England",away:"India",homeAliases:["England"],awayAliases:["India"]};
 assert.equal(classify(cricket,{provider_item_id:1,name:"FLSP | cricket: England vs India (2026-09-04 03:10:00)"},now),null);
 assert.equal(classify(cricket,{provider_item_id:2,name:"FLSP | cricket: England vs India (2026-11-05 03:10:00)"},now)?.tier,"plausible");
});


test("WTA finds tennis-specific channel choices from otherwise hidden BEST categories",()=>{
 const wta={...nba,eventId:"tennis/wta#12345",sportId:"tennis",competitionId:"tennis/wta",
  title:"WTA singles match",home:"Player A",away:"Player B",
  scheduledStartUtc:"2026-11-05T00:20:00Z",scheduledEndUtc:"2026-11-05T03:00:00Z"};
 const rows=[
  {provider_item_id:1458813,name:"UK| SKY SPORTS TENNIS HEVC FHD"},
  {provider_item_id:1458814,name:"UK| SKY SPORTS TENNIS HEVC HD"},
  {provider_item_id:2151527,name:"TV| TENNIS CHANNEL RAW"},
  {provider_item_id:1955374,name:"LIVE | NBA PISTONS VS 76ERS"},
  {provider_item_id:2062108,name:"Live | Centre Court-Tennis: different players vs someone"},
 ];
 const choices=sportFallbackCandidates(wta,rows,now,6);
 assert.deepEqual(choices.map(c=>c.providerItemId),[1458813,2151527]);
 assert.ok(choices.every(c=>c.reason==="sport-specific-unverified"&&c.evidence[0].type==="sport-option"));
 assert.ok(choices.every(c=>c.playbackObservation.status==="unknown"));
 assert.equal(choices.length,2,"Unrelated fixtures or duplicate quality variants cannot pad the results");
});

test("NFL and NBA show only sport-specific alternatives, never other matches",()=>{
 const nfl={...nba,eventId:"football/nfl#401873037",sportId:"football",competitionId:"football/nfl",
  title:"Jacksonville Jaguars at Baltimore Ravens",home:"Baltimore Ravens",away:"Jacksonville Jaguars",
  scheduledStartUtc:"2026-11-05T01:15:00Z",scheduledEndUtc:"2026-11-05T05:00:00Z"};
 const nflChoices=sportFallbackCandidates(nfl,[
  {provider_item_id:45526,name:"US| NFL NETWORK HD"},
  {provider_item_id:45524,name:"US| NFL REDZONE HD"},
  {provider_item_id:836261,name:"NFL TEAMS| CBS RAVENS BALTIMORE MD"},
  {provider_item_id:836342,name:"NBA TEAM| MIAMI HEAT"},
 ],now,6);
 assert.deepEqual(nflChoices.map(c=>c.providerItemId),[45526,836261]);
 const nbaChoices=sportFallbackCandidates(nba,[
  {provider_item_id:836345,name:"NBA TEAM| LOS ANGELES CLIPPERS"},
  {provider_item_id:836342,name:"NBA TEAM| MIAMI HEAT"},
  {provider_item_id:45526,name:"US| NFL NETWORK HD"},
 ],now,6);
 assert.deepEqual(nbaChoices.map(c=>c.providerItemId),[836342,836345]);
});
