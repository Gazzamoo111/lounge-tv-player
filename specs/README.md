# Shared Lounge TV specifications

Read the [constitution](../.specify/memory/constitution.md) and
[GPT Work / Codex workflow](../docs/SPEC_KIT_WORKFLOW.md) before feature work.

This directory is the GitHub source of truth for approved scope, implementation plans, task status
and verification. No application feature is authorized by this installation.

For a new approved feature, reserve an unused sequential number and create
<number>-<short-name>/spec.md, plan.md, tasks.md and verification.md. Use the Spec Kit templates in
[.specify/templates](../.specify/templates/) and the [verification template](verification-template.md).
Numbers organize documents; they do not require renaming GPT Work's application branches.

Each feature records: feature ID; document owner; current status; target branch and full source
commit; acceptance/authorization evidence; open questions; and related PR/run/artifact links.
Suggested statuses: Draft, Scope accepted, Plan accepted, Implementing, Ready for verification,
Verified with stated limits, Blocked, Released. Scope acceptance and release authorization are
separate decisions. Record who accepted what, when, and the relevant evidence link.

Tasks use stable IDs (T001, T002, ...), an assignee, dependency and a completion check. Keep tasks
unchecked until evidence supports completion. Verification reports distinguish tests executed,
historical CI, source inspection, device checks, visual inspection and unavailable checks.
Never mark a feature released solely because tasks are checked or CI is green.

The current adoption only installs governance and document conventions. It does not fabricate a
completed application feature specification. GPT Work remains the primary implementation owner.
