---
navigation:
  parent: examples/index.md
  title: A Shared Warehouse
  icon: ae2:drive
  position: 10
---

# A Shared Warehouse

**Goal:** keep your stock in one warehouse network, and take from it and store into it from the terminals of separate
workshop networks. Each network keeps its own channels, and only the warehouse needs power.

**You need:** a warehouse network with storage and power, one or more workshop networks with a terminal, and a
<ItemLink id="ae2federation:switch" />.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/shared_warehouse.snbt" />
  <BoxAnnotation color="#5CA7CD" min="1 1 0" max="4 4 1">
    The warehouse: drives, and the energy cell that powers every network here
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="3 0 0" max="4 1 1">
    Workshop 1: a terminal, running on the warehouse's power
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="1 0 0" max="2 1 1">
    Workshop 2: a terminal, running on the warehouse's power
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    Switch: one face per network
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

In the Federation screen, each workshop uses the warehouse's storage and shares its energy:

<FederationTopology>
  <Network key="w1" label="Workshop 1" color="#915dcd" column="0" row="0" details="Terminal" />
  <Network key="wh" label="Warehouse" color="#5CA7CD" column="1" row="0" details="Drives|Energy cell" />
  <Network key="w2" label="Workshop 2" color="#5ccd78" column="2" row="0" details="Terminal" />
  <Rule user="w1" source="wh" capability="storage" />
  <Rule user="w2" source="wh" capability="storage" />
  <Energy first="w1" second="wh" />
  <Energy first="wh" second="w2" />
</FederationTopology>

## Build it

1. **Connect each network to its own face of the Switch**, with an ME cable touching that face. One Switch takes up
   to six networks. Networks far apart each get their own Switch, joined by
   <ItemLink id="ae2federation:cable" />; see [Getting Started](../getting-started.md).
2. **Open the Federation screen** by right-clicking the Switch, and select the line between the warehouse and the
   first workshop.
3. **Switch on Storage under "Workshop uses Warehouse's".** Leave the other direction off, so the warehouse does not
   see the workshop's storage. Switch on **ME power** for the pair too: the workshop then runs on the warehouse's
   power.
4. **Check it.** Open the workshop's terminal: the warehouse's items are listed with the workshop's own. Take some
   out, and put something in: with no storage of its own, the workshop stores it in the warehouse.
5. **Add the second workshop** the same way: its own Switch face, its own Storage rule and ME power. The two workshops
   do not see each other's storage unless you switch that pair on too. Their power is pooled through the warehouse
   all the same: networks that share energy with a common network share one pool.

## What is shared

The rule shares all of the warehouse's ME storage, including fluids and other resources that addons add, and lets the
workshop insert as well as extract. It is not filtered by item and does not keep other players out. Stock that a
workshop must not touch belongs on a network you do not share.

## Try it

Switch the rule off. The warehouse's items disappear from the workshop's terminal, and the warehouse still works on
its own. Switch it back on and they return. If a rule stays yellow or red, its reason is shown under its switch; see
[Troubleshooting](../troubleshooting.md).
