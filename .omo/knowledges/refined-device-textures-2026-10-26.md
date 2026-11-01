# Federation device texture refinement

- Owner feedback: earlier converters looked like white blockouts, and the Quantum white sphere was rejected.
  The selected core direction is a layered or fragmented sphere. Federation accents must stay blue-green.
- Use full pixel artwork and UVs. Sampling a few source texels as flat materials does not reproduce the artist's
  texture detail. Current mechanical density is exactly 16 pixels per block, nearest sampling.
- Source collar colours: teal #6caebb, dark teal #477e93, highlight #a2dce2. Dark core shades derive from teal.
  Do not introduce a purple core or overexpose it to white.
- Current delivery: `tools/blender/build/refined-devices-final/refined.blend`, 12 scenes, packed images.
  Two textured laser variants, open Quantum frame, 22 outer plus 12 inner curved fragments with counterrotation,
  six cable connection studies, artist comparison and mechanical close-up.
- Pipeline: `refined_device_geometry.py`, `build_refined_device_textures.py`, `render_refined_devices.py`,
  `verify_refined_devices.py`, `compose_refined_devices.py`. Pillow runs in the approved tools/visual pixi env.
- Reopened file verification passed: 4164 mechanical UV faces, 34 fragments per core, six cable cases;
  production assets unchanged. Core MP4 is 720x720, 30 frames, four seconds.
- Ordinary and bundled cable references retain production artwork. New device carrier geometry matches their
  core radius/axis and uses source teal. Blender rendering does not verify Minecraft transparency or animation.
- See `docs/art/refined-device-art.md` for exact reproduction commands and scope. Production placeholder resources
  remain untouched; these are editable design proposals awaiting aesthetic evaluation and runtime integration.
