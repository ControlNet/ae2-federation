# Neo ECO upstream releases vs the ECO - N screen (2026-10-21)

Investigated by a subagent against the 21.2.1, 21.2.2, 21.2.3, 21.2.7 and 21.2.9 jars (compiled our `automation/neoeco`
package against each, diffed sources and resources). Upstream: DancingSnow0517/NeoECOAEExtension, Modrinth `udZtKfzP`.

## How our screen is built

- `EcoComputationSystemBlockEntity` subclasses Neo ECO's `ECOComputationSystemBlockEntity` and overrides
  `createUI(BlockUIHolder)`; it builds Neo ECO's builder window with `MultiblockBuilderUI.createFloatingPanel`.
- `EcoComputationPanel` is a **copy** of Neo ECO 21.2.1's package-private `ComputationCpuPanel` /
  `ComputationHostPanelUI` (same layout constants, sprite rectangles, colours), not an extension. Upstream deleted
  both classes in 21.2.3.
- It reads the atlas `neoecoae:textures/gui/eco_cpu_controller.png` and lang keys `gui.neoecoae.host.network.*`;
  `HostText` colours are compile-time constants inlined into our bytecode.

## What changed upstream

- 21.2.2 (10-11): the atlas moved to `textures/guis/eco_cpu_controller.png`. Our path no longer exists, so the
  panel background and row sprites are missing on 21.2.2 and later (not run in game; LDLib2 most likely draws the
  missing texture rather than crashing).
- 21.2.3 (10-14): new computation controller screen, public `ComputationControllerUI.create(Config, ...)`; the
  atlas art was redrawn, so pointing at the new path would cut the wrong regions.
- 21.2.7 / 21.2.9 (latest, 10-20): panel-texture extension points, `IECOTier.getPanelAtlas`, `ECOModularUI`.
- Everything else we use (structure calculator, `MultiBlockDefinition`/`Context`, `ECOTier` L4/L6/L9, cluster
  getters, `createUI`, CPU-lifecycle reflection in `CraftingCpuLifecycle`) compiles, links and resolves on 21.2.9.
- `NEBlock` no longer gives formed blocks light level 1 (shading differs slightly).

## In-game check on 21.2.9 (2026-10-21)

`build/neoeco-gui-check/compare.png` in the feature worktree: our N4 on 21.2.1, our N4 on 21.2.9, Neo ECO's C4 on
21.2.9. On 21.2.9 our screen opens and its data is right, but the background is black (no panel, no inventory slot
art) and the installation rows are the magenta missing texture; no crash, nothing logged. The side bar picks up
Neo ECO's new button art. The new ECO-C screen: side bar (guide, builder, CPU mode, frequency, CPU list), title +
tier badge, host column + 11-column drive grid, Controls (Swap/Cycle/Fast), CPU/Threads/Parallel panels with bars,
inventory, and a status box (Online/Off, Freq, switch modules); an "i" endgame tooltip and a CPU-list drawer.

`ComputationControllerUI.create(Config, ...)`: `Config` is a 34-component positional record (all ECO-CPU suppliers);
layout fixed by a private JSON, all text and housings painted by one package-private artwork element, so a caller can
change data and textures (`IECOTier.getPanelAtlas`, `IHostPanelTextureProvider`) but not layout or labels. Element ids
(`computation_main`, `computation_toolbar`, ...) allow removing/adding children after `create`, but are not a
contract. Only the drive grid and maybe the three stat panels map onto ECO - N; ID/alias/installations have no slot.

Aiming in that world: a cable at (13,-59,-1) sits in front of the N4 controller and the player crawls under the
overhang, so `tp 13.5 -60 -2.5 facing 13.5 -59.1 0` is what hits the controller (check with F3 "Targeted Block").

## Decision and fix (user, 2026-10-21: option a)

- 89376e7b: the panel draws from `ae2federation:textures/gui/eco_computation_controller.png`, an unmodified copy of
  the 21.2.1 atlas (sha256 4b8634d8...), with `eco_computation_controller_license.txt` beside it (GPLv3 text; our
  AGPL-3.0 combines under section 13 of each). Before/after on 21.2.9: `build/neoeco-gui-check/before-after.png`.
- The side bar still uses Neo ECO's own button art, so its icons follow whichever Neo ECO is installed.
- Compile jar and the `neoecoae`, `neoecoprototype`, `addons-all` compat profiles moved to 21.2.9 (Modrinth
  `26GPFo2j`); `useless-mod-addons` stays on 21.2.0 on purpose (requested-amount fallback). NeoECOPrototype 1.3.3
  accepts `[21.2.1-beta2,)`. LDLib2 stays 2.2.34 for compiling; Neo ECO 21.2.2+ needs LDLib2 >= 2.2.40 at run time,
  which the profiles (2.2.41) provide.
- Not adopted: rebuilding on `ComputationControllerUI` (option b). Revisit only if upstream adds layout extension
  points; a drive grid inside our own panel is the cheaper way to get in-screen cell handling.

## Guards

- `neoforge.mods.toml` declares no `neoecoae` range; `NeoEcoSupport.available()` requires >= 21.2.1 with no upper
  bound.
