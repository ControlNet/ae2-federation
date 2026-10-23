# Beam Former art, artist PR #6 (2026-10-23)

PR #6 ("添加1*1和2*2截面的光束发信器模型", artist fork `dev`) adds `tools/blockbench/projects/beam_emitter.bbmodel`
(1x1), `large_beam_emitter.bbmodel` (2x2, 32x32 voxels, resolution 64) and four textures in
`textures/block/beam_emitter/` (`light_emitter_{front,side,back}`, `large_light_emitter` 64x64). No block model JSON,
blockstate or code: the bbmodels must be exported and wired by us. No 3x3 model.

## Review findings

- The PR also deleted `textures/block/armor.png`, still the cable block model's particle. Resolved on dev in
  c9393d9f: the particle is `part/cable/dense/core`, armor.png is gone (user, 2026-10-23), so the PR's deletion is now
  a no-op.
- Names: block is `beam_former`, the artist uses `beam_emitter` / `light_emitter_*`. Ask before renaming either.
- Geometry checks passed: every face's UV extent equals its face size (16 px per block in both models), the per-face
  rotations (90/270) are consistent, no transparent pixels under used faces. `texture: null` faces are hidden faces
  (against the body or inside the frame) and are dropped on export. The bottom-right quarter of the 64x64 sheet is
  empty, unused.
- 2x2 details: four `corner` elements rotated +/-45 deg about z around (16,16,16); `b` elements reach z=17, one voxel
  past the block's front; the centre `front` and several `b` elements straddle the x=16 / y=16 member boundaries.
- The 2x2 cannot be chosen by blockstate alone: a 3x3 corner member also has exactly two perpendicular connections,
  so the real integration needs the body size from code (a baked-model wrapper like `DeviceStructureBakedModel`).

## Trial wiring (not committed)

`build/beam-former-art-check/convert_trial.py <dir with bbmodels+pngs> <pack dir>` exports the 1x1 as one model and
the 2x2 as four member models (cuboids clipped at x/y=16 with UVs remapped through the face rotation; rotated parts go
whole to the member holding their rotated centre; faces on a clipped inner plane dropped; UVs scaled 16/uv_width) and
a 768-variant blockstate picking the quadrant from facing + connection booleans (Rx/Ry(-90) per blockstate step, as
`BlockModelRotation`). In game (guide client, scratch page + snbt in a detached worktree) the quadrants join without
seams in all six facings. Images: `build/beam-former-art-check/` (`before-after.png`, `after/closeup-*.png`,
`cable-particle-before-after.png`).

Particle check recipe: guide client with `-PguideWorld=guidetest` (copy the save into the worktree's run-guide),
type chat commands through XTEST, `/particle minecraft:block{block_state:"ae2federation:cable"} ... 120 force`, then
`import -window root` about 0.25 s later.

## Shipped (2026-10-23)

PR #6 merged on dev as 6a594d65 (local `--no-ff` merge, as PR #5), wired by b1f76f89 (built by Codex gpt-6-astra
from a brief, reviewed by a fresh read-only Codex; three review fixes: damaged 2x2 members fall back to the 1x1
model, `getShadeBrightness` 1.0 for small/large, shape-test evidence written after the reset checks). Details of the
exporter and multipart: `beam-former-model-export.md`. In-game before/after (dev 6a594d65 vs wired, same world and
camera): `build/beam-former-art-check/wired/`. Texture paths kept as the artist's `block/beam_emitter/*` because the
Blockbench projects resolve textures by file name.

In-world check recipe (kept in the git-ignored `build/beam-former-art-check/tools/`): `scenes.py <ae2guide dir>
size|plain` writes the scratch page and scenes, `shoot.sh <worktree> <label>` runs the guide client with
`-PguideWorld=guidetest` on a private Xvfb :95, builds 1x1/2x2/3x3 formers with `/fill ... beam_former[facing=south]`
(they form on placement), shoots the world and then the guide page (`/guidemec ae2:guide open ...`); `typer.py`
types chat commands through XTEST (add any new punctuation to its key-name table).
