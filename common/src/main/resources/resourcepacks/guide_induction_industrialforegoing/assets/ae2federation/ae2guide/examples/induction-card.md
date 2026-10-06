---
navigation:
  parent: examples/index.md
  title: A Furnace on the Provider's FE
  icon: appflux:induction_card
  position: 46
---

# A Furnace on the Provider's FE

**Goal:** order stone on your main network while an Industrial Foregoing Resourceful Furnace on the Provider's Endpoint
smelts the cobblestone, running on FE from your main network's storage. The furnace has no power of its own, and its
subnet has no energy cell. This page appears because Applied Flux and Industrial Foregoing are installed.

**You need:** your main network with a crafting CPU, a crafting terminal, cobblestone in storage and ME Drives holding
<ItemLink id="appflux:fe_1k_cell" />s; a <ItemLink id="ae2federation:pattern_provider" /> with an
<ItemLink id="appflux:induction_card" />; a <ItemLink id="ae2federation:processing_endpoint" />;
<ItemLink id="ae2federation:cable" />; a <ItemLink id="industrialforegoing:resourceful_furnace" />; a
<ItemLink id="ae2:storage_bus" />. How FE gets into the cells is in
[Applied Flux's guide](appflux:appflux/flux_cells.md).

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/induction_furnace.snbt" />
  <BoxAnnotation color="#915dcd" min="5 0 0" max="7 2 1">
    Your main network: the energy cell, a crafting terminal, a crafting CPU, and ME Drives with item cells and ME FE
    Storage Cells
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0 0" max="5 1 1">
    Federation Pattern Provider with an Induction Card under Upgrades: sends your network's FE on to the machines at its
    Endpoints
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    Processing Endpoint: front on the Federation Cable, the furnace on its top and the furnace's subnet on its side
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1 0" max="3 2 1">
    Resourceful Furnace: takes FE from the Endpoint below and pushes its stone down into it
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="1.7 1.125 0.125" max="2 1.875 0.875">
    Storage Bus on the furnace's side
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows your main network wired to the Endpoint its Provider maps. Inputs go out along the
wire and results come back, and the subnet runs on your network's power:

<FederationTopology>
  <Network key="main" label="Main network" color="#915dcd" column="0" row="0" details="ME FE Storage Cells|Induction Card" />
  <Endpoint key="furnace" label="Endpoint · Furnace" owner="main" energy="true" details="Resourceful Furnace, no power of its own" />
</FederationTopology>

## Build it

1. **Place the Provider** with its front on Federation Cable and another face on your network.
2. **Place the Endpoint** with its front on the same cable, and the Resourceful Furnace on the Endpoint's top.
3. **Build the furnace's subnet** on another face of the Endpoint: an ME cable with a Storage Bus on the furnace's side.
   The Endpoint powers this subnet from your network while the Provider uses it. It must not connect to your main
   network.
4. **Set the furnace's output to push from the bottom** in its side configuration: choose the output inventory and set
   its bottom to Push. Leave the input inventory as it is. The bottom then pushes the stone into the Endpoint; the
   furnace takes FE on every side.
5. **Put the Induction Card in the Provider**, in the **Upgrades** slot of its screen. The Provider now sends FE from
   your network's storage into the machines touching the Endpoints it may send to, here the furnace.
6. **Add the pattern.** Encode a processing pattern from one cobblestone to one stone, put it in the Provider, and in
   the Provider's wiring graph drag it onto the Endpoint.
7. **Order stone** on your main network. The cobblestone goes into the subnet's storage, which is the furnace; it
   smelts it on your network's FE and pushes the stone into the Endpoint, which returns it to your network.

The FE goes only to Endpoints the Provider may send to now: once the Federation link to the Endpoint is cut, the
furnace gets no more.

## Try it

Build it without step 5 and order stone. The cobblestone reaches the furnace, but it has no power, so no stone comes
back and the job waits. Put the Induction Card in the Provider's Upgrades slot and the job finishes.
