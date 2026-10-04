# Wireless connection patterns in technology mods (2026-10-02)

Expanded on 2026-10-03: the [100-project survey](wireless-tech-mod-survey-2026-10-03.md) retains these eight
projects once each and adds further mechanism references plus explicitly inconclusive screening records.
Use the expanded record for the current research breadth; this page preserves the initial investigation.

## Scope

The user explicitly requested a subagent survey of well-known technology mods on CurseForge and Modrinth.
One subagent investigated CurseForge examples while the parent investigated Modrinth projects and official guides.
The user's quantum-bridge-style jump is the first candidate, not the selected exclusive solution.
Current product ideas belong in [remote Federation connections](../../docs/ideas/remote-federation-connections.md).

This is a documentation/source survey, not an in-game compatibility test. Sources span current official guides
and explicitly named versions; do not assume all behavior applies unchanged to Minecraft 1.21.1. Unknown costs
and chunk-loading behavior remain unknown. Download counts are approximate platform observations, not a ranking
or active-player measurement. No implementation, issues or PRs were created.

## Platform selection evidence

- [AE2 on Modrinth](https://modrinth.com/mod/ae2): approximately 5.8 million downloads in the inspected listing.
- [Refined Storage on Modrinth](https://modrinth.com/mod/refined-storage): approximately 2.7 million.
- [Powah on Modrinth](https://modrinth.com/mod/powah): approximately 2.4 million.
- [Integrated Dynamics on Modrinth](https://modrinth.com/mod/integrated-dynamics): approximately 113,100;
  [CurseForge](https://www.curseforge.com/minecraft/mc-mods/integrated-dynamics) shows approximately 115.4 million.
  Platform totals should not be compared as if they represented identical distribution histories.
- Subagent CurseForge observations: [Mekanism](https://www.curseforge.com/minecraft/mc-mods/mekanism) approximately
  176.1 million; [Flux Networks](https://www.curseforge.com/minecraft/mc-mods/flux-networks) 136.4 million;
  [RFTools Power](https://www.curseforge.com/minecraft/mc-mods/rftools-power) 87.4 million;
  [Ender Storage](https://www.curseforge.com/minecraft/mc-mods/ender-storage-1-8) 145.4 million.
  The observed 1.21.1 listings included Mekanism 10.7.19.85, Flux 8.0.0, RFTools Power 7.0.6 beta and
  Ender Storage 2.13.0.191. Source/documentation checks below do not constitute tests of those binaries.

## Network continuity examples

### AE2: a paired physical link token

The 1.21 guide describes paired Quantum Entangled Singularities, one in each bridge. The bridge extends a network
across arbitrary distance and dimensions, acting as a dense cable with 32 channels. Both ends must remain loaded.
The structure uses eight ring blocks and one central chamber. These are upstream facts, not proposed Federation
channel limits, costs or appearance.

Sources: [bridge](https://guide.appliedenergistics.org/1.21/items-blocks-machines/quantum_bridge),
[pairing token](https://guide.appliedenergistics.org/1.21/items-blocks-machines/singularities).

Design interpretation: an explicit two-site connection, with a physical object representing the pairing.

### Refined Storage: a card holding a destination

The current official guide instructs players to record a Network Receiver with a Network Card, then insert that
card into a Network Transmitter. Remote machines attach to the receiver as network devices. Cross-dimensional
operation is supported. Both endpoints must stay loaded; an unreachable receiver produces an error and retries.
The transmitter also supports redstone operating modes. Exact current power consumption was not established.

Sources: [setup](https://refinedmods.com/refined-storage/guides/networks-over-long-distances.html),
[card](https://refinedmods.com/refined-storage/wireless-networking/network-card.html),
[transmitter](https://refinedmods.com/refined-storage/wireless-networking/network-transmitter.html).
These are current/latest documentation, not a separately tested 1.21.1 artifact.

Design interpretation: location selection and copying an address are the central interaction. Transmitter/receiver
roles do not imply that all storage operations are one-way.

### Integrated Dynamics: alignment or group membership

The official Mono-Directional Connector guide requires connectors on a straight line, at most 512 blocks apart,
and describes visible activation when connected. It does not establish an unobstructed line-of-sight requirement
or one-way data semantics. The Omni-Directional Connector instead supports multiple networks and dimensions using
a unique group ID. Crafting can create additional group members or change groups; particles identify groups by color.
Connector-specific costs and automatic chunk loading were not established in these pages.

Sources: [directional connector](https://integrateddynamics.rubensworks.net/book/manual/parts/other/connector_mono_directional.html),
[group connector](https://integrateddynamics.rubensworks.net/book/manual/parts/other/connector_omni_directional.html).
The web manual does not pin a Minecraft version.

Design interpretation: alignment makes world placement meaningful; group membership makes many-site expansion
easy. These are distinct interactions even when both ultimately extend a network.

## Shared resource service example

### Powah: a shared energy channel with small access devices

Official guidebook sources describe an owner's Ender Network channel holding energy, accessed by Ender Cells
with valid channel capacity. Ender Gates transfer energy between adjacent blocks and the Ender Network, without
providing the cell's network-upgrade function. Tier-dependent I/O is documented but exact values are dynamically
rendered, so none are copied here. Cross-dimensional and chunk-loading details were not established from these pages.

Sources, 26.1 branch pinned to `42742741fdfde80bfee264d0b154d941f976ae05`:
[Ender Cells](https://github.com/Technici4n/Powah/blob/42742741fdfde80bfee264d0b154d941f976ae05/guidebook/storage_transfer/ender_cell.md),
[Ender Gates](https://github.com/Technici4n/Powah/blob/42742741fdfde80bfee264d0b154d941f976ae05/guidebook/storage_transfer/ender_gate.md).

Design interpretation: substantial shared infrastructure plus lightweight access points. Shared FE storage is not
equivalent to joining Federation domains; only the interaction pattern is directly comparable.

## Coverage is a separate access model

AE2's Wireless Access Point connects wireless terminals. Boosters change range and power usage; multiple access
points can extend coverage. This is terminal access, not an existing bridge between arbitrary ME networks.
Source: [AE2 1.21 access point guide](https://guide.appliedenergistics.org/1.21/items-blocks-machines/wireless_access_point).

A coverage-based Federation receiver would therefore be a new adaptation, not a claim that AE2 already provides
that feature. Likewise, no relay mesh requirement follows merely from citing a directional connector.

## Additional resource-transfer examples investigated by the subagent

### Mekanism: named frequencies

Quantum Entangloporters select or create a named frequency and configure input/output by face. Multiple endpoints
access that frequency's resource service; energy/fluid/chemical buffers are shared, rather than extending arbitrary
network interfaces. The wiki describes unlimited distance and loaded-endpoint operation, with an optional Anchor
Upgrade. Current 1.21.x source records active endpoints with dimension and position. Exact throughput numbers from
the version-unspecified wiki were not adopted.

Sources: [official wiki](https://wiki.aidancbrady.com/wiki/Quantum_Entangloporter),
[Anchor Upgrade](https://wiki.aidancbrady.com/wiki/Anchor_Upgrade),
[1.21.x frequency source](https://github.com/mekanism/Mekanism/blob/1.21.x/src/main/java/mekanism/common/content/entangloporter/InventoryFrequency.java).
The branch source supports cross-dimensional operation but was not checked against a specific released JAR.

Design interpretation: selecting a named group supports incremental expansion; do not import shared resource-buffer
semantics merely to reuse the grouping interaction.

### Flux Networks: managed wireless energy networks

Players create a network and enroll Flux Plugs for input and Flux Points for output. The author project page lists
cross-dimensional transfer, priorities, transfer-limit controls, network access settings, remote connection editing,
statistics and chunk loading. Older official wiki pages describe connection names and a per-connection chunk-loading
switch, subject to server configuration. A controller is not required for basic transfer. Detailed legacy GUI fields
were not individually verified in the current version.

Sources: [official project page](https://www.curseforge.com/minecraft/mc-mods/flux-networks),
[connector wiki](https://github.com/SonarSonic/Flux-Networks/wiki/Flux-Connectors),
[GUI wiki](https://github.com/SonarSonic/Flux-Networks/wiki/GUI-TABS).
The latter wiki documentation dates to 2019; use it as design history, not an exact 8.0.0 UI specification.

Design interpretation: connection management, recognizable names, status and diagnostics matter alongside pairing.
This is a power network, not a storage or full-network bridge.

### RFTools Power: copyable link cards

The 1.21_neo in-game guide describes linking Dimensional Cells by Powercell Cards, including creation and copying
of links. Multiple linked cells share energy storage; the project page confirms cross-dimensional operation.
The cell guide documents device tiers, side modes and an extraction loss reduced by infusion. Current automatic
chunk-loading behavior was not established. Modern Dimensional Cells are the wireless device; modern Powercells
are adjacent multiblock storage, so old naming must not be mixed with current terminology.

Sources pinned to `e44463cacddb85155839373b42d39bc705d618c0`:
[card](https://github.com/McJtyMods/RFToolsPower/blob/e44463cacddb85155839373b42d39bc705d618c0/src/main/resources/assets/rftoolsbase/patchouli_books/manual/en_us/entries/powerstorage/powercell_card.json),
[Dimensional Cell](https://github.com/McJtyMods/RFToolsPower/blob/e44463cacddb85155839373b42d39bc705d618c0/src/main/resources/assets/rftoolsbase/patchouli_books/manual/en_us/entries/powerstorage/dimensionalcell.json),
[project](https://www.curseforge.com/minecraft/mc-mods/rftools-power).

Design interpretation: a portable, copyable group credential differs from both an exclusive paired token and
a card holding one receiver's address. A physical card alone does not imply one-to-one topology.

### Ender Storage: visible color-coded shared storage

Three dye-controlled color markers select a shared inventory across dimensions; tanks provide shared fluid storage.
The author page also describes owner-specific channels and portable pouch access. Energy costs and current
chunk-loading details were not established. This is shared storage rather than remote continuity of every capability.

Source: [author project page](https://www.curseforge.com/minecraft/mc-mods/ender-storage-1-8).

Design interpretation: connection identity can be visible in the world. Federation could borrow readable visual
identity without adopting a fixed three-color code or globally shared inventory.

## Synthesis for future product discussion

Keep these dimensions distinct: what is connected (whole network, resource pool or terminal), how players identify
the connection (paired token, destination card, named group or visible marker), and how operation is constrained
(range, alignment, dimensions, cost and chunk loading). They can be combined without copying one mod wholesale.

The closest references for Federation topology are AE2, Refined Storage and Integrated Dynamics. Mekanism, Flux,
RFTools, Powah and Ender Storage add useful interaction and management patterns, with different underlying semantics.
The remote-connection idea page lists possible adaptations, all unselected. In particular, group membership,
directional placement and coverage should not be promoted to approved requirements by this survey.
