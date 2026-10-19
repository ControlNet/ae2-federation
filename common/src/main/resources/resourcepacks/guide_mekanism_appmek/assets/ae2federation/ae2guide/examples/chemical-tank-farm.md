---
navigation:
  parent: examples/index.md
  title: A Chemical Tank Farm
  icon: mekanism:dynamic_tank
  position: 50
---

# A Chemical Tank Farm

**Goal:** keep Mekanism's chemicals on a tank farm network, in a Dynamic Tank and in chemical cells, and use them from
the terminal of another network through a Storage rule. This page appears because Mekanism and Applied Mekanistics are
installed.

**You need:** a tank farm network with an energy cell, a drive with
<ItemLink id="appmek:chemical_storage_cell_1k" />s, a Dynamic Tank (<ItemLink id="mekanism:dynamic_tank" /> and a
<ItemLink id="mekanism:dynamic_valve" />) and an <ItemLink id="ae2:storage_bus" />; a workshop network with a terminal;
a <ItemLink id="ae2federation:switch" /> both touch.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/chemical_tank_farm.snbt" />
  <BoxAnnotation color="#915dcd" min="6 0 0" max="7 1 1">
    Workshop: a terminal, running on the tank farm's power
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 0 0" max="6 1 1">
    Switch: one face per network
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="1 0 0" max="5 2 1">
    Tank farm: the energy cell that powers both networks, a drive with chemical cells, and a Storage Bus
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="0 0 1" max="3 3 4">
    The smallest Dynamic Tank, 3 by 3 by 3, with the Storage Bus on the Dynamic Valve in the middle of its front
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

In the Federation screen, the workshop uses the tank farm's storage and shares its energy:

<FederationTopology>
  <Network key="workshop" label="Workshop" color="#915dcd" column="0" row="0" details="Terminal" />
  <Network key="farm" label="Tank farm" color="#5CA7CD" column="1" row="0" details="Dynamic Tank, chemical cells|Energy cell" />
  <Rule user="workshop" source="farm" capability="storage" />
  <Energy first="workshop" second="farm" />
</FederationTopology>

## Build it

1. **Build the Dynamic Tank** on the tank farm network: the smallest is 3 blocks each way, Dynamic Tank casing all
   round one block of air, with a Dynamic Valve in the middle of one face.
2. **Put a Storage Bus on the valve**, on an ME cable of the tank farm. The valve needs no setting. With Applied
   Mekanistics, the Storage Bus reads the tank's chemical as AE2 storage.
3. **Add chemical cells** for the chemicals you do not keep in the tank: Applied Mekanistics' Chemical Storage Cells in
   a drive on the tank farm. Mekanism's chemicals are not fluids, so a fluid cell does not hold them.
4. **Join both networks to the Switch**, each with an ME cable touching one face.
5. **Switch on Storage under "Workshop uses Tank farm's"** in the Federation screen, and **ME power** for the pair:
   the workshop then runs on the tank farm's power.
6. **Check it.** The workshop's terminal lists the tank farm's chemicals. Take some out, and store some back: with no
   storage of its own, the workshop stores them on the tank farm.

The rule shares all of the tank farm's storage, chemicals with everything else, as in
[A Shared Warehouse](shared-warehouse.md).

## Try it

Switch the Storage rule off. The tank's chemical and the cells' contents disappear from the workshop's terminal; the
tank farm still holds them. Switch it back on and they return.
