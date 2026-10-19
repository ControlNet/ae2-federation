# AE2 channel accounting as a Federation design reference

Research date: 2026-10-09. Scope: brainstorming, not an implementation decision.
The project pins AE2 19.2.17 in `gradle.properties`; the upstream source tag below was read directly.

## Verified behavior

[AE2 1.21.1 channel guide](https://guide.appliedenergistics.org/1.21.1/ae2-mechanics/channels):
default ordinary cables carry 8 channels and dense cables 32. Channels represent device connectivity,
not item transfer rate. Controller-free networks have a network-wide limit of 8 channel-consuming devices;
exceeding it disables channel-consuming devices. Their smart cables display the network-wide count.
Controller-based routing can overload a preferred route while alternative routes remain unused.

[PathingCalculation, neoforge/v19.2.17](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/neoforge/v19.2.17/src/main/java/appeng/me/pathfinding/PathingCalculation.java):
controller outgoing connections seed a traversal with dense-cable, ordinary-cable, then device queues.
Each visited item receives one parent route. Demands are admitted only if the selected path's bottlenecks
permit another channel. A second traversal aggregates allocations upstream. This is not maximum-flow
optimization or automatic load balancing. Visited tracking prevents cycles from creating repeated allocation
paths. Recognized multiblocks can share one demand. Multiple exits seed routes without multiplying a device's
demand. The implementation describes two linear-time passes; this does not establish Federation's complexity.

[PathingService, neoforge/v19.2.17](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/neoforge/v19.2.17/src/main/java/appeng/me/service/PathingService.java):
controller-free counting and controller-rooted path allocation are separate branches. Node additions/removals
and channel-requirement changes request recalculation. Allocated channel-node usage contributes to power cost;
the energy input device does not define the channel allocation origin.

## Implications and open decisions for Federation

These are deductions and discussion suggestions, not AE2 behavior or approved requirements:

- Separate attachment demand, segment capacity, energy cost, and resource throughput. Preserve the user's unit:
  one attached ME network or Processing Endpoint; policy does not affect counting.
- Distinguish physical block count from logical demand. Larger router geometry need not itself create more
  attached networks. Repeated attachment of the same ME network still needs its own identity rule.
- For a rooted illustration, branches with three and two admitted demands contribute five to their shared
  upstream trunk. This does not define accounting on a rootless Federation trunk.
- AE2 offers no direct answer to rootless per-segment Federation accounting: its rootless mode uses a global
  count. Do not reintroduce the Beam Charger as a routing root based on this comparison.
- Test proposed semantics using two router clusters with three and two distinct attached networks and a single
  trunk. Decide which attachments that trunk represents before introducing redundant paths or routing algorithms.
- Explain saturation and affected attachments visibly. Copying AE2's preferred-route behavior also copies its
  potential surprise that a free alternative cable does not guarantee admission.

See the [idea](../../docs/ideas/multiblock-routing-and-cable-capacity.md). No runtime changes or game tests were made.
