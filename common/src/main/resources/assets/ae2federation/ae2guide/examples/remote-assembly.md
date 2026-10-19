---
navigation:
  parent: examples/index.md
  title: Order from an Assembly Workshop
  icon: ae2:molecular_assembler
  position: 20
---

# Order from an Assembly Workshop

**Goal:** a workshop network already has <ItemLink id="ae2:pattern_provider" />s and
<ItemLink id="ae2:molecular_assembler" />s holding its patterns. Order its recipes from your main network without
moving the patterns or merging the networks.

**You need:** the workshop network (network B), set up the usual AE2 way but with no power of its own; your main network
(network A) with a crafting CPU, a crafting terminal, storage and power; a <ItemLink id="ae2federation:switch" /> for
each network and <ItemLink id="ae2federation:cable" /> between them, or a <ItemLink id="ae2federation:bridge" /> if they
touch.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/remote_assembly.snbt" />
  <BoxAnnotation color="#915dcd" min="7 0 0" max="9 2 1">
    Network A: crafting terminal, crafting CPU, storage, and the energy cell that powers both networks
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="7 1 1">
    A Switch on each network, joined by Federation Cable
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 3 1">
    Network B: pattern providers with their patterns, next to Molecular Assemblers
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the two networks and the rules between them:

<FederationTopology>
  <Network key="a" label="Network A" color="#915dcd" column="0" row="0" details="Crafting CPU, terminal|Storage, energy cell" />
  <Network key="b" label="Network B" color="#5CA7CD" column="1" row="0" details="Pattern providers|Molecular Assemblers" />
  <Rule user="a" source="b" capability="crafting" />
  <Energy first="a" second="b" />
</FederationTopology>

## Build it

1. **Leave the workshop as it is.** Its pattern providers keep their patterns, and its assemblers stay beside them.
2. **Connect the two networks** with Switches and Federation Cable.
3. **Switch on Crafting under "A uses B's"** in the Federation screen. A's CPU pays with the ingredients in A's own
   storage, so no Storage rule is needed.
4. **Switch on ME power** for the pair. The two networks then share one energy pool, so network B runs on network A's
   power.
5. **Order from network A.** B's recipes are listed among A's craftables. Request one as usual.

## How the job runs

Network A's crafting CPU plans and runs the job. It sends each step's ingredients to B's pattern providers, B's
assemblers craft as usual, and the results go back to A's CPU as soon as they enter network B. Network B needs no
crafting CPU of its own, and its CPUs are not shared with A.

Use this when the machines' network already has the patterns. If you want to keep the patterns on your own network
and send them to machines elsewhere, use a Federation Pattern Provider instead; see
[Outsourced Furnaces](endpoint-furnaces.md).

## Try it

Switch Crafting off: B's recipes disappear from A's terminal, and A can no longer order them. Remove A's crafting CPU
and order again: AE2 reports that no CPU is available, as on any network, because the CPU is always the orderer's.
