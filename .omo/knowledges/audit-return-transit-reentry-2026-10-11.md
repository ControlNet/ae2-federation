# Transit fallback reentry audit — 2026-10-11

## Confirmed defect

Baseline `2f0f99fc`: `HeldReturnTransfer.storeTransit` inserted a snapshot of transit into a native mounted storage before removing the corresponding SavedData stock. The filtered `ownStorage` view is a separate `NetworkStorage`; its recursion guard does not lock the executing Grid's original inventory. A mounted storage can synchronously extract through that original inventory, reaching the production return router while the same transit is still visible. Three original items then become three accepted by the target plus three extracted reentrantly.

This is a storage callback fault-injection reproduction on real AE2 Grids and the real return router. The callback inventory and three seeded transit items are explicit test fixtures, not a claim that a particular third-party mod performs this action.

## Fix and invariant

Reserve the actual current transit with `takeTransit(MODULATE)` before invoking the native insertion. Restore only the rejected residual afterward, following the recovery command's existing reservation pattern. Full and partial acceptance must conserve stock across repeated sweeps. No save schema, debt ownership, scheduling, or native storage ordering changes.

The `finally` restoration is not a transaction guarantee for a third-party handler that mutates its inventory and then throws without reporting acceptance. That limitation already applies to the recovery command and is not claimed covered here.

## Evidence

- Before fix: `python3 tools/dev_gametests.py projectiontransitreentry` failed in the batch and alone: expected total 3, actual 6.
- After fix: `python3 tools/dev_gametests.py projectiontransitreentry projectiontransitpartialreentry projectionorphantransitrecovery projectionnativeaccessandremount` passed 4/4 in one 34-second batch.
- Required manifest adds both new reentry regressions, asserting conservation, invisible in-flight stock, residual retention and final delivery.
- Logs: `.omo/evidence/audit-return-reentry-2026-10-11/{red,green}.log` (local evidence, ignored by Git).
- Existing recovery command already reserves transit before external insertion; no duplicate recovery implementation was added.
