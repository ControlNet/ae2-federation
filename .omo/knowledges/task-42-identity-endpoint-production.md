# Task 42: identity establishment, stale Endpoint cleanup and production crafting

## Baseline and scope

Base HEAD/dev/origin-dev: `2a3f40db8cdf910568cc4ae658dac8554be1b755` (fetched again during verification).
The task-41 review baseline `865c33c` follows `4f81513`; its remote/CI presence is historical evidence, not evidence
of which session pushed it. No commit or push had occurred at verification time; no Issue or PR was created. Art work in `design/`, `art/`, `.codex/`, Blockbench paths
and the other visual knowledge files was left untouched. The user subsequently authorized committing and pushing only this repair patch, excluding concurrent visual work.

## Confirmed identity defect and lifecycle

The old persisted `provisional` flag was compatible with SETTLED. Domain `nativeEvidence`/`confirmedNetworkId` and
Policy `activation` consume settlement, not node count, so a single-node identity could be persisted in Policy and
Domain evidence. On transfer, the old service could regenerate both NetworkId and nodeId, erasing copied-node evidence.
The baseline red tests show SETTLED instead of AMBIGUOUS_MERGE and a replaced copied node UUID.

Only native CableBus `addPart`/`addToWorld` call scope grants transient adoption authority. This is an in-memory set
of original live nodes, nested scopes close together. Scope exit establishes even a lone node; queries inside the scope
are PARTIAL_LOAD. Saved NBT, including schema-1 `provisional:true`, is established evidence. Never serialize adoption
authority or regenerate a valid node UUID on transfer. Ignore only the old provisional boolean; preserve all valid
lineage values. Two independently finished constructions represent two histories even if connected immediately later.
Two new nodes within one native assembly may connect in either order before adopting one established network.
Existing multi-network ambiguity and split/copy invalidation remain fail-closed.

`NetworkIdentityGridService` counts identical local lineages because the registry's set representation alone loses
same-Grid multiplicity. `IdentityReconciler`, registry and event-driven Claim index otherwise retain their existing
rules/cache. Stable query test: 2,000 cached reads, zero settlement computations and zero lineage lookups added,
256-node target plus 32 other 32-node Grids. Observed 361 ns/read is a single run, not a performance guarantee.

## Retained bindings

A prior push is neither proof of pending work nor completion. Retain dispatched bindings after unmap. A loaded matching
Endpoint requires successful owner/epoch CAS for `released-*`; false returns `rejected-claim-changed` and preserves it.
A loaded absent/replaced Endpoint or changed owner/epoch allows explicit local `cleared-stale-*` without touching the
new Claim. Unloaded is not absent; no force loading. Empty native send/return buffers are required for either action;
never clear buffers to manufacture completion. Native Provider dismantling drops are the documented recovery for an
irrecoverable pending-send target. Retirement/reuse revisions reject old returns. Native lock reset is explicit release,
not a CPU completion inference. Maintenance remains every 20 ticks over this Provider's own Lanes.

The menu includes retained identities absent from the online list. Confirmation binds Provider instance, Lane index,
binding/mapping revisions, Endpoint identity, epoch and remote observation/entity instance. Reopen a stale Domain
session after topology mutation; an old session is not a valid test of fresh menu access.

Tests use the existing test-only machine and a one-shot test-only release CAS fault, plus synthetic native buffers for
drop/rejection checks. These are not production furnace evidence. The fault mixin/probe is absent from the production
JAR. The copied-NBT fixture needs a larger target Grid to force AE2 to move the copied node; equal-size merges may move
the other Grid and miss the old defect. The restart identity fixture is now truly one node (remove its creative cell).
No old assertion was removed and no timeout increased. New evidence cases must also be registered in the Gradle switch;
initialization-only evidence uses one operation and zero resource movement rather than fabricated inventory counts.

## Production evidence

See `docs/testing/production-jar.md`, task-42 section, and `.omo/evidence/task-42-production/`.
Production JAR SHA-256: `f4b5b7d88faf96b7bf99f2918f039880b60cfb482ba6440c14e397e533d2b562`.
AE2 19.2.17, NeoForge 21.1.250, GuideME 21.1.1, LDLib2 2.2.34, Java 21.0.12.1; no testmod.
Two actual terminal requests of 8 iron; second uses remote chunk (5,0), source/CPU chunk (0,0). Unmap while the real
furnace holds raw iron, unload only remote, reload, return to original Provider, native CPU finishes, total16 iron.
Two-step release, save/stop/restart, terminal still16, CPU idle and lane retired. Both server stops and client exit0.

Adjacent chunks may remain loaded around the force-loaded source: verify `execute unless loaded`, do not infer from
player distance. Relocated exported bindings contain absolute positions; loading NBT into an initialized Provider does
not reconstruct its already-created Lanes. Use explicit loaded-absent cleanup and actual GUI remapping. Wait for XTEST
input completion before teleporting; racing these can click world blocks. Stand above the Router rather than under a
low ceiling that changes eye height. Direct Connect IPv4 works where LAN discovery selects unsupported IPv6.

## Final verification

Current manifest: ordinary GameTest153 entries /152 distinct IDs, restart2/2, Applied Flux1/1; combined156/155, all
covered. Full148-test sweep plus final additions/revised targeted reruns; see per-entry coverage in
`.omo/evidence/task-42-verification/coverage.json`. Fixed worktree receipts: targeted `20260926T070012212Z`, canonical
identity `20260926T070457110Z`, order/active Policy `20260926T070737541Z`, Policy `20260926T065149377Z`, resources
`20260926T065650482Z`. Strict check/build:236 JUnit tests,0 failures/errors/skips; archive evidence unit tests15 passed.
Artifact784 entries; no testmod or CAS fault implementation. Details/reproduction: `docs/testing/task-42-review-verification.md`.
Initial stale-source and new-case accounting failures remain recorded; validators were not weakened. Task14's exact
five-case contract remains unchanged, so the added Export Bus activation regression belongs to the identity case group.
Late/Ultra,2-hour soak, full three-layout comparison and third-party requalification remain outside this run.
