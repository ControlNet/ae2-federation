# Task 6 Native Provider Lanes

- Construct every lane as a real AE2 `PatternProviderLogic`; do not replace native push, target, Blocking, lock,
  send/return, ticker, or NBT behavior with a local executor.
- A forwarding `IManagedGridNode` can capture constructor-installed `IGridTickable` and `ICraftingProvider` services while
  forwarding readiness, activity, and native node identity to one physical managed node.
- Install one composite ticker on the physical node and delegate to every captured native lane ticker. Return the most
  urgent `TickRateModulation` and a request spanning the native delegates.
- Register lane providers with `addGlobalCraftingProvider`, refresh them with `refreshGlobalCraftingProvider`, and remove
  them with `removeGlobalCraftingProvider`. Never route global lanes through `refreshNodeCraftingProvider`.
- AE2 19.2.17 stores decoded Pattern membership and Blocking inputs in private fields. The qualified compatibility hook
  is a Mixin accessor for exactly `patterns` and `patternInputs`; all execution remains in `PatternProviderLogic`.
- Keep one physical `AppEngInternalInventory` for encoded Pattern ownership. Lane-native inventories should have zero
  slots; mapped views decode assigned physical slots into each lane's native membership collections.
- Equal `IPatternDetails` values are deduplicated for craftable display but AE2 retains each registered provider as an
  independent medium, allowing one Pattern to have three execution contexts.
- On pinned AE2 `19.2.17`, internal `CraftingService.getProviders(IPatternDetails)` is the narrow acceptance observer for
  those distinct mediums. Require three results whose identities are the three captured `PatternProviderLogic` lanes,
  and require zero results after global-provider removal.
- Count native ticker delegation only at the composite-to-captured-ticker call site. Wake the physical node through the
  Grid's `ITickManager` and wait for every captured delegate count to become nonzero; a direct composite call is not
  sufficient native ticking proof.
- An allocated Grid does not make a `REQUIRE_CHANNEL` provider active. Native execution proof must connect the physical
  node to a powered AE2 Grid and wait for `isActive()` before calling `pushPattern`.
- Persisted evidence must correlate properties with separately hash-bound runtime traces. Required semantics are three
  observed provider mediums, exact lane identities, all three native ticker delegates, zero providers after close, A
  locked while B/C progress, subset sizes, one Pattern drop, zero lane Pattern slots, no fallback/bypass with a real
  adjacent molecular assembler candidate, and native state restoration.
- Canonical final evidence path is recorded in `.omo/evidence/task-06/DoneClaim.md`.
