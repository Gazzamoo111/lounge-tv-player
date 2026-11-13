-- Lounge TV | Live catalogue rebuild | read-only QA gates
-- Run on the connected Supabase project. NO SQL in this file mutates customer data.
-- This reports staged readiness; a passing stage does not replace authenticated device QA.

with stage as (
  select
    count(*) total,
    count(*) filter (where publish_candidate) eligible,
    count(*) filter (where not publish_candidate) held,
    count(*) filter (where duplicate_of is not null) duplicate_holds,
    count(*) filter (where publish_candidate and review_required) pending_reviews,
    count(*) filter (where publish_candidate and source_count=0) shortlisted_without_source,
    count(*) filter (where publish_candidate and original_category='Pay-Per-View') shortlisted_ppv,
    count(*) filter (where publish_candidate and canonical_key ~* '^uk-(rte|tg4|virgin-(one|two|three|four))') shortlisted_irish,
    count(*) filter (where publish_candidate and proposed_name ~ 'ᴿᴬᵂ') shortlisted_raw_labels
  from lounge_core.live_catalogue_rebuild_20261113
),
source_stage as (
  select
    count(*) filter (where shortlist_for_probe) shortlisted_source_rows,
    count(*) filter (where shortlist_for_probe and probe_status<>'verified') unverified_source_rows,
    count(distinct proposed_canonical_key) filter (where shortlist_for_probe) shortlisted_new_targets
  from lounge_core.live_rebuild_source_candidates_20261113
),
released as (
  select
    count(*) filter (where admission='keep') live_rows,
    count(*) filter (where admission='keep' and category='PPV') ppv_rows,
    count(distinct live_channel_id) filter (where admission='keep') distinct_live_rows
  from lounge_delivery.live_release_items
  where release_id=(select id from lounge_delivery.catalogue_releases where state='active' order by activated_at desc nulls last limit 1)
),
gates as (
  select 'original_taxonomy_3369_preserved' gate, st.total=3369 pass from stage st
  union all select 'nine_categories_and_54_editorial_subfolders',
    (select count(distinct category)=9 and count(*)=54 from lounge_core.live_rebuild_category_order_20261113)
  union all select 'no_customer_shortlist_ppv', st.shortlisted_ppv=0 from stage st
  union all select 'no_customer_shortlist_irish', st.shortlisted_irish=0 from stage st
  union all select 'no_customer_shortlist_raw_labels', st.shortlisted_raw_labels=0 from stage st
  union all select 'no_customer_shortlist_without_source', st.shortlisted_without_source=0 from stage st
  union all select 'no_duplicated_display_names_on_shortlist',
    (select count(*)=0 from (
      select proposed_category,proposed_subcategory,
             lower(regexp_replace(proposed_name,'[^[:alnum:]]','','g')) normalized_name
      from lounge_core.live_catalogue_rebuild_20261113 where publish_candidate
      group by 1,2,3 having count(*)>1
    ) duplicates)
  union all select 'complete_24_7_classification_review', st.pending_reviews=0 from stage st
  union all select 'all_proposed_new_sources_verified', ss.unverified_source_rows=0 from source_stage ss
  union all select 'zero_ppv_in_published_live', rel.ppv_rows=0 from released rel
  union all select 'published_id_integrity', rel.live_rows=rel.distinct_live_rows from released rel
  union all select 'approved_nfl_nba_mlb_nhl_featured_order',
     (select string_agg(proposed_name,'|' order by proposed_sort_order)='NFL Network|NFL RedZone|NBA TV|MLB Network|NHL Network|ESPN|ESPN2|FS1|FS2|CBS Sports Network'
      from lounge_core.live_catalogue_rebuild_20261113
      where proposed_category='Sports' and proposed_subcategory='US Major Leagues' and publish_candidate)
)
select gate,case when pass then 'PASS' else 'BLOCK' end as result
from gates order by gate;
