# Returns to a pattern-provider-only network (2026-10-24, dev)

User question: A has terminal + CPU, B only storage, C only pattern providers (B gives A storage, C gives A crafting,
shared power). Planning, inputs (from B through A's storage rule), push and owed returns already worked: the
`CraftingReturnRouter` on C hands owed outputs to A's waiting CPU without touching C's storage. Two gaps clogged C's
providers when C had no storage:
1. Owed output after A's jobs ended (cancelled job, AE2 finishing a job whose output cannot be stored) needed a stop in
   C's storage before the sweep moved it.
2. Arrivals beyond every debt (byproducts no pattern lists) had nowhere to go.

## What changed (zero mixins)
- Router step 2: owed arrivals for a consumer whose watched jobs all ended (`jobs(...)`: finished, not out of sight)
  go straight into that consumer's storage.
- Router step 4: arrivals beyond the unarrived debt go to the storage of the consumer that pushed last to the provider
  named by the insert's source. AE2 19.2.17 `PatternProviderLogic` hands returns with
  `MachineSource(mainNode::getNode)`, so `source.machine() -> getActionableNode() -> getService(ICraftingProvider)` is
  the real provider; projections carry `lastPush` (a service-wide push sequence) to pick the latest consumer.
- Only on executing networks with NO crafting CPU. First try gated on "a CPU is busy" failed: AE2's
  `CraftingServiceStorage` (mounted at Integer.MAX_VALUE) ends a job when its final output arrives but returns 0, so
  the output continues into storage and reaches our router after the CPU is already idle; nothing distinguishes it
  from a byproduct.
- `NetworkStorage.insert` offers every mount in each priority group twice (preferred pass, then all), so the router
  at Integer.MAX_VALUE already saw unowed keys; it just returned 0.
- AE2's `PatternProviderReturnInventory.canExtract()` is false: read it by slots in tests.

## Tests
`StorelessProviderReturnGameTests` (dev, required): byproduct to consumer, late output after cancel, neither network
can store (AE2 ends the job, stone + gravel wait in the provider, both arrive once the consumer's cell returns),
provider network with its own CPU keeps the byproduct. Fixture: `ejectIntoProvider`, `inProviderReturns`,
`placeProviderCpu`, `planOnProvider`. `ReturnCoverageGameTests` fluid tests now expect late buckets straight in the
consumer, not held. `sharedProviderByproductToLastPusher` (chain fixture: consumer and source with CPUs both order
stone through the middle's provider; the middle has no CPU and no cell) checks the latest-pusher rule both ways: the
gravel goes to the source, then to the consumer after it orders again. Mutation check: picking the earliest pusher fails
it. Chain fixture helpers: `placeMiddleMachine`, `middleMachineReady`, `takeMiddleCell`, `shareMiddle`,
`ejectIntoMiddle`, `inMiddleReturns`, `inMiddleMachine`.

## Follow-up 2026-11-07 (project review findings)
- Lane byproducts: a Federation Pattern Provider Lane is a `PatternProviderLogic` on `CapturedManagedGridNode`, whose
  `getNode()` is the Provider's physical node, which never publishes `ICraftingProvider` (by design). `pusher` now falls
  back to matching the insert's source against each projected logic's own `actionSource` (accessor on
  `PatternProviderLogicAccess`). Test `storelessLaneByproduct` (NetherProviderProjectionScene, provider cell taken out,
  machine returns gravel with each diamond).
- Ended jobs are forgotten each sweep even while the consumer is still owed; `endedJobs` (saved) stands in for them so
  `finished` keeps meaning "a job was seen and all ended". Debts with no job ever seen (a direct push, no CPU) still
  wait, as `projectionContainerPush` needs. Test `endedJobForgottenWhileOwed`.
- `unknownJobs` clears on a sweep when the loaded consumer Grid lists no busy CPU without a lifecycle adapter (an
  unloaded addon CPU is not listed: accepted risk, its late output then goes to storage).
- After all jobs ended, owed output goes to the consumer's storage (router step 2 and the sweep's held hand-back) only
  while the consumer still projects the executing network (`projected` has the pair, re-export included). Test
  `lateOutputNeedsCraftingRule`; `projectionHeldSweepCost` now turns on crafting rules first.
- No expiry for undeliverable debts (coordinator's decision): `/ae2federation returns clear-debts <consumer-uuid>`
  (permission 2) drops the consumer's debts, jobs and flags; held stock stays as the executing network's own stock.
  Test `clearConsumerDebtsCommand`.
