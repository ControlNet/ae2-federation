# Concentrated crafting, topology and identity review — 2026-10-11

Baseline: `9e42e0ca3ae1b7145eb0e5ddfae3280bd50c370c`; published reference: 0.0.6,
`8749cf473f08651b0ad33fcbdc4fe5b860b7ad3a`. Validation was performed on the local working tree before the subsequent user-authorized dev commit and push; no release was published.
Existing edits to `docs/ideas/README.md` and the statistics proposal/knowledge notes were preserved. The independently
appearing Blender knowledge note was also left untouched. No project rules, artwork, issues or PRs were changed.

## Findings and implementation

| Review item | Finding, observable consequence and repair | Evidence and boundaries |
|---|---|---|
| Return responsibility | Statically confirmed: a hand-back attempt followed by unconditional drop erased remaining ownership. Debt now decreases only on actual delivery. Native completion, cancellation, zero waiting and repeated sweeps never erase missing outputs. All observed jobs must be resolved before unrequested held stock enters ordinary consumer storage; unknown/unloaded/running jobs defer this step. | `projectioncompletedlatereturns`: native main output completes before three delayed byproducts; disconnect, four sweeps, full consumer storage, one accepted item, SavedData round-trip, later batch of two. Crafting only throughout. Existing cancel/container/CPU/cross-dimension tests retained. The SavedData round-trip is not a process restart. |
| Independent CPU lifecycle | Statically confirmed: `watchJobs` previously skipped every independent `ICraftingCPU`. Unknown lifecycle is now persisted. A second regression reproduced Grid/tick observation caching missing a CPU that starts later in the same tick; that cache was removed. `CraftingCpuLifecycle` centralizes native and optional concrete access; missing/unformed CPUs are unresolved, not completed. Loaded adapters at the saved dimension/anchor can prove an old job no longer runs. | Independent interface test double (not a native subclass), real loaded consumer Grid, observation removed, four sweeps and persisted unknown state. Real Neo ECO and Lightning Tech processing/cancel/disconnect tests also run. The three new Neo ECO cases never enable Storage. No claim of an independently chunk-unloaded real addon CPU surviving a real process restart: that exact combination remains unverified. |
| Rejected arrival accounting | Reproduced before repair: first rejected attempt already changed held from zero to one while the machine still owned the item. Residual returns now increase held only after actual insertion into filtered native local storage; accepted quantity is reported to the caller. Same-type real arrivals are not deduplicated by value. | `projectionrejectedarrival`: debt eight, one repeatedly rejected item, twelve attempts, held remains zero; SIMULATE has no side effects; one actual acceptance gives one physical stored item and held one. `projectionhandbackfaults`, access/remount and multi-consumer sweep tests check complementary conservation paths. |
| Native storage order | Statically confirmed against AE2 19.2.17: reverse priority sorting omitted each priority's preferred pass and real mount order. A filtered native `NetworkStorage` now executes insertion and extraction, retaining original wrappers. No repeated full cache invalidation. | `projectionnativefallbackorder`: two mount orders × three priority combinations, native preferred wrapper, real target accepts one of four simulated units, remaining three match native reference distribution. `projectionnativeaccessandremount`: native read/write restrictions, reentrant unmount, stale-view rejection, four conserved items and next-tick native cached inventory refresh. Existing transit/fault tests retained. |
| Priority-only changes | Red JUnit evidence: cached native registration was not refreshed when only provider priority changed. `PatternProjection.refresh` tracks pattern list and priority; unchanged state does not remove/add. | `PatternProjectionTest.priorityChangesRefreshEveryConsumerExactlyOnce`; `projectionglobalpriority` makes three real native calculations using two different recipes for the same output, changes priorities twice, asserts selected `patternTimes`, and verifies withdrawal. Two-consumer refresh is unit coverage; this is not two simultaneous real terminal executions. |
| Official global providers | Statically confirmed omission: node enumeration plus Federation lane scan missed official node-less global registrations. Read-only mixin accessors enumerate AE2's authoritative registry; no replay and no third-party whitelist. Identity sets retain distinct equal-content providers; projections are excluded. | Independent official global providers on native Grids. Registration, priority, three plans, pattern update, two consumers, withdrawal, chain and ring verified. 64 separate providers sharing the same pattern stay distinct. Real addon compatibility results do not establish that those particular addon providers use the global path. |
| P2P refresh | Statically confirmed repeated group construction. One server drain now coalesces dirty `(Grid, frequency)` groups; both old and new frequencies are invalidated. Each group is built once per drain and shared for publication. Native star links and complete GUI graph remain. | `p2prefreshindependentgroups` asserts six callbacks, one queued task, two group builds, six group members and six publications; moving an output updates both domains. Existing frequency/input, power, channels, unload/reload and cross-dimension tests pass. Scale-31 counts below. |
| Return sweep cost | Statically confirmed repeated Grid/index/mount work per debt. Confirmed Grids follow explicit observation and identity epoch invalidation; one sweep reuses native mount views keyed by Grid, mount generation and identity epoch. Reentrant changes invalidate old operations immediately. | `projectionheldsweepcost`: three real Grids, two consumers, 32 keys, 64 injected completed-job debts backed by 64 physical test inventory items; one mount view, all items delivered once. `projectionnativeaccessandremount` tests reentrant invalidation. No TTL. This backlog is injected, not 64 naturally completed crafting plans. |
| Source discovery cost | Statically confirmed repeated discovery per relationship and linear stale membership probes. Discovery is reused per source Grid per reconciliation/periodic round; relationship maps and membership use object identity. | `projectiondiscoverycost`: 64 providers, two consumers, three relationships; two source discoveries, nine nodes and 129 native global states inspected. Global counts include projections inspected and then excluded, not extra real providers. Consumer-specific registrations are still maintained separately. |
| Orphan transit | Statically confirmed permanent reachability gap when no observed Grid can ever carry the old ID. Actual transit remains in SavedData. Added explicit operator recovery into a loaded native ME block; reserve saved stock before calling target, restore refusal, never transfer Policy/lineage or load chunks. | `projectionorphantransitrecovery`: actual stock removed from a cell, injected under a never-assigned ID, save/load, rejected destination retains three, accepted destination receives three, repeated command transfers zero. It proves no original ID is needed, but does not separately reenact access removal, split and ambiguity across real process restarts. |
| Same-tick identity | Statically confirmed that fresh-only networks could expose SETTLED while still allowed to adopt another identity. They now remain PARTIAL until the tick boundary, then issue save and binding refresh events. Fresh nodes joining a durable identity retain existing initialization behavior. | `identityfreshsettlementevent`, existing same-tick initialization/adoption and copied-lineage tests. Same-tick adoption remains authorized only by live node mint evidence; copied/saved NBT gains no such authority. Policy/lineage formats unchanged. |

The independently implemented Neo ECO CPU provided a useful counterexample: with the test's two declared processing
outputs it continues waiting after the main output, until the byproduct arrives. An attempted “main completion first”
test failed at that premise, not at Federation return routing. The final delayed-byproduct test checks its actual
behavior and returns both outputs. Native early completion evidence must not be generalized to all CPUs.

## State and recovery contract

`CraftingReturnLedger` adds `nativeCpu` to job records and a consumer `unknownJobs` set. Old records without
`nativeCpu` load as native, matching the old implementation's native-only tracking. Existing debt/transit keys and
amounts are unchanged. Unknown state is removed only after all that consumer's debt settles. No migration discards
old debt, no abandoned delegated-save strategy is restored, and Policy/identity schemas remain unchanged. Downgrading
to the previous cleanup implementation is not a way to preserve the new responsibility semantics.

There is no exact job-to-output attribution. A consumer with running, cancelled, completed and invisible jobs is
handled conservatively: active CPU demand can receive arrivals, but leftover stock waits until every observed job
has ended. Unsupported CPUs may leave responsibility indefinitely unresolved. Diagnostics distinguish `owed`,
`held`, `transit` and `unknownCpu`; retained debt is bookkeeping, not additional physical inventory.

- **Held** describes accepted arrivals in executing native storage; it is not separately extractable inventory.
- **Transit** is real extracted stock saved outside native cells after both destination and fallback refused it.
  The return router lists/extracts it only on its original executing network and excludes it from Federation export.
- Removing Federation access leaves transit saved. Reattaching the same confirmed ID allows automatic recovery.
  A split/ambiguous ID is not inherited across branches. Permanent ID loss requires an explicit operator destination.
- Recovery moves transit stock only, not return ownership or job state. Operators must inspect outstanding debt as
  well: the command does not invent a consumer allocation for executing-network-wide transit.
- SIMULATE is capacity estimation, not an atomic transaction. Compensating fallback handles normal short/refused
  insertions, not arbitrary third-party exceptions after undocumented side effects.

Operator commands (permission level 2; coordinates refer to the command's current dimension):

```text
/ae2federation returns list
/ae2federation returns recover-transit <executing-uuid> <x> <y> <z>
/execute in minecraft:the_nether run ae2federation returns recover-transit <executing-uuid> <x> <y> <z>
```

The destination must be an already loaded ME block. Partial acceptance leaves the remainder saved. Repeating a
successful recovery cannot reinsert the already removed transit. The archive gate permits `RegisterCommandsEvent`
only in the two exact production registration classes; all existing test/debug paths and symbols remain forbidden.

## Version-sensitive access

AE2 19.2.17: existing native CPU logic plus the new read-only accessors for
`CraftingService.craftingProviders`, `NetworkCraftingProviders.globalProviders`, `ProviderState.provider`, and
`NetworkStorage.priorityInventory`. They are centralized under `mixin/compat`, not copies of AE2 algorithms.

Optional CPU adapter method families: Neo ECO **21.2.1** `ECOCraftingCPU.getOwner/getLogic`; AE2 Lightning Tech
**2.1.1** `TimeWheelCraftingCPU.getHost/getCraftingLogic`; each concrete logic supplies `getLastLink/getWaitingFor`.
Only block-entity anchors are recognized. Optional classes are reflected, not required at startup. Compatibility
profiles use AE2 **19.2.18** with real addon jars; the main required suite and production client use **19.2.17**.

## Measurements

These are single-run operation counts and timings, not a controlled baseline speedup or a Late/Ultra certification.
Group members count the sum of the built group sizes; it is not an instruction-level count of every loop iteration.

| Workload | Counts | Measured time |
|---|---|---|
| P2P 31 outputs, pairing | 1,117 callbacks → 1 task; 2 group builds (one old empty frequency), 32 group members, 32 publications, 62 directed links | 3,121 µs task time |
| Same group, power off | 96 callbacks → 1 task; 1 group, 32 members/publications, zero links | 747 µs |
| Same group, power on | 96 callbacks → 2 tasks; 2 builds, 64 members/publications; final 62 links | 1,329 µs |
| Two independent three-member groups | 6 callbacks → 1 task; 2 builds, 6 members/publications, 8 directed links | 761 µs |
| 64 global providers, two consumers | 2 discoveries, 9 nodes, 129 native global states; 3 relationship updates | 27,989 ns discovery only |
| 64 held debts / 32 keys / two consumers | 1 view, 2 included mounts; 64 items delivered | 23,571,416 ns whole sweep; 93,739 ns view construction |

The measured source-discovery time excludes consumer projection maintenance. No old-build wall-time speedup is
claimed. The original repeated traversal is established by source inspection, not by backfilled timing numbers.

The final run overlapped production-client and compatibility startup; an earlier identical-size run measured
8,349,733 ns per sweep and 30,586 ns for view construction. Counts were unchanged. These noisy wall times are reported
without selecting the faster run as a claimed speedup.

## Reproducible gates and evidence

```sh
./gradlew --dependency-verification=strict check build
python3 tools/dev_gametests.py --manifest --ci
python3 tools/dev_gametests.py p2prefreshindependentgroups p2prefreshscale31
python3 tools/compat_run.py --accept-eula neoecoae ae2-lightning-tech -j 2
sha256sum neoforge-1.21.1/build/libs/ae2federation-0.0.6.jar
git diff --check
```

Expected signals: Gradle `BUILD SUCCESSFUL`; 547 JUnit tests, zero failures/errors; one complete **211/211** required
GameTest batch (no SHARED fallback accepted); two selected P2P cases; real Neo ECO **28/28**, Lightning Tech **30/30**;
clean diff whitespace. `check` includes `verifySharedJarContent` for production and source archives. The compatibility
runner uses the real addon jars plus the explicitly separate compatibility testmod. The production workflow below
uses no testmod at all. No full large-factory benchmark suite was rerun.

Raw logs, screenshots, exported fixture and source-bound receipts are in
[the local evidence directory](../../.omo/evidence/concentrated-review-2026-10-11/). It is ignored runtime evidence,
not a committed substitute for regression source. Red receipts include priority refresh, rejected arrival, and
same-tick CPU observation. Other findings have static confirmation plus passing post-repair regressions; they are
not falsely labelled as captured pre-repair runtime failures. Initial priority-test evidence was superseded because
its first draft returned early from `succeedWhen`; the final version explicitly waits for all three native plans.

Production artifact: `neoforge-1.21.1/build/libs/ae2federation-0.0.6.jar` (version intentionally unchanged).
Final SHA-256: `389d219a69dd9dfc0ead2ebbf7da68fcbf41660e7b4eaa21fd702f868c891bf0`.
Server and client copies have the same hash. Main tuple: Minecraft 1.21.1, NeoForge 21.1.250, AE2 19.2.17,
GuideME 21.1.1, LDLib2 2.2.34, Java 21. The mod directory contains only those four mod jars.

## Production workflow (no testmod)

Isolated server/client directories: `build/concentrated-review-production/{server,client}`. A native structure exported
by test tooling seeds the scene, but neither runtime loads testmod: native terminal, CPU, Pattern Provider, furnace,
hoppers and Import Bus execute every order. Operator fixture chunk tickets keep this isolated acceptance scene
available; the mod never forces chunk loads.

1. Begin with sixteen cobblestone in consumer storage. Its only Policy is CRAFTING/REQUEST; no Storage rule is ever
   created. The final parsed SavedData still has exactly that one entry, revision 1 and highWatermark 1.
2. Click the terminal to plan/order eight stone. Redstone powers the output hopper to prevent collection. Furnace
   reaches eight stone while the ledger remains `owed=8, held=0`. Removing redstone returns eight, clears debt and
   completes the native CPU. Save and stop the actual server JVM, restart and observe the same eight in the terminal.
3. Order the other eight. Save/stop with real furnace input/output and `owed=8`; restart with redstone still present.
   Furnace processing continues, debt remains eight across further sweeps. After recovery, terminal stock is sixteen.
4. With final-source client and server jars, add one explicit fixture input through player inventory into the terminal,
   make a fresh one-stone order, observe the native CPU waiting and blocked furnace output with `owed=1`, unblock,
   observe seventeen stone and zero jobs. Save/stop/restart again: seventeen stone and zero jobs persist. Final ledger
   contains no debt, jobs, unknown CPUs or transit.

The final-source SHA above applies to step 4 and final recovery/restart. Earlier eight-item runs used the preceding
candidate before the additional same-tick CPU observation repair; they are not mislabelled as entirely final-source
runs. During UI operation a creative middle-click copied 64 cobblestone to player inventory; `clear verifier
minecraft:cobblestone` removed that copy before further use. It never entered ME storage or the furnace. The three
orders consumed seventeen fixture inputs and returned seventeen stone.

Evidence: `production-policy.json`, `production-ledger-final.json`, `production-final-source-plan.png`,
`production-final-source-blocked.png`, `production-final-source-returned.png`, `production-final-restart.png`, plus
server logs in the evidence directory. This verifies actual terminal and machine behavior, blocked return, restored
native task/debt and real save/restart. It does not substitute for the separate injected partial-insertion or late
container tests, or for an addon CPU's independent chunk-unload lifecycle.

## Remaining evidence limits

- Unknown independent CPU test uses an interface implementation and a substituted service view; disappearance and
  persistence are controlled fault injection. Native chunk/dimension unload tests remain, but independent real addon
  chunk unload plus process restart is not established by them.
- Global extension-path coverage uses independent providers on native Grids, not a claim about all third-party
  providers or their lifecycle implementations.
- Orphan transit's recovery is exercised with a deliberately unreachable ID and SavedData round-trip. Separate
  production split/ambiguity/removal scenarios across process restarts remain unverified; all use the same explicit
  destination mechanism without automatic identity or Policy inheritance.
- Full per-job attribution remains impossible through the public interface. Unknown responsibility can persist
  indefinitely, and multiple jobs may conservatively delay ordinary-storage returns.
- Synthetic fault inventories/providers and injected backlog are named as such. They complement real addons and
  production-client proof; they do not certify every handler, modpack, long-running server, or Late/Ultra workload.
