---
navigation:
  parent: examples/index.md
  title: Order from an Alloy Furnace
  icon: useless_mod:advanced_alloy_furnace_block
  position: 71
---

# Order from an Alloy Furnace

**Goal:** UselessMod's Advanced Alloy Furnace keeps crafting patterns in its own pattern slots and crafts them itself,
with no Molecular Assembler. Put it on a network of its own and order its recipes from your main network. This page
appears because UselessMod is installed.

**You need:** the furnace's network (network B): the <ItemLink id="useless_mod:advanced_alloy_furnace_block" /> on an
ME cable, with no storage and no power of its own; your main network (network A) with a crafting CPU, a crafting
terminal, storage and power; a <ItemLink id="ae2federation:bridge" /> on A's cable whose outer side touches B's cable.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/alloy_furnace.snbt" />
  <BoxAnnotation color="#915dcd" min="2.375 0 0" max="5 2 1">
    Network A: crafting terminal, crafting CPU, storage, and the energy cell that powers both networks
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0.25 0.25" max="2.375 0.75 0.75">
    Bridge: on network A's cable, its outer side touching network B's cable
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="2 1 1">
    Network B: the Advanced Alloy Furnace with your crafting patterns, and nothing else
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the two networks and the rules between them:

<FederationTopology>
  <Network key="a" label="Network A" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Storage, energy cell" />
  <Network key="b" label="Network B" color="#5CA7CD" column="1" row="0" details="Advanced Alloy Furnace" />
  <Rule user="a" source="b" capability="crafting" />
  <Rule user="a" source="b" capability="storage" />
  <Energy first="a" second="b" />
</FederationTopology>

## Build it

1. **Put the furnace on network B's cable** and put your crafting patterns into its pattern slots. It needs a
   channel, like other AE2 devices.
2. **Connect the two networks** with a Bridge on A's cable, its outer side touching B's cable. If they are far apart,
   use a Switch on each network and Federation Cable between them instead.
3. **Switch on Crafting under "A uses B's"** in the Federation screen, and ME power for the pair, so network B runs
   on network A's power. The furnace crafts these patterns with no FE of its own.
4. **Order from network A.** The furnace's recipes are listed among A's craftables, and A's crafting CPU runs the job
   while the furnace crafts.

Network B has no storage. What the furnace makes goes straight back to A's waiting CPU, so it ends up in A's storage.

## Try it

Take a pattern out of the furnace. Its recipe disappears from A's terminal. Put the pattern back: the recipe returns,
and A can order it again.
