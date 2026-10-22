---
navigation:
  parent: index.md
  title: Federation Automation
  icon: ae2federation:automation_interface
  position: 550
---

# Federation Automation

<Row>
  <ItemImage id="ae2federation:automation_interface" scale="3" />
  <ItemImage id="ae2federation:creative_computing_device" scale="3" />
</Row>

Federation Automation runs small programs for a Federation domain. A program reads the domain's storage, crafting,
rules and the world around it, and acts on them: it starts crafting jobs, moves resources between networks and
blocks, changes rules and gives off redstone.

<FederationProgram src="../assets/programs/low_iron_alarm.ae2f" />

The block above is a program as the game shows it. **Script** shows its Python text, **Graph** the same function as
cards and wires. Both are one program: an edit in either view changes the other.

It takes three parts:

* **Programs** are Python files, written in the **Programs** tab of the Federation screen.
* A **computing device** in the domain runs the functions installed on it, in the **Automation** tab.
* An <ItemLink id="ae2federation:automation_interface" /> is where a program reaches the world: the block in front of
  it, and the redstone it gives off.

## Pages

* [Writing Programs](writing-programs.md): the Programs tab, the script and graph views, and Push.
* [The Language](language.md): the part of Python that programs use.
* [Library](library.md): the `ae2`, `ae2f` and `mc` functions.
* [Installing and Triggers](installing.md): putting a function on a device, and what starts it.
* [Compute and Bandwidth](compute.md): what an installation costs a survival computing device.
* [Example Programs](examples.md): programs to copy.
* <ItemLink id="ae2federation:automation_interface" /> and <ItemLink id="ae2federation:creative_computing_device" />.
