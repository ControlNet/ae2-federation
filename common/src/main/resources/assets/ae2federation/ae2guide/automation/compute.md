---
navigation:
  parent: automation/index.md
  title: Compute and Bandwidth
  icon: ae2federation:creative_computing_device
  position: 50
---

# Compute and Bandwidth

A survival computing device has three limits: **compute**, counted in CU, **bandwidth**, counted in bytes a second
(B/s), and **processes**, one for each installation. Each installation reserves its share when it is installed, and a
device that cannot hold it refuses the installation. The
<ItemLink id="ae2federation:creative_computing_device" /> has no limits.

The game works out the cost from the program itself; you cannot set it.

## Compute

An installation costs one CU for each card in the graph of the installed function and of every function it calls, each
counted once. A condition trigger's function counts too. A card inside a loop counts once, however often the loop runs.
Compute only decides whether an installation fits; every run still takes at most 10,000 steps a tick.

## Bandwidth

Bandwidth counts what a function moves in AE2 bytes, as AE2's storage cells count them: 8 items or 8,000 mB of fluid
to a byte. Only `ae2.import_from`, `ae2.export_to`, `ae2.move` and `mc.move` count; crafting, redstone, blocks and FE
are free.

An installation reserves 20 times the largest move among its calls, since a run makes at most one move a tick.

<FederationProgram src="../assets/programs/stock_farm.ae2f" />

This function moves at most 64 items a call, `min(missing, 64)`: 8 bytes, so it reserves 160 B/s.

* The game bounds an amount through constants, installation arguments, arithmetic, `min` and the conditions around
  the call. `target - ae2.amount(item, farm)` under `if missing > 0` is at most `target`.
* Water named in the program, or given as an argument, counts as fluid; an `Item` parameter counts as items. A
  resource read from the world counts as the kind with the most bytes.

## Elastic installations

A move whose amount the game cannot bound, such as `stack.amount` read from a chest, makes the installation
**elastic**.

<FederationProgram src="../assets/programs/empty_chest.ae2f" />

An elastic installation reserves no fixed bandwidth. The bandwidth left over after the fixed reservations is split
evenly between the device's elastic installations, a Parallel one taking 4 shares, and each move of a run is cut to
its share divided by 20. The shares are split again whenever installations or the device change; a run already going
keeps the share it started with, and the next runs take the new one.

## Parallel

An installation set to Parallel reserves 4 times its compute and fixed bandwidth, one for each run it may have.

## When a device shrinks

Taking a part or a cell out of a device keeps every installation. While the device holds less compute or bandwidth
than the fixed reservations, or fewer processes than installations, the whole device pauses: runs wait where they are,
and triggers, events included, start their runs once it resumes. Each installation's log says "paused", then
"resumed" once the device holds enough again.
