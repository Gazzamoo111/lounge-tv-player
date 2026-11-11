import { test } from 'node:test';
import assert from 'node:assert/strict';
import { nzSourceForBinding, nzPlaylistLines } from './nz-routing.mjs';
test('three clear NZ feeds resolve to HLS and never a BEST synthetic stream ID', () => {
  for (const id of [900000003,900000004,900000005]) {
    const s = nzSourceForBinding('MJH NZ',id);
    assert.equal(s.format,'m3u8'); assert.equal(s.drm,null);
    assert.match(s.url,/^https:\/\/i\.mjh\.nz\/\.r\/[a-z-]+\.m3u8$/);
    assert.equal(nzPlaylistLines(s).at(-1),s.url);
    assert.ok(!s.url.includes(String(id)));
  }
});
test('TVNZ preserves Widevine metadata for compatible M3U clients', () => {
  for (const id of [900000001,900000002]) {
    const s = nzSourceForBinding('MJH NZ',id);assert.equal(s.drm,'widevine');
    assert.ok(nzPlaylistLines(s).some(x=>x.includes('license_type=com.widevine.alpha')));
    assert.ok(nzPlaylistLines(s).some(x=>x.includes(s.license)));
  }
});
test('other providers, missing IDs and unknown synthetic IDs cannot acquire a NZ route', () => {
  assert.equal(nzSourceForBinding('BEST',900000003),null);
  assert.equal(nzSourceForBinding('MJH NZ',900000006),null);
  assert.equal(nzSourceForBinding('MJH NZ',null),null);
});
