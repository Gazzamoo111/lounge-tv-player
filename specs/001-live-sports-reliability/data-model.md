# Proposed data model

No database migration is prescribed. Prefer existing models and isolated pure transformations; GPT Work must justify persistence changes.

| Entity | Identity and required fields | Validation/relationships |
| --- | --- | --- |
| Fixture | source+eventId, sport/competition/division, participant IDs/aliases, UTC interval, status/observedAt | Missing end explicitly nullable; inferred duration policy recorded; one identity through hero, reminder and candidates |
| CatalogueSnapshot | source, revision, import state/completedAt, observedAt | Latest processing import is not last successful snapshot; absent/removed inventory distinguishable |
| LogicalService | source-scoped stable service ID, regional identity, display name, admission/exclusion reasons | Regional editorial identity evidenced, not inferred from prefix alone |
| StreamVariant | source+providerItemId, logicalServiceId, categoryId, EPG mapping, quality claims | Preserve playback resolver references, favourite/history links; measured quality separate |
| ProgrammeEvidence | source/programmeId, mapped service, participants/competition, UTC interval, observedAt | Mapping confidence, mismatch status and overlap evaluated before recommendation |
| CandidateDecision | eventId+serviceId+snapshot/version, tier, reasons, evidence, expiry | Verified -> plausible/unverified when expired; contradiction/exclusion -> suppressed |
| PlaybackObservation | variantId, sampledAt, device/engine/network, first frame, stalls, measured tracks | Unknown until authorized sample; must not imply future reliability |
| VisibilityDecision | source+service/variant identity, reason, ruleVersion, effective time | Excluded AU ESPN1 remains auditable; AU ESPN2 positive control |
| TuneTrace | session-local traceId, generation, selection ID, monotonic milestones | superseded generations cannot complete current state; no credentials |
| HeroState | now, eligible fixture keys, selected key, rotation cursor, lastRefresh | Keys survive refresh, terminal/stale entries removed without new input emission |

State transitions: candidate unknown -> plausible -> verified only with qualifying evidence; expiry downgrades, contradictions suppress. Temporary soccer feed absent -> eligible during evidenced window -> expired. Tune idle -> pending debounce -> resolving -> preparing/buffering -> rendered or failed; any state -> superseded/disposed; callbacks cannot resurrect superseded state. Hero PRE -> fresh IN -> terminal, with stale IN losing live eligibility.
