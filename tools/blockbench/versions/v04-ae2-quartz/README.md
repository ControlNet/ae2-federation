# V04 — quartz edges and flat AE2-style glyphs

Visual-review candidate after the user rejected V03's style. This version
replaces the gray beveled housings and dark display panels with a bright quartz
edge, one dark pixel seam and flat functional artwork. Do not treat structural
validation as aesthetic approval. V01–V03 remain independently archived.

## Reference analysis

Read the actual locked AE2 19.2.17 dependency resources. Native Interface and
Pattern Provider each use seven colors: two quartz whites (#f2f2f2, #e8e8ea),
two structural grays (#413f54, #4d4d67) and three accent colors. Their outer
edge is pale rather than V03's gray bevel. The limited color ramps and small
coherent surface variations distinguish them from industrial cabinet panels.
V04 uses those four neutral color values, independently drawn edge variation
and original functional glyphs. The three accent levels are Federation cyan or
ME purple. Native colored concentric graphics are not copied wholesale.

The outer seam is a single pixel outline within a complete 16-unit cube.
There are no modeled extrusions, gray beveled frames, corner bolts, multiple
nested housing frames or deeply recessed full-face panels.

- Router: four equal short paths meet a compact cyan center. All six faces share
  one texture; no top emblem.
- Provider: three interleaved colored pattern sheets and a short distribution
  path. No black server slots or monitor bezel.
- Endpoint: cyan execution field with central workpiece and two short execution
  ticks; distinct from the four-way Router and layered Provider glyphs.
- ME faces: dominant purple paired contact glyphs; short cyan edge cues retain
  the existing front orientation.
- Bridge and cable: retained exactly from V02, as preferred by the user.

All eight revised tiles were painted in Blockbench through MCP paint_with_brush
and saved using the native project codec. Geometry and UV orientation are unchanged.
The native AE2 reference models remain in ignored working storage; only actual
comparison captures and the JAR provenance record are included in this archive.

## Images

All screenshots come from actual Blockbench MCP rendering.

| File | Contents |
| --- | --- |
| `renders/01-ae2-context.png` | Native Provider / V04 Provider / native Interface / V04 Endpoint |
| `renders/02-family.png` | V04 full blocks with the unchanged V02 Bridge/cable |
| `renders/03-provider-comparison.png` | V03 left, V04 right |
| `renders/04-router-comparison.png` | V03 left, V04 right |
| `renders/05-endpoint-comparison.png` | V03 left, V04 right |
| `renders/06-key-faces.png` | Router / Provider / Endpoint fronts |
| `renders/07-me-back.png` | ME rear and side treatment |
| `renders/08-editor.png` | Actual Blockbench editor with the family scene |

## Open and verify

From the repository root:

```sh
./tools/blockbench/launch.sh "$PWD/tools/blockbench/versions/v04-ae2-quartz/models/v04_family.bbmodel"
python3 tools/blockbench/verify_art_versions.py
pixi run --manifest-path tools/visual/pixi.toml python tools/blockbench/verify_v04.py tools/blockbench/versions/v04-ae2-quartz
node tools/blockbench/check_v04_editor.mjs tools/blockbench/versions/v04-ae2-quartz
```

Expected: all version hashes pass; 8 tiles exactly match the authored pixel plan;
block geometry and UVs are unchanged; Router faces equivalent; 10 Bridge/cable
models and unrelated textures match V02 byte-for-byte. The editor check requires
Blockbench running and loads all 18 projects with zero missing textures. Fresh
evidence is written only to `.local/verification/v04-editor/`.

Rebuild working assets with the project editor running:

```sh
python3 tools/blockbench/prepare_v04_references.py
node tools/blockbench/design_v04.mjs
node tools/blockbench/compose_v04.mjs
node tools/blockbench/render_study.mjs tools/blockbench/.local/v04-work/ae2_context.bbmodel tools/blockbench/.local/v04-work/renders/01-ae2-context.png context
```

Reference preparation reads the locked Gradle JAR or accepts `--jar /path/to/appliedenergistics2-19.2.17.jar`.
Working outputs stay in `.local/v04-work/`. Source copies and pixel plan are
archived under `source/` for provenance; run maintained scripts from
`tools/blockbench/`. Further revisions must use V05 or later.

## Validation boundary

Actual Blockbench loading, native painting, export, pixel equality and structural
preservation checks passed. This version has not been approved by the user or
integrated into production resources. No Minecraft rendering acceptance, game
build or server test is claimed. Game lighting and final rendering remain to
verify when a visual direction is selected. Cable animation is unchanged.
