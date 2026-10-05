---
navigation:
  parent: examples/index.md
  title: Speed Up Your Own CPU
  icon: extendedae_plus:4x_crafting_accelerator
  position: 75
---

# Speed Up Your Own CPU

**Goal:** order from an assembly workshop on another network, as in
[Order from an Assembly Workshop](remote-assembly.md), with ExtendedAE-Plus' 4x Crafting Accelerator in your own
crafting CPU. This page appears because ExtendedAE-Plus is installed.

**You need:** the workshop network (network B) with <ItemLink id="ae2:pattern_provider" />s and
<ItemLink id="ae2:molecular_assembler" />s; your main network (network A) with power, a crafting terminal, storage and a
crafting CPU built from a <ItemLink id="ae2:1k_crafting_storage" /> and a
<ItemLink id="extendedae_plus:4x_crafting_accelerator" />; a <ItemLink id="ae2federation:router" /> for each network
and <ItemLink id="ae2federation:cable" /> between them.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/accelerated_cpu.snbt" />
  <BoxAnnotation color="#915dcd" min="7 0 0" max="9 3 1">
    Network A: crafting terminal, storage, the energy cell that powers both networks, and a crafting CPU with a 4x
    Crafting Accelerator on its crafting storage
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="7 1 1">
    A Router on each network, joined by Federation Cable
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 3 1">
    Network B: pattern providers with their patterns, next to Molecular Assemblers
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the two networks and the rules between them:

<FederationTopology>
  <Network key="a" label="Network A" color="#915dcd" column="0" row="0" details="CPU with 4x accelerator|Terminal, storage, energy cell" />
  <Network key="b" label="Network B" color="#5CA7CD" column="1" row="0" details="Pattern providers|Molecular Assemblers" />
  <Rule user="a" source="b" capability="crafting" />
  <Rule user="a" source="b" capability="storage" />
  <Energy first="a" second="b" />
</FederationTopology>

## Build it

1. **Build network A's crafting CPU** with the accelerator as one of its blocks. It counts as four co-processors; see
   [ExtendedAE-Plus' guide](extendedae_plus:introduction/devices/crafting_accelerator.md).
2. **Connect the two networks** with Routers and Federation Cable.
3. **Switch on Crafting under "A uses B's"** in the Federation screen, and ME power for the pair, so network B runs
   on network A's power.
4. **Order from network A.** The CPU's status lists its 4 co-processors while it runs the job.

## Where the accelerator goes

The job always runs on the ordering network's CPU, so that is the CPU to upgrade. Co-processors let the CPU start
more crafting steps at a time; they do not make network B's machines faster. If B has only a few assemblers, those
are the limit, and more assemblers on B help more than more accelerators on A.

## Try it

Move the accelerator to network B, onto a crafting storage there. A's next order still runs on A's own CPU, now with
no co-processors: the accelerator speeds up only B's CPU, which takes no part in A's orders.
