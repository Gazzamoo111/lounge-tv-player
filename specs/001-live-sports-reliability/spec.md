# Feature Specification: Lounge TV Live Sports Reliability Overhaul

**Feature ID**: 001-live-sports-reliability  
**Created**: 2026-11-05 (Pacific/Auckland)  
**Status**: Draft specification; no implementation authorized  
**Document owner**: Codex; implementation owner: GPT Work  
**Target branch**: Existing GPT Work development branch, currently `feature/lounge-major-events-home-20261103`, source `d081d77cbec6f5f498a16c58250d96f440842c90`. Reconfirm before implementation. No replacement branch prescribed.  
**Input**: Gary's seven-part Lounge TV live sports reliability scope in this chat. Authorizes specification and read-only investigation only.  
**Related records**: [Plan](plan.md), [evidence](research.md), [acceptance matrix](acceptance-matrix.md), [verification](verification.md).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find the correct match (Priority: P1)
A viewer opens a fixture and sees a small, honest list of channels carrying that fixture, followed by clearly labelled possibilities and an expandable discovery list.

**Why this priority**: Wrong confident recommendations undermine the purpose of Sport Lounge.

**Independent Test**: Replay a frozen catalogue and programme snapshot for a named fixture; compare displayed tiers, order and explanations with the matrix.

**Acceptance Scenarios**:
1. **Given** exact fixture and overlapping programme evidence, **when** candidates are shown, **then** the correct channel precedes a higher-quality generic network and its evidence is visible.
2. **Given** only broadcaster rights or network affinity, **when** candidates are shown, **then** none is called verified or asserted to carry this fixture live.
3. **Given** unrelated Stan/Paramount feeds or inactive PPV feeds, **when** opening a fixture, **then** these are absent from recommendations; valid beIN and relevant UK alternatives remain discoverable.
4. **Given** an unavailable backend, **when** opening a fixture, **then** existing safe local discovery and manual channel selection still work with honest uncertainty.

### User Story 2 - Browse usable soccer channels (Priority: P1)
A viewer selects Sports > Soccer and finds curated live soccer services and current event feeds without hundreds of inactive entries.

**Why this priority**: Correct suggestions require access to the underlying channels.

**Independent Test**: Build the customer list from a frozen current catalogue, then apply a rename, addition, removal and inactive-feed update.

**Acceptance Scenarios**:
1. **Given** several quality variants of one regional service, **when** browsing Soccer, **then** one logical service is shown with selectable alternatives and stable favourites.
2. **Given** 300 generic event feeds without current event evidence, **when** browsing Soccer, **then** zero of those feeds is automatically admitted.
3. **Given** AU ESPN 1 aliases and AU ESPN 2, **when** browsing, searching or opening recommendations, **then** AU ESPN 1 is absent and AU ESPN 2 remains available; operators can still audit excluded records.

### User Story 3 - Trust the guide and backups (Priority: P1)
Viewers can find beIN Australia 1/2/3 and appropriate Fox 501-507/Kayo alternatives, with accurate guides and measured playback status.

**Why this priority**: A matching name or guide alone does not establish a working broadcast.

**Independent Test**: Audit each service's identity, guide and observed programme independently, then exercise approved QA playback sequentially.

**Acceptance Scenarios**:
1. **Given** a channel labelled beIN AU 1 showing content different from its guide, **when** audited, **then** the mismatch is recorded and the channel cannot supply verified fixture evidence until resolved.
2. **Given** a UK-labelled beIN AU feed, **when** grouping channels, **then** its name does not establish either region or equivalence; verified service identity determines mapping.
3. **Given** a Fox/Kayo name or HD label, **when** showing an alternative, **then** no claim of reliability is made without current playback evidence.

### User Story 4 - Change channels without confusing frozen pictures (Priority: P1)
A viewer changes channels and sees the selected channel's transition state, never an old picture presented as the new programme.

**Why this priority**: Slow tuning and misleading still frames look like a broken service.

**Independent Test**: Record isolated and repeated remote presses, player milestones and rendered frames in an isolated QA app.

**Acceptance Scenarios**:
1. **Given** channel A is visible, **when** selecting B, **then** the UI immediately identifies B as loading and any retained A frame is concealed or explicitly labelled as previous content.
2. **Given** rapid channel presses, **when** the user stops, **then** the final channel wins; late callbacks cannot restore earlier content and provider-open bursts do not increase.

### User Story 5 - See timely Major Events (Priority: P2)
The homepage hero prioritizes current live headline events, offers varied upcoming events across seven days, and expires stale events.

**Why this priority**: Homepage promotion must remain timely and truthful.

**Independent Test**: Advance a controllable clock while keeping the original fixture collection unchanged.

**Acceptance Scenarios**:
1. **Given** both fresh live and upcoming eligible events, **when** ranking, **then** every live event ranks before upcoming events and variety is applied within those partitions.
2. **Given** cancelled, ended or stale live events, **when** time or status changes, **then** the hero removes them without requiring a new screen visit.
3. **Given** a live fixture with no confirmed channel, **when** displayed, **then** the action remains View event or Find channels; Watch Live is never inferred from fixture status.

### Edge Cases
Same teams on different dates; women/youth/reserve competitions; short aliases such as Spurs; reversed home/away; unknown opponents in combat/racing; overnight cricket; postponed events; missing end times; overlapping guide programmes; stale catalogue imports; missing guide IDs; guide shifted one hour; AU versus UK daylight saving; duplicate provider IDs across sources; offline refresh; app background/resume; removed favourite; late player frames; player engine handoff; no live events; fewer than twelve hero candidates.

## Requirements *(mandatory)*

### Functional Requirements
- **FR-001**: Rank fixture-specific evidence ahead of broadcaster, market, competition and quality preferences. Generic networks, fuzzy title overlap and one-team matches cannot independently establish verification.
- **FR-002**: Classify candidates as Verified fixture, Plausible broadcast or Unverified discovery. State the supporting evidence, its time and reason; distinguish fixture verification from successful playback observation.
- **FR-003**: Reject evidence for a different opponent, competition/division, scheduled date or region. Handle exact aliases with sport/competition context; ambiguous aliases remain unverified.
- **FR-004**: Suppress unrelated Stan/Paramount, inactive/expired PPV, replays, highlights, placeholders and known content-guide mismatches from fixture recommendations. Unknown activity is not active evidence.
- **FR-005**: Initially show at most six recommendations: verified first, then plausible only if fewer than six verified services qualify. With six verified and two plausible, show six verified initially and retain the two plausible under alternatives. Provide an explicit alternatives action for remaining eligible verified/plausible results and unverified discovery in separate groups, at most twelve items per page. Deduplicate logical services while exposing working variants. Do not discard a valid alternate because one network dominates.
- **FR-006**: Discover current beIN and relevant UK broadcasters by regional service identity and fixture evidence. Do not use a universal NZ/AU preference to exclude correct alternatives. Availability and rights are distinct from catalogue presence.
- **FR-007**: Provide customer-facing Sports > Soccer using current source-scoped catalogue data, reviewed service rules and current event evidence. Continuous soccer services may qualify without a current fixture; temporary feeds require current soccer event evidence. Preserve original provider references and existing sports navigation.
- **FR-008**: Deduplicate quality/name variants deterministically within an established service identity. Preserve independent regional/editorial services and selectable backups, favourites, history and stable navigation IDs.
- **FR-009**: Audit beIN AU 1/2/3 and UK-labelled variants individually for presence, customer visibility, guide mapping, time alignment and actual content. Correct only evidenced mapping defects during later authorized implementation; unsupported mappings stay explicitly unknown.
- **FR-010**: Audit Fox Sports AU 501-507 and Kayo backups individually for guide coverage, correct programme, tune success, buffering, resolution, frame rate, bitrate and audio. Report unavailable tests as unverified.
- **FR-011**: Permanently suppress AU ESPN 1 and its source aliases from all customer channel lists, counts, search, recommendations, hero actions, saved/last-channel reopening and direct user navigation. Retain AU ESPN 2 and non-AU ESPN services. Preserve backend inventory and exclusion reasons for audit; retain saved records but make excluded targets unavailable without tuning.
- **FR-012**: Measure channel-switch intent, debounce wait, resolution, player preparation, buffering, first frame and UI transition separately. Correlate every measurement and frame with the selected channel generation; late work must not affect the current selection.
- **FR-013**: Do not increase provider connection opens, concurrent connections or retry bursts. Preserve existing isolated/rapid-press behavior pending measured evidence for any proposed change. Never pre-open candidate streams to score them.
- **FR-014**: Show a clear loading/recovery state on tune, failure and engine transition; never imply that an old still frame belongs to the requested new channel. Preserve working playback, audio/subtitle settings and lifecycle recovery.
- **FR-015**: Refresh Major Events on entry, return from background and while visible; apply an independent clock-driven expiration. Use a rolling seven-day upcoming window, strict fresh-live priority and varied rotation within priority groups. Preserve selected event identity on refresh.
- **FR-016**: Remove ended/cancelled events immediately on received status; bound stale live status. No live badge or Watch Live claim can rely solely on an old IN status. Event navigation must revalidate identity and availability at click time.
- **FR-017**: Preserve all previously working Sport Lounge discovery, filters, fixture details, reminders, time display, artwork/fallbacks, manual selection and navigation. Preserve live playback, VOD resume, series continuation, seek, audio/subtitles, profiles, favourites/history and production configuration.
- **FR-018**: Deliver tests that fail on the original loose backend matches and authoritative client promotion, with immutable input snapshots, clock and expected outputs. Device, visual and streaming claims require separate evidence.

### Key Entities
- **Fixture**: Stable identity, sport, competition/division, participants, scheduled interval, status, region and provenance/freshness.
- **Service and variant**: Stable regional channel identity and its provider-specific playback references, exclusion state and alternatives.
- **Evidence**: Fixture/programme association, regional broadcaster information, timestamp, provenance and contradiction state.
- **Candidate decision**: Evidence tier, deterministic order, reasons and expiry for one fixture/service.
- **Switch trace**: Selected channel generation and timed transitions through display, preparation and playback.
- **Audit observation**: Channel identity, guide versus observed content, quality measurements, time and verification limitations.

## Success Criteria *(mandatory)*

### Measurable Outcomes
- **SC-001**: All acceptance-matrix cases pass; zero unrelated or time-conflicting candidates receive a Verified fixture label.
- **SC-002**: Every evidenced beIN/UK alternative in the frozen test corpus is discoverable within the documented alternatives flow; first view never exceeds six recommendations.
- **SC-003**: Soccer admits zero inactive generic event feeds in the 300-feed noise fixture, shows every curated eligible logical service once, and retains its backups.
- **SC-004**: AU ESPN 1 has zero customer-visible or tunable entry points in the matrix; AU ESPN 2 passes the same positive visibility checks.
- **SC-005**: All three AU beIN services, three UK-labelled variants, seven Fox services and sampled Kayo backups receive individual audit outcomes; no missing observation is reported as passed.
- **SC-006**: In at least 30 isolated and 10 rapid-switch QA trials per target engine/device, zero stale pictures are misrepresented, the final selected channel wins, and provider opens/concurrency never exceed the paired baseline. Transition feedback appears within 100 ms of selection; application-controlled switch overhead does not worsen its baseline 95th percentile by more than 100 ms. Provider-dependent first-frame latency is reported separately.
- **SC-007**: Hero ranking and expiry pass all fixed-clock tests; expired content leaves within 60 seconds while visible. No upcoming item precedes a fresh live item. Every eligible selected event appears once before repetition in an uninterrupted rotation cycle.
- **SC-008**: Every preservation case has automated or target-device evidence for the exact candidate revision before release approval.

## Assumptions
- Existing editorial headline eligibility remains the starting policy; this overhaul changes timeliness, trust and variety, not the homepage's editorial scope.
- Default evidence freshness, caps and audit sampling are specified in contracts and can be tuned only with recorded QA evidence.
- Catalogue presence establishes discoverability, not stream health. Current production catalogue reads may expose an older successful import; freshness is visible and never invented.
- This delivery is documentation only. GPT Work reviews feasibility and owns later implementation; Codex independently verifies. No build, push, merge, production mutation, deployment or APK replacement is authorized.
