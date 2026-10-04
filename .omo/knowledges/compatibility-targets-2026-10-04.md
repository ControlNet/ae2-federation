# Compatibility targets for 1.21.1 (researched 2026-10-04)

Facts gathered before building the compatibility pipeline. Versions move; re-check before pinning.

## Modpacks

- **All the Mods 10** 8.2 (2026-09-22), NeoForge 21.1.251, 464 mods. AE2 19.2.17, ExtendedAE 2.2.38, AE2WTLib 19.5.1,
  Applied Mekanistics 1.6.3, MEGA Cells 4.11.0, AdvancedAE 1.6.12, AppliedFlux 2.1.5, Create 6.0.10, FTB Quests,
  177 KubeJS server scripts. Server pack `ServerFiles-8.2.zip`, CurseForge file 8945094, 1.22 GB, keyless CDN URL
  `https://mediafilez.forgecdn.net/files/8945/94/ServerFiles-8.2.zip` (pattern `files/{id/1000}/{id%1000}/{name}`).
  `user_jvm_args.txt` uses 4–8 GB. Pack sources: github.com/AllTheMods/ATM-10. All Rights Reserved.
- **Create Ultimate Selection 2** 12.4.0 (2026-10-03), NeoForge 21.1.234, 298 mods, Create 6.0.10, AE2 19.2.17,
  ExtendedAE 2.2.35, FTB Quests (13 chapters), light KubeJS. Server pack CurseForge file 9052043, 768 MB, keyless CDN.
  All Rights Reserved.
- **Craftoria** 1.37.0: kitchen-sink, Modern Industrialization, heavy KubeJS; its server pack is only a ServerStarter
  stub, so installing needs the CurseForge API (unverified).
- **GregTech:** no mature 1.21.1 pack. GTCEu's last 1.21.1 release is 7.0.2, which crashes on a dedicated server
  (`ClientLevel` loaded in `CommonInit.init`, issue GregTech-Modern#4155). The fix is only in the `latest-1.21`
  nightly (`gtceu-1.21.1-8.0.0-SNAPSHOT`), reportedly needing KubeJS. Small packs: GregTech Pack 2 (CurseForge, no
  server pack, bundles an 8.0.0 nightly), Operation: GregTech to Space (Modrinth mrpack, LGPL-3.0, a 7.5.2 snapshot
  older than the fix).
- Downloading ARR packs in CI for private testing is fine; never commit them or upload their jars as CI artifacts.

## AE2 addons (latest 1.21.1 NeoForge builds)

AE2 19.2.18 satisfies all of them (Data Energistics needs ≥19.2.18; Lightning Tech and Thunderbolt Core need <19.3).
NeoForge ≥21.1.241 (AE2 Pattern Disk).

| Addon | Version | Where | Licence | Needs | Touches (conflict risk) |
|---|---|---|---|---|---|
| ExtendedAE | 2.2.39 | CF 892005 (MR `extended-ae` lags at 2.2.35) | LGPL-3.0 | Glodium, GuideME | providers, interfaces, assembler (medium) |
| AE2 Extras | `0` (2026-05-05) | CF 421104:8045524 | CC0-1.0 | — | cells, crafting storage (low) |
| AE2 Pattern Disk | 0.8.0 | GitHub xingluo01/AE2-Pattern-Disk releases | LGPL-3.0 | AE2WTLib ≥19.5.0, GuideME | own pattern provider (low–medium) |
| AE2 WCWT | 1.3.10 | MR `ae2-wcwt` | MIT | AE2WTLib ≥19.4.1 | wireless terminal menus (low) |
| AE2WTLib | 19.5.1 | MR `applied-energistics-2-wireless-terminals` | MIT | AE2 [19.2.17,20) | library |
| AE2Recursion | 1.21.1-26.10.1 | CF 1708111:9034452 | All Rights Reserved, obfuscated | — | own pattern type; reflects into AE2 privates |
| AE2 Lightning Tech | 2.1.1 | MR `ae2-lightning-tech` | LGPL-3.0 | Thunderbolt Core [2.0.1,3) | 33 mixins (CPU, job, provider); Thunderbolt Core mixes into Grid, GridNode, pathing, NetworkStorage (high) |
| AE All Pattern | 0.2.6 | CF 1680054:8902411 | MIT | GuideME | CraftingService, NetworkCraftingProviders, calculation (high) |
| Neo ECO AE Extension | 21.2.0 | MR `neoecoae` | GPL-3.0-only | LDLib2 ≥2.2.8 | 36 mixins: crafting tree/plan/CPU, NetworkStorage (high) |
| Data Energistics | 3.3.3 | CF 1565514:9046322 | AGPL-3.0+, assets ARR | LDLib2 ≥2.2.40, AE2 ≥19.2.18 | 67 mixins over crafting, grid, storage (highest) |
| ExtendedAE-Plus | 1.6.3 | MR `extendedae-plus` | LGPL-3.0+ | ExtendedAE | CPU cluster, simulation, provider ticker, storage bus (high) |
| OmniSequence: Transfinite | 2.0.7 | CF 1624558:9043224 | MIT (jar) | ExtendedAE ≥2.2.32, Applied Enhancements ≥1.1.0, LDLib2 ≥2.2.18 | virtual CPU, me/Grid (high) |

CurseForge-only files can be fetched as `curse.maven:<slug>-<projectId>:<fileId>` or by CDN URL without a key.

## Running tests inside a production server

NeoForge disables GameTests in production: `GameTestHooks.isGametestEnabled()` and `isGametestServer()` both return
false when `FMLLoader.isProduction()`, so tests are not registered and `MinecraftServer.tickChildren` does not tick
`GameTestTicker`. Vanilla's API is still usable: a mod can call `GameTestRegistry.register(Class)`, build a
`GameTestRunner.Builder.fromBatches(...)`, and tick `GameTestTicker.SINGLETON` from its own server tick handler.

## CI timings (2026-10-04)

- quick.yml 1m25s (all GameTests in one batch server, 39 s).
- release.yml 7m49s (16 shards, one server per test, about 24 s per test).
- qualification.yml 1h14m, 68 min of it the unsharded one-server-per-test run; the Task 36 compat matrix itself is 3 min.
- One GameTest server boot is about 11 s; Gradle adds 10–15 s per invocation.

## Decisions (owner, 2026-10-04)

- GregTech is deferred until GTCEu has a 1.21.1 release that starts on a dedicated server.
- AE2 per profile: the build pin stays 19.2.17; addon profiles run AE2 19.2.18, modpacks their own AE2.
- AE2Recursion stays `BLOCKED_LICENSE` (All Rights Reserved), like Recursive AE2 Pattern Provider.
- Addon profiles run on every dev push in their own workflow; modpacks on release/hotfix branches, weekly and on
  manual dispatch.
- Modpacks: ATM10 (kitchen sink) and Create Ultimate Selection 2 (Create-centred).
- NeoForge runtime range widened from exactly `[21.1.250]` to `[21.1.216,)` (LDLib2 2.2.34's own floor) and FML from
  exactly `[4.0.44]` to `[4,)`: the exact pins kept 0.0.4 out of both packs. Build pin stays 21.1.250.

## Production compatibility runs (built 2026-10-04)

`tools/compat_run.py` + `tests/compat/{mods.json,profiles/}` + the `compatTestJar` test mod; documented in
`docs/compatibility/production-runs.md`. Lessons:

- Server packs are hard-linked per run from `packs/<sha16>/`; the zip can be dropped once extracted (CI deletes it
  before the cache is saved). ATM10 extracted is 1.4 GB, CUS2 0.8 GB, each NeoForge server install 0.18 GB.
- CUS2's server pack runs NeoForge 21.1.243 (its `variables.txt`), not the 21.1.234 the CurseForge page lists. Both
  packs lack LDLib2, so profiles add `ldlib2@2.2.41`.
- ATM10 takes about 2 min to load (ModernFix reports 128 s) and leaves non-daemon threads after `halt`, so the runner
  stops any server 30 s after its report appears. A crash fires `ServerStoppedEvent` but not `ServerStoppingEvent`;
  the test mod writes a partial report from either.
- Applied Flux mixes `initUpgrade` into the `PatternProviderLogic` constructor and adds an `IEnergyDistributor` node
  service; anything wrapping `IManagedGridNode.addService` must tolerate unknown services.
- MEGA Pattern Provider filters its slots to processing patterns. Advanced AE providers are not
  `PatternProviderLogicHost` but are AE2 `PatternContainer`s. AppMek keys: `#t=appmek:chemical`, `id=mekanism:hydrogen`.
- Create in a GameTest: a `create:creative_motor` with `FACING=UP` under a `create:millstone`, speed set through its
  public `generatedSpeed` ScrollValueBehaviour (`setValue(256)`); cobblestone mills to gravel in about 16 ticks.
- Timings (60-core host): 15 profiles with `-j 16` take 3m14s; addon profiles 51–70 s each, CUS2 147 s, ATM10 193 s.

## Addon tolerance audit (2026-10-04)

- AE2's `ManagedGridNode.addService` is a `putInstance` (a later service of the same class replaces the earlier one)
  and every node setter works after creation. The Federation Pattern Provider's `CapturedManagedGridNode` facade now
  matches that: configuration calls are accepted and left out, flags are forwarded only before the physical node
  exists, and a Lane keeps the first (AE2's own) ticker and crafting provider.
- `PatternProviderLogicTargetBinding` uses MixinExtras `@WrapOperation` (bundled in NeoForge from 21.1.216; the
  build already resolves `mixinextras-neoforge` 0.5.3), so another mod's wrapper of `ICraftingMachine.of` in
  `pushPattern` chains instead of conflicting.
- Client workaround mixins on GuideME/LDLib2 internals use `require = 0`; their targets exist in GuideME 21.1.15 and
  21.1.19 and LDLib2 2.2.34 and 2.2.41. Server compat runs never load client mixins.
- JUnit has the Minecraft classpath (`addModdingDependenciesTo(sourceSets.test)`), so classes naming Minecraft types
  can be unit-tested; it writes empty `neoforge-1.21.1/logs/`, which is ignored.
- A saved `PatternLaneMapping` loads into a Provider with a different Pattern slot count (an addon changing AE2's
  Pattern Provider slots): shared slots keep their Lanes, extra saved slots are dropped, new slots start unmapped. A
  save naming more Lanes than the Provider has still throws. The production Provider already sizes its mapping and
  composition from the native pattern inventory, so the composition's size check cannot fail there.
- Loading data into a running Federation Pattern Provider (`/data merge block`, tools writing into a placed block) no
  longer throws on a different Lane count. It keeps the live identity and Lane bindings, adds missing Lanes unbound
  (revision 1 via `retire()`, since `ProviderLaneIdentity` rejects revision 0), unmaps Patterns from unbound Lanes and
  marks bound Lanes left with no Pattern as pending release, as an unmap does. A Lane the data does not name keeps its
  native state. GameTest `provider.reload-in-place`.
- Not covered: a fresh load (structure placement, creative pick-block with block data) still copies the source
  Provider's identity and Lane bindings.
