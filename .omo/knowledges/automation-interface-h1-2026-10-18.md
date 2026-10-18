# ME Federation Automation Interface, H1 (2026-10-18)

Branch `feature/function-system`. Decisions are in `docs/ideas/function-system.md` ("Interaction interface" and
"The interface as built").

## Adding a domain device: the places to touch

- `FederationDomainNodeKind` plus the exhaustive switches in `OperatingPowerCosts` (`perTick`, `Group.of`) and
  `CableCapacityCounter` (`device`, `addUnits`). The compiler finds the switches; `device()` is an `if`, so it does not.
- The topology view is built from networks and Endpoints, not node kinds, so a new kind stays out of it without
  any change.
- Resources: blockstate, block/item model, loot table, recipe + recipe advancement, `mineable/pickaxe` tag, lang
  (en_us/zh_cn), creative tab, guide pages (en + `_zh_cn`), `GuidePagesContractTest.FEDERATION_ITEMS`,
  `SurvivalRecipeContractTest` (expected map + inputs).
- GameTests: class in `FederationTestMod.registerGameTests`, ids in `tests/scenarios/manifest.json`. The
  `automation.` id prefix belongs to the native-automation cases (`NativeAutomationContractTest` lists them
  exactly), so these use `automation-interface.`.

## Behaviour worth knowing

- A node that links nothing is a one-node domain of its own in the registry (`federationDomainOf` is present).
  Test "not in the cable's domain", not "in no domain".
- AE2 `AEBaseBlockEntity.setBlockState` calls `onOrientationChanged` when the facing changes (wrench, or a
  same-block `setBlock` in a GameTest): the place to reopen face ports.
- Client-bound payload handlers may name client-only classes in their lambda (`AutomationInterfaceScreen.show`);
  the class only loads when the handler runs, on the client.
- In a screen with an `EditBox`, the inventory key (E) closes the screen unless `keyPressed` hands it to the
  focused box first.

## In-game check

`build/automation-interface-check/` (git-ignored): `compare.png` (cable end before / interface after),
`screens.png` (opened, alias set, duplicate alias in red), `full-manifest.txt`. A private server + client copied
from `build/visual-v07` of the main checkout (port 25583, display :87, flat world), the branch jar from
`./gradlew :neoforge-1.21.1:jar`. Typing into a screen: XTEST key events per character (shift for upper rows).
Stop only your own processes: by PID file, never `pkill -f "tail -f cmd"` (other sessions feed servers that way).

## H2: programs on the server

- Network membership of a domain (`FederationDomainSnapshot.memberships`) comes from Switch native evidence, which
  settles some ticks after the Federation links: a GameTest must wait for it, not only for the domain.
- `AEKeyType.loadKeyFromTag(registries, {"id": ...})` logs at debug level only, while `AEKey.fromTagGeneric` warns on
  every failure: use the former for "is this ID a resource" checks, and compare `key.getId()` with the input.
- In-game check `build/programs-check/compare.png`: the H1 jar has no `programs` command, the H2 jar lists a program
  with its one problem. Swap the jar in both `server/mods` and `client/mods`, then restart both.

## H3: the host

- AE2 crafting plans: `ICraftingSimulationRequester.getGridNode()` must return a node on the grid (the pivot works);
  with null, `CraftingTreeNode.buildChildPatterns` skips patterns and the plan only reports the target as missing.
- AE2 plans from `getCachedInventory()`, updated a tick after an insert: a test that inserts and plans in one tick
  sees the materials missing.
- Two networks on one Switch: their energy cells (or any AE2 parts) must not touch, or they merge into one grid.
- The checker wants one item type per list literal: build a test result with `" ".join([str(...), ...])`.
- H3 has no player-reachable surface (programs run only from GameTests until H4's installations), so its in-game
  check comes with H4.

## H4: installations and the scheduler

- `DomainDeviceBlockEntity` holds what a cable-linked domain device needs (ports, ID claim/release, node evidence,
  unload vs break); a new device overrides `kind`, `claim`, `release` and `links`. Add the node kind to the switches
  listed above.
- Checker.Result.effectfulFunctions() tells whether a function acts, through every function it calls: use it to keep
  conditions read-only.
- Brigadier: lambdas cannot be passed as `Object...`; GameTest step lists need a small builder. Count parentheses per
  `.then(` branch when nesting command trees (a depth trace per line found two extra closers).
- Block events from tests: `NeoForge.EVENT_BUS.post(new CropGrowEvent.Post(...))` and
  `new PlayerInteractEvent.RightClickBlock(mockPlayer, hand, pos, hit)` reach the listeners as the game's do.
- In-game check `build/automation-run-check/compare.png`: H2 jar (no device, `install` unknown, lamp dark) vs H4 jar
  (device placed, `install`, `run`, `log` in chat, lamp lit). IDs come from `/ae2federation programs devices` and
  `interfaces` typed into the server console.

## H5: the tab's traffic

- A payload record's static `type(...)` helper clashes with `CustomPacketPayload.type()`: name it `payloadType`.
- Common unit tests run in the NeoForge project, so they can reach NeoForge-module classes in the same package
  (`ProgramPayloadsTest` round-trips the index through a `FriendlyByteBuf(Unpooled.buffer())`).
- Keep "compute the answer" apart from "send it" (`ProgramSync.receive`/`handle` vs `upload`/`request`): GameTests
  then drive the server side without a network connection.
- zsh does not split `$ids` into words: `${=ids}` passes a list of GameTest ids.
