# Automation tab: computing-device capacity (2026-10-21)

The user picked option D on the design canvas (row "自动化页签 · 计算设备容量", boards AutoCapA-D and AutoCapDrag):
one row under a device's kind line, `CU 16/128  B/s 160/67.1M  Threads 3/4`, each used/total figure underlined by a
2px bar as wide as the figures. Elastic display (user, 2026-10-21): the B/s figure counts fixed reservations only;
the rest of its bar is dim green while any elastic installation shares it; the installation aside's Cost row gives an
elastic installation's current share, `9 CU · 67.1M B/s shared`.

## Data path

- `DeviceBudget` is public now. `DeviceBudget.Reserved(compute, bandwidth, installations, elastic)` sums what a
  device's installations reserve; `refusal(capacity, Reserved, after)` judges one more installation by the sums alone,
  the same rule as the map form (a test checks they agree). `bandwidth(id)` = fixed reservation, or an elastic
  installation's shares of what is left; `Long.MAX_VALUE` on an unlimited device.
- `ProgramSync.DeviceInfo.budget` (`Budget(capacity, reserved)`), null for interfaces and unloaded devices.
  `InstallationInfo.cost` + `bandwidth` (-1 while the device is not loaded). `FunctionInfo.cost` = what a drop
  would reserve (`ProgramScheduler.dropCost`): pushed version, or the library's closure when never pushed, no
  arguments typed, so parameters count at their defaults. Known gap: a move bounded only by an argument typed after
  the drop previews as elastic; the server still decides on install.
- `ProgramSync.watchCapacities` re-sends the index once a second while the tab is open when any device's capacity
  (or loaded state) changed: breaking a part or pulling a cell otherwise never told the tab.

## Client

- `FederationInstallView.meters`: hidden for unlimited (Creative) and unloaded devices; dashes for `Capacity.NONE`.
  A zero capacity draws an empty bar, or a full red one while something is reserved on it.
- While a function is dragged, each card ring is green when `DeviceBudget.refusal` is empty and red otherwise; the
  hovered card previews the cost as a light segment on its bars.
- An unformed ECO - N system leaves the domain (its interface stops linking), so in practice the dash state is a
  fallback.

## In-game check

`build/device-capacity-check/before-after.png` in the feature worktree (cost, drag, over). World: copy of
`device-kind-check`; the N4 at (13,-59,0) got an interface at (12,-59,1) and a cable to the router; a CE4 cell
(`neoecoae:eco_computation_cell_l4`) goes in a drive by right-clicking it with the cell. `data merge` on the drive's
`managed.cellStack` does not take. Over capacity: replace the drive with an empty one (`setblock air`, then the
drive). `tools/visual/game_input.py close` closes the Minecraft window; press Escape with XTEST instead.
