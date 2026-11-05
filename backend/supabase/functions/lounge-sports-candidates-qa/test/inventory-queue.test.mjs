import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const sql = readFileSync(new URL("../../../qa-patches/20261105_best_inventory_queue_fairness.sql", import.meta.url), "utf8");

test("review-only importer patch keeps production changes manual", () => {
  assert.match(sql, /REVIEW CANDIDATE ONLY\. DO NOT APPLY/);
  assert.match(sql, /CREATE OR REPLACE FUNCTION lounge_sync\.sync_best_inventory_batch/);
});

test("oldest live/series job wins over fresh zero-percent movie import", () => {
  assert.match(sql, /ORDER BY started_at ASC, poll_id ASC\s+LIMIT 1\s+FOR UPDATE SKIP LOCKED/i);
  assert.doesNotMatch(sql, /ORDER BY\s*\(next_offset::numeric\/expected_count\)/i);
  const jobs = [
    { kind: "movies", pollId: 350, at: "2026-11-04T22:44:00Z", progress: 0 },
    { kind: "live", pollId: 145, at: "2026-11-02T02:08:00Z", progress: 16000 / 16140 },
    { kind: "series", pollId: 147, at: "2026-11-02T02:08:00Z", progress: 8500 / 8564 },
  ];
  const old = [...jobs].sort((a,b)=>a.progress-b.progress||a.pollId-b.pollId);
  assert.equal(old[0].kind, "movies", "frozen production policy starves nearly complete live import");
  const revised=[...jobs].sort((a,b)=>a.at.localeCompare(b.at)||a.pollId-b.pollId);
  assert.equal(revised[0].kind, "live");
  assert.equal(revised[1].kind, "series");
});

test("stale provider response fails job rather than confirming bad inventory", () => {
  assert.match(sql, /Provider HTTP response expired or unavailable/);
  assert.match(sql, /SET status='failed'/);
  assert.match(sql, /IF actual_count <> job.expected_count THEN/);
  assert.match(sql, /status='review_required'/);
});
