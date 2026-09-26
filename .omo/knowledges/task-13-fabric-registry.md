# Task 13 Fabric Registry

## Registry model

`FabricRegistry` maintains direct Bridge Fabrics separately from physical Hub/Cable components. Direct Bridges have a
stable `FabricSourceId`; physical components are built only from loaded reciprocal Federation-port evidence. Memberships
are indexed incrementally by settled `NetworkId`, while each attachment retains its own `FabricSourceId`.

Invalidation removes affected Fabric snapshots and network indexes before bounded recomputation. `FabricReference`
contains the generation of the observed snapshot, so topology changes reject stale routing inputs immediately. Node and
port visit limits are explicit in `FabricRecomputeBudget`; exhaustion leaves the affected component invalid rather than
publishing partial truth.

## Native identity integration

Federation-owned Bridge and Hub boundary nodes are adapters, not independent native networks. When created beside an
already settled native Grid in a loaded adjacent position, they receive a fresh node lineage under that Grid's durable
`NetworkId` before AE2 node creation. Both Hub and Bridge seeding prove `ServerLevel.isLoaded` before querying the exposed
neighbor node, so initialization never retrieves or force-loads an unloaded chunk. Existing managed-node NBT is preserved
on reload. Genuine native Grid merges remain governed by Task 4 and continue to fail closed as ambiguous.

## Runtime QA

The Bridge diamond uses color-isolated native cables so all five native Grids settle before Bridge insertion and no
temporary native join/split contaminates identity evidence. Repeated Hub membership extends one native bypass segment per
tick, preventing concurrent temporary Grid lineages.

The exact cases are `fabric.bridge-diamond`, `fabric.hub-merge-split`, `fabric.redundant-membership`,
`fabric.partial-unload`, and `fabric.reject-stale-route`. Canonical schema-v3 evidence is
`.omo/evidence/task-13/loaded-guard-repair-20260915/attempt-20260914T174455385Z/result.json`. Fresh production, persisted
consumption, adversarial membership/identity/trace/attempt/stale-route probes, and the strict build all pass. The Bridge
diamond lives in `FabricBridgeGameTests`; the remaining Hub/Cable Fabric cases live in `FabricGameTests`, keeping both
test modules below the 250 pure-LOC ceiling without changing registered test IDs.
