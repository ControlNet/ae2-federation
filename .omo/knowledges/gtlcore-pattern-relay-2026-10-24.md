# GTLCore ME Pattern Relay vs. pattern projection (source read, 2026-10-24)

Source: github.com/GNSW/GTLCore, branch `gtl-1431-skyblock` at 5ee8c54b (LGPL-3.0, Forge 1.20.1, GTCEu 1.4.4,
GregTech Leisure pack core). File: `integration/ae2/patternrelay/PatternRelayPart.java` (688 lines). Added 2026-07-27,
smart doubling 2026-08-06, graph (cyclic) crafting hooks 2026-09-23. Not co-loadable with AE2 Federation (different
loader and Minecraft version); this is a design comparison only, nothing tested in game.

## How it works
- One AE2 cable part (`gtlcore:me_pattern_relay`, recipe: Pattern Provider + Quantum Link + Logic Processor + 2 smart
  cables -> 2), wrench toggles SUPPLIER / ACCESS. A pair works only face to face: ACCESS finds the SUPPLIER part on
  the opposite face of the adjacent block (`findPeer`), and the two parts must be on different grids. Needs a channel,
  1 AE/t idle.
- ACCESS is an `ICraftingProvider` (node service) on its grid. Every 5-20 ticks it walks the supplier grid's nodes,
  takes every active node's `ICraftingProvider` service except other relays, and offers all their patterns (the same
  objects) as its own. Global crafting providers are not node services, so they are not seen; relays are excluded,
  so no multi-hop.
- `pushPattern`: picks one of the supplier providers holding the pattern (GTL ME buffers first, then auto-expand
  providers, then plain ones) and pushes the access CPU's inputs straight into it. Inputs come from the access
  network only; no storage or power sharing.
- Outputs: the relay records `pendingOutputs` and, before the push, the supplier network's stock of each output.
  Each tick it treats any rise of the supplier network's stock above that baseline as produced and moves up to the
  pending amount into the access network's storage, where AE2's crafting storage hands it to the waiting CPU.
  Persisted in NBT.
- `getPatternPriority` is not overridden (0), so the supplier providers' priorities are lost.
- `RouteSnapshot` equality is `Map.equals`, keyed by pattern equality: re-decoded equal pattern objects are not
  picked up (the same bug our `PatternProjection.refresh()` had until 99e7d8f2). Its push still works because AE2 checks
  patterns by equality.

## Weak points read from the code (not run)
- Outputs are found by stock difference, not by the provider's return path:
  - the supplier network needs storage that takes the output;
  - a supplier CPU waiting for the same item catches it first (AE2's crafting storage sits above drives), so the
    stock never rises and the access job waits;
  - anything else that raises that stock on the supplier network while a debt is open (another job, a player, an
    import bus) is moved to the access network;
  - container items (empty buckets) are not tracked.
- Pending outputs are not tied to a job: a cancelled job still pulls later stock across.
- Only adjacent face-to-face pairs, one supplier per access, same dimension.

## Compared with Federation
Federation records each push in `CraftingReturnLedger` (outputs and container items per executing network, consumer
and key) and `CraftingReturnRouter` sits on the executing network at top priority, taking only owed keys before any
drive or local CPU and handing back no more than the consumer's CPUs still wait for; held/transit stock covers unlinked
or unloaded consumers, and the consumer's jobs are tracked for cancel. Projections are one per real provider with its
priority, reach is cable/Router/domain based with storage and power rules, and addon planners see them
(see `addon-planners-vs-projection-2026-10-24.md`).

Ideas worth noting from GTLCore: the wrench-toggled single item is very light to set up; batch size negotiation with
downstream providers (`gtlcore$getMaxPatternOperations`) is GTL-specific.
