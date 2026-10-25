---
navigation:
  parent: items/index.md
  title: ECO - N1
  icon: ae2federation:eco_computation_system_l1
  position: 471
categories:
- devices
item_ids:
- ae2federation:eco_computation_system_l1
---

# ECO - N1

<Row>
  <ItemImage id="ae2federation:eco_computation_system_l1" scale="3" />
  <ItemImage id="ae2federation:eco_computation_interface" scale="3" />
</Row>

The smallest Federation Computation System, a tier below
<ItemLink id="ae2federation:eco_computation_system_l4" />. It is Neo ECO Prototype's
[L1 computation system](neoecoprototype:neoecoprototype_intro/computation_system.md) with an ECO - N1 Federation
Computation Subsystem Controller in place of the <ItemLink id="neoecoprototype:simplify_computation_system" />, and it
runs Federation programs instead of crafting jobs. This page appears because Neo ECO Prototype 1.4.0 or later is
installed.

* **Building:** as the L1 structure, with our controller, and one to four
  <ItemLink id="ae2federation:eco_computation_interface" />s anywhere in the side face, the 2x3 end where the L1
  structure puts its interface; casing fills the rest of that face. Neo ECO Prototype's own interfaces are refused
  there. The controller's Structure Builder builds it with one communication interface.
* **Linking, power, capacity, ID:** as the other Federation Computation System controllers. Each interface takes one
  <ItemLink id="ae2federation:cable" /> once the structure has formed; no ME network joins the structure, and it is
  not a crafting CPU.
* **Compute:** 24 CU per <ItemLink id="neoecoprototype:simplify_computation_parallel_core" />, and 1,024 CU from an
  energized parallel core behind the controller.
* **Bandwidth:** 1,572,864 B/s per <ItemLink id="neoecoprototype:simplify_computation_cell_1m" /> in its drives,
  5,242,880 B/s per energized array.
* **Processes:** one per installation; two per <ItemLink id="neoecoprototype:simplify_computation_threading_core" />,
  16 from an energized threading core in the first threading cell.

These are Neo ECO Prototype's defaults; its server config (`l1_computation`) changes the plain L1 parts' numbers for
N1 as for the L1 host.

It is crafted on a crafting table from the L1 host's recipe, with a
<ItemLink id="ae2federation:nexus_processor" /> in place of the Calculation Processor.
