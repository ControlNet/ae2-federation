# Task 18 Endpoint Modes and Native Returns

- `EndpointRuntime` composes the qualified Task 7 `EndpointCapabilityComposition`; it does not own an inventory,
  scheduler, retry queue, or transfer engine.
- Local ownership is derived from loaded world adjacency. Exactly one directional native
  `PatternProviderBlockEntity` may target the Endpoint, its exact logic/node/return inventory are rebound from the
  world, its source Grid must differ from the Endpoint subnet Grid, and its target side must have no native data edge.
  Caller candidates are advisory and cannot establish a different owner.
- Federated activation requires Task 17's current authoritative owned Claim. Provider authorization additionally checks
  the Endpoint's Federated mode generation before native target resolution. The authorized native Lane then installs its
  exact `PatternProviderLogic.getReturnInv()` as the return owner.
- `EndpointModeGeneration.Local` and `.Federated` are immutable snapshots. Item and fluid return contexts are distinct
  immutable records over one exact native owner. A mode change removes old contexts from new capability lookups but does
  not mutate a handler already held by an outstanding context; it continues targeting only the old Provider inventory.
- The production Processing Endpoint is a bufferless `AENetworkedBlockEntity`, not an AE2 Interface inventory. Its five
  non-Federation faces expose the native subnet node/`ME_STORAGE` plus AE2 `GenericStackItemStorage` and
  `GenericStackFluidStorage` return adapters. The EAST Federation face exposes none of those native capabilities.
- Native adapter results are authoritative. The backpressure GameTest proves simulation does not mutate, a 10-item call
  into four units of remaining capacity returns six to the caller, and an unbound/two-owner Endpoint exposes no sink and
  accepts zero.
- Canonical current-source evidence is
  `.omo/evidence/task-18/attempt-20260915T023556832Z/result.json`. All five exact native cases, immediate persisted
  consumption, and duplicate-owner/wrong-Grid/feedback-loop/stale-generation/false-acceptance/missing-trace adversarial
  probes passed.
- Task 7's exact five native cases passed again after the stronger adjacency derivation at
  `.omo/evidence/task-07-task18-regression/attempt-20260915T023859805Z/result.json`.

## 2026-09-15 provenance and no-sink repair

- `AuthorizedNativeTarget` now carries the exact immutable `EndpointModeGeneration.Federated` snapshot issued by the
  Endpoint plus a package-issued `ProviderLogicProvenance` for one native Lane. The target cache rejects any resolution
  whose provenance object is not the object bound to that cache entry, and Endpoint binding accepts no separate logic
  argument.
- Federated reactivation advances generation even while the Claim lease remains retained. A previously issued target
  therefore cannot install a return owner after reactivation; a fresh authorization must traverse the production
  Provider -> target-cache -> Endpoint path.
- `EndpointItemReturnAttempt.insert` makes the real adapter result explicit as requested, accepted, and remainder
  amounts. An absent handler reports zero acceptance and the complete remainder; simulation and partial execution use
  the same API and preserve AE2 adapter semantics.
- Fresh repaired evidence is
  `.omo/evidence/task-18-repair/attempt-20260915T033309707Z/result.json`. Task 7 and Task 17 regressions are respectively
  `.omo/evidence/task-07-task18-repair-regression/attempt-20260915T033639879Z/result.json` and
  `.omo/evidence/task-17-task18-repair-regression/attempt-20260915T034056452Z/result.json`.

## 2026-09-15 production-bound replay repair

- A stale package-issued Lane authorization was replayed after Federated generation advancement through the native
  Provider resolver, Pattern Provider Mixin, and target cache. The red run proved that the cache exposed a native target
  even when Endpoint return-context capture rejected the stale generation. The cache now treats failed capture as a
  denied lookup.
- Replaying Lane 1's package-issued provenance through Lane 0 is denied by exact provenance-object identity before native
  target acceptance. Both replay cases produce one Mixin/cache lookup, no target found, no fresh or changed return
  context, and zero target or Lane return-inventory mutation.
- The two-owner no-sink case snapshots distinct native return inventories before and after the same production
  `EndpointItemReturnAttempt`; both full nine-slot amount vectors remain unchanged while the attempt reports requested
  10, accepted 0, and remainder 10.
- Canonical repair evidence is
  `.omo/evidence/task-18-repair-final/attempt-20260915T044326377Z/result.json`. Fresh Task 7 and Task 17 regressions are
  `.omo/evidence/task-07-task18-repair-final/attempt-20260915T044906369Z/result.json` and
  `.omo/evidence/task-17-task18-repair-final/attempt-20260915T045250525Z/result.json`.

## 2026-09-15 release-clean replay evidence

- Test-only replay state belongs entirely to the testmod. A testmod Mixin observes genuine `ProviderRuntime.resolveTarget`
  returns and can substitute a previously package-issued authorization on a later invocation without adding fields,
  methods, properties, or bridge classes to common main.
- Observe the production Pattern Provider Mixin downstream at `ProviderTargetTrace.recordMixin`. A second injection into
  the same cancellable `findAdapter` method can be skipped after the production injection sets a return value.
- Negative evidence correlates the exact expected Lane logic, issued and expected provenance objects, resolver owner,
  production Mixin owner, cache owner, cache target result, and item/fluid contexts before and after rejection.
- Parse regex-derived Groovy map keys as `String`, not `GString`, and map trace lookup counts to the persisted
  `ObservedMixinLookupCount`/`ObservedCacheLookupCount` property names explicitly.
- Canonical release-clean Task 18 evidence is
  `.omo/evidence/task-18-repair-release-clean/attempt-20260915T064504514Z/result.json`. Current Task 7 and
  Task 17 regressions are `.omo/evidence/task-07-task18-release-clean/attempt-20260915T064919526Z/result.json` and
  `.omo/evidence/task-17-task18-release-clean/attempt-20260915T065257797Z/result.json`.

## 2026-09-15 production lifecycle and evidence-integrity repair

- `EndpointBlockEntity.onReady()` is the production owner of one `EndpointTargetBinding`. It restores typed Endpoint
  identity, Claim state, configured mode, and generation from NBT, derives Local ownership from current world adjacency,
  and closes the binding idempotently on unload/removal. Serialized state contains no node, Grid, handler, inventory,
  binding, or other runtime object.
- Local neighbor updates re-run production discovery. Federated activation remains gated by the restored authoritative
  owned Claim. Native placement and save/load QA now obtain the production binding rather than constructing fixture
  authority, and prove identity/Claim restoration plus generation advancement.
- Release trace helpers no longer read `ae2federation.testId`. Endpoint and Storage log correlation is injected only by
  testmod Mixins, while release verification scans every binary and source JAR entry for test selectors/replay controls.
- Task 18 evidence is assertion-and-domain-fact based. The unsupported generic `operations`, `inserted`, `extracted`, and
  `elapsedNanos` fields were removed from its producer and are rejected if reintroduced.
- The Task 18 adversarial self-test covers duplicate mode, inventory, and negative trace records, plus missing, malformed,
  contradictory, and substituted variants for every consumed resolver, Mixin, cache, and native-target lookup/found
  count.

## 2026-09-15 owned Federated persistence and release-boundary repair

- A normally placed production Endpoint now has executable evidence for an owned Claim with Provider instance epoch 7.
  The GameTest invokes native save, unload, replacement, load, and ready hooks, then proves exact Endpoint and Provider
  identities, Claim epoch 1, configured Federated mode, a strictly advanced generation, and one fresh binding.
- Testmod Mixins observe production lifecycle and native AE2/storage boundaries directly. Production-only trace classes,
  trace Mixins, and trace calls were removed. The Pattern Provider observer uses priority 500 so it runs before the
  production cancellable injection; the storage-provider observer targets stable `mountInventories` state rather than a
  compiler-generated lambda whose static shape changes when captures change.
- Persisted Task 18 consumption correlates each property with exact save/load/ready trace facts, validates canonical
  identities and lifecycle counts, rejects serialized runtime references, and requires restored capabilities plus fresh
  native input. Fully rebound adversarial probes forge identities, epochs, modes, generations, bindings, lifecycle
  traces, and runtime-reference facts; all are rejected.
- Final current-source evidence: Task 18
  `.omo/evidence/task-18-owned-federated-release/attempt-20260915T111805245Z/result.json`, Task 7
  `.omo/evidence/task-07-trace-cleanup-final2/attempt-20260915T111437631Z/result.json`, Task 8
  `.omo/evidence/task-08-trace-cleanup-release/attempt-20260915T112259370Z/result.json`, and Task 17
  `.omo/evidence/task-17-trace-cleanup-release/attempt-20260915T112631280Z/result.json`.
