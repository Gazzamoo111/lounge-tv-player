import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
test('actual staged sports SQL predicate accepts tennis without promoting unrelated channels', () => {
 const patch = readFileSync(new URL('./tennis-allowlist.patch',import.meta.url),'utf8');
 const added = patch.split('\n').find(x=>x.startsWith('+') && x.includes('name ~*'));
 const regex = new RegExp(added.match(/name ~\* '([^']+)'/)[1],'i');
 assert.ok(regex.test(':Tennis 30'));assert.ok(regex.test('TENNIS CHANNEL PLUS'));
 assert.ok(regex.test('Sky Sport 1'));assert.ok(!regex.test('Cooking channel'));
 assert.ok(patch.includes("kind='live' and is_present=true"));
});
