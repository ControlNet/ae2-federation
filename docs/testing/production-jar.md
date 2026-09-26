# Production JAR verification

This checks the built mod JAR in a real NeoForge installation, outside `runClient` and `runGameTestServer`, with no
testmod. All files stay under the Git-ignored `build/`.

## Inputs

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
