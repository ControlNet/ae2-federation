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

## Guards

- We compile against 21.2.1 (`neoforge-1.21.1/build.gradle`, `neoEcoApi`); `neoforge.mods.toml` declares no
  `neoecoae` range; `NeoEcoSupport.available()` requires >= 21.2.1 with no upper bound; every compat profile pins
  21.2.0 or 21.2.1, so nothing tests 21.2.2+.

## Options (pending the user's decision)

- (a) Ship our own copy of the 21.2.1 atlas (GPLv3 art) under `ae2federation:`: smallest change, look frozen at 21.2.1.
- (b) Rebuild on the public `ComputationControllerUI` with our Federation panel: matches "keep Neo ECO's GUI", but its
  `Config` wants about 30 ECO-CPU suppliers.
- Either way: raise the compile jar and the `neoecoae` compat profiles to 21.2.9 (Modrinth version `26GPFo2j`).
