---
navigation:
  parent: examples/index.md
  title: An Ore Doubling Line
  icon: mekanism:enrichment_chamber
  position: 42
---

# An Ore Doubling Line

**Goal:** order iron ingots on your main network while two Mekanism machines, each behind its own Endpoint, turn every
iron ore into two ingots: an Enrichment Chamber makes two iron dust from it, and an Energized Smelter smelts the dust.
This page appears because Mekanism is installed.

**You need:** your main network with a crafting CPU, a crafting terminal and iron ore in storage; a
<ItemLink id="ae2federation:pattern_provider" />; two <ItemLink id="ae2federation:processing_endpoint" />s;
<ItemLink id="ae2federation:cable" />; a <ItemLink id="mekanism:enrichment_chamber" /> and a
<ItemLink id="mekanism:energized_smelter" />, each with a charged <ItemLink id="mekanism:basic_energy_cube" />, a
hopper and a <ItemLink id="ae2:storage_bus" />.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/ore_line.snbt" />
  <BoxAnnotation color="#915dcd" min="8 0 0" max="10 2 1">
    Your main network: crafting terminal, crafting CPU and storage
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="7 0 0" max="8 1 1">
    Federation Pattern Provider: front on the Federation Cable, back on your network
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="3 1 0" max="6 4 1">
    First Endpoint and its subnet: the Enrichment Chamber turns each iron ore into two iron dust
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 1 0" max="3 4 1">
    Second Endpoint and its subnet: the Energized Smelter smelts the dust into iron ingots
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows your main network wired to both Endpoints its Provider maps:

<FederationTopology>
  <Network key="main" label="Main network" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Pattern Provider" />
  <Endpoint key="enriching" label="Endpoint · enriching" owner="main" energy="true" details="Enrichment Chamber subnet" />
  <Endpoint key="smelting" label="Endpoint · smelting" owner="main" energy="true" details="Energized Smelter subnet" />
</FederationTopology>

## Build it

1. **Place the Provider** with its front on Federation Cable and another face on your network, and **both Endpoints**
   with their fronts on the same cable.
2. **Build each machine's subnet** as on [A Remote Mekanism Crusher](mekanism-crusher.md): on the Endpoint, an ME cable
   with a Storage Bus on the machine's top, and a hopper under the machine pushing into the Endpoint. The two subnets
   must not touch each other or your main network.
3. **Set each machine's sides**: for items, the top an input and the bottom an output; for energy, the side towards
   its cube an input. Set each cube's side towards its machine to output.
4. **Add two patterns** to the Provider: a processing pattern from one iron ore to two iron dust, and one from one iron
   dust to one iron ingot. In the Provider's wiring graph drag the first onto the Enrichment Chamber's Endpoint and the
   second onto the Energized Smelter's.
5. **Order iron ingots** on your main network. Your crafting CPU runs both steps: the ore goes to the Enrichment
   Chamber, its dust comes back to your network and goes out again to the Energized Smelter, and the ingots come back.

## Try it

Take away the hopper under the Energized Smelter, then order iron ingots. No ingots come back and the job waits. Put
the hopper back and the job finishes.
