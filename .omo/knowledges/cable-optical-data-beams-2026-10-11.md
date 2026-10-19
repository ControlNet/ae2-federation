# Optical data-beam design constraint and study (2026-10-11)

- User clarified that Federation transports data via light beams. Keep every future
  internal-renderer proposal within that optical language, while retaining the
  finalized shell. Plasma, electricity, orbiting crystals, and mechanical objects
  should not take over the visual identity.
- Implemented four Blender geometric studies in `tools/blender/optical_beams.py`:
  M Coherent Stream, N Phase Interference, O Optical Multiplex, P Burst Encoding.
  All have continuous carriers. N adds narrow cyan/violet optical helices, O adds
  four parallel channels, and P carries repeating groups of three short light pulses.
  These are artistic metaphors, not physical optics or measured traffic/protocols.
- Reused the existing scene builder with `--study optical` and an explicitly separate
  `--output tools/blender/build/optical-beams`. Earlier E–L files remain available.
  Static views and 32-frame synchronized animation share original shell objects,
  materials, lights, cameras and exposure. Palette emission strength stays at 1.
- Soft envelopes use nested translucent swept surfaces, without internal end caps
  and without compositor bloom. This is a Blender approximation, not Minecraft
  additive framebuffer blending. Java integration remains necessary.
- Straight profiles fit within radius 0.086 blocks. Global spatial envelopes make
  neighboring straight-block boundary vertices identical, including negative world
  coordinates. Secondary lanes converge at the demonstrated T junction, but the
  study does not implement arbitrary graph routing, elbow phase continuity or LOD.
- Tests: 18 total pass, including new optical seam, containment, cyclic topology and
  positive-direction envelope propagation checks. Generated renders and blend files
  are ignored by `tools/blender/.gitignore`.
- Static visual inspection finds N most distinctive; O suggests parallel optical
  communication; P makes temporal packet groups particularly easy to recognize.
  M is a restrained baseline for a continuously illuminated carrier.
- Delivery verification completed: `FUTURE_BEAMS_OK: 4 optical profiles`,
  `FUTURE_MEDIA_OK`, and `FUTURE_VERIFY_OK: 4 distinct mesh concepts`. Reopened
  Blender verification checks exact original shell identities/digests, the asset
  hash report, evaluated shape-key motion, and 32 real rendered frames per profile.
  Reviewed all four closeups and the composed contact sheet. Generated media includes
  a 3.2-second synchronized MP4/GIF and wide/close static comparison sheets.
