# ECO - N1 on Neo ECO Prototype's C1 (planned 2026-10-23, built 2026-10-25)

Owner asked for an N1 controller on Neo ECO Prototype's L1 computation system (C1), registered only when
`neoecoprototype` is loaded, as N4/N6/N9 need Neo ECO AE. Decisions (owner, 2026-10-23):
- Same rule as N4/N6/N9: share every L1 part; only the N1 controller and the side face are ours. The side face holds
  1-4 of the EXISTING `eco_computation_interface` block (no L1-styled variant), casing elsewhere.
- Recipe: C1's own (crafting table, ABA/CDC/ABA: A L1 casing, B L1 threading core, C L1 parallel core) with the
  centre `ae2:calculation_processor` replaced by the Nexus Processor.

## C1 facts (Prototype 1.3.9 source, GPL-3.0; our mod is AGPL-3.0, so they combine)
- Controller `SimplifyComputationSystemBlock extends ECOComputationSystem`; block entity is eco's
  `ECOComputationSystemBlockEntity` with tier `SimplifyTier.L1` (an `IECOTier` enum; L1_REINFORCED etc. are member
  tiers). Prototype's mixin `ECOComputationSystemBlockEntityMixin` returns `SimplifyComputationDefinition.L1` from
  `getBuildDefinition` when `tier == SimplifyTier.L1`, so a subclass BE with that tier gets the L1 build plan.
- Layout mirrors eco L4: interface at (2,1,1) = back column, middle, on the interface side; lines grow WEST
  (transmitter middle row, drives above/below, threading core behind the transmitter, parallel cores above/below it),
  cooler at the transmitter line's end facing WEST, tail casings. Energized parallel core: only the cell directly
  behind the host; energized threading core: only the first threading cell.
- `SimplifyComputationClusterCalculator` keeps the whole check in a PRIVATE `verifyStructure`, and its shell check
  demands casing in the host column on the interface side, so N4's trick (override eco's protected verifyStructure)
  does not work.

## Upstream PR
reliqwq/NeoECOPrototype#11 (opened from ControlNet's fork, branch `computation-interface-face-hook`, 2026-10-23):
adds `protected Optional<Block> validateInterfaceFace(level, controllerPos, back, top, down, interfaceSide,
energizedCorePos)` (default = old shell column + matchedComputationInterface) plus GameTest
`computationInterfaceFaceIsOverridable`. Once released, N1's calculator subclasses
`SimplifyComputationClusterCalculator` and overrides only that method; returning our own block gives the plain host
face (`communicationInterface` false).
Merged into upstream main on 2026-10-23 as `e02f8bc` (owner ran 144/144 GameTests on the merged tree and fixed the
advancement guard that failed without AppliedFlux). The hook is NOT in 1.3.9; it ships in the next release. Until
then, compile against a local build of upstream main.
Distribution: Prototype is on Modrinth since 1.1.0 (https://modrinth.com/mod/neo-eco-prototype), project id
`3HO3APHb`; Modrinth Maven `https://api.modrinth.com/maven`, coordinate `maven.modrinth:3HO3APHb:<version>`
(1.3.9 jar sha1 b1aa3eefef664cc13948a942cef6a77b16fc4bf4). No sources jar; upstream offered to add one if needed.

## Building Prototype locally
Its build.gradle needs 18 jars in `libs/` plus `neoecobeta/neoecoae-21.2.9.jar`, never committed. Sources used:
compat cache (`~/.cache/ae2federation-compat`: AE2 19.2.17 as appliedenergistics2-19.2.17.jar, eco 21.2.9, MEGA,
Mekanism, AppMek, ae2jeiintegration, NAST pack's kubejs/rhino/omnicells, Jade 15.10.6 for jade-7428884), Modrinth
(LDLib2 2.2.40 project B1CBVXHX, JEI 19.57.0.445, EMI 1.1.24, Beyond Dimensions 0.7.30), GitHub (AE2 Pattern Disk
0.5.0), CurseForge cdn files/7478/960 (ExtendedAE 2.2.28). Registrate MC1.21.1-1.4.8 is on no Maven: take it from
eco 21.2.9's `META-INF/jarjar/`. `gradlew` lacks the exec bit: run `bash ./gradlew`.
GameTests: `bash ./gradlew runGameTestServer` runs 140 tests in ~1 min; `advancementtreeloadsandnamesrealitems`
fails without AppliedFlux at runtime (compileOnly there). One run stalled after 8 batches (the keep-alive symptom);
reruns finished.

## Built on Prototype 1.4.0 (2026-10-25)
Prototype 1.4.0 (Modrinth `3HO3APHb` version `YY1JqK65`, also the GitHub release; same sha512) ships the hook and
requires Neo ECO AE Extension 21.2.10 (`udZtKfzP` `kzNgvnUI`). Compat profiles moved on dev (e272c3dd, cherry-picked to
the feature branch); the feature build's `neoEcoApi` configuration compiles against both (verification-metadata has
their sha256s). N4/N6/N9 compiled and passed unchanged on 21.2.10.

Code (`automation.neoeco`):
- `NeoEcoSupport.prototypeAvailable()`: Neo ECO >= 21.2.1 and `neoecoprototype` >= 1.4.0; only then
  `PrototypeComputing.register` runs (it refers to Prototype classes).
- `PrototypeComputing`: block/item `eco_computation_system_l1` (N4's `EcoComputationSystemBlock`), block entity
  `EcoComputationSystemBlockEntity` with `SimplifyTier.L1`, calculator factory `PrototypeComputationClusterCalculator`;
  creative tab inserts it before N4.
- `PrototypeComputationClusterCalculator extends SimplifyComputationClusterCalculator`, overriding only
  `validateInterfaceFace`: N4's six side-face cells, 1-4 of our interfaces, casing (`casing()` = L1 casing) elsewhere,
  returns our interface block (so C1's GUI-interface face never applies).
- Build plan: since eco 21.2.10, `ECOComputationSystemBlockEntity.getBuildDefinition` resolves providers; Prototype
  answers tier L1 + COMPUTATION with its L1 plan, so N1's Structure Builder gets C1's plan and `EcoComputationBuild`
  swaps in our controller and interface (checked in game: interface at the side face's back middle, formed).
- C1's host block (`SimplifyComputationSystemBlock`) is not used: N1 keeps N4's block-state set, so no
  energized-core face swap and no L1 panel badge.

Numbers (Prototype 1.4.0 server config `l1_computation`, defaults): 24 CU per L1 parallel core, 2 threads
(= installations) per L1 threading core, 1,572,864 B per CE1; fixed: energized parallel core behind the host 1,024 CU,
energized threading core 16 threads, energized array 5,242,880 B. The `SimplifyTier` enum literals (16 / 1 / 1 MiB)
are NOT what runs - `getCPU*` read the config for L1.

Art (temporary): Prototype's author allowed us (2026-10-25) to modify Prototype's art as our in-development placeholder;
both mods share one artist. `tools/visual/build_eco_computation_placeholder.py` writes N1 as it writes N4: C1's
`computation_recolor` sheets with purple (hue 210-300) turned Federation green 130, the ME interface cell of the side
face painted over with the other column mirrored (lights too), L1's green tier lights kept; textures under
`textures/block/eco_computation/l1/`, models `controller_l1_formed[_mirrored]` keep Prototype's geometry as parents.
As with N4 vs C4, the change hardly shows: C1's front has no purple, and the side-face chip is under our interface.
Check: `build/eco-n1-check/n1-art-check.png`.

Guide: the N1 page lives in its own pack `guide_neoecoprototype_computing`, required mods `neoecoprototype`,
`neoecoae`, `neoecoprototype@1.4.0` (d3cfdbf0). `GuideExamplePacks` requirements may be `<mod ID>@<version>` (that
version or later, Maven ordering via `GuideExamplePacks.requirements`); keeping it out of `guide_neoecoprototype`
leaves the singularity-mint page for Prototype 1.3.x. `GuideSceneCables` skips packs without an `assets` directory.

Tests: compat group `neoecoprototype-computing` (`NeoEcoPrototypeComputingCompatGameTests`, in the neoecoprototype and
addons-all profiles): forms/links/runs, 1-4 interfaces, refuses both Prototype interfaces, capacity incl. energized
parts, recipe. `neoecoae-computing` gained `n1ExistsOnlyBesideNeoEcoPrototype` (runs in profiles with and without
Prototype). The L1 drive has no item-handler capability: insert arrays with a mock player's `useBlock`.
In-game check: `build/eco-n1-check/` (run.sh, scene.py, n1-check.png) in the feature worktree.
