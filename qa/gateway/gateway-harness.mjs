/** Isolated execution of the complete reviewed edge handler. No socket or real DB/fetch access. */
import { readFileSync, mkdtempSync, copyFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { createHash, webcrypto } from 'node:crypto';
import { stripTypeScriptTypes } from 'node:module';
import { createContext, runInContext } from 'node:vm';
import assert from 'node:assert/strict';
import { nzSourceForBinding, nzPlaylistLines } from './nz-routing.mjs';

const fixture = fileURLToPath(new URL('./fixtures/lounge-core-xtream-v50.ts', import.meta.url));
const pristine = readFileSync(fixture, 'utf8');
export const pristineSha256 = createHash('sha256').update(pristine).digest('hex');
assert.equal(pristineSha256, '815d293aec1fd924efc83e5e8b5ea89b6b06ad292b0023fe4377ec61fb5093cb');

function patchedSource() {
  const dir = mkdtempSync(join(tmpdir(), 'lounge-gateway-contract-'));
  try {
    copyFileSync(fixture, join(dir, 'index.ts'));
    for (const name of ['gateway-nz-routing.patch', 'tennis-allowlist.patch']) {
      execFileSync('patch', ['--batch', '--fuzz=0', '-p1', '-i', fileURLToPath(new URL(name, import.meta.url))],
        { cwd: dir, stdio: 'pipe' });
    }
    return readFileSync(join(dir, 'index.ts'), 'utf8');
  } finally { rmSync(dir, { recursive: true, force: true }); }
}
export const reviewedGatewaySource = patchedSource();
export function fixtureRows() {
  return [
    ...[1, 2, 3, 4, 5].map(n => ({ external_id: 900000000 + n, provider_stream_id: 900000000 + n,
      provider_name: 'MJH NZ', source_format: n <= 2 ? 'mpd' : 'm3u8', display_name: `QA NZ ${n}`,
      category: 'New Zealand', subcategory: '', canonical_key: `qa-nz-${n}`, epg_id: `qa-nz-${n}`,
      logo_url: '', live_channel_id: `nz-${n}`, sort_order: n })),
    { external_id: 42, provider_stream_id: 71234, provider_name: 'BEST', source_format: 'ts',
      display_name: 'QA non-NZ control', category: 'Entertainment', subcategory: '',
      canonical_key: 'qa-best', epg_id: 'qa-best', logo_url: '', live_channel_id: 'best', sort_order: 6 },
  ];
}

export function gatewayHarness({ rows = fixtureRows(), inventory = [], validAccount = true,
  cachedAccount = false, usePristine = false, activeRelease = true, sourceOverride = null, routeName = 'lounge-core-xtream' } = {}) {
  const calls = { sql: [], fetch: [], authCacheWrites: 0 };
  const sql = async (strings, ...values) => {
    const query = strings.join('?').replace(/\s+/g, ' ').trim();
    calls.sql.push({ query, values });
    if (query.includes('from lounge_delivery.catalogue_releases')) {
      return activeRelease ? [{ id: '00000000-0000-0000-0000-000000000001', release_key: 'qa-local-fixture' }] : [];
    }
    if (query.startsWith('select 1 from lounge_delivery.legacy_xtream_auth_cache')) return cachedAccount ? [{}] : [];
    if (query.startsWith('insert into lounge_delivery.legacy_xtream_auth_cache')) { calls.authCacheWrites++; return []; }
    if (query.includes('from lounge_sync.best_inventory_items')) {
      // Evaluate the actual SQL's predicate against deterministic inventory. Fail closed if the
      // production query drops live/present/ID guards or changes to an unsupported SQL shape.
      assert.match(query, /where kind='live' and is_present=true and provider_item_id=\?/);
      const pattern = query.match(/name ~\* '([^']+)'/)?.[1];
      assert.ok(pattern, 'sports SQL must retain an explicit name predicate');
      const regex = new RegExp(pattern.replaceAll('[:alnum:]', 'A-Za-z0-9'), 'i');
      const categoryIds = values[1];
      assert.ok(Array.isArray(categoryIds));
      return inventory.some(row => row.kind === 'live' && row.is_present === true &&
        row.provider_item_id === values[0] && (categoryIds.includes(row.category_id) || regex.test(row.name))) ? [{}] : [];
    }
    if (query.includes('from lounge_delivery.item_ids i') && query.includes("i.item_kind='live'")) {
      assert.match(query, /l\.admission='keep'/);
      assert.match(query, /b\.source_role='primary'/);
      if (!usePristine) assert.match(query, /join public\.providers p on p\.id=b\.provider_id/);
      return rows.filter(row => row.external_id === values[1]).map(row =>
        usePristine ? { ...row, provider_name: undefined } : { ...row });
    }
    if (query.includes('from lounge_delivery.live_release_items l') && query.includes('select i.external_id')) {
      assert.match(query, /l\.admission='keep'/);
      assert.match(query, /b\.source_role='primary'/);
      if (!usePristine) assert.match(query, /join public\.providers p on p\.id=b\.provider_id/);
      return rows.map(row => usePristine ? { ...row, provider_name: undefined } : { ...row });
    }
    if (query.includes('from lounge_delivery.live_release_items l') && query.includes('group by l.category,l.subcategory')) {
      return [...new Map(rows.map(row => [row.category + '/' + row.subcategory,
        { category: row.category, subcategory: row.subcategory }])).values()];
    }
    throw new Error(`Unexpected SQL in isolated gateway contract: ${query}`);
  };
  let handler;
  const source = (sourceOverride ?? (usePristine ? pristine : reviewedGatewaySource))
    .replace('import postgres from "npm:postgres@3.4.7";', '')
    .replace('import { nzSourceForBinding, nzPlaylistLines } from "./nz-routing.mjs";', '');
  assert.ok(!/^import /m.test(source), 'only the two audited imports are substituted');
  const env = {
    SUPABASE_DB_URL: 'postgres://qa:fixture@db.qa.invalid:5432/qa',
    SUPABASE_URL: 'https://qa-gateway.invalid', LOUNGE_BEST_BASE: 'https://qa-best.invalid',
  };
  const context = createContext({
    Deno: { env: { get: name => env[name] }, serve: callback => { handler = callback; } },
    postgres: () => sql, nzSourceForBinding, nzPlaylistLines,
    fetch: async input => {
      const url = new URL(input);
      calls.fetch.push(url.toString());
      assert.equal(url.origin, 'https://qa-best.invalid', 'all outbound network is stubbed');
      assert.equal(url.pathname, '/player_api.php');
      assert.equal(url.searchParams.get('username'), 'qa-user');
      assert.equal(url.searchParams.get('password'), 'qa-pass');
      return new Response(JSON.stringify({ user_info: { auth: validAccount ? 1 : 0,
        status: validAccount ? 'Active' : 'Disabled' } }), { headers: { 'Content-Type': 'application/json' } });
    },
    URL, Request, Response, Headers, AbortSignal, TextEncoder, TextDecoder, Blob,
    crypto: webcrypto, btoa, atob, structuredClone, setTimeout, clearTimeout, console,
  });
  runInContext(stripTypeScriptTypes(source, { mode: 'transform', sourceUrl: 'isolated-gateway-v50.ts' }), context);
  assert.equal(typeof handler, 'function', 'the actual Deno.serve handler must be captured');
  return {
    calls,
    request: (path, init) => handler(new Request(`https://qa-gateway.invalid/functions/v1/${routeName}${path}`, init)),
  };
}
