# Modpack compatibility test architecture

Static inspection on 2026-10-25, dev HEAD 98da536d plus existing working-tree changes. No production server was launched.

## Execution chain

- `tests/compat/profiles/{atm10,cus2,nast}.json` select real server ZIPs, SHA-512 pins, NeoForge versions, heap, timeout and test groups. Current pins are ATM10 8.3, CUS2 12.9.0 and NAST 2.2.1. Extra mods resolve through `tests/compat/mods.json`.
- `tools/compat_run.py` uses only Python standard-library modules. It builds `:neoforge-1.21.1:jar` and `:neoforge-1.21.1:compatTestJar`, verifies pinned downloads, prepares cached NeoForge installations and assembles each profile under `neoforge-1.21.1/build/compat/runs/<profile>`.
- Assembly removes the previous profile run directory. The pack is copied using hard links where possible, preserving mods/config/KubeJS and other pack content, subject to optional profile `remove` patterns. Libraries are replaced by a symlink to the selected NeoForge installation; `server.properties` is replaced with a local offline superflat test configuration. This is a controlled pack-based server, not an unchanged pack launch script.
- The compatibility JAR contains compattest classes and reusable development fixtures/resources, but excludes the development mod entrypoint and mixins. It is separate from release artifacts.
- `ProductionGameTestRunner` registers methods with vanilla GameTestRegistry and manually ticks GameTestTicker in production. It selects group names or method IDs; unknown selections fail setup. It disables daylight/weather/mob spawning and ordinary Federation operating-power requirements for the fixtures.
- Python defaults to four concurrent profile processes (`-j 4`), each with 32 concurrent GameTests (`-p 32`). CellGridSpawner separates fixtures by whole chunks; RunsAlone tests have their own batch. `-p 1` helps diagnose interference.

## Assertions and evidence

- Core tests cover storage listing/extract/insert, filters, projected crafting, remote processing, energy, returns, cancellation, disconnection and guide scenes. `federationCraftable` checks a specific seven-item list against recipe outputs after scripts load; it does not prove full progression or ingredient availability.
- Addon groups replace native components with real addon providers, CPUs, cells, storage containers and machines.
- NeoEcoCompatGameTests.ecoPlannerCycle constructs ECOPlannerRequester with cycle planning enabled and checks ECOPlanningResultRegistry.isECOOwnedPlan. CyclePlanningScene compares plain AE2 planning against addon planning and executes a remote seed-loop order with an ECO CPU. Its seed machine is a deliberate test simulation: chest contents are converted into outputs by the test. It is not a physical farming-machine test.
- JSON reports contain setupError, per-test ID/status/required/ms/error and actual loaded mod versions. Stopping/crashing before completion produces missing tests and setupError where lifecycle hooks run.
- Python treats any non-pass test as failure. A completed passing report takes precedence over process exit: after 30 seconds of post-report grace it may kill lingering threads and still report pass. No report becomes timeout or no-report. This is not proof of graceful shutdown.
- `--bare` omits the runner-added Federation and compat JARs and checks for the server Done message, providing a pack-startup control, not a functional control. It does not actively strip a Federation JAR already included by a pack.
- Separate `compat_cpu_restart.py` and `compat_transit_restart.py` reuse a world across two/three actual processes. They require zero exit, no setupError and exactly one passing test per phase. They are not automatically run by the normal profile runner.

## CI and latest releases

- `.github/workflows/compat.yml`: addon profiles on eligible pushes; modpacks also on release/hotfix branches, weekly and by manual selection. Each profile is a matrix job, fail-fast false. Logs/reports/crashes are retained for 14 days; third-party files are not uploaded as artifacts.
- `.github/workflows/latest-modpacks.yml`: separate weekly/manual scan. Resolve newest stable Minecraft 1.21.1 NeoForge CurseForge release and its explicitly marked server pack. Detect loader version from pack files, retain the configured test groups/extra mods, and run without changing repository pins.
- `pinned-is-latest` means no new server execution, not a new pass. Newly discovered downloads are hashed locally rather than verified against a pre-existing trusted pin. Latest CI uses no Actions cache for the pack.

## Observed local evidence and verification

- Existing reports: atm10 91/91 (NeoForge 21.1.251), cus2 59/59 (21.1.243), nast 130/130 (21.1.251, NeoECO 21.2.9). All report Federation 0.0.7. Current source is 0.0.8 and current ATM10/CUS2 pins have changed; do not present these reports as current-source/current-pin qualification.
- Ran `python3 -m unittest discover -s tests -p test_compat_run.py -v`: 16 passed. These validate catalog/profile consistency, report formatting, release resolution and loader detection; external responses/installer archives in unit tests are controlled test fixtures, not live download evidence.
- No Java build, pack download, live newest-version lookup or server run performed in this reading task.
