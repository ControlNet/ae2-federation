---
navigation:
  parent: examples/index.md
  title: One Factory, Two Districts
  icon: extendedae_plus:super_assembler_matrix_frame
  position: 75
---

# One Factory, Two Districts

**Goal:** one factory network holds a Super Assembler Matrix full of crafting patterns, and two district networks order
from it at the same time, each on its own crafting CPU. The factory also powers both districts. This page appears
because ExtendedAE-Plus is installed.

**You need:** a formed Super Assembler Matrix on the factory network, with its patterns and an energy cell; two district
networks, each with a crafting CPU, a crafting terminal and storage; a <ItemLink id="ae2federation:switch" /> all three
touch. How to build the matrix is in
[ExtendedAE-Plus' guide](extendedae_plus:introduction/devices/super_assembler_matrix.md).

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/super_matrix_hub.snbt" />
  <BoxAnnotation color="#5CA7CD" min="1 0 1" max="6 3 7">
    Factory: the Super Assembler Matrix with its patterns, and the energy cell that powers all three networks
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="4 0 0" max="6 2 1">
    First district: crafting terminal, crafting CPU and storage
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="1 0 0" max="3 2 1">
    Second district: crafting terminal, crafting CPU and storage
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="4 1 1">
    One Switch: each face joins the network it touches
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the three networks and the rules between them:

<FederationTopology>
  <Network key="first" label="First district" color="#915dcd" column="0" row="0" details="Crafting CPU|Terminal, storage" />
  <Network key="factory" label="Factory" color="#5CA7CD" column="1" row="0" details="Super Assembler Matrix|Energy cell" />
  <Network key="second" label="Second district" color="#5ccd78" column="2" row="0" details="Crafting CPU|Terminal, storage" />
  <Rule user="first" source="factory" capability="crafting" />
  <Rule user="first" source="factory" capability="storage" />
  <Rule user="second" source="factory" capability="crafting" />
  <Rule user="second" source="factory" capability="storage" />
  <Energy first="first" second="factory" />
  <Energy first="factory" second="second" />
</FederationTopology>

## Build it

1. **Join the three networks** on the faces of one Switch, as in the scene. The matrix joins the factory's network
   through any of its outer blocks.
2. **Switch on Crafting under "First district uses Factory's" and under "Second district uses Factory's"**. Each
   switches its own Storage on.
3. **Switch on ME power** between the factory and each district, so both districts run on the factory's energy cell.
4. **Order from both districts.** The matrix's recipes are listed among each district's craftables.

## How the jobs run

Each district's own crafting CPU runs its order and sends the ingredients to the matrix, which crafts both orders side
by side and sends each result back to the CPU that ordered it. The factory needs no CPU, and the districts never see
each other's storage: each has rules with the factory only.

## Try it

Switch "Second district uses Factory's" Crafting off. The matrix's recipes disappear from the second district only;
the first district goes on ordering.
