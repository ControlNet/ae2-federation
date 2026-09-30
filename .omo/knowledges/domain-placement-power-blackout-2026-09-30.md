# Placing a Federation block blacks out energy across the domain (diagnosis, 2026-09-30)

Symptom: network A supplies ME power to network B through a Federation Domain. Placing a Federation Cable,
Router or Processing Endpoint anywhere against the domain makes B lose power for about 1.5 s.

## Chain of events (placing cable C next to domain cable X)

1. Block update, before the level tick: `X.neighborChanged` -> `CableFacePort.invalidate()` sets `peer = null` and
   runs `FederationDomainRegistry.invalidateNode(X)` at once (`FederationCableBlockEntity.invalidateFederationDomainTopology`).
   `invalidateComponents` removes X's whole domain. The remaining nodes still hold Federation edges to X, which is
   gone, so `collectComponent` returns `NON_RECIPROCAL_EDGE` and nothing is reinstalled: the domain no longer exists.
   `EnergyBindingService.reconcileIfPresent` then drops the A -> B binding.
2. Block-entity tick: X re-resolves and publishes with peer C. C has not initialized yet (AE2's
   `GridHelper.onFirstTick` runs in `TickHandler.readyBlockEntities` at level-tick end), so C has no registry node;
   the edge X -> C is non-reciprocal and the domain stays uninstalled.
3. `ServerTickEvent.Post`: B's `EnergyService.onServerEndTick` extracts through the `DirectionalEnergySource` on the
   Router face node, gets 0, sets `hasPower = false` and publishes "unpowered" immediately.
4. Next tick: C publishes, the domain is reinstalled, the binding is rebuilt and announces `PROVIDE_POWER`.
   AE2 only publishes "powered" after `ticksSinceHasPowerChange > 30`, so B stays offline for ~31 more ticks.

So at least one AE2 end-of-tick always falls into the gap, and AE2's power-up buffer stretches it to ~1.6 s.
Any block placed or broken next to a domain cable/Router/Endpoint face triggers step 1 (not only Federation blocks);
Router faces also invalidate on `onSaveChanges`. Storage mounts and crafting bindings are republished the same way.

## Two design points that combine

- Eager drop: `CableFacePort.invalidate()` / `RouterFacePort.invalidate()` remove the node from the registry on a
  neighbour block change instead of re-resolving on the next tick and republishing only if the peer differs.
- All-or-nothing reciprocity: one half-published edge (peer not yet published, or not pointing back) invalidates the
  whole component (Task 13 "fail closed"), rather than just not counting that edge.

Fixing only one is not enough: with a lazy re-resolve, X still publishes X -> C before C exists, which invalidates
the domain; with half-edges ignored, the eager drop of X still splits the domain at X.

## How AE2 19.2.17 maintains Grids (reference for a redesign)

- One symmetric `GridConnection` object per edge, added to both nodes. The node that becomes ready later creates it
  (`GridNode.markReady -> updateState -> InWorldGridNode.findInWorldConnections` against already exposed neighbours);
  there is no half-edge or reciprocity handshake.
- `findInWorldConnections` is a diff: `cleanupConnections` destroys only edges whose sides are no longer exposed or
  compatible, an existing edge to the same neighbour is kept (`continue sides`), only missing edges are created.
- Join: `GridConnection.create -> mergeGrids`; a standalone node just `setGrid`s into the neighbour's Grid, two Grids
  merge by `GridPropagator` moving the smaller (or lower priority) one into the better one. The surviving `Grid`
  object and its services are never rebuilt; services get incremental `addNode`/`removeNode`.
- Leave: `GridNode.destroy` removes only its own connections, moves the pivot to a neighbour, and each neighbour runs
  `validateGrid` (`GridSplitDetector` BFS for the pivot). Only a part that cannot reach the pivot gets a new Grid.
- Federation Domain differs on every point: half-edge evidence with all-or-nothing reciprocity; every change removes
  and reinstalls the domain snapshot with a new generation; `FederationDomainId.physical` is the smallest node id in
  the component, so it can change when a node joins; consumers (energy, crafting, storage, UI) compare the
  level-global `topologyRevision`, so any change anywhere in the level invalidates every binding.

## Resolution (2026-09-30, commits 08c4be0, 7d2e084, 8501eb0)

1. Registry: mutual links only, pending half-edges, per-port unsettled exclusion, id inheritance and
   member-set generations (see `task-13-fabric-registry.md`).
2. Publishers: `CableFacePort.revalidate()` / `RouterFacePort.revalidate()` resolve on `neighborChanged` and report a
   change; the Router's `onSaveChanges`, port initialization and the Provider's `GRID_BOOT` only mark dirty; a Provider
   moved to another Grid upserts its new evidence at once (registry only, inside AE2 propagation); a rotated Endpoint
   publishes its new front at once; a Router republishes every 20 ticks only if its evidence changed (identity settled).
3. Consumers: `EnergyBindingRevision.sameAuthority` / `CraftingBindingRevision.sameAuthority` ignore the level-wide
   revision; the policy session also requires its Router/Endpoint/Provider entrance to stay a node of the domain.
- Regression: `topology.placement-keeps-power` failed before (B unpowered 32 of 40 ticks after each of cable, plain
  block, Router, Endpoint and Provider placements; binding withdrawn in the same tick) and passes after, including
  "binding not rebuilt"; `topology.break-cuts-power` passes throughout; `topology.unsettled-attach-keeps-domain`
  failed before and passes after.
- Behaviour change pinned by `router.port-replacement`: a native neighbour replaced by a Federation Cable now resolves
  to `FEDERATION` in the same block update (previously `DISCONNECTED` for a tick); the stale native binding still
  never survives the replacement.
- Not run locally: ldlib2 UI scenarios (`ui.*`) and dedicated multiclient (`multiclient.*`), which need a client.

