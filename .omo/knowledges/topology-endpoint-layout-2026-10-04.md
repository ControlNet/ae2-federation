# Topology Endpoint node layout (2026-10-04)

`client/policy/EndpointNodeLayout` places Endpoint nodes around the card of the network that maps them; the
`FederationTopologyView.layout()` loop spreads the cards by 1.15 (at most 12 times) until `EndpointNodeLayout.clear`
reports no node covering a card or another node.

## Rules

- Each link leaves from its own anchor along the card side, assigned in the order of the nodes it reaches (sorted by
  y beside a card, by x above/below). Anchor step = `min(pitch, (side - 2*14)/(n-1))`, centred on the side.
- `GAP = 40` between card and first nodes, `SPACING = 8` between nodes (pitch 24 with 16-high nodes).
- Beside a card: one column of up to 5. More lengthens the first column to `max(5, ceil((n+1)/2))` so the second
  column (12 further out) can centre each node on a gap of the first. Above/below: rows of up to 3, same rule.
- No crossings: every link of one card bends within the same band `[card edge, card edge -/+ GAP]`
  (`WireCurve.bendEnd`), then runs straight. With anchors sorted like the targets, two bends in the same band with
  ordered starts and ends never cross, and second-column runs pass through gap centres of the first column.

## WireCurve

`WireCurve(fromX, fromY, toX, toY, vertical, bendEnd)`: a cubic bend from the start to `bendEnd` on the main axis,
then a straight run to the end. `at(t)` is split by length so flow dots and beads keep an even pace. Card-to-card
links use the 4-arg constructor (horizontal, no run) and must stay that way: `DirectionLabelPose` reads `tangent()`.
Move placed nodes with `Placed.moved`, which uses `WireCurve.translated` so the bend band moves with them.
