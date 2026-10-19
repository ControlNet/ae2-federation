# 1x1 cable beam: R / LITE (2026-10-18)

The owner picked R / LITE from the Blender concepts (`tools/blender/cable_beams.py`, `render_cable_beams.py`,
commit 1cdc4b08) for ordinary 1x1 cables; the 2x2 bundle's R cost (~3100 vertices per block up close) was accepted
as is.

## How it is drawn

- `BundleBeamMesh.emitCell(mask, ...)` reuses the bundle's R loop with a profile: extent (1, 1, 1), every radius
  scaled by `(3/16) / (.44 * 1.1)` so the halo, breathing by up to a tenth at an alarm, meets the 1x1 glass window,
  and sheaths {0, 2} (the 4-lobe middle one is dropped) of 8 sides; distant cells draw no sheaths.
- Do not reuse the Beam Former trick of scaling a `BundleBeam` by the pose: it only works for a straight through
  beam. Ordinary cells bend, branch and stub, so geometry must stay at the block's faces and only the radii shrink.
- `CableBeamFrame.cell` caches it per (mask, parity, detail, pass, style) with normals; `CableFlowRenderer.emitCell`
  and `emitBundle` share `emitPlasma`, which applies the view-dependent soft fade. `draw()` (testmod native preview)
  goes through `emitCell` too. 1x1 Beam Former pairs call `emitCell`, so they changed with the cables.
- Measured near cost of a straight cell: 1056 glow + 32 core vertices (M was capped at 1700).

## Pitfall found by the all-mask test

`CableBeamMesh.path` took each sample's tangent from its neighbours. At distant detail an elbow has only 2 segments,
so the end tangent tilted ~7 degrees and the halo's end ring poked ~.015 block past the face (and left a seam with the
straight neighbour). The ends now leave exactly along their own arm.

## Evidence

`build/cable-r-lite-check/`: `before-after.png` (7 views, day and night, 1x1 Former included),
`before-after-loop.gif` (eye level), `before/`, `after/`, both jars.

## M removed

After the owner approved the look, M's emission (carrier, shoulders, fog, merging), `CableBeamFrame.layer`, the
M-only `CableBeamStyle` radii and `phase`, and M's tests were deleted. `CableBeamMesh` now holds only the centerlines
(`build`, `Path` without the old `fixed` flags) and `detail`. `CableFlowGeometry`, the older ribbon model, moved out
of the mod to `tools/blender/java/` (owner's choice): the Blender export and the old beam studies built on its ribbons
still run; its Java unit test went, `tools/blender/test_assets.py` still checks the exported ribbons.
