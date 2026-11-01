# Programs and P2P tunnels (2026-11-01)

Functions: `ae2.tunnels(network=None) -> list[Tunnel]`, `ae2.tunnel_at(block) -> Optional[Tunnel]`,
`ae2.link_tunnel(input, output) -> bool` (act), `ae2.unlink_tunnel(tunnel) -> bool` (act). Code:
`automation/TunnelCalls.java`, accessor `mixin/P2PTunnelPartAccess` (invoker for AE2's package-private
`P2PTunnelPart.setOutput`; AE2 has no public way to make a tunnel an input except a player's memory card).

- `Tunnel` record: id ("dim x y z side"), type (part item), name, frequency (unsigned, 0 = not linked), output, linked
  (ids at the other end, from `P2PService`), active, network, x, y, z, dimension, side.
- Finding: per grid, machine classes assignable to `P2PTunnelPart`, keeping a node only when it is the tunnel's main
  node (an ME tunnel's outer node sits on the carried network).
- Link = memory card: input gets `setOutput(false)` + `P2PService.newFrequency()` when it has frequency 0 or is an
  output; an output of another part item is replaced (`IPartHost.replacePart(kind, side, null, null)`) as the card
  does; then `setOutput(true)` + `updateFreq`. A replaced part may have no grid yet; it keeps the frequency and AE2's
  `addNode` registers it (same fallback as `importSettings`).
- Unlink = as placed: input, frequency 0 (AE2 keeps every fresh tunnel in `inputs[0]`). An unlinked input orphans its
  outputs.
- Only same-grid links (ValueError otherwise), as AE2 keeps frequencies per grid.
- Federation P2P tunnels are listed only when their carrier network is a domain member (the carrier joins a domain
  through its own Switch); relinking them changes the domains they carry, and can stop one over capacity.

Tests: `programlinksthep2ptunnelsofitsdomain` (item/item/FE on one bus; FE becomes item; unlink orphans the output),
`programlinkingfederationtunnelscanoverloadadomain` (FederationP2PScene + carrier Switch and interface, capacity 1).
Mutation check: making `link_tunnel` a no-op fails both. UI: `ui.automation-tunnels` shows the tunnels before and after
(FE front turns into the item front; AE2's frequency colour cubes sit at depth 2-3 px and do not show at that
distance). The `run` helper in `ProgramHostGameTests` has a `settle` overload that waits after the result check;
a failed result check skips `after` so its message stays the one reported.
