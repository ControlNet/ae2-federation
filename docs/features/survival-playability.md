# Survival playability

Status: implemented. Recipe and guide resources inspected on 2026-10-03; the processor recipe was revised on 2026-10-06,
and the Nexus chain was rebuilt after AE2's processors and cores on 2026-10-08. The recipe table and the first-setup bill
were brought up to date with the Beam Converter, Beam Former and Quantum Cube on 2026-11-03.

Basic Federation equipment is obtainable using native AE2 materials and machines. The in-game GuideME pages
teach acquisition, connection, configuration and remote processing. The former idea is preserved as an
[archived discussion](../archive/ideas/survival-playability.md); its superseded proposals are not current requirements.
The processor rename and subsequent Nexus recipe revisions are preserved in the
[archived Nexus discussion](../archive/ideas/nexus-processor.md). The recipes below describe the implemented chain.

## Recipes

The [recipe resources](../../common/src/main/resources/data/ae2federation/recipe) are authoritative.

| Output | Inputs | Method |
|---|---|---|
| 1 Inscriber Nexus Press | 1 Inscriber Engineering Press (top) + 1 Ender Pearl (`c:ender_pearls`, middle) + 1 Inscriber Logic Press (bottom); top and bottom may swap | Inscriber, press mode: both presses are spent, like Advanced AE's Quantum Press. Not in `ae2:inscriber_presses`, so meteorites do not drop it |
| 1 Inscriber Nexus Press | 1 Block of Iron under the Inscriber Nexus Press | Inscriber, inscribe mode: the press stays, the copy every AE2 press has |
| 1 Printed Nexus Circuit | 1 Ender Pearl (`c:ender_pearls`) under the Inscriber Nexus Press | Inscriber, inscribe mode: the press stays, like AE2's Printed Logic Circuit |
| 1 Nexus Processor | 1 Printed Nexus Circuit + 1 Redstone Dust (`c:dusts/redstone`) + 1 Printed Silicon | Inscriber: circuit on top, redstone in middle, silicon at bottom (top and bottom may swap); press mode consumes all inputs, like AE2's Logic Processor |
| 2 Nexus Cores | 1 Fluix Crystal (`c:gems/fluix`) + 1 Ender Dust (`c:dusts/ender_pearl`) + 1 Nexus Processor | Shaped: one row in that order, like AE2's Formation Core |
| 8 Federation Cables | 8 ME Glass Cables matching `ae2:glass_cable` + 1 Nexus Core | Shaped: core in center, cables around it |
| 1 Federation Bridge | 1 ME Storage Bus + 1 Quartz Fiber + 1 Nexus Core | Shapeless |
| 1 Federation Switch | 4 Federation Cables + 2 Quartz Fibers + 1 ME Storage Bus + 1 ME Interface + 1 Nexus Core | Shaped: cables in corners, quartz fibers top and bottom, storage bus left, interface right, core center: what a Switch face exchanges, as on the Bridge |
| 1 Federation Router | 4 Federation Cables + 4 Fluix Crystals (`c:gems/fluix`) + 1 Nexus Core | Shaped: cables in corners, crystals on the sides, core center |
| 1 Beam Converter | 4 Federation Cables + 4 Energy Acceptors + 1 Nexus Core | Shaped: cables in corners, acceptors on the sides, core center |
| 1 Beam Former | 6 Federation Cables + 1 Fluix Pearl + 1 Wireless Receiver + 1 Nexus Core | Shaped: cables in both side columns, pearl top center, receiver center, core bottom center |
| 8 Quantum Cubes | 4 Federation Cables + 3 ME Quantum Rings + 1 ME Quantum Link Chamber + 1 Nexus Core | Shaped: cables in corners, rings top center and on the sides, link chamber center, core bottom center |
| 1 Federation Pattern Provider | 1 native Pattern Provider block + 1 Nexus Core | Shapeless |
| 1 Federation Processing Endpoint | 1 native ME Interface block + 1 Nexus Core | Shapeless |

Every functional device recipe uses the Nexus Core; the Switch, Router, Beam Converter, Beam Former and Quantum Cube
also take Federation Cables. Bridge and Switch are alternative ways to attach networks rather than a mandatory upgrade
sequence; the Router links Federation Cable, Switches and other Routers without attaching a network of its own
([Federation Switch](federation-switch.md)). The Federation P2P Tunnel has no recipe: Federation Cable attunes any P2P
tunnel. Yields follow AE2: two cores per batch, like the Formation Core, and eight cables from eight ME Glass Cables.

For a first setup with two Switches, sixteen placed Federation Cables and a 1x1x2 Beam Converter (operating power is
on by default, [operating power](operating-power.md)), starting with none of these components: the setup needs 32
Federation Cables (sixteen placed, eight in the two Switches, eight in the two converter blocks), so four cable
batches, and eight Nexus Cores (four for the cables, two for the Switches, two for the converter blocks), so four core
batches from four circuits and four processors. This costs 32 native ME Glass Cables, four each of Ender Pearl,
Redstone Dust, Printed Silicon, Fluix Crystal and Ender Dust, four Quartz Fibers, eight Energy Acceptors, and two
each of the native Storage Bus and ME Interface, plus once an Inscriber Engineering Press, an Inscriber Logic Press and
a fifth Ender Pearl for the Inscriber Nexus Press, which is kept. No Nexus Core remains.
This bill excludes the existing ME networks, the FE source and manufacturing equipment, and does not expand native
device recipes.

## In-game guide

The [English guide](../../common/src/main/resources/assets/ae2federation/ae2guide/index.md) and
[Simplified Chinese guide](../../common/src/main/resources/assets/ae2federation/ae2guide/_zh_cn/index.md) cover getting
started, mechanics, remote processing, troubleshooting and every item and block. Structure assets are stored with
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
