# Advanced AE retired CPU follow-up (2026-10-11)

Follow-up to `audit-independent-cpu-round2-2026-10-11.md`; this resolves its explicitly
retained Advanced AE retirement finding. The original failing test was kept in source,
then extended without weakening the terminal assertion. This commit follows worker
`e6bb3597`; only this new commit should be cherry-picked onto the integrated dev.

## Native lifecycle evidence and minimal repair

Actual Advanced AE 1.6.12 `AdvCraftingCPUCluster` owns the full `activeCpus` map. A CPU
retired after successful completion is no longer enumerated, and its standalone CPU
link does not necessarily signal done. Historical saved Federation jobs with no transient
link therefore remained unknown, preventing ordinary-storage returns of outstanding stock.
The red `quantumCoreRetirement` reproduced `end(...) == null` after actual native completion.
The test uses an isolated saved-ledger copy and explicit transient-link removal; it does
not claim a fully simulated byproduct factory or that every addon request is broken.

The adapter recognizes the exact `AdvCraftingBlockEntity` class, requires a loaded active
nonremoved anchor, an intact cluster, and checks every cluster member is loaded, nonremoved,
and has no pending `getPreviousState()` NBT. Only then can the private **complete** native
`activeCpus` map being empty prove historical jobs ended. A different live job suppresses
the inference. Native `readFromNBT` inserts each CPU into the map before reading CPU state;
`done()` clears the pending core NBT only after that read returns. Any reflective failure
retains uncertainty. This is version-pinned to 1.6.12, centralized in `CraftingCpuLifecycle`.

The public `getActiveCPUs()` method prunes/kills native slots and is deliberately not called
by the production probe. The test may use it to observe native retirement. No scheduler,
CPU, task completion state machine, extra saved data, or forced production chunk loading
was introduced. `TERMINATED` retains its already documented outcome ambiguity; no schema
change. The returns worker and root independently reviewed the actual read-side proof.

## Regression scope and commands

```bash
./gradlew --dependency-verification=strict :neoforge-1.21.1:jar :neoforge-1.21.1:compatTestJar
python3 tools/compat_run.py --accept-eula --no-build --tests quantumcoreretirement,quantumcorechunkunload,quantumcorecancel advanced-ae
python3 tools/compat_cpu_restart.py --accept-eula --no-build --profile advanced-ae
```

The original single-core targeted run passed **3/3**. The final retirement test additionally
uses a native 3×4×3 shell with internal core plus storage and injects the real running job's saved NBT into each
member's pending slot, restoring it in `finally`. Both the anchor and a different member
must reject terminal inference. It also checks an actual other live job and a destroyed
cluster (explicit flag fault injection, restored in `finally`). These fault injections
are labeled and do not replace actual addon CPU logic.

`quantumCoreChunkUnload` uses the actual Quantum Core on a native cable in the nether:
consumer Grid stays loaded, CPU chunk really unloads, two outputs stay held across four
sweeps, and reload completes the same native request. Storage is never enabled. The same
fixture supports separate prepare/resume JVMs with one real saved world through the
profile option of `compat_cpu_restart.py`. Reports assert different PIDs, unchanged saved
UUID/identity, unloaded post-restart sweeps, exact eventual return and no duplicate stock.
This remains a compatibility-test-mod server fixture with a hand-operated chest processor,
not a no-testmod production client demonstration.

Receipts are retained in `.omo/evidence/audit-advanced-cpu-retirement-2026-10-11/`. Native
bytecode review also checked submission map insertion and the remaining-capacity sentinel:
the sentinel advertises capacity, while submitted/restored jobs belong to the full map.
No further blocking counterexample was found in this bounded second pass. This does not
claim all third-party CPU versions or arbitrary ecosystem lifecycle paths are verified.

The first enhanced guard fixture stacked only a core and storage; native Advanced AE
correctly refused to form it. The fixture was corrected to an enclosing native structure
shell; its assertions and timeout were retained. This was not a production regression.

Final production source: targeted original fixture 3/3 passed; actual two-process restart
prepare/resume both passed (`independent-cpu-restart-1791691238921735217`). Full profile
passed 30/31, with only the invalid two-block fixture failing to form. After correcting
that fixture, final multiblock retirement and all member guards passed 1/1 (17 seconds).
