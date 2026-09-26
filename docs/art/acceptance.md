# Visual v1 acceptance — 2026-09-26

Implemented and exercised as real Minecraft assets. The screenshots below are untouched Minecraft F2 captures from
a production NeoForge client, not website previews. This is a first visual release with the explicit limits below;
structural validation and visual inspection are separate evidence.

## Product identity and automated checks

- Minecraft 1.21.1, NeoForge 21.1.250, AE2 19.2.17, LDLib2 2.2.34; no dependency upgrade.
- Built JAR: `neoforge-1.21.1/build/libs/ae2federation-0.1.0-dev.jar`.
- Final binary SHA-256: `08c057e772619bd51589670c1f4d83a58708a8feb75262deb95e1d94bee1ed84`.
- Installed copies in BOTH `build/visual-v1/client/mods` and `build/visual-v1/server/mods` match that binary.
- `build` and `verifySharedJarContent`: PASS. 236 JUnit tests, zero failures/errors/skips. No unrelated factory stress
  suite was rerun. Full build output: `build/visual-v1/evidence/build.log`.
- Structural validator: PASS, 266 model JSONs, 5,088 checked faces, 64 cable masks, repeatable output bytes.
- Python tooling compiles; `git diff --check` passes. No model reference to copper or other placeholder resources.
- JAR inspection: 781 entries, 23 mod PNGs; no website scripts, source design documents, preview models or historical ZIPs.
- No existing registered IDs or aliases changed. No topology, policy, claims, crafting or inventory implementation changed.

## Actual game coverage

| Check | Result and evidence |
| --- | --- |
| Production JAR client and dedicated server | Both start, client joins loopback server, world renders; final logs contain no ERROR or failed/missing model messages |
| World save/reload | Gallery, actual native AE2 parts and 2,048-cable scene survive dedicated server save/stop/restart; final JAR loads the saved scene |
| Resource reload | Real F3+T reload completes; loaded resources include `mod/ae2federation`; no missing texture/model error afterwards |
| Router | Six equal texture assignments; rendered top, sides and suspended underside inspected |
| Provider | All six facing states present in gallery; actual AE2 wrench inputs produced UP, NORTH, DOWN, SOUTH, WEST, EAST, and restored SOUTH; server `VISUAL_WRENCH` markers confirm the states |
| Player placement | Actual right-click placement against a support's top produces `facing=down`; confirmed by server `VISUAL_PLACEMENT` marker |
| Provider UI | Native ME Pattern Provider screen opens with pattern and return inventory slots; no LDLib2 redesign |
| Endpoint | Fixed EAST cyan face, WEST purple rear and consistent four peripheral faces; no artificial Federation Cable port |
| Bridge | All six native CableBus mounting directions displayed; compact purple contacts and cyan middle visible; representative south-mounted selection outline inspected, plus real outside ME cable attachment |
| Cable combinations | All 64 actual neighbor combinations in the world; includes isolated/end/three straight axes/elbows/tees/crosses/six-way; checked near and normal distance |
| Connection updates | Rotating the neighboring Provider away removes the corresponding cable arm; restoring it restores the arm, without resource/world reload |
| Continuous cable | Long X/Y/Z examples, split collars at shared block boundaries, transparent junctions and opaque low flow inspected; no obvious black hub or visible gap in sampled views |
| Lighting and animation | Noon and midnight screenshots; moving band changes while rings stay stationary; bright flow remains visible without lighting the world |
| Items | All five inventory icons, all five hand models and dropped item entities render; Provider/Endpoint item orientation corrected after identifying identical-back-face icons |
| Shutdown | Final client window closed normally; dedicated server receives native `save-all flush` and `stop`; exit receipts recorded in `runtime-summary.json` |

Final local logs: `build/visual-v1/evidence/client-final.log` and `server-final.log`. The preserved disposable world is
`build/visual-v1/server/visual-world`; regenerate its scene datapack with the command in [README.md](README.md).

## Render load observation

Xvfb, 1280x720, software OpenGL (`LIBGL_ALWAYS_SOFTWARE=1`), render distance 8, frame cap 60. Vanilla F3+L collects
about ten seconds per stationary view. These are measurements on this software-rendering host, not claims about
hardware GPU performance or a before/after regression comparison against the old placeholder build.

| Scene | Frames | Mean frame time | P95 frame time | Maximum queued uploads / batches |
| --- | ---: | ---: | ---: | ---: |
| Normal gallery | 473 | 21.09 ms | 25.58 ms | 0 / 0 |
| 2,048 cables + 128 devices | 193 | 51.73 ms | 60.53 ms | 0 / 0 |

Profiles are retained under `build/visual-v1/client/debug/profiling/`:

- `2026-09-26_15_59_12-Minecraft Server-1_21_1.zip` — gallery.
- `2026-09-26_15_58_07-Minecraft Server-1_21_1.zip` — stress scene.

For example:

```bash
pixi run --manifest-path tools/visual/pixi.toml python tools/visual/summarize_profile.py \
  'build/visual-v1/client/debug/profiling/2026-09-26_15_58_07-Minecraft Server-1_21_1.zip'
```

The stress scene has a substantial rasterization cost on software GL. Its stable zero upload/build queues, together
with the pre-baked model implementation, show that animation is not continually rebuilding chunks. This does not
prove zero render cost. The dedicated server also reported several multi-second `Can't keep up` warnings during
large-world loading and some operations. Their cause was not isolated; no network-business rewrite was attempted.

## Screenshot index

- [Full family](screenshots/01-family.png)
- [Three full blocks and key faces](screenshots/02-block-faces.png)
- [Six Provider orientations](screenshots/03-provider-orientations.png)
- [Six Bridge mount directions](screenshots/04-bridge-mounts.png)
- [All 64 cable combinations](screenshots/05-cable-64-masks.png)
- [Six-way close view](screenshots/06-six-way-detail.png)
- [Night / long continuous cable](screenshots/07-cable-night.png)
- [2,048-cable scene](screenshots/08-stress-2048-cables.png)
- [Representative Bridge selection](screenshots/09-bridge-selection.png)
- [Five inventory models](screenshots/10-inventory.png)
- [Router underside](screenshots/11-router-underside.png)
- [Endpoint fixed EAST front](screenshots/12-endpoint-front.png)
- [Provider connected](screenshots/13-provider-connected.png), [Provider rotated away](screenshots/14-provider-rotated.png)
- [Second animation frame at same night camera](screenshots/15-cable-animation-frame.png)
- [Actual wrench rotation to UP](screenshots/16-provider-wrench.png)
- [Actual player placement](screenshots/17-provider-placement.png)
- [Bridge with native ME neighbor](screenshots/18-bridge-native-me.png)
- Hands: [Router](screenshots/hand-router.png), [Provider](screenshots/hand-provider.png),
  [Endpoint](screenshots/hand-endpoint.png), [Bridge](screenshots/hand-bridge.png), [Cable](screenshots/hand-cable.png).

## Limits and rejected intermediate checks

- Hardware GPU drivers, Fabulous transparency, shader packs and additional rendering mods were not tested in this
  headless environment. Default transparency was sampled at several cameras; exhaustive sorting correctness from
  every camera is not claimed. No bloom, shader pack or dynamic-light mod is required.
- Bridge selection was sampled in game and all six mount transforms inspected; it was not exhaustively pixel-compared
  at every edge on all six mounts. The three source collision boxes match the final source geometry.
- Provider's six states and a full real wrench cycle were tested; only one representative player placement was
  exercised with real mouse input. Static rotations and directional texture checks cover the remaining states.
- No multi-hour session or new crafting/permission/chunk-loading business qualification is claimed by this art task.
- Cable flow uses one opaque animated interior with a light translucent envelope. It remains a pixel-art approximation
  of flowing volume. Junction bands are decorative and are not synchronized to a real transport direction or traffic.
- An initial empty-faced enclosed core passed an insufficient first validator but was rejected by Minecraft. This was
  corrected; the validator now explicitly rejects empty-faced elements and validates exact exposed junction surfaces.
- One intermediate test incorrectly replaced a JAR before server shutdown completed, causing a late class-load failure
  and a subsequent world-lock rejection. That run is rejected. Later runs waited for process exit and completed cleanly;
  this orchestration error is not presented as a mod business failure or successful runtime proof.
