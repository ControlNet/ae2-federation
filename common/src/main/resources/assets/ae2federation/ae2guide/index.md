---
navigation:
  title: AE2 Federation
  icon: ae2federation:router
  position: 900
---

# AE2 Federation

<Row>
  <ItemImage id="ae2federation:router" scale="3" />
  <ItemImage id="ae2federation:bridge" scale="3" />
  <ItemImage id="ae2federation:pattern_provider" scale="3" />
  <ItemImage id="ae2federation:processing_endpoint" scale="3" />
</Row>

AE2 Federation connects separate ME networks so they can share storage, autocrafting, processing machines and ME
power, while each network keeps its own channels, controllers and crafting CPUs. Nothing is shared until you switch it
on, and each permission works in one direction.

* [Getting Started](getting-started.md): craft the parts and connect your first two networks.
* [How Federation Works](mechanics.md): domains, rules, shared energy and the limits.
* [Remote Crafting](remote-processing.md): order from another network's pattern providers, or send processing
  patterns to its machines.
* [Examples](examples/index.md): complete builds for a shared warehouse, an assembly workshop and outsourced machines.
* [Troubleshooting](troubleshooting.md): what the warnings in the Federation screen mean.

## Items and blocks

* <ItemLink id="ae2federation:nexus_core" />: the ingredient every device needs, crafted from a
  <ItemLink id="ae2federation:nexus_processor" />.
* <ItemLink id="ae2federation:bridge" />: joins two adjacent networks directly.
* <ItemLink id="ae2federation:switch" />: puts up to six networks into a Federation domain.
* <ItemLink id="ae2federation:router" />: joins and branches Federation Cable; it attaches no network.
* <ItemLink id="ae2federation:cable" />: links Switches, Routers, Pattern Providers and Endpoints over a distance.
* <ItemLink id="ae2federation:pattern_provider" /> and <ItemLink id="ae2federation:processing_endpoint" />: run
  processing patterns on machines that belong to another network.
