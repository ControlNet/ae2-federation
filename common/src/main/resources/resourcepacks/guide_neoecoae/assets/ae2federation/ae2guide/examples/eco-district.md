---
navigation:
  parent: examples/index.md
  title: A Neo ECO District
  icon: neoecoae:computation_system_l4
  position: 80
---

# A Neo ECO District

**Goal:** use all three Neo ECO multiblocks across two networks without merging them. Your main network (network A)
keeps its items in an ECO storage system and runs its jobs on an ECO computation system; a factory network (network B)
holds the patterns in an ECO crafting system. A orders B's recipes, and B runs on A's power. This page appears because
Neo ECO AE Extension is installed.

**You need:**

* on network A: a formed ECO storage system, with its controller (<ItemLink id="neoecoae:storage_system_l4" />), its
  drives (<ItemLink id="neoecoae:eco_drive" />) holding ECO storage matrices such as
  <ItemLink id="neoecoae:eco_item_storage_cell_16m" />, and its <ItemLink id="neoecoae:storage_interface" />; a formed
  ECO computation system, with its controller (<ItemLink id="neoecoae:computation_system_l4" />), a flash crystal matrix
  such as <ItemLink id="neoecoae:eco_computation_cell_l4" /> in one of its drives
  (<ItemLink id="neoecoae:computation_drive" />), and its <ItemLink id="neoecoae:computation_interface" />; a crafting
  terminal; power.
* on network B: a formed ECO crafting system, with its controller (<ItemLink id="neoecoae:crafting_system_l4" />),
  patterns in its <ItemLink id="neoecoae:crafting_pattern_bus" />es, and its
  <ItemLink id="neoecoae:crafting_interface" />. No power of its own.
* a <ItemLink id="ae2federation:switch" />.

<GameScene zoom="2" interactive={true} background="transparent">
  <ImportStructure src="../assets/examples/eco_district.snbt" />
  <BoxAnnotation color="#5CA7CD" min="0 0 0" max="5 3 2">
    Network B's factory: the smallest ECO crafting system, seen from the back, its interface on network B's cable
  </BoxAnnotation>
  <BoxAnnotation color="#dddddd" min="5 1 2" max="6 2 3">
    Switch: one face per network
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="6 0 0" max="11 3 2">
    Network A's CPU: the smallest ECO computation system, seen from the back, its interface on network A's cable
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="11 0 0" max="16 3 2">
    Network A's warehouse: the smallest ECO storage system, seen from the back, its interface on network A's cable
  </BoxAnnotation>
  <BoxAnnotation color="#915dcd" min="6 1 2" max="17 2 3">
    Network A's cable on both interfaces, with a crafting terminal and the energy cell that powers both networks
  </BoxAnnotation>
  <IsometricCamera yaw="15" pitch="30" />
</GameScene>

The Federation screen then shows the two networks and the rules between them:

<FederationTopology>
  <Network key="a" label="Network A" color="#915dcd" column="1" row="0" details="ECO computation system|ECO storage system" />
  <Network key="b" label="Network B" color="#5CA7CD" column="0" row="0" details="ECO crafting system" />
  <Rule user="a" source="b" capability="crafting" />
  <Energy first="a" second="b" />
</FederationTopology>

## Build it

1. **Build the three systems** as Neo ECO's guide shows for the
   [storage](neoecoae:neoecoae_intro/storage_system.md), [crafting](neoecoae:neoecoae_intro/crafting_system.md) and
   [computation](neoecoae:neoecoae_intro/computation_system.md) systems. The smallest of each is 5 blocks long, 3 high
   and 2 deep. Connect the storage and computation systems' interfaces to network A's cable, and the crafting system's
   interface to network B's.
2. **Fill them.** Put ECO storage matrices into the storage system's drives, with the ingredients your orders need,
   and patterns into the crafting system's pattern buses. Put a flash crystal matrix into one of the computation
   system's drives: without one, it has no room for a job.
3. **Connect networks A and B** to their own faces of a Switch.
4. **Switch on Crafting under "A uses B's"** in the Federation screen (no Storage rule is needed), and switch on
   **ME power** for the pair: the crafting system then runs on network A's power.
5. **Order from network A.** B's recipes are listed among A's craftables. The computation system plans the job and
   takes the ingredients from the storage system, the crafting system crafts on network B, and the results go back to
   A's computation system, which stores them in the storage system.

Each system keeps its own job. The storage and computation systems are A's own storage and CPU, so A needs nothing
from B but the patterns. The crafting system is one of B's pattern providers, whose patterns the Crafting rule offers
to A. Network B needs no CPU and no storage of its own.

## Try it

Break one casing of the crafting system. The structure comes apart and its recipes disappear from A's terminal. Put
the casing back: the system forms again and they return.
