import test from "node:test";
import assert from "node:assert/strict";
import { verifiedEventArtwork, verifiedEventAssets } from "../../../backend/supabase/functions/lounge-hero-artwork-qa/matcher.mjs";

const fixture = {
 key:"basketball/nba:miami-gsw", title:"Miami Heat at Golden State Warriors",
 sport:"basketball", startUtcMs:Date.parse("2026-11-07T03:00:00Z"),
 teams:[{name:"Miami Heat",shortName:"Heat"},{name:"Golden State Warriors",shortName:"Warriors"}]
};
const valid = {
 dateEvent:"2026-11-07", strSport:"Basketball", strEvent:"Golden State Warriors vs Miami Heat",
 strHomeTeam:"Golden State Warriors", strAwayTeam:"Miami Heat",
 strThumb:"https://www.thesportsdb.com/images/media/event/thumb/verified-event.jpg"
};
test("accepts dated widescreen event art for both participants", () => {
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
test("rejects generic team art and skinny event banners", () => {
 assert.equal(verifiedEventArtwork(fixture,{...valid,strThumb:"https://www.thesportsdb.com/images/media/team/banner/art.jpg"}),null);
 assert.equal(verifiedEventArtwork(fixture,{...valid,strThumb:null,strBanner:"https://www.thesportsdb.com/images/media/event/banner/skinny.jpg"}),null);
});
test("rejects unrelated image domains", () => {
 assert.equal(verifiedEventArtwork(fixture,{...valid,strThumb:"https://images.pexels.com/photos/123/photo.jpg"}),null);
});
test("allows a precisely dated and named motorsport event", () => {
 const gp={ key:"racing/f1:saopaulo", title:"MSC Cruises São Paulo Grand Prix",
  sport:"racing",startUtcMs:Date.parse("2026-11-07T18:00:00Z"),teams:[]};
 assert.ok(verifiedEventArtwork(gp,{dateEvent:"2026-11-07",strSport:"Motorsport",
  strEvent:"São Paulo Grand Prix",strThumb:valid.strThumb}));
 assert.equal(verifiedEventArtwork(gp,{dateEvent:"2026-11-07",strSport:"Motorsport",
  strEvent:"Australian Grand Prix",strThumb:valid.strThumb}),null);
});
test("does not confuse rugby league with union", () => {
 const rl={...fixture,sport:"rugby-league"};
 assert.equal(verifiedEventArtwork(rl,{...valid,strSport:"Rugby"}),null);
});
test("resolves exact rugby league international badges and league badge without preview downscaling", () => {
 const rl={
  key:"rugby-league/3:nz-samoa", title:"New Zealand Kiwis vs Samoa",
  sport:"rugby-league", startUtcMs:Date.parse("2026-11-08T03:00:00Z"),
  teams:[{name:"New Zealand Kiwis",shortName:"New Zealand"},{name:"Samoa",shortName:"Samoa"}]
 };
 const item={
  dateEvent:"2026-11-08", strSport:"Rugby League",
  strEvent:"New Zealand vs Samoa", strHomeTeam:"New Zealand", strAwayTeam:"Samoa",
  strHomeTeamBadge:"https://r2.thesportsdb.com/images/media/team/badge/kiwis.png/small",
  strAwayTeamBadge:"https://r2.thesportsdb.com/images/media/team/badge/samoa.png",
  strLeagueBadge:"https://r2.thesportsdb.com/images/media/league/badge/pacific.png/medium"
 };
 const assets=verifiedEventAssets(rl,item);
 assert.ok(assets);
 assert.deepEqual(assets.teamLogos,[
  {name:"New Zealand Kiwis",url:"https://r2.thesportsdb.com/images/media/team/badge/kiwis.png"},
  {name:"Samoa",url:"https://r2.thesportsdb.com/images/media/team/badge/samoa.png"}
 ]);
 assert.equal(assets.competitionLogoUrl,"https://r2.thesportsdb.com/images/media/league/badge/pacific.png");
});
test("never accepts a team badge from an untrusted host", () => {
 const item={...valid,
  strHomeTeamBadge:"https://example.org/miami.png",
  strAwayTeamBadge:"https://r2.thesportsdb.com/images/media/team/badge/warriors.png"
 };
 const assets=verifiedEventAssets(fixture,item);
 assert.ok(assets);
 assert.deepEqual(assets.teamLogos,[{
  name:"Golden State Warriors",
  url:"https://r2.thesportsdb.com/images/media/team/badge/warriors.png"
 }]);
});
