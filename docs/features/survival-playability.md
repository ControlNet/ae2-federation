# Survival playability

Status: implemented. Recipe and guide resources inspected on 2026-10-03.

Basic Federation equipment is obtainable using native AE2 materials and machines. The in-game GuideME pages
teach acquisition, connection, configuration and remote processing. The former idea is preserved as an
[archived discussion](../archive/ideas/survival-playability.md); its superseded proposals are not current requirements.

## Recipes

The [recipe resources](../../common/src/main/resources/data/ae2federation/recipe) are authoritative.

| Output | Inputs | Method |
|---|---|---|
| 1 Federation Logic Processor | 1 native Logic Processor + 1 Fluix Dust | Inscriber: processor in middle, dust on top, bottom empty; press mode consumes inputs |
| 16 Federation Cables | 8 ME Glass Cables matching `ae2:glass_cable` + 1 Federation Logic Processor | Shaped: processor in center, cables around it |
| 1 Federation Bridge | 1 ME Storage Bus + 1 Quartz Fiber + 1 Federation Logic Processor | Shapeless |
| 4 Federation Routers | 4 Federation Cables + 1 ME Import Bus + 1 ME Export Bus + 1 ME Storage Bus + 1 ME Interface + 1 native Logic Processor | Shaped: cables in corners, import top, export bottom, storage left, interface right, native processor center |
| 1 Federation Pattern Provider | 1 native Pattern Provider block + 1 Federation Logic Processor | Shapeless |
| 1 Federation Processing Endpoint | 1 native ME Interface block + 1 Federation Logic Processor | Shapeless |

The Router uses the native Logic Processor. The other functional device recipes use the Federation Logic Processor.
Bridge and Router are alternative connection forms rather than a mandatory upgrade sequence.

For a first setup with two Routers and sixteen placed Federation Cables, starting with none of these components:
craft two cable batches and one Router batch. This costs sixteen native ME Glass Cables, three native Logic Processors,
two Fluix Dust, and one each of the native Import Bus, Export Bus, Storage Bus and ME Interface. Four Federation Cables
are consumed in the Router craft. After placing the setup, two Routers and twelve Federation Cables remain.
This bill excludes the existing ME networks and manufacturing equipment, and does not expand native device recipes.

## In-game guide

The [English guide](../../common/src/main/resources/assets/ae2federation/ae2guide/index.md) and
[Simplified Chinese guide](../../common/src/main/resources/assets/ae2federation/ae2guide/_zh_cn/index.md) cover getting
started, mechanics, remote processing, troubleshooting and all six items/blocks. Structure assets are stored with
the guide, and item pages reference actual recipes. Maintain player instructions there alongside behavior changes.

For Minecraft 1.21.1, JEI users need
[AE2 JEI Integration](https://www.curseforge.com/minecraft/mc-mods/ae2-jei-integration) to view AE2 machine recipes.
The user confirmed this resolved missing Inscriber recipes. This is viewer-specific guidance, not a mandatory
Federation dependency. See the [diagnostic record](../../.omo/knowledges/jei-inscriber-visibility-2026-10-03.md).

## Processor artwork

![Federation Logic Processor](../art/assets/federation_logic_processor.png)

The [selected source icon](../art/assets/federation_logic_processor.png) is retained with art assets; the
[runtime texture](../../common/src/main/resources/assets/ae2federation/textures/item/federation_logic_processor.png)
is in the mod resources. The 16x16 transparent icon derives from AE2 19.2.17's Logic Processor texture at commit
`db95d25ccc79f7bd55b504cf71522b57d60bf4f7`, retaining its silhouette and gold base with twelve edited pixels using
the native Fluix palette. It was edited directly without image generation.

## Verification boundary

This document migration checked source recipes, guide files and document links. It did not repeat game tests or
claim a new gameplay acceptance run. Existing checks include `SurvivalRecipeContractTest`, `GuidePagesContractTest`
and `SurvivalRecipeGameTests`; use the project's [test instructions](../testing/commands.md) for execution.
