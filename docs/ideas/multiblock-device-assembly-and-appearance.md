# Multiblock device assembly and appearance

Discussion date: 2026-10-11.
Status: handoff for the accepted visual direction and solid-cuboid requirement. The executable offline
prototype is complete for this visual review; Minecraft runtime integration has not started.

## Handoff: start here

This page is the entry point for the next implementer. The user accepted the latest appearance after
reviewing the raised Router connectors and white-backed Switch. Carry this appearance forward;
previous dark Switch infill and flat Router links are historical comparisons, not alternative final designs.
The operational proposals below are still proposals: visual acceptance does not approve failure,
merge/split, size-limit, or chunk-loading rules.

Work is in branch `design/multiblock-devices`, at the current worktree
the sibling `ae2-federation-multiblock` worktree. Continue in a worktree. Generated renders belong
under that worktree's `build/`. Local generated files may not accompany a checkout; the reproduction
commands below regenerate them from repository assets. This page does not imply that the working-tree
changes have been committed or merged.

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

### Router: raised blue connections

Use `router_style='raised'` in the prototype. The middle blue links must be physically raised above
the backing. Extend the original four-model-unit-wide core tabs toward adjacent members. Their top
is level with the original core top and one model unit (1/16 block) above the backing. Keep actual
sidewalls, original tab UVs and the original core/frame. Do not reproduce the earlier inset/flat links.

Reference: `build/multiblock-devices/router-raised-v2/`:

- `router-raised-comparison.png`: previous appearance on the left, accepted raised links on the right.
- `router-raised-closeup.png`: low-angle comparison showing the raised sidewalls.
- `raised-router-mesh.json`, `router-raised-v2.blend`, `report.json`: geometry, editable scenes and asset hashes.

### Switch: white backing and original-style recessed contacts

Use `switch_style='white-backed'` in the prototype. The field between purple modules must use the
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

## Implementation reference and reproduction

| File | Role for the next implementer |
|---|---|
| [geometry.py](../../tools/multiblock/geometry.py) | Cuboid validation and asset-backed quad generation; explicit accepted style arguments are required |
| [switch_connectors.py](../../tools/multiblock/switch_connectors.py) | Six-face socket transforms, backing cutouts, original contact geometry and UVs |
| [test_geometry.py](../../tools/multiblock/test_geometry.py) | Offline geometric invariants and regression checks |
| [render_router_links.py](../../tools/multiblock/render_router_links.py) | Accepted Router comparison and low-angle render |
| [render_switch_white.py](../../tools/multiblock/render_switch_white.py) | Accepted Switch comparison, original-block reference and low-angle render |
| [Preview README](../../tools/multiblock/README.md) | Tooling usage and historical variants |

Important: `build()` still defaults to `plain` for both styles so historical comparisons remain
reproducible. Do not mistake these defaults, `render.py`, or Switch `infill`/`edge-matched` outputs
for the approved final appearance. Select `router_style='raised'` and `switch_style='white-backed'`.

Source artwork lives under `common/src/main/resources/assets/ae2federation/`:
`models/block/router.json`, `models/block/switch.json`, `textures/block/router/router.png`, and
`textures/block/switch/switch.png`. Exported quads use Minecraft coordinates measured in blocks and
normalized UVs. Renderer camera, staging and lighting are preview-only, not game implementation rules.

Run from the current worktree (render commands regenerate files in their respective output folders):

```bash
cd ae2-federation-multiblock   # the worktree root
python -m unittest discover -s tools/multiblock -v
blender --background --factory-startup --threads 8 --python tools/multiblock/render_router_links.py
blender --background --factory-startup --threads 8 --python tools/multiblock/render_switch_white.py
```

Expected: `Ran 16 tests` and `OK`; two Router renders followed by `ROUTER_RAISED_OK`; three Switch
renders followed by `SWITCH_WHITE_OK`. Each render report must show `source_assets_unchanged: true`.
The geometry tests use standard-library Python. Rendering uses the installed Blender executable and
its bundled Python (reviewed with Blender 5.2.1); do not install `bpy` into system Python.

Verified so far: the 16 tests, visual inspection of the accepted renders, and source-asset hash checks.
A separate old/new mesh comparison covered 1x1x1, 1x1x4, 2x1x2 and 3x3x3 Switches and confirmed that
V4 changes only infill UVs, preserving V3 contact geometry. The raised Router was unchanged by V4.
These are offline results. No Minecraft renderer, world scanning, structure lifecycle, network-port
aggregation, or client synchronization has been implemented or verified by these tools.

## Proposed assembly contract

| Situation | Recommended behavior |
|---|---|
| One block | A valid 1x1x1 device; retain the existing single-block construction option. |
| Same-type blocks sharing faces | Consider them together as one candidate structure. |
| Candidate completely fills its bounding cuboid | Form one logical device. |
| Hollow shell, missing interior block, L/T shape, or protrusion | The candidate does not form; do not silently select a valid subset. |
| Two same-type cuboids touch face to face | Re-evaluate their union. Form one device only if the union is a solid cuboid. |
| Blocks touch only along an edge or corner | Remain separate structures. |
| Router touches Switch | Remain two device types with a Federation connection; do not form a mixed multiblock. |
| A member is added or removed | Re-evaluate affected components. A remaining cuboid can form; an irregular remainder cannot. |
| Relevant chunks become unavailable | Treat completeness as unconfirmed and withdraw affected operational connections until revalidated; do not force-load chunks. |

Examples: 1x1x4, 1x3x5, and 2x3x4 are valid shapes. Removing one end of a 1x1x4 leaves a valid
1x1x3. Removing a corner from a 2x2x2 leaves an invalid candidate. Putting one extra same-type block
against the middle of a completed cuboid invalidates the expanded candidate until it is completed
or the extra block is removed. These are examples of the proposed rules, not selected size limits.

The recommended invalid-state behavior is to suspend that candidate's device connectivity, while
allowing inspection of the construction error. Falling back to individually operating blocks would
make the cuboid requirement mostly cosmetic. This operational consequence needs user agreement.
Preserve existing network Policies and processing ownership; structural failure must not delete
configuration or invent successful deliveries. Existing recovery contracts still apply.

Choose supported dimensions and a bounded validation strategy before implementation. Do not import
AE2's dimension checks, crafting-storage requirement, channel use, or energy behavior automatically.
Chunk loading and partial structure knowledge need explicit tests: a loaded slice must not silently
become a smaller operational device while an existing cross-chunk structure is incomplete.

## Proposed device semantics

- A formed Switch exposes its exterior block faces as independent ME/Federation connection points,
  preserving the existing role of a Switch. Combining its body must not connect those ME faces into
  a shared native Grid. One network touching several ports remains one domain member.
- A formed Router exposes Federation connections on its exterior, without ordinary ME attachment.
- Interior touching faces belong to the body, not external attachment ports.
- A formed multiblock is one logical device, while preserving physical port positions. A logical
  device is not the entire Federation domain; several devices may share a domain.
- Right-clicking any member retains the existing whole-domain management scope when operational.
  An invalid structure should explain its shape problem even when no operational domain is available.
- Logical-device aggregation does not require immediately collapsing all physical registry nodes.
  Keep the gameplay identity separate from an implementation choice about topology storage.

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

## Proposed rendering approach

Use a chunk-baked connected model, following the same general approach as AE2 crafting CPUs and
the project's existing `CableBakedModel`. Static chassis geometry does not need a per-frame block
entity renderer. Animation is not required to validate this phase.

1. The server validates a structure and publishes enough formed-state and membership/bounds data
   for consistent client rendering. Exact packet and state representation remain implementation choices.
2. Derive each member's connections to the same formed logical body. A neighboring identical block,
   or a neighboring device in the same Federation domain, is not sufficient evidence by itself.
3. Use those directions to choose exterior surfaces, outer corners, edge segments, and panel extent.
   Keep per-face texture orientation explicit for all six faces.
4. Cache reusable baked geometry by device type and local connection pattern, invalidating it on
   resource reload. With six connection bits there are at most 64 direction masks before additional
   visual states; most valid cuboid placements use fewer patterns.
5. Refresh model data and affected chunk meshes on formation, breakup, resize, load, and unload.
   The server and client must not retain different borders after a change.

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

## Suggested runtime implementation sequence (not yet selected)

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

## Verification targets for a future implementation

- Valid thin/flat/full cuboids, invalid L/T shapes, holes, protrusions, and same-type merge/split.
- Different device types remain separate; touching only at an edge or corner does not merge.
- Placement/removal order, restart, and cross-chunk unload/reload produce consistent formation.
- Multiple independent ME networks attached to a large Switch remain independent native Grids.
- Routers continue refusing ordinary ME attachment; external Federation links and domain management work.
- One ME network attached at several ports is not duplicated in domain membership.
- Internal faces disappear without hiding visible neighboring geometry or creating holes.
- All six face orientations and one-block-thick dimensions have correct UVs, lighting, and borders.
- Formation, resize, breakup, and resource reload leave no stale client meshes.

Select runtime verification commands from [testing commands](../testing/commands.md) when game
integration exists. The reproduction section above is the current offline verification entry point.

## Decisions still required before runtime specification

1. **Dimensions and validation budget:** supported minimum/maximum dimensions, volume limits and bounded
   revalidation work. Prototype support for a shape does not select its gameplay limits.
2. **Grouping and merge/split:** accept or revise the proposed same-type connected-component rule,
   single-block validity, automatic merging, and treatment of a protruding member.
3. **Invalid structures:** decide whether they stop connectivity, retain single-block operation, or use
   another behavior, and how the construction error is communicated.
4. **Partial chunk availability:** define suspension and recovery without force-loading; distinguish
   unloaded members from blocks that were removed.
5. **Logical identity and management:** settle member/controller ownership, port/configuration persistence
   and management access during rebuilding. Preserve existing Router/Switch role and isolation contracts.

The visual choice is settled: raised Router links, white Switch backing, recessed original-style
Switch contacts, continuous outer frames and repeated modules. Do not reopen these as unanswered art
questions merely because the prototype retains earlier comparison styles. Cable capacity and power
balancing remain separate deferred discussions.

## Evidence

[Source investigation](../../.omo/knowledges/multiblock-assembly-rendering-reference-2026-10-11.md)
records the pinned AE2 assembly/rendering classes and current project assets. This proposal adopts
their useful construction/rendering patterns without claiming direct reuse is already implemented.

[Visual acceptance and runtime assessment](../../.omo/knowledges/multiblock-visual-acceptance-and-runtime-scope-2026-10-11.md),
[raised Router evidence](../../.omo/knowledges/router-raised-links-2026-10-11.md), and
[white Switch evidence](../../.omo/knowledges/switch-white-backing-2026-10-11.md) record the latest
accepted references and verification boundaries.
