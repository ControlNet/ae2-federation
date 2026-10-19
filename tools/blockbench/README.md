# AE2 Federation Blockbench workspace

All project-specific Blockbench models, versioned textures, renders, tooling,
documentation and local editor state live in this directory.

| Location | Purpose |
| --- | --- |
| `projects/` | The artist's dense Federation Cable models (core, straight, connectors, display) |
| `versions/v01-baseline/` | Frozen baseline and comparison renders |
| `versions/v02-ae2-ceramic/` | Frozen ceramic design candidate and actual Blockbench renders |
| `versions/v03-ae2-blocks/` | AE2-inspired block refinement; retained V02 Bridge/cable |
| `versions/v04-ae2-quartz/` | Quartz edge and flat glyph candidate following V03 rejection |
| `versions/v05-pattern-study/` | Revised glyphs within the approved V04 white shell/outline |
| `versions/v06-dense-panels/` | V04-based full colored panels following V05 rejection |
| `versions/v07-isolated-cable/` | Approved V06 series plus isolated cable continuity correction |
| `versions/README.md` | Version policy and comparison commands |
| `docs/` | The artist's cable design notes (`cable_design.md`) |
| `*.py`, `*.mjs`, `launch.sh` | Import, design, rendering and editor-check tools |
| `.local/` | Git-ignored plugin, isolated profile, working files and fresh verification results |

From the repository root:

```sh
./tools/blockbench/launch.sh "$PWD/tools/blockbench/versions/v07-isolated-cable/models/v07_family.bbmodel"
```

Expected: the candidate opens in Blockbench. The version verifiers (`verify_art_versions.py`, `verify_v0x.py`) were
removed on 2026-10-09; each version's `manifest.json` still lists its hashes. With the project editor running, reload
every candidate and check texture references:

```sh
node tools/blockbench/check_v07_editor.mjs tools/blockbench/versions/v07-isolated-cable
```

Expected: all 15 projects load without missing textures. Fresh screenshots and
reports go to `.local/verification/v07-editor/`, never into a frozen version.
These checks validate Blockbench assets, not Minecraft runtime acceptance.

## Relocation and version preservation

The workspace was consolidated on 2026-09-26:

| Previous location | Current location |
| --- | --- |
| `art/blockbench/` | `tools/blockbench/projects/` |
| `art/versions/` | `tools/blockbench/versions/` |
| `docs/art/blockbench/` | `tools/blockbench/docs/` |

Version directories were moved byte-for-byte, including manifests. Their
archived READMEs and source copies retain historical paths as provenance; use
the mapping above and current tools for execution. Do not edit archived files
or regenerate their hashes to hide modifications. Create v08 or later for new
artwork. The default launcher opens the artist's `projects/cable_display.bbmodel`.

The project MCP discovery entry must remain at `.codex/config.toml`; it points
to the editor's loopback service. Required `.omo/knowledges/` entries are only
pointers into this workspace. The installed Blockbench application remains a
user-local executable; MCP registration and editor state remain project-local.
Production game assets and their independent `tools/visual/` generator stay in
their existing locations.
