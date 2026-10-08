# Production JAR verification

This checks the built mod JAR in a real NeoForge installation, outside `runClient` and `runGameTestServer`, with no
testmod. All files stay under the Git-ignored `build/`.

## Previous task-41 inputs

| File | Version | SHA-256 (2026-09-26 run) |
|---|---|---|
| `ae2federation-0.1.0-dev.jar` | built from the working tree, `:neoforge-1.21.1:build` | `98861f0f518cfcdafad6c929192c584337fef2680e5111a357ae941867d6d25c` |
| `appliedenergistics2-19.2.17.jar` | AE2 19.2.17 (Gradle cache) | `460d779a0609b81409907d9956de8f6f70a1b0912257e3e5c3c7e75ac9630e95` |
| `guideme-21.1.1.jar` | required by AE2 | `62229015025b7c0a741590b626b6719631f6b8a945c483ece12e7035d4fd903d` |
| `ldlib2-neoforge-1.21.1-2.2.34-all.jar` | LDLib2 2.2.34 (Gradle cache) | `5314e624e3b4258812a881b3a52886158af53886e5fb96d34919bdf0d5a374d6` |
| `neoforge-21.1.250-installer.jar` | NeoForge 21.1.250 | `0e47a91ba2139a8db4bf7627af081f7b5789b508bb039ee8dea1272b79693d60` |

The mod JAR's checksum changes with every build; record the one you test.

## World fixture

`common/src/testmod/resources/data/ae2federation_test/production/ProductionTopologyExportGameTests.java.txt` builds a
configured topology in a GameTest and exports `production_topology.nbt`, the Policy SavedData
`ae2federation_global_policies.dat` and a properties file with the relative positions. Its header explains how to run
it. Place the template at the GameTest origin's absolute position (Endpoint position minus its relative offset from the
properties file): Lane bindings store absolute Endpoint positions.

## Server

```bash
S=build/ae2f-work/prod-server
java -jar $S/neoforge-21.1.250-installer.jar --install-server $S
mkdir -p $S/mods $S/world/data $S/world/generated/ae2f_verify/structures
cp neoforge-1.21.1/build/libs/ae2federation-0.1.0-dev.jar <ae2, guideme, ldlib2 jars> $S/mods/
cp <export>/production_topology.nbt $S/world/generated/ae2f_verify/structures/
cp <export>/ae2federation_global_policies.dat $S/world/data/
echo eula=true > $S/eula.txt
printf '\n-Dmixin.debug.export=true\n' >> $S/user_jvm_args.txt
(cd $S && ./run.sh nogui)
```

Console commands: `forceload add …`, `place template ae2f_verify:production_topology <origin>`, then
`setblock <redstone> minecraft:redstone_block`, and `data get block <chest> Items` to read results. Check
`.mixin.out/class/appeng/...` for the transformed `PatternProviderLogic`, `StorageService`,
`StorageService$ProviderState` and `DelegatingMEInventory`, then `save-all flush`, `stop`, restart, and repeat.

Expected: 16 gold ingots from Grid C (Storage Policy) and 8 iron ingots produced by the Federation Pattern Provider
through Endpoint A and a real furnace; after the restart, more raw iron fed into the source ME Chest produces more
ingots; no mod ERROR lines; `stop` exits with code 0 and leaves no Java process.

## Client

An offline production client can be installed and started with `portablemc` from an isolated pixi environment, under
`Xvfb` with software GL, joining the server with `-s 127.0.0.1`. Write `onboardAccessibility:false` in `options.txt`
first or quick-play stops at the accessibility screen. Real X input can be sent with `python-xlib`'s XTEST. Right-click
the Provider for AE2's Pattern Provider screen and a Router for the Federation Domain screen.

## Result of 2026-09-26

- Server: started, placed the fixture, produced 8/8 ingots and shared 16/16 gold; after save, stop and restart it kept the
  items and produced 4 more ingots from new input. All four mixin targets were exported with Federation members. Every
  WARN/ERROR line was vanilla (offline mode, flat generator settings). Three `stop` runs exited with code 0.
- Client: joined the server, rendered the world, opened the Provider screen with the saved Pattern and the Domain screen
  (4 members, Storage Policy revision 2, Provider active, mapping panel with **Release retained**); pressing release on
  an unmapped Endpoint returned `rejected-not-retained` from the server. The client log had no ERROR lines.
- Not covered: a multi-hour session, a real player's crafting order from a terminal (the run used an Export Bus with a
  Crafting Card), and other mods.


## Task 42: terminal request, real furnace, unload and restart (2026-09-26)

This supersedes only the previous terminal/unload coverage gap. It does not claim a soak, Late/Ultra performance,
multiplayer authorization, or third-party compatibility acceptance.

Source base: `2a3f40db8cdf910568cc4ae658dac8554be1b755`, with the identity initialization and retained-binding patch in
this working tree. The tested production JAR SHA-256 is
`f4b5b7d88faf96b7bf99f2918f039880b60cfb482ba6440c14e397e533d2b562`.
The exact checksum is also stored in `.omo/evidence/task-42-production/jar-sha256.txt`; dependencies remain AE2
19.2.17, NeoForge 21.1.250, GuideME 21.1.1 and LDLib2 2.2.34, with the hashes in the previous inputs table.
Java is 21.0.12.1. The independent server is `build/ae2f-work/task42-production`; the production client is
`build/ae2f-work/prod-client`. Both load the production JAR and the three dependencies, without testmod.
The existing exported topology/Policy data supplies the fixture, not the crafting requests.

All timestamps below are Australia/Melbourne. XTEST drives a real client window on display `:77`, through the
existing local pixi environment, kept outside the repository. Mapping, order confirmation,
unmapping and release use the visible menus and their normal server packets. Console commands only position the
player, prepare/observe the fixture, control chunk loading, and save/stop. No internal crafting request or Export Bus
Crafting Card submits these two requests.

| Time | Actual action and observation |
|---|---|
| 16:36 | First terminal order: select craftable iron, quantity 8, Next, Start. The real furnace begins processing. Remove the last mapping through the Router menu while raw iron is still inside. CPU eventually finishes; terminal shows 8 iron. Explicit menu release returns `released-0`. |
| Before 16:44 | Move the remote fixture farther away using a filtered copy of the exported native structure; preserve the source chest and its 8 iron. Add 8 raw iron through the terminal. Create the new slot-0 → Endpoint-A mapping through the Router menu. |
| 16:45:47 | Second terminal order for 8 iron starts. Furnace `CookTime:72`, raw iron present; native CPU job has `remainingItemCount:8`, `startedWork:8`, final output 8 and player ID 0. See `34-second-plan.png`. |
| 16:45:53 | Remove the final mapping through the actual menu (`accepted-0-7`). The furnace still has raw iron and `CookTime:195`. Slot 0 has no lanes; binding revision 5 remains dispatched/release-pending. See `35-second-unmap-inflight.png`. |
| 16:46:07 | Remove remote force-load tickets and move the player away. Vanilla `execute unless loaded` reports `TASK42_ENDPOINT_UNLOADED`; independent `if loaded` checks report Provider and CPU loaded. CPU still waits for 8 items. |
| 16:46:25–16:47:14 | Return the player to the remote chunk, without remapping or requesting again. `TASK42_ENDPOINT_RELOADED`; binding revision 5 and Claim epoch 1 remain. Furnace reports 5 smelts and 3 raw iron; original CPU waits for 3 items. |
| 16:48:20 | Furnace reports exactly 8 smelts for this cycle, with no input/output iron left. CPU `job` is absent. |
| 16:48:45 | Two menu presses explicitly release the retained binding: `released-0`. Lane revision becomes 6, Endpoint is unclaimed at epoch 2, mappings empty, native send/return buffers empty and lock NONE. See `37-final-released.png`. |
| 16:49:08 | Source ME chest cell contains exactly 16 iron, CPU inventory empty and no job. Terminal screenshot `38-final-count.png` independently shows 16 iron (8 from each order). |
| 16:49:16 | `save-all flush`, `stop`; server exits 0. |
| 16:50–16:55 | Restart same server/world/JAR; source cell still has 16 iron, CPU job absent, Lane revision 6 unbound. Client reconnects using IPv4 and opens terminal: `45-restarted-count.png`, 16 iron. Save/stop again exits 0 at 16:55:23; client closes normally with exit 0. |

Final qualifying topology: source ME chest `(11,-59,3)`, CPU `(12,-59,3)`, Provider `(13,-59,3)` in chunk `(0,0)`;
Router `(80,-59,3)`, Endpoint A `(80,-59,4)` and real furnace `(80,-57,4)` in chunk `(5,0)`, linked by Federation
cable. The source chunk alone stays force-loaded during the unload test. Reproduce the load observations with:

```text
execute unless loaded 80 -59 4 run say TASK42_ENDPOINT_UNLOADED
execute if loaded 13 -59 3 run say TASK42_PROVIDER_LOADED
execute if loaded 12 -59 3 run say TASK42_CPU_LOADED
execute if loaded 80 -59 4 run say TASK42_ENDPOINT_RELOADED
data get block 11 -59 3
data get block 12 -59 3 job
data get block 13 -59 3
data get block 80 -59 4 endpointClaim
data get block 80 -57 4
save-all flush
stop
```

Evidence is kept under `.omo/evidence/task-42-production/`: `server-run.log`, `server-restart.log`, client logs,
seven screenshots, source HEAD/status/diff and production-source hashes, JAR hashes, process exits, XTEST input driver
and the standard-library-only template filter. These ignored files remain available in this workspace. At production verification time the source
patch was uncommitted; the production-source hash list also covers newly added files that `git diff` alone omits.
The user subsequently authorized committing and pushing the repair patch, excluding concurrent visual work.

Important failed setup attempts are retained, not counted as passes: the first attempted server start omitted the
product JAR and was terminated before fixture validation; an adjacent Endpoint chunk remained loaded because of the
source ticket, so that attempt does not prove unloading; a vanilla clone command failed and its partial remote fixture
was replaced before the qualifying cycle. Relocated exported Lane positions initially pointed to the old coordinates:
the loaded-but-absent old binding was explicitly cleared through the menu, then remapped. A UI input/teleport race
broke a source cable during setup; only that cable was restored from the fixture, preserving the chest contents.
LAN discovery attempted unsupported IPv6 on reconnect; Direct Connect to `127.0.0.1` succeeded. None of these failures
is substituted for the completed terminal/furnace/unload sequence above.

## Cross-dimension production run

Run on 2026-10-08 (timestamps UTC). It covers a Federation Pattern Provider in the overworld driving an Endpoint in the
nether over Federation P2P tunnels joined by a Quantum Network Bridge, with an overworld Endpoint at the same
coordinates. It does not claim a soak, multiplayer authorization or third-party compatibility.

| Input | Value |
|---|---|
| Source | `a2a20deb248d4af4fd9c004de4e453d54836ad17`, clean tree |
| Mod JAR | `ae2federation-0.0.5.jar` built from that commit by `:neoforge-1.21.1:build` (the `dev` version string, not the published 0.0.5), SHA-256 `96a6d26e1381c65e7309e4d47e7852adee91e567562652ff836f730b23932ad7` |
| Dependencies | AE2 19.2.17, GuideME 21.1.1, LDLib2 2.2.34 with the hashes in the inputs table; NeoForge 21.1.250; Java 21.0.12.1 |

Server and client load the same four JARs and no testmod. The client is the portablemc client described above, under
`Xvfb`, driven by XTEST. Every menu action (mapping, highlight, orders) is real client input; console commands only
position the player, add and remove `forceload` tickets, read block data and save or stop.

### Fixture

The manual GameTest `crossdimensionproductionexport` (`CrossDimensionProductionExportGameTests`) builds the scene and
writes `crossdim_overworld.nbt`, `crossdim_nether.nbt` and `crossdim.properties` next to the evidence file:

```bash
./gradlew :neoforge-1.21.1:runGameTestServer -PfederationGameTestId=crossdimensionproductionexport \
  -PfederationNativeEvidenceFile=$PWD/.omo/evidence/round82-production/export/evidence.properties
```

- Overworld: a creative cell, an ME chest with 16 cobblestone, a 1k CPU, a Federation Pattern Provider with a
  cobblestone → stone processing pattern, an ME Terminal, a Router and an Endpoint at `203, 70, 205` (the twin), and the
  P2P carrier with its Quantum Network Bridge half.
- Nether: an Endpoint at `203, 70, 205`, its subnet with an Export Bus into a real furnace (64 coal) and a hopper back
  into the Endpoint, a Router, and the carrier's other half.

Copy both templates to `world/generated/ae2f_verify/structures/`, then from the server console:

```text
forceload add 192 192 223 223
fill 196 68 196 212 76 212 minecraft:air
fill 196 68 196 212 68 212 minecraft:glass
place template ae2f_verify:crossdim_overworld 200 69 201
fill 196 69 196 212 76 212 minecraft:air replace minecraft:barrier
execute in minecraft:the_nether run forceload add 192 192 223 223
execute in minecraft:the_nether run fill 196 69 196 212 78 212 minecraft:air
execute in minecraft:the_nether run fill 196 69 196 212 69 212 minecraft:obsidian
execute in minecraft:the_nether run place template ae2f_verify:crossdim_nether 202 70 200
execute in minecraft:the_nether run fill 196 70 196 212 78 212 minecraft:air replace minecraft:barrier
```

The GameTest structure fills empty space with barrier blocks, hence the `replace minecraft:barrier` fills. The
templates keep the Endpoints' identities, so place them once per world. Player poses used: Provider
`tp verifier 202.5 69 200.2 0 3`, terminal `tp verifier 198.5 69 203.5 -90 3`, Router
`tp verifier 198.5 69 203.5 -90 -18`; the nether chunk is unloaded with
`execute in minecraft:the_nether run forceload remove 192 192 223 223`.

### Result

1. The Provider screen lists both Endpoints; the nether one reads `203, 70, 205 · Nether`. Pattern #0 is mapped to it
   with **Map #0 here**, and the return row names the Nether.
2. Highlight on the overworld twin outlines only the overworld block; for the nether Endpoint Highlight is disabled
   while the player is in the overworld.
3. A terminal order for 8 stone. After 2 smelts the nether ticket is removed and
   `execute in minecraft:the_nether unless loaded 203 70 205` confirms the unload. The CPU keeps waiting for 6, and the
   Provider screen still shows the Lane's `203, 70, 205 · Nether` return row. About 74 seconds later the ticket is added
   again and the job finishes with exactly 8 stone: furnace `RecipesUsed` stone 8, subnet chest empty.
4. The Router's Domain graph shows the overworld and nether networks through two Routers, with the nether Endpoint
   labelled `203, 70, 205 · Nether`.
5. `save-all flush`, `stop`: exit 0. After a restart on the same world the source holds 8 cobblestone and 8 stone, the
   Lane binding keeps dimension `minecraft:the_nether`, the claim keeps epoch 1 and the twin stays unclaimed.
6. A second terminal order after the restart: 16 stone and 0 cobblestone in the source, 16 furnace smelts in total.
   The final `stop` exits 0.
7. Logs: one ERROR line, from vanilla (flat generator settings), in the first server log; none after the restart or in
   the client log. The client was closed with SIGTERM on the disconnect screen after each server stop.

Missteps, kept and not counted: in the final run a scripted left click on stocked stone took 8 stone into the player's
inventory instead of opening the order screen; they were put back through the terminal and the order was placed with
a middle click. The Endpoint cards' order can differ between worlds, so read the screen before clicking a card.

Four earlier attempts each found a defect, fixed before the next run:

| Attempt | Found | Fix |
|---|---|---|
| 1 | Endpoint cards showed coordinates without the dimension; the fixture had no Routers, so an Endpoint's Domain workspace showed no domain (by design) | `0456867`, fixture `5e63946` |
| 2 | Full flow passed, but return rows and Domain-graph labels lacked the dimension | `b75467d` |
| 3 | The return row clipped the dimension name; a scripted click first mapped the twin and was undone through the menu | `cc6df41` |
| 4 | Full flow with a 9 minute unload; the unloaded Lane's return row showed an id fragment | `a2a20de` |

Known limits: the Provider screen hides the card of a mapped Endpoint whose chunk is unloaded (its `→1` count and
return row remain); the fixture is loaded with vanilla `forceload`, an operator command, and the mod loads no chunks.

Evidence (Git-ignored): `.omo/evidence/round82-production/` holds the README, timeline, 16 screenshots, server and
client logs, JAR, dependency and client hashes, source HEAD and status, the exported fixture and each attempt's notes.
