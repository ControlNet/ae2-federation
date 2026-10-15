# Second follow-up audit and dev delivery — 2026-10-11

Baseline: `2fa70d74f271a69e80baff583940d4debd3a65ed`. This continues the
[first follow-up](follow-up-audit-2026-10-11.md). The user subsequently authorized committing and pushing the verified
changes to the existing `dev` branch. Existing design notes and the local skill directory remain outside this delivery.

## Reproduced findings

| Finding | Before repair | Repair and evidence |
|---|---|---|
| Accepted dispatch withdraws its projection reentrantly | A provider accepted one real input and removed the actual Switch inside its callback. The ledger recorded debt but no CPU lifecycle and could miss reinstalling the return router. | Capture read-only CPU observations and original Grids before calling the provider; commit only after acceptance. The required regression uses real native planning with an explicitly identified callback-injection provider double. Reconnection completes the original CPU with exactly one output; Storage sharing is never configured. |
| Return simulation counts local space twice | A capacity-10 test storage on a real native Grid reported 18 accepted for an offer of 20, with eight owed. The router and its enclosing native traversal both simulated the same mounts. | Simulate remote forwarding in the router and leave local capacity to the enclosing native traversal. Real insertion still records held only after actual acceptance. New required capacity regression and all thirty existing rejection/retry assertions pass. Simulation remains an estimate, not a transaction guarantee. |
| Colliding nonzero P2P inputs link without outputs | Two real inputs assigned the same frequency through native APIs formed a Federation domain despite no native outputs. | Build one output-only peer view per refresh group, while publishing every affected member. The required regression also adds an output and exchanges the native selected input, checking reciprocal domain links. |
| Return router retains a superseded identity | The exact same native Grid legally adopted durable identity A and then B in one tick. One accepted arrival was not held for B. | Replace the mounted router when its captured executing identity differs from the confirmed identity. Preserve A's responsibility without inheriting it into B; unchanged identity keeps the same single router. The required regression checks actual storage quantity and both ledgers. |

These four regressions failed before their production repairs and passed afterward. They do not imply that named
third-party mods perform the injected callback or frequency collision. No save-format change is introduced by these
four repairs, and the same-tick fresh-node adoption rule remains supported.

The additional Lightning Tech 2.1.1 retirement regression also failed before repair. Its standalone CPU-side link
does not necessarily become done after the native CPU retires. The centralized adapter now accepts terminal evidence
only from an active loaded Pigmee owner's fully drained native persistent CPU pool, including pending-loaded jobs.
This uses native public APIs and preserves uncertainty on reflection failure. Retirement, real independent chunk
unload and cancellation passed 3/3 in the worker; the actual two-JVM restart passed both phases. The compatibility
fixture uses real addon CPUs, a hand-operated chest processor and explicitly test-only chunk tickets.

An additional **Advanced AE 1.6.12 retired-history boundary remains unresolved**: after real completion and deliberate
transient-link removal, a saved historical job still yields unknown. The new exploratory regression failed 0/1 and
remains unchanged in `ae2-audit-r2-lifecycle`; its exact registration patch and red log are preserved in the CPU
evidence directory. The shared retirement scene is committed. This is not proof of general task failure or lost
items, and the new failure is not counted as a passing regression. A native full-state/deferred-restore proof is
still required before introducing an adapter; calling the addon's mutating active-CPU getter is not suitable.

## Integrated verification

Source `6660f8f3ab28ed89432da0f4f00c941e0d22bd15` passed **219/219 required GameTests in one 45-second batch**,
strict `check build`, **547 JUnit tests with zero failures/errors/skips**, and **15/15 archive verifier tests**.
The required-runner tests passed 5/5 and compatibility-runner tests passed 4/4. The batch includes the independent
CPU unknown-state test adapted to the read/commit boundary without changing its assertions. Later source receipts
must be identified separately rather than attributed to this run.

Final production source `6ec9e3b2` then passed strict `check build compatTestJar` and a fresh **219/219 required
batch in 43 seconds**, with isolated retries disabled. The two-process restart was rerun on this combined source:
both prepare and resume passed, in run `independent-cpu-restart-1791690607118879799`. The installed production and
compatibility jars were hash-compared with the final build. Production jar SHA-256:
`3c35df148e5e65dfe0bd0e6a67f4a906e4b7277225ca72b30c42152ad7a98e6c`.
The subsequent report commit changes documentation only. Remote checks must be matched to the actual pushed SHA.

```sh
./gradlew --dependency-verification=strict check build --no-configuration-cache
python3 tools/dev_gametests.py --manifest --ci
python3 -m unittest discover -s tests -p test_artifact_verify.py -v
python3 -m unittest discover -s tests -p test_required_gametests.py -v
python3 -m unittest discover -s tests -p test_compat_run.py -v
python3 tools/privacy_guard.py tree HEAD
python3 tools/compat_cpu_restart.py --accept-eula --no-build
git diff --check
```

Expected results are a successful strict build, one complete passing required batch, passing tooling tests, and clean
privacy/whitespace checks. Raw receipts are retained locally under `.omo/evidence/audit-round2-2026-10-11/` and the
per-finding evidence directories. Detailed causes, counterevidence and test commands are recorded in the corresponding
`.omo/knowledges/audit-*-2026-10-11.md` files.

## Performance and boundaries

The P2P correctness regression retained the existing coalescing behavior. Two independent groups produced six
callbacks, one queued task, two group constructions, six group members and six publications. A 31-output pairing run
produced 1,117 callbacks, one queued task, two group constructions, 32 group members, 32 publications and 62 directed
links; measured task time was 1,667 microseconds. Group-member counters are not counts of every Java iteration.
These are operation receipts, not a controlled before/after benchmark or evidence for Late/Ultra factories.

The production-client run recorded in the concentrated review predates this follow-up. Its no-testmod terminal,
processing, blocked-return, reconnection and restart evidence must not be presented as a run of this new artifact.
The original transit recovery boundary matrix likewise remains subject to that report's explicitly stated limits.
Passing CI does not establish all third-party extension paths or a bug-free project.
