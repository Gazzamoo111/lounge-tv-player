import test from 'node:test';import assert from 'node:assert/strict';import{readFileSync}from'node:fs';
const c=JSON.parse(readFileSync(new URL('../src/contract.json',import.meta.url)));
test('exact Android rail order, including Sport Lounge, Guide, Downloads and More',()=>assert.deepEqual(c.rail,['SEARCH','HOME','LIVE_TV','SPORTS','EPG','MOVIES','SERIES','DOWNLOADS','MORE']));
test('all twelve settings groups are retained in source order',()=>assert.deepEqual(c.settingsGroups.map(g=>g.id),['QUICK','PROFILE','SOURCES','APPEARANCE','LAYOUT','CONTENT','PLAYER','PICTURE','SOUND','LIVE','WATCHING','APP']));
test('sport settings, home settings and all Sports routes are explicitly tracked',()=>{for(const id of ['SportLoungeScreen','SportsSettingsScreen','SportsMatchScreen','SportsLeagueScreen','HomeSettingsScreen','SettingsScreen','MoreScreen','DownloadsScreen','MultiviewScreen','EpgScreen'])assert(c.screens.some(s=>s.id===id),id);});
