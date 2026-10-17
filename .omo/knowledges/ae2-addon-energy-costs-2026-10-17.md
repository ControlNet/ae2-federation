# AE2 and addon energy costs (2026-10-17)

Reference for the [operating-power idea](../../docs/ideas/federation-operating-power.md). Read from bytecode and config
defaults of AE2 19.2.17 and the AE2 addons in `tests/compat/mods.json`; no jar code was executed. Values are AE/t
unless marked per op. Unconfirmed values are marked unverified.

## Base rules

- A grid node that sets no cost pays **1.0 AE/t** (`appeng.me.ManagedGridNode$InitData.<init>`). Pattern Provider,
  Interface, every crafting-CPU block, buses, storage bus and IO Port use it.
- **1 AE = 2 FE** (`powerRatios.forgeEnergy = 0.5`).

## Devices

| Mod | Device | Cost | Scaling | Source |
|---|---|---|---|---|
| AE2 | Cables, quartz fiber, toggle bus | 0 | — | `CablePart`, `QuartzFiberPart`, `ToggleBusPart` ctors |
| AE2 | Controller | 3.0 per block | — | `ControllerBlockEntity.<init>` |
| AE2 | Terminal / panel | 0.5 / 0.0625 | — | `AbstractReportingPart.<init>` |
| AE2 | P2P: ME, item/fluid/FE / redstone, light | 2.0 / 2.0 / 0.5 | energy tunnels lose 2.5 % | `*P2PTunnelPart.getPowerDrainPerTick` |
| AE2 | Drive | 2.0 + cells (0.5–2.5 by tier) | — | `DriveBlockEntity.updateState` |
| AE2 | Molecular Assembler | 0 idle; 100–500 AE per craft | speed cards | `MolecularAssemblerBlockEntity` |
| AE2 | Wireless Access Point | 8 + b^(1+b/64) | b boosters; range 16 + b^1.5 | `AEConfig.wireless_getPowerDrain` |
| AE2 | Wireless terminal (own battery) | 1 × distance | linear | `WirelessTerminalMenuHost` |
| AE2 | Spatial Anchor | 80 + n(n+1)/2 | n loaded chunks | `SpatialAnchorBlockEntity` |
| AE2 | Quantum Network Bridge | 22.0 per block, 198 per formed side, 396 per pair | no distance or dimension term | `QuantumBridgeBlockEntity.<init>` |
| ExtendedAE | Wireless Connector | max(1, d·ln d·(1−0.1E−0.2G)) | distance, energy cards; ≤ 1000, same dimension | `TileWirelessConnector.updatePowerUsage` |
| ExtendedAE | Assembler Matrix casing / function | 0.5 / 1.0 per block | — | `TileAssemblerMatrix*` |
| ExtendedAE Plus | Wireless Transceiver | 100 flat | range 256, cross-dimension ignores distance | `ModConfigs.wirelessIdlePower` |
| AE2 Lightning Tech | Overloaded Pattern Provider / Interface | 5; wireless +5 +1 per link | — | `recomputeIdlePower` |
| AdvancedAE | Quantum computer blocks | 1.0 per block | — | default |
| Neo ECO | Multiblock blocks / cores / storage controller | 16 / 64 / 256 + 2^(1+4·tier) | — | `NEBlockEntity.onReady` and subclasses |
| MEGA Cells | 1M–256M cells | 3.0–5.0 | tier | `MEGAItems.tier` |
| Data Energistics | Beam former part / directional / omni | 1 / 2 / 4, doubled per range card | range 64/64/128 + 8 per card | `BeamDeviceKind` |
| Omnisequence | Quantum-linked machine | +512 while linked | — | `updateQuantumLink` |

Applied Mekanistics, appliedenhancements, ae2extras and ae2wtlib have no power code of their own.
Unverified: Energy Acceptor throughput, Inscriber per-operation cost, Omnisequence's `IDLE_POWER` default.

## Quantum Network Bridge unloading

`onChunkUnloaded()` → `disconnect(false)` → `QuantumCluster.destroy()`, which re-runs `updateStatus(true)`: the
connection is destroyed and the frequency unregistered. `QuantumCluster.onUnload(LevelEvent.Unload)` does the same for
a whole dimension. It reconnects on load (`onReady`, then `serverTick` → `updateStatus`). The partner is found through
one global frequency map, so dimension does not matter. It never loads chunks; only `SpatialAnchorBlockEntity` and
`ChunkLoadingService` use tickets.

## Bands

- Passive parts 0–0.5; ordinary devices 1–8; multiblocks per block 0.5–1 (AE2, AdvancedAE, ExtendedAE), 16–64 (Neo ECO),
  22 (QNB).
- Wireless: flat (QNB 198 per side, ExtendedAE Plus 100, Omnisequence +512) or distance-scaled (ExtendedAE d·ln d,
  wireless terminal 1 per block, Data Energistics doubling per range card).
