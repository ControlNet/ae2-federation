# Follow-up audit and dev delivery — 2026-10-11

Baseline: `2f0f99fc590a8e99e1e6b78d67db3aac812a345f`. This is a bounded follow-up to
[the concentrated review](concentrated-review-2026-10-11.md), including its exact-commit CI failures.
It does not replace that report's production-client evidence or turn its unverified boundaries into passes.
Unrelated existing ideas, knowledge notes and the local skill directory are excluded from delivery.

## Findings

| Finding | Evidence and player impact | Repair and regression |
|---|---|---|
| Unpaired P2P tunnels | Reproduced before repair with three active frequency-zero tunnels on one native carrier: unrelated Federation fronts were linked without pairing. | Frequency zero publishes no virtual tunnel links; physical front publication and old/new group invalidation remain. Required regression checks isolation, alongside the existing paired independent-group test. |
| Reentrant transit fallback | Reproduced on real native Grids with an explicitly injected storage callback: inserting three saved items exposed the same three to reentrant extraction, producing six. This is not an allegation against a specific addon. | Reserve transit before invoking external insertion, then restore only refusal. Required full and partial acceptance cases verify conservation and eventual empty transit. Arbitrary handlers that mutate and then throw remain outside any transaction guarantee. |
| CPU identity at shared anchors | Reproduced with two independently planned jobs on real Neo ECO CPUs sharing one threading-core block. The first different CPU could mark another still-running job done. | Search the complete active CPU set for the exact crafting UUID. Another job at the same position is not terminal evidence. Optional lifecycle access remains centralized and read-only. |
| Advanced AE cancellation | Baseline remote CI failed `quantumcorecancel` with debt two; the independent Advanced AE CPU had no lifecycle adapter. | Use the real CPU's public action source and crafting logic, retaining AE2 execution and conservative missing-CPU behavior. |
| Global provider removal | Reproduced: a retained consumer medium could call a provider after official native unregistration. A second regression exposed the same gap when the source Grid had lost its final pivot. | Observe the completed official removal synchronously; withdraw by source Grid and provider object identity. Capture server context at native service construction so empty Grids also work. No replacement provider registry or replay of registration. |
| Aggregate provider-count assertion | Disproved as a production defect. MegaCells 4.11 registers its real `DecompressionService` globally even with no available patterns; the old fixture counted this legitimate provider as a duplicate. | Assert exactly one consumer medium for each fixture recipe and two distinct fixture medium identities; keep actual crafting and exact stock/debt assertions. Real MegaCells passes 25/25 after this correction. |

The native global-removal regression uses an independent accepting provider double on real Grids, two consumers,
same-tick retained-medium attempts, re-registration, real source block removal and controlled source-face retirement.
It must pass
in a shared batch. Intermediate versions that left destroyed node handles or a CPU cluster attached to destroyed
managed nodes were rejected despite isolated success. The latter failed only when a later test reused and cleared
the scene, demonstrating why the complete required batch is necessary.
The final fixture removes native blocks before retiring the Switch face, then removes the actual Switch after all
assertions. A face-first negative control confirmed that later neighbor notifications otherwise reach its invalid
capability cache. This was an invalid partial fixture lifecycle, not a production cache change. A separate negative
control on the corrected physical-removal fixture still fails when the empty-pivot early return is restored.

## Compatibility and persistence

New internal hooks target AE2 19.2.17's `CraftingService` constructor and
`removeGlobalCraftingProvider` return. `Grid` establishes its non-null pivot before constructing services; the
captured server remains usable after the final node leaves. Internal provider refresh does not trigger this hook.
Optional CPU access targets Advanced AE 1.6.12 and Neo ECO 21.2.1. See the
[native crafting contract](../architecture/native-crafting-contract.md) for exact adapter dependencies.

The CPU ledger can additionally record `TERMINATED` when an addon proves termination without distinguishing
completion from cancellation. Neo ECO's persisted marker is used only with a formed anchor and no deferred restore
slots. It is not inferred from absence, timeout, zero waiting or a different CPU at the same anchor. Existing ledger
records remain readable; older binaries without this enum must not be used to read newly written terminal records.
No Policy, identity, held or transit amount format is changed by this follow-up.

## Verification scope

Individual red/green receipts and rejected intermediate fixtures are retained under
`.omo/evidence/audit-and-fix-2026-10-11/` and the corresponding per-finding evidence directories.
After the fixture correction, integrated source `fd03ea8e94e4636c57b31394aa91f6fda323f7ca` passed a complete
**215/215 required GameTest batch in 49 seconds**, using `--ci --no-isolate`, with no isolated retry or SHARED result.
Strict check/build and production archive verification passed again on that source. Required GameTests are added to
the manifest; no failed test is removed, assertion relaxed, or timeout/TTL extended. Final raw receipts are
`required-final-server.log`, `required-final-summary.log` and `final-build.log` in the integration evidence directory.

Reproduction and delivery gates:

```sh
./gradlew --dependency-verification=strict check build :neoforge-1.21.1:compatTestJar --no-configuration-cache
PYTHONDONTWRITEBYTECODE=1 python3 -B tools/dev_gametests.py --manifest --ci
python3 tools/compat_run.py --accept-eula --no-build addons-all useless-mod-addons -j 1
python3 -m unittest discover -s tests -p test_required_gametests.py -v
python3 -m unittest discover -s tests -p test_artifact_verify.py -v
python3 tools/privacy_guard.py tree HEAD
git diff --check
```

Expected signals are a successful strict build, one complete required GameTest batch, every selected real-addon
profile passing, and clean archive/privacy/whitespace gates. Isolated retry success is not a passing batch.

The combined production source at `ebe10ee5` passed strict check/build and archive verification with **547 JUnit
tests, zero failures/errors/skips**. Actual combined addon profiles passed **addons-all 94/94 (156 seconds)** and
**useless-mod-addons 91/91 (171 seconds)**. These profiles use the production jar plus a separate compatibility testmod.
The subsequent fixture-only correction does not alter production code or the scenarios executed by those profiles;
their test jar also packages unused GameTest classes, so the local receipts remain tied to this stated source commit.
Before integration, the final CPU change also passed real Neo ECO **30/30**, Advanced AE **29/29**, and Lightning Tech
**30/30**. The provider lifecycle hook and remaining follow-up changes are additionally present in the combined
addon runs. Manifest validation passed 5/5; artifact verification passed 15/15; privacy-tool tests passed 4/4.

Production jar SHA-256: `67ae2e1f376d0a2c8e03bda0331d2ed8f98607cd45bcd18b0829c91bc74ca306`.
The version remains 0.0.6; this is dev delivery, not a newly published release.

The previous production client run used no testmod and exercised Crafting-only terminal order, furnace processing,
blocked return, recovery and real restart. That artifact predates these follow-up changes. New regression evidence
must not be described as a repeat of that production client run, or as proof for every addon CPU's independent chunk
unload and real process restart. No new large-factory performance claim is made.
