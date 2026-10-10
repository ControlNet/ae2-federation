---
navigation:
  parent: examples/index.md
  title: An Outpost in the Nether
  icon: ae2federation:federation_p2p_tunnel
  position: 15
---

# An Outpost in the Nether

**Goal:** an outpost network in the Nether uses your base's storage in the Overworld and runs on the base's power. A
Quantum Network Bridge carries a pair of Federation P2P tunnels between the dimensions, and the outpost stays a network
of its own, with its own channels.

**You need:** your base network with storage and power; an outpost network with a terminal; a
<ItemLink id="ae2federation:switch" /> for each; a Quantum Network Bridge, that is eight
<ItemLink id="ae2:quantum_ring" />s and a <ItemLink id="ae2:quantum_link" /> at each end and a pair of
<ItemLink id="ae2:quantum_entangled_singularity" />s; two <ItemLink id="ae2:me_p2p_tunnel" />s; a
<ItemLink id="ae2:memory_card" />; a <ItemLink id="ae2:quartz_fiber" />; ME cable and
<ItemLink id="ae2federation:cable" />.

In the Overworld:

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/nether_outpost_overworld.snbt" />
  <BoxAnnotation color="#915dcd" min="5 1 0" max="7 2 1">
    The base: a drive, and the energy cell that powers every network here
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 2 0" max="6 3 1">
    The base's Switch, on top of its drive
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="4 3 1">
    The carrier: the bridge and the tunnel's cable, a network of its own with no energy cell
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4.125 1.75 0.125" max="4.875 2 0.875">
    Tunnel input: on the carrier's cable, its front under the Federation Cable to the base's Switch
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4.75 1.375 0.375" max="5 1.625 0.625">
    Quartz Fiber: passes the base's power to the carrier without joining the two networks
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

In the Nether:

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/nether_outpost_nether.snbt" />
  <BoxAnnotation color="#5ccd78" min="2 0 0" max="6 3 1">
    The carrier's other half: once the bridge links, one network with the Overworld half, and on its power
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1.125 0.125" max="2.25 1.875 0.875">
    Tunnel output: its front on the Federation Cable to the outpost's Switch
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 1 0" max="1 2 1">
    The outpost's Switch
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="1 1 1">
    The outpost: a terminal, with no power of its own
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

In the Federation screen, the base and the outpost are one domain, as if a Federation Cable ran between their Switches.
The carrier is not in it. The outpost uses the base's storage and shares its energy:

<FederationTopology>
  <Network key="base" label="Base" color="#915dcd" column="0" row="0" details="Drive|Energy cell" />
  <Network key="outpost" label="Outpost" color="#5CA7CD" column="1" row="0" details="Terminal" />
  <Rule user="outpost" source="base" capability="storage" />
  <Energy first="base" second="outpost" />
</FederationTopology>

## Build it

1. **Build the bridge**, one half in each dimension, with one singularity of the pair in each link chamber; see AE2's
   [Quantum Network Bridge](ae2:items-blocks-machines/quantum_bridge.md). Its cable is a network of its own, the
   carrier. Do not let it touch the base or the outpost: the bridge would then join a half that already exists to
   that network, and Federation pauses that network's rules ("Merge pending").
2. **Power the carrier from the base** with a Quartz Fiber between the carrier's cable and the base. The Nether half
   needs no energy cell: once the bridge links, the base's power reaches it.
3. **Place the tunnels.** Put an ME P2P tunnel on the carrier's cable at each end, and right-click each with
   Federation Cable to turn it into a [Federation P2P tunnel](../items/federation_p2p_tunnel.md). Then pair them with
   the Memory Card: sneak and right-click the Overworld tunnel, then right-click the Nether one.
4. **Connect each network to its Switch, and each Switch to its tunnel's front** with Federation Cable.
5. **Open the Federation screen** by right-clicking either Switch, and select the line between the base and the
   outpost. Switch on **Storage under "Outpost uses Base's"** and **ME power** for the pair: the outpost then runs on
   the base's power, across the dimensions.
6. **Check it.** Open the outpost's terminal: the base's items are listed. The rule is the same one as in
   [A Shared Warehouse](shared-warehouse.md).

## Keep both ends loaded

Like AE2's own bridge, the link needs both ends loaded, for example with a <ItemLink id="ae2:spatial_anchor" />. If the
outpost's chunks unload, the outpost leaves the domain at once and loses the base's storage. When they load again, it
comes back on its own.

## Try it

Take the singularity out of the Overworld link chamber. The outpost's terminal goes dark and empty: the tunnels are cut,
and with them the base's storage and power. The base keeps working on its own. Put the singularity back, and the
outpost has both again.
