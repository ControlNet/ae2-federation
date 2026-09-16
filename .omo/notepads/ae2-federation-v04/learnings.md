# Learnings — ae2-federation-v04

Conventions, patterns, and successful approaches discovered during work on this plan.

_Auto-scaffolded by /start-work. Append new entries below - never overwrite._

## 2026-09-16 - Task 20 T-S04 large variant

- T-S04 quantities describe processing input volume. A valid large call consumes 256 cobblestone while returning ordinary
  stack-sized outputs; encoding or requesting 256 output items leaves the native CPU waiting after one 64-item result.
- Fifteen sequential native planner/CPU jobs can share the one real CPU. Remapping shared Pattern slot 255 through
  `MappedPatternProvider.replaceMapping` before each calculation makes successive jobs execute on all 15 native Lanes;
  restoring the all-Lane mapping afterward preserves 270 live Provider entries.
- Diagnostic evidence `.omo/evidence/task-20-large-15-lanes/attempt-20260916T004725444Z/benchmark-native.properties`
  records native/Federation parity: 3,840 accepted input, 15 accepted pushes, 15 planner calls, 15 submitted jobs,
  15 dispatched Lanes, and fairness spread zero.
- The matching small primitive also completes 240 authentic planner/CPU jobs with 16 input, 4 primary output, and 2
  byproduct per job while cycling evenly over all 15 Lanes. Diagnostic evidence is
  `.omo/evidence/task-20-small-240-lanes/attempt-20260916T005102800Z/benchmark-native.properties`; it reaches only the
  intentionally stale accounting consumer after both scenes complete.
- Final Task 20 v2 combines both workload variants into four serial scenes and verifies separate variant facts rather than
  aggregate parity. Three captures matched over 134 non-timing/non-hash fields before baseline regeneration.
- T-S06 uses observed CPU/Lane/return-owner state and explicitly proves scheduler absence; `activeReturnOwners` replaced
  the misleading `retrySchedulerSize` name. Canonical evidence is
  `.omo/evidence/task-20-v2-final-rebound/attempt-20260916T011433814Z/result.json`.

---

## 2026-09-13 - Task 1

- The official NeoForge 1.21.1 ModDevGradle MDK commit `30cafee9cd8d7f46427ec88fa8579d49c146df9a` jointly pins ModDevGradle `2.0.146`, NeoForge `21.1.250`, Gradle `9.2.1`, and Java 21.
- AE2 `19.2.17` requires Minecraft exactly `1.21.1` and NeoForge `21.1.169` or newer; LDLib2 `2.2.34` requires NeoForge `21.1.216` or newer. NeoForge `21.1.250` satisfies both published ranges.
- Gradle dependency verification ignores ordinary file dependencies. A valid corruption self-test must publish the task-owned copy through a local repository and resolve it in a fresh nested process with an isolated Gradle user home.
- Gradle 9.2.1 configuration cache rejects execution-time access to project/task model objects from Groovy closures. Task inputs/providers must snapshot required values during configuration.

## 2026-09-13 - Task 1 loopback correction

- Minecraft 1.21.1 reads the dedicated-server bind address from `server.properties`; its documented command-line options include port override but no bind-IP override.
- ModDevGradle `taskBefore` can prepare settings inside one run's `gameDirectory`, keeping the loopback default scoped to the task-owned development server.
- A live `ss` snapshot is necessary alongside the startup message: the corrected run listened only on `127.0.0.1:25565`.

## 2026-09-13 - Task 2

- NeoForge 1.21.1 derives the native test function ID from the lower-case method name even when an explicit unprefixed
  structure template is used; `harnessNativeSmoke` registers as `harnessnativesmoke` and loads
  `ae2federation_test:harness_native_smoke`.
- AE2 Grid nodes are initialized after block placement on the server tick path. A real GameTest must wait through
  `GameTestHelper.succeedWhen` before asserting `hasGridBooted`, active state and storage operations.
- The 1.21.1 GameTest launch can emit `No test functions were given!` yet return a successful Gradle child exit for
  namespace mismatch or zero registration. Fail-closed evidence validation must inspect execution/report content.
- A powered ME chest with a 1k item cell supports a compact real AE2 storage smoke and a nonempty benchmark without
  introducing production gameplay or a custom simulation.
# Task 2 path binding and runtime cleanup repair

- Reproduced intact copied benchmark acceptance (exit 0) and retained 6.5 MB runtime tree with world/session.lock.
- Added a failing-first behavioral relocation probe; schema 3 now checks canonical producer root/attempt paths and
  their run-ID-bound integrity hash. Direct and wrapper finalizers preserve diagnostics before runtime deletion.
- Task 2 remains unchecked pending independent review; Task 3 is not started.

## 2026-09-13 - Task 3

- Minecraft accessibility onboarding must be disabled in the task-owned `run-uitest/options.txt`; otherwise LDLib2's
  title-screen wait never starts the selected scenarios.
- LDLib2's report-level environment is captured before scenario `guiScale(3)` takes effect. Render-step attachments are
  the authoritative source for tested GUI scale and window/framebuffer dimensions.
- Dynamic `.omo` session and continuation files cannot participate in source identity. Excluding `.omo/**` from both
  Git diff and status capture keeps independent evidence consumption stable while product/build inputs remain hashed.
- Actual LDLib2 resource reload and authoritative server acknowledgment passed under llvmpipe/Xvfb with synthetic input;
  the accepted schema-v3 attempt is `.omo/evidence/task-03/attempt-20260913T151420452Z`.

## 2026-09-13 - Task 3 independent verification

- File hashes prove integrity only after a report is semantically consumed. A persisted consumer that hashes
  `ldlib2/report.json` but never parses it accepts internally rebound zero-check, malformed, or forged-local-ack evidence.
- Fresh screenshots and live logs can prove the current run worked while the reusable persisted-evidence contract still
  fails closed inadequately; these are separate acceptance surfaces and both must pass.

## 2026-09-14 - Task 3 semantic-consumer repair

- Reusing the producer's `verifyLdlibReport` function in persisted consumption prevents the producer and consumer from
  silently enforcing different LDLib2 semantics.
- Canonical artifact resolution must require exactly one hash-bound `ldlib2/report.json` inside the selected attempt;
  accepting a caller-supplied or duplicate report path weakens evidence identity.
- Final rebound probes against `attempt-20260913T171948925Z` rejected zero upstream checks, malformed upstream JSON,
  and forged local acknowledgment evidence with exit code 1 after all affected identities and hashes were recomputed.
- Timing-aware visual review passed both real Minecraft/LDLib2 screenshots and confirmed the rendered-step attachment as
  the authoritative GUI scale 3, 1280x720 state.

## 2026-09-14 - Task 3 independent re-verification

- Reviewer-owned fresh attempt `attempt-20260913T173814864Z` passed actual-client execution and canonical persisted
  consumption; fully rebound zero-check, malformed-upstream, and forged-local-ack probes each failed at the intended
  semantic boundary.
- Cross-checking outer projections against a freshly parsed, uniquely hash-bound upstream report closes the prior gap
  without treating a file hash as proof of report meaning.

## 2026-09-14 - Task 4

- AE2 `GridNode.saveToNBT` dispatches provider data through `Grid.saveNodeData`; newly assigned provider metadata can use
  the native host save path through pinned `GridNode.callListener(IGridNodeListener::onSaveChanges)`.
- A restart fixture must be placed in the naturally loaded spawn chunk. A remote GameTest allocation is not sufficient
  evidence because its chunk is not loaded when the second server process starts.
- A delayed callback scheduled inside `GameTestHelper.succeedWhen` can produce false success because the outer assertion
  has already returned. Stateful bounded assertions must remain inside `succeedWhen` until evidence is written.
- Copied-live ambiguity must be recomputed when Policy inheritance is queried. The original Grid receives no native node
  event when a disconnected copy appears, so a cached settlement can otherwise remain incorrectly permissive.
- Final schema-v3 attempt accepted by persisted consumption:
  `.omo/evidence/task-04/attempt-20260913T184118156Z`.

## 2026-09-14 - Task 4 adversarial repair

- An access-replacement proof must model attachment ownership explicitly: distinct attachment IDs, zero active access
  objects for a tick, stable native Grid/node objects, and the original/recovered `NetworkId` in native evidence.
- Hash and canonical-path binding do not establish artifact meaning. Task-specific persisted consumption must parse the
  native properties and logs that substantiate outer schema assertions.
- Exact artifact cardinality must reject nested duplicate basenames as well as duplicate root-relative paths.
- Final repaired attempt accepted by canonical consumption: `.omo/evidence/task-04/attempt-20260913T194020162Z`.

## 2026-09-14 - Task 4 independent verification

- A green identity case can still miss its contract when its fixture name implies an attachment lifecycle that the
  implementation never constructs. Reviewer inspection must trace the actual placed/removed block types and node objects.
- Persisted evidence must parse case-specific native semantics. Recomputing a hash after changing `settlement` from a
  fail-closed value to `settled` demonstrates that integrity alone does not prove meaning.

## 2026-09-14 - Task 4 independent re-verification

- Equality of evidence fields does not establish presence: two missing properties compare equal. Behavioral semantic
  probes must delete or empty paired before/after fields as well as change one field to a contradictory value.

## 2026-09-14 - Task 4 native identity fact repair

- Continuity equality is valid only after each native object identity fact is parsed independently. The current producer
  emits canonical unsigned-decimal `Integer.toUnsignedString` values, so accepted syntax is `0|[1-9][0-9]*`.
- A useful persisted-evidence regression must fully rebind run ID, canonical paths, timestamps, path identity and every
  artifact hash, then prove both removed keys and explicitly empty keys fail at the semantic boundary.

## 2026-09-14 - Task 4 independent final re-verification

- Reviewer-owned attempt `attempt-20260913T202413147Z` passed the exact four native cases and canonical persisted
  consumption. Its 33 declared artifacts independently matched their hashes and current source/dependency/product binding.
- Both the executable identity-facts self-test and separate reviewer-created, fully rebound missing/empty probes rejected
  paired absent values at `requireNativeObjectIdentity`, closing the prior null-equality acceptance gap.
- Re-running equivalent settlement, artifact-cardinality, malformed-properties, lifecycle, and restart mutations against
  the fresh attempt produced nonzero exits for each intended semantic reason.

## 2026-09-14 - Task 5

- A ready AE2 in-world node owns a native Grid even when it has no connection, so `getGrid()` cannot distinguish a real
  external attachment from a floating boundary node.
- `getInWorldConnections()` plus `GridHelper.getExposedNode` provides the native face-edge correlation needed to reject
  floating, replaced, unsupported, and multi-edge ambiguous neighbors without a device-class whitelist.
- Six independently exposed managed nodes remain six Grids when no native edge joins them. Two faces can observe one
  externally joined Grid and remain independently owned face records while grouping by native Grid object identity.
- Final canonical schema-v3 attempt: `.omo/evidence/task-05/attempt-20260913T210820250Z`.

## 2026-09-14 - Task 5 independent verification

- Reviewer-owned fresh native attempt `attempt-20260913T212542015Z` passed the exact five cases, canonical persisted
  consumption, the seven built-in mutations, strict check/build, Java diagnostics, production-JAR isolation, and process,
  listener, lock, and runtime-tree cleanup checks.
- Structural identity validation is not provenance validation: replacing one six-grid face identity with a new distinct
  canonical integer and fully rebinding paths, timestamps, and every hash was accepted by persisted consumption.
- Test fixtures that may create multiple nodes on one face must retain handles per created node, not per `Direction`;
  otherwise cleanup silently loses earlier nodes even when the enclosing GameTest process eventually exits.

## 2026-09-14 - Task 5 blocker repairs and re-verification

- `NativePortFixtures` now retains every managed node by creation in an ordered list; successful test teardown destroys
  all same-direction handles and clearing the list makes repeated close safe.
- The existing negative case now proves a live native attachment before removing the exposed ME chest, then records and
  semantically validates `replacedInitiallyAttached=true` and `replacedAccepted=false` after native settlement.
- Deterministic `AE2F_PORT_TRACE` facts in separately hash-bound positive logs provide independent identity provenance.
  Both the built-in and reviewer-owned fully rebound distinct-value substitutions now fail at trace correlation.
- Confirmed fresh attempt: `.omo/evidence/task-05/attempt-20260913T214616406Z/result.json`.

## 2026-09-14 - Task 6

- Three real `PatternProviderLogic` instances can share one physical managed node when constructor-installed services are
  captured by forwarding facades: one composite physical-node ticker delegates native lane tickers, while each lane is
  published through AE2's distinct global crafting-provider API.
- Equal decoded Pattern details remain one terminal craftable Pattern but retain separate native provider mediums in
  AE2 `NetworkCraftingProviders`; this permits one encoded Pattern to execute through three independent lane contexts.
- Native `pushPattern` requires an active powered node. An allocated Grid was insufficient; the fixture needed a real
  `GridHelper.createConnection` to a creative energy-cell node before native pushes could succeed.
- Pattern mapping needs access to AE2's private decoded `patterns` and `patternInputs`. A two-field Mixin accessor is the
  minimum pinned compatibility hook; push, Blocking, lock, target, send, return, and NBT behavior remain in AE2.
- One physical `AppEngInternalInventory` owns encoded Pattern extraction/save/drop. Native lane inventories are size zero,
  while per-lane send/return state remains independently owned and included in native drops/NBT.
- Canonical pre-documentation proof passed at `.omo/evidence/task-06/attempt-20260913T223820331Z/result.json`; a final
  source-bound attempt is produced after architecture documentation is recorded.

## 2026-09-14 - Task 6 acceptance-proof repair

- `ICraftingService.getCraftingFor` deduplicates equal Pattern details and cannot prove execution-context multiplicity.
  Pinned `CraftingService.getProviders(IPatternDetails)` exposes the three actual provider mediums for acceptance checks.
- Captured constructor services should validate that each `ICraftingProvider` is the exact native lane object. Composite
  ticker evidence is authoritative only when `ITickManager.alertDevice` drives the physical node and all three captured
  delegate counters advance.
- Adjacent-machine bypass rejection needs a real AE2 `MolecularAssemblerBlockEntity` on an unconfigured side, not merely
  removal of the configured chest. The candidate must report `acceptsPlans`, remain empty, and not change push failure.
- GameTest server launches share `run-gametest/world`; running them concurrently causes `session.lock` contention. Native
  GameTest executions must be serialized unless the harness gives each process a distinct game directory.

## 2026-09-14 - Task 6 independent re-verification

- Reviewer-owned fresh attempt `attempt-20260913T233036509Z` passed the exact five native lane cases, current-source
  persisted consumption, and the Task 6 adversarial self-test with all five child exits zero.
- A fully rebound reviewer probe removed `nativeTickerDelegatesInvoked` from both the properties artifact and matching
  runtime trace; consumption failed at the intended Task 6 three-way semantic check, not path, timestamp, or hash binding.
- The acceptance scope is three distinct native AE2 provider media over one physical Pattern inventory. Distinct physical
  target blocks are not required by Task 6; Endpoint target binding and routing are deferred to dependent Tasks 7/16.

## 2026-09-14 - Task 7

- A directional native Pattern Provider excludes its push face from Grid connectivity but still discovers the adjacent
  unconfigured Interface through `ME_STORAGE`; this is the native Local input and Grid-separation seam.
- Provider returns are the native `PatternProviderReturnInventory`, and AE2's generic item/fluid adapters can expose it on
  every allowed Endpoint face without material inspection or a second buffer.
- Typed capability purpose plus a mode generation closes both input/return loops and stale Local/Federated contexts.

## 2026-09-14 - Task 7 final verification

- Native method-entry provenance is stronger when the Mixin probes record the actual transformed owner identities and the
  evidence consumer correlates them with independently emitted properties, rather than accepting method-name labels.
- NeoForge sided item/fluid capability lookups on the pinned Provider and the composed Endpoint adapters both reach the
  same identified native return inventory across the five allowed faces.
- Final current-source attempt `.omo/evidence/task-07/attempt-20260914T003210719Z/result.json` passed exact-case runtime
  execution, persisted consumption, fully rebound adversarial probes, strict build, diagnostics, and JAR isolation.

## 2026-09-14 - Task 7 independent-review repair

- A Pattern Provider intentionally suppresses exposed-node discovery on its push face, so physical Local ownership cannot
  require Provider-side `GridHelper.getExposedNode`. The correct proof is adjacent world position plus exact physical
  `PatternProviderBlockEntity.getLogic()` identity, combined with Endpoint-side node and `ME_STORAGE` resolution.
- Face composition is proven only when every face invokes a sided integration path. Repeatedly reading one zero-argument
  node getter is tautological even if the result is correct.
- Fully rebound semantic probes should assert the expected rejection message. Boolean rejection alone can hide an earlier
  trace/hash failure and does not establish that the intended semantic boundary executed.
- Final repaired attempt `.omo/evidence/task-07/attempt-20260914T011114579Z/result.json` records five equal native node
  identities, five equal storage identities, Federation-face exclusion, remote-Provider rejection, and independently
  correlated Mixin push/target owner identities.

## 2026-09-14 - Task 8

- The complete AE2 `NetworkStorage` mount table contains Grid-service/global mounts and cannot be treated as native local
  source provenance. Replaying active node `IStorageProvider.mountInventories` callbacks yields actual source delegates
  and priorities while leaving AE2 aggregation and operations authoritative.
- Projection exclusion and alias resolution must be registration-owned. Stable native object identity deduplicates the
  four-Fabric diamond; complete aggregates and opaque aliases fail closed before any partial source list is returned.
- CELLS history confirms distinct handling for election, source rebuild snapshots, first-filter visibility and listener
  resets. Adopt/adapt those lifecycle invariants later; reject the old API and one-hop restriction.
- Canonical schema-v3 evidence `.omo/evidence/task-08/attempt-20260914T022418032Z/result.json` passed exact-case runtime,
  immediate persisted consumption, intended-reason adversarial probes, strict build, diagnostics and cleanup.

## 2026-09-14 - Task 8 independent-review repair

- A production type is not sufficient provenance when tests can call `mountNative`, `mountAlias`, or `mountOpaque` with
  caller-selected classifications. Qualification must originate from the actual native node provider callback.
- Re-consuming provider callbacks avoids a shadow mount table. Federation providers now create only owner-bound projection
  and route views over a qualified native handle; an unclassified third-party wrapper rejects automatically.
- Diamond evidence needs independently correlated provider identities and priorities, not only four string labels. Four
  distinct provider callbacks and native aggregate mounts now prove priorities `40,30,20,10` and selected priority `40`.
- Repaired exact-case attempt: `.omo/evidence/task-08/attempt-20260914T032607415Z/result.json`.
- Native callback qualification is atomic: every captured mount is validated before any provider-owned source identity is
   published, so a later invalid aggregate cannot leave an earlier callback entry trusted after rejection.

## 2026-09-14 Task 9 API Research

- Pinned source and locked 19.2.17 bytecode agree: calculation is `beginCraftingCalculation`, submission returns `ICraftingSubmitResult`, and reload is public `StorageHelper.loadCraftingLink`.
- Native terminal submission is standalone (null requester); automation uses `MultiCraftingTracker`. The supplied older helper names are absent at the pinned commit.
- Native CPU completion can precede full requester insertion acceptance; eventual runtime proof must observe accepted delivery separately.

## 2026-09-14T04:12:00Z Task 9 Diagnostic Gate Verification

- TDD red: `./gradlew :neoforge-1.21.1:test --tests '*NativeCraftingBindingContractTest' --no-configuration-cache` failed with three expected missing-contract failures (`BUILD FAILED in 3s`).
- Green: focused tests, `federationTaskNineEvidenceSelfTest`, and `compileTestmodJava` passed (`BUILD SUCCESSFUL in 6s`); all eleven explicitly synthetic completed-looking probes rejected at `mandatory-native-binding-unavailable`.
- Exact five-case QA failed as required by the diagnostic gate (`BUILD FAILED in 3s`), writing `.omo/evidence/task-09/attempt-20260914T041141401Z/result.json`: schema 3, status BLOCKED, exact requested cases, zero executed/assertions, bytecode inspection exit 0.
- `./gradlew check build --no-configuration-cache` passed (`BUILD SUCCESSFUL in 1s`). Changed Java diagnostics returned `No diagnostics found`.
- Canonical `federationVerifyEvidence` consumption rejected the diagnostic report (`BUILD FAILED in 967ms`) with `Result report is incomplete or has an unsupported schema`.
- `pgrep -af '[G]ameTestServer|[g]ameTestServer|[f]ederationGameTest'` returned no matches; no `neoforge-1.21.1/run-gametest/**/session.lock` exists. No GameTests were launched or run concurrently.

## 2026-09-14 Task 9 Native Lifecycle Resolution

- `ICraftingSimulationRequester.getGridNode()` is required for AE2 provider-pattern exploration and must return a native
  node captured on the server thread before asynchronous calculation.
- `MultiCraftingTracker` successfully owns automated calculation, submission, link retention, output callback, completion
  callback, and NBT persistence on AE2 19.2.17.
- Requester output must target the physical ME chest inventory rather than the complete Grid aggregate, whose native
  crafting-service mount intercepts crafted output before ordinary storage.
- Restored requester links must be loaded before the replacement node joins the Grid so native nexus reconstruction sees
  them through `getRequestedJobs()`.
- Final source-bound verification passes the exact five cases, schema-v3 persisted consumption, intended adversarial
  rejections, focused contracts, Java diagnostics, and `check build` after documentation and debug-artifact cleanup.

## 2026-09-14T08:02:00Z Task 9 Independent Review Repair

- Repeating the same `MultiCraftingTracker` slot invocation after link installation exercises AE2's real active-link guard:
  the duplicate call returns false and the requester's observed native UUID set remains the original single link ID.
- Native terminal job identity is observable from the busy `CraftingCPUCluster` CPU link even though standalone submission
  intentionally returns no requester link.
- `cpuBusy()` alone is insufficient cancellation evidence. The deterministic checkpoint is Grid planks at zero plus
  `CraftingCpuLogic.getStored` reporting all 64 planks before suspension.

## 2026-09-14T10:55:00Z Task 10

- A one-sided `IEnergyOverlayGridConnection` remains operationally directional only until the declaring side builds its
  overlay. The shared cache then enables reverse extraction from provider to consumer storage.
- Finite native Energy Cells provide exact source-debit and ring-conservation observations. Native extraction has no
  separate target-receipt transaction.
- Cold-start public powered state appears only after AE2's greater-than-30-tick stabilization window.

## 2026-09-15 Task 11

- A direct multipart Bridge exposes an AE2 managed outer node while retaining the ordinary part main node; it classifies
  two native attachment domains but never joins them.
- The main part node's ordinary `getConnections()` is the correct cable-side boot proof. Requiring
  `getInWorldConnections()` on that node rejects valid cable-bus attachment because the in-world face edge belongs to the
  separately owned outer node.
- Same-Grid runtime coverage is modeled with a native cable bypass around the Bridge, not a synthetic
  `GridHelper.createConnection` call.
- Bridge cases are zero-transfer topology checks: schema-v3 expects one operation and allows zero inserted/extracted
  work while still requiring exact native assertion counts and runtime trace correlation.

## 2026-09-15T01:55:00Z Task 12

- A managed in-world node is not discoverable by later-initializing native neighbors unless its owning block exposes
  AE2's `IN_WORLD_GRID_NODE_HOST` capability. Registering the Hub itself as that host changed the mixed runtime case
  from zero native bindings to three native plus three custom Federation bindings.
- One `BlockCapabilityCache` per face provides unloaded-safe Federation Cable lookup and face-specific invalidation.
  Invalidation clears the stale binding synchronously; the server ticker resolves only dirty faces on the next tick.
- Six `CANNOT_CARRY`, zero-idle managed nodes preserve ME boundary isolation while a separate sided `FederationPort`
  capability represents custom physical topology without native Grid edges or Fabric power.
- Final source-bound schema-v3 evidence: `.omo/evidence/task-12/attempt-20260914T155900227Z/result.json`.

## 2026-09-15 Task 13

- Federation-owned AE2 boundary nodes must inherit the adjacent settled `NetworkId` before managed-node creation; letting
  an adapter settle as a standalone Grid first correctly turns its later attachment into an ambiguous native merge.
- Color-isolated AE2 cables provide deterministic adjacent Bridge fixtures without transiently joining the main and outer
  native Grids. Multi-segment native fixtures should extend one connected segment per tick to avoid concurrent lineages.
- Canonical Task 13 evidence is `.omo/evidence/task-13/attempt-20260914T172038907Z/result.json`.

## 2026-09-15 Task 13 loaded-neighbor repair

- Pre-creation identity seeding is external-I/O discovery: checking only the later binding-resolution path is insufficient.
  Every adjacent exposed-node lookup must independently prove `ServerLevel.isLoaded` first.
- A method-scoped source contract locks lookup ordering without requiring an unsafe mocked `ServerLevel`; it failed once
  for each unguarded Hub/Bridge path before the repair and passed afterward.
- The GameTest class exceeded the 250 pure-LOC ceiling at 256. Moving only `fabricBridgeDiamond` into the separately
  registered `FabricBridgeGameTests` reduced the modules to 202 and 69 pure LOC without changing test IDs.
- Superseding Task 13 evidence is
  `.omo/evidence/task-13/loaded-guard-repair-20260915/attempt-20260914T174455385Z/result.json`.

## 2026-09-15T04:50:00Z Task 14

- Sparse Policy state is keyed directionally by consumer `NetworkId`, provider `NetworkId`, and capability. Persist only
  configured rules, filters, flags, authoritative revisions, and tombstones; derive activation from current identity,
  Fabric, endpoint, and backend state.
- A GameTest must remove an AE2 multipart through its owning `IPartHost`. Calling `IPart.removeFromWorld()` directly
  destroys lifecycle state without removing the installed part and can prevent a later replacement node from attaching.
- Policy persistence/revision evidence performs no resource transfer. Its native evidence uses one operation and permits
  zero inserted/extracted work while still requiring exact semantic traces and assertion counts.
- Canonical source-bound Task 14 evidence is
  `.omo/evidence/task-14/attempt-20260914T184506344Z/result.json`.

## 2026-09-15T06:45:00Z Task 15

- Production UI evidence must open `FabricPolicyMenu` against current Hub/Bridge topology and read authoritative
  `PolicyService`/Fabric state; a test-only state fixture can reproduce packets while bypassing the acceptance contract.
- Independently initialized native cable groups that are later joined settle as `AMBIGUOUS_MERGE`. The reliable UI fixture
  builds the proven Bridge core first, waits for settlement, and only then extends both native networks toward the Hub.
- Adjacent transparent extensions cross-connect and collapse the Bridge domains into one native Grid. Red/blue cable
  isolation preserves two settled identities while allowing both entrances to derive the same directional Policy key.
- Canonical source-bound Task 15 evidence is
  `.omo/evidence/task-15/attempt-20260914T205305294Z/result.json`; all five scenarios and 11 LDLib2 checks passed.

## 2026-09-14T21:53:05Z Task 16

- AE2 19.2.17 `PatternProviderLogic.pushPattern` uses equality-based membership; an identity-bound current decoded set is
  required to reject an old equal handle after physical Pattern replacement while still delegating execution to AE2.
- Global Provider priority changes need explicit `refreshGlobalCraftingProvider` calls because native
  `ICraftingProvider.requestUpdate` targets the physical node, which intentionally owns no node-local provider service.
- Runtime qualification uses 3 physical Pattern slots, 3 native Lanes, 5 mapped entries, and 2 independent media for one
  equal Pattern. The counts are test dimensions, not public gameplay limits.
- Canonical source-bound Task 16 evidence is
  `.omo/evidence/task-16/attempt-20260914T220758989Z/result.json`; all five exact cases and adversarial probes passed.

## 2026-09-15 Task 17

- NeoForge's JPMS layer rejects helper classes placed in an AE2-owned package as a split package before Mixin application.
  Authorized Lane binding must remain in the Federation namespace and call AE2's public `PatternProviderTarget.get` seam.
- Task 17 Claim ownership is Endpoint-authoritative compare-and-set state over typed Endpoint and Provider identities plus
  instance/Claim epochs. Offline state does not mutate ownership and there is no TTL, newest-wins, or load-order steal path.
- Provider rotation changes the five-plus-one native exposure while preserving Provider identity, Claim state, and the
  native Lane remainder destination; target authorization remains paused until the new observation settles.
- The exact six native cases passed in one serialized run, followed by current-source persisted consumption and six fully
  rebound adversarial rejections. The accepted source-bound report is
  `.omo/evidence/task-17/attempt-20260914T232106299Z/result.json`.

## 2026-09-15 Task 17 runtime integration repair

- The production path now constructs `ProviderRuntime`, registers `EndpointTargetCapability.BLOCK`, resolves authorization
  through the Endpoint capability, and enters the bound Mixin before AE2 resolves the native target adapter.
- The authorized AE2 push resolves its target twice, once for insertion simulation and once for modulation. Evidence must
  require two Mixin/native-target lookups and one target mutation rather than treating one lookup as the native contract.
- A Federation-routed Endpoint fixture must be physically separate from the Provider's native Grid. Installing the Interface
  beside the Provider creates a native same-Grid merge before authorization and invalidates the scenario.
- Canonical repaired evidence is `.omo/evidence/task-17/attempt-20260915T011829131Z/result.json`.

## 2026-09-15 Task 18

- A dedicated bufferless `AENetworkedBlockEntity` is necessary for the Endpoint capability boundary. Reusing AE2's
  Interface block would leave its previously registered generic inventory adapters available on the Federation face.
- Immutable native return handlers naturally preserve outstanding responsibility: removing an old context from fresh
  lookups does not alter the adapter's captured `PatternProviderReturnInventory`, so mode takeover cannot redirect it.
- AE2 generic adapter remainder values provide the complete Task 18 backpressure contract without an Endpoint buffer:
  simulation and modulation report the exact native accepted amount and leave every remainder with the caller.
- Canonical source-bound evidence is `.omo/evidence/task-18/attempt-20260915T023556832Z/result.json`; Task 7's native
  compatibility cases also passed after Local ownership was strengthened to world-derived adjacency.

## 2026-09-15 Task 19

- AE2 19.2.17 Processing input transfer is nonzero-per-key simulated but not cross-resource atomic. A successful native
  push may leave an accepted target prefix and a Provider-owned `sendList` suffix; callers must not replay the full batch.
- The native result lock counts only the primary output quantity. Byproducts and partial primary returns preserve the lock,
  and completion affects only the owning Lane.
- NBT restores pending send, return, and unlock state. Pinned `addDrops` includes return inventory but omits `sendList`, a
  native dismantle limitation that Federation reports rather than masking with refunds or replay.
- Canonical evidence is `.omo/evidence/task-19-final2/attempt-20260915T133357374Z/result.json`; exact Task 17 and Task 18
  regression producers also passed after the Task 19 changes.
- Release-clean negative testing does not require a production replay API. Testmod Mixins can capture only authentic
  package-issued resolver returns, retain them in test-only identity maps, and substitute them on a later genuine resolver
  invocation while production authorization and cache rejection remain unchanged.
- Distinct object identities are useful only when bound to independent runtime traces. The accepted evidence matches native
  facts against separately logged inventory observations and resolver/Mixin/cache/context observations, then rejects
  removed, malformed, duplicated, swapped, and substituted values after full report rebinding.

## 2026-09-16 Task 19 adversarial repair

- The earlier dismantle characterization was false. AE2 19.2.17 `PatternProviderLogic.addDrops` emits every `sendList`
  entry through `AEKey.addDrops` before adding the return inventory; accepted target contents remain target-owned.
- A restart proof must destroy the original fixture and construct a distinct logic, managed node, `sendList`, and return
  inventory before loading NBT. The repaired case observes three writes, three reads, stable responsibility, and one drain.
- Dependency JAR and exact source identities are different artifacts and now use separately named SHA-256 properties.
- Canonical repaired evidence is `.omo/evidence/task-19-repair-final2/attempt-20260915T145150639Z/result.json`; fresh Task 17
  and Task 18 regression evidence is under `.omo/evidence/task-19-repair-task17` and `task-19-repair-task18`.

## 2026-09-16 Task 19 lifecycle adjudication repair

- AE2 19.2.17 assigns consumption to the caller lifecycle, not `PatternProviderLogic.addDrops`: wrench dismantling collects
  additional drops, invokes `clearContent`, hands off resources, and removes the owner.
- A single ordered receipt stream makes kind, operation, cardinality, and sequencing independently enforceable. Exact
  per-case property allowlists prevent omitted or injected report facts from becoming implicit evidence.
- The final Task 19 artifact is `.omo/evidence/task-19-final-lifecycle/attempt-20260915T171522605Z/result.json`; fresh Task 17
  and Task 18 regressions are under `.omo/evidence/task-19-final-task17` and `.omo/evidence/task-19-final-task18`.

## 2026-09-16 Task 19 exact-schema scope repair

- A correct allowlist is ineffective when placed in the wrong task closure. Adversarial tests must invoke the same verifier
  entry point as the evidence they protect, not merely confirm that equivalent code exists in the script.
- Exact shape checks should precede positional semantic checks so extra authentic-owner receipts and missing or injected
  traced facts fail with deterministic schema errors rather than incidental owner or value mismatches.
- Repaired evidence is `.omo/evidence/task-19-schema-repair/attempt-20260915T180333827Z/result.json`; Task 4/17/18 fresh
  regression evidence is retained beside it in the corresponding `task-19-schema-repair-task*` directories.

## 2026-09-16 Task 20 runtime learnings

- `PatternProviderReturnInventory` draining is not equivalent to requester completion: observe return progress, final owner inventories, requester acceptance, and `jobStateChange` independently.
- A valid requester reload proof needs a lifecycle boundary after the old node is removed and before the replacement node joins; preserving only the crafting UUID is insufficient evidence of nexus reattachment.
- Multi-Lane Federation tests must correlate the accepted `PatternProviderLogic` identity with `EndpointRuntime.itemReturnContext().owner().logic()`; aggregate return totals cannot detect cross-Lane owner replacement.
- Generated Grid counts are only trustworthy after every source/target/satellite Grid has settled and the count is derived from distinct live `IGrid` identities.

## 2026-09-16 Task 20 six-blocker finalization

- Registration is not participation: exact runtime receipts must cover every logical Pattern identity through planner/CPU/provider work.
- Seed replay is strongest when canonical runs pin digests while an explicit alternate seed must change runtime topology and schedule without changing scale or conservation.
- T-S06 requires five independently reset three-Endpoint cohorts. A whole-scene aggregate with literal cohort labels cannot prove cohort isolation or fairness.
- The final canonical artifact is `.omo/evidence/task-20/attempt-20260916T051227490Z/result.json`; three canonical captures matched 648 normalized semantic fields.
- A typed evidence label is insufficient unless the type causally selects runtime behavior. T-S06 became reviewable only
  after `return-congested` controlled a real deferred return-owner wake and cohort finalization rejected absent
  scenario-specific observations.
- Source mutation probes must call the live semantic verifier directly. Passing `requireCurrentIdentity=false` to a
  broad persisted-report verifier can silently bypass the very current-source binding the probe claims to test.
- Runtime-authority closure needs at least one actual producer mutation, not only fully rebound artifact mutation. The
  Task 20 matrix now serially executes an alternate-seed GameTest producer and confirms the canonical consumer rejects it.
