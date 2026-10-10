# Federation Switch

Status: implemented on 2026-10-10 (`21f7a15`, `22b3dc4`, `813ab98`).

The ME Federation Switch (`ae2federation:switch`, ME联邦交换机) attaches ME networks to a Federation domain; the
ME Federation Router (`ae2federation:router`) now links Federation ports only. The former idea is preserved as an
[archived discussion](../archive/ideas/federation-switch.md).

## Behavior

- **Switch faces:** each of the six faces works on its own, as the Router's did before the split. A face touched by
  an ME cable or device joins that network without a channel or idle power; a face touched by Federation Cable, a
  Router, another Switch, a Pattern Provider front or an Endpoint front links the domain onwards. Up to six networks
  meet at one Switch and are never merged; two faces on one network count once. Two Switches face to face link
  directly.
- **Router faces:** link to Federation Cable, a Switch, another Router, a Pattern Provider front or an Endpoint front.
  An ME cable or device touching a Router face stays unconnected: the Router has no ME node on any face.
- **Domain screen:** right-clicking a Switch or a Router opens its domain's screen, as with a Bridge.
- **Unchanged paths:** a Federation Pattern Provider still brings its own network in, and a Bridge still forms its
  two-network domain.
- **Recipes:** the Switch is four Federation Cables in the corners, Quartz Fibers above and below, a Storage Bus and an
  ME Interface on the sides and a Nexus Core in the centre, one Switch: the materials name what a Switch face
  exchanges (storage, crafting and processing, energy without channels), as the Bridge's do. The Router is four
  Federation Cables in the corners, four Fluix Crystals on the sides and a Nexus Core in the centre, one Router.
- **Appearance:** none yet, on purpose; the Switch shows the missing-texture cube until the artist draws it. It joins
  Federation Cable densely, as the Router does.
- **Saves:** pre-alpha; Routers in older saves are not converted, and the pre-rename ids no longer load.

## Evidence

- GameTests `routerrefusesmenetworks` and `switchesjointhroughrouter`; scenes with an ME network on a device face use
  the Switch. Implementation notes: [knowledge note](../../.omo/knowledges/federation-switch-2026-10-10.md).
- Guide: the Switch and Router item pages, and the examples, which build with Switches.
