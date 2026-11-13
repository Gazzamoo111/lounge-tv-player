import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,mkdtemp,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';
const hash=x=>createHash('sha256').update(x).digest('hex');
const revision='a'.repeat(40),commit='b'.repeat(40);
const template=await readFile(new URL('../../backend/supabase/functions/lounge-shared-epg-sync/index.ts',import.meta.url),'utf8');
async function run({authorised=true,busy=false,cursor=0,corrupt=false,complete=false,unchanged=false}={}){
 const calls=[];let handler;
 const state={revision:cursor||unchanged?revision:'',next_shard:cursor,last_success_at:unchanged?'2026-11-13':null};
 const sql=async(strings,...values)=>{
  const q=strings.join('?');calls.push({q,values});
  if(q.includes('worker_auth'))return authorised?[{}]:[];
  if(q.includes('pg_try_advisory'))return [{locked:!busy}];
  if(q.includes('for update'))return [state];
  if(q.includes('count(*)::int'))return [{channels:160,programmes:160}];
  return [];
 };
 sql.begin=async f=>f(sql);
 sql.json=value=>{assert.ok(Array.isArray(value));return value;};
 const shards=Array.from({length:16},(_,i)=>{
  const channels=Array.from({length:10},(_,j)=>({epg_id:`${i}-${j}.us`,xmltv:'<channel/>',listings:[{}],expires_at:'2026-11-20T00:00:00Z',programme_count:1}));
  const text=JSON.stringify({revision,shard:i,channels});return {text,info:{file:String(i).padStart(2,'0')+'.json',sha256:hash(text),channels:10}};
 });
 const manifest={revision,channels:160,programmes:160,shards:shards.map(s=>s.info)};
 const oldFetch=globalThis.fetch;
 globalThis.fetch=async url=>{
  if(url.includes('api.github.com'))return new Response(JSON.stringify({object:{sha:commit}}));
  assert.ok(url.includes('/'+commit+'/'),'data reads must use immutable commit');
  if(url.endsWith('manifest.json'))return new Response(JSON.stringify(manifest));
  const index=Number(url.match(/\/(\d+)\.json$/)[1]);return new Response(corrupt?shards[index].text+' ':shards[index].text);
 };
 globalThis.__testPostgres=()=>sql;
 globalThis.__testDeno={env:{get:()=> 'postgres://postgres:placeholder@db.liqocisegeqzbyuqloql.supabase.co:5432/postgres'},serve:f=>handler=f};
 const dir=await mkdtemp(join(tmpdir(),'lounge-epg-sync-'));
 try{
  const path=join(dir,'sync.ts');await writeFile(path,template.replace('import postgres from "npm:postgres@3.4.7";','const postgres=globalThis.__testPostgres; const Deno=globalThis.__testDeno;'));
  await import(pathToFileURL(path));
  const response=await handler(new Request('https://local/sync',{method:'POST',headers:{'x-lounge-core-token':'test'}}));
  return {status:response.status,body:await response.json(),calls};
 }finally{globalThis.fetch=oldFetch;delete globalThis.__testPostgres;delete globalThis.__testDeno;await rm(dir,{recursive:true});}
}
test('denies unauthorised writes',async()=>{const r=await run({authorised:false});assert.equal(r.status,401);assert.equal(r.calls.length,1);});
test('busy imports do not advance state',async()=>{const r=await run({busy:true});assert.equal(r.body.busy,true);assert.equal(r.calls.filter(x=>x.q.includes('insert into')).length,0);});
test('imports four verified shards and advances cursor',async()=>{const r=await run();assert.equal(r.body.channels,40);assert.equal(r.body.next_shard,4);assert.equal(r.calls.filter(x=>x.q.includes('delete from')).length,0);});
test('rejects corrupt shards before any write',async()=>{const r=await run({corrupt:true});assert.equal(r.status,502);assert.equal(r.calls.filter(x=>x.q.includes('insert into')).length,0);});
test('only completed imports reconcile obsolete rows',async()=>{const r=await run({cursor:12});assert.equal(r.body.next_shard,0);assert.equal(r.calls.filter(x=>x.q.includes('delete from')).length,1);});
test('completed unchanged exports avoid writes',async()=>{const r=await run({unchanged:true});assert.equal(r.body.unchanged,true);assert.equal(r.calls.filter(x=>x.q.includes('insert into')).length,0);});
