# Parameterized optical load visualization (2026-10-11)

- User wants optical intensity to reflect usage/capacity and a red overload state.
  They explicitly clarified that gameplay capacity does not exist yet and this task
  is cable visual design only. Do not implement capacity, routing, telemetry, or
  gameplay overload mechanics as part of this study.
- Added `tools/blender/beam_load.py`: `response(ratio)`, `generate_for_load`,
  `palette_for_load`, and `emission_for_load` accept arbitrary finite nonnegative
  design-input ratios. Six named profiles preview 0/25/60/90/100/125 percent.
  These are intentionally synthetic design settings, not measured gameplay data.
- Optical motif combines N's fine cyan/violet helices with a continuous axial
  carrier and moving packets. As normal load rises, core radius .005→.012 blocks,
  halo radius .027→.072, emission .45→2, and pulse density 1→3 (idle amplitude zero).
  Crossfading packet trains preserves phase velocity .625 blocks/s in the preview.
- Exactly full remains normal cyan/violet; ratio > 1 uses red optical shoulders
  with a warm bright core. Geometry saturates at full-load dimensions. Gentle alarm
  modulation repeats every 1.6 s and varies emission 1.4–2.2 without blacking out.
  No electrical arc or solid object motif is used; original shell is shared.
- Added a frame-rate-independent visual smoothing helper with .25 s rising and
  .8 s falling time constants. The six rendered scenarios have fixed input ratios;
  they do not simulate changing measured telemetry. Future authoritative overload
  state must not be inferred from a smoothed renderer value.
- Saved Blender scene `preview_load_ratio` is metadata, not a live rebuild slider.
  Python generators accept continuously adjustable input; the exported shape-key
  scenes bake the six comparison cases. Minecraft shading and state transitions
  remain future integration work.
- 23 tests pass, including normal monotonicity, full-vs-overload boundary, bounded
  geometry, invalid inputs, nonblackout red alarm, and time-step-independent smoothing.
- Reproduce with `render_future_beams.py -- --study load --animate`, compose using
  `compose_future_beams.py --directory tools/blender/build/load-beams`, and inspect
  `tools/blender/build/load-beams/load-beams.blend`. All generated media are ignored.
- Render review: the same 850×120 pixel region at (40,230) in all closeups has mean
  blue 101.57, 107.48, 120.33, 132.31, 136.27 for 0/25/60/90/100% respectively.
  Overload has mean RGB (119.32,93.33,96.96), confirming visible red dominance in
  this view. These are output-image checks, not physical brightness measurements.
- Completed static and animated delivery: `FUTURE_BEAMS_OK: 6 load profiles`,
  `FUTURE_MEDIA_OK`, and reopened-blend `FUTURE_VERIFY_OK: 6 distinct mesh concepts`.
  Original shell identity/digests and unchanged source assets were checked. Saved
  material animation was checked against the expected emission at frame 16.
  All six states contain 32 real animation frames; the final sheet was inspected.
