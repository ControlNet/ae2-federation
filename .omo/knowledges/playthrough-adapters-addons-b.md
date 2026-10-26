# Phase 3b adapters: smithing, stonecutting, AE2 addons, Powah, Mystical Agriculture

- Smithing and stonecutting are AE2 pattern kinds, not shelf machines. `PatternDetailsHelper.encodeSmithingTablePattern`
  and `encodeStonecuttingPattern` produce `IMolecularAssemblerSupportedPattern`s that the factory's Molecular
  Assemblers run. The adapter interface gained `assembler()` (no site, no `place`/`leftovers`) and `encode()`;
  `Recipe.verify()` decodes any encoded pattern and compares inputs/outputs. `Proof.shelf()` is the machine map minus
  assembler kinds; budgets, storage cells, provider setup and the endpoint guard use it.
- AE2 smithing patterns accept only `SmithingTransformRecipe` and `SmithingTrimRecipe` classes and consume all
  three inputs (as the vanilla table does). The adapter takes transforms only (trims have no fixed output).
  Netherite/allthemodium upgrades work; JustDireThings `AbilityRecipe`/`PaxelRecipe` and the jdte AE extraction
  smithing class cannot be encoded.
- alltheores ore hammers mutate the input stack inside `getRemainingItems` (damage). Phase 1 compares two remainder
  signatures to detect nondeterminism, so the first one must use a copied grid, or every hammer recipe looks
  nondeterministic and blocks all netherite smithing chains.
- Crystal Aggregator (ae2cs): ME-powered, 200 AE/t doubled per acceleration card (4 cards = 3,200 AE/t). Slots
  accept any item, so use provider blocking mode. NeoForge's hopper extract hook simulates per slot and may `grow`
  a matching destination stack even when the real extract returns EMPTY; with an output equal to an input this can
  duplicate. Reject output-equals-input recipes (glowstone, gunpowder, redstone, quantum alloy in NAST).
- Projection path blocking: the native provider pushes into an unconfigured Interface whose ME storage is the input
  subnet, so AE2 Blocking mode sees the machine's contents through the storage bus. Federation Provider lanes copy
  the native Blocking setting. `Site.blocking()` sets `Settings.BLOCKING_MODE` on the site's provider and `Settings.STORAGE_FILTER = NONE` on
  the input Storage Bus. The bus defaults to EXTRACTABLE_ONLY, which hides inputs a machine refuses to give back
  (Powah orb slots 1-6): blocking then sees an empty machine, a second batch joins the first, the orb matches no
  exact recipe and hangs (both layouts fail `job-complete` identically; a fixture bug, not Federation).
- High-yield recipes (resonating processor: 32 per run) can finish the job before the hopper drains the machine;
  stage 5 now waits up to `drainTicks()` for `machine-idle-empty` before the 25-tick idle window.
- Powah orb: slots 1-6 inputs (limit 1 each, accepted only when slot 0 is empty), slot 0 output. Rods receive FE only
  on their FACING (mounting) side; a creative energy cell pushes to all sides. Bind the rod with `setOrbPos`
  (absolute position), as the wrench does. Nitro rod: 200,000 FE/t in NAST, after a 20-tick warm-up.
- Seed Reprocessor: input from UP (slot 0), fuel from NORTH (slot 1); 200 ticks at 20 FE/t without upgrades; a stick
  burns 100 ticks at 20 FE per tick = 2,000 FE, so each run's pattern carries two sticks.
- Data Reassembler (Data Energistics): AE grid node on every side except FRONT/TOP; ME storage = insert-only
  PatternInputStorage; nine 512-item slots; the resolver picks the first recipe, by ID, that slot contents satisfy
  (at least the counts), so check the selection for 1 run and for full slots. Speed card: 40 ticks less each.
  Fluid (37) and data flow (4) recipes are rejected for lack of pack-justified supply.
- Powah's orb clears every slot when a craft completes (`inv.clear()` before setting the output), and recipe
  matching is exact (no extra items), so any extra batch in the orb is either destroyed or stalls it.
- `ae2federation.playthrough.items` selects quest tasks, so an item used by two tasks runs twice (e.g.
  `extrahnn:simulator_modeling`). `bigreactors:fluidizersolidinjector` fails `pattern-visible` on both layouts
  with or without the reprocessor adapter: unrelated to these adapters.
