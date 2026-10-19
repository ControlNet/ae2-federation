# JEI and Jade compatibility (2026-10-13)

The owner wants AE2 Federation to stay compatible with JEI and Jade, and chose three steps: a real-game check, Jade
lines on more blocks, and a server compatibility profile (not a JEI plugin of our own).

## Server profile `recipe-viewers`

`tests/compat/profiles/recipe-viewers.json`: baseline's AE2 19.2.17 / GuideME 21.1.19 / LDLib2 2.2.34 plus JEI
19.57.0.449 (picked over the day-old 19.64.0.453), Jade 15.10.6 and AE2 JEI Integration 1.2.1, tests `core`.
19/19 passed; the server log shows Jade loading `FederationJadePlugin` after AE2's own `JadeModule`, with no Jade,
JEI or Federation warnings. AE2 JEI Integration is on CurseForge only (project 1074338, file 7727898, keyless CDN
`mediafilez.forgecdn.net/files/7727/898/...`); CurseForge publishes no sha512, so the pin is trust on first use, cross
checked: the file is 91710 bytes as CurseForge lists, and both it and Jade hash the same as the copies inside the
pinned ATM10 8.2 server pack (which also ships JEI 19.57.0.446). The `atm10` profile therefore already ran Jade and
JEI beside Federation, before the Jade plugin existed.

Jade stays out of the testmod/GameTest runtime (`jadeApi` configuration): a fake player there makes Jade send
`jade:server_ping_v1` and fail. The compat runner's tests did not trip it with Jade really installed.

## Real-game check (capture install `build/visual-v07`)

The capture install now keeps JEI, Jade and AE2 JEI Integration in both `server/mods` and `client/mods` for good.
After a capture, remove only `ae2federation-compare.jar` and move the 0.0.6 jar back from `old-jar/`; never remove
the three viewer jars. Run `beam_world.sh stop` as its own shell call: its `pkill -f` patterns match a command line
that mentions the jar and the player name and kill the calling shell.

Seen in `hero_scene:base` (shots in `build/jei-jade-check/`):

- Jade on every Federation block, no errors. Cable: our capacity line. Bridge and Provider: AE2's "Device Online".
  Endpoint: AE2's "Device Offline" (its own screen says "Waiting for its ME node", unbound, unclaimed). Switch and
  Router: name only. The Federation P2P tunnel part was not looked at (no tunnel in the scene).
- JEI lists 11 Federation items; each shows its recipe: crafting, Inscriber (through AE2 JEI Integration) and the
  Federation P2P tunnel's P2P attunement.
- Our screens (LDLib2, full screen) open normally, and JEI's ingredient list does not appear on them, so nothing is
  covered; nor can a player press R/U or drag from JEI there (the Provider's pattern slots included).
- No warning or error mentions JEI, Jade or Federation in the client debug log or the server log.

## Jade lines on more blocks

The owner chose four lines and to hide AE2's "Device Online/Offline" on the Endpoint only:

- Router/Switch (`ae2federation:device`, `DeviceProvider`): the body from `DeviceSummary.of`, which reads
  `DeviceStructureUpdater.body` and, for a Switch, unions `RouterBlockEntity.attachedNetworks()` over the Valid body.
  "Networks attached: N" comes first; "Structure: X×Y×Z" shows only for a body bigger than 1×1×1, and an unformed body
  says why (not solid, touching a block of its type, an edge over 8, unloaded chunks). An L of three Routers reads as
  TOUCHING from its corner, not NOT_SOLID: the evaluator grows a 2×1×1 box first and then finds the third block beside it.
- Endpoint (`ae2federation:endpoint`): "Local mode" (`nativeProviderOnFederationFace()`) or "Federated mode · claimed /
  unclaimed" (`claimState()` Owned). It removes `ae2:grid_node_state`; that needs priority 1500 because AE2's Jade
  module adds the line at 1000 and a provider can only remove what earlier providers added.
- Federation Pattern Provider (`ae2federation:pattern_provider`): "Patterns used/total · mappings N", where mappings is
  the sum of `endpointsForSlot(slot)` over the slots. AE2's online line stays.

Text lives in the Jade-free `FederationJadeText` (unit-tested, keys checked in both languages); the providers only move
NBT. `DeviceSummaryGameTests` (manifest `structure.device-summary`) checks the server half. Before/after shots:
`build/jei-jade-check/jade-lines-before-after.png` and `jade-lines-multiblock.png` (2×1×1 Switch with 3 networks; L of
Routers "Not formed: touches a block of its type"). The `recipe-viewers` profile still passes 19/19 with them.
