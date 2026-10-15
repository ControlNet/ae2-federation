# Router connected-face tiles from the artist (2026-10-11)

The artist proposed a refined multiblock Router face: a concept image of a 3x2 face (not a UV map;
grey areas are recessed) plus four 16x16 tiles: `router_base`, `router_corner`, `router_side_hor`,
`router_side_ver`. They are now in `textures/block/router/` unchanged and drive the formed Router.

## What the tiles are

- A connected-texture set. Compose each member's face as: `router_base`; then the top/bottom 4 rows
  of `router_side_hor` where that face edge is the body's boundary; then the left/right 4 columns of
  `router_side_ver`; then the 4x4 corner square of `router_corner` where both edges are boundaries.
  The side tiles are transparent in the middle, so they are overlays.
- A 3x2 face composed this way matches the concept image (sampled at 12 px per texel) in 1531 of
  1536 texels; the 5 differences are at the image's cropped border.
- A 1x1 face composed this way equals the current `textures/block/router/router.png` exactly, and
  `router_corner` is `router.png` with the 8x8 core blanked. The set is therefore a decomposition of the
  existing art, adding lattice lines between members and small 6x6 cores where four members meet.
- `router_side_hor` and `router_side_ver` are not exact rotations of each other (off-white noise
  differs), so each face needs a fixed convention for which texture edge is "horizontal". Minecraft's
  face UVs, which the current model uses, give one (north: u = 16 - x, v = 16 - y; up: u = x, v = z).

## Relief

Grey texels are the two backing colours (135,143,165) and (120,126,151). In the current single-block
model every grey texel of a face lies at depth 1/16 and every other texel (frame, blue core, tabs) at the
surface. So "grey recessed by 1/16, everything else flush" reproduces the existing single block exactly,
and keeps the accepted rule that blue links stand 1/16 above the backing, level with the core.

Comparison image: `build/multiblock-devices/router-artist-tiles/concept-vs-composed-3x2.png` in the
main checkout (concept on the left, composition on the right).

## Implementation

- Prototype `tools/multiblock/face_tiles.py` (`router_style='tiles'`), Java `DeviceStructureGeometry`. Both
  started Router-only (`router_tiles.py`, `RouterTileGeometry`) and were generalized for the Switch tiles; see
  `switch-artist-connected-tiles-2026-10-11.md`.
  Faces merge texels of one tile and depth into rectangles (greedy, row-major); walls are emitted by the
  raised texel toward each recessed neighbour, merged along a row or column within one tile, UV folded
  over the edge. Seams look at the neighbour member's composed texel, computable from the member's own
  mask because the neighbour shares the two perpendicular boundary flags.
- Quads carry their texture (`block/router/router_base`, ...); the baked model resolves each to a sprite.
  All four tiles are under `textures/block/`, so the block atlas stitches them without a model reference.
- The baked model reads the tiles with `NativeImage`; `getPixelRGBA` returns ABGR, converted with
  `FastColor`. The JUnit parity test reads them with `ImageIO.getRGB` (ARGB). A recessed texel on a face's
  outer edge throws, and the model falls back to the standalone one.
- Parity lines now include the texture; Java matched Python for all 128 sets on the first run.
- Quads per member: 372 for a lone block (the runtime keeps the standalone model there), 45 to 262 for the other 62 connection sets.

## In-game comparison

Same scene before and after, with two added face-on views of the 3x2 north faces:
`build/multiblock-devices/router-tiles-compare/` in the worktree (`router-before-after.png`,
`concept-before-after-face-on.png`). Face on, the formed Router matches the concept; it is darker only from
Minecraft's north-face shading. At grazing angles the 1/16 walls hide much of the narrow grey recesses.
