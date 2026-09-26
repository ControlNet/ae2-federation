# V07 — isolated cable continuity correction

V06 block designs were approved for Mod integration. Before integration, the
user identified a mismatch between isolated and connected cable appearance.
This version preserves V06 and changes only the isolated cable model.

## Cause and correction

The old zero-connection state was a solid 6x6x6 cube using cable_idle.png.
Connected cable uses a 6x6 transparent envelope and a 4x4 animated cyan core.
Those were different material structures, not just different shades.

The corrected node uses the exact center geometry, textures and animation
settings from cable_end: glass [5,5,5]..[11,11,11] and core [6,6,6]..[10,10,10].
All six faces are closed because no side connects. The missing connected-side
face is restored. No collar is added; white collars remain at actual connected
block boundaries. No connection direction, port or synchronization is added.
The animation is decorative and does not imply actual throughput.

V06's three full blocks, Bridge and nine connected/representative cable models
apart from the isolated model remain untouched. All texture files are unchanged.
The obsolete cable_idle texture is retained only as historical source material;
the new isolated model does not reference it.

## Actual Blockbench evidence

- `renders/01-isolated-comparison.png`: left to right, V06 solid isolated node,
  V07 layered isolated node, unchanged one-ended cable, unchanged straight cable.
- `renders/02-isolated-detail.png`: corrected node, actual editor viewport.
- `renders/03-family.png`: V06 series with only the isolated node replaced.
- `renders/04-editor.png`: actual Blockbench editor.

The offscreen editor can simplify low-opacity glass; final transparency and
texture-atlas animation still require Minecraft verification during integration.

## Open and verify

From the repository root:

```sh
./tools/blockbench/launch.sh "$PWD/tools/blockbench/versions/v07-isolated-cable/models/isolated_comparison.bbmodel"
python3 tools/blockbench/verify_art_versions.py
python3 tools/blockbench/verify_v07.py tools/blockbench/versions/v07-isolated-cable
node tools/blockbench/check_v07_editor.mjs tools/blockbench/versions/v07-isolated-cable
```

Expected: manifest/reference checks pass; exactly two closed nested cubes using
connected-cable textures and animation settings; 12 other device/cable projects
and every texture unchanged from V06. All 15 projects load in actual Blockbench
without missing textures. The editor check needs the project MCP running and
writes fresh evidence only to `.local/verification/v07-editor/`.

Rebuild working assets with Blockbench running:

```sh
node tools/blockbench/design_v07.mjs
node tools/blockbench/compose_v07.mjs
node tools/blockbench/render_study.mjs tools/blockbench/.local/v07-work/models/isolated_comparison.bbmodel tools/blockbench/.local/v07-work/renders/01-isolated-comparison.png context
```

The design script extracts actual connected-center cubes, restores their end
faces and saves through Blockbench's native project codec. Working output stays
in `.local/v07-work/`; this archive is frozen. Future revisions use V08+.

## Production follow-up

Mod integration is still pending. The production generator must remove its
mask-zero opaque-cube exception and keep the normal central glass/core layers.
The structural validator must include mask zero in its layer/continuity checks,
assert closed surfaces and no collars. Inventory cable already references mask
zero and must be checked with the translucent model. The approved V06 textures
and this geometry correction must enter the maintained generator together.
This archive is not a claim of game integration or in-game acceptance.
