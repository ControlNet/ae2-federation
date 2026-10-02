# Cross-network crafting by pattern projection: design (2026-10-02)

Replaces the delegated-job model described in `remote-crafting-2026-10-02.md` and
`docs/architecture/native-crafting-contract.md`. Agreed with the user on 2026-10-02; not implemented yet.

## Why the model changes

The delegated model let the consumer list the provider's craftables as emitable keys and had the provider plan and run
its own native job. So the provider needed its own CPU and materials, and the consumer's materials were never used.
That is AE2's "Interface with a Crafting Card" wiring, not what a player expects from a pattern provider.

The new model behaves like a pattern provider placed on the consumer: **network 1's CPU plans and runs the job, using
everything network 1's storage shows (including network 2's storage, which a crafting rule always brings), and only the
push goes to network 2's real pattern provider and its machines.** Network 2 needs no CPU and no stock.

Every AE2 touchpoint is a public interface (`ICraftingProvider`, `IStorageProvider`, `ICraftingService`,
`IStorageService`, `IPatternDetails`). No mixin, and AE2's or an addon's pattern providers are never wrapped or
replaced, only called through `ICraftingProvider`. This keeps addon providers (ExtendedAE style) and addon pattern
types working.

## Rules

- Each capability's rule switch has three states: **disabled / enabled / enabled (re-export)**. ME power has only
  disabled / enabled: it has no transit. The states map to the existing `PolicyRule.enabled` and `allowReexport`;
  disabling clears `allowReexport`, so "disabled but re-export" cannot exist. The save format does not change.
- Re-export means a network may pass on what it receives through that rule. It is set on the upstream rule: with "2
  uses 3" and "1 uses 2", network 1 reaches network 3 only if "2 uses 3" is enabled (re-export). Along a longer path,
  every rule except the final consumer's own must be re-export (this is what `StorageDependencyCompiler` already does
  for storage). Crafting and storage re-export are independent: "you may use my machines but not see my items" is valid.
- **A crafting rule requires the storage rule of the same pair and direction.** Turning crafting on also turns storage
  on (to at least enabled). While crafting is on, storage cannot be disabled; disabling storage disables crafting in
  the same edit. Old saves with crafting on and storage off get storage turned on when loaded. AE2's planner reads
  `getStorageService().getCachedInventory()` and the CPU extracts from `getStorageService().getInventory()`; there is
  no public CPU-only view, so CPU access to network 2's storage is network 2's storage mounted on network 1. The
  terminal shows it too, as AE2's storage bus on an interface does.
- **No filters in this version.** Crafting on means all of network 2's patterns. Storage on means all of network 2's
  storage. `PolicyFilter` stays in the data model (always ALL; the storage compiler still reads it); new crafting code
  does not read it. The rule editor stops showing "Filter: all resources".
- One server command sets a rule's state with a revision check, as `setPolicy` does. When crafting changes force a
  storage change, both are written in one edit.
- GUI: a three-segment control "Disabled | Enabled | Re-export" made from AE2 button sprites, with the selected segment
  pressed. It replaces the 22x12 switch. While crafting is on, the storage row's "Disabled" segment is locked, with the
  tooltip "Crafting needs storage". The rule note explains re-export with the 1 -> 2 -> 3 example.

## Projection: what network 1 sees

- For each consumer grid C, and each real crafting provider R that C may use, one projection `ICraftingProvider` is
  registered on C with `C.getCraftingService().addGlobalCraftingProvider(...)`.
  - "C may use" covers each direct crafting rule C uses P, plus everything reached through crafting re-export along
    the path.
  - Projections are one per provider, not merged, so AE2's own provider choice, priority and busy handling apply
    unchanged.
- The projection forwards to R:
  - `getAvailablePatterns()` returns R's patterns.
  - `getPatternPriority()` and `isBusy()` return R's values.
  - `pushPattern(details, inputs)` calls R's `pushPattern`. Before pushing, it checks that the rule path is still
    current. When R refuses, it returns false, and AE2's CPU tries another provider or the next tick.
  - `getEmitableItems()` is not forwarded. An emitter with a Crafting Card on network 2 only signals network 2's own
    CPUs; projecting it would leave network 1's job waiting forever.
- Real providers on P's grid are:
  - every active, booted node whose `ICraftingProvider` service sits on that grid;
  - plus Federation Provider lanes, which register with `addGlobalCraftingProvider` (`NativeProviderLaneComposition`).
    A node scan does not see them, so they come from Federation's own lane registry.
- Projections never point at a provider on C itself. Each always resolves to a real provider, never to another
  projection, and is deduplicated per (C, R).
- AE2 has no public "patterns changed" event. Every 20 ticks the service compares each R's `getAvailablePatterns()`
  with what the projection last reported, and on a change calls `refreshGlobalCraftingProvider`.
- Network 1's terminal lists the outputs natively, and the planner mixes them with network 1's own patterns.
  `IPatternDetails` implementations (`AECraftingPattern`, `AEProcessingPattern`, ...) implement `equals`. So R's
  `PatternProviderLogic.pushPattern` check `patterns.contains(details)` accepts the details network 1's CPU decoded,
  also after a reload.

## Execution

1. The player requests on network 1. Network 1's planner uses network 1's cached inventory, which includes the mounted
   storage of network 2.
2. Network 1's CPU extracts the inputs from network 1's storage view. Materials held by network 2 move through the
   storage mount.
3. `CraftingCpuLogic` calls `craftingService.getProviders(details)`, skips busy ones and calls `pushPattern` on the
   projection, which pushes to R. R sends the inputs to its machine; its blocking and lock modes apply as usual.
4. On success the CPU adds the pattern's outputs and container items to `waitingFor` (`CraftingCpuLogic` 218-229).

If network 1 has no CPU, AE2 reports no CPU, as in vanilla.

## Return routing (the one new mechanism)

The machine's output goes back to R. `PatternProviderLogic` line 549 calls `returnInv.injectIntoNetwork` into R's own
grid storage. That grid's `CraftingServiceStorage` only feeds that grid's CPUs. So Federation must carry it to network 1.

- **In-flight ledger.** On every successful push through a projection, record per (executing network, consumer
  network, key) the amounts the CPU now waits for:
  - each `details.getOutputs()` stack;
  - for each input slot i and each key k in `inputs[i]`, the container item
    `details.getInputs()[i].getRemainingKey(k)` with that amount.
  - This mirrors `CraftingCpuHelper.extractPatternInputs` (lines 105-146) with public API only.
  - The ledger is `SavedData`, keyed by network ids, because machines keep working across a reload while the CPU's
    `waitingFor` is restored from NBT.
- **Router.** Each executing grid with ledger entries gets an `IStorageProvider`, added with
  `IStorageService.addGlobalStorageProvider`. It mounts one `MEStorage` at priority `Integer.MAX_VALUE`, whose
  `isPreferredStorageFor` is true only for keys with outstanding ledger entries.
  - Its `insert(K, n, mode, source)` works through the consumer entries for K, oldest push first.
  - For each entry it forwards `min(n left, ledger, consumer.getCraftingService().getRequestedAmount(K))` into the
    consumer grid's `getStorageService().getInventory()`. There the consumer's own `CraftingServiceStorage` (also
    MAX_VALUE, also preferred) hands it to the waiting CPU.
  - It decrements the ledger only on MODULATE, by what was accepted. It returns the total accepted, and the rest
    continues into the executing grid's storage.
  - Any source counts, not only R's return slot: a player may pull the machine output with an import bus, and a
    vanilla CPU also takes awaited keys from any source.
  - The cap by `getRequestedAmount` (the sum of the clusters' `waitingFor`) is required. Anything above it would fall
    into the consumer's other mounts.
  - AE2's `NetworkStorage.insert` returns 0 on recursive use (`mountsInUse`), so a path that leads back into the
    executing grid cannot loop.
  - Same-priority tie: the executing grid's own `CraftingServiceStorage` and the router are both preferred at
    MAX_VALUE, and their order is mount order. If that grid's own CPU waits for the same key at the same time, it may
    take a stack first. This is benign: each CPU waits only for what it pushed, items are fungible, and the next stack
    goes to the other.
- **Ledger decay.** Drop a (consumer, K) entry when the loaded consumer's `getRequestedAmount(K)` is 0, for example
  after the job was cancelled. This is checked on insert and in a 20-tick sweep. The output then stays on the executing
  network, as a cancelled vanilla job's output stays in storage. Byproducts the pattern does not list also stay there,
  and network 1 sees them through the storage rule.

## Lifecycle

- Rule disabled, storage forced off, or the common domain lost: withdraw the projections, so no new pushes happen.
  Already pushed work still returns through the ledger, because network 1 paid the inputs.
- R powered off or rebooting: R's `isBusy`/`pushPattern` refuse, and network 1's CPU waits, as for an offline vanilla
  provider. R's grid or chunk unloads: its projections are withdrawn.
- World reload: network 1's CPU restores its job natively. Projections are rebuilt when rules and grids settle, and
  the ledger reloads from `SavedData`.
- Known limitation: if the consumer is not loaded when the output arrives, the router declines (it cannot read
  `getRequestedAmount`). The output stays on the executing network and the consumer's CPU keeps waiting until the
  player cancels.

## Crafting cycles

`CraftingDependencyCycleGuard` exists for the delegated model. There a provider's own plan could request what its
consumer projects back, so A's job asked B, whose job asked A again, without end. The guard rejected every crafting rule
on a cycle (`CRAFTING_CYCLE`, proved by `crafting.reject-cycle`).

In the projection model it is not needed, and it is removed:

- Only one CPU, on the requesting network, plans and runs a job; no network starts a job for another.
- Projections resolve to real providers. They exclude providers on the consumer itself, and they are computed with a
  visited set over the rule graph. So rules 1 <-> 2 just mean each network may use the other's machines.
- Pattern loops inside one plan (A from B, B from A) are AE2's own concern: `CraftingTreeNode.notRecursive` rejects a
  pattern already on the branch.
- Storage mount cycles are still stopped by the storage compiler's origin-cycle rule. The router cannot loop
  (`mountsInUse`).

`crafting.reject-cycle` becomes a test that mutual rules work.

## Removed with the old model

- The provider CPU/pattern binding gate: `CRAFTING_CPU_MISSING`, `CRAFTING_PROVIDER_MISSING`,
  `NativeCraftingBackendRegistry`'s CPU generation and `CraftingReadinessEvents`.
- `RemoteCraftingService` delegated jobs, `RemoteCraftingRequester` and `CraftingProjection` emitables.
- The terminal path that requested on the provider's crafting service (`NativeTerminalSession` / `NativeTerminalAdapter`).
- `CraftingDependencyCycleGuard`.
- The GameTests and QA evidence groups proving those: the remote-crafting group, the binding gate cases, terminal
  no-cpu and the cycle rejection. `docs/architecture/native-crafting-contract.md` is rewritten, with source links
  pinned to 19.2.17.

## Relation to Provider / Endpoint

These are separate and both stay:

- The Federation Provider holds patterns on its own network and pushes to Endpoints anywhere. Outputs return to the
  Provider's network natively, so it needs no router.
- A crafting rule uses another network's existing pattern providers, including its Federation Provider lanes. Their
  returns land on that network, so the router carries them back.

## Lessons from GTLCore (commit 208cbe9, Forge 1.20.1, LGPLv3: ideas only, no code copied)

- **Pass the provider's own pattern objects.** Some providers advertise modified `IPatternDetails` (GTLCore's ME
  pattern buffer strips the GT circuit) and check `containsKey(details)` on push. So:
  - the projection advertises exactly the objects R's `getAvailablePatterns()` returns;
  - `pushPattern` maps the incoming details to R's own object: first by `equals`, then by `getDefinition()`. The second
    case covers a CPU job restored after a reload, which decodes the pattern again from its item.
- **Providers may never be busy.** GTLCore's buffer always reports `isBusy() == false` and buffers every push. Network
  1's CPU will push as fast as it can, and outputs can come back many ticks later and in bulk. This is another reason
  the ledger is persisted and keeps no time limit.
- **Same grid means no projection.** If two paired networks become one grid (a cable or another mod joins them), C ==
  R's grid, and the projection is skipped.
- **Mount cost.** There are two vanilla AE2 19.2.17 paths, both of which walk every mount.
  - `StorageService.onServerEndTick` (99-107) rebuilds the cached stacks every tick only while the grid has an
    `IStorageWatcherNode`: a storage level emitter or a storage or conversion monitor (`AbstractMonitorPart`).
    Otherwise it only marks the cache stale.
  - Every open terminal calls `NetworkStorage.getAvailableStacks()` directly each tick (`MEStorageMenu` 254, the
    inventory from `AbstractTerminalPart.getInventory` 156-160).

  Federation's storage mount (`AuthorizedStorageProjection` over `StorageMountHelpers.aggregate(domain.sources())`)
  enumerates only the origin's native sources, the cells and drives. It does not enumerate the remote grid's whole
  `NetworkStorage`. So its cost per walk is what those cells would cost locally, and walks do not nest through other
  Federation mounts.

  **Do not switch it to the remote grid's `getCachedInventory()`.** That inventory has the wrong scope: it includes the
  remote grid's own Federation mounts, which would count stacks twice and undo origin keying. It also bypasses
  `NetworkStorage`'s `mountsInUse` recursion guard. `updateCachedStacks` clears `cachedStacksNeedUpdate` before it
  fills the counter, so in an A <-> B pair a re-entrant read returns A's half-built cache.

  The new crafting code adds nothing to this per-tick path: projections enumerate patterns only in the 20-tick refresh,
  and the router acts only on insert.
- GTLCore's AE2 mixins (planner time slicing, `StorageService.updateCachedStacks` throttling, CPU batch push for its
  own providers) show where AE2 hurts at scale. None is needed for this design.

## Deferred: local-first storage preference

AE2 has no "local vs remote" notion, only storage priority. One number governs both directions in `NetworkStorage`:
inserts go highest priority first, and extracts (including a CPU taking its inputs) go lowest first. So the storage
filled first is drained last.

A Federation storage mount today takes the highest priority of the provider's own sources (`StorageMountService`,
usually 0). Against local drives at 0, the order is mount order. So whether network 1's CPU first takes its own or
network 2's materials is not defined by design.

Local-first for both directions would mean two mounts per relationship:

- an extract-only mount just below `Integer.MAX_VALUE`, which reports the stacks;
- an insert-only mount just above `Integer.MIN_VALUE`, which reports nothing.

This is deferred (decision 2026-10-02): it would change every storage rule. The provenance and alias code keys on the
mounted storage and its priority (`NativeSourceDomainRegistry`). Seven storage GameTest classes pin the current
priority behaviour. The bug risk outweighs the benefit for now. Cross-network crafting keeps the existing single mount
and priority unchanged.

## Phases (each green in CI and pushed before the next)

1. **Three-state rule control:** the session command with revision check, the GUI segment control and copy;
   ME power stays two-state.
2. **Crafting requires storage:** linked edit, locked segment, load-time migration, and diagnostics.
3. **Projection P1 (single hop):** projection service, ledger, router. The old emitable projection must not be
   registered for the same rule at the same time. GameTests:
   - request with network 2 having no CPU and the materials on network 1;
   - materials only on network 2;
   - cancel mid-job (the output stays on network 2, the ledger is cleared);
   - world reload mid-job;
   - rule disabled mid-job (the output still returns);
   - two providers with priorities.
4. **P2 (chain and mutual rules):** chains through crafting re-export (with and without), and mutual rules; remove the
   cycle guard.
5. **P3 (remove the delegated model):** delete its tests and evidence, and rewrite the contract doc.
6. **Storage items 3 and 5:** skip only the offending provenance source, and show the storage reason in the UI.

## Implementation notes (Phase B, 2026-10-02)

Code lives in `crafting/projection/`:

- `RealCraftingProviders.on(level, grid)`: node `ICraftingProvider` services, plus Federation Provider lanes from
  `ProviderObservationRegistry`. Lanes register with the crafting service directly (`NativeProviderLaneComposition`)
  and are not node services. Projections are global providers and never node services, so they are never re-projected.
- `PatternProjection`: forwards the real provider's own pattern objects (AE2 pattern `equals` compares the
  definition). `isBusy` is true once withdrawn.
- `CraftingProjectionService`: per level. Reconciles on rule and topology hooks, and on a tick when the
  registry/watermark/identity epoch changes. Patterns are compared every 20 ticks. `status(level, key)` feeds the
  pair editor's runtime line.
- `CraftingReturnLedger` (overworld SavedData) and `CraftingReturnRouter`:
  - The router is mounted at `Integer.MAX_VALUE`, preferred only for owed keys. It lists nothing and extracts
    nothing.
  - It hands back at most `min(left, owed, consumer.getRequestedAmount)`.
  - Debts are forgotten after two 20-tick looks with nothing requested, and never in the first 100 ticks.
- `RemoteCraftingService` no longer ticks; its manifest rows and the `remoteCraftingCases` QA group are gone.

Test gotchas:

- A pattern provider in omni mode pushes round-robin to *every* adjacent inventory, the network's own ME chest
  included. A hand-run machine needs
  `PatternProviderBlock.PUSH_DIRECTION = PushDirection.<side>` (`BlockOrientation.setOn` does not restrict it).
- After a CPU job, the output may land in the provider's chest through the storage mount (same priority, mount order).
  Assert on the sum of both chests.
