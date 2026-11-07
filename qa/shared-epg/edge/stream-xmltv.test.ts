import { test } from "node:test";
import assert from "node:assert/strict";
import { appendXmltv } from "./stream-xmltv.ts";
const enc = new TextEncoder();
function input(s: string, size: number) {
  const data=enc.encode(s);let i=0;
  return new ReadableStream<Uint8Array>({pull(c){if(i===data.length)return c.close();c.enqueue(data.slice(i,i+size));i=Math.min(data.length,i+size);}});
}
test("split UTF-8 and closing tags retain every original programme and add only the supplement",async()=>{
 const xml='<tv><channel id="one"><display-name>Māori</display-name></channel><programme start="20261107000000 +0000" stop="20261107010000 +0000" channel="one"><title>Whānau</title></programme></tv>';
 for(const size of [1,7,33,512,65536]){
  let index='';const out=await new Response(appendXmltv(input(xml,size),async p=>{index=p;return '<programme channel="two"><title>Extra</title></programme>'})).text();
  assert.ok(index.includes('<channel id="one"/>'));assert.ok(index.includes('<programme channel="one"/>'));assert.equal(out,xml.replace('</tv>','')+'<programme channel="two"><title>Extra</title></programme></tv>\n');
 }
});
test("a large guide emits data before EOF and retains a bounded ID-only supplement index",async()=>{
 const p='<programme channel="one"><title>Show</title><desc>'+('x'.repeat(2000))+'</desc></programme>';
 const xml='<tv><channel id="one"/>'+p.repeat(12000)+'</tv>';let indexLength=0;
 const reader=appendXmltv(input(xml,65536),async p=>{indexLength=p.length;return ''}).getReader();let n=0;let chunks=0;
 for(;;){const r=await reader.read();if(r.done)break;n+=r.value.length;chunks++;}
 assert.equal(n,enc.encode(xml).length+1);assert.ok(chunks>100);assert.ok(indexLength<100);
});
test("truncated guides fail instead of committing a partial cache",async()=>{
 await assert.rejects(new Response(appendXmltv(input('<tv><programme channel="one">',3),async()=>'' )).text(),/Incomplete upstream/);
});
test("known existing programme IDs are indexed even with split attribute boundaries",async()=>{
 let index='';await new Response(appendXmltv(input('<tv><channel id="A&amp;B"/><programme start="x" channel="A&amp;B"><title>x</title></programme></tv>',2),async p=>{index=p;return ''})).text();
 assert.ok(index.includes('<programme channel="A&amp;B"/>'));
});
