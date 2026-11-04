import { test } from 'node:test';
import assert from 'node:assert/strict';
import { stripTypeScriptTypes } from 'node:module';
import { execFileSync } from 'node:child_process';
import vm from 'node:vm';

// Replays the actual immutable source, not a hand-written approximation of the old matcher.
const original=execFileSync('git',['show','d081d77cbec6f5f498a16c58250d96f440842c90:backend/supabase/functions/lounge-sports-candidates/index.ts'],{encoding:'utf8'});
const pure=stripTypeScriptTypes(original.slice(original.indexOf('const SPORT_CATEGORY_IDS')));
async function runSource(source){
 let handler,n=0;
 vm.runInNewContext(source,{Deno:{serve:h=>handler=h},Response,URL,console,sql:async()=>++n===1?[]:[{provider_item_id:101,category_id:'1308',name:'Arsenal vs Chelsea',artwork_url:null,epg_channel_id:null}]});
 const response=await handler(new Request('https://example.invalid/?sport=soccer&league=Premier%20League&home=Arsenal&away=Chelsea&title=Arsenal%20vs%20Chelsea'));
 return response.json();
}
test('R01/R02 original repository promotes name-only fixture without time',async()=>{
 const result=await runSource(pure);
 assert.equal(result.candidates[0].confidence,'definite','baseline must actually exhibit original defect');
});
test('R03/R07 original UK and football defects are reproducible',()=>{
 const context={};vm.createContext(context);
 vm.runInContext(pure.slice(0,pure.indexOf('Deno.serve'))+';globalThis.probe={uk:broadcasterMatchScore("UK| Sky Sports Premier League","Sky Sports Premier League"),football:sportNetworkScore("US| NFL RedZone","football","Premier League",[])}',context);
 assert.equal(context.probe.uk,0);assert(context.probe.football>0);
});
