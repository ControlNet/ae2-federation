# DESIGN implementation audit (2026-10-02)

## Scope and verification

Reviewed the worktree at `b592340` against `DESIGN.md`, production Java/UI resources, native contract documentation,
acceptance and compatibility records, and recent knowledge notes. The worktree was initially clean. This audit changes
no implementation or design requirements. Findings below distinguish current code behavior from historical evidence.
No new synthetic test fixtures were introduced.

Executed from the repository root:

```sh
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:test
python3 -B tools/dev_gametests.py craftingremoterequest craftingremotechain craftingremotecancel craftingremotemissingretry endpointfederationfaceclaimkeepsdomain energysharedmutual
```

Results: 88 JUnit suites, 386 tests, zero failures/errors/skips; six selected GameTests passed in 24 seconds.
GameTest log: `build/dev-gametest.log`. These are development checks, not a new full evidence gate, client UI run,
third-party compatibility qualification, restart gate, or scale/soak result. The GameTests use existing native AE2
fixtures; their passing results verify the implementation's current semantics, not agreement with every design clause.

All Java paths below are relative to `common/src/main/java/space/controlnet/ae2federation/`.

## High-impact behavior differences

### Remote crafting has acquired a separate demand coordination layer

DESIGN 6.1 explicitly withdraws the default model of a new provider-side job, fixed provider-side materials and a custom
result receiver. Sections 6.3, 6.5 and 6.6 require a justified native integration and corresponding planning, missing
materials, CPU, cancellation and result semantics.

Current `crafting/remote/CraftingProjection.java` publishes emit-only outputs, no remote patterns. The consumer CPU
waits for the output. `RemoteCraftingService` polls aggregate demand every five ticks, calculates and submits a separate
native job on the provider, and retries missing-material or unavailable-CPU outcomes after 40 ticks.
`RemoteCraftingRequester` relays results to consumer storage, retains surplus at the provider, persists link-associated
amount/delivered counters, and participates in cancellation. Both planners and CPUs are real AE2 objects; the difference
is the new orchestration and player-visible contract, not a replacement implementation of AE2's planner.

`RemoteCraftingGameTests.craftingRemoteMissingRetry` explicitly asserts that a consumer job starts without provider
materials and keeps one consumer CPU busy until materials arrive. Thus the missing-material result of the remote plan
is not forwarded as the consumer's initial native missing-material preview. The successful development test confirms
this behavior. `docs/architecture/native-crafting-contract.md` documents the new path, but DESIGN chapter 6 has not been
reconciled with it. Do not call the emit-only provider a fabricated zero-input pattern: it has no patterns at all.

### Existing crafting results do not check a remaining Federation connection

DESIGN 15.4 allows existing return responsibility to survive normal rule changes, but explicitly forbids bypassing a
physical disconnection. `RemoteCraftingService.deliver` only calls `CraftingBindingService.gridIfPresent` and then inserts
into the consumer's native storage. `gridIfPresent` intentionally returns a previously observed Grid even when no rule
still involves it; `CraftingFederationDomainObserver.grid` checks its settled identity, not a common domain.

Static call-path finding: when both Grids remain loaded after their final Federation connection disappears, an existing
provider job has a result-delivery path that does not enforce connectivity. Retaining permission to return and retaining
a usable physical route must be distinguished. The exact disconnect-during-job case was not independently reproduced
in this audit and is not established by the six passing tests.

### Multiple crafting sources are selected by identity, without fallback

DESIGN 6.4 says distinct providers retain their origins and participate in native selection. In
`RemoteCraftingService.serveDemand`, candidate bindings for the same output are sorted by provider UUID and only
`bindings.getFirst()` is used. Missing materials or no usable CPU schedule another attempt against that same first
candidate; there is no attempt of the next candidate. Static implication: a second capable provider cannot satisfy
that demand while the first candidate remains offered but cannot execute. The consumer's own patterns also completely
exclude the same key from remote projections. Existing remote-request/chain tests do not cover multi-provider fallback.

## Missing or reduced player-facing features

### Storage policy terms cannot be edited in the current UI

Later design clarification: `crafting-pattern-projection-design-2026-10-02.md` records the agreed simplification
to three-state storage/crafting rules and explicitly no filters in that version. It is marked not implemented.
The comparison below remains a finding against the older DESIGN.md, not a requirement to restore filter editing.
Current code retains filter enforcement, but the newer agreed product scope takes precedence for future work.

DESIGN 4.4, 4.5 and 17.5 require configurable operations, filters/resource types and an independent re-export switch.
`policy/PolicyRule` and storage dependency/filter code support these fields. However,
`client/menu/FederationTopologyView.row` exposes only the enabled switch, and `terms` renders read-only labels.
`FederationDomainPolicyAction` has no operations/filter/re-export edit action. Session `setPolicy`/`apply` preserve
existing terms and only change enabled. New storage rules default to VIEW+INSERT+EXTRACT, all resources, re-export off.

Consequently backend tests can exercise chain sharing and filters while a normal player cannot configure them through
the shipped workspace. Backend support must not be counted as completed UI support. Mapping copy/paste from DESIGN
17.3 also has no dedicated action/control; the Provider's settings import/export forwards native configuration rather
than its Endpoint mapping graph.

### Domain membership changes invalidate the screen instead of refreshing it

DESIGN 17.7 requires an open entrance to refresh its current domain after a split/merge. Session `context` and
`contextNode` are final, selection members are captured on open, and `contextCurrent` rejects a changed domain
generation. The language resources explicitly tell the player to reopen. This safely rejects stale edits but does not
deliver automatic screen rebinding. Ordinary topology changes that preserve the relevant generation need not invalidate
the session; the difference concerns changed membership/context, not every cable update.

### Processing flow quantities lose resource units

DESIGN 16.6 forbids adding quantities of different resource types into a single throughput total; 17.2 asks for actual
resources and their own units. `ProviderObservationRegistry.recordSend`/`recordAggregateReturn` retain resource/unit
information in the detailed transport meter, but also call `recordLaneFlow` with only a raw amount.
`LevelObservabilityService.LaneKey` consists of Provider, Lane and return direction, without resource or unit.
`PairFlowWindow` adds these amounts. Session `laneTotals` and `FederationProcessingGraph` display that sum as
"last 5 seconds: sent ... / returned ..." with no unit. Mixed item/fluid lanes therefore produce a meaningless total.
The network overview mostly shows event counts, which is distinct from this incorrect quantity aggregation.

## Performance implementation and acceptance gaps

- DESIGN 16.3 calls for topology recomputation and capability publication to be spread across ticks.
  `domain/FederationDomainRegistry.flush` processes pending affected components synchronously. The standard budget is
  4,096 node visits and 24,576 port visits per component. Exceeding it removes affected domains and marks
  `BUDGET_EXHAUSTED`; it is not a resumable per-tick work queue. Multiple components are processed in the same flush.
- DESIGN 4.8/16.2 calls for indexed affected-policy work. `CraftingFederationDomainObserver.relationships` enumerates
  every ordered member pair of every domain before `CraftingBindingService.reconcileAll` filters configured policies.
  A single n-member domain therefore still allocates n*(n-1) candidate crafting relationships even for sparse policies.
- The processing canvas sends at most the first six Providers plus the selected Provider
  (`MAX_PROCESSING_PROVIDERS`, `processingProvidersJson`). Related scope caps additional networks at 64 and reports
  truncation. These are presentation limits, not proof of large-graph virtualization or a runtime Lane limit.
- DESIGN 19.9-19.13's matched three-layout late/ultra tests and long mixed soak remain unqualified in the maintained
  reports. Recent microbenchmarks and small runs must not be treated as those missing gates.

Important documentation correction for future audits: `docs/acceptance-matrix.md` and `docs/testing/commands.md` still
repeat the old "Federation 27/256 jobs, 0/3" checkpoint. The newer opening section of
`docs/benchmarks/task-37.md` records successful small Federation warmup/sample runs after identity fixes, including
`4f81513` with 5,514 sample jobs. It still explicitly lacks a same-source three-layout comparison, late/ultra and soak.
Do not present the historical warmup failure as a current reproduced bug. Likewise, acceptance text saying no manual
play has happened conflicts with the later `gui-manual-test-fixes-2026-10-02.md` account; that does not establish final
formal UI acceptance either.

## Intentional later decisions not fully reflected in DESIGN

- The user-confirmed Endpoint design recorded in `endpoint-federation-face-design-2026-09-29.md` assigns one real
  Federation face and five faces on the same backend ME subnet. The subnet does not join the domain merely through
  the Endpoint. Mode follows the upstream attached to the Federation face. DESIGN 3.1/13.2 still describe the physical
  layout as undecided and caution against automatically applying the Provider layout.
- Processing now requires an explicit mapping, exclusive Claim, distinct source/target Grids and the Provider's own
  Federation face in the Endpoint's domain. It requires no PROCESSING pair rule. `PolicyCapability` contains only
  STORAGE, CRAFTING and ME_POWER and drops legacy PROCESSING records on load. DESIGN 4.3/11.5 and several runtime
  descriptions still mention Processing Policy authorization. This is a recorded product change, not an accidental
  missing authorization check.
- Energy is already symmetric shared native `EnergyOverlayGrid`, matching the updated DESIGN 8.4. Do not list
  "missing directional energy" as a defect. Older directional wording in 4.4 should be harmonized. The current UI uses
  a quartz-style link and beads, superseding 8.4's green glow description; the knowledge note records user selection.
- AE2/LDLib2 versions and the LDLib2 UI framework are now selected, while the opening DESIGN metadata still says
  selection is pending. Build pins are AE2 19.2.17 and LDLib2 2.2.34, with separately documented loader minimum ranges.
- The project has block models/textures, dedicated Provider UI, related-domain read-only scope and world highlighting
  beyond the early provisional presentation. These do not by themselves constitute design violations.

## Major architectural matches

One common source/resource implementation and a thin NeoForge 1.21.1 target; overworld-owned global PolicySavedData;
independent native Grids; native storage projections with provenance/dependency code; per-Endpoint Provider lanes
extending real PatternProviderLogic and calling super.pushPattern; exclusive Endpoint claims and retained late-return
ownership; LDLib2 XML/LSS screens; and native energy overlay connections are present. There is no need to characterize
the whole project as a stub or assume core Processing still lacks independent native execution contexts.
