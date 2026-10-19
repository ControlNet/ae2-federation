# Dedicated-server sync check: multiblocks, bundles, capacity (2026-10-14)

Before this, multiblock Routers and Switches had only been shot on the integrated server (`runStructureSceneClient`).
Bundles had been formed on the capture server for the look comparisons, but nobody had recorded that as a sync
check. This run checks all three on a dedicated server with a remote client.

## Setup

- Jar: `./gradlew :neoforge-1.21.1:jar`, copied to `build/sync-check/ae2federation-sync.jar`. It runs on the
  capture install `build/visual-v07` (server at 127.0.0.1:25579, portablemc client `VisualProbe` on Xvfb `:79`, Jade
  on both sides). Start the world with the scratch `beam_world.sh start <jar>`. Commands go through the server's fifo
  `server/cmd`. Clicks use `tools/visual/game_input.py rightclick`.
- Scene on the flat platform (y 68, x 606-636, z 884-906), mirrored along x:
  - an Endpoint (facing east) on cable;
  - a 1x2x2 Router, a 2x2 cable box formed into a bundle with three real wrench clicks, then a second 1x2x2 Router;
  - cable to a 2x2x1 Switch with three ME networks: a drive on top, a cell and drive on one side (two faces), and a
    drive in front;
  - an L of three Routers apart from the rest.

## Results

The same in all three passes: placed live, after `tp` 4000 blocks away and back (client chunk reload, so
`getUpdateTag`), and after stopping and restarting the server (save and load, fresh join).

- Shapes: the Routers and the Switch drew as formed bodies, the bundle as one shell with four beams, and the L as
  three single blocks.
- Jade:
  - ordinary segment: "Capacity count: 3/8 (subdomain)";
  - bundle: "2x2 · capacity 48", "4/48 (Federation domain)", "Connected: 2 ends";
  - Switch: "Networks attached: 3", "Structure: 2x2x1";
  - Router: "Structure: 1x2x2";
  - L: "Not formed: touches a block of its type".
- Right-clicking the L with an empty hand showed on the action bar: "This ME Federation Router is not formed and links
  nothing: a block of the same type touches its box."

Images (not committed): `build/sync-check/{live,reload,restart}.png` and `live-overview.png`.

## Finding

An L-shaped group reports `touching`, not `not_solid`: the check grows a box from the clicked block, finds a smaller
valid box, and then a same-type block against it. The reason is true, but a player who built an L may expect "not a
solid box". The guide's L annotation says only that an L is not a solid box. The code is unchanged; this is the
owner's call.

## Afterwards

Restore the capture install: delete `ae2federation-compare.jar` from both `mods/` folders and move the 0.0.6 jars
back from `old-jar/`. A shell-variable `rm` path is refused by the safety check; use literal paths.
