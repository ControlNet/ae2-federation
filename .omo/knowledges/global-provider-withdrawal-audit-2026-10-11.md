# Global provider withdrawal audit — 2026-10-11

Baseline: `2f0f99fc590a8e99e1e6b78d67db3aac812a345f`.

## Verified bug

An independent `ICraftingProvider` registered using AE2's official global API can remain callable through a consumer projection after the source unregisters it. Native `NetworkCraftingProviders.removeProvider` removes its source medium immediately, but Federation previously discovered removal only on its 20-tick polling pass. A provider is not required to make `isBusy()` or `pushPattern()` self-disable after unregistration.

The new `projectionGlobalWithdrawalImmediate` GameTest retained the consumer medium, removed the source registration, then pushed in the same tick. Before the fix, the accepting provider test double received the push and the test failed with `Withdrawal must prevent same-tick execution through a retained projection`, both in the batch and alone. The complete red log is in the worktree's ignored `.omo/evidence/audit-global-lifecycle-2026-10-11/red.log`.

## Repair

`CraftingGlobalProviderLifecycleMixin` observes successful return from AE2 19.2.17 `CraftingService.removeGlobalCraftingProvider`. It intentionally does not hook the internal helper's remove method: `refreshGlobalCraftingProvider` uses an internal remove/add pair that is not withdrawal.

`CraftingProjectionService.globalProviderRemoved` uses the source service's server captured at construction to find the existing service and withdraws matching projections by Grid and provider object identity. It ignores Federation projections to prevent recursive callbacks and does not replay native registration or scan source nodes. The native registry remains authoritative. Pattern changes and additions retain normal discovery; re-registration makes a new projection instead of reviving the old one.

An additional review found that looking up the server from the current pivot misses a global withdrawal after the last native node disappears. The final regression destroys the native source Grid through explicit node-lifecycle fault injection while keeping consumers loaded. Before capturing server context at construction it fails with `An empty source Grid must not bypass synchronous withdrawal`; the log is `empty-source-red.log` beside the original evidence.

The first empty-Grid fixture directly destroyed `GridNode` instances and left their block owners holding destroyed managed handles; this caused a later batch test to crash. Removing blocks afterward still called stale handles during neighbor updates. The corrected fixture destroys each owner's managed node (and each Router face through its own lifecycle method), which preserves native handle cleanup. No assertions or timeouts were weakened.

The final regression covers two consumer Grids and explicitly checks retained objects after removal and re-registration. The providers are named test doubles; native Grids, registration, and consumer indexes are real AE2 implementations.

## Commands

```sh
python3 tools/dev_gametests.py projectionglobalwithdrawalimmediate projectionglobalpriority projectiondiscoverycost --ci
./gradlew --dependency-verification=strict check
```

The expected GameTest result is 3/3 PASS, covering immediate withdrawal, native planner priority changes, and multi-consumer/ring source discovery. Strict check must succeed. No production-client or real third-party provider evidence is claimed by these tests.

Final local validation on the repaired source: the three-test batch passed 3/3 in 33 seconds with `--ci --no-isolate` (no shared-only outcomes or retries). `./gradlew --dependency-verification=strict check` passed, including the archive gate. The green raw GameTest log is `.omo/evidence/audit-global-lifecycle-2026-10-11/green.log`; the strict check receipt is `check.log` beside it.

## Full-manifest fixture cleanup follow-up

The combined required suite later exposed a remaining fixture lifecycle error that the initial three-case batch did not exercise. Destroying the managed node alone left a source `CraftingBlockEntity` attached to its CPU cluster. When a later GameTest reused the area, native block removal called `breakCluster`, then `updateStatus` / `setExposedOnSides` on an already destroyed managed node. This was test pollution, not the global-provider production hook.

The fixture now removes source block entities through the real block-removal path, allowing native CPU cluster teardown before node destruction. Only the source Router face uses its own `destroy()` method, keeping both consumer Grids loaded. The old source service and both retained projections remain available to exercise explicit global withdrawal after the original Grid becomes empty. All 16 assertions remain.

A negative control on this corrected physical-removal fixture temporarily restored the old pivot-null early return. It still failed `An empty source Grid must not bypass synchronous withdrawal`, proving the empty-Grid check did not become vacuous through earlier topology reconciliation. The guard was restored immediately afterward; production source is unchanged from `4c540b28`. Evidence: `.omo/evidence/audit-global-lifecycle-2026-10-11/physical-empty-red.log`.

Native block removal also requires retiring the source Router face last. A deterministic face-first negative control produced `BlockCapabilityCache.getCapability -> RouterFacePort.resolve/revalidate -> RouterBlockEntity.neighborChanged`: the test had invalidated a face's cache while its owning Switch was still alive, then sent it block-removal neighbor events. The stack is preserved in `fixture-capability-order-red.log`. This is an unsupported partial fixture lifecycle, not evidence of a normal production removal failure. Final cleanup removes native block owners first, destroys the source face afterward, performs all assertions, and removes the actual Switch before returning. The consumer Grids remain loaded throughout the assertions.

Final follow-up validation: `python3 tools/dev_gametests.py --manifest --ci --no-isolate` passed **212/212** in 50 seconds on this worker's full manifest, including `productionproviderclaimchangedcleanup`. No isolation retries or shared-only passes were used. Raw receipt: `.omo/evidence/audit-global-lifecycle-2026-10-11/full-ordered-green.log`. The integrated branch has additional worker tests and must run its own complete manifest separately.
