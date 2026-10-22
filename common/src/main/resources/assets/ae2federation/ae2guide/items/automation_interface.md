---
navigation:
  parent: items/index.md
  title: ME Federation Automation Interface
  icon: ae2federation:automation_interface
  position: 460
categories:
- devices
item_ids:
- ae2federation:automation_interface
---

# ME Federation Automation Interface

<BlockImage id="ae2federation:automation_interface" p:facing="south" scale="6" />

Where Federation programs reach into the world: the blocks in front of it, and the redstone it gives off.

* **Front:** faces the block you clicked when placing it, so click the chest, machine or crop it should work on. A
  wrench turns it.
* **Other five faces:** take <ItemLink id="ae2federation:cable" /> only. The interface joins the domain through them
  and counts 1 toward the cable's [capacity](../mechanics/capacity.md), like an Endpoint. It uses 2 FE per tick
  ([Power](../mechanics/power.md)).

Each interface has an ID, such as `interface-k3m9qa`, that stays with it until it is broken. Right-click it to see
the ID, copy it, or give the interface an alias. An alias must be unique in its domain and must not look like an ID.

<RecipeFor id="ae2federation:automation_interface" />

The ME Interface used in the recipe must be the block.
