# V05 — functional glyph study with the approved V04 shell

The user approved V04's white background and dark outline but disliked its
internal patterns. V05 changes only the central 10x10 artwork. The surrounding
quartz pixels, dark outline, palette, full cube geometry and UV orientation
remain fixed. This is a visual-review candidate, not user approval of the glyphs.

## Design changes

- Router: compact diamond coupler with four short terminals, replacing the
  broad plus-sign silhouette. The same texture remains on all six faces.
- Provider: two offset pattern wafers with shallow etched tracks and a short
  distribution mark. Their overlapping silhouettes replace the numeral-like
  three-bar pattern. No black server-slot background.
- Endpoint: opposing contact jaws enclosing a compact processing core, with
  clear negative space between them. No face-like pair of execution dots.
- ME rear and side faces: faceted Fluix-colored shapes replace the nested
  rectangular rings. Cyan side cues preserve the original front orientation.
- Bridge and cable remain byte-identical to V02.

Painted through native Blockbench MCP paint_with_brush and exported as editable
native projects. No raster mockup generator or alternate 3D renderer was used.
All source pixels and the current scripts are archived under source/.

## Actual Blockbench captures

| File | Contents |
| --- | --- |
| `renders/01-key-faces.png` | Router / Provider / Endpoint fronts |
| `renders/02-family.png` | Full series with preserved V02 Bridge/cable |
| `renders/03-provider-comparison.png` | V04 left, V05 right |
| `renders/04-router-comparison.png` | V04 left, V05 right |
| `renders/05-endpoint-comparison.png` | V04 left, V05 right |
| `renders/06-ae2-context.png` | Native AE2 Provider / V05 Provider / native Interface / V05 Endpoint |
| `renders/07-me-back.png` | Purple rear and side artwork |
| `renders/08-editor.png` | Actual Blockbench editor with the family scene |

## Open and verify

Run from the repository root:

```sh
./tools/blockbench/launch.sh "$PWD/tools/blockbench/versions/v05-pattern-study/models/v05_family.bbmodel"
python3 tools/blockbench/verify_art_versions.py
pixi run --manifest-path tools/visual/pixi.toml python tools/blockbench/verify_v05.py tools/blockbench/versions/v05-pattern-study
node tools/blockbench/check_v05_editor.mjs tools/blockbench/versions/v05-pattern-study
```

Expected: hashes and structure checks pass; eight exported tiles match the pixel
plan; geometry/UVs unchanged; all exterior pixels outside the central 10x10 area
match V04 exactly on every revised tile; 10 Bridge/cable projects and unrelated
textures match V02 byte-for-byte. With the project editor running, all 18 models
load without missing textures. Fresh editor evidence goes only to the ignored
`.local/verification/v05-editor/` directory.

To reproduce working assets with the project editor running:

```sh
python3 tools/blockbench/prepare_v05_references.py
node tools/blockbench/design_v05.mjs
node tools/blockbench/compose_v05.mjs
node tools/blockbench/render_study.mjs tools/blockbench/.local/v05-work/models/v05_key_faces.bbmodel tools/blockbench/.local/v05-work/renders/01-key-faces.png faces
```

The reference script reads the locked AE2 19.2.17 Gradle JAR, or accepts `--jar`.
Original AE2 reference models/textures stay in ignored local storage; only their
actual context capture and JAR provenance are archived. Python pixel checks use
the existing approved tools/visual pixi environment. Rebuilds write working
files under `.local/v05-work/`, never this frozen archive. Future edits use V06+.

## Validation boundary

18 projects loaded in actual Blockbench 5.2.1 with no missing textures; pixel,
geometry, UV, shell preservation and prior-device preservation checks passed.
V01–V04 remain unchanged. These checks establish file integrity, not aesthetic
acceptance. This version has not replaced production resources and has not
undergone Minecraft in-game acceptance or a game build/server test.
