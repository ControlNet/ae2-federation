# Dark ECO - N structures (2026-11-02)

The owner asked why the N series' casing was not dark. On 2026-10-25 they had declined darkening; on 2026-11-02 they
asked for it: route (b), a client retexture of the hosts' parts inside formed Federation structures, for N1, N4, N6
and N9. They first picked "only casing and panel"; after seeing both variants in game they picked B: every part's grey
body darkens, and coloured pixels (tier lights, coolant) keep the hosts' colours.

## What the formed structure is made of

- Neo ECO's casing blocks turn `RenderShape.INVISIBLE` once formed (`ECOMachineCasing.INVISIBLE`). The "shell" a
  player sees comes from the controller's 3x3x2 formed model (ours) and the parts' own formed models.
- The parts' visible faces use their own textures (`drive/drive_north*`, `transmitter/transmitter_north`,
  `core/*_north`, `cooling_controller/controller_*_formed`); the shared `casing*` textures sit mostly on hidden inner
  faces. Darkening only `casing*` changed almost nothing visible.

## How it works

- Server: `EcoComputationSystemBlockEntity` puts the cluster bounds (`ae2federation:formed_min/max`, packed longs)
  into its update tag and calls `markForUpdate()` from `updateState`. It cannot use AE2's `writeToStream`: AE2 reads
  its update stream only when the update tag holds `#upd` alone, and Neo ECO adds its LDLib2 sync data to the same tag,
  so the client gets the whole tag in `loadTag`, which reads the bounds there.
- Client state: `compat/neoecoae/FederationComputationLooks` maps controller -> bounds, repaints old and new bounds
  through a hook the client entrypoint sets, and clears on client level unload.
- Models: `client/FederationComputationModel` wraps every blockstate model of `neoecoae:computation_*` and
  `neoecoprototype:simplify_computation_*`. `getModelData` flags positions inside a known structure; `getQuads` swaps
  each quad whose sprite has a dark copy for the same quad on that copy (UVs remapped by sprite offset).
- The dark copies are data: every texture under `textures/block/eco_computation/dark/<host>/<path>` replaces
  `<host>:block/<host folder>/<path>`. `tools/visual/build_eco_computation_placeholder.py` writes them from the
  folders in `DARK_PARTS` (casing, drive, transmitter, cable, core, cooling_controller; drive cells are skipped, the
  drive renderer draws them). The darken rule: opaque pixels with saturation < 0.2 and lightness <= 0.97 keep their hue
  at 0.5x lightness. The controller's formed sheets get the same rule.
- Host wrappers: Neo ECO 21.2.10+ wraps its formed cooling controller in `CoolingControllerBakedModel`, and Prototype
  in `SimplifyCoolingControllerBakedModel`. Their fixed renderers draw the model only if `getBlockModel(state)` is that
  class. Wrapping outside them made both coolers vanish, the host's C4 included. So when the baked model is already a
  `BakedModelWrapper`, ours goes inside it (reflection on `BakedModelWrapper.originalModel`).
- Fixed renderers ask for quads with `ModelData.EMPTY` and no position. `compat/neoecoae/HostFixedRenderers`
  re-registers every renderer in Neo ECO's `FixedBlockEntityRenderers` map (read by reflection, written through its
  public `register`) behind one that sets a thread-local "drawing a Federation part" flag from the block entity's
  position. It installs on client login, after the hosts' client setup.

## Checks

- In game, C1/N1/C4/N4 side by side (`build/eco-dark-check/run.sh`, scene from `build/eco-n1-check/compare_scene.py`):
  `build/eco-dark-check/{angle,front,back}-before-after.png`. C structures are unchanged; N structures are dark all
  round, coolers included. `c4-n4-back.png` shows the cooler regression and its fix.
- Unit: `FederationComputationLooksTest` (3). Whole suite 2191/2191.
- Compat: `python3 tools/compat_run.py --accept-eula -j 2 neoecoae neoecoprototype` -> 58/58 and 74/74.
- `tools/visual/validate_assets.py` fails on `gui/eco_computation_controller` on HEAD as well (the shipped 21.2.1 GUI
  atlas size); not caused by this.

## Limits

- Unformed parts and items keep the hosts' look; drive cells keep theirs.
- Dark copies are generated from the default art; Neo ECO's `classic_pack` resource pack would show the default dark
  art inside N structures.
- Dark copies follow the hosts' textures at generation time; the Neo ECO 21.2.9 jar the script reads and 21.2.11 have
  identical part textures (checked). Regenerate if a host redraws them.
- The dark copies are more adapted host art (GPL-3.0); list them with the N-series recolours when the notices'
  Artwork section is written.
- Not checked under Sodium/Embeddium: no client profile with them ran.
