# Topology 3D preview: what LDLib2's scene draws (2026-10-06)

`client/menu/FederationScenePreview` extends LDLib2 2.2.34 `Scene`. `createScene(level)` wraps the client level in a
`TrackedDummyWorld` unless it is one already; `WorldSceneRenderer` then draws every block from that wrapper.

## Model data (fixed)

- `WorldSceneRenderer.renderSingleBlock` asks `world.getModelData(pos)` and passes the result to
  `renderBatched`. `TrackedDummyWorld` forwards block states, block entities, chunk source, tint and biome to the real
  level, but not `getModelData`, so NeoForge's default `ModelData.EMPTY` came back.
- AE2's `CableBusBakedModel.getQuads` returns nothing without `CableBusRenderState.PROPERTY`: every cable colour and
  every part (terminals, buses, P2P, panels, facades) was invisible; only our edge outline showed.
  Other AE2 models that read model data lost their data-driven look too: Drive cells, crafting CPU (formed),
  monitors, quartz glass, spatial pylon, P2P frequency.
- Fix: `client/menu/SceneLevel` (a `TrackedDummyWorld`) forwards `getModelData` to the real level, and returns the
  default for positions left out of the scene (they read as air). `createScene(new SceneLevel(level))` uses it as-is.
- Guarded by the `ui.graph-controls` check "the 3D preview draws the network's AE2 cables, which need their model
  data". It failed without the fix and passes with it (Task 33 UI set 6/6).

## Still not drawn (open, owner's decision)

- Entities: `TrackedDummyWorld.getAllRenderedEntities` has `// TODO entity box?` for a real-level proxy and returns the
  wrapper's own empty list: no item frames, paintings, armor stands, minecarts, dropped items or mobs. Fix shape:
  override it to return the real level's entities inside the scene window.
- Block-entity renderers are drawn (the block entity comes from the real level) but are not dimmed: the hook's
  colour multiplier only reaches baked quads; `applyBESR` has only the pose stack.
- Inferred, not checked: Create kinetic parts drawn by Flywheel visuals; other mods' models that need model data are
  now covered by the same forward.
- By design: air, unloaded chunks and blocks outside the window are left out; the scene is lit at full brightness
  (`DummyWorld` returns 15), so torches and night do not show.
