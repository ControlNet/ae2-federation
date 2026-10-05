# Production Compatibility Runs

These runs put AE2 Federation's built jar into a production NeoForge 1.21.1 server next to other mods. They then check
that Federation's features still work there. They complement the [pinned matrix](matrix.md): that matrix qualifies
single capability paths with the development test mod, and these runs cover whole addon sets and modpacks. No optional
mod is bundled with AE2 Federation, and no third-party jar is committed or uploaded as a CI artifact.

## How a run works

`tools/compat_run.py` reads a profile from `tests/compat/profiles/<name>.json`. Each profile names:

- a NeoForge version;
- optionally a modpack server zip;
- mods by their key in `tests/compat/mods.json`, each a pinned URL and SHA-512;
- the test groups to run.

The runner then:

1. assembles a fresh server directory;
2. adds AE2 Federation and the compatibility test mod (`ae2federation_compat`, built by `compatTestJar`);
3. starts the server and reads the test mod's JSON report.

NeoForge does not run GameTests in production. To work around this, the test mod registers its tests with vanilla's
GameTest API and ticks them from its own server tick handler. Then it writes the report and stops the server. The test
mod has no mixins, because the addons mix into the same AE2 classes that the development test mod does.

Tests run side by side, 32 at a time by default (`-p`). Each gets a cell of whole chunks with room around its template,
because most scenes build well past it. A test marked `@RunsAlone`, such as the solar observatory, which sets the time
of day, runs by itself. A test that fails only beside others can be run again with `-p 1`, which runs one test at a
time.

Every profile runs the `core` group:

| Test | Checks |
|---|---|
| `federationCraftable` | Every Federation device is the output of at least one recipe, after the pack's recipe scripts. |
| `storageShare` | A Storage rule shares an AE2 cell between two networks joined by Routers and Federation Cable. |
| `storageBusChest`, `storageBusTank` | The same rule shares a chest and an AE2 Sky Stone Tank of water through AE2's Storage Bus. This is the control for each mod container's variant. |
| `fluidCellShared` | The same rule shares water in an AE2 fluid cell. |
| `extractOnlyRule` | A rule without Insert lets the consumer take from the cell but refuses its store-back. |
| `remoteCrafting`, `remoteCraftingMaterials` | Pattern projection: the consumer's CPU crafts with the other network's Molecular Assembler pattern, with local or remote materials. |
| `processingThreeEndpoints` | One Federation Pattern Provider runs processing jobs through three Endpoints with Blocking mode. |
| `endpointSharedEnergy` | A claimed Endpoint powers its subnet from the Provider's network. |
| `remoteCraftingPlacedBlocks`, `remoteProcessingPlacedBlocks` | The crafting scene with AE2's own placed blocks. This is the control for each addon's variant. |
| `remoteProcessingCancel` | The consumer cancels a projected job after the push: the late output stays on the provider network and nothing stays owed. This is the control for each addon CPU's variant. |
| `remoteProcessingDisconnected` | The Bridge is removed after the push: the pattern leaves the consumer, the output stays on the provider network, and the consumer's job keeps waiting with its debt still owed. This is the control for each addon CPU's variant. |
| `endpointLocalFurnace`, `endpointLocalPatternProviderPart` | Another network's AE2 Pattern Provider, as a block or as a cable part, touches an Endpoint's Federation face and runs the furnace job in Local mode. This is the control for each addon provider's variant. |
| `patternAccessTerminal` | After a job through an Endpoint, AE2's Pattern Access Terminal lists the Federation Pattern Provider once, with its pattern, and none of its lanes. |
| `endpointFurnace` | The Federation Pattern Provider sends cobblestone through an Endpoint into a furnace; a hopper pushes the stone back into the Endpoint. This is the control for each mod machine's variant. |
| `guideSceneCables` | Every guide scene the loaded mods can show is placed in the level; each cable's connections and per-side channel counts match AE2's own. |

Every storage test stores on the provider network, checks the consumer sees exactly that, takes some from the
consumer, and stores it back from the consumer. The consumer network has no storage of its own, so what it stores can
only go through the rule. When a rule mounts nothing, the failure names the reason the rule reports.

An addon group repeats the placed-block scene with the addon's own blocks, shares one of the addon's cells, or shares
one of the mod's containers through a Storage Bus.

In the Endpoint machine scene, the Endpoint's subnet stores the delivered input through a Storage Bus on the
machine's input side, so the input goes straight into the machine. The product comes back the way a player would
build it: through a hopper or a Create Chute under the machine, or by the machine's own auto-eject into the
Endpoint.

Cross-network crafting is tested along both of Federation's paths with the addons' own crafting machinery:

- **Pattern projection.** The consumer's CPU uses a pattern held by an addon provider on the other network: providers
  built on AE2's provider logic (Extended, Overloaded, MEGA), providers with their own logic (Advanced, Pigmee,
  Pattern Disk, Adaptive), multiblocks (ExtendedAE's Assembler Matrix, Neo ECO's crafting system), and
  OmniSequence's array, which crafts inside itself in batches. The pattern runs a real
  machine where the mod has one. With ExtendedAE-Plus' Smart Doubling on the provider, the test also checks that the
  consumer's plan really uses the scaled patterns.
- **Federation Pattern Provider and Endpoint.** Real machines from Mekanism, Create and ExtendedAE work behind an
  Endpoint and return items, fluids and chemicals into it. With ExtendedAE-Plus' Smart Doubling on the Federation Pattern Provider, its lanes' patterns carry the
  same Smart Doubling mark as the pattern in a plain AE2 Pattern Provider.
- **Addon CPUs on both paths.** A lone Quantum Computer Core, a Transfinite Compute Nexus or a Pigmee Mental Math Unit
  is the network's only CPU. Each runs jobs with its own CPU logic instead of AE2's, and each also cancels a projected
  job after the push and keeps waiting when the Bridge is removed after the push.
- **Local mode.** The Extended, Advanced, Overloaded, Pigmee, Pattern Disk, Adaptive and MEGA providers run an
  Endpoint in Local mode, as AE2's own does, and so do the cable-part forms of AE2's, ExtendedAE's and Advanced AE's
  providers.
- **Blocking modes.** ExpandedAE's Smart and Default blocking work on a Federation Pattern Provider's lanes as on a
  native provider. ExpandedAE ships only in ATM10, so `atm10` runs this test.

Some mods need setup that a player does by hand, and the tests do the same through the mod's own classes:

- A Mekanism machine or tank starts with every side disabled, and a bare Creative Energy Cube block is empty. The
  tests set the sides, as the Configurator does, and fill the cube, as the filled creative cube is when placed.
- ExtendedAE's tag and mod storage buses get their filter, as their screens set it.
- A MEGA Bulk Cell is partitioned to iron, as in the Cell Workbench.
- ExtendedAE's Circuit Slicer gets auto-export down into the Endpoint, and ExtendedAE-Plus' Smart Doubling is turned
  on, as their screens set them.

## Addon profiles

All addon profiles use NeoForge 21.1.250, AE2 19.2.18, GuideME 21.1.19 and LDLib2 2.2.41. There are two exceptions:

- `baseline` uses the build pins, AE2 19.2.17 and LDLib2 2.2.34.
- `neoforge-min` uses the same pins on NeoForge 21.1.216, the oldest NeoForge the mod declares.

These profiles run on every push except to master.

As the guide's examples build them, one network brings the power: in every addon crafting scene the provider network
has no energy cell and runs on the consumer's through the ME power rule, and in every addon storage scene the consumer
runs on the provider's. Only the scenes that remove the Bridge after the push keep a cell on the provider network.

| Profile | Mod versions | Mod tests | Result (2026-10-06) |
|---|---|---|---|
| `baseline`, `base-latest`, `neoforge-min` | — | core | 19/19 |
| `extendedae` | ExtendedAE 2.2.39 | crafting and processing through the Extended Pattern Provider; crafting with the Extended Molecular Assembler and with an Assembler Matrix, which also gives up and regains its recipes when a wall is broken and put back; Endpoint into a Circuit Slicer; an Endpoint in Local mode under the Extended Pattern Provider, as a block and as a part; the Bridge removed after the push; tag and mod storage buses; Infinity Cobblestone Cell | 31/31 |
| `extendedae-plus` | ExtendedAE-Plus 1.6.3 | the ExtendedAE group; crafting with a 4× Crafting Accelerator, and again after it moves to the provider network's CPU; processing with Smart Doubling, through projection and through an Endpoint; the Smart Doubling mark on a plain AE2 Pattern Provider; sharing a BigInteger cell; a Super Assembler Matrix on a Router serving two networks' orders at once | 38/38 |
| `extendedae-plus-aeallpattern` | ExtendedAE-Plus 1.6.3, AE All Pattern 0.2.6 | the ExtendedAE-Plus group, which here checks that no Smart Doubling mark is set | 26/26 |
| `data-energistics` | Data Energistics 3.3.3 | crafting and processing through the Adaptive Pattern Provider; crafting again after an AE2 Pattern Provider is upgraded into one in place and a provider is fitted; an Endpoint in Local mode under it; an Astronomical Observatory making Stellar Flux at night on a solar outpost's ME power, read through a Storage rule, and stopping when its power rule is off | 24/24 |
| `ae2-lightning-tech` | AE2 Lightning Tech 2.1.1, Thunderbolt Core 2.0.1 | crafting and processing through the Overloaded Pattern Provider, and crafting again after an AE2 Pattern Provider is upgraded into one in place; crafting through the Pigmee Pattern Provider; an Endpoint in Local mode under each; a Pigmee Mental Math Unit as the only CPU, including a cancelled job and the Bridge removed after the push; a Tianshu Supercomputer ordering from a Matter Warping Matrix on a network without storage, with a casing block broken and put back | 30/30 |
| `ae2-pattern-disk` | AE2 Pattern Disk 0.8.0, AE2WTLib 19.5.1 | crafting and processing through the Pattern Disk Provider; an Endpoint in Local mode under it | 22/22 |
| `ae2-wcwt` | AE2 WCWT 1.3.10, AE2WTLib 19.5.1 | core only | 19/19 |
| `aeallpattern` | AE All Pattern 0.2.6 | core only | 19/19 |
| `neoecoae` | Neo ECO AE Extension 21.2.0 | crafting with a crafting system, with an AE2 CPU and with a computation system as the consumer's only CPU; the guide's district, with the storage and computation systems on the consumer and the crafting system on a provider with no storage, whose recipes leave and return when a casing is broken and put back; sharing a storage system's ECO cell, which also leaves and returns when a casing is broken and put back | 24/24 |
| `omnisequence` | OmniSequence: Transfinite 2.0.7, ExtendedAE, Applied Enhancements 1.1.0 | the ExtendedAE group; a Transfinite Compute Nexus as the only CPU, including a cancelled job and the Bridge removed after the push; crafting inside a Molecular Sequence Rewrite Array; a Matter Fabrication Well's Pattern Assembly serving a remote order, whose recipe leaves and returns when a casing is broken and put back | 37/37 |
| `advanced-ae` | AdvancedAE 1.6.12, GeckoLib 4.9.3 | crafting and processing through the Advanced Pattern Provider; an Endpoint in Local mode under it, as a block and as a part; a lone Quantum Computer Core as the only CPU, including a cancelled job and the Bridge removed after the push; a formed 7x7x7 Quantum Computer on a Router running two orders at once from two other networks, one needing both in turn | 28/28 |
| `megacells` | MEGA Cells 4.11.0 | processing through the MEGA Pattern Provider; an Endpoint in Local mode under it; crafting with a MEGA 1M Crafting Storage CPU; sharing MEGA item, bulk and fluid cells | 25/25 |
| `mekanism` | Mekanism 10.7.19, Applied Mekanistics 1.6.3 | a Crusher, also as the guide builds it, its subnet powered through the Endpoint and its top switched off during a job; an Enrichment Chamber and an Energized Smelter behind two Endpoints of one Provider, turning iron ore into ingots in turn, with the Smelter's hopper taken away during a job; a Chemical Oxidizer returning a chemical, also as the guide builds it with its auto-eject off during a job and a Nutritional Liquifier returning a fluid, each both through an Endpoint and behind the other network's AE2 Pattern Provider; a Basic Bin, a Basic Fluid Tank and a Basic Chemical Tank through a Storage Bus; sharing a chemical cell; the bin and the chemical cell under a rule without Insert; an Induction Matrix behind an Energy Acceptor powering two other networks over ME power, with no energy cell, until one network's power rule is switched off | 35/35 |
| `create` | Create 6.0.10 | processing through a Millstone; Endpoint into Crushing Wheels, also as the guide builds it with the wheels stopped during a job; an Item Vault and a Fluid Tank through a Storage Bus | 24/24 |
| `storage-mods` | Sophisticated Storage 1.6.1, Functional Storage 1.5.7 | a Sophisticated Storage chest and a Functional Storage drawer through a Storage Bus | 21/21 |
| `appflux` | Applied Flux 2.1.5 | sharing FE in an FE cell; the Federation Pattern Provider's upgrade slots, as many as AE2's Pattern Provider has, taking and keeping the Induction Card and refusing a card AE2's provider refuses | 21/21 |
| `appflux-mekanism` | Applied Flux 2.1.5, Mekanism 10.7.19 | the Applied Flux group; a Flux Accessor running a Mekanism Crusher on FE shared from another network's FE cell, which stops drawing once the Storage rule is off; the Induction Card in a Federation Pattern Provider powering a Crusher touching it, and no FE sent before the card goes in; the card powering a Crusher that sits on the Provider's Endpoint with no power of its own; the card's FE reaching an energy cube beside an Endpoint across Federation Cables and a Router, and stopping once that Endpoint leaves the domain | 25/25 |
| `addons-all` | the mods of the profiles from `extendedae` to `omnisequence` except AE All Pattern, plus AE2 Extras | their groups, plus AE2 Extras' 1M crafting storage and 1M cell | 70/70 |

## Modpack profiles

Each modpack profile runs on the pack's own server files and NeoForge version. Only LDLib2 2.2.41 is added, because
neither pack ships it. A pack's recipe scripts and configs stay as they are. Packs are downloaded from the CurseForge
CDN and checked against their pinned SHA-512.

| Profile | Pack | NeoForge, AE2 | Groups | Result (2026-10-05) |
|---|---|---|---|---|
| `atm10` | All the Mods 10 8.2, 464 mods | 21.1.251, 19.2.17 | core, extendedae, advanced-ae, megacells, appmek, create, mekanism, sophisticated-storage, functional-storage, appflux, appflux-mekanism, expandedae | 72/72 |
| `cus2` | Create Ultimate Selection 2 12.4.0, 360 mods | 21.1.243, 19.2.18 | core, extendedae, appmek, create, mekanism, sophisticated-storage | 53/53 |

ATM10 needs about two minutes to load and 8 GB of heap. A test run takes about six minutes.

## Findings

The first modpack runs found two bugs in AE2 Federation 0.0.4, both fixed on dev:

- **It did not load in either pack.** The mod required exactly NeoForge 21.1.250 and FML 4.0.44, so any other NeoForge
  build refused it. It now requires NeoForge 21.1.216 or newer, the oldest that LDLib2 2.2.34 supports, and FML 4.
- **Applied Flux crashed the server.** Applied Flux adds an energy distributor service to every AE2 Pattern Provider.
  The Federation Pattern Provider's lanes rejected any service they did not know, so loading a Federation Pattern
  Provider beside Applied Flux crashed the server. Lanes now leave addon services out; the Provider's owner logic
  hands them to the Provider's node once, as AE2's own Pattern Provider has them, and the Provider has the upgrade
  slots Applied Flux gives AE2's, so its Induction Card powers machines touching the Provider and, through its
  Federation face, machines touching the Endpoints it holds.

The Endpoint machine scene found one more, also fixed on dev:

- **An Endpoint could not take back chemicals.** Applied Mekanistics adds Mekanism's chemical handler to every block
  that exposes AE2's generic internal inventory, as AE2's own Pattern Provider does for its return inventory. The
  Endpoint exposed only item and fluid returns, so a Mekanism machine could not eject a chemical into it. It now
  exposes its return inventory that way on its five logistics faces.

The interaction tests found four more, all fixed on dev:

- **Addon hooks on AE2's pattern refresh skipped the Federation Pattern Provider.** Its lanes read their patterns
  themselves instead of through AE2's `updatePatterns`. So AE2 Lightning Tech's rule for overload patterns, AE All
  Pattern's aggregate expansion and ExtendedAE-Plus' Smart Doubling never applied to them. Lanes now refresh through
  AE2's own `updatePatterns`, and when an addon runs that refresh again by itself, the network sees the result at once.
  `smartDoublingEndpoint` checks this with ExtendedAE-Plus. A dev GameTest checks a dropped pattern and an addon's own
  refresh with a test-only stand-in for the addon hook.
- **Local mode accepted only block-form providers built on AE2's provider block entity.** AE2's cable-part provider
  and the providers of Advanced AE, Pigmee, Pattern Disk and Data Energistics could not run an Endpoint in Local mode.
  An Endpoint now takes as its Local provider whatever offers AE2's crafting-provider service on its grid node, block
  or part, and returns through the inventory the provider offers to the blocks around it.
- **Local returns through AE2 Lightning Tech's Overloaded provider were duplicated.** Its logic's return inventory is
  a paged view whose insert copies the stack, so a machine's two outputs arrived as three. The Endpoint now returns
  through the provider's own outward return inventory, as a machine beside it does.
- **ExpandedAE's Smart blocking was reversed on lanes.** ExpandedAE builds every provider's targets with its own
  blocking modes, but lanes built theirs with AE2's, so Smart blocking refused the same pattern and let others in.
  Lanes now build their targets through ExpandedAE's when it is loaded. `smartBlockingLane` checks Smart and Default.

Other findings, all upstream behaviour:

- **AE2 Extras** (version `0`) crashes on its own with AE2: `Adding duplicate value MenuType` in AE2's menu
  registration. It also crashes without AE2 Federation, with AE2WTLib or ExtendedAE added, and on AE2 19.2.17. It boots
  inside `addons-all`, so it is tested only there. This is an upstream load-order problem.
- **Data Energistics' Adaptive Pattern Provider** takes patterns only after an AE2 Pattern Provider is fitted into its
  provider slot. Its slot count comes from the fitted providers. Its digital storage cell refused inserts even on its
  own network, so the cell is not tested.
- **MEGA Cells' Pattern Provider** accepts processing patterns only.
- **Advanced AE's providers** have their own provider logic. The test reaches them through AE2's Pattern Access
  Terminal interface.
- **AE2 Pattern Disk's provider** keeps patterns on Pattern Disks, so the test writes the pattern to a disk first.
  `meteorite_pattern_provider` has a blockstate but no registered block.
- **ExtendedAE's Assembler Matrix** takes crafting patterns only. The tests wait for it to form before fitting one.
- **Neo ECO's multiblocks** connect to cable only through their interface block. The tests wait for a storage system
  to form before fitting its cell.
- **OmniSequence's infinite crafting storage** is part of the Omni-Computation Core multiblock and forms no CPU on its
  own. Its CPU mixins apply to AE2's own CPUs, which `core` covers.
- **AE All Pattern turns off ExtendedAE-Plus' Smart Doubling.** AE All Pattern replaces AE2's `updatePatterns` outright.
  ExtendedAE-Plus marks patterns at the end of that method, so with both loaded no provider's patterns get the mark
  and no plan scales. This happens without AE2 Federation's providers too: the `extendedae-plus-aeallpattern` profile
  loads only those two mods and AE2's own Pattern Provider gets no mark. Since the two mods do not work together,
  `addons-all` leaves AE All Pattern out. It runs alone in `aeallpattern` and beside ExtendedAE-Plus in
  `extendedae-plus-aeallpattern`.
- **AE2Recursion** is **BLOCKED_LICENSE**: All Rights Reserved and obfuscated, like Recursive AE2 Pattern Provider.
- **GregTech** is deferred. GTCEu 7.0.2, the last 1.21.1 release, fails on a dedicated server (see the
  [matrix](matrix.md)), and no mature 1.21.1 GregTech pack exists yet.

## Running locally

Starting a server accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA), so a run requires `--accept-eula`.
Downloads are cached in `$AE2F_COMPAT_CACHE`, which defaults to `~/.cache/ae2federation-compat`.

```bash
python3 tools/compat_run.py --list
python3 tools/compat_run.py --accept-eula --group addons -j 16   # every addon profile, about two minutes
python3 tools/compat_run.py --accept-eula --group modpacks -j 2     # both packs, about four minutes after download
python3 tools/compat_run.py --accept-eula extendedae --tests extendedae
python3 tools/compat_run.py --accept-eula -p 1 extendedae          # one test at a time
python3 tools/compat_run.py --accept-eula --bare addons-all     # the same mods without AE2 Federation
```

A server that keeps running more than 30 seconds after writing its report is stopped; some pack mods leave threads
behind. Each run directory, including `server-console.log` and `compat-report.json`, is under
`neoforge-1.21.1/build/compat/runs/<profile>`. In CI, the `Compatibility` workflow runs one job per profile and
summarises the reports in the job summary. Modpack profiles run there on release and hotfix branches, weekly, and when
the workflow is dispatched with `modpacks`.
