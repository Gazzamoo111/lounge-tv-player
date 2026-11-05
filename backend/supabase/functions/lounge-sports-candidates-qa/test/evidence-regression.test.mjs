import {test} from "node:test";
import assert from "node:assert/strict";
import {rankCandidates} from "../matcher.mjs";

const now=Date.parse("2026-11-05T00:00:00Z");
const base={
  eventId:"basketball/nba#det-phi",sportId:"basketball",competitionId:"basketball/nba",
  scheduledStartUtc:"2026-11-04T23:00:00Z",scheduledEndUtc:"2026-11-05T02:00:00Z",status:"IN",
  title:"Detroit Pistons at Philadelphia 76ers",home:"Detroit Pistons",away:"Philadelphia 76ers",
  homeAliases:["Detroit Pistons","Pistons","DET"],awayAliases:["Philadelphia 76ers","76ers","PHI"],
};
const rows=[
 {provider_item_id:836373,name:"Detroit Pistons vs Philadelphia 76ers @ Nov 4 7:00 PM :NBA 01"},
 {provider_item_id:1955374,name:"LIVE | DET - PISTONS VS PHI - 76ERS | Wed 04 Nov 18:00 EST (US) | US: NBA PASS PPV 2"},
 {provider_item_id:1949811,name:"US (ESPN+ 101) | Basketball: 76ers vs. Pistons"},
 {provider_item_id:1955373,name:"LIVE | BOS - CELTICS VS MIL - BUCKS | US: NBA PASS PPV 3"},
 {provider_item_id:749756,name:"US| NBA TV HD"},
 {provider_item_id:1949790,name:"US ESPN+ | NCAA Football: PHI vs. DET • CMU vs. EMU"},
];

test("simultaneous NBA fixtures cannot exchange streams",()=>{
  const det=rankCandidates(base,rows,12,now).map(x=>x.row.providerItemId);
  assert.deepEqual(det,[1955374,836373,1949811]);
  const bos={...base,eventId:"basketball/nba#bos-mil",title:"Boston Celtics at Milwaukee Bucks",
    home:"Boston Celtics",away:"Milwaukee Bucks",homeAliases:["Boston Celtics","Celtics","BOS"],awayAliases:["Milwaukee Bucks","Bucks","MIL"]};
  const bosIds=rankCandidates(bos,rows,12,now).map(x=>x.row.providerItemId);
  assert.deepEqual(bosIds,[1955373]);
});

test("hundreds of unrelated alternatives cannot be padded into the result",()=>{
  const noise=Array.from({length:600},(_,i)=>({provider_item_id:3_000_000+i,name:`MOVIE CHANNEL ${i}`}));
  const found=rankCandidates(base,[...noise,...rows],12,now);
  assert.equal(found.length,3);
  assert.ok(found.every(x=>/PISTONS|DET|76ERS|PHI/i.test(x.row.name)));
});

test("duplicate provider ids collapse but distinct backup feeds remain",()=>{
  const duplicated=[rows[0],rows[0],rows[1],rows[2]];
  const found=rankCandidates(base,duplicated,12,now);
  assert.equal(found.length,3);
  assert.equal(new Set(found.map(x=>x.row.providerItemId)).size,3);
});
