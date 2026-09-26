# Task 36 Actual-Mod Compatibility Matrix

- Compatibility is row-specific. Artifact availability and source-demonstrated hooks do not imply runtime support.
- Applied Flux 1.21-2.1.4 is QUALIFIED on its pinned tuple: the real `FE_CELL_256M` backend preserves `appflux:flux`
  through native and Federation storage, with 8192 inserted, 2048 extracted, and 6144 remaining.
- Functional Storage 1.21.1-1.3.3 with Titanium 1.21-4.0.12 is QUALIFIED for the pinned real-drawer callback scene:
  native and Federation layouts each insert 16, extract 4, and leave 12 through the same AE2 Storage Bus mount.
- Pretty Pipes 1.21.1 with Pretty Pipes Fluids 1.21.1-3.1.0 is QUALIFIED for the pinned near-origin item/fluid scenes:
  real transport delivers in both layouts; native and Federation extract 4 ingots and 250 mB respectively.
- GTCEu 1.21.1-7.0.2 is BLOCKED_STARTUP_CLIENT_CLASS_LOAD on the pinned dedicated-server tuple; mod construction aborts
  before any machine recipe scene despite a zero nested Gradle exit.
- Recursive AE2 Pattern Provider 1.0.8 remains BLOCKED_LICENSE because its published source metadata is All Rights
  Reserved. No artifact was used and no mock replaced it.
- Compatibility source and dependency sets are opt-in and excluded from default runtime and production archives.

## 2026-09-23 independent runtime correction

- Functional Storage's pinned 1.3.3 artifact is Modrinth version `qyocTQUb` (SHA-256
  `974a1b0e45e98a9769e84fdfe4d7e3eac3de3936ed7a9baf009026ebee98fc12`). The earlier version ID
  `jH3wVEds` has a 1.3.3 filename but is published as 1.3.4 and hashes to
  `76a8c3272a78783a2bd8877fd85543c363894ec94bdafdd52632413a4c59134a`.
- A real Functional Storage + Titanium isolated GameTest reaches an actual `DrawerTile`/AE2 Storage Bus attempt, but its
  native network identity becomes unsettled after part attachment; this is unfinished fixture work, not addon rejection.
- Pretty Pipes + Pretty Pipes Fluids boot together and the ordinary harness GameTest passes, but no connected logistics
  differential executes. Startup-only must remain UNQUALIFIED.
- GTCEu 7.0.2 with AE2 19.2.17 and NeoForge 21.1.250 reaches mod construction, then fails in
  `CommonInit.init(CommonInit.java:177)` with a dedicated-server `ClientLevel` class-load exception. The Gradle nested
  launcher returns success despite the FML crash, so inspect the child log rather than exit code alone. The JAR embeds
  pinned Registrate, LDLib, and Configuration dependencies; their nested hashes are in `docs/compatibility/matrix.md`.
- The inherited Task 36 producer referenced nonexistent `runCompatibilityGameTestServer`; the real isolated FE task is
  `runAppfluxGameTestServer`. With that corrected, exact QA reaches the valid FE child and then fails closed because
  storage, two logistics, and GT children have not been integrated. Old completed-looking evidence now fails consumption
  specifically at the missing Functional Storage child.
- Task 35 five-case regression and strict check/build/archive pass. Task 31's `energy.directional-policy` regression
  currently crashes when a 1.5e9-AE scene emits `NANO_AE` greater than `ObservationLimits.MAX_RESOURCE_AMOUNT`; this
  predates the Task 36 scene and remains unresolved without weakening the Task 35 packet bound.

## 2026-09-23 Functional Storage differential repair

- The native bus needs the settled provider `NetworkId` seeded into its `gn` node and an actual cable-to-bus connection;
  otherwise joining a previously settled Grid produces `AMBIGUOUS_SPLIT`. Capture the ID before part placement.
- `StorageBusPart.getInternalHandler()` returns the adjacent composite, not the mounted `StorageBusInventory` wrapper.
  Query the real `IStorageProvider.mountInventories` callback for the exact native delegate and compare by object identity
  to the qualified Federation source domain, including the native bus owner node.
- Set the native Storage Bus priority to 100 to deterministically win against the fixture's own ME Chest. A real item
  capability on `DrawerTile` then reports 16 inserted, 4 extracted, 12 remaining in both layouts. A testmod Mixin on the
  pinned `IStorageMounts.mount` invocation independently logs the exact bus and mounted storage identities.

## 2026-09-23 replay findings

- Independently hashed Applied Flux, Glodium, GuideME, Functional Storage, Titanium, Pretty Pipes, Pretty Pipes Fluids,
  GTCEu, JGraphT and all three GTCEu embedded JarJar archives. Their hashes matched the selected pins.
- The connected Pretty Pipes item scene passed once, then failed on repeated launches: the real `PipeNetwork` extracted
  16 iron ingots but its `PipeItem` remained at the source after both 120 and 240 GameTest ticks. Reducing Storage Bus
  mount receipt logging did not restore reliable delivery. A one-off pass is not qualification; the fluid scene remains
  unbuilt. The five-case producer remains fail-closed on missing item/fluid children.
- A new isolated GTCEu 7.0.2 dedicated-server launch reproduced the `CommonInit.init(CommonInit.java:177)`
  `ClientLevel` invalid-dist exception with AE2 19.2.17 and NeoForge 21.1.250. Gradle misleadingly exited zero while
  FML aborted; no recipe scene executed and no five-case consumer result exists.
- The adversarial self-test now authenticates its source report before mutation. Feeding it the old partial result fails
  at the missing Functional Storage child rather than misleadingly reporting an accepted adversarial probe.

## 2026-09-23 Task 31 and fluid differential continuation

- `FlowState` previously applied the 9e15 item/fluid cap to internal `NANO_AE`. The native Task 31 directional proof accepts
  1,000,000,250 AE, represented exactly as 1,000,000,250,000,000,000 nano-AE. A failing-first meter test reproduced the
  rejection. A separate 9e18 nano-AE cap now admits the observed transfer without changing the item/fluid cap; focused
  meter/accounting tests and the canonical five-case Task 31 verifier passed. The latter evidence is under
  `.omo/evidence/task-31-energy-observation/attempt-20260923T045856005Z`.
- An opt-in `compatPrettyFluids` GameTest now uses real Pretty Pipes Fluids pipes, native AE2 Sky Stone Tank fluid
  capabilities, and a native AE2 Storage Bus mounted into the Federation source domain. Two isolated launches transferred
  1,000 mB in each layout and extracted 250 mB through the same native owner/delegate, leaving 750 mB; two other launches
  failed before either extraction because the in-flight `FluidPipeItem` stayed at its initial source coordinate.
- The failing fluid run at world X=4,264,128 retained `sourcePipeItems=1`, `destinationPipeItems=0`, `onTheWay=1`,
  `areaLoaded=true` after 120 ticks. Vanilla GameTestServer randomly places scenes in approximately ±15m X/Z; pinned
  Pretty Pipes uses `float` world positions and default 0.05-block/tick speed. Float granularity is a strong likely cause
  for this position-dependent native movement stall, but no runtime toggle has confirmed it yet. Preserve the test and
  leave logistics UNQUALIFIED. No Task 36 five-case result was produced.
- The exact Task 36 five-case producer was rerun with strict dependencies and rejected FE/storage-only children at
  `gradle/federation-qa.gradle:6656`: Pretty Pipes item/fluid and GTCEu child executions are still missing. Its attempt
  is under `.omo/evidence/task-36-fluid-continuation/attempt-20260923T052202631Z`, without a canonical result.

## 2026-09-23 near-origin real pipe qualification

- The baseline item replay at world X=2,329,418 left the same 16-ingot `PipeItem` at the source after 120 ticks;
  its source chest was empty and `areaLoaded=true`. Pinned bytecode stores x/y/z as floats, limits each update step to
  0.25, and calls `updateInPipe` from the real pipe block entity ticker. Large world-coordinate float granularity can
  erase each increment. A testmod-only GameTest placement Mixin chooses the origin for the two pipe cases; a near-origin
  assertion rejects an inactive hook. Three fresh isolated item and three fluid runs each passed their two 120-tick flows.
- The exact five-case producer now launches all four successful actual-mod children and a real GTCEu dedicated-server
  attempt. GT's zero Gradle exit is classified by its actual invalid-dist FML abort and a pinned-artifact/tuple receipt;
  no GT recipe or machine pass is claimed. The independent Storage Bus mount trace must match the same delegate and owner
  used by each physical differential. Its final current-source evidence path is recorded after documentation binding.
- Current-source schema-v3 result: `.omo/evidence/task-36/attempt-20260923T070721501Z/result.json`.
  Persisted consumer and nine fully rebound adversarial probes passed. Exact Task 31 and Task 35 five-case regression
  producers passed, as did strict `check build sourcesJar verifySharedJarContent` and all changed Java diagnostics.
