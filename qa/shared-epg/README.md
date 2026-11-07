# Shared US/UK EPG repair

Runs every six hours and on changes to its workflow or generator. Reads the current shared guide and adds only verified non-overlapping US/UK programme intervals. Existing current/future programmes, NZ/AU guide data, continuous labels, catalogue and playback bindings remain intact. Both XMLTV and per-channel short files are published. Concurrent-writer guard refuses stale promotion. Feed failure retains the last published data.

The manifest records the customer catalogue at 7 November 2026. Regenerate it from the active delivery release when channels change. Channels with no supported exact identity remain unmodified. Country, East/West and +1 are preserved; duplicate SD/HD candidates must agree over the next day. UK mixed-market sources accept explicit UK labels and a narrow UK brand allowlist. No generic foreign channel is inferred from a file's country name.

Tests: `python -m unittest test_repair_missing -v` (requires lxml==5.4.0). Actual mapping evidence is written to `epg/shared-us-uk-repair.json` on epg-data. A current schedule proves listings availability, not video content identity on every receiver.

History older than 48 hours is pruned only for targets with a validated replacement source. Other channels and all current/future intervals are preserved.
