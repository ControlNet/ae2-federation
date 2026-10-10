---
navigation:
  parent: examples/index.md
  title: Order from an Assembler Matrix
  icon: extendedae:assembler_matrix_frame
  position: 70
---

# Order from an Assembler Matrix

**Goal:** a workshop network crafts with ExtendedAE's Assembler Matrix, a multiblock that holds patterns and crafts
them. Order its recipes from your main network, as in [Order from an Assembly Workshop](remote-assembly.md). This page
appears because ExtendedAE is installed.

**You need:** the workshop network (network B) with a formed <ItemLink id="extendedae:assembler_matrix_frame" />
structure; your main network (network A) with a crafting CPU, a crafting terminal, storage and power; a
<ItemLink id="ae2federation:switch" /> for each network and <ItemLink id="ae2federation:cable" /> between them.

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/assembler_matrix.snbt" />
  <BoxAnnotation color="#915dcd" min="9 0 0" max="11 2 1">
    Network A: crafting terminal, crafting CPU, storage, and the energy cell that powers both networks
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="9 1 1">
    A Switch on each network, joined by Federation Cable
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="4 3 3">
    Network B: the smallest Assembler Matrix, frames on its edges and walls on its faces, with a pattern core and a
    crafter core inside
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the two networks and the rules between them:

<FederationTopology>
  <Network key="a" label="Network A" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Storage, energy cell" />
  <Network key="b" label="Network B" color="#5CA7CD" column="1" row="0" details="Assembler Matrix" />
  <Rule user="a" source="b" capability="crafting" />
  <Rule user="a" source="b" capability="storage" />
  <Energy first="a" second="b" />
</FederationTopology>

## Build it

1. **Build the matrix on network B** as [ExtendedAE's guide](extendedae:epp_intro/assembler_matrix.md) shows, and put your crafting patterns into it. The smallest
   one is 4 blocks long, 3 high and 3 deep. Its frame joins network B like any other AE2 block.
2. **Connect the two networks** with Switches and Federation Cable.
3. **Switch on Crafting under "A uses B's"** in the Federation screen, and ME power for the pair, so network B runs
   on network A's power.
4. **Order from network A.** The matrix's recipes are listed among A's craftables, and A's crafting CPU runs the job
   while the matrix crafts.

The matrix offers its patterns to network B the way pattern providers do, so Federation shares them in the same way.
You keep configuring the matrix on network B.

## Try it

Break one wall of the matrix. The structure comes apart and its recipes disappear from A's terminal. Put the wall back:
the matrix forms again and its recipes return.
