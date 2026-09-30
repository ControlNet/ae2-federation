# Minimum dependency versions

Investigation date: 2026-09-27. Target: Minecraft 1.21.1 / NeoForge 21.1.250.

| Dependency | Runtime requirement | Reproducible build pin | Boundary |
| --- | --- | --- | --- |
| Applied Energistics 2 | `[19.2.9,)` | 19.2.17 | Federation Provider Lanes require global crafting providers |
| LDLib2 | `[2.2.34,)` | 2.2.34 | The production graph zoom controls require `GraphView.setScale(float)` |

Runtime ranges are defined in `gradle.properties` independently of the build
pins and expanded into both production and testmod metadata. Minecraft,
NeoForge and Java requirements are unchanged. An open upper range permits a
newer dependency to load; it does not certify every future release.

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
- `energydirectionalpolicy`
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
