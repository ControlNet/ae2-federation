# Dependency minimums

- Keep reproducible Gradle pins separate from runtime version ranges. Production
  and testmod metadata now expand `ae2_version_range` and `ldlib2_version_range`.
- The current production source requires AE2 19.2.9's global crafting provider
  API. Official release notes identify its introduction, and 19.2.8 compilation
  fails at the actual Federation integration. Production compilation on 19.2.9
  succeeds.
- Current graph zoom controls require LDLib2 2.2.34's
  `GraphView.setScale(float)`. The preceding 2.2.33 JAR lacks it and fails
  compilation; lowering that bound would require an intentional UI adaptation.
- AE2 19.2.9 runtime tests use production/testmod bytecode compiled with normal
  pins. Test-only CPU suspension helpers do not compile against 19.2.9 and must
  not be confused with production requirements. Preserve this distinction when
  choosing lower-bound scenarios.
- The Task 33 UI suite uses 1600x960 in its existing Gradle wrapper. A direct
  1280x720 run trips the graph's minimum-height assertion even while mapping and
  endpoint scenarios pass; retain the failure when reporting a corrected run.
- See `docs/compatibility/minimum-versions.md` for evidence and commands. Raw
  exploratory downloads, logs and the isolated source copy stay under ignored
  `build/dependency-floor/`. Existing published release assets are unchanged.
- Final result: eight native server tests passed on AE2 19.2.9 / LDLib2 2.2.34,
  including complete CPU/machine/return jobs in three layouts. Actual-client
  mapping and endpoint scenarios passed at 1280x720; graph controls passed at
  the correct 1600x960 size. All 433 runtime production class files match the
  final JAR entries. This is bounded development-runtime validation, not the
  entire GameTest suite or a packaged-JAR client/server qualification.
