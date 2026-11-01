# Blender laser/Quantum artwork runtime integration

The owner approved the improved design direction and requested Minecraft implementation. The preceding FE discussion
restricted input to the converter rear. Open laser A and full open Quantum frame with teal layered fragments are used.

- `RefinedDeviceBakedModel` imports exported Blender polygons, clips them to each member with interpolated UVs and
  caches BakedQuads. Static structure art stays in chunk meshes. `RefinedDeviceRenderer` draws only the moving core
  and carriers from the lowest member. `RefinedDeviceBody` derives dimensions from synchronized connection flags.
- Three converter sizes (1x1x2, 2x2x4, 3x3x6), all six facings; Quantum edges 3,4,5. The initial unformed/item miniature approach was rejected; see `device-standalone-creative-2026-10-26.md`
  for the native cube replacement and creative purple distinction. Construction/collision semantics remain full-volume multiblocks; openness is visual.
- Atlas and exporter: `tools/visual/pack_device_atlas.py`, `tools/blender/export_refined_devices.py`. Actual shipped
  polygons and UVs come from the reviewed `.blend`. Nine JSON meshes plus surface/emission atlases are runtime data.
- Export must explicitly exclude studio props. A first preview accidentally included the studio floor in the core;
  fixed by Core-name selection and material allowlist. `RefinedDeviceResourcesTest` guards finite core bounds/UVs.
- Static planes need per-cell clipping for cross-chunk culling. Preserve UV interpolation. Planar grid-boundary faces
  must have exactly one owner. `RefinedDeviceMeshTest` checks area/UV continuity, six rotations and shared ownership.
- Use BundleBeam(..., side=3) at native scale, not a 2x2 carrier uniformly scaled 1.5: 3x3 core is only 4/3, glow 1.5.
- Open devices need empty occlusion, zero light blocking, skylight propagation and full shade brightness. Merely
  `noOcclusion` leaves old full-cube shading behaviour. Dynamic render bounds cover the complete structure.
- FE capabilities are rear exterior only; side=null is rejected. Direction-bound input objects recheck the current
  state so cached capabilities stop accepting after rotation/removal. The whole body still shares its buffer.
- The showcase fixture must feed its top/rear member for a downward converter, not the bottom/front member.
- Old violet/amber placeholder PNGs, placeholder generator and Router-copy Quantum geometry test were retired.
- Validation: strict build, 797 unit tests, asset validator, 15/15 relevant GameTests in one batch. New GameTest
  `beamconverterrearportseveryorientation` covers 18 combinations and is in `tests/scenarios/manifest.json`.
- Client run: `:neoforge-1.21.1:runRefinedDeviceClient -PrefinedDeviceOut=build/refined-devices-runtime-v3`.
  Testmod-only private flat world, real connections, vertical variants, cross-chunk large models, resource reload,
  break/reform, reopen. Earlier v2 scene had overlapping large converter fixtures; v3 separates rows and fails the
  client if the server formation report does not pass. Do not cite v1/v2 screenshots as final validation.
- Detailed commands, expected signals and boundaries: `docs/art/refined-device-runtime.md`. Initial integration
  committed as `95270da1`; no PR created.

Final v3 first-run and reopened clients both reported `REFINED_DEVICE_SCENE_OK` and exited successfully.
Evidence: `.omo/evidence/refined-devices-runtime-2026-10-26/verification.json`; screenshots in
`build/refined-devices-runtime-v3/{first-run,reopened}/`. Native meshes/atlases in the built JAR match source bytes.
