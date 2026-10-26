# Recolouring the ECO - N4/N6/N9 computation multiblock (2026-10-20)

The user asked why the N series looks exactly like Neo ECO's ECO - C computation system. This note is research only;
nothing has shipped. Sources:
- Neo ECO tag 21.2.1 (paths relative to `src/main/`).
- The feature branch at origin `feature/function-system`.
- Preview shots in `build/eco-recolour-check/` of the feature worktree. That directory is gitignored.

## What is recoloured today

Only our controller is recoloured. `tools/visual/build_eco_computation_placeholder.py` turns hues 210-300 teal in four
controller textures: `screen_off`, `controller_formed`, `controller_formed_a` and `controller_formed_switch`. The
controller's formed tier lights (`controller_formed_light_a/b/c`) remain Neo ECO's, as does every other part of the
structure. Those parts are Neo ECO's own blocks: casing, transmitter, drive, threading and parallel cores, and the
cooling controller.

Our teal (#26A6BD, hue 189) almost equals Neo ECO's L4 tier colour:
- Tier `a` (L4) textures are cyan, hue 180-210.
- Tier `b` (L6) textures are red/orange, hue 0-30.
- Tier `c` (L9) textures are purple, hue 240-270.

The hue rule therefore changes almost nothing on an L4 structure.

In a formed structure, three things set the look. Measured with `purple_share.py` and seen in the shots:
1. Our controller's formed panel lights, `controller_formed_light_<tier>`, a 128-px atlas.
2. The coolant in the transmitter glass, `neoecoae:block/cryotheum_solution_still`. It is a composite child in
   `models/block/computation_transmitter_formed.json`.
3. The core tier lights (`core_side_*`, `*_core_light_*`).

The structural accents barely show once the structure is formed:
- the blue chips on `casing_side*` and `casing_back`;
- the indigo drive rims (`drive_north*`, `drive_inside_top`);
- `transmitter_side_*`;
- `cooling_controller/controller_north`.

## How Neo ECO renders the parts

- All parts use plain blockstate JSON models: `FORMED`, `HORIZONTAL_FACING`, the threading core's `WORKING`, and the
  cooling controller's `MIRRORED`. See `all/NEBlocks.java:996-1070` and `1495-1590`.
- Some models are composites with translucent glass and cutout coolant children: the transmitter and the cooling
  controller.
- There is no connected-texture loader.
- No part model has a `tintindex`.
- The only block colour handler is for item cells (`client/NeoECOAEClient.java:138`).
- The computation drive has a BER, `ECOComputationDriveRenderer` (`all/NEBlockEntities.java:388`). It draws the cell
  and cable models by the cell item, through `api/ECOComputationModels`.
- `ECODriveBakedModel` belongs to the storage drive, not the computation drive.
- The part models use 43 textures in total (`part_textures.py`).

## How the structure check matches parts

`multiblock/calculator/NEComputationClusterCalculator.verifyStructure` (lines 134-264):
- **By exact block identity:** the casing (lines 263 and 306-309), the transmitter (166), the drive (216 and 228) and
  the interface (157).
- **By class:** the parallel core, threading core and cooling controller (lines 317, 327 and 337). Each passes
  `instanceof` plus `tier.supportsComponentTier`.
- **Members:** `isValidBlockEntity` (line 271) requires an `NEBlockEntity` whose calculator is an
  `NEComputationClusterCalculator`.

## Options

**(a) Our own N part blocks**
- **Scope:** 12 blocks, items and recipes: casing, transmitter, drive, threading cores x3, parallel cores x3 and
  cooling controllers x3.
- **Structure check:** the cores and coolers could subclass and pass `instanceof`. The casing, transmitter and drive
  are identity checks, so `verifyStructure` would have to be rewritten, which means copying Neo ECO's logic. That
  clashes with "import, never copy".
- **Block entities:** each part needs its own BE types registered through `registerCalculatorFactory`. Our drive also
  needs its own drive BE and BER.
- **Auto-build:** Neo ECO's auto-build would place C parts.
- **Pro:** distinct items.
- **Con:** the most code and the most upkeep.

**(b) Client-only retexture of Neo ECO's parts inside formed structures whose controller is ours**
- **Server/sync:** our controller BE syncs its cluster bounds (`NECluster.getBoundsMin/Max`, `NECluster.java:31,36`)
  in its update tag. The client keeps a per-dimension set of N bounds and calls `LevelRenderer.setBlocksDirty` over
  them when they change.
- **Models, using public NeoForge 21.1.250 APIs and no mixin:**
  - `ModelEvent.RegisterAdditional` registers N copies of the part models, made by a generator script with textures
    swapped, as the controller already is.
  - `ModelEvent.ModifyBakingResult` wraps the baked models of the 12 part blocks.
  - The wrapper's `IBakedModelExtension.getModelData(BlockAndTintGetter, BlockPos, BlockState, ModelData)` flags
    positions inside N bounds.
  - `getQuads` and `getRenderTypes` then delegate to the N model.
- **Unchanged:** no new items or recipes; auto-build and the structure check stay as they are; any C part becomes N
  once formed under our controller.
- **Limits:**
  - Unformed parts and items still look like C.
  - Drive cells drawn by the BER keep Neo ECO's tier colours.
- **To verify:** that Sodium/Embeddium on NeoForge honour `getModelData(level, pos, …)` for blocks without a block
  entity. Test it in the Iris/Sodium client profiles. Ordinary quads are fine with shader packs.

**(c) Tinting:** impossible without editing the models, since they have no `tintindex`. A multiply tint also cannot
turn cyan or white into another hue cleanly. Rejected.

**(d) Overriding Neo ECO's textures with a resource pack:** this recolours the real C series too. Rejected; it was
used only for the preview below.

**(e) Our controller's panel lights only:**
- Our controller models would point at our own `controller_formed_light_<tier>` copies.
- **Pro:** zero new mechanism; it is the most visible single surface.
- **Con:** the rest of the structure stays Neo ECO's.

## Preview (`build/eco-recolour-check/compare.png`)

The previews used a throwaway resource pack and the L4 world from computing-budget-check:
1. today;
2. structural accents only, set to computing pink 320: hardly visible;
3. controller panel lights only, set to pink: clearly ours;
4. whole structure (tier `a`, coolant and accents) set to pink: unmistakably different.

Hue 320 is the Creative Computing Device's pink. The Automation Interface is green 130, and Federation teal 189. Teal
for the N series would look like Neo ECO's L4.

## Recommendation

- Ship (e) now with tooling only: extend `build_eco_computation_placeholder.py` to also copy and recolour
  `controller_formed_light_*`.
- Then build (b) for the parts.
- Keep (a) out unless the user wants distinct N part items.

Whichever option is chosen, the user or the artist must pick the N palette, and decide whether tiers keep Neo ECO's
a/b/c colours.
