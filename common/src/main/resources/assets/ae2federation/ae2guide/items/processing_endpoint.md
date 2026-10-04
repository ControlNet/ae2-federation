---
navigation:
  parent: index.md
  title: ME Federation Processing Endpoint
  icon: ae2federation:processing_endpoint
  position: 150
categories:
- devices
item_ids:
- ae2federation:processing_endpoint
---

# ME Federation Processing Endpoint

<BlockImage id="ae2federation:processing_endpoint" p:facing="south" scale="6" />

Lets a <ItemLink id="ae2federation:pattern_provider" /> on another network use your machines. See
[Remote Crafting](../remote-processing.md).

* **Front:** the Federation face. It faces the block you clicked when placing it, so click
  <ItemLink id="ae2federation:cable" /> or a Router. A wrench turns it.
* **Other five faces:** join the machines' own small ME network (a processing subnet). Inputs from the Provider
  go into that network's storage; machines must push their results back into one of these faces.

An Endpoint belongs to one Provider at a time. With an ordinary AE2 pattern provider block against its front, it
works in local mode for that provider instead.

While a Provider uses it, the Endpoint shares that Provider's network's ME power with the subnet, as a Quartz Fiber
does, so the subnet needs no power of its own. The ME power switch in the Endpoint's panel turns this off.

Right-click it to open the Federation screen for the domain its front joins.

<RecipeFor id="ae2federation:processing_endpoint" />

The ME Interface used in the recipe must be the block.
