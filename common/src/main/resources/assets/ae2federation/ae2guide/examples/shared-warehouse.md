---
navigation:
  parent: examples/index.md
  title: A Shared Warehouse
  icon: ae2:drive
  position: 10
---

# A Shared Warehouse

**Goal:** keep your stock in one warehouse network, and take from it and store into it from the terminals of separate
workshop networks. Each network keeps its own channels and power.

**You need:** a warehouse network with storage, one or more workshop networks with a terminal, each with power, and a
<ItemLink id="ae2federation:router" />.

<GameScene zoom="4" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/shared_warehouse.snbt" />
  <BoxAnnotation color="#5CA7CD" min="1 1 0" max="4 4 1">
    The warehouse: drives and its own power
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="3 0 0" max="5 1 1">
    Workshop 1: a terminal and its own power
  </BoxAnnotation>
  <BoxAnnotation color="#5ccd78" min="0 0 0" max="2 1 1">
    Workshop 2: a terminal and its own power
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 0 0" max="3 1 1">
    Router: one face per network
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Build it

1. **Connect each network to its own face of the Router**, with an ME cable touching that face. One Router takes up
   to six networks. Networks far apart each get their own Router, joined by
   <ItemLink id="ae2federation:cable" />; see [Getting Started](../getting-started.md).
2. **Open the Federation screen** by right-clicking the Router, and select the line between the warehouse and the
   first workshop.
3. **Switch on Storage under "Workshop uses Warehouse's".** Leave the other direction off, so the warehouse does not
   see the workshop's storage.
4. **Check it.** Open the workshop's terminal: the warehouse's items are listed with the workshop's own. Take some
   out, and put something in: with no storage of its own, the workshop stores it in the warehouse.
5. **Add the second workshop** the same way: its own Router face and its own rule. The two workshops do not see each
   other unless you switch that pair on too.

## What is shared

The rule shares all of the warehouse's ME storage, including fluids and other resources that addons add, and lets the
workshop insert as well as extract. It is not filtered by item and does not keep other players out. Stock that a
workshop must not touch belongs on a network you do not share.

## Try it

Switch the rule off. The warehouse's items disappear from the workshop's terminal, and the warehouse still works on
its own. Switch it back on and they return. If a rule stays yellow or red, its reason is shown under its switch; see
[Troubleshooting](../troubleshooting.md).
