# Device Blender study, 2026-10-26

- Owner requested Blender designs for Quantum Cube and Beam Converter, after rendering existing assets.
- Owner's explicit Quantum direction: **open frame, central high-energy sphere, cable centre beams converging
  on that sphere**. This supersedes the initial enclosed-shell suggestion. Do not revert to a recoloured Router.
- Current artist references are white edge rails, violet-grey dark grooves, cyan interfaces, violet ME surfaces,
  and stepped relief. The old v0.2 visual spec predates the deeper frames in current production art.
- Sources: `tools/blender/device_study.py`, `device_designs.py`, `verify_device_study.py`;
  media: `tools/visual/compose_device_study.py`. Design rationale and commands: `docs/art/device-study.md`.
- Generated delivery: `tools/blender/build/device-study-v2/all.blend`, reference/size/overview PNG sheets,
  Quantum MP4/GIF. Generated binaries are ignored. Earlier v1 previews are retained separately.
- Blender 5.2 compositor uses `scene.compositing_node_group`; glare type, threshold, strength and size are node
  inputs. Use a CompositorNodeTree with a group Image output. Blender's own Python handles bpy; Pillow runs only
  through the approved visual pixi environment.
- Saved-file verification must activate each scene and update its view layer before reading matrix_world;
  inactive scene matrices need dependency-graph evaluation after reopen.
- Verified 7 design scenes, 6 central axes on every Quantum body, inward animated packets, packed reference
  textures and unchanged production-resource/Java hashes. Export validates all 4096 cable connection states.
- Quantum visual sizes: 3/4/5, central port regions 1/2/3, sphere diameter 0.48 times body edge. Converter sizes:
  1x1x2, 2x2x4, 3x3x6. Geometry is editable; final game UV atlases and dynamic rendering remain future work.
- Quantum gameplay currently requires solid member cubes. The open appearance does not itself authorize changing
  assembly/collision rules. Current formed appearance comes from DeviceStructureBakedModel. Connection preview
  imports actual cable shells but uses proposed light, not shipped R / PLASMA animation.
