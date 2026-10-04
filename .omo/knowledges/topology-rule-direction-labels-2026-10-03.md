# Topology rule-direction labels (design, 2026-10-03)

Goal: read which network uses which on the topology graph without matching network names. Only the labels in the
middle of a link change; links, ports and curves stay as they are.

## Decided

- **One label per direction (K-parallel, 35 degrees).** Each direction with rules gets its own label, on the left of
  its travel direction (provider to consumer), so a pair's two directions sit on opposite sides of the link. A solid
  arrow cap on the label's leading end points at the network that gains the capability. No network names in labels.
- **Angle from the curve's tangent.** Topology links are `WireCurve` S-curves; at t = 0.5 the tangent is twice as
  steep as the chord. Within 35 degrees of level the label turns to run along the tangent (flipped when the link runs
  leftward, so text is never upside down, cap on the left end); steeper, it stays level with the cap up or down.
  Measured on the UI-test showcase layout: about 4 of 6 labels run along the link, 2 stay level.
- **Rejected:** pure-tangent labels with icons (unreadable at the fitted zoom, and still busy with fixed-size labels),
  angle from the chord (misaligned with the curve), straight topology links (out of scope), a third centred energy
  label beside two direction labels (redundant).
- **Energy.** Shared energy is energy both ways: when a pair has other rules, each direction's label ends with an
  "Energy" chip, with no "shared" wording and no third label. A pair with only energy keeps one centred, level
  "Energy" chip without a cap.
- **Colours.** Chip colour is state: green active, yellow on but not active yet, red blocked. Energy chips are quartz
  (#cfd9e8) while the pool is shared, matching the quartz rail. An active rule that re-exports takes its own colour
  with no added word: cyan #4fd8e8 (chosen over violet #b48cff and blue #55a7ff). It avoids green (active),
  light blue #9cd3ff (selection and AE2's plain "on" switch) and the re-export switch's green #6cd680. Network
  accents stay as they are.

## Implementation (done)

- `WireCurve.tangent(t)` and the pure `client/policy/DirectionLabelPose` (rotation, cap side, size, left-of-travel
  offset; `MAX_TILT = 35`) carry the geometry, with JUnit tests. `FederationTopologyView.linkLabels` builds one Button
  per direction (`graph-pair`, `related-pair` kept), its face painted by `labelFace`: well, solid or dashed border,
  padlock, and a cap stepped in whole pixels.
- LDLib2 2.2.34 `UIElement.transform(t -> t.rotation(deg))` turns an element about its centre (`Pivot.CENTER`
  default). Verified in the T33 screenshots: rotated labels draw correctly under GraphView's zoom, and clicking a
  rotated label's centre opens its pair (checked once with the limit raised to 80 degrees, not committed). Rotated
  pixel text aliases a little; within 35 degrees it stays readable.
- `TopologySpacing.Label` still takes a box centred on the link point: `labelExtent` feeds it the union of both
  direction labels' rotated boxes, estimated from the tangent at the raw layout. Flow dots only hide under the
  energy-only chip, which is the one label sitting on the line.
- An LDLib2 label with `adaptive-width` never wraps and takes the width of its first line (`TextElement.recompute`),
  so the legend puts the five swatches first and the arrow note after a `\n`.
- Level labels stack their chips (2026-10-04): one under another, all as wide as the widest, words centred, the cap
  centred on the top or bottom end. `DirectionLabelPose.of` takes both the row and the column size and says which it
  used (`stacked`).
- The graph rebuilds only when `FederationTopologyView.refresh`'s structure signature changes. It must hold each rule's
  `mode` (off, on, re-export), not only enabled and state: switching on to re-export changes neither, so the chip kept
  its old colour until something else changed. Re-export chips carry an undrawn `reexport` class that the
  graph-controls scenario reads, as the fixture's storage rule may not be active (cyan only shows when it is).
- The showcase layout's network order varies between runs, so whether any of its labels runs along a link varies too.

Design boards: the "AE2 Federation GUI" design canvas, rows from "拓扑图规则方向" down to "颜色编码".
