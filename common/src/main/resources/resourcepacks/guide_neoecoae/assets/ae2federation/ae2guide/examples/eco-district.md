---
navigation:
  parent: examples/index.md
  title: A Neo ECO Warehouse
  icon: neoecoae:storage_system_l4
  position: 80
---

# A Neo ECO Warehouse

**Goal:** use a Neo ECO storage system as the warehouse of [A Shared Warehouse](shared-warehouse.md): it stays on its
own network, and workshops on other networks take from it and store into it. This page appears because Neo ECO AE
Extension is installed.

**You need:** a warehouse network with power and a formed ECO storage system, with its controller
(<ItemLink id="neoecoae:storage_system_l4" />), its drives (<ItemLink id="neoecoae:eco_drive" />) holding ECO storage
cells such as <ItemLink id="neoecoae:eco_item_storage_cell_16m" />, and its
<ItemLink id="neoecoae:storage_interface" />; a workshop network with a terminal and power; a
<ItemLink id="ae2federation:router" />.

<GameScene zoom="3" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/eco_storage_system.snbt" />
  <BoxAnnotation color="#5CA7CD" min="3 0 0" max="9 3 2">
    The warehouse: the smallest ECO storage system, its storage interface at the back on the warehouse network's cable,
    and the network's own power
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="2 1 1" max="3 2 2">
    Router: one face per network
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="0 1 1" max="2 2 2">
    The workshop: a terminal and its own power
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Build it

1. **Build the storage system** as [Neo ECO's guide](neoecoae:neoecoae_intro/storage_system.md) shows, put ECO storage
   cells in its drives, and connect its storage interface to the warehouse network's cable. The smallest one is 5
   blocks long, 3 high and 2 deep.
2. **Connect the warehouse network and the workshop** to their own faces of a Router.
3. **Switch on Storage under "Workshop uses Warehouse's"** in the Federation screen.
4. **Check it.** The workshop's terminal lists what the ECO cells hold. Take some out and put something back: it goes
   into the ECO cells.

The formed storage system is part of the warehouse network's ME storage, so the rule shares it as it shares drives.

## Try it

Break one casing of the storage system. The structure comes apart and its items disappear from the workshop's
terminal. Put the casing back: the system forms again and they return.
