# Return router identity replacement — 2026-10-11

## Confirmed path

Native AE2 Grid object identity does not permanently imply Federation NetworkId identity. A two-node fresh Grid can absorb a one-node durable A Grid, lose that durable node, then absorb a one-node durable B Grid within the fresh nodes' permitted adoption tick. The same native Grid survives both merges, while its settled Federation identity changes A to B. This preserves the existing same-tick adoption rule; copying saved NBT does not grant fresh adoption rights.

`CraftingProjectionService.syncRouters` previously keyed only on native Grid. The existing return router captured A and survived reconciliation after the Grid settled as B. A new actual arrival was therefore attributed to old A rather than the current B obligation.

The regression uses real managed AE2 nodes, real native connections, actual identity services and the production router. Explicitly injected debts and a capacity-limited inventory isolate ownership accounting; it is not a full native crafting-order test. After both legal merges the same Grid receives one actual item. Before the fix the test fails with held(B) expected 1, actual 0, in the batch and standalone retry.

## Repair and invariant

A router now exposes its captured executing identity to its owning service. Reconciliation removes it if that identity differs from the currently wanted identity; the existing creation path mounts the replacement. No remount occurs for an unchanged identity. Old A debt and transit are not migrated to B or deleted. Inaccessible old transit remains subject to the explicit operator recovery path; Policy is not inherited.

## Evidence

```sh
python3 tools/dev_gametests.py projectionrouteridentityreplacement
# Before: expected current identity held=1, actual=0; fails in batch and alone.

python3 tools/dev_gametests.py projectionrouteridentityreplacement projectionreturnsimulationcapacity projectionrejectedarrival identitysametickfragments identityinitializationorder
# After: 5/5 in one 29-second batch.
```

The required regression asserts same native Grid after both merges, the two adopted identities, physical stock=1, held(B)=1, held(A)=0, preserved owed(A)=1, and identity-stable router reuse. Local logs are `.omo/evidence/audit-router-identity-2026-10-11/{red,green}.log` (ignored).

## Post-fix review passes

1. **Identity and responsibility pass:** checked current confirmed identity lookup, stale mount replacement, and preserved old ledger buckets. The fix changes captured identity only by replacing the router, never by relabeling old stock or moving Policy.
2. **Storage and invalidation pass:** unchanged identity reuses the same router; removed or empty Grids still lose obsolete routers. Both old and replacement routers are Federation-managed and excluded from own-storage views, so replacing them does not require changing native mount-cache semantics. Simulation, rejected-arrival, initialization and same-tick adoption regressions remain green.

No save schema changes. No production chunk loading. No claim of broad identity migration or automatic orphan-transit recovery.
