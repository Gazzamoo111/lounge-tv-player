create table if not exists lounge_delivery.shared_epg_channels (
 epg_id text primary key,
 xmltv text not null,
 listings jsonb not null check(jsonb_typeof(listings)='array'),
 expires_at timestamptz not null,
 programme_count integer not null check(programme_count>0),
 source_revision text not null,
 synced_at timestamptz not null default now()
);
alter table lounge_delivery.shared_epg_channels enable row level security;
revoke all on lounge_delivery.shared_epg_channels from public,anon,authenticated;
create table if not exists lounge_delivery.shared_epg_sync_state (
 singleton boolean primary key default true check(singleton),
 revision text not null default '',
 next_shard integer not null default 0 check(next_shard between 0 and 15),
 last_success_at timestamptz,
 updated_at timestamptz not null default now()
);
alter table lounge_delivery.shared_epg_sync_state enable row level security;
revoke all on lounge_delivery.shared_epg_sync_state from public,anon,authenticated;
insert into lounge_delivery.shared_epg_sync_state(singleton)values(true) on conflict do nothing;
-- Run after deploying lounge-shared-epg-sync. The token stays in Vault.
select cron.schedule('lounge-shared-epg-database-sync','*/5 * * * *',$job$
 select net.http_post(
  url:='https://liqocisegeqzbyuqloql.supabase.co/functions/v1/lounge-shared-epg-sync',
  headers:=jsonb_build_object('Content-Type','application/json','x-lounge-core-token',
   (select decrypted_secret from vault.decrypted_secrets where name='lounge_core_worker_token' limit 1)),
  body:='{}'::jsonb,timeout_milliseconds:=120000);
$job$);
