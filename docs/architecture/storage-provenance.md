# Storage provenance boundary

## Qualified boundary

AE2 `StorageService` continues to own provider registration, mount lifecycle, listener notification, cache
invalidation, priority ordering, preferred-storage selection, filters and every insert/extract/list operation. Federation
does not replay `IStorageProvider.mountInventories` to discover sources. It observes the mount table AE2 actually built:

- A narrow compatibility layer (`mixin/compat`) watches `StorageService$ProviderState.mount(MEStorage,int)` and
  `unmount()` and records, per `StorageService`, each provider's mounted handles and priorities in AE2 order plus a mount
  generation that advances on every real mount or unmount. The internal hook exists because `IStorageService` exposes
  neither a mount-table query nor a mount listener. Ledger state lives on AE2's own service objects, so it disappears with
  the Grid (split, merge, destroy, level unload); nothing global must be cleared.
- `NativeSourceDomainRegistry` is the single source index. It rebuilds a Grid's source domain from that ledger only when
  its stamp changes: Grid and storage-service identity, ledger generation, provider-node activation and AE2 delegate
  links. It never scans all Grid nodes and never replays a provider callback. It holds identities, delegates and
  generations, never quantities.
- Both node providers and native **global** providers (`IStorageService.addGlobalStorageProvider`) are sources, so
  third-party inventories registered through either native path need no Federation registration. Federation's own
  projection and route providers, AE2's crafting-service storage, and complete `NetworkStorage` aggregates are excluded.
- One provider mounting several independent handles (for example a drive with several cells) yields one source per
  handle. The same handle mounted by several providers, or an AE2 `DelegatingMEInventory` chain reaching another mounted
  handle, is deduplicated because that aliasing is provable from native objects. Wrappers sharing an unmounted inner
  inventory (`AMBIGUOUS_SHARED_DELEGATE`) and third-party handles referencing another mounted handle
  (`OPAQUE_EXTERNAL_ALIAS`) fail closed with a diagnostic; they are never guessed into one source.
- A listing evaluates source validity and relationship currency once and then applies the Policy resource filter per
  key. Insert and extract validate per call against cheap revision stamps (ledger generation, Policy revision, Domain
  topology, identity), so disconnect, revocation or a remount stops real operations on the next call.

Measured with 2,500 distinct keys (`storage.source-index-scale`): five listings previously caused 12,505 discovery
rebuilds, 75,030 node scans and 50,020 `mountInventories` replays; they now cause 0, 0 and 0 with 5 source validations.

Compatibility is pinned to AE2 `19.2.17` / commit
[`79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a`](https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a),
specifically `StorageService$ProviderState` and `DelegatingMEInventory.getDelegate()`. If those change, this gate becomes
`BLOCKED` until an equivalent native hook is proven. Opaque-alias detection inspects one level of instance fields of
non-AE2 handles; deeper or indirect sharing is not detectable and is treated as independent, as AE2's own
`NetworkStorage` does. No third-party mod is qualified by these tests.

## CELLS lessons

Research used CELLS revision
[`645901c954d6f9b5e413bbba2202e471beab8141`](https://github.com/Aedial/CELLS/commit/645901c954d6f9b5e413bbba2202e471beab8141).
CELLS targets AE2-UEL/1.12-era APIs; no code or API was ported.

### Election: adapt

CELLS elects one front per origin Grid and uses the election for listing and delta forwarding
([coordinator](https://github.com/Aedial/CELLS/blob/645901c954d6f9b5e413bbba2202e471beab8141/src/main/java/com/cells/parts/subnetproxy/SubnetProxyGridCoordinator.java#L220-L258)).
Commit [`96484a20`](https://github.com/Aedial/CELLS/commit/96484a20f87cbbff90f628de9da4d9af04478de5)
repairs stale coordinator resolution. Adapt the invariant, not the API: Federation deduplicates route aliases at the
stable native-source identity and later topology work must re-resolve elected publishers against live destination Grids.
Reject route-local publication and event-ID-only deduplication because either can duplicate the initial listing.

### Stale source snapshots: adopt for lifecycle work

Issues [#83](https://github.com/Aedial/CELLS/issues/83) and
[#84](https://github.com/Aedial/CELLS/issues/84) show disconnected storage remaining extractable and partition changes
double-counting. Commit [`85cc6bb4`](https://github.com/Aedial/CELLS/commit/85cc6bb46f5d8db8ddc6168af0bbe312a4b3ae48)
retains the previous listing, refreshes active sources, then publishes the net difference
([source refresh](https://github.com/Aedial/CELLS/blob/645901c954d6f9b5e413bbba2202e471beab8141/src/main/java/com/cells/parts/subnetproxy/PartSubnetProxyFront.java#L2030-L2096)).
Adopt this old/new ordering when Tasks 21-25 add mount lifecycle and subscriptions. Task 8 does not add a snapshot cache;
queries return live native handles, so a copied snapshot cannot become spendable inventory.

### First filter: adapt

Commit [`7185f22a`](https://github.com/Aedial/CELLS/commit/7185f22a34b18216e8d8ce9090f11fd123b6771a)
fixes the first filter failing to publish until reload. CELLS separates source-topology dirty state from filter-visibility
dirty state and snapshot-diffs the latter
([filter refresh](https://github.com/Aedial/CELLS/blob/645901c954d6f9b5e413bbba2202e471beab8141/src/main/java/com/cells/parts/subnetproxy/PartSubnetProxyFront.java#L3582-L3637)).
Adapt that separation later: native mount identity changes refresh sources, while Policy/filter changes update visibility
without replacing native handlers. Reject treating filters as source identity or rebuilding all mounts for each edit.

### Listener and reset regressions: adopt the event distinction

CELLS forwards ordinary monitor deltas but treats full-list resets as invalidation and reconciliation
([listener](https://github.com/Aedial/CELLS/blob/645901c954d6f9b5e413bbba2202e471beab8141/src/main/java/com/cells/parts/subnetproxy/PartSubnetProxyFront.java#L3059-L3090)).
Commit [`c3415807`](https://github.com/Aedial/CELLS/commit/c341580712bea05f3befbf6f27edc7067a13bdd6)
coalesces repeated reset rebuilds. Adopt immediate native content deltas, coalesced full resets and complete listener
bootstrap in later subscription work. Reject suppressing AE2 listeners for deduplication; source-event identity must be
carried separately so normal native observers still receive valid changes.

The pinned CELLS commit fixes a separate interface listener problem by carrying the selected resource/direction explicitly
and sending new listeners a full state
([commit](https://github.com/Aedial/CELLS/commit/645901c954d6f9b5e413bbba2202e471beab8141)).
Adapt only the explicit-target/full-bootstrap principle. Reject its GUI packet and 1.12 container implementation as a
storage aggregation mechanism.

## Runtime proof

The exact four cases prove: projection exclusion while retaining a real native store handle; four distinct provider-owned
Federation Domain-route mounts with priorities `40,30,20,10` converging on one source selected at priority `40`; mutual aggregate
mounting diagnosed without recursion; and an unregistered opaque alias rejected without partial results. Persisted
evidence correlates properties with the ordinary test trace, independently injected AE2 mount/delegate traces, and
provider-callback mount traces. Fully rebound forgeries of source identity, diamond source count or priority, loop
acceptance and opaque-alias acceptance must fail at their named semantic boundaries.
