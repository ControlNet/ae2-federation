# Survival playability discussion boundary (2026-10-03)

The user corrected an initial brainstorm about distributed factories and multiplayer cooperation: the intended
topic is making the mod obtainable and usable in survival, including recipes, material costs and progression.
Asked to choose between early basic-AE2 access and a later mature-automation upgrade, the user selected early access.

Maintain the confirmed direction and unconfirmed suggestions in
[the survival idea](../../docs/ideas/survival-playability.md). Do not treat the assistant's earlier mid-game preference,
a tier system, storage-only entry functionality or any proposed recipe ingredients as accepted requirements.
No current implementation/recipe audit or game validation was performed in this discussion.

## Follow-up: current acquisition surface

The user subsequently agreed with early core availability and identified each block's acquisition route as the
main question. A read-only source inspection then established:

- `RouterRegistration.java` registers `router` and `cable` blocks and their block items.
- `ProcessingRegistration.java` registers `pattern_provider` and `processing_endpoint` blocks and block items.
- `BridgeRegistration.java` registers `bridge` as an AE2 `PartItem`, not a fifth standalone block.
- `README.md` explicitly says release 0.0.3 has no survival crafting recipes. Project file searches found no recipe
  resources; searches for `RecipeProvider`, `ShapedRecipe` and `ShapelessRecipe` found no implementation in the
  inspected common source/build files. This is source inspection, not an in-game recipe-manager test.
- The README documents Router or adjacent Bridge connections as basic entry routes; the Pattern Provider/Endpoint
  pair is for distributed processing. Do not invent a compulsory upgrade chain connecting all five items.
- `gradle.properties` pins Minecraft 1.21.1 / AE2 19.2.17. Exact ingredient progression and dependency recipes were
  not audited; proposed ingredients must not be presented as verified balanced recipes.

Useful inspection commands from the repository root:

```sh
rg --files common/src/main | rg 'Registration.java|recipe/|recipes/'
rg -n 'registerBlock|PartItem' common/src/main/java/space/controlnet/ae2federation/{router,bridge,processing}/*Registration.java
rg -n 'survival crafting recipes|Getting started|Remote processing' README.md
```

Expected signals: four registered blocks and one Bridge part; README's missing-survival-recipes statement; no
recipe-resource paths in this snapshot. Proposed acquisition routes are retained in the idea page; no recipe or
production-code changes were made.

## User rejection and requested reference work

The user rejected the later speculative common-component proposal and asked for AE2, NeoECO, Lightning Tech and
Data Energistics references before further discussion. Earlier candidate acquisition tables and coupling-component
names/counts are not accepted design. See the subsequent
[source-based study](survival-acquisition-ae2-addon-references-2026-10-03.md) and the current idea page.

## Follow-up: reuse native processor production

The user proposed Federation Logic Processor (联邦逻辑处理器), then expressed a preference for further processing
native AE2 processors instead of extensive original recipe design. The preceding candidate specifically used the
native Logic Processor as input. Preserve this reuse direction alongside the early-availability requirement.
Additional ingredients, processing station, quantities and consuming device recipes remain undecided. A separate
printed circuit, press, material family or machine is not an implicit requirement. No implementation was requested.

## Concrete recipe design evidence

For the subsequent recipe-design request, inspected AE2 19.2.17 generated recipes under
`network/blocks/interfaces_interface.json`, `network/blocks/pattern_providers_interface.json`,
`network/parts/quartz_fiber_part.json` and `network/cables/glass_fluix.json` in the source checkout
`/tmp/ae2-19.2.17`. The native Interface uses four iron, two glass, one Formation Core and one Annihilation Core.
The native Pattern Provider uses four iron, two crafting tables and those two cores. One Quartz Fiber plus two
Fluix ingredients produces four uncolored ME Glass Cables. Six glass and three quartz dust produce three fibers.
The Inscriber recipe class exposes optional top and bottom ingredients; the proposed new processor recipe still
requires machine/automation validation when implemented. The current six-recipe proposal and intermediate-item
bills are in the idea page, explicitly unaccepted; no recipe resources or production code were added.

## Subsequent recipe acceptance

The user accepted Fluix Dust for the Federation Logic Processor and revisiting the cable recipe, then specified
one native ME Storage Bus + one Quartz Fiber + one Federation Logic Processor for the Bridge and accepted the
other recipes. The current idea page records five confirmed recipes: processor, Bridge, Router, Provider and Endpoint.
The earlier cable conversion is not accepted; do not interpret "the others" as reversing the immediately preceding
decision to revisit it. Router cost remains four Federation Cables, four iron and one Federation Logic Processor.
The adjacent-connection bill now includes a Storage Bus and one fiber instead of two fibers. Bills involving cable
must stop at Federation Cable quantities until its production recipe is selected. No runtime or recipe implementation
was requested or performed.

## Router recipe revision after acceptance

The user then replaced the Router recipe after observing that the previous recipe appeared cheaper than the Bridge.
The new shaped recipe uses four Quartz Fibers, one native ME Import Bus, one native ME Export Bus, one native ME
Storage Bus, one native ME Interface and one native Logic Processor in the center. Preserve the explicit native
processor input; do not silently substitute the Federation Logic Processor. These nine ingredient units fit a 3x3
grid. Documentation places fibers in corners and Import/Export/Storage/Interface at top/bottom/left/right respectively;
that edge ordering is an assistant layout choice. Router crafting no longer consumes Federation Cables. The two-Router
plus 16-cable bill now uses 16 cables, not the earlier 24. This supersedes the preceding Router acceptance only.

### Final corner substitution

The user then explicitly replaced all four corner Quartz Fibers with Federation Cables. The native Logic Processor
and four functional components remain unchanged. This supersedes the preceding fiber-corner recipe and its cost
statement: two Routers plus sixteen placed cables require twenty-four Federation Cables in total. The cable's own
manufacturing recipe remains undecided. The current recipe table and bill in the idea page reflect this correction.

### Router batch output increased to four

The user subsequently selected four Routers per craft, judging one too expensive relative to the Bridge's one
Storage Bus per output. Preserve the same nine-slot ingredient layout and native Logic Processor. This supersedes
the previous single-output cost calculation. A first setup needing two Routers and sixteen placed cables requires
one batch: one each of the Import Bus, Export Bus, Storage Bus, ME Interface and native Logic Processor, plus twenty
Federation Cables total. Four cables are consumed in crafting, sixteen are placed, and two Routers remain unused.
No game recipes were implemented.

## Selected processor texture preserved

The user approved the directly edited 16x16 processor icon and explicitly requested moving it from temporary
storage into the idea directory. The unchanged selected PNG now lives at
`docs/ideas/assets/federation_logic_processor.png`, linked from the survival idea. It derives from AE2 19.2.17
commit `db95d25ccc79f7bd55b504cf71522b57d60bf4f7`, preserving the native Logic Processor alpha mask and changing
twelve pixels with native Fluix colors. No image-generation tool was used. The file move was verified by SHA256;
the asset has not been integrated into runtime resources. Temporary comparison previews are not the selected texture.

## GuideME scope and reference inspection

The user explicitly added AE2-style GuideME documentation to the survival-playability idea. Recorded this as confirmed
scope, with suggested content and validation criteria in the idea page rather than implementing integration now.
AE2 19.2.17's `guidebook/index.md` groups getting started, mechanics, example setups and item/block pages, and embeds
an interactive GameScene importing an SNBT structure. Previously inspected item pages use item links and recipe
components. A targeted search in this project's common sources and build files found GuideTooltipThreadMixin for
GuideME tooltip compatibility, but no guide content/registration in that search. A compatibility mixin alone does
not establish a working Federation guide. This was a narrow inspection, not an integration test.
Sources: https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/db95d25ccc79f7bd55b504cf71522b57d60bf4f7/guidebook/index.md
and https://guideme.appliedenergistics.org/integration/ . Check APIs against the pinned project versions before implementation.
