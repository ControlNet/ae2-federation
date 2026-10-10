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
of its own, with its own channels. Then the outpost orders what a factory joined to the base by a Bridge makes, with no
rule, cable or Bridge of its own to the factory.

**You need:** your base network with storage and power; an outpost network with a crafting terminal and a crafting
CPU; a <ItemLink id="ae2federation:switch" /> for each; a Quantum Network Bridge, that is eight
<ItemLink id="ae2:quantum_ring" />s and a <ItemLink id="ae2:quantum_link" /> at each end and a pair of
<ItemLink id="ae2:quantum_entangled_singularity" />s; two <ItemLink id="ae2:me_p2p_tunnel" />s; a
<ItemLink id="ae2:memory_card" />; a <ItemLink id="ae2:quartz_fiber" />; ME cable and
<ItemLink id="ae2federation:cable" />. For the factory: a network whose <ItemLink id="ae2:pattern_provider" /> and
<ItemLink id="ae2:molecular_assembler" /> turn logs into planks, and an <ItemLink id="ae2federation:bridge" />.

In the Overworld:

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/nether_outpost_overworld.snbt" />
  <BoxAnnotation color="#915dcd" min="5 1 0" max="7.625 2 1">
    The base: a drive, the energy cell that powers every network here, and a cable for the Bridge
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="7.625 1.25 0.25" max="8 1.75 0.75">
    Bridge between the base and the factory: a second domain, of these two networks
  </BoxAnnotation>
  <BoxAnnotation color="#cdc35c" min="8 1 0" max="10 3 1">
    The factory: a pattern provider with a Molecular Assembler, on a cable of its own and the base's power
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
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 1 1">
    The outpost: a crafting terminal and a crafting CPU, with no power of its own
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

In the Federation screen, the base and the outpost are one domain, as if a Federation Cable ran between their Switches.
The carrier is not in it. The outpost uses the base's storage and shares its energy:

<FederationTopology>
  <Network key="base" label="Base" color="#915dcd" column="0" row="0" details="Drive|Energy cell" />
  <Network key="outpost" label="Outpost" color="#5CA7CD" column="1" row="0" details="Crafting terminal|Crafting CPU" />
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

## Order from the base's factory

The base can also be the outpost's way to a factory. Here the factory is a network of its own beside the base, a
pattern provider with a Molecular Assembler that turns logs into planks, joined to the base by a Bridge. The Bridge
forms a second domain, of the base and the factory. The outpost is not in it, and no screen offers an "Outpost uses
Factory's" rule. Re-export on the base's rule passes the factory's recipes on to the outpost all the same, as in
[Across Domains](../across-domains.md).

In a Switch's Federation screen, with connected domains shown:

<FederationTopology>
  <Network key="outpost" label="Outpost" color="#5CA7CD" column="0" row="0" details="Crafting terminal|Crafting CPU" />
  <Network key="base" label="Base" color="#915dcd" column="1" row="0" details="Drive|Energy cell" />
  <Network key="factory" label="Factory" color="#cdc35c" column="2" row="0" details="Pattern provider|Molecular Assembler" />
  <Domain key="here" label="This domain" networks="outpost,base" opened="true" />
  <Domain key="bridge" label="Domain 3F1C" networks="base,factory" />
  <Rule user="outpost" source="base" capability="crafting" />
  <Rule user="outpost" source="base" capability="storage" />
  <Rule user="base" source="factory" capability="crafting" state="reexport" />
  <Energy first="outpost" second="base" />
  <Energy first="base" second="factory" />
</FederationTopology>

### Build it

1. **Join the factory to the base with a Bridge**: place the Bridge on a cable of the base's, so that its outer side
   touches the factory's cable, as in the scene. Give the factory's cable a colour of its own, so that the two
   networks' cables never join.
2. **Right-click the Bridge** and switch on **Crafting under "Base uses Factory's"**, then step it on once more, to
   Enabled with re-export. The factory here stores nothing, so it needs no Storage rule. Should it get storage later,
   switch on Storage under "Base uses Factory's" with re-export too, and the outpost sees its stock and the leftovers
   of its jobs.
3. **Switch on ME power** for the base and the factory in the same screen. The factory then runs on the base's energy
   cell, like the outpost: power pools across both domains, and needs no re-export.
4. **Right-click either Switch** and switch on **Crafting under "Outpost uses Base's"**. Enabled is enough. Leave
   that direction's Storage on from the first part: the outpost pays with the base's logs through it.
5. **Order planks from the outpost's crafting terminal.** The factory's recipe is listed among the outpost's
   craftables.

### How the order runs

The outpost's own crafting CPU runs the job. The outpost has no storage of its own, so the CPU takes the logs from the
base's drive and sends them straight to the factory's pattern provider. The planks come straight back to the CPU, which
stores them in the base's drive, and the outpost's terminal lists them. The base needs no CPU: its rules only decide who
may reach whom.

### In the Federation screen

A Switch's screen opens on **this domain**: the base and the outpost, and the rules between them. Click the other scope
button, and the caption changes to **with connected domains (read-only)**: the Bridge's domain joins on a plate of its
own, with the factory and the "Base uses Factory's" Crafting rule, coloured as re-export. It cannot be changed there;
edit it from the Bridge.

### Try it

In the Bridge's screen, step "Base uses Factory's" Crafting back to Enabled. The planks disappear from the outpost's
craftables, while the base keeps the factory's recipe. Step it to re-export again and they come back.
