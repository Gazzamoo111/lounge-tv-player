import { test } from 'node:test';
import assert from 'node:assert/strict';
import { gatewayHarness, fixtureRows } from './gateway-harness.mjs';
import { nzSourceForBinding } from './nz-routing.mjs';
import { qaGatewaySource } from './prepare-qa-gateway.mjs';
const credentials = 'username=qa-user&password=qa-pass';

test('pristine v50 reproduces the MJH-to-BEST synthetic-ID routing defect', async () => {
  const gateway = gatewayHarness({ usePristine: true });
  const response = await gateway.request('/live/qa-user/qa-pass/900000003.m3u8');
  assert.equal(response.status, 302);
  assert.equal(response.headers.get('location'), 'https://qa-best.invalid/live/qa-user/qa-pass/900000003.m3u8');
});

test('patched actual handler authenticates all three MJH clear-HLS routes without forwarding customer credentials', async () => {
  const gateway = gatewayHarness();
  for (const id of [900000003, 900000004, 900000005]) {
    const response = await gateway.request(`/live/qa-user/qa-pass/${id}.m3u8`);
    assert.equal(response.status, 302);
    assert.equal(response.headers.get('location'), nzSourceForBinding('MJH NZ', id).url);
    assert.ok(!response.headers.get('location').includes('qa-user'));
    assert.ok(!response.headers.get('location').includes(String(id)));
  }
  assert.equal(gateway.calls.fetch.length, 1, 'account authentication is reused, not repeated on each tune');
  assert.equal(gateway.calls.authCacheWrites, 1);
});

test('missing and invalid accounts cannot receive an MJH source redirect', async () => {
  const gateway = gatewayHarness({ validAccount: false });
  const response = await gateway.request('/live/qa-user/qa-pass/900000003.m3u8');
  assert.equal(response.status, 403);
  assert.equal(response.headers.get('location'), null);
  assert.deepEqual(await response.json(), { error: 'not_allowed' });
  assert.equal(gateway.calls.authCacheWrites, 0);
  const missing = await gateway.request('/live///900000003.m3u8');
  assert.equal(missing.status, 403);
});

test('valid legacy-account cache authenticates MJH without a provider round-trip', async () => {
  const gateway = gatewayHarness({ cachedAccount: true, validAccount: false });
  const response = await gateway.request('/live/qa-user/qa-pass/900000003.m3u8');
  assert.equal(response.status, 302);
  assert.equal(response.headers.get('location'), nzSourceForBinding('MJH NZ', 900000003).url);
  assert.equal(gateway.calls.fetch.length, 0);
});

test('actual handler returns honest Widevine-required errors for both TVNZ DASH sources', async () => {
  const gateway = gatewayHarness();
  for (const id of [900000001, 900000002]) {
    const response = await gateway.request(`/live/qa-user/qa-pass/${id}.mpd`);
    assert.equal(response.status, 422);
    assert.equal(response.headers.get('location'), null);
    assert.equal((await response.json()).error, 'drm_client_required');
  }
});

test('BEST control route is identical before and after both staged patches', async () => {
  for (const usePristine of [true, false]) {
    const gateway = gatewayHarness({ usePristine, validAccount: false });
    const response = await gateway.request('/live/qa-user/qa-pass/42.ts');
    assert.equal(response.status, 302);
    assert.equal(response.headers.get('location'), 'https://qa-best.invalid/live/qa-user/qa-pass/71234.ts');
    assert.equal(gateway.calls.fetch.length, 0, 'BEST retains provider-side authentication at redirect destination');
  }
});

test('missing release IDs and unknown providers cannot fall through to BEST', async () => {
  const gateway = gatewayHarness({ rows: [...fixtureRows(),
    { external_id: 43, provider_stream_id: 999, provider_name: 'MJH NZ' },
    { external_id: 44, provider_stream_id: 900000003, provider_name: 'Unknown' }] });
  assert.equal((await gateway.request('/live/qa-user/qa-pass/999.ts')).status, 403);
  assert.equal((await gateway.request('/live/qa-user/qa-pass/43.ts')).status, 502);
  assert.equal((await gateway.request('/live/qa-user/qa-pass/44.ts')).status, 502);
  const inactive = gatewayHarness({ activeRelease: false });
  assert.equal((await inactive.request('/live/qa-user/qa-pass/900000003.ts')).status, 403);
});

test('authenticated Xtream categories and stream output preserve stable released IDs and usable schema', async () => {
  const gateway = gatewayHarness();
  const categoriesResponse = await gateway.request(`/player_api.php?${credentials}&action=get_live_categories`);
  assert.equal(categoriesResponse.status, 200);
  const categories = await categoriesResponse.json();
  assert.equal(categories.length, 2);
  assert.ok(categories.every(row => /^\d+$/.test(row.category_id) && row.category_name));
  const response = await gateway.request(`/player_api.php?${credentials}&action=get_live_streams`);
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /application\/json/);
  const streams = await response.json();
  assert.equal(streams.length, 6);
  assert.deepEqual(streams.map(row => row.stream_id), fixtureRows().map(row => row.external_id));
  assert.ok(streams.every(row => row.stream_type === 'live' && row.epg_channel_id && row.name && row.category_id));
  const categoryId = categories.find(row => row.category_name === 'New Zealand').category_id;
  const filtered = await gateway.request(`/player_api.php?${credentials}&action=get_live_streams&category_id=${categoryId}`);
  assert.deepEqual((await filtered.json()).map(row => row.stream_id), [900000001,900000002,900000003,900000004,900000005]);
});

test('actual M3U output carries clear HLS and TVNZ DRM metadata while keeping BEST control on gateway', async () => {
  const gateway = gatewayHarness();
  const response = await gateway.request(`/get.php?${credentials}&type=m3u_plus`);
  assert.equal(response.status, 200);
  assert.match(response.headers.get('content-type'), /application\/x-mpegURL/i);
  const body = await response.text();
  assert.match(body, /^#EXTM3U x-tvg-url="https:\/\/qa-gateway.invalid\//);
  assert.equal((body.match(/^#EXTINF:/gm) || []).length, 6);
  for (let id = 900000001; id <= 900000005; id++) assert.ok(body.includes(nzSourceForBinding('MJH NZ', id).url));
  assert.equal((body.match(/license_type=com.widevine.alpha/g) || []).length, 2);
  assert.match(body, /license_key=https:\/\/c.mjh.nz\/tvnz-1-wv/);
  assert.match(body, /license_key=https:\/\/c.mjh.nz\/tvnz-2-wv/);
  assert.match(body, /https:\/\/qa-gateway.invalid\/functions\/v1\/lounge-core-xtream\/live\/qa-user\/qa-pass\/42.ts/);
  assert.ok(!body.includes('/live/qa-user/qa-pass/90000000'));
});

test('invalid accounts cannot access Xtream or M3U catalogues', async () => {
  const gateway = gatewayHarness({ validAccount: false });
  const api = await gateway.request(`/player_api.php?${credentials}&action=get_live_streams`);
  assert.equal(api.status, 401);
  assert.equal((await api.json()).user_info.auth, 0);
  assert.equal((await gateway.request(`/get.php?${credentials}`)).status, 403);
  assert.ok(!gateway.calls.sql.some(call => call.query.includes('select i.external_id')));
});

const inventoryRow = (provider_item_id, name, category_id = '2176', is_present = true, kind = 'live') =>
  ({ provider_item_id, name, category_id, is_present, kind });
test('actual /sport route admits named tennis feeds in both omitted categories and preserves other sports', async () => {
  const inventory = [inventoryRow(701, ':Tennis 30'), inventoryRow(702, 'TENNIS CHANNEL PLUS', '2403'),
    inventoryRow(703, 'Sky Sport 1', 'other'), inventoryRow(704, 'Existing sports feed', '2202')];
  const gateway = gatewayHarness({ inventory });
  for (const row of inventory) {
    const response = await gateway.request(`/sport/qa-user/qa-pass/${row.provider_item_id}.ts`);
    assert.equal(response.status, 302);
    assert.equal(response.headers.get('location'), `https://qa-best.invalid/live/qa-user/qa-pass/${row.provider_item_id}.ts`);
  }
});

test('actual tennis eligibility cannot admit unrelated, stale, non-live, missing or invalid inventory rows', async () => {
  const inventory = [inventoryRow(701, 'Cooking channel'), inventoryRow(702, 'Film channel', '2403'),
    inventoryRow(703, ':Tennis 30', '2176', false), inventoryRow(704, 'Tennis', '2176', true, 'movie'),
    inventoryRow(705, 'Tennison Cinema'), inventoryRow(706, 'TableTennis', '2403')];
  const gateway = gatewayHarness({ inventory });
  for (const id of [701,702,703,704,705,706,999,0,-1]) {
    const response = await gateway.request(`/sport/qa-user/qa-pass/${id}.ts`);
    assert.equal(response.status, 403, `inventory row ${id} must be denied`);
    assert.equal(response.headers.get('location'), null);
  }
});

test('sports stream eligibility never bypasses customer authentication', async () => {
  const gateway = gatewayHarness({ validAccount: false, inventory: [inventoryRow(701, ':Tennis 30')] });
  assert.equal((await gateway.request('/sport/qa-user/qa-pass/701.ts')).status, 403);
  assert.ok(!gateway.calls.sql.some(call => call.query.includes('from lounge_sync.best_inventory_items')));
});


test('isolated QA preparation keeps live routes functional, marked and free of auth-cache writes', async () => {
  const name = 'lounge-core-xtream-qa-contract';
  const gateway = gatewayHarness({ sourceOverride: qaGatewaySource(name), routeName: name });
  const live = await gateway.request('/live/qa-user/qa-pass/900000003.m3u8');
  assert.equal(live.status, 302);
  assert.equal(live.headers.get('X-Lounge-QA'), 'staged-v50-nz-tennis');
  assert.equal(live.headers.get('location'), nzSourceForBinding('MJH NZ', 900000003).url);
  const playlist = await gateway.request(`/get.php?${credentials}&_lounge_host=production.invalid`);
  assert.equal(playlist.status, 200);
  const body = await playlist.text();
  assert.ok(body.includes(`/functions/v1/${name}/live/qa-user/qa-pass/42.ts`));
  assert.ok(!body.includes('production.invalid'));
  assert.equal(gateway.calls.authCacheWrites, 0);
  const account = await gateway.request(`/player_api.php?${credentials}`);
  assert.equal(account.status, 200);
  assert.equal((await account.json()).user_info.auth, 1);
  assert.equal(gateway.calls.authCacheWrites, 0);
});

test('isolated QA rejects VOD, EPG, unknown routes, mutation methods and production function name', async () => {
  const name = 'lounge-core-xtream-qa-contract';
  const gateway = gatewayHarness({ sourceOverride: qaGatewaySource(name), routeName: name });
  for (const path of ['/movie/qa-user/qa-pass/1.mp4', '/series/qa-user/qa-pass/1.ts', '/xmltv.php', '/unknown',
    `/player_api.php?${credentials}&action=get_vod_streams`, `/player_api.php?${credentials}&action=get_short_epg`]) {
    const response = await gateway.request(path);
    assert.equal(response.status, 404);
    assert.equal(response.headers.get('X-Lounge-QA'), 'staged-v50-nz-tennis');
    assert.deepEqual(await response.json(), { error: 'qa_endpoint_not_enabled' });
  }
  assert.equal((await gateway.request(`/get.php?${credentials}`, { method: 'POST' })).status, 404);
  assert.equal(gateway.calls.sql.length, 0);
  assert.equal(gateway.calls.fetch.length, 0);
  assert.throws(() => qaGatewaySource('lounge-core-xtream'), /distinct/);
});
