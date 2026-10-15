# Switch connected-face tiles from the artist (2026-10-11)

After the Router tiles ([router-artist-connected-tiles-2026-10-11.md](router-artist-connected-tiles-2026-10-11.md)),
the artist delivered the same set for the Switch: a 3x2 concept image and `switch_base`, `switch_corner`,
`switch_side_hor`, `switch_side_ver`. The `switch.png` in the delivery is byte-identical to the existing one.
Art commits credit the artist with a Co-authored-by trailer.

## Findings

- Same structure as the Router set, but the frame is three texels wide: the side tiles are opaque for three
  rows/columns and the corner square is 3x3. `face_tiles.band()` reads the width from the side tiles, so both
  devices share one composer (Python `face_tiles.py`, Java `DeviceStructureGeometry`).
- A 1x1 face composed from the tiles equals `switch.png`; a 3x2 face matches the concept in all 1536 texels.
- Standalone Switch relief per texel: the same two greys (120,126,151), (135,143,165) at depth 1, everything
  else at 0, except eight notch texels beside the edge contacts at depth 2 on the face's outer edge. The tile
  rule (grey recessed 1, rest flush) therefore matches except those notches, which stay flush on the frame.
- The earlier element-built Switch (white backing, sockets from elements 15/16/17, channel walls, 0.25/0.18/0.12
  layering) is superseded; it remains in the Python prototype only. Its Java port was removed.
- Ray scan of all 63 non-full connection sets: no see-through rays for either device.

## In-game comparison

`build/multiblock-devices/switch-tiles-compare/` in the worktree: `switch-before-after.png` (ten views) and
`concept-before-after-face-on.png`. The "before" run already uses the Router tiles.
