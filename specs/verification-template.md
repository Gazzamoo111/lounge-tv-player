# Feature verification record

This is a reusable template, not completed verification evidence.
Copy into the selected specs/<number>-<short-name>/verification.md when verification is authorized.

## Identity and scope

- Feature ID and specification revision:
- Implementer and independent verifier:
- Target branch and exact implementation SHA:
- Specification/plan/task document ref:
- Approval evidence and permitted operations:
- Diff reviewed and task IDs covered:

## Evidence

| Check / acceptance criterion | Method | Source SHA or artifact identity | Result | Evidence |
|---|---|---|---|---|
| Fill from specification | Source / test / historical CI / device / visual | Full SHA | Pass / Fail / Unverified | Link or sanitized output |

Record commands actually run, environment and test totals. Identify inherited historical evidence.
For APK evidence, record run/attempt, package, versionCode, signing certificate and artifact hashes.
Distinguish a GitHub artifact archive digest from the APK file digest. Do not download, build,
install or deploy without applicable authorization. Record device/model and stream type for any
real-device test; never include provider credentials.

## Findings and remaining limits

- Implemented and verified behavior:
- Failed or incomplete criteria:
- Unavailable checks, including physical-device and visual QA:
- Regression, catalogue, backend/security and recovery concerns:
- Required follow-up task IDs and owners:

## Verdict and handoff

- Verdict: Pending / Changes required / Verified with stated limits.
- Exact scope covered and unresolved risks:
- GPT Work response and follow-up SHA:
- Gary's acceptance evidence, if provided:
- Separate build/push/merge/release authorization, if provided:

This record does not grant production, release or merge approval.
