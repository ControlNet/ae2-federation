# Native-structure assessment (2026-10-27)

Owner question: does Federation's coupling to AE2 internals make addon compatibility expensive beyond remote crafting?
This note assesses each feature against one test: **does an addon see something an ordinary AE2 player could also
build?** If yes, addon bugs there reproduce without Federation and belong upstream. If no, every addon needs our code.
Findings only; no decision has been made. Inputs: code statistics, the 2026-10-05 mixin audit
(`mixin-replacement-risk-audit-2026-10-05.md`), AE2 19.2.17 sources, Data Energistics main (6c2c046a), Neo ECO main
(1c1e084b), and two read-only fact-finding passes over storage and processing.

## Two kinds of coupling

- Code naming addons: 5 files in about 51k production lines (3 in `crafting/projection`, the DE guide sidebar, guide
  example packs) plus string reflection into ExpandedAE (`FederationPatternProviderTargetCache`).
- Code reaching into AE2 internals: 13 grid-side mixins (6 read private state, 5 observe, 2 change behaviour) and about
  90 non-`appeng.api` imports. By package: processing 38 (`ae2/processing` 20, `processing/provider` 11,
  `processing/endpoint` 7), `mixin/compat` 9, energy 5, p2p 5, `crafting/projection` 5, storage 7.
- AE2's public events (10, all status: power, booting, channels, controller, CPU set, statistics, spatial) cannot
  replace reading or changing state; only `StorageServiceNotificationMixin` has a public replacement
  (`IStorageWatcherNode`). Events are not the lever; AE2's extension interfaces are.

## Storage federation: keep

- Promise: per ordered pair of networks, C sees, inserts into and extracts from P's storages; optional re-export; loops
  show each item once; P's sources keep native priority inside one aggregate mounted on C (`StorageMountHelpers:15-22`).
- Wiring: mount table and `StorageService$ProviderState` mixins (no public query of mounted inventories), watcher
  notification mixin, `DelegatingMEInventory` delegate accessor, one-level field reflection on third-party wrappers
  (`NativeStorageAliasProbe`). The rest uses public `addGlobalStorageProvider`, `invalidateCache` and Grid events.
- Addon collisions: none recorded, and no storage fix needed addon code. All addon storage tests (MEGA Cells, Applied
  Flux, Applied Mekanistics, AE2 Extras, ExtendedAE, ExtendedAE-Plus, Sophisticated Storage) go through the generic
  `AddonStorageScene`. Compat logs show addons mixing into `NetworkStorage`, `DelegatingMEInventory`,
  `MEInventoryHandler`, `InterfaceLogic`, `StorageBusPart`, but none into `StorageService` or `$ProviderState`.
- Native candidate: one `MEStorage` of P's network mounted on C, i.e. a storage bus on a subnet interface. AE2 guards
  recursion with `NetworkStorage.mountsInUse` and `MEInventoryHandler.gettingAvailableContent`; a bus on an interface
  mounts the subnet's whole `NetworkStorage` with no listing cache, and the outer service re-lists it each tick when
  watchers exist.
- What the current design adds that the candidate lacks (recorded): exclusion of Federation imports and of AE2's
  `CraftingServiceStorage` interceptor (in P's aggregate at `Integer.MAX_VALUE`, inserts go to `insertIntoCpus`),
  dedupe of redundant routes (`storageProofFourFederationDomainDiamond`), origin-cycle rejection with re-export, policy
  checks per operation, stale-generation rejection, AuthorityEpoch.
- Measured against that native baseline (`performance-fixes-2026-10-01.md`): listing 90.5 us vs 297.2 us,
  extract+insert 365.6 ns vs 394.8 ns, simulate 138.3 ns vs 121.5 ns. Bus farm 1000x10 median tick: Federation one
  domain 13.9 ms, AE2 one network 14.6 ms, AE2 subnets 15.5 ms.
- Verdict: its coupling is AE2-upgrade cost, not addon cost. Keep. Optional: replace the notification mixin with
  `IStorageWatcherNode` (audit: strong candidate).
- Open check: `NativeStorageNotificationHub.MAX_RETAINED_KEYS = 64`; a source whose discovery passes 64 keys retires
  its subscription (`StorageSubscriptionService` failed starts). The player-visible effect was not traced.

## Processing (Provider/Endpoint): spike a plain provider

- Promise: Provider holds patterns, each mapped to Endpoints; every mapped Endpoint is a lane with its own Blocking,
  lock, send list and returns; a lane pushes into the Endpoint subnet's `ME_STORAGE`; results pushed into an Endpoint
  face go to that lane's return inventory; cross-network and cross-dimension; FE relay to Endpoint machines.
- Wiring: lanes subclass `PatternProviderLogic` (`task-06`: keep native push, Blocking, lock, send/return).
  `PatternProviderLogicTargetBinding` wraps private `findAdapter` to return the authorized Endpoint target and nulls
  `ICraftingMachine.of` inside `pushPattern` (audit: High, "no equivalent target override API").
- Addon collisions (recorded): DE exact-class gate gives lanes one run per push; ExpandedAE replaces `findAdapter`
  itself, so we construct its cache by string reflection, and its 2.1.1 `@Overwrite` of `pushPattern` breaks our
  mixin; AdvancedAE local mode needs reflective `getTargets()`; ExtendedAE-Plus / AE All Pattern / ae2lt lanes were
  skipped until lanes called `super.updatePatterns()`. This is the main addon-collision area outside crafting.
- AE2 facts: targets are the host's `getTargets()` sides at adjacent positions; an adjacent `ICraftingMachine` is tried
  first (no Blocking on that path); otherwise `findAdapter` takes the adjacent `ME_STORAGE` capability, then external
  storage; there is no public way to push anywhere but an adjacent position.
- Native candidate: a plain, unsubclassed `PatternProviderLogic` whose target side has, at the adjacent position, an
  `ME_STORAGE` capability that passes straight through to the Endpoint subnet's storage. That is the same target object
  lanes get today, reached natively, so no buffer is needed. Comes free: Blocking, lock modes, DE batching (inventory
  target, exact class; DE batches into a plain `ICraftingMachine` only if it implements `CountedCraftingMachine`),
  ExpandedAE's own target cache, every addon's provider mixins, no `TargetBinding`, no ExpandedAE reflection.
- Lost or to rebuild: per-Endpoint lanes (one logic has one lock and send state; AE2 round-robins its up to six sides
  with per-target Blocking), authorization and capacity gating (moves into the pass-through), returns from Endpoints
  into the provider's return inventory, flow observation (today from lane `sendList`), FE relay targets, release checks.
- Open design question: which block sits at the Provider's target side (the Federation face it already touches, or a
  separate block).
- Spike: plain provider + pass-through `ME_STORAGE` to a subnet, with DE and ExpandedAE loaded; check Blocking, DE run
  counts and ExpandedAE Smart blocking.

## Remote crafting: spike delegation before P1

- Projection and the CPU route both split CPU (on C) from provider (on P). No player can build that, so every addon's
  same-Grid assumption needs our code. Example: Neo ECO holds job-bound outputs on the provider's Grid
  (`remote-crafting-dual-registration-spike-2026-10-26.md`).
- Native candidate, delegation: C sees X as emitable (`ICraftingProvider.getEmitableItems`, meant for a level emitter
  with crafting card); `ICraftingWatcherNode.onRequestChange` reports C's request; we submit a job on P with our block
  as `ICraftingRequester`, which receives the final output exactly; we insert it into C, whose CPU takes it natively.
  Everything inside P is one ordinary Grid.
- AE2 facts: the planner uses stock, then emitables, then patterns (`CraftingTreeNode.request`), so an emitable X
  shadows C's own patterns for X; the AE2 CPU puts emitted items into `waitingFor` at job start
  (`ExecutingCraftingJob:77`); `getRequestedAmount` sums only AE2 clusters, addons extend it, and Neo ECO 21.2.9's
  cancellable inject cut AdvancedAE and Thunderbolt short (`CraftingServiceReceiptMixin` javadoc). Neo ECO and DE
  planners reference `emittedItems`.
- Costs: materials come from P (escrow from C if "C's materials, P's machines" must stay); C cannot see whether P can
  finish, so P's failures must cancel or report on C; C's preview shows X as emitted; declare X only when C cannot
  craft it itself.
- Spike: C with AE2, Neo ECO, AdvancedAE and DE CPUs ordering an emitable X; P crafting it on a Neo ECO workstation.

## Energy, P2P, identity: keep

- Energy already is a native structure: AE2's `EnergyOverlayGrid`, as a Quartz Fiber (`native-energy-contract.md`).
- P2P uses AE2's `P2PService` frequencies; internal imports are part classes; no addon collision recorded.
- Identity: `CableBusIdentityInitializationMixin` has no public replacement and no addon collision.

## Order

1. Crafting: delegation spike, then decide between delegation and the CPU route.
2. Processing: plain-provider spike.
3. Storage: keep; optional notification-mixin replacement; trace the 64-key retirement.
4. Energy, P2P, identity: keep.
