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

Every profile runs the `core` group:

| Test | Checks |
|---|---|
| `federationCraftable` | Every Federation device is the output of at least one recipe, after the pack's recipe scripts. |
| `storageShare` | A Storage rule shares an AE2 cell between two networks joined by Routers and Federation Cable. |
| `remoteCrafting`, `remoteCraftingMaterials` | Pattern projection: the consumer's CPU crafts with the other network's Molecular Assembler pattern, with local or remote materials. |
| `processingThreeEndpoints` | One Federation Pattern Provider runs processing jobs through three Endpoints with Blocking mode. |
| `endpointSharedEnergy` | A claimed Endpoint powers its subnet from the Provider's network. |
| `remoteCraftingPlacedBlocks`, `remoteProcessingPlacedBlocks` | The crafting scene with AE2's own placed blocks. This is the control for each addon's variant. |
| `endpointFurnace` | The Federation Pattern Provider sends cobblestone through an Endpoint into a furnace; a hopper pushes the stone back into the Endpoint. This is the control for each mod machine's variant. |

An addon group repeats the placed-block scene with the addon's own blocks, or shares one of the addon's cells.
The `create` group processes through a real Create Millstone that a Creative Motor turns.

In the Endpoint machine scene, the Endpoint's subnet stores the delivered input through a Storage Bus on the
machine's input side, so the input goes straight into the machine. The product comes back the way a player would
build it: through a hopper under the machine, or by the machine's own auto-eject into the Endpoint.
The `mekanism` group does this with a Crusher. The `appmek` group does it with a Chemical Oxidizer, whose carbon is a
chemical. A Mekanism machine starts with every side disabled, and a bare Creative Energy Cube block is empty, so the
test sets the machine's sides and fills the cube the way a player's Configurator and the filled creative cube would.

## Addon profiles

All addon profiles use NeoForge 21.1.250, AE2 19.2.18, GuideME 21.1.19 and LDLib2 2.2.41. There are two exceptions:

- `baseline` uses the build pins, AE2 19.2.17 and LDLib2 2.2.34.
- `neoforge-min` uses the same pins on NeoForge 21.1.216, the oldest NeoForge the mod declares.

These profiles run on every push except to master.

| Profile | Addon versions | Addon tests | Result (2026-10-04) |
|---|---|---|---|
| `baseline`, `base-latest`, `neoforge-min` | — | core | 8/8 |
| `extendedae` | ExtendedAE 2.2.39 | Extended Pattern Provider and Extended Molecular Assembler crafting | 10/10 |
| `extendedae-plus` | ExtendedAE-Plus 1.6.3 | crafting with a 4× Crafting Accelerator; sharing a BigInteger cell | 12/12 |
| `data-energistics` | Data Energistics 3.3.3 | crafting and processing through the Adaptive Pattern Provider | 10/10 |
| `ae2-lightning-tech` | AE2 Lightning Tech 2.1.1, Thunderbolt Core 2.0.1 | crafting through the Overloaded Pattern Provider | 9/9 |
| `ae2-pattern-disk` | AE2 Pattern Disk 0.8.0, AE2WTLib 19.5.1 | crafting through the Pattern Disk Provider | 9/9 |
| `ae2-wcwt` | AE2 WCWT 1.3.10, AE2WTLib 19.5.1 | core only | 8/8 |
| `aeallpattern` | AE All Pattern 0.2.6 | core only | 8/8 |
| `neoecoae` | Neo ECO AE Extension 21.2.0 | core only | 8/8 |
| `mekanism` | Mekanism 10.7.19, Applied Mekanistics 1.6.3 | Endpoint into a Crusher; Endpoint into a Chemical Oxidizer returning a chemical; sharing a chemical cell | 12/12 |
| `omnisequence` | OmniSequence: Transfinite 2.0.7, ExtendedAE, Applied Enhancements 1.1.0 | the ExtendedAE group | 10/10 |
| `addons-all` | all of the above plus AE2 Extras | every addon group above, plus AE2 Extras' 1M crafting storage and 1M cell | 18/18 |

## Modpack profiles

Each modpack profile runs on the pack's own server files and NeoForge version. Only LDLib2 2.2.41 is added, because
neither pack ships it. A pack's recipe scripts and configs stay as they are. Packs are downloaded from the CurseForge
CDN and checked against their pinned SHA-512.

| Profile | Pack | NeoForge, AE2 | Groups | Result (2026-10-04) |
|---|---|---|---|---|
| `atm10` | All the Mods 10 8.2, 464 mods | 21.1.251, 19.2.17 | core, extendedae, advanced-ae, megacells, appmek, create | 16/16 |
| `cus2` | Create Ultimate Selection 2 12.4.0, 360 mods | 21.1.243, 19.2.18 | core, extendedae, appmek, create | 12/12 |

The pack-only groups cover:

- `advanced-ae`: crafting through Advanced AE's Advanced Pattern Provider.
- `megacells`: processing through the MEGA Pattern Provider, crafting with a MEGA 1M Crafting Storage CPU, and
  sharing a MEGA 1M cell.
- `appmek`: sharing hydrogen in an Applied Mekanistics chemical cell.

ATM10 needs about two minutes to load and 8 GB of heap. A test run takes about three minutes.

## Findings

The first modpack runs found two bugs in AE2 Federation 0.0.4, both fixed on dev:

- **It did not load in either pack.** The mod required exactly NeoForge 21.1.250 and FML 4.0.44, so any other NeoForge
  build refused it. It now requires NeoForge 21.1.216 or newer, the oldest that LDLib2 2.2.34 supports, and FML 4.
- **Applied Flux crashed the server.** Applied Flux adds an energy distributor service to every AE2 Pattern Provider.
  The Federation Pattern Provider's lanes rejected any service they did not know, so loading a Federation Pattern
  Provider beside Applied Flux crashed the server. Lanes now leave addon services out, so Applied Flux's energy
  distribution does not work on a Federation Pattern Provider.

The Endpoint machine scene found one more, also fixed on dev:

- **An Endpoint could not take back chemicals.** Applied Mekanistics adds Mekanism's chemical handler to every block
  that exposes AE2's generic internal inventory, as AE2's own Pattern Provider does for its return inventory. The
  Endpoint exposed only item and fluid returns, so a Mekanism machine could not eject a chemical into it. It now
  exposes its return inventory that way on its five logistics faces.

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
- **OmniSequence's infinite crafting storage** is part of the Omni-Computation Core multiblock and forms no CPU on its
  own. Its CPU mixins apply to AE2's own CPUs, which `core` covers.
- **AE2Recursion** is **BLOCKED_LICENSE**: All Rights Reserved and obfuscated, like Recursive AE2 Pattern Provider.
- **GregTech** is deferred. GTCEu 7.0.2, the last 1.21.1 release, fails on a dedicated server (see the
  [matrix](matrix.md)), and no mature 1.21.1 GregTech pack exists yet.

## Running locally

Starting a server accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA), so a run requires `--accept-eula`.
Downloads are cached in `$AE2F_COMPAT_CACHE`, which defaults to `~/.cache/ae2federation-compat`.

```bash
python3 tools/compat_run.py --list
python3 tools/compat_run.py --accept-eula --group addons -j 12   # every addon profile, about 70 s
python3 tools/compat_run.py --accept-eula --group modpacks -j 2     # both packs, about four minutes after download
python3 tools/compat_run.py --accept-eula extendedae --tests extendedae
python3 tools/compat_run.py --accept-eula --bare addons-all     # the same mods without AE2 Federation
```

A server that keeps running more than 30 seconds after writing its report is stopped; some pack mods leave threads
behind. Each run directory, including `server-console.log` and `compat-report.json`, is under
`neoforge-1.21.1/build/compat/runs/<profile>`. In CI, the `Compatibility` workflow runs one job per profile and
summarises the reports in the job summary. Modpack profiles run there on release and hotfix branches, weekly, and when
the workflow is dispatched with `modpacks`.
