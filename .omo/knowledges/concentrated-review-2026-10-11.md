# Concentrated repair review — 2026-10-11

Baseline HEAD: `9e42e0ca3ae1b7145eb0e5ddfae3280bd50c370c`.
Initial user changes preserved: `docs/ideas/README.md`, untracked statistics dashboard proposal,
and two untracked statistics knowledge notes. No issues, PRs or push authorized.

## Confirmed contracts

- AE2 19.2.17 `NetworkCraftingProviders.ProviderState` captures priority when registered.
  `PatternProjectionTest.priorityChangesRefreshEveryConsumerExactlyOnce` failed before the priority fix.
- `projectionRejectedArrival` failed before the router fix: a rejected single item already increased held to 1.
  The caller still owned that same item. The repair counts actual acceptance into native local storage and returns
  that acceptance to the caller. SIMULATE changes no ledger state.
- `NetworkStorage` inserts preferred inventories before non-preferred inventories within each priority, keeping
  mount order within both passes. Copying provider maps and reversing a priority-sorted list is not equivalent.
- `ICraftingCPU` exposes no job UUID, location, or native link. Unknown lifecycle must be persisted conservatively.
  Neo ECO 21.2.1's concrete ECOCraftingCPU independently implements ICraftingCPU through its own API superclass;
  its concrete logic exposes getLastLink/getWaitingFor and CPU exposes getOwner/getLevel/getCluster.
- Native global provider enumeration uses read-only accessors into AE2's own service/provider states. It must exclude
  PatternProjection by identity and avoid replaying registrations or maintaining a separate authority registry.
- Completion and cancellation cannot prove all containers/late outputs returned. Debt may only be retired through
  actual delivery; aggregate waiting=0 is insufficient, including after multiple sweeps.

## Implemented and verified

- Return debt is retired by actual delivery, never by completion/cancellation/idle sweeps. A consumer with any unresolved
  observed job keeps unrequested leftovers. Unknown independent CPU lifecycle is persisted, not inferred complete.
- CPU observation cannot be cached by Grid/tick: a second CPU can start later in that tick. A strengthened independent
  CPU regression failed before removing that cache and passed in the final required batch.
- Neo ECO 21.2.1 and Lightning Tech 2.1.1 optional lifecycle APIs are centralized in CraftingCpuLifecycle. Real
  compatibility profiles passed 28/28 and 30/30. The three new Neo ECO cases never enable Storage. In the tested
  ECO processing path, main output does not complete the CPU before declared byproducts: do not generalize native
  early-completion semantics to it. Independent real addon chunk unload plus process restart is still unverified.
- P2P dirty groups are coalesced per server/Grid/frequency. A moved output invalidates both old and new groups.
  Two stable groups: six callbacks, one task, two builds, six group members/publications. Scale31 pairing: 1,117
  callbacks, one task, two builds (one old empty group), 32 members/publications, 62 directed links.
- Native local storage views retain access wrappers, actual mount order and preferred passes. Reentrant mount changes
  invalidate stale operations. Native end-of-tick monitoring sees updates without full-cache invalidation.
- Per-round source discovery and per-sweep native mount views reuse work; 64 providers/two consumers need two source
  discoveries across three relationships. A 64-debt/32-key/two-consumer injected backlog needs one native view.
- Orphan transit has an operator-only loaded-destination recovery command; it transfers actual saved stock without
  Policy/identity inheritance, and refusal/repeated execution conserve stock. It does not assign transit to a job.
- Fresh-only identity stays PARTIAL during same-tick adoption, then issues save and binding refresh notifications.
  Durable-node initialization and copied/saved lineage restrictions remain intact.

## Evidence

See [the review report](../../docs/testing/concentrated-review-2026-10-11.md) for per-item status and limitations.
`.omo/evidence/concentrated-review-2026-10-11/` holds red/green logs, final required logs, P2P counts, production logs,
screenshots and Policy JSON. Red evidence: priority refresh, rejected arrival and same-tick CPU observation.

Final required batch: 211/211. Strict check/build: 547 JUnit cases, zero failures/errors, release archive checks passed.
Actual global-priority test now checks three native plans; its initial early-return draft was replaced, and its old
partial success is not final evidence. Provider/storage doubles and injected backlog are explicitly marked in tests.

The isolated real production server/client contains only Federation, AE2 19.2.17, GuideME 21.1.1 and LDLib2 2.2.34.
Exported native blocks are driven by actual terminal input, furnace, hoppers and Import Bus, without a testmod.
Policy JSON contains exactly one CRAFTING/REQUEST entry, revision 1/highWatermark 1: Storage was never enabled.
Two eight-stone orders exercised blocked physical output and an actual server restart with owed=8; the final source
also ran a new one-stone order through blockage and recovery. Native inventory reached 17 stone from 17 fixture inputs.
Operator chunk tickets belong to the isolated acceptance fixture; production code never force-loads chunks.

Final production SHA-256: `389d219a69dd9dfc0ead2ebbf7da68fcbf41660e7b4eaa21fd702f868c891bf0`.
The review itself created no commit, issue, PR, or push. The user subsequently authorized committing and pushing the review changes to dev; unrelated knowledge/docs edits remain excluded.
