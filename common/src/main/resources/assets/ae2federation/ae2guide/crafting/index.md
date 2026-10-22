---
navigation:
  parent: index.md
  title: Crafting Across Networks
  icon: ae2federation:processing_endpoint
  position: 30
---

# Crafting Across Networks

Another network's machines can carry out your crafting jobs in two ways.

| | [Another network's pattern providers](ae2-providers.md) | [Processing Endpoints](processing-endpoints.md) |
|---|---|---|
| Patterns live in | The other network's AE2 pattern providers | Your own <ItemLink id="ae2federation:pattern_provider" /> |
| Needs a rule | Crafting, "you use theirs" | None |
| The other side | A complete ME network, set up the usual AE2 way | A small subnet beside the machines, powered through the <ItemLink id="ae2federation:processing_endpoint" /> |
| Works for | Crafting and processing patterns | Processing patterns |
| Crafting CPU | Yours runs the job; the other network needs none | Yours runs the job |

* **Use another network's pattern providers** when that network already crafts for itself and you want to order from
  it too. It keeps its patterns and its machines; your terminals list its patterns among your own.
* **Use Processing Endpoints** when the machines are yours to feed but stand on a network of their own: an outpost,
  a partner's workshop, or a line of furnaces you keep apart. The patterns stay in your Provider, and you choose
  which Endpoint runs each one.

<SubPages icons={true} />
