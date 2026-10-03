# World highlights: several at once, blinking (2026-10-03)

The user asked for three GUI changes. Hiding live flow must also stop the quartz beads on shared-energy links. Two
"Highlight 10 s" presses must outline both targets. The world outline must blink, so it is easier to spot.

## Shape now

- `client/policy/HighlightSet<T>` (MC-free, `HighlightSetTest`) keeps the running highlights in order, oldest first,
  each with its own 10 s timer. Adding the same key again restarts that entry instead of adding a copy. Past capacity
  (16 in `WorldHighlight`) the oldest entry ends. `Entry.lit(now)` blinks from the entry's own start: lit 500 ms, then
  dark for the rest of an 800 ms period. It starts lit, so an outline shows the moment it is asked for.
- `WorldHighlight.show(...)` adds a highlight keyed by `(dimension, groups)`. An empty list is now a no-op. Use
  `WorldHighlight.clear()` to end everything (tests, teardown). `activeBlocks()` / `activeGroups()` sum over all running
  highlights.
- `FederationTopologyView` keeps `highlightedUntil` per network / Endpoint id, so the "Outlining N blocks" note shows for
  every highlighted card, not only the last one.
- `Links.energyLine` still draws the quartz rail when live flow is hidden but skips the beads. The beads are live flow;
  the rail only says that the pool is shared. `FederationFlowPulses.drawnBeads()` reports the last frame's bead count for
  tests. `FederationTopologyView` is package-private, so the counter lives on the public pulses element.

## Test pitfalls

- A blinking outline breaks a pixel check that grabs one frame at a fixed time. Under Fabulous graphics the screenshot
  step took long enough that the later `FrameCapture.grab()` landed in the dark phase (0 pixels). The occluded check now
  uses `waitUntil` for a lit frame (> 400 magenta pixels), then `waitUntil` for a dark frame (< 20) while the highlight is
  still running. That second check also proves the blink.
- Highlights from earlier scenarios in the same client can still be running. Before asserting an exact count, call
  `WorldHighlight.clear()`. `ui.endpoint` seeds one unrelated highlight and checks that the Endpoint's outline joins it
  (2 groups / 2 blocks), and that pressing again keeps 2.
