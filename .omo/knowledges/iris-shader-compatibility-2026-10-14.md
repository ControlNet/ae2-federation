# Iris and shader packs (checked 2026-10-14)

Production JAR 0.0.6 at `74e9a1a4` on the capture install (`build/visual-v07`: dedicated server, portablemc client,
NeoForge 21.1.250, AE2 19.2.17, GuideME 21.1.1, LDLib2 2.2.34, Jade, JEI), software GL (llvmpipe, OpenGL 4.5) under
Xvfb. Result: no rendering difference that is ours; no code change needed.

## Versions (Modrinth, SHA-512 checked)

| Client profile | Sodium | Iris | Pack |
|---|---|---|---|
| vanilla | none | none | none |
| iris12-off | 0.6.13 | 1.8.12 (latest release for NeoForge 1.21.1) | shaders off |
| iris12-comp / -bsl / -makeup | 0.6.13 | 1.8.12 | Complementary Reimagined r5.9.3 / BSL 10.1.8 / MakeUp Ultra Fast 9.5g |
| iris14-comp | 0.8.13 | 1.8.14-beta.1 (the only Iris for Sodium 0.8) | Complementary Reimagined r5.9.3 |

Iris 1.8.12 requires Sodium 0.6.13; Sodium 0.8.x needs Iris 1.8.14-beta.1 or later.

## What was looked at

Same scene, day and night: a beam row at 5/8, a row over the limit (9/8, red), a 2x2 bundle between two 1x2x2
Routers, a 2x2x1 Switch with three networks, the wrench selection box, Jade lines. With Complementary also the domain
screen's 3D preview, the world highlight ("Highlight network") and the Bundled Cables guide scene.

- Beams (`CableFlowRenderer`, beacon-beam shader, own buffers flushed at `AFTER_BLOCK_ENTITIES`): drawn by every pack,
  same shape, colour and load. Packs add bloom, so at night the glow is stronger than without shaders.
- Bundle shells, connected Router/Switch tiles, the selection box (`BundleOverlay`), Jade: unchanged.
- World highlight (`WorldHighlight`, no-depth lines at `AFTER_LEVEL`): drawn under Complementary. It lasts 10 s, so
  at 1 FPS take the shot within a few seconds of the click.
- Domain 3D preview and guide scene: same as without shaders.
- Client logs: nothing from ae2federation. Pack/Iris mismatch warnings (`BIOME_PALE_GARDEN` etc.) are the packs'.

Images (not committed): `build/iris-check/iris-shaders-world.png`, `iris-shaders-gui.png`, per-profile folders.

## Pitfalls for repeating it

- llvmpipe with a pack runs about 1 FPS and loads the server ("Can't keep up"). Wait 20-30 s for a screen to fill,
  e.g. the domain screen's topology.
- `game_input.py command` types too fast at 1 FPS: `/` opens chat only on the next tick, so the rest of the letters
  act as game keys (`e` opens the inventory). Press `slash`, wait about 6 s, then type the rest.
- GuideME client command: `/guidemec ae2:guide open ae2federation:bundled-cables.md`. Closing the guide returns to
  the chat it was opened from, so it takes two Escapes.
- Never send a GUI left-click unless the screen is confirmed open: in creative it breaks the block under the
  crosshair. One stray click split the test bundle and left a Router's domain without networks, which looked like a
  stuck "Pending current topology" screen.
- `pkill -f 'Xvfb :79'` also matches a shell whose command line contains that text; keep such words out of the same
  command.
- Iris and Sodium leave `config/iris-excluded.json`, `config/sodium-*.json|properties` and `shaderpacks/` behind;
  remove them when restoring the install.

## Not covered

Real GPU frame times, Iris on hardware drivers, Embeddium/Oculus, other packs, and the shadow pass's cost with many
beams.
