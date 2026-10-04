---
navigation:
  parent: examples/index.md
  title: Fit Providers into One Block
  icon: data_energistics:adaptive_pattern_provider
  position: 74
---

# Fit Providers into One Block

**Goal:** replace the workshop's pattern providers from [Order from an Assembly Workshop](remote-assembly.md) with
Data Energistics' Adaptive Pattern Providers, each of which holds several pattern providers in its provider slot, while
your main network keeps ordering from it. This page appears because Data Energistics is installed.

**You need:** the workshop network (network B) with <ItemLink id="ae2:pattern_provider" />s next to
<ItemLink id="ae2:molecular_assembler" />s; an <ItemLink id="data_energistics:adaptive_pattern_provider_upgrade" />
for each provider; your main network (network A) with a crafting CPU, a crafting terminal and storage; a
<ItemLink id="ae2federation:router" /> for each network and <ItemLink id="ae2federation:cable" /> between them.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/adaptive_providers.snbt" />
  <BoxAnnotation color="#915dcd" min="7 0 0" max="9 2 1">
    Network A: crafting terminal, crafting CPU and storage
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="7 1 1">
    A Router on each network, joined by Federation Cable
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="3 3 1">
    Network B: Adaptive Pattern Providers, each with an AE2 Pattern Provider fitted, next to Molecular Assemblers
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Build it

1. **Start from the assembly workshop example:** the two networks connected, with Crafting switched on under
   "A uses B's".
2. **Sneak and use an Adaptive Pattern Provider Upgrade on each of B's pattern providers.** Each one becomes an
   Adaptive Pattern Provider and keeps its patterns, but offers none of them yet.
3. **Put an AE2 Pattern Provider into each one's provider slot.** Every fitted provider adds its pattern slots, and
   the kept patterns are offered again. See
   [Data Energistics' guide](data_energistics:items-blocks-machines/6.17_adaptive_pattern_provider.md).
4. **Order from network A** as before.

An Adaptive Pattern Provider offers the patterns in its slots to network B as an AE2 pattern provider does, so
Federation shares them in the same way.

## Try it

Order from network A, then upgrade one of B's providers. Its recipes disappear from A's terminal, because the new
block offers no patterns until a provider is fitted. Put an AE2 Pattern Provider into its provider slot: the recipes
return, and your next order runs through the Adaptive Pattern Provider.
