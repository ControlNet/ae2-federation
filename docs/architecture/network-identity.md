# Network identity

## Contract

`NetworkId` is a globally unique, versioned logical identity formatted as
`ae2federation:network:v1:<uuid>`. It is independent of Bridge identity, block position, Federation Domain identity,
AE2 Grid serial, and transient Java object identity.

Every native AE2 Grid receives one `NetworkIdentityGridService` through `GridServices.register`. The service stores
only this namespaced node data in each surviving native node:

- schema version
- `NetworkId`
- persistent node UUID
- lineage revision

The world `NetworkIdentityRegistry` is sparse. It persists only known identity settlement state. Its live Grid claims
are process-local `IdentityHashMap` entries and are never serialized. Policy is not stored in node metadata, and live
Grid/node handles are not stored in world data.

## Native provenance

Pinned AE2 source: commit `79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a`.

| Symbol | Source path | Use |
|---|---|---|
| `GridServices.register` | `src/main/java/appeng/api/networking/GridServices.java` | Constructs one registered service for every native Grid. |
| `IGridServiceProvider.addNode` | `src/main/java/appeng/api/networking/IGridServiceProvider.java` | Receives provider data loaded with a native node. |
| `IGridServiceProvider.saveNodeData` | same | Writes provider-specific namespaced node data. |
| `Grid.add` / `Grid.saveNodeData` | `src/main/java/appeng/me/Grid.java` | Dispatches node add/save to all registered services. |
| `GridNode.setGrid` | `src/main/java/appeng/me/GridNode.java` | Carries service data when a native node moves between Grids. |
| `GridNode.saveToNBT` | same | Calls Grid service persistence during native host serialization. |
| `GridNode.callListener` | same | Compatibility boundary used to request the existing native host save notification. |
| `IGridNodeListener.onSaveChanges` | `src/main/java/appeng/api/networking/IGridNodeListener.java` | Native host contract that marks block entities or part hosts for save. |

`GridNode.callListener` is a public method on an AE2 internal implementation class, not part of `IGridNode`. This is the
narrow pinned-version compatibility boundary. If AE2 removes it without adding a public provider-triggered save request,
Task 4 becomes blocked: a provider can still write during an eventual save, but cannot ensure newly assigned identity is
scheduled for host persistence.

## Settlement

Only `SETTLED` may inherit existing Policy. `PARTIAL_LOAD`, `COPIED_LIVE_IDENTITY`, `AMBIGUOUS_SPLIT`,
`AMBIGUOUS_MERGE`, and conflicting node metadata are fail-closed states.

- A complete loaded Grid with one coherent lineage recovers its existing `NetworkId`.
- A Grid with no lineage receives one new ID shared by all of its nodes; native hosts are notified for save.
- Two loaded Grids claiming the same persistent node UUID are copied-live ambiguity. Neither inherits Policy.
- Two loaded Grids claiming one prior `NetworkId` after a native split are ambiguous. Neither child inherits Policy.
- One loaded Grid containing multiple prior `NetworkId` values is merge ambiguity. It inherits neither Policy set.
- No timeout, location, load order, randomness, or newest-wins rule resolves ambiguity.

### Provisional lineage of a lone node

AE2 readies a new part on an existing cable before connecting it: `ManagedGridNode.markReady` builds a one-node Grid
(`GridNode.getInternalGrid`), and only then does `CableBusContainer.addPart` call `GridHelper.createConnection`, which
merges that Grid into the cable's. The same applies to any node whose own Grid forms before its first connection. A
NetworkId minted for such a one-node Grid is not a prior identity, so the service marks it provisional (in memory and as
a `provisional` flag in the node data, which `GridNode.setGrid` carries across the move):

- A provisional lineage that arrives in a Grid that already has nodes is discarded; the node gets a new lineage with
  that Grid's NetworkId.
- When a provisional node's one-node Grid absorbs other nodes, the provisional lineages adopt the NetworkId of a durable
  one.
- As soon as a Grid holds two or more nodes, all its lineages are durable. Splits and copies of durable lineages keep
  the rules above.

The rule depends on Grid membership, not on time or order. Two fresh nodes that meet each other before they meet an
established Grid form a durable Grid of their own; if that Grid later joins an established one, the result is still
merge ambiguity (fail-closed). A saved single-node network (for example one lone ME chest) also stays provisional, so
connecting it to an established Grid adopts that Grid's identity instead of reporting a merge.

## Settlement cache

Each Grid's identity service publishes lineage additions and removals incrementally into `IdentityClaimIndex`, which
indexes live lineages by node id and network id and caches one settlement per Grid. A settlement only looks up the
Grid's own lineages. A claim change dirties the changed Grid and every other Grid sharing that node or network id, so
copies and splits observed through another Grid invalidate the unchanged Grid too. Stable reads return the cached value
without copying or scanning, and `SavedData` is marked dirty only when a persisted status changes. On a 256-node Grid
beside 32 Grids of 32 nodes (`identity.stable-query-cost`), a stable read went from 11.7 ms to about 0.5 µs.

## Partial loading

The implementation never scans the world and never force-loads chunks. A binding may be used only from currently loaded,
coherent native evidence. While evidence is incomplete or contradictory, Policy remains persisted but inactive. Later
native `addNode` events reconcile the affected loaded Grid; no per-tick world scan is used.

## Runtime proof

The baseline characterization runs two actual GameTest server JVMs against one disposable world. Process one places a
native powered ME chest in the naturally loaded spawn chunk, assigns provider data, and shuts down through Minecraft's
real world save. Process two constructs different Grid/service/node objects and logs `restored=true` from `addNode`.

Task cases:

- `identity.replace-all-access`: attachment replacement leaves the native Grid lineage unchanged.
- `identity.restart`: two actual server processes recover the same `NetworkId` from the unchanged native network.
- `identity.ambiguous-split`: removal of a real native middle node produces two Grids that both fail closed.
- `identity.copied-node`: native block-entity NBT copied into a second live disconnected chest is rejected for both.
- `identity.part-on-settled-cable`: a cable and then an Export Bus added to a settled Grid keep it `SETTLED` with its
  NetworkId, and linking it to a second established Grid still reports `AMBIGUOUS_MERGE`. Before the provisional rule
  the Export Bus alone made the Grid `AMBIGUOUS_MERGE`.
