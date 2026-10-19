# Blender asset preview (2026-10-11)

- Developed in a separate worktree on branch `design/blender-cable-preview` (at dev `9e42e0ca`, no commits of its own). That worktree has been removed; the tools now live in this repository.
- All preview code is in `tools/blender/`; generated outputs are in its ignored `build/` directory.
- See `tools/blender/README.md` for build, reload, verification, supported asset semantics and fidelity limitations.
- Current production Java cable geometry is compiled against a functional offline quad recorder. It validates 4096 connection combinations, exports 72 shells and 64 flow masks, and samples the production phase function. No Minecraft process or third-party Python environment is needed.
- Blender 5.2.1 LTS / JDK 21 available locally. Blender uses its bundled Python; standalone scripts use only Python's standard library.
- Resolve exact AE2 dependency from `gradle.properties` and cached jar, including inherited P2P geometry. All five blocks, two parts and four generated items appear in the main gallery. Separate atlases show 64 cable masks and 14 block orientations; a flow-only scene aids internal renderer design.
- Bridge item/part JSON each have two unresolved `#missing` references on buried surfaces. Importer omits only provably occluded faces and records them; visible missing assets fail. Production assets were not changed.
- On-demand Blender sidebar reload rereads assets, recompiles Java and rebuilds tool-owned scenes; it replaces manual edits inside generated scenes. Packed images must be loaded into fresh datablocks so edited PNG bytes are reflected.
- Minecraft shader parity, GPU performance, live world connectivity, item GUI transforms, and AE2 renderer-only overlays remain outside this offline preview's validation scope.
- Validation completed: 10 standard-library tests pass; actual Cycles showcase/detail/flow PNGs rendered; saved-file check prints `BLEND_VERIFY_OK`; session-local sidebar reload prints `SIDEBAR_RELOAD_OK` and passes the animation check afterwards. Fresh-image datablocks and disposal of superseded meshes prevent stale textures/material animation after reload.

## Center beam visibility correction

- User observed that the initial studio PNGs did not show the thin center beam. Geometry and PNG hashes match the main worktree: this was not a missing or stale geometry export.
- Controlled renders identified the high camera angle as the dominant problem: the center beam projects behind the shell's opaque upper front rail. Lowering light alone was insufficient; lowering the viewing angle exposed the real beam and its moving bright segments without increasing emission or changing geometry/texture.
- Added a fifth scene, `Cable Beam Inspection`, containing the actual connected network, camera `(5,-17,3)` looking at `(.5,-3.5,.5)`, and restrained lighting. `cable-detail.png` now renders this view. The original overview remains an asset gallery rather than a beam visibility diagnostic.
- Read the local Minecraft 1.21.1 emissive fragment shader and render states. Blender now also discards texture alpha below 0.1 before vertex-alpha multiplication; the game uses SRC_ALPHA / ONE_MINUS_SRC_ALPHA, no culling, color-only writes. Blender still does not claim GPU/shader parity.
- `verify_beam_render.py` uses real renders with flow enabled/disabled and projected world-space cable centers to check visible cyan contribution, then renders frame 49 to check animation. Artifacts and numeric evidence remain ignored under `tools/blender/build/`.
- Verified `BEAM_VISIBILITY_OK`: all four sampled straight-cable centers gain visible cyan (mean G/B increase 0.15–0.26 in loaded PNG samples); frame 49 changes 45,349 color/alpha channel samples above 0.01. Ten asset/geometry tests and the saved-blend verification also pass. Activate the inspection scene and update its view layer before projecting world coordinates: inactive cameras can retain unevaluated identity matrices outside render evaluation.
