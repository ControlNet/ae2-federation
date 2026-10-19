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

### Initialization and established lineage

Identity establishment is a native lifecycle boundary, never a node-count or read-count heuristic.
`CableBusIdentityInitializationMixin` wraps the pinned AE2 `CableBusContainer.addPart` and `addToWorld` calls with
`NativeIdentityInitialization` (including nested calls and try-with-resources cleanup). AE2 creates a part's temporary
Grid before connecting the part to its cable. New live nodes without saved lineage may adopt a single identity
throughout the server tick in which they were minted, including separate native initialization batches. This authority
follows the original node across native Grid transfers, preserves its node UUID, and is never saved in NBT. A node
joining an established network adopts its unique durable identity; independently established histories still conflict.

A wholly fresh Grid reports `PARTIAL_LOAD` while adoption can still change its identity. Closing one multipart call
does not prematurely publish a settled ID. At the next native Grid start tick, the identity service advances the epoch
and sends native `onSaveChanges` notifications, including to boundary nodes, so Bridges/Switches republish membership
and bindings without requiring an unrelated player action. A fresh node joined to a durable single-identity network
can use that stable identity immediately after multipart assembly ends. Reading settlement neither grants adoption
rights nor consumes the scheduled notification. Identity data loaded from disk, including copied NBT, never grants
fresh adoption authority.

The initialization scope is process-local and keyed by the original `IGridNode` object. It is not encoded in NBT.
Grid transfers preserve the node UUID; a different node loaded from copied NBT cannot inherit initialization authority.
Two identical lineages within one Grid are also rejected, without losing multiplicity in the distinct-lineage index.

**Legacy compatibility:** every valid schema-1 lineage loaded from disk is established, even when it contains the old
`provisional: true` flag. Keep its NetworkId, node UUID and revision; omit the obsolete flag on the next save. This
conservative rule is necessary because previous versions allowed a lone provisional Grid to become `SETTLED`, enter
Domain membership via `FederationDomainRegistryAccess.nativeEvidence` / `confirmedNetworkId`, and receive persistent
Policy keyed by that identity. No saved identity can safely be assumed unused. No automatic NetworkId regeneration
or Policy migration is performed.

The integration is deliberately pinned to AE2 19.2.17. If AE2 changes multipart construction ordering, these two
wrappers and the native part/copy/merge tests must be reviewed before changing the supported version.

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
native standalone ME chest in the naturally loaded spawn chunk, assigns provider data, and shuts down through Minecraft's
real world save. Process two constructs different Grid/service/node objects and logs `restored=true` from `addNode`.

Task cases:

- `identity.replace-all-access`: attachment replacement leaves the native Grid lineage unchanged.
- `identity.restart`: two actual server processes recover the same `NetworkId` from the unchanged native network.
- `identity.ambiguous-split`: removal of a real native middle node produces two Grids that both fail closed.
- `identity.copied-node`: native block-entity NBT copied into a second live disconnected chest is rejected for both.
- `identity.part-on-settled-cable`: a cable and then an Export Bus added to a settled Grid keep it `SETTLED` with its
  NetworkId, and linking it to a second established Grid still reports `AMBIGUOUS_MERGE`. Before the provisional rule
  the Export Bus alone made the Grid `AMBIGUOUS_MERGE`.

- `identity.single-established-merge`: a standalone node has persisted Policy, reloads legacy provisional NBT without
  losing lineage, and retains but cannot inherit that Policy after a real merge.
- `identity.legacy-provisional`: copied legacy NBT remains detectable both alone and after joining a larger Grid.
- `identity.initialization-order`: new-first and established-first assembly, repeated reads, and reverse saved-node
  loading all converge to the same established identity.
- `identity.part-policy-activation`: a real Export Bus added to existing cable preserves both Policy endpoint IDs,
  revision and ACTIVE state through the real Domain/Policy services.
