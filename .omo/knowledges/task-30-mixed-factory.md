# Task 30 Mixed Factory Load

## Native composition

The mixed scene reuses one Task 28 `NativeAutomationFixture`, so Storage projection, Crafting binding, consumer/provider
Grids, Interface logic, buses, and physical ME cells share one settled topology. A seeded `NativeProviderLaneFixtures`
instance attaches to the provider Grid and publishes six real processing patterns: two alternative stone inputs, a
four-stage stone-to-emerald chain, and a separate redstone-to-glass lane.

Two physically formed crafting storage blocks expose exactly two native CPUs. The second block must be adjacent to the
provider ME Chest; `GridHelper.createConnection` alone can put its node on the Grid without creating another CPU cluster.

## Lifecycle and backpressure

Two independent `NativeCraftingRequester` instances submit the glass and emerald jobs. Planner and submission counts now
come from the existing terminal/CPU/`MultiCraftingTracker` hooks and retain requester job UUIDs. The glass target input is
retained while its live native link and CPU remain busy; release is proven by the provider push and physical output path.

The overload case uses one requester with one tracker slot. The rejected second `NativeTerminalRequest` remains the logical
order owner, is retried once after the first native link retires, and receives its own UUID and physical result. The empty
case records zero deltas across planner, tracker, projection, handler, Interface/bus, in-flight, and physical inventories.

## Evidence and verification

`mixed-small-v1` executes one warmup plus three separately reset measured native scenes. The seed selects the alternative
input per iteration; profile fields drive CPU count, chain, blocking, stocking, and batch axes. Every run persists unique
runtime/job identities, hook-owned handler/return/stocking receipts, and eleven per-key physical equations.

Task 30 is registered in the existing Task 20 benchmark runner. Dedicated persisted consumers verify current source
identity and exact schemas. Fully rebound probes reject fabricated accounting, handler/stocking attribution, call/quantity
conflation, iteration changes/copies, extra metrics, empty native work, silent drop, missing recovery, hidden queues, and
duplicate job identities. Task 20's capture binding must be refreshed from the unchanged profile/budget/dependency formula.

## Second re-review repair

- Processing authority is the registered `MixedMachineBlockEntity`, not `MixedProcessingMachine`: AE2 inserts through its
  NeoForge item capability; the block entity owns inputs, outputs, recipes, transitions, and return-handler insertion.
- `MixedFactoryTopology.from(profile)` constructs alternatives, chain stages, blocked lanes, and deterministic resources;
  scene CPU/requester/lane construction consumes those typed dimensions.
- `benchmark-runtime.receipts` is opened before the first scene and appended synchronously by the terminal, tracker,
  projection, provider/return, machine, callback, stocking, bus, and physical-inventory observation sites. Payload fields
  use URL-safe Base64 so the sequence/iteration/phase/event envelope remains exact without delimiter ambiguity.
- `MixedFactoryEvidence.writeBenchmark` never writes or mutates runtime authority. The persisted consumer parses the raw
  stream independently, requires contiguous sequence numbers and exact event schemas/scene envelopes, and recomputes
  machine, projection, bus, stocking, job, callback, owner, and per-key inventory facts before accepting the properties.
- Empty-order and overload artifacts contain observed deltas, ticks, and cardinalities. Fully rebound coordinated
  substitutions fail with `Task 30 independent runtime authority mismatch`.

## Raw-authority verification

- Fresh benchmark: `./gradlew :neoforge-1.21.1:federationBenchmark -Pprofile=mixed-small -PevidenceDir=.omo/evidence/task-30-raw-authority --dependency-verification=strict --no-configuration-cache`.
- Run `federationTaskThirtyBenchmarkEvidenceConsumer` and `federationTaskThirtyBenchmarkEvidenceSelfTest` with the fresh
  `result.json`. The self-test covers coordinated projection changes plus missing, extra, duplicate, conflicting,
  reordered, wrong-phase, wrong-iteration, and replaced raw receipts.

## Final metric authority

- `handlerQuantity` is the checked sum of positive `MACHINE_TRANSITION` amounts for each warmup or measured scene. Each
  transition must have a matching earlier `MACHINE_ACCEPTED` fact, known resources, exact owner identities, and the
  canonical transition count before the projected aggregate is compared.
- `peakInFlight` is the maximum observed busy CPU count from operation-time `CPU_IN_FLIGHT` receipts. Every observation is
  bound to a submitted job and its CPU owner, constrained by the configured CPU limit, and correlated with any `WAITING`
  fact for that exact job, busy count, and blocked-input count.
- Keep both aggregate checks inside the raw-receipt loop so warmup and measured scenes are isolated. Never derive these
  values from `benchmark-native.properties` or reconstructed `MixedFactoryIteration` objects.
- The rebound probes `unbound-handler-quantity` and `unbound-peak-in-flight` must fail with their exact independent runtime
  authority mismatch even after the projection artifact and report digests are fully rebound.
