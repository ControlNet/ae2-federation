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
`crafting/projection/` and uses public AE2 API only.

A crafting rule "C uses P" projects P's providers onto C while all of these hold:

- the rule is enabled and has the `REQUEST` operation;
- the rule is active for the pair, and C and P share a Federation Domain.

The same direction's storage rule is independent of it. Pushes go through the projection and results come back
through the return router, so neither needs storage. The storage rule only adds P's items to what C's CPU can plan
and pay with; without it, C pays with its own storage and P's leftovers stay out of C's sight. The Policy screen
switches the two rules separately, and loading a save adds no rule.

- **Projection.** `RealCraftingProviders` lists P's own providers: the `ICraftingProvider` node services on P's Grid,
  plus the Federation Provider lanes registered on it. Each one gets a `PatternProjection` on C's crafting service
  (`addGlobalCraftingProvider`). It offers the real provider's own pattern objects, priority and busy state, and no
  emitable items. A push goes straight to the real provider. A withdrawn projection offers no patterns, reports busy and
  refuses pushes before it is removed, so nothing is pushed through it afterwards.
- **Returns.** An accepted push records what P now owes C in `CraftingReturnLedger`: the pattern's outputs, plus the
  container items its inputs leave behind, such as empty buckets. The ledger is saved data, so a debt survives a
  reload.
  - P's `CraftingReturnRouter` is mounted on P's storage at `Integer.MAX_VALUE` and is preferred only for owed keys. It
    lists nothing and extracts nothing.
  - It hands back at most `min(inserted, owed, C.getRequestedAmount)` into C's storage, where AE2's
    `CraftingServiceStorage` gives it to the waiting CPU first. Any surplus stays on P.
  - AE2 counts only its own CPU clusters in `getRequestedAmount`. An addon's CPU that AE2 still lists in
    `isRequesting`, such as Neo ECO's computation system, waits for an amount AE2 cannot tell. While no AE2 cluster
    requests the key, the cap is then what the ledger owes C; if that CPU takes less, the rest lands in C's storage,
    which paid the inputs.
  - The container items are counted from the inputs C's CPU extracted for the push, as AE2's `CraftingCpuHelper`
    counts them, before the push: AE2's Molecular Assembler empties the input holder it is pushed, so nothing can be
    read back afterwards. They are owed only if the push is accepted; the holder goes to the provider untouched.
  - Each push also records C's job: AE2's crafting id and its CPU's dimension and position. A debt is dropped when C,
    loaded, requests nothing of the key (by either measure) on two 20-tick looks in a row and every watched job's CPU is
    in sight. A CPU in an unloaded chunk or dimension leaves its Grid with its job intact, so while one is out of sight
    C keeps its debts, however long that lasts; the chunk is only looked at, never loaded. The first look waits 100
    ticks after the service starts, so it does not run while the world is still loading; no debt depends on that delay.
  - How the jobs ended is read from AE2's link of each job. AE2 finishes a job on its final output, so the containers of
    its last push often arrive after it; once a job finished, what P holds for it is handed to C's storage, as it would
    land in one network. After a cancelled job (including a broken CPU, or a job that ended before its reloaded link
    was read) late outputs stay on P, where C sees them only through a storage rule of its own on P.
  - A return crosses only a live Federation link: P and C share a Federation Domain, or are joined through networks
    the domains share, as a chained push is (`FederationLinks`). Switching the rule off keeps returns going, because C
    paid the inputs. An output that arrives while the link is broken, or while C is not loaded, stays in P's storage
    and is counted as held for C (`CraftingReturnLedger`), at most what is owed. Each 20-tick look then hands C, once
    it is linked and loaded, what is held and its CPUs still wait for, taken only from P's own mounts (never
    Federation's mounts of other networks' storage, which may be C's). If P's players used it up, C waits until P
    stores that much again.
  - Handing held stock back is transactional (`HeldReturnTransfer`). Only what both P's sources and C's storage promise
    on simulation is taken out; what C's storage then refuses goes back into P's own storage, and what that refuses too
    (an extract-only storage bus, a full drive) is kept as transit stock: real stock the ledger saves, P lists, the next
    hand-back takes first and P stores back once it has room. The debt goes down only by what C took, so nothing taken
    out is dropped, and the stock reaches the job once. Transit stock survives a restart and is listed again once P is
    in a Federation Domain again.
  - A Federation Provider's Lane keeps its binding to its Endpoint while the link between them is broken, so the
    outputs of inputs pushed before the break still come back through that Endpoint into the Provider's network; no
    new inputs go out until the link is back. When storage on P is full the output may stay in the machine while it is counted as held;
    the hand-back then takes P's stock early, and the output fills P again when it lands, so P loses nothing.
- **Re-export.** Crafting passes on as storage does (`CraftingReach`). With "C uses M" and "M uses S" set to
  re-export, S's providers are projected onto C too. The push and the return still go straight between C and S; M
  takes no part. A consumer never reaches itself, so mutual rules and rings are allowed, and there is no cycle guard.
- **Reconciliation.** Rule edits and topology changes reconcile at once. The tick reconciles only after a change to the
  domain topology, a rule or a Grid identity. Providers and their patterns are compared every 20 ticks, because AE2
  has no event for a provider's patterns changing.

Saves from the earlier delegated model, where P planned and ran its own job for C, keep no delegated jobs: those jobs
are abandoned on load.

The GameTests are `crafting.projection-*`: request, remote materials, manual return, cancel, revoked, disconnected,
reload, without storage, chain, chain blocked, mutual, container return (three glass bottles back from a three-push
sugar job), container reuse (one bottle filled and reused for six sugar), container push (water as a fluid, a water
bucket and a snowball substitute match AE2's own container expectation; a refused push owes nothing), CPU out of sight
(a CPU in another dimension unloaded mid-job), CPU out of sight beside another job (a second job next to a cancelled
one), CPU late after restart (the CPU loaded long after a restart) and hand-back faults (a refusing target, a target
whose real insert takes less than it simulated, an extract-only source and a full executing network). Cross-dimension
crafting has its own `cross-dimension.*` GameTests: a nether Provider projected to an overworld consumer, and a
Provider driving an Endpoint in another dimension at the same coordinates through restart, a broken link, a replaced
Endpoint, a real chunk unload of either side and a nether level close. The CPU and level-close cases unload chunks
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
