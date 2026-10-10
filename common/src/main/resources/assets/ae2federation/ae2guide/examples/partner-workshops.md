---
navigation:
  parent: examples/index.md
  title: Partner Workshops
  icon: ae2:pattern_provider
  position: 22
---

# Partner Workshops

**Goal:** two workshops each keep their own recipes and each order what the other makes. Here workshop A makes sticks
and workshop B makes planks: A orders planks from B, and B orders sticks from A, each on its own crafting CPU.

**You need:** two networks, each with a crafting CPU, a crafting terminal, storage, and a
<ItemLink id="ae2:pattern_provider" /> next to a <ItemLink id="ae2:molecular_assembler" />; power on one of them; a
<ItemLink id="ae2federation:bridge" /> where they touch.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/partner_workshops.snbt" />
  <BoxAnnotation color="#915dcd" min="4.375 0 0" max="9 2 1">
    Workshop A: crafting terminal, crafting CPU, storage, the sticks pattern, and the energy cell that powers both
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="4 0.25 0.25" max="4.375 0.75 0.75">
    Bridge: on A's cable, its outer side touching B's cable
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="4 2 1">
    Workshop B: crafting terminal, crafting CPU, storage and the planks pattern
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the two workshops and the rules both ways:

<FederationTopology>
  <Network key="a" label="Workshop A" color="#915dcd" column="0" row="0" details="Sticks pattern|Energy cell" />
  <Network key="b" label="Workshop B" color="#5CA7CD" column="1" row="0" details="Planks pattern" />
  <Rule user="a" source="b" capability="crafting" />
  <Rule user="b" source="a" capability="crafting" />
  <Energy first="a" second="b" />
</FederationTopology>

## Build it

1. **Place the Bridge** on one workshop's cable, with its outer side touching the other's.
2. **Switch on Crafting under "A uses B's" and under "B uses A's"**. Each workshop pays with its own storage, so no Storage rule is needed.
3. **Switch on ME power** for the pair, so B runs on A's energy cell.
4. **Order planks from A**, then **order sticks from B**. Each workshop lists the other's recipe among its own.

## How the jobs run

Each order runs on the orderer's own crafting CPU, which sends the ingredients to the other workshop's pattern
provider and gets the results back. The other workshop's CPU takes no part. The two directions are separate rules:
one workshop can stop ordering from the other while the other keeps ordering from it.

## Try it

Switch "B uses A's" Crafting off. B can no longer order sticks, but A still orders planks from B.
