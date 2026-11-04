# Lounge TV Constitution

## Core Principles

### I. Production Safety
Production application, backend, configuration, data, signing identity and published artifacts
MUST remain unchanged during investigation and QA. Production changes MUST require explicit
owner approval for the exact reviewed candidate. A read-only audit MUST NOT trigger builds,
deployments, release workflows or live data writes. This protects customers and installed devices.

### II. QA-First Development
Every functional change MUST begin with a recorded baseline, acceptance criteria and a QA plan.
QA MUST use an isolated application identity and signing key, and test accounts or fixtures.
Backend isolation MUST be verified separately; a QA package alone does not isolate production.
Relevant automated checks and real target-device checks MUST pass before release approval.
Missing evidence MUST be reported as unverified, never treated as a pass.

### III. Playback Reliability
Changes affecting playback MUST exercise live tune, channel changes, reconnect, VOD resume,
series continuation, audio, subtitles and lifecycle recovery as applicable. Engine and provider
versions, device, stream type and failures MUST be recorded without secrets. Timeouts and retries
MUST be bounded and user-visible recovery MUST remain possible. Existing working streams MUST
retain their headers, DRM requirements, provider link resolution and saved progress semantics.

### IV. Regression Prevention
Bug fixes MUST include a focused regression check that fails for the original defect where
practical. Shared playback source revisions and applied patches MUST be recorded and consistent
between QA and release. Required checks MUST block release; historical green checks on another
commit MUST NOT establish correctness of a new candidate. Device-only behavior MUST have a
recorded manual test when automation cannot verify it.

### V. Catalogue Integrity
Catalogue transformations MUST be source-scoped, deterministic and covered by representative
fixtures. Unknown providers and incomplete metadata MUST retain safe access to content.
Channel deduplication, category hiding, ordering and count changes MUST document their matching
rules and expected effect. Playback references, favourites, history and recovery mappings MUST
remain valid. Heuristic quality rankings MUST NOT be represented as verified stream reliability.

### VI. Sport Lounge Accuracy
Fixtures MUST have stable identity, explicit time-zone handling, provenance and bounded freshness.
Tests MUST cover NZ date boundaries, daylight-saving transitions, cancellation and status changes.
Discovery fixtures, tentative channel matches and verified playable streams MUST be distinguished.
Ambiguous matches MUST permit user selection; no match MUST NOT imply a confirmed broadcast.
Reminders and event navigation MUST handle stale events and preserve event identity end to end.

### VII. Artwork Quality
Event artwork, team crests and league marks MUST match the intended event or entity. Unknown or
missing artwork MUST use an intentional fallback without suggesting false official attribution.
Artwork changes MUST be visually inspected on target TV layouts for cropping, contrast, legibility,
aspect ratio and focus states. Asset sources and usage permission MUST be documented; automated
URL or parser checks alone MUST NOT count as visual approval.

### VIII. Security
Credentials, provider passwords, service-role keys, signing materials and linked-project metadata
MUST NOT enter source control, reports or logs. Public client identifiers MUST be distinguished
from privileged secrets. Backend endpoints MUST validate input, authorization, permitted origins
and upstream access as appropriate, with bounded request size, timeouts and abuse controls.
Security claims MUST be supported by inspected code and configuration. Local untracked Supabase
metadata MUST be preserved and excluded from staging unless individually reviewed and sanitized.

### IX. Controlled Releases
Release approval MUST identify an immutable source commit, tested artifact hashes, application ID,
signing certificate, increasing version and rollback or forward-recovery plan. The published
artifact MUST be the exact approved candidate. Backend releases MUST have independent change
review and deployment approval. Branch checks and environment approval rules MUST be verified on
GitHub; workflow comments alone MUST NOT establish enforcement. No push, merge, build, deployment
or release is authorized by this constitution itself.

### X. Minimal, Reversible Changes
Changes MUST have one stated purpose, a bounded diff and a recovery plan. Existing local work,
untracked files and the chore/adopt-spec-kit branch MUST be preserved. Fetch and compare MUST
precede synchronization proposals. Reset, overwrite, rebase, merge, deletion or branch replacement
MUST require explicit owner approval. Unrelated refactors and speculative fixes MUST be deferred.

## Engineering Constraints

The baseline MUST record repository remote, branch, full HEAD, fetched target commit and tracked
and untracked status. Main, latest development, published release source and deployed backend MUST
be reported separately when they differ. Local equality with main MUST NOT imply equality with the
latest release. Deployed backend parity requires read-only deployment evidence; checked-in source
alone is insufficient. Every report MUST distinguish implemented, tested, visually verified,
incomplete and unknown behavior. No production credentials may be copied to establish evidence.

## Development Workflow and Quality Gates

1. Fetch references, inspect local changes and compare main, intended development and release refs.
   Report differences before proposing corrective action; preserve local work throughout.
2. Define the feature or defect with acceptance criteria, risks, affected contracts and recovery.
   Use Spec Kit specification, clarification, plan and tasks commands only within approved scope.
3. Implement the smallest approved change in isolation and run relevant tests without production
   writes. Building an APK or deploying requires task-specific authorization.
4. Review source, dependencies, secrets exposure, catalogue effects and playback regressions.
   Record automated results against the exact commit plus required target-device and visual QA.
5. Present candidate identity, evidence, limitations and recovery to the owner. Only explicit
   release authorization permits publishing, and only the exact approved artifact may be released.

## Governance

This constitution governs Lounge TV engineering work subject to explicit owner task restrictions.
Adoption establishes required standards; it does not certify existing code as compliant.
Every plan and review MUST identify applicable principles and evidence or unresolved gaps.
Exceptions MUST document scope, risk, compensating controls, expiry and explicit owner approval.
Amendments MUST explain the change, compatibility impact and any transition work, receive owner
approval, and update this document's version and amendment date. Semantic versioning applies:
MAJOR for incompatible principle removal or redefinition; MINOR for new or materially expanded
requirements; PATCH for non-semantic clarification. Templates consume the constitution at runtime
and MUST NOT be changed merely to duplicate these rules.

**Version**: 1.0.0 | **Ratified**: 2026-11-04 | **Last Amended**: 2026-11-04
