# Jade extension feasibility

Checked 2026-10-10 against Jade `1.21-neoforge` and AE2 `neoforge/v19.2.17` sources.
This is research, not a selected feature specification or implemented integration.

## Verified extension points

- [IWailaPlugin](https://github.com/Snownee/Jade/blob/1.21-neoforge/src/main/java/snownee/jade/api/IWailaPlugin.java)
  separates common and client registration. `@WailaPlugin` identifies plugins on NeoForge.
- [IServerDataProvider](https://github.com/Snownee/Jade/blob/1.21-neoforge/src/main/java/snownee/jade/api/IServerDataProvider.java)
  supplies server-side `CompoundTag` data through `appendServerData`; block providers register with
  `registerBlockDataProvider` for block entity classes.
- [Client registration](https://github.com/Snownee/Jade/blob/1.21-neoforge/src/main/java/snownee/jade/api/IWailaClientRegistration.java)
  supports block component providers, plugin configuration, and a details-key query.
- [IElementHelper](https://github.com/Snownee/Jade/blob/1.21-neoforge/src/main/java/snownee/jade/api/ui/IElementHelper.java)
  supports text, item/fluid icons, progress bars, sprites, and boxes.
- [AE2 JadeModule](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/neoforge/v19.2.17/src/main/java/appeng/integration/modules/jade/JadeModule.java)
  uses the same plugin/provider approach, adapting AE2 tooltip providers into Jade. Ordinary Federation
  block tooltip additions can use public Jade APIs without introducing mixins. AE2 parts require attention
  to part targeting and existing tooltip composition; do not assume all targets are standalone blocks.

## Project evidence and recommendations

Production source searches found no existing Jade/Waila integration. `RouterBlockEntity` exposes face
bindings and native-network grouping; `EndpointBlockEntity` exposes claim state and target binding.
`FederationDomainStateSnapshot` contains members, providers, endpoints, policies, locks, tasks, and flows.
These are candidate sources, not proof that an existing client snapshot is available when merely looking
at a block. Build a small read-only server summary rather than sending the full domain UI snapshot or
performing topology recomputation per HUD request. Respect existing observation authorization and caps.

Recommend an optional integration bundled in Federation, isolating Jade references so absence of Jade is
supported. Full server-derived status requires integration on both sides. Earlier suggestions for
connection/binding diagnostics and detail panels were not adopted: the user wants a very lightweight HUD,
with the initial candidate limited to a future cable network-count/limit line. See the
[display idea](../../docs/ideas/jade-cable-capacity.md). Future capacity/power ideas must not be presented
as already available fields. No dependencies or code changed.
