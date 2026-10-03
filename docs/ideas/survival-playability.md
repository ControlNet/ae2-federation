# Survival playability

Status: early availability, five recipes and an AE2-style GuideME guide confirmed; Federation Cable recipe remains under discussion. Not implemented.

Discussion date: 2026-10-03. [Idea index](README.md).

## Intent

Make the mod practically obtainable and usable in survival: crafting recipes, material costs, acquisition timing,
the equipment needed for a first working connection, and ordinary placement, removal and configuration workflows.
The user clarified that this discussion concerns survival integration, rather than imagined factory roles or
multiplayer scenarios.

## Confirmed direction

Basic Federation functionality should be available relatively early in a player's AE2 progression. This answers
the question of when players should be able to try connecting their first two ME networks. The earlier assistant
preference for a mid-game introduction was not accepted.

The five recipes below are confirmed. The cable recipe, exact AE2 progression milestone, complete setup budget
and power cost remain open. This decision does not set the acquisition timing of every future feature or establish
equipment tiers. It also does not change the confirmed native-P2P power/channel alignment.

## Proposals for discussion

The initial suggestions below were made before the requested reference study. The user rejected the subsequent
speculative common-component naming/recipe approach. These earlier suggestions are historical, unaccepted options,
not the selected acquisition plan; revisit them using the reference investigation below.

- Evaluate the complete cost of the minimum working connection between two existing ME networks, including every
  required endpoint, connecting component and configuration tool. Individually cheap recipes can still produce an
  expensive first setup.
- Keep frequently placed cable inexpensive and consider batch crafting. A short experimental installation should
  be affordable to build and rearrange.
- Favor familiar AE2 materials for basic devices. Investigate whether a dedicated intermediate component adds enough
  value before introducing another production step; no new processor or manufacturing chain has been chosen.
- Consider making useful basic sharing available immediately instead of requiring several upgrades before the
  first connection serves a purpose. Which capabilities belong in the initial affordable setup remains open.
- Make required configuration tools easy to obtain, and consider reusable tools and recoverable equipment to reduce
  the cost of learning. Exact drops, configuration retention and tool requirements remain undecided.
- Treat recipes, recipe-viewer discoverability, in-game setup instructions, connection-face feedback and recovery
  from configuration mistakes as parts of survival usability.

These suggestions interpret the early-availability goal; they are not individually confirmed requirements.

## Current acquisition direction

The user proposed **Federation Logic Processor** (联邦逻辑处理器) and prefers producing it by further processing
a native AE2 processor, avoiding extensive original recipe design. In the preceding comparison, the native Logic
Processor was the proposed input and is now confirmed with Fluix Dust, as recorded below. Do not introduce a separate printed
circuit, press or material family as an assumed requirement. This discussion does not authorize implementation.
Earlier statements that no component form was selected describe the preceding discussion.

The user agreed with early access to the existing core capabilities and identified acquisition of individual
blocks as the main design problem. Focus the next discussion on concrete devices and their complete material bill.
No progression ladder or restriction to storage-only has been selected.

## In-game GuideME documentation

The user identified an in-game GuideME guide, following native AE2's example, as a key part of survival playability.
This is a confirmed scope requirement alongside obtainable recipes. It is not fulfilled by repository documentation
alone. Technical integration and concrete page implementation remain work for the future coding agent.

Suggested content structure for implementation planning:

- Getting started: prerequisites, obtaining the Federation Logic Processor, connecting two existing ME networks,
  configuring sharing direction and verifying the first successful interaction.
- Items and blocks: all six current recipe-design entries, their actual recipes, placement, configuration and links
  to related examples. Explicitly show the Router's four-per-craft output.
- Mechanics: independent ME networks, Federation connectivity, directional sharing, and actual power/channel rules.
- Example setups: adjacent networks with a Bridge; Routers joined by Federation Cable; a working distributed
  processing setup with a Federation Pattern Provider and Processing Endpoint.
- Troubleshooting: connection/face mistakes, sharing direction, power or channel conditions and observable device
  states, checked against actual runtime behavior when authored.

Suggested presentation and acceptance criteria, modeled on AE2:

- Use item icons, links and recipe components backed by implemented recipes instead of hand-drawn recipe images.
- Use annotated in-game structure scenes where they clarify connection faces and network boundaries; interactive
  scenes are especially useful for setups that are difficult to explain with prose alone.
- Make relevant pages discoverable from items and provide links between device, mechanic and tutorial pages.
- Validate the first-connection walkthrough in survival and check recipe rendering, links and structure scenes in
  the project's actual AE2/GuideME versions. Do not assume APIs in current online documentation match the pinned version.
- Document available behavior; future P2P, remote bridges and advanced policy ideas must not appear as usable features.

AE2's own [guide index](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/guidebook/index.md)
separates getting started, mechanics, example setups and item/block pages, and uses interactive structure scenes.
See also [GuideME integration documentation](https://guideme.appliedenergistics.org/integration/).
Whether Federation extends AE2's guide or supplies its own linked guide is an implementation choice still open.

## Selected processor icon

The user approved this Federation Logic Processor icon and requested storing it with the idea:

![Federation Logic Processor](assets/federation_logic_processor.png)

[Selected 16x16 transparent PNG](assets/federation_logic_processor.png).
It was edited directly from AE2 19.2.17's native Logic Processor texture, retaining the gold base, dark chip and
alpha mask while changing twelve pixels to introduce Fluix-colored nodes and connections. No image generation
was used. The source is `src/main/resources/assets/ae2/textures/item/logic_processor.png` at AE2 commit
`db95d25ccc79f7bd55b504cf71522b57d60bf4f7`; added colors come from the native Fluix palette.
This selected asset is stored for future implementation and is not yet registered as an in-game item texture.

## Confirmed recipes

The user accepted Fluix Dust for the processor, requested revisiting cable conversion, then changed the Bridge
to use a Storage Bus, Quartz Fiber and Federation Logic Processor and accepted the other device recipes.
The following five recipes are confirmed product decisions, not implemented recipes.

| Output | Inputs | Station and layout |
|---|---|---|
| 1 Federation Logic Processor | 1 native Logic Processor + 1 Fluix Dust | AE2 Inscriber; dust in top slot, processor in middle, bottom empty; consume both inputs |
| 1 Federation Bridge | 1 native ME Storage Bus + 1 Quartz Fiber + 1 Federation Logic Processor | Shapeless crafting |
| 4 Federation Routers | 4 Federation Cables + 1 native ME Import Bus + 1 native ME Export Bus + 1 native ME Storage Bus + 1 native ME Interface block + 1 native Logic Processor | Shaped crafting: Federation Cables in corners; Import Bus top, Export Bus bottom, Storage Bus left, Interface right; native Logic Processor in center |
| 1 Federation Pattern Provider | 1 native Pattern Provider block + 1 Federation Logic Processor | Shapeless crafting |
| 1 Federation Processing Endpoint | 1 native ME Interface block + 1 Federation Logic Processor | Shapeless crafting |

The processor reuses native processing and adds one step without a new press or printed circuit. The Bridge's
ingredients reflect the user's combination of Storage Bus and Quartz Fiber capabilities with Federation logic;
this recipe rationale does not add or redefine runtime behavior. The Bridge offers an adjacent-network entry
route; the Router is an alternative, not a mandatory upgrade of the Bridge. The user subsequently replaced the
Router recipe because its original cost appeared lower than the revised Bridge's. Its revised bill uses four native
functional components and four Federation Cables, with a native Logic Processor explicitly requested in the center.
The user replaced the initially selected corner fibers with Federation Cables. The edge
ordering in the table is the documented layout choice; the user specified ingredients, center and shaped crafting.
The user then increased the Router output to four per craft, considering one Router too costly relative to one
Storage Bus producing one Bridge. The ingredients and layout remain unchanged; four-output batch crafting is confirmed.
Provider and Endpoint recipes reuse native device costs without separately charging for their embedded cores again.
The recipe inputs above mean inventory items; preservation of stored device data is not specified by this decision.
Native multipart Provider/Interface forms are not additional direct inputs in these recipes.

The user subsequently requested a lore-based review of Fluix Dust. AE2 describes Fluix as an energy-conversion
material, not a network-protocol or isolation material. Dust is accepted as a processor ingredient, but its
cross-network role would be our component's design, not a canonical property of the dust. Adding more Fluix to
already Fluix-based cables has weaker thematic justification; retain the cable recipe as open for revision.

The earlier cable proposal (four native Fluix ME Glass Cables plus one Fluix Dust yielding four Federation Cables)
is not confirmed. No replacement ingredient or output count has been selected. Each batch of four Routers consumes
four Federation Cables, so its complete manufacturing cost depends on the eventual cable recipe.

Example additional bills for already operating ME networks (existing power, network cables and machines excluded):

- Adjacent connection with one Bridge: 1 native ME Storage Bus, 1 Quartz Fiber, 1 native Logic Processor and 1 Fluix Dust.
- Two Routers plus 16 placed Federation Cables, starting without spare Routers: craft one batch of four Routers.
  Required: 1 native ME Import Bus, 1 native ME Export Bus, 1 native ME Storage Bus, 1 native ME Interface block,
  1 native Logic Processor and 20 Federation Cables (four consumed in the batch, sixteen placed).
  Two Routers remain for later use; this bill uses actual batch inputs, not fractional per-Router costs.
  The cable material bill cannot be expanded until its recipe is chosen.
- One Federation Pattern Provider plus one Endpoint, with connectivity already available: 1 native Pattern Provider,
  1 native ME Interface, 2 native Logic Processors and 2 Fluix Dust.

These are intermediate-item bills, not raw-resource totals; native batch-recipe leftovers are not expanded.
Balance validation, Inscriber input handling and automation, recipe collisions and in-game discoverability still require
implementation-time validation. Future P2P and remote-connection ideas are outside this recipe draft.

## Earlier device acquisition candidates (historical; superseded by confirmed recipes)

Inspection of the current registration code identifies four blocks and one cable-mounted part: Federation Cable,
Router, Pattern Provider, Processing Endpoint, and Bridge (the part). The current README describes release 0.0.3
as having no survival crafting recipes; no recipe resources or recipe-provider implementation were found in the
inspected project source. See [inspection notes](../../.omo/knowledges/survival-playability-discussion-2026-10-03.md).

The following is a proposed starting point for discussion, not approved recipes:

| Device | Candidate acquisition | Cost intention |
|---|---|---|
| Federation Cable | Batch crafting from glass, metal and an AE2 crystal material; optionally convert ordinary AE cable | Affordable repeated placement; avoid a processor cost per cable |
| Federation Bridge | Direct crafting from a small amount of AE2 crystal material, metal and a logic processor | Affordable first connection between adjacent networks |
| Federation Router | Direct crafting using a similar material family, with a modest additional structural cost | Useful alternative connection form; does not require consuming a Bridge as a mandatory progression step |
| Federation Pattern Provider | Convert a native AE2 Pattern Provider with a small amount of additional Federation-related material | Reuse the player's existing investment; avoid charging for another complete provider in the added ingredients |
| Federation Processing Endpoint | Direct crafting from ordinary materials and a logic processor, or conversion of a native ME Interface | Compare the complete cost of repeated machine-side endpoints before choosing the route |

Prefer considering ordinary crafting-table recipes for the initial devices. A new common intermediate component,
new processor, in-world transformation, acquisition through loot, and any reverse conversions remain unselected.
Recipe grids, exact ingredients, counts and outputs require later discussion. Native block conversion is a crafting
proposal, not a decision to preserve an existing placed block's inventory or configuration automatically.

## Evidence boundary

This page records product discussion. The registration/recipe-presence inspection above is narrow; no in-game
survival test was performed. Brainstorming does not authorize implementing recipes or changing runtime behavior.

## Reference-grounded acquisition discussion

The user considered adding a common component, but rejected the assistant's proposed coupling-component names,
ingredient allocation and output counts. They requested studying native AE2, NeoECO, AE2 Lightning Tech and Data
Energistics before further proposals. At that stage no common-component form or recipe was accepted; the later
processor and device decisions are recorded above.

The [source-based acquisition study](../../.omo/knowledges/survival-acquisition-ae2-addon-references-2026-10-03.md)
records four version-scoped examples: AE2 water transformation and Inscriber workflows; NeoECO's energized material
family and processing; Lightning Tech's lightning transformations; and Data Energistics' meteorite, crystal,
circuit and framework routes. Some of their costs, particularly Data Energistics' quantum-singularity processor,
are inappropriate to copy automatically into the confirmed early-access goal.

The subsequent discussion selected a short process using the existing AE2 Inscriber and native Logic Processor.
The remaining recipe-design question is Federation Cable manufacturing; implementation validation is still pending.
