# Standard-shader cable flow prototype

User requested an actual visual prototype using standard shaders after the
feasibility discussion. Implemented an opt-in BER interior while retaining the
V07 square glass/collars and item models. No GLSL, new dependencies, network
packets, or business behavior changes. Default is V07; client-only commands
`/ae2f_cable_preview on|off` switch modes and rebuild chunk meshes. Optional JVM
property `ae2federation.cableFlowPrototype=true` enables it at startup.

`CableFlowGeometry` caches ribbons for 64 masks. Two double-sided planes per
active axis form the interior. World-axis UVs repeat every four blocks and
advance with client game time/partial tick. Bounded coordinate/time phase avoids
large-coordinate precision loss and preserves periodicity on wrap. The mask
uses existing E/W/U/D/S/N bit order. Cross-sections stay within glass bounds.

`CableFlowRenderer` uses Minecraft's `entityTranslucentEmissive` render type
(standard shader, color-write, no culling), standard vertices and shared buffers.
`CableBakedModel` excludes old cutout core quads only for world preview rendering,
including null-render-type aggregate requests. The mode flag is volatile because
chunk meshing reads it off-thread. Registration stays in the client entrypoint.

Texture source is `tools/visual/cable_flow_texture.py`, called by the existing
generator, producing a reproducible 16x16 RGBA entity texture. V01–V07 archives
are unchanged. First in-game attempt was too thin; widened the texture's alpha
profile for the reviewed version. No conceptual/generated render was substituted
for the actual game captures.

Final build + verifySharedJarContent passed; 251 tests, zero failures/errors/skips,
including three geometry tests. Asset checks passed (267 models, 5,094 faces,
64 masks, reproducible) and archived hashes passed. Runtime used matching final
production JARs in copied server/client directories `build/visual-flow-prototype`.
Client joined the dedicated server; straight/elbow/six-way, mode switching,
day/night and F3+T checked. Original V07 installations were preserved.

Deliverables and exact commands: `docs/art/cable-flow-prototype/README.md`.
Native PNGs, 6.4-second MP4 and GIF are in that directory. MP4 has 128 decoded
frames with 128 distinct hashes. Stable reviewed JAR:
`build/visual-flow-prototype/ae2federation-cable-flow-prototype.jar`, SHA-256
`31f7d8c22a10c62755f784c9380949c52479698f2988f290bbaec771b6736450`.

Matched software-GL stress samples (2,048 cables +128 machines, 1280x720,
render distance 8, cap60): V07 mean52.20/P9564.50 ms; prototype
mean51.98/P9567.70 ms. Both upload/batch queues max0/0. Initial teleport sample
had86 pending uploads and is retained as excluded warm-up. No performance
equivalence or GPU-hardware claim. No optimization/shader mod tests performed.

Remaining prototype limits: junction pulse phases are axis-based, not routed;
view-dependent ribbon thickness; translucent ordering not broadly tested;
256-block BER limit leaves only enclosure beyond it; no distant interior LOD;
items intentionally retain V07. Motion is decorative, not measured traffic.

Git boundary follow-up: user explicitly reminded us to check gitignore. Added
root ignore rules scoped to prototype PNG/GIF/MP4 captures and artifact/profiles
JSON under `docs/art/cable-flow-prototype/`. Files remain locally available;
README explains that these links target local evidence, absent from fresh clones.
Source, tests, generators, production texture and documentation remain trackable.
Build/runtime outputs and pixi environments were already ignored. Existing
curated V07 screenshots and frozen art snapshots remain trackable.

## Junction refinement after user feedback

User reported unnatural crossings and bends. The initial implementation extended
each axis strip through the center, including coplanar overlapping portions and
unrelated pulse phases. Replaced perpendicular degree-two connections with a
four-facet quarter bend (radius 3/16 block), plus connected outer arms. Branches
of degree three or more now trim arms at a 4/16-wide shared center, with exactly
one patch per coordinate plane. Center and arm-end opacity is 0.6; a first
candidate at full opacity still looked too block-like in the client.

Animated arm highlights crossfade into a steady dim base at the connection.
The curve itself uses that base; it does not route a discrete animated packet
around the corner. Straight geometry and its world-coordinate phase are retained.
The texture is now a 16x32 two-tile sheet: original flow pixels above, dim shared
hub below. Validation requires all four hub edges to match the base profile.
No new shader or render type is introduced; geometry remains cached per mask.

Latest artifact: `build/visual-flow-prototype/ae2federation-cable-junction-refined.jar`,
SHA-256 `6681237e20b52efa29af120aaafd96730aaecdb02c9e11eee95bc61570242d8a`.
Original prototype and intermediate junction JARs are preserved separately.
Build + shared content checks pass; 253 tests with zero failures/errors/skips,
including all twelve elbows, unique branch center planes, all-mask glass-union
containment, connected boundaries and phase continuity. Asset reproducibility
and archived art hashes pass. No new performance or shader-mod compatibility
claim; prior performance samples predate this geometry change.

Captured actual old/new client comparisons for elbow, T, and six-way nodes.
Latest local evidence: `15-junction-final.png`, `16-junction-final.gif/.mp4`,
`17-tee-final.png`, `18-elbow-final.png`, `19-elbow-final.gif/.mp4` under
`docs/art/cable-flow-prototype/`. All remain ignored by existing scoped rules.
Latest client/server JAR hashes match; saved-world join and F3+T passed with no
ERROR/Exception entries in the reviewed client log. Early captures 07/08 had
poor camera framing and are not the published close comparisons.

Camera discovery: default server-console `tp ... facing` aimed from feet,
placing the target below the screen center at close range. Explicit yaw/pitch
146.976/25.152 aims correctly from the camera at these positions: elbow
(30.8,3,12.5), T (40.8,3,12.5), six-way (60.8,3,37.5). Original before-close
screenshots are 09/10/11; static comparison images may show different pulse phases.
