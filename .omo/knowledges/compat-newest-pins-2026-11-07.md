# Moving compat profiles to the newest releases (2026-11-07)

How the pins in `tests/compat/mods.json` were brought to the newest 1.21.1 NeoForge releases, and what to reuse next time.

## Finding the newest versions

- Modrinth: `GET https://api.modrinth.com/v2/project/<id>/version?loaders=["neoforge"]&game_versions=["1.21.1"]`,
  sort by `date_published`, keep `version_type == "release"`. Use the project id from the pinned CDN URL
  (`cdn.modrinth.com/data/<id>/...`). Titanium, Ender IO and Functional Storage publish betas only, so the newest beta
  is pinned; JEI and AE2 Lightning Tech stay on their newest release rather than newer betas.
- `python3 tools/compat_run.py pin modrinth <id> <version>` prints an entry, but names it after the argument and keeps
  Modrinth's long `version_number` (for example `mc1.21.1-2.2.45-neoforge`): keep the old entry's `name` and the short
  version used in its key.
- CurseForge: the search endpoint answers 403, but `https://api.cfwidget.com/minecraft/mc-mods/<slug>` returns the
  project id without a key, and `https://www.curseforge.com/api/v1/mods/<project>/files?pageIndex=0&pageSize=50&sort=dateCreated&sortDescending=true`
  lists files (`releaseType` 1 = release, 2 = beta; check `gameVersions` holds both `1.21.1` and `NeoForge`; ExtendedAE
  and AE2 Extras also publish MC 26.1 files). The CDN URL is `https://mediafilez.forgecdn.net/files/<id // 1000>/<id % 1000>/<file>`;
  `pin url <that URL>` downloads it and gives the SHA-512.
- Project ids: Data Energistics 1565514 (slug `data-energistics`), ExtendedAE 892005 (`ex-pattern-provider`),
  OmniSequence: Transfinite 1624558, Applied Enhancements 1665696, AE All Pattern 1680054 (`ae-all-pattern`),
  AE2 JEI Integration 1074338, AE2 Extras 421104 (`ae2-extras`); packs: ATM10 925200, CUS2 1324409, NAST 1582045.
- GitHub releases: `gh release list -R xingluo01/AE2-Pattern-Disk`, `gh release list -R SorrowMist/UselessMod`
  (its `20.x` tags are 1.20.1 backports; only `1.21.1-*` tags apply).

## Modpacks

`python3 tools/compat_run.py latest --group modpacks` shows which packs moved. `latest_pack(profile)` in
`tools/compat_run.py` downloads and unpacks the new server pack into the cache and returns the `pack` entry (name, file,
url, sha512) and the pack's NeoForge version, without running tests; the pinned run then reuses the unpacked cache. The
unit-test fixtures in `tests/compat/curseforge/` are trimmed recordings of the same API; re-record them with the same
fields and update `tests/test_compat_run.py`'s expectations.

## Check before running

Read each new jar's `META-INF/neoforge.mods.toml`: a new required dependency (Data Energistics 3.4.0 requires
AE2WTLib) or a raised range (Neo ECO Prototype 1.4.6 needs Neo ECO 21.2.15) breaks loading before any test runs.

## What broke on 2026-11-07

- Data Energistics 3.4.0 removed `PatternProviderRegistry.registerFactory`, `CountedCraftingCapacity.aggregateUnknown()`
  and the default `prepareBatchForTarget`, and changed the list components of `ExternalPatternProviderIdentity` and
  `PatternProviderMetadata` from `List` to `ObjectList`. Our plugin (`DataEnergisticsCountedProjections`) now uses
  `register(PatternProviderRegistration.counted(...))`, builds the capacity with its constructor, answers
  `prepareBatchForTarget` itself and builds the two records through their canonical constructors, so one jar links
  against 3.3 and 3.4. It still compiles against 3.3.5. 3.4.0 also moved `StellarFluxKey` to
  `integration.ae.ae2.key`.
- Data Energistics 3.4.0 regression, upstream: `AdaptivePatternProviderLogic.readFromNBT` calls
  `ConnectorMode.valueOf(tag.getString("connector_mode"))` with no default (3.3.5 passed `ConnectorMode.INPUT`), and
  its upgrade item (`BlockAndPartUpgradeItem`) loads the plain AE2 provider's saved data, which has no connector mode, into the
  new Adaptive provider. Every in-place upgrade of an AE2 Pattern Provider throws, so `adaptiveupgradeinplace` fails in
  `data-energistics`, `addons-all` and `useless-mod-addons`. The upstream fix is the 3.3.5 default. In
  `useless-mod-addons` that throw also stalls `localendpointproviderturned` in the same batch (it passes without
  `adaptiveupgradeinplace` beside it, and in profiles without UselessMod).
- UselessMod 2.4.6.5 declares `data_energistics [3.4.0,)` and `extendedae_plus [1.6.4,)`, and 2.4.6.2 crashes on
  Data Energistics 3.4.0's moved recipe classes, so `useless-mod-addons` cannot keep Data Energistics 3.3.5.
- Neo ECO 21.2.0's mixin `ldlib.AsyncThreadDataMixin` fails to inject into LDLib2 2.2.45, so `neoecoae-21.2.0`
  keeps LDLib2 2.2.44.
- `PlaythroughDataReassemblerAdapter` still names Data Energistics 3.3's `recipe.reassembler` classes; 3.4.0 moved them
  to `api.recipe.reassembler` and `common.recipe.reassembler`. NAST still ships 3.3.5, so nothing runs it on 3.4 yet.

## Decision (owner, 2026-11-07): Data Energistics stays on 3.3.5
Asked whether to pin 3.4.0 with red profiles, mark the failure, or roll back, the owner chose to roll back in the three
profiles that carry Data Energistics (`data-energistics`, `addons-all`, `useless-mod-addons`). Knock-on pins, found by
running them: Data Energistics 3.3.5's mixin `ae.extendedaeplus.EaepPatternEncodingTermMenuMixin` calls
`IPatternEncodingShiftUploadSync`, which ExtendedAE-Plus 1.6.4.1 removed, so those profiles keep ExtendedAE-Plus 1.6.3;
UselessMod 2.4.6.5 needs Data Energistics 3.4.0, so `useless-mod-addons` keeps UselessMod 2.4.6.2. Results: 29/29,
130/130, 117/117. Profiles without Data Energistics stay on ExtendedAE-Plus 1.6.4.1 and UselessMod 2.4.6.5. Our plugin
still works on both 3.3.5 and 3.4.0. Move these up once Data Energistics restores the connector-mode default.

Reported upstream: https://github.com/ModularMCLib/DataEnergistics/issues/431 (still unfixed on the `1.21` branch at
ec06088). Source: https://github.com/ModularMCLib/DataEnergistics.
