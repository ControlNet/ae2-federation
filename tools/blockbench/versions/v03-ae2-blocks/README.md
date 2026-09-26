# V03 — AE2 block refinement

Independent visual-review candidate. V01 and V02 remain frozen. V03 revises the
three full blocks; Bridge and all nine individual cable projects are exact V02
copies, including their textures and animation metadata. No production game
resources, geometry, UV orientation or business behavior changed.

## Reference and design

Inspected the actual AE2 19.2.17 dependency JAR: Pattern Provider (normal and
alternate front), Interface, Energy Acceptor, Controller and IO Port front.
`reference-provenance.json` records the artifact digest. The reference-face
render shows these six textures left-to-right, top row then bottom row.
Its flat display surfaces are a texture study, not native block geometry.
The context render uses the verified native cube_all Provider and Interface.
Native reference PNGs/models remain in ignored local storage, not this version.

AE2's neutral pale shell, graphite separation and broad functional color areas
informed a cooler, less fragmented block treatment. New pixels were authored
through Blockbench MCP paint_with_brush; original AE2 textures were not recolored
or copied into Federation assets. The v0.2 no-heavy-frame constraint remains:
full cube silhouette, one thin outer edge, local functional recesses only.

- Router: broader cyan coupler and four equal short paths, same texture on all
  six faces. No special top emblem.
- Provider: three staggered pattern plates, short distribution rail, stronger
  purple ME plates on the other faces.
- Endpoint: concentrated wide processing window with two short execution marks;
  no extra decorative top bar. Purple back and consistently oriented side cues.
- Shared shell: neutral cold white/gray, fewer fragmented corner marks, clearer
  light/dark hierarchy. Larger ME plates replace the tiny V02 purple contacts.
- Bridge/cable: retain the user-preferred V02 design, byte-for-byte.

The purple center and short cyan edge on peripheral faces preserve the existing
front direction. Colors identify Federation/ME, not throughput or transfer direction.

## Review images

All renders are actual Blockbench MCP viewport captures; no alternate renderer.

| File | Contents |
| --- | --- |
| `renders/01-family.png` | Full series, V03 blocks with retained V02 Bridge/cable |
| `renders/02-provider-comparison.png` | V02 left, V03 right |
| `renders/03-router-comparison.png` | V02 left, V03 right |
| `renders/04-endpoint-comparison.png` | V02 left, V03 right |
| `renders/05-ae2-context.png` | AE2 Provider / V03 Provider / AE2 Interface / V03 Endpoint |
| `renders/06-key-faces.png` | Three Federation fronts |
| `renders/07-me-back.png` | Purple ME rear and side treatment |
| `renders/08-ae2-reference-faces.png` | Six original reference face textures |
| `renders/09-editor.png` | Actual editor with V03 family open |

## Open and verify

Run from the repository root:

```sh
./tools/blockbench/launch.sh "$PWD/tools/blockbench/versions/v03-ae2-blocks/models/v03_family.bbmodel"
python3 tools/blockbench/verify_art_versions.py
pixi run --manifest-path tools/visual/pixi.toml python tools/blockbench/verify_v03.py tools/blockbench/versions/v03-ae2-blocks
node tools/blockbench/check_v03_editor.mjs tools/blockbench/versions/v03-ae2-blocks
```

Expected: manifest/model checks pass; 8 authored tiles exactly match the pixel
plan; block geometry/UVs remain unchanged; 10 Bridge/cable models and unrelated
textures match V02 byte-for-byte; all 18 projects load without texture errors.
The last command requires the project editor running and writes new evidence
only to `tools/blockbench/.local/verification/v03-editor/`.

To rebuild the working candidate, with Blockbench running:

```sh
python3 tools/blockbench/prepare_v03_references.py
node tools/blockbench/design_v03.mjs
node tools/blockbench/compose_v03.mjs
node tools/blockbench/render_study.mjs tools/blockbench/.local/v03-work/models/v03_family.bbmodel tools/blockbench/.local/v03-work/renders/01-family.png gallery
```

Reference preparation accepts `--jar /path/to/appliedenergistics2-19.2.17.jar`;
otherwise it reads the locked Gradle cache. Rebuilds write ignored working data,
never this archive. `source/` preserves the exact pixel plan and scripts for
provenance; executable tools live at `tools/blockbench/`. Future revisions go to
V04 or later rather than overwriting V03. Python image verification uses only
the already approved tools/visual pixi environment; no runtime dependency.

## Validation boundary

Actual Blockbench 5.2.1 loading and captures passed, including all 18 projects.
This is a review candidate, not Minecraft in-game acceptance. Blockbench shading
and transparency differ from the game; actual world lighting, blockstate/Part
rotation and resource integration need game validation when this art is selected.
No game build, server test or production replacement is claimed for this revision.
