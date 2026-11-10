# 2x2 Beam Converter art, artist PR #10 (2026-11-10)

PR #10 ("添加2x2截面光束转换器模型与纹理") adds `tools/blockbench/projects/large_beam_converter.bbmodel` and a 64x64
`textures/block/beam_converter/large_beam_converter.png`. The project spans x, y 0..32 and z -16..32: a one-layer
converter body (z -16..-3, back face = orange FE rings on a magenta cross, sides magenta panels) with a 3 px front frame,
then a two-layer head (rings, pillars, eight diagonal spokes) toward +z, the front. So it is three blocks long.

## Owner decision: 2x2 converters are 2x2x3

The rule was "twice the wider side long" (2x2x4). Asked how to fill the fourth layer, the owner chose to shorten the 2x2
converter to 2x2x3 instead of lengthening or stretching the art. `CableCrossSection` now stores each section's
converter length: 1x1 = 2, 2x2 = 3, 3x3 = 6; the unbuilt 1x2/1x3/2x3 keep 4/6/6. A converter is still longer than
either side of its front, so a box has at most one fitting axis (`BeamConverterShapeTest.noBoxHasTwoAxes` checks every
box up to 8x8x8, built or not). Lang (`wrong_shape.beam_converter`), guide size table and docs follow.

## Export and wiring

- `tools/blockbench/export_beam_converter_models.py` writes `block/beam_converter_large_{qx}{qy}{layer}` (12 slices:
  quadrant across the front, layer 0 = rear .. 2 = front) with `export_elements`, which now also clips along z when the
  clip box has three components (the Beam Former export is byte-identical).
- Vanilla models only allow element angles of 0, ±22.5, ±45. The project rotates copies by 90, 135, 180, -90, -135
  about z; `bake_quarter_turns` turns the quarter turns into the geometry (face directions and UVs found by matching
  every corner's texture coordinate) and keeps the ±45 rest as the element angle.
- Multipart: a 2x2x3 member has one connection on each axis across the front, and front/back connections give its layer.
  A 3x3x6 corner member has exactly the same connections, so the blockstate cannot tell them apart:
  `RefinedDeviceBakedModel.getModelData` checks the body (`RefinedDeviceBody.find`, section 2) and only then lets the
  member draw its blockstate slice; 3x3x6 members keep the Blender mesh and use the model only for its particle. The
  "other members" fallback was rewritten so no state matches two parts (`RefinedDeviceResourcesTest`).
- `RefinedDeviceRenderer`'s carrier now starts at half the body's length in front of its centre (was the section width,
  which equals half the length only for 1x1x2 and 3x3x6).
- `validate_assets.py` lists the 64-pixel sheet.

## Checks

Unit tests 830/830, GameTest manifest 335/335 (`beamconvertershapes` now expects 2x2x3 formed and 2x2x4 unformed;
`beamconverterbodysharesonebuffer`, `operatingpowerneedsamatchingconverter` and `beamconverterrearportseveryorientation`
build 2x2x3). In game: `build/converter-2x2-check/2x2x3-before-after.png` (south, back, east, up) and
`other-sizes-before-after.png` (2x2x4 now unformed, 3x3x6 and 1x1x2 unchanged), shot with
`build/converter-2x2-check/tools/shoot.sh <worktree> <label>` on Xvfb :95 in a copy of the guidetest save.

## Unused Blender meshes removed

`device_meshes/laser-1.json` (unused since the artist's 1x1x2 art) and `laser-2.json` (unused since this change) were
deleted at the owner's request. `RefinedDeviceAssets` loads only `laser-3`, and `tools/blender/export_refined_devices.py`
exports only the 3x3 laser, so a new Blender export does not bring them back. In game the three sizes look the same:
`build/laser-mesh-cleanup-check/converters-before-after.png`.
