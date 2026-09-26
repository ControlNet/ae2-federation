# Task 19 Processing Regression Knowledge

## Qualified runtime

- Java 21.0.12, Gradle 9.2.1, NeoForge 21.1.250, and AE2 19.2.17.
- The dependency JAR digest is `460d779a0609b81409907d9956de8f6f70a1b0912257e3e5c3c7e75ac9630e95`.
- The pinned AE2 `PatternProviderLogic.java` source digest is
  `46cbd4a6eab1862349c1739b9ef4b808453fe96d623748237a7aa2770777b225`.

## Native boundaries

- `PatternProviderLogic.pushPattern` simulates every supplied key and requires a nonzero insertion for each. Modulation is
  then performed key by key. This is not cross-resource atomic: an accepted prefix remains target-owned and an unaccepted
  suffix remains in the native Lane `sendList`; the call returns true.
- A false result before modulation transfers no responsibility. The caller may retry after the gate clears. A true partial
  result must not be reclassified as false or fully replayed while the native Lane is busy.
- Item and fluid return adapters share the original `PatternProviderReturnInventory`. Simulations may each observe the same
  last slot, while the first actual insertion consumes it and the second caller retains its remainder.
- `LOCK_UNTIL_RESULT` tracks the primary output quantity. Byproducts and partial primary returns do not fabricate
  completion; only the full primary amount releases that Lane, without affecting other Lanes.
- Native NBT preserves `sendList`, return inventory, and the outstanding unlock stack across construction of a distinct
  logic/node owner. Reconnecting a target lets the native ticker drain the restored send remainder exactly once.
- Pinned native `addDrops` materializes both `sendList` remainders and return-inventory contents. Accepted target contents
  remain target-owned and are not duplicated in the provider drops.

## Federation parity and evidence

- Federation delegates actual holder keys to the authentic native Lane and does not reconstruct nominal Pattern inputs.
- Endpoint return handlers retain the exact native return inventory established by the authorized Provider context. With
  no native context, no return capability is exposed and caller ownership remains complete.
- Canonical repaired six-case evidence is
  `.omo/evidence/task-19-repair-final2/attempt-20260915T145150639Z/result.json`.
- Fresh Task 17 and Task 18 regressions are under `.omo/evidence/task-19-repair-task17` and
  `.omo/evidence/task-19-repair-task18`.
- Persisted acceptance facts are correlated with independently formatted native observation, state, and target receipts.
  The adversarial self-test fully rebinds report hashes while mutating owner, NBT count, inventory snapshot, drain quantity,
  dismantle drop, target state, and cleanup receipts; every mutation is rejected for its intended semantic reason.

## AE2 19.2.17 repeated `addDrops` behavior

- A focused NeoForge GameTest called the real `PatternProviderLogic.addDrops` twice on the same live lane.
- Both calls emitted the identical responsibility: `minecraft:dirt:1`, `minecraft:diamond:2`, and `minecraft:gold_ingot:2`.
- Native state remained populated after the first call (`send=minecraft:dirt:1`, `returns=minecraft:diamond:2,minecraft:gold_ingot:2`), and the target snapshot remained `8063`.
- Therefore `PatternProviderLogic.addDrops` is observational and non-consuming in the pinned AE2 19.2.17 dependency. Proving an empty second call requires either mutating/clearing the lane, changing production lifecycle behavior, or replacing the native call, all of which are excluded by the Task 19 repair constraints.

## Authenticated dismantle lifecycle

- Oracle adjudication binds Task 19 to AE2's complete caller lifecycle: `addAdditionalDrops` stages observational drops, `clearContent` retires native pattern/send/return responsibility, and owner removal completes dismantling.
- The repaired GameTest records 25 ordered receipts. First collection emits dirt `1`, diamond `2`, and gold `2` while preserving native state; one real `clearContent` empties it; collection after clear emits nothing; the accepted cobblestone target remains unchanged.
- Final conservation is target `1` plus first recovery `5` plus remaining responsibility `0` plus second output `0` plus Federation recovery `0`, totaling the original `6` resources.
- Final source-bound evidence: `.omo/evidence/task-19-final-lifecycle/attempt-20260915T171522605Z/result.json`.

## Exact-schema verifier scope repair

- Receipt-shape and property-name allowlists must execute inside `verifyTaskNineteenEvidence`, after all six property and
  receipt maps are populated. Placing them in another task closure silently leaves persisted Task 19 consumption open.
- The fully rebound matrix now has 46 probes. It separately rejects an injected traced property, omission of the traced
  `nativeRemainderOwnerIdentity`, and a contiguous sequence-26 receipt carrying the authentic dismantle owner.
- Fresh repaired evidence is `.omo/evidence/task-19-schema-repair/attempt-20260915T180333827Z/result.json`; fresh Task 4,
  Task 17, and Task 18 regressions are under `.omo/evidence/task-19-schema-repair-task04`,
  `.omo/evidence/task-19-schema-repair-task17`, and `.omo/evidence/task-19-schema-repair-task18`.
