# Inscriber Nexus Press (2026-10-10)

The owner added a press of our own for the Printed Nexus Circuit, modelled on Advanced AE's Quantum Press.

## Names (surveyed from the addon jars in the compat cache)

| Mod | ID | English | Chinese |
|---|---|---|---|
| AE2 | `logic_processor_press` | Inscriber Logic Press | 逻辑压印模板 |
| MEGA Cells | `accumulation_processor_press` | Inscriber Accumulation Press | 累积压印模板 |
| ExtendedAE | `concurrent_processor_press` | Inscriber Concurrent Press | 并发压印模板 |
| Advanced AE | `quantum_processor_press` | Inscriber Quantum Press | 量子压印模板 |
| Applied Flux | `energy_processor_press` | Inscriber Energy Press | 能量压印模板 |
| Neo ECO | `superconducting_processor_press` | Superconducting Processor Press | 超导压印模板 |
| AE2LT | `overload_inscriber_press` | Overload Inscriber Press | 过载压印模板 |

Ours: `nexus_processor_press`, Inscriber Nexus Press, 联结压印模板. The ID follows AE2's press IDs (`<x>_processor_press`)
rather than the English name; AE2's press IDs never follow their English names.

## Recipes

- Make: Inscriber `press` mode, Engineering Press top, `c:ender_pearls` middle, Logic Press bottom; both presses are
  spent (Advanced AE uses the same pair with a Shattered Singularity; MEGA spends Calculation + Engineering with a
  Singularity). AE2 tries the plates both ways round (`InscriberRecipes.findRecipe` matchA/matchB).
- Copy: `inscribe` mode, the press top, `minecraft:iron_block` middle (AE2, MEGA, Advanced AE, Applied Flux; Neo ECO
  uses `c:storage_blocks/iron`).
- Printed Nexus Circuit: the Nexus Press replaces the Logic Press.
- Not in `ae2:inscriber_presses`: that tag is the Mysterious Cube's loot (meteorites). Only Neo ECO adds its press.
- No clash: an empty recipe slot is `Ingredient.EMPTY`, which only matches an empty slot, so Ender Dust (pearl alone)
  and the old Logic-Press circuit recipe cannot take three-slot inputs.

## Art

Temporary: the item model points at `ae2:item/logic_processor_press` (no copied PNG) until the artist delivers.
`SurvivalRecipeContractTest` pins that reference; `validate_assets.py` only checks the cable's models.

## Tests

- `SurvivalRecipeContractTest`: both press recipes, the circuit's new press, names, the temporary model, no press tag.
- `survivalrecipes` GameTest: press either way round (PRESS), iron copy (INSCRIBE), circuit under the Nexus Press, the
  Logic Press no longer prints it. Mutation: `mode: inscribe` on the press recipe fails it.
- Compat `advanced-ae` (29/29): `nexusAndQuantumPressesShareTheirPresses`.

## Guide

`<RecipeFor id="ae2federation:nexus_processor_press" />` showed the iron copy, not the recipe that makes the press:
with two recipes for one item, RecipeFor picks one. The pages use `<Recipe id="...">` (a recipe id, as AE2's own
guide does) for both. `GuidePagesContractTest.FEDERATION_RECIPES` lists recipe ids that are not item ids, accepted
only inside `<Recipe>`.
