# Versioned art reviews

Each delivered version is a separate snapshot. Do not overwrite a version after
review. Save later changes under a new version directory, keeping models,
textures, preview renders, notes and hashes together.

| Version | Status | Purpose |
| --- | --- | --- |
| v01-baseline | Frozen reference | Appearance before the Blockbench redesign |
| v02-ae2-ceramic | Preserved comparison; Bridge/cable retained in V03 | Ceramic shell, compact ME contacts, revised functional faces and cable pulse |
| v03-ae2-blocks | Rejected visual direction; preserved | AE2-inspired block faces; unchanged V02 Bridge/cable |
| v04-ae2-quartz | White shell/outline approved; glyphs rejected | Quartz edge, thin dark seam, flat functional glyphs |
| v05-pattern-study | Rejected: isolated symbols and excess white | Fixed V04 shell with revised functional patterns |
| v06-dense-panels | Approved for Mod integration | Continuous colored functional surfaces; native AE2 foreground coverage |
| v07-isolated-cable | Approved and integrated into production | V06 plus shared transparent envelope and animated core for mask zero |

Production generation now imports V07 textures and its isolated-node correction.
These archives remain outside game resource roots; Blockbench projects, scripts
and review images are not included in the Mod JAR. See `docs/art/acceptance-v07.md`
for actual production-JAR verification.

Open either version with the project-scoped Blockbench launcher:

```sh
./tools/blockbench/launch.sh "$PWD/tools/blockbench/versions/v01-baseline/models/review_family.bbmodel"
./tools/blockbench/launch.sh "$PWD/tools/blockbench/versions/v02-ae2-ceramic/models/v02_family.bbmodel"
```

Open the latest candidate:

```sh
./tools/blockbench/launch.sh "$PWD/tools/blockbench/versions/v07-isolated-cable/models/v07_family.bbmodel"
```

Returning to a previous design means opening/copying that version, not resetting
the Git worktree. Later candidates should branch from a copy of the chosen model.
To verify that archived files have not changed:

```sh
python3 tools/blockbench/verify_art_versions.py
```

Expected: all model/texture references and manifest hashes pass.
