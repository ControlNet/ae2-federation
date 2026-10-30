# routerPortReplacement batch-only failure (2026-10-30)

CI run 41020946099 (dev 9c76fbcb, Quick correctness) failed `routerportreplacement` in the shared batch only:
"Affected face must recreate as a Federation binding to be FEDERATION, but was DISCONNECTED". It was the first
failure of this test in about 300 Quick correctness runs. The test had no bundles, so the commit's bundle change was
not involved.

## What was ruled out, with evidence

- Another worktree: the run tested dev 9c76fbcb, whose parent is b31b2e8f; feature branches are not on dev, and each
  CI run has its own machine.
- Chunk not ticking: the Switch sits at (6,6,6), outside the 3x3x3 `harness_native_smoke` structure, so its chunk is
  not forced and GameTest does not wait for it. A Switch in a chunk two chunks from any forced chunk (FULL, not
  ticking) never links (deterministic local probe). But on CI the face saw NATIVE_ME first, and AE2 readies nodes
  only in ticking chunks (same test as `RouterTicks`), so the chunk was ticking there.
- Leftovers of nearby tests: the grid layout is the same in every run (origin is random, order is the manifest's);
  the earlier neighbours (bridgevalid, bridgeinvalid, energy tests) build near their own origins, not at the Switch.
- Local reproduction failed: full `--manifest --ci` batch, four full batches pinned to two cores
  (`taskset -c 0-1`), six router batches; all green.

## Confirmed weakness

A Router's sweep (every `RouterTicks.SWEEP_TICKS`) marks every face dirty, but `RouterFacePort.resolveLink` reads the
Federation port through its `BlockCapabilityCache`, which re-queries only after an invalidation. A stale null in that
cache is never recovered by the sweep: a local probe that put null in the east face's cache saw `works()` rise every
100 ticks while the face stayed DISCONNECTED for 400 ticks. Which event left a null there on CI is not known.

## Next failure

The assertion now prints `links`, a fresh `port` query, bundle records, `works` since the replacement, `awake`, and
the chunk state (784b1b66). Fresh port present + links true + works > 0 means a stale cache; works 0 with awake true
means the work was gated.

## Fix (owner approved 2026-10-30)

`RouterFacePort.sweep()` (called by `RouterBlockEntity.sweep`) makes the next resolve read the Federation port with
`level.getCapability`; when it differs from the cache, the face builds a new `BlockCapabilityCache`, so later events
read the right port too. GameTest `routerfacerecoversstalefederationcache` (manifest
`router.face-recovers-stale-federation-cache`) puts a stale null in a Router face's cache by reflection and expects the
link back within 150 ticks and kept after an ordinary neighbour event. Cost: one capability query per face per sweep
(every 100 ticks per Router); not benchmarked.
