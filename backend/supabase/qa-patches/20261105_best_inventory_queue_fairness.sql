-- REVIEW CANDIDATE ONLY. DO NOT APPLY automatically or deploy to production.
-- Source: read-only pg_get_functiondef() snapshot, 2026-11-05.
-- Root cause: ORDER BY fraction processed starves near-complete live/series.
-- Only the processing-job selection ORDER BY clause is changed.
-- Existing expired source-response guard remains in place.
-- QA gate: integration-test fair order, expiration, restart, 3 catalogue kinds;
-- confirm no customer-facing stream or EPG changes before authorized rollout.

CREATE OR REPLACE FUNCTION lounge_sync.sync_best_inventory_batch(p_batch_size integer DEFAULT 1200)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'pg_catalog'
AS $function$
DECLARE
  job record;
  insert_count integer := 0;
  n integer;
  processed_total integer;
  actual_count integer;
  batch_size integer := greatest(100,least(coalesce(p_batch_size,1200),2500));
  result jsonb;
BEGIN
  IF NOT pg_try_advisory_xact_lock(1773,1883) THEN
    RETURN jsonb_build_object('status','busy');
  END IF;
  INSERT INTO lounge_sync.best_inventory_imports
    (poll_id,kind,expected_count,bootstrap)
  SELECT p.poll_id,p.catalogue_type,p.item_count,
    NOT EXISTS (SELECT 1 FROM lounge_sync.best_inventory_items i
                WHERE i.kind=p.catalogue_type)
  FROM (
    SELECT DISTINCT ON(catalogue_type) poll_id,catalogue_type,item_count
    FROM lounge_sync.best_provider_poll_runs
    WHERE status='ok'
    ORDER BY catalogue_type,poll_id DESC
  ) p
  WHERE NOT EXISTS (
    SELECT 1 FROM lounge_sync.best_inventory_imports i
    WHERE i.kind=p.catalogue_type AND i.status='processing'
  )
  ON CONFLICT (poll_id) DO NOTHING;

  SELECT * INTO job
  FROM lounge_sync.best_inventory_imports
  WHERE status='processing'
  -- Serve the oldest pending import first. Percentage-complete ordering starves
  -- nearly complete live/series imports whenever a fresh movie import arrives.
  -- Existing missing-response guard below fails expired jobs without writing inventory.
  ORDER BY started_at ASC, poll_id ASC
  LIMIT 1
  FOR UPDATE SKIP LOCKED;

  IF NOT FOUND THEN
    RETURN jsonb_build_object('status','idle');
  END IF;
  IF NOT EXISTS(
    SELECT 1 FROM net._http_response r WHERE r.id=(
      SELECT request_id FROM lounge_sync.best_provider_poll_runs p WHERE p.poll_id=job.poll_id
    ) AND r.status_code=200 AND r.content IS NOT NULL
  ) THEN
    UPDATE lounge_sync.best_inventory_imports
    SET status='failed',error_summary='Provider HTTP response expired or unavailable'
    WHERE poll_id=job.poll_id;
    RETURN jsonb_build_object('status','failed','kind',job.kind,
                              'reason','source response unavailable');
  END IF;

  WITH r AS (
    SELECT response.content::jsonb AS arr
    FROM net._http_response response
    JOIN lounge_sync.best_provider_poll_runs p
       ON response.id=p.request_id
    WHERE p.poll_id=job.poll_id
  ),
  segment AS (
    SELECT item.obj, item.pos
    FROM r, LATERAL jsonb_array_elements(r.arr) WITH ORDINALITY item(obj,pos)
    WHERE item.pos > job.next_offset
      AND item.pos <= job.next_offset + batch_size
  ),
  parsed AS (
    SELECT obj,pos,
      CASE WHEN job.kind='series' THEN obj->>'series_id' ELSE obj->>'stream_id' END AS id_string
    FROM segment
  ),
  valid AS (
    SELECT DISTINCT ON (id_string::bigint)
      id_string::bigint AS provider_item_id,
      coalesce(nullif(trim(obj->>'name'),''),'Unknown provider title') AS name,
      nullif(obj->>'category_id','') AS category_id,
      nullif(obj->>'epg_channel_id','') AS epg_channel_id,
      coalesce(nullif(obj->>'stream_icon',''),nullif(obj->>'cover','')) AS artwork_url,
      nullif(obj->>'tmdb','') AS tmdb_id,
      nullif(obj->>'container_extension','') AS file_ext,
      lower(coalesce(obj->>'is_adult','0')) IN ('1','true') AS is_adult,
      jsonb_strip_nulls(jsonb_build_object(
        'added',obj->>'added',
        'last_modified',obj->>'last_modified',
        'release_date',coalesce(obj->>'release_date',obj->>'releaseDate'),
        'genre',obj->>'genre',
        'rating',obj->>'rating'
      )) AS details
    FROM parsed
    WHERE id_string ~ '^[0-9]{1,15}$'
      AND id_string::bigint > 0
    ORDER BY id_string::bigint,pos DESC
  ),
  prepared AS (
    SELECT v.*,
      md5(jsonb_build_object(
        'name',v.name,'category_id',v.category_id,
        'epg_channel_id',v.epg_channel_id,
        'artwork_url',v.artwork_url,'tmdb_id',v.tmdb_id,
        'file_ext',v.file_ext,'is_adult',v.is_adult,
        'details',v.details
      )::text) AS hash_value
    FROM valid v
  )
  INSERT INTO lounge_sync.best_inventory_items(
    kind,provider_item_id,name,category_id,epg_channel_id,artwork_url,
    tmdb_id,file_ext,is_adult,details,fingerprint,last_seen_poll_id
  )
  SELECT job.kind, v.provider_item_id,v.name,v.category_id,v.epg_channel_id,v.artwork_url,
    v.tmdb_id,v.file_ext,v.is_adult,v.details,v.hash_value,job.poll_id
  FROM prepared v
  ON CONFLICT (kind,provider_item_id) DO UPDATE SET
    name=EXCLUDED.name, category_id=EXCLUDED.category_id,
    epg_channel_id=EXCLUDED.epg_channel_id,artwork_url=EXCLUDED.artwork_url,
    tmdb_id=EXCLUDED.tmdb_id,file_ext=EXCLUDED.file_ext,
    is_adult=EXCLUDED.is_adult,details=EXCLUDED.details,
    fingerprint=EXCLUDED.fingerprint,
    last_seen_poll_id=EXCLUDED.last_seen_poll_id,
    last_seen_at=now(),is_present=true,missing_polls=0
  WHERE lounge_sync.best_inventory_items.last_seen_poll_id IS DISTINCT FROM
          EXCLUDED.last_seen_poll_id
     OR lounge_sync.best_inventory_items.fingerprint IS DISTINCT FROM
          EXCLUDED.fingerprint
     OR NOT lounge_sync.best_inventory_items.is_present;

  GET DIAGNOSTICS insert_count = ROW_COUNT;
  n := least(batch_size,job.expected_count-job.next_offset);
  processed_total:=job.next_offset+n;
  UPDATE lounge_sync.best_inventory_imports
  SET next_offset=processed_total
  WHERE poll_id=job.poll_id;

  IF processed_total >= job.expected_count THEN
    SELECT count(*) INTO actual_count
    FROM lounge_sync.best_inventory_items
    WHERE kind=job.kind AND last_seen_poll_id=job.poll_id;

    IF actual_count <> job.expected_count THEN
      UPDATE lounge_sync.best_inventory_imports
      SET status='review_required',completed_at=now(),
          error_summary=format('Expected %s unique rows, loaded %s',job.expected_count,actual_count)
      WHERE poll_id=job.poll_id;
      RETURN jsonb_build_object('kind',job.kind,'poll_id',job.poll_id,
        'status','review_required','expected',job.expected_count,'actual',actual_count);
    END IF;
    IF NOT job.bootstrap THEN
      WITH missing_rows AS (
        UPDATE lounge_sync.best_inventory_items i
        SET missing_polls=i.missing_polls+1,
            is_present=(i.missing_polls+1<2)
        WHERE i.kind=job.kind
          AND i.is_present=true
          AND i.last_seen_poll_id<>job.poll_id
        RETURNING i.kind,i.provider_item_id,i.missing_polls,i.fingerprint
      )
      INSERT INTO lounge_sync.best_inventory_deltas(
        poll_id,kind,provider_item_id,event_name,previous_fingerprint
      )
      SELECT job.poll_id,kind,provider_item_id,'missing_confirmed',fingerprint
      FROM missing_rows WHERE missing_polls>=2
      ON CONFLICT DO NOTHING;
    END IF;
    UPDATE lounge_sync.best_inventory_imports
    SET status='complete',completed_at=now() WHERE poll_id=job.poll_id;
    RETURN jsonb_build_object('kind',job.kind,'poll_id',job.poll_id,
      'status','complete','processed',processed_total,'unique',actual_count,
      'bootstrap',job.bootstrap);
  END IF;

  RETURN jsonb_build_object('kind',job.kind,'poll_id',job.poll_id,
    'status','processing','processed',processed_total,'total',job.expected_count,
    'touched',insert_count,'bootstrap',job.bootstrap);
EXCEPTION WHEN OTHERS THEN
  RAISE;
END
$function$

