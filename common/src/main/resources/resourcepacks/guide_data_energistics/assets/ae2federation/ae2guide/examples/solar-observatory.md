---
navigation:
  parent: examples/index.md
  title: A Solar Observatory
  icon: data_energistics:astronomical_observatory
  position: 74
---

# A Solar Observatory

**Goal:** an observatory network turns AE power into Data Energistics' Stellar Flux at night, on the power of a solar
outpost network, and your main network reads the Stellar Flux from the observatory's storage. Power and product travel
in opposite directions, each by its own rule. This page appears because Data Energistics is installed.

**You need:** an outpost network with six <ItemLink id="data_energistics:me_solar_panel" />s and an energy cell; an
observatory network with an <ItemLink id="data_energistics:astronomical_observatory" /> and an
<ItemLink id="ae2:drive" /> holding a <ItemLink id="data_energistics:digital_storage_cell_1k" />; your main network with
a crafting terminal; a <ItemLink id="ae2federation:switch" /> all three touch. Data Energistics' guide covers
[the panels](data_energistics:items-blocks-machines/6.1_me_solar_panel.md) and
[the Observatory](data_energistics:items-blocks-machines/6.12_astronomical_observatories.md).

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/solar_observatory.snbt" />
  <BoxAnnotation color="#5ccd78" min="4 0 1" max="7 2 3">
    Outpost: six ME Solar Panels in one array, on an ME cable and the energy cell that powers all three networks
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="3 0 0" max="4 1 1">
    Your main network: a crafting terminal
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 1" max="3 1 2">
    Observatory: the Astronomical Observatory and a drive with a Digital Storage Cell for its Stellar Flux
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 1" max="4 1 2">
    One Switch: each face joins the network it touches
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the three networks and the rules between them:

<FederationTopology>
  <Network key="outpost" label="Outpost" color="#5ccd78" column="0" row="0" details="Solar panels|Energy cell" />
  <Network key="main" label="Main network" color="#915dcd" column="1" row="0" details="Crafting terminal" />
  <Network key="observatory" label="Observatory" color="#5CA7CD" column="2" row="0" details="Observatory|Digital Storage Cell" />
  <Rule user="main" source="observatory" capability="storage" />
  <Energy first="outpost" second="main" />
  <Energy first="main" second="observatory" />
</FederationTopology>

## Build it

1. **Join the three networks** on the faces of one Switch, as in the scene.
2. **Build the outpost:** an ME cable and the energy cell on the Switch's face, and the panels on top of them in one
   array. A panel joins the network only through its bottom face, and panels side by side share their power, so the
   ones on the cable and the cell carry the whole array. Every panel needs open sky above it.
3. **Give the Observatory open sky above it**, and put the Digital Storage Cell in the observatory's drive. Stellar Flux
   is a Data Energistics resource, kept in a Digital Storage Cell.
4. **Switch on ME power** for the outpost and your main network, and for your main network and the observatory. Power
   pools along the chain, so the Observatory runs on the outpost's panels.
5. **Switch on Storage under "Main network uses Observatory's".** The Stellar Flux shows in your main network's
   terminal.

## How it runs

The Observatory works only at night, from tick 13,000 to 23,000 of each day, and turns 4,000 AE/t into 8 Stellar
Flux/t. At night a panel makes 1,000 AE/t, a third of its daytime output, so six panels cover the Observatory with
some to spare for the networks. The energy cell is what makes it work: the Observatory takes its 4,000 AE in one piece
each tick, and the three networks alone hold only a few hundred AE. Rain cuts the Observatory's output to a quarter,
and thunder stops it.

## Try it

Switch off ME power between your main network and the observatory. The Observatory goes dark and the Stellar Flux in
your terminal stops rising, while your main network keeps running on the outpost. Switch it back on, and the Stellar
Flux rises again.
