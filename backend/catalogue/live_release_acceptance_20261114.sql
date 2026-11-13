-- Lounge TV | 14 November 2026 | read-only acceptance for inactive QA release
-- No activation and no changes to the currently active customer release.
WITH d AS (
  SELECT id,state,live_expected,movie_expected,series_expected
  FROM lounge_delivery.catalogue_releases
  WHERE release_key='live-3252-20261114-qa-draft'
), old AS (
  SELECT id FROM lounge_delivery.catalogue_releases
  WHERE release_key='ppv-curated-817-20261106-qa'
), live AS (
 SELECT * FROM lounge_delivery.live_release_items WHERE release_id=(SELECT id FROM d) AND admission='keep'
), binding AS (
 SELECT * FROM lounge_delivery.live_source_bindings WHERE release_id=(SELECT id FROM d)
), vod AS (
 SELECT * FROM lounge_delivery.vod_release_items WHERE release_id=(SELECT id FROM d) AND admission='keep'
), categories AS (
 SELECT * FROM lounge_delivery.categories WHERE release_id=(SELECT id FROM d)
), checks AS (
 SELECT 'draft_not_active' gate,(SELECT state='draft' FROM d) pass
 UNION ALL SELECT 'production_active_original',
  (SELECT id=(SELECT id FROM old) FROM lounge_delivery.catalogue_releases WHERE state='active')
 UNION ALL SELECT 'official_release_validator',
  (SELECT COALESCE((lounge_delivery.validate_release(id)->>'ok')::boolean,false) FROM d)
 UNION ALL SELECT 'live_3252', (SELECT count(*)=3252 FROM live)
 UNION ALL SELECT 'movies_5142', (SELECT count(*)=5142 FROM vod WHERE content_type='movie')
 UNION ALL SELECT 'series_3395', (SELECT count(*)=3395 FROM vod WHERE content_type='series')
 UNION ALL SELECT 'no_ppv_in_qa_live', (SELECT count(*)=0 FROM live WHERE category='PPV' OR subcategory='PPV')
 UNION ALL SELECT '9_main_live_categories',
  (SELECT count(*)=9 FROM categories WHERE kind='live' AND enabled)
 UNION ALL SELECT '43_populated_presentation_folders',
  (SELECT count(*)=43 FROM (SELECT category,subcategory FROM live GROUP BY 1,2) folders)
 UNION ALL SELECT 'unique_canonical_keys',
  (SELECT count(*)=count(DISTINCT canonical_key) FROM live)
 UNION ALL SELECT 'unique_live_channel_ids',
  (SELECT count(*)=count(DISTINCT live_channel_id) FROM live)
 UNION ALL SELECT 'one_primary_per_channel',
  NOT EXISTS (
    SELECT 1 FROM live l
    LEFT JOIN binding b ON b.live_channel_id=l.live_channel_id AND b.source_role='primary'
    GROUP BY l.live_channel_id HAVING count(b.provider_stream_id)<>1
  )
 UNION ALL SELECT 'all_327_probed_candidates_bound',
  (SELECT count(*)=327 FROM lounge_core.live_rebuild_source_candidates_20261113 c
    WHERE c.shortlist_for_probe AND c.probe_status='transport_ok'
      AND EXISTS (
        SELECT 1 FROM live l JOIN binding b ON b.live_channel_id=l.live_channel_id
        WHERE l.canonical_key=c.proposed_canonical_key AND b.provider_stream_id=c.provider_stream_id
      ))
 UNION ALL SELECT 'new_sources_only_verified_transport',
  NOT EXISTS(SELECT 1 FROM lounge_core.live_rebuild_source_candidates_20261113 c
             WHERE c.shortlist_for_probe AND c.probe_status<>'transport_ok')
 UNION ALL SELECT 'no_missing_live_artwork',
  NOT EXISTS(SELECT 1 FROM live WHERE nullif(btrim(logo_url),'') IS NULL)
 UNION ALL SELECT 'no_duplicate_display_names_by_folder',
  NOT EXISTS(
    SELECT 1 FROM live GROUP BY category,subcategory,lower(regexp_replace(display_name,'[^[:alnum:]]','','g'))
    HAVING count(*)>1
  )
 UNION ALL SELECT 'nz_special_playback_bindings_kept',
  (SELECT count(*)=5 FROM live l JOIN binding b ON b.live_channel_id=l.live_channel_id
    WHERE b.source_role='primary' AND l.canonical_key IN ('nz-tvnz-1','nz-tvnz-2','nz-three','nz-whakaata-maori','nz-te-reo')
      AND b.provider_id='3399fd30-21e6-4282-9132-b14c93028e5d'::uuid
      AND b.provider_stream_id BETWEEN 900000001 AND 900000005)
 UNION ALL SELECT 'us_major_networks_pinned',
  (SELECT string_agg(display_name,'|' ORDER BY sort_order)
     ='NFL Network|NFL RedZone|NBA TV|MLB Network|NHL Network|ESPN|ESPN2|FS1|FS2|CBS Sports Network'
  FROM live WHERE category='Sports' AND subcategory='US Major Leagues')
 UNION ALL SELECT 'australia_flagships_pinned',
  (SELECT (array_agg(display_name ORDER BY sort_order))[1:7]
    = ARRAY['Seven','Channel 9','Ten','ABC','SBS','ABC News Australia','Sky News Australia']
  FROM live WHERE category='Australia' AND subcategory='TV')
 UNION ALL SELECT 'cinemania_numeric_sort',
  (SELECT (array_agg(display_name ORDER BY sort_order))[1:3]
     = ARRAY['Cinemania Hollywood 1','Cinemania Hollywood 2','Cinemania Hollywood 3']
     AND (array_agg(display_name ORDER BY sort_order))[59:60]
     = ARRAY['Cinemania Premiere 9','Cinemania Premiere 10']
  FROM live WHERE category='Movie Channels' AND subcategory='Cinemania')
 UNION ALL SELECT 'australia_nine_network_epg_linked',
  (SELECT count(*)=3 FROM live l JOIN lounge_delivery.shared_epg_channels e ON e.epg_id=l.epg_id
   WHERE l.canonical_key IN ('au-9go','au-9life','au-9rush') AND e.programme_count>0 AND e.expires_at>now())
 UNION ALL SELECT 'wrong_epg_identity_mappings_removed',
  (SELECT count(*)=3 FROM live
   WHERE display_name IN('BBC Brit','MeTV Plus','BEIN SPORTS 2') AND epg_id IS NULL)
 UNION ALL SELECT 'vod_exact_copy',
  NOT EXISTS(
    (SELECT title_id,content_type,display_name,sort_order,poster_url,backdrop_url,editorial FROM vod
     EXCEPT
     SELECT title_id,content_type,display_name,sort_order,poster_url,backdrop_url,editorial
     FROM lounge_delivery.vod_release_items WHERE release_id=(SELECT id FROM old) AND admission='keep')
    UNION ALL
    (SELECT title_id,content_type,display_name,sort_order,poster_url,backdrop_url,editorial
     FROM lounge_delivery.vod_release_items WHERE release_id=(SELECT id FROM old) AND admission='keep'
     EXCEPT
     SELECT title_id,content_type,display_name,sort_order,poster_url,backdrop_url,editorial FROM vod)
  )
 UNION ALL SELECT 'vod_category_memberships_preserved',
  NOT EXISTS(
    (SELECT c.kind,c.category_key,m.item_kind,m.canonical_id,m.rank
     FROM lounge_delivery.category_members m JOIN categories c ON c.id=m.category_id
     WHERE m.release_id=(SELECT id FROM d) AND c.kind<>'live'
     EXCEPT
     SELECT c.kind,c.category_key,m.item_kind,m.canonical_id,m.rank
     FROM lounge_delivery.category_members m JOIN lounge_delivery.categories c ON c.id=m.category_id
     WHERE m.release_id=(SELECT id FROM old) AND c.kind<>'live')
    UNION ALL
    (SELECT c.kind,c.category_key,m.item_kind,m.canonical_id,m.rank
     FROM lounge_delivery.category_members m JOIN lounge_delivery.categories c ON c.id=m.category_id
     WHERE m.release_id=(SELECT id FROM old) AND c.kind<>'live'
     EXCEPT
     SELECT c.kind,c.category_key,m.item_kind,m.canonical_id,m.rank
     FROM lounge_delivery.category_members m JOIN categories c ON c.id=m.category_id
     WHERE m.release_id=(SELECT id FROM d) AND c.kind<>'live')
  )
 UNION ALL SELECT 'real_firestick_playback_verified', false
)
SELECT gate,CASE WHEN pass THEN 'PASS' ELSE 'BLOCK' END result
FROM checks ORDER BY gate;