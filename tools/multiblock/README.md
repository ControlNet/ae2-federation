# Connected multiblock device preview

This is an executable geometry prototype for Router and Switch cuboids. It reads the current
artist-authored production JSON models and PNG textures and produces connected meshes and Blender
renders. The game uses a Java port of the same geometry (`RouterTileGeometry`, `DeviceStructureGeometry`);
the parity fixture below keeps the two equal.

## Implemented geometry

- Reject empty, hollow, incomplete, or protruding cuboid candidates.
- Retain exactly eight corner pieces for the whole body.
- Retain the original edge assemblies only along the body's exterior edges and extend their ends
  across block boundaries. End-pixel extension preserves the central decorative pieces.
- Generate exterior panel surfaces with borderless extensions. The original center UV region keeps
  its scale; narrow edge pixels extend across new seams without stretching the center motif.
- Preserve the Router's raised core and face details. The Switch retains its purple surface motifs
  and the original detailed edge assemblies.
- Export geometry as Minecraft-coordinate quads with normalized UVs, independent of Blender.
- Keep source models and textures unchanged, recording SHA-256 checks in the render report.

The accepted appearance uses `router_style='tiles'` and `switch_style='white-backed'`. Default
`plain` styles, the raised Router links and earlier renders remain historical comparisons. Start with the
[implementation handoff](../../docs/ideas/multiblock-device-assembly-and-appearance.md) for accepted
requirements, final render commands and runtime behaviour.

A connected Switch member also gets the inner walls of each edge channel (`channel_walls`): the
original body and face slabs closed them, and without them the world shows through the notches
beside the edge contacts.

## Run

From this worktree's root, check geometry with standard-library Python (no packages required):

```bash
python -m unittest discover -s tools/multiblock -v
```

Expected: twenty-one tests pass, including exterior coverage, corner counts, source assets, UV bounds,
thin structures, invalid shapes, unchanged Router/single-block geometry, links meeting across
all six face orientations, and rays into connected members always meeting a face. The shape inputs
are local geometry test cases, not game-world integration tests.

After changing the geometry, rewrite and check the Java parity fixture
(`common/src/test/resources/multiblock/device-structure-parity.json`), then run
`DeviceStructureGeometryTest`:

```bash
python3 tools/multiblock/export_runtime_parity.py
python3 tools/multiblock/export_runtime_parity.py --check   # PARITY_FIXTURE_CURRENT
```

Render with the installed Blender executable and its bundled Python; do not install `bpy` with pip:

```bash
blender --background --factory-startup --threads 8 --python tools/multiblock/render.py
```

The command creates or replaces generated artifacts under `build/multiblock-devices/`, including:

| Artifact | Content |
|---|---|
| `router-comparison.png` | Original stacked blocks and connected 3x2x2 Router |
| `switch-comparison.png` | Original stacked blocks and connected 3x2x2 Switch |
| `front-panel-detail.png` | Front orthographic 3x3x1 surfaces of both devices |
| `thin-structures.png` | 1x1x4 connected bodies beside their original single blocks |
| `router-3x2x2-mesh.json`, `switch-3x2x2-mesh.json` | Actual connected mesh data |
| `multiblock-devices.blend` | Editable scenes with textures packed |
| `report.json` | Asset hashes, geometry counts and render filenames |

Expected: four `MULTIBLOCK_RENDERED` messages followed by `MULTIBLOCK_OK`. The report must show
`source_assets_unchanged: true`. For an existing preview, replace just the front image with:

```bash
blender --background --factory-startup --threads 8 --python tools/multiblock/render.py -- --only front-panel-detail
```

This still refreshes the scene file, exported meshes and report. Rendered images stay in `build/`.
The renderer uses Blender 5.2.1 as installed in this workspace; no production asset generator is run.

## Switch surface-density follow-up

The user found too much pale empty space between the Switch's purple centers. Two optional geometry
styles address that feedback while preserving the baseline (`switch_style='plain'`) and Router:

- `links`: shallow cyan connector strips with dark beds, joining neighboring purple modules.
- `infill`: the same connectors plus dark panels in the surrounding pale spaces and fine grooves
  at four-way junctions. Existing source texture pixels provide every color.

The base surface recesses by 0.25 model units when these details are present, so decorative layers
stay within block bounds and do not share a plane with the original panel. Links terminate against
the purple motifs, meet across block boundaries and do not wrap around body corners. Single-block
geometry remains unchanged. Neither variant has been selected as final art.

Generate an independent comparison set (creates or replaces its own V2 output files):

```bash
blender --background --factory-startup --threads 8 --python tools/multiblock/render_switch_fill.py
```

Expected: three `MULTIBLOCK_RENDERED` messages and `SWITCH_FILL_OK`. Files are under
`build/multiblock-devices/switch-fill-v2/`: `switch-fill-comparison.png` compares the baseline and
both styles; `switch-fill-front.png` shows front surfaces; `switch-filled-shapes.png` checks full
and thin bodies. Mesh JSON, an asset-hash report and a packed `.blend` accompany the images.
The previous four preview images and their scene file are retained.

## Matching the original contact relief

The user accepted the overall direction but rejected the flat interior connector treatment. The
requested correction is specifically to match the original blue perimeter contacts in material,
texture, and recessed geometry. It does not call for a new chassis or surface layout.

`switch_style='edge-matched'` keeps the V2 infill layout and replaces its connector strips with
the original Switch model's cyan contact (element 15) and recessed side pieces (elements 16/17).
`switch_connectors.py` transforms their actual coordinates and UVs into each exterior face.
These pieces retain the original 0/1/2-model-unit depth profile. Matching halves meet at seams;
a one-unit recessed neck meets each purple module. Additional caps close sides that were buried
in the source model but are visible in the new socket.

Both the base skin and infill are cut away at the socket footprint. A floor and actual vertical
walls enclose the two-unit-deep pocket. This avoids placing a recessed component behind an opaque
flat panel, or merely using a dark rectangle to suggest a recess. Source model/texture files and
previous preview variants remain unchanged.

```bash
blender --background --factory-startup --threads 8 --python tools/multiblock/render_switch_contacts.py
```

Expected: three `MULTIBLOCK_RENDERED` messages and `SWITCH_CONTACTS_OK`. The command creates or
replaces its own outputs under `build/multiblock-devices/switch-contacts-v3/`: a V2/V3 comparison,
an original-block/connected-body close-up, a grazing-angle socket view, mesh JSON, source-hash
report and packed `.blend`. Tests additionally check the exact source-contact UV range and
depths and prove that no skin/infill quad still covers a socket opening.

## Router connected-face tiles

`router_style='tiles'` (`router_tiles.py`) builds the Router from the artist's four tiles in
`textures/block/router/`: `router_base` on every exposed face, the top/bottom rows of `router_side_hor`
and left/right columns of `router_side_ver` where a face edge is on the body's outside, and the 4x4
corner squares of `router_corner` where both are. Texels in the two backing greys are recessed one
unit; every other texel is flush; walls close each step and show the raised texel folded over the
edge. The PNGs are read with a small standard-library decoder. Tests check that a single block
equals `router.png` and the standalone model's relief on all six faces, that four members share a
small core, that frame tiles stay on the body's outer edges, and that every step has a wall.

## Raised Router links (superseded)

The user requested that the Router's middle blue connections protrude above the backing. The
earlier preview left most of each connection as a texture on the one-unit-inset panel. With
`router_style='raised'`, the builder extends the artist's four-unit-wide, one-unit-high core tabs
to adjacent member boundaries. The connector top is coplanar with the original core top, one
model unit above the backing, and the connector has actual sidewalls. Original tab UVs and end
pixels supply the texture. Core shapes, outside frames and all Switch variants are preserved.

```bash
blender --background --factory-startup --threads 8 --python tools/multiblock/render_router_links.py
```

Expected: two `MULTIBLOCK_RENDERED` messages and `ROUTER_RAISED_OK`. Outputs are created or replaced
only under `build/multiblock-devices/router-raised-v2/`: `router-raised-comparison.png` and
`router-raised-closeup.png`, plus mesh JSON, an asset-hash report and a packed `.blend`. Earlier
images are retained. The additional tests assert connector height, sidewall thickness, matching
seams on all six faces, unchanged core/frame, and unchanged single-block/Switch geometry.

## Runtime integration

The Router tiles and the white-backed Switch run in game as a chunk-baked model; see the
[handoff](../../docs/ideas/multiblock-device-assembly-and-appearance.md) for the runtime, its tests
and the in-game screenshot run. Offline lighting differs from Minecraft, and the Blender images
are not in-game screenshots.

### Original white Switch backing

The `white-backed` style retains V3 geometry and replaces only the infill UVs with
original Switch body pixel `(2, 2)` (`RGB 242, 242, 242`). Contact sockets, purple
modules, outer frame and junction grooves remain unchanged.

```bash
python -m unittest discover -s tools/multiblock -v
blender --background --factory-startup --threads 8 --python tools/multiblock/render_switch_white.py
```

Expected: 16 passing tests and `SWITCH_WHITE_OK`. The comparison, original-block
reference, grazing detail, exported mesh, hash report and packed Blender scene
are written under `build/multiblock-devices/switch-white-v4/`. This is an offline
visual prototype; existing V3 outputs are retained for comparison.
