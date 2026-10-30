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
