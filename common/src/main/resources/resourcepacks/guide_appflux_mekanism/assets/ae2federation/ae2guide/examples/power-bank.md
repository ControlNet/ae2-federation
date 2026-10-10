---
navigation:
  parent: examples/index.md
  title: A Shared Power Bank
  icon: appflux:flux_accessor
  position: 45
---

# A Shared Power Bank

**Goal:** keep FE in one power bank network and run a Mekanism Crusher on another network from it. The workshop has no
storage or energy cell of its own. This page appears because Applied Flux and Mekanism are installed.

**You need:** a power bank network with ME Drives holding <ItemLink id="appflux:fe_1k_cell" />s and an energy cell; a
workshop network with a terminal and a <ItemLink id="appflux:flux_accessor" />; a <ItemLink id="mekanism:crusher" />;
a <ItemLink id="ae2federation:switch" /> both networks touch. How FE gets into the cells is in
[Applied Flux's guide](appflux:appflux/flux_cells.md).

<GameScene zoom="5" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/power_bank.snbt" />
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 2 1">
    Power bank: ME FE Storage Cells in ME Drives, and the energy cell that powers both networks
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="4 0 0" max="7 1 1">
    Workshop: a terminal, a Flux Accessor and the Crusher, with no storage or energy cell of its own
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="6 1 1">
    Flux Accessor: sends the FE its network can reach into the machines it touches
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="4 1 1">
    One Switch: each face joins the network it touches
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the two networks and the rules between them:

<FederationTopology>
  <Network key="workshop" label="Workshop" color="#915dcd" column="0" row="0" details="Flux Accessor|Crusher" />
  <Network key="bank" label="Power bank" color="#5CA7CD" column="1" row="0" details="ME FE Storage Cells|Energy cell" />
  <Rule user="workshop" source="bank" capability="storage" />
  <Energy first="workshop" second="bank" />
</FederationTopology>

## Build it

1. **Join both networks** on the faces of one Switch, as in the scene.
2. **Switch on Storage under "Workshop uses Power bank's"**. The bank's FE now shows in the workshop's terminal, beside
   anything else the bank stores.
3. **Switch on ME power** between the two networks, so the workshop runs on the bank's energy cell.
4. **Set the Crusher's sides** in its side configuration. For energy, make the side towards the Flux Accessor an input;
   for items, make the top an input and the bottom an output.
5. **Give the Crusher cobblestone.** It crushes it into gravel on the bank's FE, which the Flux Accessor sends it.

The Flux Accessor draws FE from any storage its own network can reach, so the Storage rule is how it reaches the bank.
Any machine that takes FE works the same way.

## Try it

Switch "Workshop uses Power bank's" Storage off while the Crusher has cobblestone left. The bank's FE leaves the
workshop's terminal and the Flux Accessor sends no more, so the Crusher stops once the energy it holds runs out.
