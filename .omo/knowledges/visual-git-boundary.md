# Visual asset Git boundary

- Track production resources, generators, validators, the design specification,
  project-scoped MCP configuration, and the complete frozen Blockbench versions.
- Keep version manifests and their source/model/texture/render files together;
  these reviewed snapshots support comparison and rollback.
- Track curated Minecraft screenshots and acceptance reports under `docs/art/`.
- Ignore the downloaded website under `design/visual/ae2-federation-web/` and
  matching original ZIP downloads. Production generation does not depend on it.
- Ignore `tools/blockbench/.local/`: downloaded plugin, editor profile, extracted
  references, scratch work and fresh verification captures belong there.
- Existing rules exclude build outputs, runtime worlds, pixi environments and
  Python caches. Do not broadly ignore PNG, JSON or bbmodel files: they are assets.
- Unrelated GUI research notes are separate work, not disposable files; leave
  them outside the art commit without adding ignore rules for them.
