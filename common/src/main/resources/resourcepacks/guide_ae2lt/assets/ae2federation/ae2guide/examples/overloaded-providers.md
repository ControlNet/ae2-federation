---
navigation:
  parent: examples/index.md
  title: Upgrade the Workshop's Providers
  icon: ae2lt:overloaded_pattern_provider
  position: 72
---

# Upgrade the Workshop's Providers

**Goal:** the workshop from [Order from an Assembly Workshop](remote-assembly.md) runs out of pattern slots. Upgrade
its pattern providers to AE2 Lightning Tech's Overloaded Pattern Providers, with 36 pattern slots each, while your main
network keeps ordering from it. This page appears because AE2 Lightning Tech is installed.

**You need:** the workshop network (network B) with <ItemLink id="ae2:pattern_provider" />s next to
<ItemLink id="ae2:molecular_assembler" />s; an <ItemLink id="ae2lt:overloaded_pattern_provider_upgrade" /> for each
provider; your main network (network A) with a crafting CPU, a crafting terminal and storage; a
<ItemLink id="ae2federation:router" /> for each network and <ItemLink id="ae2federation:cable" /> between them.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/overloaded_providers.snbt" />
  <BoxAnnotation color="#915dcd" min="7 0 0" max="9 2 1">
    Network A: crafting terminal, crafting CPU and storage
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="7 1 1">
    A Router on each network, joined by Federation Cable
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 3 1">
    Network B: upgraded Overloaded Pattern Providers with their patterns, next to Molecular Assemblers
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Build it

1. **Start from the assembly workshop example:** the two networks connected, with Crafting switched on under
   "A uses B's".
2. **Use an Overloaded Pattern Provider Upgrade on each of B's pattern providers.** Each one becomes an Overloaded
   Pattern Provider and keeps its patterns and settings; see
   [AE2 Lightning Tech's guide](ae2lt:overloaded-network/overloaded-pattern-provider.md).
3. **Fill the new slots with more patterns.** Their recipes are listed among A's craftables like the others.
4. **Order from network A** as before.

An Overloaded Pattern Provider offers its patterns to network B as an AE2 pattern provider does, so Federation shares
them in the same way. This example uses its Normal mode, which pushes into the assembler beside it.

## Try it

Order from network A, then upgrade one of B's providers. A's terminal still lists its recipes, and your next order runs
through the Overloaded Pattern Provider.
