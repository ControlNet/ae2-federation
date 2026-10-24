# Native Crafting Contract

## Status

Task 9 is complete on the locked Minecraft 1.21.1, NeoForge 21.1.250, AE2 19.2.17, and Java 21 runtime.
Five serialized GameTests prove terminal-equivalent submission, automated stocking, missing-material rejection,
no-CPU rejection, and requester disconnect/persist/reload/cancel behavior through AE2's native crafting service.

The accepted public lifecycle is:

- `ICraftingService.beginCraftingCalculation(...)`
- `ICraftingService.submitJob(...)`
- `ICraftingSubmitResult.link()` for requester-owned jobs
- `ICraftingLink.writeToNBT(...)`
- `StorageHelper.loadCraftingLink(...)`

No compatibility shim, replacement planner, replacement CPU, or alternate crafting ledger is used.

## Cross-Network Crafting

Network C crafts with network P's pattern providers as AE2 does within one network: C's own CPU plans and runs the
job, with what C's storage shows, and pushes each pattern to P's provider. P needs no CPU. The code is in
`crafting/projection/`. Planning, submission, execution and provider registration use AE2 API; the read-only
compatibility access described below is pinned to AE2 19.2.17.

A crafting rule "C uses P" projects P's providers onto C while all of these hold:

- the rule is enabled and has the `REQUEST` operation;
- the rule is active for the pair, and C and P share a Federation Domain.

The same direction's storage rule is independent of it. Pushes go through the projection and results come back
through the return router, so neither needs storage. The storage rule only adds P's items to what C's CPU can plan
and pay with; without it, C pays with its own storage and P's leftovers stay out of C's sight. The Policy screen
switches the two rules separately, and loading a save adds no rule.

- **Projection.** `RealCraftingProviders` lists P's own providers: the `ICraftingProvider` node services on P's Grid,
  plus every native global registration, including Federation Provider lanes and third-party providers without node services.
  Read-only accessors inspect AE2's `CraftingService.craftingProviders`, `NetworkCraftingProviders.globalProviders`,
  and `ProviderState.provider`; no registration is replayed. Objects are deduplicated by identity, and Federation
  projections are excluded. Priority-only edits refresh each consumer registration once; unchanged checks do not remount. Each one gets a `PatternProjection` on C's crafting service
  (`addGlobalCraftingProvider`). It offers the real provider's own pattern objects, priority and busy state, and no
  emitable items. A push goes straight to the real provider. A withdrawn projection offers no patterns, reports busy and
  refuses pushes before it is removed, so nothing is pushed through it afterwards.
- **Returns.** An accepted push records what P now owes C in `CraftingReturnLedger`: the pattern's outputs, plus the
  container items its inputs leave behind, such as empty buckets. The ledger is saved data, so a debt survives a
  reload. Every `AEKey` type takes the same path: fluid outputs (vanilla GameTests) and Applied Mekanistics chemical
  outputs (the `mekanism` compatibility profile) are routed, held, handed back and settled as items are, including
  owed, held and transit amounts read back after a restart, chemicals held while the Bridge is removed, and fluid
  outputs of jobs on Neo ECO, Advanced AE and Lightning Tech CPUs, also when the networks are joined into one Grid
  mid-job (each addon's `*MergedFluidReturn` test, with the AE2 CPU control `nativeCpuMergedFluidReturn` in `core`).
  - P's `CraftingReturnRouter` is mounted on P's storage at `Integer.MAX_VALUE` and is preferred only for owed keys. It
    lists and extracts real saved transit stock. It does not list debt or `held` as additional inventory.
  - It hands back at most `min(inserted, owed, C.getRequestedAmount)` into C's storage, where AE2's
    `CraftingServiceStorage` gives it to the waiting CPU first. Any surplus stays on P.
  - AE2 counts only its own CPU clusters in `getRequestedAmount`. The optional lifecycle adapters provide exact
    waiting amounts for recognized independent CPUs. For an unsupported busy CPU listed by native `isRequesting`,
    the conservative cap is the debt; the public CPU interface supplies no exact waiting amount.
  - The container items are counted from the inputs C's CPU extracted for the push, as AE2's `CraftingCpuHelper`
    counts them, before the push: AE2's Molecular Assembler empties the input holder it is pushed, so nothing can be
    read back afterwards. They are owed only if the push is accepted; the holder goes to the provider untouched.
  - Each push observes C's busy CPUs. Native jobs retain the link ID, dimension and CPU position. Unknown
    independent `ICraftingCPU` implementations expose no ID or position in the public interface, so their uncertainty
    is saved explicitly, never interpreted as completion. A small optional adapter reads Neo ECO 21.2.1's concrete
    `ECOCraftingCPU.getOwner/getLogic` and `getLastLink/getWaitingFor` methods, and Lightning Tech 2.1.1's
    `TimeWheelCraftingCPU.getHost/getCraftingLogic` methods. Advanced AE 1.6.12 uses its public `getSrc().machine()`
    block anchor and public `craftingLogic` field. These are version-sensitive dependencies, not proof that every
    addon lifecycle or planner path was tested. Only block-entity anchors with a dimension are accepted. Multiple
    independent CPUs can share an anchor: lifecycle lookup must match the crafting UUID across every slot, and a
    different job at that anchor never proves completion. An unformed or missing addon CPU remains unknown unless
    native terminal evidence exists. Neo ECO's `ECOCraftingJobLifecycle.isTerminated(Level, UUID)` reads its own saved
    terminal record after a completed CPU retires. The adapter requires `isFormed()` and no pending entries in the
    private `deferredInit` array (pinned to 21.2.1); any deferred or quarantined slot suppresses this inference because
    resuming persisted jobs can clear a temporary cancellation marker. No lifecycle registration is replayed.
    Lightning Tech 2.1.1's active loaded Pigmee owner proves historical jobs ended only when its native
    `TimeWheelCraftingCpuPool.hasPersistentState()` is false. That full pool includes pending-loaded CPUs; a slot
    remains until jobs, pending restores, inventories and requester outputs drain. This read-only public API probe
    does not infer completion from the Grid CPU list. Actual chunk unload and a separate server-process restart
    are covered with Storage never enabled; the compatibility fixture uses a hand-operated chest processor.
    Advanced AE 1.6.12's active loaded owner can likewise resolve historical jobs only when the non-destroyed
    cluster's private `activeCpus` map is empty and every loaded member has no `getPreviousState()` pending NBT.
    The private field dependency is centralized in the adapter; `getActiveCPUs()` is deliberately avoided because
    it prunes native CPU slots. Native restore inserts each slot before restoring its logic, then clears pending
    state. A different running job, any pending restore or an unloaded member suppresses terminal inference.
    The additive saved job outcome `TERMINATED` means the addon proves ending without exposing completion versus
    cancellation. Existing worlds load unchanged; downgrading to a version whose enum lacks `TERMINATED` is not
    supported after such a record is saved. The real Neo ECO processing CPU test waits for both declared outputs
    before finishing; native CPU early completion must not be generalized to that implementation.
  - Return sweeps run every 20 ticks from the service's first tick, with no startup delay: the first job after a
    server start can finish within seconds and leave containers held once its CPU stops waiting. Loaded CPU lifecycle
    checks decide when those arrivals can enter ordinary storage; unloaded or unformed CPUs remain unresolved.
  - Debt is removed only by actual delivery. Zero waiting, two idle observations, a completed job, cancellation,
    or attempting a return cannot prove that all machine outputs arrived. A completed/cancelled consumer receives
    its outstanding arrivals into its own storage once all observed jobs are resolved. With mixed running/unloaded
    jobs, stock goes only to an actively requesting CPU; leftovers remain held until that ambiguity clears. There is
    no invented job-to-output association. Unknown lifecycles conservatively retain debt and may require manual
    recovery from the executing network's own storage after cancellation. `/ae2federation returns list` exposes owed,
    held, transit, and unknown-CPU state to operators. No Storage rule is required for automatic return.
  - When native cable joins P and C into one AE2 Grid (a Bridge part removed, a cable placed between them), their
    identities are a pending merge: Federation sees neither, withdraws the projections and unmounts the router, and
    AE2's `CraftingServiceStorage`, mounted first at `Integer.MAX_VALUE`, hands P's output straight to the waiting CPUs
    through `CraftingService.insertIntoCpus`. That receipt is the delivery. Every addon that runs its own CPUs feeds
    them from that method and adds their waits to `getRequestedAmount`: Neo ECO, Advanced AE, Lightning Tech (through
    Thunderbolt) and Data Energistics' Trinity Data Core. So one AE2 19.2.17 `@WrapMethod` around `insertIntoCpus`,
    outermost by its mixin priority, sees AE2's and the addons' CPUs alike. For a MODULATE insert while the Grid carries
    both a network that owes the key and the network it owes, it reads how much the CPUs wait for before and after the
    call: the larger of `getRequestedAmount` and the sum over `getCpus()` of the lifecycle adapters' exact waits. The
    second count is needed because one addon's hook can cut the others short: Neo ECO 21.2.9's cancellable return inject
    on `getRequestedAmount` returns before Advanced AE's and Thunderbolt's run, so in a pack with Neo ECO (the NAST
    pack) the public total misses Advanced AE and Lightning Tech CPUs. With `owed` what such debtors owe such consumers
    of the key, it settles `min(owed, max(0, taken - max(0, before - owed)))`, oldest debt first, where `taken =
    min(amount, before - after)`. It uses the fall in the waits, not the method's result: AE2 counts a final output as
    received even when the job has no requester to take it, as a terminal job has, and then returns 0 for it. The cap at
    `amount` keeps a finishing job's cleared surplus waits out of `taken`. The public CPU listener cannot do this
    either: it names only a key, and a finishing job clears its remaining waits with the same notification. Outside a
    joined Grid no Grid carries both ends of a debt, so the router stays the only path that settles; a hand-back into
    C's storage passes C's `insertIntoCpus` and settles nothing there.
  - Limits of the joined-Grid receipt. AE2 does not say which job took an output, so other waits for the same key count
    first; when the projected job took it anyway, its debt stays, and after the split the next unit P stores is handed
    to the finished job. One output can end the wait of every requester-less job waiting for it as its final output;
    `taken` is capped at the amount delivered, so that counts once, again for other waits first. A debt whose job has
    already ended (cancelled with outputs outstanding) is still counted in `owed`, so another job's receipt of that key
    in the joined Grid can settle it. A saturated total (`Long.MAX_VALUE`) settles nothing. A wait that a CPU ends for
    another key than the one delivered (AE2 Utility's fuzzy matching, Useless Mod's and Lightning Tech's claims for
    other expected outputs) does not show in the delivered key's waits and settles nothing; neither does a CPU missing
    from both counts. Two addon paths reach a CPU without `insertIntoCpus`: Neo ECO's `insertIntoCpuForJob`, used only
    for a batch an ECO CPU pushed straight to an ECO parallel provider, never through a projection; and Data
    Energistics' Trinity pattern cores, which route their outputs into their own Data Core's CPUs
    (`insertExactIntoCpus`), not covered when P's provider is a Trinity pattern core. Lightning Tech's Tianshu
    supercomputer plans only from the Matrix controllers on its own Grid and runs on an escrow of the inputs, so it
    never pushes through a projection and never waits for an output. An output that arrives in the joined Grid when no
    CPU waits for it goes to shared storage and keeps its debt.
  - Genuine native CPU removal ends its native job but does not erase machine return ownership. Outputs from already
    dispatched inputs still belong to the consumer, including after cancellation; they no longer depend on sharing
    the factory's inventory. If the consumer itself is permanently removed, responsibility remains diagnostic state;
    the executing network still owns the physical storage from which a player can recover it.
  - A return crosses only a live Federation link: P and C share a Federation Domain, or are joined through networks
    the domains share, as a chained push is (`FederationLinks`). Switching the rule off keeps returns going, because C
    paid the inputs. An output that arrives while the link is broken, or while C is not loaded, stays in P's storage
    and is counted as held for C (`CraftingReturnLedger`), at most what is owed. Each 20-tick look then hands C, once
    it is linked and loaded, what is held and its CPUs still wait for, taken only from P's own mounts (never
    Federation's mounts of other networks' storage, which may be C's). If P's players used it up, C waits until P
    stores that much again.
  - Handing held stock back uses simulated capacity checks and compensating storage (`HeldReturnTransfer`), not a
    transaction or reservation. Only what both P's sources and C's storage promise
    on simulation is taken out; what C's storage then refuses goes back into P's own storage, and what that refuses too
    (an extract-only storage bus, a full drive) is kept as transit stock: real stock the ledger saves, P lists, the next
    hand-back takes first and P stores back once it has room. The debt goes down only by what C took, so nothing taken
    out is dropped, and the stock reaches the job once. Transit stock survives a restart and is listed again once P is
    in a Federation Domain again. Without the original NetworkId after permanent removal, split or unresolved identity,
    it remains saved rather than being inherited by multiple branches. An operator can explicitly recover real transit
    into a loaded ME block with `/ae2federation returns recover-transit <executing-uuid> <x> <y> <z>`;
    use vanilla `execute in <dimension> run ...` for another dimension. Only accepted units leave saved transit;
    repeated recovery cannot duplicate them. This transfers stock, never lineage or Policy. The destination must be
    loaded; the command does not load chunks. Partial refusal leaves the balance available for a later attempt.
  - A Federation Provider's Lane keeps its binding to its Endpoint while the link between them is broken, so the
    outputs of inputs pushed before the break still come back through that Endpoint into the Provider's network; no
    new inputs go out until the link is back. Rejected output remains in the machine and does not increase held.
    The router directly inserts eligible residual output into the filtered native local storage view and reports that
    actual acceptance to the caller before recording held. Repeated rejection is therefore not a new arrival.
    Existing local inventory can still replace previously accepted stock that a player consumed; this permitted
    advance is bounded by proven arrivals, not by retries.
  - Filtered local storage uses native `NetworkStorage` insertion/extraction, preserving each priority's preferred
    pass and real mount order. A read-only accessor to `NetworkStorage.priorityInventory` preserves that order;
    original wrappers remain intact. Federation and crafting mounts are excluded. Views are reused within a sweep
    and invalidated by native mount generation; reentrant mount changes make old operations fail closed. Native
    end-of-tick storage refresh supplies watcher notifications; the sweep does not repeatedly invalidate full caches.
- **Re-export.** Crafting passes on as storage does (`CraftingReach`). With "C uses M" and "M uses S" set to
  re-export, S's providers are projected onto C too. The push and the return still go straight between C and S; M
  takes no part. A consumer never reaches itself, so mutual rules and rings are allowed, and there is no cycle guard.
- **Reconciliation.** Rule edits and topology changes reconcile at once. The tick reconciles only after a change to the
  domain topology, a rule or a Grid identity. Providers and their patterns are compared every 20 ticks, because AE2
  has no event for a provider's patterns changing. Discovery is shared once per source Grid in each reconciliation
  round; consumer projections remain separate. Stale membership checks use identity sets. Confirmed Grid lookup
  follows the identity epoch, not a TTL. Explicit global-provider removal is synchronous: a small AE2 19.2.17
  `CraftingService.removeGlobalCraftingProvider` return hook withdraws existing consumer projections by source
  Grid and provider identity, so a retained medium cannot execute an unregistered provider between polls. The
  same mixin captures server context at native service construction, while a Grid pivot still exists; withdrawal
  also works after the last node has left and the pivot is null. Native
  refresh's internal remove/add pair does not trigger this hook. Re-registration creates a new projection on the
  next discovery; it cannot reactivate a withdrawn object.

Saves from the earlier delegated model, where P planned and ran its own job for C, keep no delegated jobs: those jobs
are abandoned on load.

The GameTests are `crafting.projection-*`: cake remainders with and without a craftable bucket pattern (returned and
retired within 25 idle ticks), partial output followed by cancellation, cancelled Endpoint output, request, remote
materials, manual return, cancel, revoked, disconnected,
reload, without storage, chain, chain blocked, mutual, container return (three glass bottles back from a three-push
sugar job), container reuse (one bottle filled and reused for six sugar), container push (water as a fluid, a water
bucket and a snowball substitute match AE2's own container expectation; a refused push owes nothing), fluid return
(lava as the output, flint with a late bucket of water, lava after a cancel, lava held while the Bridge is removed,
held and transit lava across a restart),
CPU out of sight
(a CPU in another dimension unloaded mid-job), CPU out of sight beside another job (a second job next to a cancelled
one), CPU late after restart (the CPU loaded long after a restart), merged mid-job (the Grids joined by removing only
the Bridge part, or by a placed cable, then split; later provider stone or lava stays there), merged with mixed demand
(a native job on the consumer's other CPU waits for the same stone; the one stone delivered while merged settles
nothing, and after the split each job gets its stone) and hand-back faults (a
refusing target, a target whose real insert takes less than it simulated, an extract-only source and a full executing
network).
Cross-dimension crafting has its own `cross-dimension.*` GameTests: a nether Provider projected to an overworld
consumer, and a Provider driving an Endpoint in another dimension at the same coordinates through restart, a broken
link, a replaced Endpoint, a real chunk unload of either side and a nether level close. The CPU and level-close cases
unload chunks
with an explicitly marked test fixture; production code loads no chunks. The first command runs every
GameTest of the manifest whose id contains `projection` (including the nether Provider and level-close cases), the
second the Endpoint cases:

```sh
python3 tools/dev_gametests.py --manifest --match projection
python3 tools/dev_gametests.py endpointacrossdimensionskeepsidentity endpointacrossdimensionslinkbreaks endpointacrossdimensionsreplaced endpointacrossdimensionsunloaded
```

The level-close case calls the mod's level-unload callbacks on the nether level directly, as the server does when it
closes a level; a GameTest server cannot close a vanilla dimension.

## Pinned Sources

All source links refer to AE2 commit `79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a`.

- [Public service](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/api/networking/crafting/ICraftingService.java)
- [Public link](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/api/networking/crafting/ICraftingLink.java)
- [Public reload helper](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/api/storage/StorageHelper.java)
- [Terminal confirmation](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/menu/me/crafting/CraftConfirmMenu.java)
- [Automation tracker](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/helpers/MultiCraftingTracker.java)
- [Native service](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/me/service/CraftingService.java)
- [Native CPU execution](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/crafting/execution/CraftingCpuLogic.java)
- [Crafting provider](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/api/networking/crafting/ICraftingProvider.java)
- [Storage provider](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/api/networking/storage/IStorageProvider.java)
- [Native storage priority](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/79ee2c704ad62941a426c26b1cb1f76ef5b2ee5a/src/main/java/appeng/me/storage/NetworkStorage.java)

## Verified Native Lifecycle

Terminal-equivalent requests use a mock-player `PlayerSource`, calculate with
`CalculationStrategy.REPORT_MISSING_ITEMS`, and submit with a null requester. The successful submission is standalone,
extracts two planks, dispatches one native pattern through the Pattern Provider and Molecular Assembler, and returns four
sticks to Grid storage.

Automated stocking uses AE2's `MultiCraftingTracker` with a managed node registered as `ICraftingRequester`. Its
calculation and submission produce a native requester link. Final output is accepted through
`ICraftingRequester.insertCraftedItems`, completion invokes `jobStateChange` once, and the tracker discards the dead link.
After the tracker installs that link, the GameTest invokes the same logical slot a second time. AE2 rejects the duplicate
invocation, the observed requester-link UUID set remains equal to the original link ID with cardinality one, only two
planks are extracted, and exactly four sticks are accepted and stored.
The requester writes into the physical ME chest inventory, not the complete Grid aggregate: the aggregate contains
AE2's highest-priority `CraftingServiceStorage` interception mount and is not a valid requester destination.

The simulation requester must return a native Grid node from `ICraftingSimulationRequester.getGridNode()`. AE2 skips
provider-pattern exploration when that node is absent. The node is captured on the server thread before calculation;
GameTest helper access from the calculator thread is invalid.

Requester persistence writes the native link to NBT and reconstructs it with `StorageHelper.loadCraftingLink`. The
restored link is exposed from `getRequestedJobs()` before the replacement requester joins the Grid, allowing
`CraftingService.addNode()` to rebuild the CPU/requester nexus. Before suspension, the cancellation proof observes Grid
material fall from 64 to zero and all 64 planks in `CraftingCpuLogic` inventory. It then suspends the submitted CPU before
disconnect so no pattern input is dispatched during reconstruction; native cancellation remains active, stops the CPU,
returns the observed CPU inventory to exactly 64 Grid planks, accepts no final output, and preserves the crafting UUID
through a second reload.

## Failure Cases

- Missing two planks produces a simulation plan and native `INCOMPLETE_PLAN`; no task is submitted.
- Available materials and pattern with no crafting CPU produce an executable plan and native `NO_CPU_FOUND`; materials
  remain untouched.
- Native completion is not treated as proof of delivery. Stocking separately asserts the amount accepted by the requester.

## Evidence And Verification

The canonical schema-v3 result selects and executes exactly these five cases with assertion counts `10`, `12`, `7`, `7`,
and `14`:

```sh
./gradlew :neoforge-1.21.1:federationVerify -Pcases=craft-proof.native-terminal,craft-proof.native-stocking,craft-proof.missing-material,craft-proof.no-cpu,craft-proof.disconnect-cancel-restart -PevidenceDir=.omo/evidence/task-09 --no-configuration-cache
RESULT_FILE=$(ls -td .omo/evidence/task-09/attempt-*/result.json | sed -n '1p')
./gradlew :neoforge-1.21.1:federationVerifyEvidence -PresultFile="$RESULT_FILE" -PevidenceDir=.omo/evidence/task-09 --no-configuration-cache
./gradlew :neoforge-1.21.1:federationTaskNineEvidenceSelfTest -PresultFile="$RESULT_FILE" --no-configuration-cache
./gradlew :neoforge-1.21.1:test --tests '*NativeCraftingBindingContractTest' --no-configuration-cache
./gradlew check build --no-configuration-cache
```

Persisted consumption recomputes native job cardinality from observed UUID sets, correlates tracker and restart UUIDs with
their link IDs, validates the exact native artifact set and runtime facts, and binds source, dependency, and product
identity. The adversarial self-test fully rebinds copied evidence and rejects forged duplicate counts/UUIDs, a submitted
duplicate invocation, missing provider dispatch, unaccepted stocking output, invalid missing-material/no-CPU submissions,
forged pre-cancel Grid/CPU extraction, altered return facts, and missing artifacts.
