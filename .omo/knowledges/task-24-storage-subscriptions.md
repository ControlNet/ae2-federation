# Task 24 Storage Subscriptions

## Runtime model

- One subscription is keyed by `ExportSourceId` plus `SourceGeneration`, so alternative paths to the same true native
  source share one listener and one snapshot ledger.
- `StorageServiceNotificationMixin` observes AE2's `StorageService.postWatcherUpdate` boundary. Its amount is an absolute
  native count, not a delta. The callback is forwarded only when the exact native `IStorageService` has a registered
  Federation listener.
- A source event re-reads the changed key from the qualified native `MEStorage`, updates the generation-bound ledger, and
  invalidates each identity-distinct consumer storage service once. Imported Federation views remain excluded by Task 22
  provenance and therefore never become local native origins.
- Quantity updates invalidate consumer caches without refreshing the dependency index or rebuilding effective topology.
  Subscription targets retain relationship, mount-generation, source-generation, Policy, and Fabric currency checks.

## Snapshot and lifecycle boundaries

- Listener registration precedes the initial source snapshot. Events arriving during the snapshot boundary enter a bounded
  FIFO and replay afterward, preventing the initial baseline from erasing a concurrent update.
- Snapshot and event versions are independent. Absolute updates publish only real amount changes; the ledger also supports
  explicit delta input without confusing it with AE2's absolute watcher contract.
- A reset snapshots all affected keys and invalidates current targets even when the baseline is unchanged, which keeps the
  first filter/reset/reconnect observation correct.
- Reconciliation removes a binding when its source generation, native storage handle, or qualified source identity changes.
  Closing a binding closes its ledger and exact listener registration; level teardown closes every remaining binding.
- A stale generation cannot initialize, accept an event, reset a current consumer, or remove a newer binding.
- AE2 watcher updates are aggregate and can mask equal-and-opposite source changes. The server-end hook therefore performs
  one round-robin true-source reconciliation per native storage service per tick; unchanged snapshots do not invalidate.
- Snapshot queue overflow closes the ledger and compare-removes that exact binding. Reconciliation can then install a fresh
  registration for the unchanged plan; a retained callback from the retired binding cannot affect or remove its successor.

## Native fixture lesson

- The four-network diamond fixture must stage Bridge attachment exactly like Task 23. A `succeedWhen` body must stop before
  reading `fixture.d()` until phase 5; otherwise an early tick can dereference a Grid that has not attached yet.
- Run GameTests serially because every producer shares `neoforge-1.21.1/run-gametest/world`.

## Verification

- Canonical cases: `subscription.two-same-key-events`, `subscription.diamond-once`, `subscription.snapshot-race`,
  `subscription.first-filter`, `subscription.listener-cleanup`, and `subscription.reject-stale-generation`.
- Repaired canonical evidence root: `.omo/evidence/task-24-repair-bound-final/`.
- Persisted evidence verification and `federationTaskTwentyFourEvidenceSelfTest` pass. The self-test rejects lost same-key
  events, doubled diamond delivery, delta/absolute confusion, forged reset state, stale-generation acceptance, listener
  leakage, imported re-origination, topology rebuild, missing in-window callback, missing overflow retirement/recovery, and
  stale compare-and-remove claims.
- Fresh Tasks 21, 22, and 23 native regression matrices pass under `.omo/evidence/task-24-repair-regression-21/`,
  `.omo/evidence/task-24-repair-regression-22/`, and `.omo/evidence/task-24-repair-regression-23/`.
- Focused unit/contracts, strict dependency-verified `check build`, sources JAR generation, and shared-JAR isolation pass.

## Second independent-review repair

- Periodic reconciliation never calls the Grid cache or materializes a source listing. Each native service visits at most
  one source provider and probes at most eight source-known keys per tick using `extract(..., SIMULATE, ...)`; provider and
  key cursors resume across ticks, so stable registrations and keys progress after finite churn.
- Initial snapshots and real aggregate callbacks seed each source's key cursor. Equal-opposite changes to known keys remain
  observable per source even when AE2's aggregate amount is unchanged; managed Federation projections remain excluded by
  provenance and never receive source listeners.
- The snapshot GameTest explicitly invalidates and realizes the pinned AE2 cache while `completeSnapshot` is paused. Testmod
  traces record production hub entry, open-ledger acceptance, queued count, completion order, and exactly one replay.
- Test synchronization is owned by `SubscriptionHookOwner`. Fixture close requires every owned hook to be consumed; explicit
  owner abandonment clears pending snapshot, registration, and trace hooks before later boundaries can fire them.
- The repaired canonical evidence root is `.omo/evidence/task-24-second-final/` and includes the added
  `subscription.masked-equal-opposite` native case.

## Third-gate repair

- Each native `IStorageService` owns a shared discovery catalog. Qualified source lifecycle snapshots seed it, every source
  listener receives newly discovered keys, and listeners registered later replay all retained keys. A previously unknown
  aggregate callback key is admitted only when at least one qualified true source currently contains it, so managed-only
  projection keys cannot seed discovery authority.
- Service catalogs and listener cursors each retain at most 64 keys. Retention never silently evicts. The 65th distinct key
  increments a diagnostic receipt, retires every listener on that service, and removes its catalog; normal reconciliation
  can rebuild registrations after overflow pressure is removed.
- Periodic work remains one listener and eight keyed simulated probes per native service per tick. Stable-key revisit is
  guaranteed under the documented supported bound of at most seven newly retained keys between visits to that listener;
  adversarial growth is stopped at the hard memory ceiling.
- Fixture close captures an unconsumed-hook assertion, clears owner state and closes the bridge in `finally`, then rethrows
  the original assertion. Trace state is correlated by owner and exact ledger identity rather than one global active slot.
- The real masking case starts two physical sources at `0/8`, transitions them to `8/0` without aggregate realization, and
  derives source, aggregate, event, delivery, provenance, refresh, and unchanged-follow-up receipts from live state.
- Canonical repaired evidence root: `.omo/evidence/task-24-third-repair-final/`. Task 24 remains unchecked.

## Fourth-gate repair

- Service overflow retirement is hub-owned. `failClosed` first notifies listeners, then snapshot-closes every exact
  registration through `IdentityListenerRegistry.closeAll`, and finally removes the discovery catalog. Correctness no
  longer depends on listeners cooperatively closing themselves.
- Native masking evidence exercises both deterministic registration-order paths: an empty catalog broadcasts discovery to
  both listeners, while a key discovered between registrations is replayed to the late listener. Both preserve `0/8 ->
  8/0` and `8/0 -> 0/8` source-local events under an unchanged aggregate.
- Testmod callback tracing wraps the exact `onAmountChanged` invocation and correlates listener identity with ledger
  identity. A reversed two-owner/two-ledger completion proves no cross-attribution and no stranded owner state.
- Overflow evidence retains a pre-overflow callback and proves it cannot publish, deliver, or mutate authority after exact
  retirement. Recovery then installs fresh registration IDs and receives a real AE2 cache-driven native event.
- Canonical fourth-repair evidence:
  `.omo/evidence/task-24-fourth-repair-final/attempt-20260919T235419263Z/result.json`. Task 24 remains unchecked.
