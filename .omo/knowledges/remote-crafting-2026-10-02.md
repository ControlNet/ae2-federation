# Remote crafting: using a Crafting binding from the consumer (2026-10-02)

## The gap

Before this change, `CraftingBindingService` published bindings, but nothing on the consumer used them:
`NativeTerminalAdapter.discover` was called only from tests. In game, a consumer's terminal never listed the provider's
craftables, and the consumer could not request them. `crafting.remote-request` failed before the fix.

## Mechanism (package `crafting/remote`)

- `CraftingProjection`: an emit-only `ICraftingProvider`, one per current binding. It is registered on the consumer
  Grid with `getCraftingService().addGlobalCraftingProvider(...)`.
  - It has no patterns, `pushPattern` returns false, and `isBusy` returns false.
  - `getEmitableItems` returns the provider's craftables, as the rule's filter permits, minus keys the consumer has its
    own patterns for.
  - Global providers are pull-only, so call `refreshGlobalCraftingProvider` whenever the key set changes.
- `RemoteCraftingService` (per level; ticked from `NeoForgeEntrypoint.onLevelTick`):
  - Refreshes projections every 20 ticks.
  - Every 5 ticks it computes, per consumer and key, `getRequestedAmount` minus what live provider jobs still owe. For
    any shortfall it plans on the provider with `REPORT_MISSING_ITEMS` and submits with `submitJob(plan, requester,
    null, false, ofMachine(requester))`.
  - A plan that is a simulation (missing materials) or a failed submit is retried after 40 ticks.
  - A key not requested on two consecutive looks has its provider jobs cancelled with `link.cancel()`.
- `RemoteCraftingRequester`: an `ICraftingRequester` node service on Federation nodes of the provider Grid (the
  Bridge's main and outer nodes, and each Router face port).
  - `insertCraftedItems` sends what the job asked for to the consumer Grid's storage inventory. There the consumer's
    `CraftingServiceStorage`, which has the highest priority, hands it to the waiting CPU.
  - The surplus goes into the provider Grid's storage.
  - Links are saved with the host's NBT and reloaded with `StorageHelper.loadCraftingLink`.
  - `getRequestedJobs()` registers the requester with the service.

## AE2 facts this rests on

- `ICraftingService.getCraftables` includes emitable items; `isCraftable` and `getCraftingFor` count patterns only.
  Terminals list `getCraftables`, so check that in tests.
- An emitable key wins over the consumer's own patterns: `CraftingTreeNode` plans it as emitted without looking at
  patterns. Keys the consumer can craft itself must therefore be excluded from the projection.
- AE2 hands the whole output of the last craft to `job.link.insert`, capped at `waitingFor`, which is the pattern's
  full output (4 planks for a request of 2). It **ignores the remainder** the requester does not take. So the
  requester must keep the surplus itself (it goes into the provider Grid's storage), or those items are lost.
- `CraftingCPUCluster.cancelJob()` is the public cancel; a cancelled provider job returns its materials to the provider.

## Recursion and cycles

- The provider's own `getCraftables` includes its own projections, so a provider's plan recurses along Crafting rules
  (`crafting.remote-chain`: consumer → middle → source, with no consumer → source rule).
- `CraftingDependencyCycleGuard` keeps the Crafting rule graph acyclic, so the recursion ends. Cycles stay rejected
  (`crafting.reject-cycle`).

## Pitfalls hit while testing

- `CraftingCapabilityBinding.nativeService()` / `isCurrent()` call `NativeCraftingBackendRegistry.discover`, which walks
  every node of the Grid. Don't call them on periodic paths; the projection refresh reads
  `relationship().providerGrid().getCraftingService()` instead. `crafting.reject-cycle` counts every discovery while
  it runs.
- GameTest Grids outlive their test. A fixture that leaves Crafting rules on keeps
  `FederationBindingRefresh` → `observeFederationDomainMembers` → `reconcile` → `discover` running for every later
  test, which broke `crafting.reject-cycle` in batches. `RemoteCraftingChainFixture.close()` switches its rules off.
- Assert submission counts per `PolicyKey` (`submissionCount(key)`), not level-wide: `--manifest` runs tests
  concurrently in one level.

## Reload and unreachable consumers

- `crafting.remote-reload` reloads the Bridge's cable bus in one tick (`saveWithFullMetadata` → AIR → same state →
  `loadWithComponents`, `PolicyBridgeFixtures.reloadFirstBridgeHost`) and calls `RemoteCraftingService.closeLevel`
  while the provider CPU is suspended.
- It first failed: right after the restart no binding was published yet, so the consumer's demand looked absent and
  `serveDemand` cancelled the reloaded job (traced with temporary output, since removed). The same rule would have
  cancelled provider jobs whenever a consumer's chunk unloaded.
- Fixes: only a reachable consumer (one with a current projection) is judged for cancellation; and for
  `STARTUP_TICKS = 100` after the service starts it starts and cancels nothing (AE2's nexus cancels a job whose
  requester stays missing for more than 60 ticks, so a job not reconnected by then is gone anyway).
- `PartHelper.getPart(IPartItem, level, pos, side)` re-resolves the part after the reload; the fixture's old part
  reference is dead.

## federationVerify group

- `remoteCraftingCases` / `verifyRemoteCraftingEvidence` / `federationRemoteCraftingEvidenceConsumer` in
  `gradle/federation-qa.gradle`. Each test writes `NativeCraftingEvidence` facts from measured values; the verifier
  checks the exact schema, the `AE2F_CRAFT_NATIVE_ENTRY` trace, assertion counts (must match the manifest), and the
  expected outcome per field. A mutation of one expected value was rejected with "outcome mismatch".
- No mutation self-test task and no persisted `.omo/evidence` receipt, unlike Tasks 26–31.

## Not covered

- The reload test keeps both CPUs loaded; a full world restart (CPU jobs reloaded too) is not exercised.
- A provider must have its own patterns and CPU to be bound (`NativeCraftingBackendRegistry.discover`). A network
  with neither cannot provide Crafting at all, so it cannot pass on another network's craftables either.
