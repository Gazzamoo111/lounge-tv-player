import test from "node:test";
import assert from "node:assert/strict";
import { verifiedEventArtwork } from "../../../backend/supabase/functions/lounge-hero-artwork-qa/matcher.mjs";

const fixture = {
 key:"basketball/nba:miami-gsw", title:"Miami Heat at Golden State Warriors",
 sport:"basketball", startUtcMs:Date.parse("2026-11-07T03:00:00Z"),
 teams:[{name:"Miami Heat",shortName:"Heat"},{name:"Golden State Warriors",shortName:"Warriors"}]
};
const valid = {
 dateEvent:"2026-11-07", strSport:"Basketball", strEvent:"Golden State Warriors vs Miami Heat",
 strHomeTeam:"Golden State Warriors", strAwayTeam:"Miami Heat",
 strBanner:"https://www.thesportsdb.com/images/media/event/banner/verified-event.jpg"
};
test("accepts exact dated banner for both participants", () => {
 assert.equal(verifiedEventArtwork(fixture,valid)?.confidence,"exact");
});
test("accepts swapped home and away order", () => {
 assert.ok(verifiedEventArtwork(fixture,{...valid,strHomeTeam:"Miami Heat",strAwayTeam:"Golden State Warriors"}));
});
test("rejects a different opponent", () => {
 assert.equal(verifiedEventArtwork(fixture,{...valid,strAwayTeam:"Boston Celtics",strEvent:"Golden State Warriors vs Boston Celtics"}),null);
});
test("rejects stale or wrong dated events", () => {
 assert.equal(verifiedEventArtwork(fixture,{...valid,dateEvent:"2026-11-20"}),null);
});
test("rejects different sports even with same names", () => {
 assert.equal(verifiedEventArtwork(fixture,{...valid,strSport:"Rugby League"}),null);
});
test("rejects generic team art and low-resolution thumbnails", () => {
 assert.equal(verifiedEventArtwork(fixture,{...valid,strBanner:"https://www.thesportsdb.com/images/media/team/banner/art.jpg"}),null);
 assert.equal(verifiedEventArtwork(fixture,{...valid,strBanner:null,strThumb:"https://www.thesportsdb.com/images/media/event/thumb/small.jpg"}),null);
});
test("rejects unrelated image domains", () => {
 assert.equal(verifiedEventArtwork(fixture,{...valid,strBanner:"https://images.pexels.com/photos/123/photo.jpg"}),null);
});
test("allows a precisely dated and named motorsport event", () => {
 const gp={ key:"racing/f1:saopaulo", title:"MSC Cruises São Paulo Grand Prix",
  sport:"racing",startUtcMs:Date.parse("2026-11-07T18:00:00Z"),teams:[]};
 assert.ok(verifiedEventArtwork(gp,{dateEvent:"2026-11-07",strSport:"Motorsport",
  strEvent:"São Paulo Grand Prix",strBanner:valid.strBanner}));
 assert.equal(verifiedEventArtwork(gp,{dateEvent:"2026-11-07",strSport:"Motorsport",
  strEvent:"Australian Grand Prix",strBanner:valid.strBanner}),null);
});
test("does not confuse rugby league with union", () => {
 const rl={...fixture,sport:"rugby-league"};
 assert.equal(verifiedEventArtwork(rl,{...valid,strSport:"Rugby"}),null);
});