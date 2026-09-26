# V06 — full functional panels, branched from V04

The user rejected V05's isolated symbols and excessive white negative space,
and explicitly requested returning to V04. V06 loads V04 device projects and
reworks their full colored panels. It does not build on V05's diamond, paper
or bracket graphics. V04 and every earlier delivered version remain unchanged.

## Foreground/background correction

Keep the approved V04 quartz border and dark outline exactly. Fill the central
10x10 field with functional material, using three accent levels to define its
structure. White remains behind the panel and at its edge; it does not cut the
foreground into a small symbol. This follows the measured area allocation of
the actual AE2 19.2.17 Pattern Provider and Interface textures.

Non-neutral pixels within the central 100-pixel field:

| Version | Router | Provider | Endpoint |
| --- | ---: | ---: | ---: |
| V04 | 64 | 84 | 66 |
| V05 (rejected) | 48 | 78 | 44 |
| V06 | 100 | 100 | 100 |

Native AE2 Provider and Interface both use 100 colored pixels in this field.
These counts describe area allocation, not aesthetic quality or user approval.
See foreground-coverage.json for the recorded comparison.

- Router: continuous cyan coupling surface, central core and four symmetric
  approach structures. Six faces remain equivalent.
- Provider: three broad pattern layers on a connected cyan substrate, retaining
  V04's functional layout without its white cutouts.
- Endpoint: full cyan processing panel, concentrated central execution window
  and short lower marks. No isolated bracket or pictogram treatment.
- ME rear and sides: full purple plate surface with interlocking tonal areas;
  the existing short cyan cue still points toward the real Federation front.
- Bridge/cable: unchanged from V04, which preserved the preferred V02 designs.

All eight tiles were painted through native Blockbench MCP paint_with_brush and
exported with its project codec. Geometry, face UVs, palette, exterior pixels,
connection semantics and production resources are unchanged.

## Actual Blockbench images

| File | Contents |
| --- | --- |
| `renders/01-key-faces.png` | Router / Provider / Endpoint fronts |
| `renders/02-ae2-context.png` | Native AE2 Provider / V06 Provider / native Interface / V06 Endpoint |
| `renders/03-family.png` | Full series with unchanged Bridge/cable |
| `renders/04-provider-comparison.png` | V04 left, V06 right |
| `renders/05-router-comparison.png` | V04 left, V06 right |
| `renders/06-endpoint-comparison.png` | V04 left, V06 right |
| `renders/07-me-back.png` | Purple rear and side treatment |
| `renders/08-editor.png` | Actual editor with the family scene |

## Open and verify

Run from the repository root:

```sh
./tools/blockbench/launch.sh "$PWD/tools/blockbench/versions/v06-dense-panels/models/v06_family.bbmodel"
python3 tools/blockbench/verify_art_versions.py
pixi run --manifest-path tools/visual/pixi.toml python tools/blockbench/verify_v06.py tools/blockbench/versions/v06-dense-panels
node tools/blockbench/check_v06_editor.mjs tools/blockbench/versions/v06-dense-panels
```

Expected: manifest and reference checks pass; eight exported tiles exactly match
the pixel plan and embedded sources; geometry/UVs unchanged; all exterior pixels
match V04; all 100 central pixels per tile carry functional color. Ten Bridge/cable
projects and unrelated textures remain byte-identical to V02. With Blockbench
running, all 18 projects load without missing textures. New evidence writes only
to `.local/verification/v06-editor/`.

To reproduce working assets with the project editor running:

```sh
python3 tools/blockbench/prepare_v06_references.py
node tools/blockbench/design_v06.mjs
node tools/blockbench/compose_v06.mjs
node tools/blockbench/render_study.mjs tools/blockbench/.local/v06-work/ae2_context.bbmodel tools/blockbench/.local/v06-work/renders/02-ae2-context.png context
```

The reference script reads the locked AE2 19.2.17 Gradle JAR or accepts `--jar`.
Reference models/textures stay in ignored local storage, not in production or
this archive. Python image checks use the existing tools/visual pixi environment.
Working outputs go to `.local/v06-work/`; archived source copies record provenance.
Do not overwrite this version. Further revisions use V07 or later.

## Validation boundary

18 native projects loaded in actual Blockbench 5.2.1 without missing textures.
Pixel, geometry, UV, exterior preservation, coverage and previous-device checks
passed. This remains an art-review candidate. It has not replaced production
resources or undergone Minecraft in-game acceptance or a new client/server build.
