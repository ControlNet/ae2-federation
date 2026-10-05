---
navigation:
  parent: examples/index.md
  title: A Remote Mekanism Crusher
  icon: mekanism:crusher
  position: 40
---

# A Remote Mekanism Crusher

**Goal:** order gravel on your main network while a Mekanism Crusher on a small network of its own crushes the
cobblestone. This page appears because Mekanism is installed.

**You need:** your main network with a crafting CPU, a crafting terminal and cobblestone in storage; a
<ItemLink id="ae2federation:pattern_provider" />; a <ItemLink id="ae2federation:processing_endpoint" />;
<ItemLink id="ae2federation:cable" />; a <ItemLink id="mekanism:crusher" />; a charged
<ItemLink id="mekanism:basic_energy_cube" />; a hopper and a <ItemLink id="ae2:storage_bus" />.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/mekanism_crusher.snbt" />
  <BoxAnnotation color="#915dcd" min="5 0 0" max="7 2 1">
    Your main network: crafting terminal, crafting CPU and storage
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    Federation Pattern Provider: front on the Federation Cable, back on your network
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1 0" max="3 2 1">
    Processing Endpoint: front down on the cable, top face on the Crusher's subnet
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1.125 3 0.125" max="1.875 3.3 0.875">
    Storage Bus on the Crusher's top, which is set to take items
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1 1 0" max="2 2 1">
    Hopper under the Crusher, which is set to give items at the bottom: pushes the gravel into the Endpoint's side
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="0 2 0" max="1 3 1">
    Basic Energy Cube: its side towards the Crusher outputs energy
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows your main network wired to the Endpoint its Provider maps. Inputs go out along the
wire and results come back, and the subnet runs on your network's power:

<FederationTopology>
  <Network key="main" label="Main network" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Pattern Provider" />
  <Endpoint key="crusher" label="Endpoint · Crusher" owner="main" energy="true" details="Crusher subnet" />
</FederationTopology>

## Build it

1. **Place the Provider** with its front on Federation Cable and another face on your network.
2. **Place the Endpoint** with its front on the same cable.
3. **Build the Crusher's subnet** on another face of the Endpoint: an ME cable with a Storage Bus on the Crusher's top.
   The Endpoint powers this subnet from your network while the Provider uses it. It must not connect to your main
   network.
4. **Set the Crusher's sides** in its side configuration. For items, make the top an input and the bottom an output;
   for energy, make the side towards the cube an input. The Storage Bus then fills the input slot and the hopper
   empties the output slot.
5. **Power the Crusher.** Place a charged Basic Energy Cube beside it and set the cube's side towards the Crusher to
   output. Mekanism has no generator of its own; charge the cube from any machine that makes FE, such as one from
   Mekanism Generators.
6. **Return the results.** A hopper under the Crusher pushes the gravel into one of the Endpoint's faces other than its
   front.
7. **Add the pattern.** Encode a processing pattern from one cobblestone to one gravel, put it in the Provider, and in
   the Provider's wiring graph drag it onto the Endpoint.
8. **Order gravel** on your main network. The cobblestone goes into the subnet's storage, which is the Crusher; the
   gravel comes back through the hopper and the Endpoint into your network.

To crush more at once, build more cells as on [Outsourced Furnaces](endpoint-furnaces.md).

## Try it

Turn the Crusher's top off in its side configuration, then order gravel. The Storage Bus has nowhere to put the
cobblestone, so no gravel comes back and the job waits. Set the top back to input and the job finishes.
