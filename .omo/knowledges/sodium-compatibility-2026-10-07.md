# Sodium compatibility (checked 2026-10-07)

Sodium 0.8.13+mc1.21.1 for NeoForge (Modrinth `mc1.21.1-0.8.13-neoforge`, SHA-512 checked) with the production JAR
0.0.5 (e1bcfd5), NeoForge 21.1.250, AE2 19.2.17, GuideME 21.1.1, LDLib2 2.2.34, software GL (llvmpipe) under Xvfb.
Result: no difference found; no code change needed.

## What our client rendering relies on, and why Sodium keeps it

- `CableBakedModel` picks one of 64 baked variants from `ModelData` returned by
  `getModelData(level, pos, state, data)`, and filters the old cutout layer by `RenderType`. Sodium's NeoForge
  chunk mesher calls the NeoForge model data and render type hooks. `CableVisualConnections.mask` reads only six
  neighbour block states, so it is safe on Sodium's meshing threads and its `LevelSlice`.
- `CableFlowRenderer` is a plain BER on vanilla `entityTranslucentEmissive`; Sodium leaves block entity rendering
  to vanilla.
- `WorldHighlight` draws at `RenderLevelStageEvent.AFTER_LEVEL`; NeoForge still fires it with Sodium.
- Client mixins touch only GuideME and LDLib text/tooltip code, not anything Sodium rewrites. LDLib2 2.2.34 checks
  only for Iris, not Sodium.

## Evidence

- Same world, production server plus portablemc client, with and without Sodium: gallery, Provider orientations,
  AE2 cable Bridges, all 64 cable masks, close-ups of a six-way hub (glass envelope, collars, flow), long straight
  runs, a Provider front joined by cable, night. Only the flow pulse phase differs (animation time). GuideME Router
  page scene identical.
- Client logs: same warnings both ways (missing Nexus icons, known); Sodium adds only its driver-workaround notice.
- `federationUiTest` Task 33 set (`ui.graph-controls`, `ui.mapping`, `ui.endpoint`, `ui.multipart-attachments`,
  `ui.chinese-scales`, `ui.reject-claim-conflict`) with Sodium in `run-uitest/mods`: all passed, Sodium loaded. This
  includes the topology 3D preview check that AE2 cables are drawn (needs model data, `SceneLevel`) and the world
  highlight through glass in rain.

## How to repeat

Put the Sodium JAR in `neoforge-1.21.1/run-uitest/mods/` before `federationUiTest` (the task deletes `run-uitest` only
after the run). For world shots: copy a prepared production server/client pair (as in `docs/art/README.md`), add the
JAR to the client's `mods`, and drive the server console through a FIFO read with `cat` (`tail -f` waits for EOF).

## Not covered

Iris with shader packs (LDLib2 has an Iris path; `entityTranslucentEmissive` flow and the highlight's no-depth lines
are the parts to watch), Embeddium (Sodium declares it incompatible), real GPU frame times, and other Sodium
add-ons.
