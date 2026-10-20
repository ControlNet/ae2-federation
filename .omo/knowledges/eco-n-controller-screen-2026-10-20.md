# ECO - N controller screen (option A, 2026-10-20)

The ECO - N4/N6/N9 controllers open Neo ECO's own controller screen (`BlockUIMenuType`, LDLib2), filled with the
computing device's readouts. Board: `EcoNA` on the GUI design canvas (installations row added at y 75).

## How it is wired

- `EcoComputationSystemBlock` no longer overrides `useWithoutItem`: Neo ECO's `ECOComputationSystem.useWithoutItem`
  opens `BlockUIMenuType`, whose `createUI` calls the block entity's `createUI` (virtual, so ours runs).
- `EcoComputationSystemBlockEntity.createUI` builds Neo ECO's builder window (`MultiblockBuilderUI.createFloatingPanel`)
  and hands it to `EcoComputationPanel.create`.
- Neo ECO keeps its `MultiBlockBuildController` and `buildPanel` private. We hold a second controller over the same
  `MultiBlockBuildController.Host` (the block entity's public getters/setters) and tick it from an overridden `tick`.
  Neo ECO's own controller stays idle (no session). The builder builds whatever `getBuildDefinition()` returns: the
  seam for the communication-interface definition wrapper.
- Neo ECO's `ComputationCpuPanel`, `ComputationScrollbar`, `ComputationCpuStatusPanel` are package-private, so
  `EcoComputationPanel` re-draws the same atlas (`neoecoae:textures/gui/eco_cpu_controller.png`: background 0,0 261x214;
  rows 0,222 / 69,222 67x22) with the same coordinates and a copy of the AE2 `Scrollbar` adapter. Public pieces reused:
  `HostSideButtonBar.left`, `GuideButton.create`, `MultiblockBuilderUI`, `HostElements.absolute`, `HostText` colours,
  `NEByteFormatter`, stylesheet `NEStyleSheets.ECO`.
- Sync: one S2C `CompoundTag` snapshot (sampled every 5 ticks on the server; installations, costs, capacity, alias,
  refused alias, domain link). Row selection and scrolling are client-only (the list is read only). The alias travels
  as a numbered `"<n>:<alias>"` string binding, as the Provider screen's requests do (C2S-only bindings break the
  menu's initial data).

## Gotchas

- `TextField.setText` before the field's first layout scrolls the text out of view (`updateDisplayOffset` uses a
  zero content width and never scrolls back). Set the text while drawing, then `setCursor(0)` and
  `setCursor(length)`.
- Replace the field's text only when the server's alias changed; otherwise a refused alias is overwritten before
  the player sees it in red. INVALID = an alias that looks like an ID (`device-xxxxxx`).
- LDLib2's `TextField` shows "Empty" as its default placeholder; set `placeholder(Component.empty())`.
- In the reused computing-budget world the controller at (13, -59, 0) has a Federation cable in front; aim past the
  cable from (12.3, -60, -1.6) facing (13.25, -60.57, 0) to right-click the controller itself.
- A lone controller's Build places Neo ECO's `computation_interface` at (2,1,1) until the definition wrapper lands, so
  the built structure does not form yet.

Evidence: `build/eco-gui-check/before-after.png`, `design-vs-game.png`, `look/build-new.png`, `look/built.png`.
