# V02 — AE2 ceramic study

Status: an independently versioned art candidate for human review. It does not
replace production assets. Future revisions must use a new version directory.

## Design

- Replace flat blue-white panels with a restrained ceramic palette, shallow
  panel transitions, small corner seats and short service seams.
- Router: a central cyan coupler with continuous short four-way paths, equal on
  all six faces.
- Provider: three staggered pattern plates with pale upper edges, cyan faces,
  dark lower edges and a short distribution bus.
- Endpoint: a focused horizontal processing window with paired execution marks,
  visually separate from the Provider's stacked plates.
- Peripheral ME faces: compact violet contacts replacing the large repeated
  purple/cyan stripes. Short cyan segments retain the authored front direction.
- Bridge: pale contact seats with localized violet contact pads and a restrained
  cyan middle window. Preserve the real 8 x 8 x 6 multipart envelope.
- Cable: quieter glass highlights, stationary pale collars, a continuous teal
  volume with a small traveling bright band. Flow is decorative and does not
  represent traffic quantity or transmission direction.

The device geometry and actual port semantics remain unchanged. This study
concentrates on material and pixel shading; apparent plate depth comes from the
texture. All ordinary tiles are 16 x 16. Flow sheets are 16 x 256 (16 frames).
Java projects retain the format range covering Minecraft 1.21.1.

## Review files

- `models/v02_family.bbmodel`: whole family; review scene, not one game block.
- `models/router.bbmodel`, `pattern_provider.bbmodel`, `processing_endpoint.bbmodel`,
  `bridge.bbmodel`: independent editable device projects.
- `models/cable_*.bbmodel`: representative states and connected review scenes.
- `models/*_comparison.bbmodel`: V01 on the left, V02 on the right.
- `textures/`: actual PNGs saved from the Blockbench texture canvases, plus
  animation metadata.
- `renders/01-family.png`: full collection.
- `renders/02-provider-comparison.png`, `03-router-comparison.png`,
  `04-endpoint-comparison.png`, `05-bridge-comparison.png`: before/after views.
- `renders/06-cable-continuous.png`, `07-cable-connections.png`: continuity,
  corners, branches, vertical and six-way junctions.
- `renders/08-cable-pulse.gif`: 16 frames captured by Blockbench MCP and encoded
  as a 10 fps GIF. There is no synthetic glow or post-render painting.
- `renders/09-ae2-context.png`: native AE2 Provider, Federation Provider, native
  AE2 Interface, Federation Endpoint, left to right. Native reference textures
  are read from the project's AE2 19.2.17 JAR; they are not shipped as candidate
  textures or models. This is a Blockbench context comparison, not Minecraft.
- `renders/10-key-faces.png`: Router, Provider and Endpoint functional faces.
- `renders/11-provider.png`, `12-router.png`, `13-endpoint.png`: same cameras as
  `v01-baseline/renders/provider.png`, `router.png`, `endpoint.png`.
- `renders/14-me-rear.png`: the ME-facing material treatment.

## Authoring and rendering

The pixel plan was applied using MCP `paint_with_brush` in the real editor.
Animation frames and transparent glass were edited with Blockbench's native
`Texture.edit` canvas API inside Undo edits. Individual projects were saved with
Blockbench's `export_model` project codec. View images were captured with MCP
`create_offscreen_view`, `set_camera_angle`, and `capture_screenshot`.

`source/` archives the exact authoring scripts and pixel plan for this version;
these are provenance copies, not standalone scripts to run from that directory.
The working commands live under `tools/blockbench/` and write only to the ignored
`.local/v02-work/` staging area. They do not overwrite this version directory.
Native AE2 context seeds and cable connection source models are staging inputs.

To reproduce a specific view from the delivered project, start the project
editor, then run from the repository root:

```sh
./tools/blockbench/launch.sh "$PWD/art/versions/v02-ae2-ceramic/models/v02_family.bbmodel"
```

In another terminal:

```sh
node tools/blockbench/render_study.mjs \
  art/versions/v02-ae2-ceramic/models/v02_family.bbmodel \
  /tmp/ae2-federation-v02-family.png gallery
python3 tools/blockbench/verify_art_versions.py
```

## Validation and limits

Validated actual editor loading, embedded textures, UV ranges and geometry
bounds. The 17 painted static tiles were compared pixel-for-pixel with the
intended palette plan: zero mismatches. The pulse sheet has 16 distinct rendered
frames. The V01 device snapshots remain identical to their source files.

The review scenes preserve the original models' connection masks; they do not
implement network simulation. Blockbench transparency/shading and animated
junction appearance still need Minecraft validation after a direction is
selected. No new in-game visual acceptance, JAR build or server test is claimed
for this art-only review. No gameplay, GUI or production assets were changed.
