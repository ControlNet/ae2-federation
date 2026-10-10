---
navigation:
  parent: examples/index.md
  title: A Tianshu District and Foundry
  icon: ae2lt:tianshu_supercomputer_controller
  position: 72
---

# A Tianshu District and Foundry

**Goal:** a district network whose only CPU is AE2 Lightning Tech's Tianshu Supercomputer orders sticks from a foundry
network whose only crafter is its Tianshu Matter Warping Matrix. Each multiblock stays on its own network, and the two
work together through one Switch. This page appears because AE2 Lightning Tech is installed.

**You need:** a district network with a formed Tianshu Supercomputer
(<ItemLink id="ae2lt:tianshu_supercomputer_controller" />), a crafting terminal, storage and an energy cell; a foundry
network with a formed Tianshu Matter Warping Matrix (<ItemLink id="ae2lt:matter_warping_matrix_controller" />) and a
crafting pattern from two planks to four sticks; a <ItemLink id="ae2federation:switch" /> both touch. How to build them
is in AE2 Lightning Tech's guide: [the Supercomputer](ae2lt:tianshu/construction.md) and
[the Matrix](ae2lt:matrix/construction.md).

<GameScene zoom="2" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/tianshu_foundry.snbt" />
  <BoxAnnotation color="#915dcd" min="9 0 0" max="17 8 7">
    District: the Tianshu Supercomputer, a crafting terminal, a drive, and the energy cell that powers both networks
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="8 11 7">
    Foundry: the Tianshu Matter Warping Matrix, with the sticks pattern in a Pattern Storage
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="8 0 0" max="9 1 1">
    One Switch: each face joins the network it touches
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the two networks and the rules between them:

<FederationTopology>
  <Network key="district" label="District" color="#915dcd" column="0" row="0" details="Tianshu Supercomputer|Energy cell" />
  <Network key="foundry" label="Foundry" color="#5CA7CD" column="1" row="0" details="Matter Warping Matrix" />
  <Rule user="district" source="foundry" capability="crafting" />
  <Energy first="district" second="foundry" />
</FederationTopology>

## Build it

1. **Join the two networks** on the faces of one Switch, as in the scene: ME cable from one face to the
   <ItemLink id="ae2lt:tianshu_supercomputer_port" /> in the middle of the Supercomputer's bottom face, and from another
   to the <ItemLink id="ae2lt:matter_warping_matrix_port" /> opposite the Matrix's controller. Each Port takes cables only
   once its structure has formed.
2. **Put the sticks pattern in the Matrix**, through its Port's pattern screen.
3. **Switch on Crafting under "District uses Foundry's"**. The district pays with its own storage, so no Storage
   rule is needed.
4. **Switch on ME power** for the two networks, so the Matrix runs on the district's energy cell: 8 AE/t while idle, and
   1 AE for each craft it takes.
5. **Order sticks** in the district's crafting terminal, with planks in the district's storage.

## How the job runs

The Supercomputer plans and runs the job like any crafting CPU. It sends the planks to the Matrix, which crafts the
sticks itself, with no molecular assembler, and puts them into the foundry's network. Federation hands them straight
back to the district as they arrive. The foundry needs no CPU, no storage and no power of its own.

## Try it

Break one block of the Matrix's casing. The structure comes apart and its Port leaves the foundry's network, so sticks
disappear from the district's craftables, and the Federation screen shows the foundry as "Split pending". Put the block
back: the Matrix forms again, the two halves join, and sticks return to the district.
