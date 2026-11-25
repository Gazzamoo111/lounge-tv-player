-- Restore previous production function definition if needed.
CREATE OR REPLACE FUNCTION lounge_core.refresh_best_vod_source_presence()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_provider_id uuid;
  v_missing integer := 0;
  v_restored_movies integer := 0;
  v_restored_series integer := 0;
begin
  select id into v_provider_id from public.providers where upper(name)='BEST' limit 1;
  if v_provider_id is null then raise exception 'BEST provider not found'; end if;

  update lounge_core.sources s
  set source_name=i.name,
      file_ext=coalesce(i.file_ext,s.file_ext),
      last_seen_at=i.last_seen_at,
      updated_at=now()
  from lounge_sync.best_inventory_items i
  where s.provider_id=v_provider_id
    and s.provider_item_id=i.provider_item_id
    and ((s.provider_kind='movies' and i.kind='movies') or (s.provider_kind='series' and i.kind='series'))
    and i.is_present;

  with gone as (
    update lounge_core.sources s
    set status='missing',
        source_role='backup',
        runtime_health='dead',
        runtime_verified=false,
        updated_at=now()
    where s.provider_id=v_provider_id
      and s.provider_kind in ('movies','series')
      and s.status in ('verified','candidate','degraded')
      and not exists(
        select 1
        from lounge_sync.best_inventory_items i
        where i.is_present
          and i.provider_item_id=s.provider_item_id
          and i.kind=case when s.provider_kind='movies' then 'movies' else 'series' end
      )
    returning 1
  )
  select count(*) into v_missing from gone;

  with restored as (
    update lounge_core.sources s
    set status='candidate',
        source_role='candidate',
        runtime_health='unknown',
        runtime_verified=false,
        updated_at=now()
    where s.provider_id=v_provider_id
      and s.provider_kind='movies'
      and s.status='missing'
      and exists(
        select 1 from lounge_sync.best_inventory_items i
        where i.kind='movies' and i.is_present and i.provider_item_id=s.provider_item_id
      )
    returning s.id
  ), queued as (
    insert into lounge_core.source_probe_jobs(source_id,status,next_attempt_at,updated_at)
    select id,'queued',now(),now() from restored
    on conflict(source_id) do update set
      status=case when lounge_core.source_probe_jobs.status='processing' then 'processing' else 'queued' end,
      next_attempt_at=case when lounge_core.source_probe_jobs.status='processing' then lounge_core.source_probe_jobs.next_attempt_at else now() end,
      completed_at=case when lounge_core.source_probe_jobs.status='processing' then lounge_core.source_probe_jobs.completed_at else null end,
      updated_at=now()
    returning 1
  )
  select count(*) into v_restored_movies from queued;

  with restored as (
    update lounge_core.sources s
    set status='candidate',
        source_role='candidate',
        runtime_health='unknown',
        runtime_verified=false,
        updated_at=now()
    where s.provider_id=v_provider_id
      and s.provider_kind='series'
      and s.status='missing'
      and exists(
        select 1 from lounge_sync.best_inventory_items i
        where i.kind='series' and i.is_present and i.provider_item_id=s.provider_item_id
      )
    returning s.id
  ), queued as (
    insert into lounge_core.series_probe_jobs(source_id,status,next_attempt_at,priority,updated_at)
    select id,'queued',now(),-100,now() from restored
    on conflict(source_id) do update set
      status=case when lounge_core.series_probe_jobs.status='processing' then 'processing' else 'queued' end,
      next_attempt_at=case when lounge_core.series_probe_jobs.status='processing' then lounge_core.series_probe_jobs.next_attempt_at else now() end,
      completed_at=case when lounge_core.series_probe_jobs.status='processing' then lounge_core.series_probe_jobs.completed_at else null end,
      priority=least(lounge_core.series_probe_jobs.priority,-100),
      updated_at=now()
    returning 1
  )
  select count(*) into v_restored_series from queued;

  return jsonb_build_object(
    'marked_missing',v_missing,
    'restored_movies',v_restored_movies,
    'restored_series',v_restored_series,
    'refreshed_at',now()
  );
end;
$function$

