# Blockbench workspace

2026-09-26: User requested installation and an editable AE2 Federation project.
Installed official Blockbench 5.2.1 x86_64 under ~/.local/opt/blockbench/5.2.1;
verified GitHub asset SHA-256. ~/.local/bin/blockbench launches the extracted
AppImage without needing FUSE or disabling the sandbox. Desktop app and project
shortcuts were registered. No plugins installed.

tools/blockbench/projects has 14 portable embedded-texture bbmodels: a Free Model overview
and 13 Java Block/Item device/representative cable projects. These are current v1
baselines, not new approved artwork. tools/blockbench/create_projects.py uses
only stdlib and refuses to overwrite existing projects. Production assets and
their generator remain unchanged. Do not make the overview a production block;
do not lose NeoForge layer routing or AE2 Part axes when integrating later art.

Actually loaded all files in Blockbench 5.2.1 over isolated Xvfb/CDP: overview has
155 cubes, 43 groups, 20 textures; zero texture load errors or missing face
references. Both stream strips recognized as 16-frame animations. Editor glass
sorting differs from Minecraft; toggle glass groups for internal inspection.
See tools/blockbench/projects/README.md and tools/blockbench/docs/installation.md.

Concurrent unrelated identity/provider/policy code changes were present and
were left untouched. No commits or pushes were requested for this task.

## MCP plugin evaluation

User proposed https://github.com/jasonjgardner/blockbench-mcp-plugin . Inspected
upstream main README, index.ts, package.json, headless/README.md, and desktop
project/cube/camera/history/network tool implementations. At inspection,
package version is 1.9.2; desktop manifest requires Blockbench >=5.0.0, so the
installed 5.2.1 satisfies the declared requirement (not yet runtime tested).
Desktop HTTP endpoint defaults to localhost:3000/bb-mcp. Tools include
get_project_info, place_cube, modify_cube, set_camera_angle, capture_screenshot,
capture_app_screenshot, undo/redo and save_checkpoint. Prefer the desktop mode
for visible iterative review; headless is separate stdio and its renderer needs
Node >=23.6 plus GPU. Existing bbmodels remain usable.

Do not enable AI Scratchpad for Java production geometry: it relaxes format
limits. The network implementation currently calls httpServer.listen(port)
without a host, so do not assume the README localhost client URL restricts
server binding; verify and restrict listener scope before actual setup.
No plugin installation or MCP client configuration was performed during this
evaluation. The existing editor still only has the previously installed app.

## MCP installed, project scope only

User explicitly required no user-space MCP registration. Added only repository
.codex/config.toml, server ae2f_blockbench at http://127.0.0.1:31337/bb-mcp.
Normal ~/.codex/config.toml hash remained unchanged; `codex mcp get` succeeds
inside repo and reports missing server from /tmp.

Installed pinned upstream plugin 1.9.2 under tools/blockbench/.local (gitignored),
published artifact commit 657e0af81883f338b8478b6adf07b3ca8907197d. Installer checks
SHA-256 and applies a single bind-host patch; verified listener 127.0.0.1 only.
launch.sh uses Blockbench --userData with the repository-local profile. MCP is
not installed in the normal user Blockbench profile. Bootstrap uses temporary
CDP 9339, while routine launch has no debug port. Project plugin permissions are
process/net/fs. CDN prompt downloads and AI Scratchpad off, AI attribution on.

Real MCP smoke test passed twice, including normal restart: provider-copy load,
geometry modify, undo/redo, single-pixel paint and undo, offscreen capture,
export/save/reopen with identical geometry and texture data, original untouched.
68 tools available initially; undo availability is conditional. risky_eval's
validator rejects // even inside encoded PNG strings: escape JSON slashes as
Unicode escapes when passing an embedded project. It is needed for project load;
normal edit/paint/export calls use the dedicated tools. Protocol client in
mcp_client.mjs allows actual MCP calls even when the current session cannot hot
reload its native tool list. Reload project Codex session for native discovery.

Found and fixed baseline project format metadata: Blockbench 5.2.1 defaults to
Java 26.3 unless java_block_version is set. Set individual device files and their
generator to "1.9.0", the UI's 1.9-1.21.5 range that includes MC1.21.1. No geometry
or texture changes. See tools/blockbench/docs/mcp.md, mcp-validation.json and
provider-mcp.png. Tests use disposable files, not art proposals. Test application
and Xvfb are closed after verification. Concurrent business edits untouched.

## Workspace consolidation verification

2026-09-26: Moved projects, versions and docs under tools/blockbench.
All frozen version files and original bbmodels remained byte-identical.
Updated active script paths and the existing desktop project shortcut.
Python, Node and shell syntax checks passed. Started the real Blockbench
editor via the relocated default project, then loaded all 21 V02 projects
over MCP with zero missing textures. Version hashes passed again afterward.
Fresh editor checks now write only .local/verification/v02-editor.
Historical version source paths remain archived; see the workspace README
for their relocation mapping. No production assets or game logic changed.
