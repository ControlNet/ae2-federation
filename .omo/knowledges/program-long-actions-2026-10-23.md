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
- Not marked: graph card heads (actions and long actions share the purple head) and player functions that call a long
  action.

Image: `build/long-action-check/before-after.png`.
