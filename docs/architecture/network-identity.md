# Network identity

## Contract

`NetworkId` is a globally unique, versioned logical identity formatted as
`ae2federation:network:v1:<uuid>`. It is independent of Bridge identity, block position, Fabric identity,
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
