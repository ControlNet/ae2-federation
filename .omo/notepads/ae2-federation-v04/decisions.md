# Decisions — ae2-federation-v04

Architectural choices and rationales discovered during work on this plan.

_Auto-scaffolded by /start-work. Append new entries below - never overwrite._

---

## 2026-09-13 - Task 1

- Keep `common` as a source/resource directory consumed directly by the sole `neoforge-1.21.1` Gradle project; it is not a second subproject or distributable JAR.
- Declare AE2 and LDLib2 as exact `implementation` mod dependencies so the ModDevGradle dedicated-server surface loads the qualified runtime tuple.
- Use separate `@Mod` entrypoints for BOTH and CLIENT physical sides. The common startup path has no reference to `net.minecraft.client`; client classes live under isolated client packages and are reached only by the client-only entrypoint.
- Preserve the candidate dependency versions exactly. The built tuple resolved and ran, so no candidate change was authorized or needed.

## 2026-09-13 - Task 1 loopback correction

- Prepare `neoforge-1.21.1/run-server/server.properties` before `runServer` with `server-ip=127.0.0.1`. The task preserves other generated properties and never reads or writes a user server directory.
- Keep the Task 1 plan checkbox unchecked until independent verification confirms this correction.

## 2026-09-13 - Task 2

- Keep the normal `main` artifact unchanged and load `common/src/testmod` as the separate dev mod
  `ae2federation_test` only in the ModDevGradle `gameTestServer` run.
- Register only the selected annotated test method per isolated GameTest process. Ordinary direct execution selects the
  positive required test; deliberate failure, timeout and benchmark variants are `manualOnly` and selected explicitly.
- Treat the wrapper as a selector, launcher and evidence validator. Native `GameTestServer` remains the world-test
  engine and owns required-test exits and diagnostics.
- Keep restart support at the process-orchestration boundary. No structure reset is described as a server restart or
  natural chunk unload.

## 2026-09-13 - Task 3

- Keep all UI fixtures and scenarios in the dev-only testmod; the production JAR remains unchanged and contains no
  LDLib2 scenario classes or `ae2federation_test` resources.
- Treat LDLib2's synthetic selector input, waits, checks, screenshots, and report as the actual-client test engine. The
  Gradle wrapper owns exact-case selection, immutable evidence binding, adversarial report probes, Xvfb launch, and cleanup.
- Use server-owned fixture state and the parent run ID for acknowledgment. A local callback alone is not accepted as
  client/server synchronization evidence.

## 2026-09-14 - Task 3 semantic-consumer repair

- Make `verifyLdlibReport` the shared semantic boundary for fresh production and persisted evidence consumption, then
  cross-check the verified upstream projections against the outer schema-v3 result.
- Model `ui-harness.reject-stale` as a wrapper `self-test`; only `ui-harness.shared-resource` and
  `ui-harness.server-ack` are actual LDLib2 scenarios.
- Keep screenshot inspection read-only by writing ImageMagick output to task-owned temporary state rather than beside
  immutable evidence.

## 2026-09-14 - Task 4

- Use a versioned UUID `NetworkId` plus per-node persistent UUID/revision metadata owned by one registered native AE2
  Grid service. Do not add an anchor block or derive identity from positions, Bridge, Fabric, or Grid serial.
- Keep world persistence sparse: serialize settlement status only; keep live Grid claims process-local and never persist
  native Grid/node handles.
- Only `SETTLED` permits Policy inheritance. Copied live identities, splits, merges, conflicting metadata, and incomplete
  evidence remain fail closed without timeout, newest-wins, load-order, or location-based resolution.
- Treat pinned AE2 commit `79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a` and public internal
  `appeng.me.GridNode.callListener` as the narrow compatibility boundary.

## 2026-09-14 - Task 4 adversarial repair

- Use a dev-only `FederationIdentityAccessAttachment` fixture for Task 4 lifecycle proof. It binds to the real native
  node and production identity service but does not introduce production gameplay or pre-implement Task 5+ topology.
- Share one `verifyTaskFourEvidence` semantic boundary between fresh production and persisted consumption so both paths
  enforce identical case, settlement, identity, object-trace, restart, and artifact-cardinality rules.

## 2026-09-14 - Task 5

- Define native attachment as one verified in-world face edge to the currently exposed adjacent node, not allocated node
  existence, power, boot state, owner class, location identity, or Federation identity.
- Represent Hub ME boundaries as six distinct native nodes and prohibit every internal native connection between them.
- Preserve face ownership separately from membership deduplication; group repeated observations only by native `IGrid`
  object identity.
- Share one `verifyTaskFiveEvidence` semantic parser between fresh production and persisted consumption, with fully
  rebound adversarial probes for topology meaning and artifact cardinality.

## 2026-09-14 - Task 7

- Model the Endpoint gate as a narrow capability composition over one subnet node and one identified native Provider
  return inventory; do not create a block implementation, stocking inventory, or processing engine in this feasibility task.
- Bind Local ownership only for exactly one Provider whose source Grid differs from the subnet and whose target side has
  no native data edge. Bind behavior by capability call context, not item/fluid identity.
- Pin native Local input provenance with narrow `HEAD` probes on `PatternProviderLogic.pushPattern` and
  `PatternProviderTargetCache.find`; keep the probes observational and leave all execution semantics in AE2.
- Derive Local ownership from immutable world coordinates and native block/capability identity. Caller candidate lists may
  nominate Providers, but cannot establish adjacency or Endpoint targeting.
- Resolve Endpoint node/storage separately for each non-Federation face and fail closed when either native sided lookup
  disagrees with the composition's subnet identity.

## 2026-09-14 - Task 8

- Define native Storage sources as real delegates mounted by active node `IStorageProvider` registrations, not the Grid
  aggregate, cached counters, snapshots, labels or all global service mounts.
- Exclude Federation projections and resolve known aliases only from registration-owned provenance. Deduplicate physical
  sources by native object identity and fail closed for complete aggregate loops or opaque aliases.
- Keep mount and delegation Mixins observational. AE2 retains priority, filtering, preferred-storage, listener and
  operation semantics; the production compatibility boundary only identifies delegates and priorities.

## 2026-09-14 - Task 8 independent-review repair

- Qualify source identity only after resolving `IStorageProvider` internally from an actual `IGridNode`. A Federation
  provider may create projections and routes but cannot declare an arbitrary `MEStorage` native or opaque.
- Consume callbacks directly on each query rather than replaying a parallel provenance mount map. Preserve native handle
  identity and merge duplicate route priorities with `Math.max`.
- Require independent provider callback traces in persisted evidence so four route labels cannot substitute for four
   distinct mounted providers and their actual priorities.

## 2026-09-14 Task 9 Fail-Closed Binding Gate

- Preserve locked versions and explicit mandatory API requirements. Emit diagnostic `BLOCKED` schema-v3 attempts with zero execution/assertions instead of claiming native lifecycle success.
- Reject completed-looking Task 9 records until actual native runtime proof exists. Provider-only acceptance cannot substitute for planner, CPU, link or delivery ownership.

## 2026-09-14 Task 9 Verified Binding Decision

- Bind to AE2's actual public 19.2.17 lifecycle: `beginCraftingCalculation`, `submitJob`, `result.link()`, link NBT, and
  `StorageHelper.loadCraftingLink`; remove the obsolete unavailable-binding gate.
- Use null-requester player submission for terminal equivalence and pinned `MultiCraftingTracker` for automation.
- Keep requester delivery on a physical native storage sink, expose restored links before Grid registration, and verify
  accepted output independently from link completion.

## 2026-09-14T08:02:00Z Task 9 Evidence Repair Decision

- Use AE2 link UUIDs as the authoritative native job identity and derive cardinality from the observed UUID set.
- Exercise duplicate discovery through the pinned tracker's existing active-slot guard; do not add a Federation scheduler,
  deduplication ledger, or shadow job model.
- Require both sides of material movement before cancellation: zero remaining in Grid and 64 stored in the native CPU.

## 2026-09-14T10:55:00Z Task 10

- Keep Task 10 test-only: qualify the pinned Quartz Fiber composition, but add no production energy adapter because the
  composition cannot enforce directional authorization.
- Emit schema-v3 `BLOCKED` only after all five canonical GameTests pass their factual observations. Ordinary persisted
  consumption and fully rebound completed-looking evidence both fail closed.
- Record Fabric boundary power as zero idle cost, not generated energy; no FE storage or synthetic energy key is used.

## 2026-09-15 Task 11

- Keep the Bridge direct-only: it may publish a typed two-domain membership candidate for later federation work, but it
  must not call `GridHelper.createConnection` or otherwise join native Grids in production.
- Treat a missing adjacent node as `MISSING_OUTER_ATTACHMENT` and a non-air unsupported candidate as
  `FEDERATION_CABLE_UNSUPPORTED`; both remain fail closed and publish no membership candidate.
- Make repeated removal idempotent by destroying only the Bridge-owned outer node once, retaining `REMOVED` status, and
  allowing AE2's native part lifecycle to own the main node teardown.

## 2026-09-15T01:55:00Z Task 12

- Represent each Hub face with one independently owned `HubFacePort`: one native managed node, one custom capability
  cache, and one typed binding. Never connect Hub boundary nodes internally.
- Model Federation Cable as a sided `FederationPort` block capability only. Physical adjacency and opposite-facing port
  identity establish a custom topology edge; the cable exposes no AE2 node, energy overlay, or transfer engine.
- Resolve native and Federation candidates independently and accept exactly one. Neither candidate or both candidates
  fail closed. Guard native resolution with `ServerLevel.isLoaded` and rely on `BlockCapabilityCache`'s unloaded-null
  contract so discovery never forces a neighbor chunk.
- Keep Task 12 evidence namespaced under `hub.*` with a dedicated semantic validator and adversarial self-test; prior
  task case sets and validators remain unchanged.

## 2026-09-15 Task 13

- Keep direct Bridge Fabrics independent even when they share a native Grid. Physical Fabric merging requires loaded,
  reciprocal Hub/Cable port evidence and never follows native Grid equality alone.
- Publish only settled native identities, invalidate indexes before bounded component recomputation, and bind consumers to
  generation-bearing `FabricReference` values so uncertain or stale topology cannot be routed.
- Seed only new Federation-owned boundary nodes from settled adjacent native identities; preserve loaded NBT and leave
  genuine native merge ambiguity unchanged.

## 2026-09-15 Task 13 loaded-neighbor repair

- Treat an unloaded adjacent position exactly like an absent seeding candidate: do not call `GridHelper.getExposedNode`,
  do not force-load, and create the new boundary node without inherited identity.
- Keep `FabricRegistry` intact at 244 pure LOC because it remains one cohesive component-recompute owner within the
  warning band. Split the 256-pure-LOC test module by topology type instead: Bridge diamond versus Hub/Cable cases.

## 2026-09-15T04:50:00Z Task 14

- Model Policy keys as ordered consumer/provider `NetworkId` pairs plus `PolicyCapability`; never key configuration by
  runtime Grid objects, Bridge instances, Fabric IDs, or routes.
- Use authoritative compare-and-set revisions for edits and deletes. Preserve deletions as revisioned tombstones so stale
  writers cannot resurrect removed rules; keep unconfigured pairs absent for sparse scaling.
- Resolve activation in fail-closed precedence: `UNCONFIGURED`, `OFF`, `DISCONNECTED`, `BACKEND_UNREADY`, then `ACTIVE`.
  `ACTIVE` requires both settled identities and an intersection of confirmed Task 13 Fabrics.
- Store Policy `SavedData` in the overworld and persist configuration only. Runtime endpoints, activation, caches, routes,
  `IGrid`, and `FabricId` remain derived and non-persistent; storage reexport defaults to false.

## 2026-09-15T06:45:00Z Task 15

- Use one production `FabricPolicyMenu`/`FabricPolicySession` path for Hub and Bridge entrypoints. Testmod code may arrange
  world topology and actions but must not own Policy state, fabricate acknowledgments, or substitute a parallel session.
- Treat stale Fabric generation or Policy revision as terminal for the open editor: synchronize the rejection and disable
  every selector and mutation control until the player reopens against current context.
- Keep disabled Bridges diagnostic-only, use deterministic server acknowledgment `policy-<revision>`, and bind evidence to
  the same ordered real `NetworkId` pair and capability observed from both entrances.

## 2026-09-14T21:53:05Z Task 16

- Keep Task 16 as a reusable `processing/provider/` composition and typed host boundary rather than introducing the
  Task 17 physical block, orientation, Claim, or Endpoint routing lifecycle early.
- Persist deterministic slot-to-Lane assignments and per-slot generations before restoring native Lane state. Refresh
  only affected global providers for mapping/inventory changes, but refresh every Lane when shared priority changes.
- Keep one physical managed node with one composite ticker and zero node-local crafting-provider services; Lane providers
  remain distinct global AE2 media and own independent native execution contexts.

## 2026-09-15 Task 17

- Expose one Provider managed node on exactly five native faces and exclude the Federation face. Rotation updates those
  connections only; stable Provider identity is UUID plus instance epoch and is never derived from position.
- Store the authoritative Claim on the Endpoint as typed owner identity and monotonically advanced Claim epoch. Acquisition
  is synchronized compare-and-set; an existing owner is retained offline and cannot be stolen by timeout or a newer load.
- Authorize a native Lane target only after loaded Endpoint capability identity, Claim owner/epoch, native target node and
  storage identity, source-target Grid separation, non-overlapping target domain, settled network identities, common Fabric,
  and Processing EXECUTE/SUPPLY Policy checks all pass.
- Keep execution inside AE2: the Mixin intercepts only bound target lookup and resolves through public
  `PatternProviderTarget.get`; native push, send-list, remainder inventory, ticking, Blocking, and persistence remain AE2-owned.
- Make `ProviderRuntime` the production lifecycle owner that constructs five-plus-one wiring and binds every mapped Lane;
  register the Endpoint target capability from the NeoForge entrypoint instead of relying on test-only direct calls.

## 2026-09-15 Task 18

- Keep Task 7's `EndpointCapabilityComposition` as the native node/storage and Local-owner qualification boundary, and
  compose it from `processing/endpoint/EndpointRuntime` rather than creating new routing semantics.
- Represent Local/Federated ownership and item/fluid returns as immutable generation-bearing records. Fresh capability
  lookup follows only current mode state; an already issued return adapter retains only its original native owner.
- Implement the production Endpoint as a custom networked block with no inventory. Register node, ME storage, item, fluid,
  and Task 17 target capabilities only on its five logistics faces; keep the Federation face outside native logistics.
- Keep all replay control and mutable replay state in the separately loaded testmod. Production `ProviderRuntime` retains
  only its normal resolver state, while testmod Mixins observe genuine issued authorizations and the unchanged native
  resolver, Pattern Provider Mixin, cache, and Endpoint rejection path.
- Enforce the release boundary on both binary and sources JARs, including private ProviderRuntime symbols, instead of
  excluding a known test bridge by filename.

## 2026-09-15 Task 19

- Preserve AE2's exact ownership transition: false keeps the full batch caller-owned; true may transfer an accepted prefix
  and place only the suffix under native `sendList` responsibility. Add no refund, replay, retry, scheduler, or transaction.
- Keep all Processing probes, accessors, selectors, and trace collection in the testmod. Production Provider, Endpoint,
  Claim, Policy, and native target routing remain unchanged.
- Bind evidence to AE2 19.2.17 and the inspected source digest, require the exact six-case set, correlate every property to
  runtime traces, and reject fully rebound fabricated atomicity or missing native authority traces.

## 2026-09-16 Task 19 adversarial repair

- Authenticate the AE2 dependency JAR and exact `PatternProviderLogic.java` source with separately named hashes; never use
  a binary digest as a source-semantics receipt.
- Prove restart at the lifecycle boundary by disposing the original owner and loading NBT into a newly constructed owner.
- Treat native observation/state/target records as independent semantic receipts and require rebound ownership, quantity,
  snapshot, dismantle, and cleanup mutations to fail even when artifact and report hashes are recomputed.

## 2026-09-16 Task 19 lifecycle adjudication repair

- Bind dismantle verification to AE2's authentic `addAdditionalDrops -> clearContent -> removal` caller lifecycle.
  `addDrops` remains observational; exactly-once ownership is proved by ordered lifecycle receipts and final conservation.
- Require exact ordered receipt and property schemas for all six Processing cases, plus exact cleanup-artifact content.
  Missing, extra, duplicate, reordered, malformed, rebound, and swapped semantic evidence fails closed.

## 2026-09-16 Task 19 exact-schema scope repair

- Execute the Task 19 receipt and property allowlists only inside `verifyTaskNineteenEvidence`, after all six artifacts and
  receipt streams are parsed. Task-specific evidence rules must not depend on another task verifier being selected.
- Retain the original wrong-owner extra-receipt mutation and add distinct fully rebound injected-property,
  omitted-property, and authentic-owner extra-receipt probes, producing a 46-probe matrix without removing categories.
