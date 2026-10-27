# Remote crafting: migration to real providers and a compat layer (plan, 2026-10-26)

Owner decision, 2026-10-26: cross-network crafting moves to real provider objects registered on the consumer, with
dispatch accounting taken from CPU-side hooks in an isolated compat layer. The projection wrapper route is abandoned.
There is no wrapper fallback, and once the migration is done the old implementation is deleted completely. Background
and the options considered: `remote-crafting-architecture-2026-10-26.md`.

## Target architecture

- P's real `ICraftingProvider` objects are registered on C's crafting service with `addGlobalCraftingProvider`. Addons
  then recognise them by class, logic and markers without help.
- The core (ledger, router, held returns, reach, statistics) depends only on `compat/api`: internal events and
  interfaces. It never names an addon class or string.
- Addon knowledge lives in `compat/<mod>/` packages. Each translates its executor's dispatch into the internal
  "dispatch committed" event: consumer grid, provider, pattern, committed runs or expected outputs, and a job id where
  known. It also reports job lifecycle (ended or cancelled) where the core needs it for ledger cleanup.
- AE2 public events are used wherever they exist: `GridCraftingCpuChange` (CPU set changed), `ICraftingWatcherNode`
  (request and craftable changes), and `GridPowerStatusChange` / `GridBootingStatusChange`. AE2 19.2.17 has no event
  for pushes or pattern changes; see the events package in the sources jar.
- The internal event is shaped like a dispatch event we could propose to AE2 upstream. It may later become a small
  public API that addon authors call themselves.

## Compat layer rules

1. Packages:
   - `compat/api`: events and interfaces;
   - `compat/ae2`: hooks on AE2 itself, including today's `mixin/compat` AE2 mixins;
   - `compat/<modid>`: one package per addon;
   - `compat/jade`, `compat/jech`: UI integrations, unchanged.
2. A contract test fails when a file outside `compat/` names an addon package or class string. It works like
   `GuidePagesContractTest`.
3. Addon mixins go in their own mixin config. Its `IMixinConfigPlugin.shouldApplyMixin` applies a mixin only when its
   mod is loaded, and every hook uses `require = 0`. A mismatched addon version disables that adapter, never the game.
4. Prefer typed compile-only dependencies (Modrinth Maven, as already used for AppliedFlux and similar) over
   reflection by string.
5. Statistics, diagnostics and the ledger read only internal events.

## Phases (each green in CI and pushed before the next)

### P0. Coverage diagnostic (log-only, no behaviour change)

- Probes go in a separate compat-test mixin config, enabled by a system property; `compatTestJar` excludes the
  testmod mixins. They sit at:
  - AE2 `CraftingService.onServerEndTick` scope;
  - AE2 `CraftingCpuLogic.executeCrafting` and its `pushPattern` call;
  - `PatternProviderLogic.pushPattern`, `onPushPatternSuccess` and `sendStacksOut`;
  - DE `GridCraftingDispatchParticipant.dispatchStep` and `CraftingDispatchCommitter`;
  - the Neo ECO executors `ECOCraftingCPULogic` and `ECOSingleCraftingExecutor`, plus its batch path;
  - AAE `AdvCraftingCPULogic`;
  - LT `Ae2LtTimeWheelCraftingCpuLogic`;
  - provider registration and refresh, and return ingress.
- Each scene runs twice, federated and joined (`joinedBeforeOrder()`). Joined runs expose the fast paths the wrapper
  hides today. Profiles: baseline, data-energistics, neoecoae, advanced-ae, ae2-lightning-tech, neoecoae-advanced-ae,
  addons-all.
- Output: a coverage table of executor x path, covering context, quantity, bypasses, duplicates and return ingress.
- Known risk to measure: Neo ECO 21.2.10 itself injects into AE2's `CraftingCpuLogic.executeCrafting` (a cancellable
  inject in `Ae2CraftingCpuFastPathMixin`) and wraps its `pushPattern` call. So an AE2 CPU with Neo ECO installed may
  dispatch through Neo ECO code, not AE2's.
- Result: [remote-crafting-p0-dispatch-coverage-2026-10-26.md](remote-crafting-p0-dispatch-coverage-2026-10-26.md).
  Every push happened inside a crafting-service scope that named the right consumer.

### P1. Compat layer skeleton (no behaviour change)

- Create `compat/api` and the mod-gated mixin config plugin, and add the contract test.
- Move code that is unrelated to the crafting route:
  - the ExpandedAE target cache from `ae2/processing/FederationPatternProviderTargetCache` into `compat/expandedae`;
  - the DE guide sidebar check from `mixin/client/GuideNavBarMixin` into `compat/dataenergistics`;
  - `mixin/compat` to `compat/ae2`.
- The contract test may whitelist the crafting files that P4 deletes, until P4.

### P2. Executor adapters in shadow mode

- Write the adapters that emit "dispatch committed": AE2 native, Neo ECO, AAE, LT and DE. Use `@WrapOperation` on each
  executor's `pushPattern` call; batch protocols get hooks at their commit points.
- The wrapper still runs. A shadow check compares event totals with the wrapper ledger's pushes in every crafting
  GameTest and compat test. Any mismatch is a bug in an adapter.

### P3. Switch to real providers

- Register real providers on consumers, with a provenance registry that keeps mirror and native registrations apart.
  `RealCraftingProviders` currently tells imports apart with `isProjection`, and that test stops working.
- Fan pattern and priority refreshes out to mirrors, coalesced.
- Re-export (`CraftingReach`) keeps resolving to origin providers.
- Feed the ledger from events, and job cleanup from lifecycle events instead of `CraftingCpuLifecycle` reflection.
- Apply the unknown-CPU policy (see the open decisions).
- Take statistics from events.
- Measured in [remote-crafting-dual-registration-spike-2026-10-26.md](remote-crafting-dual-registration-spike-2026-10-26.md):
  outputs return to the provider's own Grid, so the return router stays; Neo ECO batches pushed with a job id are held
  on P until the job ends, so the Neo ECO adapter drops the job id for mirrored providers; C never hears of pattern
  changes or removal without an explicit refresh or removal.

### P4. Delete the projection route

- Delete `PatternProjection`, `ProjectionShowingLogic`, `ProjectedProviderLogic`, `NeoEcoBatching`,
  `CraftingCpuLifecycle` and the wrapper parts of `CraftingProjectionService`.
- Rewrite or remove the tests that name wrapper classes: PatternProjection, ProjectionReview, ProjectionContainer,
  ProjectionMerge, ProjectionCpuSight, ProjectionHandBack, ProjectionProductionExport, StorelessProviderReturn,
  Return*, CrossDimensionCrafting, Statistics, the projection fixtures, and the compat scenes MegaGlobalCrafting,
  IndependentCpu*, CoreCompat.
- Rewrite `docs/architecture/native-crafting-contract.md` and the guide pages that describe the mechanism. Remove the
  contract test's temporary whitelist.

### P5. Native parity as the compat promise

- Every addon crafting scene runs federated and joined.
- Outcomes are classified as: our bug / unsupported pairing / performance gap.

## Kept as is (re-homed if needed)

`CraftingReturnLedger`, `CraftingReturnRouter`, `HeldReturnTransfer`, `CraftingReach`, `CraftingRelationship`,
`CraftingFederationDomainObserver`, `FederationLinks`, `ReturnRecoveryCommands`, `ProjectionDiagnostics` (renamed).
The ledger keeps quantitative obligations: demand alone is not enough (counterexamples in the architecture note).

## Open decisions for the owner

- Unknown executor on C:
  - pause cross-network crafting for that consumer, shown in the UI (recommended), or
  - register anyway and show a diagnostic when its job stalls.
- Trinity pattern core (`trinityPatternCoreProvider`): it is a non-native pairing whose outputs bypass grid insert.
  Should it be refused, or kept red as a known gap? This is independent of the route.
- Uncommitted test work: `trinityCpuSixteenRemoteRuns`, `trinityPatternCoreProviderJoined` and the scene changes are
  green. `trinityPatternCoreProvider` is red.

## Risks

- Mixins from several addons on the same AE2 executor; Neo ECO is the first known case.
- Deferred dispatch (DE) needs context that the core cannot take from a grid tick scope.
- One provider object sits in several services' `ProviderState`, and some addon may assume a single grid.
- Batch counts may be `BigInteger` (DE); the ledger needs an explicit overflow policy.
- Trinity return ingress is not solved by accounting.
