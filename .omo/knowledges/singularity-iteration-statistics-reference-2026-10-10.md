# Singularity Iteration statistics reference

Inspection date: 2026-10-10.
Repository: https://github.com/2132937983/Singularity-Iteration
Inspected default branch: `0.1.7.30-beta`.
Pinned revision: `b355c64facb419987698333f72fe5da49872a357`.
Method: read repository source; no build, game session, or visual verification performed.
These findings describe this revision, not a verified released artifact.

## Verified energy terminal behavior

- `EnergyTerminalScreen` combines four summary tiles (generation/capacity, consumption/demand,
  storage, and net balance with estimated fill/depletion time), generation/consumption area
  plots, and a charge-fraction line. Hovering the chart exposes sampled values.
- Local mode shows the largest consumers grouped by block type, with item icons and counts.
  Global mode replaces that panel with sub-network summaries. A filterable, scrollable device
  table includes per-device readings and remote switches for supported devices.
- Local scope follows a cable segment; global scope can traverse transformers to summarize
  a multi-voltage system. This is the mod's own energy topology, not an AE2 statistics provider.
- `EnergyTerminalBlockEntity` accumulates engine node readings each server tick and publishes
  averages every 20 ticks. Topology refresh is scheduled every five such windows, with earlier
  invalidation paths. At 20 TPS, these intervals correspond to one and five seconds.
- History consists of 112 fixed-length samples for generation, consumption, and charge fraction.
  The UI labels this as 112 seconds; at lower TPS, elapsed wall time differs.
- Collection lives in the ticking block entity, independently of the menu. The menu sends a full
  snapshot to its viewer when the version changes, normally once per sampling window.
- The collector bounds discovery at 8192 conductors and 256 devices, reports truncation, and
  skips unloaded positions. These are implementation limits, not proposed Federation defaults.
- Storage ports referring to the same multiblock owner are deduplicated for stored-energy totals.
  This is useful precedent for preventing duplicate inventory totals, but does not itself solve
  Federation re-export/shared-storage accounting.
- The block entity's persistence methods save the global/local flag, not history arrays.
  Thus this implementation is an in-memory recent-history monitor, not persistent long-term telemetry.

## Additional equipment monitoring

`EquipmentEnergyMonitor` samples synchronized worn/held electric-item energy on the client.
It keeps 60 history samples, averages live rates over three windows, ranks item drain, and
estimates fill/depletion times. It resets when the player/world is absent.
Its inferred internal transfer correction subtracts the minimum of observed equipment gains
and battery losses. This is a heuristic based on net changes, not authoritative transaction tracing.
Do not equate inventory deltas with gross production/consumption in a Federation design.

## Implications for the statistics idea

Useful candidates are summary + trend + ranking/detail composition, explicit local/global scope,
resource icons, hover values, bounded collection, and separation of sampling from rendering.
The renderer uses Minecraft GUI drawing utilities; a web dashboard runtime is not required for
these particular charts. None of this selects a framework for Federation.

Longer history, persistence, unavailable-data semantics, topology changes, and accurate AE2/Federation
metric definitions remain our own design questions. Switching scope in the inspected terminal does
not explicitly clear its history arrays, which can mix differently scoped samples; a reference should
not be copied without deciding those semantics. Remote control is an existing feature of that mod,
not an approved addition to our statistics idea or a replacement for the automation idea.

## Pinned source links

- [Screen](https://github.com/2132937983/Singularity-Iteration/blob/b355c64facb419987698333f72fe5da49872a357/src/main/java/com/miophas/singularity_iteration/common/client/screen/EnergyTerminalScreen.java)
- [Collector](https://github.com/2132937983/Singularity-Iteration/blob/b355c64facb419987698333f72fe5da49872a357/src/main/java/com/miophas/singularity_iteration/common/blockentity/wiring/terminal/EnergyTerminalBlockEntity.java)
- [Snapshot](https://github.com/2132937983/Singularity-Iteration/blob/b355c64facb419987698333f72fe5da49872a357/src/main/java/com/miophas/singularity_iteration/common/blockentity/wiring/terminal/EnergyNetworkSnapshot.java)
- [Topology](https://github.com/2132937983/Singularity-Iteration/blob/b355c64facb419987698333f72fe5da49872a357/src/main/java/com/miophas/singularity_iteration/common/blockentity/wiring/terminal/NetworkWalker.java)
- [Menu](https://github.com/2132937983/Singularity-Iteration/blob/b355c64facb419987698333f72fe5da49872a357/src/main/java/com/miophas/singularity_iteration/common/menu/wiring/EnergyTerminalMenu.java)
- [Renderer](https://github.com/2132937983/Singularity-Iteration/blob/b355c64facb419987698333f72fe5da49872a357/src/main/java/com/miophas/singularity_iteration/common/client/screen/DspUi.java)
- [Equipment monitor](https://github.com/2132937983/Singularity-Iteration/blob/b355c64facb419987698333f72fe5da49872a357/src/main/java/com/miophas/singularity_iteration/common/client/screen/EquipmentEnergyMonitor.java)
