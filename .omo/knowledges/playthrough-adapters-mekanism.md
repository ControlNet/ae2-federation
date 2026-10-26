# Phase 3b Mekanism playthrough adapters

- Adapters: `metallurgic-infuser` (`PlaythroughMetallurgicInfuserAdapter`), and `enrichment-chamber` / `crusher`
  (one record, `PlaythroughMekanismMachineAdapter`). All three are in the default `machines=all` list.
- The compat source set does not compile against Mekanism. Recipes are read through Mekanism's own classes by
  reflection (`PlaythroughMekanismMachineAdapter.call`, which matches public methods by name and argument types).
  Machines are configured through the existing `MekanismSetup` (Configurator side config, cube fill).
  - Infusing (Mekanism 10.7, 1.21.1): `getItemInput()` (ItemStackIngredient, `ingredient()` is a NeoForge
    SizedIngredient, `getNeededAmount(stack)`), `getChemicalInput()` (ChemicalStackIngredient: `amount()`,
    `testType(ChemicalStack)`), `perTickUsage()`, `getOutput(ItemStack, ChemicalStack)`.
  - Item-to-item: `getInput()`, `getOutput(ItemStack)`.
  - Conversion (`mekanism:chemical_conversion`): `getInput()`, `getOutput(ItemStack)` gives a ChemicalStack.
  - Operation length: the public static `BASE_TICKS_REQUIRED` on the block entity class (200 for all three).
- Infuse types are chemicals in 10.7. Infusion tags such as `mekanism:redstone` are chemical tags. The infusion
  slot converts items one at a time via `chemical_conversion`. Dust gives 10 and enriched items give 80, so the
  basic circuit (20 redstone) takes 2 redstone dust and reinforced alloy (20 diamond) takes 2 diamond dust.
  Atomic alloy (40 refined obsidian) takes 4 refined obsidian dust. No NAST infusing recipe uses a per-tick cost.
- Site: a filled `mekanism:creative_energy_cube` at `machine.west()` ejects EAST, and the machine's ENERGY INPUT is
  WEST. ITEM INPUT is UP (top bus filtered to the main ingredient), EXTRA is EAST (side bus filtered to the infuse
  item) and OUTPUT is DOWN (the site hopper pulls the product). A newly placed Mekanism machine has every side
  disabled, so every side the adapter uses must be configured.
- Idle check: all `getInventorySlots(null)` stacks empty, all `getChemicalTanks(null)` empty,
  `getOperatingTicks() == 0`.
- Selection safety: reject when another recipe of the type `testType`-accepts the same concrete input (and the
  same chemical). Mekanism runs the first match from its input cache.
- `mekanism:reaction` is not adapted. Every recipe needs oxygen, hydrogen, ethene or plutonium, which only other
  chemical machines produce. Applied Mekanistics chemical keys exist in NAST, but there is no raw supply to justify.
  `mekanism:hdpe_pellet` has no Phase 0 producer, so it is supplied, and `hdpe_sheet` runs on the Enrichment Chamber.
- Huge target: `compactmekanismmachines:compressed_wind_generator_x532480` becomes reachable through infused alloy.
  It plans 7.1M crafts (about 997k basic circuits) and cannot finish within the 1400 s wall clock, so its chunk
  stops there. Rerun the rest of that chunk by `items`.
- `chapter=通用机械` also matches `通用机械附属` (title `contains`): 196 eligible tasks, 10 chunks of 20.
- Wall-clock-limited targets (both layouts, 1400 s): `appflux:core_256m` (132k crafts), the
  `appgen:{flux,singularity}_generator_256m` generators (singularity: 673k crafts) and `singularity_generator_64m`.
  `core_64m` finishes in about 875k ticks (about 6 minutes per layout). Put such targets in their own runs.
- `draconicevolution:creative_capacitor`: the driver's `TagParser.parseTag(task.snbt())` throws on its component
  SNBT (empty key `{"":"BOOLEAN"}`). That aborts every chunk whose selection contains it (harness error).
- `ae2:creative_energy_cell`, `ae2omnicells:creative_ae_cell_{long,biginteger}`, `ae2lt:infinite_storage_cell`,
  `extendedae_plus:infinity_{biginteger_cell,core}`: both layouts return `plan-inputs`, also with only the Phase 3a
  adapters. The report prints `KeyCounter@...` instead of the missing keys.
- Final evidence: 195 chapter tasks merged across both paths, 194 both-pass and 1 not-bootstrappable
  (`pellet_antimatter`). Infuser 157 tasks / 10 recipes, Enrichment Chamber 97 / 3, Crusher 1 / 1. Rejections: 18
  crushing and 8 enriching recipes for input ambiguity, no infusing rejections.
