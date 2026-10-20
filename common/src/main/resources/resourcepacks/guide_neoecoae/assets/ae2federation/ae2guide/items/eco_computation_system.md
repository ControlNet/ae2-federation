---
navigation:
  parent: automation/index.md
  title: Federation Computation System
  icon: ae2federation:eco_computation_system_l4
  position: 157
categories:
- devices
item_ids:
- ae2federation:eco_computation_system_l4
- ae2federation:eco_computation_system_l6
- ae2federation:eco_computation_system_l9
- ae2federation:eco_computation_interface
---

# Federation Computation System

<Row>
  <ItemImage id="ae2federation:eco_computation_system_l4" scale="3" />
  <ItemImage id="ae2federation:eco_computation_system_l6" scale="3" />
  <ItemImage id="ae2federation:eco_computation_system_l9" scale="3" />
  <ItemImage id="ae2federation:eco_computation_interface" scale="3" />
</Row>

The survival computing device. It is Neo ECO's
[computation subsystem](neoecoae:neoecoae_intro/computation_system.md) with an ECO - N4, N6 or N9 Federation
Computation Subsystem Controller in place of Neo ECO's controller, and it runs Federation programs instead of crafting
jobs. It needs Neo ECO 21.2.1 or later.

* **Building:** as Neo ECO's structure, with two differences: our controller, and Computation Casing where Neo ECO
  puts its ME interface. A network switch module is refused. The controller's tier must be at least that of the
  cores. The controller's Structure Builder builds it with one communication interface.
* **Linking:** the controller takes no cable. Up to four
  <ItemLink id="ae2federation:eco_computation_interface" />s go anywhere in the side face, the 2x3 end where Neo ECO
  puts its ME interface. Once the structure has formed, each takes one <ItemLink id="ae2federation:cable" /> on its
  outer face, which joins the system to the domain. A 2x2 bundled cable links only to four interfaces that fill a 2x2
  square. A fifth interface, or one in another face, keeps the structure from forming. No ME network joins the
  structure, and it is not a crafting CPU.
* **Compute:** the accelerators of its parallel cores, 64, 192 or 576 CU per core for tier 4, 6 or 9.
* **Bandwidth:** the bytes of the flash crystal matrices in its drives, 67,108,864 (CE4), 268,435,456 (CE6) or
  1,073,741,824 (CE9) B/s per matrix. Right-click the controller with matrices to fill empty drives.
* **Installations:** as many as the threads of its threading cores.
* **ID and alias:** right-click the controller with an empty hand to see or copy its ID, such as `device-k3m9qa`, or
  to give it an alias.

How the cost of an installation is counted: [Compute and Bandwidth](../automation/compute.md).

Each controller is made in Neo ECO's Integrated Working Station, from Neo ECO's controller recipe with
<ItemLink id="ae2federation:nexus_processor" />s in place of its Superconducting Processors. The communication
interface is Neo ECO's computation interface recipe with an <ItemLink id="ae2federation:router" /> in place of the ME
Interface and Nexus Processors in place of the Calculation Processors.
