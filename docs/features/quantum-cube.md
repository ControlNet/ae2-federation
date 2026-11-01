# ME Federation Quantum Cube

Status: implemented on `dev` (2026-10-17). Design discussion:
[remote Federation connections](../ideas/remote-federation-connections.md), follow-up 2026-10-17; plan and decisions
D1-D10: `.omo/plans/quantum-cube.md`. The Blender artwork is integrated as an open textured frame with a
layered teal fragment core (2026-10-26); see [runtime art notes](../art/refined-device-runtime.md).

## Behavior

- **Block.** `ae2federation:quantum_cube` 「ME联邦量子立方」, a solid-cuboid device like the Router
  (`FormableDevice`). A body is a cube of edge 3, 4 or 5 (`QuantumCubeShape`); any other cuboid does not form
  (`WRONG_SHAPE`). The edge gives the section: 3 → 1x1, 4 → 2x2, 5 → 3x3. Recipe `CRC / RQR / CLC` → 8 (Federation
  Cable, `ae2:quantum_ring`, `ae2:quantum_link`, Nexus Core).
- **Ports.** Each block is a node of kind `QUANTUM_CUBE` with the section's capacity factor, and links toward the rest
  of its body. On an outer face a block links only inside the face's (K-2) x (K-2) centre (`QuantumCubeBlock.links`);
  edge and corner blocks expose nothing outward. `FormableDevice.covers` reports the same rule to `BundleContacts`, so
  a bundle pressed partly on the border is not a whole contact and joins nothing.
- **Binding.** AE2's `MemoryCardItem.doesSneakBypassUse` is true, so the cube's `useItemOn` runs first. Sneak-use
  clears the card and writes the body's lowest block (`ae2federation:quantum_cube_card`, a `GlobalPos`) and AE2's
  `EXPORTED_SETTINGS_SOURCE`; use on another formed cube gives both bodies a new random pair id
  (`QuantumLinkService.bind`). Both leave their old pairs, and a loaded old partner is cleared, so a third cube never
  joins a pair. Every member stores the id and the partner's position (for Jade); a body adopts the one non-zero id
  its members agree on, and members that disagree leave it unbound. No `SavedData`.
- **Link.** `QuantumLinkService`, one per server (pairs cross dimensions), ticked after the beam service. A formed,
  loaded body's lowest block reports itself; `QuantumPairIndex` links a pair id when exactly two bodies of the same
  size are loaded (otherwise `NOT_BOUND`, `PARTNER_NOT_LOADED`, `WRONG_SIZE` or `MORE_THAN_TWO`). Both anchors then
  name a mirrored remote port, `"quantum:" + partner` to `(partner, "quantum:" + self)`, in the same pass. As AE2's
  Quantum Network Bridge, an end that unloads (its chunk or its dimension) leaves the index and the pair unlinks; it
  links again on load. Nothing loads chunks.
- **Capacity.** A linked pair is passable for counting and carries its region's demand, so both sides are one region
  judged against its section's limit: 3x3x3 C, 4x4x4 6C, 5x5x5 unlimited ([cable capacity](cable-capacity.md)).
- **Power.** Each block of a formed body costs 44 FE/t (22 AE/t, AE2's bridge), linked or not, and both ends pay; an
  unformed block costs 0. A cube costs 1188, 2816 or 5500 FE/t by edge. A linked pair's section counts with the
  cables' when the domain's converters are matched; the statistics page groups the cost as "Quantum Cubes"
  ([operating power](operating-power.md)).
- **Display.** Static frame meshes are clipped into their owning chunk members, while the lowest member renders
  the counter-rotating 22/12-fragment core and incoming cable carriers. All three sizes use the source Federation
  teal palette. Jade shows one status line (linked to a position and dimension, not bound, partner not
  loaded at a position, another size, more than two cubes) and the capacity count. Cables draw a joint only toward
  face centres (`CableVisualConnections`).

## Evidence

- Unit tests: `QuantumCubeShapeTest`, `QuantumPairIndexTest`, `CableCapacityCounterTest.aQuantumCubePairCountsAsCable`,
  `OperatingPowerCostsTest.aFormedQuantumCubePaysPerBlockLinkedOrNot`, `RefinedDeviceResourcesTest`,
  `SurvivalRecipeContractTest`, `GuidePagesContractTest`.
- GameTests (manifest `quantum.*`): `cube-shapes`, `links-inside-face-centres`, `pair-binds-with-a-memory-card`
  (bind, another size, break and mend, rebind drops the old partner), `pair-needs-its-size-of-converter`,
  `pair-links-across-dimensions` (an overworld cube bound to a nether one through `OtherDimensionSite`: linked, the
  nether chunks really unload and reload, then the nether level closes).
- In game (dedicated server, 2026-10-17): a 3x3x3 pair between the Overworld and the Nether bound with a real Memory
  Card, Jade before and after the Nether end unloads and reloads, and the capacity count of the joined networks.

## In-game guide

The [English page](../../common/src/main/resources/assets/ae2federation/ae2guide/items/quantum_cube.md) and
[Simplified Chinese page](../../common/src/main/resources/assets/ae2federation/ae2guide/_zh_cn/items/quantum_cube.md),
with a hand-written scene; the index, Beam Converter and Network Capacity pages link to it.
