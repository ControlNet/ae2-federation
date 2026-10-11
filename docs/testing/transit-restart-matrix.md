# Transit recovery across real server restarts

Run the dedicated three-process suite from the repository root:

```sh
python3 tools/compat_transit_restart.py --accept-eula
./gradlew check --dependency-verification=strict
```

The runner builds the production mod and separate compatibility test mod, installs the pinned baseline profile
(AE2 19.2.17), and starts three independent JVMs against one saved world. It requires exactly one passing matrix
GameTest in each phase and preserves the phase reports, console logs, process IDs and `restart-summary.json` under
`neoforge-1.21.1/build/compat/runs/transit-recovery-restart-*`. The phase property is required to register this test;
it cannot accidentally run as part of an ordinary `all` compatibility selection.

This is server integration evidence with a compatibility test mod. It is not a production-client or terminal-order
proof. Native chests, cells, cable removal, Bridge removal, copied native block NBT, identity resolution, saves,
restarts and recovery commands are real. The explicit fault injection is the initial transit: the fixture inserts
three stone into an actual native cell, extracts those same three units and records them as transit. It does not
pretend that a third-party storage rejection naturally generated this state. The copied-lineage case copies a cell
containing only filler, never the stone tracked by the conservation assertions. Test-only persistent chunk tickets
keep the complete matrix ticking across the restart; production code does not acquire these tickets.

Removing a Bridge exposes the cable's ordinary native connection face. The fixture removes its empty temporary
outer chest before removing the Bridge, so it tests loss of Federation access without inadvertently merging two
native networks. Directly reconnecting two durable identities instead correctly yields `AMBIGUOUS_MERGE`.

No sharing Policy is configured at any point. Each phase asserts that the saved Policy map remains empty. Full
source cells prevent pre-shutdown transit fallback. After the first real restart the test frees that capacity and
waits four actual ledger sweeps before repairing access, proving that an unobserved or ambiguous Grid does not
silently inherit the saved resources.

| Saved condition | Resource location after restart | Player recovery entrance exercised |
| --- | --- | --- |
| Every Federation access part removed; original native storage survives | Three units remain in saved transit under the original executing UUID, outside native cells | Reattach a Bridge; the same confirmed ID becomes observable and transit returns to the original native cells |
| Native cable split creates two live branches with the same saved identity | Three units remain in saved transit; both branches are unconfirmed | Reconnect the original branches, then reattach access; only the legally resolved original ID receives the stock |
| A second live native chest carries copied lineage | Three units remain in saved transit; neither ambiguous Grid receives them | Remove the copied node, then reattach access; the original ID resolves without transferring lineage or Policy |
| Original native network permanently removed | Three units remain in saved transit; there are no remaining original blocks to receive them | Operator explicitly selects a loaded, separate native ME chest with `recover-transit` |

The permanent-removal case rejects a permission-zero command, an unloaded destination, and a destination with no
cell before accepting recovery. The unloaded destination stays unloaded. Repeating the successful command moves
nothing further. The third JVM checks that each case has exactly three physical stone and zero saved transit, then
repeats recovery again to verify that the persisted debit cannot be replayed. Global transit across every executing
UUID is exactly twelve before recovery and zero afterward, preventing a hidden duplicate balance under another ID.
Both split branches must explicitly report `AMBIGUOUS_SPLIT`; both copied-lineage Grids must explicitly report
`COPIED_LIVE_IDENTITY`. The permanently removed ID must have no live native claim anywhere in the registry.

The operator command transfers real transit only. It does not infer consumer allocation, clear outstanding crafting
debt, transfer network identity, or authorize Storage sharing. A player with permanent identity loss needs an
operator to select the intended destination; automatic selection among ambiguous branches would be unsafe. This
matrix covers orderly save/shutdown/restart, not storage corruption, process termination during a save, or every
third-party storage implementation.
