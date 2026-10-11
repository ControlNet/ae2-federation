# Third follow-up audit — 2026-10-11

Baseline: `3968e286e3b1479abdc52057046afcd47012a225`. This round closes evidence gaps in the original review and
repairs two additional qualified defects. Earlier reports remain historical receipts; this report supersedes their
Advanced AE retirement, real global-provider, final-artifact client, and transit-restart limitations where stated.
Unrelated rendering, preview and documentation edits in the main worktree are preserved and excluded from this delivery.

## Findings and supported dispositions

| Review area | Current disposition and evidence |
| --- | --- |
| Return responsibility | Original unconditional drop was statically confirmed and repaired in the concentrated round. New `projectionMixedJobResponsibilities` retains three real native job UUIDs (completed, cancelled and running) across four sweeps, including late containers and cancelled outputs. All final quantities, CPU inventory, held, debt and transit are checked. Storage is never configured. The processor is explicitly hand-operated. |
| Independent CPUs | Original native-only tracking was statically confirmed and repaired. A new real Advanced AE 1.6.12 regression reproduced retired CPU history remaining unresolved after native completion. The narrow adapter now requires an active, formed, non-destroyed host, all members loaded with no pending restore NBT, and an empty full native CPU map. It never calls the mutating `getActiveCPUs`. Actual chunk unload, cancellation and two-JVM restore tests supplement the independent-interface double and prior Neo ECO/Lightning Tech evidence. Unknown implementations remain conservative. |
| Rejected arrival counting | Original held inflation was reproduced and repaired. New multi-consumer test retains one machine item across twelve full refusals, then partially accepts a three-item batch and retries its actual remainder twelve times. Reconnection returns only accepted arrivals, leaves seven unrelated extractable factory items intact, and later completes exact consumer balances one and eight. Debt/job/capacity injection is explicit; the native Grids, Switch and machine ItemStack are real. |
| Native storage order | Existing differential tests cover mount order, priority/preferred combinations, native read/write wrappers, partial refusal, transit and native cache updates. No new defect qualified in this pass. Filtered native NetworkStorage remains the implementation; no wrapper stripping or broad cache invalidation was introduced. |
| Priority changes | Existing refresh repair retained. New two-consumer regression makes nine real native plans across three priority states, including the source reference; pattern objects stay unchanged and both consumers follow the native selected recipe. Independent official providers are identified test doubles. |
| Global providers | Original omission was statically confirmed and repaired through read-only native registration access. New MEGACells 4.11.0 integration uses its actual global DecompressionService, real module/card/bulk cell, native plan and native execution: one iron block becomes nine consumer ingots, factory/debt are zero, and removal withdraws the recipe. This is real-mod integration with compattest, not a production-client or every-addon claim. |
| P2P performance | New regression reproduced 272 directed evidence links for sixteen colliding inputs and sixteen outputs, instead of the native canonical star's 32. Only the selected native input now scans/publishes output links; all affected nodes still publish invalidation. Frequency-zero and old-group behavior remain native. Full legitimate GUI relationships are preserved. |
| Sweep view reuse | Existing 64-debt/32-key/two-consumer test retains one native mount view with explicit mount/identity invalidation. No TTL or per-key rebuild introduced. Backlog/job state is explicitly injected. |
| Source discovery reuse | Existing 64-provider/two-consumer native scene performs two source discoveries for three relationships. Identity membership and native authoritative registrations remain in use. Per-consumer projection maintenance is still required and measured separately from discovery. |
| Transit recovery | Four exact native topology conditions pass across three independent JVMs: access removal, AMBIGUOUS_SPLIT, COPIED_LIVE_IDENTITY, and permanently absent original ID. Resources remain saved until legal reattachment or explicit operator recovery; total transit across every UUID is 12 then zero, with twelve physical items recovered once. No Policy is configured. See [the full matrix](transit-restart-matrix.md) for fixture injection, commands and limitations. Existing recovery mechanisms cover these boundaries; no additional production patch is needed. |
| Same-tick identity | Existing provisional settlement and tick-end refresh are confirmed. A one-shot LOWEST ServerTick.Post probe records actual provider visibility at the first maturity-notified tick end, along with save notification, final lineage and preserved dormant Policy. The earlier level-tick assertion ran before the pending flush; that fixture failure is concrete counterevidence, not a production defect. No timeout was extended. |

There is still no exact job-to-output association. Actual CPU completion or cancellation does not erase missing
outputs. Unknown/unloaded jobs retain responsibility; ordinary consumer-storage fallback waits for every observed
job to be resolved. Existing factory stock may legitimately advance a return, but retries do not become arrivals.
Held is accounting for accepted native stock; transit is actual extracted stock outside cells. SIMULATE remains an
estimate, not a transaction guarantee. The operator recovery command moves transit only, not debt or Policy.

## Production artifact and real restart

Final production source: `dc8b5b42a2132b11205bd8c9faabfc7046fe857a`; subsequent commits add tests/documentation only.
Both installed server/client JARs have SHA-256
`39caefa772950eb8163f1b73b1a719070fc91ecf6f8e643112a2d3e4d510d12c`.
Minecraft 1.21.1 / NeoForge 21.1.250 / AE2 19.2.17 / GuideME 21.1.1 / LDLib2 2.2.34 / Java 21;
only four mod JARs, no testmod.

Actual terminal input orders two stone; the native Provider, CPU, furnace and hoppers process two cobblestone.
A powered hopper blocks collection. Save and stop the first server while CPU waits for two, furnace contains two,
and ledger owes two. The second independent JVM restores that state; unblocking completes the original order and
terminal stock moves from seventeen to nineteen. The third JVM preserves nineteen and an empty ledger. All three
servers and the client exit zero. Saved Policy is exactly unchanged: one Crafting/Request entry and no Storage.
The isolated scene was originally exported by test tooling and uses operator chunk tickets; this does not imply
production code loads chunks. Screenshots, raw NBT/JSON, installed hashes and logs are retained in local ignored
`.omo/evidence/audit-round3-2026-10-11/`.

## Performance receipts

Counts below describe measured workloads, not Late/Ultra certification or a controlled wall-time speedup.

| Workload | Operations | Time |
| --- | --- | --- |
| 16 colliding P2P inputs + 16 outputs, stable refresh | 32 requests, one task, one group build, 32 members/publications, 32 links; pre-fix links 272 | 1,025,826 ns task |
| Earlier 31-output pairing | 1,117 callbacks, one task, two groups including old empty frequency, 32 members/publications, 62 links | 3,121 µs |
| Earlier two independent three-member groups | Six callbacks, one task, two groups, six members/publications, eight links | 761 µs |
| Earlier 64 providers / two consumers | Two discoveries, nine nodes, 129 native global states, three relationship updates | 27,989 ns discovery only |
| Earlier 64 debts / 32 keys / two consumers | One view, two mounts, 64 delivered items | 23,571,416 ns sweep; 93,739 ns view |

Earlier measurements belong to the concentrated-round source, not a new timing run on this artifact. Wall times vary
with concurrent startup; operation counts are the primary evidence. No full large-factory benchmarks were rerun.

## Save format and compatibility limits

This round introduces no new persistence fields or migration. The concentrated round's additive nativeCpu job flag
and unknownJobs set remain; missing old nativeCpu fields default to native, matching old tracking. Existing debt is
never discarded by migration. Policy, lineage, transit key and quantity formats remain unchanged.

The Advanced AE adapter's private activeCpus map and public member/pending-state methods are pinned to 1.6.12 and
centralized in CraftingCpuLifecycle. Failure to access them yields unresolved lifecycle, never inferred completion.
AE2 authoritative registry/storage access remains pinned to 19.2.17 as documented in the native crafting contract;
real addon profiles use their recorded 19.2.18 tuple. No claim covers arbitrary addon versions, exception-after-write
handlers, crash-mid-save recovery, or all independent CPU implementations.

## Reproducible final gates

```sh
./gradlew --dependency-verification=strict check build :neoforge-1.21.1:compatTestJar --no-configuration-cache
python3 tools/dev_gametests.py --manifest --ci --no-isolate
python3 tools/compat_run.py --accept-eula --no-build advanced-ae megacells -j 1
python3 tools/compat_transit_restart.py --accept-eula --no-build
python3 tools/compat_cpu_restart.py --accept-eula --profile advanced-ae --no-build
python3 -m unittest discover -s tests -p test_required_gametests.py -v
python3 -m unittest discover -s tests -p test_compat_run.py -v
git diff --check
```

Expected signals: strict build/archive success, one complete 224-test required batch with no isolated retry,
passing addon profiles, three transit phases and two CPU phases, and passing runner tests. Runtime receipts below
identify actual outcomes separately from these commands.

Final integrated source `2c24bedf` passed **224/224 required GameTests in 45 seconds**, one batch and no isolated
retry; strict `check build compatTestJar` passed in six seconds, including production/source archive gates.
JUnit: **547 tests, zero failures/errors/skips**. Runner unit tests: **5/5 and 4/4**. Complete real addon profiles:
Advanced AE **31/31 in 62 seconds**, MEGACells **26/26 in 60 seconds**. The artifact hash remains exactly equal to
the production client/server receipt above.

On the same combined source, transit prepare/recover/verify passed in three independent JVMs
(receipt `transit-recovery-restart-1791692985657810350`), and Advanced AE prepare/resume passed in two independent
JVMs (receipt `independent-cpu-restart-1791692986828652587`). These include compattest and are separate from the
no-testmod terminal proof. Raw final logs and phase reports were copied outside the worktree before cleanup.

A final independent return-path review found no additional reproduced or statically provable defect. It rechecked
native recursion guards, actual accepted held increments, reservation before external transit insertion, and mixed
job conservative lifecycle. Absence of a further qualified finding is not proof that every ecosystem path is bug-free.
The locally customized audit skill now requires prompt cleanup of completed worktrees after evidence and integration
checks. Finished worker worktrees have been removed; unrelated user worktrees and ongoing edits are preserved.
