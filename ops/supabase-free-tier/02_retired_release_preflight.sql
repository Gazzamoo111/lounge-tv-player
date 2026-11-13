-- READ-ONLY safety checks. Do not delete retired releases before externally verified backups and client/rollback tests.
WITH r AS (
  SELECT r.id,r.release_key,r.state::text AS state,r.created_at,r.activated_at,
         (SELECT count(*) FROM lounge_delivery.vod_release_items v WHERE v.release_id=r.id) AS vod_rows,
         (SELECT count(*) FROM lounge_delivery.category_members c WHERE c.release_id=r.id) AS category_members,
         (SELECT count(*) FROM lounge_delivery.live_release_items l WHERE l.release_id=r.id) AS live_rows,
         (SELECT count(*) FROM lounge_delivery.live_source_bindings b WHERE b.release_id=r.id) AS bindings
  FROM lounge_delivery.catalogue_releases r
)
SELECT * FROM r ORDER BY state,created_at DESC;

SELECT n.nspname AS referenced_schema,t.relname AS referencing_table,
       c.conname AS fk_constraint,pg_get_constraintdef(c.oid) AS constraint_definition
FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
JOIN pg_namespace n ON n.oid=t.relnamespace
WHERE c.contype='f' AND c.confrelid='lounge_delivery.catalogue_releases'::regclass
ORDER BY 1,2;

SELECT n.nspname AS schema_name,t.relname AS table_name,
       pg_total_relation_size(t.oid) AS bytes
FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
WHERE n.nspname IN ('lounge_delivery','lounge_core','lounge_sync','cron','net')
AND t.relkind='r' ORDER BY bytes DESC LIMIT 30;

-- Guardrails for a future separately reviewed retention migration:
-- 1. Never touch the active release.
-- 2. Keep at least one tested previous release and a verified recoverable backup.
-- 3. Do not run a destructive migration while provider polling is failing.
-- 4. Verify all five FK child tables and any non-FK references.
-- 5. Confirm physical storage reclamation can occur without blocking customers.
