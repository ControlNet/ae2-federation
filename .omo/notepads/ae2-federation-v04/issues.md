# Issues — ae2-federation-v04

Problems and gotchas encountered during work on this plan.

_Auto-scaffolded by /start-work. Append new entries below - never overwrite._

---

## 2026-09-13 - Task 1

- The first strict build caught two configuration-cache-incompatible verification closures. Both were corrected by capturing project paths and the JAR provider during configuration; final strict and fresh strict builds pass.
- LDLib2 `2.2.34` is published with stable Maven checksums, but the inspected public source repository exposes no matching `2.2.34` tag. This remains a documented provenance limitation, not a build incompatibility.

## 2026-09-13 - Task 1 loopback correction

- Independent verification found the initial development server generated blank `server-ip` and listened on `*:25565`. The run configuration now prepares a loopback-only property, with fresh-world log and socket evidence pending reviewer confirmation.

## 2026-09-13 - Task 2

- The initial JUnit red run was stopped by strict dependency verification until the exact JUnit 5.10.1 artifacts were
  added to `gradle/verification-metadata.xml`; after that, it failed for the intended missing-manifest reason.
- The first native GameTest failed because the test resolved the AE2 node before first-tick initialization. Moving node
  lookup into the bounded `succeedWhen` path fixed the root timing contract without sleeps or retries.
- Namespace mismatch and zero registration expose misleading inner Gradle success exits in this toolchain. The outer
  verifier now rejects the native `No test functions were given!` diagnostic and records the contradictory exit.

## 2026-09-13 - Task 3 independent verification

- Fresh actual-client execution, resource reload, synthetic selector input, integrated-server acknowledgment, visual
  rendering, strict build, and production-JAR exclusion pass.
- Blocking: `federationUiVerifyEvidence` validates hashes but does not re-parse the bound LDLib2 report. Independently
  rebound evidence with zero checks, malformed upstream content, or a locally fabricated acknowledgment was accepted.
- Blocking: `tests/scenarios/manifest.json` does not register the three Task 3 `ui-harness.*` case IDs required by the
  plan evidence contract.

## 2026-09-14 - Task 3 repair resolution

- Resolved: persisted consumption now semantically verifies the uniquely resolved, hash-bound upstream LDLib2 report and
  cross-checks its scenarios, controls, attachments, screenshots, rendering metadata, and acknowledgment identity.
- Resolved: `tests/scenarios/manifest.json` now registers both LDLib2 cases and the stale-evidence wrapper self-test.
- The three final selected adversarial probes fail for their intended semantic reasons; no Task 3 repair blocker remains.

## 2026-09-14 - Task 4

- The first verifier integration hit a Groovy 9 bytecode verifier defect for a closure combining a primitive `long` and
  a fourth/default argument. Replacing timeout/retention parameters with one options map resolved the harness failure.
- The first copied-node run exposed misleading GameTest success: a delayed assertion never ran before the outer
  `succeedWhen` completed. The case now cannot pass until both native Grids fail closed and evidence is written.
- The corrected copied-node case exposed a production stale-settlement defect on the original Grid. Reconciliation at
  Policy-access time now observes current live claims and rejects both the source and copy.
- The exact four-case Task 4 verifier and two-process restart completed successfully; no Task 4 blocker remains.

## 2026-09-14 - Task 4 adversarial repair

- Independent verification correctly rejected the stone-toggle replace-all-access case and hash-only persisted native
  semantics. Both defects reproduced before repair.
- The reviewer-equivalent fully rebound copied-node forgery now fails specifically at
  `Task 4 settlement mismatch in native-identitycopiednode.properties`, while canonical evidence passes.

## 2026-09-14 - Task 4 independent verification

- Blocking: `identity.replace-all-access` does not exercise its named acceptance contract. It toggles an unrelated stone
  block while retaining the same ME chest/node/service; no Federation device or attachment is removed or re-added.
- Blocking: persisted Task 4 evidence consumption is hash-bound but not semantically bound. A copied attempt with
  `native-identitycopiednode.properties` changed from `copied-live-identity` to `settled`, then canonically rebound with
  current timestamps and artifact hashes, was accepted by `federationVerifyEvidence`.
- Fresh native split/copied/restart executions themselves passed; these two blockers concern missing acceptance coverage
  and reusable persisted-evidence fail-closed semantics.

## 2026-09-14 - Task 4 independent re-verification

- Both prior defects are independently repaired: fresh attachment close/recreate lifecycle passed on stable native
  Grid/node objects, and fully rebound copied-node `settled` forgery now fails at semantic validation.
- New blocking consumer gap: deleting all four lifecycle native Grid/node before/after identity facts and rebinding
  paths, run, timestamps and hashes is accepted. `requireProperty` compares missing actual and expected values as null.
- Reviewer attempt: `attempt-20260913T195044041Z`; reproducible accepted forgery and log:
  `attempt-reviewer-reverify-missingNativeIdentities/result.json` and `reverify-missingNativeIdentities.log`.

## 2026-09-14 - Task 4 native identity fact repair

- Resolved: `nativeGridBefore`, `nativeGridAfter`, `nativeNodeBefore`, and `nativeNodeAfter` are now individually required
  and parsed as canonical unsigned-decimal facts before continuity comparison.
- Fully rebound missing and empty probes both exit `1` with `is missing, empty, or malformed`; canonical fresh evidence
  remains accepted.

## 2026-09-14 - Task 4 independent final re-verification

- No blocking Task 4 issue remains. Fresh runtime, persisted consumption, executable and independent semantic probes,
  targeted/full Gradle gates, product-JAR isolation, Java diagnostics, and cleanup all passed.
- Groovy LSP remains unavailable because no `.gradle` language server is configured; executable Gradle validation covered
  the changed verifier surface and completed successfully.

## 2026-09-14 - Task 5

- The first exact run exposed a cross-grid fixture ordering defect: boundary nodes were created before neighboring AE2
  block hosts initialized, leaving no face edges. A bounded two-phase wait/create sequence corrected the native fixture.
- A prior Task 4 result could not be reconsumed during Task 5 because schema-v3 correctly rejected its expired timestamp.
  Task 4 contract tests and the full strict build still pass; no Task 4 validator was weakened.
- Groovy Gradle LSP remains unavailable; targeted executable consumer/self-test runs and the full strict build cover the
  changed verifier surface.

## 2026-09-14 - Task 5 independent verification blockers

- Blocking: a fully rebound reviewer mutation changed `native-portshubsixgrids.properties` `face.east` to fabricated
  identity `4294967295`; `federationVerifyEvidence` still exited 0 because it checks only canonical integer syntax and
  six-way distinctness, not correlation with independently consumed runtime/native traces.
- Blocking: the canonical five cases do not construct a valid attachment and then replace its exposed AE2 host/node, so
  the documented replaced-neighbor fail-closed behavior is not executable evidence.
- Blocking: `NativePortFixtures.managedNodes` is keyed by `Direction`. Tests create multiple `EAST` nodes in one fixture,
  overwrite earlier handles, and `close()` destroys only the final same-face node.

## 2026-09-14 - Task 5 independent repair resolution

- Resolved: managed nodes are retained by creation and every handle is destroyed on fixture close.
- Resolved: `ports.reject-floating-node` includes valid-then-replaced native host lifecycle proof with required
  `replacedAccepted=false` evidence.
- Resolved: persisted identity facts are correlated with separately consumed hash-bound runtime traces. A fully rebound
  `face.east=4294967295` substitution exits 1 at `Task 5 runtime trace identity mismatch`.
- Exact native QA, canonical consumer, nine-mutation self-test, strict check/build, JAR isolation, Java diagnostics, and
  process/runtime/lock/listener cleanup all pass.

## 2026-09-14 - Task 6

- The first compatibility attempt used a NeoForge access transformer, but ModDevGradle validated that file against the
  transformed Minecraft source set and rejected AE2 dependency fields as missing targets. The failed file was removed;
  a dependency-targeting Mixin accessor is used instead.
- The initial Mixin package covered the complete production processing package, causing Mixin to reject ordinary lane
  classes as direct references. Moving the accessor into a dedicated `space.controlnet.ae2federation.mixin` package
  resolved runtime class loading.
- The first native push failed because the provider node and creative energy cell occupied separate Grids. Runtime
  activation evidence identified the cause; an explicit native connection changed the same GameTest from failure to
  three successful pushes.
- Groovy LSP is unavailable. Focused JUnit contracts, Gradle script configuration, the adversarial consumer task, exact
  native GameTests, and the full strict build cover the changed verifier surface.

## 2026-09-14 - Task 6 acceptance-proof repair

- Independent review found three acceptance gaps despite the prior green run: provider multiplicity was inferred from
  lane/view counts, ticker delegation was not runtime-observed, and fallback rejection had no real alternate crafting
  machine. All three now have native runtime assertions and trace-correlated persisted facts.
- One parallel manual GameTest launch collided on the shared `world/session.lock`. The same fallback case passed when
  rerun alone; canonical harness execution already serializes its native children.

## 2026-09-14 - Task 6 independent re-verification

- No blocking Task 6 issue remains. Exact native runtime, persisted semantic consumption, fully rebound missing-fact
  rejection, targeted contracts, strict check/build, Java diagnostics, product-JAR isolation, and cleanup passed.
- The aggregate lane ticker is owned by the physical node and is removed when that node is destroyed; `close()` removes
  global provider publication. Reusing one live physical node for replacement compositions is outside this gate and must
  preserve the same node-owned lifetime in later production integration.

## 2026-09-14 - Task 7

- The first contract run failed for the intended missing manifest, production composition, and semantic verifier surfaces.
- Groovy Gradle LSP remains unavailable; executable contract, consumer, adversarial, native, and strict-build gates cover it.

## 2026-09-14 - Task 7 independent-review repair resolution

- Resolved: Local ownership no longer accepts a remote Provider from caller assertions; world adjacency, exact physical
  Provider logic, Endpoint-side native node, and sided `ME_STORAGE` identity are all required.
- Resolved: native-entry parsing moved from Task 6 into `verifyTaskSevenEvidence`, with push/target properties correlated
  against independently emitted Mixin log entries.
- Resolved: fully rebound owner mutations and all prior Task 7 probes now require their intended semantic rejection reason.
- Resolved: the five-face case performs separate native node/storage lookups per face and proves Federation-face exclusion.

## 2026-09-14 - Task 8

- The first native run correctly rejected an overbroad implementation that enumerated the complete Grid aggregate: AE2
  included 201 global/service mounts. The boundary was tightened to registered node providers before final evidence.
- A subsequent producer self-check found the native delegate trace covered only the keyed listing overload. Adding the
  no-argument AE2 listing probe repaired observation without changing storage semantics.
- Groovy Gradle LSP remains unavailable; targeted contracts, executable consumer/self-test and strict build cover it.

## 2026-09-14 - Task 8 independent-review rejection and repair

- Independent review rejected the earlier green attempt because unknown aliases could be accepted through caller labels,
  projection/opaque classifications were test-selected, diamond paths were manual wrappers without provider-priority proof,
  and documentation described an accessor that did not exist.
- Resolved: caller classification APIs were removed; source qualification now comes from native provider callbacks,
  provider-owned views are production-created, opaque rejection is unregistered, and persisted evidence correlates four
   distinct providers, route mounts and priorities. Documentation now describes the actual callback boundary.

## 2026-09-14 Task 9 Mandatory API Conflict

- Required `ICraftingService.beginCraftingJob` and `ICraftingService.loadCraftingLink` do not exist in pinned source or locked bytecode. No shim or dependency change was made.
- The available public lifecycle route remains unproven. This is a requirements blocker, not evidence that native crafting is impossible.

## 2026-09-14 Task 9 Resolution

- Resolved: the available AE2 19.2.17 public lifecycle is now proven by five native GameTests. The obsolete mandatory API
  conflict no longer blocks Task 9 because the executable contract uses the actual pinned signatures without a shim.
- Resolved: stocking callback output no longer targets the complete Grid aggregate and is independently asserted before
  native completion is accepted.
- Resolved: persisted-link reload occurs before replacement-node registration, and cancellation returns the full initial
  CPU inventory with no accepted final output.

## 2026-09-20T07:22:00+10:00 Task 24 second independent-review repair

- The first bounded implementation stopped forcing AE2 cache realization, so empty sources had no known key until a real
  watcher/cache callback. Native ordinary-event fixtures now explicitly invalidate and realize the pinned cache; periodic
  production work remains bounded and probes keys seeded by lifecycle snapshots or real callbacks.
- The first multi-contributor fixture reused an auxiliary callback provider that was not a second qualified source on the
  target Grid. Replacing it with a second physical ME chest created two independently qualified native contributors.
- The first phased overflow proof repeated its destructive 257-event action after a later assertion retried. Splitting
  retirement, recovery, and post-recovery callback checks into distinct phases made each mutation execute exactly once.

## 2026-09-14T08:02:00Z Task 9 Review Gaps Resolved

- Resolved: `uniqueNativeTasks` is no longer a literal claim. Runtime UUID sets from AE2 CPU/requester links determine the
  count, and the stocking case executes a rejected duplicate tracker invocation.
- Resolved: restart cancellation now observes Grid extraction and native CPU inventory before suspension, then proves the
  same 64 planks return through native cancellation.

## 2026-09-14T10:55:00Z Task 10

- The first reverse run extracted before consumer-side overlay discovery and returned zero. Explicit consumer discovery
  reproduced the pinned shared-cache transition and then proved 200 AE reverse extraction with matching consumer debit.

## 2026-09-15 Task 11

- The initial same-Grid fixture attempted a second synthetic connection after AE2 had already created the relevant edge,
  causing `GridConnection.create` to throw. The fixture now uses a three-cable native bypass and performs no synthetic
  connection creation.
- The generic evidence harness initially treated Bridge cases as two-operation work tests. Adding `bridge.*` to the
  topology accounting branch repaired the real schema-v3 `federationVerify` run without weakening semantic checks.
- No unresolved Task 11 implementation blocker remains after the exact five-case runtime run, persisted consumer, and
  fully rebound identity/rebound-case/incomplete-trace self-test.

## 2026-09-15T01:55:00Z Task 12

- The first exact run failed because Hub-owned native nodes existed but the Hub did not expose
  `AECapabilities.IN_WORLD_GRID_NODE_HOST`; all nine nodes initialized as separate Grids and native face count remained
  zero. A focused red contract and runtime repro locked this failure before capability registration repaired it.
- Resolved: the exact five canonical cases, persisted consumer, and fully rebound fabricated-success, case-identity,
  incomplete-trace, and incomplete-attempt probes pass. No Task 12 implementation blocker remains.

## 2026-09-15 Task 13

- Initial Bridge fixtures either merged different temporary node lineages or split a temporarily joined native Grid.
  Pre-creation boundary identity seeding plus color-isolated native cables resolved both failure modes.
- The initial redundant-membership bypass initialized as two temporary native cable groups under some schedules. Phased
  connected placement removed the nondeterminism. Exact runtime, persisted consumption, adversarial probes, and build pass.

## 2026-09-15 Task 13 loaded-neighbor repair

- Independent acceptance rejected the first green evidence because Hub and Bridge pre-creation identity seeding queried an
  adjacent exposed node without first proving the position loaded. Both lookup paths are now guarded and structurally
  regression-tested.
- Fresh exact Task 12 and Task 13 runtime suites, persisted consumers, and adversarial self-tests pass. No unresolved
  loaded-neighbor or pure-LOC blocker remains.

## 2026-09-15T04:50:00Z Task 14

- The first replacement-Bridge run stalled at `MISSING_MAIN_ATTACHMENT` because the fixture called
  `removeFromWorld()` directly and left the old part installed in its AE2 host. Removing through
  `IPartHost.removePart` restored a valid replacement lifecycle.
- The first canonical run completed all GameTests but rejected Policy restart evidence under identity-restart transfer
  accounting. Policy restart verification now expects one operation and explicitly allows zero transfer work.
- No unresolved Task 14 implementation blocker remains after canonical runtime, persisted consumption, adversarial
  evidence probes, strict build, Java diagnostics, and runtime cleanup.

## 2026-09-15T06:45:00Z Task 15

- The first real-topology UI attempts permanently merged independently initialized cable identities. Phased Bridge-first
  construction fixed identity settlement, but transparent adjacent Hub extensions then joined both Bridge domains.
- Distinct red/blue extension colors corrected the remaining topology fault. The exact five-case actual-client run,
  persisted consumer, forged-ack/stale-success probes, focused tests, Java diagnostics, and strict build now pass.

## 2026-09-14T21:53:05Z Task 16

- The first refresh-priority GameTest combined stale rejection and post-replacement execution, obscuring the failure.
  Separating the assertions showed stale rejection was correct; direct inspection of AE2's qualified `patternInputs` plus
  a clear target then proved Blocking input refresh and native execution independently.
- `IGrid.getActiveMachines(PatternContainer.class)` does not search assignable machine keys. Mirroring the native Pattern
  Access Terminal's machine-class enumeration proves exactly one active physical container.

## 2026-09-15 Task 17

- The first native launch failed before tests because a helper under `appeng.helpers.patternprovider` created a JPMS split
  package. Moving it to the Federation namespace and using AE2's public native target adapter resolved the launch failure.
- The generic native evidence pre-check initially omitted the `claim.*` one-operation topology cases, and the aggregate
  artifact check compared generated names incorrectly. Focused verifier diagnostics exposed both mismatches; exact String
  filename accounting now passes fresh and persisted verification.
- Groovy LSP remains unavailable. Executable Gradle configuration, exact native production, persisted consumption,
  adversarial self-test, focused Java tests, and the full build/check gates cover the verifier surface.

## 2026-09-15 Task 18

- The first native Local run timed out because the fixture used `GameTestHelper.getBlockEntity` to probe an intentionally
  absent optional second Provider; that helper asserts presence. A loaded-world optional lookup fixed the fixture without
  changing production behavior.
- Directory-wide Java diagnostics briefly timed out in the LSP daemon; focused diagnostics over every changed production,
  unit-test, and testmod Java area subsequently returned zero diagnostics.
- A testmod injection into `PatternProviderLogic.findAdapter` did not observe the production Mixin because the production
  cancellable injection supplied a return first. Observing its downstream `ProviderTargetTrace.recordMixin` call preserves
  exact owner identity without competing injection ordering.
- The first identity-bound verifier runs rejected valid inventory and negative traces because interpolated Groovy keys
  remained `GString` values and lookup-count property names require an `Observed` infix. Normalized keys and explicit
  property mapping fixed the parser; the expanded adversarial self-test now passes.

## 2026-09-15 Task 19

- The first shared-capacity return probe addressed item slot zero after filling it; the native item adapter is slot-indexed.
  Using the actual final slot proved item/fluid simulations can overestimate shared capacity and actual callers retain the
  second remainder.
- The dismantle probe disproved the earlier assumption that native `addDrops` includes `sendList`: only return inventory was
  dropped. Evidence now records this pinned limitation explicitly.
- The first parent report omitted Task 19 from task-specific assertion accounting and the executed-case switch. Both
  producer integrations now fail closed and the canonical producer, consumer, and adversarial self-test pass.

## 2026-09-16 Task 19 adversarial repair

- Independent review found that the initial dismantle test asserted the inverse of pinned AE2 bytecode/source behavior,
  restart reused one live owner, and fully rebound semantic mutations passed the consumer.
- The repaired tests now use exact `sendList` plus return drops and a destroyed/reconstructed owner. Native state/target
  receipts and an expanded mutation matrix close the evidence gap without changing production behavior.
- The first lock-state receipt run exposed a null unlock stack after completion. Serializing that valid native state as
  `empty` fixed observation only; the focused lock GameTest and complete six-case producer then passed.

## 2026-09-16 Task 19 lifecycle adjudication repair

- A direct repeated `addDrops` probe correctly duplicated observational output. Pinned AE2 caller inspection resolved the
  apparent contradiction: one authentic `clearContent` must occur between first and second collection.
- Expanding the self-test inline initially triggered a Groovy semantic-analysis compiler defect from excessive nested
  closures. Moving reusable fully rebound mutation probes to top-level closures retained coverage and restored compilation.
- No unresolved implementation blocker remains; Task 19 stays unchecked pending independent review.

## 2026-09-16 Task 19 exact-schema scope repair

- Independent review found the receipt-shape and property allowlists lexically nested in `verifyTaskFourEvidence`, making
  them unreachable from both Task 19 producer and consumer verification.
- Reviewer mutation directories had been cleaned before continuation, so their exact persisted paths could not be rerun.
  Equivalent mutations now run inside the fully rebound self-test and reject with their exact schema errors.
- No unresolved repair blocker remains. Gradle has no configured LSP server, so executable Gradle tasks provide syntax and
  semantic validation; Task 19 remains unchecked pending another independent review.

## 2026-09-16 Task 20 six-blocker finalization

- One initial Task 18 regression invocation used non-canonical case IDs and failed before GameTests launched. The authoritative five-case set was then run successfully with its consumer and adversarial self-test.
- Six Java LSP requests initially timed out; focused retries returned zero diagnostics. No unresolved implementation or verification blocker remains.
- Task 20 intentionally remains unchecked pending independent acceptance.

## 2026-09-19 Task 21

- The first native-access run reached a confirmed Fabric but mounted nothing. Runtime instrumentation distinguished
  `provider=1` from `activeProvider=0`: both ME Chests had cells but their native Grids lacked AE power.
- Adding adjacent creative AE power only in the Task 21 fixture changed the same GameTest from a missing projection to a
  passing consumer-aggregate extraction. Temporary diagnostics and the debug journal were removed afterward.
- Groovy Gradle LSP is not configured; executable contract, producer, consumer, adversarial, and strict build tasks cover
  the changed verifier surface.

## 2026-09-19 Task 21 independent-review repair

- The first two-Hub run exposed that Hubs publish before cable reciprocity settles. Adding a cable-driven topology hook
  fixed the missed relationship; the exact five-case repair run then passed.

## 2026-09-19 Task 21 repair round 2

- The first expanded self-test run reported `falseCleanupReceipt` as accepted because the verifier rejected zero through a
  generic numeric parser message instead of the required Task 21 cleanup semantic message. Direct exact-value
  reconciliation fixed the reason binding; the fully rebound false receipt then failed as intended.
- No unresolved implementation blocker remains. Task 21 stays unchecked pending final independent re-verification.

## 2026-09-19 Task 22

- The first native multi-entry run crashed in AE2 before assertions because a live provider callback mounted the exact same
  inventory twice. The fixture was corrected to join with an empty callback and expose duplicate entries only during the
  Task 8-qualified provenance replay; production behavior did not bypass AE2's mount invariant.
- The first Task 22 self-test run showed all mutations accepted because persisted consumption was not yet wired to
  `verifyTaskTwentyTwoEvidence`. After adding that call path, three probes initially rejected for generic property/log
  mismatch rather than their intended semantic reason; coordinated property, Policy trace, and provenance trace rebinding
  corrected the probes.

## 2026-09-20 Task 22 independent-review repair

- The first repair fixture settled a bridge concurrently with Grid replacement and timed out before the repaired paths.
  Serializing provenance teardown/rebound before bridge publication removed that race.
- Restoring the synthetic callback node beside separately restored native nodes produced competing Task 4 claims, while a
  standalone provider lacked power. Making the persisted callback provider itself an infinite native AE power service
  yielded one active restored Grid with the exact saved lineage and no caller-authored identity.
- A delayed Bridge placement did not receive another neighbor event after its attachments settled. The test fixture now
  invokes the real `onNeighborChanged` lifecycle before readiness inspection; the production Bridge path is unchanged.

## 2026-09-20T01:01:27+10:00 Task 22 identity-settlement gating repair

- Fresh verification intermittently crashed in `provenance.multi-entry` after `ProvenanceStorageFixture.ready()` returned
  true but before the merged Grid exposed a confirmed Task 4 `NetworkId`. `NativeSourceDomainRegistry` correctly rejected
  that state as `UNSETTLED_ORIGIN`; the fixture precondition was incomplete.
- The unchanged four-case suite and isolated native case passed on a quiet workspace, refuting active runtime residue or
  concurrent server contention as the current cause. The isolated outer verifier exited nonzero only because Task 22
  intentionally requires its exact four-case canonical set; its native log recorded `All 1 required tests passed :)`.
- Resolved by extending fixture readiness with the exact production settlement predicate. No production provenance,
  callback-slot, managed exclusion, opaque rejection, rebound, or stale-mount semantics changed.

## 2026-09-20 Task 23 chain sharing

- The first chain fixture used `List.of` while Grid handles were temporarily null and crashed instead of polling; explicit
  nullable iteration fixed startup. Incorrect north-facing Bridge geometry and same-tick bulk placement then caused stale
  attachment status. Correct WEST/EAST faces plus serialized placement resolved all five native cases.

## 2026-09-20 Task 24 fourth-gate repair

- One Task 22 regression run failed `provenance.opaque-boundary` while waiting for fixture readiness. The exact isolated case
  passed immediately, followed by a green full four-case retry and green Task 23 matrix; no Task 24 code change was made for
  this pre-existing native timing flake.
- Groovy Gradle LSP is unavailable. The exact producer, persisted consumer, adversarial self-test, focused contracts, strict
  build/archive tasks, and native regressions execute the changed verifier surface.
- Aggregate execution exposed two further fixture defects: readiness diagnostics dereferenced unsettled identities, and
  bulk cable creation intermittently produced `AMBIGUOUS_MERGE`. Null-safe status reporting plus one-settled-tick cable
  staging removed both races without weakening production settlement checks.
- The first successful isolated cycle emitted native evidence but the outer verifier reported `executed=[]`; the chain
  cases were missing from the generic evidence-accounting switch. Registering all five canonical IDs restored accounting.

## 2026-09-20T11:04:56+10:00 Task 25 resource qualification

- Modrinth `sD979rMC` publishes no dependency records although its JAR metadata requires AE2, GuideME, and Glodium.
- The inspected commit is a 2.1.5 version bump, while the candidate artifact is 2.1.4. No reproducible-build metadata or
  exact commit attestation was found, so adding the artifact coordinate or verification metadata would overstate support.

## 2026-09-20T08:36:00+10:00 Task 24 third-gate repair

- The previous known-key masking proof began both physical sources with iron, so it did not exercise discovery for an
  initially empty contributor. The repaired native case begins `0/8`, transitions to `8/0` without aggregate cache
  realization, and observes both source-local updates while the aggregate remains eight.
- The append-only cursor had no memory ceiling. Service and listener retention now share a hard 64-key limit; crossing it
  retires all listeners for that service, removes its catalog, increments a diagnostic receipt, and permits clean recovery.
- `DirectSubscriptionFixture.close()` previously let `assertConsumedAndClose()` bypass bridge and hook cleanup. Cleanup now
  runs in `finally`, and the native boundary case proves snapshot, registration, and trace hooks remain inert after failure.

## 2026-09-20T12:15:00+10:00 Task 25 Applied Flux correction

- The earlier BLOCKED classification inspected the wrong later source revision. Independent review identified the matching
  2.1.4 bump commit, so the blocker was removed only after a real isolated addon runtime passed.
- Modrinth declares no dependencies for `sD979rMC`; its embedded NeoForge metadata is authoritative for required GuideME and
  Glodium runtime presence. Both are now pinned explicitly under strict dependency verification.
- The generic native accounting verifier originally required inserted and extracted totals to match. Stored FE intentionally
  proves a non-empty final quantity, so only that case opts into truthful unequal-work accounting.
