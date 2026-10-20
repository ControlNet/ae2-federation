# NeoECO computation system, read for a survival Federation computing device (2026-10-20)

Source: DancingSnow0517/NeoECOAEExtension, tag 21.2.1 (the version our compat profile runs; 21.2.8 is the latest tag).
NeoECO is GPL-3.0; this mod is AGPL-3.0-only (GPLv3 section 13 allows combining). The user's direction: import
NeoECO's classes from its jar at run time, only when the mod is loaded; copy none of its code. The artist of both mods
allowed recolouring NeoECO's textures (AE purple -> Federation teal) as placeholders.

## What the computation system is

A multiblock AE2 crafting CPU, in tiers L4, L6 and L9. The parts are:
- the controller `computation_system_<tier>` (`ECOComputationSystem` / `ECOComputationSystemBlockEntity`);
- casing `computation_casing`;
- an ME interface `computation_interface`;
- a line of `computation_transmitter`;
- a line of `computation_threading_core_<tier>` behind it, with `computation_parallel_core_<tier>` above and below;
- `computation_drive` lines above and below that hold `eco_computation_cell_<tier>` items;
- a cooling controller;
- an optional network switch (normal x2, high-energy x8), which pools several systems through
  `NELogicalNetworkManager`.

The structure is checked by `NEComputationClusterCalculator.verifyStructure`. Threading and parallel cores must match
the controller's tier. The maximum length is `NEConfig.computationSystemMaxLength`.

## Numbers per tier (`api/ECOTier`)

| Tier | Threads per threading core | Accelerators per parallel core | Cell bytes |
| ---- | -------------------------- | ------------------------------ | ---------- |
| L4   | 4                          | 64                             | 2^26       |
| L6   | 16                         | 192                            | 2^28       |
| L9   | 64                         | 576                            | 2^30       |

The cluster sums them:
- `maxThreads`: one AE2 crafting job per thread;
- `getCPUAccelerators`: parallelism;
- `getTotalStorage`: the bytes a job reserves.

## Extension points found

- `ECOComputationSystemBlockEntity` is public and not final. Its public constructor takes
  `(BlockEntityType, BlockPos, BlockState, IECOTier)` and passes `NEComputationClusterCalculator::new` to `NEBlockEntity`.
- The calculator finds the controller by `ECOComputationSystemBlockEntity.class`, through
  `findUniqueController` / `findCandidateController`. A subclass placed as controller therefore forms the same
  structure from NeoECO's own other blocks.
- `CraftingServiceMixin` publishes CPUs by `instanceof ECOComputationSystemBlockEntity` and
  `getCluster() instanceof NEComputationCluster`. A subclass controller would also become an AE2 crafting CPU unless it
  is kept out. How to keep it out is not checked yet; candidates are an `IGridNode` the mixin never sees, or no
  grid node on the controller.
- Blocks are registered with Registrate (`NEBlocks`, `NEBlockEntities`); `NEBlocks.createComputationSystem` builds the
  controllers.

## Spike results (feature/function-system, 2026-10-20)

Package `automation/neoeco`. Only `NeoEcoSupport` is touched when NeoECO is absent; it checks neoecoae >= 21.2.1.
- `NeoEcoComputing` registers `eco_computation_system_<l4|l6|l9>`. The block subclasses `ECOComputationSystem`, and
  the block entity subclasses `ECOComputationSystemBlockEntity`. Each type gets its own calculator through
  `NEBlockEntity.registerCalculatorFactory` (new in 21.2.1).
- Members route `verifyInternalStructure` and `checkMultiblockScale` to the controller's calculator. `createCluster`
  is not routed, so the cluster cannot be swapped.
- `EcoComputationClusterCalculator`:
  - makes the ME interface cell a casing column (user option A);
  - rejects any `NENetworkSwitchBlock` at either switch position (`NENetworkSwitchUtil.switchPosition`, both mirror
    variants). It checks before calling super, because super already sets the switch and controller states.
- The parts still form their own AE2 grid of the structure only, about 30 nodes. That grid advertises one
  `ECOCraftingCPU` with 0 bytes. Compat test: ME cables placed against the parts' outer faces do not join that grid. So
  no ME network reaches it.
- No `IN_WORLD_GRID_NODE_HOST` is registered for our controller. Federation cable links its front face only
  (`CableLinkedBlock`, while formed).
- An L9 controller accepts lower-tier parts (`IECOTier.supportsComponentTier` is `>=`). The switch test uses this: an
  L9 controller over the L4 layout.
- Right-clicking the controller with a cell item fills empty drives, up to 8 per click (`ECOComputationSystem.useItemOn`).
  Our `useWithoutItem` opens the automation screen instead, so with an empty hand the player gets our screen.
- Compat tests: `federationComputationSystem{LinksAtItsFront,RefusesAnMeInterface,RefusesANetworkSwitch}`. Turning
  off the switch check fails the third test.
