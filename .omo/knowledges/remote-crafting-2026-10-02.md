# Remote crafting: using a Crafting binding from the consumer (2026-10-02)

> Superseded by `crafting-pattern-projection-design-2026-10-02.md` (agreed 2026-10-02): the delegated-job model below is
> being replaced by pattern projection. This note describes the code until that lands.

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
- Eight cases since 2026-10-02: plus `crafting.remote-world-reload`, `crafting.remote-late-provider`,
  `crafting.remote-restart`. The restart case is backend `gametest-restart` (two processes, state file
  `crafting-restart-state.properties`, logs `restart-prepare.log` / `restart-verify.log`); the verifier checks its
  evidence from `restart-verify.log` and the process IDs. `tools/required_gametests.py` (CI) runs only backend
  `gametest`, so CI does not run it.

## Reload and restart coverage (2026-10-02)

- Bug found by the restart case: `CraftingBindingService.reconcileAll` ran only on rule edits, Bridge events, domain
  mutations and `capability()` reads. After a restart the domain was known before the provider's CPU cluster formed,
  discovery threw `CRAFTING_CPU_MISSING`, and nothing reconciled again (same in game for a rule enabled before the
  provider had a CPU). Fix: `CraftingReadinessEvents` subscribes to `GridCraftingCpuChange`, `GridPowerStatusChange`,
  `GridBootingStatusChange` (registered in `CommonStartup`); an event on an observed Grid marks the level, and
  `CraftingBindingService.flushReadiness` publishes only missing bindings at level tick end
  (`NeoForgeEntrypoint.onLevelTick`). A full `reconcileAll` there broke `terminalnocpu`: removing the provider's CPU
  withdrew the binding, so submission returned `StaleBinding` instead of AE2's `NO_CPU_FOUND`. AE2 19.2.x
  has no pattern-change Grid event; readiness only needs an active provider node and a CPU, and node activity
  changes only with power or a reboot.
- In-process world reload (`BlockEntityReload`): save each block entity with `saveWithFullMetadata`, then
  `onChunkUnloaded()` + `removeBlockEntity`, then `BlockEntity.loadStatic` + `setBlockEntity` (which schedules AE2's
  init). Reload the whole fixture region, or the old Grid's link nexus cancels the job. Replacing a CPU block with AIR
  is NOT an unload: `AbstractCraftingUnitBlock.onRemove` breaks the cluster and cancels its job.
- A consumer CPU joined by `createConnection` is not persisted; place it adjacent to a cable instead.
- `GameTestHelper.relativePos` does not invert `absolutePos` outside the structure; use
  `absolute.subtract(helper.absolutePos(BlockPos.ZERO))` (tests are not rotated). Tests are placed at a random far
  position each run.
- The restart case builds at its own test position and keeps the fixture's chunks loaded with a persistent NeoForge
  `TicketController` ticket (`RestartChunkTickets`, owner = the prepare test's origin, stored in the state file;
  verify removes it). NeoForge saves the ticket and `MinecraftServer.prepareLevels` reinstates it. Vanilla
  `setChunkForced` does NOT work: `GameTestRunner` unforces every vanilla forced chunk when a batch ends, before the
  server saves. (`identity.restart` and the policy restart still build beside spawn, whose chunks tick from start.)
- The GameTest server ticks unthrottled (1600 ticks ≈ 1 s), so wall-clock waits mean nothing; wait in ticks.
- Running the two phases by hand: pass `-PfederationRetainGameTestRuntime=true` to both, or the verify run starts
  from a deleted world:
  `./gradlew :neoforge-1.21.1:runGameTestServer -PfederationGameTestSelection=positive -PfederationGameTestId=craftingremoterestart -PfederationCraftingPhase=prepare -PfederationCraftingStateFile=<scratch>/crstate.properties -PfederationRetainGameTestRuntime=true --no-configuration-cache`
  then the same with `=verify`.
- Batch mode runs one test per batch, in order (`GameTestSequentialBatchMixin` sets `ae2federation.testId` per
  test); fixtures that build outside the 3×3×3 structure leave their networks and rules in the level after the
  test. So a "SHARED" failure is leftover state from a finished test, not concurrency. Fixes on 2026-10-02:
  `ChainStorageFixture.close()` and `craftingremotelateprovider` switch their rules off;
  `craftingrejectunavailable` / `craftingdeduplicatecapability` count `CraftingBindingFixture.ownBindingCount()`
  (bindings between the fixture's two networks); `craftingrejectcycle` counts only discoveries of the fixture's
  networks; `subscriptionsnapshotrace` / `subscriptionlistenercleanup` count listeners relative to the count before
  they configure, plus `subscriptionRegistrationId(key)`. The full batch then had no SHARED test.
- `craftingremotelateprovider` measures the fix itself: binding within 2 ticks after the provider's CPU cluster
  appears (event at level tick end), consumer listing within one 20-tick projection refresh after that.

## Not covered

- A provider must have its own patterns and CPU to be bound (`NativeCraftingBackendRegistry.discover`). A network
  with neither cannot provide Crafting at all, so it cannot pass on another network's craftables either.
