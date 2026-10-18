---
navigation:
  parent: index.md
  title: Creative ME Federation Computing Device
  icon: ae2federation:creative_computing_device
  position: 156
categories:
- devices
item_ids:
- ae2federation:creative_computing_device
---

# Creative ME Federation Computing Device

<BlockImage id="ae2federation:creative_computing_device" scale="6" />

Runs Federation programs for its domain, with unlimited compute. It is a stand-in for testing: the survival
computing device comes later.

* **All six faces:** take <ItemLink id="ae2federation:cable" /> only. The device joins the domain through them and
  counts 1 toward the cable's [capacity](../network-capacity.md), like an Endpoint. It uses no power.
* **Programs** installed on it act through the domain's
  <ItemLink id="ae2federation:automation_interface" />s. For now an operator installs them with the
  `/ae2federation programs` commands.
* **Redstone:** a signal arriving at the device starts the programs set to start on redstone.

It is only available in creative mode.
