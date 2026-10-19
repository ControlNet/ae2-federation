---
navigation:
  parent: examples/index.md
  title: Order from a Matter Fabrication Well
  icon: molecularmanipulator:matter_fabrication_pattern_assembly
  position: 90
---

# Order from a Matter Fabrication Well

**Goal:** keep OmniSequence's Matter Fabrication Well on a production network of its own (network B) and order its
products from your main network (network A) without merging the networks. This page appears because OmniSequence:
Transfinite is installed.

**You need:** network B with a formed Matter Fabrication Well, its controller
(<ItemLink id="molecularmanipulator:matter_fabrication_controller" />) on B's cable, and a
<ItemLink id="molecularmanipulator:matter_fabrication_pattern_assembly" /> in one of its service positions; your main
network (network A) with a crafting CPU, a crafting terminal, storage and power; a <ItemLink id="ae2federation:switch" />.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/matter_fabrication_well.snbt" />
  <BoxAnnotation color="#915dcd" min="2 1 0" max="5 3 1">
    Network A: crafting terminal, crafting CPU, storage, and the energy cell that powers both networks
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 2 1" max="4 3 2">
    Switch: one face per network
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="3 2 2" max="5 3 8">
    Network B's cable along the service channel into the front of the controller
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="5 2 4" max="6 3 5">
    Pattern Assembly in the service position on the channel's front step, holding the processing pattern
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 4" max="7 4 10">
    A corner of the formed well, around its controller; the whole well is 41 by 41 blocks
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the two networks and the rules between them:

<FederationTopology>
  <Network key="a" label="Network A" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Storage, energy cell" />
  <Network key="b" label="Network B" color="#5CA7CD" column="1" row="0" details="Matter Fabrication Well|Pattern Assembly" />
  <Rule user="a" source="b" capability="crafting" />
  <Energy first="a" second="b" />
</FederationTopology>

## Build it

1. **Build the well on network B** as
   [OmniSequence's guide](molecularmanipulator:items-blocks-machines/matter_fabrication_well.md) shows, with its
   controller on B's cable.
2. **Install a Pattern Assembly** in one of the well's service positions. Making the assembly needs the well's tier 1
   research; see [Fabrication Research](molecularmanipulator:items-blocks-machines/matter_fabrication_research.md).
3. **Put a processing pattern into the assembly** that matches a well recipe exactly, inputs, outputs and amounts; see
   [Fabrication Pattern Assembly](molecularmanipulator:items-blocks-machines/matter_fabrication_pattern_assembly.md).
   4 Nether Quartz and 4 Bone Meal into 8 Calcite needs no research.
4. **Connect networks A and B** to their own faces of a Switch.
5. **Switch on Crafting under "A uses B's"** in the Federation screen, and ME power for the pair: the well then runs
   on network A's power.
6. **Order Calcite from network A.** A's crafting CPU sends the quartz and bone meal to the assembly, the well makes
   the calcite, and the assembly puts it into network B, from where it goes back to A's CPU.

Research belongs to the well's controller on network B. The rule shares the recipes that B's Pattern Assembly already
offers; it researches nothing for network A.

## Try it

Break one casing of the well. The well comes apart and Calcite disappears from A's craftables. Put the casing back:
the well forms again and Calcite returns.
