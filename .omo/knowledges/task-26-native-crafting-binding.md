# Task 26 Native Crafting Binding

## Contract

- A Crafting relationship is keyed by directional `PolicyKey(consumer, provider, CRAFTING)`, not by a physical Bridge route.
- The binding exposes the provider Grid's exact `ICraftingService`, `ICraftingProvider`, provider node, and CPU objects. Federation owns authorization and invalidation only.
- A binding is current only while its Policy revision, Fabric topology revision, provider generation, source Grid identity, native providers, and native CPUs remain current.
- Policy disable/delete/stale binding use, Fabric loss, provider loss/replacement, or unavailable native CPU capacity withdraws the capability before the next binding access.
- Multiple Bridge routes between the same directional network pair retain one relationship and do not multiply patterns, providers, or CPUs.

## Runtime Fixture Lesson

Identity-bearing AE2 blocks must be added after the base consumer/provider Grids settle in GameTests. Their managed nodes
are seeded with that settled provider `NetworkId` before their first server tick and added in stages. Otherwise the Pattern
Provider, assembler, and CPU can briefly settle independently before merging and correctly produce `AMBIGUOUS_MERGE`.
Newly placed Bridge parts also need a neighbor refresh after their outer node settles.

## Canonical GameTests

- `craftingnativebinding`: exact native identity and execution, Policy revision gates, then live final-Fabric withdrawal with
  pre/post binding, Fabric, topology, withdrawal-counter, and denied-access receipts.
- `craftingdeduplicatecapability`: two physical routes, one relationship/provider/pattern/CPU capacity.
- `craftingnativestateowner`: provider A removal and distinct real provider B installation on the same settled source;
  generation/new binding advance plus retained-A denial after B becomes current.
- `craftingrejectunavailable`: no native CPU means no advertised capability or fallback execution.

All four tests passed serially with `:neoforge-1.21.1:runGameTestServer` and their individual `-PfederationGameTestId` values on 2026-09-20.

## Independent Authority Evidence

`NativeCraftingEvidence` writes semantic properties and `AE2F_CRAFT_NATIVE_ENTRY`; those are intentionally one channel.
`NativeCraftingAuthorityReceipt` is the independent channel: it scans the current AE2 Grid nodes and native services
directly and emits no semantic Map. Persisted verification requires exact per-child phases and correlates NetworkId,
Grid/service, node UUID/object, provider, CPUs, pattern object, binding/generation, Fabric/topology, and withdrawal state.

The fully rebound self-test keeps artifact hashes, paths, run identity, and semantic entry lines current while mutating
source Grid/NetworkId, service, node UUID/object, provider, CPU, pattern, replacement-A authority, and withdrawal counters.
It also rejects missing, duplicate, substituted-child, and conflicting authority receipts.
