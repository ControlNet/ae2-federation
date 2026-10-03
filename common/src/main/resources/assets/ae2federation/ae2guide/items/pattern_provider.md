---
navigation:
  parent: index.md
  title: ME Federation Pattern Provider
  icon: ae2federation:pattern_provider
  position: 140
categories:
- devices
item_ids:
- ae2federation:pattern_provider
---

# ME Federation Pattern Provider

<ItemImage id="ae2federation:pattern_provider" scale="4" />

Sends processing patterns to machines on other networks, through
<ItemLink id="ae2federation:processing_endpoint" />s. See [Remote Processing](../remote-processing.md).

* **Front:** the Federation face. When you place the Provider, the front faces the block you clicked, so click
  <ItemLink id="ae2federation:cable" /> or a Router. A wrench turns it; sneak and use a wrench to pick it up.
* **Other five faces:** join the Provider's own ME network, where it uses one channel.

Right-click it to insert up to nine encoded patterns and map each to Endpoints. It also has blocking mode, crafting
lock, priority and visibility in the Pattern Access Terminal, like AE2's <ItemLink id="ae2:pattern_provider" />.
Breaking it drops its patterns and anything waiting to be sent or returned.

<RecipeFor id="ae2federation:pattern_provider" />

The native pattern provider used in the recipe must be the block, and its patterns are not carried over.
