# Straight-line link ranges in other mods (2026-10-17)

Source survey for the [line-of-sight beam links](../../docs/archive/ideas/line-of-sight-beam-links.md) idea. Every value comes
from source code at the commit listed; anything not found in code is marked unverified.

## ME Beam Former carries a real grid link

GaLicn/ME-Beam-Former (CurseForge project 1351545) scans the line each server tick and calls
`GridHelper.createConnection(myNode, otherNode)` (`BeamFormerBlockEntity.java` L234); the node is `DENSE_CAPACITY`
(L41, 32 channels). Range is `MAX_BEAM_RANGE = 32` on both 1.20.1 and 1.21.1. The CurseForge page's "client-only
visuals" note refers to the beam's look, not the connection.

## Comparison

| Mod / device | Max range | Alignment | Obstruction (recheck) | Unloaded chunks | X-dim | Energy |
|---|---|---|---|---|---|---|
| ME Beam Former | 32, hardcoded | Same axis, facing exactly opposite | `canOcclude() && !isAir()` (glass passes); full rescan every tick | Own chunk unloads → disconnect; scan has no loaded check (unverified) | No | AE2 node default 1 AE/t |
| ME Beam Former, Omni | Box dx,dz ≤ 16, dy ≤ 32 | Any; binding tool | None | Link kept, skipped while unloaded | No | Default |
| NAE2 Beam Former (1.12) | 32, hardcoded | Facing pair | Fully opaque blocks (glass passes); event-driven plus 20–300 tick polling | Unverified | No | `max(2, len)^1.05` AE/t, scales with distance |
| BeamFormers (1.12) | Config 32 (1–256) | Facing pair | Opaque blocks (glass passes); event-driven | Scan stops at the first unloaded chunk, resumes on load | No | Unverified |
| Mekanism Laser | Config 64 (1–1024) | 6 facings | First block with an outline (glass blocks); every tick while firing | Unverified | No | Lossless |
| Actually Additions Laser Relay | 15; 35 with upgrade | Any | None | Skipped while unloaded | No | 5/8/10 % loss per hop |
| LaserIO node / advanced | 8 / unlimited | Any | None | Both ends must be loaded | Advanced only | None |
| Integrated Dynamics Mono-Directional | Config 512 | Facing pair | None found | Unverified | No | 32 per tick, flat |
| Draconic Evolution crystals | 32 / 64 / 127 by tier | Any | None | Link kept, skipped while unloaded | No | None found |
| Immersive Engineering wires | 16 / 16 / 32, config | Any | Checked on placement; a placed block breaks the wire | Proxies stand in (inference) | No | Loss per 16 blocks |
| ExtendedAE Wireless Connector | Config 1000 (10–10000) | Any | None | Both ends loaded (inference) | No | `max(1, d·ln d)` AE/t, scales |
| Powah Energizing Rod | 4, config | Any | None | Loaded only | No | n/a |
| AE2 Wireless Access Point | 16 + boosters^1.5 (terminal range) | n/a | None | n/a | No | 8 base, grows |

Create and Thermal have no straight-line link. No surveyed mod scales range with beam width.

## Findings

- Every AE2 straight-beam mod uses 32 blocks. Point-to-point linkers sit at 8–35; non-line-of-sight wireless 64–1000+.
- Existing scaling is by tier, upgrade card or booster count. Cost scales with distance in NAE2, ExtendedAE and IE.
- Glass usually passes (opacity-based); Mekanism is the exception.
- Unloaded chunks: disconnect (ME Beam Former), stop the scan and resume on load (BeamFormers), keep the link and
  skip it (Draconic, Actually Additions, LaserIO), or keep topology through proxies (IE).
- Rescanning a 3x3 beam every tick means nine columns; event-driven rechecks (BeamFormers, NAE2) scale better.

## Sources (commit, path under `src/main/java`)

1. GaLicn/ME-Beam-Former @71c1a53 (1.20.1), @ab64564 (1.21.1): `com/mebeamformer/blockentity/BeamFormerBlockEntity.java`
   L28, L41, L160, L234, L333; `OmniBeamFormerBlockEntity.java` L80; `item/LaserBindingTool.java` L429
2. AE2-UEL/NAE2 @2fe8ded: `co/neeve/nae2/common/parts/implementations/PartBeamFormer.java` L98, L194, L375
3. romanfedyniak/BeamFormers @7c63bc0: `com/beamformers/BeamFormersConfig.java` L15, L19; `former/BeamEnd.java` L115–133, L308
4. mekanism/Mekanism @bcd7a8b: `mekanism/common/config/GeneralConfig.java` L236; `tile/laser/TileEntityBasicLaser.java` L102
5. Ellpeck/ActuallyAdditions @cd9d9c6: `mod/tile/TileEntityLaserRelay.java` L34–35; `mod/items/ItemLaserWrench.java` L59;
   `mod/config/CommonConfig.java` L57
6. Direwolf20-MC/LaserIO @4d278a6: `common/items/LaserWrench.java` L23, L93–99; `common/blockentities/LaserNodeBE.java` L570
7. CyclopsMC/IntegratedDynamics @cea2160: `GeneralConfig.java` L45, L47, L110; `part/PartTypeConnectorMonoDirectional.java` L119–123
8. Draconic-Inc/Draconic-Evolution @6251e32: `blocks/energynet/tileentity/TileCrystalBase.java` L123, L298–305
9. BluSunrize/ImmersiveEngineering @75a27f0: `common/config/IEServerConfig.java` L56–64, L91
10. GlodBlock/ExtendedAE @1fb32a6: `config/EAEConfig.java` L37; `common/tileentities/TileWirelessConnector.java` L78–80
11. Technici4n/Powah @4274274: `owmii/powah/config/v2/PowahConfig.java` L60
12. AppliedEnergistics/Applied-Energistics-2 @b141e91: `appeng/core/AEConfig.java` L653–655; `appeng/me/ManagedGridNode.java` L68
