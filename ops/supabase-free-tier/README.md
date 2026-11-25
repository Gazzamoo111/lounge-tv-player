# Lounge TV free-tier optimisation

Production Supabase project: `liqocisegeqzbyuqloql`.

## Change 01: change-only source presence refresh

The 15-minute scheduled VOD presence job previously issued an UPDATE for every present matched movie/series source even when the source name, extension and last-seen timestamp were unchanged. The patch skips those unchanged row writes while preserving missing/restored detection, re-probe queues and the JSON return structure.

- Apply: `01_change_only_vod_presence.sql`
- Rollback: `rollback_01_original_vod_presence.sql`
- Scope: a database function, not the Lounge TV 2.0 application branch.
- No cron jobs disabled, no records deleted.

## Validation

Run read-only checks:

```sql
select position('s.last_seen_at IS DISTINCT FROM i.last_seen_at' in pg_get_functiondef('lounge_core.refresh_best_vod_source_presence()'::regprocedure)) > 0 as change_only_guard_installed;
select jobid, jobname, active, schedule from cron.job where jobid=26;
select pg_database_size(current_database()) as database_bytes;
```

Compare the current and following-day counters in `pg_stat_user_tables` for `lounge_core.sources`. Verify the job 26 execution results and logs; error rate must remain zero. Roll back immediately if catalogue source status or probe queues behave unexpectedly.

## Future changes not included here

Retired-release compaction, metadata reduction, log retention, physical table reclamation, and sports-query optimisation require isolated QA and rollback/backup validation. They are not executed by this change. Uncached egress also requires a verified Supabase usage breakdown, not only query-row statistics.

## Pre-existing provider polling issue detected during verification

At ~12:06 NZDT on 2026-11-09 the hourly BEST provider polls began returning HTTP 451 for live, movies and series. This started before the function optimisation was deployed. The cron entries are marked `succeeded` because the scheduled SQL executed, but the recorded provider poll status is `failed`.

This optimisation does not alter the provider HTTP requests, credentials or schedules. Treat the 451 issue as a separate incident; do not bypass a provider restriction or assume the upstream streams are healthy. Before any catalogue migration/cleanup, confirm that the source import pipeline has recovered and record a clean baseline.

As of verification, `pg_database_size(current_database())` remains about 692.5 MB. A standard, online `VACUUM (ANALYZE)` on `net._http_response` reclaimed no physical relation size. No deleted records, disabled jobs or customer deployments were used.
