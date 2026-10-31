# Beam Converter art, artist PR #8 (2026-10-30, review only, not merged)

PR #8 ("添加光束转换器方块", artist fork `dev`, no description) adds three Blockbench projects in
`tools/blockbench/projects/` and six 16x16 textures in `textures/block/beam_converter/`, all new files, no deletions:

- `beam_converter.bbmodel`: one block, body z 0..13 plus a recessed front frame z 13..16; south = front
  (`beam_converter_front`), north = back (`beam_converter_back`, orange rings), sides/top/bottom
  `beam_converter_side` (magenta square in a white frame).
- `beam_converter_restraints.bbmodel`: twelve 2 px edge rails of one block (`beam_converter_restraints_frame`).
- `beam_converter_restraints_formed.bbmodel`: a square ring at z 3..5 with four rails along z, four of them reaching
  z = -1, one pixel past the block (`beam_converter_restraints`, `beam_converter_restraints_front`). It reads as a
  pixel-art take on the shipped open-collimator rear section.

No block model JSON, blockstate or code; textures do not replace the shipped `back/front/side/top.png`, so merging
alone changes nothing in game. No creative variant, no 2x2x4 / 3x3x6 pieces.

## Checks (all clean)
Every used face's UV extent equals its face size (16 px per block); no transparent texels under used faces; hidden
faces have `texture: null`. Only the four formed-rail elements leave the block (z = -1).

## Open questions for the owner/artist
Which model is the unformed block and which the formed members; how the formed pieces tile for 1x1x2, 2x2x4, 3x3x6
and whether they replace the Blender open-laser mesh; magenta on an ordinary converter while purple was reserved for
the creative one; magenta squares on the sides look like ports although only the rear takes FE.

## Review aid
`build/pr8-check/tools/render_bbmodel.py OUT TEXDIR front|back|x,y,z SCALE model.bbmodel[@dx,dy,dz[@k]] ...` renders
bbmodel cuboids orthographically with Minecraft's face UV/rotation convention (Pillow only, run with
`tools/visual/.pixi/envs/default/bin/python`). Renders: `build/pr8-check/models-overview.png`, `models-extra.png`.

## Owner decision and wiring (2026-10-30, option A)
Owner: the 1x1x2 is the converter block (charging port) plus the spiked head that emits into the network. Option A
chosen: one block type, look by position. Merged locally as 477380a9, wired in the next commit:
- `tools/blockbench/export_beam_converter_models.py` (reuses the Beam Former exporter's `export_elements`/`ROT`) writes
  `block/beam_converter` (artist converter) and `block/beam_converter_head` (`restraints_formed`) plus a 24-case
  multipart: unformed -> converter; formed with only the front connection -> converter (rear member); only the back
  connection -> head; any cross connection or both/none axial -> converter (particle only, the wrapper draws Blender).
- `RefinedDeviceBakedModel.pixelMember`: ordinary converter states with exactly one axial connection fall through to
  the blockstate model; larger bodies and every creative converter keep the Blender mesh. The internal carrier beam
  of `RefinedDeviceRenderer` still runs and shows through the head's ring (kept, looks right).
- The plain cage (`restraints`) is unused; its shipped texture and the old generated `beam_converter/{back,front,side,
  top}.png` were deleted (validate_assets forbids unused textures; the bbmodels embed their textures).
  `build_device_block_textures.py` no longer writes the ordinary converter.
- Checks: `RefinedDeviceResourcesTest.ordinaryConverterStatesPickTheArtistsConverterOrHead`, validate_assets
  regenerates the export. In game: `build/beam-converter-art-check/before-after.png` (tools/shoot.sh <worktree> <label>
  on Xvfb :95 with a fresh copy of the guidetest save; /fill forms bodies on placement).
- Open: 2x2x4/3x3x6 art, creative colours, side "ports" vs rear-only FE.

## Creative converter removed (owner, 2026-10-31)
After PR #8 was merged (330b2e18) the owner first asked for a dark + violet creative variant of the artist's models,
then dropped the creative converter: most FE mods ship a creative energy source, which feeds an ordinary converter.
Removed: block, item, block entity binding, the `creative` codec field, the ledger's unlimited branch, the purple
carrier tint, the creative atlases and cube faces, loot, pickaxe tag, lang, guide mentions. The power guide now says
creative energy sources from other mods work. GameTest `operatingpowerneedsamatchingconverter` ends with a fed 1x1x2
instead; the refined-device review scene turns `OperatingPowerRule` off in its settle step.
The dark + violet recolour (artist palette mapped texel by texel, `CREATIVE` table in the exporter) was built and shot in
game, then discarded uncommitted; the shots stay in `build/creative-converter-check/` (local only).
