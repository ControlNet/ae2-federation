# Native integration gates

## Task 4 - Network identity through native Grid Service node data

Status: `PASS`

Native owner: AE2 `Grid`, `GridNode`, managed-node host serialization, and `IGridNodeListener` save notification.

Adaptation: one registered Federation Grid service writes namespaced lineage metadata through `saveNodeData`, restores it
through `addNode`, and keeps a sparse world settlement registry. It does not add an anchor block, persist Grid handles,
or derive identity from location, Bridge, Fabric, or Grid serial.

Runtime evidence requirement: the schema-v3 Task 4 attempt must contain successful native reports for all four manifest
cases, separate `restart-prepare` and `restart-verify` child exits, native save/add traces with changed process-local object
identities, source/dependency/JAR hashes, and cleanup receipts. A missing or stale report is not a PASS.

Persisted consumption semantically parses exactly one hash-bound native properties artifact per Task 4 case. It requires
the exact four-case set, versioned `NetworkId` values, case-specific settlement, nonzero native accounting, access
attachment replacement facts, fail-closed split/copy object facts, and correlated two-process restart traces. Rebinding
paths and hashes cannot turn a forged native settlement into accepted evidence.

Compatibility boundary: pinned AE2 commit `79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a`, specifically public
`appeng.me.GridNode.callListener`. Removal of that method without a public equivalent changes this gate to `BLOCKED`.

## Task 5 - Native attachment and isolated boundary nodes

Status: `PASS`

Native owner: AE2 `IInWorldGridNodeHost`, `InWorldGridNode`, `IGridConnection`, and `IGrid` object identity.

Adaptation: `NativeAttachmentResolver` accepts a port only when its independently owned in-world boundary node has
exactly one face connection, the connected opposite node is still the node returned by AE2 exposed-node discovery, and
both endpoints have the same current native `IGrid`. A ready node with an allocated Grid but no face edge is invalid.
Cable and block-device neighbors use the same native discovery path; no device-class whitelist is present.

Hub proof: `HubBoundaryTopology` requires six distinct boundary node objects, one per direction. It never creates a
native connection between those nodes. Six distinct native neighbors therefore remain six `IGrid` objects. Multiple
faces that reach one externally connected native Grid remain separate face records and are grouped only by `IGrid`
object identity for membership deduplication.

Fail-closed behavior: missing, unsupported, floating, replaced, or multi-edge ambiguous neighbors are rejected. The
replacement fixture first proves a real native attachment, removes its exposed AE2 host, then proves the stale boundary
resolves empty. A Hub
native join request is rejected without calling `GridHelper.createConnection`; AE2's connection implementation would
merge the endpoint Grids and violate the product boundary.

Runtime evidence requirement: schema-v3 Task 5 evidence selects exactly the five `ports.*` cases, contains one native
properties artifact per case, records all six face-to-Grid identities, repeated-Grid deduplication, cable/device native
edges, floating/unsupported/ambiguous rejection, cross-grid rejection, source/dependency/product identity, timestamps,
artifact hashes, and cleanup receipts. Every Grid identity is also emitted as a deterministic fact in the separately
hash-bound positive runtime log and persisted consumption requires exact property-to-trace correlation. Executable fully
rebound probes reject missing, duplicate, substituted, malformed, forged-identity, distinct identity substitution,
floating/replaced acceptance, and cross-grid-acceptance data.

Compatibility boundary: pinned AE2 commit `79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a`, specifically public
`GridHelper.getExposedNode`, `IGridNode.getInWorldConnections`, `IGridConnection.getOtherSide`, and `IGridNode.getGrid`.
If native face edges can no longer be inspected independently of standalone node allocation, this gate becomes
`BLOCKED`.

## Task 6 - Independent native Pattern Provider lanes

Status: `PASS`

Native owner: AE2 `PatternProviderLogic` owns each lane's Blocking, lock, target cache, send list, return inventory,
priority, NBT state, and push implementation. AE2 `ICraftingService` owns publication and medium selection. One
Federation `AppEngInternalInventory` owns the encoded Pattern items, extraction, save, and Pattern drops.

Composition: each real `PatternProviderLogic` is constructed with a managed-node facade that forwards node state while
capturing the native `IGridTickable` and `ICraftingProvider` services installed by the constructor. The physical node
owns one composite ticker that delegates to all captured native tickers. Lanes are registered as distinct global
crafting providers and refreshed only through `refreshGlobalCraftingProvider`; the singular node-provider refresh path
is not used.

Mapped Pattern views: lane assignment is a slot predicate over the one physical Pattern inventory. A required Mixin
accessor exposes only AE2's private decoded `patterns` list and `patternInputs` set so the subclass can rebuild those
views. Execution still enters `PatternProviderLogic.pushPattern`, including membership checks, native target discovery,
Blocking, lock transitions, send/return handling, and native NBT methods. Lane Pattern inventories have zero slots and
therefore cannot duplicate encoded Pattern ownership.

Runtime evidence requirement: schema-v3 Task 6 evidence selects exactly the five `lane.*` cases and contains one native
properties artifact and one positive runtime log per case. Persisted consumption correlates every semantic property
with `AE2F_LANE_TRACE`. The three-way case queries pinned `CraftingService.getProviders(IPatternDetails)` and requires
three provider mediums whose identities are the three captured native lanes, observes all three captured ticker delegates
being invoked through the native Grid tick manager, and requires zero published mediums after close. It also proves lane
A remains locked while B/C push. The other cases prove mapped subset sizes, one encoded Pattern drop, zero lane Pattern
slots, rejection with a real non-target adjacent molecular assembler present, and native lock/Pattern state round-trip.
Fully rebound probes reject missing/duplicate artifacts, trace disagreement, forged provider count/identity/removal,
forged ticker delegation, duplicate drop ownership, absent adjacent-machine construction, and accepted fallback/bypass.

Compatibility boundary: pinned AE2 `19.2.17`, specifically `PatternProviderLogic.patterns`,
`PatternProviderLogic.patternInputs`, the constructor-installed `IGridTickable`/`ICraftingProvider` services, and public
global-provider registration/refresh/removal methods. Acceptance observation additionally pins internal
`CraftingService.getProviders(IPatternDetails)` because the public craftable query deduplicates equal Pattern details.
Renaming or structurally changing either accessed field or the provider-medium observation, removing native ticker
service installation, or removing distinct global-provider refresh changes this gate to `BLOCKED` until an equivalent
native hook is qualified.

## Task 7 - Five-face Endpoint capabilities and Local-mode separation

Status: `PASS`

Native owner: AE2 `PatternProviderLogic.pushPattern` and `PatternProviderTargetCache.find` own Local processing input;
the unconfigured native `InterfaceLogic.getInventory` view owns access to the subnet Grid storage; the identified
upstream `PatternProviderReturnInventory` owns item/fluid returns and its native insertion guard.

Composition: one Federation face is excluded and the other five logical faces share one Endpoint subnet node. Local
ownership is installed only for exactly one physically adjacent native Pattern Provider block entity whose world position,
target direction, exact `PatternProviderLogic`, source node, and return inventory agree. Its target side has no data
connection, its source Grid differs from the subnet Grid, and the Endpoint side resolves the expected node and
`ME_STORAGE` capability. A remote caller-supplied Provider cannot claim ownership. The Provider still reaches the Endpoint
through `ME_STORAGE`, while pipe returns on any allowed face use the same native generic return inventory through AE2's
`GenericStackItemStorage` and `GenericStackFluidStorage` adapters. Selection is by typed call context, never by resource
identity. No Endpoint buffer, stocking slots, output guessing, Grid merge, or device-class whitelist is present.

Mode isolation: Local and Federated target contexts are distinct and generation-bound. Changing mode invalidates Local
input and return contexts, removes the Local owner, rejects local input in Federated mode, and requires a new verified
claim before Local mode can resume. A return context cannot resolve target storage, and an input context cannot resolve
the return inventory.

Runtime evidence requirement: schema-v3 Task 7 evidence selects exactly the five `endpoint.*` cases and contains one
native properties artifact plus one positive runtime log per case. Every allowed face independently resolves the Endpoint
node and `ME_STORAGE` capability through its face-specific native integration query, while the Federation face resolves
neither subnet nor return capabilities. `AE2F_ENDPOINT_TRACE` records Provider/subnet Grid identities, face-specific
node/storage identities, native-return-inventory identity, all five item/fluid results, two-owner rejection, loop rejection,
and mode-generation crossover rejection. Mixin-owned `AE2F_ENDPOINT_NATIVE_ENTRY` records independently identify the
actual `PatternProviderLogic.pushPattern` and `PatternProviderTargetCache.find` owners; persisted consumption correlates
them with the properties facts in addition to ordinary trace correlation. Fully rebound probes must fail at their intended
semantic boundaries, including forged push/target owner identities after ordinary Endpoint facts and hashes are rebound.

Compatibility boundary: pinned AE2 `19.2.17` public `PatternProviderLogic.getReturnInv`, `InterfaceLogic.getInventory`,
`AECapabilities.ME_STORAGE`, `AECapabilities.GENERIC_INTERNAL_INV`, and the generic item/fluid adapter classes. If the
Provider target no longer prioritizes `ME_STORAGE`, or native return inventory insertion can no longer be exposed without
reimplementing its guard/state, this gate becomes `BLOCKED` until an equivalent narrow native hook is qualified.

## Task 8 - Native Storage mount/source separation

Status: `PASS`

Native owner: AE2 `StorageService`, `IStorageProvider`, `NetworkStorage` and the actual mounted `MEStorage` delegates own
aggregation, priority, filters, preferred storage, listeners, listing and operations. Federation invokes the pinned
native provider callback only to qualify source handles and priorities; it does not inspect or replace the Grid aggregate.

Boundary: only mounts captured from a provider resolved internally from an actual non-Federation `IGridNode` qualify native source identity. Federation-owned
route views may reference those handles and deduplicate by object identity at the highest callback priority. A
Federation-managed projection is not exported. A complete `NetworkStorage` aggregate, unqualified mount, invalid route
target, or unsupported opaque alias returns no partial sources and throws an explicit diagnostic.

Runtime evidence requirement: schema-v3 Task 8 evidence selects exactly the four `storage-proof.*` cases, contains one
native properties artifact and positive runtime log per case, and has nonzero semantic assertions. Properties are
correlated with `AE2F_STORAGE_TRACE`; independently injected `AE2F_STORAGE_NATIVE_MOUNT` and
`AE2F_STORAGE_NATIVE_DELEGATE` records prove the mounted and actually queried native object identities, while
`AE2F_STORAGE_PROVIDER_MOUNT` proves four distinct Federation provider callbacks and their priorities. Fully rebound
forgeries of source identity, duplicate diamond count, selected priority, accepted loop and accepted opaque alias fail for
intended reasons.

Compatibility boundary: pinned AE2 `19.2.17` public `IGridNode.getService(IStorageProvider.class)` and
`IStorageProvider.mountInventories`. If active node providers no longer expose their real mounted delegates and priorities
through that callback, this gate becomes `BLOCKED`; enumerating all global service mounts or replacing native aggregation
is not permitted.
