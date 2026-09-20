# ae2-federation-v04 - Work Plan

## TL;DR (For humans)

**What you'll get:** A complete six-capability AE2 Federation prototype: isolated network connections, persistent sharing rules, native remote processing and crafting, shared storage and ME power, and a usable in-game management interface. It includes reproducible native-behavior comparisons, actual-client tests and factory-scale reports.

**Why this approach:** Federation configures and binds the connections; actual AE2 objects keep ownership of execution and resources. Native integration feasibility is proved first, so unavailable extension points cannot quietly turn into a replacement engine later.

**What it will NOT do:** It will not merge ME networks, add a Matrix or another access device, connect Bridge to Federation Cable, impose a new supply/recovery protocol, or add wireless access, forced chunk loading, player ACLs or Fabric energy fees. Final art and recipe balance remain deferred.

**Effort:** XL.
**Risk:** High — native Lane composition, cross-network Crafting and directional energy require actual runtime proof; source-level feasibility is not sufficient.
**Decisions to sanity-check:** Hub has six interchangeable ME/Federation faces with independent network boundaries; Bridge is direct-only Multipart; Endpoint has one Federation face and five Interface-style faces. TDD and native recovery are confirmed. Dependency versions are explicitly candidate pins until the qualification gate passes.

Your next move: choose a separate implementation session, or request high-accuracy plan review first. A failed native feasibility gate blocks its dependent work; it never authorizes a different product or a second execution engine.

---

> TL;DR (machine): XL effort, high integration risk, full prototype scope, 8 waves, 40 implementation-and-test tasks, 4 final verifiers; no implementation performed.

## Scope

### Authority and current repository

This is an implementation plan, not a claim that the mod or any feasibility prototype exists. The user approved writing the plan, not executing it. Baseline is `DESIGN.md` v0.4 (1851 lines when read), plus the explicit clarifications below. Those clarifications override contradictory v0.4 wording without authorizing changes to `DESIGN.md` itself.

Existing project sources are documentation only. Paths introduced below are proposed files/modules and task contracts, not existing APIs. Read `AGENTS.md`, `DESIGN.md`, and `.omo/knowledges/design-v04-planning-grounding.md` before execution. Preserve all existing user changes, including untracked design/rule files. Do not edit project `AGENTS.md`, create issues/PRs, push, delete user worlds, or run `git clean`.

### Must have

1. Minecraft 1.21.1, NeoForge only, Java 21, ModDevGradle, one shared `common` source/resource tree and thin `neoforge-1.21.1` target.
2. Full Storage, Crafting, Processing, Automation/Stocking, ME Energy and visualization prototype scope, with compatibility and factory testing throughout.
3. **Bridge is an AE2 Multipart.** Use Toggle Bus's attachment, external-facing node, cable-extension and lifecycle mechanisms as reference. It directly attaches two different real ME networks, via cables or other native-connectable ME devices. It never connects Federation Cable. Its own isolated outer node is not proof of a second network. Missing attachment or same-Grid endpoints disable operation. Never connect its main and outer nodes to each other using native Grid connections.
4. **Hub has six equivalent faces.** Each can connect a native ME network or Federation Cable. Use independent ME boundary nodes per attached face, not one shared node that merges attached networks. All Federation faces belong to the Hub's Fabric topology. Multiple faces reaching one Grid identify one member/source, not additional capacity. A Hub can form a multi-member Fabric without requiring additional cables.
5. **Provider has one Federation face and five local ME faces sharing one native node.** One physical Pattern inventory, mapped native execution contexts per Endpoint, no item duplication.
6. **Endpoint has one Federation face and five Interface-like subnet ME/logistics faces.** Those five ME faces share its subnet node; capability type distinguishes native processing-target access from pipe returns. No dedicated exclusive rear-ME or fixed return face. Local and Federated modes remain mutually exclusive; Local mode verifies one adjacent upstream native Provider without connecting its source Grid into the subnet.
7. Global persistent `NetworkId` and sparse directional Policy keyed by consumer/provider identities and capability. Missing rules deny sharing. Topology-derived active state is separate from persistent enabled, resource filtering and backend readiness. Rules survive all Fabric/Bridge replacement, inactive periods and restart; explicit disable/delete cannot be resurrected.
8. Direct capability registration occurs once per logical authorized relationship, regardless of Bridge/Fabric count. Chained Storage retains origin/filters and never activates a direct rule lacking common Fabric.
9. Native AE2 objects and methods own execution, Blocking, locks, planner/CPU tasks, actual inputs, send remainders, returns and their persistence. Federation configures/binds/delegates and observes. Recovery follows the verified corresponding native path; associations cannot become a second executable ledger.
10. Hub/Bridge provide equivalent full management of their current Fabric. All players can operate; server validates menu context, objects, Policy, Claim, revisions and payload bounds. No player ACL is implied.
11. Same-dimensional wired prototype; zero additional Fabric energy consumption; real ME energy still required. Stored addon FE remains Storage, not automatic ME supply or Fabric fuel.
12. Explicitly provisional, recognizable prototype art; actual-client XML/LSS, controls, screenshots and diagnostics; TDD, native differential tests, call-trace assertions and persistent-world tests.

### Must NOT have (guardrails, anti-slop, scope boundaries)

- No Matrix, extra access/anchor/controller block, global terminal, handheld manager or Stocking Controller.
- No Grid/channel/CPU merging across Hub faces or Bridge boundaries. No device-class whitelist instead of native sided connectivity.
- No new Provider engine, planner, machine scheduler, inventory allocator, batch reservation/transaction engine, independent lock machine, fabricated zero-input recipes or default remote self-supply/paid-material protocol.
- No simulated success counted as transport; no blind whole-batch replay after partial writes, disconnect or Policy reactivation; no inferred exact Batch completion from untagged machine output.
- No speculative default recovery item, forced-drain gameplay or hidden input queue. Do not replace native recovery simply because Federation adds distance.
- No hidden world blocks or temporary neighbor swapping to trick target resolution; no fallback local crafting-machine dispatch when a remote lane loses binding.
- No wireless/crossdimension access, forced chunk tickets, player/team ACL, Fabric power fee/source block, second game target, formal art or final recipe balance. These are explicitly deferred, not silently omitted capabilities.
- No browser reproduction of the production UI; no mocks presented as third-party compatibility. Controlled test machines are explicitly test-only fixtures and do not replace actual mod testing.
- No dependency upgrade or source-copying policy hidden inside an API workaround. Record exact provenance and per-file licensing; preserve the existing AGPL license.

### Source reference catalog

References below use stable IDs; every ID expands to an exact document or pinned source root. Source observations are not runtime proof.

| ID | Exact reference and required symbols |
|---|---|
| D | `DESIGN.md` v0.4; cited sections in each task; latest user overrides are the Must-have list above |
| K | `.omo/knowledges/design-v04-planning-grounding.md`, including corrected overclaims from prior research |
| A | `https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a` (AE2 candidate 19.2.17) |
| A-connect | A: `src/main/java/appeng/parts/misc/ToggleBusPart.java`; `parts/AEBasePart.java`; `parts/CableBusContainer.java`; `api/networking/GridHelper.java`; `api/networking/IInWorldGridNodeHost.java` |
| A-identity | A: `api/networking/GridServices.java`; `api/networking/IGridServiceProvider.java`; `me/Grid.java`; `me/GridNode.java`; `api/networking/IGridNodeListener.java`, all under `src/main/java/appeng/` |
| A-process | A: `helpers/patternprovider/PatternProviderLogic.java`, `PatternProviderLogicHost.java`, `PatternProviderTargetCache.java`, `PatternProviderTarget.java`, `PatternProviderReturnInventory.java`; `me/ManagedGridNode.java`; `api/networking/IManagedGridNode.java`, all under `src/main/java/appeng/` |
| A-craft | A: `api/networking/crafting/ICraftingService.java`, `ICraftingProvider.java`, `ICraftingRequester.java`, `ICraftingLink.java`; `me/service/helpers/NetworkCraftingProviders.java`; `crafting/execution/CraftingCpuLogic.java`; `crafting/CraftingLinkNexus.java`; `menu/me/crafting/CraftConfirmMenu.java`; `helpers/InterfaceLogic.java` |
| A-storage | A: `api/storage/MEStorage.java`; `api/networking/storage/IStorageProvider.java`, `IStorageService.java`, `IStorageWatcherNode.java`; `me/service/StorageService.java`; `me/storage/NetworkStorage.java`; `parts/storagebus/StorageBusPart.java`; `helpers/InterfaceLogic.java`; `init/InitCapabilityProviders.java` |
| A-energy | A: `parts/networking/QuartzFiberPart.java`; `me/service/EnergyService.java`, `EnergyOverlayGrid.java`; `me/energy/IEnergyOverlayGridConnection.java`; `api/networking/energy/IEnergyService.java` |
| L | `https://github.com/Low-Drag-MC/LDLib2/tree/52dcc476975a5bcea48b6f8aa1a5dab9fc67060c`; `gradle/ldlib2-uitest.gradle`; `src/main/java/com/lowdragmc/lowdraglib2/uitest/`; `https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/ui/testing.html` (bind actual API to chosen artifact, not latest-source flags) |
| N | `https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/tree/30cafee9cd8d7f46427ec88fa8579d49c146df9a`; build, properties and wrapper; `https://docs.neoforged.net/docs/1.21.1/`; `https://github.com/neoforged/ModDevGradle` |
| C | `https://github.com/Aedial/CELLS/tree/645901c954d6f9b5e413bbba2202e471beab8141`; `src/main/java/com/cells/parts/subnetproxy/SubnetProxyGridCoordinator.java`, `PartSubnetProxyFront.java`, `SubnetProxyInventoryHandler.java`, `SubnetProxyInsertionHandler.java`; `CHANGELOG.md` |
| M | `https://github.com/ControlNet/minecraft-matrix-bridge/blob/4055c885704a1d326830790161b2a10ff38cb81c/neoforge-1.21/build.gradle`; source-sharing reference only, not compatibility evidence |

### Planned file boundaries

- Build: root `settings.gradle`, `build.gradle`, `gradle.properties`, `gradle/wrapper/`, `gradlew`, `gradlew.bat`; `neoforge-1.21.1/build.gradle`, `src/main/resources/META-INF/neoforge.mods.toml` and minimal loader entrypoints.
- Shared implementation: `common/src/main/java/space/controlnet/ae2federation/` packages `identity`, `policy`, `fabric`, `bridge`, `hub`, `processing`, `storage`, `crafting`, `energy`, `persistence`, `observability`, `ae2`, `client`. These are packages, not extra Gradle subprojects. Client class loading remains isolated.
- Shared assets: `common/src/main/resources/assets/ae2federation/` (`ui/`, `lang/`, `models/`, `textures/`); data resources under `data/ae2federation/`.
- Tests: `common/src/test/java/space/controlnet/ae2federation/`; separate dev-only source set `common/src/testmod/java/space/controlnet/ae2federation/test/` and `common/src/testmod/resources/`. Build must package/load it for tests only, never normal distribution. LDLib2 scenarios need a loaded dev source set, not merely JUnit classpath.
- New contracts/docs: `docs/architecture/`, `docs/compatibility/`, `docs/testing/`, `docs/benchmarks/`; fixture/config roots `tests/scenarios/`, `tests/benchmarks/`; Gradle QA wiring in `gradle/federation-qa.gradle` and `gradle/federation-ui.gradle`.

## Verification strategy

**Test decision: TDD.** Each executable implementation task includes red/green tests. Native comparison fixtures characterize actual AE2 behavior before Federation assertions are added. Use JUnit 5 for deterministic logic; actual NeoForge GameTest/dev testmod for world/runtime behavior; LDLib2's actual-client harness for UI. No human clicking is required for task acceptance.

### NeoForge GameTest is the primary world-integration framework

User explicitly requested NeoForge GameTest. Use its Minecraft 1.21.1 APIs, not a custom replacement scenario engine or APIs from newer Minecraft releases. Reference: `https://docs.neoforged.net/docs/1.21.1/misc/gametest/`; ModDevGradle run configuration: `https://github.com/neoforged/ModDevGradle#runs`.

| Test layer | Mandatory backend and responsibilities |
|---|---|
| Deterministic pure logic | JUnit5: Policy composition, revisions, graph/index algorithms and serialization helpers without live-world assumptions |
| In-world integration | NeoForge GameTest: actual blocks, Multipart, sided capabilities, native AE2 Grid topology, inventory, Processing, Crafting, ME energy and tick-driven state changes |
| Client interaction/rendering | LDLib2 actual-client harness: controls, server acknowledgment, text/bounds/screenshots; server-only GameTest is not GUI evidence |
| Real restart/unload/crash and long performance runs | Task-owned process/world orchestration; reuse GameTest/native fixture setup where suitable, but do not claim a structure reset proves a server restart or chunk unload |

- Task 2 creates a ModDevGradle run named `gameTestServer` with type `gameTestServer`, loading the product and dev-only testmod source set. Use `neoForge.runs` and `systemProperty`, not NeoGradle's different run DSL or its `setForceExit` workaround.
- Register annotated `@GameTest` methods through `RegisterGameTestsEvent` on the dev testmod's mod event bus, with explicit `templateNamespace = "ae2federation_test"`; keep test registration and fixtures out of the normal JAR. Limit `neoforge.enabledGameTestNamespaces` to `ae2federation_test`. Set `@PrefixGameTestTemplate(false)` when using explicit template names to avoid accidental class-name prefixes.
- Place actual bounded structure fixtures under `common/src/testmod/resources/data/ae2federation_test/structure/` (singular `structure` for 1.21.1). Use namespaced templates and `GameTestHelper` relative coordinates; generate layout variants through shared builders or `@GameTestGenerator` only where needed. No manually prebuilt private world prerequisite.
- All acceptance GameTests are `required = true`, have explicit finite `timeoutTicks`, and succeed only after substantive assertions. Prefer `GameTestHelper` state waits/sequences such as `succeedWhen` over fixed sleeps; assert no early success before asynchronous native work settles. Do not use retries to hide flaky correctness tests.
- Isolate concurrent tests by structure/region and unique world-level Policy/identity fixtures; never clear another test's world registry. Tests with shared-world lifecycle or unload assumptions run in isolated batches/processes. GameTest-managed loaded areas must not be mistaken for actual natural chunk-unload evidence.
- `tests/scenarios/manifest.json` maps each world-case ID to an actual registered GameTest/TestFunction ID and backend. `-Pcases` is project wrapper functionality, not a native GameTest CLI flag. Resolve the selected cases through verified 1.21.1 registration/filtering before launch; assert the registered and executed case sets match.
- Retain GameTest-native failure diagnostics/test IDs and machine-readable results alongside wrapper metadata; required failures and timeouts must fail CI. Zero selected tests is failure even if the server exits zero. The wrapper only selects runs, launches native backends and collects evidence; it does not replace GameTest execution or assertions.
- Task 39's quick CI explicitly runs the required GameTest suite in addition to JUnit/build; ordinary `check` alone is not sufficient proof that GameTests executed.

### Commands and evidence contract

The following are **task entrypoints to implement in tasks 1-3**, not commands already available in this documentation-only repository. They must work from repo root. `-Pcases` is a comma-separated list of exact registered case IDs; zero matches is failure. All case IDs named later must be registered in `tests/scenarios/manifest.json` with their D test IDs and expected assertions.

```bash
./gradlew :neoforge-1.21.1:check :neoforge-1.21.1:build
./gradlew :neoforge-1.21.1:runGameTestServer
./gradlew :neoforge-1.21.1:federationVerify -Pcases=bridge.valid,bridge.invalid -PevidenceDir=.omo/evidence/task-11
./gradlew :neoforge-1.21.1:federationUiTest -Pcases=ui.hub,ui.stale-context -PevidenceDir=.omo/evidence/task-15
./gradlew :neoforge-1.21.1:federationBenchmark -Pprofile=small -PevidenceDir=.omo/evidence/task-20
```

- `federationVerify`: dispatches pure logic to JUnit and bounded world-integration cases to registered NeoForge GameTests via `runGameTestServer`; it enforces the case manifest and collects machine-readable reports, without implementing a separate world-test framework. Persistent restart cases use two or more actual server processes against the same disposable world, not mocked NBT only. It includes independent dedicated-server and client-connect modes.
- `federationUiTest`: adapts documented LDLib2 system properties to ModDevGradle; invokes real client, stable selectors, synchronization waits, screenshots, bounds/text assertions, then a report verifier. Use synthetic input. Start under Xvfb if necessary; do not assume no-display OpenGL works. Preserve current source/artifact/GL/GUI scale/language metadata.
- `federationBenchmark`: creates/replays real AE2 worlds, measures native downstream work, verifies resources and rejects empty-work throughput. Profiles and duration are fixed below.
- Every invocation creates a new child attempt directory under the specified evidence directory, records run ID, source revision plus dirty diff hash, dependency lock hash, built JAR SHA-256, seed, start/end time, requested and executed cases, assertions, exit codes and artifact paths. Preserve prior attempts, never accept them as current results.
- Missing report, zero scenarios/assertions, mismatched case set, stale run ID/hash, crash, timeout or nonzero child exit means failure. Include deliberate negative self-tests proving this rule. Do not accept a success banner alone.
- “Current” evidence means the explicitly selected attempt has a unique run ID, structurally valid ordered timestamps, and source revision, dirty-diff, dependency-lock, profile/budget and built-JAR hashes that reconcile with the checkout and artifacts being verified. Do not impose a fixed wall-clock age shorter than the longest permitted workload; an old attempt whose identities still match may be inspected explicitly, while default task entrypoints must select the newly created attempt rather than a prior directory.
- Source revision plus dirty-diff hash is the authoritative repository-state identity and must include the current bytes of modified and untracked regular files outside documented evidence/build/runtime exclusions. Do not make a manually maintained transitive source manifest, filename heuristic or source LOC ceiling a blocking acceptance condition. A scoped source digest may exist only as non-blocking diagnostics unless a task explicitly requires it.
- Baseline files must bind the profile, budgets, dependency tuple/lock and capture identity without hashing an unhashed copy of their own binding field. Keep repository identity on the result/attempt, or use explicit canonical self-field normalization; never add a directly self-referential dirty-diff field. A committed baseline does not become invalid solely because its own commit changes `HEAD`; current result acceptance is decided by the attempt identity and frozen input hashes.
- Child execution uses workload-derived deadlines rather than one generic timeout. Record execution timeout separately from post-success shutdown timeout, allow a bounded graceful shutdown interval, inspect the owned process tree, and fail if execution exceeds its deadline, the main child exits nonzero, shutdown exceeds its grace period or descendants remain alive. A success marker never converts a hung process into success.
- Store stdout/stderr, JSON results, state snapshots and call traces in the attempt directory. UI adds actual PNG screenshots. Compare quantity units per AEKey type; no item+fluid+energy aggregate throughput number.
- Test-only instrumentation records **actual native method entry and object identity** for push, target access, lock/return, planning, CPU and native persistence. A wrapper invocation counter is insufficient.
- Test worlds and client/server processes are task-owned. Default bind address is loopback, remote development access is not enabled. Do not change user's server settings or accept external legal terms on their behalf; required EULA/environment approvals are explicit preflight blockers.

### Feasibility gate contract

Tasks 4-10 record `PASS`, `FAIL` or `BLOCKED` in `docs/architecture/native-integration-gates.md`, with exact source hooks, runtime evidence, native state owner and narrow adaptation rationale. `PASS` requires actual execution, not source inspection. A failure stops affected dependent tasks; independent tasks may continue. Missing dependency/hardware/API support is `BLOCKED`, never skipped success. No fallback may reduce full scope or introduce a prohibited engine/device/protocol. If no permitted native composition works, report the exact boundary for a design revision rather than claiming the plan's full outcome completed.

### Coverage and performance

Cover all D test families: T-P01..10, T-L01..12, T-E01..06, T-F01..12, T-R01..08, T-C01..08, T-S01..10, T-U01..08, T-V01..05, T-G01..14, T-B01..09. T-V03/04 are explicitly not applicable to the single-target prototype; verify no unsupported second target/range instead. Add new multipart Bridge, six-face Hub and five-face Endpoint cases. Local references override obsolete fixed-face or Matrix assertions.

- Small: 16 Grids, 256 logical Patterns, orders 1,000-10,000 units.
- Late: 128 Grids, 4,000 Patterns, orders 100,000-1,000,000 units.
- Ultra: 512 Grids, 16,000 Patterns, orders 1,000,000-10,000,000 units; an exploration tier, not an unconditional supported-scale promise.
- Correctness smoke runs on every relevant task. Task 20 establishes the deterministic `processing-small` smoke baseline with profile-declared bounded samples so it remains suitable for iterative correctness/regression work; it is not the final scale qualification and sets no timing gate. Task 37 measured runs use 5-minute warmup, 10-minute sample and three repetitions for each supported measured profile. Task 38 long soak is 2 hours after warmup. Task 37 and Task 38 record hardware/JVM/CPU/machine/load differences and both tick-time and real-time arrival rates.
- Task 20 freezes only correctness and resource budgets in `tests/benchmarks/budgets.json`; timing remains `environment-sensitive-secondary` evidence and must not freeze reference medians or define any timing regression trigger, because observed measurements on a shared unpinned machine vary by 19-29% run to run, far beyond any useful threshold. Before optimization, task 37 freezes reference medians and budgets per fixed scene/hardware on a quiesced baseline machine, recording CPU model, core count, JVM and observed load alongside each median. Default regression trigger: >10% worse throughput or p95/p99 MSPT versus the same Federation scene baseline, or >10% retained heap increase after comparable GC/idle phases. A trigger requires investigation, not silent budget changes. No resource/identity correctness regression is allowed at any tier, including task 20. Compare to native layouts for attribution, not an unsupported promise of native-equal cost.

## Execution strategy

### Parallel execution waves

Eight waves, 40 implementation-and-test tasks, then four independent final verifiers. Wave ordering is a planning aid; the dependency matrix is authoritative. No one owns more than one concurrent writer for the same files. Bootstrap/build harness work finishes before consumers modify it. Later shared contracts change through their owning task with dependent tests rerun.

Task-scoped independent review is bounded by the written plan. Every blocking finding cites an explicit task acceptance, cross-cutting contract or regression caused by the task. The first review establishes the complete blocking set; a later review may add a blocker only when the repair introduced it or the earlier review demonstrably missed an existing explicit requirement. Allow at most two repair/re-review rounds per task. If a cited plan requirement still fails after that limit, mark the task blocked and escalate the exact unresolved requirement to the user; do not invent broader closure rules or silently pass it. Improvements outside the plan are non-blocking follow-up notes. This round limit does not apply to the final F1-F4 wave, whose four distinct scopes are defined below.

| Wave | Tasks | Outcome |
|---|---|---|
| M0-A | 1-5 | Reproducible environment, test harness, actual UI and identity/connection baselines |
| M0-B | 6-10 | Native Lane, Endpoint, Storage, Crafting and energy composition proof |
| M1 | 11-15 | Multipart Bridge, six-face Hub, topology, global Policy and usable management |
| M2 | 16-20 | Production Processing bindings, native returns/recovery and first scale baseline |
| M3 | 21-25 | Native Storage access, provenance, chain sharing, subscriptions and resources |
| M4 | 26-30 | Native Crafting/automation integration, lifecycle and mixed workload |
| M5 | 31-35 | ME supply, scoped observability, full management UI and client/runtime qualification |
| M6 | 36-40 | Ecosystem evidence, benchmarks, soak, artifact verification and handoff docs |

### Dependency matrix

`Blocks` is the direct inverse of `Depends on`; transitive blocking follows the graph. `Can parallelize` means siblings in the same wave once dependencies pass; shared-path edits remain serialized.

| Todo | Depends on | Blocks | Can parallelize with |
|---|---|---|---|
| 1 | — | 2,3 | — |
| 2 | 1 | 4,5,6,7,8,9,10,36 | 3 |
| 3 | 1 | 15,33 | 2,4,5 |
| 4 | 2 | 13,14 | 3,5 |
| 5 | 2 | 11,12 | 3,4 |
| 6 | 2 | 16,17 | 7,8,9,10 |
| 7 | 2 | 18 | 6,8,9,10 |
| 8 | 2 | 21,22 | 6,7,9,10 |
| 9 | 2 | 26 | 6,7,8,10 |
| 10 | 2 | 31 | 6,7,8,9 |
| 11 | 5 | 13 | 12,14 |
| 12 | 5 | 13 | 11,14 |
| 13 | 4,11,12 | 14 | — |
| 14 | 4,13 | 15,17,21,23,26,31,32 | — |
| 15 | 3,14 | 33 | — |
| 16 | 6 | 17,18 | — |
| 17 | 6,14,16 | 19 | 18 |
| 18 | 7,16 | 19 | 17 |
| 19 | 17,18 | 20,29 | — |
| 20 | 19 | 30,37 | — |
| 21 | 8,14 | 22,25 | — |
| 22 | 8,21 | 23 | 25 |
| 23 | 14,22 | 24 | 25 |
| 24 | 23 | 27,32,37 | 25 |
| 25 | 21 | 28,36 | 22,23,24 |
| 26 | 9,14 | 27,29 | — |
| 27 | 24,26 | 28 | 29 |
| 28 | 25,27 | 30 | 29 |
| 29 | 19,26 | 30 | 27,28 |
| 30 | 20,28,29 | 34,37 | — |
| 31 | 10,14 | 32,34,36 | 33 |
| 32 | 14,24,31 | 33 | — |
| 33 | 3,15,32 | 34,35 | — |
| 34 | 30,31,33 | 38 | 35 |
| 35 | 33 | 39 | 34 |
| 36 | 2,25,31 | 37,39 | — |
| 37 | 20,24,30,36 | 38,40 | 39 |
| 38 | 34,37 | 40 | 39 |
| 39 | 35,36 | 40 | 37,38 |
| 40 | 37,38,39 | F1,F2,F3,F4 | — |

## Todos

Implementation and tests are one task. Commands below use the entrypoint contract above; evidence paths are roots containing fresh attempts. All commits are **proposed atomic commit boundaries**, not permission to commit or publish automatically.

- [x] 1. Bootstrap the shared-source NeoForge target and qualify a locked dependency tuple
  - Work: create the listed root Gradle files, wrapper, thin target, shared source/resource sets, minimal mod registration and side-isolated startup. Start with candidate MC 1.21.1 / NeoForge 21.1.250 / AE2 19.2.17 / LDLib2 2.2.34 / MDG 2.0.146 / Gradle 9.2.1 / Java21. Verify coordinates, checksums, actual metadata and dependency intersection; record in `docs/compatibility/dependencies.md` and dependency verification metadata. The collected LDLib hash is a comparison clue, not trusted proof. No floating versions or automatic Gradle downgrade. If incompatible, record blocker and precise candidate change; do not silently change the tuple.
  - References: D18.1,18.7-18.9,19.15; K; N; M; L. Dependencies: none.
  - Acceptance: shared code/assets present in one target JAR; dedicated-server startup does not load client renderer classes; dependency verification catches tampering; no second target.
  - QA: `./gradlew :neoforge-1.21.1:check :neoforge-1.21.1:build --dependency-verification=strict`; `./gradlew :neoforge-1.21.1:dependencyVerificationSelfTest`. Happy: exact tuple resolves and builds; failure: a task-owned corrupted dependency copy is rejected, without changing the shared dependency cache. Evidence `.omo/evidence/task-01`.
  - Commit: proposed `build: establish locked shared-source NeoForge target`.

- [x] 2. Establish TDD, NeoForge GameTest fixtures and a fail-closed evidence runner
  - Work: add JUnit5, dev testmod source set, NeoForge test registration, `gradle/federation-qa.gradle`, `tests/scenarios/manifest.json` and `docs/testing/commands.md`. Implement `federationVerify`, `federationBenchmark` and reusable native layout builders. The benchmark entrypoint includes profile dispatch, actual native smoke workload, metrics/report schema and rejection of unknown profiles or zero work; it is not an empty passing stub. Include actual call/object tracing, state snapshots, server restart orchestration and deterministic fault injection. Controlled machine fixtures are test-only, labeled, and always operate through real AE2 services.
  - GameTest implementation: configure the `gameTestServer` run, dev-only `ae2federation_test` registration, actual structure fixtures, relative-coordinate builders, required assertions/timeouts and manifest-to-GameTest ID dispatch as specified above. Implement positive registration/execution proof plus required-failure, timeout, namespace-mismatch and zero-selection rejection self-tests. No world-integration case may silently fall back to JUnit mocks.
  - References: D19.1,19.6,19.9,19.13; A-process/A-storage/A-craft; N; `https://docs.neoforged.net/docs/1.21.1/misc/gametest/`. Depends on 1.
  - Acceptance: red GameTest assertion yields an actual required-test failure and failed build; green GameTest executes in a real server world and passes; timeout/zero-selection cannot pass; reports bind current artifact/run; production build excludes fixtures; task cleans up only its processes/worlds.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=harness.native-smoke,harness.reject-stale,harness.reject-empty,harness.detect-child-crash,harness.benchmark-reject-unknown,harness.benchmark-reject-empty -PevidenceDir=.omo/evidence/task-02`; `./gradlew :neoforge-1.21.1:federationBenchmark -Pprofile=harness-native-smoke -PevidenceDir=.omo/evidence/task-02-benchmark`. Failure cases succeed only when their deliberately invalid inner run is rejected. Evidence includes the inner nonzero exit and expected failure assertion.
  - Commit: proposed `test: add native scenario and evidence harness`.

  - Additional GameTest QA: `./gradlew :neoforge-1.21.1:runGameTestServer`; `./gradlew :neoforge-1.21.1:federationVerify -Pcases=harness.gametest-registered,harness.gametest-required-failure,harness.gametest-timeout,harness.gametest-namespace-mismatch,harness.gametest-zero-selection -PevidenceDir=.omo/evidence/task-02-gametest`. Negative inner runs are isolated and must fail for the expected reason; they are not permanently failing tests in the ordinary required suite.

- [x] 3. Qualify LDLib2 actual-client UI tests on the selected build/runtime
  - Work: add `gradle/federation-ui.gradle`, `common/src/testmod/.../ui/`, actual XML/LSS fixture and `federationUiTest`. Adapt L's runtime properties to ModDevGradle's run DSL; do not copy NeoGradle top-level `runs` assumptions. Verify chosen artifact has required selectors/waits/capture APIs. Qualify synthetic input under display/Xvfb and record GL/GUI/language metadata.
  - References: D17.8-17.11,19.14; L; K. Depends on 1.
  - Acceptance: modify shared resource, reload actual client and assert new rendered text; screenshots and control bounds collected; UI failure or missing/stale report fails task. No production dependence on debug services.
  - QA: `./gradlew :neoforge-1.21.1:federationUiTest -Pcases=ui-harness.shared-resource,ui-harness.server-ack,ui-harness.reject-stale -PevidenceDir=.omo/evidence/task-03`. Happy: stable ID click receives server acknowledgment; failure: stale report deliberately rejected.
  - Commit: proposed `test(ui): qualify native client automation`.

- [x] 4. Prove persistent NetworkId continuity through native Grid Service node data
  - Work: prototype `identity/` service registered via `GridServices`, namespaced node metadata through `saveNodeData/addNode`, sparse world registry and host-save notification. Anchor identity in surviving native nodes, not Bridge UUID/location or Grid serial. No new anchor block. Preserve a unique lineage when provable; collisions, actual split/merge ambiguity and copied live identity fail closed without transferring old Policy.
  - References: D2.3,14.1,14.4,19.16; A-identity; K. Depends on 2.
  - Acceptance: remove all Federation devices, save/restart unchanged native network, add new attachment and recover same ID. Different/copy/split network cannot inherit permissions merely by shared location/old ID. Record settlement and partial-load conditions in `docs/architecture/network-identity.md`.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=identity.replace-all-access,identity.restart,identity.ambiguous-split,identity.copied-node -PevidenceDir=.omo/evidence/task-04`. Happy: ID continuity; failure: ambiguous lineage blocks capabilities while preserving rules.
  - Commit: proposed `test(identity): prove native network identity persistence`.

- [x] 5. Prove native attachments and isolated multipart/Hub boundary nodes
  - Work: build connection fixtures for Toggle-Bus-style Bridge and six-face Hub candidates. Reuse native exposed-node discovery, native device/cable attachments and extension rendering; never connect across independent boundary nodes. Classify actual external attachment separately from allocated node existence. Hub ports independently resolve ME or Federation neighbor; unsupported or ambiguous neighbor fails closed.
  - References: Must-have 3-4; A-connect; D19 T-F01,T-G06,T-B01. Depends on 2.
  - Acceptance: six different Hub ME neighbors remain six Grids; one neighbor connected twice is deduplicatable; Bridge outer standalone node is invalid; cables and connectable devices work without class whitelist. Rendering verified later by task 33.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=ports.bridge-cable-device,ports.hub-six-grids,ports.hub-repeated-grid,ports.reject-floating-node,ports.reject-cross-grid-join -PevidenceDir=.omo/evidence/task-05`.
  - Commit: proposed `test(fabric): prove native attachment isolation`.

- [x] 6. Prove independent native Provider Lane composition without duplicate Pattern ownership
  - Work: in `ae2/processing/`, compose actual `PatternProviderLogic` instances, mapped non-extractable Pattern views, node-service capture/global-provider registration and native ticker delegation. Start from public Host/managed-node composition; isolate necessary target/view/refresh hooks in a compatibility boundary. Preserve native push/Blocking/lock/send/return/NBT execution. Ensure global-provider refresh is not incorrectly sent through the singular node-provider refresh path. Inventory/drop/save ownership must be resolved together.
  - References: D9-12,18.3,19 T-C06..08; A-process/A-craft; K. Depends on 2.
  - Acceptance: one Pattern, three native contexts; A locked while B/C progress; different pattern-to-lane sets; one extractable/drop inventory; native state round-trip; one live publication per context. No local target fallback or adjacent crafting-machine bypass. Gate PASS only with actual native-entry traces.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=lane.native-three-way,lane.pattern-subsets,lane.single-drop-owner,lane.reject-local-fallback,lane.native-reload -PevidenceDir=.omo/evidence/task-06`.
  - Commit: proposed `test(processing): prove native lane composition`.

- [x] 7. Prove five-face Endpoint capability composition and Local-mode separation
  - Work: use Interface/native storage and generic inventory adapters to compose one Federation face plus five subnet ME/logistics faces. Bind Provider target access to subnet storage and pipe insertion to the identified upstream native return inventory. In Local mode, exactly one verified adjacent Provider's source-side connection must remain outside the subnet; suppress unintended data adjacency on that boundary, not all other ME faces. Federated mode refuses local upstream input.
  - References: latest Endpoint decision; D11-13,T-E01..06; A-storage `InitCapabilityProviders`, A-process. Depends on 2.
  - Acceptance: native local input reaches subnet; pipes on each of five sides return correctly; two upstream Providers cannot share claim; capability-aware callers cannot loop input into returns. No material-based output guessing, no stocking slots.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=endpoint.local-native,endpoint.five-face-item-fluid,endpoint.reject-two-upstreams,endpoint.reject-capability-loop,endpoint.mode-isolation -PevidenceDir=.omo/evidence/task-07`.
  - Commit: proposed `test(endpoint): prove Interface-style port composition`.

- [x] 8. Prove native Storage mount/source separation and collect CELLS regression lessons
  - Work: qualify a native source-only export boundary excluding Federation projections without replacing native aggregation/priority/filters. Prefer registered storage-provider/mount tracking; any internal access only identifies native delegates or excludes managed imports. Add actual diamond/loop fixtures and read C source/history for election, stale snapshot, first-filter and listener bugs; write `docs/architecture/storage-provenance.md` with adopt/adapt/reject reasons.
  - References: D4.5-4.7,5,18.6; A-storage; C; K. Depends on 2.
  - Acceptance: source handles are actual native stores; same source listed once; no recursion through entire mutually mounted aggregate; unsupported opaque alias boundary diagnosed. No CELLS 1.12 API port, one-hop product rule or extra spendable inventory.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=storage-proof.native-projection,storage-proof.four-fabric-diamond,storage-proof.reject-loop,storage-proof.opaque-alias -PevidenceDir=.omo/evidence/task-08`.
  - Commit: proposed `test(storage): qualify native provenance boundary`.

- [x] 9. Prove a complete native Crafting capability path before selecting production bindings
  - Work: trace native terminal and Crafting Card discovery, planner, actual material extraction, CPU/provider, result and cancel/reload. Evaluate native service/request binding first; native Provider delegation only if complete return and originating job behavior can also be preserved. Record exact object identities, service ownership and the minimal connection-only hooks in `docs/architecture/native-crafting-contract.md`. Do not impose separate supply rules, create fake zero-input patterns, or register capability-only stubs as completion.
  - References: D6-7,18.3,T-B09,T-C06..08; A-craft/A-storage; K. Depends on 2.
  - Acceptance: same backend/reference conditions yield matching native material, missing-material, CPU, result/cancel behavior. One unique native task per actual request despite duplicate paths. If neither permitted binding closes the entire flow, gate BLOCKED with exact missing hook; M4 cannot proceed by inventing a protocol.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=craft-proof.native-terminal,craft-proof.native-stocking,craft-proof.missing-material,craft-proof.no-cpu,craft-proof.disconnect-cancel-restart -PevidenceDir=.omo/evidence/task-09`.
  - Commit: proposed `test(crafting): qualify end-to-end native delegation`.

- [x] 10. Prove directional ME supply through native energy operations
  - Work: qualify native energy-provider/service composition for a consumer to use an allowed provider's real energy. Quartz Fiber is an isolation reference, not permission for a symmetric overlay that defeats directional Policy. Prevent recursive remote energy providers, never add a separate energy store/settlement engine, and distinguish native local energy from imported supply.
  - References: D8,T-F10..11,T-B02..05; A-energy. Depends on 2.
  - Acceptance: allowed direction works; reverse without rule cannot draw; rings conserve energy; no source means no ME power; loaded unpowered consumer can discover allowed supply because Fabric control is zero cost. Unavailable native composition is BLOCKED, not automatic symmetric fallback.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=energy-proof.directional,energy-proof.cold-start,energy-proof.reject-reverse,energy-proof.no-source,energy-proof.ring -PevidenceDir=.omo/evidence/task-10`.
  - Commit: proposed `test(energy): qualify directional native supply`.

- [x] 11. Implement the direct-only Multipart Bridge and native lifecycle
  - Work: `bridge/`, thin registration and provisional part model. Adopt task 5's validated native host/external node setup and Toggle Bus visual extension. Two actual externally attached distinct network domains required; no Federation Cable or redstone-toggle feature. Expose operational reason, membership candidate and right-click context; native side updates refresh classification.
  - References: Must-have 3; A-connect; task 5 contract; D T-F01,T-F03,T-G05..06. Depends on 5.
  - Acceptance: native-connectable cable/device forms valid bridge; absent side/same network disables; removing/replacing/unloading cleans nodes and no main-to-outer connection exists.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=bridge.valid,bridge.invalid,bridge.same-grid,bridge.reject-federation-cable,bridge.reload-replace -PevidenceDir=.omo/evidence/task-11`.
  - Commit: proposed `feat(bridge): add isolated native multipart attachment`.

- [x] 12. Implement six-face Hub and Federation Cable topology ports
  - Work: `hub/`, `fabric/port/`, Hub/Cable registration and recognizable provisional models. Every Hub face accepts ME or Federation cable based on actual neighbor capabilities. ME boundary nodes stay isolated; Federation faces join only custom topology. Recognize native devices as well as cables; remove/recreate affected bindings on neighbor and capability changes.
  - References: latest Hub decision; task 5 contract; D3-4,19 T-G01,T-B02. Depends on 5.
  - Acceptance: mixed port layouts, all-six-ME and all-six-Federation layouts work; unsupported neighbor stays disconnected; no native Grid joins; no forced chunk loads or Fabric power requirement.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=hub.mixed-six-faces,hub.six-independent-me,hub.repeat-network,hub.port-replacement,hub.reject-unsupported -PevidenceDir=.omo/evidence/task-12`.
  - Commit: proposed `feat(fabric): add six-face isolated Hub and cable ports`.

- [x] 13. Implement Fabric components, memberships and incremental common-Fabric indexes
  - Work: `fabric/` registry using task 4 identities and tasks 11/12 port events. Each valid direct Bridge forms its own minimum Fabric; Hub/Cable physical connectivity forms components. A shared ME Grid does not merge Fabric topology. Maintain network-to-Fabric and affected-component indexes; loaded evidence only, budgeted recomputation and immediate invalidation of uncertain routes.
  - References: D4.1,4.6,4.8,16; A-identity; tasks 4,5. Depends on 4,11,12.
  - Acceptance: cycles converge, four independent Bridge Fabrics stay separate, Hub branches merge/split, redundant network attachments deduplicate. No world scan per tick or enumeration of all possible paths/network pairs.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=fabric.bridge-diamond,fabric.hub-merge-split,fabric.redundant-membership,fabric.partial-unload,fabric.reject-stale-route -PevidenceDir=.omo/evidence/task-13`.
  - Commit: proposed `feat(fabric): index components and shared membership`.

- [x] 14. Implement sparse global Policy, revisions and lifecycle activation
  - Work: `policy/`, world `persistence/`, namespaced schema, sparse consumer/provider/capability entries, enabled/filter/reexport settings and authoritative revisions. Derive active only from confirmed common Fabric; distinguish unconfigured/off/disconnected/backend-unready. Serialize configured rules, not active/Grid handles. Reject stale edits and keep deleted/disabled records from stale cache resurrection. No TTL/LRU eviction of Policy.
  - References: D4.3-4.8,14.6,T-G01..14; task 4 identity gate. Depends on 4,13.
  - Acceptance: all merge/split/new-Bridge/restart scenarios preserve rules; invalid identities never inherit rules; old edit cannot resurrect deletion. Initial Storage reexport toggle is off as D's engineering default.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=policy.lifecycle-matrix,policy.new-bridge-restore,policy.reject-stale-edit,policy.delete-reconnect,policy.sparse-scale -PevidenceDir=.omo/evidence/task-14`.
  - Commit: proposed `feat(policy): persist sparse global network rules`.

- [x] 15. Deliver minimal unified Hub/Bridge management with real server acknowledgments
  - Work: `client/menu/`, `client/policy/`, `ui/fabric.xml`, shared LSS and localized labels. Right-click any valid Hub/Bridge opens full current-Fabric members and directional rules; pending topology invalidates stale editor context. All players may operate, but object/menu distance/current context/input bounds/revisions are server checked. Disabled Bridge exposes diagnostic state without fabric-wide stale edits.
  - References: D17.1,17.5,17.7,19 T-F12,T-U03,T-G10; L; task 3. Depends on 3,14.
  - Acceptance: two different entrances edit the same record; no player ACL; menu closure never stops sharing; stale topology submission visibly rejected.
  - QA: `./gradlew :neoforge-1.21.1:federationUiTest -Pcases=ui.hub,ui.bridge,ui.shared-policy,ui.stale-context,ui.close-unsubscribe -PevidenceDir=.omo/evidence/task-15`.
  - Commit: proposed `feat(ui): add unified Fabric policy management`.

- [x] 16. Implement single-owner Pattern inventory and mapped native Lane lifecycle
  - Work: `processing/provider/` and task 6's qualified `ae2/processing/` boundary. Physical Pattern slots use native storage/decoding; each Lane sees only assigned patterns. Handle native provider registration/refresh and ticker wake/sleep without duplicate node services; priority/redstone/Pattern access terminal affect correct instances. Keep public gameplay limits out of prototype fixtures; document tested counts.
  - References: D9.2,10.2-10.5,17.3; A-process/A-craft; task 6 gate. Depends on 6.
  - Acceptance: inserted/removed/replaced Pattern updates relevant native publications and Blocking inputs; equal Patterns retain independent mediums; item inventory/save/drop ownership once.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=provider.pattern-mapping,provider.refresh-priority,provider.single-inventory,provider.reject-stale-pattern,provider.native-ticker-count -PevidenceDir=.omo/evidence/task-16`.
  - Commit: proposed `feat(processing): bind mapped native Provider lanes`.

- [x] 17. Implement Provider five-plus-one wiring, Claim and authorized target binding
  - Work: Provider facing and `processing/claim/`, target binding from native lane to Endpoint, authoritative Endpoint owner/epoch, same-subnet overlap detection, source-target separation, Policy/common Fabric checks. Rotation updates connections, not identity or owned remainder destination. Preserve legacy Claim on offline target; never auto-steal on timeout.
  - References: D3.3,10.1,10.6-10.7,14.5,T-L05..06,T-B01; task 6 contract. Depends on 6,14,16.
  - Acceptance: only one owner wins; five ME faces one source Grid; Federation face no native data adjacency; same/subsequently merged target domain pauses affected lanes without retargeting.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=provider.orientation,claim.compete,claim.offline-owner,claim.overlap,provider.rotate-pending,provider.reject-same-grid -PevidenceDir=.omo/evidence/task-17`.
  - Commit: proposed `feat(processing): authorize native Endpoint bindings`.

- [x] 18. Implement Interface-like Endpoint Local/Federated modes and native return binding
  - Work: `processing/endpoint/`, task 7 capability bindings on five faces, explicit single local upstream identity or federated Claim. Return generic inventory/item/fluid paths delegate into correct actual native Provider return inventory. No default independent Endpoint buffer; when no permitted sink exists, accept zero so the caller retains ownership. Mode changes cannot repurpose outstanding native contexts.
  - References: D12-13,T-E01..06,T-P04,T-L07..08; latest owner corrections; task 7 gate. Depends on 7,16.
  - Acceptance: input and return are separated by capability/context, not guessed resource; all five logistics sides work; Local mode needs no Hub/Cable; two local upstreams rejected; no Grid merge across local source boundary.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=endpoint.local,endpoint.federated,endpoint.five-face-returns,endpoint.return-backpressure,endpoint.reject-mode-takeover -PevidenceDir=.omo/evidence/task-18`.
  - Commit: proposed `feat(endpoint): delegate Interface-style input and returns`.

- [x] 19. Complete native Processing differential, disconnect and dismantling regressions
  - Work: characterize native reference failure/removal behavior first, then validate Federation at identical boundaries. Cover actual substituted inputs, shared-slot capacity, mixed fluids, native partial-send ownership, redstone locks, partial primary output, byproducts, deleted mappings and reloaded native state. Preserve retired native contexts while they own recoverable resources; do not add automatic refunds/replay or a new default recovery item.
  - References: D11-12,15,19 T-P01..10,T-L01..12,T-R01..05,T-C06..08; A-process. Depends on 17,18.
  - Acceptance: native methods/objects remain authoritative; every resource has one owner; normal reject has no mutation; partial write never causes false rejection plus full replay; unresolved non-native responsibility blocks explicitly. Native limitations are reported, not disguised as stronger atomicity.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=processing.native-differential,processing.shared-capacity,processing.lock-isolation,processing.disconnect-restart,processing.dismantle,processing.reject-false-replay -PevidenceDir=.omo/evidence/task-19`.
  - Commit: proposed `test(processing): lock native execution and recovery parity`.

- [x] 20. Establish generated factory scenes and Processing performance baseline
  - Work: add `tests/benchmarks/processing/` and the real-world factory scene generator; extend task 2's existing `federationBenchmark` with the deterministic Processing small-smoke profile, metrics and budgets. Record physical patterns versus lanes/routes/provider entries, actual input calls, accepted native remainders, return retries, CPU limits and delivered output. Native single-grid/multi-provider references use equal resources. Measure counters without per-event production logging. Before final acceptance, repair the shared harness to enforce the evidence identity, freshness and phase-aware process-lifecycle rules above; remove `source-authority.json` and its transitive-closure/LOC checks from blocking verification rather than expanding a hand-maintained manifest. Timing is recorded as environment-sensitive-secondary evidence; do not freeze medians or timing thresholds here.
  - References: D16,19.9-19.13,T-S04,T-S06; task 19. Depends on 19.
  - Acceptance: seeded small scene replays real production; rejected targets backpressure without unbounded memory; current-attempt resources reconcile; baseline/budget files freeze correctness and resource limits only, and bind their declared profile/budget/dependency inputs without baseline self-reference. Persisted result acceptance matches source revision plus dirty-diff and artifact hashes, not a fixed 900-second age or manual source closure. Execution timeout, shutdown timeout, nonzero exit and surviving descendants are distinct fail-closed outcomes. Contract tests cover modified/untracked source mismatch, baseline self-reference prevention, old-but-hash-current explicit evidence, execution timeout and a zero-exit parent that leaves a live descendant.
  - QA: `./gradlew :neoforge-1.21.1:federationBenchmark -Pprofile=processing-small -PevidenceDir=.omo/evidence/task-20`; `./gradlew :neoforge-1.21.1:federationVerify -Pcases=benchmark.reject-empty-work,benchmark.resource-accounting,benchmark.evidence-identity,benchmark.execution-timeout,benchmark.shutdown-timeout -PevidenceDir=.omo/evidence/task-20-selftest`. After the harness repair, run one final independent Task 20 review limited to this task's acceptance and the cross-cutting contracts cited here; additional non-plan enhancements are recorded as follow-up and do not reopen Task 20.
  - Commit: proposed `test(perf): establish reproducible Processing factory baseline`.

- [x] 21. Mount authorized native Storage projections once per relationship
  - Work: `storage/mount/` using task 8's qualified native boundary. Forward AEKey, quantity, action and source; compose native filter/access/priority behavior with Policy, not a new allocator. Distinguish visibility/extraction/insertion and source readiness. Hub and Bridge use the same policy service; no Endpoint inventory automatically exported merely by processing reachability.
  - References: D5,T-F01..06,T-C01,T-C03; A-storage; task 8. Depends on 8,14.
  - Acceptance: native terminal/automation sees permitted resources; actual accepted amount controls mutation; simulate has no side effects; redundant connections add no mounts/capacity.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=storage.native-access,storage.priority,storage.view-only,storage.simulate,storage.reject-revoked -PevidenceDir=.omo/evidence/task-21`.
  - Commit: proposed `feat(storage): mount authorized native access`.

- [x] 22. Implement provenance aliasing and source-only export lifecycle
  - Work: `storage/provenance/`, map native source domains, ExportSource and aliases, distinguish managed projections from true native sources. Unmount/reattach with generation changes; diagnose opaque external aliases without claiming full third-party dedup. Two Bridges or two Hub faces reaching one source are aliases, not inventories.
  - References: D2.3,4.5-4.6,5.3,T-F02..03,T-S10; A-storage; C; task 8. Depends on 8,21.
  - Acceptance: exact same origin listed once across entry aliases and Grid rebound; unsupported alias configuration cannot silently double quantities; native stores remain operation authority.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=provenance.multi-entry,provenance.native-rebind,provenance.exclude-import,provenance.opaque-boundary -PevidenceDir=.omo/evidence/task-22`.
  - Commit: proposed `feat(storage): preserve true source identity`.

- [x] 23. Compile chain-sharing policies into bounded effective source relationships
  - Work: `storage/dependency/` and `policy/` effective rule composition. Initial reexport off; per-chain intersection and alternative-chain union retain operation and resource scope. Stop origin cycles and deduplicate origin/consumer pairs; do not enumerate all complete paths. Track candidate relationship revisions and gate actual operations through permitted native chains.
  - References: D4.5-4.7,T-S01..02,T-G11; C; tasks 14,22. Depends on 14,22.
  - Acceptance: four independent Bridge Fabrics form A-B/C-D dependency without common Hub; D sees A once. A-C direct inactive rule stays inactive even when a chain permits access. One allowed filter cannot widen another forbidden resource.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=chain.four-fabric-diamond,chain.filter-union-intersection,chain.toggle-reexport,chain.reject-direct-activation,chain.reject-cycle -PevidenceDir=.omo/evidence/task-23`.
  - Commit: proposed `feat(storage): compile controlled chain sharing`.

- [x] 24. Implement source subscriptions, snapshot/increment boundaries and bounded invalidation
  - Work: `storage/subscription/` with task 8's verified native notification boundary. Single forwarding responsibility, source generation/event identity when native events permit it, correct absolute-count versus delta interpretation, snapshot version baseline and affected-range reconciliation. Quantity changes do not rebuild topology. Do not mute required native notifications or count imported updates as new local origins.
  - References: D4.7,16.2-16.3,T-S03,T-S08..10; A-storage; C. Depends on 23.
  - Acceptance: two legitimate same-key changes retained; diamond consumer effective update once; first filter/reset/reconnect correct; listeners released; invalid policies block mutations before deferred recomputation completes.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=subscription.two-same-key-events,subscription.diamond-once,subscription.snapshot-race,subscription.first-filter,subscription.listener-cleanup,subscription.reject-stale-generation -PevidenceDir=.omo/evidence/task-24`.
  - Commit: proposed `perf(storage): bound provenance-aware notification work`.

- [x] 25. Qualify registered addon resources including stored FE
  - Work: `storage/resources/` only where native type registration/serialization requires glue, plus isolated compatibility test profiles. Select a published MC1.21.1 NeoForge addon that actually registers an AE2-storable energy key, verify source/license/artifact, pin in `docs/compatibility/resources.md`. Do not invent a synthetic FE key and label it ecosystem support. Preserve component identity, native long quantities and units; no FE-to-ME conversion.
  - References: D5.5,T-C02,T-B04; A-storage; task 21. Depends on 21.
  - Acceptance: real item/fluid/addon key round-trips through storage and filters, absence of optional addon loads cleanly; unsupported registrations are reported as missing compatibility, not silently converted.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=resources.item-fluid-components,resources.stored-fe,resources.optional-absent,resources.reject-overflow,resources.reject-fe-power-coupling -PevidenceDir=.omo/evidence/task-25`. Missing qualifying addon makes stored-FE case BLOCKED, never mocked PASS.
  - Commit: proposed `test(storage): qualify native addon resource keys`.

- [x] 26. Integrate the verified native Crafting capability binding under global Policy
  - Work: `crafting/binding/` and only hooks proven by task 9's native contract. Publish each genuine capability once; preserve native service/planner/CPU/request/result ownership and actual source attribution. Bind/unbind on Policy and common-Fabric lifecycle, not menu state. Do not add an independent order manager or canned material contract.
  - References: D6,T-F02,T-F07,T-C06..08; A-craft; task 9 PASS contract. Depends on 9,14.
  - Acceptance: real target capability visible through the selected native boundary; duplicate connections do not add execution capacity; unavailable native path is not advertised as working.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=crafting.native-binding,crafting.deduplicate-capability,crafting.native-state-owner,crafting.reject-unavailable -PevidenceDir=.omo/evidence/task-26`.
  - Commit: proposed `feat(crafting): bind authorized native capabilities`.

- [x] 27. Integrate native terminal discovery, planning and result recognition
  - Work: adapt the exact native terminal entrypoints proven in task 9, including permitted storage visibility, planner snapshot/thread constraints, missing-material feedback and native CPU selection. Do not broaden terminal behavior beyond the approved native delegation contract. Result ownership follows actual native callback paths, not public inventory increments.
  - References: D6.3-6.6,T-B09,T-C03; A-craft `CraftConfirmMenu`, A-storage; task 9. Depends on 24,26.
  - Acceptance: native terminal can complete permitted remote-capability demand; missing-material/no-CPU cases remain accurate; backend world access does not occur illegally on planner thread.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=terminal.native-crafting,terminal.native-result,terminal.missing-material,terminal.no-cpu,terminal.reject-async-world-access -PevidenceDir=.omo/evidence/task-27`.
  - Commit: proposed `feat(crafting): preserve native terminal request flow`.

- [x] 28. Verify native Interface/Crafting Card and bus automation over shared capabilities
  - Work: qualify actual Interface stocking, Crafting Card and native Import/Export Bus use with tasks 21-27. Reuse native request/in-flight state; no Federation duplicate bookkeeping or stocking controller. Test item/fluid/addon absence and source contention using actual native inventories.
  - References: D7,T-F09,T-C03,T-S07; A-craft `InterfaceLogic`; A-storage. Depends on 25,27.
  - Acceptance: one native demand does not resubmit each tick; canceled demand follows native cancellation; simultaneous consumers never extract more than real stock; no implicit reciprocal stocking rule.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=automation.interface-stock,automation.crafting-card,automation.native-buses,automation.reject-duplicate-demand,automation.contention -PevidenceDir=.omo/evidence/task-28`.
  - Commit: proposed `test(automation): qualify native stocking and buses`.

- [ ] 29. Close Crafting cancellation, cycles, disconnect and native reload lifecycle
  - Work: bind necessary native request references across actual service/node lifecycle as proven in task 9. Cycle detection protects capability/request dependency without becoming a scheduler. Separate lost visibility from native task failure; reactivation cannot resubmit already-existing native work. Retain only necessary mapping to native link/state; no default result buffer.
  - References: D6.5-6.6,15,T-F07..08,T-R01..05,T-G12; A-craft. Depends on 19,26.
  - Acceptance: native cancel semantics preserved after material consumption; closed UI does not lose results; disconnect/restart/different same-coordinate node cannot cause duplicate job or wrong result owner.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=crafting.cancel-native,crafting.disconnect-restart,crafting.reject-cycle,crafting.reject-replay,crafting.replace-requester -PevidenceDir=.omo/evidence/task-29`.
  - Commit: proposed `test(crafting): lock native lifecycle and failure parity`.

- [ ] 30. Exercise mixed Storage/Crafting/Processing/Stocking factory load
  - Work: `tests/benchmarks/mixed/`, seeded long recipe chains, varied batch/key sizes, native CPU limits and repeated stocking. Include independent-call-frequency versus quantity axes, alternative inputs and blocked lanes. Attribute native planner/storage/handler work instead of only direct Federation cost.
  - References: D16.6,19.10-19.13,T-S04..07; tasks 20,28,29. Depends on 20,28,29.
  - Acceptance: actual output completes and accounts reconcile; submitted/planning/waiting/executing counts remain distinct; overload produces bounded backpressure, not a growing hidden task queue.
  - QA: `./gradlew :neoforge-1.21.1:federationBenchmark -Pprofile=mixed-small -PevidenceDir=.omo/evidence/task-30`; `./gradlew :neoforge-1.21.1:federationVerify -Pcases=mixed.reject-empty-orders,mixed.overload-backpressure -PevidenceDir=.omo/evidence/task-30-negative`.
  - Commit: proposed `test(perf): exercise native mixed factory workloads`.

- [ ] 31. Implement directional ME energy Policy binding and native cold-start recovery
  - Work: `energy/` using task 10 PASS contract. Attach through Hub/Bridge membership and local Endpoint supply boundary where appropriate. Real source/query/consume and native powered-state changes; no symmetric-overlay bypass, artificial startup energy, loss tax, Framework power cost or independent settlement store.
  - References: D8,T-F10..11,T-B02..05; A-energy; task 10. Depends on 10,14.
  - Acceptance: cold unpowered consumer can regain real supply while Grids/channels stay independent; reverse rule absent remains denied; stale route/supply cannot continue extraction; zero Fabric overhead remains true.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=energy.directional-policy,energy.cold-start,energy.ring-conservation,energy.reject-reverse,energy.disconnect-no-source -PevidenceDir=.omo/evidence/task-31`.
  - Commit: proposed `feat(energy): bind directional native ME supply`.

- [ ] 32. Implement scoped state snapshots, native transport metering and subscription lifecycle
  - Work: `observability/`, server snapshot/delta payloads with revisions and stable IDs, bounded event windows and per-Fabric subscriptions. Count native actual accepted resource amounts once, not simulation/path hops. Read native lock/task/send/return state; mark aggregate Lane returns, never exact untagged Batch completion. Avoid exposing all global Policy through one Fabric menu.
  - References: D16,17.1-17.7,17.10,T-R07,T-U06; tasks 14,24,31. Depends on 14,24,31.
  - Acceptance: repeat paths do not multiply flow; closed screens release listeners; invalid menu/context payload cannot mutate or subscribe outside scope; data-only updates do not trigger full topology work.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=observe.native-flow-once,observe.scoped-snapshot,observe.close-cleanup,observe.reject-stale-delta,observe.reject-cross-fabric-edit -PevidenceDir=.omo/evidence/task-32`.
  - Commit: proposed `feat(observability): expose bounded native state and flow`.

- [ ] 33. Complete Fabric graph, Provider mappings and Endpoint diagnostics in LDLib2
  - Work: shared XML/LSS and `client/` graph viewport with pan/zoom, physical/capability layers, grouped members, virtualized lists, filtered Pattern search/mapping and native state detail. Keep stable control IDs; data updates do not relayout static graph. Include provisional Bridge cable-extension and Hub/Provider/Endpoint face visuals in actual world captures. No Matrix, web clone or final visual redesign.
  - References: D17,T-U01..06,T-F12,T-B08; L; A-connect; tasks 3,15,32. Depends on 3,15,32.
  - Acceptance: actual client edits rules and mappings with server acknowledgment; long Chinese names/large quantities/GUI scales 2,3,4 readable; unauthorized-by-Policy or conflicting Claim explains reason; same Fabric entrances equivalent.
  - QA: `./gradlew :neoforge-1.21.1:federationUiTest -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict -PevidenceDir=.omo/evidence/task-33`.
  - Commit: proposed `feat(ui): complete scoped graph and processing diagnostics`.

- [ ] 34. Verify multi-client state consistency and bounded graph overhead
  - Work: actual dedicated-server-connected clients, independent screen states and conflicting edits; qualify graph counts/grouping/layout/payload cost with real workloads. Multi-client automation must exercise a real connection, not several isolated singleplayer clients. Test claim/Policy conflicts and context invalidation under Hub/Fabric changes.
  - References: D17.10,T-R07,T-U03,T-U05..07,T-S09; tasks 30,31,33. Depends on 30,31,33.
  - Acceptance: two clients share authoritative revision; one stale edit rejected; close/disconnect frees subscriptions; topology changes refresh scope; native simulation keeps running with GUI closed.
  - QA: `./gradlew :neoforge-1.21.1:federationUiTest -Pcases=multiclient.shared-server,multiclient.conflicting-edit,multiclient.fabric-split,multiclient.disconnect-cleanup -PevidenceDir=.omo/evidence/task-34`; `./gradlew :neoforge-1.21.1:federationBenchmark -Pprofile=ui-small -PevidenceDir=.omo/evidence/task-34-perf`.
  - Commit: proposed `test(ui): qualify dedicated-server multi-client behavior`.

- [ ] 35. Audit production/dev separation, payload bounds and runtime-side loading
  - Work: separate scenario/debug registrations, test machine resources and instrumentation from release artifact. Enforce server-thread scheduling, menu/context/revision/payload validation, resource quantity limits and absent-addon handling. No owner/team ACL added. Dev commands cannot accidentally ship enabled.
  - References: D16.4,17.9,18.1,T-U07..08,T-V01..02; tasks 1-3,33. Depends on 33.
  - Acceptance: production dedicated server starts without client class loads; final JAR contains no dev scenario/test block registration; malformed/oversized/out-of-context payloads rejected without world mutation.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=release.server-side-load,release.no-test-content,release.optional-absent,packets.reject-malformed,packets.reject-out-of-context -PevidenceDir=.omo/evidence/task-35`.
  - Commit: proposed `test(runtime): enforce production boundaries and safe payloads`.

- [ ] 36. Build a pinned actual-mod compatibility matrix and differential factory scenes
  - Work: `docs/compatibility/matrix.md` plus isolated dependency profiles covering actual storage, item/fluid logistics, native Provider-extension addon, storable FE addon and a compatible GregTech-style or other technology production line. Select by published MC1.21.1 NeoForge artifact and source-demonstrated capability path; record checksums/licenses before use. Every enabled combination gets native and Federation layouts with identical backends. Do not bundle optional mods or claim arbitrary ecosystem support.
  - References: D1.4,18.5,19.8-19.9,T-C01..08; tasks 2,25,31. Depends on 2,25,31.
  - Acceptance: each claimed combination has actual runtime evidence and preserved native hook trace; incompatible or unavailable combination explicitly unsupported/BLOCKED, never replaced with a mock. No license assumptions copied from researcher prose.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=compat.native-differential,compat.provider-hooks,compat.real-tech-line,compat.optional-absent,compat.reject-unsupported -PevidenceDir=.omo/evidence/task-36`.
  - Commit: proposed `test(compat): qualify pinned native ecosystem paths`.

- [ ] 37. Execute small/late/ultra three-layout benchmarks and fix measured regressions
  - Work: generate comparable single-big-grid, native-subnet and Federation layouts; run D T-S01..10 with fixed profiles, same CPUs/energy/loaded chunks/input replay where expressible. Correct measured bottlenecks with focused changes only and rerun affected native correctness/call-trace cases. Report unsupported native topology comparisons as non-comparable, not speedup. Freeze the reference timing medians and the >10% regression thresholds only on a quiesced baseline machine, recording CPU model, core count, JVM and observed load with each median.
  - References: D19.9-19.13; frozen task 20 correctness/resource budgets; tasks 24,30,36. Depends on 20,24,30,36.
  - Acceptance: raw metrics + resource accounts + repetitions + profiles saved; support scale based on observed results. No guaranteed 512-Grid TPS claim. Any budget adjustment includes reason and before/after evidence rather than quietly making a failing run pass. Timing medians are frozen only from repeated measurements whose recorded environment matches; a median from a shared or unpinned machine is not acceptable.
  - QA: `./gradlew :neoforge-1.21.1:federationBenchmark -Pprofile=small,late,ultra -PevidenceDir=.omo/evidence/task-37`; `./gradlew :neoforge-1.21.1:federationVerify -Pcases=perf.resource-integrity,perf.reject-incomparable-speedup,perf.reject-budget-regression -PevidenceDir=.omo/evidence/task-37-checks`.
  - Commit: proposed `perf: validate factory scale and address measured regressions`.

- [ ] 38. Run long-soak churn, restart and uncertainty-boundary qualification
  - Work: actual two-hour mixed load with periodic Fabric split/rejoin, bridge/hub removal, Policy toggles, subscription churn, client connects and native shutdown/restart; isolated fault runs kill only task-owned processes at recorded boundaries. Track listeners/cache retention, native resource owners and pending native work, not custom work queues.
  - References: D15-16,19.6,19.13,T-S08..09,T-G08,T-G12; tasks 34,37. Depends on 34,37.
  - Acceptance: below-capacity backlog remains bounded; cleanup returns to explained baseline; crash uncertainty never causes automatic whole-batch replay; restart retains inactive Policy and native state. Native crash durability limits explicitly reported, not claimed exactly-once.
  - QA: `./gradlew :neoforge-1.21.1:federationBenchmark -Pprofile=soak-2h -PevidenceDir=.omo/evidence/task-38`; `./gradlew :neoforge-1.21.1:federationVerify -Pcases=recovery.normal-restart,recovery.injected-interruption,recovery.reject-uncertain-replay -PevidenceDir=.omo/evidence/task-38-faults`.
  - Commit: proposed `test(recovery): qualify long-running native lifecycle`.

- [ ] 39. Verify final JAR provenance, supported tuple and reproducible clean-environment build
  - Work: finalize native hook/version compatibility manifest, dependency verification, production metadata, license notices, resource packaging and checksum manifest. Rebuild in a new task-owned directory/cache, not by cleaning the user's worktree. Launch the same final JAR on selected server and actual client; no separate compilation misrepresented as same artifact. Add CI for quick correctness and optional scheduled/full qualification; no issue/PR creation.
  - References: D18.5,18.8,19.15,T-V01..05,T-U08; K; tasks 35,36. Depends on 35,36.
  - Acceptance: locked clean build, exact MC1.21.1 metadata and explicit supported dependency combination; same JAR hash used for runtime smoke; no dev/test content; no unsupported version range expansion.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=artifact.clean-build,artifact.same-jar-client-server,artifact.license-manifest,artifact.reject-wrong-tuple,artifact.no-extra-target -PevidenceDir=.omo/evidence/task-39`.
  - Commit: proposed `build: qualify reproducible prototype artifacts`.

- [ ] 40. Publish local documentation, evidence coverage and honest prototype support boundaries
  - Work: update `README.md`, `docs/testing/`, `docs/compatibility/`, `docs/benchmarks/` and `docs/architecture/` with actual commands, build/dependency tuple, native delegation map, setup layouts, all clarified port semantics, Policy lifecycle, diagnostics, measured scale and known native/third-party limits. Provide `docs/acceptance-matrix.md` mapping every D/new case to current evidence. Register the final `audit.*` and `final.*` suites named below by composing the implemented scenario fixtures and evidence validators, including their deliberate negative checks; final reviewers still independently inspect artifacts and behavior. Record formal art/balance/extra platforms as deferred; do not edit DESIGN or AGENTS without explicit authorization.
  - References: full D, K, plan Must-have/NOT-have; tasks 37-39. Depends on 37,38,39.
  - Acceptance: all implemented claims point to reproducible current evidence; missing/blocked gates cannot be described as supported; documentation explains that provisional visuals and test fixtures are not finished assets/actual mod compatibility.
  - QA: `./gradlew :neoforge-1.21.1:federationVerify -Pcases=docs.coverage,docs.commands,docs.reject-unsupported-claims,docs.reject-stale-evidence -PevidenceDir=.omo/evidence/task-40`.
  - Commit: proposed `docs: document verified Federation prototype and evidence`.

## Final verification wave

Run independently in parallel only after every implementation task and feasibility gate required for full scope passes. Each verifier must use current files/artifacts, not worker self-report. All four must approve; surface results and wait for user approval before declaring implementation complete. These are future implementation verifications, not reviews already performed on this plan.

- [ ] F1. Plan compliance audit
  - Read D, latest-owner overrides and all 40 tasks. Check `docs/acceptance-matrix.md`, artifact identity and command results. Run `./gradlew :neoforge-1.21.1:federationVerify -Pcases=audit.requirements,audit.reject-missing-gate -PevidenceDir=.omo/evidence/final-F1`. Verify all six capabilities, real native call/state ownership, sparse persistent Policy and no cross-boundary Grid merge. Negative fixture must detect a missing gate. Evidence: requirement verdict JSON plus citations. No commit.
- [ ] F2. Code quality review
  - Inspect native-hook scope, mutable state authority, side/thread isolation, lifecycle cleanup, bounded work, licenses and dependency locks; run `./gradlew :neoforge-1.21.1:check :neoforge-1.21.1:build --dependency-verification=strict` and `./gradlew :neoforge-1.21.1:federationVerify -Pcases=audit.native-hooks,audit.reject-dual-authority -PevidenceDir=.omo/evidence/final-F2`. Reject copied Provider/transaction engines even if integration tests pass. Evidence: review report and current command logs. No commit.
- [ ] F3. Real manual QA
  - Agent operates the actual client through the harness: build direct Bridge and mixed Hub layouts, map one Pattern to three endpoints, block one lane, change Policy, unplug/replug and inspect native state. Run `./gradlew :neoforge-1.21.1:federationUiTest -Pcases=final.native-workflow,final.hub-six-faces,final.bridge-invalid,final.policy-reconnect,final.chinese-scales -PevidenceDir=.omo/evidence/final-F3`. Include actual server/client synchronization and screenshots, plus current benchmark report spot-check. No human clicking; no static mock images. No commit.
- [ ] F4. Scope fidelity
  - Review repository diff and final JAR against exclusions and user clarifications. Run `./gradlew :neoforge-1.21.1:federationVerify -Pcases=audit.scope,audit.reject-prohibited-content -PevidenceDir=.omo/evidence/final-F4`. Check no Matrix/extra adapter/anchor, Bridge-to-Federation mode, shared Hub ME node, ACL, chunk tickets, independent recovery engine, unsupported versions or automatic publication. Negative test must recognize deliberately marked prohibited fixture, not add prohibited production code. No commit.

## Commit strategy

- The task lines define reviewable atomic boundaries only. They do not authorize commits, pushes, issues or PRs in this planning session. An execution worker follows explicit user permissions and repository rules.
- Product implementation and regression tests belong in the same proposed commit. Keep unrelated files, design edits and local tool state out.
- Use read-only git status/diff inspection before any future commit; never `git clean`, force reset or overwrite the user's untracked design. Load git-master for git operations and secret-guard before a future authorized commit.
- Do not publish artifacts or open issues/PRs automatically. Do not use start-work `--make-pr` or `--ship` under current project rules.

## Success criteria

1. All six prototype capabilities operate within the clarified topology; Bridge/Hub preserve distinct native domains, Provider/Endpoint expose the approved faces, and native services remain execution authority.
2. Global Policy persists through unrelated topology changes and new connections; missing/off rules deny access, duplicates do not multiply ability, and chain access does not forge direct activation.
3. All required runtime gates and 40 task criteria pass; no BLOCKED gate is hidden by a stub, unsupported claim, custom engine or reduced scope.
4. Native reference/call-trace tests cover actual inputs, independent locks, correct return contexts, cancellation, unloading, normal restart and honest crash limitations without speculative compensation.
5. Actual Minecraft UI is repeatably editable, operable and diagnosable; dedicated server/client class and state isolation pass; dev tooling absent from production artifact.
6. Compatibility is pinned and experimentally bounded. Small/late/ultra and soak reports retain actual resource accounting, hardware/configuration differences and measured bottlenecks; supported scale never exceeds evidence.
7. Final F1-F4 approval and user acceptance precede completion claims. Implementation starts only in a separate user-started worker session.

Planning tooling note: saved through apply_patch because no parent command-execution tool is available. The prescribed scaffold script was read but not executed. Read-only Metis integrity review (session `ses_f69531125ffej6tJoUgCMtuY9I`) reports PASS for eight canonical headers, 40 sequential implementation tasks, four final tasks, section placement, exact dependency inverses and no cycles; the identified bootstrap ownership issue was corrected and rechecked. All product builds and tests remain unexecuted. High-accuracy Momus/Oracle review was not requested and has not been performed.
