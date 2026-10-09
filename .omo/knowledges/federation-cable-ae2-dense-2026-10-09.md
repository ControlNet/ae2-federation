# Federation Cable as AE2's dense cable (2026-10-09)

The Federation Cable's model is built in code from AE2's dense cable geometry. User decisions: dense size (as AE2
Lightning Tech Reborn's overloaded cable), keep the BER flow. The artist's PR #3 (merged ba0e8b5) brought interim
dense textures (`textures/part/cable/dense/`), resized the cable to a 12-voxel core / 10-voxel dense arms, and redrew
`entity/cable_flow.png` and the P2P panel; the artist is the authority for all three, and the old generators
(`cable_flow_texture.py`, `p2p_panel_texture.py`) and V07 texture copying are retired. At the artist's suggestion a
Provider or Endpoint front now joins the cable densely, as a Router does (no AE2 covered arm with a cap), so a
Router-cable-Provider line is one straight tube and `dense/connector` (the old cap) ships undrawn.

## Where things are

- `router/CableVisualConnections`: two bits per side, E/W/U/D/S/N. `DENSE` (cable, Router, Provider or Endpoint
  front), `COVERED` (Federation P2P tunnel front, a 4-voxel arm). `mask()` is derived and still feeds the BER and GameTests.
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
- A translucent shell shows faces AE2's opaque shell hides: arms start at the core surface (14), and a straight tube has no end caps and no ±0.01 overhang (that is for facades).
- `tools/blockbench/create_projects.py` already failed before this change (`KeyError: 'missing'` on the hand-made
  device models); the cable entries were removed from it.

## Render check

Side-by-side scene (Federation row z=0, AE2 dense row z=6) on a fresh flat server copied from the round-82 production
server, client `build/ae2f-work/prod-client/start-client.sh <tag>`. Screenshots in `build/dense-visual-shots/`.
