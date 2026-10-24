# Playthrough adapters: AE2 addon machines (Phase 3b, addons-a)

Adapters in `common/src/compattest/.../compat/`, registered in `PlaythroughMachineAdapters.REGISTERED`:

| Adapter ID | Recipe type | Machine block | Class |
|---|---|---|---|
| `crystal-assembler` | `extendedae:crystal_assembler` | `extendedae:crystal_assembler` | `PlaythroughCrystalAssemblerAdapter` |
| `reaction-chamber` | `advanced_ae:reaction` (no registry key; Phase 0 name) | `advanced_ae:reaction_chamber` | `PlaythroughReactionChamberAdapter` |
| `overload-processing` | `ae2lt:overload_processing` | `ae2lt:overload_processing_factory` | `PlaythroughLightningTechAdapter.overloadProcessing()` |
| `lightning-assembly` | `ae2lt:lightning_assembly` | `ae2lt:lightning_assembly_chamber` | `PlaythroughLightningTechAdapter.lightningAssembly()` |
| `lightning-simulation` | `ae2lt:lightning_simulation` | `ae2lt:lightning_simulation_room` | `PlaythroughLightningTechAdapter.lightningSimulation()` |

Shared reflection helpers live in `PlaythroughAddonRecipes` (the compat source set does not compile against addons).

## Facts that took digging

- All five machines are AE grid nodes. Placed at the site they join the input subnet through the cable above,
  and take AE power from it (projection: Quartz Fiber; endpoint: the Endpoint's energy overlay).
- An AE2 Interface with no config exposes the subnet's whole storage. A processing pattern's non-item inputs (fluid,
  FE, lightning) therefore land in whatever subnet cell accepts them, without extra plumbing.
- Crystal Assembler: `getInputs()` are Glodium `IngredientStack.Item` (`getIngredient`, `getAmount`), `getFluid()`
  an `IngredientStack.Fluid`. The machine picks the first recipe in `RecipeManager` order whose `getSample()` testers
  are all consumed; the adapter replays that. MAX_PROGRESS 200, 50/tick with 4 speed cards = 4 ticks.
- Reaction Chamber: `getInputs()`, `getFluid()`, public `output` (GenericStack), `getEnergy()` (AE). Static
  `ReactionChamberRecipes.findRecipe(Level, List<ItemStack>, GenericStack)` is the machine's lookup. Tank slot 1 is
  input, slot 0 output. 4 ticks with 4 cards.
- AE2 Lightning Tech (`com.moakiee.ae2lt`): lightning is an AEKey type `ae2lt:lightning`
  (`me.key.LightningKey.of(Tier)`, HV/EHV). Machines take lightning from their grid storage at completion and FE
  through Applied Flux's recharge from grid storage. Recipe services `findFirstProcessable(level, inv[, inFluid,
  outFluid], hv, ehv)` choose the recipe; the overload service also returns a `parallel` (1 without a collapse
  matrix). The chambers' candidates have no `parallel`.
- FE per tick with 4 cards: overload factory = config `overloadFactoryFePerTickFourSpeedCards`; assembly 500k;
  simulation 200k. Chambers buffer only 20M FE, so FE must come from an FE cell in the subnet, not from a bus.
- Lightning costs reach 32767 and 1,000,000 in NAST's KubeJS recipes; `lightning_storage_component_v` holds 65536,
  so costs above that are rejected.
- Justification for provided lightning: the Lightning Collector stores natural (EHV) and artificial (HV) strikes as
  ME lightning; the Tesla Coil makes HV from overload crystal dust + FE. JEI shows lightning only as text.

## Harness fix found here

Endpoint `advanced_ae:quantum_processor` failed baseline only ("requester holds 1/2 of 4"): surplus outputs were still
moving through two hoppers when the CPU went idle, and the 25-tick idle window started too early. Stage 5 of
`PlaythroughFactory` now also waits for `PlaythroughMachines.idle()` (the `machine-idle-empty` check).

## Rejected (stay stand-ins)

- Reaction recipes with a fluid product (`advanced_ae:quantum_infusion`, KubeJS `time_fluid`) and those needing
  `quantum_infusion_source` (quantum alloy/plate, oblivion singularity, ae2cs quantum crystal seed): need a fluid
  return pipe and a fluid-producing step.
- Overload processing fluid products; lightning costs above 65536; derived recipes where the machine's lookup picks
  another recipe (e.g. derived inscriber recipes).
- `ae2lt:lightning_transform` is an in-world lightning strike on dropped items, not a machine: not adapted.

## Results (2026-10-24)

- 25 quest targets both-pass on projection and endpoint with every step observed in both layouts (runs
  `nast-aa-r1/r3/r5/r6/r7/r8-*`); 8-target regression with all adapters: 8/8 both-pass on both paths (`nast-aa-reg-*`).
- No suspected Federation bug. Endpoint federated timing outliers (970 vs 436 ticks for `ae2lt:overloaded_cable`) did
  not reproduce on rerun.
- With all adapters on, the shallowest producer (`QuestCoverageAnalysis.productionProofs`) wins: the regression's AE2 processors come from the Overload
  Processing Factory and charged certus from the Reaction Chamber, so the Inscriber/Charger lose coverage there.
- `pipez:infinity_upgrade` aborts a run: its plan needs 139,207,908 CPU bytes (harness CPU template limit).
- `ae2lt:perfect_electro_chime_crystal` takes ~1.05M ticks (524288 singularity runs); a 16-target run takes ~15 min.
