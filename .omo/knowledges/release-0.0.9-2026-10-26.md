# Release 0.0.9 (2026-10-26)

## Delivery

- Owner authorized publication after reviewing the performance/compatibility scope, with unfinished-art blocks excluded.
- Preparation `6eadaf74`, release-only exclusion `e9311d2f`, production verification record `ad94667b`.
- Master merge/tag target: `4254416dcad85b9d1b4c9247e22d6112fd4aff3b`, `v0.0.9`.
- Release workflow [40277000009](https://github.com/ControlNet/ae2-federation/actions/runs/40277000009) succeeded:
  build, platform preflight, all 16 isolated GameTest shards, GitHub, Modrinth and CurseForge publication.
- [GitHub Release](https://github.com/ControlNet/ae2-federation/releases/tag/v0.0.9) published at 06:06:26 UTC.
  Replaced generated notes with `docs/releases/0.0.9.md` and verified the body by read-back.
- [Modrinth version XiHS3aL5](https://modrinth.com/mod/ae2-federation/version/XiHS3aL5): uploaded and verified by CI.
  This host's unauthenticated project/version read returned HTTPError; CI's authenticated read-back passed.
- [CurseForge file 9330451](https://www.curseforge.com/minecraft/mc-mods/ae2-federation/files/9330451): upload succeeded.
  The public file list did not yet contain 0.0.9 immediately afterward; moderation/public visibility may lag.
- Binary `ae2federation-neoforge-1.21.1-0.0.9.jar`, 3,263,721 bytes, SHA-256
  `7afe5746f2b72279776f79b7cd4b9b1540eec0ccb934d69d7b55354f1870d4dc`.
  Local production-tested JAR, CI `release-dist` and downloaded GitHub binary are byte-identical. Downloaded binary
  and sources pass `sha256sum --check SHA256SUMS.txt`.

## Scope and verification

- Exclusion reuses 0.0.8's patch: unregister ME Federation Beam Converter, Creative ME Federation Beam Converter
  and ME Federation Quantum Cube; remove assets, recipes, creative/Jade/guide entries and associated tests; disable
  operating power. Remove Quantum from the compatibility playthrough's selectable topologies as well.
- Kept development implementations in Java; there are no `beam_converter` or `quantum_cube` resource entries in the
  release JAR. Development restores the excluded content after the release merge.
- 791 JUnit tests, 15 archive tests, release metadata tests, required-test selection tests, asset validation,
  and the complete shared GameTest batch (284/284, 62 seconds) passed locally. All 32 guide pages compiled without
  PageCompiler warnings. Secret and privacy checks passed.
- Release-code head `e9311d2f` passed Quick correctness 40276089846, Compatibility 40276089725 (34 profiles), and
  Playthrough 40276089793. Documentation head `ad94667b` also passed all three workflows.
- Actual production client/server cross-dimension test passed, including a terminal order through a nether Endpoint,
  real chunk unload, recovery, save/restart and a second terminal order. Final: 16 stone, zero cobblestone, empty
  endpoint subnet storage and no CPU job. Both server runs and both clients shut down normally.
- See `docs/testing/production-jar.md` for the public verification record and setup failures that were not counted.
  Local evidence is `build/release-0.0.9/` in the primary workspace.
- Release notes disclose statistics format 2 -> 3: old history and pinned statistics items reset. Native ME energy
  sharing remains available; disabling Federation operating power does not disable native network power.

## Development continuation

- Merged master back to dev (`4c8a5f08`), reverted only the exclusion (`d5f794eb`), then set 0.0.10-dev (`aa2c341b`).
- Verified `common`, platform source and the scenario manifest exactly match pre-release dev `9840c48d` after
  restoration; release notes and validation documentation remain. No issues or pull requests were created.
- An unrelated untracked `tools/blender/device_study.py` appeared during release work and was left untouched.
