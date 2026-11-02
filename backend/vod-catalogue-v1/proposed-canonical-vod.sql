-- Lounge TV canonical VOD catalogue v1 proposal (READ ONLY)
--
-- This CTE-only query does not create, update, or delete database objects.
-- It derives a premium, hidden-by-default Movies / Series / Streaming proposal
-- from the 2026-11-02 BEST inventory snapshot and the existing TMDB-enriched
-- lounge_core canonical layer.

with
params as (
  select date '2026-11-02' as snapshot_date
),
removed_categories(kind,category_id,removal_group) as (
  values
    ('movies','2063','foreign_or_subbed'),
    ('movies','1275','foreign_or_subbed'),
    ('movies','2212','foreign_or_subbed'),
    ('movies','1064','foreign_or_subbed'),
    ('movies','653','provider_bucket'),
    ('movies','633','duplicate_or_technical'),
    ('movies','2043','cam_or_prerelease'),
    ('movies','634','duplicate_or_technical'),
    ('movies','362','provider_bucket'),
    ('movies','364','provider_bucket'),
    ('movies','1133','provider_bucket'),
    ('movies','1257','provider_bucket'),
    ('movies','1369','provider_bucket'),
    ('movies','1394','provider_bucket'),
    ('movies','631','duplicate_or_technical'),
    ('movies','928','duplicate_or_technical'),
    ('movies','1180','provider_bucket'),
    ('movies','1712','provider_bucket'),
    ('movies','2172','provider_bucket'),
    ('series','1035','foreign_or_subbed'),
    ('series','1157','foreign_or_subbed'),
    ('series','1164','foreign_or_subbed')
),
platform_categories(kind,category_id,platform) as (
  values
    ('movies','1239','Netflix'),('movies','561','Netflix'),('movies','562','Netflix'),
    ('movies','759','Disney+'),('movies','925','Disney+'),('movies','926','Disney+'),
    ('movies','1242','Prime Video'),('movies','927','Apple TV+'),
    ('movies','1243','HBO Max'),('movies','1241','Hulu'),
    ('movies','1370','BET & ALLBLK'),
    ('series','1136','Netflix'),('series','558','Netflix'),
    ('series','718','Netflix'),('series','848','Netflix'),
    ('series','1138','Disney+'),('series','1183','Disney+'),
    ('series','933','Disney+'),('series','934','Disney+'),
    ('series','1137','Prime Video'),
    ('series','1146','Apple TV+'),('series','935','Apple TV+'),
    ('series','936','Apple TV+'),('series','1174','HBO Max'),
    ('series','1176','Hulu'),('series','1398','Paramount+'),
    ('series','1651','Peacock'),('series','1652','AMC+ & MGM+'),
    ('series','1920','Showtime & Starz'),
    ('series','1399','BET & ALLBLK')
),
mapped_sources as (
  select
    t.id title_id,t.content_type,t.tmdb_id,t.canonical_title,t.sort_title,
    t.original_title,t.release_year,t.release_date,t.original_language,
    t.primary_audio_language,t.overview,t.poster_url,t.backdrop_url,
    t.content_rating,t.runtime_minutes,t.quality_gate,t.quality_score,
    t.editorial_status,t.editorial_score,t.editorial_reasons,
    t.metadata_source,t.metadata,
    s.id source_id,s.provider_item_id,s.provider_kind,s.source_name,
    s.file_ext,s.source_language,s.source_role,s.status source_status,
    s.rejection_reasons,s.is_cam,s.is_foreign,s.resolution_height,
    s.quality_score source_quality_score,s.last_verified_at,
    i.category_id,c.category_name provider_category,i.artwork_url provider_artwork,
    i.is_present,i.is_adult,i.last_seen_at,
    r.removal_group,p.platform,
    (
      coalesce(s.is_cam,false)
      or i.name ~* '(^|[^A-Z])(HDCAM|CAMRIP|TELESYNC|TS[ ._-]*CAM|CAM)([^A-Z]|$)'
    ) is_cam_source,
    coalesce(s.is_foreign,false) is_foreign_flagged_source,
    i.name ~* '(ENG[- ]?SUB|MULTI[[:space:]]*SUB|DUAL[[:space:]]+MULTI|KOREAN[[:space:]]+MULTI|JAPANESE/ENG|GERMAN[[:space:]]+ENG[- ]?SUB|FRENCH[[:space:]]*/[[:space:]]*ENG[[:space:]]*SUB|ITALIAN[[:space:]]*/[[:space:]]*ENG[[:space:]]*SUB)'
      is_explicit_foreign_source
  from lounge_core.titles t
  join lounge_core.sources s on s.title_id=t.id
  join lounge_sync.best_inventory_items i
    on i.provider_item_id=s.provider_item_id and i.kind=s.provider_kind
  join lounge_sync.best_categories c
    on c.kind=i.kind and c.category_id=i.category_id
  left join removed_categories r
    on r.kind=i.kind and r.category_id=i.category_id
  left join platform_categories p
    on p.kind=i.kind and p.category_id=i.category_id
  where (t.content_type='movie' and i.kind='movies')
     or (t.content_type='series' and i.kind='series')
),
source_flags as (
  select m.*,
    (
      m.is_present
      and not m.is_adult
      and m.removal_group is null
      and not m.is_cam_source
      and not m.is_explicit_foreign_source
    ) is_eligible_source
  from mapped_sources m
),
title_source_stats as (
  select
    title_id,min(content_type) content_type,min(tmdb_id) tmdb_id,
    min(canonical_title) canonical_title,min(sort_title) sort_title,
    min(original_title) original_title,min(release_year) release_year,
    min(release_date) release_date,min(original_language) original_language,
    min(primary_audio_language) primary_audio_language,min(overview) overview,
    min(poster_url) poster_url,min(backdrop_url) backdrop_url,
    min(content_rating) content_rating,min(runtime_minutes) runtime_minutes,
    min(quality_gate) quality_gate,max(quality_score) quality_score,
    min(editorial_status) editorial_status,max(editorial_score) editorial_score,
    min(editorial_reasons) editorial_reasons,min(metadata_source) metadata_source,
    min(metadata::text)::jsonb metadata,
    count(*) filter(where is_present and not is_adult) current_source_count,
    count(*) filter(where is_eligible_source) eligible_source_count,
    count(*) filter(where is_present and removal_group is not null) removed_source_count,
    count(*) filter(where is_present and is_cam_source) cam_source_count,
    count(*) filter(where is_present and is_explicit_foreign_source) foreign_source_count,
    count(*) filter(where is_eligible_source and not is_foreign_flagged_source) eligible_nonforeign_source_count,
    count(*) filter(where is_present and is_foreign_flagged_source) foreign_flagged_source_count,
    count(*) filter(where is_present and is_adult) adult_source_count,
    array_remove(array_agg(distinct category_id) filter(where is_eligible_source),null) eligible_category_ids,
    array_remove(array_agg(distinct platform) filter(where is_eligible_source),null) platforms
  from source_flags
  group by title_id
),
category_only_stats as (
  select title_id,min(content_type) content_type,
    bool_or(is_present and not is_adult and removal_group is not null) touched_removed_category,
    count(*) filter(where is_present and not is_adult and removal_group is null) present_nonremoved_source_count
  from source_flags
  group by title_id
),
eligible_sources as (
  select f.*,
    row_number() over (
      partition by f.title_id
      order by
        coalesce(f.is_foreign_flagged_source,false),
        case f.source_status when 'verified' then 0 when 'candidate' then 1
          when 'degraded' then 2 when 'missing' then 4 when 'rejected' then 5 else 3 end,
        coalesce(f.source_quality_score,0) desc,
        (f.last_verified_at is not null) desc,
        f.last_verified_at desc nulls last,f.last_seen_at desc,f.provider_item_id
    ) source_rank
  from source_flags f
  where f.is_eligible_source
),
primary_sources as (
  select title_id,source_id,provider_item_id,source_name,category_id,
         provider_category,file_ext,source_status,source_quality_score,
         last_verified_at,is_foreign_flagged_source
  from eligible_sources where source_rank=1
),
genre_stats as (
  select tg.title_id,
    array_agg(distinct g.name order by g.name) genres,
    bool_or(g.name in ('Action','Action & Adventure')) has_action,
    bool_or(g.name in ('Adventure','Action & Adventure')) has_adventure,
    bool_or(g.name='Animation') has_animation,
    bool_or(g.name='Comedy') has_comedy,
    bool_or(g.name='Crime') has_crime,
    bool_or(g.name='Documentary') has_documentary,
    bool_or(g.name='Drama') has_drama,
    bool_or(g.name='Family') has_family,
    bool_or(g.name='Fantasy') has_fantasy,
    bool_or(g.name='Kids') has_kids,
    bool_or(g.name='Reality') has_reality,
    bool_or(g.name='Romance') has_romance,
    bool_or(g.name in ('Science Fiction','Sci-Fi & Fantasy')) has_scifi,
    bool_or(g.name in ('War','War & Politics')) has_war,
    bool_or(g.name='Western') has_western
  from lounge_core.title_genres tg
  join lounge_core.genres g on g.id=tg.genre_id
  group by tg.title_id
),
studio_stats as (
  select ts.title_id,
    array_agg(distinct coalesce(nullif(s.display_name,''),s.name) order by coalesce(nullif(s.display_name,''),s.name)) studios,
    bool_or(coalesce(nullif(s.display_name,''),s.name) ~* '(Disney|Pixar|Marvel|Lucasfilm|Warner|HBO|Universal|Paramount|Sony|Columbia|MGM|Metro-Goldwyn|Netflix|Amazon|Apple|BBC|ITV|A24|Lionsgate|DreamWorks|Studio Ghibli|Showtime|Starz|AMC)') recognised_studio
  from lounge_core.title_studios ts
  join lounge_core.studios s on s.id=ts.studio_id
  group by ts.title_id
),
collection_stats as (
  select tc.title_id,true has_collection,
         array_agg(distinct c.name order by c.name) collections
  from lounge_core.title_collections tc
  join lounge_core.collections c on c.id=tc.collection_id
  group by tc.title_id
),
candidate_features as (
  select s.*,p.source_id,p.provider_item_id primary_provider_item_id,
    p.source_name primary_source_name,p.category_id primary_category_id,
    p.provider_category primary_provider_category,p.file_ext primary_file_ext,
    p.source_status primary_source_status,
    coalesce(p.source_quality_score,0) primary_source_quality_score,
    p.last_verified_at primary_last_verified_at,
    p.is_foreign_flagged_source primary_source_is_foreign_flagged,
    coalesce(g.genres,array[]::text[]) genres,
    coalesce(g.has_action,false) has_action,
    coalesce(g.has_adventure,false) has_adventure,
    coalesce(g.has_animation,false) has_animation,
    coalesce(g.has_comedy,false) has_comedy,
    coalesce(g.has_crime,false) has_crime,
    coalesce(g.has_documentary,false) has_documentary,
    coalesce(g.has_drama,false) has_drama,
    coalesce(g.has_family,false) has_family,
    coalesce(g.has_fantasy,false) has_fantasy,
    coalesce(g.has_kids,false) has_kids,
    coalesce(g.has_reality,false) has_reality,
    coalesce(g.has_romance,false) has_romance,
    coalesce(g.has_scifi,false) has_scifi,
    coalesce(g.has_war,false) has_war,
    coalesce(g.has_western,false) has_western,
    coalesce(st.studios,array[]::text[]) studios,
    coalesce(st.recognised_studio,false) recognised_studio,
    coalesce(cs.has_collection,false) has_collection,
    coalesce(cs.collections,array[]::text[]) collections,
    case when coalesce(s.metadata->>'vote_count','') ~ '^[0-9]+$'
      then (s.metadata->>'vote_count')::bigint else 0 end vote_count,
    case when coalesce(s.metadata->>'vote_average','') ~ '^[0-9]+([.][0-9]+)?$'
      then (s.metadata->>'vote_average')::numeric else 0 end vote_average,
    case when coalesce(s.metadata->>'popularity','') ~ '^[0-9]+([.][0-9]+)?$'
      then (s.metadata->>'popularity')::numeric else 0 end popularity,
    case when coalesce(s.metadata->>'number_of_seasons','') ~ '^[0-9]+$'
      then (s.metadata->>'number_of_seasons')::integer else 0 end season_count,
    case when coalesce(s.metadata->>'number_of_episodes','') ~ '^[0-9]+$'
      then (s.metadata->>'number_of_episodes')::integer else 0 end episode_count,
    case when jsonb_typeof(s.metadata->'cast')='array'
      then jsonb_array_length(s.metadata->'cast') else 0 end cast_count,
    case when jsonb_typeof(s.metadata->'networks')='array'
      then jsonb_array_length(s.metadata->'networks') else 0 end network_count,
    case when jsonb_typeof(s.metadata->'artwork'->'logos')='array'
      then jsonb_array_length(s.metadata->'artwork'->'logos') else 0 end logo_count,
    (
      coalesce(s.poster_url,'') like 'https://image.tmdb.org/t/p/original/%'
      or exists(
        select 1 from jsonb_array_elements(
          case when jsonb_typeof(s.metadata->'artwork'->'posters')='array'
            then s.metadata->'artwork'->'posters' else '[]'::jsonb end
        ) a
        where coalesce((a->>'width')::integer,0)>=500
          and coalesce((a->>'height')::integer,0)>=750
          and coalesce(a->>'language','') in ('','en')
      )
    ) professional_poster,
    (
      coalesce(s.backdrop_url,'') like 'https://image.tmdb.org/t/p/original/%'
      or exists(
        select 1 from jsonb_array_elements(
          case when jsonb_typeof(s.metadata->'artwork'->'backdrops')='array'
            then s.metadata->'artwork'->'backdrops' else '[]'::jsonb end
        ) a
        where coalesce((a->>'width')::integer,0)>=1280
          and coalesce((a->>'height')::integer,0)>=720
      )
    ) professional_backdrop,
    exists(
      select 1 from jsonb_array_elements(
        case when jsonb_typeof(s.metadata->'networks')='array'
          then s.metadata->'networks' else '[]'::jsonb end
      ) n
      where coalesce(n->>'name','') ~* '(Netflix|Disney|HBO|Amazon|Prime Video|Apple TV|BBC|ITV|AMC|Showtime|Starz|Paramount|Peacock|Hulu|FX|CBS|NBC|ABC|PBS)'
    ) recognised_network
  from title_source_stats s
  join primary_sources p on p.title_id=s.title_id
  left join genre_stats g on g.title_id=s.title_id
  left join studio_stats st on st.title_id=s.title_id
  left join collection_stats cs on cs.title_id=s.title_id
  where s.eligible_source_count>0 and s.tmdb_id is not null
),
screened as (
  select f.*,
    lower(coalesce(f.canonical_title,'')||' '||coalesce(f.original_title,'')) title_screening_text,
    lower(coalesce(f.canonical_title,'')||' '||coalesce(f.original_title,'')||' '||coalesce(f.overview,'')) screening_text,
    (
      lower(coalesce(f.canonical_title,'')||' '||coalesce(f.original_title,''))
        ~ '\m(porn|pornography|pornographic|softcore|porno|erotic stories|hot girls wanted|supersex)\M'
    ) hard_explicit_content,
    (
      lower(coalesce(f.canonical_title,'')||' '||coalesce(f.original_title,''))
        ~ '\m(porn|pornography|pornographic|softcore|sexploitation|erotic|erotica|playboy|adult-only)\M'
      or lower(coalesce(f.overview,''))
        ~ '(pornography|pornographic|softcore|sexploitation|adult film|erotic (film|stories|thriller)|playboy (magazine|mansion|model|playmate)|nudist|nudism)'
    ) suspicious_content,
    (
      f.poster_url is null or f.backdrop_url is null
      or not f.professional_poster or not f.professional_backdrop
    ) artwork_failure,
    (
      nullif(btrim(coalesce(f.overview,'')),'') is null
      or f.release_year is null
      or cardinality(f.genres)=0
      or f.vote_count=0
      or (f.content_type='series' and (f.season_count=0 or f.episode_count=0))
    ) metadata_failure,
    (
      f.primary_source_status in ('missing','rejected')
      or f.primary_source_quality_score<40
    ) source_failure,
    (f.eligible_nonforeign_source_count=0) foreign_language_review,
    (
      f.vote_count<10 and f.popularity<1 and coalesce(f.editorial_score,0)<10
      and not (cardinality(f.platforms)>0 or f.has_collection or f.recognised_studio or f.recognised_network)
      and coalesce(f.release_year,1900)<extract(year from p.snapshot_date)::integer-1
    ) low_value_drop,
    (
      f.vote_count>=20 and f.vote_average<4.5 and f.popularity<3
      and not (cardinality(f.platforms)>0 or f.has_collection or f.recognised_studio or f.recognised_network)
    ) low_rating_drop
  from candidate_features f cross join params p
),
scored as (
  select s.*,
    (
      case when vote_count>=5000 then 20 when vote_count>=1000 then 17
        when vote_count>=250 then 14 when vote_count>=75 then 10
        when vote_count>=20 then 6 when vote_count>=5 then 3 else 0 end
      + case when popularity>=30 then 15 when popularity>=15 then 12
        when popularity>=7 then 9 when popularity>=3 then 6
        when popularity>=1 then 3 else 0 end
      + case when vote_average>=8 and vote_count>=50 then 12
        when vote_average>=7.3 and vote_count>=25 then 10
        when vote_average>=6.7 and vote_count>=10 then 7
        when vote_average>=6 and vote_count>=5 then 4 else 0 end
      + case when coalesce(editorial_score,0)>=60 then 12
        when coalesce(editorial_score,0)>=45 then 10
        when coalesce(editorial_score,0)>=30 then 8
        when coalesce(editorial_score,0)>=15 then 5
        when coalesce(editorial_score,0)>0 then 2 else 0 end
      + case when coalesce(quality_score,0)>=80 then 8
        when coalesce(quality_score,0)>=60 then 6
        when coalesce(quality_score,0)>=40 then 3 else 0 end
      + case when nullif(btrim(coalesce(overview,'')),'') is not null then 2 else 0 end
      + case when cardinality(genres)>0 then 2 else 0 end
      + case when cast_count>0 then 2 else 0 end
      + case when recognised_studio or recognised_network then 2 else 0 end
      + case when professional_poster then 4 else 0 end
      + case when professional_backdrop then 4 else 0 end
      + case when logo_count>0 then 2 else 0 end
      + case when cardinality(platforms)>0 then 5 else 0 end
      + case when has_collection then 3 else 0 end
      + case
          when release_year>=2024 then 5
          when release_year>=2018 then 3
          when release_year<=2005 and (vote_count>=250 or (vote_count>=50 and vote_average>=6.8)) then 5
          else 0 end
      + case when primary_source_status='verified' then 6
        when primary_source_quality_score>=80 then 5
        when primary_source_quality_score>=60 then 3
        when primary_source_quality_score>=40 then 1 else 0 end
      + case when content_type='series' and season_count>0 and episode_count>0 then 4 else 0 end
    )::integer lounge_premium_score
  from screened s
),
decisions as (
  select s.*,
    case
      when hard_explicit_content then 'DROP'
      when low_value_drop then 'DROP'
      when low_rating_drop then 'DROP'
      when suspicious_content then 'HOLD'
      when foreign_language_review then 'HOLD'
      when artwork_failure then 'HOLD'
      when metadata_failure then 'HOLD'
      when source_failure then 'HOLD'
      when lounge_premium_score >= case when content_type='movie' then 50 else 48 end then 'KEEP'
      else 'HOLD'
    end decision,
    case
      when hard_explicit_content then 'explicit_content_excluded'
      when low_value_drop then 'low_value_low_signal'
      when low_rating_drop then 'low_rating_low_interest'
      when suspicious_content then 'manual_content_review'
      when foreign_language_review then 'foreign_language_review'
      when artwork_failure then 'artwork_gate_failed'
      when metadata_failure then 'metadata_gate_failed'
      when source_failure then 'source_quality_gate_failed'
      when lounge_premium_score < case when content_type='movie' then 50 else 48 end then 'premium_score_review'
      else 'premium_gate_passed'
    end decision_reason
  from scored s
),
proposed_titles as (
  select
    'tmdb:'||content_type||':'||tmdb_id canonical_key,
    title_id,content_type,tmdb_id,canonical_title,release_year,release_date,
    overview,poster_url,backdrop_url,content_rating,runtime_minutes,
    genres,studios,collections,platforms,vote_average,vote_count,popularity,
    season_count,episode_count,lounge_premium_score,
    primary_provider_item_id,primary_source_name,primary_category_id,
    primary_source_status,primary_source_quality_score,
    primary_source_is_foreign_flagged,
    greatest(eligible_source_count-1,0) hidden_backup_source_count,
    decision,decision_reason,logo_count
  from decisions
),
movie_views as (
  select title_id,'Featured Movies' customer_view,10 sort_order from decisions
    where decision='KEEP' and content_type='movie' and lounge_premium_score>=75
      and logo_count>0 and (primary_source_status='verified' or primary_source_quality_score>=60)
  union all
  select d.title_id,'New Releases',20 from decisions d cross join params p
    where d.decision='KEEP' and d.content_type='movie'
      and d.release_date between p.snapshot_date-interval '18 months' and p.snapshot_date
  union all
  select title_id,'Trending',30 from decisions
    where decision='KEEP' and content_type='movie' and (popularity>=8 or lounge_premium_score>=70)
  union all
  select title_id,'Popular Movies',40 from decisions
    where decision='KEEP' and content_type='movie'
  union all select title_id,'Action',50 from decisions where decision='KEEP' and content_type='movie' and has_action
  union all select title_id,'Comedy',60 from decisions where decision='KEEP' and content_type='movie' and has_comedy
  union all select title_id,'Crime',70 from decisions where decision='KEEP' and content_type='movie' and has_crime
  union all select title_id,'Documentaries',80 from decisions where decision='KEEP' and content_type='movie' and has_documentary
  union all select title_id,'Drama',90 from decisions where decision='KEEP' and content_type='movie' and has_drama
  union all select title_id,'Family',100 from decisions where decision='KEEP' and content_type='movie' and (has_family or has_animation)
  union all select title_id,'Sci-Fi & Fantasy',110 from decisions where decision='KEEP' and content_type='movie' and (has_scifi or has_fantasy)
  union all select title_id,'Romance',120 from decisions where decision='KEEP' and content_type='movie' and has_romance
  union all select title_id,'War',130 from decisions where decision='KEEP' and content_type='movie' and has_war
  union all select title_id,'Westerns',140 from decisions where decision='KEEP' and content_type='movie' and has_western
  union all
  select title_id,'Stand-Up Comedy',150 from decisions
    where decision='KEEP' and content_type='movie'
      and ('1280'=any(eligible_category_ids) or screening_text ~ '\mstand[- ]?up\M')
  union all
  select title_id,'Classic Movies',160 from decisions
    where decision='KEEP' and content_type='movie' and release_year<=2005
      and (vote_count>=250 or (vote_count>=50 and vote_average>=6.8) or has_collection)
  union all
  select title_id,'Christmas (Seasonal)',900 from decisions
    where decision='KEEP' and content_type='movie'
      and ('1273'=any(eligible_category_ids) or screening_text ~ '\mchristmas\M')
),
series_views as (
  select title_id,'Featured Series' customer_view,10 sort_order from decisions
    where decision='KEEP' and content_type='series' and lounge_premium_score>=75
      and logo_count>0 and (primary_source_status='verified' or primary_source_quality_score>=60)
  union all
  select d.title_id,'New Releases',20 from decisions d cross join params p
    where d.decision='KEEP' and d.content_type='series'
      and d.release_date between p.snapshot_date-interval '18 months' and p.snapshot_date
  union all
  select title_id,'Trending',30 from decisions
    where decision='KEEP' and content_type='series' and (popularity>=12 or lounge_premium_score>=70)
  union all
  select title_id,'Popular Series',40 from decisions
    where decision='KEEP' and content_type='series'
  union all select title_id,'Action & Adventure',50 from decisions where decision='KEEP' and content_type='series' and (has_action or has_adventure)
  union all select title_id,'Comedy',60 from decisions where decision='KEEP' and content_type='series' and has_comedy
  union all select title_id,'Crime',70 from decisions where decision='KEEP' and content_type='series' and has_crime
  union all select title_id,'Documentaries',80 from decisions where decision='KEEP' and content_type='series' and has_documentary
  union all select title_id,'Drama',90 from decisions where decision='KEEP' and content_type='series' and has_drama
  union all select title_id,'Kids & Family',100 from decisions where decision='KEEP' and content_type='series' and (has_kids or has_family or has_animation)
  union all select title_id,'Reality',110 from decisions where decision='KEEP' and content_type='series' and has_reality
  union all select title_id,'Sci-Fi & Fantasy',120 from decisions where decision='KEEP' and content_type='series' and (has_scifi or has_fantasy)
  union all
  select title_id,'Classic Series',130 from decisions
    where decision='KEEP' and content_type='series' and release_year<=2005
      and (vote_count>=100 or (vote_count>=25 and vote_average>=7))
),
streaming_views as (
  select d.title_id,d.content_type,unnest(d.platforms) platform
  from decisions d where d.decision='KEEP' and cardinality(d.platforms)>0
),
kids_family_view as (
  select title_id,content_type
  from decisions
  where decision='KEEP'
    and (has_family or has_kids or has_animation)
),
removed_category_metrics as (
  select r.kind,r.category_id,c.category_name,r.removal_group,
    count(i.*) filter(where i.is_present) raw_rows,
    count(distinct f.title_id) filter(where f.is_present) canonical_titles_touched,
    count(distinct f.title_id) filter(where f.is_present and co.present_nonremoved_source_count=0) titles_only_in_removed_sources
  from removed_categories r
  join lounge_sync.best_categories c on c.kind=r.kind and c.category_id=r.category_id
  left join lounge_sync.best_inventory_items i on i.kind=r.kind and i.category_id=r.category_id
  left join source_flags f on f.provider_kind=r.kind and f.provider_item_id=i.provider_item_id
  left join category_only_stats co on co.title_id=f.title_id
  group by r.kind,r.category_id,c.category_name,r.removal_group
),
raw_summary as (
  select kind,
    count(*) all_historical_rows,
    count(*) filter(where is_present) present_rows,
    count(*) filter(where is_present and not is_adult) present_nonadult_rows,
    count(*) filter(where is_present and is_adult) present_adult_rows
  from lounge_sync.best_inventory_items
  where kind in ('movies','series') group by kind
),
unmapped_retained_raw as (
  select i.kind,count(*) rows
  from lounge_sync.best_inventory_items i
  left join removed_categories r on r.kind=i.kind and r.category_id=i.category_id
  where i.kind in ('movies','series') and i.is_present and not i.is_adult
    and r.category_id is null
    and i.name !~* '(^|[^A-Z])(HDCAM|CAMRIP|TELESYNC|TS[ ._-]*CAM|CAM)([^A-Z]|$)'
    and not exists(
      select 1 from lounge_core.sources s
      where s.provider_kind=i.kind and s.provider_item_id=i.provider_item_id
    )
  group by i.kind
),
summary as (
  select jsonb_build_object(
    'snapshot_date',(select snapshot_date from params),
    'raw_movie_rows',(select present_rows from raw_summary where kind='movies'),
    'raw_series_rows',(select present_rows from raw_summary where kind='series'),
    'historical_movie_rows',(select all_historical_rows from raw_summary where kind='movies'),
    'historical_series_rows',(select all_historical_rows from raw_summary where kind='series'),
    'unique_canonical_movies',(select count(*) from decisions where content_type='movie'),
    'unique_canonical_series',(select count(*) from decisions where content_type='series'),
    'movie_keep',(select count(*) from decisions where content_type='movie' and decision='KEEP'),
    'movie_hold',(select count(*) from decisions where content_type='movie' and decision='HOLD'),
    'movie_drop',(select count(*) from decisions where content_type='movie' and decision='DROP'),
    'series_keep',(select count(*) from decisions where content_type='series' and decision='KEEP'),
    'series_hold',(select count(*) from decisions where content_type='series' and decision='HOLD'),
    'series_drop',(select count(*) from decisions where content_type='series' and decision='DROP'),
    'duplicate_sources_consolidated',(select coalesce(sum(eligible_source_count-1),0) from decisions),
    'removed_category_rows',(select coalesce(sum(raw_rows),0) from removed_category_metrics),
    'removed_only_canonical_titles',(
      select count(*) from category_only_stats
      where touched_removed_category and present_nonremoved_source_count=0
    ),
    'movie_removed_only_canonical_titles',(
      select count(*) from category_only_stats
      where content_type='movie' and touched_removed_category and present_nonremoved_source_count=0
    ),
    'series_removed_only_canonical_titles',(
      select count(*) from category_only_stats
      where content_type='series' and touched_removed_category and present_nonremoved_source_count=0
    ),
    'foreign_or_subbed_source_rows',(
      select count(*) from source_flags
      where is_present and not is_adult
        and (removal_group='foreign_or_subbed' or is_explicit_foreign_source or is_foreign_flagged_source)
    ),
    'adult_source_rows',(select sum(present_adult_rows) from raw_summary),
    'explicit_content_dropped',(select count(*) from decisions where decision_reason='explicit_content_excluded'),
    'content_manual_review',(select count(*) from decisions where decision_reason='manual_content_review'),
    'artwork_failures_all_candidates',(select count(*) from decisions where artwork_failure),
    'artwork_hold_primary_reason',(select count(*) from decisions where decision_reason='artwork_gate_failed'),
    'metadata_failures_all_candidates',(select count(*) from decisions where metadata_failure),
    'metadata_hold_primary_reason',(select count(*) from decisions where decision_reason='metadata_gate_failed'),
    'unmapped_retained_movie_rows',(select coalesce(max(rows),0) from unmapped_retained_raw where kind='movies'),
    'unmapped_retained_series_rows',(select coalesce(max(rows),0) from unmapped_retained_raw where kind='series'),
    'production_mutations',0
  ) value
),
decision_counts as (
  select jsonb_agg(jsonb_build_object(
    'content_type',content_type,'decision',decision,'reason',decision_reason,'titles',titles
  ) order by content_type,decision,decision_reason) value
  from (
    select content_type,decision,decision_reason,count(*) titles
    from decisions group by content_type,decision,decision_reason
  ) x
),
movie_category_counts as (
  select jsonb_agg(jsonb_build_object('category',customer_view,'titles',titles) order by sort_order,customer_view) value
  from (
    select customer_view,min(sort_order) sort_order,count(distinct title_id) titles
    from movie_views group by customer_view
  ) x
),
series_category_counts as (
  select jsonb_agg(jsonb_build_object('category',customer_view,'titles',titles) order by sort_order,customer_view) value
  from (
    select customer_view,min(sort_order) sort_order,count(distinct title_id) titles
    from series_views group by customer_view
  ) x
),
streaming_counts as (
  select jsonb_agg(jsonb_build_object(
    'platform',platform,'movies',movies,'series',series,'total',movies+series
  ) order by case platform
      when 'Netflix' then 10 when 'Disney+' then 20 when 'Prime Video' then 30
      when 'Apple TV+' then 40 when 'HBO Max' then 50 when 'Hulu' then 60
      when 'Paramount+' then 70 when 'Peacock' then 80 when 'AMC+ & MGM+' then 90
      when 'Showtime & Starz' then 100 when 'BET & ALLBLK' then 110 else 999 end
  ) value
  from (
    select platform,
      count(distinct title_id) filter(where content_type='movie') movies,
      count(distinct title_id) filter(where content_type='series') series
    from streaming_views group by platform
  ) x
),
kids_family_counts as (
  select jsonb_build_object(
    'movies',count(*) filter(where content_type='movie'),
    'series',count(*) filter(where content_type='series'),
    'total',count(*)
  ) value
  from kids_family_view
),
removal_details as (
  select jsonb_agg(jsonb_build_object(
    'kind',kind,'category_id',category_id,'provider_category',category_name,
    'removal_group',removal_group,'raw_rows',raw_rows,
    'canonical_titles_touched',canonical_titles_touched,
    'titles_only_in_removed_sources',titles_only_in_removed_sources
  ) order by kind,category_id::integer) value
  from removed_category_metrics
),
manual_review_details as (
  select jsonb_agg(jsonb_build_object(
    'content_type',content_type,'tmdb_id',tmdb_id,'title',canonical_title,
    'year',release_year,'reason',decision_reason,'score',lounge_premium_score
  ) order by content_type,canonical_title,tmdb_id) value
  from decisions where decision_reason='manual_content_review'
),
qa_invariants as (
  select jsonb_build_object(
    'customer_visible_movies',(select count(*) from decisions where decision='KEEP' and content_type='movie'),
    'customer_visible_series',(select count(*) from decisions where decision='KEEP' and content_type='series'),
    'visible_cam_or_prerelease_sources',(
      select count(*) from decisions where decision='KEEP'
        and primary_source_name ~* '(^|[^A-Z])(HDCAM|CAMRIP|TELESYNC|TS[ ._-]*CAM|CAM)([^A-Z]|$)'
    ),
    'visible_explicit_foreign_or_subbed_sources',(
      select count(*) from decisions where decision='KEEP'
        and (primary_source_is_foreign_flagged
          or primary_source_name ~* '(ENG[- ]?SUB|MULTI[[:space:]]*SUB|TURKISH[[:space:]]+SERIES[[:space:]]+SUB|NETFLIX[[:space:]]+ASIA|ASIAN[[:space:]]+SERIES)')
    ),
    'visible_missing_poster_or_backdrop',(
      select count(*) from decisions where decision='KEEP' and artwork_failure
    ),
    'duplicate_canonical_title_records',(
      select count(*) from (
        select content_type,tmdb_id from decisions group by content_type,tmdb_id having count(*)>1
      ) x
    ),
    'raw_provider_category_names_customer_facing',0,
    'customer_top_levels',jsonb_build_array('MOVIES','SERIES','STREAMING','KIDS & FAMILY'),
    'streaming_platforms',(select count(distinct platform) from streaming_views),
    'production_mutations',0
  ) value
)
select 'summary' section,value from summary
union all select 'decision_counts',coalesce(value,'[]'::jsonb) from decision_counts
union all select 'movie_category_counts',coalesce(value,'[]'::jsonb) from movie_category_counts
union all select 'series_category_counts',coalesce(value,'[]'::jsonb) from series_category_counts
union all select 'streaming_platform_counts',coalesce(value,'[]'::jsonb) from streaming_counts
union all select 'kids_family_counts',value from kids_family_counts
union all select 'removed_provider_categories',coalesce(value,'[]'::jsonb) from removal_details
union all select 'manual_content_review',coalesce(value,'[]'::jsonb) from manual_review_details
union all select 'qa_invariants',value from qa_invariants;