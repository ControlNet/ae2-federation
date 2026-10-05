# Production Mixin replacement and compatibility audit

## Scope and evidence

Static audit of the working tree on 2026-10-05, including the uncommitted `client.GuideBuilderMixin`.
The production configuration contains 11 Mixins (7 common, 4 client). The 32 testmod Mixins are
instrumentation, excluded from the production artifact; they are not 32 additional player-facing compatibility hooks.
See `mixin-inventory-2026-10-05.md` for the inventory. No implementation was changed for this audit.

Sources inspected:

- `common/src/main/resources/ae2federation.mixins.json` and every production Mixin and its consumers.
- AE2 19.2.17 source checkout: `StorageService`, `PatternProviderLogic`,
  `DelegatingMEInventory`, `AppEngClient`, `IStorageService`, `IStorageWatcherNode`, `IStackWatcher`.
- GuideME 21.1.1 cached sources and the 21.1.19 source artifact from Maven Central.
- LDLib2 2.2.34 cached sources and the 2.2.41 source artifact from its configured FirstDark Maven repository.
- Decompiled 1.21.1 `MouseHandler` source already available in the local research workspace.
- Existing identity, native storage, and font-thread evidence in this knowledge directory.
- [GuideME custom tag documentation](https://guideme.appliedenergistics.org/integration/custom-tag/).

Build pins are AE2 19.2.17 and LDLib2 2.2.34; GuideME is compile-only 21.1.1. Production compatibility
profiles also exercise newer dependencies. This audit did not rerun their tests and does not certify those profiles.
The repository is being edited concurrently: conclusions describe inspected source, not a frozen release artifact.

Risk ratings below are qualitative, not measured failure probabilities. Distinguish transformation failure,
semantic conflict after successful transformation, and version migration cost. Reflection and access transformers
can remove a Mixin declaration while retaining dependence on the exact same internal implementation.

## Summary

| Mixin | Replacement assessment | Principal retained risk |
|---|---|---|
| CableBusIdentityInitializationMixin | No equivalent public call-scope hook found; deferred settlement is a redesign | Medium/high: network identity during multipart construction |
| PatternProviderLogicAccess | Partly replaceable; complete removal needs lifecycle/observation changes | Medium: private mutable caches and queue representation |
| PatternProviderLogicTargetBinding | No equivalent target override API found; independent provider changes addon compatibility | High: competing provider target hooks and authorization bypass |
| StorageServiceNotificationMixin | Strong public-API replacement candidate, with timing/performance qualification | Medium: private notifications, tick ordering, conditional delivery |
| DelegatingMEInventoryAccessMixin | No general public unwrap API; own-wrapper adapters only cover own objects | Low transformation conflict, medium semantic/version risk |
| StorageServiceMountLedgerMixin | Tick scheduling can move; authoritative table lookup cannot simply disappear | High migration sensitivity; medium runtime conflict |
| StorageServiceProviderStateMixin | No equivalent public mount observation/query API found | High: private inner class and synthetic outer reference |
| LDLibTextThreadMixin | Local subclass only partial; upstream thread fix is preferred | Medium: global cancellation and silently absent injection |
| GuideTooltipThreadMixin | Newer upstream handler has a relevant guard; removal needs client qualification | Low/medium: global hotkey handler cancellation |
| MouseHandlerAccess | GLFW-only restoration is a candidate with asynchronous semantics | Low: cursor cache and input mod interaction |
| GuideBuilderMixin | Own Guide has public registration; same AE2 Guide/custom tag has no inspected hook | Medium: builder lifecycle, tag collisions, client startup |

## 1. CableBusIdentityInitializationMixin

Wraps AE2 `CableBusContainer.addPart(...)` and `addToWorld()` in `NativeIdentityInitialization`.
That scope distinguishes nodes temporarily created during one multipart assembly operation from durable nodes.
`NetworkIdentityGridService` uses it for provisional lineage adoption and returns `PARTIAL_LOAD` while provisional
nodes remain. The code also tracks nodes minted in the current tick, but that does not prove call scope redundant.

Public grid-service node callbacks observe additions/removals, not the complete outer multipart transaction.
Wrapping only Federation-owned blocks misses AE2's own part placement/load paths. Deferring all settlement to a tick
boundary is plausible but changes when identity becomes usable and which newly created nodes may adopt an identity.
It requires explicit invariants for same-tick network merges, saved lineage, and copied NBT before replacing this hook.

Risks: changed descriptors or initialization paths; another wrapper creating nodes outside our scope; cancellation
or exceptions; an addon bypassing these CableBus methods. A successful injection can still cover an incomplete
lifecycle. Consequences include temporary conflicts, incorrect adoption, or unavailable rules. The wrapper calls
the original and closes the scope via try-with-resources, which helps composition but does not establish ordering
with other wrappers. Retain for now; test placement, chunk reload, split/merge and copied-node identity separately.

## 2. PatternProviderLogicAccess

Exposes `patterns`, `patternInputs`, and `sendList`. Current consumers clear the first two during lane lifecycle
operations, inspect the send queue for accepted-flow observation, and query whether sends are pending.

Concrete reductions:

- `hasPendingSend()` can use inherited `isBusy()` in inspected AE2: its implementation is exactly
  `!sendList.isEmpty()`. Test addon modifications of `isBusy()` before treating that as invariant across a pack.
- `getAvailablePatterns()` publicly returns the patterns list in this version. It can replace read access; mutating
  it is not a stable cache-management contract and does not clear `patternInputs`.
- Clearing/rebuilding through `super.updatePatterns()` is promising only when the level and inventory lifecycle are
  valid. The current null-level branch and addon hooks must remain correct. Do not revive manual pattern decoding:
  current `NativeProviderLane` deliberately calls AE2's update path so addons can modify patterns.
- Exact queue snapshots have no equivalent public getter found. Instrumenting the Federation-owned target's actual
  insertions could replace flow accounting, but simulation, retries, delayed delivery and duplicate counting require
  a deliberate observation redesign. Serializing full provider NBT per operation is not an attractive substitute.

Risks: fields renamed or represented differently; direct cache mutation skipping addon-maintained state; flow
calculations assuming a particular queue lifecycle. Accessors do not cancel another mod's code, so direct injector
conflict is lower than target binding. Partial removal is justified; complete removal is not a one-line change.

## 3. PatternProviderLogicTargetBinding

Cancels `findAdapter` at HEAD for a bound lane, returning its authorized Federation target (including null when
unauthorized). Wraps the `ICraftingMachine.of` call inside `pushPattern` to suppress physical-neighbor delivery for
bound lanes. AE2 otherwise tries adjacent crafting machines before generic inventory targets.

`findAdapter` is private. Registering a block capability at the real adjacent position does not implement arbitrary
remote targeting and does not prevent the earlier crafting-machine path. A standalone `ICraftingProvider` can avoid
this Mixin, but reimplementing native provider behavior can lose addon transformations, blocking, locking, retries
and return behavior. The preferred upstream extension would expose target resolution and crafting-machine selection.

Risks: other cancellable HEAD hooks or replacement target types; another mod rewriting/removing the invocation;
native control-flow changes introducing a new path that bypasses authorization. The local target cache already has
an ExpandedAE-specific reflective adapter, evidence that target semantics need addon-aware integration.
`WrapOperation` is more composable than `Redirect`, but the bound branch intentionally does NOT call `original`:
inner wrappers/original behavior can be skipped. Do not claim every wrapper will execute.

Highest-value tests: place a real crafting machine next to the physical provider and prove it receives no bound
lane inputs; revoke authorization while pending; exercise addon blocking and transformed patterns on bound lanes;
verify unbound native providers remain unaffected. Retain narrowly scoped behavior until an equivalent hook exists.

## 4. StorageServiceNotificationMixin

Publishes `postWatcherUpdate` at HEAD to `NativeStorageNotificationHub` and reconciles at server tick TAIL.
An actual public replacement exists: attach `IStorageWatcherNode` to an appropriate Federation node, call
`IStackWatcher.setWatchAll(true)` in `updateWatcher`, and forward `onStackChange` to the hub. This includes newly
appearing keys, not just a predetermined watch list. A Federation grid-service tick or server-tick scheduler can
perform reconciliation without injecting into StorageService.

This is a feasibility finding, not proof of drop-in equivalence:

- Register before node creation; handle grid transfers, removal, no-observer periods, and multiple Federation nodes
  in one grid without duplicate publication. Tie the callback to the current service, not an obsolete grid.
- Maintain the hub's source-specific amount probes, bounded discovery and overflow behavior: a network aggregate
  alone is not a particular exported source's amount.
- AE2 19.2.17 rebuilds available stacks every tick when its interest manager is nonempty. Enabling watch-all can
  introduce full-network scans where the existing passive hook does not. Benchmark rather than assuming savings.
- Conversely, the existing hook is only called when AE2 actually emits watcher updates; no native interest means
  end-tick may merely invalidate cache. The Mixin alone does not guarantee continuous all-key notifications.
- Preserve ordering relative to storage refresh, mount reconciliation and policy invalidation. A generic tick
  listener may have different ordering from StorageService's TAIL.

Retained risks are private method changes, notification suppression by other injectors, and publication before
other watcher callbacks. Recommend this as the first public-API replacement prototype, with explicit performance
and lifecycle gates.

## 5. DelegatingMEInventoryAccessMixin

Exposes protected `getDelegate()` for source-alias discovery and chain validation. There is no general public
unwrapping method on `MEStorage`. Subclassing exposes delegates only for objects we create, not existing AE2/addon
objects. An explicit adapter registry would reduce reliance for participating wrappers but cannot cover arbitrary
wrappers automatically. Removing chain knowledge changes conservative alias handling and available sharing paths.

Retain this small bridge for now. Method signature changes and overridden delegate behavior are the main risks.
The caller correctly distinguishes plain `DelegatingMEInventory` (exact class) from subclasses before treating
forwarding as transparent; preserve that distinction. Do not flatten a filtering/security wrapper into its delegate
for actual insert/extract operations. Test dynamic delegate swaps, wrapper chains and cycles.

## 6. StorageServiceMountLedgerMixin

Exposes private node/global provider tables and stores generation/dirty state. The TAIL hook coalesces source-domain
reconciliation. `IStorageService` has mount registration/refresh APIs and aggregate inventory access, but no query
returning the actual per-provider mounted inventory identities and priorities.

The tick hook and generation ownership could move to Federation-owned services. That does not replace access to
the underlying tables. Observing only our own providers misses native drives, storage buses and addon global
providers. Replaying `mountInventories` is not an authoritative query and can have provider side effects; aggregate
inventory polling loses provenance. An upstream mount snapshot/change API is the meaningful removal path.

Risks: private map/list representation changes, an alternative storage-service implementation, and two separate
TAIL hooks on `StorageService.onServerEndTick` whose ordering should not be assumed without a test. Stale snapshots
can keep source relationships available too long or make valid shares unavailable. Read-only table access lowers
direct behavior interference but cannot guarantee a snapshot matches another mod's mounting semantics.

## 7. StorageServiceProviderStateMixin

Records successful `mount(MEStorage,int)` at TAIL and clears on `unmount()` TAIL. Shadows `provider` and synthetic
outer-instance field `this$0` on AE2's private inner class. It supplies data required by the preceding ledger.

No equivalent public observer was found. Independently mounting again or observing node topology misses storage
refreshes, inventory/priority changes and global providers. It should migrate together with the ledger if upstream
adds a mount API.

This is the most structurally fragile target: inner-class refactors, synthetic field changes, changed mount paths
and other code modifying NetworkStorage directly. Partial mutation followed by an exception can also leave the
mirror inconsistent; TAIL guarantees normal completion of the target path, not transactional atomicity across all
mounting code. Test refresh, failed mount, add/remove global provider, priority changes, split/merge and addon cells.
Keep critical failures visible rather than silently disabling the ledger and proceeding with incomplete provenance.

## 8. LDLibTextThreadMixin

Cancels `TextElement.recompute` off Minecraft's main thread. Existing evidence captured integrated-server menu
construction touching client font caches. Both inspected LDLib2 2.2.34 and 2.2.41 still start recompute with only
`LDLib2.isClient()`, so upgrading to 2.2.41 alone is not source evidence that this guard can be removed.

A thread-safe local TextElement subclass or client-only UI layout construction can eliminate our own calls, but
labels/widgets instantiated inside LDLib or other addons would remain. An upstream thread-aware layout fix is the
best complete replacement. Scheduling every call on the main thread is not automatically equivalent either:
server UI object lifetime, mutation and measurement ordering matter.

Risks: cancellation applies globally, so another user's background layout work can be skipped; skipped work is not
rescheduled. `require=0` permits the injection to stop matching without an error, potentially reintroducing the
font defect. Keep a runtime font-thread assertion test and qualify integrated-server/client reload behavior, not
only dedicated-server boot.

## 9. GuideTooltipThreadMixin

Cancels GuideME's private interactive tooltip handler off the main thread. Cancelling or editing a tooltip after
the handler runs would not undo font access or shared-hotkey-state mutation. The handler is registered with an
anonymous event listener; a competing event listener is not a clean replacement for this narrow guard.

Important version finding: GuideME 21.1.19's event listener rejects events whose entity is not the current local
player, explicitly mentioning creative-search indexing. The inspected 21.1.1 listener has no such guard. This gives
a concrete upstream route for removing our patch after upgrading the supported minimum and running client tests.
Entity identity is not a general thread check: an addon could still construct an off-thread tooltip with the player,
and the guard's null-player case is not equivalent to our check. Do not certify removal from source alone.

Retained risks: global hotkey behavior, private method changes and `require=0` silently losing the guard. Verify async
search, reload, normal hold-to-open behavior and zero font-thread violations without the Mixin on supported versions.
The older font-thread knowledge note describes required injections; current source uses `require=0`, so that note's
wording is historical rather than the current configuration.

## 10. MouseHandlerAccess

Sets private cached `xpos/ypos` after `glfwSetCursorPos` when switching Federation screens. The native mouse callback
updates those fields too, but `MouseHandler.onMove` is private and public position methods are getters.

Try GLFW-only restoration and let the normal callback update Minecraft. That removes direct field access but must
be tested for first-frame hover/click accuracy and event delivery timing; it is not yet proven synchronous-equivalent.
Avoid replacing GLFW's global callbacks or calling broad grab/release methods to emulate a small cursor restoration.
Accepting cursor recentering is an explicit UX reduction, not an equivalent replacement.

Risk is relatively low: field mapping changes, input mods replacing coordinate semantics and stale deltas/hover
between events. This is a suitable low-cost experiment, not a priority compared with storage/target correctness.

## 11. GuideBuilderMixin

Adds the custom topology compiler only when `GuideBuilder.id` is `ae2:guide`. In inspected GuideME 21.1.1 and
21.1.19, extensions enter through the builder; AE2 19.2.17 constructs that builder privately in
`AppEngClient.createGuide()`. No post-build extension registration API was found in the inspected path.

Non-Mixin options are real but change the product choice:

- Build a Federation-owned Guide with public `Guide.builder(...).extension(...)`; preserve the custom renderer,
  but change the current integration into AE2's existing book and navigation.
- Keep the AE2 book and author diagrams with supported tags/images; remove this compiler dependency, but replace
  custom layout and any associated interaction/accessibility behavior.
- Obtain an upstream guide-builder extension event/API for addons; then retain both current integration and tags.

Risks: builder internals or creation lifecycle changes; duplicate extension registration if a builder is reused;
tag-name collision and interactions with other compiler registrations. Current tag is `FederationTopology`; official
GuideME documentation recommends a mod-ID prefix for custom tags. Registration only for `ae2:guide` limits scope.
Because this is in a required config, a broken cosmetic integration can still affect client startup: separate
optional-feature fallback from critical storage hooks, with explicit diagnostic pages if custom tags are unavailable.

## Priorities and verification boundaries

1. Prototype the public storage watcher bridge with scan-cost, duplicate-event and tick-order tests.
2. Remove redundant provider pending-queue access; separately design lifecycle cache clearing and flow observation.
3. Qualify removal of the GuideME workaround on a deliberately supported newer minimum. LDLib still needs a fix.
4. Keep source provenance, multipart identity and bound target hooks small; seek upstream extension points.
5. Evaluate cursor callback restoration and the guide ownership/content tradeoff independently of core mechanics.

Current config is `required=true`, `defaultRequire=1`; only the two thread injectors explicitly use `require=0`.
This does not make every possible shadow/class failure optional. Do not globally loosen injection requirements:
startup success with missing provenance/authorization behavior is worse than a clear incompatibility diagnostic.
Declared dependency ranges have no upper bound, which is broader than the inspected/tested versions.

Commands for a future implementation change, not executed by this static audit:

```sh
./gradlew :neoforge-1.21.1:test :neoforge-1.21.1:verifySharedJarContent --dependency-verification=strict --console=plain
./gradlew :neoforge-1.21.1:federationUiTest -Pcases=ui.graph-controls,ui.mapping,ui.endpoint,ui.multipart-attachments,ui.chinese-scales,ui.reject-claim-conflict -PevidenceDir=.omo/evidence/mixin-thread-audit --dependency-verification=strict --no-configuration-cache --console=plain
```

Expected: unit tests/artifact boundary checks pass; UI scenes pass with zero font-thread violations in the actual
client log. These checks are necessary but not sufficient: rerun production addon profiles and the targeted lifecycle,
authorization and queue cases described above. Use the existing production runner documentation for profile setup.
Testmod injection conflicts must be distinguished from release conflicts by reproducing in the production harness,
whose compatibility test artifact deliberately excludes the development instrumentation Mixins.
