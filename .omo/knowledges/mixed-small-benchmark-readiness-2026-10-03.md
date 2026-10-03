# mixed-small benchmark readiness (2026-10-03)

`federationBenchmark -Pprofile=mixed-small` timed out at `stage=0` since before the pattern-projection work.

## Cause

`MixedFactoryScene` placed the processing lane fixture and the extra CPUs only after the rules were enabled. Those
blocks carry the provider's network id (`NetworkIdentityNodeSeed.managedNode("proxy" | "provider", id)`). The lane's
creative energy cell gets its node one tick after placement and is joined only by `NativeProviderLaneFixtures.connectEnergy`,
which the scene never called. For that window two live Grids claim the provider id, so `IdentityClaimIndex` settles the
provider as `AMBIGUOUS_SPLIT`.

- The orphan cell never joined, so the split never healed.
- When the cell was joined a tick later, the crafting projection status stayed empty after the heal. Inferred from the
  code, not observed directly: the crafting observer (and likely the storage observer) dropped the provider Grid in
  `discardStaleGrids` while its id was unconfirmed. Nothing re-registered it, because the fixture's
  `observeConnectedGrids` is one-shot and the bridge part only refreshes on node-state or neighbour changes.

## Fix (testmod only)

The scene builds the whole provider network before the rules are enabled. It places the lane and the CPUs once the binding
fixture is ready, joins them (CPUs, `connectEnergy`, `connectTo`) at the start of each readiness check, and calls
`NativeAutomationFixture.readiness()`, which enables the rules, last. `readiness()` strings on the scene, the automation
fixture and the binding fixture name the first unmet condition in the timeout message.

## Still open

- Resolved in cb2c32a (user-approved): the three `tests/benchmarks/*/baseline.json` held the
  `gradle/verification-metadata.xml` sha from before e8eb052, which only added the AE2 19.2.9 checksums, so their
  `dependencyLockSha256` and `captureIdentitySha256` were refreshed. `ui-small` was also broken since 0063940: it projected
  the Bridge's domain, which no longer holds the Provider or Endpoint. It now projects `processingScope()`, which has one
  member network and one physical edge. The baseline `productionProjection` and the Task 34 report builder were updated
  to match. All three benchmarks and the Task 30/34 self-tests pass.
- Confirmed and fixed (2026-10-03). GameTest `storage.identity-split-recovers` puts a stray block carrying the
  provider's id on a second Grid, waits for `AMBIGUOUS_SPLIT`, then joins the block to the provider Grid.
  - Before the fix the crafting projection was withdrawn during the split and never came back; 1153 ticks after the heal
    it was still missing.
  - The storage mount was never reconciled on the identity change. A held projection failed closed on use (insert 0),
    was dropped, and never came back either.
  - Cause: the storage, crafting and energy observers removed a Grid from `loadedGrids` as soon as its id was
    unconfirmed. Only a Bridge or Router refresh re-registered it, and a heal on the same Grids fires neither. Also,
    `StorageMountService` never reconciled on an `IdentityEpoch` change, unlike the crafting and energy services.
  - Fix: `domain/ObservedGrids` keeps observed Grids by identity, reads their confirmed id on each query and forgets only
    emptied Grids. All three observers use it. `StorageMountService.tick` reconciles when `IdentityEpoch` changed and is
    called from the level tick before crafting.
