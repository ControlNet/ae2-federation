# GameTests in another dimension (2026-10-08)

The GameTest framework places structures only in the overworld. `test/world/OtherDimensionSite` (test-only) gives a
test a region of the nether at the test's own x and z, at y 81 (below the bedrock roof, above the lava sea), walled in
stone and emptied when it opens and closes.

## Why its block entities did not tick at first

- A block entity ticks only when `LevelChunk.isTicking(pos)` holds: inside the world border, chunk at least
  `BLOCK_TICKING`, **and** `ServerLevel.areEntitiesLoaded(chunk)`.
- A chunk's entities load only once its entity-ticking future completes, which waits for every chunk within two of it
  to be FULL. A forced ticket on the site's chunks alone left those neighbours ungenerated, so `areEntitiesLoaded`
  stayed false for the whole test even though `isPositionTicking` was true.
- Symptom: AE2 nodes were created (AE2's `TickHandler` only needs `isPositionTicking`), but Federation's Routers never
  resolved a face: their `serverTick` never ran.
- Fix: force the site's chunks with a ticking `TicketController` ticket (block owner is enough), load every chunk within
  two of them with `level.getChunk` at open, and build only after `site.ready()` (every site chunk ticking with
  entities loaded).

## Cross-dimension GameTests (`CrossDimensionGameTests`)

- `cross-dimension.two-domains`: Router domains in the overworld and the nether share storage; both rules set through
  the overworld's `PolicyService` handle.
- `cross-dimension.unload-keeps-overworld`: runs the entrypoint's level-unload sequence on the nether while the server
  runs; the nether's nodes and mount go, the overworld's stay; `reloadBlockEntities()` brings the nether domain back
  under the kept rule. Mutation checked: a `closeLevel` that does not remove the dimension fails it.
- `cross-dimension.quantum-bridge-projection`: a real AE2 Quantum Network Bridge (3x3 flat, both sides powered,
  singularities with one random `AEComponents.ENTANGLED_SINGULARITY_ID`) carries the provider network into the nether;
  its AE2 pattern provider there is projected to the overworld consumer.
- Not covered yet: a *Federation* Pattern Provider in another dimension on a spanning network
  (`RealCraftingProviders` reading every dimension). Its lanes register only once mapped to an Endpoint; a nether
  Provider scene is needed. Replacing that fix with a per-level read passes all three tests.
- Before P2P there is no real block that links a Federation Domain across dimensions, so Provider (overworld) to
  Endpoint (nether) is tested with the P2P carrier instead.

## QA registration

A new GameTest family needs: manifest rows, the case ids in the generic-evidence `case` group of
`gradle/federation-qa.gradle`, and its id prefix in both prefix lists there (`operations: 1`, `allowZeroWork`).
Tests write `PolicyEvidence.write(testId, assertions, facts)` with the manifest's assertion count.
