# Task 40 documentation handoff

## 2026-09-25 interim writing pass

- `README.md` points to the manual client guide, test commands, tuple, compatibility and acceptance index. These are local, provisional docs, not external publication or Task 40 completion.
- `docs/acceptance-matrix.md` groups design families and registered cases with evidence roots, while marking incomplete Task 37 resources/Federation timing, absent Task 38 soak and pending Task 40/F1-F4 audit evidence. Evidence is source-bound and must be regenerated/consumed after code changes.
- `docs/testing/manual-client.md` records that `:neoforge-1.21.1:runClient` is unavailable in the actual Gradle task listing; existing client tasks are scripted and not suitable for human free play. It gives conditional human steps without pretending to have an interactive launcher. No actual client was launched in this writing pass. The code-side audit registration belongs to a separate worker; case names in the plan must not be presented as passed until verified.

## 2026-09-25 manual launcher setup

- Launch with `./gradlew :neoforge-1.21.1:runManualClient --dependency-verification=strict --no-configuration-cache` on a graphical Java 21 desktop. This new run loads only the product `main` mod plus pinned AE2/LDLib2 dependencies; it does not load `ae2federation_test`, quick-join a server, or require Task 39 EULA approval. It owns `neoforge-1.21.1/run-manual-client/`, ignored by Git and never automatically deleted; use the Minecraft menu to quit and preserve any later user-created world.
- Gradle `tasks --all`, dry-run, strict test/check/build and Xvfb title/accessibility-screen smoke were verified. The Xvfb launch showed the exact tuple in `run-manual-client/logs/latest.log` and no world in `saves/`. No human walkthrough, gameplay claim or Task 40 completion follows. Full receipts and command spelling: `.omo/evidence/task-40-manual-client/verification.md`. The manual guide and README retain stale blocker language until the separate documentation update.

## 2026-09-25 acceptance mapping update

- `docs/acceptance-matrix.md` now accounts for every numbered DESIGN T-P/L/E/F/R/C/S/U/V/G/B range using exact registered manifest IDs at family level, with explicit notice that exact numbered-case correspondence has not been verified. Physical Bridge, six-face Hub and five-face Endpoint IDs are spelled out. The tester must run and consume new source-bound results rather than interpret task receipts as current approval.
- Task 37 small direct/subnet timing is 3/3 each on the cited source; Federation failed 27/256 warmup, resource parity and late/ultra are missing, Task 38 soak is absent. Documentation and F1/F2/F4 suites are registered but BLOCKED, while F3 planned IDs are unregistered. T-V03/04 apply only after declaring another target. Editing the matrix invalidates its prior hash-bound docs receipt. No manual client or final approval was performed here.

## 2026-09-25 manual launcher documentation correction

- The README, test command index, acceptance tester step and human guide now use the verified `./gradlew :neoforge-1.21.1:runManualClient --dependency-verification=strict --no-configuration-cache` entrypoint rather than the superseded missing-launcher warning. The launcher smoke reached the Minecraft title/accessibility prompt under Xvfb, with matching production mods in `run-manual-client/logs/latest.log`, no world created and no human interaction. New Creative worlds persist under ignored `neoforge-1.21.1/run-manual-client/saves/`; there is no automated cleanup. F3 and Task 40 qualification remain open.
