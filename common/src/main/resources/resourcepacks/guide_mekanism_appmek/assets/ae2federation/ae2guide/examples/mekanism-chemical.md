---
navigation:
  parent: examples/index.md
  title: Return a Chemical
  icon: mekanism:chemical_oxidizer
  position: 50
---

# Return a Chemical

**Goal:** order carbon, one of Mekanism's chemicals, on your main network. A Chemical Oxidizer on a small network of
its own turns charcoal into carbon and pushes it straight back into the Endpoint. This page appears because Mekanism
and Applied Mekanistics are installed.

**You need:** your main network with a crafting CPU, a crafting terminal, charcoal in storage and a
<ItemLink id="appmek:chemical_storage_cell_1k" /> in a drive; a <ItemLink id="ae2federation:pattern_provider" />; a
<ItemLink id="ae2federation:processing_endpoint" />; <ItemLink id="ae2federation:cable" />; a
<ItemLink id="mekanism:chemical_oxidizer" />; a charged <ItemLink id="mekanism:basic_energy_cube" />; a
<ItemLink id="ae2:storage_bus" />.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/mekanism_oxidizer.snbt" />
  <BoxAnnotation color="#915dcd" min="5 0 0" max="7 2 1">
    Your main network: crafting terminal, crafting CPU, and a drive with an item cell and a chemical cell
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    Federation Pattern Provider: front on the Federation Cable, back on your network
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1 0" max="3 2 1">
    Processing Endpoint: front down on the cable; the oxidizer stands on it and the subnet's cable meets its side
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1.7 2.125 0.125" max="2 2.875 0.875">
    Storage Bus on the oxidizer's side, which is set to take items
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 2 0" max="3 3 1">
    Chemical Oxidizer: its bottom gives chemicals, with auto-eject on, so the carbon goes straight into the Endpoint
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 3 0" max="3 4 1">
    Basic Energy Cube: its bottom outputs energy into the oxidizer's top
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Build it

1. **Place the Provider** with its front on Federation Cable and another face on your network.
2. **Place the Endpoint** with its front on the same cable, and stand the Chemical Oxidizer on the Endpoint's top.
3. **Build the oxidizer's subnet** on another face of the Endpoint: an ME cable with a Storage Bus on the oxidizer's
   side. The Endpoint powers this subnet from your network while the Provider uses it. It must not connect to your
   main network.
4. **Set the oxidizer's sides** in its side configuration. For items, make the side towards the Storage Bus an input.
   For chemicals, make the bottom an output and turn on auto-eject. For energy, make the top an input.
5. **Power the oxidizer.** Place a charged Basic Energy Cube on top of it and set the cube's bottom to output. Charge
   the cube from any machine that makes FE.
6. **Store the carbon.** Applied Mekanistics lets AE2 store chemicals: put a Chemical Storage Cell in a drive on your
   main network. Mekanism's chemicals are not fluids, so a fluid cell does not hold them.
7. **Add the pattern.** The oxidizer makes 20 mB of carbon from one charcoal. Encode a processing pattern from one
   charcoal to 20 mB of carbon, put it in the Provider, and in the Provider's wiring graph drag it onto the Endpoint.
8. **Order carbon** on your main network. The charcoal goes into the subnet's storage, which is the oxidizer; the
   carbon flows down into the Endpoint and back into your network.

The Endpoint takes back items, fluids and the other resources your AE2 addons add, such as chemicals with Applied
Mekanistics, through any face but its front. A machine that pushes out its own products needs no hopper.

## Try it

Turn off the oxidizer's auto-eject for chemicals, then order carbon. The carbon collects in the oxidizer, nothing comes
back and the job waits. Turn auto-eject back on and the job finishes.
