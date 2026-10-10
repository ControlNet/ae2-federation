# UselessMod compatibility (checked 2026-10-08)

UselessMod (`useless_mod` 1.21.1-2.4.5.10, MIT, GitHub SorrowMist/UselessMod) needs AE2 [19.2.17,20); everything
else is optional. It is pinned in `tests/compat/mods.json`; profiles `useless-mod` (alone) and `useless-mod-addons`
(all of `addons-all` plus Advanced AE, whose classes UselessMod also mixes into).

## What it touches in AE2

Mixins (main config, `required=false`): `CraftingCpuLogic`, `ExecutingCraftingJob`, `CraftingService` (Smart
Doubling), `CraftingTreeProcess`, `PatternDetailsHelper`, `AEProcessingPattern`, `PatternEncodingLogic`,
`PathingCalculation`/`PathingService`, `GridNode`, plus Advanced AE, ExtendedAE and Botany Pots targets. Missing
optional targets only log mixin `ClassNotFoundException` warnings.

- Smart Doubling hands scaled patterns only to UselessMod's own providers. `CraftingCpuLogicMixin` wraps every
  `pushPattern`, including the consumer CPU's push of a projected pattern; no effect seen.
- `AeDeviceLinker` (a wand) links any in-world node to a wireless access point with `GridHelper.createConnection`,
  ignoring channels, rebuilt every 20 ticks. Linking blocks of two federated networks merges them into one grid, so
  Federation no longer sees two networks. This is the player's choice, not a bug of either mod.

## Results

- `useless-mod`: core + `useless-mod` groups, 21/21.
- `useless-mod-addons`: every `addons-all` group plus `advanced-ae` and `useless-mod`, 81/81 (79/79 before the
  `useless-mod` group was added); UselessMod's Advanced AE and ExtendedAE mixins apply there.
- `UselessModCompatGameTests.alloyFurnaceCrafting`: the Advanced Alloy Furnace (`ICraftingProvider` and
  `PatternContainer`, 108 pattern slots) holds the provider network's stick crafting pattern and runs it itself on a
  virtual crafting grid (`AdvancedAlloyFurnaceAeManager.pushCraftingPattern`), with no Molecular Assembler; the
  consumer's CPU orders through the projection and gets the exact output. `AddonCraftingScene.structure(..., ready)`
  serves providers that craft by themselves without being AE2 multiblocks.

## Guide example (2026-10-08, redone 2026-10-10)

The owner picked the Advanced Alloy Furnace. Pack `guide_useless_mod` (requires `useless_mod`), page
`examples/alloy-furnace.md` (position 71), scene `alloy_furnace.snbt`. Blockstate is `facing` (four horizontal) plus
`active`; the scene needs no block entity NBT and renders at the title screen (unlike Mekanism machines).

- First version: network A ordered from network B, the furnace alone behind one Bridge. Its test
  `alloyFurnaceWorkshop` (pattern taken out and put back) stays as a plain compat test.
- Since 2026-10-10 the page is a cross-domain crafting chain, "An Alloy Furnace Two Bridges Away": the workshop (furnace
  and a drive of its own stock), a trading post (cable only) that uses the workshop's Crafting with re-export and its
  Storage plain Enabled, and the base (CPU, terminal, storage, the only energy cell) with its own Bridge to the post.
  `alloyFurnaceTradingPost` backs it: the base's CPU orders 4 sticks, they return to the base's chest and nothing
  stays owed; the post sees the workshop's chest, the base does not (no Storage re-export); stepping the post's
  Crafting back to Enabled takes the recipe from the base but not the post, re-export brings it back and the base
  orders again. Mutation checked: a no-op step-back stalls at "Waiting for the recipe to leave the orderer"; plain
  Enabled at setup stalls at "Waiting for the furnace's recipe on the orderer".
- `BridgeChainFixture` (compattest) builds such chains: each network a line of coloured glass cable along z at y 4
  (different colours keep neighbouring lines apart), each Bridge on one line facing the parallel line beside it; it
  places one Bridge per tick, each once the one before has joined its domain (`joined()` pokes `onUpdateShape`), then
  switches ME power on per Bridge pair. Devices touching two lines would merge the networks, so place them on the
  outer side of a line. Neo ECO's `ecoWarehouseChain` uses it too.
- `guideSceneCables` in the `useless-mod` profiles checks the scene (mutation checked with a wrong channel count).

## UselessMod's own defects seen (upstream; not filed by us)

- `MePatternAssemblyBlock:113` and `AdvancedAlloyFurnaceBlock:341` reference ExtendedAE's `ContainerRenamer` without a
  loaded-mod guard (Quartz Cutting Knife use without ExtendedAE); `ChainGroupScreen` references JEI unguarded.

## Guide example candidates

No GuideME pages, Ponder scenes or structure files exist in UselessMod to copy from.

- Advanced Alloy Furnace as a remote pattern provider (built, see above): one block; the test gives it no FE, only
  the AE power the provider network shares. Its item/fluid/FE faces all default to DISABLED, so as an Endpoint
  machine a player must configure faces first.
- Ore Generator: an AE node without power that inserts `c:ores`/`c:raw_materials` items into its grid every 20 ticks;
  a storage-sharing example (ores appear on the other network).
- Omniversal Alloy Furnace multiblock (3 wide x 4 tall x 3 deep, `OmniversalAlloyFurnaceStructure.createEntries()`):
  core at the front centre of layer 0, `me_pattern_assembly` (the only AE node) and `omniversal_mold_hub` beside it,
  rings of eight same-tier coils on layers 1-2, casing cap on layer 3. Outputs go straight into the grid storage.
