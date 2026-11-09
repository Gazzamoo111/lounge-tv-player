-- READ ONLY. pg_stat_statements is cumulative, not a billed egress meter.
SELECT stats_reset FROM pg_stat_statements_info;
SELECT calls,rows,round(total_exec_time::numeric/1000,1) AS total_seconds,
       left(regexp_replace(query,E'[\n\r\t ]+',' ','g'),250) AS query_excerpt
FROM pg_stat_statements
WHERE dbid=(SELECT oid FROM pg_database WHERE datname=current_database())
ORDER BY rows DESC LIMIT 25;

SELECT j.jobid,j.jobname,j.active,j.schedule,
       count(r.*) FILTER(WHERE r.start_time>now()-interval '24 hours') AS runs_24h,
       count(r.*) FILTER(WHERE r.start_time>now()-interval '24 hours'
                         AND r.status<>'succeeded') AS failed_cron_24h
FROM cron.job j LEFT JOIN cron.job_run_details r ON j.jobid=r.jobid
GROUP BY j.jobid,j.jobname,j.active,j.schedule ORDER BY j.jobid;

-- The cron status does NOT prove that queued HTTP or background provider imports succeeded.
SELECT catalogue_type, max(queued_at) FILTER(WHERE status='ok') AS last_good_poll,
       max(queued_at) FILTER(WHERE status='failed') AS last_failed_poll,
       count(*) FILTER(WHERE status='failed'
                       AND queued_at>now()-interval '24 hours') AS failed_polls_24h
FROM lounge_sync.best_provider_poll_runs GROUP BY 1 ORDER BY 1;

SELECT pg_database_size(current_database()) AS database_bytes,
       pg_total_relation_size('net._http_response'::regclass) AS http_bytes,
       pg_total_relation_size('cron.job_run_details'::regclass) AS cron_bytes;

-- Supabase's organisation-level Usage dashboard is the source of truth for
-- cached and uncached egress, including Shared Pooler traffic.
