# Transit recovery matrix investigation (2026-10-11)

Baseline: `3968e286e3b1479abdc52057046afcd47012a225`, isolated branch
`audit/transit-restart-20261011`. Scope: actual JVM-restart evidence for orphan transit; no production change.

## Reproduction design and counterevidence

Four independent actual native ME networks contain three extracted stone each in saved transit. This initial
ledger insertion is explicit fault injection after native extraction, not a claim that a machine naturally produced
it. The test uses native cable split, native copied block NBT, access removal and full source removal. Copies contain
only coal filler, never the conserved stone. All phases assert no saved sharing Policy exists.

The first fixture did not keep later cases ticking outside the GameTest region. Its timeout at
`MISSING_MAIN_ATTACHMENT` was a fixture error. Persistent test-only chunk tickets now cover construction and reload.
Initial setup was further strengthened to require both source endpoints to share one actual native Grid and the
outer access chest to remain on another Grid before capturing identity. Initial nodes are placed within one chunk.

A later failure at access removal was valid `AMBIGUOUS_MERGE`, not lost lineage: removing a Bridge exposes the native
cable face to the neighboring chest and directly joins two durable networks. Remove the empty temporary outer chest
before removing the Bridge to exercise access removal without a native network merge. Reattach that access endpoint
when restoring the Bridge. This is concrete counterevidence against treating that fixture failure as an identity bug.

Independent review requested exact `AMBIGUOUS_SPLIT` and `COPIED_LIVE_IDENTITY` statuses on both nonnull, distinct,
loaded Grids; empty confirmed identity alone could falsely pass for unfinished initialization. The test now checks
those exact states, plus no remaining live registry claim for the permanently removed executing UUID.

## Recovery entry points and boundaries

After restart without an observed Federation access, stock remains real SavedData transit even when the surviving
native cell has room. The fixture waits four actual ledger sweeps to distinguish this from a brief startup delay.
Reattaching a Bridge observes the legally confirmed original Grid. Split branches must be physically rejoined; a
copied node must be removed; neither ambiguous branch automatically inherits identity or Policy.

If all original nodes are permanently gone, the existing operator `recover-transit` command selects an explicit
loaded native destination. Tests reject permission zero, unloaded target (which stays unloaded), and no-cell target
before successful repeated recovery. A third JVM verifies that native stock and the saved transit debit both
survive and another command cannot duplicate the stock. The command does not discharge debt or allocate transit to
individual consumers. Orderly shutdown is tested; crash-during-save and arbitrary addon storage are outside scope.

## Re-run

```sh
python3 tools/compat_transit_restart.py --accept-eula
./gradlew check --dependency-verification=strict
```

The dedicated property-gated compatibility test is excluded from ordinary `all` selection because its phases require
one persistent world and separate server processes. See `docs/testing/transit-restart-matrix.md` for the four-case
matrix and artifact locations. This is compatibility-test server evidence, not a no-testmod production-client proof.

## Final evidence

Final fixed test source passed all three independent server phases: prepare 23.0 s, recover 23.7 s, verify 15.9 s.
These are single-run wall times, not a performance benchmark. Run receipt:
`transit-recovery-restart-1791692527134282411`. Reports, raw console logs, process/identity state and strict check/build
log are preserved in the main workspace `.omo/evidence/transit-restart-matrix-2026-10-11/`.
`check build --dependency-verification=strict` passed, including `verifySharedJarContent`; Python syntax compilation
and staged secret scan also passed. Independent CPU worker reviewed the matrix and requested the precise status
and global twelve-to-zero assertions now included.

Disposition: all four recovery conditions are covered by existing production mechanisms in this controlled native
server matrix. No production repair and no save-format change were necessary. The test adds no crash-recovery or
all-addon guarantee.
