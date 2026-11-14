/** Prepare an isolated, read-only-auth QA clone. This script never deploys or contacts a service. */
import { mkdirSync, writeFileSync, copyFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { reviewedGatewaySource, pristineSha256 } from './gateway-harness.mjs';

export function qaGatewaySource(name = 'lounge-core-xtream-qa') {
  if (!/^lounge-core-xtream-qa(?:-[a-z0-9-]+)?$/.test(name)) throw new Error('Use a distinct lounge-core-xtream-qa[-suffix] name');
  let source = reviewedGatewaySource;
  const start = source.indexOf('async function rememberLegacyAccount(');
  const end = source.indexOf('\nasync function catalogueAccountState(', start);
  if (start < 0 || end < 0) throw new Error('Reviewed auth-cache writer not found');
  source = source.slice(0, start) + 'async function rememberLegacyAccount(_u:string,_p:string){\n  // Isolated QA must not write production auth-cache records.\n  return;\n}\n' + source.slice(end);
  source = source.replaceAll('lounge-core-xtream', name);
  const marker = 'Deno.serve(async(req)=>{';
  if (source.split(marker).length !== 2 || !source.trimEnd().endsWith('});')) throw new Error('Reviewed serve registration changed');
  source = source.replace(marker, 'const qaHandler=async(req:Request)=>{').trimEnd().slice(0, -3) + '};\n';
  source += `
// QA isolation wrapper: only live catalogue/stream and sport contracts, visibly marked responses.
Deno.serve(async(req)=>{
  const url=new URL(req.url);
  const marker="/${name}";
  const rest=url.pathname.includes(marker)?url.pathname.split(marker)[1]||"/":url.pathname;
  const endpoint=rest.split("/").filter(Boolean)[0]||"player_api.php";
  const action=url.searchParams.get("action");
  const allowedMethod=req.method==="GET"||req.method==="OPTIONS";
  const allowedEndpoint=["live","sport","player_api.php","get.php"].includes(endpoint);
  const allowedAction=endpoint!=="player_api.php"||!action||["get_live_streams","get_live_categories"].includes(action);
  let response:Response;
  if(!allowedMethod||!allowedEndpoint||!allowedAction) response=json({error:"qa_endpoint_not_enabled"},404);
  else {
    // Keep emitted catalogue URLs on this QA function even if a forwarded-host hint is supplied.
    url.searchParams.delete("_lounge_host");
    response=await qaHandler(new Request(url,req));
  }
  const headers=new Headers(response.headers);
  headers.set("X-Lounge-QA","staged-v50-nz-tennis");
  return new Response(response.body,{status:response.status,statusText:response.statusText,headers});
});
`;
  return source;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const output = process.argv[2];
  if (!output) throw new Error('Usage: node qa/gateway/prepare-qa-gateway.mjs OUTPUT_DIR [lounge-core-xtream-qa-suffix]');
  const name = process.argv[3] || 'lounge-core-xtream-qa';
  const source = qaGatewaySource(name);
  mkdirSync(output, { recursive: true });
  writeFileSync(resolve(output, 'index.ts'), source);
  copyFileSync(fileURLToPath(new URL('./nz-routing.mjs', import.meta.url)), resolve(output, 'nz-routing.mjs'));
  const manifest = { functionName: name, sourceBaselineSha256: pristineSha256,
    qaSourceSha256: createHash('sha256').update(source).digest('hex'),
    authCacheWrites: false, permittedRoutes: ['live', 'sport', 'player_api.php (account/live streams/live categories)', 'get.php'],
    productionDeployment: false };
  writeFileSync(resolve(output, 'qa-manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log(JSON.stringify(manifest));
}
