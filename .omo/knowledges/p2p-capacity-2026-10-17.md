# Federation P2P carries one cable's capacity (2026-10-17)

- `CableCapacityCounter.count` now gives P2P tunnel nodes the same demand as the cables of their region; their
  evidence keeps capacity factor 1, so `FederationDomainRegistry.judgeCapacity` limits them to C with no other change.
- Where it matters: a region holding cables was already limited to C by those cables. The change bites only where a
  region has tunnels but no ordinary cable, e.g. Router - tunnel pair - Router (a domain trunk made only of P2P) or a
  tunnel front directly on a Switch. A 1x1 tunnel front cannot join a bundle end (partial contact), so bundles do not
  pass through a pair.
- Jade: a tunnel is an AE2 part, so the line goes through `appeng.api.integrations.igtooltip.PartTooltips`
  (`addServerData` + `addBody`) from `FederationP2PRegistration.register`; see `compat/jade/P2PCapacityTooltip`.
  `CableCapacity.demand(ServerLevel, FederationDomainNodeId)` serves it.
- Capture tip: AE2 energy cells placed next to each other merge into one ME network; to get N distinct networks on a
  Switch, put one cell on each face of a 1x1 Switch (the six neighbours never touch each other).
- In-game check: `build/p2p-capacity-check/` (10 networks through one pair: before running, after "over capacity,
  stopped", tunnel Jade 10/8).
