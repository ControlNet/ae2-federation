# Survival playability

Status: implemented. Recipe and guide resources inspected on 2026-10-03; the processor recipe was revised on 2026-10-06,
and the Nexus chain was rebuilt after AE2's processors and cores on 2026-10-08.

Basic Federation equipment is obtainable using native AE2 materials and machines. The in-game GuideME pages
teach acquisition, connection, configuration and remote processing. The former idea is preserved as an
[archived discussion](../archive/ideas/survival-playability.md); its superseded proposals are not current requirements.

## Recipes

The [recipe resources](../../common/src/main/resources/data/ae2federation/recipe) are authoritative.

| Output | Inputs | Method |
|---|---|---|
| 1 Printed Nexus Circuit | 1 Ender Pearl (`c:ender_pearls`) under the Inscriber Logic Press | Inscriber, inscribe mode: the press stays, like AE2's Printed Logic Circuit |
| 1 Nexus Processor | 1 Printed Nexus Circuit + 1 Redstone Dust (`c:dusts/redstone`) + 1 Printed Silicon | Inscriber: circuit on top, redstone in middle, silicon at bottom (top and bottom may swap); press mode consumes all inputs, like AE2's Logic Processor |
| 2 Nexus Cores | 1 Fluix Crystal (`c:gems/fluix`) + 1 Ender Dust (`c:dusts/ender_pearl`) + 1 Nexus Processor | Shaped: one row in that order, like AE2's Formation Core |
| 8 Federation Cables | 8 ME Glass Cables matching `ae2:glass_cable` + 1 Nexus Core | Shaped: core in center, cables around it |
| 1 Federation Bridge | 1 ME Storage Bus + 1 Quartz Fiber + 1 Nexus Core | Shapeless |
| 1 Federation Switch | 4 Federation Cables + 2 Quartz Fibers + 1 ME Storage Bus + 1 ME Interface + 1 Nexus Core | Shaped: cables in corners, quartz fibers top and bottom, storage bus left, interface right, core center: what a Switch face exchanges, as on the Bridge |
| 1 Federation Router | 4 Federation Cables + 4 Fluix Crystals (`c:gems/fluix`) + 1 Nexus Core | Shaped: cables in corners, crystals on the sides, core center |
| 1 Federation Pattern Provider | 1 native Pattern Provider block + 1 Nexus Core | Shapeless |
| 1 Federation Processing Endpoint | 1 native ME Interface block + 1 Nexus Core | Shapeless |

Every functional device recipe uses the Nexus Core; the Switch and the Router also take four Federation Cables.
Bridge and Switch are alternative ways to attach networks rather than a mandatory upgrade sequence; the Router only
links and branches Federation Cable. Yields follow AE2: two cores per batch, like the Formation Core, and eight cables
from eight ME Glass Cables.

For a first setup with two Switches and sixteen placed Federation Cables, starting with none of these components:
the setup needs 24 Federation Cables (sixteen placed, eight in the two Switches), so three cable batches, and five
Nexus Cores (three for the cables, two for the Switches), so three core batches from three circuits and three
processors. This costs 24 native ME Glass Cables, three each of Ender Pearl, Redstone Dust, Printed Silicon, Fluix
Crystal and Ender Dust, four Quartz Fibers, and two each of the native Storage Bus and ME Interface; the
Inscriber Logic Press is kept. After placing the setup, one Nexus Core remains.
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

The Printed Nexus Circuit, Nexus Processor and Nexus Core icons are the mod's artist's work (2026-10-08), All Rights
Reserved like the rest of the mod's art.

## Verification boundary

This document migration checked source recipes, guide files and document links. It did not repeat game tests or
claim a new gameplay acceptance run. Existing checks include `SurvivalRecipeContractTest`, `GuidePagesContractTest`
and `SurvivalRecipeGameTests`; use the project's [test instructions](../testing/commands.md) for execution.
