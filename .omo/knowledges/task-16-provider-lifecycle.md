# Task 16 Mapped Native Provider Lifecycle

- `MappedPatternProvider` owns one physical encoded-Pattern inventory and a deterministic slot-to-Lane mapping. It composes
  Task 6's real `PatternProviderLogic` lanes and does not implement crafting, decoding, target selection, send/return,
  Blocking, lock, or recovery logic.
- Mapping updates use `PatternSlotHandle(slot, generation)`. Bounds are checked before mutation, stale generations are
  rejected, and only the union of the old and new Lane assignments is refreshed through
  `refreshGlobalCraftingProvider`.
- Physical inventory insertion, replacement, removal, extraction, save, NBT, terminal access, and encoded-Pattern drops
  have one owner. Lane-native inventories remain size zero; their native NBT and drops contain only execution state.
- Every refresh rebuilds AE2's pinned `patterns` and `patternInputs` fields from the current physical slots. Each decoded
  handle is tracked by identity before delegating to `super.pushPattern`, so removed, replaced, and pre-load handles are
  rejected even when their Pattern details compare equal.
- Priority is applied to each native Lane and followed by global-provider refresh. Redstone updates and main-node state
  changes fan out to the native Lane logic. `MappedPatternProviderHost` exposes only the physical inventory to the
  Pattern Access Terminal while retaining AE2's terminal visibility setting.
- One physical managed node owns one composite `IGridTickable` and no node-local crafting provider. For three Lanes, the
  composite has three captured native ticker delegates and the crafting service has three distinct global provider media.
  Closing removes global providers; destroying the physical node removes its ticker service.
- Runtime qualification uses 3 physical Pattern slots, 3 Lanes, 5 mapped provider entries, and 2 distinct native media for
  one equal Pattern. These are tested cardinalities, not public gameplay limits.
- Current source-bound evidence is `.omo/evidence/task-16/attempt-20260914T220758989Z/result.json`; all five exact cases
  and adversarial probes passed.
- Compatibility remains pinned to AE2 19.2.17. The private hook is the existing Task 6 accessor for
  `PatternProviderLogic.patterns` and `patternInputs`; source and bytecode agree on global provider refresh, priority,
  redstone, terminal, NBT, push membership, and ticker APIs.
