# M coherent-stream load alternative (2026-10-11)

- User paused the requested Java renderer port to compare original M under different
  capacity-use settings. Complete this visual alternative first; no production Java
  changes were made during this task or the interrupted initial inspection.
- `tools/blender/beam_load_m.py` reuses the existing load response, palette, emission
  and red alarm, with the same 0/25/60/90/100/125 percent design inputs. The model
  retains M's straight axial carrier and smooth traveling waves; no helix or violet
  side channel is introduced. Wave sharpness 12 matches the original M study.
- `render_future_beams.py -- --study m-load --animate` writes a separate ignored
  `build/m-load-beams` study. Earlier helical and optical studies remain available.
- `compose_load_alternatives.py` verifies matched shell digests, placements, frames
  and animation length, then pairs helical versus M at full load and overload.
- 26 tests pass, including actual M mesh axial symmetry, straight-block seams across
  negative world coordinates, topology consistency across load, loop closure and
  shell envelope containment. Input ratios are artistic design settings, not actual
  mod telemetry. All renderer integration remains future work.
- Delivery verified: six M states rendered with 32 frames each; saved-blend verifier
  passes `FUTURE_VERIFY_OK`, compositors pass `FUTURE_MEDIA_OK` and
  `LOAD_ALTERNATIVES_OK`. Reviewed both final sheets. Normal-state mean blue in the
  fixed closeup ROI rises 101.35→106.48→118.47→130.80→135.38; overload is red-dominant.
  The synchronized alternative comparison MP4 is 1440×676, 32 frames / 3.2 seconds.
