# Requirement coverage audit and targeted regressions, 2026-10-11

Read-only review baseline: `3968e286e3b1479abdc52057046afcd47012a225`, the three
concentrated / first-follow-up / second-follow-up reports in `docs/testing/`.
Implementation worktree: `audit/return-coverage-20261011`. Changes in this branch
are regression tests, fixture support, manifest and documentation only.

## Original requirement matrix

Test paths below are relative to `common/src/testmod/java/space/controlnet/ae2federation/test/`
unless a full path is stated. Evidence from previous source commits is not
mislabelled as a test of the latest artifact.

| Requirement | Established evidence | Remaining gap or qualification at review time |
| --- | --- | --- |
| Return responsibility | `ProjectionReviewGameTests.projectionCompletedLateReturns`: real native completion before containers, four disconnected/refused sweeps, two delayed batches, SavedData round-trip, Crafting only. `PatternProjectionGameTests` cancellation. | Final-artifact real process restart belongs to root acceptance run. The original two-job `ProjectionCpuSightGameTests.projectionCpuOutOfSightOtherJob` does not cover three distinct terminal/running states at once and enables Storage. New `ReturnCoverageGameTests.projectionMixedJobResponsibilities` closes that specific gap with actual native plans and UUIDs. |
| Independent CPU lifecycle | Independent interface double in `ProjectionReviewGameTests`; real Neo ECO shared-anchor, Advanced AE cancellation, Lightning Tech retirement/unload and two-JVM restart in compatibility tests. | Advanced AE retired persisted history was still unresolved in the second report; CPU worker owns its native full-state proof. Do not generalize one addon's restart to all addons. |
| Rejected arrivals | `projectionRejectedArrival` proves twelve rejected attempts do not count, then one accepted arrival. `ProjectionHandBackGameTests` and `ReturnReentryGameTests` prove complementary partial fallback/transit conservation. | Previously no integrated multi-consumer partial machine retry plus reconnect with unrelated factory stock. New `projectionPartialRetryRecovery` explicitly injects debts/completed jobs/capacity while preserving actual native Grids, mounts, Switch removal/restoration and a conserved ItemStack buffer. |
| Native storage order | `projectionNativeFallbackOrder` compares actual target distribution against native NetworkStorage across mount order, preferred status and priorities. `projectionNativeAccessAndRemount` checks native access wrappers, reentrant mount invalidation and cache refresh. | Counting inventories and capacity refusals are identified test doubles. No claim of arbitrary third-party handler transactionality. |
| Priority-only provider updates | `PatternProjectionTest` checks both consumers' registration refresh; `projectionGlobalPriority` checks three actual native plans for one consumer. | New `ProviderPriorityCoverageGameTests` calculates both consumers and the native source reference after each of two priority-only edits: nine actual plans, unchanged pattern objects. |
| Official global providers | Independent official registrations, identity deduplication, chains/rings, immediate withdrawal and empty source Grid in `GlobalProviderLifecycleGameTests`; native registration state remains authority. | Real MegaCells 4.11 `DecompressionService` does expose patterns when a decompression module and bulk cell are installed, not merely emitables. Existing generic compatibility passes do not prove that path. CPU worker owns a specific real-mod regression. |
| P2P costs/lifecycle | Existing two-group and scale-31 receipts plus collision domain isolation; old frequency, input removal, power, channels and cross-dimension unload tests. | Root confirmed residual colliding-input candidate publication cost: 16 inputs × 16 outputs produced 272 directed evidence links instead of 32. Canonical-input repair/tests are outside this branch; independent source review approved it. No GUI information loss: nonselected inputs have no native links. |
| Return sweep reuse | `projectionHeldSweepCost`: real native Grids/two consumers/32 keys/64 physically backed debts; one native mount view. Explicit mount generation and identity epoch invalidation. | Jobs/backlog are injected; this is operation-count evidence, not 64 naturally executed crafting jobs or a Late/Ultra certification. |
| Source discovery reuse | `projectionDiscoveryCost`: 64 distinct provider objects, real multi-consumer Grids, two source discoveries/three relationships. | Source discovery time excludes required per-consumer registration. Reported timings are noisy single runs, not a controlled speedup. |
| Transit recovery boundaries | Existing `projectionOrphanTransitRecovery` has no original NetworkId requirement and conserved one-time recovery; `ReturnReentryGameTests` handles reservation before callback. | Access removal, split, ambiguity/recovery and permanently absent ID across real process restart were not covered by the original round-trip. Returns worker owns the real multi-JVM matrix. |
| Same-tick identity/persistent binding | `identityFreshSettlementEvent`, fresh adoption/copy/legacy tests and `ReturnIdentityGameTests` legal same-IGrid A→B router replacement. | Listener/status checks alone did not prove actual binding publication after settlement. New `IdentityBindingCoverageGameTests` tests native managed nodes, an explicit optimistic direct-domain evidence injection, permitted dormant Policy configuration, fail-closed activation, automatic tick-boundary provider publication, and saved final lineage/Policy. This is not a GUI-click or process-restart test. |

## Contract details and counterevidence

`PolicyService.edit` intentionally permits saving inactive/offline rules. A test
requiring it to reject every unsettled endpoint would encode a different product
contract. Fresh-only nodes must instead expose no confirmed NetworkId and publish
Unsettled evidence; runtime Policy activation and provider binding must stay
inactive. Tick settlement must generate actual save and binding events. A fresh
node joining a durable identity can still adopt that identity in its minting tick.
The legal same-IGrid A-to-B transition must not inherit A's rules or responsibility.

The new identity test injects optimistic direct-bridge evidence deliberately: even
with topology and a saved rule present, identity must block activation. It does
not call reconcile after the tick boundary. A read-only LOWEST-priority ServerTick.Post probe captures exactly once, on the
first server tick ending after the maturity save notification. The captured
provider visibility must already be true after the production HIGH-priority
binding refresh; waiting longer cannot replace that snapshot. This checks the
actual tick-end contract rather than an arbitrary GameTest level-tick delay. The test separately saves and reloads Policy data without
restarting the process, and checks the discarded provisional identity has no rule.

P2P source review against AE2 19.2.17 `P2PTunnelPart.getInput` confirms frequency
zero, absent Grid and wrong tunnel class all yield null. Reading canonical input
for input tunnels is supported. Old native output callbacks are emitted by
`P2PService.removeNode/updateFreq` even if later output enumeration is empty.
Filtering nonselected inputs before scanning outputs preserves old-group
invalidation: every changed node still publishes its new empty link evidence.
Only legitimate links remain in the full GUI relationship graph.

MegaCells 4.11.0 bytecode inspected from the pinned installed jar:
`gripe._90.megacells.misc.DecompressionService` registers itself through
`addGlobalCraftingProvider` in its constructor. With installed modules,
`onServerEndTick` obtains `BulkCellInventory.getDecompressionPatterns` and calls
`refreshGlobalCraftingProvider`. `pushPattern` queues a real decompression primary
output and `onServerStartTick` inserts it into native storage. Thus a real-mod
path test is applicable; empty default service registration alone is insufficient.

## New return regression mechanics

`projectionMixedJobResponsibilities` uses the same real native CPU for three
successive jobs, leaving three distinct watched UUIDs: completed diamond with
three delayed bottles, cancelled emerald with two late bowls, and running stone.
The first bottle plus cancelled outputs arrive while stone still waits. After
four ledger sweeps, all three distinct job states and their separate owed keys
remain. The second bottle batch and final stone then allow all actual outputs to
return. Every key has exact final consumer, factory, held, transit and debt checks.
The processor is explicitly hand-operated; no Storage Policy is configured.

`projectionPartialRetryRecovery` uses one item retained through twelve rejected
attempts with a debt-eight consumer plus a debt-one consumer. Two new processor
items form a batch of three; one is accepted and the remaining two are retried
twelve times without increasing held. Seven unrelated factory items are made
available before reconnecting the real Switch. Four sweeps return only the one
accepted item, leaving seven items at the factory. Capacity then accepts the
actual remainder; only three outputs reach consumers. Six explicitly new outputs
finish the injected debts; consumers finish with one and eight, while the factory
still has seven. Existing stock may legitimately supply the returned unit; the
assertion constrains total actual delivery, not per-item provenance. Both consumers
never configure Storage. Job/debt and capacity injection are named in evidence.

The first two new return tests passed together **2/2 in 27 seconds** on unchanged
production code. Raw local receipt: `.omo/evidence/return-coverage-2026-10-11/two-new-coverage-green.log`.
Strict `check` and testmod compilation passed after adding the identity and priority
regression sources. No pre-repair failure is claimed for coverage-only additions.


## Rejected identity fixture assumptions

The first identity draft wrongly selected a specific fresh node as the required
identity loser. Native Grid survival does not prescribe `adoptionTarget`'s chosen
lineage when all regular nodes are fresh. The final fixture records both distinct
initial IDs, checks that the final ID is one of them, and uses the other as the
discarded ID. It preserves the requirement that a same-tick identity change
actually occurred.

A second draft asserted provider visibility inside a delayed GameTest level
callback before the corresponding ServerTick.Post flush. The diagnostic captured
SETTLED, save notification, active Policy, live source recipe and unchanged Grid,
but zero projections and `FederationBindingRefresh.pending=true`; a separately
labelled manual-reconcile control immediately produced two projections. This is
not a production red/green claim. The final test observes the first relevant
server tick end, captures once, and does not call reconcile or increase timeout.
Original failure and diagnostic logs are retained in the local evidence directory.

## Final worker verification

The four new required scenarios and three existing lifecycle/scene-reuse cases
passed in one **7/7 batch, 29 seconds**, without isolated retries. After adding
explicit empty native CPU inventory assertions to both conservation cases, the
final source passed another complete **7/7 batch in 35 seconds**:

```sh
python3 tools/dev_gametests.py identityfreshpolicybinding projectionmixedjobresponsibilities projectionpartialretryrecovery projectionprioritytwoconsumerplans projectionglobalwithdrawalimmediate productionproviderclaimchangedcleanup projectioncpuoutofsightotherjob --ci --no-isolate
./gradlew --dependency-verification=strict check build
```

Expected signals: all seven named cases PASS, exactly one complete batch, strict
Gradle BUILD SUCCESSFUL with production/shared archive checks intact. Raw final
batch log: `.omo/evidence/return-coverage-2026-10-11/coverage-conservation-seven-green.log`;
the earlier batch remains separately recorded as `coverage-seven-green.log`.
The identity event-phase candidate is covered by an existing correct mechanism;
no production patch was needed. The remaining three additions passed against
unchanged production code too. The tests do not add save-format changes or claim
a new performance improvement, and do not substitute for root's no-testmod client
or the returns worker's real process-restart matrix.
