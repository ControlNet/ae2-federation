# Multiblock device assembly and appearance

Discussion date: 2026-10-11.
Status: accepted appearance and solid-cuboid bodies implemented in Minecraft and merged into `dev`;
operational rules selected (see [Selected rules](#selected-rules)).
Remaining work is listed under [Not yet verified](#not-yet-verified).

## Handoff: start here

This page is the entry point for the next implementer. The user accepted the latest appearance after
reviewing the raised Router connectors and white-backed Switch, and it now renders in game from
validated bodies. Both devices' faces have since been refined by the artist's connected-face tiles
(see [Router](#router-the-artists-connected-face-tiles) and [Switch](#switch-the-artists-connected-face-tiles)). Previous dark Switch infill and flat Router links are historical comparisons, not
alternative final designs. The user also selected the failure, merge, size and chunk rules recorded
under [Selected rules](#selected-rules).

The work was done on branch `design/multiblock-devices` and merged into `dev` on 2026-10-11.
Generated renders and game screenshots belong under `build/`; they are not committed, and the
commands below regenerate them.

## Confirmed requirements

- Prioritize multiblock Switches and Routers; bundled/multiblock cables are deferred.
- Only complete, axis-aligned solid rectangular prisms are valid multiblock bodies. Length, width,
  and height need not be equal. Hollow, incomplete, L-shaped or branched bodies are invalid.
- Follow the shape principle of AE2 crafting CPUs. This does not import their storage requirements,
  channel costs, recipes, size limits, or exact detection algorithm.
- Preserve the original devices' visual identity, using a continuous outer frame and repeated
  interior modules. The surfaces must retain real relief; flat texture substitutions are insufficient.
- Use the accepted Router and Switch appearances described below as the implementation reference.
- Keep generated preview images in `build/` and perform changes in an isolated worktree.

## Selected rules

The user selected these on 2026-10-11; they are implemented.

- **Invalid candidate:** an incomplete, hollow, L/T-shaped, protruding or oversized candidate stops
  linking as a whole. No subset forms and the blocks do not fall back to single-block operation.
- **Size:** each edge is at most 8 blocks (`DeviceStructure.MAX_EDGE`); there is no minimum, so 1x1x1 is valid.
- **Grouping:** AE2 crafting-CPU rules. Same-type blocks sharing a face are judged together; one extra
  block against a finished body invalidates the whole candidate; edge or corner contact keeps bodies
  separate; Router and Switch never mix. No new persistent identity: the existing per-block nodes and
  Federation evidence carry it, and right-clicking any member of a formed body opens the domain screen.
- **Unloaded chunks:** a body keeps its last validated state, is checked again when its chunks load,
  and never force-loads a chunk.

Cable capacity, operating-power mechanics and new recipes are not selected by this handoff. The
[broader multiblock and capacity idea](multiblock-routing-and-cable-capacity.md) remains the record
for those separate discussions.

## Accepted appearance

### Shared construction

The outer corners and edges belong to the complete body, rather than repeating a full frame around
every constituent block. Extend exterior panels across member boundaries; retain repeated core/panel
motifs at their original scale. Preserve visible geometry and texture orientation on all six faces,
including one-block-thick bodies. The accepted prototype uses the existing model and texture assets;
no replacement texture artwork was needed.

### Router: the artist's connected-face tiles

On 2026-10-11 the artist refined the Router: a concept image of a 3x2 face (grey areas recessed)
and four 16x16 tiles in `textures/block/router/`: `router_base`, `router_side_hor`, `router_side_ver`
and `router_corner`. Use `router_style='tiles'` in the prototype (`face_tiles.py`).

- Each exposed face of a member starts from `router_base`: a core, 4-wide blue links to each edge and a
  quarter of a small core in each corner. Where a face edge lies on the body's outside, four rows of
  `router_side_hor` (top, bottom) or four columns of `router_side_ver` (left, right) are laid over it;
  where both edges of a corner do, the corner's 4x4 square of `router_corner` covers that.
- The result: links run between neighbouring cores, a small core forms where four members meet, and
  the frame stays on the body's outside. A 1x1 face composed this way equals `router.png` exactly.
- Relief comes from the art: texels in the two backing greys sit one unit (1/16) below the face and
  all others are flush, with real walls on every step. This reproduces the standalone model's relief
  exactly, and keeps the earlier rule that links stand 1/16 above the backing, level with the core.
- Texture edges follow Minecraft's face UVs, as the standalone model's do (top face: u along x, v along z).

The superseded raised-link composition (`router_style='raised'`) stays in the prototype for comparison.
Its reference renders are in `build/multiblock-devices/router-raised-v2/`:

- `router-raised-comparison.png`: previous appearance on the left, accepted raised links on the right.
- `router-raised-closeup.png`: low-angle comparison showing the raised sidewalls.
- `raised-router-mesh.json`, `router-raised-v2.blend`, `report.json`: geometry, editable scenes and asset hashes.

### Switch: the artist's connected-face tiles

On 2026-10-11 the artist refined the Switch the same way: a 3x2 concept and four tiles in
`textures/block/switch/` (`switch_base`, `switch_side_hor`, `switch_side_ver`, `switch_corner`; the
delivered `switch.png` is byte-identical to the existing one). Use `switch_style='tiles'`.

- Composition and relief follow the Router rules above. The Switch frame is three texels wide, so its
  strips and corner squares are three texels; the width is read from the side tiles' opaque band.
- Each member shows its purple module; edge contacts between neighbouring modules join into one
  contact, and where four members meet the quarter contacts form one contact in a recessed grey ring.
  The old junction grooves are gone, as in the concept.
- A 1x1 face composed this way equals `switch.png`, and a 3x2 face matches the concept in every texel.
- The relief equals the standalone model's except at the eight notches beside its edge contacts, which
  the standalone model sinks two units on the face's outer edge; the tiles keep that frame edge flush.
  The earlier connected Switch's 0.25/0.18/0.12-unit layering of module, backing and grooves is gone:
  module and backing are flush, as in the standalone block.

### Switch (superseded): white backing and original-style recessed contacts

This was the accepted Switch before the tiles; `switch_style='white-backed'` keeps it in the prototype. The field between purple modules must use the
original white body material, not the dark blue-gray infill from earlier previews. The current
implementation samples original texture pixel `(2, 2)`, RGB `(242, 242, 242)`. Retain the small junction
grooves and the purple modules.

Interior blue connections must match the perimeter contacts in color, texture and recessed structure.
The prototype reuses source elements 15/16/17 and their UVs, with the original 0/1/2-model-unit depth
profile. Cut openings through both backing layers and retain real pocket floors, sidewalls and recessed
necks. The cyan contact top reaches the outer plane; its surrounding stepped socket supplies the recess.
Do not flatten this assembly or simply paint a dark patch to simulate depth.

Reference: `build/multiblock-devices/switch-white-v4/`:

- `switch-white-comparison.png`: superseded dark backing on the left, accepted white backing on the right.
- `switch-white-closeup.png`: original single block beside the accepted 2x1x2 multiblock.
- `switch-white-grazing.png`: low-angle view of the contact/socket relief.
- `white-backed-mesh.json`, `switch-white-v4.blend`, `report.json`: geometry, editable scenes and asset hashes.

The in-game check found that a connected Switch member let the world show through the 1-unit
notches beside its edge contacts, at grazing angles; the offline renders hid it. The original block
closes each edge channel with its body and face slabs, which a connected member drops. The geometry
now adds the two inner channel walls behind each edge of a connected Switch member, textured from the
original faces on those planes. They are visible only through the notches; the accepted surfaces,
single blocks and the Router are unchanged. (The tile-built Switch has no such channels.)

## Implementation reference and reproduction

| File | Role for the next implementer |
|---|---|
| [geometry.py](../../tools/multiblock/geometry.py) | Cuboid validation and asset-backed quad generation; explicit accepted style arguments are required |
| [face_tiles.py](../../tools/multiblock/face_tiles.py) | Router and Switch faces composed from the artist's tiles, relief read from the art (`'tiles'` styles) |
| [switch_connectors.py](../../tools/multiblock/switch_connectors.py) | Superseded Switch: six-face socket transforms, backing cutouts, original contact geometry and UVs |
| [test_geometry.py](../../tools/multiblock/test_geometry.py) | Offline geometric invariants and regression checks, including a see-through ray test |
| [export_runtime_parity.py](../../tools/multiblock/export_runtime_parity.py) | Writes and checks the per-mask hashes the Java port must match |
| [render_router_links.py](../../tools/multiblock/render_router_links.py) | Superseded raised-link Router comparison and low-angle render |
| [render_switch_white.py](../../tools/multiblock/render_switch_white.py) | Accepted Switch comparison, original-block reference and low-angle render |
| [Preview README](../../tools/multiblock/README.md) | Tooling usage and historical variants |

Important: `build()` still defaults to `plain` for both styles so historical comparisons remain
reproducible. Do not mistake these defaults, `render.py`, or Switch `infill`/`edge-matched` outputs
for the approved final appearance. Select `router_style='tiles'` and `switch_style='tiles'`.

Source artwork lives under `common/src/main/resources/assets/ae2federation/`:
`models/block/router.json`, `models/block/switch.json`, `textures/block/router/router.png`,
`textures/block/switch/switch.png`, and the four tiles of each device beside them (`router_*.png`, `switch_*.png`). Exported quads use Minecraft coordinates measured in blocks and
normalized UVs. Renderer camera, staging and lighting are preview-only, not game implementation rules.

Run from the current worktree (render commands regenerate files in their respective output folders):

```bash
cd ae2-federation-multiblock   # the worktree root
python -m unittest discover -s tools/multiblock -v
blender --background --factory-startup --threads 8 --python tools/multiblock/render_router_links.py
blender --background --factory-startup --threads 8 --python tools/multiblock/render_switch_white.py
```

Expected: `Ran 21 tests` and `OK`; two Router renders followed by `ROUTER_RAISED_OK`; three Switch
renders followed by `SWITCH_WHITE_OK`. Each render report must show `source_assets_unchanged: true`.
The geometry tests use standard-library Python. Rendering uses the installed Blender executable and
its bundled Python (reviewed with Blender 5.2.1); do not install `bpy` into system Python.

The Blender renders above are offline previews, not game screenshots. They predate the channel walls
and the tiles and were not regenerated; the tiles were checked in game instead.

## Runtime implementation

| Class | Role |
|---|---|
| [DeviceStructure](../../common/src/main/java/space/controlnet/ae2federation/router/DeviceStructure.java) | AE2 `MBCalculator`-style cuboid search: grow the corners, check edge length, full interior and the one-block shell; an unloaded position gives `Unknown` |
| [DeviceStructureUpdater](../../common/src/main/java/space/controlnet/ae2federation/router/DeviceStructureUpdater.java) | Queues checks from block entity initialization and neighbour changes, checks each body once per level tick, writes `formed` and six connection booleans as blockstates |
| [RouterBlock](../../common/src/main/java/space/controlnet/ae2federation/router/RouterBlock.java) | Blockstate properties; right-clicking an unformed member shows why it does not form |
| [RouterFacePort](../../common/src/main/java/space/controlnet/ae2federation/domain/port/RouterFacePort.java) | A face of an unformed block binds nothing, so its Federation evidence is withdrawn; its boundary node is untouched |
| [DeviceStructureGeometry](../../common/src/main/java/space/controlnet/ae2federation/client/DeviceStructureGeometry.java) | Client-free Java port of `face_tiles.py` for both devices; built from the four tiles' pixels |
| [DeviceStructureBakedModel](../../common/src/main/java/space/controlnet/ae2federation/client/DeviceStructureBakedModel.java) | Wraps each connected blockstate's model; quads per connection mask, cached, rebuilt on resource reload |

- Checks never load chunks and only change blockstates, sent to clients without neighbour updates.
  Formed members name exactly their neighbours inside the same cuboid; an invalid candidate clears
  every member it reached.
- A single block or unformed member keeps the artist's standalone model (connection mask 0).
- Switch native ME networks stay separate Grids in every state, including while a body breaks or forms:
  interior faces link members through Federation only, and the boundary nodes are never rewired.
- Routers remain Federation-only. External faces keep their positions and orientations.
- Java and Python agree for all 64 masks of both devices (SHA-256 of canonical quads,
  `common/src/test/resources/multiblock/device-structure-parity.json`).

## Assembly contract

| Situation | Behavior |
|---|---|
| One block | A valid 1x1x1 device; retain the existing single-block construction option. |
| Same-type blocks sharing faces | Consider them together as one candidate structure. |
| Candidate completely fills its bounding cuboid | Form one logical device. |
| Hollow shell, missing interior block, L/T shape, or protrusion | The candidate does not form; do not silently select a valid subset. |
| Two same-type cuboids touch face to face | Re-evaluate their union. Form one device only if the union is a solid cuboid. |
| Blocks touch only along an edge or corner | Remain separate structures. |
| Router touches Switch | Remain two device types with a Federation connection; do not form a mixed multiblock. |
| A member is added or removed | Re-evaluate affected components. A remaining cuboid can form; an irregular remainder cannot. |
| Relevant chunks become unavailable | Keep the last validated state; check again on load; never force-load. A loaded member next to a removed one drops that connection. |

Examples: 1x1x4, 1x3x5, and 2x3x4 are valid shapes. Removing one end of a 1x1x4 leaves a valid
1x1x3. Removing a corner from a 2x2x2 leaves an invalid candidate. Putting one extra same-type block
against the middle of a completed cuboid invalidates the expanded candidate until it is completed
or the extra block is removed. Any edge longer than 8 blocks leaves the candidate unformed.

An invalid candidate suspends its connectivity: its faces disconnect, attached networks leave the
domain, and using a member names the reason (`ae2federation.structure.*`). Network Policies and
processing ownership are untouched; structural failure deletes no configuration. A loaded slice of
a cross-chunk body never becomes a smaller device while the rest is unloaded.

## Device semantics

- A formed Switch exposes its exterior block faces as independent ME/Federation connection points,
  preserving the existing role of a Switch. Combining its body must not connect those ME faces into
  a shared native Grid. One network touching several ports remains one domain member.
- A formed Router exposes Federation connections on its exterior, without ordinary ME attachment.
- Interior touching faces belong to the body, not external attachment ports.
- A formed multiblock is one logical device, while preserving physical port positions. A logical
  device is not the entire Federation domain; several devices may share a domain.
- Right-clicking any member of a formed body opens the existing whole-domain management screen.
  Right-clicking an unformed member shows its shape problem on the action bar.
- The registry was not rewritten: each member keeps its six physical ports, and interior faces link
  through Federation, so the body is one connected part of the domain.

## Why texture swapping alone is insufficient

Current production resources already contain artist-authored models for both devices. The Router
has 51 cuboid elements and separate body, corner, edge, face, and face-detail groups. Its body is
inset from 0..16 to 1..15. The Switch has 73 elements, a body/face arrangement, eight corner pieces,
and twelve grouped edge assemblies. Both use their own existing 16x16 texture.

Keeping these static models unchanged repeats raised borders at every block boundary. Removing
texture borders alone leaves the geometry seams. Removing geometry borders without extending
the inset surfaces can leave holes. The formed model therefore needs coordinated geometry and UV
changes, plus suppression of internal surfaces.

Blockbench groups are useful authoring metadata, but ordinary Minecraft JSON rendering does not
conditionally hide those groups. Runtime component selection needs an explicit model arrangement
or baked-model builder; it cannot depend on group names magically affecting rendering.

## Geometry placement reference: continuous frame, repeating panels

The accepted appearance retains each device's existing palette and recognizable panel details.
The following placement rules document the prototype; standalone/unformed runtime state handling
remains subject to the operational decisions below.

| Piece | Placement and connection rule |
|---|---|
| Outer corner | Appears at the eight outer corners of the entire body, not at each constituent block's corners. |
| Outer edge | Runs continuously along the body's twelve edges. Middle segments repeat; endpoint details meet corners. |
| Surface panel | Repeats across exterior faces; extends across internal block boundaries without repeated chassis borders. |
| Device detail | Optional per-block panel/core detail retained as deliberate modular surface detail, with bounded relief. |
| Interior surface | Omitted inside the same valid multiblock. |
| Standalone/unformed model | Uses the existing standalone asset; construction status is explained separately. |

The target is a continuous chassis containing readable modules. It does not require one giant center
emblem or a single stretched texture across each complete face. Repetition preserves texel density
on long or wide structures; corners and edge endings retain their original proportions.

For a 3x3 face, the four corner tiles carry the corresponding two outer border segments, the four
edge-middle tiles carry one border segment, and the center tile carries no outer border. All nine
tiles use compatible panel surfaces. For a one-block-wide face, opposite borders coexist; 1x1 and
1xN shapes must be designed explicitly rather than inferred from only the 3x3 example.

Repeated Router cores and Switch purple panels are now part of the accepted appearance.
Their visual connections do not imply native ME connections between Switch ports.

## Rendering approach (implemented)

The model is chunk-baked, like AE2 crafting CPUs: no block entity renderer and no animation.

1. The server validates the body and stores each member's membership as blockstate properties, which
   vanilla synchronizes and saves; no custom packet exists.
2. Connection bits name only neighbours inside the same validated cuboid, never mere same-type or
   same-domain adjacency.
3. Those bits choose exterior surfaces, outer corners, edge segments and panel extent on all six faces.
4. Quads are cached per device and mask (at most 64) and rebuilt with the baked models on resource reload.
5. A blockstate change re-meshes the chunk, so formation, breakup, load and unload refresh the client.

Prefer geometrically separate frame/panel pieces to a full arbitrary-shape connected-texture atlas.
The valid body is convex and rectangular, so its formed exterior does not need concave-corner art.
A dedicated interior, horizontal edge, vertical edge, and corner material family may be sufficient,
with device details layered through real geometry as needed. Rotating one edge texture is optional:
directional shading can justify distinct horizontal and vertical artwork.

The existing cable edge-mask utility is a useful UV/orientation reference, not a complete device
solution. Device surfaces have different geometry and must use valid multiblock membership.

Occlusion requires special care. Router/Switch currently hide no neighbor face because their models
have gaps. Do not globally switch them to full-cube occlusion to eliminate internal faces. Suppress
faces specifically within the same valid body and keep any exterior recesses and neighboring cable
contacts correct. Inspect collision/selection outlines together with rendered geometry.

## Original implementation sequence (historical)

Kept for context; all three steps are now implemented as described under
[Runtime implementation](#runtime-implementation).

The existing [CableBakedModel](../../common/src/main/java/space/controlnet/ae2federation/client/CableBakedModel.java)
provides a local model-data and cached-quad integration example. Reuse its general pattern, not its
cable-specific translucent render layer. AE2's crafting model is a source reference, not a public
base class available for direct subclassing.

1. Implement bounded solid-cuboid validation, synchronized membership and the accepted geometry in
   a chunk-baked model. Verify placement/removal, thin bodies, all six faces and resource reload in game.
2. Integrate logical-device coordination with exterior ports and suppress internal attachment faces.
   Preserve native ME isolation on Switches and Federation-only attachment on Routers. Existing physical
   registry nodes can potentially remain; logical aggregation need not force a registry rewrite.
3. Validate merge/split, save/load and partial chunk availability, including connection withdrawal and
   recovery. Resolve the open operational rules before implementing their dependent behavior.

The geometry transfer is expected to be less risky than lifecycle and topology changes.
[RouterBlockEntity](../../common/src/main/java/space/controlnet/ae2federation/router/RouterBlockEntity.java)
currently owns six per-block ports and publishes per-block domain evidence;
[RouterFacePort](../../common/src/main/java/space/controlnet/ae2federation/domain/port/RouterFacePort.java)
maintains boundary-node isolation and initialization ordering. Reassembly must preserve these invariants,
including transient states. A partially loaded structure must not accidentally become an unrelated
smaller device without a deliberate operational rule.

Minecraft lighting/AO, culling, chunk refresh and resource reload remain separate acceptance work.
Preserve the approved shape and material relationships while inspecting actual game screenshots;
Blender lighting is not a promise of identical in-game shading. Keep subsequent screenshots/renders
under `build/`. This sequence is a recommendation, not an authorization or claim of runtime completion.

## Verification

### Targets

- Valid thin/flat/full cuboids, invalid L/T shapes, holes, protrusions, and same-type merge/split.
- Different device types remain separate; touching only at an edge or corner does not merge.
- Placement/removal order, restart, and cross-chunk unload/reload produce consistent formation.
- Multiple independent ME networks attached to a large Switch remain independent native Grids.
- Routers continue refusing ordinary ME attachment; external Federation links and domain management work.
- One ME network attached at several ports is not duplicated in domain membership.
- Internal faces disappear without hiding visible neighboring geometry or creating holes.
- All six face orientations and one-block-thick dimensions have correct UVs, lighting, and borders.
- Formation, resize, breakup, and resource reload leave no stale client meshes.

### Commands

From the worktree root:

```bash
python -m unittest discover -s tools/multiblock -v
python3 tools/multiblock/export_runtime_parity.py --check
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:test \
  --tests 'space.controlnet.ae2federation.client.DeviceStructureGeometryTest' \
  --tests 'space.controlnet.ae2federation.router.DeviceStructureTest'
python3 tools/dev_gametests.py devicestructurelifecycle devicestructureshapes devicestructurekeepsports \
  devicestructuresuspendsswitch devicestructuresuspendsrouter devicestructureacrosschunks
python3 tools/dev_gametests.py --manifest
./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:check :neoforge-1.21.1:build
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1600x960x24 +extension GLX" \
  ./gradlew --no-daemon --dependency-verification=strict :neoforge-1.21.1:runStructureSceneClient --no-configuration-cache
```

Expected: `Ran 21 tests` / `OK`; `PARITY_FIXTURE_CURRENT`; 3 geometry and 6 cuboid-search JUnit tests
pass; the six GameTests and the whole manifest (220 on 2026-10-11) pass; `check build` succeeds
(556 JUnit tests on 2026-10-11).
After a geometry change, run `python3 tools/multiblock/export_runtime_parity.py` to rewrite the fixture.

The six GameTests cover formation and breakup order, thin/flat/full and invalid shapes, edge/corner
contact, Router/Switch separation, ME isolation with a shared domain, suspension of a Switch and a
Router candidate (all faces disconnect, networks leave the domain, the status names the reason, the
body recovers), and a body across a chunk border kept while the other chunk is unloaded.

### In-game screenshots

`runStructureSceneClient` builds a flat test world, photographs it and quits; it writes to
`build/multiblock-devices/ingame/` (`first-run/` when it creates the world, `reopened/` when the world
already exists, which checks save and re-entry). Move the saved world in `neoforge-1.21.1/run-structure-scene/saves/`
aside for a fresh first run. Views: overview; the 3x2 north faces of the Router and the Switch straight on;
Router and Switch 3x2x2 from front and back; grazing
views of both; floating 2x2x2 bodies from below; thin and flat bodies; an unformed L beside singles;
8x4x1 walls from 32 blocks; a Switch with a member removed and replaced; both devices after a resource
reload. These are real client screenshots (software OpenGL), unlike the Blender previews above.

The Router tiles were compared in game with the same scene before and after (`-PstructureSceneOut=`
`build/multiblock-devices/router-tiles-compare/before` and `.../after`):
`router-tiles-compare/router-before-after.png` pairs the Router views, and
`router-tiles-compare/concept-before-after-face-on.png` sets the concept beside both face-on shots.
The Switch tiles were compared the same way in `build/multiblock-devices/switch-tiles-compare/`
(`switch-before-after.png`, `concept-before-after-face-on.png`); its "before" already has the Router tiles.

## Not yet verified

- A remote client on a dedicated server (screenshots use the integrated server).
- Hardware GPU lighting and shader packs; screenshots use software OpenGL.
- Other resource packs replacing the tiles (the connected model reads the active `router_*.png` and
  `switch_*.png`; on a read failure, or a recessed texel on a face's outer edge, it logs a warning and
  keeps the standalone models).
- Performance with many large bodies changing at once.
- The right-click status message through a real player click; GameTests check the message itself.

The visual choice is settled: the artist's connected Router and Switch tiles, continuous outer frames
and repeated modules. Do not reopen these as unanswered art questions merely because the prototype
retains earlier comparison styles. Cable capacity and power
balancing remain separate deferred discussions.

## Evidence

[Source investigation](../../.omo/knowledges/multiblock-assembly-rendering-reference-2026-10-11.md)
records the pinned AE2 assembly/rendering classes and current project assets. This proposal adopts
their useful construction/rendering patterns without claiming direct reuse is already implemented.

[Visual acceptance and runtime assessment](../../.omo/knowledges/multiblock-visual-acceptance-and-runtime-scope-2026-10-11.md),
[raised Router evidence](../../.omo/knowledges/router-raised-links-2026-10-11.md) (superseded),
[Router tiles evidence](../../.omo/knowledges/router-artist-connected-tiles-2026-10-11.md),
[Switch tiles evidence](../../.omo/knowledges/switch-artist-connected-tiles-2026-10-11.md), and
[white Switch evidence](../../.omo/knowledges/switch-white-backing-2026-10-11.md) record the latest
accepted references and verification boundaries.
