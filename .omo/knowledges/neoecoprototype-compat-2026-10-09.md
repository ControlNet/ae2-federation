# Neo ECO Prototype compatibility (2026-10-09)

Neo ECO Prototype (github.com/reliqwq/NeoECOPrototype, GPL-3.0, GitHub releases only) is an L1 tier for Neo ECO AE
Extension. 1.3.2 needs neoecoae `[21.2.1-beta2,)` and LDLib2 `[2.2.40,)`, so profile `neoecoprototype` pins
neoecoae 21.2.1 (since 2026-10-10 the `neoecoae` and `addons-all` profiles run 21.2.1 too, and `addons-all` includes MEGA Cells and the prototype, 86/86; only `useless-mod-addons` stays on 21.2.0, keeping the requested-amount fallback tested). Result: 34/34 (core 19, neoecoae 5, neoecoprototype 10).

## Upstream bug found

1.3.2 crashes at item registration without MEGA Cells, with or without Federation (`--bare` run):
`SimplifySingularityCellItem.<clinit>` calls `AEItemKey.of(AEItems.SINGULARITY)` (and later `ae2:inscriber`) inside
its item supplier, and its `ae2` dependency has `ordering = "NONE"`, so its RegisterEvent handler can run before AE2's.
Its optional `megacells` entry has `ordering = "AFTER"`, and MEGA Cells loads after AE2, so with MEGA Cells present it
boots. The profile includes MEGA Cells. Reported upstream as reliqwq/NeoECOPrototype#6 (2026-10-10).
Fix PR reliqwq/NeoECOPrototype#7 (2026-10-10): only `ae2` ordering `NONE` -> `AFTER` in `neoforge.mods.toml`.
Verified by putting that toml into a copy of the 1.3.2 jar on a bare server (AE2, LDLib2, GuideME, eco 21.2.1): original
crashes 3/3, patched boots 4/4 without MEGA Cells and also with it. Once a release carries it, MEGA Cells can leave the
`neoecoprototype` profile.

1.3.3 (2026-10-10) carries it (`ae2` and `neoecoae` ordering `AFTER`, the singularity read lazily). Pinned that day;
MEGA Cells left the profile: bare boot 3/3, `neoecoprototype` 36/36, `addons-all` 90/90 (still with MEGA Cells, as a
player pack would have). Its release notes say an `ae2:inscriber` stack in the same report comes from Neo ECO's own
init reading AE2's press tag; it did not show in these runs.

## How the tests reuse Neo ECO's scenes

- The L1 definitions mirror eco's L4 layout (interface on the other hand), and the `Simplify*ClusterCalculator`s
  accept both hands, so `NeoEcoCompatGameTests` placements take a `Tier` (namespace + renames from the L4 names).
- L1 drives mount only L1 cells unless the server config `[l1_storage] additional_storage_cells` names others: use
  `simplify_item_storage_cell_1m` and `simplify_computation_cell_1m`. `getCellStack`/`setCellStack` exist as on eco's.
- `simplify_pattern_provider` is a `PatternProviderBlockEntity` (27 slots), `cable_pattern_provider` a
  `PatternProviderPart`; `simplify_stonecutting_assembler` is a `MolecularAssemblerBlockEntity` that passes AE2
  crafting patterns to super.
- `superconductive_interface` is an `IPassiveEnergyGenerator` (4000 AE/t). AE2 injects passive generation each tick
  into the grid's per-node buffer, so it powers other networks over the ME power rule with no energy cell anywhere.
- Trinity is left out: upstream marks it not implemented.

Neo ECO 21.2.1 includes the `getRequestedAmount` fix (tag is ahead of 76bfb358).

## Processor assembler and the Nexus chain (2026-10-10)

`ProcessorAssemblerRecipes.derived` (source, 1.3.2 to master 6bbbdf5) turns every AE2 Inscriber `press`-mode recipe
with all three slots filled into an assembler recipe. Each single-item input is replaced by the middle input of the
`inscribe` recipe that makes it ("printed part -> material inscribed into it"). `inscribe` outputs, i.e. printed
circuits, never become assembler outputs: AE2's own printed circuits are not offered either. Checked with a temporary
reflection probe in the `neoecoprototype` profile (not committed):

- `ae2federation:nexus_processor` <- redstone, ender pearl, silicon (the circuit unfolds to the pearl). Expected.
- `ae2federation:nexus_processor_press` <- ender pearl, iron block, iron block. AE2's press copy recipes (press on top,
  iron block in the middle, `inscribe`) count as "the press is inscribed from an iron block", so both AE2 presses unfold
  to iron blocks. MEGA's `accumulation_processor_press` gets singularity + 2 iron blocks the same way. Upstream side
  effect; a pack can refuse it with the server config `disabled_processor_recipes = ["ae2federation:nexus_processor_press"]`.
  An upstream fix would skip `inscribe` recipes whose top input is their own result (the copies) when building the map.

## 1.3.9 and the singularity mint guide page (2026-10-23)

1.3.9 needs Neo ECO `[21.2.9,)`, so dev's `neoecoae`, `neoecoprototype` and `addons-all` profiles moved to
Neo ECO 21.2.9 together with it (`useless-mod-addons` stays on 21.2.0). Upstream changes that touched our tests:

- **The L1 Processor Assembler takes processor recipes only.** `SimplifyStonecuttingAssemblerBlockEntity.pushPattern`
  wraps a pattern whose output matches a `ProcessorAssemblerRecipe` (built in, or derived from the inscriber) in a
  `ProcessorAssemblyPattern` and refuses everything else, logging "这台机器只跑处理器装配配方，普通分子装配样板请交给分子装配室";
  upstream's own GameTest `assemblerRefusesOrdinaryCraftingPatterns` pins it. `l1ProcessorAssemblerCrafting` now pushes
  a processing pattern (silicon + redstone + gold ingot -> logic processor); the assembler returns the processor to
  the provider itself, as a Molecular Assembler does.
- **The L1 drive is not Neo ECO's `ECODriveBlockEntity`.** `SimplifyDriveBlockEntity` has `insertCell(ItemStack)` and
  `removeCell()`, which its players use; calling `setCellStack(EMPTY)` by reflection left the mounted cell in place
  (the network kept listing its contents). `NeoEcoCompatGameTests.swapCell` uses `removeCell`/`insertCell` when they
  exist.
- **L1 Singularity Cell** (`simplify_singularity_cell`): the L1 drive mounts it with no config entry; its stock is
  computed from the world tick it was first read at, 120 per 600 ticks by default. Taking it out removes the
  singularities from every network that reads the mint; putting the same stack back returns them.

Guide example `examples/singularity-mint.md` (pack `guide_neoecoprototype`, needs neoecoprototype and neoecoae): a mint
network with the smallest L1 storage system holding the singularity cell, powered only by an Energized
Superconductive Interface; two districts use the mint's storage through Storage rules (no Crafting rule) and run on
its power over ME power. Compat test `singularityMint` (scale_36_empty, `RouterCraftingScene.usingStorageOf`) builds it,
waits for the first batch, takes 64 on district A, checks district B sees the rest, then takes the cell out and puts
it back; mutation-checked (leaving the cell in fails at "Waiting for the singularities to leave district A").
The scene is Prototype's own `ae2guide/scenes/l1_storage_min.nbt` turned half a turn (drives face north for the
yaw-195 camera), read with a stdlib NBT reader; `guidescenecables` matched AE2's channels on the first try.
Guide screenshots ran in a portablemc production client (own work dir under `build/prototype-guide-check/`, own Xvfb)
because `run-guide` was in use by another session's client; `-Dguideme.showOnStartup=ae2:guide!<page>` works in
production GuideME 21.1.19.
