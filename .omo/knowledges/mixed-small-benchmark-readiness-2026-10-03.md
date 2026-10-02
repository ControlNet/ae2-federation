# mixed-small benchmark readiness (2026-10-03)

`federationBenchmark -Pprofile=mixed-small` timed out at `stage=0` since before the pattern-projection work.

## Cause

`MixedFactoryScene` placed the processing lane fixture and the extra CPUs only after the rules were enabled. Those
blocks carry the provider's network id (`NetworkIdentityNodeSeed.managedNode("proxy" | "provider", id)`). The lane's
creative energy cell gets its node one tick after placement and is joined only by `NativeProviderLaneFixtures.connectEnergy`,
which the scene never called. For that window two live Grids claim the provider id, so `IdentityClaimIndex` settles the
provider as `AMBIGUOUS_SPLIT`.

- The orphan cell never joined, so the split never healed.
- Even with the cell joined a tick later, the crafting and storage observers had already dropped the provider Grid
  (`discardStaleGrids` on an unconfirmed id). Nothing re-registered it: the fixture's `observeConnectedGrids` is one-shot,
  and the bridge part only refreshes on node-state or neighbour changes. The projection status stayed empty.

## Fix (testmod only)

The scene builds the whole provider network before the rules are enabled. It places the lane and the CPUs once the binding
fixture is ready, joins them (CPUs, `connectEnergy`, `connectTo`) at the start of each readiness check, and calls
`NativeAutomationFixture.readiness()`, which enables the rules, last. `readiness()` strings on the scene, the automation
fixture and the binding fixture name the first unmet condition in the timeout message.

## Still open

- The Task 30 verifier then fails with `benchmark capture identity is stale`. `tests/benchmarks/*/baseline.json` record
  the `gradle/verification-metadata.xml` sha from before e8eb052; all three baselines (mixed, processing, ui) are stale the
  same way. Their policy is `explicit-edit-and-rerun-required`, so refreshing them is the user's call.
- Product question: the storage and crafting observers forget a Grid during a transient unconfirmed identity and re-learn it
  only from a bridge/router refresh. A split that heals without a block update beside a Federation block may leave the
  rule unobserved.
