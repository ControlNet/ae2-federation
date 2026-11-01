# Minimum dependency versions

Investigation date: 2026-09-27. Target: Minecraft 1.21.1 / NeoForge 21.1.250.

| Dependency | Runtime requirement | Reproducible build pin | Boundary |
| --- | --- | --- | --- |
| Applied Energistics 2 | `[19.2.9,)` | 19.2.17 | Federation Provider Lanes require global crafting providers |
| LDLib2 | `[2.2.34,)` | 2.2.34 | The production graph zoom controls require `GraphView.setScale(float)` |
| NeoForge | `[21.1.216,)` | 21.1.250 | LDLib2 2.2.34 itself requires NeoForge 21.1.216 |

Runtime ranges are defined in `gradle.properties` independently of the build
pins and expanded into both production and testmod metadata. Minecraft and Java
requirements are unchanged. An open upper range permits a newer dependency to
load; it does not certify every future release.

NeoForge was pinned to exactly 21.1.250, and the FML loader to exactly 4.0.44,
until 2026-10-04. That stopped the mod from loading in modpacks on any other
NeoForge build, such as All the Mods 10 (21.1.251) and Create Ultimate
Selection 2 (21.1.243). The lower bound is now the one LDLib2 2.2.34 declares,
since nothing older can load that dependency. The loader range is FML 4, the
line every NeoForge 21.1 build ships. The `neoforge-min` production
compatibility profile runs the core tests on NeoForge 21.1.216 (see
[production runs](production-runs.md)).

## Why these lower bounds

AE2's [19.2.9 release](https://github.com/AppliedEnergistics/Applied-Energistics-2/releases/tag/neoforge%2Fv19.2.9)
introduced global crafting providers independent of grid nodes. Federation uses
`ICraftingService.addGlobalCraftingProvider`, `removeGlobalCraftingProvider`
and `refreshGlobalCraftingProvider` in `NativeProviderLaneComposition`.
The official tagged interfaces from 19.0.27, 19.2.4, 19.2.7 and 19.2.8 lack
these methods; 19.2.9 through 19.2.17 contain them. Compilation against 19.2.8
fails in this production integration. Compilation of production code against
19.2.9 succeeds without changing gameplay code.

LDLib2's published 2.2.20, 2.2.30 and 2.2.33 binaries lack the graph scale
setter; the immediately following 2.2.34 binary contains it. Compiling the
current production UI against both 2.2.20 and 2.2.33 fails at the zoom buttons
in `FederationDomainPolicyMenuHolder`. Version 2.2.0 also lacks other required
UI/layout APIs, including `VirtualScrollerView`. Supporting older LDLib2 would
require UI adaptation, rather than only changing a dependency declaration.
Artifacts were obtained from the [upstream Maven publication](https://maven.firstdark.dev/snapshots/com/lowdragmc/ldlib2/ldlib2-neoforge-1.21.1/).
The inspected 2.2.20, 2.2.30 and 2.2.33 JAR SHA-256 values match their published
Gradle module metadata.

## Test harness boundary

Some crafting test fixtures use AE2's newer `CraftingCpuLogic.setJobSuspended`
method to hold jobs while inspecting intermediate state. That method is absent
in 19.2.9 and is not called by production code. Consequently, compiling the
entire testmod against 19.2.9 fails even though production compilation succeeds.
Do not raise the runtime minimum solely to satisfy this test-only method.

For runtime checks, compile the unchanged production and testmod classes with
the regular pins first, then switch the runtime to 19.2.9 while excluding those
two compilation tasks. The selected scenarios must not use that newer fixture
operation. This also exercises bytecode built with the regular dependency pins
against the older dependency.

```sh
./gradlew :neoforge-1.21.1:classes :neoforge-1.21.1:testmodClasses \
  --dependency-verification=strict --no-configuration-cache --no-daemon
./gradlew :neoforge-1.21.1:runGameTestServer \
  -Pae2_version=19.2.9 -PfederationGameTestId=lanenativethreeway \
  -x :neoforge-1.21.1:compileJava -x :neoforge-1.21.1:compileTestmodJava \
  --dependency-verification=strict --no-configuration-cache --no-daemon
```

Expected: the resolved mod list reports AE2 19.2.9, exactly one required
GameTest passes, and Gradle exits zero. Do not run a production build between
these two commands. Exploratory negative compilations used an isolated source
copy under ignored `build/dependency-floor/checkout`; no user worlds were used.
Only the accepted AE2 19.2.9 artifacts were added to the repository's strict
dependency verification metadata, after comparison with Maven Central bytes.

## Observed verification

The minimum tuple above passed eight actual GameTest server runs, each with
exactly one required test and exit zero:

- `harnessnativesmoke`
- `lanenativethreeway`
- `lanerejectlocalfallback`
- `processingnativedifferential`
- `storagenativeaccess`
- `identitypartonsettledcable`
- `energydirectionalpolicy` (the directional energy case of that time; energy is now shared through
  `energysharedmutual` and its siblings, which this floor run did not cover)
- `scalesmallprocessingdevelopment`

The last case completed one real crafting job in each of the native big-grid,
native subnet and Federation layouts. Each observed one consumed input, one
machine output, one requester callback and one physical output. The Federation
case returned through the owning Provider and left no input in the target.

An actual Minecraft client under Xvfb/software GL passed `ui.endpoint` and
`ui.mapping` at 1280x720. The first `ui.graph-controls` run at that size failed
only its minimum-height assertion. It passed at 1600x960, the size already used
by the repository's Task 33 wrapper: 316 recorded steps, 22 checks, no failures.
The original failed report is retained; it is not counted as a passing run.
To reproduce the successful graph run after the pinned compilation above:

```sh
xvfb-run -a -s '-screen 0 1600x960x24 -nolisten tcp' \
  env LIBGL_ALWAYS_SOFTWARE=1 ./gradlew :neoforge-1.21.1:runUiTestClient \
  -Pae2_version=19.2.9 -PfederationUiSelection=ui.graph-controls \
  -PfederationUiWindow=1600x960 \
  "-PfederationUiOutputDir=$PWD/build/minimum-ui" \
  -x :neoforge-1.21.1:compileJava -x :neoforge-1.21.1:compileTestmodJava \
  --dependency-verification=strict --no-configuration-cache --no-daemon
```

Expected: `ui.graph-controls` reports PASS, the client exits zero, and
`build/minimum-ui/report.json` has no failed checks.

All 433 production class files used for the minimum runtime checks are
byte-identical to the class entries in the final production JAR. These are
development-runtime tests of the production bytecode, not a claim that a
packaged-JAR client/server acceptance run or the complete 152-case GameTest
suite was repeated on the lower tuple.

The normal pinned build and archive checks also pass, with 253 JUnit tests and
15 Python archive-verifier regression tests passing. The final binary metadata
contains AE2 `[19.2.9,)` and LDLib2 `[2.2.34,)`, while its manifest still records
the build pins 19.2.17 and 2.2.34.

The [evidence summary](minimum-versions-evidence.json) records hashes, selected
tests, client outcomes and limitations. Full logs and client screenshots remain
under ignored `build/dependency-floor/`. Published release assets have not been
replaced; the metadata change takes effect in newly built artifacts.

## Optional-addon version investigation (2026-10-25)

The three requirements above are the **Federation-only baseline**, not a promise
that every version of every AE2 addon can be combined with that tuple. Optional
integrations fall into three categories:

- AE2 public interfaces: ordinary storage, providers, pattern details and plans.
  Supporting an addon's current content through these interfaces does not itself
  establish an addon-specific minimum version.
- Version-sensitive adapters: `CraftingCpuLifecycle` recognizes concrete
  AdvancedAE, Neo ECO and Lightning Tech CPU/host classes; `NeoEcoBatching`
  reflects the ECO batching interfaces; `FederationPatternProviderTargetCache`
  reflects ExpandedAE's target-cache constructor. Pattern projection also
  preserves methodless provider markers and refreshes changed pattern objects.
- Client integrations: Jade's plugin API is compiled directly; JECharacters'
  `Match.contains(String, CharSequence)` is resolved lazily. Conditional guide
  pages and navigation do not make their optional addons mandatory.

An observed lower boundary must be labeled **Federation behavior**, **upstream
dependency/ABI**, **feature availability**, or **test-fixture API**. In particular,
the production GameTest runner marks every unfinished test in a batch as failed
if one test throws a runtime exception. A historical CPU lifecycle audit with
no tracked CPU can therefore obscure otherwise working storage/crafting tests.
Those cases were repeated without the incompatible audit, not counted as
independent failures of every sibling feature.

### Transitive requirements are conditional

These ranges come from the actual downloaded JAR metadata. They apply only when
the named addon/version is installed; they must not replace the baseline with
mandatory dependencies on optional mods.

| Installed addon | Additional declared requirements relevant to the baseline |
| --- | --- |
| Neo ECO 21.2.10 | AE2 `>=19.2.17`; LDLib2 `>=2.2.40` |
| Neo ECO Prototype 1.4.1 | Neo ECO `>=21.2.10`; AE2 `>=19.2.17`; LDLib2 `>=2.2.40` |
| AdvancedAE 1.6.12 | AE2 `>=19.2.16`; GeckoLib `>=4.8.2`; NeoForge `>=21.1.169` |
| MEGA Cells 4.11.0 | AE2 `>=19.2.15` |
| Data Energistics 3.3.5 | AE2 `>=19.2.18`; LDLib2 `>=2.2.40` |
| Lightning Tech 2.1.1 | AE2 `[19.2.17,19.3)`; Thunderbolt `[2.0.1,3.0.0)` |
| Lightning Tech 2.1.2-beta4 | NeoForge `>=21.1.238`; AE2 `[19.2.17,19.3)`; LDLib2 `[2.2.42,3.0.0)`; Thunderbolt `[2.0.3-beta3,3.0.0)` |
| OmniSequence 2.0.9 | NeoForge `>=21.1.220`; AE2 `>=19.2.17`; ExtendedAE `>=2.2.32`; Applied Enhancements `>=1.1.2`; LDLib2 `>=2.2.18` |
| Pattern Disk 0.9.4 | NeoForge `[21.1.241,21.2.0)`; AE2WTLib `>=19.5.0` |

Five negative loader controls actually rejected Prototype 1.4.1 with ECO
21.2.9, ECO 21.2.10 with LDLib2 2.2.34, Lightning 2.1.2-beta4 with LDLib2
2.2.41, that Lightning beta with Thunderbolt 2.0.1, and Data 3.3.5 with AE2
19.2.17. These are upstream loader requirements, not Federation adapter failures.
OmniSequence 2.0.9 initially rejected Applied Enhancements 1.1.0 and passed after
it was replaced with 1.1.4.

Thunderbolt ranges are not interchangeable across Lightning releases:
Lightning 2.0.9 requires `[1.0.1,2.0.0)`, while 2.1.0-beta.1 requires
`[2.0.0-beta.1,2.0.0)` and declares Data Energistics incompatible. Stable
2.1.1 uses `[2.0.1,3.0.0)`. Therefore a simple "Thunderbolt version or newer"
rule is insufficient even when a Federation CPU adapter supports both
generations.

Modrinth and CurseForge do not expose identical release histories: for example,
ExtendedAE's observed Modrinth list stopped at 2.2.35 while CurseForge supplied
2.2.42. Historical candidates were collected from both services and upstream
GitHub releases. Downloads are pinned by URL and SHA-512 in
[`tests/compat/version-boundaries.json`](../../tests/compat/version-boundaries.json).
Modrinth hashes come from its API; CurseForge/GitHub download hashes were
calculated on the retrieved bytes, not treated as independent publisher signatures.

### Federation-specific boundaries versus qualified addon versions

Most packaged-server cases used NeoForge **21.1.250**, AE2 **19.2.18**,
GuideME **21.1.19** and LDLib2 **2.2.41**. The manifest records server exceptions,
including AE2 19.2.9, LDLib2 2.2.34/2.2.44, historical Glodium/GeckoLib and
Thunderbolt generations; the evidence also records development-client tuples.
The production artifact was built with AE2 19.2.17 / LDLib2 2.2.34. A result below applies to its
recorded dependency tuple and selected feature, not every possible modpack.

The archive contains **170 server experiments**: 151 matrix cases, 14 bare
controls and five native-grid/production-only/isolated controls, plus two
development-client runs. Its 86 completed GameTest reports contain 1,287 test
outcomes, including intentional negative cases; this is not a production
pass-rate benchmark.

[`version-boundaries-evidence.json`](../../tests/compat/version-boundaries-evidence.json)
preserves the resolved tuples, **122 upstream JAR** pins and metadata,
per-run results, report/log hashes, binary API declarations and client checks.
The production JAR SHA256 is identical throughout the server comparisons.
Three ordinary compatibility-fixture JAR snapshots occur; their changed class
entries and per-run hashes are recorded explicitly. The matrix does not claim
identical test-fixture bytecode across all runs.

| Integration | Observed boundary or qualification | Interpretation |
| --- | --- | --- |
| Neo ECO | 21.1.1 and 21.2.0-beta1/beta2 leave **2** units of cancellation responsibility; beta3 does not take the tested job. Beta4 passes an isolated cancellation selection (**6/6**) and the full selection excluding the newer planner-ownership fixture (**41/41**). Stable 21.2.0, 21.2.9 and 21.2.10 each pass **42/42**. | Concrete CPU/lifecycle ABI appears in **21.2.0-beta4**; first fully qualified stable release is **21.2.0**. Ordinary crafting/storage can work before it. The excluded beta4 check uses a method absent from that release, not a Federation production dependency. |
| AdvancedAE | Adjacent 1.1.12 fails duplicate menu registration even without Federation; 1.1.13 passes **34/34** with historical GeckoLib/Glodium. 1.1.18, 1.2.0/1.2.5 and 1.4.0/1.4.3/1.4.6 pass **19/19**. 1.5.0 and 1.6.12 pass **34/34**; 1.6.11 passes **19/19**. 1.3.0, 1.6.0 and 1.6.1 encounter dedicated-server client-class loading failures. | First qualified current-AE2 generation: **1.1.13**, not the current pin. This is not a monotonic open-ended guarantee: exclude the observed upstream-broken tuples. |
| ExtendedAE | 2.2.6 fails menu registration even without Federation, and also fails on the AE2 19.2.9 / Glodium 2.0 control tuple. 2.2.7 is the adjacent passing provider generation (**10/10**). 2.2.8/2.2.16/2.2.32 pass the same selection; 2.2.39 and 2.2.42 pass **33/33**. | Qualified boundary for the recorded AE2 tuples: **2.2.7**. This is upstream registration/AE2 compatibility, not a dedicated Federation reflection API. |
| ExtendedAE-Plus | 1.4.2/1.4.3/1.4.4 run ordinary accelerated-CPU crafting but have no `SMART_DOUBLING` provider setting. 1.5.0 and 1.6.0 pass the selected CPU/smart-doubling cases (**9/9**); 1.6.3 passes **41/41** including ExtendedAE. | Smart-doubling feature boundary: **1.5.0**, not a requirement for every use of Plus. 1.4.0 and its fix fail dedicated-server client-class loading on the tested tuple. |
| ExpandedAE | 2.1.1 overwrites `PatternProviderLogic.pushPattern` and prevents Federation's target-binding injection. 2.1.2 introduces the reflected five-argument target cache returning AE2's target type; 2.1.2/2.1.3/2.1.4 each pass smart blocking (**6/6**). | Actual version-sensitive adapter/Mixin boundary: **2.1.2** for the tested integration. Supply AdvancedAE, ExtendedAE and AE2WTLib: these releases access their classes even when metadata calls them optional. |
| Lightning Tech | 1.0.0 passes overloaded-provider cases (**8/8**). 2.0.9 passes **11/12**, leaving **2** units of cancellation responsibility on its old independent CPU. 2.1.0-beta.1 passes the new TimeWheel CPU selection (**9/9**); stable 2.1.1 and 2.1.2-beta4 each pass **35/35**. | Independent-CPU lifecycle boundary: **2.1.0-beta.1**, or **2.1.1** when selecting stable releases. Do not reject the working old ordinary providers merely because they predate this CPU. |
| MEGA Cells | 4.8.0 fails upstream grid-service construction without Federation; adjacent 4.9.0 and 4.10.0 pass **12/12**, and 4.11.0 passes **27/27**. Older 4.0.2 also fails on the AE2 19.2.9 control tuple. | Use **4.9.0 or a separately qualified version** for the recorded current tuple. This is an upstream AE2/registration constraint, not a Federation-specific API floor for all environments. |
| Applied Flux | 2.0.0, 2.1.4 and 2.1.7 pass the targeted capacitor/provider selection (**7/7**); 2.1.5 passes **22/22**. | No additional Federation-specific floor found above the earliest tested 1.21.1 release. |
| AE All Pattern | 0.2.1 passes ordinary Federation **20/20**, but not the cycle-planning selection. Adjacent 0.2.2 and current 0.2.6 pass that selection (**6/6**). | Qualify the tested cycle planner from **0.2.2**; do not impose it as a global loader minimum. |
| Applied Enhancements | 1.0.0 passes ordinary Federation **20/20** but lacks the fixture's automatic Aelis planner switch. 1.1.0 and 1.1.4 pass the planner selection (**6/6**). | Automatic-planner API/feature boundary for this fixture, not proof that Federation cannot coexist with 1.0.0. |
| Data Energistics | 1.0.0 passes adaptive-provider selection (**7/7**); 3.3.3 and 3.3.5 pass the current addon selection (**13/13**). | No additional global Federation floor established; current content and its AE2 19.2.18 requirement are separate. |
| Neo ECO Prototype | 1.4.0 and 1.4.1 pass the L1 selection (**16/16**). The 1.0.0 run encounters missing later block IDs and the fixture's newer cell-insertion API. | These are qualified versions, **not proof of a 1.4.0 global minimum**. Prototype 1.4.1 independently requires ECO 21.2.10. |
| OmniSequence | 1.3.0 boots with production Federation alone; its ordinary core selection passes **19/20**, with one guide-cable geometry mismatch. 2.0.7 passes its selection (**11/11**); 2.0.9 with Enhancements 1.1.4 passes **26/26**. | Old guide/content fixtures and upstream companion requirements must not become a fabricated global addon floor. The initial 1.3.0 run reaches `Done` and the tests, then stalls after its fixture cannot find `MatterFabricationStructure`; it is not a startup/loader failure. |
| Pattern Disk | 0.8.0 and 0.9.4 pass **8/8**. 0.2.1 boots with contemporary ECO 21.2.0 but lacks the current fixture's `PatternDiskApi` encoder; adding ECO 21.2.10 instead fails its reverse integration with that old disk mod. | Historical addon-pair and fixture API boundaries; **0.8.0 is a qualified version, not a demonstrated Federation minimum**. |
| UselessMod | All published tested jars 2.4.5.9/2.4.5.10/2.4.5.11 pass **9/9**. 2.4.5.12/2.4.5.13/2.4.6 time out in all four alloy-furnace scenarios while core controls pass. | Keep a known-working furnace version for this tuple. The real furnace also times out on a joined native grid in 2.4.5.12 and 2.4.6. Federation remains loaded in this control: it isolates projected cross-grid execution, **not all Federation effects or fixture assumptions**, and does not prove an upstream-only defect or a global upper bound. |
| Jade / JECharacters | Actual client runs load Jade 15.1.6 + JECharacters 4.5.14, and Jade 15.10.6 + JECharacters 4.5.30. Each passes **34** UI checks, including actual Chinese pinyin search; Jade 15.1.6 also passes **20/20** server core tests. | No narrower API floor found between the tested endpoints. Jade HUD contents were not separately visually qualified. |

ExpandedAE 2.1.1 also fails on its own with modern ExtendedAE because
`ItemMEPackingTape.registerPackableDevice(ResourceLocation)` was removed.
The matched control uses ExtendedAE 2.2.32, which still has that method: it
**boots without Federation**, but adding Federation fails its target-binding
Mixin. This isolates the real Federation conflict from that separate upstream
ABI error. OmniSequence also declares ExpandedAE 2.1.1 incompatible.

For timeouts, `compat_run.failure_hint` prints the **first** recognized error,
not the terminal server state. The harmless early world-property message in its
output is not the timeout cause. The complete terminal logs attribute both ECO
beta4 full runs to the fixture's missing
`ECOPlanningResultRegistry.isECOOwnedPlan(ICraftingPlan)` method and the old
OmniSequence run to the missing fixture blueprint class. Those methods/classes
are not Federation production dependencies.

The old Glodium control was obtained from its
[published 2.0 file](https://www.curseforge.com/minecraft/mc-mods/glodium/files/5614731),
not inferred from the declared minimum or replaced with modern
[2.2](https://www.curseforge.com/minecraft/mc-mods/glodium/files/5821676).
AdvancedAE 0.6.1 and ExtendedAE 2.0.1 still fail on the minimum AE2 19.2.9 /
Glodium 2.0 tuple. Removing GeckoLib from newer AdvancedAE releases instead
produces legitimate missing-dependency rejections; those negative controls are
not compatibility-floor evidence. Matched GeckoLib 4.6.6/4.7.6 controls are
recorded separately.

### Scope and enforcement

No production Java code, required dependency ranges, or release pins were
changed by this investigation. In particular, the addon table is not a list of
new mandatory mods, and passing endpoints do not certify every intervening or
future version. Addons integrated solely through AE2 public interfaces, including
other storage/wireless-terminal integrations, acquire no new addon-specific
compile-time floor merely because an addon-specific acceptance test exists.

Servers, released addon JARs, AE2 grids, pattern providers, CPUs, storage and
planner objects are real. Some existing processing/cancellation scenarios use
manually driven chest test machines to control delayed outputs; they do not
certify every downstream processing machine. The UselessMod native differential
uses its actual alloy furnace on a joined grid, not a substitute furnace.

Reproduction commands and expected exit statuses are in
[production runs](production-runs.md#historical-addon-versions). Full logs,
reports and client screenshots remain in ignored
`neoforge-1.21.1/build/addon-version-boundaries/`.
