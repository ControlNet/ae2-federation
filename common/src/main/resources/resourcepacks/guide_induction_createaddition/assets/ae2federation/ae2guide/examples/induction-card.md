---
navigation:
  parent: examples/index.md
  title: A Millstone on the Provider's FE
  icon: appflux:induction_card
  position: 46
---

# A Millstone on the Provider's FE

**Goal:** order gravel on your main network while a Create Millstone at the Provider's Endpoint mills the cobblestone.
An Electric Motor turns it on FE from your main network's storage; there is no other rotational power and no energy
cell on the Endpoint's subnet. This page appears because Applied Flux and Create Crafts & Additions are installed.

**You need:** your main network with a crafting CPU, a crafting terminal, cobblestone in storage and ME Drives holding
<ItemLink id="appflux:fe_1k_cell" />s; a <ItemLink id="ae2federation:pattern_provider" /> with an
<ItemLink id="appflux:induction_card" />; a <ItemLink id="ae2federation:processing_endpoint" />;
<ItemLink id="ae2federation:cable" />; an <ItemLink id="createaddition:electric_motor" />; a
<ItemLink id="create:shaft" />, a <ItemLink id="create:cogwheel" />, a <ItemLink id="create:chute" /> and a
<ItemLink id="create:millstone" />; a <ItemLink id="ae2:storage_bus" />. How FE gets into the cells is in
[Applied Flux's guide](appflux:appflux/flux_cells.md).

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/induction_motor.snbt" />
  <BoxAnnotation color="#915dcd" min="5 0 0" max="7 2 1">
    Your main network: the energy cell, a crafting terminal, a crafting CPU, and ME Drives with item cells and ME FE
    Storage Cells
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    Federation Pattern Provider with an Induction Card under Upgrades: sends your network's FE on to the machines at its
    Endpoints
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    Processing Endpoint: front on the Federation Cable, the Chute on its top and the Millstone's subnet on its side
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 1" max="3 1 2">
    Electric Motor beside the Endpoint: takes FE from it and turns the shaft above it
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 2 1" max="3 3 2">
    Cogwheel: meshes with the Millstone and turns it
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1 0" max="3 2 1">
    Chute: takes the gravel out of the Millstone and drops it into the Endpoint
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1.7 2.125 0.125" max="2 2.875 0.875">
    Storage Bus on the Millstone's side
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows your main network wired to the Endpoint its Provider maps. Inputs go out along the
wire and results come back, and the subnet runs on your network's power:

<FederationTopology>
  <Network key="main" label="Main network" color="#915dcd" column="0" row="0" details="ME FE Storage Cells|Induction Card" />
  <Endpoint key="mill" label="Endpoint · Millstone" owner="main" energy="true" details="Millstone, Electric Motor" />
</FederationTopology>

## Build it

1. **Place the Provider** with its front on Federation Cable and another face on your network.
2. **Place the Endpoint** with its front on the same cable, a Chute on the Endpoint's top and the Millstone on the
   Chute.
3. **Place the Electric Motor** on another face of the Endpoint, facing up, with a shaft and then a cogwheel above it.
   The cogwheel sits beside the Millstone and turns it.
4. **Build the Millstone's subnet** on another face of the Endpoint: an ME cable with a Storage Bus on the Millstone's
   side. The Endpoint powers this subnet from your network while the Provider uses it. It must not connect to your main
   network.
5. **Put the Induction Card in the Provider**, in the **Upgrades** slot of its screen. The Provider now sends FE from
   your network's storage into the machines touching the Endpoints it may send to, here the Electric Motor.
6. **Add the pattern.** Encode a processing pattern from one cobblestone to one gravel, put it in the Provider, and in
   the Provider's wiring graph drag it onto the Endpoint.
7. **Order gravel** on your main network. The cobblestone goes into the subnet's storage, which is the Millstone; the
   motor turns it on your network's FE, and the Chute drops the gravel into the Endpoint, which returns it to your
   network.

The motor runs at 32 RPM when placed. Faster, it mills faster and draws more FE each tick. The FE goes only to Endpoints
the Provider may send to now: once the Federation link to the Endpoint is cut, the motor gets no more.

## Try it

Build it without step 5 and order gravel. The cobblestone reaches the Millstone, but the motor has no power and the
Millstone stands still, so no gravel comes back and the job waits. Put the Induction Card in the Provider's Upgrades
slot and the job finishes.
