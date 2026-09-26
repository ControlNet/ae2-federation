# Project-scoped Blockbench MCP

The integration is scoped to this repository. It does not add a server to
`~/.codex/config.toml` or install a plugin in the normal Blockbench profile.

## Use

From a desktop terminal:

```sh
cd /home/zhixi/GitRepos/ae2-federation
./tools/blockbench/launch.sh
```

Then open/reload the Codex session for this trusted repository. The server is
`ae2f_blockbench`, configured in `.codex/config.toml`. The application must remain
open while its MCP tools are used. Existing sessions may need to reload their
MCP connections; editing the config does not insert new tools into an already
running assistant's fixed tool list.

The ordinary **Blockbench** and previously created desktop shortcut still use
the normal profile, which has no MCP plugin. Use the project launcher for MCP.
Close the project editor normally to stop its server; unsaved work retains the
normal Blockbench save prompt.

Open a specific project:

```sh
./tools/blockbench/launch.sh "$PWD/tools/blockbench/projects/pattern_provider.bbmodel"
```

Configuration and state:

| Path | Purpose |
| --- | --- |
| `.codex/config.toml` | Project-only Streamable HTTP client definition |
| `tools/blockbench/launch.sh` | Runs Blockbench with the project's `--userData` directory |
| `tools/blockbench/.local/mcp.js` | Pinned upstream plugin with a loopback listener patch |
| `tools/blockbench/.local/profile/` | Plugin registration, settings, permissions and editor state |
| `tools/blockbench/.local/verification/` | Disposable smoke-test models and results |

`.local/` is ignored by Git. No plugin bundles or editor caches enter the Mod
JAR. The HTTP service binds **127.0.0.1:31337** with endpoint `/bb-mcp`.
Project scoping controls when Codex loads the integration and which editor
profile runs it; it is not an operating-system filesystem sandbox.

## Pinned installation

- Desktop: Blockbench 5.2.1.
- Plugin: Blockbench MCP 1.9.2.
- Upstream: https://github.com/jasonjgardner/blockbench-mcp-plugin
- Published artifact commit: `657e0af81883f338b8478b6adf07b3ca8907197d`.
- Upstream artifact SHA-256:
  `766ddeda3111bf38015ae5cad3c3396b3f6964c4f8e94020104fe1eaca737b53`.
- `install_mcp.py` verifies that digest and changes the single listener call from
  `listen(port, callback)` to `listen(port, "127.0.0.1", callback)`. It refuses an
  unexpected artifact or patch location. Local installation metadata records
  both original and patched digests.
- The project profile grants this plugin `process`, `net`, and `fs` access for
  server startup and model saving. No permissions are added to the normal user
  profile. CDN prompt downloads and AI Scratchpad are disabled; AI attribution
  remains enabled.

For a fresh checkout on a machine with Blockbench and Node 22+ installed, run:

```sh
python3 tools/blockbench/install_mcp.py
./tools/blockbench/launch.sh --remote-debugging-port=9339 "$PWD/tools/blockbench/projects/pattern_provider.bbmodel"
```

While that editor is open, run in another terminal:

```sh
cd /home/zhixi/GitRepos/ae2-federation
node tools/blockbench/bootstrap_mcp.mjs
```

Close that bootstrap editor and restart with `./tools/blockbench/launch.sh`.
The normal launcher does **not** enable a debugging port. Bootstrap uses CDP
only to register the plugin and settings in the explicitly checked project
profile. All subsequent edit/paint/capture/undo/export checks use MCP itself.
Python scripts use only the standard library; Node scripts use built-in APIs.
`BLOCKBENCH_PATH` can select another installed executable.

## Verification commands

With the project editor running:

```sh
codex mcp get ae2f_blockbench --json
node tools/blockbench/mcp_client.mjs get_project_info
node tools/blockbench/verify_mcp.mjs
ss -ltnp | rg ':31337'
```

Expected: project server enabled; real editor project info; all verification
checks true; listener on `127.0.0.1:31337`, not a wildcard address.

`verify_mcp.mjs` loads a disposable Provider copy, changes its height by one
model unit, undoes/redoes/undoes the change, paints one test pixel, undoes the
paint, captures an offscreen view, exports a `.bbmodel`, and reloads it. Geometry
and texture data must match before/after, and the source file hash must remain
unchanged. Temporary modifications are test operations, not proposed artwork.
The screenshot shows the restored baseline.

Project scope can be checked from outside the repository:

```sh
(cd /tmp && codex mcp get ae2f_blockbench --json)
```

Expected: `No MCP server named 'ae2f_blockbench' found` and nonzero exit status.
This is a successful isolation check, not an installation failure.

## Actual results

Completed against the real 5.2.1 editor over Xvfb/software GL, including a clean
restart without CDP and a second full MCP smoke test:

- MCP initialization and tools discovery passed (68 tools initially available;
  availability changes with editor state, such as undo becoming available).
- Read, geometry edit, undo, redo, painting, paint undo, screenshot, save and
  reopen passed; original Provider unchanged.
- Loopback-only listener verified; no debug listener after normal restart.
- Codex recognizes the server in this repository and cannot find it in `/tmp`.
- The user Codex config hash is unchanged.
- Java device projects explicitly use Blockbench's `1.9.0` format option, whose
  UI label is **1.9 - 1.21.5**, covering this project's Minecraft 1.21.1. Without
  this field, Blockbench 5.2.1 defaults to newer 26.3 rules. The overview remains
  Free Model format. No game geometry or game assets changed.

Evidence: `mcp-validation.json` and `provider-mcp.png` in this directory.
This proves the editor integration, not a new art design or game acceptance.
The test editor and virtual display are closed after verification.
