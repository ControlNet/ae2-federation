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
- Product question (inferred, not verified in isolation): the storage and crafting observers forget a Grid during a
  transient unconfirmed identity and re-learn it only from a bridge/router refresh. A split that heals without a block update beside a Federation block may leave the
  rule unobserved.
