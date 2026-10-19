# Federation Cable as AE2's dense cable (2026-10-09)

The Federation Cable's model is built in code from AE2's dense cable geometry. User decisions: dense size (as AE2
Lightning Tech Reborn's overloaded cable), keep the BER flow. The artist's PR #3 (merged ba0e8b5) brought interim
dense textures (`textures/part/cable/dense/`), resized the cable to a 12-voxel core / 10-voxel dense arms, and redrew
`entity/cable_flow.png` and the P2P panel; the artist is the authority for all three, and the old generators
(`cable_flow_texture.py`, `p2p_panel_texture.py`) and V07 texture copying are retired. At the artist's suggestion a
Provider or Endpoint front now joins the cable densely, as a Router does (no AE2 covered arm with a cap), so a
Router-cable-Provider line was one straight tube and `dense/connector` (the old cap) ships undrawn.

Since 2026-10-10 (user decision) cables join each other at full width (`CABLE` kind, 12-voxel arm, core face toward
a cable left out); the 10-voxel dense "neck" appears only toward a Router or Provider/Endpoint front, so a cable
beside a machine is a core, not a tube. With no step between cables, tube end faces, the JOINS_FIRST/SECOND model
bits and the flowMask redraw are gone. Before/after shots: `build/neck-shots/`.

## Where things are

- `router/CableVisualConnections`: two bits per side, E/W/U/D/S/N. `CABLE` (another cable, 12-voxel arm), `DENSE`
  (Router, Provider or Endpoint front, 10-voxel arm), `COVERED` (Federation P2P tunnel front, a 4-voxel arm). `mask()` is derived and still feeds the BER and GameTests.
- `router/CableShapes`: outline boxes per connection set, cached lazily (4096 sets).
- `client/FederationCableBuilder`: AE2 `CableBuilder` / AE2LT helper logic (LGPL notice kept), drawing with AE2's
  `appeng.client.render.cablebus.CubeBuilder` (public class, not API package; AE2LT does the same).
- `CableShapes` boxes must equal the builder's cubes; JUnit `CableConnectionsTest` pins the outline sizes.
- `client/CableBakedModel`: one translucent layer; wraps the placeholder `block/cable` and `item/cable` models; quads per connection set in a
  `ConcurrentHashMap` (chunk meshing is off-thread), reset on reload because the model instance is new.

## Mapping from AE2

AE2's `CableBusContainer.getRenderState` uses `AECableType.min(cable, neighbour.getCableConnectionType())`. Controller
is DENSE_SMART, Pattern Provider and Interface (InterfaceLogic) are SMART, parts default to GLASS and a neighbouring
cable bus sets `cableBusAdjacent` (no big cap). A dense cable with a SMART/COVERED/GLASS connection draws a covered arm.

## Pitfalls found

- NeoForge `BakedModelWrapper.applyTransform` returns the ORIGINAL model; the item renderer then draws the empty
  placeholder. Override it to apply the transform and return `this`.
- Item path calls the 3-argument `getQuads(null, side, random)`; override it too.
- Until 2026-10-10 straight tube end faces followed the artist's `cable_display.bbmodel` (drawn toward a non-tube
  neighbour to cover the 12-to-10 step), which needed model bits read two blocks away plus a client redraw, since
  vanilla re-meshes only within one block of a change. Full-width cable joins made all of that unnecessary.
- A translucent shell shows faces AE2's opaque shell hides: arms start at the core surface (14), and a straight tube has no end caps and no ±0.01 overhang (that is for facades).
- `tools/blockbench/create_projects.py` already failed before this change (`KeyError: 'missing'` on the hand-made
  device models); the cable entries were removed from it.

## Render check

Side-by-side scene (Federation row z=0, AE2 dense row z=6) on a fresh flat server copied from the round-82 production
server, client `build/ae2f-work/prod-client/start-client.sh <tag>`. Screenshots in `build/dense-visual-shots/`.

A/B check of a new texture (2026-10-10, the artist's core revision): put it in a folder pack under
`prod-client/resourcepacks/<name>/` (pack_format 34), list `"file/<name>"` after `mod_resources` in the client's
`options.txt` `resourcePacks`, shoot, then copy the old texture into the pack, reload with
`game_input.py reload` (F3+T; wait for a new "Reloading ResourceManager" line) and shoot the same `tp` spots. Restore
`options.txt` afterwards. Shots and side-by-sides in `build/core-revision-shots/`.

## Connected core faces (merged to dev 2026-10-10)

Full-width cable joins were tried on `feature/cable-full-width-joins` (reverted on dev in ba4e96c while undecided)
and merged with the connected cores once the user approved them; the branch is gone. The artist's
`core_connected_1..4` plus `core_connected_2_straight` (two opposite edges open). Until 2026-10-10 that slot held my
stand-in `core_connected_opposite`, composed from `core_connected_1`; the artist then delivered
`core_connected_2_straight`, pixel-identical to the stand-in, and it replaced it under the artist's name. `CableCoreFaces` maps each face's texture-space edges (top, right, bottom, left) to world sides as
AE2 `CubeBuilder`'s standard UVs lay them (south: up, east, down, west; north: up, west, down, east; west: up, south,
down, north; east: up, north, down, south; up and down: north, east, south, west) and `setUvRotation(face, k)` turns
the texture k quarter turns clockwise; both confirmed in-game on the first render. Only `CABLE` opens an edge. New
`part/cable/dense/*` textures need no atlas entry: AE2's blocks atlas lists the whole `part/` directory for every
namespace. Shots: `build/neck-shots/`, `build/core-connected-shots/` (diagnostics in `connected-shapes.png`).

## Artist's collar (2026-10-11)

`cable_straight.bbmodel` and `cable_display.bbmodel` had a collar since the artist's 44d6610 (four 1-voxel
`collar.png` bars ringing the tube at 4..12 at both ends of a straight block), but it was never built: the artist's
interim builder wired only core, line, connector and streams, and 9aed66a filed `dense/collar` under
`validate_assets.py` NOT_DRAWN_YET instead of checking the bbmodel geometry. Lesson: when the artist's textures arrive,
diff every `.bbmodel` element against `FederationCableBuilder`, not just the textures the interim code wires.

`FederationCableBuilder.addCollar` copies the artist's bars, faces, UVs and rotations verbatim (north ring samples the
bar's inner column, south ring the outer, mirrored, so two halves at a seam show the texture's 2-pixel bar) and bakes
them with vanilla `FaceBakery` under `BlockModelRotation` X0_Y0 / X90_Y0 / X0_Y90, as a blockstate rotation would.
`collarEnd` picks the ring that lands on the requested side via `Direction.rotate(matrix, SOUTH)`
(`CableCollarTest`). Drawn on both ends of a straight tube, at the end of every `CABLE` arm of a core, and on the
item; never toward Router, machine or P2P.

In game: rings show through the glass windows at every seam. The collar is opaque, unlit terrain in the translucent
layer drawn after the beam, so it correctly hides the beam behind it but also cuts the glow line into segments; at night
it is dark and the night crop lost about 25% of its bright cyan pixels (1732 -> 1305). Light emission 0 is the
artist's choice; making it full-bright would be a design change. Shots: `build/beam-compare/collar-{before,after}/`,
sheet `cable-collar-before-after.png`, night crop `collar-night-crop.png`, bbmodel render `artist-collar.png`.
`validate_assets.py` currently fails on `block/router/router_side_ver` (unused texture from e2348d1), unrelated.
