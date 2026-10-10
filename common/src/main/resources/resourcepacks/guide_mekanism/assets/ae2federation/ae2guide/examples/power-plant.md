---
navigation:
  parent: examples/index.md
  title: A Power Plant
  icon: mekanism:basic_induction_cell
  position: 43
---

# A Power Plant

**Goal:** a plant network keeps your base's power in a Mekanism Induction Matrix and powers two other networks, a
district and its workshop, through ME power. None of the networks has an energy cell: the matrix is the only store of
power. This page appears because Mekanism is installed.

**You need:** a plant network with an Induction Matrix (<ItemLink id="mekanism:induction_casing" />,
<ItemLink id="mekanism:induction_port" />, a <ItemLink id="mekanism:basic_induction_cell" /> and a
<ItemLink id="mekanism:basic_induction_provider" />) and an <ItemLink id="ae2:energy_acceptor" />; a district network
with a crafting CPU, a crafting terminal and storage; a workshop network with a pattern provider and a molecular
assembler; a <ItemLink id="ae2federation:switch" /> all three touch.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/power_plant.snbt" />
  <BoxAnnotation color="#5CA7CD" min="2 0 1" max="5 4 5">
    Plant: the Induction Matrix, its port set to output into an Energy Acceptor on an ME cable
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="4 0 0" max="6 2 1">
    District: crafting terminal, crafting CPU and drive
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="3 1 1">
    Workshop: a pattern provider with a pattern from two planks to four sticks, beside a molecular assembler
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="3 0 0" max="4 1 1">
    One Switch: each face joins the network it touches
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

The Federation screen then shows the three networks and the rules between them:

<FederationTopology>
  <Network key="district" label="District" color="#915dcd" column="0" row="1" details="Crafting CPU, terminal|Drive" />
  <Network key="plant" label="Plant" color="#5CA7CD" column="1" row="0" details="Induction Matrix|Energy Acceptor" />
  <Network key="workshop" label="Workshop" color="#5ccd78" column="2" row="1" details="Sticks pattern" />
  <Rule user="district" source="workshop" capability="crafting" />
  <Energy first="plant" second="district" />
  <Energy first="plant" second="workshop" />
</FederationTopology>

## Build it

1. **Join the three networks** on the faces of one Switch, as in the scene.
2. **Build the plant:** an ME cable on the Switch's face with the Energy Acceptor on top, and the Induction Matrix
   beyond it with its port touching the acceptor. Sneak-right-click the port with a
   <ItemLink id="mekanism:configurator" /> to set it to output: it then pushes the matrix's power into the acceptor,
   which turns it into AE.
3. **Charge the matrix** from your generators through another Induction Port set to input.
4. **Switch on ME power** for the plant and the district, and for the plant and the workshop. Both run on the matrix.
5. **Switch on Crafting under "District uses Workshop's"**; no Storage rule is needed. Order sticks in the
   district's crafting terminal, with planks in the district's storage.

## How it runs

The Energy Acceptor fills the three networks' small internal buffers from the matrix as fast as they use power, so the
networks need no energy cell of their own. The matrix's level drops as they run, and your generators refill it.

## Try it

Switch off ME power for the plant and the workshop. The workshop goes dark and sticks disappear from the district's
craftables, while the district keeps running on the plant.
