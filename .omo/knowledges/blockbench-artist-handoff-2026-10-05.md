# Blockbench artist handoff inventory

Verified on 2026-10-05 with `git ls-files`, `git status`, and workspace documentation.

- Application executable: `$HOME/.local/bin/blockbench` (user-local, outside the repository).
- Maintained workspace: `tools/blockbench/`; entry documentation: `tools/blockbench/README.md`.
- Approved artwork baseline: `tools/blockbench/versions/v07-isolated-cable/`, including editable models,
  textures, renders, sources and manifests. Production integration is recorded in `visual-assets-v07.md`.
- `projects/` contains original imports, not the latest approved artwork. The launcher defaults to their old overview;
  explicitly pass `versions/v07-isolated-cable/models/v07_family.bbmodel` to preview the approved family.
- Git tracks 465 workspace files, including 137 `.bbmodel` files across original imports and historical versions.
  V07 contains 49 tracked files. No tracked modifications or nonignored untracked files in this workspace at inspection.
- `tools/blockbench/.local/` is ignored: editor profile, downloaded plugin, scratch work and fresh verification captures.
- Artist can use ordinary Blockbench to open versioned `.bbmodel` projects; project MCP tooling is for automation.
- Preserve frozen archives. New artwork belongs in a new version (V08 or later), followed by deliberate production
  generator/resource integration. Editing a Blockbench project alone does not update the game assets.
- Include runtime texture directories when scoping item icons or newer effects beyond the frozen block artwork:
  `common/src/main/resources/assets/ae2federation/textures/`.
