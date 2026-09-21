# Decisions — ae2-federation-v04

Architectural choices and rationales discovered during work on this plan.

_Auto-scaffolded by /start-work. Append new entries below - never overwrite._

## 2026-09-21 Task 32 scoped observability

- Keep observation read-only and Fabric-scoped. Stable IDs hash the Fabric ID, entity kind, and authoritative native
  identity; Provider and Endpoint generations prevent stale runtime replacement from retaining an observation ID.
- Represent native aggregate return inventory as `return-buffered` task state. Do not emit an exact-operation flow unless
  a positive native operation was actually accepted and metered.
- Use one bounded level owner for transport windows and subscriptions, and production lifecycle seams for menu removal,
  logout, invalidation, and level unload. Do not add a parallel polling or scheduling authority.

## 2026-09-21 Task 31 directional ME energy

- Bind ordered `ME_POWER/SUPPLY` Policy relationships to consumer-local sources on existing Bridge/Hub boundary nodes;
  never connect or merge provider and consumer native Grids.
- Capture Policy, Fabric topology/reference, provider generation, exact Grid, and exact native service identity in each
  binding, and revalidate before every simulated or modulating extraction.
- Preserve Task 10 blocked overlay evidence unchanged. Task 31 uses independent `energy.*` manifest cases and verifier.
- Keep the existing 250 AE directional proof and add the >1e9 proof as a second operation in the same canonical case.
  Persist both operations' summed fixed-point work and retain final-review evidence until Atlas and reviewer inspection.

## 2026-09-21 Task 30 mixed factory load

- Compose Task 30 on one `NativeAutomationFixture` / `CraftingBindingFixture` topology. Seed and attach the mapped
  Processing provider to the existing provider Grid; do not nest fixtures that create independent Grids.
- Use two independent one-slot `NativeCraftingRequester` owners for concurrent blocked and progressing jobs. Use one
  occupied one-slot tracker for the overload negative case; bounded native rejection is the proof, not a Federation queue.
- Keep benchmark lifecycle, profile copying, evidence envelopes, source identity, persisted consumption, and mutation
  probes in the Task 20 runner. Task 30 adds profile-specific semantic checks but no parallel benchmark framework.

## 2026-09-21 Task 30 five-blocker repair

- Delete the self-authored mixed lifecycle aggregate. Build each iteration from terminal, CPU, Provider, return-inventory,
  projection, tracker, and bus hook snapshots plus pre-authorized physical handler/Interface receipts.
- Treat warmup as a correctness run only and exclude it from measured operations, transfer totals, and elapsed aggregation.
- Enforce one exact properties schema derived from the profile and frozen baseline, including fixed receipt cardinalities,
  exact per-key fields, unique runtime/job identities, and fully rebound intended-reason mutation probes.
- Refresh Task 20 capture identity only from profile, budget, dependency-lock, and pinned-version hashes; preserve current
  source identity and stale-attempt rejection in the shared outer evidence envelope.

## 2026-09-20 Task 28 native automation

- Keep Task 28 qualification-only. Federation exposes the authorized Storage projection; native `InterfaceLogic`,
  `MultiCraftingTracker`, `ImportBusPart`, and `ExportBusPart` own demand and transfer state.
- Use an automation-only second provider ME Chest with a native fluid cell, created before topology settlement. Preserve the
  callback-probe slot and behavior for every other `PolicyBridgeFixtures` consumer.
- Require physical source and destination observations for real item/fluid movement and exact native owner receipts. Do not
  infer reciprocal authority or add a controller, scheduler, task queue, reservation engine, ledger, replay, or buffer.

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

## 2026-09-16 Task 20 six-blocker finalization

- Keep canonical baseline consumption pinned to seed `20019006`, but allow an explicit producer replay seed for deterministic variation testing.
- Treat seeded topology and schedule as execution authority, not descriptive metadata, and persist exact per-Pattern participation plus five independent cohort observations.
- Bind the baseline to a fail-closed grouped 46-file source-authority manifest and enforce the 250-physical-line Java ceiling in the contract suite.

## 2026-09-19 Task 21

- Key exactly one logical native Storage projection by directional `PolicyKey`; repeated physical Bridge/Hub observations
  reconcile rather than duplicate the mount.
- Discover provider delegates only from active `providerGrid.getNodes()` registrations qualified by Task 8 provenance.
  Aggregate Grid storage, caller labels, managed projections, and opaque aliases cannot become export sources.
- Re-evaluate Task 14 activation, operation permissions, filters, and provider-node readiness on every projection call.
  Revocation both removes the global provider and invalidates held projection handles without cached spendable state.
- Preserve AE2 as allocation and transaction authority by delegating directly to native `MEStorage` and using
  `NetworkStorage` only when multiple qualified native delegates must be composed.

## 2026-09-19 Task 21 independent-review repair

- Derive Storage relationships from current Task 13 Fabric snapshots plus loaded live Grid references on every topology
  transition; do not enumerate routes or scan the world.
- Bind projection authority to fresh qualified callback identity/priority and close all per-level mounts on level unload.

## 2026-09-19 Task 21 repair round 2

- Prove redundant-route deduplication numerically from native operations: reconcile provider quantity with consumer-visible
  quantity and reconcile consumer simulated capacity with exactly one provider capacity plus local consumer capacity.
- Route NeoForge unload and the native cleanup proof through one `StorageLevelLifecycle.close` path whose receipt captures
  exact pre-state, removed provider count, removed object identity, and post-state without lookup-driven recreation.
- Bind cleanup facts into `storage.native-access` rather than trusting the generic unload log, and reject fully rebound
  quantity, capacity, missing-fact, and false-cleanup mutations for Task 21 semantic reasons.

## 2026-09-19 Task 22

- Define origin, export source, alias, source generation, and mount generation as explicit records under
  `storage/provenance/`. Alias identity is native registration node lineage plus callback index, never position or label.
- Retain source identity across a new runtime Grid only when settled Task 4 `NetworkId` and current callback-owned node
  lineage overlap. A same-ID rebound with no source-registration continuity fails as `UNPROVEN_GRID_REBOUND`.
- Mark Federation-created providers and storage views explicitly and exclude them before export discovery. For an external
  callback with multiple distinct untyped handles, reject the whole domain as `OPAQUE_EXTERNAL_ALIAS`; do not guess alias
  equivalence or partially publish earlier native callbacks.

## 2026-09-20 Task 22 independent-review repair

- Represent a qualified callback entry as `(raw callback index, NativeStorageSource)` and use only that retained index for
  `SourceAliasId`. Managed entries remain excluded from publication but continue to occupy their literal callback slots.
- Keep stale projection enforcement unchanged and add read-only lifecycle observations for the current mount generation
  and cumulative native provider removals. Evidence compares those runtime values around stale A operations against B.
- Keep the four canonical Task 22 IDs. Extend `provenance.native-rebind` with the negative callback-slot rebound and the
  genuine relationship remount instead of introducing Task 23 behavior or weakening existing import/opaque boundaries.

## 2026-09-20T01:01:27+10:00 Task 22 identity-settlement gating repair

- Keep `NativeSourceDomainRegistry.discover()` fail closed for an unsettled origin. The deterministic repair belongs in the
  native GameTest fixture because readiness is the caller's declaration that all discovery preconditions are satisfied.
- Define `ProvenanceStorageFixture.ready()` as active node, booted Grid, and present confirmed `NetworkId`; perform no catch-
  and-continue, retry wrapper, arbitrary wait, timeout increase, or production fallback.
- Lock this boundary with `nativeFixtureReadinessIncludesSettledDiscoveryOrigin` and preserve all real callback, duplicate-
  alias, priority, quantity/capacity, operation-authority, rebound, and stale-projection assertions.

## 2026-09-20 Task 23 chain sharing

- Compile effective source relationships separately from configured direct policies. Derived permission may mount a native
  origin but must not mutate, synthesize, or activate the corresponding direct rule.
- Bind effective relationships to every contributing policy/Fabric/source revision and fail closed until recompilation.
- Represent effective authority as operation-to-filter mappings. Public aggregate accessors may summarize authority for
  diagnostics, but runtime permission checks must select the filter belonging to the requested operation.
- Keep identity reconciliation fail closed. Stabilize the GameTest by sequencing native topology creation through settled
  states rather than adding retries, timeout inflation, or production fallbacks for `AMBIGUOUS_MERGE`.

## 2026-09-20 Task 24 storage subscriptions

- Key one listener and ledger by `ExportSourceId + SourceGeneration`, not by route or mounted relationship. Reconcile a
  target set onto that source binding so a diamond emits one effective consumer invalidation.
- Preserve AE2's absolute watcher semantics at the native `StorageService.postWatcherUpdate` seam. Keep delta handling an
  explicit ledger operation and never infer it from the native callback.
- Quantity events invalidate current consumer caches only. Topology refresh remains reserved for Policy, Fabric, source,
  and mount lifecycle changes.
- Register the listener before snapshotting, use a bounded snapshot-race queue, and fail closed on overflow or stale
  generation. Remove the exact registration on source replacement, relationship teardown, and level close.

## 2026-09-20 Task 24 independent-review repair

- Supplement AE2 aggregate watcher callbacks with server-end, cache-only round-robin source reconciliation. Keep the unit
  of bounded work at one source binding per native storage service per tick and never rebuild dependency/Fabric topology.
- Give every registration an immutable ID and active receipt. Overflow and stale work retire through
  `bindings.remove(key, binding)` so an old callback cannot remove a newer generation or same-plan recovery binding.
- Keep synchronization and callback-capture controls entirely in testmod Mixins; production exposes only read-only receipt
  values needed by native evidence.

## 2026-09-20T07:22:00+10:00 Task 24 second independent-review repair

- Replace end-tick aggregate/source snapshots with `ReconciliationBudget(1, 8)`: one source provider and at most eight
  keyed simulated-extraction probes per native service per tick. Preserve full snapshots only at explicit lifecycle reset.
- Retain source-known zero keys after discovery so a later equal-opposite reappearance is detectable without aggregate key
  enumeration. Append newly observed callback keys to the cursor and resume rather than restarting its rotation.
- Require native acceptance to correlate hub, ledger-accept, replay-start, and snapshot-complete receipts. Literal race
  booleans and manual hub publication are not accepted.
- Scope testmod actions to an owner token. Normal fixture close asserts zero pending hooks; intentional abandonment closes
  the temporary owner and proves its cleared actions remain inert at later production boundaries.

## 2026-09-20T08:36:00+10:00 Task 24 third-gate repair

- Own one `SharedDiscoveryCatalog<AEKey>` per identity-distinct native `IStorageService`. Seed it only from qualified source
  lifecycle snapshots and aggregate callbacks whose key is already retained or currently present in a qualified true source.
- Set `MAX_RETAINED_KEYS` to 64 for both the service catalog and every source cursor. Do not evict: overflow increments an
  observable diagnostic, retires every service listener, removes catalog state, and relies on normal plan reconciliation
  for recovery. The supported arrival bound is seven newly retained keys between visits to a listener.
- Remove catalog state with the final exact listener registration. Managed projections neither own source registrations nor
  qualify previously unknown aggregate callback keys.
- Bind testmod callback traces to the exact `SourceSnapshotLedger` identity selected by the hook owner instead of one global
  active trace slot.

## 2026-09-20 Task 24 fourth-gate repair

- Make `IdentityListenerRegistry.closeAll(source)` idempotently snapshot-close every exact registration. Invoke it from hub
  overflow after notification and before catalog removal, independent of listener behavior.
- Keep production dispatch explicit so the testmod `@Redirect` can bracket each exact listener callback. Bind the resulting
  listener identity only to the active trace selected by the accepting ledger.
- Require persisted evidence for both real registration-order paths, two exact overflow removals, inert retained callbacks,
  fresh recovered IDs, a real post-recovery event/delivery, and zero final service catalogs.

## 2026-09-20T11:04:56+10:00 Task 25 resource qualification

- Keep Applied Flux off every default and compatibility classpath until a published artifact is exactly mapped to source.
  The isolated `appliedFluxCompatibility` configuration is intentionally empty and non-resolvable while blocked.
- Use no production addon adapter: AE2 already owns native key registration, serialization, filtering, and storage. Add only
  a generic non-negative checked-long arithmetic boundary under `storage/resources/`.
- Represent `resources.stored-fe` as a source-bound BLOCKED assertion while running the other four cases as real GameTests;
  do not let a factual source inspection become a native stored-FE PASS.

## 2026-09-20T12:15:00+10:00 Task 25 Applied Flux correction

- Supersede the earlier BLOCKED decision using matching commit `a54eafb72d72bd259bc3b5fa226b4f5542c4c3c4` and
  authoritative ordinary correlation. Continue to disclaim reproducible-build or cryptographic binary identity.
- Resolve Applied Flux, GuideME, and Glodium only through `appliedFluxCompatibility` and compile/run `appfluxTest` only in
  the dedicated compatibility process. Default main, testmod, runtime, and archives remain addon-free.
- Require five zero-exit native children and use the ordinary schema-v3 completed consumer. Keep Task 25-specific adversarial
  rejection for provenance, real addon execution, codec/filter identity, long quantities, coupling, and optional absence.

## 2026-09-20 Task 26 native Crafting binding

- Key one logical capability by ordered consumer/provider `NetworkId` plus `CRAFTING`, independent of physical Bridge count.
- Expose exact native `ICraftingService`, providers, provider sources, and CPUs; do not add a Federation planner, CPU,
  order queue, reservation engine, result ledger, or copied executable pattern state.
- Bind validity to Policy revision, Fabric generation, provider generation, settled source identity, and native readiness.
  Any mismatch withdraws the old capability and fails closed before another binding access.

## 2026-09-20 Task 26 first-gate repair

- Keep semantic facts in `NativeCraftingEvidence`, but establish authority through a separate direct native observer. Require
  exact per-child receipt phases and reject any missing, duplicate, substituted, or conflicting phase before correlation.
- Put live final-Fabric removal in `crafting.native-binding`; retain `crafting.reject-unavailable` solely as the independent
  no-CPU/no-fallback proof.
- Make `crafting.native-state-owner` a real A-to-B replacement on one settled source NetworkId. Persist both complete native
  authority snapshots and prove retained A remains empty after B is active.

## 2026-09-20 Task 27 native terminal flow

- Keep terminal discovery, plan polling, and submission state server-thread owned. Cross into AE2's planner thread only
  through an immutable requester containing the already-captured action source and provider node.
- Recheck Policy revision, Fabric generation/topology, service/Grid identity, and exact provider node/provider identity at
  submission. Do not include CPU presence in that check, because native `submitJob` must produce `NO_CPU_FOUND` itself.
- Recognize completed output through native CPU callback plus physical cell insertion correlated to one native job. Do not
  add a Federation result ledger or infer ownership from public aggregate storage.
- Keep Task 27 evidence as an exact five-child schema-v3 contract with a dedicated consumer and fully rebound adversarial
  self-test; do not mark the plan complete before independent Atlas review.

## 2026-09-20 Task 27 independent-gate repair

- Establish result authority before submission from the exact physical cell delegate, then extend it only from actual AE2
  submission/callback/insertion hooks. Do not derive destination authority from `TerminalNativeObservation` or accept an
  arbitrary matching `BasicCellInventory` insertion.
- Prove Policy scope with two genuine patterns owned by the same native provider and an exact stick `ALLOW_LIST`. Keep
  consumer-native craftables empty and reject copied patterns, alternate providers, and recursive projections.
- Leave the plan checkbox unchanged pending a new independent acceptance verdict; add no Task 28/29 behavior.

## 2026-09-20 Task 28 independent-authority repair

- Establish Interface and Storage projection authority before native operations in a dedicated observer that does not read
  semantic facts or native operation observations.
- Treat projection remounts as distinct authorities: capture the newly current projection only after topology settlement
  and before configuration, retain earlier authorized projections, and require every observed identity to be authorized.
- Leave Task 28 unchecked and uncommitted pending independent re-review; introduce no Task 29 behavior.

## 2026-09-20 Task 29

- Retain native request authority across capability withdrawal in `CraftingBindingService`; clear it at level closure.
- Reject rebinding one native crafting UUID to another policy/requester-node/slot authority. Same coordinates do not imply
  requester continuity.
- Reject enabled reciprocal/transitive Crafting request cycles before native backend reconciliation. Add no Federation
  scheduler, queue, result ledger, or result buffer.

## 2026-09-20 Task 29 independent-review repair

- Keep active request ownership in a dedicated bounded registry with one key entry and one UUID entry. Permit an atomic
  object rebind only for a loaded tracker link with the same policy, lineage, slot, and UUID; never infer continuity from
  coordinates or UUID alone.
- Treat capability visibility and native job lifetime as separate concerns. Bridge withdrawal invalidates capability access
  but does not cancel or retire active work; only native terminal state, requester retirement, or level close retires it.
- Establish evidence authority before submission and extend it only through native tracker, CPU, requester callback, and
  exact physical-cell hooks. Semantic properties remain a presentation channel and cannot authorize themselves.
- Keep Task 29 unchecked and uncommitted; this repair does not implement Task 30 or introduce Federation scheduling state.

## 2026-09-20 Task 29 second re-review repair

- Register tracked native work from `NativeTerminalRequest`, rebind it through that same request after requester reload, and
  retire terminal links by polling AE2's `ICraftingLink`; fixture-only synchronization is not lifecycle authority.
- Require the disconnect/restart proof to plan and submit through the exact session request that is later closed. Session
  closure invalidates session APIs only and must not cancel or own the native job or its result.
- Give every Task 29 child an exact semantic field set and reject unknown property or trace facts before semantic values.
  Retain both coordinated identity forgery and fully rebound extra-fact adversarial probes.

## 2026-09-21 Task 30 second re-review repair

- Register a minimal testmod processing block entity rather than promoting a fixture helper: AE2 invokes its capability,
  the machine owns transformation state, and the machine alone injects produced output into the authorized native return
  handler.
- Keep canonical `mixed-small` values while accepting bounded typed profile variations. Build all topology dimensions from
  the parsed profile and execute independent construction probes during the benchmark.
- Require `benchmark-runtime.properties` as a distinct hashed artifact and compare its exact schema/value set against every
  authority-bearing benchmark property before equation checks.
- Persist negative-case values from native/physical before-and-after snapshots and observed tick order; retain no Federation
  queue, scheduler, planner, CPU, result ledger, or stocking controller.

## 2026-09-21 Task 30 final metric-authority repair

- Define per-scene `handlerQuantity` as the checked sum of exact positive `MACHINE_TRANSITION` amounts after owner,
  resource, accepted-input, order, and canonical-count validation.
- Define per-scene `peakInFlight` as the maximum operation-time busy CPU count from `CPU_IN_FLIGHT` receipts correlated to
  submitted job/CPU owners and exact `WAITING` observations, bounded by both the configured CPU limit and owner set.
- Compare both aggregates inside each raw warmup/measured receipt stream. Projected properties and final iteration objects
  are consumers only and cannot authorize either aggregate.

## 2026-09-21 Task 32 independent-review repair

- Use a complete immutable `ObservationSession` in both envelopes and subscription ownership. Supersession creates a new
  generation and nonce; stale packets can neither initialize nor mutate another active projection.
- Use canonical full replacement projections for deltas instead of seven parallel mutable patch maps. Contiguous revision
  checks provide ordering while replacement semantics make removals explicit through absence.
- Treat untagged return-buffer insertions and drains as aggregate Lane returns only. Exact Batch completion remains false
  even when provider/lane identity is known.

## 2026-09-21 Task 32 final runtime acceptance

- Seed the canonical Provider and production Endpoint nodes with the selected Fabric member identities before physical
  attachment, so the test exercises real native grids without creating an ambiguous settled-grid merge.
- Treat NeoForge `PlayerContainerEvent.Close` as the server-side menu ownership boundary. LDLib2's
  `ModularUI.onRemoved()` is client-screen-only and cannot authorize dedicated-server subscription cleanup.
- Require independent `AE2F_OBSERVATION_RECEIPT` records for the real player/menu/scope identity, projection counts,
  Processing acceptance, aggregate-return attribution, and non-flow policy addition/removal.

## 2026-09-21 Task 32 cross-Fabric authority repair

- Bind canonical cross-Fabric rejection to two real production menu subscriptions. Rebound Fabric B's server-issued
  subscription identity under Fabric A's player/menu identity only in the encoded attack payload, never in the baseline.
- Require the persisted verifier to correlate OPENED/snapshot/authority/CLOSED receipts by player, menu, Fabric, generation,
  subscription ID, and nonce, then reject missing or fabricated authority receipts for their intended reason.
