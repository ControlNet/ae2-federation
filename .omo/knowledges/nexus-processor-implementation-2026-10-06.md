# Nexus Processor implementation (2026-10-06)

Implements `docs/ideas/nexus-processor.md`. Decisions the owner made when it was implemented:

- Ender Dust in both recipes is the tag `c:dusts/ender_pearl` (AE2's `ae2:ender_dust` is in it); redstone is
  `c:dusts/redstone`.
- Core layout: `RRR` over `EEE`, shaped, 16 cores. Reversed rows do not match (shaped recipes only mirror sideways).
- The Nexus Core icon comes from the artist; no placeholder texture is committed. The owner had the change shipped
  without it: the game starts and draws the missing texture. When the icon arrives, add
  `textures/item/nexus_core.png` (16x16, transparent) and put the core back into the texture check in
  `SurvivalRecipeContractTest.theCoreAndProcessorAreRegisteredNamedAndModelled`.

## What changed

- `MaterialRegistration`: `NEXUS_CORE` (`nexus_core`) and `NEXUS_PROCESSOR` (`nexus_processor`); the old
  `federation_logic_processor` registration is gone, without alias or migration.
- Recipes: `recipe/nexus_core.json` (shaped), `recipe/nexus_processor.json` (`ae2:inscriber`, `press`: top core,
  middle dust tag, bottom `ae2:printed_silicon`); every device recipe and recipe-book advancement points at
  `nexus_processor`; new advancement `recipes/misc/nexus_core.json` unlocks on any `#c:dusts/ender_pearl`.
- The processor keeps its texture, renamed to `textures/item/nexus_processor.png`. `docs/art/assets/` keeps the old
  file name because the archived survival discussion links to it.
- Guide: `items/nexus_processor.md` (en, zh) owns both item ids and shows both recipes; getting-started and index
  updated.

## Verification

- JUnit: all green (the core's texture is not checked until it exists).
- `python3 tools/dev_gametests.py survivalrecipes survivalinscriber`: 2/2. federationVerify for `survival.recipes`
  (44 assertions) and `survival.inscriber` (5) passed. Mutation `mode: inscribe` fails both tests.
- The Inscriber fed through one side sorts core, dust and silicon into place (`survivalInscriber`).
- `python3 tools/compat_run.py --accept-eula atm10`: 79/79 (`federationCraftable` covers core and processor).
- Guide client screenshots (en, zh): both recipe displays render.
