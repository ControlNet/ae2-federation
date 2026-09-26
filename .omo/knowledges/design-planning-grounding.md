# AE2 Federation planning evidence

> Historical v0.2 evidence. Product scope and proposed architecture are superseded by `design-v04-planning-grounding.md`. Do not carry forward the optional Matrix, default remote-job contract, custom lock/reservation machinery, or prior unanswered choices.

## Scope and repository baseline

- Source: `DESIGN.md`, version 0.2, 2026-09-12, 1697 lines.
- Root currently contains design, README, AGENTS, LICENSE and tool directories; no build or product source is present in the root listing. CodeGraph returned no relevant implementation.
- `LICENSE` contains GNU AGPL version 3. Preserve the existing license; assess upstream source reuse obligations separately.
- Required outcome covers the entire design, not only Processing: infrastructure, Processing, Storage, Crafting/Automation, Energy and visualization, compatibility/performance/release evidence.
- Fixed decisions: Minecraft 1.21.1, NeoForge only, one `common` with thin `neoforge-1.21.1`, provisional LDLib2, independent grids and provider lanes, explicit returns, no machine scheduler or implicit unlimited queues.
- Product owner decisions remain in section 20.3; platform choice is not an open question.

## Direct source observations (not runtime verification)

Read on 2026-09-13 from mutable AE2 `1.21.1` branch:
https://raw.githubusercontent.com/AppliedEnergistics/Applied-Energistics-2/1.21.1/src/main/java/appeng/helpers/patternprovider/PatternProviderLogic.java

- `adapterAcceptsAll` simulates each input separately and rejects zero acceptance; it does not prove shared-capacity atomic insertion or require simulated acceptance of the full amount.
- `pushPattern` holds actual insertion remainders in `sendList`, returns success after taking delivery responsibility, and prevents new pushes while that list is nonempty.
- Native target discovery and success/return-lock handlers in this class are private. Simple subclassing is not evidence of a usable target override.
- Blocking checks the aggregate decoded pattern-input set (secondary components dropped), not a generic machine-busy flag.
- Result unlock tracks the primary output and decrements its outstanding amount when the return inventory actually injects matching resources into the network.
- These observations must be re-bound to the selected release commit and tested. They do not prove Federation implementation feasibility.

## GUI and test documentation observations

https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/ui/testing.html

- Documentation labels the UI harness as available since 2.2.34.
- It describes real-client isolated worlds, selector operations, synchronization waits, screenshots and reports; synthetic input is default.
- Documented run form: `./gradlew runClient -PldTest=furnace_ui`; actual project task paths do not exist yet.
- `verifyUiTest` must fail on absent reports. Dedicated-server synchronization still requires separate verification.
- Documentation alone does not prove a chosen 1.21.1 artifact contains the harness or that dev-only registrations are physically excluded from release JARs.
- ModDevGradle Context7 documentation confirms named `gameTestServer` runs and unit-test configuration; returned example version numbers are illustrative, not dependency pins.

## Planning tooling limitation

The current parent tool surface exposes no command executor. The ulw-plan scaffold script has not been run; no formal plan or scaffold-compliant draft has been created. This knowledge record preserves evidence without pretending that the required scaffold operation occurred.
