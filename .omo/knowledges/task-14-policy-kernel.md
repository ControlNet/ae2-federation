# Task 14 Policy Kernel

## State model

Policy configuration is sparse and directional. `PolicyKey` contains the consumer `NetworkId`, provider `NetworkId`, and
`PolicyCapability`; reversing the endpoints selects a different rule. `PolicyStore` contains only configured records and
deletion tombstones. Accepted edits and deletes advance a world-global high-water revision, while stale expected revisions
are rejected without mutation. Tombstones retain the latest revision and prevent an older writer from resurrecting a
deleted rule.

`PolicyRule` owns operations, filters, enablement, and reexport flags. Storage defaults to no reexport. There is no TTL or
LRU behavior and no allocation for unconfigured network pairs.

## Persistence and activation

`PolicySavedData.get(ServerLevel)` always resolves through the server overworld. `PolicyStateCodec` persists the revision
high-water mark, configured rules, and tombstones. It does not persist `IGrid`, `FabricId`, routes, activation results,
backend state, or caches.

`PolicyService.activation` evaluates fail-closed in this order: `UNCONFIGURED`, `OFF`, `DISCONNECTED`,
`BACKEND_UNREADY`, `ACTIVE`. Connectivity requires settled Task 4 identities matching the directional key and a common
confirmed Task 13 Fabric. Native Grid object equality is not federation connectivity.

## Runtime and evidence

The canonical cases are `policy.lifecycle-matrix`, `policy.new-bridge-restore`, `policy.reject-stale-edit`,
`policy.delete-reconnect`, and `policy.sparse-scale`. Lifecycle persistence uses separate prepare and verify JVM processes
and checks distinct process IDs. Replacement Bridges are removed through `IPartHost.removePart`; directly invoking a
part's `removeFromWorld` callback leaves an invalid installed-part fixture.

Policy evidence is state/topology evidence rather than resource-transfer evidence, so each native artifact records one
operation and allows zero inserted/extracted work. Persisted validation still binds exact case identity, assertion count,
runtime traces, child exits, artifact hashes, and two-process restart claims. Accepted evidence:
`.omo/evidence/task-14/attempt-20260914T184506344Z/result.json`.
