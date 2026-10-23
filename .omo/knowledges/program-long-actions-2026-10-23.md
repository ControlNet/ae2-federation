# Long actions (user, 2026-10-23)

Library functions come in three kinds, each with one tag:

| Kind | Tag (EN / ZH) | Functions | Rule |
| --- | --- | --- | --- |
| read | none | `fs.read`, `net.receive`, `ae2.amount`, ... | answers at once, no limit |
| action | action / 操作 (purple square) | `fs.write`, `net.disconnect`, `mc.set_redstone`, ... | one per tick, simulated in a dry run |
| long action | long action / 长操作 (amber square `#ffb547`) | `mc.wait`, `net.http`, `net.connect`, `net.send` | starts as the tick's one action, then the run waits until it finishes; the wait takes no actions |

- The user chose the word (not "waits", which clashes with `mc.wait`; not "task", which clashes with AE2's 合成任务)
  and that a long action takes the tick's action slot when it starts. Before, `mc.wait` was exempt; now
  `ae2.craft(...); mc.wait(0)` puts the wait on the next tick.
- Code: `StandardLibrary.LONG_ACTIONS` / `lasts(Signature)` (a long action is also `effect`, pinned by
  `StandardLibraryTest`); `Runner.tick` no longer exempts `mc.wait`; `Candidate.lasts` and `LibraryRows.Row.lasts`
  pick the amber square in completion and the Library tree; `ui.automation.lasts` is the page line.
- The Chinese UI label for actions was "动作" while the guide said "操作"; both now say 操作.
- Graph card heads (user, 2026-10-23): an action's head is dark purple `0xff4b2f73`, a long action's dark amber
  `0xff6b4f20` (the amber square darkened by the same factor); `GraphCards.Card.lasts`, `FederationGraphView.LASTS`.
- Player functions (user, 2026-10-23): one that calls a long action, directly, through another player function or
  through another file's function, is a long action too. `Effects` spreads it by the same fixed point as effects
  (`LIBRARY_LONG` target, `lastingKeys` = own names plus `file.name`), so recursion and cycles end. It shows in
  completion (amber square; `Completion.lasting`) and on graph card heads. A player function
  that only calls ordinary actions keeps no square in completion, as before. The Library panel lists only the
  standard library, so nothing changes there.
- Runner accounting is unchanged: a call to a player function is not an action node; the long action inside it takes
  the tick's action slot when it starts, as it always did.

Images: `build/long-action-check/before-after.png`, `build/long-action-cards-check/before-after.png`.
