---
navigation:
  parent: examples/index.md
  title: A Neo ECO District
  icon: neoecoae:computation_system_l4
  position: 80
---

# A Neo ECO District

**Goal:** use all three Neo ECO multiblocks across two networks without merging them. A factory network (network B)
keeps an ECO storage system as its warehouse and an ECO crafting system with the patterns; your main network (network
A) runs an ECO computation system as its crafting CPU, orders B's recipes and sees B's storage. This page appears
because Neo ECO AE Extension is installed.

**You need:**

* on network B: a formed ECO storage system, with its controller (<ItemLink id="neoecoae:storage_system_l4" />), its
  drives (<ItemLink id="neoecoae:eco_drive" />) holding ECO storage matrices such as
  <ItemLink id="neoecoae:eco_item_storage_cell_16m" />, and its <ItemLink id="neoecoae:storage_interface" />; a formed
  ECO crafting system, with its controller (<ItemLink id="neoecoae:crafting_system_l4" />), patterns in its
  <ItemLink id="neoecoae:crafting_pattern_bus" />es, and its <ItemLink id="neoecoae:crafting_interface" />; power.
* on network A: a formed ECO computation system, with its controller
  (<ItemLink id="neoecoae:computation_system_l4" />), a flash crystal matrix such as
  <ItemLink id="neoecoae:eco_computation_cell_l4" /> in one of its drives
  (<ItemLink id="neoecoae:computation_drive" />), and its <ItemLink id="neoecoae:computation_interface" />; a crafting
  terminal; power.
* a <ItemLink id="ae2federation:router" />.

<GameScene zoom="2" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/eco_district.snbt" />
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="5 3 2">
    Network B's warehouse: the smallest ECO storage system, its interface at the back of its right end
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="5 0 0" max="10 3 2">
    Network B's factory: the smallest ECO crafting system, its interface at the back of its right end
  </BoxAnnotation>
  <BoxAnnotation color="#5CA7CD" min="3 1 2" max="10 2 3">
    Network B's cable on both interfaces, with the network's own power
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="10 1 2" max="11 2 3">
    Router: one face per network
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="11 0 0" max="16 3 2">
    Network A's CPU: the smallest ECO computation system, its interface at the back of its right end
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="11 1 2" max="17 2 3">
    Network A's cable on the computation system's interface, with a crafting terminal and the network's own power
  </BoxAnnotation>
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Build it

1. **Build the three systems** as Neo ECO's guide shows for the
   [storage](neoecoae:neoecoae_intro/storage_system.md), [crafting](neoecoae:neoecoae_intro/crafting_system.md) and
   [computation](neoecoae:neoecoae_intro/computation_system.md) systems. The smallest of each is 5 blocks long, 3 high
   and 2 deep. Connect the storage and crafting systems' interfaces to network B's cable, and the computation system's
   interface to network A's.
2. **Fill them.** Put ECO storage matrices into the storage system's drives and patterns into the crafting system's
   pattern buses. Put a flash crystal matrix into one of the computation system's drives: without one, it has no room
   for a job.
3. **Connect networks A and B** to their own faces of a Router.
4. **Switch on Crafting under "A uses B's"** in the Federation screen. This switches the same direction's Storage on
   too, so A's terminal lists what the ECO storage matrices hold.
5. **Order from network A.** B's recipes are listed among A's craftables. The computation system plans and runs the
   job, the crafting system crafts on network B, and the results go back to A's computation system.

Each system keeps its own job. The formed storage system is part of B's ME storage, which the Storage rule shares as
it shares drives. The crafting system is one of B's pattern providers, whose patterns the Crafting rule offers to A.
The computation system is A's own CPU: network B needs no CPU of its own, and its CPUs are not shared with A.

## Try it

Break one casing of the storage system. The structure comes apart and its items disappear from A's terminal. Put the
casing back: the system forms again and they return.
