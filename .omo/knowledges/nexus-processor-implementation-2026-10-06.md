# Nexus Processor implementation (2026-10-06)

Implements the 2026-10-06 proposal now preserved in [the archived Nexus idea](../../docs/archive/ideas/nexus-processor.md).
The later 2026-10-08 revision is documented in [the current survival feature](../../docs/features/survival-playability.md).
Decisions the owner made when it was implemented:

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
- The processor's old icon (recoloured from AE2 art, so CC BY-NC-SA) was deleted on 2026-10-07 together with its
  source in `docs/art/assets/`, so all art is All Rights Reserved; the artist is redrawing it. When both icons
  arrive, add `nexus_processor.png` and `nexus_core.png` and restore their texture check.
- Guide: `items/nexus_processor.md` (en, zh) owns both item ids and shows both recipes; getting-started and index
  updated.

## Verification

- JUnit: all green (the core's texture is not checked until it exists).
- Both icons arrived in the artist's PR #2 (2026-10-08) and the texture check is restored; see
  `artist-device-art-2026-10-07.md`.
- `python3 tools/dev_gametests.py survivalrecipes survivalinscriber`: 2/2. federationVerify for `survival.recipes`
  (44 assertions) and `survival.inscriber` (5) passed. Mutation `mode: inscribe` fails both tests.
- The Inscriber fed through one side sorts core, dust and silicon into place (`survivalInscriber`).
- `python3 tools/compat_run.py --accept-eula atm10`: 79/79 (`federationCraftable` covers core and processor).
- Guide client screenshots (en, zh): both recipe displays render.
