# Federation Cable as AE2's dense cable (2026-10-09)

The Federation Cable's model is built in code from AE2's dense cable geometry. User decisions: dense size (as AE2
Lightning Tech Reborn's overloaded cable), keep the BER flow, keep the old V07 textures as TEMPORARY until the artist
delivers dense cable textures, and keep `glass` on the shell even though it is almost invisible (alpha avg 5/255).

## Where things are

- `router/CableVisualConnections`: two bits per side, E/W/U/D/S/N. `DENSE` (cable, Router), `COVERED_CAP` (Provider or
  Endpoint front), `COVERED` (Federation P2P tunnel front). `mask()` is derived and still feeds the BER and GameTests.
- `router/CableShapes`: outline boxes per connection set, cached lazily (4096 sets).
- `client/FederationCableBuilder`: AE2 `CableBuilder` / AE2LT helper logic (LGPL notice kept), drawing with AE2's
  `appeng.client.render.cablebus.CubeBuilder` (public class, not API package; AE2LT does the same).
- `client/CableBakedModel`: wraps the placeholder `block/cable` and `item/cable` models; quads per connection set in a
  `ConcurrentHashMap` (chunk meshing is off-thread), reset on reload because the model instance is new.

## Mapping from AE2

AE2's `CableBusContainer.getRenderState` uses `AECableType.min(cable, neighbour.getCableConnectionType())`. Controller
is DENSE_SMART, Pattern Provider and Interface (InterfaceLogic) are SMART, parts default to GLASS and a neighbouring
cable bus sets `cableBusAdjacent` (no big cap). A dense cable with a SMART/COVERED/GLASS connection draws a covered arm.

## Pitfalls found

- NeoForge `BakedModelWrapper.applyTransform` returns the ORIGINAL model; the item renderer then draws the empty
  placeholder. Override it to apply the transform and return `this`.
- Item path calls the 3-argument `getQuads(null, side, random)`; override it too.
- A translucent shell shows faces AE2's opaque shell hides: arms start at the core surface (13, not 11), have no inner
  end; a straight tube has no end caps and no ±0.01 overhang (that is for facades).
- `tools/blockbench/create_projects.py` already failed before this change (`KeyError: 'missing'` on the hand-made
  device models); the cable entries were removed from it.

## Render check

Side-by-side scene (Federation row z=0, AE2 dense row z=6) on a fresh flat server copied from the round-82 production
server, client `build/ae2f-work/prod-client/start-client.sh <tag>`. Screenshots in `build/dense-visual-shots/`.
