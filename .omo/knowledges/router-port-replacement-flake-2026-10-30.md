# routerPortReplacement batch-only failure (2026-10-30)

CI run 41020946099 (dev 9c76fbcb) and run 41028260423 (dev 9883159b) failed `routerportreplacement` in the shared
batch only, both with "Affected face must recreate as a Federation binding to be FEDERATION, but was DISCONNECTED".
Neither commit was involved.

## Cause

The message was a mask. The second run's diagnostics showed `neighbour=Block{minecraft:stone}`: phase 1 had already
seen the Federation link and placed the stone, then a later check failed before the phase moved on, so every later
tick failed the first check against the stone. The check that failed was "Unsupported replacement must not recreate
another face": the test holds the west face's binding and compares it by identity, and
`RouterFacePort.tick()`/`revalidate()` stored every resolve's result even when it equalled the old binding. A Router's
sweep (`RouterTicks.SWEEP_TICKS`, 100 ticks, on the tick its position's hash picks) landing between phase 0 and phase
1 re-resolved the west face into an equal new object. The random grid origin moves that tick from run to run, hence a
rare failure that never showed locally. GameTest `routerfacekeepsitsbindingwhenunchanged` reproduces it at once.

Fix: a face keeps its binding when the new one is equal (`RouterFacePort.rebind`); `NativeAttachment` is a record of
its nodes and grid, so equal means the same attachment. The test now records the stone checks and asserts them from
phase 2 on, so a failure there reports itself.

## Wrong turns, kept for the next investigation

- First conclusion was a stale Federation capability cache. It was not the cause, but the gap is real: a sweep marked
  faces dirty while `resolveLink` read the port through a `BlockCapabilityCache` that re-queries only after an
  invalidation. Fixed anyway (`RouterFacePort.sweep()` reads the port afresh and replaces the cache when it disagrees;
  test `routerfacerecoversstalefederationcache`). Cost: one capability query per face per sweep, not benchmarked.
- Chunk not ticking: the Switch sits at (6,6,6), outside the 3x3x3 `harness_native_smoke` structure, so its chunk is
  not forced and GameTest does not wait for it; a Switch in a non-ticking chunk never links. Not the cause here (the
  face saw NATIVE_ME first, which needs a ticking chunk), but tests that build far outside their structure can hit it.
- Leftovers of nearby tests and another worktree: ruled out by the grid layout and the tested commit.
- Local reproduction (full batches, two-core `taskset`, router batches) never failed: the cause depends on the random
  origin, not on load.

Lesson: in a `succeedWhen` phase, an action followed by assertions that fail leaves the phase stuck, and the timeout
reports the phase's first assertion, not the one that failed.
