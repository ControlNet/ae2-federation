# Learnings — ae2-federation-v04

Conventions, patterns, and successful approaches discovered during work on this plan.

_Auto-scaffolded by /start-work. Append new entries below - never overwrite._

## 2026-09-25 Task 40 documentation status sync

- The README and acceptance matrix can report the four-case docs-only PASS while keeping the overall Task 40 and F1-F4 gates pending. A matrix edit invalidates its earlier byte-bound receipt, so rerun the exact four-case producer and persisted consumer/self-test after the final prose change. This update's current-bound result is `.omo/evidence/task-40/attempt-20260925T065852702Z/result.json`; full exits and limits are appended to `.omo/evidence/task-40-qa/verification.md`.
- Linking the append-only QA log from bound documents avoids pinning a stale pre-edit receipt inside the prose. The final current-document result after that link change is `.omo/evidence/task-40/attempt-20260925T070045771Z/result.json`; producer, persisted consumer/self-test and strict build each exited 0. The earlier `attempt-20260925T065852702Z` is now historical.

## 2026-09-25 Task 40 docs can qualify before scale and soak

- A truthful Task 40 docs suite must not depend on Tasks 37/38 checkboxes. The expanded `T-*` matrix can be checked against the exact DESIGN ID set and manifest IDs, links, command entrypoints, and documented blocked scale/soak boundaries. Keep the separate F1-F4 final-wave reports blocked. The current code-side suite is waiting on writer-owned stale prose before a positive source-bound run; exact attempts and source hashes are in `.omo/evidence/task-40-qa/verification.md`.
- Groovy regex `Matcher.collect { it[0] }` without a capture group returns the first character of a matched String, not the entire match. Capture the identifier explicitly before comparing set coverage.

## 2026-09-25 Task 40 manual launcher

- ModDevGradle 2.0.146 registered `runManualClient` from `manualClient { client(); gameDirectory = project.file('run-manual-client'); sourceSet = sourceSets.main }`. Explicit `runs.manualClient.loadedMods = [mods."${mod_id}"]` excludes the registered `ae2federation_test` mod. The isolated ignored directory is persistent, not cleaned with `build/` or automated run finalizers.
- The strict dry-run was red before registration and green afterward. Offline strict test/check/build passed; an Xvfb smoke reached Minecraft's first-run title accessibility prompt, and its mod list contained Federation, AE2, LDLib2 and GuideME without testmod. No user world or manual walkthrough was performed. Details: `.omo/evidence/task-40-manual-client/verification.md`.

## 2026-09-25 Task 40 code-side blocked suite dispatch

- Register the docs and F1/F2/F4 suite IDs without treating manifest presence as execution approval. The schema-v3 result retains exact requested/executed IDs, source/product hashes and hash-bound matrix/negative receipts while `BLOCKED` and parent exit 1 ensure persisted consumption rejects it. A separate existing self-test control still passes. Receipts and commands are under `.omo/evidence/task-40-qa/verification.md`.
- The current acceptance matrix explicitly distinguishes direct/subnet small timing from failed Federation warmup and Task 38 absence. Rejecting stale source/missing artifact can reuse the canonical persisted verifier; an unsupported-scale row is checked against incomplete Task 37 and exercised with a deliberately marked test-only fixture.

## 2026-09-25 Task 40 documentation handoff

- The latest Task 37 timed record must be read from its final same-source continuation, not the first paragraph: direct/subnet 3/3, Federation failed 27/256 warmup, resources non-comparable. Task 38 and F1-F4 remain open.
- Gradle's actual task list has no `runClient`; `runUiTestClient` and `runArtifactClient` are scripted. A human manual-client walkthrough must identify this launch blocker, use registered case IDs only as comparisons, and make no claim that a human session or final F3 took place. Keep evidence paths distinct from source-current acceptance.

## 2026-09-24 current-source remote sixteen-target three-layout replay

- A fresh source-matched 739-file snapshot under remote `~/ae2f/snapshots/task37-current-20260924T102858Z-d09fc1c4` ran direct, native-subnet and Federation 256-job selectors serially. All three explicit Gradle child exits and process-local audit exits were zero; each passed one required GameTest with 256 distinct native UUIDs, sixteen exclusive working machine/return-owner groups, 256 Drive receipts and 4096 actual units. Java descendants were observed on CPUs 2-9, each finalizer removed the world, and remote source pre/post digest was unchanged. Full receipts and hashes: `.omo/evidence/task-37-remote-current-three-layout/verification.md`.
- Concurrent unrelated local verifier/docs changes after the snapshot mean final live-local-to-snapshot parity is false even though snapshot source parity is stable. The runs are source-bound to the preflight digest and explicitly NON_COMPARABLE; no measured profile, speedup or Task 37/38/40/F1-F4 completion follows.

## 2026-09-24 Task 37 sixteen-target physical Federation 256 replay

- A new manual-only selector reuses the sixteen physical Bridge/Fabric/active Policy/Claim/Endpoint paths while its sixteen Providers each own sixteen fixture-mapped encoded Patterns, covering the same host-major catalog as direct/subnet-256. The missing global slot-255 red failed only after 255 native craftables were published. After repair, the detached long-lived GameTest process reported one required pass and 256 real native links, sixteen target Grid/return-owner groups, 4096 exact typed machine/callback units and actual five-cell Drive readback. Old source, Federation-16, direct-256 and native-subnet-256 controls plus strict build passed serially. Exact commands, caveat about the detached launcher exit, transcript/source hashes and resource differences: `.omo/evidence/task-37-large-federation-256-development/verification.md`.
- Configure each Federation target machine once with sixteen typed recipes after the first real routed input establishes its claimed Endpoint return context, then test `isItemValid` before Export Bus activation. Subsequent slots reuse the same owner/capability, and every job checks that its Endpoint resolves the selected native Lane. The new replay opts out of repeated expensive source-fixture inspection after the full first physical inspection, checks all target identities at publication and each host boundary, and checks selected route authority per job. This is correctness fixture cadence, not benchmark timing evidence.

## 2026-09-24 Task 37 sixteen native-subnet 256 replay

- The separate manual `scalelargenativesubnet256replaydevelopment` selector passed after a settled 255-craftable missing-last-Pattern red. Sixteen physical Providers with sixteen fixture-mapped encoded slots each advertise the same host-major 256 disjoint catalog as direct-256; sixteen independently confirmed native target Grids each receive through their Interface/cell/Export Bus/machine route. Its 256 serial sixteen-unit planner/CPU jobs emitted 256 distinct native link IDs, 4096 typed machine/callback units and 4096 actual units in five mounted source Drive cells. Independent old direct-256 and subnet-16 selectors, strict build, diagnostics and diff check passed. Full process-bound receipt: `.omo/evidence/task-37-large-subnet-256-development/verification.md`.
- A preliminary slow run's server-thread dump located repeated `NetworkIdentityRegistry.settle` inside the shared source fixture's every-tick `inspect`. The new selector uses the fixture's one-time full source verification, while old selectors retain every-tick inspection; the new selector checks its sixteen target identities at publication and each host boundary. This is a development fixture cost observation, not a timed benchmark or throughput claim.

## 2026-09-24 Task 37 sixteen native-subnet destinations

- The settled 16-Provider large source accepts sixteen separate physical native-subnet pods, each with an Interface, cell Chest, Export Bus and capability machine on its own confirmed target Grid. A missing-sixteenth red failed only after all source channels settled. The final selected process completed sixteen serial native 16-unit planner/CPU/requester jobs with disjoint keys, distinct Grid IDs and link UUIDs, exact per-machine transitions and per-key callback, and 256 units physically retained in the one-cell source Drive. Independent source-only, direct-16 and earlier four-subnet selectors passed. Commands, transcript hashes and row boundaries: `.omo/evidence/task-37-large-subnet-sixteen-target-development/verification.md`.
- The native Export Bus may take many ticks to drain a complete 16-unit target cell after filter activation; an early 120-tick completion wait was too short. The final bounded 2000-tick per-job wait and 40000-tick test timeout are development guards, not measurements. A passing target topology plus Drive conservation does not equal a matched benchmark: sixteen extra target Grids, cells and creative energy sources have no direct-16 resource parity, and loaded-chunk/energy/peak-usage metrics were not measured. Task 37 stays `[~]`.

## 2026-09-24 Task 37 physical Federation C route

- Keep the proven H0 two-Bridge route unchanged and stage C separately after H0's source-edge handoff. A second source-only red cable path from the source chest to H1's WEST face and a red extension south of B can coexist on the exact source Grid without merging the target Grids. Settle C anchor, seeded Endpoint and seeded bus separately; only then add red/blue Bridge legs and H1-owned Claim/Policy/Runtime. Read-only per-job readiness preserves Fabric publication. With C Policy removed, H1's actual native push yields `POLICY_DENIED` and zero C input; restore it before three serial planner/CPU jobs. Final process, 256 replay control and exact source hashes: `.omo/evidence/task-37-federation-three-target-development/verification.md`.

## 2026-09-24 Task 37 remote H1 power-node side effect

- A remote Provider fixture can share a source energy position without placing a new creative cell. Its node seeding condition must be local-only, or `loadFromNBT` silently changes an already-live H0 power node. Snapshot the managed node NBT around H1 placement on the repeating GameTest path and assert the same block, same node and same saved state; red was `true/true/false`, green after excluding `remoteHost`. H1 still joins the source through its real WEST cable edge. Serial selected/control and build receipts: `.omo/evidence/task-37-native-three-target-development/verification.md`.

## 2026-09-24 Task 37 batch-16 one-Grid probe

- A 16-output AE2 Processing request can be proven through the native plan's `finalOutput`, three serial tracker links, per-key callback and physical cell quantities, and 16 one-item capability machine transitions per typed recipe. The non-stackable high-slot item reached the old machine singly in an exploratory run, but the new selector explicitly opts into one-item-per-tick processing so all three recipes share the same bounded transition shape. Bind append-only sibling log rows to the exact selected process. Full receipts and commands: `.omo/evidence/task-37-batch16-development/verification.md`.

## 2026-09-23 UTC Task 37 remote environment smoke

- The immutable `364173f` snapshot passed a confined Java 21 strict `test check build` and exactly one manual-only `scalenativebiggridtwopatternsdevelopment` run. The direct selector wrote two typed sibling Processing receipts, not the configured `benchmark-native.properties` filename: cobblestone/diamond then dirt/gold ingot with distinct native UUIDs, one required GameTest, and six ordered push/return observations. The live Minecraft listener was `[::ffff:127.0.0.1]:45611`; the finalizer removed the task-owned world/config/lock and no owned GameTest Java remained. Source identity was unchanged. Exact commands and copied allowlisted logs are in `.omo/evidence/task-37-remote-smoke/verification.md`.
- The approved remote EULA file was copied byte-for-byte into only `run-gametest` after regular-file/mode/content checks. The source verifier excludes generated build and runtime trees, so it can be repeated after Gradle without promoting those outputs into immutable source identity. The 60-vCPU VM appeared idle at preflight but was not pinned/reserved or proven quiesced; the run is not a timing baseline or Task 37 qualification.

## 2026-09-24 Task 37 independent two-Pattern rerun

- A fresh serial selector gives two typed native-link rows and six ordered native push/return observations, while the unchanged three-layout selector gives three more rows. With one evidence directory, the direct selectors append to the same sibling `scale-small-processing.log`; bind each row to its selected process transcript rather than mistaking the five-row file for one run. Source guards prove first diamond retention, opposite-key zero, idle return/CPU and distinct UUIDs; the receipt alone does not serialize those guards. Independent commands, UUIDs and cleanup are recorded in `.omo/evidence/task-37-two-pattern-independent/verification.md`.

## 2026-09-24 Task 37 two-Pattern native probe

- A `succeedWhen` assertion in a one-time setup branch can be skipped on the next tick after mutating stage; catalog assertions belong on the repeating path. Two encoded physical slots must be decoded and correlated with the native Lane's advertised Patterns, not inferred from a fixture-authored count.
- The same native requester/CPU can submit two serial typed jobs after the first link retires. Correlate per-key physical cell quantities, exact machine transition keys and callback amounts at each completion; retain the first physical output through the second job and require distinct native UUIDs. The selected probe and original three-layout regression both passed; exact commands and receipts are in `.omo/knowledges/task-37-scale-blocker.md`.

## 2026-09-23 Task 39 nested wrapper lifecycle

- Join and JAR-load observations are not proof that the nested Gradle `JavaExec` wrapper completed cleanly. The runtime report must be written only after both nested wrapper exits and descendant cleanup are checked; the persisted consumer must require those exit receipts and reject logged build failure.
- A synthetic `Popen` harness can reproduce valid hash/load/joins followed by a post-join failed `wait()` without opening a real socket or launching Minecraft. It failed before the fix and passes after it; the real source-bound five-case attempt remains pending.

## 2026-09-23 Task 39 actual orderly proof

- A Gradle `RunGameTask` extends `JavaExec`; signaling its descendant tree before game shutdown interrupts the daemon and fails the nested build. The proof-only client now stops after authentic join and the server halts after that player's logout; the Python owner waits boundedly for both nested wrappers before emergency cleanup. The actual logs show `Stopping!`, `Stopping server`, and two `BUILD SUCCESSFUL` lines with genuine zero wrapper exits.
- Fresh current-source attempt `attempt-20260923T132715867Z` passes exact five-case producer and same-attempt persisted consumer. The client reload has no missing multipart Bridge item-model or texture warning. Keep prior daemon-disappearance attempts rejected; do not infer rendered visual quality from a log-only smoke.

## 2026-09-23 Task 37 third Processing job

- The Endpoint's east face is reserved for Federation and does not expose item return; place a registered machine against its south logistics face and the target Export Bus east of that machine facing west. The bus joins the target Grid through the existing seeded native connection, while its item capability feeds the machine from the target physical cell.
- The federated return context is bound by actual authorized native Provider resolution on CPU-driven input push, not by Claim activation alone. Configure the machine's recipe only after the physical target cell contains the pushed cobblestone and the south-sided Endpoint item capability is the context's exact return handler; assert that its owner is the original Provider logic and return inventory.
- The selected small development test now has three independently completed one-job receipts and 16 distinct active Grids through the Federation result. The route receipt retains `resolution=ACTIVE` and `pushAccepted=false` for the planner-driven job. This does not qualify the JSON tier's replay, timings or late/ultra profiles. Exact commands and evidence are in `.omo/knowledges/task-37-scale-blocker.md`.

## 2026-09-23 Task 37 fixture safety

- The 16x16 x/z GameTest footprint with five y layers and two-block x/z separation offers only 320 fixture slots. The 512-Grid exploratory tier cannot use the existing layout without unsupported extra chunk loading. A GameTest failure before block placement is the truthful result until a bounded topology and real workload exist.
- `succeedWhen` repeats its body until success: only read-only Grid readiness/count checks belong in the polling phase. A one-shot test-authored iron insert/extract does not establish native planner/CPU/Provider/return Processing or scale qualification.

## 2026-09-23 Task 37 small Processing diagnosis

- Per-node before/after Grid receipts isolated the previous `Waiting for small native Grids`: Task20's isolated energy at `(1,2,3)` and Provider host at `(3,2,3)` replaced live scale ME chests. Reserving the six nearby lattice positions preserves 16 auxiliary Grids through seeded CPU placement. The reserved 16x16 lattice has 314 positions; ultra remains unqualified.
- Source identity must be confirmed before Task20's `ProcessingCraftingGrid` seeds the physical storage/CPU. The temporary CPU/storage Grid may briefly report unconfirmed before its native join; after connection the Provider, chest and CPU share the source's confirmed Grid identity. Stage diagnostics and exact per-node receipts live at `.omo/evidence/task-37-small-development/scale-small-nodes.log`.
- One real development job completed through AE2 planner/CPU/Provider, Task30's registered capability-backed machine, native return and physical ME cell. Handler transition, callback and destination independently agreed on one diamond from one cobblestone. The exact GameTest remains intentionally failing because 16 auxiliary Grids plus the source are not a comparable small layout and neither native-subnet nor Federation Processing run exists. JUnit and strict check/build pass; GameTest world cleanup and zero owned Java processes were observed. Exact commands and failure text are in `.omo/knowledges/task-37-scale-blocker.md`.

## 2026-09-23 Task 37 physical route

- Replacing a fixture-created non-in-world Provider/storage edge with production five-face Provider wiring can invalidate an already published Bridge Fabric even when its native Grid identities remain stable. Refreshing the physical Bridge after that handoff restores the common Fabric and Policy activation before target resolution; the rejected pre-refresh push was `FABRIC_DISCONNECTED`.
- An east-facing Bridge at `(4,2,1)` with the blue target cable at `(5,2,1)` stays within the bounded structure and preserves 16 distinct active Grids. Exact Claim ownership plus active Policy led to `ProviderRuntime` `ACTIVE`, one accepted native push, and one cobblestone in the physical target cell. This is input-route readiness, not a Federation Processing job or a measured scale profile. The selected run and evidence are recorded in `.omo/knowledges/task-37-scale-blocker.md`.

## 2026-09-23 Task 39 safety boundary

- Explicitly passing a user-authored EULA file is a preflight input; a preexisting unrelated runtime `eula.txt` is not consent. Validate before creating runtime paths, binding a socket, or starting a child; copy approved bytes rather than generating acceptance.
- A native `All 1 required tests passed` banner is only one selected registration. Explicitly enumerate the manifest's direct GameTest IDs and fail when any selected run has zero or more than one native required success.

## 2026-09-22 Task 35 release and packet hardening

- An LDLib2 server callback still needs project-owned authority checks: server thread, current menu, exact holder, live
  session, exact event schema, and current revision must all precede mutation.
- Production separation needs executable coverage on both surfaces: semantic binary/sources archive inspection and a real
  `sourceSets.main` dedicated-server startup with only the production mod loaded.
- Evidence child probes that initialize Log4j need an isolated, removed working directory; otherwise post-capture rollover
  logs alter dirty-source identity and make an otherwise valid current-source result stale.
- Bounded quantity validation belongs in the immutable state constructor as well as packet parsing so every creation path
  enforces the same exact-integer ceiling.
- Owned packet rejection is also editor-state behavior. When a concurrently advanced Policy revision invalidates an
  otherwise current request, the handler must refresh the expected revision and synchronize terminal `STALE_REVISION`
  state rather than returning a rejection enum alone.

## 2026-09-21 Task 33 processing-row glyph repair

- Minecraft's font renderer exposes literal tab characters as square control glyphs. Visible list rows should use explicit
  ASCII separators such as ` | `, while tabs may remain in internal snapshot serialization that is never rendered.
- Isolating visible row formatting in a pure Java formatter makes exact CJK text and control-character regressions testable
  without loading Minecraft-owned projection classes in plain JUnit.

## 2026-09-21 Task 33 LDLib2 Fabric workspace

- LDLib2 2.2.34 registers `VirtualScrollerView` as `virtual-scroller-view` even though its pinned XSD omits that tag; the
  runtime XML loader accepts it and Java must install the item provider.
- Graph geometry can be keyed only by Task 32 topology revision while the full encoded snapshot remains the S2C binding
  value. Provider status, Endpoint mode, and Pattern rows then refresh without relayout.
- A usable actual-client mapping proof needs a live `ProviderRuntime`: constructing only `MappedPatternProvider` does not
  register it in `ProviderObservationRegistry` and therefore cannot appear in the scoped workspace.
- LDLib2 `Button` installs direct defaults on its internal `.__button_text__` element. Localized wrapping and font sizing
  must target that child directly; styling only the parent button leaves adaptive-width and default font metrics active.
- Text-element bounds alone do not prove glyph fit. The actual-client suite now compares rendered font width to content
  width for the paired Chinese controls and exercises the shared workspace at GUI scales 2, 3, and 4.
- Multipart diagnostics can remain server-authoritative without a parallel fixture label: resolve the live
  `MultipartBridgePart` from the entrance position/side and render its actual cable connection length in the bound caption.
- AE2 processing inputs expose normalized candidate stacks plus `IPatternDetails.IInput.getMultiplier()`; rendered native
  quantities must multiply those checked-long values rather than displaying the candidate stack amount alone.
- LDLib2 `ElementRef.text()` on a `VirtualScrollerView` reads the container, not mounted rows. Actual-client assertions for
  virtualized content must inspect the rendered `.virtual-row` labels.
- A Fabric topology revision does not cover independently registered Provider/Endpoint projection changes. Layout reuse
  therefore requires both the topology revision and a deterministic node/edge structural signature; status-only changes
  still reuse the exact layout object.

## 2026-09-21 Task 32 scoped observability

- Exact native transport accounting belongs after positive `MODULATE` acceptance. Storage records after its authorized
  delegate accepts; energy records only at the outermost accepted demand so recursion and simulations remain invisible.
- A live projection does not need a second scheduler or shadow task model. Provider identity plus native Lane lock,
  busy, send-list, and return-inventory state is sufficient to describe current operation without inventing completion.
- Client deltas must match exact Fabric generation and topology revision and have a contiguous base data revision;
  rejection transitions the client to explicit resnapshot-required state.

## 2026-09-21 Task 31 directional ME energy

- AE2 cold-start recovery works through a consumer-local read-only `IAEPowerStorage` plus a native
  `GridPowerStorageStateChanged(PROVIDE_POWER)` event; no artificial startup reserve is needed.
- Native source rediscovery reconstructs descriptor records. Generation tracking must compare those immutable descriptors
  by value while retaining exact node/storage identities inside each descriptor.
- Provider accounting must sum all native public stores on the provider Grid; an ME Chest can charge an adjacent energy
  cell before the assertion tick, so one-cell deltas are not authoritative for aggregate service extraction.
- AE2's production `EnergyCellBlock` constructor supplies the finite maximum enforced by `EnergyCellBlockEntity` and
  `StoredEnergyAmount`; a testmod registration can therefore exercise native values above 1e9 without a fake source or
  the non-debiting creative cell.

## 2026-09-21 Task 30 mixed factory load

- A second AE2 crafting storage joins the provider crafting service only when it forms through a real adjacent network
  block. A remote managed-node connection can share the Grid while `getCpus()` remains unchanged. The stable second CPU
  position is another free face of the provider ME Chest.
- `MultiCraftingTracker.handleCrafting` owns an asynchronous calculation. `NativeTerminalRequest.submitTracked` may return
  empty with no link on its first call; retry it on later ticks only until that request is accepted, then never resubmit it.
- Task 28's default consumer Interface and Import Bus source chest both use `BASE.west()`. Mixed fixtures that need both
  simultaneously must place the stocking Interface on a dedicated consumer-Grid-adjacent block.
- The accepted mixed scene used two native CPUs, six Processing patterns, two alternative stone inputs, one delayed lane,
  a four-stage emerald chain, one glass order, three Interface stocking cycles, and Import/Export Bus work. Thirty-one
  source diamonds reconciled to two consumed nine-item stocks, nine final stocked items, and four exported items.

## 2026-09-21 Task 30 five-blocker repair

- A mixed benchmark cannot summarize multiple profile iterations in one scene. Clearing the bounded fixture volume after
  each close permits one warmup plus three genuinely reconstructed native topologies without carrying policies or nodes.
- Planner, CPU submission, Provider push/return, projection, tracker, and bus Mixins already expose sufficient native
  authority. Task 30 now cross-correlates those receipts with pre-authorized handler/Interface owners and physical deltas.
- The universal per-key equation is `initial + projectionInserted + handlerProduced = projectionExtracted +
  handlerConsumed + finalSource`; callback, stocking, and export receipts independently corroborate final destinations.
- A one-slot overload retry must wait for `activeLink() == null`, not merely `observedDone()`, before the one controlled
  retry. This produces exactly two native submissions and two UUIDs without a Federation queue.

## 2026-09-20 Task 28 native automation

- Native AE2 automation can consume the Task 21 directional Storage projection without a Federation scheduler: Interface
  stocking, Crafting Card demand, Import/Export Buses, duplicate-demand rejection, cancellation, and contention all remain
  owned by AE2 native logic and trackers.
- Restored AE2 parts use managed-node tag `gn`; explicit cable reconnection is required after loading seeded part identity.
- A pre-settled fluid source must be created in the provider topology before identity settlement. The stable location is
  `secondPosition.north(2)`, adjacent to the existing provider cable; adding a chest below an established cable caused a
  late identity merge and prevented fixture readiness.
- Real fluid automation moved 1000 mB water into a native Interface and left 500 mB in the physical provider cell. Final
  source-bound evidence is `.omo/evidence/task-28/attempt-20260920T102042301Z/result.json`.

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

## 2026-09-19 Task 21 native Storage mounts

- An ME Chest with a cell exposes an `IStorageProvider`, but the provider node remains inactive until its native Grid is
  powered. Task 21 fixtures need both the cell and native AE power before relationship reconciliation.
- `IStorageService.addGlobalStorageProvider` and `removeGlobalStorageProvider` are sufficient for a logical directional
  mount. Native provider callbacks remain the source of delegates and priorities; the complete Grid aggregate is never an
  export source.
- Keeping authorization dynamic inside the mounted `MEStorage` makes a previously obtained projection fail closed after
  Policy revocation, while immediate reconciliation also removes it from the consumer Grid's global providers.
- Canonical evidence is `.omo/evidence/task-21/attempt-20260919T090525880Z/result.json`.

## 2026-09-19 Task 21 independent-review repair

- Hub publication can precede reciprocal Federation Cable convergence. Storage must retain loaded Grid identities and
  re-derive relationships when cable topology changes, not only when a Hub publishes its local faces.
- A held projection is safe only while a fresh Task 8 discovery returns the same callback identities and priorities.

## 2026-09-19 Task 21 repair round 2

- A logical mount count cannot prove native capacity deduplication. With both Bridge routes live, the provider held 13
  iron, the consumer aggregate exposed 13, and simulated consumer capacity `16243` equaled provider `8115` plus local
  consumer `8128` exactly once.
- A post-removal `containsKey` boolean is not a teardown receipt. Capturing the registered object before removal, verifying
  the removed identity, counting global providers removed, and reading the already-held consumer aggregate after teardown
  distinguishes real native cleanup from a label.
- Adversarial probes must fail for the intended semantic error. A zero cleanup count initially reached a generic positive-
  integer parser error; exact cleanup-field reconciliation now produces the Task 21 cleanup semantic rejection.

## 2026-09-19 Task 22

- Persisted Task 4 node lineage supplies the stable callback-registration identity needed to distinguish a native Grid
  rebound from a new source. The runtime `IGrid` and `MEStorage` may both change while `NetworkId`, node UUID, and callback
  slot prove continuity; source generation still advances.
- AE2 rejects mounting one identical inventory twice in a live provider callback. The native multi-entry fixture therefore
  registers an initially empty real provider node, then exposes duplicate callback entries only to the qualified provenance
  replay, proving maximum priority and deduplication without corrupting AE2's own live mount table.
- Separate source and relationship mount generations close the stale-handle reentrancy gap: an old projection first proves
  it is still the exact current mount before it can remove anything.

## 2026-09-20 Task 22 independent-review repair

- Filtering a callback before assigning coordinates changes identity. The capture boundary must pair every callback entry
  with its raw sequence index first, then exclude managed views while retaining that original index.
- A persisted synthetic provider node can prove a same-origin rebound without fixture-authored IDs: save its managed-node
  state, wait for the removed Grid to have no nodes, release the removed runtime claim, and restore the same provider with
  its own `IAEPowerStorage` service so no unrelated fresh Grid lineage contaminates settlement.
- Stale-generation safety requires invoking the old object after the new mount exists. Comparing projection identity,
  source generation, mount generation, provider-removal count, and operations before and after all three stale calls makes
  the early-return guard observable rather than source-inspected.

## 2026-09-20T01:01:27+10:00 Task 22 identity-settlement gating repair

- AE2 `isActive()` and `hasGridBooted()` do not imply Task 4 identity settlement. A GameTest fixture that immediately calls
  provenance discovery must also observe `FabricRegistryAccess.confirmedNetworkId(grid()).isPresent()` after provider-node
  merge/reconfiguration.
- The failure signature is timing-sensitive: the rejected run moved a restored node from one runtime Grid into another
  immediately before `UNSETTLED_ORIGIN`, while quiet full-scope and isolated runs attached all nodes to one Grid and passed.
- A source contract that binds fixture readiness to discovery's exact production precondition provides deterministic red/
  green coverage without adding sleeps, retries, timeout inflation, or exception suppression.

## 2026-09-20 Task 23 chain sharing

- A bounded origin frontier is sufficient for chain sharing: serial authority intersects, alternative routes union, and a
  `(consumer, origin, capability)` key deduplicates diamonds without retaining complete paths.
- Native multi-Bridge fixtures must stage each part and refresh its public shape lifecycle before testing membership; bulk
  same-tick insertion can leave the initial `MISSING_MAIN_ATTACHMENT` status stale.
- Alternative-route authority cannot union operations and filters independently. Retaining a filter per operation prevents
  `VIEW iron` on one route plus `EXTRACT gold` on another from synthesizing `EXTRACT iron`.
- AE2 fixture nodes created in one tick may receive independent identities before their native connections converge. Place
  each cable from the storage-adjacent anchor outward, and wait for all current identities to settle between additions.

## 2026-09-20 Task 24 storage subscriptions

- AE2 `IStorageWatcherNode.onStackChange` receives the new absolute amount. Subscribe at
  `StorageService.postWatcherUpdate`, then re-read the qualified native source for that key rather than treating the value
  as a delta or forwarding a Federation projection as a new origin.
- Register before taking the initial snapshot and queue events at that boundary with a hard limit. Replaying the queue after
  the baseline preserves concurrent same-key changes without an unbounded journal.
- A true-source key of `ExportSourceId + SourceGeneration` naturally deduplicates diamond paths. Fan-out should invalidate
  each identity-distinct consumer once while leaving the effective dependency topology untouched.
- A phased GameTest must assert that staging reached its terminal phase before accessing the downstream Grid. Falling
  through a `succeedWhen` tick during early attachment causes a fixture NPE that is unrelated to subscription behavior.

## 2026-09-20 Task 24 independent-review repair

- AE2's watcher boundary is aggregate, so a bounded per-source reconciliation path is required in addition to watcher
  callbacks. Round-robin one listener per native service per server tick bounds work while eventually exposing masked
  source-local changes; unchanged snapshots must not invalidate consumers.
- Queue overflow is a binding lifecycle event, not only a ledger state. Compare-remove the exact binding, close its exact
  registration, and let the same desired plan create a fresh binding on reconciliation.
- A deterministic testmod-only injection at `SourceSnapshotLedger.completeSnapshot` can place a real native callback after
  `beginSnapshot` and before completion. Capturing the actual old listener at registration similarly proves stale callbacks
  cannot mutate or remove a rebound binding.

## 2026-09-20T07:22:00+10:00 Task 24 second independent-review repair

- Selecting one listener is not a work bound when the listener materializes all Grid/source keys. The pinned `MEStorage`
  API supports a bounded keyed quantity probe through simulated extraction; a resumable cursor limits periodic work to one
  qualified source and eight known keys per native service per tick.
- A mutation inside `completeSnapshot` is not a race proof until the test explicitly invalidates and realizes AE2's cache.
  Only then does `postWatcherUpdate` synchronously enter the production Mixin/hub while `snapshotting` remains true.
- Two physical ME chests on the same native Grid provide a deterministic aggregate-mask fixture: `4+8` and `8+4` preserve
  aggregate 12 while two source-local events publish over one listener rotation and the managed consumer import stays out
  of origin subscriptions.
- One-shot static hooks need ownership as well as consumption. Removing every queued/active hook for a closed owner prevents
  an abandoned fixture from changing a later test's snapshot or registration boundary.

## 2026-09-20T08:36:00+10:00 Task 24 third-gate repair

- Per-source cursors cannot discover an initially absent key from an aggregate callback that equal-opposite changes suppress.
  A bounded catalog owned by the native `IStorageService` closes the gap: every qualified source snapshot contributes keys,
  late listeners replay them, and each listener probes the same retained authority independently of plan iteration order.
- Never evict a discovery key silently. A 64-key hard ceiling on both service catalogs and listener cursors turns excess
  distinct-key churn into an observable whole-service listener retirement; the unchanged plan recovers after pressure is
  removed. With eight probes per listener visit and at most seven arrivals between visits, a stable key is revisited.
- Aggregate callbacks must qualify a previously unknown key against true sources before catalog insertion. This keeps keys
  visible only through managed Federation projections from becoming discovery authority.
- Cleanup assertions belong outside cleanup control flow. Capturing the unconsumed-hook failure, clearing owner state and
  closing the bridge in `finally`, then rethrowing the original assertion prevents abandoned fixture hooks from leaking.

## 2026-09-20 Task 24 fourth-gate repair

- Fail-closed ownership must remain with the hub: notifying listeners is advisory, while snapshot-closing the registry is
  what guarantees exact retirement even for no-op listeners.
- A native registration-order proof needs observable catalog state at each real registration boundary. Empty-first
  broadcast and populated-first late replay together cover both paths without substituting a unit-only driver.
- Correlating a callback by whichever trace is globally active is unsafe under interleaving. Carry the exact listener on a
  delivery stack and look up trace state by exact ledger identity.
- Recovery is only proven after invoking a retained retired callback and then causing a real cache-driven native event on
  fresh registrations; direct hub publication would bypass the behavior under test.

## 2026-09-20T11:04:56+10:00 Task 25 resource qualification

- AE2 generic key serialization preserves `AEItemKey` data components and `AEFluidKey` identity; physical item/fluid cell
  delegates retain independent component quantities and millibucket-scale long amounts through native operations.
- Live Modrinth metadata corrected the inherited title mismatch: `sD979rMC` is consistently 2.1.4 by title, version, JAR
  metadata, and filename. The remaining blocker is exact source-artifact provenance, not artifact identity.
- The similarly named GitHub tag `1.21-2.1.4-neoforge` belongs to an older ExtendedAE tree and cannot qualify Applied Flux.

## 2026-09-20T11:24:11+10:00 Task 25 verification

- The exact five-case producer persisted `.omo/evidence/task-25/attempt-20260920T011257199Z/result.json`: four real native
  GameTests exited zero and only `resources.stored-fe` remained BLOCKED. The blocked consumer and twelve adversarial probes
  passed, as did strict build/archive isolation and exact Task 21/24 regressions.

## 2026-09-20T12:15:00+10:00 Task 25 Applied Flux correction

- Commit `a54eafb72d72bd259bc3b5fa226b4f5542c4c3c4` is the exact public 2.1.3 to 2.1.4 version bump. Official
  Modrinth project linkage, artifact metadata, implementation, timestamps, and resource history establish an authoritative
  ordinary correlation without implying reproducible-build identity.
- A conditional ModDev source-set association is required: globally associating the compatibility source set leaks its class
  metadata into default GameTests, while leaving it unassociated prevents compatibility registration. The explicit
  `enableAppfluxCompatibility` property keeps both paths honest.
- The registered `FE_CELL_256M` inventory retains native values above `Integer.MAX_VALUE`; a real run inserted
  4,294,967,311 FE and left 3,221,225,482 FE after extraction while AE power remained unchanged.

## 2026-09-20 Task 26 native Crafting binding

- Native Crafting publication can retain exact AE2 service/provider/pattern/CPU ownership while Federation owns only a
  directional Policy/Fabric authorization binding and its invalidation.
- Identity-aware GameTests must seed newly placed AE2 managed nodes from the already-settled provider `NetworkId` before
  their first tick. Staging alone can still observe stale settlement under the schema-v3 child launcher.
- Final exact evidence is `.omo/evidence/task-26-final/attempt-20260920T035302303Z/result.json`; four isolated native
  children, current-identity consumption, and fully rebound adversarial probes passed.

## 2026-09-20 Task 26 first-gate repair

- A log projection of the semantic evidence Map is not independent authority. The repaired observer scans the exact live
  AE2 Grid, node services, node lineage, crafting service, CPU set, and provider pattern objects without reading that Map.
- Native Pattern Provider replacement can rebuild runtime Grid/service objects while retaining the settled source
  `NetworkId`. Receipt A and receipt B must therefore preserve and correlate each phase independently rather than assuming
  service/CPU identity survives AE2 reconstruction.
- Provider removal temporarily unsettles dynamic lookup. Pin the already-authorized directional `PolicyKey`, seed provider B
  from its provider `NetworkId`, then drive the existing Bridge neighbor lifecycle to re-observe the settled current Grid.

## 2026-09-20 Task 27 native terminal flow

- AE2 planning needs the provider's native `IGridNode` on its `AE Crafting Calculator` thread; a requester captured on the
  server thread is sufficient and avoids planner-thread discovery or mutable world authority access.
- AE2 does not necessarily call the simulation requester's `getActionSource()` during planning. Evidence must require the
  captured node call and calculator thread while accepting a zero, independently accounted action-source call count.
- Exact result ownership needs both the native CPU logic callback and physical `BasicCellInventory` insertion. Aggregate
  storage growth alone cannot identify which Grid, CPU, job, or cell owns the result.
- Canonical final-tree evidence is retained under `.omo/evidence/task-27-final/attempt-*/result.json`; Task 9 and Task 26 regression suites
  also pass after the terminal boundary changes.

## 2026-09-20 Task 27 independent-gate repair

- A semantic/runtime result trace is not independent authority when every owner can be rebound consistently. Capturing the
  exact physical cell delegate before submission provides an immutable destination oracle for later job, CPU logic,
  callback, key/amount, and insertion correlation.
- A one-pattern source cannot prove allow-list filtering. The repaired source publishes real stick and crafting-table
  patterns from the same native provider; direct source/service observation proves the forbidden pattern exists while only
  the original allowed stick pattern is discovered.
- Final repaired evidence is
  `.omo/evidence/task-27-acceptance-repair-final/attempt-20260920T075857792Z/result.json`; fresh Task 9 and Task 26
  regressions are under their matching `task-*-task27-acceptance-repair-final` evidence roots.

## 2026-09-20 Task 28 independent-authority repair

- Cross-correlating two mutable final observations does not prove runtime authority. A separate pre-operation observer must
  own the exact object references and gate the runtime hooks before producing its receipt.
- Native topology settlement can replace an `AuthorizedStorageProjection` between item-bus and fluid-Interface phases.
  Persist an exact set of independently authorized projection identities and verify observed identities are a nonempty
  subset; equality incorrectly rejects authorized but unused projections.
- Repaired evidence is `.omo/evidence/task-28-authority-repair/attempt-20260920T115809462Z/result.json`.

## 2026-09-20 Task 29 native crafting lifecycle

- Federation retains only native requester/link authority keyed by policy, requester node lineage, and tracker slot;
  cancellation and completion remain live `ICraftingLink` state.
- Bridge removal withdraws capability visibility without canceling native work. Reloaded `MultiCraftingTracker` NBT keeps
  the same native UUID, rejects duplicate submission, and delivers the result into physical ME cell storage.
- Crafting policy cycles are rejected before backend discovery by testing provider-to-consumer reachability for each edge.
- Canonical evidence is `.omo/evidence/task-29/attempt-20260920T130507949Z/result.json`.

## 2026-09-20 Task 29 independent-review repair

- UUID continuity is necessary but insufficient for reload authority. Rebinding also requires the same policy, requester
  node lineage, tracker slot, and a live loaded `MultiCraftingTracker` containing that exact link.
- Native result delivery is chunked. Independent evidence must accumulate callback acceptance and exact physical cell
  insertion to the requested total; requiring one 128-item callback falsely rejects the real AE2 path.
- A cancellation can return partially crafted intermediates to source storage. Replay proof therefore distinguishes those
  native returns from delivery of the requested result and suspends execution before serialization when zero result
  insertion is the property under test.
- Final repaired evidence is
  `.omo/evidence/task-29-repair-final-9/attempt-20260920T145510366Z/result.json`; fresh Task 9/19/26/27/28 regressions are
  retained under the matching `task-*-task29-repair-regression` roots.

## 2026-09-20 Task 29 second re-review repair

- Registration must be owned by the real terminal request path, not a test fixture. `submitTracked` can retain AE2's real
  `MultiCraftingTracker` as the submitter while binding the resulting owner/link under the captured terminal snapshot.
- Terminal cleanup does not require a Federation callback state machine. Polling live `ICraftingLink` terminal state while
  reconciling or querying cardinality removes both request-key and UUID-owner entries before level close.
- Exact evidence schemas must compare both property names and runtime trace fact names against a child-specific set before
  comparing values; otherwise a fully rebound extra property and trace line remain self-authorizing.
- Final evidence is `.omo/evidence/task-29-final-repair/attempt-20260920T161953674Z/result.json`; the fresh Task 27 regression
  is `.omo/evidence/task-27-task29-final-repair/attempt-20260920T162342556Z/result.json`.

## 2026-09-21 Task 30 second re-review repair

- A truthful Processing test boundary is a registered world block entity reached by AE2 through NeoForge's item-handler
  capability. The block entity owns input/output inventories and deterministic recipe transitions; orchestration only
  configures recipes and releases the deliberately blocked lane.
- Profile axes must build topology, not validate one fixture. A typed topology now derives CPUs, lanes/requesters,
  alternatives, chain stages, and the seed-selected tracked resource set, with runtime variation checks for each axis.
- Independent evidence can share native observations but cannot share the final property map. A separately emitted exact
  runtime artifact binds machine owners, projection operations, bus owners/work, stocking owners, jobs, and per-key
  physical snapshots, so coordinated property substitutions fail while the runtime artifact remains unchanged.
- Negative evidence is strongest when every zero and bound is a subtraction or relationship between captured boundary
  snapshots. Overload tick order, registry/link cardinality, submissions, callbacks, and physical source deltas now form
  one independently recomputable recovery timeline.

## 2026-09-21 Task 30 final metric-authority repair

- A bounded projected aggregate is still forgeable when the independent stream proves only its components. The consumer
  must recompute the aggregate from operation-time receipts and require exact equality, not merely enforce a ceiling.
- CPU pressure needs a receipt at each native observation point, not only the final waiting snapshot. The maximum of those
  observations is the independent authority for `peakInFlight`; waiting facts then provide exact job/blocking correlation.
- Groovy interpolated map values can remain `GString` instances. Normalize resource IDs to `String` before comparing them
  with Base64-decoded Java strings, or valid raw transitions fail closed through `containsValue`.

## 2026-09-21 Task 32 independent-review repair

- One process-global client projection is unsafe even when every record is Fabric-scoped. The ownership key must include
  player, menu session, subscription generation, exact Fabric generation, and a server nonce.
- Full replacement deltas are a small deterministic way to express additions, updates, and removals atomically while
  preserving canonical bounded collections and no-mutation-on-rejection behavior.
- Operation IDs may be random only when created once at the physical native boundary and carried through every observer;
  minting inside observability turns repeated reports into distinct physical operations.

## 2026-09-21 Task 32 final runtime lessons

- GameTest setup must distinguish pre-attachment identity settlement from post-attachment runtime readiness. Rechecking
  standalone-grid identity gates after a deliberate native attachment misdiagnoses later phases and can crash helpers that
  assume an identity remains independently settled.
- Native test nodes joining an established Fabric should load that Fabric member's `NetworkId` before node creation. A
  physical connection between independently settled identities correctly fails closed as `AMBIGUOUS_MERGE`.
- Framework UI lifecycle assumptions must be verified against source: LDLib2 removes `ModularUI` from client screen
  mixins, not from the dedicated-server container menu, so server ownership requires a platform container-close event.

## 2026-09-21 Task 32 cross-Fabric authority repair

- A cross-scope client rejection is authoritative only when the baseline and competing scope originate from separate real
  production menus and server-issued sessions. Directly constructed sessions prove value-object validation, not ownership.
- Snapshot acknowledgement may legitimately remove historical flow-window entries in a following delta. Full-state
  non-mutation compares every collection and revision byte-for-byte/value-for-value without requiring every collection nonempty.

## 2026-09-22 Task 34 multi-client runtime

- LDLib2 server buttons bind `mouseDown`, and synchronized status classes are more reliable than localized label text.
- Dedicated multi-client phases require authoritative receipts and explicit close/open ownership; fixed client ticks,
  screen identity, and container ID are not stable synchronization boundaries.
- Final evidence proves revision 1 acceptance, stale-revision rejection, Fabric split invalidation, refreshed scope,
  six subscription removals or more, zero active subscriptions, and 20 closed-GUI server ticks.

## 2026-09-22 Task 34 Atlas authority repair

- Elapsed server ticks prove only time. Closed-GUI continuity requires a real native owner and independently observed state;
  the AE2 provider composition's per-lane ticker counters advance from `0,0,0` to `1,1,1` after both menus close.
- A useful bounded synthetic scale can remain honest when its source is an exact persisted production projection and the
  derivation is explicit. The verifier must independently parse that source payload and bind its node kinds, edge layers,
  Pattern rows, and digest before accepting scaled counts.

## 2026-09-22 Task 34 rendered screenshot synchronization

- `ClientTickEvent.Post` can observe the synchronized LDLib2 class and label value before the framebuffer has rendered
  either change. Evidence capture must wait for consecutive `ScreenEvent.Render.Post` frames where both values agree.
- Clearing test-client chat, toasts, and tutorial prompts before render prevents unrelated HUD fragments from appearing in
  the margins of an otherwise valid production-menu capture; screenshot text itself must never be mutated or post-processed.

## 2026-09-22 Task 34 refreshed topology authority

- A non-original Fabric reference alone is insufficient after split/rejoin because an intermediate one-member publication
  can satisfy it. Reopening is safe only after the registry has one current non-original Hub reference containing exactly
  both expected confirmed identities.
- Invalidating a Fabric retires its subscriptions immediately. Reconstruction must wait for both stale UI receipts and zero
  subscriptions before removing the old Hub, then restore the physical extension and create a fresh Hub through normal
  block lifecycle.
- LDLib2 parent text aggregates child member rows. The refreshed capture gate validates the `2 members` summary and two
  distinct nonblank member-ID rows for three rendered frames instead of comparing the aggregate to the summary alone.

## 2026-09-22 Task 35 packet authority repair

- LDLib2 generic UI RPC IDs are resolved against the current holder and carry no project menu identity. Mutation-bearing
  actions therefore require a project-owned payload with server-issued container, nonce, sequence, Fabric context, and
  Policy revision authority.
- Packet rejection is not sufficient evidence unless it drives the production menu handler. The repaired GameTest opens
  menu A then B, replays A against B, checks all mutation receipts, accepts B exactly once, and emits log-correlated facts.
- Validate complete `FlowState` input before touching meter windows or deduplication. Otherwise a rejected oversized event
  can consume its event ID and advance hidden state even when no flow is visible.
## 2026-09-22 Task 36 actual-mod compatibility

- Applied Flux 2.1.4 can be exercised through the production Storage mount without addon-specific production linkage:
  native `IStorageProvider` discovery and the Federation projection retain the same `FE_CELL_256M` backend and
  `appflux:flux` key, with matching 8192 insert, 2048 extract, and 6144 final quantity.
- Artifact checksums and source-demonstrated capability paths are necessary but not sufficient for a compatibility claim.
  Functional Storage, Pretty Pipes/Fluids, and GTCEu remain BLOCKED until complete runtime scenes exist.

## 2026-09-23 Task 36 independent profile attempts

- Modrinth version `jH3wVEds` is catalogued as Functional Storage 1.3.4 despite its 1.3.3-named JAR; the genuine 1.3.3
  artifact is `qyocTQUb` and independently matches the matrix SHA-256. Titanium's embedded license text is GNU LGPL.
- Functional Storage and Titanium load with AE2; an actual `DrawerTile` plus AE2 Storage Bus fixture currently unsettles
  native Grid identity, so no differential is qualified. Pretty Pipes/Fluids both boot, but the smoke has no logistics.
- GTCEu 7.0.2 embeds Registrate, LDLib 1.0.35.a, and Configuration 3.1.0; the exact pinned dedicated server rejects a
  client `ClientLevel` class load during `CommonInit.init`. The Gradle child exit alone is not a startup-success signal.
- Task 36's original producer called nonexistent `runCompatibilityGameTestServer`. Correcting it to the existing isolated
  Applied Flux task preserved the actual FE child; the five-case producer now fails explicitly at missing other children.

## 2026-09-23 Task 36 drawer identity repair

- `StorageBusPart.getInternalHandler()` is the adjacent composite, while the native `mountInventories` callback publishes a
  different `StorageBusInventory` object. Differential identity must compare that exact callback delegate with the
  Federation source domain, not equate the composite with its mounted wrapper.
- A newly attached Storage Bus requires the settled provider NetworkId seeded into `gn` and an explicit native cable-part
  connection before discovery. Bus priority 100 ensures native/Federation insertions target the drawer rather than the
  fixture's lower-priority ME Chest. Strict isolated GameTest passes with exact 16/4/12 physical counts.

## 2026-09-23 Task 36 replay

- The connected Pretty Pipes item scene passed once but subsequently failed three isolated runs. The actual network
  accepted 16 ingots from the source chest, yet its PipeItem remained at the source after 120 or 240 GameTest ticks.
  Extra tick budget and reducing mount trace volume did not resolve it. Neither item nor fluid routing is qualified.
- Reconfirmed the GTCEu 7.0.2 dedicated-server `ClientLevel` invalid-dist crash at `CommonInit.init:177` with AE2
  19.2.17/NeoForge 21.1.250. The launcher returned zero despite FML aborting; logs, not exit code, are authoritative.
- The Applied Flux scene now checks Federation source-domain delegate and ME Chest owner identity, and passed in isolation.
- Task 36 adversarial probes must validate their source result first: an incomplete baseline otherwise reports a generic
  probe failure instead of the missing child. The old partial report is rejected before rebinding.

## 2026-09-23 Task 31 observation and Pretty Pipes Fluids

- The Task 31 accepted amount is 1,000,000,250 AE, or 1,000,000,250,000,000,000 nano-AE. A unit-specific 9e18
  nano-AE observation bound fixes the native meter failure without changing the 9e15 item/fluid cap. A red-first unit
  regression and canonical five-case Task 31 verify passed.
- The actual Pretty Pipes Fluids network transported 1,000 mB into a Sky Stone Tank in two isolated launches; the
  native AE2 Storage Bus and Federation projection each extracted 250 mB from the identical delegate and tank.
  Two other launches left one real FluidPipeItem in the source pipe after 120 ticks despite loaded chunks.
- Vanilla GameTestServer places structures at random coordinates up to ±15m; Pretty Pipes `PipeItem` uses float
  absolute world coordinates and a 0.05-block/tick default speed. The failed run at X=4,264,128 had an unchanged
  source-pipe position, consistent with movement rounding away. This is a native addon transport issue to investigate,
  not license to replace the flow with a mock or treat successful runs as qualified.

## 2026-09-23 Task 36 real logistics

- Vanilla GameTestServer chooses a random ±15-million-block test location independently of the world spawn. A testmod
  `startTests` local-position hook can constrain only the two real-pipe tests to origin while preserving the actual
  block-entity ticker, addon routing, source extraction, physical destination and AE2 Storage Bus callback owner.
- Check the nested GT child log for the FML invalid-dist exception and absence of GameTest completion; Gradle's exit
  code alone is insufficient to classify a mod-construction failure.

## 2026-09-23 Task 39 artifact preflight (shared QA conflict)

- The unchanged strict `check build verifySharedJarContent` passes with `--no-configuration-cache`. With configuration
  cache enabled, the existing `verifyReleaseArchive(File, boolean)` closure fails deserialization on primitive `boolean`.
- The baseline binary JAR SHA-256 was `b3eda0e8863a7818472c75ffcf2020e303effcc9c6669830e753f0cf559ccb7c`;
  sources JAR was `803c3bea3bb04c8c58f40472022dc21b519ccdfd66f7930fdd54e1e14a0d713d` and dependency verification
  metadata was `2677d763489033fdd42a50b0c523cf1bb7d12b158fe71752a394b8d14b382b1b`. These characterize the
  existing incremental build only, not a clean rebuild or runtime-loaded artifact.
- The exact five-case Task 39 command fails at `gradle/federation-qa.gradle:6514`: all `artifact.*` IDs are unknown.
  Manifest registration alone is insufficient because the shared script owns child dispatch and persisted semantics.
  No shared file edit or GameTest/client launch was made while Task 37 is active.
- Cleanup receipt: no disposable checkout/cache, server/client process, world, port or Task 39 evidence attempt was
  created. The baseline used the existing workspace and Gradle cache; no task-owned external state needs removal.

## 2026-09-23 Task 39 independent artifact implementation

- Negative tests failed first on missing `artifact_verify` and now reject altered MC metadata, corrupted real JAR copies,
  split server/client bytes, stale rebuild hashes and an added target. Six tests pass against the built production archive.
- Both production archives now carry the exact AGPL project `LICENSE`. The machine-readable artifact pin checks exact
  loader/AE2/LDLib2/MC ranges, manifest fields, required shared resources and no testmod/addon content.
- An isolated first checkout/cache run failed on NeoForged's Mojang Meta 502, then a fresh attempt built successfully but
  sources JAR bytes differed only in metadata for three empty `appeng/` directories. `sourcesJar.includeEmptyDirs=false`
  removed those non-source entries. The subsequent fresh checkout/cache strict build matched both local JAR hashes.
- The independent source-bound receipt is `.omo/evidence/task-39-independent/clean-build.json`: revision
  `364173f51241fda172a4ff23b1fc3bc0105a214d`, source digest
  `0b48e11552cd6cd876dc3fcf8bdb931f54991f5036d6e0eedac963e79ef2ed21`, binary SHA-256
  `e6a284dd1cd8b5a9227f1121417d218903003ab9e2387a5134c9459cbaf7085e` and sources SHA-256
  `bd3457ed20c2fa170ca85a0764f52be7f2f581accf17381fd56b4968d8359760`. It was current at the last check.
  This is not a schema-v3 Task 39 five-case result and does not prove a running client/server loaded the binary.
- Cleanup receipt: the disposable Git worktree and isolated Gradle home were removed by the checker, and `git worktree
  list` shows only the user's original checkout. Task-created Python bytecode directories were removed. No native/client
  process or listening port was started; the GameTest CI command is deferred until Task 37 stops using the run directory.

## 2026-09-23 Task 37 staged target identity

- Establishing a physical target ME Chest/Grid before Endpoint creation, then seeding the Endpoint and AE2 Export Bus from its confirmed ID before their first tick, produced distinct node lineages on one settled target Grid. The source Grid remained distinct and the fixture retained 16 active Grids.
- A prior staged unseeded Endpoint also joined the settled anchor without an ambiguous split; therefore its separate-lineage hypothesis was false for that construction. Do not attribute earlier simultaneous-placement ambiguities to a specific competing Grid without a receipt from those runs.
- Native big-grid and subnet one-job Processing outputs can be removed before identity-only target observations. Their successful receipts do not supply a physical Federation Processing job or measured scale qualification. Final identity-only receipts are in `.omo/evidence/task-37-staged-identity-final/scale-small-identity.log`.

## 2026-09-23 Task 39 explicit approval input

- Python `require_approved_eula` and the Gradle direct artifact task prerequisite both require a nonsymlink regular file containing exactly the 10 ASCII bytes `eula=true\n`; the task-owned file and dated, scoped consent receipt are under `.omo/evidence/task-39/`. The approved input is preparation, not a runtime or artifact acceptance receipt. The exact deferred command and five case IDs are in `.omo/knowledges/task-39-artifact.md`.

## 2026-09-23 Task 37 independent three-job verification

- A fresh strict serial `scalesmallprocessingdevelopment` run independently passed one required GameTest with three distinct native-link UUIDs and three accepted push/return-injection cycles. The three physical target arrangements and their owner assertions were inspected, not inferred from layout labels; evidence and exact commands are in `.omo/evidence/task-37-independent-three-job/verification.md`.
- A configured `benchmark-native.properties` path in this direct GameTest selects sibling scale receipt output but is not itself a generated properties artifact. The native transcript and sibling receipts are the development proof. The focused profile contract, module `test check build`, Java diagnostics, world/lock and process cleanup checks passed; Task 37 remains `[~]`.

## 2026-09-23 Task 39 authorized artifact runtime

- The consent-supplied file and fresh isolated clean build produced `.omo/evidence/task-39/attempt-20260923T124941507Z`: exact five-case schema-v3 result, equal binary/source rebuild hashes, independent server/client NeoForge load paths and SHA-256, loopback two-sided join, and current-source persisted consumption. Nine Python tests and strict check/build/archive gates passed. Exact identities, commands and caveats are appended to `.omo/knowledges/task-39-artifact.md`.
- Distinguish the outer `artifact-runtime` child exit 0 and zero survivors from the nested server Gradle wrapper, which reported daemon disappearance on deliberate shutdown after the authenticated join. Do not describe the nested wrapper as a graceful zero exit.

## 2026-09-23 Task 39 multipart Bridge item asset

- `BridgeRegistration.MULTIPART_BRIDGE` registers `multipart_bridge` as a `PartItem`; the actual client requests `ae2federation:item/multipart_bridge` independently of `MultipartBridgePart.MODEL` (`part/multipart_bridge`). The old client log at `.omo/evidence/task-39/attempt-20260923T124941507Z/artifact-client.log:67` and the pre-edit JAR show the missing item resource.
- The new item JSON inherits the already-packaged provisional part geometry, which uses AE2 19.2.17's `assets/ae2/textures/part/quartz_fiber.png` and Minecraft 1.21.1's `assets/minecraft/textures/block/cyan_concrete.png`. No new texture, gameplay registration or art variant is needed. A local strict check/build/archive gate and ZIP model-chain inspection pass; a new rendered/client observation remains pending.

## 2026-09-24 Task 37 remote EULA consent preflight

- The user's explicit `我现在给予全局的EULA授权！` supersedes the earlier Task39-only consent scope for Task37. This preflight records consent but does not qualify a benchmark or launch a server.
- Batch-mode, strict-host-key SSH to `<user>@<benchmark-host>` first entered `~/ae2f` and verified physical cwd `~/ae2f`; `ls -ld . evidence` and `ls -la evidence` showed the real, empty parent. `evidence/task-37-remote-preflight` and its EULA input were absent before creation. Inspection exited 0.
- Created only `~/ae2f/evidence/task-37-remote-preflight/` (mode 700) and its nonsymlink regular `eula.txt` (mode 600). The input is exactly 10 ASCII bytes `eula=true\n`, written without overwrite. Creation/first byte comparison and a separate SSH readback (`realpath`, `stat`, `cmp`, `ls`) each exited 0; readback confirmed exact path, content, modes, and that the directory contains only `eula.txt`. No SSH credential bytes or fingerprint were captured.
- Remote `java -version` and `javac -version` still report 21.0.12.1 (creation command exit 0). Future task-owned GameTest runner must explicitly consume this path and retain all HOME/Gradle/temp/cache/game/evidence paths beneath `~/ae2f`, with loopback-only binding. Source transfer, Gradle, Minecraft/GameTest, process launch, listening ports, and timed Task37 qualification were not performed here; Task37 remains `[~]`.

## 2026-09-23 UTC Task 37 isolated source transfer

- A sparse checkout from an exact-HEAD object pack requires all current HEAD blobs even for excluded `.omo` paths. Packing current blobs plus commit/tree objects, but no historical blobs, allowed a clean Git sparse checkout with `.omo` absent from the working tree. The 44 committed `.omo` payloads were screened and are retained remotely only in the fresh snapshot's `.git` object store.
- An explicit 30-file overlay preserved eight modified tracked and 22 untracked source paths. A manifest over all 659 allowed regular paths plus matching scoped porcelain status, scoped binary diff, and Gradle-equivalent status-derived dirty identity proved local/remote parity without launching Gradle. The exact snapshot and proof are appended in `.omo/evidence/task-37-remote-transfer/verification.md`.

## 2026-09-24 Task 37 one-source-Grid development

- Omitting auxiliary `ScaleGridFixture` alone is insufficient: the original isolated creative power cell owns its own live native Grid. The one-Grid variant must physically join its AE2 power cell to the source and pre-tick seed both nodes with the same source `NetworkId` so identity settles without merging independent lineages.
- Enumerate scene AE2 block entities independently of fixture lists and expand discovered Grid membership with native `getNodes()`. Distinct active nodes are not distinct Grids: the final five nodes shared the same exact `IGrid` before and after both typed jobs. Keep the one-Grid assertion on the repeating `succeedWhen` path; bind output receipts to the selected process because direct selectors append to one sibling Processing log. Exact red/green and regression proof: `.omo/evidence/task-37-one-grid-development/verification.md`.

## 2026-09-24 Task 37 native physical catalog

- The mapped Provider's `AppEngInternalInventory` is sized by the testmod fixture and the registered machine's recipe list is independent of its 16 physical item-handler slots. A deterministic 256-entry one-item Processing catalog can therefore live on one Provider/Lane and share a single registered machine for serial work. Assert physical occupied slots, decoded distinct input/output keys, Lane details and native `ICraftingService.getCraftables` together; a fixture-owned count alone cannot establish advertisement. The selected red/green and limited two-job proof are in `.omo/evidence/task-37-256-catalog-development/verification.md`.
- The 256-Pattern one-Grid catalog selector can execute its highest physical slot after the two original jobs by decoding stack 255 on the repeating GameTest path and feeding its exact typed pair to the native planner. Its registered machine capability accepted and transitioned `command_block_minecart` to `golden_pickaxe`, and the third distinct link returned one output to the physical source cell while diamond and gold stayed there. Bind the three rows to the final selected process, not the shared append-only sibling log; red/green, serial regressions and cleanup are in `.omo/evidence/task-37-high-slot-development/verification.md`. This is three one-unit jobs, not catalog-wide replay.

## 2026-09-24 Task 37 64-type Drive retention

- A no-Drive 16-output job sequence reached exactly 63 completed distinct physical outputs before the original chest rejected the input for decoded slot 255. The first 2400-tick attempt had only timed out at 35, so distinguish execution budget from cell capacity by a measured, bounded selector-specific timeout. Pinned AE2 19.2.17 Drive exposes per-slot `getCellInventory`, populated through its real internal inventory; the source chest remains the direct callback storage. Retain output only after the native job retires, check exact chest extraction and cell insertion/readback, all previous typed outputs and the native mounted service view. The final 64-job selector has 64 distinct links and 1024 retained units; evidence: `.omo/evidence/task-37-64-type-development/verification.md`.

## 2026-09-24 Task 37 256 distinct-pattern one-Grid replay

- For a 256-key native replay, preserve one decoded physical Pattern slot per job rather than repeating a 64-key subset. Two real 16k Drive cells fail at job127 only after authentic callback placed 16 in the direct chest and both occupied cells simulated zero insertion. Five physical mounted cells, each capped at 63 distinct item types, retain all 256 keys after post-callback extraction: actual `getAvailableStacks` sets and quantities read back as 63/63/63/63/4 types, 4096 units. Per-key machine, callback, chest, Drive and mounted Grid accounts remain independent; bind append-only sibling rows to the selected process. Full red/green/serial regression details: `.omo/evidence/task-37-256-replay-development/verification.md`. No comparable layouts or timing qualification claimed.

## 2026-09-24 bounded native-subnet batch-16 continuation

- A source Drive at `(4,2,2)` touches the adjacent subnet Interface and merges their native Grids, so place the subnet selector's five-cell Drive at `(2,2,2)` on the source side. The corrected selected red failed at one occupied Pattern against 256; then a separately configured 256-recipe subnet probe passed decoded slots 0/1/255, each with actual target-cell arrival 16 before enabling the Export Bus, 16 typed capability transitions, exact Provider return/callback and post-callback five-cell retention. Its matched one-Grid three-job/five-cell control and serial 256/64/3x16/small regressions passed. Exact process-bound evidence and commands: `.omo/evidence/task-37-subnet-batch16-development/verification.md`. This is one untimed working subnet, not Task37 qualification.

## 2026-09-24 Task 37 physical Federation catalog batch-16

- A separate target ME Chest can hold all 16 native-routed inputs before enabling its filtered Export Bus. For each decoded slot 0/1/255, correlate the current Endpoint item capability and its exact Provider return inventory and authorized Lane with the production route, then configure the registered machine from that handler (not a test-authored return). The original source CPU/requester and five Drive cells remain on one settled Grid; the target stays separate. The final process passed three 16-output jobs and five-cell `3,0,0,0,0` / `48,0,0,0,0` distribution. Exact commands and UUIDs are in `.omo/evidence/task-37-federation-batch16-development/verification.md`.

## 2026-09-24 Task 37 one-target Federation 256-job replay

- Reusing the same physical Federation target, real catalog and five-cell Drive with decoded slots 0..255 yields 256 distinct native links, 256 target-cell pre-export arrivals of 16, 256 typed machine/callback/drain accounts and final actual cell distribution `63,63,63,63,4` / `1008,1008,1008,1008,64`. The original direct ME Chest remains the callback destination before every Drive drain. Keep per-tick route authority assertions but not a redundant full receipt each tick; the 23,000-tick one-Grid bound reached only 149 jobs in the first Federation attempt, so this selector alone uses 46,000 ticks. Full process-bound evidence: `.omo/evidence/task-37-federation-256-replay-development/verification.md`.

## 2026-09-24 Task 37 one-target native-subnet 256-job replay

- The prior subnet three-job path first fails the new selector's 256-job work assertion after authentic slots 0/1/255. Decoding all physical slots 0..255 into the existing planner/requester/CPU probe completes 256 distinct 16-unit jobs, each with 16 actual target-cell inputs before Export Bus activation, exact per-key machine/return/callback accounts and post-callback chest-to-five-cell retention. Final native cells hold 256 distinct output keys and 4,096 units in the same `63,63,63,63,4` distribution. Seed the replay target's real Interface/Chest/Bus nodes with one target lineage before boot and verify separate confirmed source/target identities; the old subnet constructor is unchanged. This selector has no auxiliary fixture or additional target. Process-bound evidence and serial controls: `.omo/evidence/task-37-subnet-256-replay-development/verification.md`. It is untimed and not scale qualification.

## 2026-09-24 Task 37 two working native-subnet targets

- Disjoint physical Pattern slots 0/1 can publish exactly one native Lane each on one Provider; `CraftingService.getProviders(pattern)` proves exact AE2 publication while the per-Lane host's EAST/SOUTH target faces determine actual input destination. With both hosts EAST, Lane 1's native dirt push reached target A rather than satisfying B's 16-unit pre-export gate. Changing only that Lane face to SOUTH made the same two serial CPU/requester jobs reach separate settled physical target Grids.
- A south pod at Interface `(3,2,4)`, Chest `(3,2,5)`, bus `(2,2,5)` facing its machine `(2,2,6)` does not overlap the original east pod or source Drive `(2,2,2)` and power `(2,2,3)`. Each target's own physical cell reaches 16 before its Export Bus filter is enabled; machine/return owners, 16 one-item transitions, callbacks and a typed physical Drive readback close each account, totaling 32. Full process-bound red/green and serial three-job/256-job controls: `.omo/evidence/task-37-native-two-target-development/verification.md`. No concurrency or timing claim follows.

## 2026-09-24 Task 37 physical Federation two-target route

- A second Bridge's red source leg must be staged as an actually connected source-Grid cable path; placing the low south cable alone can leave it on an inactive, isolated Grid. An elevated red extension inside the GameTest footprint gives both Bridges separate active Fabrics to two settled target Grids without joining either target to the source native Grid.
- A single `ProviderRuntime` can resolve two separately claimed physical Endpoints by native Lane index while sharing Provider identity, CPU, requester, Drive and encoded Pattern inventory. An intentional Lane 1-to-A request binding completed Lane 0's authentic job first, then failed at the actual authorized target resolution with B's cell empty. Exact indexed binding passed two serial 16-output jobs, each with 16 machine transitions, exact return owner, callback and physical retention. Process-bound commands and limitations are in `.omo/evidence/task-37-federation-two-target-development/verification.md`.

## 2026-09-24 Task 37 direct native two-machine development

- Two testmod capability machines can sit directly EAST and SOUTH of one shared Provider at `(3,2,3)` without adding a second native Grid: six active source nodes, including the Provider/CPU/requester/Drive, share one exact settled `IGrid`. Disjoint encoded Pattern slots publish through AE2 `CraftingService.getProviders(decoded)` to separate native Lanes whose host faces choose their distinct adjacent machines.
- With both hosts EAST, the first genuine 16-output job completed but Lane 1's native push repeatedly rejected dirt at machine A, while machine B remained empty. Switching Lane 1 SOUTH yielded two separate physical machine and return-inventory identities, 16 one-item transitions and callbacks per typed job, and 32 retained units in one actual source Drive cell. Process-bound red/green and serial controls: `.omo/evidence/task-37-one-grid-two-machines-development/verification.md`. This is untimed, serial correctness only.

## 2026-09-24 Task 37 direct two-machine 256-Pattern replay

- Alternating physical catalog slots by parity across the existing two native Lanes requires dispatching the selected machine **and** Provider return owner by decoded slot, not by the second-job index. Configure disjoint 128-recipe capability machines before native work; the existing per-key transition filter and direct callback chest-to-five-cell drain then prove 128 genuine 16-unit jobs on each machine. A deliberate EAST/EAST face failed at the B destination after A had completed a native job, while SOUTH routed odd slots successfully. The final process has 256 distinct native UUIDs, 256 retained types and 4096 units on one stable six-node Grid. Process-bound red/green, two serial controls and limitations: `.omo/evidence/task-37-one-grid-two-machines-256-development/verification.md`.

## 2026-09-24 Task 37 two-target native-subnet 256 replay

- Multi-job subnet target selection must follow the decoded physical slot parity, not `jobIndex == 1`. The shared Provider has 256 unique one-in-one-out physical Patterns split 128/128 between EAST and SOUTH native Lanes; their separate target Interface/Chest/Export Bus/machine pods avoid the five-cell source Drive at `(2,2,2)`. Each original target cell receives 16 through native push before its own bus activates, with zero selected input in the opposite target. Per-tick exact Grid object and settled NetworkId checks keep source/CPU/requester/Drive and both target Grids distinct through all 256 serial jobs. A process-bound audit verifies 256 distinct native UUIDs, exclusive machine and return owners and 4,096 physical Drive units. Full red/green and controls: `.omo/evidence/task-37-native-two-target-256-development/verification.md`. This is correctness only, not timed scale qualification.

## 2026-09-24 Task 37 two-target Federation 256 replay

- The two-Endpoint `ProviderRuntime` can keep both real Bridge/Fabric/Policy/Claim legs active while a 256-slot shared physical catalog publishes 128 exclusive native Patterns per Lane. Dispatch the selected physical Endpoint, machine, Lane and native return owner by decoded slot parity throughout the replay. Each capability machine must receive its complete parity-specific 128-recipe catalog only once, after the first native arrival establishes its exact Endpoint return handler; later jobs verify handler identity rather than reconfigure it. The selected route's original target cell receives all 16 native inputs before its own Export Bus activates, and the opposite cell stays empty for that key.
- A wrong Lane 1-to-A binding failed after authentic A work; the final single process completed 256 distinct native UUIDs, 128 jobs each on nonshared machines and return owners, and 4,096 units in five mounted source Drive cells. Exact serial commands, process-bound audit, controls and limitations: `.omo/evidence/task-37-federation-two-target-256-development/verification.md`. This is untimed two-target correctness only; Task 37 remains `[~]`.
- Readiness inspection must not call the physical Bridge neighbor-change hook on every job polling tick. Refresh the two legs only during staged topology establishment, then use read-only membership/Policy/Claim/identity checks during the 256-job replay. The final source-bound selector and both serial controls passed after this correction; the earlier green process is an intermediate, not the final SHA-bound process.

## 2026-09-24 Task 37 direct four-machine one-Grid development

- EAST `(4,2,3)`, SOUTH `(3,2,4)`, UP `(3,3,3)` and DOWN `(3,1,3)` are four viable adjacent capability-machine positions around the existing Provider `(3,2,3)` without colliding with its WEST power cell. Four exclusive decoded physical Patterns, native Lane publications, unique BE/handler/return identities and one stable six-node confirmed Grid were checked repeatedly. Four serial 16-unit native planner/tracker/CPU jobs each changed only its selected physical machine's transition count and produced its own typed callback and Drive output. The two-machine 256 replay and older one-Grid controls still pass. Exact red/green process binding and limitations: `.omo/evidence/task-37-one-grid-four-machines-development/verification.md`. This is neither 16-target feasibility nor timing evidence; Task 37 stays `[~]`.

## 2026-09-24 Task 37 direct four-machine 256-Pattern replay

- The four-job fixture's real inventory has four slots, so the new manual selector's 256-slot repeating assertion failed first with `was 4`. With 256 physical Pattern slots mapped `slot % 4`, each of the four adjacent registered machines receives exactly 64 disjoint recipes and executes 64 serial native planner/CPU/requester jobs of 16 units. Direct machine and return-owner selection must follow decoded physical slot modulo four; the four-job transition-count rule needs a replay-specific cumulative per-machine expected count on the fifth and later jobs. Process-local audit found 256 strictly growing unique native UUIDs, 256 typed 16-transition/callback/Drive accounts, and five real Drive cells holding `63,63,63,63,4` types / 4096 units on one stable six-node Grid. The original four-job and two-machine 256 controls passed independently in serial processes. Commands and receipt boundaries: `.omo/evidence/task-37-one-grid-four-machines-256-development/verification.md`. No concurrent, 16-target or timing claim follows; Task 37 stays `[~]`.

## 2026-09-24 Task 37 third native-subnet target gate

- The elevated source trunk works as ten staged physical cable segments. Its `(3,3,2)` start is a BE-free GameTest template barrier, not the logical requester's in-world host. Accept only air/barrier at loaded, BE-free positions; prove the previous cable's actual in-world edge and exact source `IGrid` before the next placement. H1 uses an AE2 Pattern Provider block at `(9,2,3)` facing EAST: its WEST in-world edge joins the source after pre-tick source-ID seeding without a second WEST power cell or synthetic H1 connection. H1's one-slot mapped Lane and H0's two-slot/two-Lane inventory exclusively publish three typed Patterns.
- C's Interface `(10,2,3)`, Chest `(11,2,3)`, energy `(11,1,3)`, Export Bus `(11,2,2)` and registered machine `(12,2,2)` form a fourth distinct settled Grid. A temporary H1 Lane host aimed at H0's EAST block caused C's native job to send its first input to A and fail before C's Export Bus activation. Restored H1-local routing completed three serial 16-output jobs and 48 physically retained Drive units; two-target subnet and four-machine direct 256-job controls passed separately. Evidence: `.omo/evidence/task-37-native-three-target-development/verification.md`. Task 37 stays `[~]`.

## 2026-09-24 Task 37 three-target Federation 256 replay

- Two physical Provider inventories can publish one global 256-Pattern catalog without duplicate encoded slots: H0 stores only modulo-3 A/B slots (171) and H1 stores only C slots (85) at the same global indexes. Native craftables remain exactly 256; target machines receive only 86/85/85 disjoint recipes after their first native arrival reveals each Endpoint's exact immutable return handler. The selected process completed 256 serial 16-unit jobs and retained all 4096 outputs in five mounted Drive cells. Process-bound audit, red, wrong-owner negative, controls and source hashes are in `.omo/evidence/task-37-federation-three-target-256-development/verification.md`. This is not timed or concurrent qualification; Task 37 remains `[~]`.

## 2026-09-24 Task 37 fourth native-subnet target

- Extending the proven source trunk from `(8,2,3)` along `(8,2,4..7)` can attach a second remote Pattern Provider at `(9,2,7)` through its real WEST cable edge without merging C or any target Grid. Preflight every candidate for loaded BE-free air/barrier before staging, then prove each predecessor's in-world edge one tick at a time. D's Interface/Chest/power/Export Bus/machine at `(10,2,7)`/`(11,2,7)`/`(11,1,7)`/`(11,2,6)`/`(12,2,6)` forms a fifth distinct Grid with confirmed identity. After separate missing-host and missing-job reds, four decoded exclusive physical Patterns completed serial 16-output native jobs and the source Drive retained 64 units. Evidence and exact process hashes: `.omo/evidence/task-37-native-four-target-development/verification.md`; no 16-target or timing claim.

## 2026-09-24 Task 37 fourth physical Federation target

- The already-proven H1 source trunk can be extended south along `(8,2,4..7)` without changing the three-target selector. A separate H2 at `(9,2,7)` joins the exact source Grid through a real WEST edge; red Bridge cable `(9,2,6)` and blue target cable `(10,2,6)` reach D's own seeded anchor/Endpoint/Export Bus without merging native Grids. Preflight loaded, replaceable, BE-free positions before construction and compare the H0 power node's saved NBT before/after H2 placement. The new route checks exact main/outer Bridge Grids, Fabric, EXECUTE/SUPPLY Policy, Claim, WEST binding and selected native Lane/return owner.
- The selected missing-D topology red failed before work; a separate missing-D-job red completed three authentic A/B/C 16-unit jobs first. Final run completed D's own fourth planner/CPU/requester job and retained 64 units in the physical source Drive. Older three-target, four-subnet and Federation three-target 256-job controls passed serially. Exact selected process hashes, commands and limitations: `.omo/evidence/task-37-federation-four-target-development/verification.md`.

## 2026-09-24 Task 37 four-target Federation 256 replay

- The separate exact manual-only selector first failed at the repeating D physical inventory assertion (one slot, expected 256). H0, H1 and H2 then installed the same global physical catalog indexes exclusively by `slot % 4`: H0 A/B, H1 C, H2 D. The native service advertised precisely 256 disjoint outputs, each of four Lanes published 64 decoded Patterns, and high slot 255 authorized only D's registered machine and return owner. Four distinct physical target Grids and one source Grid remained separate with settled IDs and real Bridge/Policy/Claim/Endpoint authority.
- The selected native process passed 256 serial planner/CPU/requester jobs with 256 distinct growing link UUIDs, 16 typed one-item machine transitions and callbacks per job, exact pre-export target cell input and owner-specific Endpoint return, zero final input, and five mounted Drive cells retaining 256 typed outputs / 4096 units. Original four-job Federation, three-target 256 Federation and four-target subnet controls passed independently. Process-local audit, commands, hashes and limitations: `.omo/evidence/task-37-federation-four-target-256-development/verification.md`. This does not qualify 16 targets, concurrency or measured timing; Task 37 remains `[~]`.

## 2026-09-24 Task 37 four physical native-subnet 256 replay

- H0 holds the modulo-four A/B physical catalog (128 occupied slots), H1 holds C (64) and H2 holds D (64), all in 256-slot inventories. The existing staged WEST source edges and four separate seeded target pods support 256 serial 16-unit native jobs without a synthetic source edge. The machine fixture allows recipes to be configured once, so H0 A/B must receive modulo-four recipes during construction while C/D are configured when added. Process-local red/green/control hashes and five-cell readback: `.omo/evidence/task-37-native-four-target-256-development/verification.md`.

## 2026-09-24 Task 37 empty 36x8x36 structure preflight

- A valid compressed structure can preserve the old empty palette/zero-block/zero-entity schema while changing only the three `size` integers, never its NBT list length. Pinned GameTest bounds start one Y above `helper.absolutePos(BlockPos.ZERO)` and span exact 36x8x36 dimensions. A candidate guard checks every intended absolute position for actual bounds, load status, no BlockEntity and air or BE-free barrier; derive the complete placed-chunk set from bounds separately from candidate chunks. Old-size and real-chest negatives and clean positive ran serially. The framework temporarily forces structure chunks; the fixture adds no tickets. Full hashes/commands: `.omo/evidence/task-37-large-structure-preflight/verification.md`.

## 2026-09-24 Task 37 sixteen source hosts

- The 36x8x36 template can hold a separately staged one-controller source with 154 dense/ordinary cable parts and 16 real Provider BEs at y=6. Check all 176 actual planned positions before placement, then settle each predecessor's *upstream* edge before extending; a newly placed Provider must boot and gain its physical WEST edge before its Lane composition can register. AE2's controller node itself reports zero carried channels, but its dense root edge carried 16 and the Grid pathing service reported 19 total under DEFAULT mode. Each Provider had one assigned channel, distinct BE/node/Lane and one confirmed source `IGrid`. Failure-first 15-host and final 16-host process receipts, exact commands, hashes and cleanup: `.omo/evidence/task-37-sixteen-source-host-development/verification.md`. This is not sixteen target routes or Task 37 completion.

## 2026-09-24 Task 37 large-source direct one-target job

- Keep the verified source-only selector independent: pass its settled source scene into a separate target helper, check the 16 original channels on each target tick, and close the original requester/hosts/Drive/CPU after the target finishes. Host 0 `(7,6,5)` has a bounded BE-free EAST position `(8,6,5)` with only its WEST Provider BE neighbor. One decoded physical slot published only through its original Lane, one registered machine, a native 16-output planner/requester UUID, 16 exact machine/return-owner one-item transitions and a real 16-unit source Drive readback all passed. The selected red omitted just the machine after 16 source channels; independent source-only and direct-four controls remained green. The direct selector logs the actual process and Drive receipt, not a schema-v3 properties artifact. Commands and hashes: `.omo/evidence/task-37-large-direct-one-target-development/verification.md`. It does not prove 16 working destinations, timings or Task 37 qualification.

## 2026-09-24 Task 37 large-source direct two-machine continuation

- Host 1 `(15,6,5)` has its own bounded, loaded BE-free EAST machine site `(16,6,5)` with only the exact WEST Provider BE adjacent, separate from host 0's machine. One physical slot 0 on each different Provider encodes a disjoint typed Pattern; native craftables include exactly diamond and gold ingot, each advertised only by its own active Lane. The one original source CPU/requester runs cobblestone->diamond and dirt->gold ingot serially as two distinct UUIDs. Exact per-machine/native-return transitions and callbacks are 16 each; the existing physical Drive retains both keys and 32 actual units after separate chest drains. Original source-only and one-target selectors remained green; commands, receipts, hashes and scope: `.omo/evidence/task-37-large-direct-two-target-development/verification.md`. This remains untimed direct correctness, not sixteen target routes or Task 37 completion.

## 2026-09-24 Task 37 sixteen working direct destinations

- The verified 16-host source supports distinct actual registered capability machines on every Provider EAST face within `scale_36_empty`; preflight all sixteen bounded/loaded/BE-free sites and non-WEST neighbors before placing any. Give each physical host its own slot 0 with disjoint `ScaleProcessingCatalog` recipe index 0..15 (do not interpret repeated physical-slot zero as a duplicate publication). Require each native service craftable/provider to resolve only its own Lane, confirmed source ID and active WEST channel. An after-settlement missing-machine-15 red failed exactly at host 15; the complete selector then passed 16 serial native 16-output planner/CPU/requester jobs with distinct UUIDs, typed exclusive machine/return transitions and callbacks. The original one-cell Drive read back all 16 distinct outputs and 256 actual units. Original source-only, one-/two-target and direct-four controls passed separately. Exact process-bound receipts and limitations: `.omo/evidence/task-37-large-direct-sixteen-target-development/verification.md`. This does not qualify sixteen distinct target Grids, concurrency or timing.

## 2026-09-24 Task 37 sixteen physical Federation destinations

- The sixteen-Provider source supports sixteen separate physical red/blue Bridge paths to sixteen independently identified target Grids in `scale_36_empty` without merging either side. Stage each anchor/Endpoint/bus and Bridge/Policy/Claim before starting the next pod; preflight all 144 distinct loaded, bounded and BE-free route positions. The exact after-settlement fifteen-target red failed, and the corrected selected process completed sixteen serial native 16-output planner/CPU/Provider/Endpoint/machine/return jobs with distinct links and target IDs, exact per-machine typed transitions/callbacks and all 256 units in the one-cell source Drive. Source-only, direct-16, subnet-16 and previous Federation-four controls passed serially, as did strict `test check build`. Exact transcript/source hashes and boundaries: `.omo/evidence/task-37-large-federation-sixteen-target-development/verification.md`.
- The selected machine's `isItemValid` check must occur immediately after recipe configuration, before the Export Bus begins feeding it: after processing starts, the handler can legitimately reject an additional stack while occupied. This is a test-stage ordering issue, not a production input-path fallback. These serial correctness receipts are not concurrency or speed measurements; Task 37 remains `[~]`.

## 2026-09-24 Task 37 sixteen-host direct full replay

- The sixteen physical source hosts each support a fixture-owned mapped sixteen-slot Pattern inventory when explicitly constructed at that capacity; the preserved one-slot selectors still run unchanged. A publication-settled red rejected missing slot 255 with 255 native craftables, and the repaired selector executed 256 distinct native 16-output planner/CPU/requester jobs on sixteen adjacent machines. Exactly 256 native UUIDs, 4096 typed one-item transitions/callbacks and five-cell physical Drive readback passed; source/direct controls and strict build passed serially. Resource differences and bound logs are in `.omo/evidence/task-37-large-direct-256-development/verification.md`.

## 2026-09-24 Task 37 reserved-local-host preflight

- Predeclared eligibility thresholds before a read-only 60-second `/proc` load sample, distinct from the user's approximately nine-hour reservation. This KVM host reports 60 online Icelake vCPUs and Java 21.0.12.1, but all observed affinities span 0-59, no designated exclusive benchmark/server CPU set was measured, and CPU governor data is absent. The seven load samples and six aggregate busy intervals, redacted competing process list, exact commands/exits and source/executable hashes are in `.omo/evidence/task-37-host-preflight/verification.md`. Whole-host low utilization alone is not pinned JVM or quiescence proof; no median was frozen.

## 2026-09-24 Task 37 resource-integrity preflight

- Parse only complete selected process transcripts: direct `green-attempt.log`, subnet `task37-large-subnet-256-green.log`, and Federation `task37-large-federation-256-green-complete.log`. Their ordered input/output catalog and input replay digests match across all 256 host-major jobs; every process has 256 distinct native UUIDs, sixteen typed transitions/callbacks per job, 4096 retained units and exact five-cell final distribution. The direct and subnet/federation source rows each report 19 Grid channels; the source CPU and Drive count are verified from layout/assertion plus physical readback, not inferred from a tier declaration.
- A process-local loaded device/pod chunk-key set (6/9/6 respectively) is not a whole-world loaded-chunk/ticket census. Powered creative cells are not finite energy debit. Keep those fields unavailable rather than copying fixture literals into a comparable resource ledger. The independent preflight emits `NON_COMPARABLE`, exit 1; in-memory negative comparator tests reject changed capacity/CPU/input and missing energy/chunks. Full commands and hashes: `.omo/evidence/task-37-resource-integrity-preflight/verification.md`.

## 2026-09-24 Task 37 perf-case wiring

- Two manifest `perf.*` IDs now use a dedicated backend over explicitly path- and SHA-bound selected process transcripts; it copies the three logs into one source/attempt-bound report and reuses the single existing Python parser. A missing measured observation is an explicit `BLOCKED`/`NON_COMPARABLE` report and nonzero Gradle exit, not an assertion pass. The negative case runs one marked test-only fully bound fixture with deliberately different CPU capacity and confirms comparison rejection. Future positive persisted consumption reruns the parser on the hash-bound attempt-local inputs. Exact command, hashes and result: `.omo/evidence/task-37-perf-verify-wiring/verification.md`.

## 2026-09-24 Task 37 first authentic small direct timing

- `small-native-big-grid` now runs the physical sixteen-Provider/256-Pattern/one-CPU/five-Drive-cell scene through `federationBenchmark` and its real `runGameTestServer` child. It repeats native planner/CPU/machine/return/callback/Drive work across separate wall/tick windows instead of sleeping after a replay. One 256-job cycle retains and verifies 4096 physical units, then accounts for their exact Drive drain before reusing the catalog. The first actual attempt reached a 303.431567194-second warmup with 4468 jobs but failed at a 300000-tick GameTest bound; the accelerated server had advanced nearly 300000 ticks in five wall minutes. It was not counted as a sample. Raising only this selected timed selector to 2000000 ticks allowed a full sample. See `.omo/evidence/task-37-small-timed-first/verification.md`.
- Three serial direct repetitions on an immutable 741-file intermediate source snapshot each passed full 300s+600s windows, with sample work of 155328, 156432 and 152816 units, respectively. The source's completion summary mislabeled cumulative drained work as retained Drive units; its raw per-job/cycle receipts remain real but it is not the final source. A later immutable final-source snapshot `be10d7f4422cce510824bbd9f678a0b25dd337aa00d3bb39d15426dc6e610a42` independently completed one warmup 300.016450434s/342614 ticks/5111 jobs/81776 units and sample 602.340710402s/634973 ticks/9476 jobs/151616 units. Its 14587 unique native UUIDs and Drive receipts reconcile 229376 drained plus 4016 final retained to 233392 callbacks. Both Gradle producer and independent persisted consumer exited zero; all owned worlds/Java descendants were gone. This is 1/3 **final-source direct** repetitions only, not a three-layout measurement.
- The strict scale tier loader rejects unrecognized JSON fields. Adding a generic lifecycle field to `small.json` initially broke that contract; a failing-first JUnit assertion caught it. The final source leaves the tier schema unchanged, keeps the timed lifecycle in the manifest entry, and binds a copy of `small.json` into its attempt. Follow-on layout/profile work must preserve this separation.

## 2026-09-24 Task 37 small subnet/Federation timed selectors

- Reusing the physical correctness replays with a cycle-boundary Drive drain, distinct global native UUIDs, and `ScaleTimedWindow` allowed a real timed subnet repetition on immutable 742-file source digest `089cd6f5cbaa0e0b041fdd184f0ac95c0a4925a9c22f351a0684ae0b9ff5e0ec`: warmup 302.338772894s/773758 ticks/3883 jobs/62128 units and sample 603.215400760s/1650201 ticks/8263 jobs/132208 units. The 12146 native links/callbacks reconcile 192512 drained plus 1824 final retained Drive units to 194336. Both producer and independent persisted consumer exited zero; owned world and descendants cleared. Earlier subnet snapshot `496209a5...` passed a true warmup but hit the 2000000 GameTest-tick ceiling before sample; only the two new selectors now have a 5000000-tick ceiling, while the 300/600 wall/work requirement remains unchanged. All hashes and receipts: `.omo/evidence/task-37-small-timed-first/verification.md`.
- The same final source's Federation selector routed 27 distinct native jobs/432 real Drive/callback units in its first ~300 wall seconds, then correctly refused to label that an accepted warmup because it had not completed the 256-Pattern catalog. It had no sample or result JSON; outer exit 1 and cleanup succeeded. The nominal 300-second warmup needs at least 256 jobs, versus the observed 27 (~0.09 jobs/s), so raising only GameTest ticks cannot make this declared work/window qualification pass. This is a measured work-rate blocker, not evidence of a speedup ratio.

## 2026-09-24 source-matched direct/subnet repetitions

- On the immutable `089cd6f5...` 742-file source, a strictly serial task-owned batch finished three direct and the remaining two subnet repetitions, each with its own 300s+ warmup, 600s+ sample, native UUID/physical Drive receipt count, pre/post manifest, process/consumer exit 0, ten-second host samples, and world cleanup. This joins the existing subnet repetition on that **same** source: direct 3/3 sample units 153280/161472/161056 and subnet 3/3 132208/134576/138800. Independent audits counted 14589/14858/15104 direct and 12146/12200/12799 subnet job UUIDs, exactly the same numbers of physical Drive receipts. Every callback total equals all drained plus final retained Drive units. Raw rates and result/log hashes are in `.omo/evidence/task-37-small-timed-first/verification.md`.
- Descriptive median sample units/wall-second are direct **268.419** and subnet **223.831** on this snapshot, giving a **1.199x raw two-layout rate ratio only**. A ~600s fixed-duration sample is not a same-resource elapsed comparison: direct has no sixteen target Grids/cells/creative power sources, and live energy, whole-world chunk/ticket, matched capacity and peak demand remain unavailable. No strict speedup or three-layout reference is supported; Federation still has no accepted warmup/sample on this source.

## 2026-09-25 Task 37 Federation stage diagnostic instrumentation

- The timed Federation replay now optionally aggregates five stage intervals (planning, native submission, target input/context, machine/callback, receipt/readback), per-stage waiting tick counts, and completed jobs. It emits one summary at the failed 256-job warmup boundary before the original exception propagates, or at each successful phase boundary. The phase clock starts at the existing post-publication timed-window start; successful warmup resets the accumulator for the sample. A server-tick difference is not CPU time, and wall interval attribution includes time between GameTest callbacks, so stage dominance remains a hypothesis until a real selected run supplies numbers.
- `ae2federation.federationStageDiagnostics=true` is read only by the timed replay; absent/false leaves the accumulator null and produces no stage-summary lines. Focused JUnit checked stage sums, waits and phase reset with synthetic clock values; the strict full `test check build` passed locally. No GameTest or client ran, so this is diagnostic readiness rather than a new Federation measurement. See `.omo/evidence/task-37-small-timed-first/verification.md` for the appended local gate and safe-run proposal.
