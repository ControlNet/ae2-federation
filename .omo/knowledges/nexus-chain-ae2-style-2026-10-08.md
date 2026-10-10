# Nexus chain rebuilt after AE2's processors and cores (2026-10-08)

The owner asked for the Nexus items to follow AE2's own chain, in both recipes and names. AE2 19.2.18's recipes were
read from its jar (`data/ae2/recipe/inscriber/*.json`, `materials/formationcore.json`, `annihilationcore.json`).

| Step | AE2 | Federation |
|---|---|---|
| Printed circuit | `inscribe` mode: Inscriber Logic Press (kept) + gold ingot -> Printed Logic Circuit (`printed_logic_processor`) | Logic Press (kept) + `c:ender_pearls` -> Printed Nexus Circuit (`printed_nexus_circuit`) |
| Processor | `press` mode: circuit + redstone + Printed Silicon -> Logic Processor | circuit + `c:dusts/redstone` + Printed Silicon -> Nexus Processor |
| Core | shaped row `abc`: certus (`ae2:all_certus_quartz`) + `c:dusts/fluix` + Logic Processor -> 2 Formation Cores | row `FEP`: `c:gems/fluix` + `c:dusts/ender_pearl` + Nexus Processor -> 16 Nexus Cores (owner's choice) |
| Devices | interfaces and planes take cores | all six device recipes take a Nexus Core |

The names follow AE2's English: Printed Logic Circuit, Logic Processor, Formation Core. The Chinese names follow AE2's
逻辑电路板, 逻辑处理器, 成型核心.

The owner also had the circuit's ID follow its English name. The artist's `printed_nexus_processor` became
`printed_nexus_circuit`, with no alias: the item had existed for a few hours, without a recipe.

## No clashes

No AE2 Inscriber recipe takes a press with an Ender Pearl. AE2's own `ender_dust` recipe takes the pearl alone in the
middle slot, and it still matches when there is no press; the GameTest checks this.

## Recipe-book unlocks

- The core unlocks when the player holds a Nexus Processor.
- Each device unlocks when the player holds a Nexus Core.
- The Inscriber steps have no unlock, as in AE2.

## Guide

The page `items/nexus_processor.md` became `items/nexus_core.md` (en and zh). It owns all three item ids and shows the
three recipes in order. Getting Started and the index were updated to match.

## Tests

- `SurvivalRecipeContractTest` pins all three recipe JSONs, the core inputs of the six devices, and the names in both
  languages (`chainItemsAreNamedAfterTheirAe2Counterparts`).
- `SurvivalRecipeGameTests` checks the loaded recipes (`survival.recipes` 51 assertions, `survival.inscriber` 5):
  - the core row in any grid row, and mirrored: vanilla shaped recipes also match left-right mirrored, as AE2's
    Formation Core does, so a "reversed row makes nothing" assertion is wrong;
  - a native Logic Processor or a missing input makes no core;
  - a Formation Core does not make a Router or a Cable;
  - the circuit keeps its press;
  - a real Inscriber fed from one side presses two processors from circuits.
