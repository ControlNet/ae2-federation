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
