# Survival acquisition references: AE2 and selected addons (2026-10-03)

## Scope and correction

The user rejected the assistant's speculative common-component names and simple ingredient allocation. They
explicitly requested studying AE2, NeoECO, AE2 Lightning Tech and Data Energistics before continuing acquisition
design. Early availability remains confirmed. A common component is a possibility, not a selected item or recipe.
The rejected coupling-component proposal and its quantities must not become an implementation specification.

This investigation reads official source recipes and bundled guides, not just feature lists. No game was launched.
Local snapshots are version-scoped; none is asserted to represent every release or modpack recipe override.

| Project | Inspected source snapshot | Local checkout |
|---|---|---|
| AE2 | 19.2.17 source, `db95d25ccc79f7bd55b504cf71522b57d60bf4f7` | `/tmp/ae2-19.2.17` |
| NeoECOAEExtension | `c33f736a821683c6aa4715665ec7c3dadad7765f` | `/tmp/ae2-gui-research-k2AQa8/NeoECOAEExtension` |
| AE2 Lightning Tech | `1d4589b6bd50672051f78d766505530beacfebc0` (2026-09-09 commit) | `/tmp/ae2lt` |
| Data Energistics | branch `1.21`, `ddef032e7ca10744c52efc179a7b80b738e06ad9` | `/tmp/federation-survival-data-energistics` |

Data Energistics' current repository is ModularMCLib/DataEnergistics; its README retains fish1145 release links and
links CurseForge project 1565514. The author namespace is `com.fish_dan_`. The CF page was inaccessible to the web
reader during this pass, so findings come from the author repository, not a third-party feature summary.

## AE2: distinct material and electronic-component workflows

- The Fluix guide and generated `transform/fluix_crystals.json` establish charged Certus Quartz + redstone + Nether
  Quartz transformed in water into two Fluix Crystals. The guide explicitly provides an automation route using
  Formation/Annihilation Planes. Initial manual production and later automation use the same underlying operation.
- `inscriber/logic_processor_print.json` uses a Logic Press and gold to make a printed logic circuit. The processor
  recipe then combines that print, redstone and printed silicon. Material preparation and electronic assembly have
  different purposes; the processing tool is part of the acquisition experience.
- Formation and Annihilation Cores are simpler reusable components: Certus versus Nether Quartz, Fluix dust and
  a Logic Processor, yielding two cores. Thus AE2 does not require every common component to introduce a new ore,
  machine or independent processor family.

Primary sources: [Fluix guide](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/guidebook/items-blocks-machines/fluix_crystal.md),
[processor print](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/src/generated/resources/data/ae2/recipe/inscriber/logic_processor_print.json),
[processor assembly](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/src/generated/resources/data/ae2/recipe/inscriber/logic_processor.json),
[Formation Core](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/src/generated/resources/data/ae2/recipe/materials/formationcore.json),
[Annihilation Core](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/src/generated/resources/data/ae2/recipe/materials/annihilationcore.json).

## NeoECO: reusable material family and later production routes

- The bundled Energized Crystal guide describes lightning conversion of Certus budding blocks and subsequent crystal
  growth. This creates a renewable material source. Optional ExtendedAE repair routes are conditional integrations.
- Generated transformation recipes go beyond the guide: energized dust + Fluix yields Energized Fluix Crystal;
  Crystal Ingot and Energized Superconductive Ingot use explosion transformations with their respective inputs.
- The Superconducting Press recipe uses six energized superconductive ingots plus the three AE2 processor presses.
  The new press and superconductive ingot produce a printed circuit in the AE2 Inscriber. Final assembly uses that
  print, printed silicon and a Crystal Matrix; the matrix itself uses five Crystal Ingots.
- A built-in Integrated Working Station recipe combines four charged Certus and four energized dust plus water and
  energy into eight Energized Crystals. A larger AdvancedAE reaction recipe is explicitly conditional on that addon.
  These are later production options, not evidence that their machines are required for the first crystal.

Primary sources: [crystal guide](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/c33f736a821683c6aa4715665ec7c3dadad7765f/guidebook/_zh_cn/neoecoae_intro/energized_crystal.md),
[generated recipes](https://github.com/DancingSnow0517/NeoECOAEExtension/tree/c33f736a821683c6aa4715665ec7c3dadad7765f/src/generated/resources/data/neoecoae/recipe),
[superconducting processor](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/c33f736a821683c6aa4715665ec7c3dadad7765f/src/generated/resources/data/neoecoae/recipe/inscriber/superconducting_processor.json),
[station crystal recipe](https://github.com/DancingSnow0517/NeoECOAEExtension/blob/c33f736a821683c6aa4715665ec7c3dadad7765f/src/generated/resources/data/neoecoae/recipe/integrated_working_station/energized_crystal.json).

Interpretation: a material can connect several device recipes while its source and processing provide identity.
The depth and ingredient gates of this chain are not automatically suitable for early Federation access.

## AE2 Lightning Tech: a themed transformation process

- The entry guide links Overload budding crystals, lightning transformation and subsequent machinery. The inspected
  simple damaged-budding recipe consumes a Certus budding center's surrounding quartz/Fluix blocks and accepts
  non-natural lightning (`requires_natural_lightning: false`). Higher-grade routes have different requirements;
  do not flatten these into one universal natural-lightning requirement.
- Overload Alloy Blank is initially table-crafted; lightning transformation and later machines process materials
  further. The guide describes collection/storage/consumption of lightning as the subsequent production loop.
- An actual `lightning_transform/overload_circuit_board.json` recipe consumes four unoverloaded boards and four
  overload dust for one overload board. The inspected Inscriber processor recipe uses an overload board, redstone
  and printed silicon. The same snapshot also contains an assembler processor recipe.
- The bundled processor guide describes a different sequence for some steps. Therefore the guide is used for
  intended progression and the recipe files for specific recipe claims; no single exclusive route is asserted.

Primary sources: [entry guide](https://github.com/ae2lt/AE2-Lightning-Tech/blob/1d4589b6bd50672051f78d766505530beacfebc0/src/main/resources/assets/ae2lt/ae2guide/_zh_cn/getting-started.md),
[simple budding transformation](https://github.com/ae2lt/AE2-Lightning-Tech/blob/1d4589b6bd50672051f78d766505530beacfebc0/src/main/resources/data/ae2lt/recipe/lightning_strike/simple_damaged_budding_overload_crystal.json),
[board transformation](https://github.com/ae2lt/AE2-Lightning-Tech/blob/1d4589b6bd50672051f78d766505530beacfebc0/src/main/resources/data/ae2lt/recipe/lightning_transform/overload_circuit_board.json),
[processor recipe](https://github.com/ae2lt/AE2-Lightning-Tech/blob/1d4589b6bd50672051f78d766505530beacfebc0/src/main/resources/data/ae2lt/recipe/inscriber/overload_processor.json).

Interpretation: transformation and subsequent production machinery express the mod's subject. Copying its lightning
event or advanced material gates would require a separate reason in Federation, not merely a similar ingredient name.

## Data Energistics: exploration, material preparation and separate component roles

- The bundled guide establishes Digitalized Meteorites as an entry source for data budding crystals and a central
  mysterious block that supplies the data press template. Buds/clusters supply data dust according to their loot
  stage; do not assume the grown cluster directly drops the final refined crystal.
- The actual AE2 transformation recipe combines data dust and charged Certus to make a Data Crystal. The Inscriber
  then uses that crystal and the data template to make a circuit board.
- The Data Processor recipe combines that board with printed silicon and **a Quantum Entangled Singularity**.
  This is a substantive progression gate. It is not a recipe cost to import into early Federation play.
- Data Framework is a separate structural component: four Data Crystals, four iron ingots and a Fluix Block. The
  Data Reassembler recipe consumes both framework and processor alongside other AE2/DE components. A reusable
  structural component and a processed electronic component have different jobs in the same recipe family.

Primary sources: [meteorite guide](https://github.com/ModularMCLib/DataEnergistics/blob/ddef032e7ca10744c52efc179a7b80b738e06ad9/src/main/resources/assets/data_energistics/ae2guide/_zh_cn/items-blocks-machines/1_data_meteorite.md),
[template guide](https://github.com/ModularMCLib/DataEnergistics/blob/ddef032e7ca10744c52efc179a7b80b738e06ad9/src/main/resources/assets/data_energistics/ae2guide/_zh_cn/items-blocks-machines/5.4_template.md),
[crystal recipe](https://github.com/ModularMCLib/DataEnergistics/blob/ddef032e7ca10744c52efc179a7b80b738e06ad9/src/main/resources/data/data_energistics/recipe/ae2/transform/data_crystal.json),
[processor recipe](https://github.com/ModularMCLib/DataEnergistics/blob/ddef032e7ca10744c52efc179a7b80b738e06ad9/src/main/resources/data/data_energistics/recipe/ae2/inscriber/data_processor.json),
[framework recipe](https://github.com/ModularMCLib/DataEnergistics/blob/ddef032e7ca10744c52efc179a7b80b738e06ad9/src/main/resources/data/data_energistics/recipe/crafting/data_framework.json).

## Implications for discussion, not approved features

### Fluix lore and proposed recipe interpretation

On the user's request to evaluate Fluix Dust against AE2 lore, inspected the pinned AE2 guide pages
`items-blocks-machines/fluix_crystal.md`, `fluix_dust.md`, `certus_quartz_crystal.md`, `processors.md`
and `quartz_fiber.md`. Fluix crystals absorb and convert energy between forms; Certus crystals accept large
quantities of energy into their crystalline matrix. The dust page describes crushed Fluix used in machines and
components, with no separate networking-protocol property. Quartz Fiber shares power while keeping ME networks
separate. The processor page explains ingredients and manufacturing, not a detailed processor instruction-set lore.
Formation and Annihilation Core recipes already combine native Logic Processors with Fluix Dust and a quartz type.

Thus Fluix Dust is a plausible material for a Federation electronic component, but the claim that it inherently
provides cross-network translation, permissions or isolation is unsupported. Such behavior must be attributed to
the designed Federation component, explicitly as addon fiction. Adding dust to an already Fluix-based cable has
weaker explanatory value; the draft cable ingredient remains open rather than being justified by canonical lore.
Primary sources: [Fluix](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/guidebook/items-blocks-machines/fluix_crystal.md),
[dust](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/guidebook/items-blocks-machines/fluix_dust.md),
[fiber](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/guidebook/items-blocks-machines/quartz_fiber.md).

### Follow-up: Federation Logic Processor candidate

The user subsequently proposed the name Federation Logic Processor (联邦逻辑处理器) and asked about feasibility
and comparable addons. Re-reading the three addon Inscriber recipes above confirms direct precedents for custom
processor items assembled through AE2's recipe type. This supports feasibility without requiring changes to native
processor behavior. A candidate dedicated Federation crafting ingredient need not substitute for AE2's Logic Processor.
No acquisition route or implementation is approved.

ExtendedAE also has a Concurrent Processor. Its inspected `26.1.2-neoforge` branch contains a config-conditioned
Crystal Assembler recipe using its own processor print, AE2 printed silicon and redstone dust (four of each for
four processors, 4000 energy). This is additional design precedent, not a verified recipe for this project's
Minecraft version or evidence that the assembler is the exclusive acquisition route.
Source: [author recipe](https://github.com/GlodBlock/ExtendedAE/blob/26.1.2-neoforge/src/generated/resources/data/extendedae/recipe/assembler/concurrent_processor.json).

The following earlier implications remain discussion guidance:

1. Decide whether a new common ingredient is a base material, processed electronic component or structural part.
   Its role, first acquisition and repeated production should inform its name and device recipes.
2. Early availability constrains the prerequisites; it does not require every recipe to be an ordinary table recipe.
   A short process using an existing early AE2 machine or world transformation is a candidate, not a decision.
3. Study the distinction between making the first sample and automating repeated production. A required material
   must not depend exclusively on the Federation device it is supposed to craft.
4. A new crystal, press, machine, ore, loot gate, lightning mechanic, dedicated energy resource or processor family
   is not automatically necessary. The four references illustrate different combinations, not one mandatory pattern.
5. No new component name, ingredient count or output quantity has been approved. Preserve the user's rejection of
   the prior speculative coupling-component recipe and return to the acquisition process before another naming list.

Product discussion: [survival playability](../../docs/archive/ideas/survival-playability.md).
