# Multiblock router and switch design reading

Reviewed the project background and current multiblock proposal for a discussion-only request.
No implementation or runtime verification was performed; no new product decisions were accepted.

## Authoritative reading boundaries

- `docs/ideas/multiblock-routing-and-cable-capacity.md` records the current proposal:
  solid-cuboid routers and switches, bundled cables with bends, attachment-based capacity,
  and a candidate counting boundary based on distinct adjacent routers.
- `docs/features/federation-switch.md` records the implemented role split: switches attach
  ordinary ME networks; routers only interconnect Federation ports. Router capacity boundaries
  and multiblocks are future work. Providers retain their own network attachment path.
- Historical free-form geometry statements in the earlier knowledge note are superseded.
- `DESIGN.md` still contains earlier same-dimension/wired-only scope statements; the implemented
  `docs/features/federation-p2p.md` documents subsequent cross-dimensional connectivity.
  Do not interpret old prototype scope as the current feature inventory.

## Discussion assessment, not accepted requirements

- The proposal adds physical construction and spatial planning to independent-network cooperation.
  Connection capacity, operating power, and actual resource throughput remain separate concepts.
- Router-bounded counting would give routers a distinct role beyond cable branching. Switch
  traversal remains unresolved; transparent traversal is a possible recommendation, not a decision.
- Adding a second router to a cable region changes demand from local to domain-wide even without
  adding attachments. This needs player-visible explanation and an explicit overload scope.
- Solid geometry can change logical identity: adjacent router bodies that merge versus remain
  separate can produce different counts. Structure separation is therefore a gameplay rule.
- A 3x3 unlimited tier intentionally ends capacity progression. Whether smaller tiers remain
  attractive depends on construction cost, space, bends, and optional operating costs.
- Concentrated stars, Bridge re-export chains, and elongated solid routers remain accepted
  alternatives. Do not introduce router caps or mandatory thick trunks to prohibit them.
- Future capacity rules must explicitly integrate existing P2P topology and independently
  configurable power/capacity enforcement. Neither their rules nor overload behavior are settled.

Recorded only this reading note; existing idea documents and unrelated work were left intact.
