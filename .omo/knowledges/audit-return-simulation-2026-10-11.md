# Return routing simulation audit, round 2 — 2026-10-11

Baseline: `2fa70d74f271a69e80baff583940d4debd3a65ed`.

## Confirmed: native capacity counted twice

`CraftingReturnRouter.Storage.insert(SIMULATE)` called the filtered executing-network storage view, which visited the same native mounts that the enclosing `NetworkStorage.insert` would visit after the router. Simulation leaves capacity unchanged, so a remaining input caused that capacity to be counted twice.

The regression mounts the production router and a clearly identified capacity-10 test inventory on a real AE2 Grid. The consumer is unreachable and owes eight items. Inserting twenty with SIMULATE returned eighteen before the fix, while only ten could actually fit. Both the batch and isolated retry failed with expected 10, actual 18. This proves an incorrect simulated quantity; it does not establish item loss in native callers or equate simulation to a transaction.

The router now simulates only its remote forwarding. The enclosing native storage traversal simulates local mounts once. Real insertion still intercepts actual local acceptance before recording held arrivals; rejected attempts remain uncounted. The router is an internal mount of that native inventory, not a standalone storage capability.

The older rejected-arrival test called the internal router mount directly, bypassing the outer traversal that exposed this bug. It now mounts the actual router on the native Grid and executes the same thirty quantity and side-effect assertions through that Grid. No expected quantities or rejection counts were weakened.

## Evidence

```sh
python3 tools/dev_gametests.py projectionreturnsimulationcapacity
# Before: expected native capacity 10, actual 18; fails in batch and alone.

python3 tools/dev_gametests.py projectionreturnsimulationcapacity projectionrejectedarrival projectionrequest projectiontransitreentry projectiontransitpartialreentry projectionnativefallbackorder
# After: 6/6 in one 26-second batch.
```

Logs are in `.omo/evidence/audit-return-simulation-2026-10-11/{red,green}.log` (local, ignored). The new capacity regression is required in the manifest. No save format or public API changes.

## Independent post-fix review passes and dispositions

1. **Native routing and conservation pass.** Followed native AE2 19.2.17 `NetworkStorage.insert` through preferred and ordinary passes. Local simulated capacity is now counted only by that traversal. MODULATE still records only accepted arrivals; the existing repeated-rejection test, actual request, native ordering differential and full/partial transit-reentry tests all pass. Hypothesis that merely skipping local simulation would stop actual held accounting is disproved by the unchanged MODULATE path and held=8/cell=10 regression assertions.
2. **Authority, lifecycle and view-invalidity pass.** Re-read recovery command permission checks, loaded-position lookup, per-key reservations, ledger snapshots and native-view guards. Recovery remains permission-level 2, uses the command source dimension and does not load destination chunks. A callback consuming another transit key cannot make a later stale snapshot duplicate it: `takeTransit` reserves current actual quantity. Old native views reject mutation after mount-generation or identity-epoch changes. Unknown CPU provenance intentionally retains aggregate responsibility; that conservative policy is not a new completion inference. No additional confirmed defect was found in these paths.

Additional hypotheses qualified rather than silently treated as bugs:

- Immediate output during `pushPattern`: parent inspection of native `CraftingCpuLogic` confirms native waiting registration also occurs after `pushPattern`. Without a differential against that native ordering, this is not established as a Federation defect.
- Router captured identity versus a changed identity on the same Grid: shared-service lead passed to the parent/topology reviewer; a legal settled same-Grid identity transition still needs to be demonstrated. No speculative identity migration was added.
- A third-party inventory mutating and then throwing without an accepted count remains outside the normal acceptance contract. Reservation and compensating fallback are not a generic transaction guarantee.
