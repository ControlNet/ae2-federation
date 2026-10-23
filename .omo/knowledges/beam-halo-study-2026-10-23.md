# Beam Former halo study, round 2

- Study tooling: `tools/blender/{beam_halo,render_beam_halo,compose_beam_halo,verify_beam_halo}.py`.
  Generated media and detailed notes: ignored `tools/blender/build/beam-halo/`. Proposal only; no Java or assets changed.
- Use Blender's bundled Python for this study, including unittest, composition and verification. No external packages.
- Assemble the four real `_xy` model JSONs with `(x,y)` block offsets. Far end rotates 180 degrees.
  Centre plate z=14/16, beam starts at z=1; retain the recess. Entire beam spans the six gap cells z=1..7.
- Actual rotated `corner` arms reach sqrt(82)/16=.565961571 blocks /9.055385138 voxels from centre.
  G/H's outer band radius .640..685 leaves >1 voxel of radial clearance, including polygon chords.
  Outer plane z=17.1/16 clears the tallest solid (17/16) by .1 voxel. I applies D's load/pulse rule there.
- Inner B/D/F rings originally crossed the cardinal b solids beneath visible surfaces. Clip their quads
  against the +/-1-voxel cardinal strips, leaving .0016 voxel clearance. Visible treatment stays interrupted.
  Clipped medium-phase vertex counts: B 960, D 192, F 192; G 1280, H 2240, I 256 per former; BASE 0.
- Round 1 lens-shift cameras were rejected. Round 2 uses actual perspective targeting: level 1.5 blocks beside
  axis, 3 in front, eye 1.62 above floor; oblique 45 degrees/4 horizontal blocks; close 30 degrees/2 blocks;
  pair orthographic scale 8.7. Final lenses: level 37 mm, oblique 40 mm, close 18 mm. No lens shift. Every concept shares the camera transforms.
- Source resolution 960x720, labels 960x778, Cycles 32 samples/denoise. All loops 32 frames at 10 fps.
- Standard view, exposure -.25, study-only imported plasma emission multiplier .65 on core and other layers.
  Preserve original beam RGB and geometry. Compare `calibration.png` against supplied real client screenshot.
  This remains a Blender additive-transparency approximation, not a claim of Minecraft GPU parity.
- Keep original runtime unpowered semantics separate from load: `fromLoad(0,true)`, not overload width.
  Unlinked emits zero beam/halo geometry. Full load blue, overload red. Both ends need halo emission independent
  of the beam renderer's single-positive-facing guard in any eventual Java implementation.
- Use `--replace` after any setting change; resume reuses files deliberately. Separate `--only` render processes
  can run concurrently; only B saves the shared scene. No agent delegation is required for subprocess rendering.
- Compare decompressed PNG IDAT data, not whole-file hashes (timestamp metadata varies). Verify all unlinked
  states match across concepts. Validate source dimensions and all MP4 frame counts/fps/duration.
- Sanitizer strips generated PNG text and local-machine paths from fixed-size Blender string fields using zstd;
  reopen and render the sanitized scene with packed textures afterward. Background Render Result.has_data
  is not reliable: save and reopen a PNG to check the real result instead.
- Focused halo/bundle/plasma suites avoid the unrelated missing `tools/blender/build/cable.json` fixture.

- Final framing was checked by projecting every actual near-former vertex: close and oblique retain about 2%
  vertical frame clearance. Use `--stills-only --views close oblique --replace` for those views independently.
- All 21 source loops have 32 distinct 960x720 frames. All 8 unlinked view/light combinations match across concepts.
- Activate the study scene before saving the Blender file so it opens usefully. After reopening, set
  `bpy.context.window.scene` and update the view layer before projecting vertices; otherwise an inactive
  scene can expose unevaluated world matrices. The actual sanitized scene passed a fresh reopen render.
- Final verification passed for 280 labelled stills, 672 source frames, 21 MP4/GIF pairs and all comparison
  movies. Focused suites passed 25 tests. Final output scan found zero local-machine identifiers.

## Round 3: portal comparison

- J/J2 use `beam_halo.portal()` with per-face material RGB/alpha/mode; the legacy `generate()` adapter
  preserves the existing pair API. All seven earlier treatments are unchanged.
- Inner frame faces are 10 voxels from centre; the actual centre plate is 6x6 voxels at z=14.
  Diagonal arms also top out at z=14. J's main plane is z=13.99 voxels so actual opaque arms occlude
  it; J2 is z=19, two voxels beyond the tallest cardinal clamps. Disc/streaks stay at z=14.01/14.02.
- Main white core r=7..8 voxels, cyan falloff to 6.4 and 8.6, line at 9, 16 dashes near 9.4,
  spill ends at 9.8 (inside frame). 5272 vertices per linked end, zero unlinked.
- `--device cpu|optix|cuda` defaults to CPU; GPU mode selects matching GPU devices only and fails
  if unavailable. `--preview --views close --only J J2` writes separate 480x360/8-sample day stills.
- Composition `--only BASE G H J J2 --suffix=-portal` names all derivatives separately, including
  per-concept labels/loops. Client calibration reference is optional. Full GPU commands and band
  parameters are at the top of ignored `tools/blender/build/beam-halo/NOTES.md`.
- Viewed the supplied third-party portal reference in place only. Exactly two CPU previews were
  rendered and inspected; they show clamped vs floating geometry. Full GPU media remain owner-run.
  Do not interpret historical round-2 validation as round-3 GPU/animation validation.
